-- =============================================================================
-- V27：客户编码自动生成 + 国标行业枚举（t129）
-- =============================================================================
-- 幂等：MariaDB 10.x 支持 ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS。
-- 内置行业由应用启动器（IndustryTypeBootstrapRunner）在「表为空」时插入；
-- 本脚本负责表结构与历史数据回填（回填同样幂等）。

CREATE TABLE IF NOT EXISTS industry_types (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    code        VARCHAR(16)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    parent_code VARCHAR(16)  NULL,
    builtin     BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order  INT          NOT NULL DEFAULT 0,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_industry_types_code ON industry_types (code);
CREATE INDEX IF NOT EXISTS idx_industry_types_name ON industry_types (name);

-- 客户表：行业显示名保留，新增行业编码
ALTER TABLE customers ADD COLUMN IF NOT EXISTS industry_code VARCHAR(16) NULL;

-- 历史数据回填：按名称精确匹配内置/已存在行业（匹配不到保持 industry_code = NULL，不阻塞）
UPDATE customers c
  JOIN industry_types t ON t.name = c.industry
   SET c.industry_code = t.code
 WHERE c.industry_code IS NULL
   AND c.industry IS NOT NULL;

-- 校验
SHOW TABLES LIKE 'industry_types';
SELECT COUNT(*) AS builtin_industries FROM industry_types WHERE builtin = 1;
SELECT COUNT(*) AS customers_with_industry_code FROM customers WHERE industry_code IS NOT NULL;
