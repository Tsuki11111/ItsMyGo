package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 管理端中奖记录（只含中奖，不含谢谢参与） */
public record LotteryRecordAdminView(
        Long id,
        Long activityId,
        Long userId,
        String username,
        Long prizeId,
        String prizeName,
        String prizeType,
        String prizeCode,
        LocalDateTime drawnAt,
        LotteryClaimView claim
) {
}
