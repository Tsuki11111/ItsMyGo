package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CouponRequest;
import com.tsuk1.itsmygo.dto.CouponView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.UserCouponView;
import com.tsuk1.itsmygo.entity.Coupon;
import com.tsuk1.itsmygo.entity.UserCoupon;
import com.tsuk1.itsmygo.mapper.CouponMapper;
import com.tsuk1.itsmygo.mapper.UserCouponMapper;
import com.tsuk1.itsmygo.service.CouponService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CouponServiceImpl implements CouponService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int ENABLED = 1;
    private static final int DISABLED = 0;

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;

    public CouponServiceImpl(CouponMapper couponMapper, UserCouponMapper userCouponMapper) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
    }

    @Override
    public List<CouponView> claimable(Long userId) {
        List<Coupon> coupons = couponMapper.selectList(Wrappers.<Coupon>lambdaQuery()
                .eq(Coupon::getStatus, ENABLED)
                .apply("valid_from <= NOW() AND valid_to >= NOW() AND claimed_count < total_count")
                .orderByDesc(Coupon::getId));
        if (coupons.isEmpty()) return List.of();

        Set<Long> claimedIds = userId == null ? Set.of()
                : userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                        .eq(UserCoupon::getUserId, userId)
                        .in(UserCoupon::getCouponId, coupons.stream().map(Coupon::getId).toList()))
                .stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());

        return coupons.stream().map(coupon -> new CouponView(
                coupon.getId(), coupon.getName(), coupon.getThresholdAmount(),
                coupon.getDiscountAmount(), coupon.getTotalCount(), coupon.getClaimedCount(),
                coupon.getTotalCount() - coupon.getClaimedCount(),
                coupon.getValidFrom(), coupon.getValidTo(),
                claimedIds.contains(coupon.getId()))).toList();
    }

    @Override
    @Transactional
    public void claim(Long userId, Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券不存在");
        }
        if (!Integer.valueOf(ENABLED).equals(coupon.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "优惠券已停用");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        // 快照：模板以后改价/改期，不影响已领的券
        userCoupon.setCouponName(coupon.getName());
        userCoupon.setThresholdAmount(coupon.getThresholdAmount());
        userCoupon.setDiscountAmount(coupon.getDiscountAmount());
        userCoupon.setValidFrom(coupon.getValidFrom());
        userCoupon.setValidTo(coupon.getValidTo());
        userCoupon.setStatus(UserCoupon.UNUSED);

        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "该优惠券已领取");
        }

        // 最终以这条条件更新为准：领完/失效都会返回 0，事务回滚上面那条插入
        if (couponMapper.claimOne(couponId) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "优惠券已领完或已失效");
        }
    }

    @Override
    public PageResponse<UserCouponView> mine(Long userId, int page, int size, String status) {
        if (StringUtils.hasText(status)
                && !UserCoupon.UNUSED.equals(status)
                && !UserCoupon.USED.equals(status)
                && !UserCoupon.EXPIRED.equals(status)) {
            throw new BizException(ResultCode.BAD_REQUEST, "未知的券状态");
        }
        Page<UserCoupon> result = userCouponMapper.selectMinePage(
                new Page<>(normalizePage(page), normalizeSize(size)), userId,
                StringUtils.hasText(status) ? status : null);
        List<UserCouponView> views = result.getRecords().stream().map(this::toView).toList();
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<UserCouponView> available(Long userId, BigDecimal amount) {
        BigDecimal threshold = amount == null ? BigDecimal.ZERO : amount;
        return userCouponMapper.selectAvailable(userId, threshold).stream().map(this::toView).toList();
    }

    @Override
    public PageResponse<Coupon> pageForAdmin(int page, int size, Boolean enabled, String keyword) {
        Page<Coupon> result = couponMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<Coupon>lambdaQuery()
                        .eq(enabled != null, Coupon::getStatus,
                                Boolean.TRUE.equals(enabled) ? ENABLED : DISABLED)
                        .like(StringUtils.hasText(keyword), Coupon::getName, keyword)
                        .orderByDesc(Coupon::getId));
        return PageResponse.of(result);
    }

    @Override
    public Coupon create(CouponRequest request) {
        validatePeriod(request);
        Coupon coupon = new Coupon();
        apply(coupon, request);
        coupon.setClaimedCount(0);
        couponMapper.insert(coupon);
        return coupon;
    }

    @Override
    public Coupon update(Long id, CouponRequest request) {
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券不存在");
        }
        if (request.totalCount() < coupon.getClaimedCount()) {
            throw new BizException(ResultCode.BAD_REQUEST, "发放总量不能小于已领取数");
        }
        validatePeriod(request);
        apply(coupon, request);
        couponMapper.updateById(coupon);
        return coupon;
    }

    private void apply(Coupon coupon, CouponRequest request) {
        coupon.setName(request.name().trim());
        coupon.setThresholdAmount(request.thresholdAmount());
        coupon.setDiscountAmount(request.discountAmount());
        coupon.setTotalCount(request.totalCount());
        coupon.setValidFrom(request.validFrom());
        coupon.setValidTo(request.validTo());
        coupon.setStatus(Boolean.TRUE.equals(request.enabled()) ? ENABLED : DISABLED);
    }

    private void validatePeriod(CouponRequest request) {
        if (!request.validFrom().isBefore(request.validTo())) {
            throw new BizException(ResultCode.BAD_REQUEST, "有效期开始时间必须早于结束时间");
        }
    }

    private UserCouponView toView(UserCoupon coupon) {
        String status = coupon.getDisplayStatus() == null ? coupon.getStatus() : coupon.getDisplayStatus();
        return new UserCouponView(coupon.getId(), coupon.getCouponId(), coupon.getCouponName(),
                coupon.getThresholdAmount(), coupon.getDiscountAmount(),
                coupon.getValidFrom(), coupon.getValidTo(), status,
                coupon.getUsedOrderId(), coupon.getCreatedAt());
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
