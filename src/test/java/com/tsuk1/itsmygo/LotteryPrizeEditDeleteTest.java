package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.ActivityRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 管理端奖项编辑 / 删除：库存差量同步 Redis 奖池、有中奖记录时的类型冻结与删除保护 */
class LotteryPrizeEditDeleteTest extends ApiTestBase {

    @AfterEach
    void clearLotteryRedis() {
        Set<String> keys = redisTemplate.keys("its-my-go:lottery:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void updatePrizeSyncsRedisStock() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long activityId = createActivity(admin);
        long prizeId = addPrize(admin, activityId, "初始奖品", 5);

        // 先抽一次让奖池初始化：5 张码抽走 1 张
        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.result").value("WIN"));

        // 库存 5 → 10，Redis 池应按差量补 5 张：10 - 1 = 9
        mockMvc.perform(put("/api/admin/activities/" + activityId + "/prizes/" + prizeId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("改名奖品", "", null, null, 2, 8, 10))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("改名奖品"))
                .andExpect(jsonPath("$.data.weight").value(8))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.remainingStock").value(9));

        mockMvc.perform(get("/api/admin/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data[0].remainingStock").value(9));
    }

    @Test
    void shrinkStockBelowIssuedIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String alice = customerToken("a" + System.nanoTime());
        String bob = customerToken("b" + System.nanoTime());
        long activityId = createActivity(admin);
        long prizeId = addPrize(admin, activityId, "限量奖品", 2);

        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.data.result").value("WIN"));
        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.data.result").value("WIN"));

        // 已中出 2 件，库存不能降到 1
        mockMvc.perform(put("/api/admin/activities/" + activityId + "/prizes/" + prizeId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("限量奖品", "", null, null, 1, 1, 1))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("已中出")));
    }

    @Test
    void changingTypeWithWinnerIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long activityId = createActivity(admin);
        long prizeId = addPrize(admin, activityId, "实物奖品", 3);

        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.result").value("WIN"));

        mockMvc.perform(put("/api/admin/activities/" + activityId + "/prizes/" + prizeId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest("实物奖品", "", "COUPON", null, 1, 1, 3))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("奖项已有中奖记录，不能修改奖品类型"));
    }

    @Test
    void deletePrizeWithoutWinnerSucceeds() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long activityId = createActivity(admin);
        long prizeId = addPrize(admin, activityId, "无人中奖", 3);

        mockMvc.perform(delete("/api/admin/activities/" + activityId + "/prizes/" + prizeId)
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/admin/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void deletePrizeWithWinnerReturns409() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long activityId = createActivity(admin);
        long prizeId = addPrize(admin, activityId, "已中出奖品", 3);

        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.result").value("WIN"));

        mockMvc.perform(delete("/api/admin/activities/" + activityId + "/prizes/" + prizeId)
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该奖项已有中奖记录，不能删除"));
    }

    @Test
    void unknownPrizeReturns404() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long activityId = createActivity(admin);

        mockMvc.perform(delete("/api/admin/activities/" + activityId + "/prizes/99999999")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("奖项不存在"));
    }

    private long createActivity(String admin) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String body = mockMvc.perform(post("/api/admin/activities")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActivityRequest(
                                "LOTTERY", "抽奖" + System.nanoTime(), "测试抽奖", "#e8638f", "",
                                now.minusHours(1), now.plusHours(1)))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long addPrize(String admin, long activityId, String name, int stock) throws Exception {
        String body = mockMvc.perform(post("/api/admin/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest(name, "", null, null, 1, 1, stock))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }
}
