package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest extends ApiTestBase {

    @Autowired
    private AdminUserService adminUserService;

    private long userId(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username)).getId();
    }

    @Test
    void listFiltersAndHidesPassword() throws Exception {
        String admin = adminToken("au" + System.nanoTime());
        String username = "customer" + System.nanoTime();
        customerToken(username);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(admin))
                        .param("keyword", username)
                        .param("role", "CUSTOMER")
                        .param("status", "1"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value(username))
                .andExpect(jsonPath("$.data.records[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.records[0].password").doesNotExist());
    }

    @Test
    void disabledUserLosesAccessAndCanBeEnabled() throws Exception {
        String admin = adminToken("au" + System.nanoTime());
        String username = "disabled" + System.nanoTime();
        String customer = customerToken(username);
        long targetId = userId(username);

        mockMvc.perform(post("/api/admin/users/" + targetId + "/disable")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/admin/users/" + targetId + "/enable")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void cannotDisableSelfOrMissingUser() throws Exception {
        String adminName = "au" + System.nanoTime();
        String admin = adminToken(adminName);
        long adminId = userId(adminName);

        mockMvc.perform(post("/api/admin/users/" + adminId + "/disable")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能禁用当前登录账号"));

        mockMvc.perform(post("/api/admin/users/99999999/disable")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void cannotDisableLastActiveAdmin() throws Exception {
        String adminName = "au" + System.nanoTime();
        adminToken(adminName);
        long adminId = userId(adminName);

        // 用例自隔离：库是共享测试库，可能已有别的活跃管理员；
        // 在本次事务内把其余活跃管理员临时停用（事务回滚后恢复），才能稳定验证「最后一个管理员」规则
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getRole, Roles.ADMIN)
                .eq(User::getStatus, 1)
                .ne(User::getId, adminId)
                .set(User::getStatus, 0));

        assertThatThrownBy(() -> adminUserService.disable(99999999L, adminId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("最后一个管理员");
    }
}
