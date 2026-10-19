package com.tsuk1.itsmygo.common.chat;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期给所有 SSE 连接发心跳，顺便清理已经断开的连接。 */
@Component
public class ChatHeartbeatJob {

    private final ChatStreamRegistry registry;

    public ChatHeartbeatJob(ChatStreamRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        registry.heartbeat();
    }
}
