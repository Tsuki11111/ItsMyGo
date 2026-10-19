package com.tsuk1.itsmygo.common.chat;

import com.tsuk1.itsmygo.dto.ChatMessageView;
import com.tsuk1.itsmygo.dto.ConversationView;

/** SSE 事件协议。事件名与前端 EventSource 监听保持一致。 */
public final class ChatEvent {

    public static final String CONNECTED = "connected";
    public static final String MESSAGE = "message";
    public static final String READ = "read";
    public static final String PRESENCE = "presence";

    private ChatEvent() {
    }

    public record Connected(Long userId, long customerUnread, long merchantUnread) {
    }

    /** scope 是接收方在这条会话里的身份；unread 是接收方的全局未读总数，前端直接用于角标。 */
    public record Message(String scope, ChatMessageView message,
                          ConversationView conversation, long unread) {
    }

    public record Read(Long conversationId, String readerType, Long lastReadMessageId) {
    }

    public record Presence(Long merchantId, boolean online) {
    }
}
