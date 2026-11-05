-- V23: 用户登录页主题补第三态（NULL = 跟随系统默认，t112）
--
-- 背景：V19 建列时是 NOT NULL DEFAULT 'pencil'，导致「跟随系统默认」只能把**当时的**缺省值
-- 写死进用户行；管理员之后改缺省（system_settings.login.defaultTheme）这些用户跟随不上，
-- 「从账号同步」还会把旧值搬回来（t110 自报的局限）。
--
-- 语义（三态）：
--   login_theme IS NULL      → 跟随系统默认（管理员缺省值，随时可变）
--   login_theme = <code>     → 用户亲自指定的主题（管理员改缺省不影响）
--   历史行仍是具体值（如 'pencil'）→ 保持不变，视为用户指定（本迁移**不修改任何既有值**）
--
-- 幂等：MariaDB 10.x 没有 PostgreSQL 的 `ALTER COLUMN … DROP NOT NULL` 语法，
-- 用 MODIFY COLUMN 表达同一语义；重复执行结果一致，不影响既有数据。
-- 生产 Flyway 关闭（SPRING_FLYWAY_ENABLED=false）→ 手工执行 deploy/manual-ddl.sql 的「V23」段。

ALTER TABLE users MODIFY COLUMN login_theme VARCHAR(32) NULL DEFAULT NULL;
