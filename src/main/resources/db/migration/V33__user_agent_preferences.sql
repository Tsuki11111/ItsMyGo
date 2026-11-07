-- 顾客的客服设置：智能推广 / 联网搜索 / 流式输出。
-- 没有行 = 没改过设置，读取时用默认值（推广关、联网开、流式关），**不落库**；
-- 用户点「保存」才写这一行，避免给每个顾客都预先插一条。
-- last_promotion_date 给后续「智能推广」用（每天最多推荐一次），本次先建好列。
CREATE TABLE user_agent_preferences (
  user_id BIGINT PRIMARY KEY COMMENT '平台用户 ID',
  promotion_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '智能推广：默认关，用户主动开启',
  web_search_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '联网搜索：默认开，保持旧行为',
  stream_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '流式输出：默认关，保持旧行为',
  last_promotion_date DATE NULL COMMENT '上次智能推广日期（后续任务用）',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顾客的客服设置（买小购三开关）';
