-- 站内通知（商家端 / 平台管理端铃铛）
-- 商家：新订单支付待发货、新售后申请、售后超时/退款结果
-- 管理员：商家入驻待审核、智能体评测完成等
CREATE TABLE notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '接收人 users.id',
    type        VARCHAR(40)  NOT NULL COMMENT 'ORDER_PAID / AFTER_SALE_APPLIED / AFTER_SALE_RESULT / MERCHANT_APPLY / AGENT_EVAL_DONE 等',
    title       VARCHAR(120) NOT NULL,
    content     VARCHAR(500) NOT NULL DEFAULT '',
    link        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '点击跳转的前端路由',
    biz_key     VARCHAR(120) NULL COMMENT '业务幂等键（同一接收人+键只插一次）',
    read_at     DATETIME     NULL DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_user_biz (user_id, biz_key),
    KEY idx_notification_user_read (user_id, read_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知';
