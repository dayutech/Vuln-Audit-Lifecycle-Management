# 数据字典

## 1. 用户与权限相关表

### 1.1 用户表 (users)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| username | VARCHAR(50) | 是 | - | 用户名，唯一 |
| email | VARCHAR(255) | 是 | - | 邮箱，唯一 |
| phone | VARCHAR(20) | 否 | null | 手机号，用于短信通知 |
| password_hash | VARCHAR(255) | 是 | - | 密码哈希 |
| full_name | VARCHAR(100) | 是 | - | 真实姓名 |
| avatar | VARCHAR(500) | 否 | null | 头像URL |
| position | VARCHAR(100) | 否 | null | 职位 |
| status | VARCHAR(20) | 是 | 'active' | 状态：active/suspended/deactivated |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |
| last_login | TIMESTAMP | 否 | null | 最后登录时间 |
| 注：用户与角色的关系通过 user_roles 表管理 |

### 1.2 角色表 (roles)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(100) | 是 | - | 角色名称 |
| code | VARCHAR(50) | 是 | - | 角色代码 |
| description | VARCHAR(255) | 否 | null | 角色描述 |
| is_system | BOOLEAN | 是 | false | 是否系统内置角色 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 1.3 权限表 (permissions)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(100) | 是 | - | 权限名称，如"资产查看" |
| code | VARCHAR(50) | 是 | - | 权限代码，如"asset:read" |
| api_path | VARCHAR(200) | 是 | - | API路径，如"/api/v1/assets" |
| http_method | VARCHAR(10) | 是 | - | HTTP方法：GET/POST/PUT/DELETE |
| description | VARCHAR(255) | 否 | null | 权限描述 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 1.4 角色权限关联表 (role_permissions)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| role_id | UUID | 是 | - | 外键，关联roles表 |
| permission_id | UUID | 是 | - | 外键，关联permissions表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 1.5 用户角色关联表 (user_roles)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| user_id | UUID | 是 | - | 外键，关联users表 |
| role_id | UUID | 是 | - | 外键，关联roles表 |
| assigned_by | UUID | 是 | - | 外键，关联users表，分配者 |
| assigned_at | TIMESTAMP | 是 | 当前时间 | 分配时间 |

### 1.6 用户权限关联表 (user_permissions)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| user_id | UUID | 是 | - | 外键，关联users表 |
| permission_id | UUID | 是 | - | 外键，关联permissions表 |
| granted_by | UUID | 是 | - | 外键，关联users表，授权者 |
| granted_at | TIMESTAMP | 是 | 当前时间 | 授权时间 |
| expires_at | TIMESTAMP | 否 | null | 过期时间 |

### 1.7 团队表 (teams)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| name | VARCHAR(100) | 是 | - | 团队名称 |
| parent_id | UUID | 否 | null | 外键，关联teams表，上级团队 |
| description | TEXT | 否 | null | 团队描述 |
| leader_id | UUID | 是 | - | 外键，关联users表，团队负责人 |
| created_by | UUID | 是 | - | 外键，关联users表，创建者 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 1.8 团队成员关联表 (team_members)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| user_id | UUID | 是 | - | 外键，关联users表 |
| team_id | UUID | 是 | - | 外键，关联teams表 |
| is_leader | BOOLEAN | 是 | false | 是否为团队负责人 |
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

### 2.4 资产标签表 (asset_tags)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| tag | VARCHAR(50) | 是 | - | 标签名称 |
| created_by | UUID | 是 | - | 外键，关联users表，创建人 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 2.5 资产历史表 (asset_history)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| field_name | VARCHAR(50) | 是 | - | 变更字段名 |
| old_value | TEXT | 否 | null | 变更前值 |
| new_value | TEXT | 否 | null | 变更后值 |
| changed_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 变更时间 |

