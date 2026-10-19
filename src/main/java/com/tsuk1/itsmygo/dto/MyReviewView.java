package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 用户中心「我的评价」列表项 */
public record MyReviewView(
        Long id,
        Long orderItemId,
        Long productId,
        String productName,
        String productImage,
        Long merchantId,
        String merchantName,
        Integer rating,
        String content,
        /** 1 展示 / 0 已被管理端隐藏 */
        Integer status,
        LocalDateTime createdAt
) {
}
