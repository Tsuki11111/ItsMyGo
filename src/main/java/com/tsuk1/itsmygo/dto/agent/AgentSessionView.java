package com.tsuk1.itsmygo.dto.agent;

/** 买小购会话列表项（来自 agent 的 ListSessions，已按 user_id 过滤）。 */
public record AgentSessionView(
        String sessionId,
        String title,
        int count,
        String lastAt) {
}
