CREATE TABLE categories
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类 ID',
    parent_id  BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类 ID，0 表示顶层',
    name       VARCHAR(32) NOT NULL COMMENT '分类名称',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '商品分类';
