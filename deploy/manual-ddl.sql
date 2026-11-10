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
-- V20：自助注册（手机必填 / 邮箱可选）+ 双验证码 + 管理员审批（t102）
-- =============================================================================
-- 1) users：phone 列早已由 Hibernate 建出（可空），此处补唯一索引并新增两个 verified 标记；
--    历史用户 phone 为 NULL 不受影响（NULL 不参与唯一性冲突）。
-- 2) registration_requests：注册申请（审批前不产生可登录账号）
-- 3) verification_codes：验证码（只存 BCrypt 哈希；5 分钟内同 target+purpose 只发一次；单 target 每小时上限）
-- 幂等：ADD COLUMN / CREATE INDEX / CREATE TABLE 均带 IF NOT EXISTS（MariaDB 10.x）。
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE;
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

-- 校验：列与表存在、存量用户 verified 全为 0
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME IN ('phone','email_verified','phone_verified');
SELECT COUNT(*) AS users_phone_verified FROM users WHERE phone_verified = 1;
SHOW TABLES LIKE 'registration_requests';
SHOW TABLES LIKE 'verification_codes';

-- =============================================================================
-- V22：系统设置（key/value，t109）
-- =============================================================================
-- 用途：管理员统一设置的全局参数。首个设置项 login.defaultTheme = 登录页缺省主题
-- （未登录访客在登录页看到的那套；用户自行选择后以本人 users.login_theme 为准）。
-- 幂等：CREATE TABLE / CREATE INDEX 均带 IF NOT EXISTS，重复执行不报错、不影响已有数据。
-- 默认值不在此 INSERT：应用启动时 SystemSettingServiceImpl#ensureDefaults() 幂等补行
-- （多实例并发由 uk_system_settings_key 唯一键兜底）。
CREATE TABLE IF NOT EXISTS system_settings (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL,
    setting_value TEXT         NULL,
    updated_by    VARCHAR(36)  NULL,
    updated_at    DATETIME(6)  NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_system_settings_key ON system_settings (setting_key);

-- 校验：表与唯一索引存在
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_settings';
SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_settings';

-- 校验：缺省主题行（应用启动后应恰好 1 行，值为 8 个主题之一）
SELECT setting_key, setting_value, updated_by, updated_at FROM system_settings;

-- =============================================================================
-- V21：忘记密码（验证码重置 + 旧 token 失效）配套变更（t103）
-- =============================================================================
-- 1) users.token_invalid_before：重置密码后写入「签发时间早于此时间戳的 JWT 一律失效」，
--    JwtAuthenticationFilter 比较 token 的 iat 实现「改密后全端下线」（无状态 JWT 无需黑名单）。
-- 2) 历史账号邮箱回填为已验证：email_verified 是 t102 才引入的列，管理员代建的历史账号邮箱
--    在创建时已核对；不回填会导致「忘记密码」对所有历史账号静默不可用。
--    占位邮箱（<username>@no-email.local）保持未验证（不可达）。
-- 幂等：ADD COLUMN IF NOT EXISTS；UPDATE 只改 email_verified=false 的行，重复执行不再变更。
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_invalid_before DATETIME(6) NULL;

UPDATE users
   SET email_verified = TRUE
 WHERE email_verified = FALSE
   AND email IS NOT NULL
   AND email LIKE '%@%'
   AND email NOT LIKE '%@no-email.local';

-- 校验：列存在；回填后仍为未验证的只应是占位邮箱
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'token_invalid_before';
SELECT COUNT(*) AS unverified_real_emails FROM users
 WHERE email_verified = FALSE AND email NOT LIKE '%@no-email.local';

