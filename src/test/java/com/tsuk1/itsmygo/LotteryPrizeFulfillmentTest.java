package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.ActivityRequest;
import com.tsuk1.itsmygo.dto.CouponRequest;
import com.tsuk1.itsmygo.dto.LotteryClaimAddressRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryShipRequest;
import com.tsuk1.itsmygo.entity.LotteryDrawRecord;
import com.tsuk1.itsmygo.entity.LotteryDrawResult;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.entity.UserCoupon;
import com.tsuk1.itsmygo.mapper.LotteryDrawRecordMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeClaimMapper;
import com.tsuk1.itsmygo.mapper.UserCouponMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 抽奖奖品下发：券自动到账、实物领取发货、每天一次 */
class LotteryPrizeFulfillmentTest extends ApiTestBase {

    @Autowired
    private LotteryDrawRecordMapper drawRecordMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private LotteryPrizeClaimMapper claimMapper;

    @AfterEach
    void clearLotteryRedis() {
        Set<String> keys = redisTemplate.keys("its-my-go:lottery:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void couponPrizeIsIssuedAndCoexistsWithClaimedCoupon() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customerName = "c" + System.nanoTime();
        String customer = customerToken(customerName);
        long activityId = createActivity(admin);
        long couponId = createCoupon(admin);
        addPrize(admin, activityId, "满100减10券", "COUPON", couponId, 5);

        // 先在领券中心领一张同款券
        mockMvc.perform(post("/api/coupons/" + couponId + "/claim")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200));

        // 抽奖中同一张券：允许并存，且奖券来源是 LOTTERY
        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.result").value("WIN"))
                .andExpect(jsonPath("$.data.prizeType").value("COUPON"));

        long userId = userIdOf(customerName);
        long total = userCouponMapper.selectCount(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId));
        assertThat(total).isEqualTo(2);
        long lotteryIssued = userCouponMapper.selectCount(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId)
                .eq(UserCoupon::getSource, UserCoupon.SOURCE_LOTTERY));
        assertThat(lotteryIssued).isEqualTo(1);

        mockMvc.perform(get("/api/lottery/my-prizes")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].prizeType").value("COUPON"))
                .andExpect(jsonPath("$.data.records[0].couponStatus").value("UNUSED"))
                .andExpect(jsonPath("$.data.records[0].userCouponId").isNumber());
    }

    @Test
    void physicalPrizeClaimAndShipFlow() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customerName = "c" + System.nanoTime();
        String customer = customerToken(customerName);
        long activityId = createActivity(admin);
        addPrize(admin, activityId, "机械键盘", "PHYSICAL", null, 1);

        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.result").value("WIN"))
                .andExpect(jsonPath("$.data.prizeType").value("PHYSICAL"));

        String myPrizes = mockMvc.perform(get("/api/lottery/my-prizes")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].claim.status").value("PENDING_ADDRESS"))
                .andReturn().getResponse().getContentAsString();
        long claimId = objectMapper.readTree(myPrizes)
                .path("data").path("records").get(0).path("claim").path("id").asLong();

        // 用户填收货地址
        mockMvc.perform(put("/api/lottery/claims/" + claimId + "/address")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LotteryClaimAddressRequest(
                                "Test User", "13800138000", "Shanghai Pudong Road 1"))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING_SHIPMENT"));

        // 管理端能看到中奖记录与待发货状态
        mockMvc.perform(get("/api/admin/activities/" + activityId + "/records")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].username").value(customerName))
                .andExpect(jsonPath("$.data.records[0].claim.status").value("PENDING_SHIPMENT"));

        // 管理端填运单号发货
        mockMvc.perform(put("/api/admin/activities/claims/" + claimId + "/ship")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LotteryShipRequest("SF123456"))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.trackingNo").value("SF123456"));

        mockMvc.perform(get("/api/lottery/my-prizes")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.records[0].claim.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.records[0].claim.trackingNo").value("SF123456"));

        // 发货后不能再改地址
        mockMvc.perform(put("/api/lottery/claims/" + claimId + "/address")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LotteryClaimAddressRequest(
                                "Other", "13900139000", "Beijing Road 2"))))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void anotherUserCannotEditClaimAddress() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String owner = customerToken("c" + System.nanoTime());
        String other = customerToken("o" + System.nanoTime());
        long activityId = createActivity(admin);
        addPrize(admin, activityId, "实物奖", "PHYSICAL", null, 1);

        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.data.result").value("WIN"));
        String claimIdText = mockMvc.perform(get("/api/lottery/my-prizes")
                        .header("Authorization", bearer(owner)))
                .andReturn().getResponse().getContentAsString();
        long claimId = objectMapper.readTree(claimIdText)
                .path("data").path("records").get(0).path("claim").path("id").asLong();

        mockMvc.perform(put("/api/lottery/claims/" + claimId + "/address")
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LotteryClaimAddressRequest(
                                "Other", "13900139000", "Beijing Road 2"))))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void canDrawAgainOnNextDay() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customerName = "c" + System.nanoTime();
        String customer = customerToken(customerName);
        long activityId = createActivity(admin);
        addPrize(admin, activityId, "每日奖", "PHYSICAL", null, 5);

        // 昨天的记录：不占今天的资格
        LotteryDrawRecord yesterday = new LotteryDrawRecord();
        yesterday.setActivityId(activityId);
        yesterday.setUserId(userIdOf(customerName));
        yesterday.setDrawDate(LocalDate.now().minusDays(1));
        yesterday.setResult(LotteryDrawResult.THANKS.name());
        drawRecordMapper.insert(yesterday);

        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.result").value("WIN"));

        long records = drawRecordMapper.selectCount(Wrappers.<LotteryDrawRecord>lambdaQuery()
                .eq(LotteryDrawRecord::getActivityId, activityId));
        assertThat(records).isEqualTo(2);

        // 今天再抽仍返回今天同一条，不新增记录
        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.result").value("WIN"));
        assertThat(drawRecordMapper.selectCount(Wrappers.<LotteryDrawRecord>lambdaQuery()
                .eq(LotteryDrawRecord::getActivityId, activityId))).isEqualTo(2);
    }

    private long userIdOf(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }

    private long createActivity(String admin) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String body = mockMvc.perform(post("/api/admin/activities")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActivityRequest(
                                "LOTTERY", "奖品下发" + System.nanoTime(), "测试抽奖", "#e8638f", "",
                                now.minusHours(1), now.plusHours(1)))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createCoupon(String admin) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String body = mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponRequest(
                                "抽奖测试券", new BigDecimal("100.00"), new BigDecimal("10.00"),
                                100, now.minusDays(1), now.plusDays(7), true, null))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void addPrize(String admin, long activityId, String name, String prizeType,
                          Long couponId, int stock) throws Exception {
        mockMvc.perform(post("/api/admin/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LotteryPrizeRequest(
                                name, "", prizeType, couponId, 1, 1, stock))))
                .andExpect(jsonPath("$.code").value(200));
    }
}
