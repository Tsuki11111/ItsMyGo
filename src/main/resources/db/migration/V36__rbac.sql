-- 权限管理（RBAC v1，2026-11-08 设计）
-- 用户类型仍用 users.role（CUSTOMER/MERCHANT/ADMIN）；管理端子角色走 admin_user_roles。
-- 由 itsmygo.rbac.enabled 控制：false 时管理员视为拥有全部权限码（兼容现状）。

CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(64)  NOT NULL COMMENT 'SUPER_ADMIN / OPERATOR / CUSTOMER_SERVICE / FINANCE',
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    builtin     TINYINT      NOT NULL DEFAULT 1 COMMENT 'v1 固定角色，禁止改码/删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理端角色';

CREATE TABLE permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(96)  NOT NULL COMMENT 'admin:order:ship',
    name        VARCHAR(64)  NOT NULL,
    module      VARCHAR(32)  NOT NULL COMMENT 'order / aftersale / merchant / product ...',
    type        VARCHAR(16)  NOT NULL COMMENT 'API / MENU / BUTTON',
    description VARCHAR(255) NOT NULL DEFAULT '',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限点';

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permissions_permission (permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-权限';

CREATE TABLE admin_user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_admin_user_roles_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员-角色（仅 users.role=ADMIN 使用）';

INSERT INTO roles (code, name, description) VALUES
('SUPER_ADMIN', '超级管理员', '全部权限，含管理员管理/退款/审计'),
('OPERATOR', '运营', '商品/券/知识库/评测/订单查看导出'),
('CUSTOMER_SERVICE', '客服', '订单查看、售后处理、聊天、用户查看'),
('FINANCE', '财务', '订单查看导出、退款审核、审计查看');

INSERT INTO permissions (code, name, module, type) VALUES
('admin:dashboard:view', '数据概览', 'dashboard', 'API'),
('admin:order:view', '订单查看', 'order', 'API'),
('admin:order:export', '订单导出', 'order', 'API'),
('admin:order:ship', '订单代发货', 'order', 'API'),
('admin:order:refund', '退款审核', 'order', 'API'),
('admin:order:cancel-expired', '取消超时订单', 'order', 'API'),
('admin:aftersale:view', '售后查看', 'aftersale', 'API'),
('admin:aftersale:handle', '售后处理', 'aftersale', 'API'),
('admin:aftersale:reason', '售后原因字典', 'aftersale', 'API'),
('admin:merchant:view', '商家查看', 'merchant', 'API'),
('admin:merchant:review', '商家审核', 'merchant', 'API'),
('admin:merchant:ship-address', '商家发货地址', 'merchant', 'API'),
('admin:product:view', '商品查看', 'product', 'API'),
('admin:product:manage', '商品管理', 'product', 'API'),
('admin:user:view', '用户查看', 'user', 'API'),
('admin:user:manage', '用户处置', 'user', 'API'),
('admin:admin:view', '管理员查看', 'admin', 'API'),
('admin:admin:manage', '管理员管理', 'admin', 'API'),
('admin:knowledge:import', '知识库导入', 'knowledge', 'API'),
('admin:knowledge-gap:manage', '知识缺口', 'knowledge', 'API'),
('admin:agent-eval:view', '评测查看', 'agent-eval', 'API'),
('admin:agent-eval:run', '评测运行', 'agent-eval', 'API'),
('admin:coupon:manage', '平台券管理', 'coupon', 'API'),
('admin:audit:view', '审计日志', 'audit', 'API'),
('admin:notification:view', '站内通知', 'notification', 'API'),
('admin:chat:view', '客服会话查看', 'chat', 'API'),
('admin:chat:reply', '客服会话回复', 'chat', 'API'),
('admin:review:manage', '评价管理', 'review', 'API');

-- SUPER_ADMIN：全部权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON r.code = 'SUPER_ADMIN';

-- OPERATOR：概览/订单查看导出/商品/券/知识库/评测/评价/客服会话查看/通知
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON r.code = 'OPERATOR'
WHERE p.code IN ('admin:dashboard:view', 'admin:order:view', 'admin:order:export',
                 'admin:product:view', 'admin:product:manage', 'admin:coupon:manage',
                 'admin:knowledge:import', 'admin:knowledge-gap:manage',
                 'admin:agent-eval:view', 'admin:agent-eval:run', 'admin:review:manage',
                 'admin:chat:view', 'admin:notification:view');

-- CUSTOMER_SERVICE：概览/订单查看/售后/用户查看/客服会话/通知
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON r.code = 'CUSTOMER_SERVICE'
WHERE p.code IN ('admin:dashboard:view', 'admin:order:view',
                 'admin:aftersale:view', 'admin:aftersale:handle',
                 'admin:user:view', 'admin:chat:view', 'admin:chat:reply',
                 'admin:notification:view');

-- FINANCE：订单查看导出/退款审核/售后查看/审计/通知
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON r.code = 'FINANCE'
WHERE p.code IN ('admin:order:view', 'admin:order:export', 'admin:order:refund',
                 'admin:aftersale:view', 'admin:audit:view', 'admin:notification:view');

-- 存量 ADMIN → SUPER_ADMIN（幂等）
INSERT IGNORE INTO admin_user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'SUPER_ADMIN'
WHERE u.role = 'ADMIN';
