-- M31 下单异步补账（D1 半同步占位 + 异步补账）
-- 1) 创建失败原因（仅 CREATE_FAILED 时非空）
-- 2) 扫描重投次数（CREATING 超时后重投，超过上限转失败）
ALTER TABLE orders
    ADD COLUMN create_error VARCHAR(200) NOT NULL DEFAULT '' AFTER status_before_refund,
    ADD COLUMN create_attempts INT NOT NULL DEFAULT 0 AFTER create_error;

-- 扫描 CREATING 超时订单用
CREATE INDEX idx_orders_status_created ON orders (status, created_at);
