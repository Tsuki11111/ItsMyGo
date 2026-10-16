-- 种子数据：电子设备商城
-- 商品型号尽量取自 rag_helper 的产品手册语料，将来接入智能客服时"问这个打印机怎么用"能真的查到手册
--
-- ⚠️ 导入完要**重启后端** —— 布隆过滤器只在启动时预热，
--    应用运行期间手工插的商品不在过滤器里，点详情会被判"不存在"。

-- 按依赖倒序清，别留下悬空的规格值
DELETE FROM product_skus;
DELETE FROM product_spec_values;
DELETE FROM product_specs;
DELETE FROM products;
DELETE FROM categories;

INSERT INTO categories (parent_id, name, sort, status) VALUES
(0, '打印机', 1, 1),
(0, '打印耗材', 2, 1),
(0, '路由器', 3, 1),
(0, '网络设备', 4, 1),
(0, '笔记本电脑', 5, 1),
(0, '显示器', 6, 1),
(0, '键鼠外设', 7, 1),
(0, '存储设备', 8, 1),
(0, '办公设备', 9, 1),
(0, '配件线材', 10, 1);

INSERT INTO products (category_id, name, subtitle, main_image, price, stock, sales, status) VALUES
-- 打印机
((SELECT id FROM categories WHERE name = '打印机'), '奔图 P3030 激光打印机', 'A4 黑白激光 · 22ppm · 家用办公', '', 699.00, 42, 328, 1),
((SELECT id FROM categories WHERE name = '打印机'), '联想 Z26 激光打印机', 'A4 黑白 · 自动双面 · 商用', '', 1299.00, 18, 156, 1),
((SELECT id FROM categories WHERE name = '打印机'), '兄弟 HAK 180 烫金机', '小型烫金 · 名片请柬适用', '', 1999.00, 7, 89, 1),
((SELECT id FROM categories WHERE name = '打印机'), '至像 Z35 黑白激光一体机', '打印复印扫描 · 三合一', '', 1580.00, 14, 203, 1),
-- 打印耗材
((SELECT id FROM categories WHERE name = '打印耗材'), '奔图 P3030 原装硒鼓', '适配 P3000 系列 · 约 1600 页', '', 189.00, 120, 1520, 1),
((SELECT id FROM categories WHERE name = '打印耗材'), 'A4 复印纸 70g 500张/包', '整箱 5 包 · 高白度不卡纸', '', 109.00, 300, 4280, 1),
-- 路由器
((SELECT id FROM categories WHERE name = '路由器'), 'H3C ER2100 企业级路由器', '千兆双 WAN · 多线路负载均衡', '', 899.00, 25, 412, 1),
((SELECT id FROM categories WHERE name = '路由器'), 'H3C MSR 系列多业务路由器', '模块化 · VPN · 中小企业网关', '', 2680.00, 9, 63, 1),
-- 网络设备
((SELECT id FROM categories WHERE name = '网络设备'), 'Aolynk CB304n 网桥', '以太网延伸 · 即插即用', '', 259.00, 30, 78, 1),
((SELECT id FROM categories WHERE name = '网络设备'), 'H3C S5024 千兆交换机', '24 口全千兆 · 机架式', '', 1180.00, 16, 94, 1),
-- 笔记本电脑
((SELECT id FROM categories WHERE name = '笔记本电脑'), 'HUAWEI MateBook B3-420', '14 英寸 · 16G+512G · 商务本', '', 5499.00, 12, 205, 1),
((SELECT id FROM categories WHERE name = '笔记本电脑'), 'HUAWEI MateBook B5-430', '14 英寸 · 标压处理器 · 轻薄', '', 6999.00, 6, 47, 1),
((SELECT id FROM categories WHERE name = '笔记本电脑'), 'HUAWEI MateStation B515', '台式机 · 锐龙 · 商用办公', '', 3899.00, 10, 128, 1),
-- 显示器
((SELECT id FROM categories WHERE name = '显示器'), '27 英寸 2K 商用显示器', 'IPS · 低蓝光 · 可升降支架', '', 1099.00, 22, 176, 1),
((SELECT id FROM categories WHERE name = '显示器'), '24 英寸 1080P 显示器', '75Hz · HDMI+VGA · 办公', '', 599.00, 35, 342, 1),
-- 键鼠外设
((SELECT id FROM categories WHERE name = '键鼠外设'), '无线键鼠套装', '静音 · 2.4G · 长续航', '', 129.00, 88, 865, 1),
((SELECT id FROM categories WHERE name = '键鼠外设'), '人体工学无线鼠标', '垂直握姿 · 缓解腕部压力', '', 159.00, 60, 421, 1),
-- 存储设备
((SELECT id FROM categories WHERE name = '存储设备'), '1TB 移动固态硬盘', 'USB3.2 · 读写 1050MB/s', '', 599.00, 40, 517, 1),
((SELECT id FROM categories WHERE name = '存储设备'), '64G U盘 USB3.0', '金属外壳 · 车载办公两用', '', 49.00, 200, 2130, 1),
-- 办公设备
((SELECT id FROM categories WHERE name = '办公设备'), '碎纸机 5 级保密', '单次 8 张 · 连续 30 分钟', '', 459.00, 15, 132, 1),
((SELECT id FROM categories WHERE name = '办公设备'), '指纹考勤机', '免软件 · U盘导出 · 1000 用户', '', 329.00, 26, 189, 1),
-- 配件线材
((SELECT id FROM categories WHERE name = '配件线材'), 'HDMI 2.1 高清线 2米', '8K@60Hz · 镀金接口', '', 39.00, 150, 1876, 1),
((SELECT id FROM categories WHERE name = '配件线材'), '六类千兆网线 5 米', '无氧铜 · 屏蔽抗干扰', '', 25.00, 400, 3260, 1);

