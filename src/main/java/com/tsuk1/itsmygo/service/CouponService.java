package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.CouponRequest;
import com.tsuk1.itsmygo.dto.CouponView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.UserCouponView;
import com.tsuk1.itsmygo.entity.Coupon;

import java.math.BigDecimal;
import java.util.List;

/** 优惠券：领券中心、我的券、管理端模板 */
public interface CouponService {

    /** 可领的模板；userId 为 null 表示匿名浏览 */
    List<CouponView> claimable(Long userId);

    void claim(Long userId, Long couponId);

    PageResponse<UserCouponView> mine(Long userId, int page, int size, String status);

    /** 结算页预校验：未使用、未过期、门槛满足 */
    List<UserCouponView> available(Long userId, BigDecimal amount);

    PageResponse<Coupon> pageForAdmin(int page, int size, Boolean enabled, String keyword);

    Coupon create(CouponRequest request);

    Coupon update(Long id, CouponRequest request);
}
