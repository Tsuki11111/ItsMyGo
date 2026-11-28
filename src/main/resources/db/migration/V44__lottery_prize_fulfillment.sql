-- 抽奖奖品下发（2026-11-28，architecture D42–D45）
--
-- 1) 资格从「每人一次」改为「每人每天一次」：
--    draw_date 进唯一键，Redis 资格 Set 也按天区分（见 LotteryRedisStore）。
-- 2) 奖品种类化：COUPON 自动发券 / PHYSICAL 生成领取单。
-- 3) 券来源区分：领券中心与抽奖可以各持有一张同款券。
-- 4) 实物奖品领取单：用户填地址 → 管理端填运单号发货。

ALTER TABLE lottery_draw_records
    ADD COLUMN draw_date DATE NOT NULL DEFAULT '1970-01-01' COMMENT '抽奖日期（每人每天一次）' AFTER user_id;

UPDATE lottery_draw_records SET draw_date = DATE(created_at);

ALTER TABLE lottery_draw_records
    ALTER COLUMN draw_date DROP DEFAULT;

ALTER TABLE lottery_draw_records
    DROP INDEX uk_activity_user,
    ADD UNIQUE KEY uk_activity_user_day (activity_id, user_id, draw_date);

ALTER TABLE lottery_prizes
    ADD COLUMN prize_type VARCHAR(16) NOT NULL DEFAULT 'PHYSICAL'
        COMMENT 'COUPON 优惠券 / PHYSICAL 实物' AFTER image,
    ADD COLUMN coupon_id BIGINT NULL
        COMMENT '券类奖品关联的券模板' AFTER prize_type;

ALTER TABLE user_coupons
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'CLAIM'
        COMMENT 'CLAIM 领券中心 / LOTTERY 抽奖' AFTER coupon_id,
    ADD COLUMN source_id BIGINT NOT NULL DEFAULT 0
        COMMENT '来源业务 id；抽奖为抽奖记录 id' AFTER source;

ALTER TABLE user_coupons
    DROP INDEX uk_user_coupon,
    ADD UNIQUE KEY uk_user_coupon_source (user_id, coupon_id, source, source_id);

CREATE TABLE lottery_prize_claims
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '领取单 ID',
    draw_record_id   BIGINT       NOT NULL COMMENT '中奖记录',
    activity_id      BIGINT       NOT NULL COMMENT '活动 ID',
    prize_id         BIGINT       NOT NULL COMMENT '奖项 ID',
    user_id          BIGINT       NOT NULL COMMENT '中奖用户',
    status           VARCHAR(24)  NOT NULL DEFAULT 'PENDING_ADDRESS'
        COMMENT 'PENDING_ADDRESS 待填地址 / PENDING_SHIPMENT 待发货 / SHIPPED 已发货',
    receiver_name    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '收货人快照',
    receiver_phone   VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '收货电话快照',
    receiver_address VARCHAR(255) NOT NULL DEFAULT '' COMMENT '完整收货地址快照',
    tracking_no      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '快递单号',
    shipped_at       DATETIME     NULL COMMENT '发货时间',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_record (draw_record_id),
    KEY idx_activity_status (activity_id, status),
    KEY idx_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '抽奖实物奖品领取单';
