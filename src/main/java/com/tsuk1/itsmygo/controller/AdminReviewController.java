package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.dto.AdminReviewView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ReviewVisibleRequest;
import com.tsuk1.itsmygo.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端：评价审核（只做显示/隐藏，不删除） */
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public Result<PageResponse<AdminReviewView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean visible,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long merchantId) {
        return Result.ok(reviewService.pageForAdmin(page, size, visible, keyword, merchantId));
    }

    @PutMapping("/{id}/visible")
    @AdminAudit(action = "REVIEW_VISIBILITY", label = "修改评价展示状态", targetType = "REVIEW")
    public Result<Void> setVisible(@PathVariable Long id,
                                   @Valid @RequestBody ReviewVisibleRequest request) {
        reviewService.setVisible(id, request.visible());
        return Result.ok();
    }
}
