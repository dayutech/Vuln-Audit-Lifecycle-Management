-- =============================================================================
-- V34：PENDING 上传保留用户填写的显示名与描述（t165）
-- =============================================================================
-- 背景（t163 真机验收）：V32 的 upload_approval_requests 没有 display_name/description，
-- 用户填的工具名/描述在「走审批」这条路径上丢失 → 批准后工具名回退成「文件名去扩展名」。
--
-- 幂等：ADD COLUMN IF NOT EXISTS。
-- ⚠ 排序规则（t134 硬规则）：列显式 COLLATE utf8mb4_unicode_ci（与表默认一致，写出来防止继承歧义）。

ALTER TABLE upload_approval_requests
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(200) COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE upload_approval_requests
    ADD COLUMN IF NOT EXISTS description VARCHAR(1000) COLLATE utf8mb4_unicode_ci NULL;

-- 校验：两列已存在；历史 PENDING 记录的回填情况（老数据为空属预期）
SELECT COUNT(*) AS total_requests,
       SUM(CASE WHEN display_name IS NULL THEN 1 ELSE 0 END) AS without_display_name,
       SUM(CASE WHEN description IS NULL THEN 1 ELSE 0 END) AS without_description
  FROM upload_approval_requests;

SELECT COUNT(*) AS approved_with_display_name FROM upload_approval_requests
 WHERE status = 'APPROVED' AND display_name IS NOT NULL;