-- =============================================================================
-- V24：第三方 SSO 登录（GitHub / Microsoft / Google / WeChat，t104）
-- =============================================================================
-- 1) sso_providers：提供商配置。凭据（client_id/client_secret）不写死在代码里，从本表读取；
--    缺 client_id 或 client_secret 视为「未配置」——即使 enabled=true，authorize-url 也返回 503，
--    登录页按钮也不展示。启动时表为空会由应用插入 4 条默认记录（enabled=false）。
-- 2) sso_bindings：本地账号 ↔ 第三方身份（(provider_code, external_id) 与 (user_id, provider_code) 各唯一）。
-- 3) sso_pending_bindings：SSO 首次登录的「待审批绑定」，审批通过后转为 sso_bindings。
-- 4) registration_requests 增加 source/provider_code/external_id/display_name，phone 改为可空。
-- 幂等：CREATE TABLE/INDEX、ADD COLUMN IF NOT EXISTS；MODIFY COLUMN 可重复执行。

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
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL,
    provider_code VARCHAR(32)  NOT NULL,
    external_id   VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128) NULL,
    bound_at      DATETIME(6)  NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sso_bindings_provider_external ON sso_bindings (provider_code, external_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sso_bindings_user_provider ON sso_bindings (user_id, provider_code);

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
CREATE UNIQUE INDEX IF NOT EXISTS uk_sso_pending_provider_external ON sso_pending_bindings (provider_code, external_id);
CREATE INDEX IF NOT EXISTS idx_sso_pending_request ON sso_pending_bindings (registration_request_id);

ALTER TABLE registration_requests ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE registration_requests ADD COLUMN IF NOT EXISTS provider_code VARCHAR(32) NULL;
ALTER TABLE registration_requests ADD COLUMN IF NOT EXISTS external_id VARCHAR(255) NULL;
ALTER TABLE registration_requests ADD COLUMN IF NOT EXISTS display_name VARCHAR(128) NULL;
ALTER TABLE registration_requests MODIFY COLUMN phone VARCHAR(20) NULL;

-- 校验：三张表存在；4 条默认 provider（应用首次启动后）且默认全部未开启
SHOW TABLES LIKE 'sso_providers';
SHOW TABLES LIKE 'sso_bindings';
SHOW TABLES LIKE 'sso_pending_bindings';
SELECT code, name, enabled FROM sso_providers ORDER BY code;
SELECT COUNT(*) AS enabled_providers FROM sso_providers WHERE enabled = 1;
SELECT COUNT(*) AS pending_bindings FROM sso_pending_bindings WHERE status = 'PENDING';

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

-- =============================================================================
-- V23：用户登录页主题补第三态（NULL = 跟随系统默认，t112）
-- =============================================================================
-- V19 建列时是 NOT NULL DEFAULT 'pencil'，导致「跟随系统默认」只能把当时的缺省值写死进用户行；
-- 管理员之后改缺省（system_settings.login.defaultTheme），这些用户跟随不上。
-- 现语义：
--   login_theme IS NULL  → 跟随系统默认（管理员缺省值，随时可变）
--   login_theme = <code> → 用户亲自指定（管理员改缺省不影响）
--   历史行仍是具体值（如 'pencil'）→ 保持不变，视为用户指定（本段**不修改任何既有值**）
-- 幂等：重复执行结果一致；MariaDB 10.x 用 MODIFY COLUMN（没有 PG 的 DROP NOT NULL 语法）。
ALTER TABLE users MODIFY COLUMN login_theme VARCHAR(32) NULL DEFAULT NULL;

-- 校验：列允许 NULL（期望 IS_NULLABLE = YES）、默认值为 NULL
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'login_theme';

-- 校验：三态分布（NULL=跟随默认、具体值=用户指定；历史 pencil 行属后者，保留）
SELECT CASE WHEN login_theme IS NULL THEN 'follow-default' ELSE 'explicit' END AS state,
       COUNT(*) AS users
FROM users GROUP BY state;

-- =============================================================================
-- V25：SSO 提供商「最后修改人」（t116）
-- =============================================================================
-- 凭据 Web 配置接口写入 updated_by（管理员用户ID），管理端列表回显「谁最后改的」。
-- 只加一列；旧行 NULL（bootstrap 初始化 / 直接改库）。幂等：ADD COLUMN IF NOT EXISTS。
ALTER TABLE sso_providers ADD COLUMN IF NOT EXISTS updated_by VARCHAR(36) NULL;

-- 校验：列存在且可空
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sso_providers' AND COLUMN_NAME = 'updated_by';

-- 校验：各提供商配置状态（configured=5 项齐全；secret 只关心是否有值，不要在 SQL 里输出明文）
SELECT code, enabled,
       (client_id IS NOT NULL AND client_id <> ''
        AND client_secret IS NOT NULL AND client_secret <> ''
        AND authorize_url IS NOT NULL AND authorize_url <> ''
        AND token_url IS NOT NULL AND token_url <> ''
        AND userinfo_url IS NOT NULL AND userinfo_url <> '') AS configured,
       (client_secret IS NOT NULL AND client_secret <> '') AS has_secret,
       updated_by
FROM sso_providers ORDER BY code;

-- =============================================================================
-- V26：双因子认证（TOTP + 恢复码）+ remember-me 长效令牌（t124/t125）
-- =============================================================================
-- 生产 Flyway 关闭，本段手工执行（等价于 backend/src/main/resources/db/migration/V26__two_factor_and_remember_me.sql）。
-- 幂等：ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS；索引用 information_schema 先查后建。
-- 列宽注意：TOTP 密文形如 v1:base64(iv[12] || ct || tag) = **83 字符**，
--          原设计的 VARCHAR(64) 会触发 "Value too long for column totp_secret"（t125 实测），故取 128。

-- 1) users：2FA 三列
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(128) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_confirmed_at DATETIME NULL;
-- 若历史库已按 64 建列 → 幂等加宽（只在长度 < 128 时执行 MODIFY）
SET @totp_len := (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'users'
                    AND column_name = 'totp_secret');
