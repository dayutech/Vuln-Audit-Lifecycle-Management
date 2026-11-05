-- V22: 系统设置（key/value，t109）
--
-- 用途：承载「由管理员统一设置、对全站生效」的参数。首个用途是登录页缺省主题
-- login.defaultTheme（未登录访客在登录页看到的那一套；用户自行选择后以本人偏好为准）。
--
-- 幂等：CREATE TABLE / CREATE INDEX 均带 IF NOT EXISTS（MariaDB 10.x 与 H2 2.x 支持）。
-- 生产 Flyway 关闭（SPRING_FLYWAY_ENABLED=false）→ 手工执行 deploy/manual-ddl.sql 的「V22」段。
--
-- 说明：默认值不在这里 INSERT —— 由应用启动时 SystemSettingServiceImpl#ensureDefaults()
-- 幂等补行（多实例并发由唯一键兜底），这样默认值只存在于 LoginTheme 枚举一处。

CREATE TABLE IF NOT EXISTS system_settings (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL,
    setting_value TEXT         NULL,
    updated_by    VARCHAR(36)  NULL,
    updated_at    DATETIME(6)  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_system_settings_key ON system_settings (setting_key);
