package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.ChatMessagePage;
import com.tsuk1.itsmygo.dto.ChatMessageView;
import com.tsuk1.itsmygo.dto.ChatUnreadView;
import com.tsuk1.itsmygo.dto.ConversationView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.SendChatMessageRequest;
import com.tsuk1.itsmygo.dto.StartConversationRequest;
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
