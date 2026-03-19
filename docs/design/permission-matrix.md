# 接口权限矩阵

本文档定义了系统中各内置角色对应的 API 接口权限。

## 1. 角色定义

| 角色代码 | 角色名称 | 描述 |
|----------|----------|------|
| admin | 系统管理员 | 系统全权管理，可管理所有用户、角色、权限 |
| project_leader | 项目负责人 | 管理整个项目，审批资产访问，查看所有数据 |
| team_leader | 小组长 | 管理小组团队成员，审批小组内资产访问申请 |
| vulnerability_engineer | 漏洞挖掘工程师 | 挖掘漏洞，上传文档/文件，申请资产访问 |
| member | 普通成员 | 受限操作，需申请资产访问权限 |

## 2. 接口权限矩阵

### 2.1 认证模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| POST /api/v1/auth/login | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/auth/logout | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/auth/refresh | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/auth/me | ✅ | ✅ | ✅ | ✅ | ✅ |

### 2.2 用户管理模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/users | ✅ | ✅ | ✅ | ❌ | ❌ |
| GET /api/v1/users/{id} | ✅ | ✅ | ✅ | ❌ | ❌ |
| POST /api/v1/users | ✅ | ❌ | ❌ | ❌ | ❌ |
| PUT /api/v1/users/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |
| DELETE /api/v1/users/{id} | ✅ | ❌ | ❌ | ❌ | ❌ |

### 2.3 团队管理模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/teams | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/teams/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/teams | ✅ | ✅ | ❌ | ❌ | ❌ |
| PUT /api/v1/teams/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |
| DELETE /api/v1/teams/{id} | ✅ | ❌ | ❌ | ❌ | ❌ |
| GET /api/v1/teams/{id}/members | ✅ | ✅ | ✅ | ❌ | ❌ |

### 2.4 资产管理模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/assets | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/assets/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/assets | ✅ | ✅ | ✅ | ❌ | ❌ |
| PUT /api/v1/assets/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |
| DELETE /api/v1/assets/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |
| POST /api/v1/assets/{id}/claim | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/assets/{id}/access-request | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/my/authorized-assets | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/my/claimed-assets | ✅ | ✅ | ✅ | ✅ | ✅ |

### 2.5 漏洞管理模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/vulnerabilities | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/vulnerabilities/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/vulnerabilities | ✅ | ✅ | ✅ | ✅ | ❌ |
| PUT /api/v1/vulnerabilities/{id} | ✅ | ✅ | ✅ | ✅ | ❌ |
| PATCH /api/v1/vulnerabilities/{id}/status | ✅ | ✅ | ✅ | ✅ | ❌ |
| DELETE /api/v1/vulnerabilities/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |
| GET /api/v1/vulnerabilities/export | ✅ | ✅ | ✅ | ❌ | ❌ |

### 2.6 环境管理模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/environments | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/environments/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/environments | ✅ | ✅ | ❌ | ❌ | ❌ |
| PUT /api/v1/environments/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |
| DELETE /api/v1/environments/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |
| POST /api/v1/environments/{id}/deploy | ✅ | ✅ | ✅ | ❌ | ❌ |
| POST /api/v1/environments/{id}/snapshot | ✅ | ✅ | ✅ | ✅ | ❌ |

### 2.7 文档管理模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/documents | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/documents/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/documents | ✅ | ✅ | ✅ | ✅ | ✅ |
| PUT /api/v1/documents/{id} | ✅ | ✅ | ✅ | ✅ | ❌ |
| DELETE /api/v1/documents/{id} | ✅ | ✅ | ✅ | ✅ | ❌ |
| POST /api/v1/documents/{id}/access-request | ✅ | ✅ | ✅ | ✅ | ✅ |

### 2.8 工具资源库模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/tools | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/tools/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/tools | ✅ | ✅ | ✅ | ✅ | ❌ |
| PUT /api/v1/tools/{id} | ✅ | ✅ | ✅ | ❌ | ❌ |
| DELETE /api/v1/tools/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |
| GET /api/v1/tools/{id}/download | ✅ | ✅ | ✅ | ✅ | ✅ |

### 2.9 任务管理模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/tasks | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/tasks/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/tasks | ✅ | ✅ | ✅ | ❌ | ❌ |
| PUT /api/v1/tasks/{id} | ✅ | ✅ | ✅ | ✅ | ❌ |
| DELETE /api/v1/tasks/{id} | ✅ | ✅ | ❌ | ❌ | ❌ |

