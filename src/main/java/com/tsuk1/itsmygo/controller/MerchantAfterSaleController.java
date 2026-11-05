package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AfterSaleRejectRequest;
import com.tsuk1.itsmygo.dto.AfterSaleStatsView;
import com.tsuk1.itsmygo.dto.AfterSaleView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AfterSaleService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商家售后：只看/只处理本店售后单。 */
@RestController
@RequestMapping("/api/merchant/after-sales")
public class MerchantAfterSaleController {

    private final AfterSaleService afterSaleService;

    public MerchantAfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    @GetMapping
    public Result<PageResponse<AfterSaleView>> page(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) String status) {
        return Result.ok(afterSaleService.pageForMerchant(requireMerchant(user), page, size, status));
    }

    @GetMapping("/stats")
    public Result<AfterSaleStatsView> stats(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(afterSaleService.statsForMerchant(requireMerchant(user)));
    }

    @GetMapping("/{id}")
    public Result<AfterSaleView> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(afterSaleService.detailForMerchant(requireMerchant(user), id));
    }

    @PostMapping("/{id}/approve")
    public Result<AfterSaleView> approve(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(afterSaleService.approveForMerchant(requireMerchant(user), id));
    }

    @PostMapping("/{id}/reject")
    public Result<AfterSaleView> reject(@AuthenticationPrincipal CurrentUser user,
                                        @PathVariable Long id,
                                        @Valid @RequestBody AfterSaleRejectRequest request) {
        return Result.ok(afterSaleService.rejectForMerchant(requireMerchant(user), id, request));
    }

    @PostMapping("/{id}/confirm-received")
    public Result<AfterSaleView> confirmReceived(@AuthenticationPrincipal CurrentUser user,
                                                 @PathVariable Long id) {
        return Result.ok(afterSaleService.confirmReceivedForMerchant(requireMerchant(user), id));
    }

    private Long requireMerchant(CurrentUser user) {
        if (!user.isMerchant()) {
            throw new BizException(ResultCode.FORBIDDEN, "需要商家权限");
        }
        return user.merchantId();
    }
}
