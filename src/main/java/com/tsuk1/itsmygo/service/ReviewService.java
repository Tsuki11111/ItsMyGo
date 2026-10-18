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

    void setVisible(Long reviewId, boolean visible);
}
