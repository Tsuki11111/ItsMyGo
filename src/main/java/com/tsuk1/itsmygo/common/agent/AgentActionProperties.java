package com.tsuk1.itsmygo.common.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 智能下单 / 退款动作的开关与有效期。 */
@ConfigurationProperties(prefix = "itsmygo.agent.actions")
public record AgentActionProperties(Boolean enabled, Integer expirySeconds) {

    public AgentActionProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (expirySeconds == null || expirySeconds <= 0) {
            expirySeconds = 600;
        }
    }
}
