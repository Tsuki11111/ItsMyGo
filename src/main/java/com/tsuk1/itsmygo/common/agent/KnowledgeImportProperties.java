package com.tsuk1.itsmygo.common.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库导入服务（rag_helper 的 8001，内网 HTTP）。
 * 密钥复用 agent 访问密钥；默认地址与导入服务一致。
 */
@ConfigurationProperties(prefix = "itsmygo.knowledge")
public record KnowledgeImportProperties(String importUrl, String key) {

    public KnowledgeImportProperties {
        if (importUrl == null || importUrl.isBlank()) {
            importUrl = "http://127.0.0.1:8001";
        }
        if (key == null) {
            key = "";
        }
    }

    public boolean configured() {
        return !key.isBlank();
    }
}
