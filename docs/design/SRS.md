# 漏洞挖掘生命周期管理系统 - 系统需求规格说明书

## 1. 项目概述

### 1.1 项目背景

在漏洞挖掘团队的实际工作中，面临着信息孤岛、资源管理混乱、协作效率低下等问题。团队成员之间缺乏统一的信息共享平台，导致重复劳动、环境配置不一致、知识传承困难等挑战。

### 1.2 项目目标

本系统旨在构建一套完整的漏洞挖掘生命周期管理平台，实现：
- 统一的资产管理和信息共享
- 标准化的环境配置和部署流程
- 全流程的漏洞生命周期跟踪
- 团队协作和知识沉淀

### 1.3 术语定义

| 术语 | 定义 |
|------|------|
| 资产 | 待挖掘的目标产品/系统 |
| 漏洞 | 安全缺陷的统称 |
| POC | 概念验证代码 |
| 环境快照 | 环境的完整配置备份 |
| RBAC | 基于角色的访问控制 |

---

## 2. 功能性需求

### 2.1 目标资产管理

#### 2.1.1 功能描述
建立全面的目标资产数据库，记录所有待挖掘产品/系统的基本信息、目标类型、重要程度及历史挖掘记录。支持目标分组管理，一个目标可属于多个分组。

#### 2.1.2 核心功能
- **资产 CRUD**：支持资产的创建、读取、更新、删除操作
- **资产访问控制**：漏洞挖掘工程师需申请访问资产，部门领导控制工程师能查看哪些内容（文档、文件等）
- **目标分组**：支持多对多关系，一个目标可属于多个分组（如按客户、按项目等）
- **目标来源**：区分客户委托目标和自主发现目标
- **重要程度标记**：P0/P1/P2/P3 四级优先级
- **历史记录**：记录资产的所有变更历史
- **关联漏洞**：管理与资产相关的漏洞列表
- **资产文件管理**：管理目标系统的ISO镜像、VMDK磁盘文件、安装包、提取的程序代码、依赖配置文件等，支持多版本管理

#### 2.1.3 数据模型
```
Asset {
    id: UUID
    name: String
    code: String (资产编号)
    target_type: String (目标类型：Web应用/系统/服务/IoT设备等)
    target_source: Enum (客户委托/自主发现)
    description: Text
    priority: Enum (P0/P1/P2/P3)
    status: Enum (active/suspended/deactivated)
    owner_id: UUID
    created_at: DateTime
    updated_at: DateTime
}

AssetGroup {
    id: UUID
    name: String (分组名称)
    type: Enum (客户/项目/自建)
    description: Text
    created_at: DateTime
}

AssetGroupRelation {
    asset_id: UUID
    group_id: UUID
}

AssetAccess {
    id: UUID
    asset_id: UUID (关联资产)
    user_id: UUID (申请人/漏洞挖掘工程师)
    status: Enum (pending/approved/rejected)
    access_scope: JSON (可访问的内容范围：{documents: true/false, files: ["类型1", "类型2"], environments: true/false})
    request_note: Text (申请说明)
    reviewed_by: UUID (审批人/部门领导)
    reviewed_at: DateTime
    review_note: Text (审批备注)
    created_at: DateTime
    updated_at: DateTime
}

AssetFile {
    id: UUID
    asset_id: UUID (关联资产)
    name: String (文件名)
    file_type: Enum (iso/vmdk/exe/zip/7z/tar/gz/源码包/二进制程序/配置文件/其他)
    category: Enum (系统镜像/磁盘文件/安装包/提取程序/配置文件/其他)
    description: Text (文件描述)
    file_size: BigInteger (文件大小字节)
    file_path: String (存储路径)
    checksum: String (SHA256校验值)
    version: Integer (当前版本号)
    status: Enum (active/archived)
    uploaded_by: UUID
    created_at: DateTime
    updated_at: DateTime
}

AssetAppMetadata {
    id: UUID
    asset_id: UUID (关联资产，认领后填写)
    claimed_by: UUID (认领人/漏洞挖掘工程师)
    version: Integer (当前版本号)
    system_architecture: String (系统架构：x86/arm/mips等)
    install_path: String (安装路径)
    startup_script_path: String (启动脚本位置)
    config_file_path: String (配置文件位置)
    startup_command: String (启动命令)
    programming_language: String (采用语言)
    framework: String (使用框架)
    database_type: String (数据库类型)
    database_connection: String (数据库连接信息，仅存储连接字符串模板)
    admin_username: String (管理员用户名)
    admin_password_encrypted: String (管理员密码，加密存储)
    service_ports: String (服务端口，多个用逗号分隔)
    api_endpoints: String (API端点列表)
    special_notes: Text (特殊说明)
    created_at: DateTime
    updated_at: DateTime
}

AssetAppMetadataHistory {
    id: UUID
    metadata_id: UUID (关联主表)
    version: Integer (版本号)
    changed_by: UUID (修改人)
    change_reason: String (修改理由，必填)
    system_architecture: String
    install_path: String
    startup_script_path: String
    config_file_path: String
    startup_command: String
    programming_language: String
    framework: String
    database_type: String
    database_connection: String
    admin_username: String
    admin_password_encrypted: String
    service_ports: String
    api_endpoints: String
    special_notes: Text
    created_at: DateTime
}

AssetFileVersion {
    id: UUID
    file_id: UUID
    version: Integer (版本号)
    file_path: String (存储路径)
    file_size: BigInteger
    checksum: String
    changelog: Text (版本变更说明)
    uploaded_by: UUID
    created_at: DateTime
}
```

