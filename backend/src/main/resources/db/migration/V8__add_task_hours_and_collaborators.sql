ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS estimated_hours DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS actual_hours DECIMAL(10,2) NOT NULL DEFAULT 0;

ALTER TABLE task_comments
    ADD COLUMN IF NOT EXISTS spent_hours DECIMAL(10,2);

CREATE TABLE IF NOT EXISTS task_collaborators (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    user_id UUID NOT NULL,
    added_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_collaborators_task_user
    ON task_collaborators (task_id, user_id);

CREATE INDEX IF NOT EXISTS idx_task_collaborators_task_id
    ON task_collaborators (task_id);
