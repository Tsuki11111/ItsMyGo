package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.dto.ActivityRequest;
import com.tsuk1.itsmygo.dto.ActivityStatusRequest;
import com.tsuk1.itsmygo.dto.ActivityUpdateRequest;
import com.tsuk1.itsmygo.dto.ActivityView;
import com.tsuk1.itsmygo.dto.LotteryPrizeRequest;
import com.tsuk1.itsmygo.dto.LotteryPrizeView;
import com.tsuk1.itsmygo.dto.LotteryClaimView;
import com.tsuk1.itsmygo.dto.LotteryRecordAdminView;
import com.tsuk1.itsmygo.dto.LotteryShipRequest;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.service.ActivityAdminService;
import com.tsuk1.itsmygo.service.LotteryAdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端活动管理：活动 CRUD 对所有类型通用；
 * 奖项子资源只对抽奖活动开放（LotteryAdminService 会校验 type）。
 *
 * <p>路径在 /api/admin/** 下，SecurityConfig 已限制 ADMIN；
 * 权限码：查看用 admin:activity:view，写操作用 admin:activity:manage。</p>
 */
@RestController
@RequestMapping("/api/admin/activities")
@PreAuthorize("hasAuthority('admin:activity:view')")
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
    @PreAuthorize("hasAuthority('admin:activity:manage')")
    @AdminAudit(action = "ACTIVITY_CREATE", label = "创建活动", targetType = "ACTIVITY")
    public Result<ActivityView> create(@Valid @RequestBody ActivityRequest request) {
        return Result.ok(activityAdminService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:activity:manage')")
    @AdminAudit(action = "ACTIVITY_UPDATE", label = "编辑活动", targetType = "ACTIVITY")
    public Result<ActivityView> update(@PathVariable Long id,
                                       @Valid @RequestBody ActivityUpdateRequest request) {
        return Result.ok(activityAdminService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('admin:activity:manage')")
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
    @PreAuthorize("hasAuthority('admin:activity:manage')")
    @AdminAudit(action = "LOTTERY_PRIZE_CREATE", label = "新增抽奖奖项", targetType = "LOTTERY_PRIZE")
    public Result<LotteryPrizeView> addPrize(@PathVariable Long id,
                                             @Valid @RequestBody LotteryPrizeRequest request) {
        return Result.ok(lotteryAdminService.addPrize(id, request));
    }

    @PutMapping("/{id}/prizes/{prizeId}")
    @PreAuthorize("hasAuthority('admin:activity:manage')")
    @AdminAudit(action = "LOTTERY_PRIZE_UPDATE", label = "编辑抽奖奖项", targetType = "LOTTERY_PRIZE")
    public Result<LotteryPrizeView> updatePrize(@PathVariable Long id,
                                                @PathVariable Long prizeId,
                                                @Valid @RequestBody LotteryPrizeRequest request) {
        return Result.ok(lotteryAdminService.updatePrize(id, prizeId, request));
    }

    @DeleteMapping("/{id}/prizes/{prizeId}")
    @PreAuthorize("hasAuthority('admin:activity:manage')")
    @AdminAudit(action = "LOTTERY_PRIZE_DELETE", label = "删除抽奖奖项", targetType = "LOTTERY_PRIZE")
    public Result<Void> deletePrize(@PathVariable Long id, @PathVariable Long prizeId) {
        lotteryAdminService.deletePrize(id, prizeId);
        return Result.ok();
    }

    @GetMapping("/{id}/records")
    public Result<PageResponse<LotteryRecordAdminView>> records(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(lotteryAdminService.listRecords(id, page, size));
    }

    @PutMapping("/claims/{claimId}/ship")
    @PreAuthorize("hasAuthority('admin:activity:manage')")
    @AdminAudit(action = "LOTTERY_CLAIM_SHIP", label = "实物奖品发货", targetType = "LOTTERY_CLAIM")
    public Result<LotteryClaimView> shipClaim(@PathVariable Long claimId,
                                              @Valid @RequestBody LotteryShipRequest request) {
        return Result.ok(lotteryAdminService.shipClaim(claimId, request.trackingNo()));
    }
}