### 2.6 资产认领表 (asset_claims)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| user_id | UUID | 是 | - | 外键，关联users表，认领人/漏洞挖掘工程师 |
| status | VARCHAR(20) | 是 | 'pending' | 状态：pending/approved/rejected/completed/abandoned |
| claimed_at | TIMESTAMP | 否 | null | 认领时间 |
| completed_at | TIMESTAMP | 否 | null | 完成时间 |
| note | TEXT | 否 | null | 认领备注 |
| reviewed_by | UUID | 否 | null | 外键，关联users表，审核人 |
| reviewed_at | TIMESTAMP | 否 | null | 审核时间 |
| review_note | TEXT | 否 | null | 审核备注 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 2.7 资产访问申请表 (asset_access)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| user_id | UUID | 是 | - | 外键，关联users表，申请人 |
| status | VARCHAR(20) | 是 | 'pending' | 状态：pending/approved/rejected |
| can_view_documents | BOOLEAN | 是 | false | 是否可查看文档 |
| can_view_environments | BOOLEAN | 是 | false | 是否可查看环境配置 |
| can_view_files | BOOLEAN | 是 | false | 是否可查看文件 |
| allowed_file_types | VARCHAR(500) | 否 | null | 允许访问的文件类型，多个用逗号分隔 |
| request_note | TEXT | 否 | null | 申请说明 |
| reviewed_by | UUID | 否 | null | 外键，关联users表，审批人 |
| reviewed_at | TIMESTAMP | 否 | null | 审批时间 |
| review_note | TEXT | 否 | null | 审批备注 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 2.8 资产文件表 (asset_files)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| name | VARCHAR(500) | 是 | - | 文件名 |
| file_type | VARCHAR(20) | 是 | - | 文件类型：iso/vmdk/exe/zip/7z/tar/gz/源码包/二进制程序/配置文件/其他 |
| category | VARCHAR(20) | 是 | - | 分类：系统镜像/磁盘文件/安装包/提取程序/配置文件/其他 |
| description | TEXT | 否 | null | 文件描述 |
| file_size | BIGINT | 否 | null | 文件大小（字节） |
| file_path | VARCHAR(500) | 是 | - | 存储路径 |
| checksum | VARCHAR(100) | 否 | null | SHA256校验值 |
| version | INTEGER | 是 | 1 | 当前版本号 |
| status | VARCHAR(20) | 是 | 'active' | 状态：active/archived |
| uploaded_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 2.9 资产文件版本表 (asset_file_versions)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| file_id | UUID | 是 | - | 外键，关联asset_files表 |
| version | INTEGER | 是 | - | 版本号 |
| file_path | VARCHAR(500) | 是 | - | 存储路径 |
| file_size | BIGINT | 否 | null | 文件大小（字节） |
| checksum | VARCHAR(100) | 否 | null | SHA256校验值 |
| changelog | TEXT | 否 | null | 版本变更说明 |
| uploaded_by | UUID | 是 | - | 外键，关联users表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 2.10 资产应用元数据表 (asset_app_metadata)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| claimed_by | UUID | 是 | - | 外键，关联users表（认领人） |
| version | INTEGER | 是 | 1 | 当前版本号 |
| system_architecture | VARCHAR(50) | 否 | null | 系统架构：x86/arm/mips等 |
| install_path | VARCHAR(500) | 否 | null | 安装路径 |
| startup_script_path | VARCHAR(500) | 否 | null | 启动脚本位置 |
| config_file_path | VARCHAR(500) | 否 | null | 配置文件位置 |
| startup_command | VARCHAR(1000) | 否 | null | 启动命令 |
| programming_language | VARCHAR(100) | 否 | null | 采用语言 |
| framework | VARCHAR(100) | 否 | null | 使用框架 |
| database_type | VARCHAR(50) | 否 | null | 数据库类型 |
| database_connection | VARCHAR(500) | 否 | null | 数据库连接信息（仅存储连接字符串模板） |
| admin_username | VARCHAR(100) | 否 | null | 管理员用户名 |
| admin_password_encrypted | VARCHAR(255) | 否 | null | 管理员密码（加密存储） |
| service_ports | VARCHAR(200) | 否 | null | 服务端口（多个用逗号分隔） |
| api_endpoints | TEXT | 否 | null | API端点列表 |
| special_notes | TEXT | 否 | null | 特殊说明 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 2.11 资产应用元数据历史表 (asset_app_metadata_history)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| metadata_id | UUID | 是 | - | 外键，关联asset_app_metadata表 |
| version | INTEGER | 是 | - | 版本号 |
| changed_by | UUID | 是 | - | 外键，关联users表（修改人） |
| change_reason | VARCHAR(500) | 是 | - | 修改理由（必填） |
| system_architecture | VARCHAR(50) | 否 | null | 系统架构 |
| install_path | VARCHAR(500) | 否 | null | 安装路径 |
| startup_script_path | VARCHAR(500) | 否 | null | 启动脚本位置 |
| config_file_path | VARCHAR(500) | 否 | null | 配置文件位置 |
| startup_command | VARCHAR(1000) | 否 | null | 启动命令 |
| programming_language | VARCHAR(100) | 否 | null | 采用语言 |
| framework | VARCHAR(100) | 否 | null | 使用框架 |
| database_type | VARCHAR(50) | 否 | null | 数据库类型 |
| database_connection | VARCHAR(500) | 否 | null | 数据库连接信息 |
| admin_username | VARCHAR(100) | 否 | null | 管理员用户名 |
| admin_password_encrypted | VARCHAR(255) | 否 | null | 管理员密码（加密存储） |
| service_ports | VARCHAR(200) | 否 | null | 服务端口 |
| api_endpoints | TEXT | 否 | null | API端点列表 |
| special_notes | TEXT | 否 | null | 特殊说明 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

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
| workflow_status | VARCHAR(30) | 是 | 'discovered' | 工作流状态：discovered/verified/analyzing/analyzed/exploit_development/field_testing/delivered/closed |
| customer_status | VARCHAR(20) | 是 | 'pending' | 客户确认状态：pending/confirmed/rejected |
| disclosure_status | VARCHAR(20) | 是 | 'undisclosed' | 公开披露状态：undisclosed/disclosed/has_cve |
| customer_confirmed_at | TIMESTAMP | 否 | null | 客户确认时间 |
| customer_rejected_at | TIMESTAMP | 否 | null | 客户拒绝时间 |
| customer_reject_reason | TEXT | 否 | null | 客户拒绝原因 |
| exploit_stage | VARCHAR(20) | 是 | '探测' | 利用阶段：探测/突破/控守/已完成 |
| exploit_code | TEXT | 否 | null | 利用代码 |
| field_test_status | VARCHAR(20) | 是 | '待测试' | 出场测试状态 |
| field_test_result | TEXT | 否 | null | 测试结果 |
| delivered_at | TIMESTAMP | 否 | null | 交付时间 |
| delivery_note | TEXT | 否 | null | 交付备注 |
| disclosure_date | TIMESTAMP | 否 | null | 公开披露日期 |
| disclosure_source | VARCHAR(200) | 否 | null | 披露来源，如CVE、安全公告等 |
| vendor_fixed | BOOLEAN | 是 | false | 厂商是否已修复（通过公开渠道观察） |
| vendor_fixed_at | TIMESTAMP | 否 | null | 厂商修复时间 |
| vendor_fix_version | VARCHAR(50) | 否 | null | 修复版本号 |
| description | TEXT | 是 | - | 漏洞描述 |
| detail | TEXT | 是 | - | 漏洞详情（Markdown格式，包含完整的漏洞分析、修复建议等） |
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
| status_type | VARCHAR(20) | 是 | - | 状态类型：workflow/customer/disclosure |
| from_status | VARCHAR(30) | 是 | - | 变更前状态 |
| to_status | VARCHAR(30) | 是 | - | 变更后状态 |
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

