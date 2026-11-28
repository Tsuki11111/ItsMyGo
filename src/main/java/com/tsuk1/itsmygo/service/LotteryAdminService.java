package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.LotteryClaimView;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeView;
import com.tsuk1.itsmygo.dto.LotteryRecordAdminView;
import com.tsuk1.itsmygo.dto.PageResponse;

import java.util.List;

/** 抽奖类型专属配置：奖项的增查、中奖记录、实物奖品发货；活动本身由 ActivityAdminService 管理 */
public interface LotteryAdminService {

    LotteryPrizeView addPrize(Long activityId, LotteryPrizeRequest request);

    List<LotteryPrizeView> listPrizes(Long activityId);

    /** 某个活动的中奖记录（只含 WIN） */
    PageResponse<LotteryRecordAdminView> listRecords(Long activityId, int page, int size);

    /** 实物奖品填运单号发货 */
    LotteryClaimView shipClaim(Long claimId, String trackingNo);
}
