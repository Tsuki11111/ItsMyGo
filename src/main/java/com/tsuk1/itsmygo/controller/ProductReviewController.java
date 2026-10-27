package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ReviewSummary;
import com.tsuk1.itsmygo.dto.ReviewView;
import com.tsuk1.itsmygo.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价的公开读接口。
 * 路径挂在 `/api/products/**` 下，由 SecurityConfig 统一放行匿名访问。
 */
@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ProductReviewController {

    private final ReviewService reviewService;

    public ProductReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public Result<PageResponse<ReviewView>> page(@PathVariable Long productId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "5") int size,
                                                 @RequestParam(required = false) Integer rating) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BizException(ResultCode.BAD_REQUEST, "星级只能是 1–5");
        }
        return Result.ok(reviewService.productReviews(productId, page, size, rating));
    }

    @GetMapping("/summary")
    public Result<ReviewSummary> summary(@PathVariable Long productId) {
        return Result.ok(reviewService.summary(productId));
    }
}
