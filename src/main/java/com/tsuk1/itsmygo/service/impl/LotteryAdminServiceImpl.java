package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.common.lottery.LotteryPrizePoolService;
import com.tsuk1.itsmygo.dto.LotteryClaimView;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeView;
import com.tsuk1.itsmygo.dto.LotteryRecordAdminView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.Activity;
import com.tsuk1.itsmygo.entity.ActivityStatus;
import com.tsuk1.itsmygo.entity.ActivityType;
import com.tsuk1.itsmygo.entity.Coupon;
import com.tsuk1.itsmygo.entity.LotteryClaimStatus;
import com.tsuk1.itsmygo.entity.LotteryDrawRecord;
import com.tsuk1.itsmygo.entity.LotteryDrawResult;
import com.tsuk1.itsmygo.entity.LotteryPrize;
import com.tsuk1.itsmygo.entity.LotteryPrizeClaim;
import com.tsuk1.itsmygo.entity.LotteryPrizeType;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.ActivityMapper;
import com.tsuk1.itsmygo.mapper.CouponMapper;
import com.tsuk1.itsmygo.mapper.LotteryDrawRecordMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeClaimMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.LotteryAdminService;
import com.tsuk1.itsmygo.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 抽奖奖项管理；所有入口都会校验活动类型是 LOTTERY */
@Slf4j
@Service
public class LotteryAdminServiceImpl implements LotteryAdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int COUPON_ENABLED = 1;

    private final ActivityMapper activityMapper;
    private final LotteryPrizeMapper prizeMapper;
    private final LotteryDrawRecordMapper drawRecordMapper;
    private final LotteryPrizeClaimMapper claimMapper;
    private final CouponMapper couponMapper;
    private final UserMapper userMapper;
    private final LotteryPrizePoolService prizePoolService;
    private final NotificationService notificationService;

    public LotteryAdminServiceImpl(ActivityMapper activityMapper,
                                   LotteryPrizeMapper prizeMapper,
                                   LotteryDrawRecordMapper drawRecordMapper,
                                   LotteryPrizeClaimMapper claimMapper,
                                   CouponMapper couponMapper,
                                   UserMapper userMapper,
                                   LotteryPrizePoolService prizePoolService,
                                   NotificationService notificationService) {
        this.activityMapper = activityMapper;
        this.prizeMapper = prizeMapper;
        this.drawRecordMapper = drawRecordMapper;
        this.claimMapper = claimMapper;
        this.couponMapper = couponMapper;
        this.userMapper = userMapper;
        this.prizePoolService = prizePoolService;
        this.notificationService = notificationService;
    }

    @Override
    public LotteryPrizeView addPrize(Long activityId, LotteryPrizeRequest request) {
        Activity activity = requireLotteryActivity(activityId);
        if (activity.getStatus() == ActivityStatus.ENDED.code()) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已结束，不能添加奖项");
        }

        String prizeType = resolvePrizeType(request.prizeType());
        Long couponId = null;
        if (LotteryPrizeType.COUPON.name().equals(prizeType)) {
            couponId = requirePrizeCoupon(request.couponId(), activity);
        }

        LotteryPrize prize = new LotteryPrize();
        prize.setActivityId(activityId);
        prize.setName(request.name());
        prize.setImage(request.image() == null ? "" : request.image());
        prize.setPrizeType(prizeType);
        prize.setCouponId(couponId);
        prize.setLevel(request.level());
        prize.setWeight(request.weight());
        prize.setStock(request.stock());
        prizeMapper.insert(prize);

        LotteryPrize saved = prizeMapper.selectById(prize.getId());
        return toView(saved, saved.getStock());
    }

    @Override
    public LotteryPrizeView updatePrize(Long activityId, Long prizeId, LotteryPrizeRequest request) {
        Activity activity = requireLotteryActivity(activityId);
        if (activity.getStatus() == ActivityStatus.ENDED.code()) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已结束，不能编辑奖项");
        }
        LotteryPrize prize = requirePrize(activityId, prizeId);

        String prizeType = resolvePrizeType(request.prizeType());
        if (!prizeType.equals(prize.getPrizeType()) && hasWinner(activityId, prizeId)) {
            // 历史中奖记录没有奖品快照，类型一变「券已发 / 实物待发」的语义就错了
            throw new BizException(ResultCode.BAD_REQUEST, "奖项已有中奖记录，不能修改奖品类型");
        }
        Long couponId = null;
        if (LotteryPrizeType.COUPON.name().equals(prizeType)) {
            couponId = requirePrizeCoupon(request.couponId(), activity);
        }

        // 先按差量同步 Redis 奖池：调低库存小于已中出数量时在这里拒绝，DB 不动
        prizePoolService.adjustStock(activity, prize, prize.getStock(), request.stock());

        prize.setName(request.name());
        prize.setImage(request.image() == null ? "" : request.image());
        prize.setPrizeType(prizeType);
        prize.setCouponId(couponId);
        prize.setLevel(request.level());
        prize.setWeight(request.weight());
        prize.setStock(request.stock());
        prizeMapper.updateById(prize);

        LotteryPrize saved = prizeMapper.selectById(prizeId);
        Integer remaining = null;
        try {
            remaining = (int) prizePoolService.remainingStock(activity, saved);
        } catch (RuntimeException e) {
            log.warn("读取抽奖实时库存失败: activityId={} prizeId={} err={}",
                    activityId, prizeId, e.getMessage());
        }
        return toView(saved, remaining);
    }

    @Override
    public void deletePrize(Long activityId, Long prizeId) {
        Activity activity = requireLotteryActivity(activityId);
        if (activity.getStatus() == ActivityStatus.ENDED.code()) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动已结束，不能删除奖项");
        }
        LotteryPrize prize = requirePrize(activityId, prizeId);
        if (hasWinner(activityId, prizeId)) {
            throw new BizException(ResultCode.CONFLICT, "该奖项已有中奖记录，不能删除");
        }
        prizeMapper.deleteById(prizeId);
        prizePoolService.removePrizePool(activity, prize);
    }

    private LotteryPrize requirePrize(Long activityId, Long prizeId) {
        LotteryPrize prize = prizeMapper.selectById(prizeId);
        if (prize == null || !activityId.equals(prize.getActivityId())) {
            throw new BizException(ResultCode.NOT_FOUND, "奖项不存在");
        }
        return prize;
    }

    private boolean hasWinner(Long activityId, Long prizeId) {
        Long count = drawRecordMapper.selectCount(Wrappers.<LotteryDrawRecord>lambdaQuery()
                .eq(LotteryDrawRecord::getActivityId, activityId)
                .eq(LotteryDrawRecord::getPrizeId, prizeId)
                .eq(LotteryDrawRecord::getResult, LotteryDrawResult.WIN.name()));
        return count != null && count > 0;
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

    @Override
    public PageResponse<LotteryRecordAdminView> listRecords(Long activityId, int page, int size) {
        requireLotteryActivity(activityId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<LotteryDrawRecord> result = drawRecordMapper.selectPage(
                new Page<>(safePage, safeSize),
                Wrappers.<LotteryDrawRecord>lambdaQuery()
                        .eq(LotteryDrawRecord::getActivityId, activityId)
                        .eq(LotteryDrawRecord::getResult, LotteryDrawResult.WIN.name())
                        .orderByDesc(LotteryDrawRecord::getId));

        List<LotteryDrawRecord> records = result.getRecords();
        if (records.isEmpty()) {
            return new PageResponse<>(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }

        Set<Long> prizeIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> recordIds = new HashSet<>();
        for (LotteryDrawRecord record : records) {
            prizeIds.add(record.getPrizeId());
            userIds.add(record.getUserId());
            recordIds.add(record.getId());
        }
        Map<Long, LotteryPrize> prizes = new HashMap<>();
        for (LotteryPrize prize : prizeMapper.selectBatchIds(prizeIds)) {
            prizes.put(prize.getId(), prize);
        }
        Map<Long, String> usernames = new HashMap<>();
        for (User user : userMapper.selectBatchIds(userIds)) {
            usernames.put(user.getId(), user.getUsername());
        }
        Map<Long, LotteryPrizeClaim> claims = new HashMap<>();
        for (LotteryPrizeClaim claim : claimMapper.selectList(
                Wrappers.<LotteryPrizeClaim>lambdaQuery()
                        .in(LotteryPrizeClaim::getDrawRecordId, recordIds))) {
            claims.put(claim.getDrawRecordId(), claim);
        }

        List<LotteryRecordAdminView> views = new ArrayList<>(records.size());
        for (LotteryDrawRecord record : records) {
            LotteryPrize prize = prizes.get(record.getPrizeId());
            LotteryPrizeClaim claim = claims.get(record.getId());
            views.add(new LotteryRecordAdminView(
                    record.getId(),
                    record.getActivityId(),
                    record.getUserId(),
                    usernames.getOrDefault(record.getUserId(), ""),
                    record.getPrizeId(),
                    prize == null ? "" : prize.getName(),
                    prize == null ? null : prize.getPrizeType(),
                    record.getPrizeCode(),
                    record.getCreatedAt(),
                    claim == null ? null : toClaimView(claim)));
        }
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public LotteryClaimView shipClaim(Long claimId, String trackingNo) {
        LotteryPrizeClaim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BizException(ResultCode.NOT_FOUND, "领取单不存在");
        }
        if (!LotteryClaimStatus.PENDING_SHIPMENT.name().equals(claim.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    LotteryClaimStatus.SHIPPED.name().equals(claim.getStatus())
                            ? "该奖品已发货" : "用户还未填写收货地址");
        }
        claim.setTrackingNo(trackingNo.trim());
        claim.setStatus(LotteryClaimStatus.SHIPPED.name());
        claim.setShippedAt(LocalDateTime.now());
        claimMapper.updateById(claim);

        try {
            notificationService.notifyUser(claim.getUserId(), "LOTTERY_SHIPPED", "奖品已发货",
                    "你抽中的实物奖品已发货，快递单号：" + claim.getTrackingNo(),
                    "/user/prizes", "lottery-shipped:" + claim.getId());
        } catch (RuntimeException e) {
            // 发货已成功，通知失败只记日志
            log.warn("奖品发货通知发送失败: claimId={} userId={} err={}",
                    claimId, claim.getUserId(), e.getMessage());
        }
        return toClaimView(claim);
    }

    private Activity requireLotteryActivity(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null || !ActivityType.LOTTERY.name().equals(activity.getType())) {
            throw new BizException(ResultCode.NOT_FOUND, "抽奖活动不存在");
        }
        return activity;
    }

    private String resolvePrizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return LotteryPrizeType.PHYSICAL.name();
        }
        String value = raw.trim().toUpperCase();
        if (!LotteryPrizeType.COUPON.name().equals(value)
                && !LotteryPrizeType.PHYSICAL.name().equals(value)) {
            throw new BizException(ResultCode.BAD_REQUEST, "奖品类型只能是 COUPON 或 PHYSICAL");
        }
        return value;
    }

    private Long requirePrizeCoupon(Long couponId, Activity activity) {
        if (couponId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "券类奖品必须选择优惠券");
        }
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "优惠券不存在");
        }
        if (coupon.getMerchantId() != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "抽奖奖品只能使用平台券");
        }
        if (!Integer.valueOf(COUPON_ENABLED).equals(coupon.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "优惠券已停用");
        }
        if (coupon.getValidTo() == null || coupon.getValidTo().isBefore(activity.getEndAt())) {
            throw new BizException(ResultCode.BAD_REQUEST, "优惠券有效期必须覆盖活动结束时间");
        }
        return couponId;
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

    private LotteryPrizeView toView(LotteryPrize prize, Integer remainingStock) {
        return new LotteryPrizeView(
                prize.getId(),
                prize.getActivityId(),
                prize.getName(),
                prize.getImage(),
                prize.getPrizeType(),
                prize.getCouponId(),
                prize.getLevel(),
                prize.getWeight(),
                prize.getStock(),
                remainingStock,
                prize.getCreatedAt());
    }
}
