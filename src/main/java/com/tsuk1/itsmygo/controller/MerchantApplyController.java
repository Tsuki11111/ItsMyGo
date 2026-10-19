package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.MerchantRequest;
import com.tsuk1.itsmygo.dto.MerchantView;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 顾客提交/查询商家入驻申请。任意登录用户可用，不要求 MERCHANT 角色。 */
@RestController
@RequestMapping("/api/merchant-apply")
public class MerchantApplyController {

    private final MerchantService merchantService;

    public MerchantApplyController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    public Result<MerchantView> apply(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody MerchantRequest request) {
        return Result.ok(merchantService.apply(user.id(), request));
    }

    /** 没有申请时 data 为 null */
    @GetMapping
    public Result<MerchantView> status(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(merchantService.myApplication(user.id()));
    }
}
