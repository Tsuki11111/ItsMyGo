package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CouponRequest;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.Coupon;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商家后台：只能管理本店店铺券 */
@RestController
@RequestMapping("/api/merchant/coupons")
public class MerchantCouponController {

    private final CouponService couponService;

    public MerchantCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public Result<PageResponse<Coupon>> page(@AuthenticationPrincipal CurrentUser user,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(couponService.pageForMerchant(requireMerchant(user), page, size,
                status, keyword));
    }

    @PostMapping
    public Result<Coupon> create(@AuthenticationPrincipal CurrentUser user,
                                 @Valid @RequestBody CouponRequest request) {
        return Result.ok(couponService.createForMerchant(requireMerchant(user), request));
    }

    @PutMapping("/{id}")
    public Result<Coupon> update(@AuthenticationPrincipal CurrentUser user,
                                 @PathVariable Long id,
                                 @Valid @RequestBody CouponRequest request) {
        return Result.ok(couponService.updateForMerchant(requireMerchant(user), id, request));
    }

    private Long requireMerchant(CurrentUser user) {
        if (!user.isMerchant()) {
            throw new BizException(ResultCode.FORBIDDEN, "需要商家权限");
        }
        return user.merchantId();
    }
}
