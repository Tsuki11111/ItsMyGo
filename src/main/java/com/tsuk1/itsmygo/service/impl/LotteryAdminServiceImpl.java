package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.common.lottery.LotteryPrizePoolService;
import com.tsuk1.itsmygo.dto.LotteryActivityRequest;
import com.tsuk1.itsmygo.dto.LotteryActivityView;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeView;
import com.tsuk1.itsmygo.entity.LotteryActivity;
import com.tsuk1.itsmygo.entity.LotteryActivityStatus;
import com.tsuk1.itsmygo.entity.LotteryPrize;
import com.tsuk1.itsmygo.mapper.LotteryActivityMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeMapper;
import com.tsuk1.itsmygo.service.LotteryAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 管理端抽奖配置：活动 / 奖项的增查与状态修改 */
@Slf4j
@Service
public class LotteryAdminServiceImpl implements LotteryAdminService {

    private final LotteryActivityMapper activityMapper;
    private final LotteryPrizeMapper prizeMapper;
    private final LotteryPrizePoolService prizePoolService;

    public LotteryAdminServiceImpl(LotteryActivityMapper activityMapper,
                                   LotteryPrizeMapper prizeMapper,
                                   LotteryPrizePoolService prizePoolService) {
        this.activityMapper = activityMapper;
        this.prizeMapper = prizeMapper;
        this.prizePoolService = prizePoolService;
    }

    @Override
    public LotteryActivityView createActivity(LotteryActivityRequest request) {
        // DTO 的 @NotNull 只保证时间不为空；先后顺序必须在这里校验
        if (!request.startAt().isBefore(request.endAt())) {
            throw new BizException(ResultCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }

        LotteryActivity activity = new LotteryActivity();
        activity.setName(request.name());
        activity.setStatus(LotteryActivityStatus.ACTIVE.code());
        activity.setStartAt(request.startAt());
        activity.setEndAt(request.endAt());
        activityMapper.insert(activity);

        // created_at / updated_at 是数据库默认值，insert 不会回填；重查一次保证响应里的时间准确
        LotteryActivity saved = activityMapper.selectById(activity.getId());
        return toView(saved);
    }

    @Override
    public List<LotteryActivityView> listActivities() {
        List<LotteryActivity> activities = activityMapper.selectList(
                Wrappers.<LotteryActivity>lambdaQuery().orderByDesc(LotteryActivity::getId));
        List<LotteryActivityView> views = new ArrayList<>(activities.size());
        for (LotteryActivity activity : activities) {
            views.add(toView(activity));
        }
        return views;
    }

    @Override
    public LotteryActivityView updateStatus(Long activityId, int status) {
        LotteryActivity activity = requireActivity(activityId);
        LotteryActivityStatus target = LotteryActivityStatus.fromCode(status);
        // 只更新 status，避免把实体里旧的 updated_at 写回去、覆盖数据库的自动更新时间
        activityMapper.update(null, Wrappers.<LotteryActivity>lambdaUpdate()
                .eq(LotteryActivity::getId, activityId)
                .set(LotteryActivity::getStatus, target.code()));
        activity.setStatus(target.code());
        return toView(activity);
    }

    @Override
    public LotteryPrizeView addPrize(Long activityId, LotteryPrizeRequest request) {
        LotteryActivity activity = requireActivity(activityId);
        if (activity.getStatus() == LotteryActivityStatus.ENDED.code()) {
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
        // 新奖项还没被抽过，实时剩余就是初始库存；奖池会在第一次抽奖/列表时惰性初始化
        return toView(saved, saved.getStock());
    }

    @Override
    public List<LotteryPrizeView> listPrizes(Long activityId) {
        LotteryActivity activity = requireActivity(activityId);
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
                // 管理端列表不能因为 Redis 故障整个打不开；前端看到 null 会显示 “-”
                log.warn("读取抽奖实时库存失败: activityId={} prizeId={} err={}",
                        activityId, prize.getId(), e.getMessage());
            }
            views.add(toView(prize, remaining));
        }
        return views;
    }

    private LotteryActivity requireActivity(Long activityId) {
        LotteryActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private LotteryActivityView toView(LotteryActivity activity) {
        LotteryActivityStatus status = LotteryActivityStatus.fromCode(activity.getStatus());
        String statusLabel = status == LotteryActivityStatus.ACTIVE ? "进行中" : "已结束";
        return new LotteryActivityView(
                activity.getId(),
                activity.getName(),
                activity.getStatus(),
                statusLabel,
                activity.getStartAt(),
                activity.getEndAt(),
                activity.getCreatedAt());
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
