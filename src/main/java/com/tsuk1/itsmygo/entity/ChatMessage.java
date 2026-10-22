package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 只追加的聊天消息；卡片内容存冻结快照，不回查商品/订单当前值。 */
@Data
@TableName("messages")
public class ChatMessage {

    public static final String SENDER_CUSTOMER = "CUSTOMER";
    public static final String SENDER_MERCHANT = "MERCHANT";
    /** 买小购客服消息（催发货等）；sender_user_id 仍记发起顾客 */
    public static final String SENDER_AGENT = "AGENT";

    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_PRODUCT = "PRODUCT";
    public static final String TYPE_ORDER = "ORDER";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long senderUserId;

    private String senderType;

    private String contentType;

    private String content;

    /** 商品/订单卡片 JSON 快照；非卡片为 null */
    private String cardData;

    private LocalDateTime createdAt;
}
