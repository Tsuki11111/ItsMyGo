package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.dto.LotteryActivityRequest;
import com.tsuk1.itsmygo.dto.LotteryActivityView;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeView;
import com.tsuk1.itsmygo.dto.LotteryStatusRequest;
import com.tsuk1.itsmygo.service.LotteryAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端抽奖配置；/api/admin/** 在 SecurityConfig 里已限制 ADMIN */
@RestController
@RequestMapping("/api/admin/lottery")
public class AdminLotteryController {

    private final LotteryAdminService lotteryAdminService;

    public AdminLotteryController(LotteryAdminService lotteryAdminService) {
        this.lotteryAdminService = lotteryAdminService;
    }

    @GetMapping("/activities")
    public Result<List<LotteryActivityView>> listActivities() {
        return Result.ok(lotteryAdminService.listActivities());
    }

    @PostMapping("/activities")
    @AdminAudit(action = "LOTTERY_ACTIVITY_CREATE", label = "创建抽奖活动",
            targetType = "LOTTERY_ACTIVITY")
    public Result<LotteryActivityView> createActivity(
            @Valid @RequestBody LotteryActivityRequest request) {
        return Result.ok(lotteryAdminService.createActivity(request));
    }

    @PutMapping("/activities/{id}/status")
    @AdminAudit(action = "LOTTERY_ACTIVITY_STATUS", label = "修改抽奖活动状态",
            targetType = "LOTTERY_ACTIVITY")
    public Result<LotteryActivityView> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody LotteryStatusRequest request) {
        return Result.ok(lotteryAdminService.updateStatus(id, request.status()));
    }

    @PostMapping("/activities/{id}/prizes")
    @AdminAudit(action = "LOTTERY_PRIZE_CREATE", label = "新增抽奖奖项",
            targetType = "LOTTERY_PRIZE")
    public Result<LotteryPrizeView> addPrize(
            @PathVariable Long id,
            @Valid @RequestBody LotteryPrizeRequest request) {
        return Result.ok(lotteryAdminService.addPrize(id, request));
    }

    @GetMapping("/activities/{id}/prizes")
    public Result<List<LotteryPrizeView>> listPrizes(@PathVariable Long id) {
        return Result.ok(lotteryAdminService.listPrizes(id));
    }
}
