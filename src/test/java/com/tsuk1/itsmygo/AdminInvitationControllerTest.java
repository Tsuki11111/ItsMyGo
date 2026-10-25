package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.AdminInvitationCreateRequest;
import com.tsuk1.itsmygo.dto.AdminInvitationAcceptRequest;
import com.tsuk1.itsmygo.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 管理员邀请：发起 → 另一位管理员批准 → 被邀请人接受；发起人不能自批。 */
class AdminInvitationControllerTest extends ApiTestBase {

    private JsonNode createInvitation(String adminToken, String username) throws Exception {
        String body = mockMvc.perform(post("/api/admin/invitations")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminInvitationCreateRequest(username))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    @Test
    void invitationNeedsAnotherAdminAndThenInviteeAcceptance() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminA = adminToken("inva" + suffix);
        String adminB = adminToken("invb" + suffix);
        String customerName = "invc" + suffix;
        String customer = customerToken(customerName);

        // 普通顾客不能访问管理端邀请接口
        mockMvc.perform(get("/api/admin/invitations")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());

        JsonNode invitation = createInvitation(adminA, customerName);
        long invitationId = invitation.path("id").asLong();
        String token = invitation.path("token").asText();
        String status = invitation.path("status").asText();
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("PENDING_APPROVAL");
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();

        // 发起人自己不能批准
        mockMvc.perform(post("/api/admin/invitations/" + invitationId + "/approve")
                        .header("Authorization", bearer(adminA)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("需要另一位管理员批准"));

        // 另一位管理员批准
        mockMvc.perform(post("/api/admin/invitations/" + invitationId + "/approve")
                        .header("Authorization", bearer(adminB)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 被邀请人接受
        mockMvc.perform(post("/api/user/admin-invitation/accept")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminInvitationAcceptRequest(token))))
                .andExpect(jsonPath("$.code").value(200));

        // token 一次性：重复接受失败
        mockMvc.perform(post("/api/user/admin-invitation/accept")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminInvitationAcceptRequest(token))))
                .andExpect(jsonPath("$.code").value(400));

        // 重新登录后拿到 ADMIN 身份，并能访问管理端
        resetRateLimits();
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(customerName, "secret123"))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String newAdmin = objectMapper.readTree(loginBody).path("data").path("token").asText();
        mockMvc.perform(get("/api/admin/invitations")
                        .header("Authorization", bearer(newAdmin)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/admin/invitations")
                        .header("Authorization", bearer(adminA)))
                .andExpect(jsonPath("$.data[0].status").value("ACCEPTED"));
    }

    @Test
    void cannotInviteNonCustomerOrUnknownUser() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = adminToken("invd" + suffix);
        String anotherAdminName = "inve" + suffix;
        adminToken(anotherAdminName);

        mockMvc.perform(post("/api/admin/invitations")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminInvitationCreateRequest(anotherAdminName))))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/admin/invitations")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminInvitationCreateRequest("no_such_" + suffix))))
                .andExpect(jsonPath("$.code").value(404));
    }
}