### 3.4 漏洞标签表 (vulnerability_tags)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| vulnerability_id | UUID | 是 | - | 外键，关联vulnerabilities表 |
| tag | VARCHAR(50) | 是 | - | 标签名称 |
| created_by | UUID | 是 | - | 外键，关联users表，创建人 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

---

## 4. 环境管理相关表

> **说明**：本模块为靶场环境管理功能，相关表暂保持为空实现，待后续需求明确后再行开发。

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
| category | VARCHAR(50) | 是 | - | 分类：guide/report/notes/tech/case |
| summary | VARCHAR(200) | 否 | null | 文档摘要，用于列表展示 |
| content | TEXT | 是 | - | 文档内容 |
| asset_id | UUID | 否 | null | 外键，关联assets表(可选) |
| author_id | UUID | 是 | - | 外键，关联users表 |
| status | VARCHAR(20) | 是 | 'draft' | 状态：draft/review/published/archived |
| version | INTEGER | 是 | 1 | 版本号 |
| word_count | INTEGER | 否 | 0 | 字数统计 |
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

### 5.3 文档访问申请表 (document_access)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| document_id | UUID | 是 | - | 外键，关联documents表 |
| user_id | UUID | 是 | - | 外键，关联users表，申请查看的用户 |
| status | VARCHAR(20) | 是 | 'pending' | 状态：pending/approved/rejected |
| request_note | TEXT | 否 | null | 申请说明 |
| reviewed_by | UUID | 否 | null | 外键，关联users表，审批人 |
| reviewed_at | TIMESTAMP | 否 | null | 审批时间 |
| review_note | TEXT | 否 | null | 审批备注 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 5.4 文档标签表 (document_tags)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| document_id | UUID | 是 | - | 外键，关联documents表 |
| tag | VARCHAR(50) | 是 | - | 标签名称 |
| created_by | UUID | 是 | - | 外键，关联users表，创建人 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

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
| project_leader | 项目负责人 |
| team_leader | 小组长 |
| vulnerability_engineer | 漏洞挖掘工程师（新成员入职即为此角色） |

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

