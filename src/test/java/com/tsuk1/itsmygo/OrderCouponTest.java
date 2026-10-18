package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.entity.UserCoupon;
import com.tsuk1.itsmygo.mapper.UserCouponMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 下单使用优惠券与取消/退款退券 */
class OrderCouponTest extends ApiTestBase {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private UserCouponMapper userCouponMapper;

    private long createAddress(String customer) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressRequest(
                                "券测试", "13800000000", "广东省", "深圳市", "南山区",
                                "科技园 1 号", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createAndClaimCoupon(String admin, String customer,
                                      String threshold, String discount, int total) throws Exception {
        String json = """
                {"name": "满%s减%s", "thresholdAmount": %s, "discountAmount": %s,
                 "totalCount": %d, "validFrom": "%s", "validTo": "%s", "enabled": true}
                """.formatted(threshold, discount, threshold, discount, total,
                TIME.format(LocalDateTime.now().minusDays(1)),
                TIME.format(LocalDateTime.now().plusDays(7)));
        String body = mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn().getResponse().getContentAsString();
        long couponId = objectMapper.readTree(body).path("data").path("id").asLong();
        mockMvc.perform(post("/api/coupons/" + couponId + "/claim")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200));
        return couponId;
    }

    private long userCouponId(String customer) throws Exception {
        String body = mockMvc.perform(get("/api/coupons/mine")
                        .header("Authorization", bearer(customer))
                        .param("status", "UNUSED"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("records").get(0).path("id").asLong();
    }

    private String orderBody(long addressId, String token, long productId, long skuId, Long couponId) {
        return """
                {"addressId": %d, "token": "%s",
                 "items": [{"productId": %d, "skuId": %d, "quantity": 1}],
                 "couponId": %s}
                """.formatted(addressId, token, productId, skuId,
                couponId == null ? "null" : couponId.toString());
    }

    private String token(String customer) throws Exception {
        String body = mockMvc.perform(get("/api/orders/token")
                        .header("Authorization", bearer(customer)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asText();
    }

    private long placeOrder(String customer, long addressId, long productId, long skuId, Long couponId)
            throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(addressId, token(customer), productId, skuId, couponId)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("order").path("id").asLong();
    }

    @Test
    void couponDiscountsOrderAndIsMarkedUsed() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        createAndClaimCoupon(admin, customer, "100.00", "20.00", 5);
        long userCouponId = userCouponId(customer);
        long productId = createProductForTest(admin, "用券商品" + System.nanoTime(), "100.00", 5);
        long addressId = createAddress(customer);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(addressId, token(customer), productId,
                                defaultSkuId(productId), userCouponId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.order.originalAmount").value(100.00))
                .andExpect(jsonPath("$.data.order.discountAmount").value(20.00))
                .andExpect(jsonPath("$.data.order.totalAmount").value(80.00))
                .andExpect(jsonPath("$.data.order.userCouponId").value(userCouponId))
                .andExpect(jsonPath("$.data.order.couponName").isNotEmpty());

        mockMvc.perform(get("/api/coupons/mine")
                        .header("Authorization", bearer(customer))
                        .param("status", "USED"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void orderBelowThresholdIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        createAndClaimCoupon(admin, customer, "100.00", "20.00", 5);
        long userCouponId = userCouponId(customer);
        long productId = createProductForTest(admin, "低价商品" + System.nanoTime(), "50.00", 5);
        long addressId = createAddress(customer);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(addressId, token(customer), productId,
                                defaultSkuId(productId), userCouponId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("订单金额未达到优惠券使用门槛"));
    }

    @Test
    void usedCouponCannotBeUsedByAnotherOrder() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        createAndClaimCoupon(admin, customer, "0.00", "10.00", 5);
        long userCouponId = userCouponId(customer);
        long productId = createProductForTest(admin, "重复用券" + System.nanoTime(), "50.00", 5);
        long addressId = createAddress(customer);

        placeOrder(customer, addressId, productId, defaultSkuId(productId), userCouponId);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(addressId, token(customer), productId,
                                defaultSkuId(productId), userCouponId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("优惠券已使用"));
    }

    @Test
    void anotherUsersCouponIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String owner = customerToken("o" + System.nanoTime());
        String other = customerToken("x" + System.nanoTime());
        createAndClaimCoupon(admin, owner, "0.00", "10.00", 5);
        long userCouponId = userCouponId(owner);
        long productId = createProductForTest(admin, "越权用券" + System.nanoTime(), "50.00", 5);
        long addressId = createAddress(other);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(addressId, token(other), productId,
                                defaultSkuId(productId), userCouponId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("优惠券不存在"));
    }

    @Test
    void cancelOrderReturnsCoupon() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        createAndClaimCoupon(admin, customer, "0.00", "10.00", 5);
        long userCouponId = userCouponId(customer);
        long productId = createProductForTest(admin, "取消退券" + System.nanoTime(), "50.00", 5);
        long addressId = createAddress(customer);
        long orderId = placeOrder(customer, addressId, productId, defaultSkuId(productId), userCouponId);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/coupons/mine")
                        .header("Authorization", bearer(customer))
                        .param("status", "UNUSED"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void refundReturnsCoupon() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        createAndClaimCoupon(admin, customer, "0.00", "10.00", 5);
        long userCouponId = userCouponId(customer);
        long productId = createProductForTest(admin, "退款退券" + System.nanoTime(), "50.00", 5);
        long addressId = createAddress(customer);
        long orderId = placeOrder(customer, addressId, productId, defaultSkuId(productId), userCouponId);

        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/refund/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/coupons/mine")
                        .header("Authorization", bearer(customer))
                        .param("status", "UNUSED"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void returningAnExpiredCouponMarksItExpired() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        createAndClaimCoupon(admin, customer, "0.00", "10.00", 5);
        long userCouponId = userCouponId(customer);
        long productId = createProductForTest(admin, "过期退券" + System.nanoTime(), "50.00", 5);
        long addressId = createAddress(customer);
        long orderId = placeOrder(customer, addressId, productId, defaultSkuId(productId), userCouponId);

        // 用掉之后把有效期改到过去，模拟「取消时券已过期」
        UserCoupon coupon = userCouponMapper.selectById(userCouponId);
        coupon.setValidTo(LocalDateTime.now().minusDays(1));
        userCouponMapper.updateById(coupon);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/coupons/mine")
                        .header("Authorization", bearer(customer))
                        .param("status", "EXPIRED"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("EXPIRED"));
    }
}
