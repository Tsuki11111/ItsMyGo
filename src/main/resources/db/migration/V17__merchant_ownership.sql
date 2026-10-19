-- 商品/订单/评价归属商户。
--
-- 存量数据全部回填到默认商户 1（平台自营）。
-- DEFAULT 1 是为了兼容 seed 脚本与测试里的直接 INSERT；
-- 业务代码仍必须显式写 merchantId，并靠测试防止新数据误落到平台自营。
--
-- cart_items 不加 merchant_id：由商品派生，避免商品归属调整后购物车行漂移。
-- order_items 不加 merchant_id：一个订单天然只属于一个商户，快照里不需要。

ALTER TABLE products
    ADD COLUMN merchant_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属商户' AFTER id,
    ADD KEY idx_merchant_status (merchant_id, status, id);

ALTER TABLE orders
    ADD COLUMN merchant_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属商户（按店拆单）' AFTER user_id,
    ADD KEY idx_merchant_status (merchant_id, status, id);

ALTER TABLE reviews
    ADD COLUMN merchant_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属商户（下单时快照）' AFTER product_id,
    ADD KEY idx_merchant_status (merchant_id, status, created_at);
