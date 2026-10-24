package com.tsuk1.itsmygo.dto;

import com.tsuk1.itsmygo.entity.AdminAuditLog;

import java.time.LocalDateTime;

/** 管理端审计列表项。 */
public record AdminAuditView(
        Long id,
        Long actorUserId,
        String actorUsername,
        String action,
        String targetType,
        Long targetId,
        String summary,
        String result,
        String errorMessage,
        String ip,
        String userAgent,
        String traceId,
        String detail,
        LocalDateTime createdAt) {

    public static AdminAuditView from(AdminAuditLog log) {
        return new AdminAuditView(
                log.getId(),
                log.getActorUserId(),
                nullToEmpty(log.getActorUsername()),
                nullToEmpty(log.getAction()),
                nullToEmpty(log.getTargetType()),
                log.getTargetId(),
                nullToEmpty(log.getSummary()),
                nullToEmpty(log.getResult()),
                nullToEmpty(log.getErrorMessage()),
                nullToEmpty(log.getIp()),
                nullToEmpty(log.getUserAgent()),
                nullToEmpty(log.getTraceId()),
                nullToEmpty(log.getDetail()),
                log.getCreatedAt());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
