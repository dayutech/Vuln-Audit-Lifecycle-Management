-- =============================================================================
-- V32：大文件上传需管理员审批（t160）
-- =============================================================================
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- ⚠ 排序规则（t134 硬规则）：新表显式 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci。
--
-- 背景：工具/组件库的大文件上传必须管理员批准；PENDING/REJECTED 期间文件只存在于**临时区**，
-- 不写任何业务表、不在任何列表/下载/统计中可见；拒绝立即删临时文件，过期（purge_after）由定时任务清理。

CREATE TABLE IF NOT EXISTS upload_approval_requests (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    target_type   VARCHAR(20)  NOT NULL,              -- TOOL | COMPONENT | KNOWLEDGE | DOCUMENT
    target_id     VARCHAR(36)  NULL,                  -- 批准后写入（新建时为空）
    file_name     VARCHAR(300) NOT NULL,              -- 净化后的原文件名
    file_size     BIGINT       NULL,
    content_type  VARCHAR(200) NULL,
    storage_key   VARCHAR(1000) NULL,                 -- 临时区相对路径
    status        VARCHAR(20)  NOT NULL,              -- PENDING | APPROVED | REJECTED | EXPIRED
    requested_by  VARCHAR(36)  NULL,
    requested_at  DATETIME(6)  NULL,
    reviewed_by   VARCHAR(36)  NULL,
    reviewed_at   DATETIME(6)  NULL,
    review_note   TEXT         NULL,
    purge_after   DATETIME(6)  NULL,                  -- 过期清理水位（默认 +7 天）
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_upload_approval_status ON upload_approval_requests (status);
CREATE INDEX IF NOT EXISTS idx_upload_approval_requester ON upload_approval_requests (requested_by);
CREATE INDEX IF NOT EXISTS idx_upload_approval_purge ON upload_approval_requests (status, purge_after);

-- 校验：表结构 + 待办数量 + 临时区遗留
SELECT COUNT(*) AS total_requests,
       SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_requests,
       SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_requests,
       SUM(CASE WHEN status IN ('REJECTED','EXPIRED') THEN 1 ELSE 0 END) AS closed_requests
  FROM upload_approval_requests;
SELECT COUNT(*) AS pending_without_storage FROM upload_approval_requests
 WHERE status = 'PENDING' AND storage_key IS NULL;
