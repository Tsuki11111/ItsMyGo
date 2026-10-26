package com.tsuk1.itsmygo.common.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 智能体评测服务（rag_helper 的 8002，内网 HTTP）。
 * 密钥默认复用 agent 访问密钥；只留在后端，不下发浏览器。
 */
@ConfigurationProperties(prefix = "itsmygo.agent.eval")
public record AgentEvalProperties(String url, String key) {

    public AgentEvalProperties {
        if (url == null || url.isBlank()) {
            url = "http://127.0.0.1:8002";
        }
        if (key == null) {
            key = "";
        }
    }

    public boolean configured() {
        return !key.isBlank();
    }
}
