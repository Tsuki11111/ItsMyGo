-- 种子数据：办公 / 网络 / 3C 商城
-- 商品全部取自 rag_helper/doc 下的产品手册：每个型号一件商品，
-- 这样「问这台打印机怎么用」时，智能客服从知识库能匹配到对应手册。
--
-- ⚠️ 导入后要做三件事：
--   1. 导入时 mysql 客户端必须加 `--default-character-set=utf8mb4`，否则中文会乱码
--   2. 清掉分类缓存：docker exec redis redis-cli DEL its-my-go:category:tree
--   3. 重启后端 —— 布隆过滤器只在启动时预热，手工插的商品不在过滤器里，
--      点详情会被判「不存在」。
--
-- 商品 ID 由自增序列生成；清空 products 不会重置 AUTO_INCREMENT，
-- 因此旧订单 / 旧购物车里的 product_id 不会误指到新商品。

-- 按依赖倒序清，别留下悬空的规格值；购物车引用的是即将删除的商品，一并清掉
DELETE FROM cart_items;
DELETE FROM product_skus;
DELETE FROM product_spec_values;
DELETE FROM product_specs;
DELETE FROM products;
DELETE FROM categories;

INSERT INTO categories (parent_id, name, sort, status) VALUES
(0, '打印机', 1, 1),
(0, '路由器', 2, 1),
(0, '网络设备', 3, 1),
(0, '笔记本电脑', 4, 1),
(0, '台式机', 5, 1),
(0, '平板电脑', 6, 1),
(0, '显示器', 7, 1),
(0, '办公设备', 8, 1);

SET @printer = (SELECT id FROM categories WHERE name = '打印机');
SET @router = (SELECT id FROM categories WHERE name = '路由器');
SET @network = (SELECT id FROM categories WHERE name = '网络设备');
SET @laptop = (SELECT id FROM categories WHERE name = '笔记本电脑');
SET @desktop = (SELECT id FROM categories WHERE name = '台式机');
SET @tablet = (SELECT id FROM categories WHERE name = '平板电脑');
SET @monitor = (SELECT id FROM categories WHERE name = '显示器');
SET @office = (SELECT id FROM categories WHERE name = '办公设备');

INSERT INTO products (category_id, name, subtitle, main_image, price, stock, sales, status) VALUES
-- 打印机（19 款，对应 19 份用户手册）
(@printer, '奔图 P3030 激光打印机', 'A4 黑白激光 · 22ppm · 家用办公', '', 699.00, 42, 328, 1),
(@printer, '奔图 P3000 激光打印机', 'A4 黑白激光 · 商用入门', '', 899.00, 30, 210, 1),
(@printer, '联想 LJ2268 激光打印机', 'A4 黑白 · 小型办公', '', 799.00, 26, 188, 1),
(@printer, '联想 LJ2680DN 激光打印机', 'A4 黑白 · 自动双面 · 网络', '', 1299.00, 18, 142, 1),
(@printer, '联想 M3070 激光多功能一体机', '打印 / 复印 / 扫描 · 商用', '', 1599.00, 15, 126, 1),
(@printer, '联想 M7208W Pro 激光一体机', '无线连接 · 家用办公', '', 1199.00, 22, 230, 1),
(@printer, '联想 M7268 激光多功能一体机', '自动双面 · 商用办公', '', 1499.00, 14, 98, 1),
(@printer, '奔图 Panda Pro 系列打印机', '黑白激光 · 高速打印', '', 1899.00, 10, 64, 1),
(@printer, '联想 Z26 MIC 激光打印机', 'A4 黑白 · 商用', '', 1399.00, 16, 132, 1),
(@printer, '至像 Z26 激光打印机', 'A4 黑白 · 自动双面 · 商用', '', 1299.00, 18, 156, 1),
(@printer, '联想 Z2 打印机', '黑白激光 · 紧凑机身', '', 999.00, 24, 176, 1),
(@printer, '联想 Z35 黑白激光一体机', '打印复印扫描 · 三合一', '', 1580.00, 14, 203, 1),
(@printer, '兄弟 HAK 180 烫金机', '小型烫金 · 名片请柬适用', '', 1999.00, 7, 89, 1),
(@printer, '兄弟 HL-3040 彩色激光打印机', '彩色激光 · 网络打印', '', 2499.00, 8, 41, 1),
(@printer, 'GC251DNS 激光打印机', 'A4 黑白 · 网络双面', '', 1599.00, 12, 57, 1),
(@printer, '华为擎云 P5 激光多功能一体机', '打印 / 复印 / 扫描 · 商用', '', 2299.00, 11, 73, 1),
(@printer, 'HUAWEI PixLab B5 激光多功能一体机', '黑白激光 · 无线打印', '', 2199.00, 13, 86, 1),
(@printer, '联想海豚系列打印机', '家用学习 · 黑白激光', '', 1099.00, 20, 148, 1),
(@printer, '联想鲸鱼系列打印机', '商用办公 · 黑白激光', '', 1399.00, 17, 104, 1),

