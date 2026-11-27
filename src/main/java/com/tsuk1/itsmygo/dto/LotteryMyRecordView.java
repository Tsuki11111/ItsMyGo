package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 顾客在某活动下的抽奖记录；没抽过时只有 drawn=false */
public record LotteryMyRecordView(
        boolean drawn,
        String result,
        Long prizeId,
        String prizeName,
        String prizeCode,
        LocalDateTime drawnAt
) {
}
