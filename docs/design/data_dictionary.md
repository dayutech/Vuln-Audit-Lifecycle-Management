# 数据字典

## 1. 用户与权限相关表

### 1.1 用户表 (users)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| username | VARCHAR(50) | 是 | - | 用户名，唯一 |
| email | VARCHAR(255) | 是 | - | 邮箱，唯一 |
| password_hash | VARCHAR(255) | 是 | - | 密码哈希 |
| full_name | VARCHAR(100) | 是 | - | 真实姓名 |
| avatar | VARCHAR(500) | 否 | null | 头像URL |
| role | VARCHAR(20) | 是 | 'engineer' | 角色：admin/leader/engineer/auditor |
| status | VARCHAR(20) | 是 | 'active' | 状态：active/suspended/deactivated |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |
| last_login | TIMESTAMP | 否 | null | 最后登录时间 |

### 1.2 角色表 (roles)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(50) | 是 | - | 角色名称，唯一 |
| description | VARCHAR(255) | 否 | null | 角色描述 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 1.3 权限表 (permissions)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(100) | 是 | - | 权限名称 |
| resource | VARCHAR(50) | 是 | - | 资源类型：asset/vulnerability/environment等 |
| action | VARCHAR(50) | 是 | - | 操作类型：create/read/update/delete |
| description | VARCHAR(255) | 否 | null | 权限描述 |

### 1.4 用户角色关联表 (user_roles)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| user_id | UUID | 是 | - | 外键，关联users表 |
| role_id | UUID | 是 | - | 外键，关联roles表 |
| granted_at | TIMESTAMP | 是 | 当前时间 | 授予时间 |

### 1.5 角色权限关联表 (role_permissions)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| role_id | UUID | 是 | - | 外键，关联roles表 |
| permission_id | UUID | 是 | - | 外键，关联permissions表 |

### 1.6 团队表 (teams)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(100) | 是 | - | 团队名称 |
| description | TEXT | 否 | null | 团队描述 |
| leader_id | UUID | 是 | - | 外键，关联users表，团队负责人 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 1.7 团队成员关联表 (team_members)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| user_id | UUID | 是 | - | 外键，关联users表 |
| team_id | UUID | 是 | - | 外键，关联teams表 |
| role | VARCHAR(20) | 是 | 'member' | 成员角色：leader/member |
| status | VARCHAR(20) | 是 | 'available' | 状态：available/busy/offline |
| joined_at | TIMESTAMP | 是 | 当前时间 | 加入时间 |

---

## 2. 资产管理相关表

### 2.1 资产表 (assets)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(200) | 是 | - | 资产名称 |
| code | VARCHAR(50) | 是 | - | 资产编号，唯一 |
| team_id | UUID | 是 | - | 外键，关联teams表 |
| target_type | VARCHAR(100) | 是 | - | 目标类型：Web应用/系统/服务/IoT设备等 |
| target_source | VARCHAR(20) | 是 | - | 目标来源：客户委托/自主发现 |
| description | TEXT | 否 | null | 资产描述 |
| priority | VARCHAR(2) | 是 | 'P2' | 优先级：P0/P1/P2/P3 |
| status | VARCHAR(20) | 是 | 'active' | 状态：active/under_test/suspended/archived |
| vendor_notified | BOOLEAN | 是 | false | 是否已通知厂商 |
| vendor_fixed | BOOLEAN | 是 | false | 厂商是否已修复 |
| fixed_at | TIMESTAMP | 否 | null | 厂商修复时间 |
| owner_id | UUID | 是 | - | 外键，关联users表，资产负责人 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 2.2 资产分组表 (asset_groups)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(100) | 是 | - | 分组名称 |
| type | VARCHAR(20) | 是 | - | 分组类型：客户/项目/自建 |
| description | TEXT | 否 | null | 分组描述 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 2.3 资产分组关联表 (asset_group_relations)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| group_id | UUID | 是 | - | 外键，关联asset_groups表 |

### 2.4 资产历史表 (asset_history)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| field_name | VARCHAR(50) | 是 | - | 变更字段名 |
| old_value | TEXT | 否 | null | 变更前值 |
| new_value | TEXT | 否 | null | 变更后值 |
| changed_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 变更时间 |

---

## 3. 漏洞管理相关表

### 3.1 漏洞表 (vulnerabilities)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| title | VARCHAR(500) | 是 | - | 漏洞标题 |
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| cve_id | VARCHAR(20) | 否 | null | CVE编号 |
| type | VARCHAR(100) | 是 | - | 漏洞类型：SQL注入/XSS/RCE等 |
| severity | VARCHAR(10) | 是 | 'medium' | 严重程度：critical/high/medium/low |
| cvss_score | DECIMAL(3,1) | 否 | null | CVSS评分 |
| status | VARCHAR(20) | 是 | 'discovered' | 状态：discovered/verified/analyzing/analyzed/reported/closed/rejected |
| description | TEXT | 是 | - | 漏洞描述 |
| impact | TEXT | 否 | null | 影响分析 |
| poc_code | TEXT | 否 | null | POC代码 |
| discoverer_id | UUID | 是 | - | 外键，关联users表，发现者 |
| assigned_to | UUID | 否 | null | 外键，关联users表，分配给 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 3.2 漏洞历史表 (vulnerability_history)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| vulnerability_id | UUID | 是 | - | 外键，关联vulnerabilities表 |
| from_status | VARCHAR(20) | 是 | - | 变更前状态 |
| to_status | VARCHAR(20) | 是 | - | 变更后状态 |
| changed_by | UUID | 是 | - | 外键，关联users表 |
| comment | TEXT | 否 | null | 变更说明 |
| created_at | TIMESTAMP | 是 | 当前时间 | 变更时间 |

