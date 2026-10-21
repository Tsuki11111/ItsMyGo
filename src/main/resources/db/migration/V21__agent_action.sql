-- 客服智能体的待确认动作（M9/M10）
--
-- 设计要点：
--   * payload_json 是**服务端快照**，确认时只按它执行；浏览器/模型都改不了
--   * 确认接口只收 action_no，不收业务参数 —— 用户看到什么就执行什么
--   * 高危动作（下单 / 退款申请）必须用户点确认；催办类低危动作以后可复用同表
--   * expires_at 兜底：草稿过期后即使被确认，也不会执行
CREATE TABLE agent_actions
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '内部自增 ID',
    action_no     VARCHAR(32)  NOT NULL COMMENT '对外动作号（UUID）',
    user_id       BIGINT       NOT NULL COMMENT '发起用户',
    session_id    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '客服会话 ID',
    target_id     BIGINT       NULL COMMENT '关联业务对象（如订单 id）',
    type          VARCHAR(32)  NOT NULL COMMENT 'CREATE_ORDER / APPLY_REFUND',
    status        VARCHAR(32)  NOT NULL COMMENT 'PENDING_CONFIRM / EXECUTING / SUCCEEDED / FAILED / CANCELLED / EXPIRED',
    payload_json  TEXT         NOT NULL COMMENT '服务端执行快照（浏览器不可改）',
    summary_json  TEXT         NOT NULL COMMENT '确认卡片展示快照',
    result_json   TEXT         NULL COMMENT '执行结果',
    error_message VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
    expires_at    DATETIME     NOT NULL COMMENT '确认截止时间',
    confirmed_at  DATETIME     NULL COMMENT '用户确认时间',
    executed_at   DATETIME     NULL COMMENT '执行完成时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_action_no (action_no),
    KEY idx_user_status (user_id, status),
    KEY idx_target (user_id, type, target_id, status),
    KEY idx_session (session_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '客服智能体的待确认动作';
