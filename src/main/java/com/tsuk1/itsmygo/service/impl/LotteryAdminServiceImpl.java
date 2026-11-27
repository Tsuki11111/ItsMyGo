package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.common.lottery.LotteryPrizePoolService;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeView;
import com.tsuk1.itsmygo.entity.Activity;
import com.tsuk1.itsmygo.entity.ActivityStatus;
import com.tsuk1.itsmygo.entity.ActivityType;
import com.tsuk1.itsmygo.entity.LotteryPrize;
import com.tsuk1.itsmygo.mapper.ActivityMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeMapper;
import com.tsuk1.itsmygo.service.LotteryAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 抽奖奖项管理；所有入口都会校验活动类型是 LOTTERY */
@Slf4j
@Service
public class LotteryAdminServiceImpl implements LotteryAdminService {

    private final ActivityMapper activityMapper;
    private final LotteryPrizeMapper prizeMapper;
    private final LotteryPrizePoolService prizePoolService;

    public LotteryAdminServiceImpl(ActivityMapper activityMapper,
                                   LotteryPrizeMapper prizeMapper,
                                   LotteryPrizePoolService prizePoolService) {
        this.activityMapper = activityMapper;
        this.prizeMapper = prizeMapper;
        this.prizePoolService = prizePoolService;
    }

    @Override
    public LotteryPrizeView addPrize(Long activityId, LotteryPrizeRequest request) {
        Activity activity = requireLotteryActivity(activityId);
        if (activity.getStatus() == ActivityStatus.ENDED.code()) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已结束，不能添加奖项");
        }

        LotteryPrize prize = new LotteryPrize();
        prize.setActivityId(activityId);
        prize.setName(request.name());
        prize.setImage(request.image() == null ? "" : request.image());
        prize.setLevel(request.level());
        prize.setWeight(request.weight());
        prize.setStock(request.stock());
        prizeMapper.insert(prize);

        LotteryPrize saved = prizeMapper.selectById(prize.getId());
        return toView(saved, saved.getStock());
    }

    @Override
    public List<LotteryPrizeView> listPrizes(Long activityId) {
        Activity activity = requireLotteryActivity(activityId);
        List<LotteryPrize> prizes = prizeMapper.selectList(Wrappers.<LotteryPrize>lambdaQuery()
                .eq(LotteryPrize::getActivityId, activityId)
                .orderByAsc(LotteryPrize::getLevel)
                .orderByAsc(LotteryPrize::getId));

        List<LotteryPrizeView> views = new ArrayList<>(prizes.size());
        for (LotteryPrize prize : prizes) {
            Integer remaining = null;
            try {
                remaining = (int) prizePoolService.remainingStock(activity, prize);
            } catch (RuntimeException e) {
                log.warn("读取抽奖实时库存失败: activityId={} prizeId={} err={}",
                        activityId, prize.getId(), e.getMessage());
            }
            views.add(toView(prize, remaining));
        }
        return views;
    }

    private Activity requireLotteryActivity(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null || !ActivityType.LOTTERY.name().equals(activity.getType())) {
            throw new BizException(ResultCode.NOT_FOUND, "抽奖活动不存在");
        }
        return activity;
    }

    private LotteryPrizeView toView(LotteryPrize prize, Integer remainingStock) {
        return new LotteryPrizeView(
                prize.getId(),
                prize.getActivityId(),
                prize.getName(),
                prize.getImage(),
                prize.getLevel(),
                prize.getWeight(),
                prize.getStock(),
                remainingStock,
                prize.getCreatedAt());
    }
}
