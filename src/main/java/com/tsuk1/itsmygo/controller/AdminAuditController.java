package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.AdminAuditView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.service.AdminAuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 管理端操作审计：只读查询。 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    public AdminAuditController(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public Result<PageResponse<AdminAuditView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.ok(adminAuditService.page(page, size, keyword, action, result,
                dateFrom, dateTo));
    }
}
