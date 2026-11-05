package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.dto.AfterSaleReasonRequest;
import com.tsuk1.itsmygo.dto.AfterSaleReasonView;
import com.tsuk1.itsmygo.service.AfterSaleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端维护售后原因字典。 */
@RestController
@RequestMapping("/api/admin/after-sale-reasons")
public class AdminAfterSaleReasonController {

    private final AfterSaleService afterSaleService;

    public AdminAfterSaleReasonController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    @GetMapping
    public Result<List<AfterSaleReasonView>> list() {
        return Result.ok(afterSaleService.adminReasons());
    }

    @PostMapping
    @AdminAudit(action = "AFTER_SALE_REASON_CREATE", label = "新增售后原因", targetType = "AFTER_SALE_REASON")
    public Result<AfterSaleReasonView> create(@Valid @RequestBody AfterSaleReasonRequest request) {
        return Result.ok(afterSaleService.createReason(request));
    }

    @PutMapping("/{id}")
    @AdminAudit(action = "AFTER_SALE_REASON_UPDATE", label = "修改售后原因", targetType = "AFTER_SALE_REASON")
    public Result<AfterSaleReasonView> update(@PathVariable Long id,
                                              @Valid @RequestBody AfterSaleReasonRequest request) {
        return Result.ok(afterSaleService.updateReason(id, request));
    }
}
