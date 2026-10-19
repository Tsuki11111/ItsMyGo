package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.CouponView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.UserCouponView;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.CouponService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 顾客端优惠券。
 *
 * `GET /api/coupons` 在 SecurityConfig 里精确放行匿名访问（领券中心），
 * 其余接口都需要登录。
 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 领券中心：匿名可看；登录时带 claimed 标记 */
    @GetMapping
    public Result<List<CouponView>> claimable(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(couponService.claimable(user == null ? null : user.id()));
    }

    @RateLimit(scene = "coupon", limit = 10, windowSeconds = 60, byUser = true)
    @PostMapping("/{couponId}/claim")
    public Result<Void> claim(@AuthenticationPrincipal CurrentUser user, @PathVariable Long couponId) {
        couponService.claim(user.id(), couponId);
        return Result.ok();
    }

    /** 我的券：status 支持 UNUSED / USED / EXPIRED */
    @GetMapping("/mine")
    public Result<PageResponse<UserCouponView>> mine(@AuthenticationPrincipal CurrentUser user,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestParam(required = false) String status) {
        return Result.ok(couponService.mine(user.id(), page, size, status));
    }

    /** 结算页预校验：按商品总额与店铺筛出可用券（平台券 + 该店铺券） */
    @GetMapping("/mine/available")
    public Result<List<UserCouponView>> available(@AuthenticationPrincipal CurrentUser user,
                                                  @RequestParam BigDecimal amount,
                                                  @RequestParam(required = false) Long merchantId) {
        return Result.ok(couponService.available(user.id(), amount, merchantId));
    }
}
