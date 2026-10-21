package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.AdminMerchantView;
import com.tsuk1.itsmygo.dto.MerchantRejectRequest;
import com.tsuk1.itsmygo.dto.MerchantView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ShipAddressRequest;
import com.tsuk1.itsmygo.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 平台端：商户审核与启停 */
@RestController
@RequestMapping("/api/admin/merchants")
public class AdminMerchantController {

    private final MerchantService merchantService;

    public AdminMerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public Result<PageResponse<AdminMerchantView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(merchantService.pageForAdmin(page, size, status, keyword));
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        merchantService.approve(id);
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @Valid @RequestBody MerchantRejectRequest request) {
        merchantService.reject(id, request.reason());
        return Result.ok();
    }

    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        merchantService.disable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        merchantService.enable(id);
        return Result.ok();
    }

    /** 平台自营（id=1）与普通商户共用；平台后台可代设 */
    @PutMapping("/{id}/ship-address")
    public Result<MerchantView> updateShipAddress(@PathVariable Long id,
                                                  @Valid @RequestBody ShipAddressRequest request) {
        return Result.ok(merchantService.updateShipAddress(id, request));
    }
}