---

### 2.2 环境管理模块

#### 2.2.1 功能描述
提供标准化的环境配置模板，支持环境快照、版本控制和一键部署功能。

#### 2.2.2 核心功能
- **环境模板**：创建和管理标准环境配置模板
- **环境快照**：保存环境的完整配置快照
- **版本控制**：支持环境的版本管理
- **一键部署**：通过模板快速创建新环境
- **环境状态监控**：实时监控环境运行状态

#### 2.2.3 数据模型
```
Environment {
    id: UUID
    name: String
    asset_id: UUID (关联资产)
    template_id: UUID
    config: JSON
    status: Enum (running/stopped/error)
    version: Integer
    snapshot_count: Integer
    created_at: DateTime
    updated_at: DateTime
}

EnvironmentSnapshot {
    id: UUID
    environment_id: UUID
    version: Integer
    config_snapshot: JSON
    description: String
    created_by: UUID
    created_at: DateTime
}

EnvironmentTemplate {
    id: UUID
    name: String
    type: String (docker/vm/manual)
    config_schema: JSON
    deployment_script: Text
    created_at: DateTime
}
```

---

### 2.3 文档管理系统

#### 2.3.1 功能描述
集中存储各类挖掘文档，支持在线创建、编辑、查看、删除等操作。用户可查看其他用户创建的文档及上传的资源列表，但查看具体内容需由部门领导审批。

#### 2.3.2 核心功能
- **文档在线创建**：支持在线创建新文档，选择文档类型和关联资产
- **文档在线编辑**：富文本编辑器，支持在线编辑文档内容
- **文档在线查看**：在线查看文档内容，支持渲染富文本
- **文档删除**：支持删除自己创建的文档
- **文档与资产关联**：每个资产可关联多个用户的文档空间
- **用户文档隔离存储**：文档按用户ID目录结构隔离存储，层次化管理，结构清晰
- **文档列表可见**：用户可查看其他用户有哪些文档、安装包等资源（仅列表，不含内容）
- **文档申请查看**：用户可申请查看其他用户的文档内容，由部门领导审批后可得
- **文档分类**：环境搭建指南、漏洞分析报告、利用思路、技术笔记
- **版本控制**：支持文档的多版本管理
- **全文检索**：支持文档内容搜索

#### 2.3.3 数据模型
```
Document {
    id: UUID
    title: String
    category: Enum (guide/report/notes/tech/case)
    content: Text (富文本内容，支持Markdown/HTML)
    asset_id: UUID (关联资产)
    author_id: UUID (文档所有者)
    status: Enum (draft/published/archived)
    version: Integer
    word_count: Integer (字数统计)
    created_at: DateTime
    updated_at: DateTime
}

DocumentVersion {
    id: UUID
    document_id: UUID
    version: Integer
    content: Text
    changelog: String
    created_by: UUID
    created_at: DateTime
}
```

---

### 2.4 漏洞生命周期跟踪

#### 2.4.1 功能描述
实现从漏洞发现、验证、客户确认、利用开发、出场测试到交付归档的全流程跟踪。

#### 2.4.2 漏洞状态流转
```
发现 -> 验证 -> 分析完成 -> 客户确认 -> 利用开发 -> 出场测试 -> 已交付 -> 归档关闭
                                              \-> 厂商修复跟踪 -> 已修复标记
```

