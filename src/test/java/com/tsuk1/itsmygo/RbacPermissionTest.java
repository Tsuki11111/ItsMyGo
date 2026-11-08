package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.entity.AdminUserRole;
import com.tsuk1.itsmygo.entity.Role;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AdminUserRoleMapper;
import com.tsuk1.itsmygo.mapper.RoleMapper;
import com.tsuk1.itsmygo.service.RbacService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** RBAC 打开时：管理端子角色按权限码拦截；无角色行的历史管理员按 SUPER_ADMIN 兼容。 */
@SpringBootTest(properties = "itsmygo.rbac.enabled=true")
class RbacPermissionTest extends ApiTestBase {

    @Autowired
    private AdminUserRoleMapper adminUserRoleMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RbacService rbacService;

    private long userIdOf(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        return user.getId();
    }

    private void assignRole(String username, String roleCode) {
        long userId = userIdOf(username);
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getCode, roleCode));
        AdminUserRole link = new AdminUserRole();
        link.setUserId(userId);
        link.setRoleId(role.getId());
        adminUserRoleMapper.insert(link);
        rbacService.evict(userId);
    }

    @Test
    void customerServiceCanViewOrdersButCannotShipOrRefund() throws Exception {
        String username = "cs" + System.nanoTime();
        String token = adminToken(username);
        assignRole(username, "CUSTOMER_SERVICE");

        mockMvc.perform(get("/api/admin/orders").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/admin/after-sales").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/auth/permissions").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.roles[0]").value("CUSTOMER_SERVICE"))
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.hasItem("admin:order:view")))
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("admin:order:ship"))));

        // 无代发货 / 退款权限：方法级权限直接 403
        mockMvc.perform(post("/api/admin/orders/1/ship")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"trackingNo\":\"SF1\"}"))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/api/admin/orders/1/refund/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void financeCanRefundButCannotShip() throws Exception {
        String username = "fin" + System.nanoTime();
        String token = adminToken(username);
        assignRole(username, "FINANCE");

        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));
        // 通过权限校验后由业务层报订单不存在（404），不是 403
        mockMvc.perform(post("/api/admin/orders/1/refund/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(post("/api/admin/orders/1/ship")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"trackingNo\":\"SF1\"}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void legacyAdminWithoutRoleRowsFallsBackToSuperAdmin() throws Exception {
        String token = adminToken("legacy" + System.nanoTime());
        mockMvc.perform(get("/api/admin/orders").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));
        // 全部权限码 → 通过权限校验后由业务层报 404
        mockMvc.perform(post("/api/admin/orders/1/ship")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"trackingNo\":\"SF1\"}"))
                .andExpect(jsonPath("$.code").value(404));
    }
}
