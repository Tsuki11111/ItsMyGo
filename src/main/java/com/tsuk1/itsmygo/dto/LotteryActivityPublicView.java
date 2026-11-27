package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 顾客端抽奖活动：只暴露展示需要的字段，不含库存等内部配置 */
public record LotteryActivityPublicView(
        Long id,
        String name,
        Integer status,
        String statusLabel,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