SET @ddl := IF(@totp_len IS NOT NULL AND @totp_len < 128,
    'ALTER TABLE users MODIFY COLUMN totp_secret VARCHAR(128) NULL',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) remember-me 长效令牌（Cookie 只放 seriesId.secret，库里只存 SHA-256(secret)）
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
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'remember_me_tokens'
                      AND index_name = 'uk_rm_hash');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE remember_me_tokens ADD UNIQUE KEY uk_rm_hash (token_hash)',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 双因子恢复码（一次性，BCrypt 哈希存储；明文只在 confirm 响应里出现一次）
CREATE TABLE IF NOT EXISTS two_factor_recovery_codes (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    code_hash  VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX IF NOT EXISTS idx_2fa_rc_user ON two_factor_recovery_codes (user_id);

-- 校验 1：三列存在，且 totp_secret 宽度 ≥ 83（期望 128）
SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
  AND COLUMN_NAME IN ('two_factor_enabled', 'totp_secret', 'two_factor_confirmed_at');

-- 校验 2：两张表的索引齐备（用户维度 + 唯一 token_hash）
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('remember_me_tokens', 'two_factor_recovery_codes')
ORDER BY TABLE_NAME, INDEX_NAME;

-- 校验 3：2FA 开启人数与剩余凭据（**不输出密文/明文**）
SELECT COUNT(*) AS enabled_users FROM users WHERE two_factor_enabled = 1;
SELECT COUNT(*) AS active_recovery_codes FROM two_factor_recovery_codes WHERE used_at IS NULL;
SELECT COUNT(*) AS active_remember_me FROM remember_me_tokens
 WHERE revoked_at IS NULL AND expires_at > NOW();


-- =============================================================================
-- V27：客户编码自动生成 + 国标行业枚举（t129）—— 与 backend/src/main/resources/db/migration/V27__industry_types_and_customer_code.sql 等价
-- =============================================================================
-- 幂等：CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS；回填按名称精确匹配且只填 NULL。
-- ⚠ 排序规则（t134）：新表与新列显式 utf8mb4_unicode_ci，回填 JOIN 两侧显式 COLLATE。
--   否则在「库默认 collation = utf8mb4_general_ci」的实例上会报
--   ERROR 1267 (HY000): Illegal mix of collations (utf8mb4_general_ci) and (utf8mb4_unicode_ci) for operation '='

CREATE TABLE IF NOT EXISTS industry_types (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    code        VARCHAR(16)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    parent_code VARCHAR(16)  NULL,
    builtin     BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order  INT          NOT NULL DEFAULT 0,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX IF NOT EXISTS uk_industry_types_code ON industry_types (code);
CREATE INDEX IF NOT EXISTS idx_industry_types_name ON industry_types (name);

-- 客户表：保留 industry 显示名，新增 industry_code（新列显式 collation）
ALTER TABLE customers ADD COLUMN IF NOT EXISTS industry_code VARCHAR(16)
    COLLATE utf8mb4_unicode_ci NULL;

-- 历史回填：按行业名称精确匹配（匹配不到保持 NULL，不阻塞）
UPDATE customers c
  JOIN industry_types t ON t.name COLLATE utf8mb4_unicode_ci = c.industry COLLATE utf8mb4_unicode_ci
   SET c.industry_code = t.code
 WHERE c.industry_code IS NULL
   AND c.industry IS NOT NULL;

-- 校验：表存在、内置条数、已回填客户数
SHOW TABLES LIKE 'industry_types';
SELECT COUNT(*) AS builtin_industries FROM industry_types WHERE builtin = 1;
SELECT COUNT(*) AS customers_with_industry_code FROM customers WHERE industry_code IS NOT NULL;


-- =============================================================================
-- V28：项目类型管理 + 项目编码（t130）—— 与 backend/src/main/resources/db/migration/V28__project_types_and_project_code.sql 等价
-- =============================================================================
-- 幂等：MariaDB 10.x 支持 ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS /
--       CREATE INDEX IF NOT EXISTS；内置项用 INSERT ... WHERE NOT EXISTS 保证只插一次。
--
-- 两套 DDL：
--   ① industry_types：补 deleted_at（软删，避免破坏历史客户引用）
--   ② project_types ：新表（内置 5 项 + 管理员自定义）
--   同时给 projects 补 type_code（引用 project_types.code，type 保留原字符串）

-- ---------------------------------------------------------------- ① 行业类型：软删列
ALTER TABLE industry_types ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
CREATE INDEX IF NOT EXISTS idx_industry_types_deleted ON industry_types (deleted_at);

-- ---------------------------------------------------------------- ② 项目类型表
CREATE TABLE IF NOT EXISTS project_types (
    id          VARCHAR(36) NOT NULL PRIMARY KEY,
    code        VARCHAR(32) NOT NULL,
    name        VARCHAR(50) NOT NULL,
    builtin     BOOLEAN     NOT NULL DEFAULT FALSE,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order  INT         NOT NULL DEFAULT 0,
    deleted_at  DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- code 全局唯一（含软删行）：软删后不可复用同一 code，避免历史项目引用歧义
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_types_code ON project_types (code);
CREATE INDEX IF NOT EXISTS idx_project_types_name ON project_types (name);
CREATE INDEX IF NOT EXISTS idx_project_types_deleted ON project_types (deleted_at);

-- ---------------------------------------------------------------- 内置项目类型（5 项，幂等）
INSERT INTO project_types (id, code, name, builtin, enabled, sort_order, created_at, updated_at)
SELECT UUID(), 'PENTEST', '渗透测试', 1, 1, 10, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM project_types WHERE code = 'PENTEST');

INSERT INTO project_types (id, code, name, builtin, enabled, sort_order, created_at, updated_at)
SELECT UUID(), 'CODE_AUDIT', '代码审计', 1, 1, 20, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM project_types WHERE code = 'CODE_AUDIT');

INSERT INTO project_types (id, code, name, builtin, enabled, sort_order, created_at, updated_at)
SELECT UUID(), 'EMERGENCY', '应急响应', 1, 1, 30, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM project_types WHERE code = 'EMERGENCY');

