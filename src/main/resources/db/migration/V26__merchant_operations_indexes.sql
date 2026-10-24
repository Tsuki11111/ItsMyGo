-- 商户经营统计与低库存筛选的索引
ALTER TABLE orders
    ADD KEY idx_orders_merchant_paid (merchant_id, paid_at),
    ADD KEY idx_orders_merchant_created (merchant_id, created_at);

ALTER TABLE product_skus
    ADD KEY idx_skus_status_stock (status, stock);
