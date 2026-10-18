package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.MyReviewView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ReviewRequest;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 顾客端评价：提交、我的评价 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @RateLimit(scene = "review", limit = 5, windowSeconds = 60, byUser = true)
    @PostMapping
    public Result<Void> create(@AuthenticationPrincipal CurrentUser user,
                               @Valid @RequestBody ReviewRequest request) {
        reviewService.create(user.id(), request);
        return Result.ok();
    }

    @GetMapping("/mine")
    public Result<PageResponse<MyReviewView>> mine(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.mine(user.id(), page, size));
    }
}
