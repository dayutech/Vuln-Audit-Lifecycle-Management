-- =============================================================================
-- V35：自定义填表系统 · 数据模型（第 1/5 块，t178）
-- =============================================================================
-- 四张表一次建好：form_definition / form_field / form_submission / form_value
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- ⚠ 排序规则（t134 硬规则）：全部显式 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci。
--
-- 三层值语义（关键设计，后续块依赖）：
--   source_value   生成实例时从业务库取的**快照**，生成后不可变（业务改名/改状态都不影响历史表单）
--   filled_value   填表人手工填写的值
--   override_value 修改覆盖，**只影响导出**，不回写业务库
-- 为什么修改不回写业务库：填表是「某一时点的记录/申报」，回写会让历史失真且与审计口径冲突。

CREATE TABLE IF NOT EXISTS form_definition (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    code          VARCHAR(50)   NOT NULL,
    name          VARCHAR(200)  NOT NULL,
    description   VARCHAR(1000) NULL,
    enabled       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by    VARCHAR(36)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    deleted_at    DATETIME(6)   NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- code 唯一（含软删行：模板编码不复用，避免历史表单指向混乱）
CREATE UNIQUE INDEX IF NOT EXISTS uk_form_definition_code ON form_definition (code);
CREATE INDEX IF NOT EXISTS idx_form_definition_deleted ON form_definition (deleted_at);

CREATE TABLE IF NOT EXISTS form_field (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    definition_id VARCHAR(36)   NOT NULL,
    field_key     VARCHAR(64)   NOT NULL,
    label         VARCHAR(200)  NOT NULL,
    field_type    VARCHAR(20)   NOT NULL,
    required      BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order    INT           NOT NULL DEFAULT 0,
    source_type   VARCHAR(64)   NULL,
    options       VARCHAR(2000) NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    deleted_at    DATETIME(6)   NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX IF NOT EXISTS uk_form_field_key ON form_field (definition_id, field_key);
CREATE INDEX IF NOT EXISTS idx_form_field_definition ON form_field (definition_id, sort_order);

CREATE TABLE IF NOT EXISTS form_submission (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    definition_id VARCHAR(36)   NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    created_by    VARCHAR(36)   NULL,
    assigned_to   VARCHAR(36)   NULL,
    confirmed_by  VARCHAR(36)   NULL,
    assigned_at   DATETIME(6)   NULL,
    submitted_at  DATETIME(6)   NULL,
    confirmed_at  DATETIME(6)   NULL,
    note          VARCHAR(2000) NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_form_submission_status_assignee ON form_submission (status, assigned_to);
CREATE INDEX IF NOT EXISTS idx_form_submission_creator ON form_submission (created_by);
CREATE INDEX IF NOT EXISTS idx_form_submission_definition ON form_submission (definition_id);

CREATE TABLE IF NOT EXISTS form_value (
    id             VARCHAR(36) NOT NULL PRIMARY KEY,
    submission_id  VARCHAR(36) NOT NULL,
    field_id       VARCHAR(36) NOT NULL,
    source_value   TEXT        NULL,
    filled_value   TEXT        NULL,
    override_value TEXT        NULL,
    assigned_to    VARCHAR(36) NULL,
    updated_by     VARCHAR(36) NULL,
    updated_at     DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX IF NOT EXISTS uk_form_value_submission_field ON form_value (submission_id, field_id);
CREATE INDEX IF NOT EXISTS idx_form_value_field ON form_value (field_id);

-- 校验：四表存在 + 关键唯一索引存在 + 数据量
SELECT COUNT(*) AS definitions FROM form_definition;
SELECT COUNT(*) AS fields FROM form_field;
SELECT COUNT(*) AS submissions FROM form_submission;
SELECT COUNT(*) AS values_cnt FROM form_value;
