-- =============================================================================
-- V31：资产文件 / 交付物补齐元数据（t148）
-- =============================================================================
-- 幂等：ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- ⚠ 排序规则（t134 硬规则）：本迁移不新建表，新列沿用表的 collation（utf8mb4_unicode_ci）。
--
-- 口径说明（重要）：
--   `uploaded_by` 在 asset_files / asset_deliverables 上**本来就存在**（NOT NULL，上传时已写当前用户），
--   本轮**不再新增该列**，也不存在「从 created_by 回填」——两表没有 created_by 列。
--   真正缺的是：
--     ① `remark`（上传备注，VARCHAR(500)）——此前前端只能把备注拼进 description；
--     ② 列表接口的分页/筛选/排序（后端改动，无 DDL）；
--     ③ 列表响应里的上传人展示名（后端改动，无 DDL）。
--   因此本脚本只加 remark + 排序索引，并输出 uploaded_by 的覆盖率校验。

ALTER TABLE asset_files ADD COLUMN IF NOT EXISTS remark VARCHAR(500) NULL;
ALTER TABLE asset_deliverables ADD COLUMN IF NOT EXISTS remark VARCHAR(500) NULL;

-- 排序索引（列表默认按 created_at desc，筛选条件是 asset_id）
CREATE INDEX IF NOT EXISTS idx_asset_files_asset_created ON asset_files (asset_id, created_at);
CREATE INDEX IF NOT EXISTS idx_asset_deliverables_asset_created ON asset_deliverables (asset_id, created_at);

-- 校验：remark 列已存在；uploaded_by 覆盖情况；索引
SELECT COUNT(*) AS files_total,
       SUM(CASE WHEN uploaded_by IS NULL THEN 1 ELSE 0 END) AS files_without_uploader,
       SUM(CASE WHEN remark IS NULL THEN 1 ELSE 0 END) AS files_without_remark
  FROM asset_files;
SELECT COUNT(*) AS deliverables_total,
       SUM(CASE WHEN uploaded_by IS NULL THEN 1 ELSE 0 END) AS deliverables_without_uploader,
       SUM(CASE WHEN remark IS NULL THEN 1 ELSE 0 END) AS deliverables_without_remark
  FROM asset_deliverables;
