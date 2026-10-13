CREATE TABLE products
(
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '商品 ID',
    category_id BIGINT         NOT NULL COMMENT '所属分类',
    name        VARCHAR(128)   NOT NULL COMMENT '商品名称',
    subtitle    VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '副标题',
    main_image  VARCHAR(512)   NOT NULL DEFAULT '' COMMENT '主图 URL',
    price       DECIMAL(10, 2) NOT NULL COMMENT '售价（元）',
    stock       INT            NOT NULL DEFAULT 0 COMMENT '库存',
    sales       INT            NOT NULL DEFAULT 0 COMMENT '销量',
    detail      TEXT           NULL COMMENT '商品详情',
    status      TINYINT        NOT NULL DEFAULT 0 COMMENT '1 上架 / 0 下架',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_category_id (category_id),
    KEY idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '商品';
