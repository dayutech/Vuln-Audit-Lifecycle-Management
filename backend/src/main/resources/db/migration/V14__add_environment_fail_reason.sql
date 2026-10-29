-- 环境部署失败原因（容器编排不可用 / docker 命令失败等）
ALTER TABLE environments
    ADD COLUMN IF NOT EXISTS fail_reason VARCHAR(500) NULL;

-- 修复历史脏数据：早期 createSnapshot 用 UUID.randomUUID() 冒充操作者，
-- 这里把「在 users 表中不存在」的快照创建人回退为环境创建人。
UPDATE environment_snapshots s
    JOIN environments e ON e.id = s.environment_id
SET s.created_by = e.created_by
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = s.created_by);