### 10.4 漏洞工作流状态 (vulnerability_workflow_status)
| 值 | 描述 |
|----|------|
| discovered | 已发现 |
| verified | 已验证 |
| analyzing | 分析中 |
| analyzed | 已分析 |
| exploit_development | 利用开发中 |
| field_testing | 出场测试中 |
| delivered | 已交付 |
| closed | 已关闭 |

### 10.5 客户确认状态 (customer_status)
| 值 | 描述 |
|----|------|
| pending | 待确认 |
| confirmed | 已确认 |
| rejected | 已拒绝 |

### 10.6 公开披露状态 (disclosure_status)
| 值 | 描述 |
|----|------|
| undisclosed | 未公开 |
| disclosed | 已公开 |
| has_cve | 已分配CVE |

### 10.7 漏洞利用阶段 (exploit_stage)
| 值 | 描述 |
|----|------|
| 探测 | 探测阶段 |
| 突破 | 突破阶段 |
| 控守 | 控守阶段 |
| 已完成 | 利用代码完成 |

### 10.8 出场测试状态 (field_test_status)
| 值 | 描述 |
|----|------|
| 待测试 | 待出场测试 |
| 测试中 | 出场测试中 |
| 测试通过 | 测试通过 |
| 测试失败 | 测试失败 |

### 10.9 目标来源 (target_source)
| 值 | 描述 |
|----|------|
| 客户委托 | 客户委托进行挖掘的目标 |
| 自主发现 | 团队自主发现并添加的目标 |

### 10.10 任务状态 (task_status)
| 值 | 描述 |
|----|------|
| todo | 待处理 |
| in_progress | 进行中 |
| review | 待审核 |
| done | 已完成 |
| cancelled | 已取消 |
| blocked | 阻塞 |

### 10.11 文档分类 (document_category)
| 值 | 描述 |
|----|------|
| guide | 环境搭建指南 |
| report | 漏洞分析报告 |
| notes | 技术笔记 |
| tech | 技术文档 |
| case | 典型案例 |

### 10.12 工具分类 (tool_category)
| 值 | 描述 |
|----|------|
| scanner | 扫描工具 |
| exploit | 利用工具 |
| poc | POC代码 |
| auxiliary | 辅助工具 |

### 10.13 资产认领状态 (asset_claim_status)
| 值 | 描述 |
|----|------|
| pending | 待审核 |
| approved | 已批准 |
| rejected | 已拒绝 |
| completed | 已完成 |
| abandoned | 已放弃 |

### 10.14 资产文件类型 (asset_file_type)
| 值 | 描述 |
|----|------|
| iso | ISO镜像文件 |
| vmdk | VMDK虚拟机磁盘 |
| exe | 可执行文件 |
| zip | ZIP压缩包 |
| 7z | 7z压缩包 |
| tar | TAR归档文件 |
| gz | GZ压缩文件 |
| 源码包 | 源代码压缩包 |
| 二进制程序 | 二进制可执行程序 |
| 配置文件 | 配置文件 |
| 其他 | 其他文件 |

### 10.15 资产文件分类 (asset_file_category)
| 值 | 描述 |
|----|------|
| 系统镜像 | 操作系统镜像文件 |
| 磁盘文件 | 虚拟机磁盘文件 |
| 安装包 | 应用安装包 |
| 提取程序 | 从目标系统提取的程序 |
| 配置文件 | 系统或应用配置文件 |
| 其他 | 其他文件 |

---

## 11. 消息通知相关表

