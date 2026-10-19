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
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.UserCoupon;
import com.tsuk1.itsmygo.mapper.CouponMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.UserCouponMapper;
import com.tsuk1.itsmygo.service.CouponService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CouponServiceImpl implements CouponService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int ENABLED = 1;
    private static final int DISABLED = 0;

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final MerchantMapper merchantMapper;

    public CouponServiceImpl(CouponMapper couponMapper, UserCouponMapper userCouponMapper,
                             MerchantMapper merchantMapper) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.merchantMapper = merchantMapper;
    }

    @Override
    public List<CouponView> claimable(Long userId) {
        List<Coupon> coupons = couponMapper.selectList(Wrappers.<Coupon>lambdaQuery()
                .eq(Coupon::getStatus, ENABLED)
                .apply("valid_from <= NOW() AND valid_to >= NOW() AND claimed_count < total_count")
                .orderByDesc(Coupon::getId));
        if (coupons.isEmpty()) return List.of();

        Map<Long, Merchant> merchants = loadMerchants(coupons.stream()
                .map(Coupon::getMerchantId).filter(Objects::nonNull).toList());

        Set<Long> claimedIds = userId == null ? Set.of()
                : userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                        .eq(UserCoupon::getUserId, userId)
                        .in(UserCoupon::getCouponId, coupons.stream().map(Coupon::getId).toList()))
                .stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());

        return coupons.stream()
                // 店铺券只在店铺营业中时可领；平台券不受影响
                .filter(coupon -> coupon.getMerchantId() == null
                        || isActive(merchants.get(coupon.getMerchantId())))
                .map(coupon -> new CouponView(
                        coupon.getId(), coupon.getMerchantId(),
                        merchantName(merchants, coupon.getMerchantId()),
                        coupon.getName(), coupon.getThresholdAmount(),
                        coupon.getDiscountAmount(), coupon.getTotalCount(), coupon.getClaimedCount(),
                        coupon.getTotalCount() - coupon.getClaimedCount(),
                        coupon.getValidFrom(), coupon.getValidTo(),
                        claimedIds.contains(coupon.getId())))
                .toList();
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
        if (coupon.getMerchantId() != null
                && !isActive(merchantMapper.selectById(coupon.getMerchantId()))) {
            throw new BizException(ResultCode.BAD_REQUEST, "店铺已停用，无法领取该券");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setMerchantId(coupon.getMerchantId());
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
        Map<Long, Merchant> merchants = loadMerchants(result.getRecords().stream()
                .map(UserCoupon::getMerchantId).filter(Objects::nonNull).toList());
        List<UserCouponView> views = result.getRecords().stream()
                .map(coupon -> toView(coupon, merchantName(merchants, coupon.getMerchantId())))
                .toList();
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<UserCouponView> available(Long userId, BigDecimal amount) {
        return available(userId, amount, null);
    }

    @Override
    public List<UserCouponView> available(Long userId, BigDecimal amount, Long merchantId) {
        BigDecimal threshold = amount == null ? BigDecimal.ZERO : amount;
        List<UserCoupon> coupons = userCouponMapper.selectAvailable(userId, threshold, merchantId);
        Map<Long, Merchant> merchants = loadMerchants(coupons.stream()
                .map(UserCoupon::getMerchantId).filter(Objects::nonNull).toList());
        return coupons.stream()
                .map(coupon -> toView(coupon, merchantName(merchants, coupon.getMerchantId())))
                .toList();
    }

    @Override
    public PageResponse<Coupon> pageForAdmin(int page, int size, Boolean enabled, String keyword) {
        return pageForAdmin(page, size, enabled, keyword, null);
    }

    @Override
    public PageResponse<Coupon> pageForAdmin(int page, int size, Boolean enabled, String keyword,
                                             Long merchantId) {
        Page<Coupon> result = couponMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<Coupon>lambdaQuery()
                        .eq(enabled != null, Coupon::getStatus,
                                Boolean.TRUE.equals(enabled) ? ENABLED : DISABLED)
                        .eq(merchantId != null, Coupon::getMerchantId, merchantId)
                        .like(StringUtils.hasText(keyword), Coupon::getName, keyword)
                        .orderByDesc(Coupon::getId));
        return PageResponse.of(result);
    }

    @Override
    public Coupon create(CouponRequest request) {
        validatePeriod(request);
        validateMerchant(request.merchantId());
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
        // 已有用户领取后，券的归属与金额条款不能再改（否则用户权益会被悄悄改掉）
        if (coupon.getClaimedCount() > 0 && termsChanged(coupon, request)) {
            throw new BizException(ResultCode.BAD_REQUEST, "已有用户领取，不能修改券的归属或金额条款");
        }
        validatePeriod(request);
        validateMerchant(request.merchantId());
        apply(coupon, request);
        couponMapper.updateById(coupon);
        return coupon;
    }

    @Override
    public PageResponse<Coupon> pageForMerchant(Long merchantId, int page, int size,
                                                String status, String keyword) {
        Boolean enabled = StringUtils.hasText(status) ? "1".equals(status) : null;
        return pageForAdmin(page, size, enabled, keyword, merchantId);
    }

    @Override
    public Coupon createForMerchant(Long merchantId, CouponRequest request) {
        return create(withMerchant(request, merchantId));
    }

    @Override
    public Coupon updateForMerchant(Long merchantId, Long id, CouponRequest request) {
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null || !merchantId.equals(coupon.getMerchantId())) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券不存在");
        }
        return update(id, withMerchant(request, merchantId));
    }

    private CouponRequest withMerchant(CouponRequest request, Long merchantId) {
        return new CouponRequest(request.name(), request.thresholdAmount(),
                request.discountAmount(), request.totalCount(), request.validFrom(),
                request.validTo(), request.enabled(), merchantId);
    }

    private boolean termsChanged(Coupon coupon, CouponRequest request) {
        return !Objects.equals(coupon.getMerchantId(), request.merchantId())
                || coupon.getThresholdAmount().compareTo(request.thresholdAmount()) != 0
                || coupon.getDiscountAmount().compareTo(request.discountAmount()) != 0
                || !coupon.getValidFrom().equals(request.validFrom())
                || !coupon.getValidTo().equals(request.validTo());
    }

    private void validateMerchant(Long merchantId) {
        if (merchantId != null && merchantMapper.selectById(merchantId) == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "店铺不存在");
        }
    }

    private Map<Long, Merchant> loadMerchants(List<Long> merchantIds) {
        List<Long> ids = merchantIds.stream().distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return merchantMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));
    }

    private String merchantName(Map<Long, Merchant> merchants, Long merchantId) {
        Merchant merchant = merchantId == null ? null : merchants.get(merchantId);
        return merchant == null ? "" : merchant.getName();
    }

    private boolean isActive(Merchant merchant) {
        return merchant != null && Merchant.ACTIVE.equals(merchant.getStatus());
    }

    private void apply(Coupon coupon, CouponRequest request) {
        coupon.setMerchantId(request.merchantId());
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

    private UserCouponView toView(UserCoupon coupon, String merchantName) {
        String status = coupon.getDisplayStatus() == null ? coupon.getStatus() : coupon.getDisplayStatus();
        return new UserCouponView(coupon.getId(), coupon.getCouponId(), coupon.getMerchantId(),
                merchantName, coupon.getCouponName(),
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
