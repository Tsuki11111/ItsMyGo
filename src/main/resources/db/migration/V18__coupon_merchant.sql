-- 优惠券归属：merchant_id 为 NULL 表示平台券，非空表示店铺券。
-- user_coupons.merchant_id 是领取时的快照。
-- 存量券保持 NULL，即平台券，无需回填。

ALTER TABLE coupons
    ADD COLUMN merchant_id BIGINT NULL COMMENT '所属商户；NULL=平台券' AFTER id,
    ADD KEY idx_merchant_status (merchant_id, status);

ALTER TABLE user_coupons
    ADD COLUMN merchant_id BIGINT NULL COMMENT '领取时的商户快照；NULL=平台券' AFTER coupon_id,
    ADD KEY idx_user_merchant (user_id, merchant_id, status);
