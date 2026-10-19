package com.tsuk1.itsmygo.dto;

import java.util.List;

/** 消息分页；peerLastReadMessageId 用于渲染「已读 / 未读」。 */
public record ChatMessagePage(
        List<ChatMessageView> records,
        boolean hasMore,
        Long peerLastReadMessageId) {
}
