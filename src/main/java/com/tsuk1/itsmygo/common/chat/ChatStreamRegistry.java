package com.tsuk1.itsmygo.common.chat;

import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.common.identity.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
        return register(user, new SseEmitter(0L));
    }

    /** 可注入 emitter 的重载：生产用上面的版本，测试用它模拟断连/不可用连接 */
    SseEmitter register(CurrentUser user, SseEmitter emitter) {
        Connection connection = new Connection(user, emitter);
        byUser.computeIfAbsent(user.id(), key -> new CopyOnWriteArraySet<>()).add(connection);

        emitter.onCompletion(() -> remove(connection));
        emitter.onTimeout(() -> remove(connection));
        emitter.onError(error -> remove(connection));

        if (presenceMerchantId(user) != null) {
            Long merchantId = presenceMerchantId(user);
            AtomicInteger count = merchantOnline.computeIfAbsent(merchantId, key -> new AtomicInteger());
            if (count.incrementAndGet() == 1) {
                broadcast(ChatEvent.PRESENCE, new ChatEvent.Presence(merchantId, true));
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

        if (presenceMerchantId(connection.user) != null) {
            Long merchantId = presenceMerchantId(connection.user);
            AtomicInteger count = merchantOnline.get(merchantId);
            if (count != null && count.decrementAndGet() <= 0) {
                merchantOnline.remove(merchantId, count);
                broadcast(ChatEvent.PRESENCE, new ChatEvent.Presence(merchantId, false));
            }
        }
    }

    /** 商家在线状态：普通商家看自己的店，管理员统一算作平台自营店在线 */
    private Long presenceMerchantId(CurrentUser user) {
        if (user.isAdmin()) {
            return Merchant.PLATFORM_MERCHANT_ID;
        }
        if (user.isMerchant() && user.merchantId() != null
                && user.merchantId() != Merchant.PLATFORM_MERCHANT_ID) {
            return user.merchantId();
        }
        return null;
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
                } catch (Exception e) {
                    // 客户端断连（含 Tomcat 异步 error dispatch 抛出的
                    // AsyncRequestNotUsableException）属于正常现象：静默清理，不记 ERROR 堆栈
                    fail(e);
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
                } catch (Exception e) {
                    fail(e);
                }
            }
        }

        /** 发送失败 = 连接已死：标记、移出注册表、关掉 emitter（不向外抛，避免污染 HTTP 响应） */
        private void fail(Exception e) {
            alive.set(false);
            log.debug("SSE 连接发送失败，按断连清理: userId={}, err={}",
                    user.id(), e.getMessage());
            remove(this);
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 连接已经断了，complete 失败无需处理
            }
        }
    }
}
