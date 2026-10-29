-- 工具版本：补充版本序号与文件元数据，并加 (tool_id, version_no) 唯一约束防并发重复版本号
ALTER TABLE tool_versions
    ADD COLUMN IF NOT EXISTS version_no INT NULL,
    ADD COLUMN IF NOT EXISTS file_name VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS file_size BIGINT NULL;

-- 历史数据回填：按 created_at 排序生成 1..n
UPDATE tool_versions tv
    JOIN (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY tool_id ORDER BY created_at, id) AS rn
        FROM tool_versions
    ) numbered ON numbered.id = tv.id
SET tv.version_no = numbered.rn
WHERE tv.version_no IS NULL;

ALTER TABLE tool_versions MODIFY COLUMN version_no INT NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_tool_versions_tool_version_no
    ON tool_versions (tool_id, version_no);
