-- =============================================================================
-- 生产环境手工 DDL 汇总（线上 SPRING_FLYWAY_ENABLED=false + ddl-auto=update）
-- =============================================================================
-- 背景：生产为 compatibility 考虑关闭了 Flyway，改由 Hibernate 建表（ddl-auto=update）。
-- Hibernate 能补列，但**不会**：
--   1) 扩展已有列的宽度（如把 varchar(20) 改成 varchar(50)）；
--   2) 给已有列补 NOT NULL（需要先回填数据）；
--   3) 建唯一索引（并发语义依赖它）。
-- 因此下列三类变更必须在部署时手工执行。脚本设计为**可重复执行**（IF NOT EXISTS /
-- 带 WHERE 条件的 UPDATE），执行前请先备份数据库。
--
-- 执行方式（在 192.168.188.200 上）：
--   MYSQL_PWD="$DB_PASSWORD" mariadb -uvulnapp vuln_audit < deploy/manual-ddl.sql
-- 逐段执行并核对每段末尾的「校验」输出，再重启后端：
--   systemctl --user restart vuln-audit-backend
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. notifications.type 扩宽（t16）
--    问题：默认类型 vulnerability_discovered 为 24 字符，而原列宽 20，
--          导致 POST /notifications/vulnerability-discovered 返回 500。
-- -----------------------------------------------------------------------------
ALTER TABLE notifications MODIFY COLUMN type VARCHAR(50) NOT NULL;

-- 校验：期望 Type=varchar(50)，Null=NO
SHOW COLUMNS FROM notifications LIKE 'type';

-- 业务校验：不传 type 应返回 200（部署并重启后执行）
--   curl -s -o /dev/null -w '%{http_code}\n' -X POST \
--     "$BASE/api/v1/notifications/vulnerability-discovered?vulnerabilityId=<uuid>&recipientIds=<uuid>" \
--     -H "Authorization: Bearer $TOKEN"


-- -----------------------------------------------------------------------------
-- 2. tool_versions 增加版本号与文件字段（t23）
--    问题：上传工具版本时 createdBy 为 null 触发 NOT NULL，且未处理上传文件。
--    注意：version_no 需要先回填再置 NOT NULL，并建唯一索引以支持并发 409 语义。
-- -----------------------------------------------------------------------------
ALTER TABLE tool_versions
    ADD COLUMN IF NOT EXISTS version_no INT NULL,
    ADD COLUMN IF NOT EXISTS file_name  VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS file_size  BIGINT NULL;

-- 回填历史版本号（按创建时间排序；MariaDB 无窗口函数时用自连接方案）
UPDATE tool_versions tv
    JOIN (
        SELECT a.id AS id, COUNT(*) AS rn
        FROM tool_versions a
            JOIN tool_versions b
                ON a.tool_id = b.tool_id
                AND (b.created_at < a.created_at
                     OR (b.created_at = a.created_at AND b.id <= a.id))
        GROUP BY a.id
    ) ranked ON ranked.id = tv.id
SET tv.version_no = ranked.rn
WHERE tv.version_no IS NULL;

ALTER TABLE tool_versions MODIFY COLUMN version_no INT NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_tool_versions_tool_version_no
    ON tool_versions (tool_id, version_no);

-- 校验：期望为空集
SELECT tool_id, version_no, COUNT(*) c
FROM tool_versions GROUP BY tool_id, version_no HAVING c > 1;


-- -----------------------------------------------------------------------------
-- 3. document_versions 版本号唯一化（t32）
--    问题：无唯一约束时并发更新会算出相同版本号，历史排序/回滚/审计追溯失效。
--    注意：实体列名是 version（不是 version_no）。
-- -----------------------------------------------------------------------------
UPDATE document_versions dv
    JOIN (
        SELECT a.id AS id, COUNT(*) AS new_version
        FROM document_versions a
            JOIN document_versions b
                ON a.document_id = b.document_id
                AND (b.created_at < a.created_at
                     OR (b.created_at = a.created_at AND b.id <= a.id))
        GROUP BY a.id
    ) ranked ON ranked.id = dv.id
SET dv.version = ranked.new_version
WHERE dv.document_id IN (
    SELECT document_id FROM (
        SELECT document_id
        FROM document_versions
        GROUP BY document_id, version
        HAVING COUNT(*) > 1
    ) d
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_document_versions_document_version
    ON document_versions (document_id, version);

-- 校验：期望为空集
SELECT document_id, version, COUNT(*) c
FROM document_versions GROUP BY document_id, version HAVING c > 1;


-- =============================================================================
-- 执行完成后的统一核对
-- =============================================================================
-- 1) Flyway 版本号唯一（防止后续误引入重号）：
--    ls backend/src/main/resources/db/migration/V*.sql | sort -V
-- 2) 后端健康与关键接口：
--    curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8088/actuator/health
-- 3) RBAC 幂等（重启两次，行数不变）：
--    SELECT COUNT(*) FROM permissions;        -- 期望 90
--    SELECT COUNT(*) FROM role_permissions;   -- 期望 261
-- 4) 冒烟：bash scripts/smoke.sh
-- =============================================================================
