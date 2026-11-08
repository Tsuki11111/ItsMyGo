package com.tsuk1.itsmygo.dto.notification;

import java.time.LocalDateTime;

/** 一条站内通知。 */
public record NotificationView(
        Long id,
        String type,
        String title,
        String content,
        String link,
        boolean read,
        LocalDateTime createdAt) {
}
