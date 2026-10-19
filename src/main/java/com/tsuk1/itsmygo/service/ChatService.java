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

    /** @return 标记已读后，该身份在全平台的未读总数（用于更新角标） */
    long customerRead(CurrentUser user, Long conversationId, Long lastMessageId);

    ChatUnreadView unread(CurrentUser user);

    PageResponse<ConversationView> merchantConversations(CurrentUser user, int page, int size);

    ConversationView merchantConversation(CurrentUser user, Long conversationId);

    ChatMessagePage merchantMessages(CurrentUser user, Long conversationId,
                                     Long beforeId, Long afterId, int size);

    ChatMessageView merchantSend(CurrentUser user, Long conversationId, SendChatMessageRequest request);

    long merchantRead(CurrentUser user, Long conversationId, Long lastMessageId);
}
