package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 跨店购物车 → 按店拆单；商家订单隔离；券按店铺范围校验 */
class OrderSplitTest extends ApiTestBase {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private ProductSkuMapper productSkuMapper;
    @Autowired
    private StockService stockService;

    private long createCategory(String admin) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryRequest(null, "SC" + System.nanoTime(), 0, true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createProduct(String merchantToken, long categoryId, String name, String price,
                               int stock) throws Exception {
        String body = mockMvc.perform(post("/api/merchant/products")
                        .header("Authorization", bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, name, "", "", new BigDecimal(price), stock, "", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createAddress(String customer) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiver":"拆单测试","phone":"13800000000","province":"广东省",
                                 "city":"深圳市","district":"南山区","detail":"科技园 1 号","isDefault":true}
                                """))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void addToCart(String customer, long productId, long skuId) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": %d, "skuId": %d, "quantity": 1}
                                """.formatted(productId, skuId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String token(String customer) throws Exception {
        String body = mockMvc.perform(get("/api/orders/token")
                        .header("Authorization", bearer(customer)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asText();
    }

    private String checkoutBody(long addressId, String token, String couponsJson) {
        return """
                {"addressId": %d, "token": "%s", "coupons": %s}
                """.formatted(addressId, token, couponsJson);
    }

    private long createCoupon(String admin, String name, String threshold, String discount,
                              int total, Long merchantId) throws Exception {
        String merchant = merchantId == null ? "null" : merchantId.toString();
        String json = """
                {"name": "%s", "thresholdAmount": %s, "discountAmount": %s, "totalCount": %d,
                 "validFrom": "%s", "validTo": "%s", "enabled": true, "merchantId": %s}
                """.formatted(name, threshold, discount, total,
                TIME.format(LocalDateTime.now().minusDays(1)),
                TIME.format(LocalDateTime.now().plusDays(7)), merchant);
        String body = mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long claimCoupon(String customer, long couponId) throws Exception {
        mockMvc.perform(post("/api/coupons/" + couponId + "/claim")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200));
        String body = mockMvc.perform(get("/api/coupons/mine")
                        .header("Authorization", bearer(customer))
                        .param("status", "UNUSED"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("records").get(0).path("id").asLong();
    }

    @Test
    void crossStoreCheckoutCreatesOneOrderPerMerchant() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession a = merchantSession("a" + System.nanoTime(), "拆单A" + System.nanoTime());
        MerchantSession b = merchantSession("b" + System.nanoTime(), "拆单B" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long categoryId = createCategory(admin);
        long productA = createProduct(a.token(), categoryId, "A店品" + System.nanoTime(), "60.00", 5);
        long productB = createProduct(b.token(), categoryId, "B店品" + System.nanoTime(), "40.00", 5);
        addToCart(customer, productA, defaultSkuId(productA));
        addToCart(customer, productB, defaultSkuId(productB));
        long addressId = createAddress(customer);

        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(addressId, token(customer), "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orders.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        // 两个子订单分别属于 A/B 店，金额正确
        var orders = objectMapper.readTree(body).path("data").path("orders");
        long orderA = 0;
        long orderB = 0;
        for (var order : orders) {
            long merchantId = order.path("order").path("merchantId").asLong();
            if (merchantId == a.merchantId()) {
                orderA = order.path("order").path("id").asLong();
                assertThat(order.path("order").path("totalAmount").asDouble()).isEqualTo(60.0);
            } else if (merchantId == b.merchantId()) {
                orderB = order.path("order").path("id").asLong();
                assertThat(order.path("order").path("totalAmount").asDouble()).isEqualTo(40.0);
            }
        }
        assertThat(orderA).isPositive();
        assertThat(orderB).isPositive();

        // 每家店只看得到自己的订单
        mockMvc.perform(get("/api/merchant/orders")
                        .header("Authorization", bearer(a.token())))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(orderA));
        mockMvc.perform(get("/api/merchant/orders")
                        .header("Authorization", bearer(b.token())))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(orderB));
        // A 不能动 B 的订单
        mockMvc.perform(get("/api/merchant/orders/" + orderB)
                        .header("Authorization", bearer(a.token())))
                .andExpect(jsonPath("$.code").value(404));

        // 各自支付、各自发货
        mockMvc.perform(post("/api/orders/" + orderA + "/pay")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/orders/" + orderB + "/pay")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200));

        // B 申请退款后：A 不能审核 B 的退款，B 自己可以
        mockMvc.perform(post("/api/orders/" + orderB + "/refund")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/merchant/orders/" + orderB + "/refund/approve")
                        .header("Authorization", bearer(a.token())))
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(post("/api/merchant/orders/" + orderB + "/refund/approve")
                        .header("Authorization", bearer(b.token())))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/merchant/orders/" + orderA + "/ship")
                        .header("Authorization", bearer(a.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingNo":"SFA001"}
                                """))
                .andExpect(jsonPath("$.data.order.trackingNo").value("SFA001"));
        mockMvc.perform(post("/api/merchant/orders/" + orderB + "/ship")
                        .header("Authorization", bearer(a.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingNo":"SFB001"}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void platformCouponCannotBeAssignedToTwoMerchants() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession a = merchantSession("a" + System.nanoTime(), "券A" + System.nanoTime());
        MerchantSession b = merchantSession("b" + System.nanoTime(), "券B" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long categoryId = createCategory(admin);
        long productA = createProduct(a.token(), categoryId, "券A品" + System.nanoTime(), "60.00", 5);
        long productB = createProduct(b.token(), categoryId, "券B品" + System.nanoTime(), "60.00", 5);
        addToCart(customer, productA, defaultSkuId(productA));
        addToCart(customer, productB, defaultSkuId(productB));
        long addressId = createAddress(customer);
        long userCouponId = claimCoupon(customer,
                createCoupon(admin, "平台券" + System.nanoTime(), "0.00", "10.00", 5, null));

        String coupons = """
                [{"merchantId": %d, "userCouponId": %d},
                 {"merchantId": %d, "userCouponId": %d}]
                """.formatted(a.merchantId(), userCouponId, b.merchantId(), userCouponId);
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(addressId, token(customer), coupons)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("同一张优惠券不能用于多个店铺"));
    }

    @Test
    void storeCouponCannotBeUsedOnAnotherStore() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession a = merchantSession("a" + System.nanoTime(), "范围A" + System.nanoTime());
        MerchantSession b = merchantSession("b" + System.nanoTime(), "范围B" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long categoryId = createCategory(admin);
        long productB = createProduct(b.token(), categoryId, "范围B品" + System.nanoTime(), "60.00", 5);
        long addressId = createAddress(customer);
        long userCouponId = claimCoupon(customer,
                createCoupon(admin, "A店券" + System.nanoTime(), "0.00", "10.00", 5,
                        a.merchantId()));

        String body = """
                {"addressId": %d, "token": "%s",
                 "items": [{"productId": %d, "skuId": %d, "quantity": 1}],
                 "coupons": [{"merchantId": %d, "userCouponId": %d}]}
                """.formatted(addressId, token(customer), productB, defaultSkuId(productB),
                b.merchantId(), userCouponId);
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该优惠券不能用于此店铺"));
    }

    @Test
    void failedStoreRollsBackAllStockReservations() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession a = merchantSession("a" + System.nanoTime(), "回滚A" + System.nanoTime());
        MerchantSession b = merchantSession("b" + System.nanoTime(), "回滚B" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long categoryId = createCategory(admin);
        long productA = createProduct(a.token(), categoryId, "回滚A品" + System.nanoTime(), "60.00", 5);
        long productB = createProduct(b.token(), categoryId, "回滚B品" + System.nanoTime(), "60.00", 5);
        long skuA = defaultSkuId(productA);
        long skuB = defaultSkuId(productB);
        addToCart(customer, productA, skuA);
        addToCart(customer, productB, skuB);
        long addressId = createAddress(customer);
        int stockABefore = productSkuMapper.selectById(skuA).getStock();

        // 让 B 店的 SKU 在下单瞬间库存不足
        ProductSku sku = productSkuMapper.selectById(skuB);
        sku.setStock(0);
        productSkuMapper.updateById(sku);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(addressId, token(customer), "[]")))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("库存不足"));

        // A 店先预扣的 Redis 库存必须被回补。
        // 注意：测试方法本身在 @Transactional 里，服务层的数据库扣减会加入这个测试事务，
        // 这里看不到「生产环境中整单回滚」的效果；Redis 回补是独立于事务的，必须单独断言。
        assertThat(stockService.currentSkuStock(skuA)).isEqualTo(stockABefore);
    }
}
