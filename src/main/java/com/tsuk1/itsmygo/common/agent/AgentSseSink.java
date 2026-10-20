package com.tsuk1.itsmygo.common.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单条客服 SSE 流的发送器：SseEmitter 不是线程安全的，
 * worker 与心跳线程都通过这里的锁发送；断线后静默关闭。
 */
@Slf4j
public class AgentSseSink {

    private static final long HEARTBEAT_SECONDS = 20;

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final Object writeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private ScheduledFuture<?> heartbeat;

    public AgentSseSink(SseEmitter emitter, ObjectMapper objectMapper,
                        ScheduledExecutorService scheduler) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
    }

    public void start() {
        // 先发一个 comment 冲刷响应头，避免浏览器/代理攒着不吐首帧
        sendComment("connected");
        heartbeat = scheduler.scheduleAtFixedRate(
                () -> sendComment("ping"),
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    public boolean send(String event, Map<String, Object> data) {
        if (closed.get()) {
            return false;
        }
        synchronized (writeLock) {
            if (closed.get()) {
                return false;
            }
            try {
                emitter.send(SseEmitter.event()
                        .name(event)
                        .data(objectMapper.writeValueAsString(data)));
                return true;
            } catch (Exception e) {
                log.debug("客服 SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
                close();
                return false;
            }
        }
    }

    private void sendComment(String comment) {
        if (closed.get()) {
            return;
        }
        synchronized (writeLock) {
            if (closed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment(comment));
            } catch (Exception e) {
                close();
            }
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (heartbeat != null) {
            heartbeat.cancel(false);
            heartbeat = null;
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 客户端已断开
        }
    }
}
