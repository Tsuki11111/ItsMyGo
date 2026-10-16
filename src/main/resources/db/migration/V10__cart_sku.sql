-- 购物车行改成挂在 SKU 上。
--
-- 之前一行 = 一个商品（uk_user_product）。有了多规格之后，
-- 「同一件商品的黑色和白色」是两条不同的行，得按 SKU 区分。

ALTER TABLE cart_items
    ADD COLUMN sku_id BIGINT NOT NULL DEFAULT 0 COMMENT 'SKU ID' AFTER product_id;

-- 回填：现有购物车行都指向各自商品的**默认 SKU**（spec_value_ids 为空的那条）。
-- V9 保证每个商品都有且只有一条这样的 SKU，所以这个 JOIN 不会漏。
UPDATE cart_items c
    JOIN product_skus s ON s.product_id = c.product_id AND s.spec_value_ids = ''
SET c.sku_id = s.id;

-- 唯一键从「用户 + 商品」换成「用户 + SKU」——
-- 同一个人可以把同一件商品的不同规格各放一行
ALTER TABLE cart_items
    DROP INDEX uk_user_product,
    ADD UNIQUE KEY uk_user_sku (user_id, sku_id);
