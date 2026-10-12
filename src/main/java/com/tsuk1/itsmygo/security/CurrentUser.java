package com.tsuk1.itsmygo.security;

/** 当前登录用户，由 JWT 解析得到，挂在 SecurityContext 的 principal 上 */
public record CurrentUser(Long id, String username) {
}
