package com.tsuk1.itsmygo.dto;

import com.tsuk1.itsmygo.entity.User;

import java.time.LocalDateTime;

/** 管理端用户列表项；**不包含密码**。 */
public record AdminUserView(
        Long id,
        String username,
        String nickname,
        String phone,
        String avatar,
        Integer status,
        String role,
        Long merchantId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AdminUserView from(User user) {
        return new AdminUserView(
                user.getId(),
                nullToEmpty(user.getUsername()),
                nullToEmpty(user.getNickname()),
                nullToEmpty(user.getPhone()),
                nullToEmpty(user.getAvatar()),
                user.getStatus(),
                nullToEmpty(user.getRole()),
                user.getMerchantId(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
