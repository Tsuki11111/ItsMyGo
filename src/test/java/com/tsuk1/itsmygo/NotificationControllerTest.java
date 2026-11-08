package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.MerchantRequest;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 站内通知：铃铛接口 + 业务事件写入通知。 */
class NotificationControllerTest extends ApiTestBase {

    @Autowired
    private NotificationService notificationService;

    private long userIdOf(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }

    @Test
    void notificationListAndReadFlow() throws Exception {
        String username = "n" + System.nanoTime();
        String token = customerToken(username);
        long userId = userIdOf(username);
        String bizKey = "test-biz-" + System.nanoTime();

        // 未读 1 条；同一 bizKey 重复写入保持幂等
        notificationService.notifyUser(userId, "TEST", "测试通知", "通知内容", "/x", bizKey);
        notificationService.notifyUser(userId, "TEST", "重复通知", "应被幂等忽略", "/x", bizKey);
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].title").value("测试通知"))
                .andExpect(jsonPath("$.data.items[0].read").value(false));

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data").value(1));

        // 全部已读
        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data").value(1));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    void adminsReceiveMerchantApplyNotification() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        mockMvc.perform(post("/api/merchant-apply")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MerchantRequest(
                                "通知测试店" + System.nanoTime(), "", "测试店铺", "店主", "13800000000"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].type", hasItem("MERCHANT_APPLY")))
                .andExpect(jsonPath("$.data.unreadCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void platformStoreMerchantNotificationGoesToAdmins() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        notificationService.notifyMerchant(1L, "TEST_PLATFORM", "平台店通知", "平台自营店事件", "/x",
                "test-platform-" + System.nanoTime());
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.items[*].type", hasItem("TEST_PLATFORM")));
    }
}
