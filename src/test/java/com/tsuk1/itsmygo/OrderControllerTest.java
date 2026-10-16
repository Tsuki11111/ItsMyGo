package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.dto.ShipRequest;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.Payment;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.PaymentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
                        .content(objectMapper.writeValueAsString(new CartAddRequest(productId, quantity))))
                .andExpect(status().isOk());
    }

    /** 领一次性提交令牌（前端进结算页时会调） */
    private String fetchOrderToken(String token) throws Exception {
        String body = mockMvc.perform(get("/api/orders/token").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asText();
    }

    private long placeOrder(String token, long addressId) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(addressId, fetchOrderToken(token)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("order").path("id").asLong();
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
                                new CreateOrderRequest(addressId, fetchOrderToken(token)))))
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
                .andExpect(jsonPath("$.data.statusLabel").value("已支付"))
                .andExpect(jsonPath("$.data.order.paidAt").isNotEmpty());

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/ship")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShipRequest("SF123456"))))
                .andExpect(jsonPath("$.data.statusLabel").value("已发货"))
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
                .andExpect(jsonPath("$.message").value("订单当前是「待支付」，不能变成「已发货」"));

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
                                new CreateOrderRequest(addressId, fetchOrderToken(token)))))
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
        String body = objectMapper.writeValueAsString(new CreateOrderRequest(addressId, submitToken));

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
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(bobAddress, aliceToken))))
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
        String body = objectMapper.writeValueAsString(new CreateOrderRequest(addressId, submitToken));

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
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(addressId, ""))))
                .andExpect(jsonPath("$.code").value(400));
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
                .andExpect(jsonPath("$.data.statusLabel").value("已支付"));

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

        // 从「已发货」申请退款
        mockMvc.perform(post("/api/orders/" + orderId + "/refund").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.statusLabel").value("退款中"));

        // 驳回后要回到「已发货」而不是别的什么状态
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/refund/reject")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.statusLabel").value("已发货"));

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
