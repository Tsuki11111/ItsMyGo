-- 用户收藏与浏览足迹。
--
-- 两者都是「用户-商品」关系，靠唯一键做幂等：
-- 重复收藏不会报错，重复浏览只更新时间。
-- 商品表没有外键（项目统一不用 FK），商品下架/删除后这些行保留，
-- 列表里按当前商品状态显示「已下架」或「商品已不存在」。

CREATE TABLE user_favorites
(
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏 ID',
    user_id    BIGINT   NOT NULL COMMENT '用户 ID',
    product_id BIGINT   NOT NULL COMMENT '商品 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    KEY idx_user_created (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '用户收藏';

CREATE TABLE user_footprints
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '足迹 ID',
    user_id    BIGINT      NOT NULL COMMENT '用户 ID',
    product_id BIGINT      NOT NULL COMMENT '商品 ID（按商品去重，不区分 SKU）',
    -- 毫秒精度：同一秒内浏览多个商品也能稳定排序
    viewed_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近一次浏览时间',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    KEY idx_user_viewed (user_id, viewed_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '用户足迹（按商品去重，只保留最近 100 条）';
