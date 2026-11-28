package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.LotteryClaimAddressRequest;
import com.tsuk1.itsmygo.dto.LotteryClaimView;
import com.tsuk1.itsmygo.dto.LotteryDrawResponse;
import com.tsuk1.itsmygo.dto.LotteryMyRecordView;
import com.tsuk1.itsmygo.dto.LotteryPrizePublicView;
import com.tsuk1.itsmygo.dto.LotteryPrizeRecordView;
import com.tsuk1.itsmygo.dto.PageResponse;

import java.util.List;

/** 顾客端抽奖：奖池、抽一次、今天的记录、我的奖品与实物收货信息 */
public interface LotteryService {

    List<LotteryPrizePublicView> prizes(Long activityId);

    LotteryDrawResponse draw(Long userId, Long activityId);

    /** 今天在该活动的抽奖记录 */
    LotteryMyRecordView myRecord(Long userId, Long activityId);

    /** 我的奖品（只含中奖记录，分页） */
    PageResponse<LotteryPrizeRecordView> myPrizes(Long userId, int page, int size);

    /** 实物奖品填写/修改收货信息 */
    LotteryClaimView saveClaimAddress(Long userId, Long claimId, LotteryClaimAddressRequest request);
}
