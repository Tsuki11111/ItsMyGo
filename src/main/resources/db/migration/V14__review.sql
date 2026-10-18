-- 商品评价：每个订单项只能评一次。
--
-- 评价挂在 order_items 上而不是「用户 + 商品」上：同一个人买两次可以评两次，
-- 且只有已完成订单的订单项才能评。商品名/规格不在评价里冗余 ——
-- 商品详情页本来就拿着商品上下文，「我的评价」再按 productId 批量查即可。

CREATE TABLE reviews
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价 ID',
    user_id       BIGINT       NOT NULL COMMENT '评价人',
    order_id      BIGINT       NOT NULL COMMENT '订单 ID（校验用，不做外键）',
    order_item_id BIGINT       NOT NULL COMMENT '订单项 ID，一个订单项只能评一次',
    product_id    BIGINT       NOT NULL COMMENT '商品 ID',
    sku_id        BIGINT       NOT NULL COMMENT 'SKU ID，仅供追溯',
    rating        TINYINT      NOT NULL COMMENT '1-5 星',
    content       VARCHAR(500) NOT NULL DEFAULT '' COMMENT '评价内容',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 展示 / 0 管理端隐藏',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_item (order_item_id),
    KEY idx_product_status (product_id, status, created_at),
    KEY idx_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '商品评价';
