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
