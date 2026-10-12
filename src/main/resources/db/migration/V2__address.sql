CREATE TABLE addresses
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址 ID',
    user_id    BIGINT       NOT NULL COMMENT '所属用户',
    receiver   VARCHAR(32)  NOT NULL COMMENT '收货人',
    phone      VARCHAR(20)  NOT NULL COMMENT '联系电话',
    province   VARCHAR(32)  NOT NULL COMMENT '省',
    city       VARCHAR(32)  NOT NULL COMMENT '市',
    district   VARCHAR(32)  NOT NULL COMMENT '区/县',
    detail     VARCHAR(128) NOT NULL COMMENT '详细地址',
    is_default TINYINT      NOT NULL DEFAULT 0 COMMENT '1 默认地址 / 0 普通',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '收货地址';
