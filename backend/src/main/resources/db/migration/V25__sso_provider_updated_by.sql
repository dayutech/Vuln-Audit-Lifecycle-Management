-- V25: SSO 提供商「最后修改人」（t116）
--
-- 背景：t116 增加凭据 Web 配置接口，管理端列表需要展示 updatedBy（谁最后改的配置）。
-- 只加一列，不影响既有数据；旧行为 NULL（bootstrap 初始化 / 直接改库）。
--
-- 幂等：ADD COLUMN IF NOT EXISTS（MariaDB 10.x 与 H2 2.x 支持）。
-- 生产 Flyway 关闭（SPRING_FLYWAY_ENABLED=false）→ 手工执行 deploy/manual-ddl.sql 的「V25」段。

ALTER TABLE sso_providers ADD COLUMN IF NOT EXISTS updated_by VARCHAR(36) NULL;