### 2.10 知识库模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/knowledge/articles | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/knowledge/articles/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/knowledge/articles | ✅ | ✅ | ✅ | ✅ | ✅ |
| PUT /api/v1/knowledge/articles/{id} | ✅ | ✅ | ✅ | ✅ | ❌ |
| DELETE /api/v1/knowledge/articles/{id} | ✅ | ✅ | ✅ | ❌ | ❌ |

### 2.11 权限管理模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/permissions | ✅ | ✅ | ❌ | ❌ | ❌ |
| POST /api/v1/permissions | ✅ | ❌ | ❌ | ❌ | ❌ |
| GET /api/v1/roles | ✅ | ✅ | ❌ | ❌ | ❌ |
| POST /api/v1/roles | ✅ | ❌ | ❌ | ❌ | ❌ |
| PUT /api/v1/roles/{id}/permissions | ✅ | ❌ | ❌ | ❌ | ❌ |
| DELETE /api/v1/roles/{id} | ✅ | ❌ | ❌ | ❌ | ❌ |
| GET /api/v1/users/{id}/roles | ✅ | ✅ | ❌ | ❌ | ❌ |
| POST /api/v1/users/{id}/roles | ✅ | ❌ | ❌ | ❌ | ❌ |
| DELETE /api/v1/users/{id}/roles/{roleId} | ✅ | ❌ | ❌ | ❌ | ❌ |
| GET /api/v1/approvals/pending | ✅ | ✅ | ✅ | ❌ | ❌ |
| PUT /api/v1/approvals/{id} | ✅ | ✅ | ✅ | ❌ | ❌ |

### 2.12 消息通知模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/notifications | ✅ | ✅ | ✅ | ✅ | ✅ |
| PUT /api/v1/notifications/{id}/read | ✅ | ✅ | ✅ | ✅ | ✅ |
| PUT /api/v1/notifications/batch-read | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/notifications/unread-count | ✅ | ✅ | ✅ | ✅ | ✅ |
| DELETE /api/v1/notifications/{id} | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/notifications/settings | ✅ | ✅ | ✅ | ✅ | ✅ |
| PUT /api/v1/notifications/settings | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/announcements | ✅ | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/notifications/subscribe | ✅ | ✅ | ✅ | ✅ | ✅ |
| DELETE /api/v1/notifications/subscribe/{assetId} | ✅ | ✅ | ✅ | ✅ | ✅ |

### 2.13 仪表盘统计模块

| 接口 | admin | project_leader | team_leader | vulnerability_engineer | member |
|------|-------|----------------|-------------|----------------------|--------|
| GET /api/v1/dashboard/assets | ✅ | ✅ | ✅ | ❌ | ❌ |
| GET /api/v1/dashboard/vulnerabilities | ✅ | ✅ | ✅ | ❌ | ❌ |
| GET /api/v1/dashboard/workload | ✅ | ✅ | ✅ | ❌ | ❌ |
| GET /api/v1/dashboard/trends | ✅ | ✅ | ✅ | ❌ | ❌ |
| GET /api/v1/dashboard/my | ✅ | ✅ | ✅ | ✅ | ✅ |
| PUT /api/v1/dashboard/my | ✅ | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/dashboard/export | ✅ | ✅ | ❌ | ❌ | ❌ |

## 3. 特殊权限说明

### 3.1 审批权限继承

- **项目负责人 (project_leader)**：可审批所有资产的访问申请
- **小组长 (team_leader)**：只能审批自己被授权范围内的资产访问申请，审批范围由项目负责人分配
- 审批范围通过 `approval_scopes` 表管理

### 3.2 数据可见性

| 角色 | 资产可见范围 | 漏洞可见范围 | 文档可见范围 |
|------|------------|------------|-------------|
| admin | 全部 | 全部 | 全部 |
| project_leader | 全部 | 全部 | 全部 |
| team_leader | 本团队 | 本团队 | 本团队 |
| vulnerability_engineer | 已授权资产 | 已授权资产的漏洞 | 已授权文档 |
| member | 已授权资产 | 已授权资产的漏洞 | 已授权文档 |

---

| 版本 | 日期 | 修改内容 |
|------|------|----------|
| 1.0 | 2024-01-01 | 初始版本 |
