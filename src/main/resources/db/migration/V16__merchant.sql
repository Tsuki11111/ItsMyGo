-- 多商户地基：商户主体 + 用户归属。
--
-- id=1 是「平台自营」默认商户，owner_user_id 为 NULL：
-- 它承接改造前的所有存量商品/订单/评价，只能由平台管理员管理，
-- 不允许审核、禁用或转让。新商户从 PENDING 开始，平台审核通过后变 ACTIVE。

CREATE TABLE merchants
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '商户 ID',
    name           VARCHAR(64)  NOT NULL COMMENT '店铺名',
    logo           VARCHAR(512) NOT NULL DEFAULT '' COMMENT '店铺 Logo URL',
    description    VARCHAR(500) NOT NULL DEFAULT '' COMMENT '店铺简介',
    contact_name   VARCHAR(32)  NOT NULL COMMENT '联系人',
    contact_phone  VARCHAR(20)  NOT NULL COMMENT '联系电话',
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/REJECTED/DISABLED',
    owner_user_id  BIGINT       NULL COMMENT '店主用户；默认商户为 NULL',
    reject_reason  VARCHAR(255) NOT NULL DEFAULT '' COMMENT '驳回原因',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name),
    UNIQUE KEY uk_owner (owner_user_id),
    KEY idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '商户/店铺';

INSERT INTO merchants (id, name, description, contact_name, contact_phone, status)
VALUES (1, '平台自营', '平台自营店铺', '平台', '10000000000', 'ACTIVE');

ALTER TABLE users
    ADD COLUMN merchant_id BIGINT NULL COMMENT '所属商户；平台管理员/普通顾客为 NULL' AFTER role,
    ADD KEY idx_merchant (merchant_id);
