package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 管理端活动视图 */
public record ActivityView(
        Long id,
        String type,
        String typeLabel,
        String name,
        String description,
        String primaryColor,
        String coverImage,
        Integer status,
        String statusLabel,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime createdAt
) {
}
