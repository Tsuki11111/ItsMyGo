package com.tsuk1.itsmygo.dto;

/** 用户中心展示的个人资料。**不含密码**，也不含 status —— 那是管理端才关心的 */
public record ProfileView(Long id, String username, String nickname, String phone,
                          String avatar, String role) {
}
