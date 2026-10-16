-- 商品规格与 SKU。
--
-- 在此之前价格和库存直接挂在 products 上（单规格）。改成多规格后，
-- 「真相」下沉到 product_skus —— 每个规格组合一行、独立价格独立库存；
-- products 上的 price / stock / sales 退化成**冗余汇总**，
-- 只为列表页分页服务（否则列表查询要 join SKU 做聚合，配分页会很重）。

CREATE TABLE product_specs
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '规格 ID',
    product_id BIGINT      NOT NULL COMMENT '所属商品',
    name       VARCHAR(32) NOT NULL COMMENT '规格名，如「款式」',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_product_id (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '商品规格定义';

CREATE TABLE product_spec_values
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '规格值 ID',
    spec_id    BIGINT      NOT NULL COMMENT '所属规格',
    value      VARCHAR(64) NOT NULL COMMENT '规格值，如「云宝」',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_spec_id (spec_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '商品规格值';

CREATE TABLE product_skus
(
    id             BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    product_id     BIGINT         NOT NULL COMMENT '所属商品',
    -- 升序、逗号分隔的规格值 id，如 "11,12"。
    -- 它是「组合」的规范键 —— 靠它才能给唯一约束：关联表表达不了
    -- 「一个 SKU 的规格值集合唯一」，JSON 做唯一索引要函数索引、MyBatis 侧也难写。
    -- 空串是合法值：表示无规格商品的**默认 SKU**，唯一约束顺带保证每商品只有一条。
    -- ⚠️ 写入前必须按**数值**排序再去重（"10" < "2" 是字符串比较的经典坑）
    spec_value_ids VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '规格值 id，升序逗号分隔',
    -- 规格值会改名、会删除，历史订单要原样显示，所以下单时快照进 order_items
    spec_text      VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '规格描述，如「款式:云宝」',
    price          DECIMAL(10, 2) NOT NULL COMMENT '售价（元）',
    stock          INT            NOT NULL DEFAULT 0 COMMENT '**库存的真相在这里**',
    sales          INT            NOT NULL DEFAULT 0 COMMENT '销量',
    image          VARCHAR(512)   NOT NULL DEFAULT '' COMMENT '规格图，空则回落到商品主图',
    status         TINYINT        NOT NULL DEFAULT 1 COMMENT '1 在售 / 0 停用',
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_specs (product_id, spec_value_ids),
    KEY idx_product_id (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '商品 SKU';

-- 列表页据此显示「¥19.8 起」。实时算要 N+1 次查询，不如保存 SKU 时顺手维护
ALTER TABLE products
    ADD COLUMN has_specs TINYINT NOT NULL DEFAULT 0 COMMENT '1 有多规格 / 0 单规格' AFTER sales;

-- 回填：给每个现有商品生成一条默认 SKU，把价格库存搬过去。
-- ⚠️ **这一步不能少** —— 没有 SKU 的商品在详情页会显示总库存 0、
-- 下单永远报「库存不足」，看着像库存 bug，其实是数据没铺开。
INSERT INTO product_skus (product_id, spec_value_ids, spec_text, price, stock, sales, image, status)
SELECT id, '', '', price, stock, sales, main_image, 1
FROM products;
