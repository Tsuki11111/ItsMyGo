package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.LotteryActivityRequest;
import com.tsuk1.itsmygo.dto.LotteryActivityView;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeView;

import java.util.List;

/** 管理端抽奖配置：只做活动 / 奖项的增查与状态修改，不涉及抽奖逻辑 */
public interface LotteryAdminService {

    LotteryActivityView createActivity(LotteryActivityRequest request);

    List<LotteryActivityView> listActivities();

    LotteryActivityView updateStatus(Long activityId, int status);

    LotteryPrizeView addPrize(Long activityId, LotteryPrizeRequest request);

    List<LotteryPrizeView> listPrizes(Long activityId);
}
