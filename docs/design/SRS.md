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

## 2. 用户角色描述

### 2.1 角色概述

本系统涉及四类主要用户角色，每类角色具有不同的职责和系统使用场景。

### 2.2 管理员

#### 角色特征
- **职责定位**：系统运维和全局管理
- **技术能力**：具备系统管理和安全运维经验
- **使用频率**：低频使用，主要在系统配置和异常处理时介入

#### 典型场景
| 场景 | 描述 |
|------|------|
| 用户管理 | 创建/停用用户账号，重置密码 |
| 角色分配 | 为新员工分配初始角色，调整员工角色 |
| 权限配置 | 配置API权限，创建自定义角色 |
| 系统监控 | 查看系统运行状态，处理异常告警 |
| 审计日志 | 查看敏感操作日志，安全审计 |

### 2.3 项目负责人

#### 角色特征
- **职责定位**：项目整体管理和决策
- **技术能力**：具备漏洞挖掘经验和管理能力
- **使用频率**：中频使用，每日查看进度，定期审批

#### 典型场景
| 场景 | 描述 |
|------|------|
| 资产审批 | 审批工程师的资产访问申请 |
| 进度监控 | 查看仪表盘，了解团队工作进度 |
| 漏洞审核 | 审核重要漏洞的挖掘质量 |
| 资源分配 | 分配资产给小组或工程师 |
| 报表导出 | 导出统计报表，向上级汇报 |

### 2.4 小组长

#### 角色特征
- **职责定位**：小组日常管理和技术指导
- **技术能力**：资深漏洞挖掘工程师
- **使用频率**：高频使用，每日多次登录

#### 典型场景
| 场景 | 描述 |
|------|------|
| 任务分配 | 将资产分配给组内工程师 |
| 审批申请 | 审批组内成员的资产访问申请 |
| 技术指导 | 查看组员提交的漏洞，提供技术建议 |
| 进度跟踪 | 跟踪组内任务完成情况 |
| 文档审核 | 审核组员上传的文档质量 |

### 2.5 漏洞挖掘工程师

#### 角色特征
- **职责定位**：核心业务执行者
- **技术能力**：具备漏洞挖掘和安全测试技能
- **使用频率**：极高频使用，全天候使用

#### 典型场景
| 场景 | 描述 |
|------|------|
| 资产认领 | 申请并认领待挖掘的资产 |
| 环境搭建 | 配置和部署测试环境 |
| 漏洞挖掘 | 发现漏洞，提交漏洞报告 |
| 文档编写 | 编写环境搭建指南、漏洞分析报告 |
| POC开发 | 开发漏洞利用代码和验证脚本 |
| 工具使用 | 使用工具资源库中的工具 |

### 2.6 角色关系图

```
管理员
  │
  ├── 项目负责人 (可管理多个项目)
  │       │
  │       ├── 小组长 A (管理小组A)
  │       │       ├── 工程师 1
  │       │       ├── 工程师 2
  │       │       └── ...
  │       │
  │       ├── 小组长 B (管理小组B)
  │       │       ├── 工程师 3
  │       │       └── ...
  │       │
  │       └── ...
  │
  └── ...
```

---

## 3. 功能性需求

### 3.1 目标资产管理

#### 3.1.1 功能描述
建立全面的目标资产数据库，记录所有待挖掘产品/系统的基本信息、目标类型、重要程度及历史挖掘记录。支持目标分组管理，一个目标可属于多个分组。

#### 3.1.2 核心功能
- **资产 CRUD**：支持资产的创建、读取、更新、删除操作
- **资产访问控制**：漏洞挖掘工程师需申请访问资产，部门领导控制工程师能查看哪些内容（文档、文件等）
- **目标分组**：支持多对多关系，一个目标可属于多个分组（如按客户、按项目等）
- **目标来源**：区分客户委托目标和自主发现目标
- **重要程度标记**：P0/P1/P2/P3 四级优先级
- **历史记录**：记录资产的所有变更历史
- **关联漏洞**：管理与资产相关的漏洞列表
- **资产文件管理**：管理目标系统的ISO镜像、VMDK磁盘文件、安装包、提取的程序代码、依赖配置文件等，支持多版本管理

#### 3.1.3 数据模型
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
    can_view_documents: Boolean (是否可查看文档)
    can_view_environments: Boolean (是否可查看环境配置)
    can_view_files: Boolean (是否可查看文件)
    allowed_file_types: String (允许访问的文件类型，多个用逗号分隔，如 "iso,vmdk,zip"，为空表示全部)
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

