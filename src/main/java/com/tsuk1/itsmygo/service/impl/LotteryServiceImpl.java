package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.common.lottery.LotteryPrizePoolService;
import com.tsuk1.itsmygo.common.lottery.LotteryRedisStore;
import com.tsuk1.itsmygo.dto.LotteryClaimAddressRequest;
import com.tsuk1.itsmygo.dto.LotteryClaimView;
import com.tsuk1.itsmygo.dto.LotteryDrawResponse;
import com.tsuk1.itsmygo.dto.LotteryMyRecordView;
import com.tsuk1.itsmygo.dto.LotteryPrizePublicView;
import com.tsuk1.itsmygo.dto.LotteryPrizeRecordView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.Activity;
import com.tsuk1.itsmygo.entity.ActivityStatus;
import com.tsuk1.itsmygo.entity.ActivityType;
import com.tsuk1.itsmygo.entity.LotteryClaimStatus;
import com.tsuk1.itsmygo.entity.LotteryDrawRecord;
import com.tsuk1.itsmygo.entity.LotteryDrawResult;
import com.tsuk1.itsmygo.entity.LotteryPrize;
import com.tsuk1.itsmygo.entity.LotteryPrizeClaim;
import com.tsuk1.itsmygo.entity.UserCoupon;
import com.tsuk1.itsmygo.mapper.ActivityMapper;
import com.tsuk1.itsmygo.mapper.LotteryDrawRecordMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeClaimMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeMapper;
import com.tsuk1.itsmygo.mapper.UserCouponMapper;
import com.tsuk1.itsmygo.service.LotteryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 顾客抽奖（每人每活动每天 1 次，多奖项按 weight 加权）。
 *
 * <p>一致性策略：Redis Set 只做快速判断和实时库存，MySQL 的
 * {@code uk_activity_user_day} 才是「一人一天一次」的最终兜底。Redis 与 MySQL 不一致时，
 * 以 MySQL 为准，并把抽走的奖券放回奖池。</p>
 *
 * <p>中奖落库与发奖（发券 / 实物领取单 / 通知）在 {@link LotteryFulfillmentService}
 * 的同一个事务里完成；发奖失败整笔回滚，奖券回到奖池。</p>
 */
