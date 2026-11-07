package com.tsuk1.itsmygo.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * sessionId 为空表示新会话；Java 会生成并记录归属。
 * {@code formResult} 是会话内表单的回传（方案 A）；老客户端不传为 null，按普通文本对话处理。
 */
public record AgentConverseRequest(
        String sessionId,
        @NotBlank(message = "消息不能为空")
        @Size(max = 2000, message = "消息不能超过 2000 字")
        String message,
        FormResult formResult) {

    /** 表单回传：formId 由服务端下发并校验一次性/过期。 */
    public record FormResult(String formId, Map<String, String> values) {
    }
}
