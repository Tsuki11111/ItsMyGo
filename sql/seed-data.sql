-- 种子数据：电子设备商城
-- 商品型号尽量取自 rag_helper 的产品手册语料，将来接入智能客服时"问这个打印机怎么用"能真的查到手册

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
