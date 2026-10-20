-- 客服智能体会话归属：Java 侧为每个用户创建并校验 session_id
CREATE TABLE agent_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '平台用户 ID',
  session_id VARCHAR(64) NOT NULL COMMENT '买小购会话 ID（Java 生成 UUID）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_active_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_id (session_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服智能体会话归属';