### 3.3 漏洞附件表 (vulnerability_attachments)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| vulnerability_id | UUID | 是 | - | 外键，关联vulnerabilities表 |
| file_name | VARCHAR(255) | 是 | - | 文件名 |
| file_path | VARCHAR(500) | 是 | - | 文件存储路径 |
| file_type | VARCHAR(50) | 否 | null | 文件类型 |
| file_size | BIGINT | 否 | null | 文件大小(字节) |
| uploaded_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 上传时间 |

---

## 4. 环境管理相关表

### 4.1 环境表 (environments)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(200) | 是 | - | 环境名称 |
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| template_id | UUID | 是 | - | 外键，关联environment_templates表 |
| config | JSONB | 是 | {} | 环境配置 |
| status | VARCHAR(20) | 是 | 'created' | 状态：created/configured/deploying/running/stopped/error/updating |
| version | INTEGER | 是 | 1 | 版本号 |
| container_id | VARCHAR(100) | 否 | null | Docker容器ID |
| created_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 4.2 环境快照表 (environment_snapshots)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| environment_id | UUID | 是 | - | 外键，关联environments表 |
| version | INTEGER | 是 | - | 快照版本号 |
| config_snapshot | JSONB | 是 | {} | 配置快照 |
| description | VARCHAR(500) | 否 | null | 快照描述 |
| created_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 4.3 环境模板表 (environment_templates)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(200) | 是 | - | 模板名称 |
| type | VARCHAR(20) | 是 | 'docker' | 模板类型：docker/vm/manual |
| config_schema | JSONB | 是 | {} | 配置Schema |
| deployment_script | TEXT | 否 | null | 部署脚本 |
| created_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

---

## 5. 文档管理相关表

### 5.1 文档表 (documents)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| title | VARCHAR(500) | 是 | - | 文档标题 |
| category | VARCHAR(50) | 是 | - | 分类：guide/report/notes/case |
| content | TEXT | 是 | - | 文档内容 |
| asset_id | UUID | 否 | null | 外键，关联assets表(可选) |
| author_id | UUID | 是 | - | 外键，关联users表 |
| status | VARCHAR(20) | 是 | 'draft' | 状态：draft/review/published/archived |
| version | INTEGER | 是 | 1 | 版本号 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 5.2 文档版本表 (document_versions)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| document_id | UUID | 是 | - | 外键，关联documents表 |
| version | INTEGER | 是 | - | 版本号 |
| content | TEXT | 是 | - | 文档内容 |
| changelog | VARCHAR(500) | 否 | null | 变更说明 |
| created_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 5.3 文档权限表 (document_permissions)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| document_id | UUID | 是 | - | 外键，关联documents表 |
| user_id | UUID | 是 | - | 外键，关联users表 |
| permission | VARCHAR(20) | 是 | 'read' | 权限：read/write/admin |

---

## 6. 工具资源库相关表

### 6.1 工具表 (tools)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(200) | 是 | - | 工具名称 |
| category | VARCHAR(50) | 是 | - | 分类：scanner/exploit/poc/auxiliary |
| description | TEXT | 否 | null | 工具描述 |
| version | VARCHAR(50) | 是 | '1.0.0' | 当前版本 |
| file_path | VARCHAR(500) | 是 | - | 文件存储路径 |
| requirements | TEXT | 否 | null | 依赖要求 |
| platform | VARCHAR(50) | 否 | null | 适用平台 |
| created_by | UUID | 是 | - | 外键，关联users表 |
| download_count | INTEGER | 是 | 0 | 下载次数 |
| status | VARCHAR(20) | 是 | 'active' | 状态：active/deprecated/archived |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 6.2 工具版本表 (tool_versions)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| tool_id | UUID | 是 | - | 外键，关联tools表 |
| version | VARCHAR(50) | 是 | - | 版本号 |
| file_path | VARCHAR(500) | 是 | - | 文件存储路径 |
| changelog | VARCHAR(500) | 否 | null | 变更说明 |
| created_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 6.3 工具标签表 (tool_tags)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| tool_id | UUID | 是 | - | 外键，关联tools表 |
| tag | VARCHAR(50) | 是 | - | 标签 |

### 6.4 工具下载记录表 (tool_downloads)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| tool_id | UUID | 是 | - | 外键，关联tools表 |
| user_id | UUID | 是 | - | 外键，关联users表 |
| downloaded_at | TIMESTAMP | 是 | 当前时间 | 下载时间 |

