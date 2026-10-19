package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 顾客与店铺的唯一会话；一店一客只会有一条。 */
@Data
@TableName("conversations")
public class Conversation {

    public static final String SIDE_CUSTOMER = "CUSTOMER";
    public static final String SIDE_MERCHANT = "MERCHANT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerUserId;

    private Long merchantId;

    /** 空会话为 null，不进会话列表 */
    private Long lastMessageId;

    private String lastMessagePreview;

    private LocalDateTime lastMessageAt;

    private Integer customerUnread;

    private Integer merchantUnread;

    private Long customerLastReadMessageId;

    private Long merchantLastReadMessageId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
