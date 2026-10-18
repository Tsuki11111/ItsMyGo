-- 用户头像。存对象存储里的公开 URL，空串表示没设置。
-- 头像文件走 MinIO 的 avatars/ 目录（和商品图的 products/ 分开）。
ALTER TABLE users
    ADD COLUMN avatar VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像 URL' AFTER phone;
