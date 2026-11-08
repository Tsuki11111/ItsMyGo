package com.tsuk1.itsmygo.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.dto.AfterSaleRejectRequest;
import com.tsuk1.itsmygo.dto.AfterSaleStatsView;
import com.tsuk1.itsmygo.dto.AfterSaleView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.service.AfterSaleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端售后：全平台查看、平台介入处理（驳回 / 同意 / 确认收货）。 */
@PreAuthorize("hasAuthority('admin:aftersale:view')")
@RestController
@RequestMapping("/api/admin/after-sales")
public class AdminAfterSaleController {

    private final AfterSaleService afterSaleService;

    public AdminAfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    @GetMapping
    public Result<PageResponse<AfterSaleView>> page(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword) {
        return Result.ok(afterSaleService.pageForAdmin(page, size, status, keyword));
    }

    @GetMapping("/stats")
    public Result<AfterSaleStatsView> stats() {
        return Result.ok(afterSaleService.statsForAdmin());
    }

    @GetMapping("/{id}")
    public Result<AfterSaleView> detail(@PathVariable Long id) {
        return Result.ok(afterSaleService.detailForAdmin(id));
    }

    @PreAuthorize("hasAuthority('admin:aftersale:handle')")
    @PostMapping("/{id}/approve")
    @AdminAudit(action = "AFTER_SALE_APPROVE", label = "平台介入：同意售后", targetType = "AFTER_SALE")
    public Result<AfterSaleView> approve(@PathVariable Long id) {
        return Result.ok(afterSaleService.approveForAdmin(id));
    }

    @PreAuthorize("hasAuthority('admin:aftersale:handle')")
    @PostMapping("/{id}/reject")
    @AdminAudit(action = "AFTER_SALE_REJECT", label = "平台介入：驳回售后", targetType = "AFTER_SALE")
    public Result<AfterSaleView> reject(@PathVariable Long id,
                                        @Valid @RequestBody AfterSaleRejectRequest request) {
        return Result.ok(afterSaleService.rejectForAdmin(id, request));
    }

    @PreAuthorize("hasAuthority('admin:aftersale:handle')")
    @PostMapping("/{id}/confirm-received")
    @AdminAudit(action = "AFTER_SALE_CONFIRM_RECEIVED", label = "平台介入：确认退货收货",
            targetType = "AFTER_SALE")
    public Result<AfterSaleView> confirmReceived(@PathVariable Long id) {
        return Result.ok(afterSaleService.confirmReceivedForAdmin(id));
    }
}
