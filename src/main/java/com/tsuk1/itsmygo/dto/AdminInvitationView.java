package com.tsuk1.itsmygo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/** 管理员邀请视图；token 只在创建成功时返回一次，列表永远不返回。 */
public record AdminInvitationView(
        Long id,
        Long targetUserId,
        String targetUsername,
        String targetNickname,
        Long invitedBy,
        String inviterUsername,
        Long approvedBy,
        String approverUsername,
        String status,
        LocalDateTime expiresAt,
        LocalDateTime approvedAt,
        LocalDateTime acceptedAt,
        LocalDateTime createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String token) {
}
