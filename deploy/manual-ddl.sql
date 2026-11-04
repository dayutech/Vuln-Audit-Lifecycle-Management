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



-- -----------------------------------------------------------------------------
-- 4. 客户 / 项目 / 期次 / 资产挂靠（V18，t67/t75）
--    生产 Flyway 关闭 → 本段是 V18 的等价手工脚本（与迁移文件内容一致，幂等可重复执行）。
--    要点：纯新增表，不 ALTER 既有表；含默认客户 CUST-DEFAULT、历史项目 PRJ-HISTORY 与
--          存量资产回填（每个资产至少一条挂靠，linkType=SHARED）。
--    执行顺序：建表 → 建索引 → 种子行 → 回填 → 校验。
-- -----------------------------------------------------------------------------

-- ============================================================================
-- V18：客户 / 项目 / 期次 / 资产挂靠 领域模型 + 历史数据回填（t67）
-- ----------------------------------------------------------------------------
-- 背景：平台目前只有「资产」一层，缺少客户、项目、期次的业务归属，导致
--   · 无法按客户/项目出报告与统计；
--   · 同一资产服务多个客户/项目时无法表达。
-- 本迁移只建表 + 回填，不改任何既有表结构（assets 不加 project_id，见 README §33）。
--
-- 幂等性：全部语句可重复执行
--   · 建表 / 建索引 → IF NOT EXISTS
--   · 种子行 → INSERT ... SELECT ... WHERE NOT EXISTS
--   · 挂靠回填 → INSERT ... SELECT ... WHERE NOT EXISTS（配合唯一索引双保险）
-- 兼容性：不使用窗口函数、不使用 MariaDB 生成列语法（phase_key 为普通列，由应用/迁移写入），
--   全部为标准 SQL，MariaDB 10.x 与 H2 均可执行。
-- ============================================================================

-- 1) 客户
CREATE TABLE IF NOT EXISTS customers (
    id             UUID          NOT NULL PRIMARY KEY,
    code           VARCHAR(64)   NOT NULL,
    name           VARCHAR(200)  NOT NULL,
    industry       VARCHAR(100)  NULL,
    level          VARCHAR(8)    NULL,
    contact_name   VARCHAR(100)  NULL,
    contact_phone  VARCHAR(50)   NULL,
    contact_email  VARCHAR(200)  NULL,
    owner_id       UUID          NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'active',
    description    TEXT          NULL,
    created_by     UUID          NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_customers_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_customers_status ON customers (status);
CREATE INDEX IF NOT EXISTS idx_customers_owner ON customers (owner_id);

-- 2) 项目
CREATE TABLE IF NOT EXISTS projects (
    id             UUID          NOT NULL PRIMARY KEY,
    code           VARCHAR(64)   NOT NULL,
    customer_id    UUID          NOT NULL,
    name           VARCHAR(200)  NOT NULL,
    type           VARCHAR(32)   NOT NULL DEFAULT 'PENTEST',
    status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    manager_id     UUID          NOT NULL,
    start_date     DATE          NULL,
    end_date       DATE          NULL,
    is_phased      BOOLEAN       NOT NULL DEFAULT FALSE,
    contract_no    VARCHAR(100)  NULL,
    description    TEXT          NULL,
    created_by     UUID          NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_projects_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_projects_customer ON projects (customer_id);
CREATE INDEX IF NOT EXISTS idx_projects_manager ON projects (manager_id);
CREATE INDEX IF NOT EXISTS idx_projects_status ON projects (status);

-- 3) 项目期次
CREATE TABLE IF NOT EXISTS project_phases (
    id             UUID          NOT NULL PRIMARY KEY,
    project_id     UUID          NOT NULL,
    phase_no       INT           NOT NULL,
    name           VARCHAR(200)  NOT NULL,
    start_date     DATE          NULL,
    end_date       DATE          NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'PLANNED',
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_phases_project_no UNIQUE (project_id, phase_no)
);
CREATE INDEX IF NOT EXISTS idx_project_phases_project ON project_phases (project_id);
CREATE INDEX IF NOT EXISTS idx_project_phases_status ON project_phases (status);

-- 4) 资产—项目挂靠
--    phase_key = COALESCE(phase_id, '00000000-0000-0000-0000-000000000000')：
--    MariaDB 唯一索引对 NULL 不去重，靠这个非空列实现「同一资产在同一项目下无期次挂靠只允许一条」。
CREATE TABLE IF NOT EXISTS asset_project_links (
    id             UUID          NOT NULL PRIMARY KEY,
    asset_id       UUID          NOT NULL,
    project_id     UUID          NOT NULL,
    phase_id       UUID          NULL,
    phase_key      UUID          NOT NULL,
    customer_id    UUID          NOT NULL,
    link_type      VARCHAR(20)   NOT NULL DEFAULT 'TARGET',
    remark         VARCHAR(500)  NULL,
    linked_by      UUID          NULL,
    linked_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_asset_project_links_asset_project_phase UNIQUE (asset_id, project_id, phase_key)
);
CREATE INDEX IF NOT EXISTS idx_asset_project_links_asset ON asset_project_links (asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_project_links_project ON asset_project_links (project_id);
CREATE INDEX IF NOT EXISTS idx_asset_project_links_phase ON asset_project_links (phase_id);
CREATE INDEX IF NOT EXISTS idx_asset_project_links_customer ON asset_project_links (customer_id);

-- 5) 回填：默认客户「历史数据」
INSERT INTO customers (id, code, name, industry, level, status, description, created_at, updated_at)
SELECT '00000000-0000-0000-0000-00000000c001',
       'CUST-DEFAULT',
       '历史数据',
       '未分类',
       'C',
       'active',
       'V18 迁移自动创建：承接迁移前的存量资产',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE code = 'CUST-DEFAULT');

