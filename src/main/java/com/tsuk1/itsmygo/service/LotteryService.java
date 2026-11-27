package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.LotteryDrawResponse;
import com.tsuk1.itsmygo.dto.LotteryMyRecordView;
import com.tsuk1.itsmygo.dto.LotteryPrizePublicView;

import java.util.List;

/** 顾客端抽奖：奖池、抽一次、查自己的记录 */
public interface LotteryService {

    List<LotteryPrizePublicView> prizes(Long activityId);

    LotteryDrawResponse draw(Long userId, Long activityId);

    LotteryMyRecordView myRecord(Long userId, Long activityId);
}
