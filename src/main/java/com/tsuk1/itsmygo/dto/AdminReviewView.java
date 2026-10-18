package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 管理端评价列表项 */
public record AdminReviewView(
        Long id,
        Long userId,
        String userNickname,
        Long productId,
        String productName,
        Integer rating,
        String content,
        Integer status,
        LocalDateTime createdAt
) {
}
