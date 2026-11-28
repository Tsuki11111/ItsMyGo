package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/**
 * 我的奖品：一次中奖一条（不含谢谢参与）。
 *
 * <p>券类奖品带 {@code userCouponId} / {@code couponStatus}，实物奖品带 {@code claim}。</p>
 */
public record LotteryPrizeRecordView(
        Long id,
        Long activityId,
        String activityName,
        Long prizeId,
        String prizeName,
        String prizeImage,
        String prizeType,
        String prizeCode,
        LocalDateTime drawnAt,
        LotteryClaimView claim,
        Long userCouponId,
        String couponStatus,
        LocalDateTime couponValidTo
) {
}
