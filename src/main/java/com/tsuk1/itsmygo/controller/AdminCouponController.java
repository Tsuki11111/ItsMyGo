package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.dto.CouponRequest;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.Coupon;
import com.tsuk1.itsmygo.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端：优惠券模板。不提供删除，停用即可（已领的券照常可用） */
@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public Result<PageResponse<Coupon>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) Boolean enabled,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long merchantId) {
        return Result.ok(couponService.pageForAdmin(page, size, enabled, keyword, merchantId));
    }

    @PostMapping
    @AdminAudit(action = "COUPON_CREATE", label = "新增优惠券", targetType = "COUPON")
    public Result<Coupon> create(@Valid @RequestBody CouponRequest request) {
        return Result.ok(couponService.create(request));
    }

    @PutMapping("/{id}")
    @AdminAudit(action = "COUPON_UPDATE", label = "修改优惠券", targetType = "COUPON")
    public Result<Coupon> update(@PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return Result.ok(couponService.update(id, request));
    }
}
