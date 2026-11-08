package com.tsuk1.itsmygo.dto.notification;

import java.util.List;

/** 通知列表 + 未读数。 */
public record NotificationListView(long unreadCount, List<NotificationView> items) {
}
