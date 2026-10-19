package com.tsuk1.itsmygo.dto;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** 单条消息；card 是服务端生成并冻结的 JSON 快照，非卡片为 null。 */
public record ChatMessageView(
        Long id,
        Long conversationId,
        Long senderUserId,
        String senderType,
        String contentType,
        String content,
        JsonNode card,
        LocalDateTime createdAt) {
}
