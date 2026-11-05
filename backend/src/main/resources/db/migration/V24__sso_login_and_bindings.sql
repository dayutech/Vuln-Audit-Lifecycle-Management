-- V24: 第三方 SSO 登录（GitHub / Microsoft / Google / WeChat，t104）
--
-- 1) sso_providers：提供商配置（凭据不写死在代码里，从本表/环境变量读取）。
--    启动时若表为空，应用会插入 4 条默认 enabled=false 的记录（见 SsoProviderBootstrapRunner）。
-- 2) sso_bindings：本地账号 ↔ 第三方身份（unique(provider_code, external_id)）。
-- 3) sso_pending_bindings：SSO 首次登录产生的「待审批绑定」，审批通过后转成 sso_bindings。
-- 4) registration_requests 增加 source/provider_code/external_id/display_name，
--    并把 phone 改为可空（SSO 注册没有手机号）。
--
-- 幂等：CREATE TABLE / CREATE INDEX / ADD COLUMN IF NOT EXISTS；MODIFY COLUMN 可重复执行。

CREATE TABLE IF NOT EXISTS sso_providers (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    code          VARCHAR(32)  NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    icon          VARCHAR(64)  NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    client_id     VARCHAR(255) NULL,
    client_secret VARCHAR(512) NULL,
    authorize_url VARCHAR(512) NULL,
    token_url     VARCHAR(512) NULL,
    userinfo_url  VARCHAR(512) NULL,
    scopes        VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sso_providers_code ON sso_providers (code);

CREATE TABLE IF NOT EXISTS sso_bindings (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NULL,
    bound_at    DATETIME(6)  NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sso_bindings_provider_external
    ON sso_bindings (provider_code, external_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sso_bindings_user_provider
    ON sso_bindings (user_id, provider_code);

CREATE TABLE IF NOT EXISTS sso_pending_bindings (
    id                      VARCHAR(36)  NOT NULL PRIMARY KEY,
    registration_request_id VARCHAR(36)  NULL,
    provider_code           VARCHAR(32)  NOT NULL,
    external_id             VARCHAR(255) NOT NULL,
    email                   VARCHAR(255) NULL,
    display_name            VARCHAR(128) NULL,
    status                  VARCHAR(20)  NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    activated_at            DATETIME(6)  NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sso_pending_provider_external
    ON sso_pending_bindings (provider_code, external_id);
CREATE INDEX IF NOT EXISTS idx_sso_pending_request
    ON sso_pending_bindings (registration_request_id);

ALTER TABLE registration_requests ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE registration_requests ADD COLUMN IF NOT EXISTS provider_code VARCHAR(32) NULL;
ALTER TABLE registration_requests ADD COLUMN IF NOT EXISTS external_id VARCHAR(255) NULL;
ALTER TABLE registration_requests ADD COLUMN IF NOT EXISTS display_name VARCHAR(128) NULL;

-- SSO 注册没有手机号：去掉 NOT NULL（可重复执行）
ALTER TABLE registration_requests MODIFY COLUMN phone VARCHAR(20) NULL;