INSERT INTO project_types (id, code, name, builtin, enabled, sort_order, created_at, updated_at)
SELECT UUID(), 'COMPLIANCE', '合规测评', 1, 1, 40, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM project_types WHERE code = 'COMPLIANCE');

INSERT INTO project_types (id, code, name, builtin, enabled, sort_order, created_at, updated_at)
SELECT UUID(), 'TRAINING', '安全培训', 1, 1, 50, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM project_types WHERE code = 'TRAINING');

-- ---------------------------------------------------------------- projects.type_code
-- 新列显式 collation：否则在 general_ci 库上会继承默认值，与 projects.type（unicode_ci）不一致
ALTER TABLE projects ADD COLUMN IF NOT EXISTS type_code VARCHAR(32)
    COLLATE utf8mb4_unicode_ci NULL;
CREATE INDEX IF NOT EXISTS idx_projects_type_code ON projects (type_code);

-- 历史数据回填：projects.type 存的就是编码（PENTEST/CODE_AUDIT/…），按 code 精确匹配
-- 两侧显式 COLLATE：JOIN 是「列 = 列」比较，两侧 collation 不一致会抛 1267
UPDATE projects p
  JOIN project_types t ON t.code COLLATE utf8mb4_unicode_ci = p.type COLLATE utf8mb4_unicode_ci
   SET p.type_code = t.code
 WHERE p.type_code IS NULL;

