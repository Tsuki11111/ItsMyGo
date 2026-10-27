package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 公开商品提问，列表里带最多两条回答预览 */
public record ProductQuestionView(
        Long id,
        String content,
        String userNickname,
        String userAvatar,
        int answerCount,
        LocalDateTime createdAt,
        List<ProductAnswerView> answers) {
}
