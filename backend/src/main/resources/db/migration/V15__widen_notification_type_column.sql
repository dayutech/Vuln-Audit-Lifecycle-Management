-- notifications.type 扩列：默认值 vulnerability_discovered 为 23 字符，
-- 旧列宽 VARCHAR(20) 会导致 POST /api/v1/notifications/vulnerability-discovered
-- 在落库时抛 DataIntegrityViolationException（500）。这里扩到 50 与实体保持一致。
ALTER TABLE notifications MODIFY COLUMN type VARCHAR(50) NOT NULL;
