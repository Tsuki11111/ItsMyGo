package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.ChangePasswordRequest;
import com.tsuk1.itsmygo.dto.LoginRequest;
import com.tsuk1.itsmygo.dto.UpdateProfileRequest;
import com.tsuk1.itsmygo.service.StorageService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户中心：个人资料、改密码、头像。
 *
 * 头像用 {@code @MockitoBean} 替掉 StorageService —— 这里验的是「上传后落库并回到资料里」，
 * 真正的 MinIO 上传在 StorageServiceTest 里覆盖（CI 上会按 SKIP_MINIO_TESTS 跳过）。
 */
class UserProfileControllerTest extends ApiTestBase {

    @MockitoBean
    private StorageService storageService;

    @Test
    void profileRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized());
    }

    /** 新用户的昵称回退成用户名，手机号和头像都是空串 */
    @Test
    void newUserProfileHasFallbackNickname() throws Exception {
        String username = "p" + System.nanoTime();
        String token = customerToken(username);

        mockMvc.perform(get("/api/user/profile").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.nickname").value(username))
                .andExpect(jsonPath("$.data.phone").value(""))
                .andExpect(jsonPath("$.data.avatar").value(""));
    }

    @Test
    void canUpdateNicknameAndPhone() throws Exception {
        String token = customerToken("p" + System.nanoTime());

        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest("新昵称", "13800001234"))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.phone").value("13800001234"));
    }

    @Test
    void blankNicknameIsRejected() throws Exception {
        String token = customerToken("p" + System.nanoTime());

        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("  ", ""))))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 手机号选填：空串放行，填了就必须是 11 位手机号 */
    @Test
    void phoneIsOptionalButMustBeValidWhenFilled() throws Exception {
        String token = customerToken("p" + System.nanoTime());

        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest("张三", "12345"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请输入正确的 11 位手机号"));

        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest("张三", ""))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.phone").value(""));
    }

    /** 旧密码错返回 400，**不能是 401** —— 401 会让前端当成登录失效直接登出 */
    @Test
    void changingPasswordRequiresCorrectOldPassword() throws Exception {
        String token = customerToken("pw" + System.nanoTime());

        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("wrongpass", "newsecret123"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("旧密码不正确"));
    }

    @Test
    void newPasswordCannotEqualOld() throws Exception {
        String token = customerToken("pw" + System.nanoTime());

        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("secret123", "secret123"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("新密码不能和旧密码相同"));
    }

    /** 改完密码：旧密码登录失败、新密码成功 */
    @Test
    void afterPasswordChangeOldPasswordStopsWorking() throws Exception {
        String username = "pw" + System.nanoTime();
        String token = customerToken(username);

        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("secret123", "newsecret456"))))
                .andExpect(jsonPath("$.code").value(200));

        resetRateLimits();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, "secret123"))))
                .andExpect(jsonPath("$.code").value(401));

        resetRateLimits();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, "newsecret456"))))
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 上传成功后返回整份资料，并且真的落库了 */
    @Test
    void uploadingAvatarStoresUrlAndReturnsProfile() throws Exception {
        String token = customerToken("av" + System.nanoTime());
        String url = "http://localhost:9000/its-my-go-product-images/avatars/x.png";
        Mockito.when(storageService.uploadAvatar(Mockito.any())).thenReturn(url);

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/user/avatar").file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.avatar").value(url));

        mockMvc.perform(get("/api/user/profile").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.avatar").value(url));

        Mockito.verify(storageService).uploadAvatar(Mockito.any());
    }
}