-- ── 默认 SKU ──
-- ⚠️ 每个商品都必须有 SKU。上面是直接 INSERT 进 products 的（没走管理端接口，
--    而接口会顺手建默认 SKU），所以这里得补上 —— 少了它，详情页会显示总库存 0、
--    下单永远报「库存不足」，看着像库存 bug，其实是数据没铺开。
INSERT INTO product_skus (product_id, spec_value_ids, spec_text, price, stock, sales, image, status)
SELECT id, '', '', price, stock, sales, main_image, 1
FROM products;

-- ── 演示用的真实规格 ──
-- 挑两个商品配上规格组合，顾客端详情页的「规格选择栏」才有东西可看：
-- 一个是单规格（三选一），一个是双规格（二选二，四个 SKU 组合）。

-- ① HDMI 线：按长度分三种
SET @hdmi = (SELECT id FROM products WHERE name = 'HDMI 2.1 高清线 2米');
INSERT INTO product_specs (product_id, name, sort) VALUES (@hdmi, '长度', 0);
SET @spec_len = LAST_INSERT_ID();
INSERT INTO product_spec_values (spec_id, value, sort) VALUES
(@spec_len, '1 米', 0), (@spec_len, '2 米', 1), (@spec_len, '3 米', 2);

SET @len1 = (SELECT id FROM product_spec_values WHERE spec_id = @spec_len AND value = '1 米');
SET @len2 = (SELECT id FROM product_spec_values WHERE spec_id = @spec_len AND value = '2 米');
SET @len3 = (SELECT id FROM product_spec_values WHERE spec_id = @spec_len AND value = '3 米');

DELETE FROM product_skus WHERE product_id = @hdmi;
INSERT INTO product_skus (product_id, spec_value_ids, spec_text, price, stock, sales, image, status) VALUES
(@hdmi, CAST(@len1 AS CHAR), '长度:1 米', 29.00, 200, 940,  '', 1),
(@hdmi, CAST(@len2 AS CHAR), '长度:2 米', 39.00, 150, 1876, '', 1),
(@hdmi, CAST(@len3 AS CHAR), '长度:3 米', 49.00, 80,  430,  '', 1);

-- ② 键鼠套装：颜色 × 版本，四个组合
SET @kb = (SELECT id FROM products WHERE name = '无线键鼠套装');
INSERT INTO product_specs (product_id, name, sort) VALUES (@kb, '颜色', 0), (@kb, '版本', 1);
SET @spec_color = (SELECT id FROM product_specs WHERE product_id = @kb AND name = '颜色');
SET @spec_ver = (SELECT id FROM product_specs WHERE product_id = @kb AND name = '版本');

INSERT INTO product_spec_values (spec_id, value, sort) VALUES
(@spec_color, '黑色', 0), (@spec_color, '白色', 1),
(@spec_ver, '标准版', 0), (@spec_ver, '静音版', 1);

SET @c_black = (SELECT id FROM product_spec_values WHERE spec_id = @spec_color AND value = '黑色');
SET @c_white = (SELECT id FROM product_spec_values WHERE spec_id = @spec_color AND value = '白色');
SET @v_std = (SELECT id FROM product_spec_values WHERE spec_id = @spec_ver AND value = '标准版');
SET @v_quiet = (SELECT id FROM product_spec_values WHERE spec_id = @spec_ver AND value = '静音版');

DELETE FROM product_skus WHERE product_id = @kb;
-- spec_value_ids 必须**升序**拼接 —— 颜色值先插，所以它的 id 更小，直接拼就是升序
INSERT INTO product_skus (product_id, spec_value_ids, spec_text, price, stock, sales, image, status) VALUES
(@kb, CONCAT(@c_black, ',', @v_std),   '颜色:黑色;版本:标准版', 129.00, 40, 520, '', 1),
(@kb, CONCAT(@c_black, ',', @v_quiet), '颜色:黑色;版本:静音版', 149.00, 26, 210, '', 1),
(@kb, CONCAT(@c_white, ',', @v_std),   '颜色:白色;版本:标准版', 129.00, 18, 95,  '', 1),
(@kb, CONCAT(@c_white, ',', @v_quiet), '颜色:白色;版本:静音版', 149.00, 4,  40,  '', 1);

-- 汇总字段跟着重算：列表页靠 price 显示最低价、靠 has_specs 决定要不要加「起」
UPDATE products SET has_specs = 1 WHERE id IN (@hdmi, @kb);
UPDATE products p
SET price = (SELECT MIN(price) FROM product_skus WHERE product_id = p.id AND status = 1),
    stock = (SELECT SUM(stock) FROM product_skus WHERE product_id = p.id AND status = 1),
    sales = (SELECT SUM(sales) FROM product_skus WHERE product_id = p.id AND status = 1)
WHERE p.id IN (@hdmi, @kb);
