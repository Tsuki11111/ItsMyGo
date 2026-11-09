package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.report.ConsumptionReportView;
import com.tsuk1.itsmygo.common.identity.CurrentUser;
import com.tsuk1.itsmygo.service.ConsumptionReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 顾客侧个人消费报告：只返回当前登录用户的数据。 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ConsumptionReportService reportService;

    public ReportController(ConsumptionReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/consumption")
    public Result<ConsumptionReportView> consumption(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) String date) {
        return Result.ok(reportService.report(user.id(), period, date));
    }
}
