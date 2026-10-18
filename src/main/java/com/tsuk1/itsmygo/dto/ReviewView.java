package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 商品详情页公开评价：只含展示所需字段，不暴露 userId */
public record ReviewView(
        Long id,
        Integer rating,
        String content,
        String userNickname,
        String userAvatar,
        LocalDateTime createdAt
) {
}
