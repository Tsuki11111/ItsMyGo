package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/**
 * 管理端活动视图。
 *
 * <p>{@code statusLabel} 由后端给中文，避免前端再维护一份状态文案（同 D36 的约定）。</p>
 */
public record LotteryActivityView(
        Long id,
        String name,
        Integer status,
        String statusLabel,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime createdAt
) {
}
