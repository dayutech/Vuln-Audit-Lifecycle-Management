-- V19: 用户登录页主题偏好（t96）
-- 登录页提供 4 套主题（pencil / geometric / owl / pixel-eyes），用户选择后跨设备保留。
-- 幂等：ADD COLUMN IF NOT EXISTS（MariaDB 10.x 与 H2 2.x 均支持）；生产 Flyway 关闭时用 deploy/manual-ddl.sql 等价段。

ALTER TABLE users ADD COLUMN IF NOT EXISTS login_theme VARCHAR(32) NOT NULL DEFAULT 'pencil';