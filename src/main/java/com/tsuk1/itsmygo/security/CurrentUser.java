package com.tsuk1.itsmygo.security;

import com.tsuk1.itsmygo.common.Roles;

/** 当前登录用户，由 JWT 解析 + 数据库授权快照组装，挂在 SecurityContext 的 principal 上 */
public record CurrentUser(Long id, String username, String role, Long merchantId) {

    /** 兼容只解析 JWT 的场景（真正的授权以 AuthIdentityService 为准） */
    public CurrentUser(Long id, String username, String role) {
        this(id, username, role, null);
    }

    public boolean isAdmin() {
        return Roles.ADMIN.equals(role);
    }

    public boolean isMerchant() {
        return Roles.MERCHANT.equals(role) && merchantId != null;
    }
}
