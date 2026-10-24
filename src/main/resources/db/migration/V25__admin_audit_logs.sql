-- 管理端操作审计（只追加）
CREATE TABLE admin_audit_logs
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    actor_user_id  BIGINT       NOT NULL COMMENT '操作人用户 ID',
    actor_username VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '操作人用户名快照',
    action         VARCHAR(64)  NOT NULL COMMENT '稳定动作 code，如 ORDER_SHIP',
    target_type    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '目标类型',
    target_id      BIGINT       NULL COMMENT '目标 ID；无具体目标时为空',
    summary        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '动作中文摘要',
    result         VARCHAR(16)  NOT NULL COMMENT 'SUCCESS / FAILED',
    error_message  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '失败原因',
    ip             VARCHAR(64)  NOT NULL DEFAULT '',
    user_agent     VARCHAR(512) NOT NULL DEFAULT '',
    trace_id       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '关联运行日志 traceId',
    detail         TEXT         NULL COMMENT 'method + URI + query，不含请求体',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_created (created_at),
    KEY idx_audit_action (action, created_at),
    KEY idx_audit_actor (actor_user_id, created_at),
    KEY idx_audit_result (result, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '管理端操作审计';

-- 最后管理员保护要按 role + status 查活跃管理员
ALTER TABLE users
    ADD KEY idx_user_role_status (role, status);
