package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.ChatMessagePage;
import com.tsuk1.itsmygo.dto.ChatMessageView;
import com.tsuk1.itsmygo.dto.ChatUnreadView;
import com.tsuk1.itsmygo.dto.ConversationView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.SendChatMessageRequest;
import com.tsuk1.itsmygo.dto.StartConversationRequest;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.security.CurrentUser;

public interface ChatService {

    ConversationView startConversation(CurrentUser user, StartConversationRequest request);

    PageResponse<ConversationView> customerConversations(CurrentUser user, int page, int size);

    ConversationView customerConversation(CurrentUser user, Long conversationId);

    ChatMessagePage customerMessages(CurrentUser user, Long conversationId,
                                     Long beforeId, Long afterId, int size);

    ChatMessageView customerSend(CurrentUser user, Long conversationId, SendChatMessageRequest request);

    /**
     * 客服动作内部调用：以「买小购」身份给店铺发一条文本消息（催发货等）。
     * senderType=AGENT，店铺侧未读 +1；不走浏览器发送接口，但同样推 SSE。
     */
    ChatMessageView sendAgentText(long customerUserId, long merchantId, String text);

    /**
     * 支付成功内部调用：在「顾客—店铺」会话里插一条 SYSTEM 消息提醒商家及时发货
     * （商家未读 +1、SSE 实时推送；平台自营店推给管理员）。
     * 店铺停用/无店主等无法通知的情况只记日志，不抛异常。
     */
    void notifyOrderPaid(Order order);

    /**
     * 支付成功内部调用：以商家身份给顾客发一条“请核对订单信息”的卡片
     * （contentType=ORDER_CONFIRM，发货前可改收货地址）；顾客未读 +1、SSE 实时推送。
     * 平台自营店由管理员代发。
     */
    ChatMessageView sendOrderConfirmRequest(Order order);

    /**
     * 智能推广内部调用：在「顾客—店铺」会话里以商家身份发一条 PROMOTION 商品卡。
     * 校验店铺 ACTIVE、商品在售且有库存；顾客未读 +1、SSE 实时推送。
     * 发送失败抛 {@code BizException}，由调用方决定是否记日志跳过。
     */
    ChatMessageView sendPromotionMessage(long userId, long merchantId, long productId, String reasonText);

    /** 顾客在发货前修改收货地址后，给商家发一条 SYSTEM 消息同步新地址。 */
    void notifyOrderAddressChanged(Order order);

    /** @return 标记已读后，该身份在全平台的未读总数（用于更新角标） */
    long customerRead(CurrentUser user, Long conversationId, Long lastMessageId);

    ChatUnreadView unread(CurrentUser user);

    PageResponse<ConversationView> merchantConversations(CurrentUser user, int page, int size);

    ConversationView merchantConversation(CurrentUser user, Long conversationId);

    ChatMessagePage merchantMessages(CurrentUser user, Long conversationId,
                                     Long beforeId, Long afterId, int size);

    ChatMessageView merchantSend(CurrentUser user, Long conversationId, SendChatMessageRequest request);

    long merchantRead(CurrentUser user, Long conversationId, Long lastMessageId);

    // ── 平台自营店：管理员以商家身份处理会话 ──

    PageResponse<ConversationView> adminConversations(CurrentUser user, int page, int size);

    ConversationView adminConversation(CurrentUser user, Long conversationId);

    ChatMessagePage adminMessages(CurrentUser user, Long conversationId,
                                  Long beforeId, Long afterId, int size);

    ChatMessageView adminSend(CurrentUser user, Long conversationId, SendChatMessageRequest request);

    long adminRead(CurrentUser user, Long conversationId, Long lastMessageId);
}
