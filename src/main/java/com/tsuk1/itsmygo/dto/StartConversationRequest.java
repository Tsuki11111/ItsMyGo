package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotNull;

/** 顾客发起会话；productId / orderId 用于新建会话时自动附一张卡片。 */
public record StartConversationRequest(
        @NotNull Long merchantId,
        Long productId,
        Long orderId) {
}
