package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.LotteryActivityRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryStatusRequest;
import com.tsuk1.itsmygo.entity.LotteryDrawRecord;
import com.tsuk1.itsmygo.mapper.LotteryDrawRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 顾客端抽奖：一人一次、库存不超发、谢谢参与、MySQL 兜底 */
class LotteryDrawControllerTest extends ApiTestBase {

    @Autowired
    private LotteryDrawRecordMapper drawRecordMapper;

    @AfterEach
    void clearLotteryRedis() {
        Set<String> keys = redisTemplate.keys("its-my-go:lottery:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void drawOnceReturnsSameRecordOnRetry() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long activityId = createActivity(admin);
        addPrize(admin, activityId, "一等奖", 5);

        String first = draw(customer, activityId);
        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.result").value("WIN"))
                .andExpect(jsonPath("$.data.prizeCode").value(
                        objectMapper.readTree(first).path("data").path("prizeCode").asText()));

        long records = drawRecordMapper.selectCount(Wrappers.<LotteryDrawRecord>lambdaQuery()
                .eq(LotteryDrawRecord::getActivityId, activityId));
        assertThat(records).isEqualTo(1);
    }

    @Test
    void thanksWhenNoPrizeAndChanceIsConsumed() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long activityId = createActivity(admin);

        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.result").value("THANKS"))
                .andExpect(jsonPath("$.data.prizeId").value(nullValue()));
        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.result").value("THANKS"));

        mockMvc.perform(get("/api/lottery/" + activityId + "/me")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.drawn").value(true))
                .andExpect(jsonPath("$.data.result").value("THANKS"))
                .andExpect(jsonPath("$.data.prizeId").value(nullValue()));

        long records = drawRecordMapper.selectCount(Wrappers.<LotteryDrawRecord>lambdaQuery()
                .eq(LotteryDrawRecord::getActivityId, activityId));
        assertThat(records).isEqualTo(1);
    }

    @Test
    void stockIsNotOverIssuedSequentially() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String alice = customerToken("a" + System.nanoTime());
        String bob = customerToken("b" + System.nanoTime());
        long activityId = createActivity(admin);
        addPrize(admin, activityId, "唯一奖品", 1);

        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.data.result").value("WIN"));
        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.data.result").value("THANKS"));

        mockMvc.perform(get("/api/lottery/" + activityId + "/prizes")
                        .header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.data[0].remainingStock").value(0));
    }

    @Test
    void myRecordReflectsResult() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long activityId = createActivity(admin);
        addPrize(admin, activityId, "记录奖", 1);

        mockMvc.perform(get("/api/lottery/" + activityId + "/me")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.drawn").value(false));

        draw(customer, activityId);

        mockMvc.perform(get("/api/lottery/" + activityId + "/me")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.drawn").value(true))
                .andExpect(jsonPath("$.data.result").value("WIN"))
                .andExpect(jsonPath("$.data.prizeName").value("记录奖"))
                .andExpect(jsonPath("$.data.prizeCode").isNotEmpty());
    }

    @Test
    void endedActivityRejectsDraw() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long activityId = createActivity(admin);

        mockMvc.perform(put("/api/admin/lottery/activities/" + activityId + "/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LotteryStatusRequest(2))))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("活动已结束"));
    }

    @Test
    void unknownActivityReturns404() throws Exception {
        String customer = customerToken("c" + System.nanoTime());

        mockMvc.perform(post("/api/lottery/99999999/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("活动不存在"));
    }

    @Test
    void anonymousCanSeeActiveActivityAndPrizesButCannotDraw() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long activityId = createActivity(admin);
        addPrize(admin, activityId, "公开奖池", 2);

        // 当前活动与奖池匿名可看
        mockMvc.perform(get("/api/lottery/active"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(activityId))
                .andExpect(jsonPath("$.data.statusLabel").value("进行中"));
        mockMvc.perform(get("/api/lottery/" + activityId + "/prizes"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("公开奖池"))
                .andExpect(jsonPath("$.data[0].remainingStock").value(2));

        // 抽奖与我的记录仍要求登录
        mockMvc.perform(post("/api/lottery/" + activityId + "/draw"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/lottery/" + activityId + "/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeReturnsLatestActiveActivity() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long first = createActivity(admin);
        long second = createActivity(admin);

        mockMvc.perform(get("/api/lottery/active"))
                .andExpect(jsonPath("$.data.id").value(second));

        // 结束最新那个后，回到前一个进行中的活动
        mockMvc.perform(put("/api/admin/lottery/activities/" + second + "/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LotteryStatusRequest(2))))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/lottery/active"))
                .andExpect(jsonPath("$.data.id").value(first));
    }

    @Test
    void redisParticipantLossStillPreventsDuplicateDraw() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long activityId = createActivity(admin);
        addPrize(admin, activityId, "兜底奖", 5);

        String first = draw(customer, activityId);
        String firstCode = objectMapper.readTree(first).path("data").path("prizeCode").asText();

        // 模拟 Redis 资格集合丢失：MySQL 唯一键仍必须阻止重复抽奖
        redisTemplate.delete("its-my-go:lottery:participants:" + activityId);

        mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.result").value("WIN"))
                .andExpect(jsonPath("$.data.prizeCode").value(firstCode));

        long records = drawRecordMapper.selectCount(Wrappers.<LotteryDrawRecord>lambdaQuery()
                .eq(LotteryDrawRecord::getActivityId, activityId));
        assertThat(records).isEqualTo(1);
    }

    private long createActivity(String admin) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String body = mockMvc.perform(post("/api/admin/lottery/activities")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryActivityRequest("抽奖" + System.nanoTime(),
                                        now.minusHours(1), now.plusHours(1)))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void addPrize(String admin, long activityId, String name, int stock) throws Exception {
        mockMvc.perform(post("/api/admin/lottery/activities/" + activityId + "/prizes")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LotteryPrizeRequest(name, "", 1, 1, stock))))
                .andExpect(jsonPath("$.code").value(200));
    }

    private String draw(String customer, long activityId) throws Exception {
        return mockMvc.perform(post("/api/lottery/" + activityId + "/draw")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
    }
}
