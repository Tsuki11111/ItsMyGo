package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知（商家端 / 平台管理端铃铛）。 */
@Data
@TableName("notifications")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 users.id */
    private Long userId;

    /** ORDER_PAID / AFTER_SALE_APPLIED / AFTER_SALE_RESULT / MERCHANT_APPLY / AGENT_EVAL_DONE */
    private String type;

    private String title;

    private String content;

    /** 点击跳转的前端路由 */
    private String link;

    /** 业务幂等键：同一接收人 + bizKey 只插一次 */
    private String bizKey;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