-- 路由器（6 款）
(@router, 'H3C ER2100 企业级路由器', '千兆双 WAN · 多线路负载均衡', '', 899.00, 25, 412, 1),
(@router, 'H3C MER 系列路由器', '多业务 · 企业分支组网', '', 3280.00, 8, 52, 1),
(@router, 'H3C MSR 系列多业务路由器', '模块化 · VPN · 中小企业网关', '', 2680.00, 9, 63, 1),
(@router, 'H3C MSR WiNet 系列云简路由器', '云管理 · 无线组网', '', 1980.00, 12, 71, 1),
(@router, 'H3C MSR-XS 系列路由器', '紧凑型 · 多业务接入', '', 2280.00, 10, 49, 1),
(@router, 'H3C NR-1200W 企业级无线宽带路由器', '无线双频 · 企业办公', '', 699.00, 28, 190, 1),

-- 网络设备（2 款）
(@network, 'Aolynk CB304n Cable 网桥', '以太网延伸 · 即插即用', '', 259.00, 30, 78, 1),
(@network, 'H3C LA2608 室内无线网关', '室内覆盖 · 无线接入', '', 899.00, 18, 66, 1),

-- 笔记本电脑（8 款）
(@laptop, 'HUAWEI MateBook B3-420', '14 英寸 · 16G+512G · 商务本', '', 5499.00, 12, 205, 1),
(@laptop, 'HUAWEI MateBook B3-520', '15.6 英寸 · 16G+512G · 商务本', '', 5999.00, 10, 118, 1),
(@laptop, 'HUAWEI MateBook B5-430', '14 英寸 · 标压处理器 · 轻薄', '', 6999.00, 6, 47, 1),
(@laptop, '华为擎云 L410', '14 英寸 · 国产化办公本', '', 4999.00, 15, 132, 1),
(@laptop, '华为擎云 L420x', '14 英寸 · 高性能商务本', '', 6299.00, 9, 74, 1),
(@laptop, '华为擎云 L540', '14 英寸 · 轻薄商务本', '', 5899.00, 11, 96, 1),
(@laptop, '华为擎云 L540x', '14 英寸 · 长续航商务本', '', 6199.00, 8, 63, 1),
(@laptop, '华为擎云 HM740', '14 英寸 · 鸿蒙商用本', '', 7499.00, 7, 38, 1),

-- 台式机（4 款）
(@desktop, 'HUAWEI MateStation B515', '锐龙处理器 · 商用办公', '', 3899.00, 10, 128, 1),
(@desktop, 'HUAWEI MateStation B520', '高性能台式机 · 商用', '', 4599.00, 8, 77, 1),
(@desktop, '华为擎云 W515y', '国产化台式机 · 商用办公', '', 5299.00, 6, 45, 1),
(@desktop, '华为擎云 W525', '国产化台式机 · 稳定办公', '', 4999.00, 9, 61, 1),

-- 平板电脑（1 款）
(@tablet, '华为平板 C7', '10.4 英寸 · 2K 全面屏', '', 2299.00, 20, 167, 1),

-- 显示器（3 款）
(@monitor, '华为显示器 B3-211H', '21.5 英寸 · 1080P · 低蓝光', '', 999.00, 22, 176, 1),
(@monitor, '华为显示器 B3-271Q', '27 英寸 · 2K · 高色域', '', 1799.00, 16, 121, 1),
(@monitor, '华为显示器 B5-341W', '34 英寸 · 曲面带鱼屏', '', 2699.00, 9, 54, 1),

-- 办公设备（1 款）
(@office, 'RS-12 数字万用表', '便携式 · 电工测量', '', 159.00, 40, 312, 1);

