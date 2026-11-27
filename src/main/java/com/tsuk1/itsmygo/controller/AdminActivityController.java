package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.dto.ActivityRequest;
import com.tsuk1.itsmygo.dto.ActivityStatusRequest;
import com.tsuk1.itsmygo.dto.ActivityUpdateRequest;
import com.tsuk1.itsmygo.dto.ActivityView;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeView;
import com.tsuk1.itsmygo.service.ActivityAdminService;
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

/**
 * 管理端活动管理：活动 CRUD 对所有类型通用；
 * 奖项子资源只对抽奖活动开放（LotteryAdminService 会校验 type）。
 *
 * <p>路径在 /api/admin/** 下，SecurityConfig 已限制 ADMIN；
 * 暂不接 RBAC 权限码，和抽奖模块保持一致。</p>
 */
@RestController
@RequestMapping("/api/admin/activities")
public class AdminActivityController {

    private final ActivityAdminService activityAdminService;
    private final LotteryAdminService lotteryAdminService;

    public AdminActivityController(ActivityAdminService activityAdminService,
                                   LotteryAdminService lotteryAdminService) {
        this.activityAdminService = activityAdminService;
        this.lotteryAdminService = lotteryAdminService;
    }

    @GetMapping
    public Result<List<ActivityView>> list() {
        return Result.ok(activityAdminService.list());
    }

    @PostMapping
    @AdminAudit(action = "ACTIVITY_CREATE", label = "创建活动", targetType = "ACTIVITY")
    public Result<ActivityView> create(@Valid @RequestBody ActivityRequest request) {
        return Result.ok(activityAdminService.create(request));
    }

    @PutMapping("/{id}")
    @AdminAudit(action = "ACTIVITY_UPDATE", label = "编辑活动", targetType = "ACTIVITY")
    public Result<ActivityView> update(@PathVariable Long id,
                                       @Valid @RequestBody ActivityUpdateRequest request) {
        return Result.ok(activityAdminService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @AdminAudit(action = "ACTIVITY_STATUS", label = "修改活动状态", targetType = "ACTIVITY")
    public Result<ActivityView> updateStatus(@PathVariable Long id,
                                             @Valid @RequestBody ActivityStatusRequest request) {
        return Result.ok(activityAdminService.updateStatus(id, request.status()));
    }

    @GetMapping("/{id}/prizes")
    public Result<List<LotteryPrizeView>> listPrizes(@PathVariable Long id) {
        return Result.ok(lotteryAdminService.listPrizes(id));
    }

    @PostMapping("/{id}/prizes")
    @AdminAudit(action = "LOTTERY_PRIZE_CREATE", label = "新增抽奖奖项", targetType = "LOTTERY_PRIZE")
    public Result<LotteryPrizeView> addPrize(@PathVariable Long id,
                                             @Valid @RequestBody LotteryPrizeRequest request) {
        return Result.ok(lotteryAdminService.addPrize(id, request));
    }
}
