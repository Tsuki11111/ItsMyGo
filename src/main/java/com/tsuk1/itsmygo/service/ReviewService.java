package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.AdminReviewView;
import com.tsuk1.itsmygo.dto.MyReviewView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ReviewRequest;
import com.tsuk1.itsmygo.dto.ReviewSummary;
import com.tsuk1.itsmygo.dto.ReviewView;

/** 商品评价 */
public interface ReviewService {

    void create(Long userId, ReviewRequest request);

    PageResponse<MyReviewView> mine(Long userId, int page, int size);

    /** 商品详情页公开评价，只返回未隐藏的 */
    PageResponse<ReviewView> productReviews(Long productId, int page, int size);

    ReviewSummary summary(Long productId);

    PageResponse<AdminReviewView> pageForAdmin(int page, int size, Boolean visible, String keyword);

    /** 平台可按商户筛选 */
    PageResponse<AdminReviewView> pageForAdmin(int page, int size, Boolean visible, String keyword,
                                               Long merchantId);

    void setVisible(Long reviewId, boolean visible);

    // ── 商家端 ──

    PageResponse<AdminReviewView> pageForMerchant(Long merchantId, int page, int size,
                                                  Boolean visible, String keyword);

    void setVisibleForMerchant(Long merchantId, Long reviewId, boolean visible);
}
