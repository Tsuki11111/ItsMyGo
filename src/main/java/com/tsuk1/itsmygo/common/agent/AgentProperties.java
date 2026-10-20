package com.tsuk1.itsmygo.common.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 客服智能体 gRPC 连接配置。密钥只放后端，绝不下发浏览器。 */
@ConfigurationProperties(prefix = "itsmygo.agent.grpc")
public record AgentProperties(
        String target,
        String key,
        int deadlineSeconds) {

    public AgentProperties {
        if (target == null || target.isBlank()) {
            target = "127.0.0.1:8003";
        }
        if (deadlineSeconds <= 0) {
            deadlineSeconds = 240;
        }
    }
}
