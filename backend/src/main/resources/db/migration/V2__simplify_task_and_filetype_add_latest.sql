ALTER TABLE tasks
    DROP COLUMN IF EXISTS type,
    DROP COLUMN IF EXISTS priority;

ALTER TABLE asset_files
    DROP COLUMN IF EXISTS file_type;

ALTER TABLE asset_deliverables
    DROP COLUMN IF EXISTS file_type;

ALTER TABLE asset_versions
    ADD COLUMN IF NOT EXISTS is_latest BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE asset_versions av
JOIN (
    SELECT asset_id, MAX(version_no) AS max_version_no
    FROM asset_versions
    GROUP BY asset_id
) latest ON latest.asset_id = av.asset_id AND latest.max_version_no = av.version_no
SET av.is_latest = TRUE;
