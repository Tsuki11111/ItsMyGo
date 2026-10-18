package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.entity.UserCoupon;
import com.tsuk1.itsmygo.mapper.UserCouponMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 优惠券：领取、重复领取、领完、状态计算、管理端模板 */
class CouponControllerTest extends ApiTestBase {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private UserCouponMapper userCouponMapper;

    private String couponJson(String name, String threshold, String discount, int total,
                              LocalDateTime from, LocalDateTime to, boolean enabled) {
        return """
                {"name": "%s", "thresholdAmount": %s, "discountAmount": %s,
                 "totalCount": %d, "validFrom": "%s", "validTo": "%s", "enabled": %s}
                """.formatted(name, threshold, discount, total,
                TIME.format(from), TIME.format(to), enabled);
    }

    private long createCoupon(String admin, String json) throws Exception {
        String body = mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long userId(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username)).getId();
    }

    @Test
    void claimThenListMineAndCouponCenter() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long couponId = createCoupon(admin, couponJson("满100减20", "100.00", "20.00", 10,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), true));

        // 匿名可看领券中心
        mockMvc.perform(get("/api/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(couponId))
                .andExpect(jsonPath("$.data[0].claimed").value(false))
                .andExpect(jsonPath("$.data[0].remainingCount").value(10));

        mockMvc.perform(post("/api/coupons/" + couponId + "/claim")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/coupons")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data[0].claimed").value(true))
                .andExpect(jsonPath("$.data[0].remainingCount").value(9));

        mockMvc.perform(get("/api/coupons/mine")
                        .header("Authorization", bearer(customer))
                        .param("status", "UNUSED"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].couponName").value("满100减20"))
                .andExpect(jsonPath("$.data.records[0].status").value("UNUSED"));

        // 重复领取：400，而不是 500
        mockMvc.perform(post("/api/coupons/" + couponId + "/claim")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该优惠券已领取"));
    }

    @Test
    void lastCouponClaimedCannotBeClaimedByAnotherUser() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String first = customerToken("a" + System.nanoTime());
        String second = customerToken("b" + System.nanoTime());
        long couponId = createCoupon(admin, couponJson("仅一张", "0.00", "5.00", 1,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), true));

        mockMvc.perform(post("/api/coupons/" + couponId + "/claim")
                        .header("Authorization", bearer(first)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/coupons/" + couponId + "/claim")
                        .header("Authorization", bearer(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("优惠券已领完或已失效"));
    }

    @Test
    void expiredCouponCannotBeClaimedAndIsNotListed() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long couponId = createCoupon(admin, couponJson("已过期", "0.00", "5.00", 10,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), true));

        mockMvc.perform(get("/api/coupons"))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/api/coupons/" + couponId + "/claim")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void availableCouponsFilterByAmount() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long small = createCoupon(admin, couponJson("满100减20", "100.00", "20.00", 10,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), true));
        long big = createCoupon(admin, couponJson("满500减50", "500.00", "50.00", 10,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), true));

        mockMvc.perform(post("/api/coupons/" + small + "/claim")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/coupons/" + big + "/claim")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/coupons/mine/available")
                        .header("Authorization", bearer(customer))
                        .param("amount", "200"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].couponId").value(small));

        mockMvc.perform(get("/api/coupons/mine/available")
                        .header("Authorization", bearer(customer))
                        .param("amount", "600"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void expiredUserCouponIsComputedAsExpired() throws Exception {
        String username = "c" + System.nanoTime();
        String customer = customerToken(username);
        long userId = userId(username);

        UserCoupon expired = new UserCoupon();
        expired.setUserId(userId);
        expired.setCouponId(888_888L);
        expired.setCouponName("过期券");
        expired.setThresholdAmount(BigDecimal.ZERO);
        expired.setDiscountAmount(new BigDecimal("5.00"));
        expired.setValidFrom(LocalDateTime.now().minusDays(2));
        expired.setValidTo(LocalDateTime.now().minusDays(1));
        expired.setStatus(UserCoupon.UNUSED);
        userCouponMapper.insert(expired);

        mockMvc.perform(get("/api/coupons/mine")
                        .header("Authorization", bearer(customer))
                        .param("status", "EXPIRED"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("EXPIRED"));

        mockMvc.perform(get("/api/coupons/mine")
                        .header("Authorization", bearer(customer))
                        .param("status", "UNUSED"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void adminCanCreateAndUpdateCoupon() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long couponId = createCoupon(admin, couponJson("满100减20", "100.00", "20.00", 10,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), true));

        mockMvc.perform(get("/api/admin/coupons")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("满100减20"));

        mockMvc.perform(put("/api/admin/coupons/" + couponId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson("满200减30", "200.00", "30.00", 20,
                                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), false)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("满200减30"))
                .andExpect(jsonPath("$.data.status").value(0));

        mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson("越权", "0.00", "1.00", 1,
                                LocalDateTime.now(), LocalDateTime.now().plusDays(1), true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void totalCountCannotDropBelowClaimed() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String first = customerToken("a" + System.nanoTime());
        String second = customerToken("b" + System.nanoTime());
        long couponId = createCoupon(admin, couponJson("库存测试", "0.00", "5.00", 5,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), true));

        mockMvc.perform(post("/api/coupons/" + couponId + "/claim")
                        .header("Authorization", bearer(first)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/coupons/" + couponId + "/claim")
                        .header("Authorization", bearer(second)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(put("/api/admin/coupons/" + couponId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson("库存测试", "0.00", "5.00", 1,
                                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), true)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("发放总量不能小于已领取数"));
    }

    @Test
    void invalidPeriodIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());

        mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson("时间反了", "0.00", "5.00", 1,
                                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(1), true)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("有效期开始时间必须早于结束时间"));
    }

    @Test
    void myCouponsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/coupons/mine"))
                .andExpect(status().isUnauthorized());
    }
}
