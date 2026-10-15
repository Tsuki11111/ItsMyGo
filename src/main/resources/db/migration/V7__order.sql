CREATE TABLE orders
(
    id           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '订单 ID',
    order_no     VARCHAR(32)    NOT NULL COMMENT '订单号（对外展示）',
    user_id      BIGINT         NOT NULL COMMENT '下单用户',
    total_amount DECIMAL(10, 2) NOT NULL COMMENT '订单总额',
    status       VARCHAR(16)    NOT NULL COMMENT 'PENDING_PAYMENT / PAID / SHIPPED / COMPLETED / CANCELLED',
    -- 收货信息一律快照：用户事后改了或删了地址，历史订单不能跟着变
    receiver     VARCHAR(32)    NOT NULL COMMENT '收货人（下单时快照）',
    phone        VARCHAR(20)    NOT NULL COMMENT '联系电话（快照）',
    address      VARCHAR(255)   NOT NULL COMMENT '完整收货地址（快照）',
    tracking_no  VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '物流单号',
    paid_at      DATETIME       NULL COMMENT '支付时间',
    shipped_at   DATETIME       NULL COMMENT '发货时间',
    completed_at DATETIME       NULL COMMENT '完成时间',
    cancelled_at DATETIME       NULL COMMENT '取消时间',
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '订单';

CREATE TABLE order_items
(
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '订单项 ID',
    order_id      BIGINT         NOT NULL COMMENT '所属订单',
    product_id    BIGINT         NOT NULL COMMENT '商品 ID（仅供追溯，展示时不要 join 取当前值）',
    -- 以下三项是订单快照：商品改价改名后，历史订单必须保持原样
    product_name  VARCHAR(128)   NOT NULL COMMENT '商品名（下单时快照）',
    product_image VARCHAR(512)   NOT NULL DEFAULT '' COMMENT '主图（下单时快照）',
    unit_price    DECIMAL(10, 2) NOT NULL COMMENT '单价（下单时快照）',
    quantity      INT            NOT NULL COMMENT '数量',
    subtotal      DECIMAL(10, 2) NOT NULL COMMENT '小计 = unit_price × quantity',
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '订单项';
