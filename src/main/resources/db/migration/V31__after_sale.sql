-- 售后单（M30）
--
-- 为什么独立建表：退款只是订单状态的一个分支，撑不起「部分退款 / 退货 / 多次售后 / 处理记录」；
-- 一个订单可能有多条售后（不同商品、不同时间），订单状态无法表达多路并发。
--
-- 状态机：
--   PENDING_REVIEW ─同意(仅退款)────────────> REFUNDED
--        │   └──同意(退货退款)──> WAITING_RETURN ─买家填单号─> RETURN_SHIPPED ─商家确认收货─> REFUNDED
--        ├──驳回（必填理由）──> REJECTED（可再次申请）
--        └──买家撤销────────> CANCELLED
CREATE TABLE after_sales
(
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    after_sale_no       VARCHAR(32)   NOT NULL COMMENT '售后单号',
    order_id            BIGINT        NOT NULL COMMENT '订单 ID',
    order_no            VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '订单号快照',
    user_id             BIGINT        NOT NULL COMMENT '申请顾客',
    merchant_id         BIGINT        NOT NULL COMMENT '所属商户（审核方）',
    type                VARCHAR(20)   NOT NULL COMMENT 'REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款',
    status              VARCHAR(24)   NOT NULL COMMENT 'PENDING_REVIEW/WAITING_RETURN/RETURN_SHIPPED/REFUNDED/REJECTED/CANCELLED',
    reason_code         VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '原因字典 code',
    reason_text         VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '补充说明',
    evidence_images     VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '凭证图片 URL 的 JSON 数组',
    apply_amount        DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '申请时估算退款额（优惠等比分摊）',
    refund_amount       DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '实际退款额（执行时定稿，含尾差）',
    reject_reason       VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '驳回理由',
    return_tracking_no  VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '买家退货单号',
    return_carrier      VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '买家退货快递公司',
    review_deadline     DATETIME      NULL COMMENT '商家审核截止时间（超时自动同意）',
    reviewed_at         DATETIME      NULL COMMENT '审核时间（同意/驳回）',
    shipped_back_at     DATETIME      NULL COMMENT '买家寄回时间',
    received_at         DATETIME      NULL COMMENT '商家确认收货时间',
    refunded_at         DATETIME      NULL COMMENT '退款完成时间',
    cancelled_at        DATETIME      NULL COMMENT '买家撤销时间',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_no (after_sale_no),
    KEY idx_after_sale_user (user_id, created_at),
    KEY idx_after_sale_merchant (merchant_id, status, created_at),
    KEY idx_after_sale_status (status, review_deadline),
    KEY idx_after_sale_return (status, shipped_back_at),
    KEY idx_after_sale_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '售后单';

-- 售后单包含的订单项（支持按项 / 按数量部分退款）
CREATE TABLE after_sale_items
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    after_sale_id BIGINT        NOT NULL COMMENT '售后单 ID',
    order_item_id BIGINT        NOT NULL COMMENT '订单项 ID',
    product_id    BIGINT        NOT NULL COMMENT '商品 ID（快照）',
    sku_id        BIGINT        NOT NULL COMMENT 'SKU ID',
    product_name  VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '商品名快照',
    product_image VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '图片快照',
    spec_text     VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '规格快照',
    unit_price    DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '单价快照',
    quantity      INT           NOT NULL COMMENT '退款数量',
    refund_amount DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '该项实际退款额（含分摊与尾差）',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_item (after_sale_id, order_item_id),
    KEY idx_after_sale_item_order_item (order_item_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '售后单明细';

-- 售后原因字典（平台维护，申请时必选）
CREATE TABLE after_sale_reasons
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(32) NOT NULL COMMENT '稳定编码',
    label      VARCHAR(64) NOT NULL COMMENT '展示名称',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序（小的在前）',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_reason_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '售后原因字典';

INSERT INTO after_sale_reasons (code, label, sort)
VALUES ('QUALITY', '商品质量问题', 10),
       ('NOT_AS_DESCRIBED', '与描述不符', 20),
       ('DAMAGED', '运输破损', 30),
       ('WRONG_ITEM', '发错商品', 40),
       ('NO_LONGER_NEEDED', '不想要了', 50),
       ('OTHER', '其他原因', 90);

-- 订单累计已退金额：等于 total_amount 表示整单退完（部分退款时订单继续履约）
ALTER TABLE orders
    ADD COLUMN refunded_amount DECIMAL(10, 2) NOT NULL DEFAULT 0
        COMMENT '累计已退款金额（售后单累加）' AFTER discount_amount;

-- 老数据：已退款订单视为整单退完
UPDATE orders
SET refunded_amount = total_amount
WHERE status = 'REFUNDED';