### 11.1 通知表 (notifications)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| type | VARCHAR(50) | 是 | - | 通知类型：asset_access_approved/asset_access_rejected/task_assigned/vulnerability_discovered/vulnerability_updated/document_comment/system_announcement |
| title | VARCHAR(200) | 是 | - | 通知标题 |
| content | TEXT | 是 | - | 通知内容 |
| related_type | VARCHAR(50) | 否 | null | 关联类型：asset/task/vulnerability/document |
| related_id | UUID | 否 | null | 关联ID |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 11.2 通知接收人表 (notification_recipients)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| notification_id | UUID | 是 | - | 外键，关联notifications表 |
| user_id | UUID | 是 | - | 外键，关联users表，接收用户 |
| recipient_type | VARCHAR(20) | 是 | - | 接收人类型：leader/assignee/specific_user |
| channel | VARCHAR(20) | 是 | 'in_app' | 通知渠道：email/sms/webhook/in_app |
| send_status | VARCHAR(20) | 是 | 'pending' | 发送状态：pending/sent/failed |
| sent_at | TIMESTAMP | 否 | null | 发送时间 |
| error_message | VARCHAR(500) | 否 | null | 错误信息 |
| is_read | BOOLEAN | 是 | false | 是否已读 |
| read_at | TIMESTAMP | 否 | null | 阅读时间 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 11.3 通知设置表 (notification_settings)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| user_id | UUID | 是 | - | 外键，关联users表 |
| type | VARCHAR(50) | 是 | - | 通知类型 |
| enabled | BOOLEAN | 是 | true | 是否启用 |
| notify_email | BOOLEAN | 是 | false | 邮件通知 |
| notify_sms | BOOLEAN | 是 | false | 短信通知（可配置） |
| notify_webhook | BOOLEAN | 是 | false | webhook推送 |

### 11.4 系统公告表 (system_announcements)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| title | VARCHAR(200) | 是 | - | 公告标题 |
| content | TEXT | 是 | - | 公告内容 |
| target_roles | JSONB | 否 | null | 目标角色 |
| priority | VARCHAR(20) | 是 | normal | 优先级 |
| published_at | TIMESTAMP | 是 | - | 发布时间 |
| expires_at | TIMESTAMP | 否 | null | 过期时间 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |

### 11.5 资产订阅表 (asset_subscriptions)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| user_id | UUID | 是 | - | 外键，关联users表 |
| asset_id | UUID | 是 | - | 外键，关联assets表 |
| created_at | TIMESTAMP | 是 | 当前时间 | 订阅时间 |

---

## 12. 仪表盘与统计相关表

### 12.1 仪表盘配置表 (dashboard_configs)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| user_id | UUID | 是 | - | 外键，关联users表 |
| layout | JSONB | 是 | - | 布局配置 |
| cards | JSONB | 是 | - | 启用的卡片 |
| created_at | TIMESTAMP | 是 | 当前时间 | 创建时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |

### 12.2 审批范围表 (approval_scopes)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| approver_id | UUID | 是 | - | 审批者ID |
| scope_type | VARCHAR(20) | 是 | - | 范围类型 |
| scope_id | UUID | 是 | - | 范围ID |
| granted_by | UUID | 是 | - | 授权者 |
| granted_at | TIMESTAMP | 是 | 当前时间 | 授权时间 |
| expires_at | TIMESTAMP | 否 | null | 过期时间 |

### 12.3 统计缓存表 (stat_cache)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| stat_type | VARCHAR(50) | 是 | - | 统计类型 |
| stat_key | VARCHAR(100) | 是 | - | 统计维度 |
| stat_value | JSONB | 是 | - | 统计值 |
| calculated_at | TIMESTAMP | 是 | 当前时间 | 计算时间 |

---

## 13. 第三方登录相关表（预留）

### 13.1 第三方账号绑定表 (oauth_bindings)

| 字段名 | 数据类型 | 是否必填 | 默认值 | 描述 |
|--------|----------|----------|--------|------|
| id | UUID | 是 | 自动生成 | 主键ID |
| user_id | UUID | 是 | - | 外键，关联users表 |
| provider | VARCHAR(20) | 是 | - | 提供商 |
| provider_user_id | VARCHAR(100) | 是 | - | 第三方用户ID |
| access_token | TEXT | 否 | null | 访问令牌 |
| refresh_token | TEXT | 否 | null | 刷新令牌 |
| expires_at | TIMESTAMP | 否 | null | 令牌过期时间 |
| created_at | TIMESTAMP | 是 | 当前时间 | 绑定时间 |
| updated_at | TIMESTAMP | 是 | 当前时间 | 更新时间 |
