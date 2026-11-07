package com.tsuk1.itsmygo.dto.agent;

import jakarta.validation.constraints.NotNull;

/**
 * 保存客服设置：三个开关**全量必传**（PUT 语义），缺字段直接 400。
 * 用包装类型 + {@code @NotNull}，避免 boolean 缺省成 false 被当成用户选择。
 */
public record AgentPreferenceRequest(
        @NotNull(message = "智能推广开关不能为空") Boolean promotionEnabled,
        @NotNull(message = "联网搜索开关不能为空") Boolean webSearchEnabled,
        @NotNull(message = "流式输出开关不能为空") Boolean streamEnabled) {
}
