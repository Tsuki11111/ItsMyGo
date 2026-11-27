package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.LotteryActivityRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryStatusRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 管理端抽奖配置：活动与奖项的创建、校验、权限 */
class LotteryAdminControllerTest extends ApiTestBase {

    @AfterEach
    void clearLotteryRedis() {
        Set<String> keys = redisTemplate.keys("its-my-go:lottery:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void adminCanCreateActivityAndPrize() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String activityName = "抽奖" + System.nanoTime();
        long activityId = createActivity(admin, activityName);

        mockMvc.perform(get("/api/admin/lottery/activities")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value(activityName))
                .andExpect(jsonPath("$.data[0].statusLabel").value("进行中"));

        mockMvc.perform(post("/api/admin/lottery/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("一等奖", "", 1, 1, 2))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.stock").value(2))
                .andExpect(jsonPath("$.data.weight").value(1))
                .andExpect(jsonPath("$.data.remainingStock").value(2));

        mockMvc.perform(get("/api/admin/lottery/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("一等奖"))
                .andExpect(jsonPath("$.data[0].remainingStock").value(2));
    }

    @Test
    void timeRangeIsValidated() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        LocalDateTime now = LocalDateTime.now();

        mockMvc.perform(post("/api/admin/lottery/activities")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryActivityRequest("时间反了", now, now.minusHours(1)))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("结束时间必须晚于开始时间"));
    }

    @Test
    void prizeStockMustBePositive() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long activityId = createActivity(admin, "库存校验" + System.nanoTime());

        mockMvc.perform(post("/api/admin/lottery/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("零库存", "", 1, 1, 0))))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void addPrizeToMissingActivityReturns404() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());

        mockMvc.perform(post("/api/admin/lottery/activities/99999999/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("不存在", "", 1, 1, 1))))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("活动不存在"));
    }

    @Test
    void endedActivityCannotAddPrize() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long activityId = createActivity(admin, "已结束" + System.nanoTime());

        mockMvc.perform(put("/api/admin/lottery/activities/" + activityId + "/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LotteryStatusRequest(2))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.statusLabel").value("已结束"));

        mockMvc.perform(post("/api/admin/lottery/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("晚了", "", 1, 1, 1))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("活动已结束，不能添加奖项"));
    }

    @Test
    void customerCannotAccessAdminLottery() throws Exception {
        String customer = customerToken("c" + System.nanoTime());

        mockMvc.perform(get("/api/admin/lottery/activities")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    private long createActivity(String admin, String name) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String body = mockMvc.perform(post("/api/admin/lottery/activities")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryActivityRequest(name, now.minusHours(1), now.plusHours(1)))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }
}