@Slf4j
@Service
public class LotteryServiceImpl implements LotteryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ActivityMapper activityMapper;
    private final LotteryPrizeMapper prizeMapper;
    private final LotteryDrawRecordMapper drawRecordMapper;
    private final LotteryPrizeClaimMapper claimMapper;
    private final UserCouponMapper userCouponMapper;
    private final LotteryPrizePoolService prizePoolService;
    private final LotteryRedisStore redisStore;
    private final LotteryFulfillmentService fulfillmentService;

    public LotteryServiceImpl(ActivityMapper activityMapper,
                              LotteryPrizeMapper prizeMapper,
                              LotteryDrawRecordMapper drawRecordMapper,
                              LotteryPrizeClaimMapper claimMapper,
                              UserCouponMapper userCouponMapper,
                              LotteryPrizePoolService prizePoolService,
                              LotteryRedisStore redisStore,
                              LotteryFulfillmentService fulfillmentService) {
        this.activityMapper = activityMapper;
        this.prizeMapper = prizeMapper;
        this.drawRecordMapper = drawRecordMapper;
        this.claimMapper = claimMapper;
        this.userCouponMapper = userCouponMapper;
        this.prizePoolService = prizePoolService;
        this.redisStore = redisStore;
        this.fulfillmentService = fulfillmentService;
    }

    @Override
    public List<LotteryPrizePublicView> prizes(Long activityId) {
        Activity activity = requireLotteryActivity(activityId);
        prizePoolService.ensureInitialized(activity);

        List<LotteryPrize> prizes = prizeMapper.selectList(Wrappers.<LotteryPrize>lambdaQuery()
                .eq(LotteryPrize::getActivityId, activityId)
                .orderByAsc(LotteryPrize::getLevel)
                .orderByAsc(LotteryPrize::getId));

        List<LotteryPrizePublicView> views = new ArrayList<>(prizes.size());
        for (LotteryPrize prize : prizes) {
            long remaining = prizePoolService.remainingStock(activity, prize);
            views.add(new LotteryPrizePublicView(
                    prize.getId(), prize.getName(), prize.getImage(),
                    prize.getLevel(), remaining));
        }
        return views;
    }

    @Override
    public LotteryDrawResponse draw(Long userId, Long activityId) {
        Activity activity = requireLotteryActivity(activityId);
        validateActive(activity);
        LocalDate today = LocalDate.now();

        // 快速路径：Redis 说今天已抽过，就直接查 MySQL 返回今天的已有记录
        if (redisStore.isParticipant(activityId, userId, today)) {
            LotteryDrawRecord existing = findRecord(activityId, userId, today);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        prizePoolService.ensureInitialized(activity);

        // 在还有库存的奖项里按权重随机；选中的奖池刚好被抢空就换下一个
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
            LotteryPrize candidate = pickWeighted(candidates);
            candidates.remove(candidate);
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
        record.setDrawDate(today);
        record.setPrizeId(wonPrize == null ? null : wonPrize.getId());
        record.setPrizeCode(prizeCode);
        record.setResult(wonPrize == null
                ? LotteryDrawResult.THANKS.name() : LotteryDrawResult.WIN.name());

        try {
            // 记录 + 发奖同一事务：发奖失败整笔回滚，下面 catch 把奖券放回 Redis
            fulfillmentService.persistAndFulfill(record, wonPrize, activity);
        } catch (DuplicateKeyException e) {
            LotteryDrawRecord existing = findRecord(activityId, userId, today);
            if (existing != null) {
                // 同一用户今天已有记录：本次抽到的奖券退回池子，返回已有结果
                returnPrizeQuietly(activity, wonPrize, prizeCode);
                return toResponse(existing);
            }
            // 不是同一用户的重复：说明奖券码已被别人用过，不能再放回池子
            throw new BizException(ResultCode.CONFLICT, "抽奖冲突，请重试");
        } catch (RuntimeException e) {
            // 落库/发奖失败：奖券放回，让用户可以重新抽
            returnPrizeQuietly(activity, wonPrize, prizeCode);
            throw e;
        }

        // MySQL 已经落库；Redis 只是缓存，写失败只记日志，不影响结果
        cacheSuccessQuietly(activity, userId, wonPrize);

        return toResponse(record);
    }

    @Override
    public LotteryMyRecordView myRecord(Long userId, Long activityId) {
        requireLotteryActivity(activityId);
        LotteryDrawRecord record = findRecord(activityId, userId, LocalDate.now());
        if (record == null) {
            return new LotteryMyRecordView(false, null, null, null, null, null, null);
        }
        LotteryPrize prize = record.getPrizeId() == null
                ? null : prizeMapper.selectById(record.getPrizeId());
        return new LotteryMyRecordView(
                true,
                record.getResult(),
                record.getPrizeId(),
                prize == null ? null : prize.getName(),
                prize == null ? null : prize.getPrizeType(),
                record.getPrizeCode(),
                record.getCreatedAt());
    }

    @Override
    public PageResponse<LotteryPrizeRecordView> myPrizes(Long userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<LotteryDrawRecord> result = drawRecordMapper.selectPage(
                new Page<>(safePage, safeSize),
                Wrappers.<LotteryDrawRecord>lambdaQuery()
                        .eq(LotteryDrawRecord::getUserId, userId)
                        .eq(LotteryDrawRecord::getResult, LotteryDrawResult.WIN.name())
                        .orderByDesc(LotteryDrawRecord::getId));

        List<LotteryDrawRecord> records = result.getRecords();
        if (records.isEmpty()) {
            return new PageResponse<>(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }

        Set<Long> prizeIds = new HashSet<>();
        Set<Long> activityIds = new HashSet<>();
        Set<Long> recordIds = new HashSet<>();
        for (LotteryDrawRecord record : records) {
            if (record.getPrizeId() != null) {
                prizeIds.add(record.getPrizeId());
            }
            activityIds.add(record.getActivityId());
            recordIds.add(record.getId());
        }
        Map<Long, LotteryPrize> prizes = loadPrizes(prizeIds);
        Map<Long, Activity> activities = loadActivities(activityIds);
        Map<Long, LotteryPrizeClaim> claims = loadClaims(recordIds);
        Map<Long, UserCoupon> coupons = loadCoupons(recordIds);

        List<LotteryPrizeRecordView> views = new ArrayList<>(records.size());
        for (LotteryDrawRecord record : records) {
            LotteryPrize prize = record.getPrizeId() == null ? null : prizes.get(record.getPrizeId());
            Activity activity = activities.get(record.getActivityId());
            LotteryPrizeClaim claim = claims.get(record.getId());
            UserCoupon coupon = coupons.get(record.getId());
            views.add(new LotteryPrizeRecordView(
                    record.getId(),
                    record.getActivityId(),
                    activity == null ? "" : activity.getName(),
                    record.getPrizeId(),
                    prize == null ? "" : prize.getName(),
                    prize == null ? "" : prize.getImage(),
                    prize == null ? null : prize.getPrizeType(),
                    record.getPrizeCode(),
                    record.getCreatedAt(),
                    claim == null ? null : toClaimView(claim),
                    coupon == null ? null : coupon.getId(),
                    coupon == null ? null : displayStatus(coupon),
                    coupon == null ? null : coupon.getValidTo()));
        }
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public LotteryClaimView saveClaimAddress(Long userId, Long claimId,
                                             LotteryClaimAddressRequest request) {
        LotteryPrizeClaim claim = claimMapper.selectById(claimId);
        if (claim == null || !claim.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "领取单不存在");
        }
        if (LotteryClaimStatus.SHIPPED.name().equals(claim.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "奖品已发货，不能再修改地址");
        }
        claim.setReceiverName(request.receiverName().trim());
        claim.setReceiverPhone(request.receiverPhone().trim());
        claim.setReceiverAddress(request.receiverAddress().trim());
        claim.setStatus(LotteryClaimStatus.PENDING_SHIPMENT.name());
        claimMapper.updateById(claim);
        return toClaimView(claim);
    }

    private Activity requireLotteryActivity(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null || !ActivityType.LOTTERY.name().equals(activity.getType())) {
            throw new BizException(ResultCode.NOT_FOUND, "抽奖活动不存在");
        }
        return activity;
    }

    private LotteryPrize pickWeighted(List<LotteryPrize> candidates) {
        int total = 0;
        for (LotteryPrize prize : candidates) {
            total += weightOf(prize);
        }
        // ThreadLocalRandom 线程安全，避免多个抽奖请求共用同一个 Random
        int roll = ThreadLocalRandom.current().nextInt(total) + 1;
        return pickWeighted(candidates, roll);
    }

    /** 按权重累计区间选奖项；抽离成静态方法方便单测 roll 的边界 */
    static LotteryPrize pickWeighted(List<LotteryPrize> candidates, int roll) {
        int cumulative = 0;
        for (LotteryPrize prize : candidates) {
            cumulative += weightOf(prize);
            if (roll <= cumulative) {
                return prize;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static int weightOf(LotteryPrize prize) {
        return prize.getWeight() == null ? 1 : Math.max(1, prize.getWeight());
    }

    private void validateActive(Activity activity) {
        if (activity.getStatus() != ActivityStatus.ACTIVE.code()) {
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

    private LotteryDrawRecord findRecord(Long activityId, Long userId, LocalDate drawDate) {
        return drawRecordMapper.selectOne(Wrappers.<LotteryDrawRecord>lambdaQuery()
                .eq(LotteryDrawRecord::getActivityId, activityId)
                .eq(LotteryDrawRecord::getUserId, userId)
                .eq(LotteryDrawRecord::getDrawDate, drawDate));
    }

    private Map<Long, LotteryPrize> loadPrizes(Set<Long> prizeIds) {
        Map<Long, LotteryPrize> map = new HashMap<>();
        if (prizeIds.isEmpty()) {
            return map;
        }
        for (LotteryPrize prize : prizeMapper.selectBatchIds(prizeIds)) {
            map.put(prize.getId(), prize);
        }
        return map;
    }

    private Map<Long, Activity> loadActivities(Set<Long> activityIds) {
        Map<Long, Activity> map = new HashMap<>();
        if (activityIds.isEmpty()) {
            return map;
        }
        for (Activity activity : activityMapper.selectBatchIds(activityIds)) {
            map.put(activity.getId(), activity);
        }
        return map;
    }

    private Map<Long, LotteryPrizeClaim> loadClaims(Set<Long> recordIds) {
        Map<Long, LotteryPrizeClaim> map = new HashMap<>();
        List<LotteryPrizeClaim> claims = claimMapper.selectList(
                Wrappers.<LotteryPrizeClaim>lambdaQuery()
                        .in(LotteryPrizeClaim::getDrawRecordId, recordIds));
        for (LotteryPrizeClaim claim : claims) {
            map.put(claim.getDrawRecordId(), claim);
        }
        return map;
    }

    private Map<Long, UserCoupon> loadCoupons(Set<Long> recordIds) {
        Map<Long, UserCoupon> map = new HashMap<>();
        List<UserCoupon> coupons = userCouponMapper.selectLotteryCoupons(recordIds);
        for (UserCoupon coupon : coupons) {
            map.put(coupon.getSourceId(), coupon);
        }
        return map;
    }

    private String displayStatus(UserCoupon coupon) {
        return coupon.getDisplayStatus() == null ? coupon.getStatus() : coupon.getDisplayStatus();
    }

    private LotteryClaimView toClaimView(LotteryPrizeClaim claim) {
        LotteryClaimStatus status = LotteryClaimStatus.valueOf(claim.getStatus());
        return new LotteryClaimView(
                claim.getId(),
                claim.getStatus(),
                status.label(),
                claim.getReceiverName(),
                claim.getReceiverPhone(),
                claim.getReceiverAddress(),
                claim.getTrackingNo(),
                claim.getShippedAt());
    }

    private void returnPrizeQuietly(Activity activity, LotteryPrize prize, String prizeCode) {
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

    private void markParticipantQuietly(Activity activity, Long userId) {
        try {
            redisStore.markParticipant(activity.getId(), userId, LocalDate.now());
        } catch (RuntimeException e) {
            log.warn("写抽奖资格缓存失败: activityId={} userId={} err={}",
                    activity.getId(), userId, e.getMessage());
        }
    }

    private void cacheSuccessQuietly(Activity activity, Long userId, LotteryPrize wonPrize) {
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
            return new LotteryDrawResponse("THANKS", null, null, null, null, "谢谢参与");
        }
        LotteryPrize prize = prizeMapper.selectById(record.getPrizeId());
        return new LotteryDrawResponse("WIN", record.getPrizeId(),
                prize == null ? "" : prize.getName(),
                prize == null ? null : prize.getPrizeType(),
                record.getPrizeCode(), "恭喜中奖");
    }
}