---

## 7. 任务管理相关表

### 7.1 任务表 (tasks)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| title | VARCHAR(500) | 是 | - | 任务标题 |
| type | VARCHAR(20) | 是 | - | 任务类型：asset/vulnerability/document/tool |
| reference_id | UUID | 是 | - | 关联对象ID |
| assignee_id | UUID | 是 | - | 外键，关联users表，任务接收人 |
| priority | VARCHAR(20) | 是 | 'medium' | 优先级：urgent/high/medium/low |
| status | VARCHAR(20) | 是 | 'todo' | 状态：todo/in_progress/review/done/cancelled/blocked |
| due_date | TIMESTAMP | 否 | null | 截止日期 |
| created_by | UUID | 是 | - | 外键，关联users表，创建人 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 7.2 任务评论表 (task_comments)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| task_id | UUID | 是 | - | 外键，关联tasks表 |
| user_id | UUID | 是 | - | 外键，关联users表 |
| content | TEXT | 是 | - | 评论内容 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 7.3 任务附件表 (task_attachments)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| task_id | UUID | 是 | - | 外键，关联tasks表 |
| file_name | VARCHAR(255) | 是 | - | 文件名 |
| file_path | VARCHAR(500) | 是 | - | 文件存储路径 |
| uploaded_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 上传时间 |

---

## 8. 知识库相关表

### 8.1 知识文章表 (knowledge_articles)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| title | VARCHAR(500) | 是 | - | 文章标题 |
| category | VARCHAR(50) | 是 | - | 分类：technique/case/guide/Q&A |
| content | TEXT | 是 | - | 文章内容 |
| author_id | UUID | 是 | - | 外键，关联users表 |
| view_count | INTEGER | 是 | 0 | 浏览次数 |
| status | VARCHAR(20) | 是 | 'draft' | 状态：draft/published/archived |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 8.2 知识评论表 (knowledge_comments)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| article_id | UUID | 是 | - | 外键，关联knowledge_articles表 |
| user_id | UUID | 是 | - | 外键，关联users表 |
| content | TEXT | 是 | - | 评论内容 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 8.3 知识标签表 (knowledge_tags)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| article_id | UUID | 是 | - | 外键，关联knowledge_articles表 |
| tag | VARCHAR(50) | 是 | - | 标签 |

---

## 9. 审计日志相关表

### 9.1 审计日志表 (audit_logs)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| user_id | UUID | 是 | - | 外键，关联users表 |
| action | VARCHAR(50) | 是 | - | 操作类型：create/update/delete/login/logout |
| resource | VARCHAR(50) | 是 | - | 资源类型 |
| resource_id | UUID | 是 | - | 资源ID |
| old_value | JSONB | 否 | null | 变更前值 |
| new_value | JSONB | 否 | null | 变更后值 |
| ip_address | VARCHAR(45) | 否 | null | IP地址 |
| user_agent | VARCHAR(500) | 否 | null | 用户代理 |
| created_at | TIMESTAMP | 是 | 当前时间 | 操作时间 |

---

## 10. 枚举值参考

### 10.1 用户角色 (user_role)
| 值 | 描述 |
|----|------|
| admin | 系统管理员 |
| leader | 团队负责人 |
| engineer | 漏洞工程师 |
| auditor | 审计人员 |

### 10.2 资产优先级 (asset_priority)
| 值 | 描述 |
|----|------|
| P0 | 紧急 |
| P1 | 高 |
| P2 | 中 |
| P3 | 低 |

### 10.3 漏洞严重程度 (vulnerability_severity)
| 值 | 描述 |
|----|------|
| critical | 严重 |
| high | 高危 |
| medium | 中危 |
| low | 低危 |

### 10.4 漏洞状态 (vulnerability_status)
| 值 | 描述 |
|----|------|
| discovered | 已发现 |
| verified | 已验证 |
| analyzing | 分析中 |
| analyzed | 已分析 |
| reported | 已报告 |
| closed | 已关闭 |
| fixed | 厂商已修复 |
| rejected | 已拒绝 |

### 10.5 目标来源 (target_source)
| 值 | 描述 |
|----|------|
| 客户委托 | 客户委托进行挖掘的目标 |
| 自主发现 | 团队自主发现并添加的目标 |

### 10.6 任务状态 (task_status)
| 值 | 描述 |
|----|------|
| todo | 待处理 |
| in_progress | 进行中 |
| review | 待审核 |
| done | 已完成 |
| cancelled | 已取消 |
| blocked | 阻塞 |

### 10.6 文档分类 (document_category)
| 值 | 描述 |
|----|------|
| guide | 环境搭建指南 |
| report | 漏洞分析报告 |
| notes | 技术笔记 |
| case | 典型案例 |

### 10.7 工具分类 (tool_category)
| 值 | 描述 |
|----|------|
| scanner | 扫描工具 |
| exploit | 利用工具 |
| poc | POC代码 |
| auxiliary | 辅助工具 |
