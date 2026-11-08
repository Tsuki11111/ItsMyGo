package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.notification.NotificationListView;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 站内通知：商家端 / 平台管理端顶栏铃铛。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<NotificationListView> list(@AuthenticationPrincipal CurrentUser user,
                                             @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(notificationService.list(user.id(), limit));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(notificationService.unreadCount(user.id()));
    }

    @PostMapping("/{id}/read")
    public Result<Integer> markRead(@AuthenticationPrincipal CurrentUser user,
                                    @PathVariable Long id) {
        return Result.ok(notificationService.markRead(user.id(), id));
    }

    @PostMapping("/read-all")
    public Result<Integer> markAllRead(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(notificationService.markAllRead(user.id()));
    }
}
