ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS task_id UUID;

CREATE INDEX IF NOT EXISTS idx_documents_task_id
    ON documents (task_id);