#### 2.4.3 核心功能
- **漏洞记录**：详细记录漏洞信息（支持Markdown格式的描述和详情）
- **漏洞详情**：完整的漏洞分析报告，包含漏洞原理、修复建议等
- **状态变更**：跟踪漏洞状态变更历史
- **处理进度**：可视化漏洞处理进度
- **漏洞评级**：CVSS评分、内部评级
- **客户确认**：跟踪客户是否确认接收漏洞
- **利用开发**：记录漏洞利用代码开发阶段（探测、突破、控守）
- **出场测试**：跟踪漏洞利用代码的测试进度
- **交付管理**：记录漏洞交付状态和交付时间
- **厂商修复跟踪**：跟踪目标厂商是否修复了该漏洞，标记为"已修复"
- **关联资产**：管理与漏洞相关的资产

#### 2.4.4 数据模型
```
Vulnerability {
    id: UUID
    title: String
    asset_id: UUID
    cve_id: String (可选)
    type: String (漏洞类型)
    severity: Enum (critical/high/medium/low)
    cvss_score: Float
    status: Enum (discovered|verified|analyzing|analyzed|customer_confirmed|exploit_development|field_testing|delivered|closed|fixed|rejected)
    
    customer_confirmed: Boolean (客户是否确认接收)
    customer_confirmed_at: DateTime (客户确认时间)
    exploit_stage: Enum (探测|突破|控守|已完成)
    exploit_code: Text (利用代码)
    field_test_status: Enum (待测试|测试中|测试通过|测试失败)
    field_test_result: Text (测试结果)
    delivered_at: DateTime (交付时间)
    delivery_note: Text (交付备注)
    
    vendor_notified: Boolean (是否已通知厂商)
    vendor_fixed: Boolean (厂商是否已修复)
    fixed_at: DateTime (修复时间)
    description: Text (漏洞描述，Markdown格式)
    detail: Text (漏洞详情，Markdown格式，包含完整的漏洞分析、修复建议等)
    impact: Text
    poc_code: Text (POC代码)
    discoverer_id: UUID
    assigned_to: UUID
    created_at: DateTime
    updated_at: DateTime
}

VulnerabilityHistory {
    id: UUID
    vulnerability_id: UUID
    from_status: String
    to_status: String
    changed_by: UUID
    comment: String
    created_at: DateTime
}
```

---

### 2.5 工具资源库

#### 2.5.1 功能描述
建立团队共享的漏洞利用工具、POC代码和辅助脚本库。

#### 2.5.2 核心功能
- **工具分类**：按用途、平台、漏洞类型分类
- **版本管理**：工具版本控制
- **标签检索**：多维度标签检索
- **使用统计**：工具使用次数统计
- **权限控制**：敏感工具的访问控制

#### 2.5.3 数据模型
```
Tool {
    id: UUID
    name: String
    category: String
    description: Text
    version: String
    file_path: String
    requirements: Text
    created_by: UUID
    download_count: Integer
    status: Enum (active/deprecated)
    created_at: DateTime
    updated_at: DateTime
}

ToolVersion {
    id: UUID
    tool_id: UUID
    version: String
    file_path: String
    changelog: String
    created_by: UUID
    created_at: DateTime
}

ToolTag {
    id: UUID
    tool_id: UUID
    tag: String
    created_by: UUID
    created_at: DateTime
}
```

---

### 2.6 团队协作功能

#### 2.6.1 功能描述
实现任务分配、进度更新、工作状态可视化。

#### 2.6.2 核心功能
- **任务管理**：创建、分配、跟踪任务
- **进度看板**：可视化团队工作进度
- **工作状态**：实时查看团队成员状态
- **重复检测**：避免重复工作
- **通知提醒**：任务状态变更通知

#### 2.6.3 数据模型
```
Task {
    id: UUID
    title: String
    type: Enum (asset|vulnerability|document|tool)
    reference_id: UUID (关联对象ID)
    assignee_id: UUID
    priority: Enum (urgent/high/medium/low)
    status: Enum (todo|in_progress|review|done)
    due_date: DateTime
    created_by: UUID
    created_at: DateTime
    updated_at: DateTime
}

TeamMember {
    id: UUID
    user_id: UUID
    team_id: UUID
    role: String
    status: Enum (available|busy|offline)
    updated_at: DateTime
}
```

---

### 2.7 权限与安全控制

#### 2.7.1 功能描述
基于API级别的细粒度权限控制，支持内置角色和自定义权限分配。管理员拥有最高权限，可控制每个用户能访问哪些API接口。按漏洞挖掘部门架构管理用户（项目负责人、小组长、工程师等）。

