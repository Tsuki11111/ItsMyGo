-- 满减优惠券：模板 + 用户券 + 订单优惠快照。
--
-- 关键设计：
--   * user_coupons 在**领取时**快照名称/门槛/减免/有效期 —— 模板后续改价不影响已领的券
--   * uk_user_coupon 保证每人每券只能领一张
--   * uk_used_order 保证一张订单最多用一张券（MySQL 唯一索引允许多个 NULL）
--   * orders.total_amount 继续表示**实付**；original_amount 是优惠前商品总额
--   * 订单上的 coupon_name / discount_amount 是快照 —— 券退回后可能被新订单复用，
--     历史订单展示只能读这里，不能 join user_coupons

CREATE TABLE coupons
(
    id               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '券模板 ID',
    name             VARCHAR(64)    NOT NULL COMMENT '券名称',
    threshold_amount DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '满多少可用',
    discount_amount  DECIMAL(10, 2) NOT NULL COMMENT '减多少',
    total_count      INT            NOT NULL COMMENT '发放总量',
    claimed_count    INT            NOT NULL DEFAULT 0 COMMENT '已领取数',
    valid_from       DATETIME       NOT NULL COMMENT '可领取/可使用开始时间',
    valid_to         DATETIME       NOT NULL COMMENT '可领取/可使用结束时间',
    status           TINYINT        NOT NULL DEFAULT 1 COMMENT '1 可领 / 0 停用',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_valid (status, valid_to)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '优惠券模板';

CREATE TABLE user_coupons
(
    id               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '用户券 ID',
    user_id          BIGINT         NOT NULL COMMENT '用户 ID',
    coupon_id        BIGINT         NOT NULL COMMENT '券模板 ID',
    coupon_name      VARCHAR(64)    NOT NULL COMMENT '领取时的券名称快照',
    threshold_amount DECIMAL(10, 2) NOT NULL COMMENT '领取时的门槛快照',
    discount_amount  DECIMAL(10, 2) NOT NULL COMMENT '领取时的减免快照',
    valid_from       DATETIME       NOT NULL COMMENT '领取时的有效期快照',
    valid_to         DATETIME       NOT NULL COMMENT '领取时的有效期快照',
    status           VARCHAR(16)    NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED / USED / EXPIRED',
    used_order_id    BIGINT         NULL COMMENT '使用它的订单',
    used_at          DATETIME       NULL,
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupon (user_id, coupon_id),
    UNIQUE KEY uk_used_order (used_order_id),
    KEY idx_user_status (user_id, status, valid_to)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '用户优惠券';

ALTER TABLE orders
    ADD COLUMN original_amount  DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '优惠前商品总额' AFTER total_amount,
    ADD COLUMN discount_amount  DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '优惠金额' AFTER original_amount,
    ADD COLUMN user_coupon_id   BIGINT         NULL COMMENT '使用的用户券 ID' AFTER discount_amount,
    ADD COLUMN coupon_name      VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '券名称快照' AFTER user_coupon_id;

-- 存量订单没有优惠：原价就是实付
UPDATE orders SET original_amount = total_amount;
