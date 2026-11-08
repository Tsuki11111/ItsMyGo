-- 商品归档 / 自动清理（2026-11-08）
-- 下架（status=0）超过阈值 → 自动归档（archived_at）；归档满宽限期且无任何引用才允许物理删除。
ALTER TABLE products
    ADD COLUMN archived_at DATETIME NULL DEFAULT NULL COMMENT '自动归档时间（长期下架）' AFTER updated_at,
    ADD KEY idx_products_archive (status, archived_at, updated_at);
