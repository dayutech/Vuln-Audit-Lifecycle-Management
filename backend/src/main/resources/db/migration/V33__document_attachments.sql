-- =============================================================================
-- V33：知识库 / 文档附件（t162）
-- =============================================================================
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- ⚠ 排序规则（t134 硬规则）：新表显式 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci。
--
-- 背景：此前知识库/文档没有附件模型，前端只能用「正文内 base64 附件块」，受 content TEXT(64KB) 限制
-- （二进制实际只能 ≤40KB）。本表把附件独立成行，文件落磁盘（UUID 前缀），**不提供按路径直接下载**，
-- 下载必须经 /{knowledge|documents}/{id}/attachments/{attachmentId}/download 并做归属校验。

CREATE TABLE IF NOT EXISTS document_attachments (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    owner_type    VARCHAR(20)   NOT NULL,              -- KNOWLEDGE | DOCUMENT
    owner_id      VARCHAR(36)   NOT NULL,
    file_name     VARCHAR(300)  NOT NULL,              -- 净化后的原文件名（下载用，支持中文）
    stored_name   VARCHAR(400)  NOT NULL,              -- 磁盘名（uuid + 原文件名）
    content_type  VARCHAR(200)  NULL,
    file_size     BIGINT        NULL,
    storage_path  VARCHAR(1000) NOT NULL,              -- 相对路径
    uploaded_by   VARCHAR(36)   NULL,
    uploaded_at   DATETIME(6)   NULL,
    deleted_at    DATETIME(6)   NULL,                  -- 软删：列表不含、下载 404
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_document_attachments_owner ON document_attachments (owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_document_attachments_deleted ON document_attachments (deleted_at);

-- 校验：总量 / 按归属分布 / 是否有软删行
SELECT COUNT(*) AS attachments_total,
       SUM(CASE WHEN deleted_at IS NULL THEN 1 ELSE 0 END) AS active_attachments,
       SUM(CASE WHEN deleted_at IS NOT NULL THEN 1 ELSE 0 END) AS deleted_attachments
  FROM document_attachments;
SELECT owner_type, COUNT(*) AS cnt FROM document_attachments GROUP BY owner_type;
