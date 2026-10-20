package com.tsuk1.itsmygo.dto.agent;

import jakarta.validation.constraints.NotBlank;

public record AgentStopRequest(
        @NotBlank(message = "会话 ID 不能为空") String sessionId,
        @NotBlank(message = "runId 不能为空") String runId) {
}
