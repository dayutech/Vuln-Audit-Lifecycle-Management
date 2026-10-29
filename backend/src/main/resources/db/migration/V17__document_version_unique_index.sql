-- 文档版本唯一性收敛（t32）
-- 背景：document_versions 此前无唯一约束，并发保存同一文档时两个请求会基于同一个
-- documents.version 写出两条相同 version 的快照，导致历史排序/回滚/审计追溯失真。
--
-- 选型说明：MariaDB 10.0/10.1 不支持窗口函数（10.2+ 才支持），为兼容生产现状，
-- 这里用「自连接 + GROUP BY」计算排名（任何 MySQL/MariaDB 版本可用），
-- 并按 (created_at, id) 排序重排版本号；语句可重复执行（幂等）。

-- 1) 先重排存在重复版本号的文档（仅处理冲突文档，避免全表重写）
UPDATE document_versions dv
    JOIN (
        SELECT a.id AS id, COUNT(*) AS new_version
        FROM document_versions a
        JOIN document_versions b
          ON a.document_id = b.document_id
         AND (b.created_at < a.created_at
              OR (b.created_at = a.created_at AND b.id <= a.id))
        GROUP BY a.id
    ) ranked ON ranked.id = dv.id
SET dv.version = ranked.new_version
WHERE dv.document_id IN (
    SELECT document_id FROM (
        SELECT document_id
        FROM document_versions
        GROUP BY document_id, version
        HAVING COUNT(*) > 1
    ) duplicates
);

-- 2) 加唯一索引：同一文档内版本号唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_document_versions_document_version
    ON document_versions (document_id, version);
