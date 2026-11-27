package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.common.lottery.LotteryPrizePoolService;
import com.tsuk1.itsmygo.common.lottery.LotteryRedisStore;
import com.tsuk1.itsmygo.dto.LotteryActivityPublicView;
import com.tsuk1.itsmygo.dto.LotteryDrawResponse;
import com.tsuk1.itsmygo.dto.LotteryMyRecordView;
import com.tsuk1.itsmygo.dto.LotteryPrizePublicView;
import com.tsuk1.itsmygo.entity.LotteryActivity;
import com.tsuk1.itsmygo.entity.LotteryActivityStatus;
import com.tsuk1.itsmygo.entity.LotteryDrawRecord;
import com.tsuk1.itsmygo.entity.LotteryDrawResult;
import com.tsuk1.itsmygo.entity.LotteryPrize;
import com.tsuk1.itsmygo.mapper.LotteryActivityMapper;
import com.tsuk1.itsmygo.mapper.LotteryDrawRecordMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeMapper;
import com.tsuk1.itsmygo.service.LotteryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 顾客抽奖（每人每活动 1 次，多奖项等概率）。
 *
 * <p>一致性策略：Redis Set 只做快速判断和实时库存，MySQL 的
 * {@code uk_activity_user} 才是「一人一次」的最终兜底。Redis 与 MySQL 不一致时，
 * 以 MySQL 为准，并把抽走的奖券放回奖池。</p>
 */
@Slf4j
@Service
public class LotteryServiceImpl implements LotteryService {

    private final LotteryActivityMapper activityMapper;
    private final LotteryPrizeMapper prizeMapper;
    private final LotteryDrawRecordMapper drawRecordMapper;
    private final LotteryPrizePoolService prizePoolService;
    private final LotteryRedisStore redisStore;

    public LotteryServiceImpl(LotteryActivityMapper activityMapper,
                              LotteryPrizeMapper prizeMapper,
                              LotteryDrawRecordMapper drawRecordMapper,
                              LotteryPrizePoolService prizePoolService,
                              LotteryRedisStore redisStore) {
        this.activityMapper = activityMapper;
        this.prizeMapper = prizeMapper;
        this.drawRecordMapper = drawRecordMapper;
        this.prizePoolService = prizePoolService;
        this.redisStore = redisStore;
    }

    @Override
    public LotteryActivityPublicView activeActivity() {
        // 只取最近一个「进行中」的活动；活动多起来再升级成列表接口
        LotteryActivity activity = activityMapper.selectOne(Wrappers.<LotteryActivity>lambdaQuery()
                .eq(LotteryActivity::getStatus, LotteryActivityStatus.ACTIVE.code())
                .orderByDesc(LotteryActivity::getId)
                .last("LIMIT 1"));
        if (activity == null) {
            return null;
        }
        LotteryActivityStatus status = LotteryActivityStatus.fromCode(activity.getStatus());
        String statusLabel = status == LotteryActivityStatus.ACTIVE ? "进行中" : "已结束";
        return new LotteryActivityPublicView(
                activity.getId(),
                activity.getName(),
                activity.getStatus(),
                statusLabel,
                activity.getStartAt(),
                activity.getEndAt());
    }

    @Override
    public List<LotteryPrizePublicView> prizes(Long activityId) {
        LotteryActivity activity = requireActivity(activityId);
        prizePoolService.ensureInitialized(activity);

        List<LotteryPrize> prizes = prizeMapper.selectList(Wrappers.<LotteryPrize>lambdaQuery()
                .eq(LotteryPrize::getActivityId, activityId)
                .orderByAsc(LotteryPrize::getLevel)
                .orderByAsc(LotteryPrize::getId));

        List<LotteryPrizePublicView> views = new ArrayList<>(prizes.size());
        for (LotteryPrize prize : prizes) {
            long remaining = prizePoolService.remainingStock(activity, prize);
            views.add(new LotteryPrizePublicView(
                    prize.getId(), prize.getName(), prize.getLevel(), remaining));
        }
        return views;
    }

