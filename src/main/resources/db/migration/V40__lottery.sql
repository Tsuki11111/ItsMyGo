-- 抽奖模块：活动、奖项、每次抽奖记录。
--
-- 设计要点（architecture D38）：
--   · Redis Set 负责快速资格判断与实时奖池库存；
--   · MySQL 负责活动 / 奖项配置与每次抽奖的最终记录；
--   · lottery_draw_records 的 uk_activity_user 是「每人只能抽一次」的最终兜底。

CREATE TABLE lottery_activities
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动 ID',
    name       VARCHAR(100) NOT NULL COMMENT '活动名称',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 进行中 / 2 已结束',
    start_at   DATETIME     NOT NULL COMMENT '开始时间',
    end_at     DATETIME     NOT NULL COMMENT '结束时间；Redis 缓存 TTL 以此为基准',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_end (status, end_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '抽奖活动';

CREATE TABLE lottery_prizes
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '奖项 ID',
    activity_id BIGINT       NOT NULL COMMENT '所属活动',
    name        VARCHAR(100) NOT NULL COMMENT '奖项名称',
    level       INT          NOT NULL DEFAULT 1 COMMENT '奖项等级（仅展示）',
    stock       INT          NOT NULL COMMENT '初始库存；实时剩余以 Redis SCARD 为准',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_activity (activity_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '抽奖奖项';

CREATE TABLE lottery_draw_records
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '记录 ID',
    activity_id BIGINT      NOT NULL COMMENT '活动 ID',
    user_id     BIGINT      NOT NULL COMMENT '用户 ID',
    prize_id    BIGINT      NULL COMMENT '奖项 ID；NULL 表示谢谢参与',
    prize_code  VARCHAR(64) NULL COMMENT '奖券码；NULL 表示谢谢参与',
    result      VARCHAR(16) NOT NULL COMMENT 'WIN / THANKS',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 一人一活动只能有一条记录：Redis 资格集合丢失时，由这个唯一键兜底
    UNIQUE KEY uk_activity_user (activity_id, user_id),
    -- 奖券码全局唯一；MySQL 允许多个 NULL，所以谢谢参与记录不受影响
    UNIQUE KEY uk_prize_code (prize_code),
    KEY idx_activity_prize (activity_id, prize_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '抽奖记录（每次抽奖一条，含谢谢参与）';
