-- 活动管理权限码（2026-11-28）
-- 活动管理页此前只有 /api/admin/** 的 ADMIN 角色兜底，未接 RBAC 权限码。

INSERT INTO permissions (code, name, module, type) VALUES
('admin:activity:view', '活动查看', 'activity', 'API'),
('admin:activity:manage', '活动管理', 'activity', 'API');

-- SUPER_ADMIN：全部权限（与 V36 seed 保持一致）
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON r.code = 'SUPER_ADMIN'
WHERE p.code IN ('admin:activity:view', 'admin:activity:manage');

-- OPERATOR（运营）：活动配置属于日常运营职责
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON r.code = 'OPERATOR'
WHERE p.code IN ('admin:activity:view', 'admin:activity:manage');
