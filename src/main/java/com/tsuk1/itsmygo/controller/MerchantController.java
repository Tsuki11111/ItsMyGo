package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.MerchantRequest;
import com.tsuk1.itsmygo.dto.MerchantView;
import com.tsuk1.itsmygo.dto.ShipAddressRequest;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家后台：当前商家的店铺资料 */
@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping("/profile")
    public Result<MerchantView> profile(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(merchantService.profile(requireMerchant(user)));
    }

    @PutMapping("/profile")
    public Result<MerchantView> update(@AuthenticationPrincipal CurrentUser user,
                                       @Valid @RequestBody MerchantRequest request) {
        return Result.ok(merchantService.updateProfile(requireMerchant(user), request));
    }

    @PutMapping("/ship-address")
    public Result<MerchantView> updateShipAddress(@AuthenticationPrincipal CurrentUser user,
                                                  @Valid @RequestBody ShipAddressRequest request) {
        return Result.ok(merchantService.updateShipAddress(requireMerchant(user), request));
    }

    private Long requireMerchant(CurrentUser user) {
        if (!user.isMerchant()) {
            throw new BizException(ResultCode.FORBIDDEN, "需要商家权限");
        }
        return user.merchantId();
    }
}
