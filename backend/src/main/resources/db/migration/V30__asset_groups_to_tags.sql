-- =============================================================================
-- V30：资产分组 → 标签体系（t141）
-- =============================================================================
-- 幂等：ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS；
--       数据迁移全部用 NOT EXISTS 守卫，重复执行只补缺、不重复插入。
--
-- ⚠ 排序规则（t134 硬规则）：新表显式 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci。
--
-- 迁移口径：
--   ① 新建 tag_definitions（标签目录：name 唯一、builtin、color、deleted_at、sort_order）；
--   ② asset_groups 补 migrated_at（迁移水位）+ deleted_at（软删分组不迁移）；
--   ③ 每个**未软删**分组 → 一条标签定义（name=分组名）；
--   ④ asset_group_relations → asset_tags(asset_id, tag=分组名)，保持资产与标签的对应；
--   ⑤ 旧数据保留（不删 asset_groups / asset_group_relations），便于回溯。

-- ---------------------------------------------------------------- ① 标签定义表
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
-- name 唯一（含软删行）：软删后同名不可复用，避免历史标签语义漂移
CREATE UNIQUE INDEX IF NOT EXISTS uk_tag_definitions_name ON tag_definitions (name);
CREATE INDEX IF NOT EXISTS idx_tag_definitions_deleted ON tag_definitions (deleted_at);

-- 内置标签（3 条，幂等）
INSERT INTO tag_definitions (id, name, color, builtin, deleted_at, sort_order, created_at, updated_at)
SELECT UUID(), '核心系统', '#f5222d', 1, NULL, 10, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tag_definitions WHERE name = '核心系统');

INSERT INTO tag_definitions (id, name, color, builtin, deleted_at, sort_order, created_at, updated_at)
SELECT UUID(), '对外服务', '#1677ff', 1, NULL, 20, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tag_definitions WHERE name = '对外服务');

INSERT INTO tag_definitions (id, name, color, builtin, deleted_at, sort_order, created_at, updated_at)
SELECT UUID(), '测试环境', '#52c41a', 1, NULL, 30, NOW(6), NOW(6) FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tag_definitions WHERE name = '测试环境');

-- ---------------------------------------------------------------- ② 分组表补齐迁移水位 + 软删列
ALTER TABLE asset_groups ADD COLUMN IF NOT EXISTS migrated_at DATETIME(6) NULL;
ALTER TABLE asset_groups ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
CREATE INDEX IF NOT EXISTS idx_asset_groups_deleted ON asset_groups (deleted_at);

-- ---------------------------------------------------------------- ③ 分组 → 标签定义
-- ⚠ t147（沿用 t134 硬规则）：下面两处「标签名 = 分组名」是**列 = 列**比较，两侧必须显式 COLLATE。
-- 新表 tag_definitions.name 是 unicode_ci，而 asset_groups.name / asset_tags.tag 会继承**库默认**；
-- 在默认 general_ci 的实例上不做 COLLATE 会直接 ERROR 1267（t147 在空库 general_ci 场景实测复现）。
INSERT INTO tag_definitions (id, name, color, builtin, deleted_at, sort_order, created_at, updated_at)
SELECT UUID(), g.name, NULL, 0, NULL, 100, NOW(6), NOW(6)
  FROM asset_groups g
 WHERE g.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM tag_definitions t
                     WHERE t.name COLLATE utf8mb4_unicode_ci = g.name COLLATE utf8mb4_unicode_ci);

-- ---------------------------------------------------------------- ④ 分组关系 → 资产标签（asset_tags 沿用既有表）
INSERT INTO asset_tags (id, asset_id, tag, created_by, created_at)
SELECT UUID(), r.asset_id, g.name, '00000000-0000-0000-0000-000000000000', NOW(6)
  FROM asset_group_relations r
  JOIN asset_groups g ON g.id = r.group_id
 WHERE g.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM asset_tags t
                    WHERE t.asset_id = r.asset_id
                      AND t.tag COLLATE utf8mb4_unicode_ci = g.name COLLATE utf8mb4_unicode_ci);

-- ---------------------------------------------------------------- ⑤ 迁移水位（只标记未软删分组）
UPDATE asset_groups SET migrated_at = NOW(6)
 WHERE deleted_at IS NULL AND migrated_at IS NULL;

-- ---------------------------------------------------------------- 校验
SELECT COUNT(*) AS tag_definitions_total FROM tag_definitions;
SELECT COUNT(*) AS builtin_tags FROM tag_definitions WHERE builtin = 1;
SELECT COUNT(*) AS migrated_groups FROM asset_groups WHERE migrated_at IS NOT NULL;
SELECT COUNT(*) AS groups_pending_migration FROM asset_groups WHERE migrated_at IS NULL AND deleted_at IS NULL;
SELECT COUNT(*) AS asset_tags_total FROM asset_tags;
SELECT t.name, COUNT(at.id) AS asset_count
  FROM tag_definitions t LEFT JOIN asset_tags at
         ON at.tag COLLATE utf8mb4_unicode_ci = t.name COLLATE utf8mb4_unicode_ci
 GROUP BY t.name ORDER BY asset_count DESC, t.name;
