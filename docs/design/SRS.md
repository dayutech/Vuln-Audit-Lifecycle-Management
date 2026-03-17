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
- **目标分组**：支持多对多关系，一个目标可属于多个分组（如按客户、按项目等）
- **目标来源**：区分客户委托目标和自主发现目标
- **重要程度标记**：P0/P1/P2/P3 四级优先级
- **历史记录**：记录资产的所有变更历史
- **关联漏洞**：管理与资产相关的漏洞列表

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
    status: Enum (active/inactive/archived)
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
集中存储各类挖掘文档，支持版本控制和权限管理。

#### 2.3.2 核心功能
- **文档分类**：环境搭建指南、漏洞分析报告、利用思路、技术笔记
- **版本控制**：支持文档的多版本管理
- **权限管理**：文档级别的访问控制
- **全文检索**：支持文档内容搜索
- **协作编辑**：支持多人协同编辑

#### 2.3.3 数据模型
```
Document {
    id: UUID
    title: String
    category: Enum (guide/report/notes|tech/case)
    content: Text
    asset_id: UUID (可选，关联资产)
    author_id: UUID
    status: Enum (draft/published/archived)
    version: Integer
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
- **漏洞记录**：详细记录漏洞信息
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
    status: Enum (discovered|verified|analyzing|analyzed|customer_confirmed|exploit_development|field_testing|delivered|closed|fixed)
    
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
    description: Text
    impact: Text
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
    tags: Array(String)
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
基于角色的访问控制机制，确保敏感信息安全。

#### 2.7.2 核心功能
- **角色管理**：定义不同角色及其权限
- **权限分配**：用户-角色-权限映射
- **数据权限**：细粒度的数据访问控制
- **操作审计**：记录所有敏感操作
- **数据备份**：定期自动备份
- **灾难恢复**：快速恢复能力

#### 2.7.3 角色定义
| 角色 | 权限描述 |
|------|----------|
| 管理员 | 系统全权管理 |
| 团队负责人 | 团队全部权限 |
| 漏洞工程师 | 漏洞挖掘相关操作 |
| 审计人员 | 只读访问 |
| 新成员 | 受限操作 |

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
    tags: Array(String)
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
- 后端: Python FastAPI / Node.js Express
- 前端: React / Vue.js
- 数据库: PostgreSQL + Redis
- 文件存储: 本地存储 / 对象存储
- 容器化: Docker

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