-- ── 默认 SKU ──
-- 每个商品都必须有 SKU；先给每个商品铺一条默认 SKU，下面两个多规格商品再单独覆盖。
INSERT INTO product_skus (product_id, spec_value_ids, spec_text, price, stock, sales, image, status)
SELECT id, '', '', price, stock, sales, main_image, 1
FROM products;

-- ── 演示用的真实规格 ──
-- ① 奔图 P3030：套餐二选一
SET @p3030 = (SELECT id FROM products WHERE name = '奔图 P3030 激光打印机');
INSERT INTO product_specs (product_id, name, sort) VALUES (@p3030, '套餐', 0);
SET @p3030_spec = LAST_INSERT_ID();
INSERT INTO product_spec_values (spec_id, value, sort) VALUES
(@p3030_spec, '标准装', 0), (@p3030_spec, '含硒鼓套装', 1);

SET @p3030_std = (SELECT id FROM product_spec_values WHERE spec_id = @p3030_spec AND value = '标准装');
SET @p3030_drum = (SELECT id FROM product_spec_values WHERE spec_id = @p3030_spec AND value = '含硒鼓套装');

DELETE FROM product_skus WHERE product_id = @p3030;
INSERT INTO product_skus (product_id, spec_value_ids, spec_text, price, stock, sales, image, status) VALUES
(@p3030, CAST(@p3030_std AS CHAR), '套餐:标准装', 699.00, 30, 240, '', 1),
(@p3030, CAST(@p3030_drum AS CHAR), '套餐:含硒鼓套装', 899.00, 12, 88, '', 1);

-- ② MateBook B3-420：内存 × 硬盘，四个组合
SET @b3420 = (SELECT id FROM products WHERE name = 'HUAWEI MateBook B3-420');
INSERT INTO product_specs (product_id, name, sort) VALUES (@b3420, '内存', 0), (@b3420, '硬盘', 1);
SET @b3420_mem = (SELECT id FROM product_specs WHERE product_id = @b3420 AND name = '内存');
SET @b3420_disk = (SELECT id FROM product_specs WHERE product_id = @b3420 AND name = '硬盘');

INSERT INTO product_spec_values (spec_id, value, sort) VALUES
(@b3420_mem, '16G', 0), (@b3420_mem, '32G', 1),
(@b3420_disk, '512G', 0), (@b3420_disk, '1TB', 1);

SET @m16 = (SELECT id FROM product_spec_values WHERE spec_id = @b3420_mem AND value = '16G');
SET @m32 = (SELECT id FROM product_spec_values WHERE spec_id = @b3420_mem AND value = '32G');
SET @d512 = (SELECT id FROM product_spec_values WHERE spec_id = @b3420_disk AND value = '512G');
SET @d1t = (SELECT id FROM product_spec_values WHERE spec_id = @b3420_disk AND value = '1TB');

DELETE FROM product_skus WHERE product_id = @b3420;
-- spec_value_ids 必须升序拼接（内存值先插，id 更小）
INSERT INTO product_skus (product_id, spec_value_ids, spec_text, price, stock, sales, image, status) VALUES
(@b3420, CONCAT(@m16, ',', @d512), '内存:16G;硬盘:512G', 5499.00, 8, 130, '', 1),
(@b3420, CONCAT(@m16, ',', @d1t),  '内存:16G;硬盘:1TB', 5999.00, 4, 50,  '', 1),
(@b3420, CONCAT(@m32, ',', @d512), '内存:32G;硬盘:512G', 6299.00, 3, 30,  '', 1),
(@b3420, CONCAT(@m32, ',', @d1t),  '内存:32G;硬盘:1TB', 6999.00, 2, 15,  '', 1);

-- 汇总字段跟着重算：列表页靠 price 显示最低价、靠 has_specs 决定要不要加「起」
UPDATE products SET has_specs = 1 WHERE id IN (@p3030, @b3420);
UPDATE products p
SET price = (SELECT MIN(price) FROM product_skus WHERE product_id = p.id AND status = 1),
    stock = (SELECT SUM(stock) FROM product_skus WHERE product_id = p.id AND status = 1),
    sales = (SELECT SUM(sales) FROM product_skus WHERE product_id = p.id AND status = 1)
WHERE p.id IN (@p3030, @b3420);
