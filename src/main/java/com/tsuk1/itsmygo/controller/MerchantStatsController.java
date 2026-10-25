package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.MerchantDashboardView;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.MerchantStatsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家后台经营看板：只统计当前商家，merchantId 来自登录态。 */
@RestController
@RequestMapping("/api/merchant/stats")
public class MerchantStatsController {

    private final MerchantStatsService statsService;

    public MerchantStatsController(MerchantStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public Result<MerchantDashboardView> dashboard(@AuthenticationPrincipal CurrentUser user) {
        if (!user.isMerchant()) {
            throw new BizException(ResultCode.FORBIDDEN, "需要商家权限");
        }
        return Result.ok(statsService.dashboard(user.merchantId()));
    }
}
