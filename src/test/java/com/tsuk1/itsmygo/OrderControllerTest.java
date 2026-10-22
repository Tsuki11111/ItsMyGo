package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.dto.ShipRequest;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.Payment;
import com.tsuk1.itsmygo.mapper.AddressMapper;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.PaymentMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ReviewMapper;
import com.tsuk1.itsmygo.mapper.UserCouponMapper;
import com.tsuk1.itsmygo.service.ShipmentService;
import com.tsuk1.itsmygo.service.StockService;
import com.tsuk1.itsmygo.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest extends ApiTestBase {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private StockService stockService;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;
    @Autowired
    private MerchantMapper merchantMapper;

    private long createProduct(String adminToken, String name, String price, int stock) throws Exception {
        String catBody = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest(null, "C" + name, 0, true))))
                .andReturn().getResponse().getContentAsString();
        long categoryId = objectMapper.readTree(catBody).path("data").path("id").asLong();

        String body = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, name, "sub", "", new BigDecimal(price), stock, "", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createAddress(String token) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressRequest(
                                "Receiver", "13800000000", "P", "C", "D", "Detail 1", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void addToCart(String token, long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CartAddRequest(productId, defaultSkuId(productId), quantity))))
                .andExpect(status().isOk());
    }

    /** 领一次性提交令牌（前端进结算页时会调） */
    private String fetchOrderToken(String token) throws Exception {
        String body = mockMvc.perform(get("/api/orders/token").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asText();
    }

    /** 购物车结算：items 留空，商品从购物车勾选项取 */
    private long placeOrder(String token, long addressId) throws Exception {
        return placeOrder(token, addressId, null);
    }

    private long placeOrder(String token, long addressId,
                            List<CreateOrderRequest.BuyNowItem> items) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(addressId, fetchOrderToken(token), items))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("order").path("id").asLong();
    }

    /** 「立即购买」：直接指定商品，不走购物车 */
    private long placeBuyNowOrder(String token, long addressId, long productId, int quantity)
            throws Exception {
        return placeOrder(token, addressId,
                List.of(new CreateOrderRequest.BuyNowItem(productId, defaultSkuId(productId), quantity)));
    }

    /**
     * 商品详情现在带缓存，而**库存变动不会主动失效详情缓存**（那会让每次下单都打穿缓存）。
     * 所以这里先 evict 强制回源，断言的才是数据库里的真实库存。
     */
    private void assertProductStock(String token, long productId, int expected) throws Exception {
        redisTemplate.delete("its-my-go:product:" + productId);
        mockMvc.perform(get("/api/products/" + productId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.stock").value(expected));
    }

    /**
     * 库存拆成独立接口后要保证的重点：**详情缓存里的 stock 不再被信任**。
     * 下单扣了库存，详情页和独立接口都必须立刻反映，而不是等缓存 TTL 过期。
     */
    @Test
    void stockIsRealtimeOnBothDetailAndStockEndpoint() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Realtime", "10.00", 10);
        long addressId = createAddress(token);

        mockMvc.perform(get("/api/products/" + productId + "/stock"))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.available").value(true));

        addToCart(token, productId, 3);
        placeOrder(token, addressId);

        // 独立库存接口立刻反映（读的是 Redis 计数器，不查库）
        mockMvc.perform(get("/api/products/" + productId + "/stock"))
                .andExpect(jsonPath("$.data.stock").value(7))
                .andExpect(jsonPath("$.data.available").value(true));

        // 详情接口里的 stock 也必须是实时的，不能是缓存里那份旧值
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.data.stock").value(7))
                .andExpect(jsonPath("$.data.name").value("Realtime"));
    }

    /**
     * 「立即购买」的语义就是**完全不碰购物车** —— 这是它和「加入购物车再结算」唯一的区别，
     * 所以三件事要同时成立：订单里的商品对、库存只扣立即购买的那件、购物车里那条一动不动。
     */
    @Test
    void buyNowDoesNotTouchCart() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long inCartProduct = createProduct(admin, "InCart", "20.00", 10);
        long buyNowProduct = createProduct(admin, "BuyNow", "30.00", 10);
        long addressId = createAddress(token);

        // 购物车里先躺着一件勾选中的商品 —— 立即购买不该动它，更不该把它一起下单
        addToCart(token, inCartProduct, 2);

        long orderId = placeBuyNowOrder(token, addressId, buyNowProduct, 3);

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(buyNowProduct))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.order.totalAmount").value(90.00));

        assertProductStock(token, buyNowProduct, 7);

        // 购物车原样：那条还在、数量没变，它对应的商品库存也没动
        mockMvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(inCartProduct))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
        assertProductStock(token, inCartProduct, 10);
    }

    /** 立即购买走的是同一条库存防线，超量照样被拒，且订单不落地 */
    @Test
    void buyNowRejectsQuantityOverStock() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Tight", "10.00", 2);
        long addressId = createAddress(token);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                addressId, fetchOrderToken(token),
                                List.of(new CreateOrderRequest.BuyNowItem(
                                        productId, defaultSkuId(productId), 3))))))
                .andExpect(jsonPath("$.code").value(400));

        assertProductStock(token, productId, 2);
    }

    /**
     * 多件立即购买时，后面那件库存不足 → 整单失败。
     *
     * ⚠️ 这里最容易漏的是**第一件的 Redis 预扣**：数据库那边有 `@Transactional` 兜着，
     * 但 Redis 不在事务里，只能靠 `doCreate` 的 catch 手动补回来。
     * 少了那一步，一次失败的下单会凭空吃掉库存。
     */
    @Test
    void buyNowRollsBackReservedStockWhenLaterItemFails() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long okProduct = createProduct(admin, "Enough", "10.00", 5);
        long shortProduct = createProduct(admin, "Short", "10.00", 1);
        long addressId = createAddress(token);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                addressId, fetchOrderToken(token),
                                List.of(new CreateOrderRequest.BuyNowItem(
                                                okProduct, defaultSkuId(okProduct), 2),
                                        new CreateOrderRequest.BuyNowItem(
                                                shortProduct, defaultSkuId(shortProduct), 2))))))
                .andExpect(jsonPath("$.code").value(400));

        assertProductStock(token, okProduct, 5);
        assertProductStock(token, shortProduct, 1);
    }

    /** 管理员改库存也要同步到计数器，否则顾客看到的是旧数字 */
    @Test
    void adminStockChangeIsVisibleImmediately() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Restock", "10.00", 5);

        mockMvc.perform(get("/api/products/" + productId + "/stock"))
                .andExpect(jsonPath("$.data.stock").value(5));

        // 管理员补货到 50
        mockMvc.perform(put("/api/admin/products/" + productId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProductRequest("Restock", "10.00", 50))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + productId + "/stock"))
                .andExpect(jsonPath("$.data.stock").value(50));
    }

    /** 构造一个改库存用的请求体（分类沿用该商品原有的） */
    private com.tsuk1.itsmygo.dto.ProductRequest newProductRequest(String name, String price, int stock)
            throws Exception {
        long categoryId = objectMapper.readTree(mockMvc.perform(get("/api/admin/products").header(
                        "Authorization", bearer(adminToken("tmp" + System.nanoTime()))))
                .andReturn().getResponse().getContentAsString())
                .path("data").path("records").get(0).path("categoryId").asLong();
        return new com.tsuk1.itsmygo.dto.ProductRequest(categoryId, name, "sub", "",
                new BigDecimal(price), stock, "", true);
    }

    @Test
    void placeOrderDeductsStockAndClearsCart() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "HAK180", "1999.00", 10);
        long addressId = createAddress(token);
        addToCart(token, productId, 2);

        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(addressId, fetchOrderToken(token), null))))
                .andExpect(jsonPath("$.data.statusLabel").value("待支付"))
                .andExpect(jsonPath("$.data.order.totalAmount").value(3998.00))
                // 收货信息是快照
                .andExpect(jsonPath("$.data.order.receiver").value("Receiver"))
                .andExpect(jsonPath("$.data.items[0].productName").value("HAK180"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(body).path("data").path("order").path("orderNo").asText()).isNotBlank();

        assertProductStock(token, productId, 8);
        mockMvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    /** 订单快照的核心验证：下单后商品改价改名，历史订单必须保持原样 */
    @Test
    void orderItemsAreSnapshotsNotLiveReferences() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Original", "100.00", 5);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);
        long orderId = placeOrder(token, addressId);

        // 改价改名（走管理端接口）
        mockMvc.perform(put("/api/admin/products/" + productId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                objectMapper.readTree(mockMvc.perform(get("/api/products/" + productId))
                                        .andReturn().getResponse().getContentAsString())
                                        .path("data").path("categoryId").asLong(),
                                "Renamed", "sub", "", new BigDecimal("999.00"), 5, "", true))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.order.totalAmount").value(100.00))
                .andExpect(jsonPath("$.data.items[0].productName").value("Original"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(100.00));
    }

    @Test
    void fullLifecyclePayShipConfirm() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Lifecycle", "50.00", 10);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);
        long orderId = placeOrder(token, addressId);

        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.statusLabel").value("待发货"))
                .andExpect(jsonPath("$.data.order.paidAt").isNotEmpty());

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/ship")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShipRequest("SF123456"))))
                .andExpect(jsonPath("$.data.statusLabel").value("待收货"))
                .andExpect(jsonPath("$.data.order.trackingNo").value("SF123456"));

        mockMvc.perform(post("/api/orders/" + orderId + "/confirm").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.statusLabel").value("已完成"));

        // 已完成是终态，不能再取消
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void illegalTransitionsAreRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Illegal", "10.00", 10);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);
        long orderId = placeOrder(token, addressId);

        // 未支付不能发货
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/ship")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShipRequest("SF1"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("订单当前是「待支付」，不能变成「待收货」"));

        // 未发货不能确认收货
        mockMvc.perform(post("/api/orders/" + orderId + "/confirm").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(400));

        // 已支付后不能重复支付
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void cancelRestoresStock() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Cancelme", "10.00", 5);
        long addressId = createAddress(token);
        addToCart(token, productId, 3);
        long orderId = placeOrder(token, addressId);

        assertProductStock(token, productId, 2);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.statusLabel").value("已取消"));

        // 库存和销量都退回去了
        assertProductStock(token, productId, 5);
    }

    @Test
    void placingOrderWithEmptySelectionIsRejected() throws Exception {
        String token = customerToken("c" + System.nanoTime());
        long addressId = createAddress(token);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(addressId, fetchOrderToken(token), null))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请先勾选要购买的商品"));
    }

    // ── 防重复提交 ──

    /** 用户连点两次「下单」：只有第一次成功，第二次令牌已被消费 */
    @Test
    void duplicateSubmitIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Dup", "10.00", 10);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);

        String submitToken = fetchOrderToken(token);
        String body = objectMapper.writeValueAsString(new CreateOrderRequest(addressId, submitToken, null));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(200));

        // 同一个令牌再提交一次 —— 必须被拒
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("订单已提交或令牌已失效，请勿重复提交"));

        // 只扣了一次库存、只生成一笔订单
        assertProductStock(token, productId, 9);
        mockMvc.perform(get("/api/orders").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    /** 令牌是绑定用户的：拿别人的令牌用不了 */
    @Test
    void tokenIsBoundToItsOwner() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String alice = customerToken("a" + System.nanoTime());
        String bob = customerToken("b" + System.nanoTime());
        long productId = createProduct(admin, "Bound", "10.00", 10);
        long bobAddress = createAddress(bob);
        addToCart(bob, productId, 1);

        String aliceToken = fetchOrderToken(alice);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(bobAddress, aliceToken, null))))
                .andExpect(jsonPath("$.code").value(400));

        // Bob 用自己的令牌照样能下单
        placeOrder(bob, bobAddress);
        assertProductStock(bob, productId, 9);
    }

    /** 下单失败（比如库存不足）要把令牌还回去，否则用户修正后无法重试 */
    @Test
    void tokenIsRestoredWhenOrderFails() throws Exception {
        String token = customerToken("c" + System.nanoTime());
        long addressId = createAddress(token);

        String submitToken = fetchOrderToken(token);
        String body = objectMapper.writeValueAsString(new CreateOrderRequest(addressId, submitToken, null));

        // 购物车是空的 → 失败
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(400));

        // 同一个令牌仍然可用（失败时已归还）
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请先勾选要购买的商品"));
    }

    @Test
    void orderWithoutTokenIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "NoTok", "10.00", 10);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(addressId, "", null))))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ── 订单列表（带明细）──

    /** 列表要带明细，前端才能显示「买了什么」 */
    @Test
    void orderListCarriesItemsAndTotalCount() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Listed", "25.00", 20);
        long addressId = createAddress(token);
        addToCart(token, productId, 2);
        placeOrder(token, addressId);

        mockMvc.perform(get("/api/orders").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].statusLabel").value("待支付"))
                .andExpect(jsonPath("$.data.records[0].items.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].items[0].productName").value("Listed"))
                .andExpect(jsonPath("$.data.records[0].itemCount").value(1))
                // 快照字段要在，否则前端显示的金额可能和实付对不上
                .andExpect(jsonPath("$.data.records[0].items[0].unitPrice").isNumber())
                .andExpect(jsonPath("$.data.records[0].order.orderNo").isNotEmpty());
    }

    /** 列表只给前几件明细，但 itemCount 是**总件数** */
    @Test
    void orderListTruncatesItemsButKeepsTotalCount() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long addressId = createAddress(token);

        // 一个订单里放 4 件不同商品
        for (int i = 0; i < 4; i++) {
            long pid = createProduct(admin, "Multi" + i + "_" + System.nanoTime(), "10.00", 10);
            addToCart(token, pid, 1);
        }
        placeOrder(token, addressId);

        mockMvc.perform(get("/api/orders?previewLimit=2").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.records[0].items.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].itemCount").value(4));

        // 传 0 表示不截断
        mockMvc.perform(get("/api/orders?previewLimit=0").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.records[0].items.length()").value(4))
                .andExpect(jsonPath("$.data.records[0].itemCount").value(4));
    }

    /**
     * 列表里的明细必须是**一次批量查询**捞回来的，不是逐个订单查（N+1）。
     *
     * 用一条 IN 查询 + 内存分组：3 个订单只发 1 条明细 SQL。
     * 写成循环里 orderItemMapper.selectList(...) 的话这里会是 3。
     */
    @Test
    void orderListLoadsItemsInOneBatchQuery() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Batch", "10.00", 30);
        long addressId = createAddress(token);

        for (int i = 0; i < 3; i++) {
            addToCart(token, productId, 1);
            placeOrder(token, addressId);
        }

        AtomicInteger itemQueries = new AtomicInteger();
        OrderItemMapper countingMapper = Mockito.mock(OrderItemMapper.class);
        Mockito.when(countingMapper.selectList(Mockito.any())).thenAnswer(invocation -> {
            itemQueries.incrementAndGet();
            return orderItemMapper.selectList(invocation.getArgument(0));
        });

        // 只把 orderItemMapper 换成计数版，其余依赖用容器里的真货
        OrderServiceImpl service = new OrderServiceImpl(orderMapper, countingMapper, cartItemMapper,
                productMapper, productSkuMapper, addressMapper, paymentMapper, stockService,
                redisTemplate, reviewMapper, userCouponMapper, merchantMapper,
                Mockito.mock(ShipmentService.class));

        PageResponse<OrderView> result = service.pageForCustomer(
                currentUserId(token), 1, 10, 3);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.records()).hasSize(3);
        // 三个订单，明细只查了一次
        assertThat(itemQueries.get()).isEqualTo(1);
    }

    /** 订单列表按状态筛：单状态、逗号分隔多状态、拼错的状态名要被挡住 */
    @Test
    void orderListCanBeFilteredByStatus() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Filter", "10.00", 10);
        long addressId = createAddress(token);

        addToCart(token, productId, 1);
        long pendingOrder = placeOrder(token, addressId);

        addToCart(token, productId, 1);
        long paidOrder = placeOrder(token, addressId);
        mockMvc.perform(post("/api/orders/" + paidOrder + "/pay")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));

        // 不传 status → 全部
        mockMvc.perform(get("/api/orders").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/orders?status=PENDING_PAYMENT").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].order.id").value(pendingOrder));

        mockMvc.perform(get("/api/orders?status=PAID").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].order.id").value(paidOrder));

        mockMvc.perform(get("/api/orders?status=PAID,PENDING_PAYMENT")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.total").value(2));

        // 拼错的状态名要变成 400，而不是被当成「没有订单」
        mockMvc.perform(get("/api/orders?status=NOT_A_STATUS")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 从 /api/auth/me 拿当前用户 id */
    private Long currentUserId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    // ── M4：支付流水与退款 ──

    /** 支付要留下可查的流水，而不是只在 orders 上改个状态 */
    @Test
    void payingCreatesPaymentRecord() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Payable", "99.00", 5);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);
        long orderId = placeOrder(token, addressId);

        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.statusLabel").value("待发货"));

        Payment payment = paymentMapper.selectOne(
                Wrappers.<Payment>lambdaQuery().eq(Payment::getOrderId, orderId));
        assertThat(payment).isNotNull();
        assertThat(payment.getPaymentNo()).startsWith("PAY");
        assertThat(payment.getAmount()).isEqualByComparingTo("99.00");
        assertThat(payment.getStatus()).isEqualTo(Payment.STATUS_SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getRefundedAt()).isNull();
        // 冗余的订单号也要对得上，排查问题时才不用 join
        assertThat(payment.getOrderNo()).isNotBlank();
    }

    /** 完整退款：申请 → 同意 → 库存回滚 + 支付流水置为已退款 */
    @Test
    void refundFlowRestoresStockAndMarksPaymentRefunded() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Refundable", "50.00", 5);
        long addressId = createAddress(token);
        addToCart(token, productId, 2);
        long orderId = placeOrder(token, addressId);

        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(token)));
        assertProductStock(token, productId, 3);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.statusLabel").value("退款中"));

        // 审核期间库存**还不能**回滚 —— 万一被驳回呢
        assertProductStock(token, productId, 3);

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/refund/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.statusLabel").value("已退款"));

        // 钱退了，货也要退
        assertProductStock(token, productId, 5);

        Payment payment = paymentMapper.selectOne(
                Wrappers.<Payment>lambdaQuery().eq(Payment::getOrderId, orderId));
        assertThat(payment.getStatus()).isEqualTo(Payment.STATUS_REFUNDED);
        assertThat(payment.getRefundedAt()).isNotNull();
    }

    /** 驳回退款：回到申请前的状态，库存不动 */
    @Test
    void rejectedRefundReturnsToPreviousStatus() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Reject", "30.00", 5);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);
        long orderId = placeOrder(token, addressId);

        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(token)));
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/ship")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShipRequest("SF999"))));

        // 从「待收货」申请退款
        mockMvc.perform(post("/api/orders/" + orderId + "/refund").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.statusLabel").value("退款中"));

        // 驳回后要回到「待收货」而不是别的什么状态
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/refund/reject")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.statusLabel").value("待收货"));

        assertProductStock(token, productId, 4);
    }

    @Test
    void unpaidOrderCannotBeRefunded() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Unpaid", "10.00", 5);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);
        long orderId = placeOrder(token, addressId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("订单当前是「待支付」，不能申请退款"));
    }

    /** 已退款的订单是终态，不能再申请一次 */
    @Test
    void refundedOrderCannotBeRefundedAgain() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Twice", "10.00", 5);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);
        long orderId = placeOrder(token, addressId);

        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(token)));
        mockMvc.perform(post("/api/orders/" + orderId + "/refund").header("Authorization", bearer(token)));
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/refund/approve")
                .header("Authorization", bearer(admin)));

        mockMvc.perform(post("/api/orders/" + orderId + "/refund").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 顾客不能自己批退款 */
    @Test
    void customerCannotApproveRefund() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "NoApprove", "10.00", 5);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);
        long orderId = placeOrder(token, addressId);
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(token)));
        mockMvc.perform(post("/api/orders/" + orderId + "/refund").header("Authorization", bearer(token)));

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/refund/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    /** 超时未支付自动取消，并回滚库存 */
    @Test
    void cancelExpiredOrdersRestoresStock() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Expired", "20.00", 8);
        long addressId = createAddress(token);
        addToCart(token, productId, 3);
        long orderId = placeOrder(token, addressId);

        assertProductStock(token, productId, 5);

        // 把创建时间往前拨，模拟「已经放了 2 小时没人付」。
        // 这里必须用**数据库的** NOW() 来算 —— 业务判断超时用的也是它。
        // 用 JVM 的 LocalDateTime.now() 拨的话，在「MySQL 跑 UTC、应用跑 UTC+8」
        // 的环境下会拨出个「未来时间」，测试和业务就永远对不上。
        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .setSql("created_at = DATE_SUB(NOW(), INTERVAL 2 HOUR)"));

        mockMvc.perform(post("/api/admin/orders/cancel-expired?minutes=30")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.statusLabel").value("已取消"));
        assertProductStock(token, productId, 8);
    }

    /** 刚下的单不该被超时任务误伤 */
    @Test
    void freshOrderIsNotCancelledByExpiryJob() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Fresh", "20.00", 5);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);
        long orderId = placeOrder(token, addressId);

        mockMvc.perform(post("/api/admin/orders/cancel-expired?minutes=30")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data").value(0));

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.statusLabel").value("待支付"));
    }

    @Test
    void otherUserCannotSeeOrTouchSomeoneElsesOrder() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String alice = customerToken("a" + System.nanoTime());
        String bob = customerToken("b" + System.nanoTime());
        long productId = createProduct(admin, "Private", "10.00", 10);
        long aliceAddress = createAddress(alice);
        addToCart(alice, productId, 1);
        long aliceOrder = placeOrder(alice, aliceAddress);

        mockMvc.perform(get("/api/orders/" + aliceOrder).header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(post("/api/orders/" + aliceOrder + "/cancel").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.code").value(404));

        // Bob 的订单列表是空的
        mockMvc.perform(get("/api/orders").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.data.total").value(0));

        // Alice 的订单还在，仍是待支付
        mockMvc.perform(get("/api/orders/" + aliceOrder).header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.data.statusLabel").value("待支付"));
    }

    @Test
    void customerCannotShip() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Ship", "10.00", 10);
        long addressId = createAddress(token);
        addToCart(token, productId, 1);
        long orderId = placeOrder(token, addressId);
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(token)));

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/ship")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShipRequest("SF1"))))
                .andExpect(status().isForbidden());
    }
}
