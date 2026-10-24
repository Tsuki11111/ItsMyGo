-- 商品品牌：品牌专区按品牌筛选，不再依赖商品名关键词
ALTER TABLE products ADD COLUMN brand VARCHAR(64) NOT NULL DEFAULT '' AFTER name;

-- 存量回填（按商品名归类；GC251DNS 的手册注明是 Lenovo）
UPDATE products SET brand = '奔图' WHERE name LIKE '奔图%';
UPDATE products SET brand = '联想' WHERE name LIKE '联想%' OR name = 'GC251DNS 激光打印机';
UPDATE products SET brand = '至像' WHERE name LIKE '至像%';
UPDATE products SET brand = '兄弟' WHERE name LIKE '兄弟%';
UPDATE products SET brand = '华为' WHERE name LIKE '华为%' OR name LIKE 'HUAWEI%';
UPDATE products SET brand = 'H3C' WHERE name LIKE 'H3C%';
UPDATE products SET brand = 'Aolynk' WHERE name LIKE 'Aolynk%';
UPDATE products SET brand = 'RS PRO' WHERE name LIKE 'RS-%';

CREATE INDEX idx_products_brand_status ON products (brand, status);
