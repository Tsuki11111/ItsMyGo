package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 顾客端活动视图（活动中心 / 抽奖页） */
public record ActivityPublicView(
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
        LocalDateTime endAt
) {
}
