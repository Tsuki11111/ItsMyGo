package com.tsuk1.itsmygo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 会话列表/详情。顾客侧看店铺信息，商家侧看顾客信息，两边共用同一个结构。 */
public record ConversationView(
        Long id,
        Long merchantId,
        String merchantName,
        String merchantLogo,
        Long customerId,
        String customerName,
        String customerAvatar,
        Long lastMessageId,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        long unread,
        boolean peerOnline,
        LocalDateTime createdAt) {
}
