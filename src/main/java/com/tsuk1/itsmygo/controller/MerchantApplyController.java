package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.MerchantRequest;
import com.tsuk1.itsmygo.dto.MerchantView;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.MerchantService;
import com.tsuk1.itsmygo.service.StorageService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 顾客提交/查询商家入驻申请。任意登录用户可用，不要求 MERCHANT 角色。 */
@RestController
@RequestMapping("/api/merchant-apply")
public class MerchantApplyController {

    private final MerchantService merchantService;
    private final StorageService storageService;

    public MerchantApplyController(MerchantService merchantService, StorageService storageService) {
        this.merchantService = merchantService;
        this.storageService = storageService;
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

    /** 申请人尚无 MERCHANT 角色，单独开放 Logo 上传；按用户限流防刷存储 */
    @RateLimit(scene = "upload", limit = 20, windowSeconds = 60, byUser = true)
    @PostMapping("/logo")
    public Result<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        return Result.ok(storageService.uploadMerchantLogo(file));
    }
}