### 3.2 环境管理模块

#### 3.2.1 功能描述
提供标准化的环境配置模板，支持环境快照、版本控制和一键部署功能。

#### 3.2.2 核心功能
- **环境模板**：创建和管理标准环境配置模板
- **环境快照**：保存环境的完整配置快照
- **版本控制**：支持环境的版本管理
- **一键部署**：通过模板快速创建新环境
- **环境状态监控**：实时监控环境运行状态

#### 3.2.3 数据模型
```
Environment {
    id: UUID
    name: String
    asset_id: UUID (关联资产)
    template_id: UUID
    config: JSON (环境配置，结构见下方config_schema定义)
    status: Enum (running/stopped/error)
    version: Integer
    snapshot_count: Integer
    created_at: DateTime
    updated_at: DateTime
}

config_schema定义：
{
    "cpu": Integer (CPU核心数),
    "memory": Integer (内存大小MB),
    "disk": Integer (磁盘大小GB),
    "network": {
        "type": String (网络类型：bridge/nat/host),
        "ip": String (IP地址，可选),
        "port_mappings": Array[{host_port: Integer, container_port: Integer}]
    },
    "os": {
        "type": String (操作系统类型),
        "version": String (操作系统版本)
    },
    "environment_variables": Object (环境变量键值对),
    "startup_command": String (启动命令),
    "health_check": {
        "enabled": Boolean,
        "interval": Integer (检查间隔秒),
        "timeout": Integer (超时秒)
    }
}

EnvironmentSnapshot {
    id: UUID
    environment_id: UUID
    version: Integer
    config_snapshot: JSON (环境配置快照，结构同config_schema)
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

### 3.3 文档管理系统

#### 3.3.1 功能描述
集中存储各类挖掘文档，支持在线创建、编辑、查看、删除等操作。用户可查看其他用户创建的文档及上传的资源列表，但查看具体内容需由部门领导审批。

#### 3.3.2 核心功能
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

#### 3.3.3 数据模型
```
Document {
    id: UUID
    title: String
    category: Enum (guide/report/notes/tech/case)
    summary: String (文档摘要，用于列表展示，最多200字)
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

### 3.4 漏洞生命周期跟踪

#### 3.4.1 功能描述
实现从漏洞发现、验证、客户确认、利用开发、出场测试到交付归档的全流程跟踪。

#### 3.4.2 漏洞状态流转

##### 3.4.2.1 工作流状态（workflow_status）

主流程状态流转：
```
发现(discovered) 
    ↓
已验证(verified) 
    ↓
分析中(analyzing) 
    ↓
分析完成(analyzed) 
    ↓
利用开发(exploit_development) 
    ↓
出场测试(field_testing) 
    ↓
已交付(delivered) 
    ↓
已关闭(closed)
```

状态说明：
| 状态 | 说明 | 可转换目标状态 |
|------|------|----------------|
| discovered | 新发现的漏洞，待验证 | verified, closed |
| verified | 已验证漏洞存在 | analyzing, closed |
| analyzing | 正在进行漏洞分析 | analyzed, verified |
| analyzed | 漏洞分析完成，等待客户确认 | exploit_development, closed |
| exploit_development | 正在开发利用代码 | field_testing, analyzed |
| field_testing | 出场测试中 | delivered, exploit_development |
| delivered | 已交付给客户 | closed |
| closed | 流程结束 | - |

##### 3.4.2.2 客户确认状态（customer_status）

客户确认流程：
```
待确认(pending) 
    ├──→ 已确认(confirmed) → 继续利用开发流程
    └──→ 已拒绝(rejected) → 记录拒绝原因，流程可重新开始
```

状态说明：
| 状态 | 说明 | 触发条件 |
|------|------|----------|
| pending | 等待客户确认 | 漏洞分析完成后默认状态 |
| confirmed | 客户已确认接收漏洞 | 客户确认操作 |
| rejected | 客户拒绝接收漏洞 | 客户拒绝操作，需填写拒绝原因 |

##### 3.4.2.3 公开披露状态（disclosure_status）

公开披露跟踪流程：
```
未公开(undisclosed) 
    ↓
已公开(disclosed) 
    ↓
已分配CVE(has_cve)
```

状态说明：
| 状态 | 说明 | 触发条件 |
|------|------|----------|
| undisclosed | 未公开 | 默认状态，漏洞信息仅团队内部可见 |
| disclosed | 已公开 | 漏洞被其他人公开披露（安全公告、博客等） |
| has_cve | 已分配CVE | 漏洞已被分配CVE编号 |

##### 3.4.2.4 状态流转规则

1. **客户确认与工作流的关联**：
   - 当 workflow_status 为 analyzed 时，customer_status 应为 pending
   - customer_status 为 confirmed 后，workflow_status 才能进入 exploit_development
   - customer_status 为 rejected 后，可重新提交漏洞分析，workflow_status 回退到 analyzing

2. **公开披露的独立性**：
   - disclosure_status 独立于 workflow_status，可在任意阶段更新
   - 通过公开渠道（CVE数据库、安全公告、社交媒体等）观察漏洞是否被披露
   - 厂商修复状态（vendor_fixed）通过公开渠道观察，不主动通知厂商

3. **状态回退规则**：
   - 允许从后续状态回退到前一状态（如 field_testing → exploit_development）
   - 回退操作需填写回退原因
   - 回退后相关时间戳字段清空

#### 3.4.3 核心功能
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

#### 3.4.4 数据模型
```
Vulnerability {
    id: UUID
    title: String
    asset_id: UUID
    cve_id: String (可选)
    type: String (漏洞类型)
    severity: Enum (critical/high/medium/low)
    cvss_score: Float
    
    workflow_status: Enum (discovered|verified|analyzing|analyzed|exploit_development|field_testing|delivered|closed)
    customer_status: Enum (pending|confirmed|rejected)
    
    customer_confirmed_at: DateTime (客户确认时间)
    customer_rejected_at: DateTime (客户拒绝时间)
    customer_reject_reason: Text (客户拒绝原因)
    exploit_stage: Enum (探测|突破|控守|已完成)
    exploit_code: Text (利用代码)
    field_test_status: Enum (待测试|测试中|测试通过|测试失败)
    field_test_result: Text (测试结果)
    delivered_at: DateTime (交付时间)
    delivery_note: Text (交付备注)
    
    disclosure_status: Enum (undisclosed|disclosed|has_cve)
    disclosure_date: DateTime (公开披露日期)
    disclosure_source: String (披露来源，如CVE、安全公告等)
    vendor_fixed: Boolean (厂商是否已修复，通过公开渠道观察)
    vendor_fixed_at: DateTime (厂商修复时间)
    vendor_fix_version: String (修复版本号)
    
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
    status_type: Enum (workflow|customer|disclosure)
    from_status: String
    to_status: String
    changed_by: UUID
    comment: String
    created_at: DateTime
}
```

---

### 3.5 工具资源库

#### 3.5.1 功能描述
建立团队共享的漏洞利用工具、POC代码和辅助脚本库。

#### 3.5.2 核心功能
- **工具分类**：按用途、平台、漏洞类型分类
- **版本管理**：工具版本控制
- **标签检索**：多维度标签检索
- **使用统计**：工具使用次数统计
- **权限控制**：敏感工具的访问控制

#### 3.5.3 数据模型
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

### 3.6 团队协作功能

#### 3.6.1 功能描述
实现任务分配、进度更新、工作状态可视化。

#### 3.6.2 核心功能
- **任务管理**：创建、分配、跟踪任务
- **进度看板**：可视化团队工作进度
- **工作状态**：实时查看团队成员状态
- **重复检测**：避免重复工作
- **通知提醒**：任务状态变更通知

#### 3.6.3 数据模型
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

### 3.7 权限与安全控制

#### 3.7.1 功能描述
基于API级别的细粒度权限控制，支持内置角色和自定义权限分配。管理员拥有最高权限，可控制每个用户能访问哪些API接口。按漏洞挖掘部门架构管理用户（项目负责人、小组长、工程师等）。

#### 3.7.2 核心功能
- **API权限管理**：每个API接口单独管理权限，管理员可控制用户能访问哪些接口
- **内置角色**：预定义项目负责人、小组长、漏洞挖掘工程师等角色
- **角色权限分配**：每个角色包含一组API权限
- **用户角色分配**：将角色分配给用户，支持一个用户多个角色
- **用户权限继承**：用户权限 = 角色权限的并集 + 单独分配的权限
- **审批授权管理**：部门领导只能审批其直属下属的申请，审批权限由上级领导授予
- **审批权限继承**：小组长只能审批自己被授权范围内的资产访问，审批范围由项目负责人分配
- **跨团队审批处理**：当申请人申请的资产不在直属领导审批范围内时，系统自动路由到有权限的审批人
- **操作审计**：记录所有敏感操作日志

#### 3.7.3 审批流程规则

##### 3.7.3.1 审批范围类型
| 范围类型 | 说明 | 适用角色 |
|----------|------|----------|
| team | 审批团队内所有资产的访问申请 | 小组长 |
| asset_category | 审批特定资产分类的访问申请 | 项目负责人 |
| asset | 审批特定资产的访问申请 | 项目负责人/指定审批人 |

##### 3.7.3.2 审批路由规则
1. **申请人提交资产访问申请**
2. **系统查找审批人**：
   - 优先查找申请人的直属领导（小组长）
   - 检查直属领导是否有该资产的审批权限
   - 若无权限，向上查找项目负责人
   - 若仍无权限，路由到系统管理员

3. **审批流程图**：
```
申请人提交申请
    ↓
查找直属领导
    ├── 有审批权限 → 直属领导审批
    └── 无审批权限 → 查找项目负责人
                        ├── 有审批权限 → 项目负责人审批
                        └── 无审批权限 → 系统管理员审批
```

##### 3.7.3.3 审批超时处理
- 审批超时时间：72小时
- 超时后自动提醒审批人
- 超过7天未审批，自动升级到上级领导

#### 3.7.4 角色定义
| 角色 | 权限描述 |
|------|----------|
| 管理员 | 系统全权管理，可管理所有用户、角色、权限 |
| 项目负责人 | 管理整个项目，审批资产访问，查看所有数据 |
| 小组长 | 管理小组团队成员，审批小组内资产访问申请 |
| 漏洞挖掘工程师 | 挖掘漏洞，上传文档/文件，申请资产访问（新成员入职即为此角色） |

#### 3.7.5 数据模型
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
    phone: String (手机号，用于短信通知)
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
    created_by: UUID (创建者)
    created_at: DateTime
    updated_at: DateTime
}
```

---

### 3.8 知识沉淀与传承

#### 3.8.1 功能描述
建立知识库，记录挖掘经验和典型案例。

#### 3.8.2 核心功能
- **知识库分类**：按技术领域分类
- **案例管理**：典型漏洞案例记录
- **经验分享**：挖掘技巧和心得
- **新手引导**：新成员快速上手指南
- **问答系统**：常见问题解答

#### 3.8.3 数据模型
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

### 3.9 消息通知模块

#### 3.9.1 功能描述
实现系统消息通知功能，当用户的申请被审批、任务被分配、有新漏洞更新等事件发生时，发送通知给相关用户。

#### 3.9.2 核心功能
- **通知中心**：统一接收和查看所有通知
- **通知类型**：资产访问审批结果、任务分配、漏洞发现、漏洞状态变更、文档评论、系统公告
- **通知方式**：站内消息（必选）、邮件通知（可配置）、短信通知（可配置）、webhook推送（可选）
- **漏洞通知**：新漏洞发现时自动通知所有上级领导，也可指定发送给特定用户
- **通知订阅**：用户可订阅感兴趣的资产和漏洞动态
- **已读未读**：标记通知为已读，支持批量操作
- **历史记录**：保留通知历史，支持查询

#### 3.9.3 数据模型
```
Notification {
    id: UUID
    type: Enum (asset_access_approved/asset_access_rejected/task_assigned/vulnerability_discovered/vulnerability_updated/document_comment/system_announcement)
    title: String
    content: Text
    related_type: String (关联类型：asset/task/vulnerability/document)
    related_id: UUID (关联ID)
    created_at: DateTime
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
    is_read: Boolean (是否已读)
    read_at: DateTime (阅读时间)
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

### 3.10 仪表盘与统计模块

#### 3.10.1 功能描述
提供数据统计和可视化展示，帮助管理层了解团队工作进度、资产安全状况、漏洞挖掘成果等。

#### 3.10.2 核心功能
- **资产统计**：资产总数、按状态分布、按类型分布、按重要程度分布
- **漏洞统计**：漏洞总数、按状态分布、按危害等级分布、按资产分布
- **工作量统计**：各工程师提交漏洞数量、文档数量、资产处理数量
- **趋势分析**：漏洞发现趋势、工作量趋势、资产增长趋势
- **仪表盘配置**：用户可自定义展示的统计卡片和布局
- **导出报表**：支持导出统计报表（Excel/PDF）

#### 3.10.3 数据模型
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

## 4. 非功能性需求

### 4.1 性能需求

#### 4.1.1 响应时间要求

| 操作类型 | 响应时间要求 | 说明 |
|----------|--------------|------|
| 页面加载 | < 2秒 | 首页、列表页等常规页面 |
| 数据查询 | < 1秒 | 简单查询，如单表查询 |
| 复杂查询 | < 3秒 | 多表关联查询、统计查询 |
| 数据提交 | < 2秒 | 表单提交、数据保存 |
| 文件上传 | < 10秒 | 单文件 < 100MB |
| 文件下载 | < 5秒 | 开始下载响应时间 |
| 报表导出 | < 30秒 | Excel/PDF报表生成 |

#### 4.1.2 并发能力要求

| 指标 | 要求 | 说明 |
|------|------|------|
| 同时在线用户 | 200+ | 系统支持的最大在线用户数 |
| 并发请求数 | 100+ | 系统支持的同时请求数 |
| 数据库连接数 | 50+ | 数据库连接池大小 |
| API QPS | 500+ | 每秒处理请求数 |

#### 4.1.3 数据规模要求

| 数据类型 | 预估规模 | 说明 |
|----------|----------|------|
| 资产数量 | 10,000+ | 系统支持的资产总数 |
| 漏洞数量 | 100,000+ | 系统支持的漏洞总数 |
| 文档数量 | 50,000+ | 系统支持的文档总数 |
| 用户数量 | 500+ | 系统支持的用户总数 |
| 文件存储 | 10TB+ | 系统支持的文件存储总量 |

### 4.2 安全需求
- 数据传输加密 (HTTPS)
- 敏感数据加密存储
- 操作日志完整记录
- 定期安全审计

### 4.3 可用性需求

#### 4.3.1 系统可用性
- 系统可用性目标：99.9%（年度计算）
- 计划内停机时间：每月不超过4小时
- 故障恢复时间目标(RTO)：< 1小时
- 数据恢复点目标(RPO)：< 24小时

#### 4.3.2 数据备份策略

| 备份类型 | 备份频率 | 保留周期 | 存储位置 |
|----------|----------|----------|----------|
| 全量备份 | 每周日凌晨 | 4周 | 异地存储 |
| 增量备份 | 每日凌晨 | 7天 | 本地+异地 |
| 数据库日志备份 | 每小时 | 24小时 | 本地存储 |
| 配置文件备份 | 每次变更时 | 10个版本 | 版本控制 |

#### 4.3.3 数据恢复策略

| 故障类型 | 恢复方式 | 预计恢复时间 |
|----------|----------|--------------|
| 单表数据误删 | 从增量备份恢复 | < 30分钟 |
| 数据库损坏 | 从全量备份+增量备份恢复 | < 1小时 |
| 服务器故障 | 切换到备用服务器 | < 15分钟 |
| 数据中心故障 | 从异地备份恢复 | < 4小时 |

#### 4.3.4 备份验证
- 每月进行一次备份恢复演练
- 验证备份数据完整性和可用性
- 记录演练结果，持续优化恢复流程

### 4.4 可扩展性需求
- 支持多团队协作
- 支持横向扩展
- 模块化设计

### 4.5 数据迁移需求

#### 4.5.1 迁移场景
本系统为新建系统，暂无历史数据迁移需求。但需考虑以下场景：
- 从Excel/CSV等离线数据导入
- 从其他漏洞管理平台迁移数据
- 系统升级时的数据库迁移

#### 4.5.2 数据导入功能
| 数据类型 | 导入方式 | 说明 |
|----------|----------|------|
| 资产数据 | Excel/CSV导入 | 支持批量导入资产基本信息 |
| 用户数据 | Excel/CSV导入 | 支持批量创建用户账号 |
| 漏洞数据 | Excel/CSV导入 | 支持批量导入历史漏洞记录 |
| 文档数据 | 文件上传 | 支持批量上传文档文件 |

#### 4.5.3 数据导出功能
| 数据类型 | 导出格式 | 说明 |
|----------|----------|------|
| 资产数据 | Excel/CSV | 支持筛选条件导出 |
| 漏洞数据 | Excel/CSV/PDF | 支持漏洞报告导出 |
| 统计报表 | Excel/PDF | 支持仪表盘数据导出 |
| 审计日志 | CSV | 支持按时间范围导出 |

#### 4.5.4 数据迁移原则
1. **数据完整性**：迁移过程中确保数据不丢失、不损坏
2. **数据一致性**：迁移后数据关联关系保持正确
3. **可回滚性**：迁移失败时支持回滚到迁移前状态
4. **增量迁移**：支持增量数据迁移，减少停机时间

### 4.6 国际化需求

#### 4.6.1 语言支持
- 系统支持中文（简体）和英文两种语言
- 用户可在个人设置中切换语言偏好
- 默认语言为中文

#### 4.6.2 国际化范围

| 国际化内容 | 说明 |
|------------|------|
| 界面文字 | 所有菜单、按钮、提示信息等 |
| 错误消息 | 系统错误提示、验证错误信息 |
| 帮助文档 | 用户帮助文档、操作指南 |
| 邮件模板 | 系统发送的邮件通知模板 |
| 数据字典 | 下拉选项、状态枚举等 |

#### 4.6.3 技术实现
- 前端使用i18n国际化框架（如vue-i18n/react-i18next）
- 后端返回错误码，前端根据语言环境显示对应文本
- 数据库存储多语言内容（如资产名称、描述等）
- 语言包文件独立管理，便于翻译和维护

#### 4.6.4 不支持国际化的内容
- 用户输入的内容（如文档内容、漏洞描述等）
- 审计日志（记录原始操作内容）
- 系统配置项

### 4.7 接口需求

#### 4.7.1 外部系统接口

| 接口名称 | 用途 | 接口类型 | 必要性 |
|----------|------|----------|--------|
| 邮件服务接口 | 发送通知邮件 | SMTP/API | 必选 |
| 短信网关接口 | 发送短信通知 | HTTP API | 可选 |
| Webhook推送 | 第三方系统集成 | HTTP POST | 可选 |
| 文件存储接口 | 大文件存储 | S3兼容API | 预留 |

#### 4.7.2 邮件服务接口

**功能说明**：用于发送系统通知邮件，如审批结果、任务分配、漏洞更新等。

**接口规范**：
- 支持SMTP协议或邮件服务商API（如阿里云邮件推送、腾讯企业邮箱）
- 支持HTML格式邮件
- 支持邮件模板配置
- 支持发送状态追踪

**配置参数**：
```
SMTP_HOST: String (SMTP服务器地址)
SMTP_PORT: Integer (SMTP端口)
SMTP_USERNAME: String (用户名)
SMTP_PASSWORD: String (密码/授权码)
SMTP_FROM: String (发件人地址)
SMTP_FROM_NAME: String (发件人名称)
```

#### 4.7.3 短信网关接口

**功能说明**：用于发送紧急通知短信，如漏洞发现提醒、审批紧急通知等。

**接口规范**：
- 支持主流短信服务商API（如阿里云短信、腾讯云短信）
- 支持短信模板管理
- 支持发送状态回执
- 支持发送频率限制

**配置参数**：
```
SMS_PROVIDER: Enum (aliyun/tencent/other)
SMS_ACCESS_KEY: String (访问密钥)
SMS_SECRET_KEY: String (密钥)
SMS_SIGN_NAME: String (短信签名)
SMS_TEMPLATE_CODE: String (模板编码)
```

#### 4.7.4 Webhook推送接口

**功能说明**：用于向第三方系统推送事件通知，支持与外部系统集成。

**接口规范**：
- 支持HTTP POST请求
- 支持自定义请求头
- 支持请求签名验证
- 支持重试机制（最多3次）

**推送事件类型**：
| 事件类型 | 说明 |
|----------|------|
| vulnerability.discovered | 新漏洞发现 |
| vulnerability.status_changed | 漏洞状态变更 |
| asset.access_approved | 资产访问审批通过 |
| task.assigned | 任务分配 |
| document.commented | 文档评论 |

#### 4.7.5 文件存储接口

**功能说明**：用于存储大文件（如ISO镜像、VMDK磁盘文件等），预留对象存储接口。

**接口规范**：
- 兼容S3协议（MinIO/Ceph/阿里云OSS等）
- 支持分片上传（大文件）
- 支持断点续传
- 支持文件校验（MD5/SHA256）

**当前阶段**：使用本地文件存储，预留S3兼容接口以便未来扩展。

---

## 5. 系统架构

### 5.1 整体架构
采用前后端分离架构，后端提供RESTful API，前端采用单页应用(SPA)架构。

### 5.2 技术选型
- 后端: Java Spring Boot
- 前端: React / Vue.js
- 数据库: MariaDB + Redis
- 文件存储: 本地存储（当前阶段），预留对象存储(MinIO/S3)接口
- 容器化: Docker
- 构建工具: Maven

---

## 6. 附录

### 6.1 需求优先级矩阵

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

### 6.2 版本规划
- V1.0: 核心功能上线
- V2.0: 知识库模块
- V3.0: 高级分析和报表
