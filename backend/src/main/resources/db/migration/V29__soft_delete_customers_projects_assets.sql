-- =============================================================================
-- V29：客户 / 项目 / 资产的软删除（deleted_at + deleted_by + 索引，t140）
-- =============================================================================
-- 幂等：MariaDB 10.x 支持 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- H2 测试走 ddl-auto=create-drop（实体带 @SQLRestriction），不执行本脚本。
--
-- ⚠ 排序规则（t134 硬规则）：新列显式 COLLATE utf8mb4_unicode_ci，不依赖库默认 —— 本脚本新增的
-- deleted_by 是 VARCHAR，若继承到与既有列不同的 collation，后续「列 = 列」比较会抛 1267。
--
-- 唯一键语义（重要）：软删记录**仍占用唯一键**（customers.code / projects.code / assets.code），
-- 因此唯一索引**不需要**变更；服务层查重已改为「含已删」（countByCodeIncludingDeleted）。

-- ---------------------------------------------------------------- ① customers
ALTER TABLE customers ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(36)
    COLLATE utf8mb4_unicode_ci NULL;
CREATE INDEX IF NOT EXISTS idx_customers_deleted ON customers (deleted_at);

-- ---------------------------------------------------------------- ② projects
ALTER TABLE projects ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(36)
    COLLATE utf8mb4_unicode_ci NULL;
CREATE INDEX IF NOT EXISTS idx_projects_deleted ON projects (deleted_at);

-- ---------------------------------------------------------------- ③ assets
ALTER TABLE assets ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(36)
    COLLATE utf8mb4_unicode_ci NULL;
CREATE INDEX IF NOT EXISTS idx_assets_deleted ON assets (deleted_at);

-- ---------------------------------------------------------------- 校验
-- 1) 三张表都有两列（应为 6 行）
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('customers', 'projects', 'assets')
  AND COLUMN_NAME IN ('deleted_at', 'deleted_by')
ORDER BY TABLE_NAME, COLUMN_NAME;

-- 2) 三个索引都在（应为 3 行）
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_customers_deleted', 'idx_projects_deleted', 'idx_assets_deleted')
ORDER BY TABLE_NAME;

-- 3) 唯一索引未受软删影响（仍存在；软删行继续占码）
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('uk_customers_code', 'uk_projects_code')
ORDER BY TABLE_NAME;

-- 4) 当前软删水位（部署后应为 0）
SELECT
  (SELECT COUNT(*) FROM customers WHERE deleted_at IS NOT NULL) AS deleted_customers,
  (SELECT COUNT(*) FROM projects  WHERE deleted_at IS NOT NULL) AS deleted_projects,
  (SELECT COUNT(*) FROM assets    WHERE deleted_at IS NOT NULL) AS deleted_assets;
