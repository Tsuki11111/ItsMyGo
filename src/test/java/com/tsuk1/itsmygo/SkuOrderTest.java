package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 订单挂到 SKU 之后：规格要快照进订单，取消要回补到**正确的那一款** */
class SkuOrderTest extends ApiTestBase {

    @Autowired
    private StockService stockService;

    private record ProductWithSkus(long productId, long blackSkuId, long whiteSkuId) {
    }

    private long createProduct(String adminToken, String name) throws Exception {
        String catBody = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryRequest(null, "C" + name, 0, true))))
                .andReturn().getResponse().getContentAsString();
        long categoryId = objectMapper.readTree(catBody).path("data").path("id").asLong();

        String body = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, name, "sub", "", new BigDecimal("10.00"), 0, "", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** 建一个「颜色：黑 / 白」两规格的商品，黑 5 件 19.90、白 3 件 29.90 */
    private ProductWithSkus createProductWithTwoSkus(String admin, String name) throws Exception {
        long productId = createProduct(admin, name);

        mockMvc.perform(put("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "specs": [{"name": "颜色",
                                             "values": [{"id": -1, "value": "黑色"}, {"id": -2, "value": "白色"}]}],
                                  "skus": [
                                    {"specValueIds": [-1], "price": 19.90, "stock": 5, "image": ""},
                                    {"specValueIds": [-2], "price": 29.90, "stock": 3, "image": ""}
                                  ]
                                }
                                """))
                .andExpect(jsonPath("$.code").value(200));

        List<ProductSku> skus = productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId));
        return new ProductWithSkus(productId,
                skuIdOf(skus, "黑色"), skuIdOf(skus, "白色"));
    }

    private long skuIdOf(List<ProductSku> skus, String color) {
        return skus.stream().filter(s -> s.getSpecText().contains(color))
                .findFirst().orElseThrow().getId();
    }

    private void addToCart(String token, long productId, long skuId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CartAddRequest(productId, skuId, quantity))))
                .andExpect(jsonPath("$.code").value(200));
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

    private String fetchOrderToken(String token) throws Exception {
        String body = mockMvc.perform(get("/api/orders/token").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asText();
    }

    /** 购物车结算下单，返回订单 id */
    private long placeOrderFromCart(String token, long addressId) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(addressId, fetchOrderToken(token), null))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("order").path("id").asLong();
    }

    /** 库存的真相在 SKU 行上，Redis 计数器也得跟着 —— 两处都要对 */
    private void assertSkuStock(long skuId, int expected) {
        assertThat(productSkuMapper.selectById(skuId).getStock()).isEqualTo(expected);
        assertThat(stockService.currentSkuStock(skuId)).isEqualTo(expected);
    }

    /**
     * 订单项要记下**买的是哪一款**，而且价格取的是那一款的价格 ——
     * 不是商品的最低价（否则买贵的款式会被按便宜价记账）。
     */
    @Test
    void orderItemSnapshotsSpecAndUsesThatSkusPrice() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        ProductWithSkus p = createProductWithTwoSkus(admin, "记规格");
        long addressId = createAddress(token);

        addToCart(token, p.productId(), p.whiteSkuId(), 2);   // 白色 29.90
        long orderId = placeOrderFromCart(token, addressId);

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].skuId").value(p.whiteSkuId()))
                .andExpect(jsonPath("$.data.items[0].specText").value("颜色:白色"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(29.90))
                .andExpect(jsonPath("$.data.order.totalAmount").value(59.80));
    }

    /**
     * **快照的意义**：商户事后把规格值改个名，历史订单必须原样显示 ——
     * 跟商品改名改价不影响历史订单是一个道理。
     */
    @Test
    void renamingTheSpecLaterDoesNotRewriteHistory() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        ProductWithSkus p = createProductWithTwoSkus(admin, "改名");
        long addressId = createAddress(token);

        addToCart(token, p.productId(), p.whiteSkuId(), 1);
        long orderId = placeOrderFromCart(token, addressId);

        // 商户把「白色」改成「珍珠白」（规格值 id 不变，只是改了名）
        ProductSku white = productSkuMapper.selectById(p.whiteSkuId());
        white.setSpecText("颜色:珍珠白");
        productSkuMapper.updateById(white);

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.items[0].specText").value("颜色:白色"));
    }

    /**
     * 取消订单要把库存还给**它自己那一款**。
     *
     * 这条盯的是 SKU 化过程中最容易漏的一处：`release` 如果还传商品 id（或者传错 SKU），
     * 数字看着也会动，但补的是别人的库存 —— 只有断言「另一款纹丝不动」才抓得出来。
     */
    @Test
    void cancellingRestoresStockToTheVerySkuThatWasBought() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        ProductWithSkus p = createProductWithTwoSkus(admin, "回补");
        long addressId = createAddress(token);

        addToCart(token, p.productId(), p.whiteSkuId(), 2);
        long orderId = placeOrderFromCart(token, addressId);

        assertSkuStock(p.whiteSkuId(), 1);
        assertSkuStock(p.blackSkuId(), 5);   // 没买的那款一点没动

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));

        assertSkuStock(p.whiteSkuId(), 3);   // 回到原样
        assertSkuStock(p.blackSkuId(), 5);
    }

    /** 立即购买同样按 SKU 扣库存，并且只动那一款 */
    @Test
    void buyNowDeductsTheChosenSkuOnly() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        ProductWithSkus p = createProductWithTwoSkus(admin, "立即买");
        long addressId = createAddress(token);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                addressId, fetchOrderToken(token),
                                List.of(new CreateOrderRequest.BuyNowItem(
                                        p.productId(), p.blackSkuId(), 2))))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].specText").value("颜色:黑色"))
                .andExpect(jsonPath("$.data.order.totalAmount").value(39.80));

        assertSkuStock(p.blackSkuId(), 3);
        assertSkuStock(p.whiteSkuId(), 3);
    }

    /** 下单时那一款已经停售 → 整单拒绝，不能悄悄换成别的规格 */
    @Test
    void buyingAStoppedSkuIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        ProductWithSkus p = createProductWithTwoSkus(admin, "停售拒单");
        long addressId = createAddress(token);

        ProductSku white = productSkuMapper.selectById(p.whiteSkuId());
        white.setStatus(0);
        productSkuMapper.updateById(white);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                addressId, fetchOrderToken(token),
                                List.of(new CreateOrderRequest.BuyNowItem(
                                        p.productId(), p.whiteSkuId(), 1))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
