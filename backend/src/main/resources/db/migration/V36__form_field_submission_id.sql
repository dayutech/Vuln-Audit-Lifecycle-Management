-- =============================================================================
-- V36：实例级字段 + 实例的业务主体（第 2/5 块，t179）
-- =============================================================================
-- 1) form_field.submission_id（task 推荐方案 ①）：
--      submission_id IS NULL  → 模板字段（所有实例共用）
--      submission_id 非空     → 实例级字段（发起人在某个表单里临时加的）
-- 2) form_submission.subject_type / subject_id：
--      V35 建表时没有落「业务主体」，但生成快照与详情/列表都必须知道自己挂的是谁
--      （客户/项目/资产的名字不能靠标题反解）。一并在此补上，避免再开一个迁移。
--
-- 幂等：ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- ⚠ 新列显式 COLLATE utf8mb4_unicode_ci（t134 硬规则）。

ALTER TABLE form_field
    ADD COLUMN IF NOT EXISTS submission_id VARCHAR(36) COLLATE utf8mb4_unicode_ci NULL;

CREATE INDEX IF NOT EXISTS idx_form_field_submission ON form_field (submission_id, sort_order);

ALTER TABLE form_submission
    ADD COLUMN IF NOT EXISTS subject_type VARCHAR(20) COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE form_submission
    ADD COLUMN IF NOT EXISTS subject_id VARCHAR(36) COLLATE utf8mb4_unicode_ci NULL;

CREATE INDEX IF NOT EXISTS idx_form_submission_subject ON form_submission (subject_type, subject_id);

-- 校验：模板字段 vs 实例级字段；已落主体的实例数
SELECT SUM(CASE WHEN submission_id IS NULL THEN 1 ELSE 0 END) AS template_fields,
       SUM(CASE WHEN submission_id IS NOT NULL THEN 1 ELSE 0 END) AS instance_fields
  FROM form_field;
SELECT COUNT(*) AS submissions_with_subject FROM form_submission WHERE subject_id IS NOT NULL;
