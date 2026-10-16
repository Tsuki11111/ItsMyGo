-- 支付流水：一笔订单一条成功支付记录。
-- 之前只在 orders 上改个状态，没有流水可查 —— 对账、退款、排查都要靠它。
CREATE TABLE payments
(
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '支付记录 ID',
    payment_no  VARCHAR(32)    NOT NULL COMMENT '支付流水号（对外展示）',
    order_id    BIGINT         NOT NULL COMMENT '对应订单',
    order_no    VARCHAR(32)    NOT NULL COMMENT '订单号（冗余，排查问题时不用再 join）',
    user_id     BIGINT         NOT NULL COMMENT '付款用户',
    amount      DECIMAL(10, 2) NOT NULL COMMENT '支付金额',
    method      VARCHAR(16)    NOT NULL COMMENT 'MOCK / ALIPAY / WECHAT',
    status      VARCHAR(16)    NOT NULL COMMENT 'SUCCESS / REFUNDED',
    paid_at     DATETIME       NOT NULL COMMENT '支付时间',
    refunded_at DATETIME       NULL COMMENT '退款时间',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    -- 一个订单只能有一条支付记录：即使并发请求绕过了状态机，数据库这一关也拦得住
    UNIQUE KEY uk_order_id (order_id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '支付流水';

-- 退款被驳回时要回到申请前的状态，所以得把那个状态记下来
ALTER TABLE orders
    ADD COLUMN status_before_refund VARCHAR(16) NULL
        COMMENT '申请退款前的状态，仅 REFUNDING 期间有值' AFTER tracking_no;
