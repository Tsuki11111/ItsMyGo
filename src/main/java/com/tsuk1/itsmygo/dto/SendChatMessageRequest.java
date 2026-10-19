package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发送消息。卡片只给 ID，由服务端查库生成快照 —— 客户端不能伪造卡片内容。
 * type=TEXT 用 text；IMAGE 用 imageUrl；PRODUCT/ORDER 用对应 ID。
 */
public record SendChatMessageRequest(
        @NotBlank String type,
        String text,
        String imageUrl,
        Long productId,
        Long orderId) {
}
