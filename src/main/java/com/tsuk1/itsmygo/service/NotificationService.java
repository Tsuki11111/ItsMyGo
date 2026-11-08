package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.notification.NotificationListView;

/** 站内通知：写入（业务事件触发）与读取（铃铛接口）。 */
public interface NotificationService {

    /** 单人通知；bizKey 非空时同一接收人同键只插一次（幂等） */
    void notifyUser(Long userId, String type, String title, String content, String link, String bizKey);

    /** 通知店铺负责人；平台自营店通知全部管理员（两者用各自的跳转链接） */
    void notifyMerchant(Long merchantId, String type, String title, String content,
                        String merchantLink, String adminLink, String bizKey);

    /** 通知全部管理员（平台后台铃铛） */
    void notifyAdmins(String type, String title, String content, String link, String bizKey);

    /** 最近通知 + 未读数 */
    NotificationListView list(Long userId, int limit);

    /** 未读总数 */
    long unreadCount(Long userId);

    /** 标记单条已读（只允许本人的通知），返回影响行数 */
    int markRead(Long userId, Long id);

    /** 全部已读，返回影响行数 */
    int markAllRead(Long userId);
}
