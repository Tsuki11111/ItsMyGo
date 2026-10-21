package com.tsuk1.itsmygo.dto.agent;

import java.time.LocalDateTime;

/** 待确认动作的展示视图。summary / result 是自由形状的 JSON 对象。 */
public record AgentActionView(
        String actionNo,
        String type,
        String status,
        Object summary,
        Object result,
        String errorMessage,
        LocalDateTime expiresAt,
        LocalDateTime createdAt) {
}
