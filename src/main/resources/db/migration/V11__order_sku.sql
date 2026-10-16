-- 订单项要记下买的是哪个 SKU、以及规格的**快照**。
--
-- sku_id 仅供追溯（和 product_id 一样，展示时不要 join 取当前值）。
-- spec_text 是真正的快照：规格值会改名、会被删除，而历史订单必须原样显示
-- 「颜色:黑色;版本:静音版」—— 跟商品名、主图、单价那三个快照是同一个道理。

ALTER TABLE order_items
    ADD COLUMN sku_id BIGINT NOT NULL DEFAULT 0 COMMENT 'SKU ID（仅供追溯）' AFTER product_id,
    ADD COLUMN spec_text VARCHAR(255) NOT NULL DEFAULT '' COMMENT '规格快照，如「颜色:黑色」' AFTER product_image;

-- 历史订单都是单规格的，按商品的默认 SKU 回填 id；spec_text 留空 ——
-- 那些订单本来就没有规格可显示，编一个反而是造数据。
UPDATE order_items oi
    JOIN product_skus s ON s.product_id = oi.product_id AND s.spec_value_ids = ''
SET oi.sku_id = s.id;
