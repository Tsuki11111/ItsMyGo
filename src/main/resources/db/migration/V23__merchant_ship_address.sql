-- 商户发货地址（M7.6）
--
-- 发货时优先用商家的发货地址作为物流起点；没设坐标时回退到虚拟转运中心。
-- 平台自营商户 id=1 也使用同一套字段，由管理员在 /admin/merchants 设置。
ALTER TABLE merchants
    ADD COLUMN ship_province VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '发货地址-省' AFTER contact_phone,
    ADD COLUMN ship_city     VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '发货地址-市' AFTER ship_province,
    ADD COLUMN ship_district VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '发货地址-区县' AFTER ship_city,
    ADD COLUMN ship_address  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '发货地址-详细' AFTER ship_district,
    ADD COLUMN ship_lng      DECIMAL(10, 6) NULL COMMENT '发货地经度' AFTER ship_address,
    ADD COLUMN ship_lat      DECIMAL(10, 6) NULL COMMENT '发货地纬度' AFTER ship_lng;
