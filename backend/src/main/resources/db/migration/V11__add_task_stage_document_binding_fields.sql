ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS env_collect_doc_id UUID,
    ADD COLUMN IF NOT EXISTS env_setup_doc_id UUID,
    ADD COLUMN IF NOT EXISTS code_audit_doc_id UUID;
