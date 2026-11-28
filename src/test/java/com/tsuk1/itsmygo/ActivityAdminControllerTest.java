package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.ActivityRequest;
import com.tsuk1.itsmygo.dto.ActivityStatusRequest;
import com.tsuk1.itsmygo.dto.ActivityUpdateRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
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

/** 管理端活动管理：活动 CRUD、状态、抽奖奖项、校验与权限 */
class ActivityAdminControllerTest extends ApiTestBase {

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

        mockMvc.perform(get("/api/admin/activities")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value(activityName))
                .andExpect(jsonPath("$.data[0].typeLabel").value("抽奖"))
                .andExpect(jsonPath("$.data[0].statusLabel").value("进行中"));

        mockMvc.perform(post("/api/admin/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("一等奖", "", null, null, 1, 1, 2))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.stock").value(2))
                .andExpect(jsonPath("$.data.weight").value(1))
                .andExpect(jsonPath("$.data.remainingStock").value(2));

        mockMvc.perform(get("/api/admin/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("一等奖"))
                .andExpect(jsonPath("$.data[0].remainingStock").value(2));
    }

    @Test
    void adminCanUpdateActivity() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long activityId = createActivity(admin, "待改名" + System.nanoTime());
        LocalDateTime now = LocalDateTime.now();

        mockMvc.perform(put("/api/admin/activities/" + activityId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActivityUpdateRequest(
                                "改名后", "新的副标题", "#7c9cff", "/activity/sale-sign.jpg",
                                now.minusHours(1), now.plusHours(2)))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("改名后"))
                .andExpect(jsonPath("$.data.description").value("新的副标题"))
                .andExpect(jsonPath("$.data.primaryColor").value("#7c9cff"))
                .andExpect(jsonPath("$.data.coverImage").value("/activity/sale-sign.jpg"));
    }

    @Test
    void timeRangeIsValidated() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        LocalDateTime now = LocalDateTime.now();

        mockMvc.perform(post("/api/admin/activities")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActivityRequest(
                                "LOTTERY", "时间反了", "", "#e8638f", "",
                                now, now.minusHours(1)))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("结束时间必须晚于开始时间"));
    }

    @Test
    void unknownActivityTypeIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        LocalDateTime now = LocalDateTime.now();

        mockMvc.perform(post("/api/admin/activities")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActivityRequest(
                                "UNKNOWN", "未知活动", "", "#e8638f", "",
                                now.minusHours(1), now.plusHours(1)))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不支持的活动类型"));
    }

    @Test
    void prizeStockMustBePositive() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long activityId = createActivity(admin, "库存校验" + System.nanoTime());

        mockMvc.perform(post("/api/admin/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("零库存", "", null, null, 1, 1, 0))))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void addPrizeToMissingActivityReturns404() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());

        mockMvc.perform(post("/api/admin/activities/99999999/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("不存在", "", null, null, 1, 1, 1))))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("抽奖活动不存在"));
    }

    @Test
    void endedActivityCannotAddPrize() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long activityId = createActivity(admin, "已结束" + System.nanoTime());

        mockMvc.perform(put("/api/admin/activities/" + activityId + "/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActivityStatusRequest(2))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.statusLabel").value("已结束"));

        mockMvc.perform(post("/api/admin/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("晚了", "", null, null, 1, 1, 1))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("活动已结束，不能添加奖项"));
    }

    @Test
    void customerCannotAccessAdminActivities() throws Exception {
        String customer = customerToken("c" + System.nanoTime());

        mockMvc.perform(get("/api/admin/activities")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    private long createActivity(String admin, String name) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String body = mockMvc.perform(post("/api/admin/activities")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActivityRequest(
                                "LOTTERY", name, "测试活动", "#e8638f", "",
                                now.minusHours(1), now.plusHours(1)))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }
}
