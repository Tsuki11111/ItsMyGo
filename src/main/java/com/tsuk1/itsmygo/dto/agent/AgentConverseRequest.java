package com.tsuk1.itsmygo.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** sessionId 为空表示新会话；Java 会生成并记录归属。 */
public record AgentConverseRequest(
        String sessionId,
        @NotBlank(message = "消息不能为空")
        @Size(max = 2000, message = "消息不能超过 2000 字")
        String message) {
}
