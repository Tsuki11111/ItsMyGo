package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/**
 * 管理端奖项视图。
 *
 * <p>{@code stock} 是初始库存；{@code remainingStock} 是 Redis 奖池的实时剩余，
 * Redis 不可用时为 null（前端显示 “-”）。</p>
 */
public record LotteryPrizeView(
        Long id,
        Long activityId,
        String name,
        String image,
        Integer level,
        Integer weight,
        Integer stock,
        Integer remainingStock,
        LocalDateTime createdAt
) {
}
