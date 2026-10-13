package com.tsuk1.itsmygo.security;

import com.tsuk1.itsmygo.common.Roles;

/** 当前登录用户，由 JWT 解析得到，挂在 SecurityContext 的 principal 上 */
public record CurrentUser(Long id, String username, String role) {

    public boolean isAdmin() {
        return Roles.ADMIN.equals(role);
    }
}