-- ---------------------------------------------------------------- 校验
SHOW TABLES LIKE 'project_types';
SELECT COUNT(*) AS builtin_project_types FROM project_types WHERE builtin = 1;
SELECT COUNT(*) AS custom_project_types FROM project_types WHERE builtin = 0;
SELECT COUNT(*) AS projects_with_type_code FROM projects WHERE type_code IS NOT NULL;
SELECT COUNT(*) AS industry_with_deleted_at FROM industry_types WHERE deleted_at IS NOT NULL;

-- 内置清单（应恰好 5 行）
SELECT code, name, sort_order FROM project_types WHERE builtin = 1 ORDER BY sort_order;


-- =============================================================================
-- V29：客户 / 项目 / 资产软删除（deleted_at + deleted_by + 索引，t140）
-- 与 backend/src/main/resources/db/migration/V29__soft_delete_customers_projects_assets.sql 等价
-- =============================================================================
-- 幂等：ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- ⚠ 排序规则（t134 硬规则）：新增的 deleted_by 显式 COLLATE utf8mb4_unicode_ci。
-- 唯一键语义：软删行仍占用 customers.code / projects.code / assets.code，唯一索引不变；
--            服务层查重改为「含已删」（countByCodeIncludingDeleted）。

ALTER TABLE customers ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(36)
    COLLATE utf8mb4_unicode_ci NULL;
CREATE INDEX IF NOT EXISTS idx_customers_deleted ON customers (deleted_at);

ALTER TABLE projects ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(36)
    COLLATE utf8mb4_unicode_ci NULL;
CREATE INDEX IF NOT EXISTS idx_projects_deleted ON projects (deleted_at);

ALTER TABLE assets ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(36)
    COLLATE utf8mb4_unicode_ci NULL;
CREATE INDEX IF NOT EXISTS idx_assets_deleted ON assets (deleted_at);

-- 校验：三表各两列（6 行）、三个索引（3 行）、唯一索引仍在、软删水位（部署后应为 0/0/0）
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('customers', 'projects', 'assets')
  AND COLUMN_NAME IN ('deleted_at', 'deleted_by')
ORDER BY TABLE_NAME, COLUMN_NAME;

SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_customers_deleted', 'idx_projects_deleted', 'idx_assets_deleted')
ORDER BY TABLE_NAME;

SELECT
  (SELECT COUNT(*) FROM customers WHERE deleted_at IS NOT NULL) AS deleted_customers,
  (SELECT COUNT(*) FROM projects  WHERE deleted_at IS NOT NULL) AS deleted_projects,
  (SELECT COUNT(*) FROM assets    WHERE deleted_at IS NOT NULL) AS deleted_assets;


-- =============================================================================
-- V30：资产分组 → 标签体系（t141）
-- 与 backend/src/main/resources/db/migration/V30__asset_groups_to_tags.sql 等价
-- =============================================================================
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / 迁移语句全部 NOT EXISTS 守卫。
-- ⚠ 排序规则（t134 硬规则）：新表显式 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci。
-- 旧数据保留：asset_groups / asset_group_relations 不删，只给 asset_groups 补 migrated_at（迁移水位）。

CREATE TABLE IF NOT EXISTS tag_definitions (
    id          VARCHAR(36) NOT NULL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    color       VARCHAR(20) NULL,
    builtin     BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at  DATETIME(6) NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX IF NOT EXISTS uk_tag_definitions_name ON tag_definitions (name);
CREATE INDEX IF NOT EXISTS idx_tag_definitions_deleted ON tag_definitions (deleted_at);

INSERT INTO tag_definitions (id, name, color, builtin, deleted_at, sort_order, created_at, updated_at)
SELECT UUID(), '核心系统', '#f5222d', 1, NULL, 10, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tag_definitions WHERE name = '核心系统');
INSERT INTO tag_definitions (id, name, color, builtin, deleted_at, sort_order, created_at, updated_at)
SELECT UUID(), '对外服务', '#1677ff', 1, NULL, 20, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tag_definitions WHERE name = '对外服务');
INSERT INTO tag_definitions (id, name, color, builtin, deleted_at, sort_order, created_at, updated_at)
SELECT UUID(), '测试环境', '#52c41a', 1, NULL, 30, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tag_definitions WHERE name = '测试环境');

ALTER TABLE asset_groups ADD COLUMN IF NOT EXISTS migrated_at DATETIME(6) NULL;
ALTER TABLE asset_groups ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
CREATE INDEX IF NOT EXISTS idx_asset_groups_deleted ON asset_groups (deleted_at);