    @Override
    public LotteryDrawResponse draw(Long userId, Long activityId) {
        LotteryActivity activity = requireActivity(activityId);
        validateActive(activity);

        // 快速路径：Redis 说已抽过，就直接查 MySQL 返回已有记录
        if (redisStore.isParticipant(activityId, userId)) {
            LotteryDrawRecord existing = findRecord(activityId, userId);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        prizePoolService.ensureInitialized(activity);

        // 在还有库存的奖项里等概率选一个；选中的奖池刚好被抢空就换下一个
        List<LotteryPrize> candidates = new ArrayList<>();
        List<LotteryPrize> prizes = prizeMapper.selectList(Wrappers.<LotteryPrize>lambdaQuery()
                .eq(LotteryPrize::getActivityId, activityId));
        for (LotteryPrize prize : prizes) {
            if (prizePoolService.remainingStock(activity, prize) > 0) {
                candidates.add(prize);
            }
        }

        LotteryPrize wonPrize = null;
        String prizeCode = null;
        while (!candidates.isEmpty()) {
            // ThreadLocalRandom 是线程安全的随机数，多个并发抽奖请求不会共用同一个 Random 实例
            int index = ThreadLocalRandom.current().nextInt(candidates.size());
            LotteryPrize candidate = candidates.remove(index);
            String code = redisStore.takePrize(activityId, candidate.getId());
            if (code != null) {
                wonPrize = candidate;
                prizeCode = code;
                break;
            }
        }

        LotteryDrawRecord record = new LotteryDrawRecord();
        record.setActivityId(activityId);
        record.setUserId(userId);
        record.setPrizeId(wonPrize == null ? null : wonPrize.getId());
        record.setPrizeCode(prizeCode);
        record.setResult(wonPrize == null
                ? LotteryDrawResult.THANKS.name() : LotteryDrawResult.WIN.name());

        try {
            drawRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 唯一键冲突：可能是同一用户重复抽（Redis 丢了资格），也可能是奖券码冲突
            LotteryDrawRecord existing = findRecord(activityId, userId);
            if (existing != null) {
                // 用户已有记录：本次抽到的奖券退回池子，返回已有结果
                returnPrizeQuietly(activity, wonPrize, prizeCode);
                markParticipantQuietly(activity, userId);
                return toResponse(existing);
            }
            // 不是同一用户的重复：说明奖券码已被别人用过，不能再放回池子
            throw new BizException(ResultCode.CONFLICT, "抽奖冲突，请重试");
        } catch (RuntimeException e) {
            // MySQL 写入失败：奖券放回，让用户可以重新抽
            returnPrizeQuietly(activity, wonPrize, prizeCode);
            throw e;
        }

        // MySQL 已经落库；Redis 只是缓存，写失败只记日志，不影响结果
        cacheSuccessQuietly(activity, userId, wonPrize);

        return toResponse(record);
    }

    @Override
    public LotteryMyRecordView myRecord(Long userId, Long activityId) {
        requireActivity(activityId);
        LotteryDrawRecord record = findRecord(activityId, userId);
        if (record == null) {
            return new LotteryMyRecordView(false, null, null, null, null, null);
        }
        LotteryPrize prize = record.getPrizeId() == null
                ? null : prizeMapper.selectById(record.getPrizeId());
        return new LotteryMyRecordView(
                true,
                record.getResult(),
                record.getPrizeId(),
                prize == null ? null : prize.getName(),
                record.getPrizeCode(),
                record.getCreatedAt());
    }

    private LotteryActivity requireActivity(Long activityId) {
        LotteryActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private void validateActive(LotteryActivity activity) {
        if (activity.getStatus() != LotteryActivityStatus.ACTIVE.code()) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已结束");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartAt())) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动尚未开始");
        }
        if (now.isAfter(activity.getEndAt())) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已结束");
        }
    }

    private LotteryDrawRecord findRecord(Long activityId, Long userId) {
        return drawRecordMapper.selectOne(Wrappers.<LotteryDrawRecord>lambdaQuery()
                .eq(LotteryDrawRecord::getActivityId, activityId)
                .eq(LotteryDrawRecord::getUserId, userId));
    }

    private void returnPrizeQuietly(LotteryActivity activity, LotteryPrize prize, String prizeCode) {
        if (prize == null || prizeCode == null) {
            return;
        }
        try {
            redisStore.returnPrize(activity.getId(), prize.getId(), prizeCode,
                    redisStore.ttlSeconds(activity.getEndAt()));
        } catch (RuntimeException e) {
            log.error("退回奖券失败，可能少一张库存: activityId={} prizeId={} code={} err={}",
                    activity.getId(), prize.getId(), prizeCode, e.getMessage());
        }
    }

    private void markParticipantQuietly(LotteryActivity activity, Long userId) {
        try {
            redisStore.markParticipant(activity.getId(), userId,
                    redisStore.ttlSeconds(activity.getEndAt()));
        } catch (RuntimeException e) {
            log.warn("写抽奖资格缓存失败: activityId={} userId={} err={}",
                    activity.getId(), userId, e.getMessage());
        }
    }

    private void cacheSuccessQuietly(LotteryActivity activity, Long userId, LotteryPrize wonPrize) {
        markParticipantQuietly(activity, userId);
        if (wonPrize == null) {
            return;
        }
        try {
            redisStore.markWinner(activity.getId(), userId,
                    redisStore.ttlSeconds(activity.getEndAt()));
        } catch (RuntimeException e) {
            log.warn("写中奖名单缓存失败: activityId={} userId={} err={}",
                    activity.getId(), userId, e.getMessage());
        }
    }

    private LotteryDrawResponse toResponse(LotteryDrawRecord record) {
        if (LotteryDrawResult.THANKS.name().equals(record.getResult())) {
            return new LotteryDrawResponse("THANKS", null, null, null, "谢谢参与");
        }
        LotteryPrize prize = prizeMapper.selectById(record.getPrizeId());
        String prizeName = prize == null ? "" : prize.getName();
        return new LotteryDrawResponse("WIN", record.getPrizeId(), prizeName,
                record.getPrizeCode(), "恭喜中奖");
    }
}
