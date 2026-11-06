-- =============================================================================
-- V26：双因子认证（TOTP + 恢复码）+ remember-me 长效令牌（t124/t125）
-- =============================================================================
-- 幂等：MariaDB 10.x 支持 ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS；
-- 索引用「先查 information_schema 再建」以兼容老版本。H2 测试走 ddl-auto=create-drop，不需要本脚本。
--
-- ⚠ 排序规则（t134）：新表显式声明 utf8mb4_unicode_ci，不依赖库默认 —— 与其他业务表保持一致，
-- 也避免将来与既有表（unicode_ci）做「列 = 列」比较时抛 1267 Illegal mix of collations。

-- 1) users：2FA 三列
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(128) NULL;
-- 密文为 1: + base64(iv[12]+ct+tag) ≈ 83 字符，64 会截断/报错 → 统一放宽到 128（幂等）
ALTER TABLE users MODIFY COLUMN totp_secret VARCHAR(128) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_confirmed_at DATETIME NULL;

-- 2) remember-me 长效令牌（t126 使用）：Cookie 只放 seriesId.secret，库里只存 SHA-256(secret)
CREATE TABLE IF NOT EXISTS remember_me_tokens (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL,
    series_id     VARCHAR(64)  NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    last_used_at  DATETIME(6)  NULL,
    revoked_at    DATETIME(6)  NULL,
    user_agent    VARCHAR(255) NULL,
    ip            VARCHAR(64)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX IF NOT EXISTS idx_rm_user ON remember_me_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_rm_series ON remember_me_tokens (series_id);
CREATE INDEX IF NOT EXISTS idx_rm_hash ON remember_me_tokens (token_hash);
-- token_hash 唯一（同一 secret 不可能出现两行）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'remember_me_tokens'
                      AND index_name = 'uk_rm_hash');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE remember_me_tokens ADD UNIQUE KEY uk_rm_hash (token_hash)',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 双因子恢复码（一次性，BCrypt 哈希存储）
CREATE TABLE IF NOT EXISTS two_factor_recovery_codes (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    code_hash  VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX IF NOT EXISTS idx_2fa_rc_user ON two_factor_recovery_codes (user_id);

-- 校验
SELECT COUNT(*) AS has_2fa_columns FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'users'
   AND column_name IN ('two_factor_enabled', 'totp_secret', 'two_factor_confirmed_at');
SHOW TABLES LIKE 'remember_me_tokens';
SHOW TABLES LIKE 'two_factor_recovery_codes';
SELECT COUNT(*) AS enabled_users FROM users WHERE two_factor_enabled = 1;
