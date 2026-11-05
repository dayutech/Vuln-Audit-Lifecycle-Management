-- V21: 忘记密码（验证码重置 + 旧 token 失效）配套变更（t103）
--
-- 1) users.token_invalid_before：重置密码后写入「签发时间早于此时间戳的 JWT 一律失效」。
--    当前是无状态 JWT（无 token 黑名单），因此用「用户级时间戳 + JWT iat 比较」实现全端下线。
-- 2) 历史账号的邮箱回填为已验证：email_verified 列是 t102 才引入的，既有账号（管理员代建）的邮箱
--    在创建时已由管理员核对，若不回填会导致「忘记密码」对所有历史账号静默不可用。
--    占位邮箱（<username>@no-email.local）保持未验证——它不可达，验证也没有意义。
--
-- 幂等：ADD COLUMN IF NOT EXISTS；回填 UPDATE 只影响 email_verified=false 的行，重复执行不再变更。

ALTER TABLE users ADD COLUMN IF NOT EXISTS token_invalid_before DATETIME(6) NULL;

UPDATE users
   SET email_verified = TRUE
 WHERE email_verified = FALSE
   AND email IS NOT NULL
   AND email LIKE '%@%'
   AND email NOT LIKE '%@no-email.local';
