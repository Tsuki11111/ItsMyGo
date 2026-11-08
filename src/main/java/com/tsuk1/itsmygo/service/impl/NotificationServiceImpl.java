package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.dto.notification.NotificationListView;
import com.tsuk1.itsmygo.dto.notification.NotificationView;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Notification;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.NotificationMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.NotificationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 站内通知实现：通知写入与铃铛读取。 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_LIST = 50;

    private final NotificationMapper notificationMapper;
    private final MerchantMapper merchantMapper;
    private final UserMapper userMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   MerchantMapper merchantMapper,
                                   UserMapper userMapper) {
        this.notificationMapper = notificationMapper;
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public void notifyUser(Long userId, String type, String title, String content, String link, String bizKey) {
        if (userId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content == null ? "" : content);
        notification.setLink(link == null ? "" : link);
        notification.setBizKey(bizKey);
        notification.setCreatedAt(LocalDateTime.now());
        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException ignored) {
            // 同一 user + bizKey 已存在：幂等事件（重复支付回调等），忽略
        }
    }

    @Override
    @Transactional
    public void notifyMerchant(Long merchantId, String type, String title, String content, String link, String bizKey) {
        if (merchantId == null) {
            return;
        }
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return;
        }
        if (merchant.getOwnerUserId() != null) {
            notifyUser(merchant.getOwnerUserId(), type, title, content, link, bizKey);
            return;
        }
        // 平台自营店：商家侧消息由全部管理员接收（与聊天未读同口径）
        if (Objects.equals(merchant.getId(), Merchant.PLATFORM_MERCHANT_ID)) {
            notifyAdmins(type, title, content, link, bizKey);
        }
    }

    @Override
    @Transactional
    public void notifyAdmins(String type, String title, String content, String link, String bizKey) {
        List<User> admins = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getRole, Roles.ADMIN)
                .eq(User::getStatus, 1));
        for (User admin : admins) {
            notifyUser(admin.getId(), type, title, content, link, bizKey);
        }
    }

    @Override
    public NotificationListView list(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIST);
        List<Notification> recent = notificationMapper.selectList(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
                .orderByDesc(Notification::getId)
                .last("LIMIT " + safeLimit));
        List<NotificationView> items = recent.stream()
                .map(item -> new NotificationView(item.getId(), item.getType(), item.getTitle(),
                        item.getContent(), item.getLink(), item.getReadAt() != null, item.getCreatedAt()))
                .toList();
        return new NotificationListView(unreadCount(userId), items);
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt));
    }

    @Override
    public int markRead(Long userId, Long id) {
        return notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now()));
    }

    @Override
    public int markAllRead(Long userId) {
        return notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now()));
    }
}
