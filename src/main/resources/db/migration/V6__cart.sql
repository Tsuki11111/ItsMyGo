CREATE TABLE cart_items
(
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '购物车项 ID',
    user_id    BIGINT   NOT NULL COMMENT '所属用户',
    product_id BIGINT   NOT NULL COMMENT '商品 ID',
    quantity   INT      NOT NULL COMMENT '数量',
    selected   TINYINT  NOT NULL DEFAULT 1 COMMENT '1 勾选 / 0 未勾选',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 同一用户同一商品只占一行，重复加购走「数量累加」而不是插新行
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '购物车';
