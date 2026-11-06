-- =============================================================================
-- V28：项目类型管理（内置不可改删）+ 项目编码自动生成（t130）
-- =============================================================================
-- 幂等：MariaDB 10.x 支持 ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS /
--       CREATE INDEX IF NOT EXISTS；内置项用 INSERT ... WHERE NOT EXISTS 保证只插一次。
--
-- 两套 DDL：
--   ① industry_types：补 deleted_at（软删，避免破坏历史客户引用）
--   ② project_types ：新表（内置 5 项 + 管理员自定义）
--   同时给 projects 补 type_code（引用 project_types.code，type 保留原字符串）
--
-- ⚠ 排序规则（t134）：新表与新列**必须显式声明** utf8mb4_unicode_ci，回填 JOIN 两侧显式 COLLATE。
-- 否则在「库默认 collation = utf8mb4_general_ci」的实例上，project_types.code 是 general_ci、
-- 既有 projects.type 是 unicode_ci，JOIN 比较会报：
--   ERROR 1267 (HY000): Illegal mix of collations (utf8mb4_general_ci, IMPLICIT)
--   and (utf8mb4_unicode_ci, IMPLICIT) for operation '='

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
