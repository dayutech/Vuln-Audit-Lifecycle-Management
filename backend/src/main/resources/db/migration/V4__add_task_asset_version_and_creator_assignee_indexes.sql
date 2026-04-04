ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS asset_version_id UUID NULL;

UPDATE tasks
SET reference_id = asset_id
WHERE reference_id IS NULL
  AND asset_id IS NOT NULL;

CREATE INDEX idx_tasks_asset_id_version ON tasks (asset_id, asset_version_id);
CREATE INDEX idx_tasks_assigned_by ON tasks (assigned_by);
