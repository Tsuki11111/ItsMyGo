package com.tsuk1.itsmygo.common.chat;

import com.tsuk1.itsmygo.common.identity.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 断连回归：浏览器断开后 Tomcat 的异步 error dispatch 会抛
 * {@link AsyncRequestNotUsableException}，推送侧必须静默清理，
 * 不能让异常冒到全局异常处理器被记成 ERROR。
 */
class ChatStreamRegistryTest {

    private final CurrentUser user = new CurrentUser(42L, "buyer", "CUSTOMER");

    @Test
    void broadcastToDisconnectedClientIsSilentlyDropped() {
        ChatStreamRegistry registry = new ChatStreamRegistry();
        registry.register(user, new DeadEmitter());
        assertDoesNotThrow(() -> registry.broadcast(ChatEvent.CONNECTED, Map.of("a", 1)));
        assertDoesNotThrow(registry::heartbeat);
    }

    @Test
    void liveConnectionStillReceivesAfterDeadOne() {
        ChatStreamRegistry registry = new ChatStreamRegistry();
        RecordingEmitter live = new RecordingEmitter();
        registry.register(user, live);
        registry.register(user, new DeadEmitter());
        registry.sendToUser(user.id(), "chat", Map.of("k", "v"));
        assertFalse(live.events.isEmpty(), "活连接应继续收到推送");
    }

    /** 模拟客户端已断开：send 抛出 Tomcat/容器侧包装后的运行时异常 */
    static class DeadEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) {
            throw new java.io.UncheckedIOException(new IOException("你的主机中的软件中止了一个已建立的连接"));
        }
    }

    static class RecordingEmitter extends SseEmitter {
        final List<String> events = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            events.add(String.valueOf(builder.build()));
        }
    }
}
