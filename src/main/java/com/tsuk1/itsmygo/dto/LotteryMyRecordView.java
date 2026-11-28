package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 顾客在某个抽奖活动下**今天**的记录；没抽过时只有 drawn=false */
public record LotteryMyRecordView(
        boolean drawn,
        String result,
        Long prizeId,
        String prizeName,
        String prizeType,
        String prizeCode,
        LocalDateTime drawnAt
) {
}
