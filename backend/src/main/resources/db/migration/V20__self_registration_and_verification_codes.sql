-- V20: 自助注册（手机必填 / 邮箱可选）+ 双验证码 + 管理员审批（t102）
--
-- 1) users：phone 已存在（Hibernate 建过，可空），此处补唯一索引；新增 email_verified / phone_verified
-- 2) registration_requests：注册申请（审批前不产生可登录账号）
-- 3) verification_codes：验证码（只存哈希；5 分钟内同 target+purpose 只发一次；单 target 每小时上限）
--
-- 幂等：ADD COLUMN / CREATE INDEX 均带 IF NOT EXISTS（MariaDB 10.x 与 H2 2.x 支持）。
-- 生产 Flyway 关闭（SPRING_FLYWAY_ENABLED=false）→ 手工执行 deploy/manual-ddl.sql 的「V20」段。

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- 手机号唯一（可空：历史用户允许为空；NULL 不参与唯一性冲突）
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_phone ON users (phone);

CREATE TABLE IF NOT EXISTS registration_requests (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    email          VARCHAR(255) NULL,
    phone_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)  NOT NULL,
    reviewer_id    VARCHAR(36)  NULL,
    review_note    VARCHAR(500) NULL,
    created_at     DATETIME(6)  NOT NULL,
    reviewed_at    DATETIME(6)  NULL
);

CREATE INDEX IF NOT EXISTS idx_registration_requests_status ON registration_requests (status, created_at);
CREATE INDEX IF NOT EXISTS idx_registration_requests_username ON registration_requests (username);
CREATE INDEX IF NOT EXISTS idx_registration_requests_phone ON registration_requests (phone);

CREATE TABLE IF NOT EXISTS verification_codes (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    target      VARCHAR(120) NOT NULL,
    purpose     VARCHAR(20)  NOT NULL,
    code_hash   VARCHAR(255) NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    consumed_at DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_verification_codes_target ON verification_codes (target, purpose, created_at);
