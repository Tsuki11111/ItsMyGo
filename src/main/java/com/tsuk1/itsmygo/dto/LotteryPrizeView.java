package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/**
 * 管理端奖项视图。
 *
 * <p>这里的 {@code stock} 是**初始库存**；实时剩余库存要等 Redis 奖池接上后，
 * 额外返回 {@code remainingStock}（下一步再加）。</p>
 */
public record LotteryPrizeView(
        Long id,
        Long activityId,
        String name,
        Integer level,
        Integer stock,
        LocalDateTime createdAt
) {
}
