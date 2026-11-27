package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeView;

import java.util.List;

/** 抽奖类型专属配置：奖项的增查；活动本身由 ActivityAdminService 管理 */
public interface LotteryAdminService {

    LotteryPrizeView addPrize(Long activityId, LotteryPrizeRequest request);

    List<LotteryPrizeView> listPrizes(Long activityId);
}