-- 未软删分组 → 标签定义（name = 分组名，builtin = 0）
INSERT INTO tag_definitions (id, name, color, builtin, deleted_at, sort_order, created_at, updated_at)
SELECT UUID(), g.name, NULL, 0, NULL, 100, NOW(6), NOW(6)
  FROM asset_groups g
 WHERE g.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM tag_definitions t
   WHERE t.name COLLATE utf8mb4_unicode_ci = g.name COLLATE utf8mb4_unicode_ci);

-- 分组关系 → 资产标签（asset_tags 沿用既有表；同资产同标签只保留一行）
INSERT INTO asset_tags (id, asset_id, tag, created_by, created_at)
SELECT UUID(), r.asset_id, g.name, '00000000-0000-0000-0000-000000000000', NOW(6)
  FROM asset_group_relations r
  JOIN asset_groups g ON g.id = r.group_id
 WHERE g.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM asset_tags t
                    WHERE t.asset_id = r.asset_id
     AND t.tag COLLATE utf8mb4_unicode_ci = g.name COLLATE utf8mb4_unicode_ci);

UPDATE asset_groups SET migrated_at = NOW(6)
 WHERE deleted_at IS NULL AND migrated_at IS NULL;

-- 校验：标签总数 / 内置数 / 已迁移分组 / 待迁移 / 标签资产数分布
SELECT COUNT(*) AS tag_definitions_total FROM tag_definitions;
SELECT COUNT(*) AS builtin_tags FROM tag_definitions WHERE builtin = 1;
SELECT COUNT(*) AS migrated_groups FROM asset_groups WHERE migrated_at IS NOT NULL;
SELECT COUNT(*) AS groups_pending_migration FROM asset_groups WHERE migrated_at IS NULL AND deleted_at IS NULL;
SELECT t.name, COUNT(at.id) AS asset_count
  FROM tag_definitions t LEFT JOIN asset_tags at ON at.tag = t.name
 GROUP BY t.name ORDER BY asset_count DESC, t.name;





-- =============================================================================
-- V31：资产文件 / 交付物补齐元数据（t148）
-- 与 backend/src/main/resources/db/migration/V31__asset_file_metadata.sql 等价
-- =============================================================================
-- 幂等：ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- 说明：uploaded_by 两表本来就有（NOT NULL，上传时写当前用户），本轮不新增也不回填；
--       本轮只加 remark（上传备注，与 description 分离）+ 列表排序索引。

ALTER TABLE asset_files ADD COLUMN IF NOT EXISTS remark VARCHAR(500) NULL;
ALTER TABLE asset_deliverables ADD COLUMN IF NOT EXISTS remark VARCHAR(500) NULL;

CREATE INDEX IF NOT EXISTS idx_asset_files_asset_created ON asset_files (asset_id, created_at);
CREATE INDEX IF NOT EXISTS idx_asset_deliverables_asset_created ON asset_deliverables (asset_id, created_at);

-- 校验：remark 覆盖率 + uploaded_by 覆盖情况（历史数据是否都带上上传人）
SELECT COUNT(*) AS files_total,
       SUM(CASE WHEN uploaded_by IS NULL THEN 1 ELSE 0 END) AS files_without_uploader,
       SUM(CASE WHEN remark IS NULL THEN 1 ELSE 0 END) AS files_without_remark
  FROM asset_files;
SELECT COUNT(*) AS deliverables_total,
       SUM(CASE WHEN uploaded_by IS NULL THEN 1 ELSE 0 END) AS deliverables_without_uploader,
       SUM(CASE WHEN remark IS NULL THEN 1 ELSE 0 END) AS deliverables_without_remark
  FROM asset_deliverables;

-- =============================================================================
-- V32：大文件上传需管理员审批（t160）
-- 与 backend/src/main/resources/db/migration/V32__upload_approval_requests.sql 等价
-- =============================================================================
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- ⚠ 排序规则（t134 硬规则）：新表显式 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci。
-- 语义：PENDING/REJECTED 期间文件只在临时区，业务表无记录 → 不进任何列表/下载/统计；
--       拒绝立即删文件；过期（purge_after，默认 +7 天）由定时任务标 EXPIRED 并删文件。

