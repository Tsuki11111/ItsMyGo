package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.entity.Activity;
import com.tsuk1.itsmygo.entity.LotteryClaimStatus;
import com.tsuk1.itsmygo.entity.LotteryDrawRecord;
import com.tsuk1.itsmygo.entity.LotteryPrize;
import com.tsuk1.itsmygo.entity.LotteryPrizeClaim;
import com.tsuk1.itsmygo.entity.LotteryPrizeType;
import com.tsuk1.itsmygo.entity.UserCoupon;
import com.tsuk1.itsmygo.mapper.LotteryDrawRecordMapper;
import com.tsuk1.itsmygo.mapper.LotteryPrizeClaimMapper;
import com.tsuk1.itsmygo.service.CouponService;
import com.tsuk1.itsmygo.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 抽奖落库与发奖：中奖记录、券发放 / 实物领取单、站内通知在同一个事务里。
 *
 * <p>只要发奖失败就整笔回滚，调用方再把奖券放回 Redis 奖池 —— 保证「中奖必有奖」。
 * 单独成类是为了让 {@code @Transactional} 真正经过代理（同类自调用不生效）。</p>
 */
@Slf4j
@Service
public class LotteryFulfillmentService {

    private final LotteryDrawRecordMapper drawRecordMapper;
    private final LotteryPrizeClaimMapper claimMapper;
    private final CouponService couponService;
    private final NotificationService notificationService;

    public LotteryFulfillmentService(LotteryDrawRecordMapper drawRecordMapper,
                                     LotteryPrizeClaimMapper claimMapper,
                                     CouponService couponService,
                                     NotificationService notificationService) {
        this.drawRecordMapper = drawRecordMapper;
        this.claimMapper = claimMapper;
        this.couponService = couponService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void persistAndFulfill(LotteryDrawRecord record, LotteryPrize prize, Activity activity) {
        drawRecordMapper.insert(record);
        if (prize == null) {
            return;
        }

        String content;
        if (LotteryPrizeType.COUPON.name().equals(prize.getPrizeType())) {
            if (prize.getCouponId() == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "奖品券未配置，无法发放");
            }
            couponService.issueForLottery(record.getUserId(), prize.getCouponId(), record.getId());
            content = "你在「" + activity.getName() + "」抽中了「" + prize.getName()
                    + "」，优惠券已放入「我的优惠券」";
        } else {
            LotteryPrizeClaim claim = new LotteryPrizeClaim();
            claim.setDrawRecordId(record.getId());
            claim.setActivityId(record.getActivityId());
            claim.setPrizeId(prize.getId());
            claim.setUserId(record.getUserId());
            claim.setStatus(LotteryClaimStatus.PENDING_ADDRESS.name());
            claimMapper.insert(claim);
            content = "你在「" + activity.getName() + "」抽中了「" + prize.getName()
                    + "」，请到「我的奖品」填写收货地址";
        }

        try {
            notificationService.notifyUser(record.getUserId(), "LOTTERY_WIN", "恭喜中奖",
                    content, "/user/prizes", "lottery-win:" + record.getId());
        } catch (RuntimeException e) {
            // 通知是旁路，失败不影响发奖与中奖结果
            log.warn("中奖通知发送失败: userId={} recordId={} err={}",
                    record.getUserId(), record.getId(), e.getMessage());
        }
    }
}
