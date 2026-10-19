package com.tsuk1.itsmygo.common.chat;

import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.security.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单实例 SSE 连接注册表 + 商家在线状态。
 *
 * 多实例部署需要 Redis Pub/Sub 或粘性会话，本期按单机设计；
 * SseEmitter 不是线程安全的，每条连接发送时都单独加锁。
 */
@Slf4j
@Component
public class ChatStreamRegistry {

    private final Map<Long, Set<Connection>> byUser = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> merchantOnline = new ConcurrentHashMap<>();

    public SseEmitter register(CurrentUser user) {
        SseEmitter emitter = new SseEmitter(0L);
        Connection connection = new Connection(user, emitter);
        byUser.computeIfAbsent(user.id(), key -> new CopyOnWriteArraySet<>()).add(connection);

        emitter.onCompletion(() -> remove(connection));
        emitter.onTimeout(() -> remove(connection));
        emitter.onError(error -> remove(connection));

        if (isMerchant(user)) {
            AtomicInteger count = merchantOnline.computeIfAbsent(user.merchantId(), key -> new AtomicInteger());
            if (count.incrementAndGet() == 1) {
                broadcast(ChatEvent.PRESENCE, new ChatEvent.Presence(user.merchantId(), true));
            }
        }
        return emitter;
    }

    /** 推给某个用户的所有标签页 */
    public void sendToUser(Long userId, String event, Object data) {
        Set<Connection> connections = byUser.get(userId);
        if (connections == null || connections.isEmpty()) {
            return;
        }
        for (Connection connection : connections) {
            connection.send(event, data);
        }
    }

    public void broadcast(String event, Object data) {
        for (Set<Connection> connections : byUser.values()) {
            for (Connection connection : connections) {
                connection.send(event, data);
            }
        }
    }

    public boolean isMerchantOnline(Long merchantId) {
        AtomicInteger count = merchantOnline.get(merchantId);
        return count != null && count.get() > 0;
    }

    /** 心跳：发送失败会顺带清理死连接 */
    public void heartbeat() {
        for (Set<Connection> connections : byUser.values()) {
            for (Connection connection : connections) {
                connection.ping();
            }
        }
    }

    private void remove(Connection connection) {
        if (!connection.removed.compareAndSet(false, true)) {
            return;
        }
        Set<Connection> connections = byUser.get(connection.user.id());
        if (connections != null) {
            connections.remove(connection);
            if (connections.isEmpty()) {
                byUser.remove(connection.user.id(), connections);
            }
        }

        if (isMerchant(connection.user)) {
            AtomicInteger count = merchantOnline.get(connection.user.merchantId());
            if (count != null && count.decrementAndGet() <= 0) {
                merchantOnline.remove(connection.user.merchantId(), count);
                broadcast(ChatEvent.PRESENCE, new ChatEvent.Presence(connection.user.merchantId(), false));
            }
        }
    }

    private boolean isMerchant(CurrentUser user) {
        return user.isMerchant() && user.merchantId() != null
                && user.merchantId() != Merchant.PLATFORM_MERCHANT_ID;
    }

    private final class Connection {

        private final CurrentUser user;
        private final SseEmitter emitter;
        private final Object writeLock = new Object();
        private final AtomicBoolean removed = new AtomicBoolean(false);
        private final AtomicBoolean alive = new AtomicBoolean(true);

        private Connection(CurrentUser user, SseEmitter emitter) {
            this.user = user;
            this.emitter = emitter;
        }

        private void send(String event, Object data) {
            if (!alive.get()) {
                return;
            }
            synchronized (writeLock) {
                try {
                    emitter.send(SseEmitter.event().name(event).data(data));
                } catch (IOException | IllegalStateException e) {
                    alive.set(false);
                    remove(this);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // 连接已经断了，complete 失败无需处理
                    }
                }
            }
        }

        private void ping() {
            if (!alive.get()) {
                return;
            }
            synchronized (writeLock) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    alive.set(false);
                    remove(this);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // 同上
                    }
                }
            }
        }
    }
}
