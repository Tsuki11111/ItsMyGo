-- 平台政策：MySQL 保存政策原文与章节，作为事实源；Milvus 只做章节语义召回。
CREATE TABLE platform_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_key VARCHAR(64) NOT NULL COMMENT '政策短标识，如 terms/privacy/refund/merchant',
    title VARCHAR(255) NOT NULL,
    category VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL DEFAULT '1.0',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    content_md MEDIUMTEXT NOT NULL COMMENT '完整 Markdown 原文',
    effective_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_policy_key (policy_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE platform_policy_section (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    section_no INT NOT NULL,
    heading VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    UNIQUE KEY uk_platform_policy_section (policy_id, section_no),
    KEY idx_platform_policy_section_policy (policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