#### 2.7.2 核心功能
- **API权限管理**：每个API接口单独管理权限，管理员可控制用户能访问哪些接口
- **内置角色**：预定义项目负责人、小组长、漏洞挖掘工程师等角色
- **角色权限分配**：每个角色包含一组API权限
- **用户角色分配**：将角色分配给用户，支持一个用户多个角色
- **用户权限继承**：用户权限 = 角色权限的并集 + 单独分配的权限
- **审批授权管理**：部门领导只能审批其直属下属的申请，审批权限由上级领导授予
- **审批权限继承**：小组长只能审批自己被授权范围内的资产访问，审批范围由项目负责人分配
- **操作审计**：记录所有敏感操作日志

#### 2.7.3 角色定义
| 角色 | 权限描述 |
|------|----------|
| 管理员 | 系统全权管理，可管理所有用户、角色、权限 |
| 项目负责人 | 管理整个项目，审批资产访问，查看所有数据 |
| 小组长 | 管理小组团队成员，审批小组内资产访问申请 |
| 漏洞挖掘工程师 | 挖掘漏洞，上传文档/文件，申请资产访问（新成员入职即为此角色） |

#### 2.7.4 数据模型
```
Permission {
    id: UUID
    name: String (权限名称，如 "资产查看", "漏洞提交")
    code: String (权限代码，如 "asset:read", "vulnerability:create")
    api_path: String (API路径，如 "/api/v1/assets")
    http_method: String (HTTP方法，如 GET/POST/PUT/DELETE)
    description: Text
    created_at: DateTime
}

Role {
    id: UUID
    name: String (角色名称)
    code: String (角色代码)
    description: Text
    is_system: Boolean (是否系统内置角色)
    created_at: DateTime
    updated_at: DateTime
}

RolePermission {
    id: UUID
    role_id: UUID
    permission_id: UUID
    created_at: DateTime
}

UserRole {
    id: UUID
    user_id: UUID
    role_id: UUID
    assigned_by: UUID (分配者)
    assigned_at: DateTime
}

UserPermission {
    id: UUID
    user_id: UUID
    permission_id: UUID
    granted_by: UUID (授权者)
    granted_at: DateTime
    expires_at: DateTime (过期时间，可选)
}

ApprovalScope {
    id: UUID
    approver_id: UUID (被授权的审批者ID)
    scope_type: Enum (team/asset_category/asset)
    scope_id: UUID (团队ID/资产分类ID/资产ID)
    granted_by: UUID (授权者，通常是项目负责人或更高级别)
    granted_at: DateTime
    expires_at: DateTime (可选)
}

User {
    id: UUID
    username: String
    email: String
    password_hash: String
    full_name: String
    avatar: String
    position: String (职位)
    status: Enum (active/suspended/deactivated)
    created_at: DateTime
    updated_at: DateTime
    last_login: DateTime
    注：用户与团队关系通过 team_members 表管理，用户与角色关系通过 user_roles 表管理
}

Team {
    id: UUID
    name: String (团队/部门名称)
    parent_id: UUID (上级团队，可用于层级结构)
    leader_id: UUID (团队负责人)
    description: Text
    created_at: DateTime
}
```

---

### 2.8 知识沉淀与传承

#### 2.8.1 功能描述
建立知识库，记录挖掘经验和典型案例。

#### 2.8.2 核心功能
- **知识库分类**：按技术领域分类
- **案例管理**：典型漏洞案例记录
- **经验分享**：挖掘技巧和心得
- **新手引导**：新成员快速上手指南
- **问答系统**：常见问题解答

#### 2.8.3 数据模型
```
KnowledgeArticle {
    id: UUID
    title: String
    category: String
    content: Text
    author_id: UUID
    view_count: Integer
    status: Enum (draft/published)
    created_at: DateTime
    updated_at: DateTime
}

KnowledgeComment {
    id: UUID
    article_id: UUID
    user_id: UUID
    content: Text
    created_at: DateTime
}

KnowledgeTag {
    id: UUID
    article_id: UUID
    tag: String
    created_by: UUID
    created_at: DateTime
}
```

### 2.9 消息通知模块

#### 2.9.1 功能描述
实现系统消息通知功能，当用户的申请被审批、任务被分配、有新漏洞更新等事件发生时，发送通知给相关用户。

#### 2.9.2 核心功能
- **通知中心**：统一接收和查看所有通知
- **通知类型**：资产访问审批结果、任务分配、漏洞发现、漏洞状态变更、文档评论、系统公告
- **通知方式**：站内消息（必选）、邮件通知（可配置）、短信通知（可配置）、webhook推送（可选）
- **漏洞通知**：新漏洞发现时自动通知所有上级领导，也可指定发送给特定用户
- **通知订阅**：用户可订阅感兴趣的资产和漏洞动态
- **已读未读**：标记通知为已读，支持批量操作
- **历史记录**：保留通知历史，支持查询

