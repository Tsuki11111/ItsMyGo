package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AdminReviewView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ReviewVisibleRequest;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商家后台：只看/只隐藏本店商品的评价 */
@RestController
@RequestMapping("/api/merchant/reviews")
public class MerchantReviewController {

    private final ReviewService reviewService;

    public MerchantReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public Result<PageResponse<AdminReviewView>> page(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean visible,
            @RequestParam(required = false) String keyword) {
        return Result.ok(reviewService.pageForMerchant(requireMerchant(user), page, size,
                visible, keyword));
    }

    @PutMapping("/{id}/visible")
    public Result<Void> setVisible(@AuthenticationPrincipal CurrentUser user,
                                   @PathVariable Long id,
                                   @Valid @RequestBody ReviewVisibleRequest request) {
        reviewService.setVisibleForMerchant(requireMerchant(user), id, request.visible());
        return Result.ok();
    }

    private Long requireMerchant(CurrentUser user) {
        if (!user.isMerchant()) {
            throw new BizException(ResultCode.FORBIDDEN, "需要商家权限");
        }
        return user.merchantId();
    }
}