CREATE TABLE IF NOT EXISTS upload_approval_requests (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    target_type   VARCHAR(20)  NOT NULL,
    target_id     VARCHAR(36)  NULL,
    file_name     VARCHAR(300) NOT NULL,
    file_size     BIGINT       NULL,
    content_type  VARCHAR(200) NULL,
    storage_key   VARCHAR(1000) NULL,
    status        VARCHAR(20)  NOT NULL,
    requested_by  VARCHAR(36)  NULL,
    requested_at  DATETIME(6)  NULL,
    reviewed_by   VARCHAR(36)  NULL,
    reviewed_at   DATETIME(6)  NULL,
    review_note   TEXT         NULL,
    purge_after   DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_upload_approval_status ON upload_approval_requests (status);
CREATE INDEX IF NOT EXISTS idx_upload_approval_requester ON upload_approval_requests (requested_by);
CREATE INDEX IF NOT EXISTS idx_upload_approval_purge ON upload_approval_requests (status, purge_after);

-- 校验：总量 / 待办 / 已批准 / 已关闭 / 待办却缺 storage_key（异常项应为 0）
SELECT COUNT(*) AS total_requests,
       SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_requests,
       SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_requests,
       SUM(CASE WHEN status IN ('REJECTED','EXPIRED') THEN 1 ELSE 0 END) AS closed_requests
  FROM upload_approval_requests;
SELECT COUNT(*) AS pending_without_storage FROM upload_approval_requests
 WHERE status = 'PENDING' AND storage_key IS NULL;

-- =============================================================================
-- V33：知识库 / 文档附件（t162）
-- 与 backend/src/main/resources/db/migration/V33__document_attachments.sql 等价
-- =============================================================================
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- ⚠ 排序规则（t134 硬规则）：新表显式 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci。
-- 语义：附件独立成行 + 文件落盘（UUID 前缀）；**没有按 storage_path 直接下载的端点**，
--       下载必须经 /{knowledge|documents}/{id}/attachments/{attachmentId}/download 并做归属校验。

CREATE TABLE IF NOT EXISTS document_attachments (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    owner_type    VARCHAR(20)   NOT NULL,
    owner_id      VARCHAR(36)   NOT NULL,
    file_name     VARCHAR(300)  NOT NULL,
    stored_name   VARCHAR(400)  NOT NULL,
    content_type  VARCHAR(200)  NULL,
    file_size     BIGINT        NULL,
    storage_path  VARCHAR(1000) NOT NULL,
    uploaded_by   VARCHAR(36)   NULL,
    uploaded_at   DATETIME(6)   NULL,
    deleted_at    DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_document_attachments_owner ON document_attachments (owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_document_attachments_deleted ON document_attachments (deleted_at);

-- 校验：总量 / 活动与软删 / 按归属类型分布
SELECT COUNT(*) AS attachments_total,
       SUM(CASE WHEN deleted_at IS NULL THEN 1 ELSE 0 END) AS active_attachments,
       SUM(CASE WHEN deleted_at IS NOT NULL THEN 1 ELSE 0 END) AS deleted_attachments
  FROM document_attachments;
SELECT owner_type, COUNT(*) AS cnt FROM document_attachments GROUP BY owner_type;

-- =============================================================================
-- V34：PENDING 上传保留 display_name / description（t165）
-- 与 backend/src/main/resources/db/migration/V34__upload_approval_display_name.sql 等价
-- =============================================================================
-- 幂等：ADD COLUMN IF NOT EXISTS。⚠ 列显式 COLLATE utf8mb4_unicode_ci（t134 硬规则）。

ALTER TABLE upload_approval_requests
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(200) COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE upload_approval_requests
    ADD COLUMN IF NOT EXISTS description VARCHAR(1000) COLLATE utf8mb4_unicode_ci NULL;

-- 校验：两列覆盖率（历史数据为空属预期）+ 已批准且带显示名的条数
SELECT COUNT(*) AS total_requests,
       SUM(CASE WHEN display_name IS NULL THEN 1 ELSE 0 END) AS without_display_name,
       SUM(CASE WHEN description IS NULL THEN 1 ELSE 0 END) AS without_description
  FROM upload_approval_requests;
SELECT COUNT(*) AS approved_with_display_name FROM upload_approval_requests
 WHERE status = 'APPROVED' AND display_name IS NOT NULL;
