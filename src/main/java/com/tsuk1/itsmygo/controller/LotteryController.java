package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.identity.CurrentUser;
import com.tsuk1.itsmygo.dto.LotteryClaimAddressRequest;
import com.tsuk1.itsmygo.dto.LotteryClaimView;
import com.tsuk1.itsmygo.dto.LotteryDrawResponse;
import com.tsuk1.itsmygo.dto.LotteryMyRecordView;
import com.tsuk1.itsmygo.dto.LotteryPrizePublicView;
import com.tsuk1.itsmygo.dto.LotteryPrizeRecordView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.service.LotteryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 顾客端抽奖：除奖池外都需登录 */
@RestController
@RequestMapping("/api/lottery")
public class LotteryController {

    private final LotteryService lotteryService;

    public LotteryController(LotteryService lotteryService) {
        this.lotteryService = lotteryService;
    }

    @GetMapping("/{activityId}/prizes")
    public Result<List<LotteryPrizePublicView>> prizes(@PathVariable Long activityId) {
        return Result.ok(lotteryService.prizes(activityId));
    }

    @PostMapping("/{activityId}/draw")
    public Result<LotteryDrawResponse> draw(@AuthenticationPrincipal CurrentUser user,
                                            @PathVariable Long activityId) {
        return Result.ok(lotteryService.draw(user.id(), activityId));
    }

    @GetMapping("/{activityId}/me")
    public Result<LotteryMyRecordView> me(@AuthenticationPrincipal CurrentUser user,
                                          @PathVariable Long activityId) {
        return Result.ok(lotteryService.myRecord(user.id(), activityId));
    }

    @GetMapping("/my-prizes")
    public Result<PageResponse<LotteryPrizeRecordView>> myPrizes(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(lotteryService.myPrizes(user.id(), page, size));
    }

    @PutMapping("/claims/{claimId}/address")
    public Result<LotteryClaimView> saveClaimAddress(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long claimId,
            @Valid @RequestBody LotteryClaimAddressRequest request) {
        return Result.ok(lotteryService.saveClaimAddress(user.id(), claimId, request));
    }
}