#### 2.9.3 数据模型
```
Notification {
    id: UUID
    user_id: UUID (接收用户)
    type: Enum (asset_access_approved/asset_access_rejected/task_assigned/vulnerability_discovered/vulnerability_updated/document_comment/system_announcement)
    title: String
    content: Text
    related_type: String (关联类型：asset/task/vulnerability/document)
    related_id: UUID (关联ID)
    is_read: Boolean
    read_at: DateTime
    created_at: DateTime
}

NotificationSetting {
    id: UUID
    user_id: UUID
    type: String (通知类型)
    enabled: Boolean (是否启用)
    notify_email: Boolean (邮件通知)
    notify_sms: Boolean (短信通知)
    notify_webhook: Boolean (webhook推送)
}

NotificationRecipient {
    id: UUID
    notification_id: UUID (关联通知)
    user_id: UUID (接收用户)
    recipient_type: Enum (leader/assignee/specific_user, 接收人类型)
    channel: Enum (email/sms/webhook/in_app, 通知渠道)
    send_status: Enum (pending/sent/failed, 发送状态)
    sent_at: DateTime (发送时间)
    error_message: String (错误信息)
    created_at: DateTime
}

SystemAnnouncement {
    id: UUID
    title: String
    content: Text
    target_roles: Array(String) (目标角色，为空则全员)
    priority: Enum (normal/important/urgent)
    published_at: DateTime
    expires_at: DateTime (可选)
    created_at: DateTime
}
```

### 2.10 仪表盘与统计模块

#### 2.10.1 功能描述
提供数据统计和可视化展示，帮助管理层了解团队工作进度、资产安全状况、漏洞挖掘成果等。

#### 2.10.2 核心功能
- **资产统计**：资产总数、按状态分布、按类型分布、按重要程度分布
- **漏洞统计**：漏洞总数、按状态分布、按危害等级分布、按资产分布
- **工作量统计**：各工程师提交漏洞数量、文档数量、资产处理数量
- **趋势分析**：漏洞发现趋势、工作量趋势、资产增长趋势
- **仪表盘配置**：用户可自定义展示的统计卡片和布局
- **导出报表**：支持导出统计报表（Excel/PDF）

#### 2.10.3 数据模型
```
DashboardConfig {
    id: UUID
    user_id: UUID
    layout: JSON (仪表盘布局配置)
    cards: Array(String) (启用的统计卡片)
    created_at: DateTime
    updated_at: DateTime
}

StatCache {
    id: UUID
    stat_type: String (统计类型)
    stat_key: String (统计维度，如日期、资产ID等)
    stat_value: JSON (统计值)
    calculated_at: DateTime
}
```

---

## 3. 非功能性需求

### 3.1 性能需求
- 系统响应时间 < 3秒
- 支持 100+ 并发用户
- 大规模数据查询优化

### 3.2 安全需求
- 数据传输加密 (HTTPS)
- 敏感数据加密存储
- 操作日志完整记录
- 定期安全审计

### 3.3 可用性需求
- 系统可用性 99.9%
- 故障恢复时间 < 1小时
- 数据备份频率: 每日增量，每周全量

### 3.4 可扩展性需求
- 支持多团队协作
- 支持横向扩展
- 模块化设计

---

## 4. 系统架构

### 4.1 整体架构
采用前后端分离架构，后端提供RESTful API，前端采用单页应用(SPA)架构。

### 4.2 技术选型
- 后端: Java Spring Boot
- 前端: React / Vue.js
- 数据库: MariaDB + Redis
- 文件存储: 本地存储（当前阶段），预留对象存储(MinIO/S3)接口
- 容器化: Docker
- 构建工具: Maven

---

## 5. 附录

### 5.1 需求优先级矩阵

| 功能模块 | 优先级 | 迭代 |
|----------|--------|------|
| 目标资产管理 | P0 | V1.0 |
| 漏洞生命周期跟踪 | P0 | V1.0 |
| 环境管理模块 | P1 | V1.0 |
| 文档管理系统 | P1 | V1.0 |
| 工具资源库 | P1 | V1.0 |
| 团队协作功能 | P1 | V1.0 |
| 权限与安全控制 | P0 | V1.0 |
| 知识沉淀与传承 | P2 | V2.0 |

### 5.2 版本规划
- V1.0: 核心功能上线
- V2.0: 知识库模块
- V3.0: 高级分析和报表
