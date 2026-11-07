-- 商品价格历史：智能推广的「降价」信号依赖它。
-- 存量商品没有历史就永远算不出降价，所以这里顺手给每个商品补一条 baseline。
-- 之后只在价格**真的变化**时追加 update（商品级 sku_id 为 NULL，规格级带上 sku_id）。
CREATE TABLE product_price_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  sku_id BIGINT NULL,
  price DECIMAL(10,2) NOT NULL,
  reason VARCHAR(32) NOT NULL COMMENT 'baseline / update',
  changed_at DATETIME NOT NULL,
  KEY idx_product_time (product_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品价格历史（智能推广降价信号）';

INSERT INTO product_price_history (product_id, sku_id, price, reason, changed_at)
SELECT id, NULL, price, 'baseline', NOW() FROM products;
