package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 公开商品回答；所有回答者都经过购买校验，前端统一展示「已购买」 */
public record ProductAnswerView(
        Long id,
        String content,
        String userNickname,
        String userAvatar,
        LocalDateTime createdAt) {
}
