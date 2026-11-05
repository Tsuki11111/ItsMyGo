package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.AfterSaleApplyRequest;
import com.tsuk1.itsmygo.dto.AfterSaleReasonView;
import com.tsuk1.itsmygo.dto.AfterSaleShipRequest;
import com.tsuk1.itsmygo.dto.AfterSaleView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AfterSaleService;
import com.tsuk1.itsmygo.service.StorageService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 顾客售后：申请、我的售后、撤销、填退货物流、凭证上传。 */
@RestController
@RequestMapping("/api/after-sales")
public class AfterSaleController {

    private final AfterSaleService afterSaleService;
    private final StorageService storageService;

    public AfterSaleController(AfterSaleService afterSaleService, StorageService storageService) {
        this.afterSaleService = afterSaleService;
        this.storageService = storageService;
    }

    @GetMapping("/reasons")
    public Result<List<AfterSaleReasonView>> reasons() {
        return Result.ok(afterSaleService.reasons());
    }

    @RateLimit(scene = "after-sale-evidence", limit = 10, windowSeconds = 60, byUser = true)
    @PostMapping("/evidence")
    public Result<String> uploadEvidence(@RequestPart("file") MultipartFile file) {
        return Result.ok(storageService.uploadAfterSaleImage(file));
    }

    @RateLimit(scene = "after-sale-apply", limit = 10, windowSeconds = 60, byUser = true)
    @PostMapping
    public Result<AfterSaleView> apply(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody AfterSaleApplyRequest request) {
        return Result.ok(afterSaleService.apply(user.id(), request));
    }

    @GetMapping
    public Result<PageResponse<AfterSaleView>> page(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long orderId) {
        return Result.ok(afterSaleService.pageForCustomer(user.id(), page, size, status, orderId));
    }

    @GetMapping("/{id}")
    public Result<AfterSaleView> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(afterSaleService.detailForCustomer(user.id(), id));
    }

    @PostMapping("/{id}/cancel")
    public Result<AfterSaleView> cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(afterSaleService.cancel(user.id(), id));
    }

    @PostMapping("/{id}/ship-back")
    public Result<AfterSaleView> shipBack(@AuthenticationPrincipal CurrentUser user,
                                          @PathVariable Long id,
                                          @Valid @RequestBody AfterSaleShipRequest request) {
        return Result.ok(afterSaleService.shipBack(user.id(), id, request));
    }
}