-- 6) 回填：历史项目「历史项目」，项目经理想取第一个 admin（角色 code='admin'），
--    取不到时退回第一个用户，都没有则留空 UUID 哨兵（仅用于满足 NOT NULL）。
INSERT INTO projects (id, code, customer_id, name, type, status, manager_id,
                      start_date, end_date, is_phased, description, created_at, updated_at)
SELECT '00000000-0000-0000-0000-00000000b001',
       'PRJ-HISTORY',
       (SELECT id FROM customers WHERE code = 'CUST-DEFAULT'),
       '历史项目',
       'OTHER',
       'ACTIVE',
       COALESCE(
           (SELECT ur.user_id
              FROM user_roles ur
              JOIN roles r ON r.id = ur.role_id
             WHERE r.code = 'admin'
             ORDER BY ur.user_id
             LIMIT 1),
           (SELECT u.id FROM users u ORDER BY u.created_at, u.id LIMIT 1),
           '00000000-0000-0000-0000-000000000000'
       ),
       NULL,
       NULL,
       FALSE,
       'V18 迁移自动创建：承接迁移前的存量资产',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM projects WHERE code = 'PRJ-HISTORY');

-- 7) 回填：现有全部资产挂到历史项目（项目级、SHARED、客户=默认客户）
INSERT INTO asset_project_links (id, asset_id, project_id, phase_id, phase_key,
                                 customer_id, link_type, remark, linked_at)
SELECT a.id,
       a.id,
       (SELECT id FROM projects WHERE code = 'PRJ-HISTORY'),
       NULL,
       '00000000-0000-0000-0000-000000000000',
       (SELECT id FROM customers WHERE code = 'CUST-DEFAULT'),
       'SHARED',
       'V18 迁移自动回填',
       CURRENT_TIMESTAMP
FROM assets a
WHERE NOT EXISTS (
    SELECT 1
      FROM asset_project_links l
     WHERE l.asset_id = a.id
       AND l.project_id = (SELECT id FROM projects WHERE code = 'PRJ-HISTORY')
       AND l.phase_key = '00000000-0000-0000-0000-000000000000'
);


-- 校验：四张表齐全（期望 4 行）
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('customers','projects','project_phases','asset_project_links')
ORDER BY table_name;

-- 校验：唯一约束（期望 4 行）
SELECT table_name, index_name FROM information_schema.statistics
WHERE table_schema = DATABASE() AND non_unique = 0
  AND index_name IN ('uk_customers_code','uk_projects_code',
                     'uk_project_phases_project_no','uk_asset_project_links_asset_project_phase')
GROUP BY table_name, index_name ORDER BY 1;

-- 校验：默认客户与历史项目存在（期望各 1）
SELECT COUNT(*) AS cust_default FROM customers WHERE code = 'CUST-DEFAULT';
SELECT COUNT(*) AS prj_history  FROM projects  WHERE code = 'PRJ-HISTORY';

-- 校验【硬断言】：无挂靠资产必须为 0
SELECT COUNT(*) AS orphan_assets FROM assets a
WHERE NOT EXISTS (SELECT 1 FROM asset_project_links l WHERE l.asset_id = a.id);


-- =============================================================================
-- V19：用户登录页主题偏好（t96 / t100）
-- =============================================================================
-- 登录页 8 套主题（角色组 pencil / geometric / owl / pixel-eyes；抽象极简组 aurora / mono-grid / orbit / big-type），
-- 用户选择后跨设备保留。列宽 VARCHAR(32) 足够容纳最长主题码（pixel-eyes = 10）。
-- 白名单在应用侧 LoginTheme 枚举中维护，DDL 无枚举约束（扩展主题不需要改表）。
-- 幂等：ADD COLUMN IF NOT EXISTS（MariaDB 10.x 支持；重复执行不报错）。
ALTER TABLE users ADD COLUMN IF NOT EXISTS login_theme VARCHAR(32) NOT NULL DEFAULT 'pencil';

-- 校验：列存在且默认值为 pencil
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'login_theme';

-- 校验：存量用户全部落到默认主题（不应有 NULL / 空串）
SELECT COUNT(*) AS users_without_theme FROM users WHERE login_theme IS NULL OR login_theme = '';


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
