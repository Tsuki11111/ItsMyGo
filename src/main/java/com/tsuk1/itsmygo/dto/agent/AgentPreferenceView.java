package com.tsuk1.itsmygo.dto.agent;

/** 客服设置（浏览器可见的三个开关）；last_promotion_date 属于服务端内部状态，不下发。 */
public record AgentPreferenceView(
        boolean promotionEnabled,
        boolean webSearchEnabled,
        boolean streamEnabled) {
}
