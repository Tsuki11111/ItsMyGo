package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 实物奖品领取单视图（顾客端与管理端共用） */
public record LotteryClaimView(
        Long id,
        String status,
        String statusLabel,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String trackingNo,
        LocalDateTime shippedAt
) {
}
