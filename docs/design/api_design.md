# API接口设计文档

## 1. 认证模块 API

### 1.1 用户登录

**请求**
```
POST /api/v1/auth/login
Content-Type: application/json
```

**请求体**
```json
{
    "username": "string",
    "password": "string"
}
```

**响应 200**
```json
{
    "access_token": "string",
    "refresh_token": "string",
    "token_type": "bearer",
    "expires_in": 3600,
    "user": {
        "id": "uuid",
        "username": "string",
        "email": "string",
        "full_name": "string",
        "role": "string"
    }
}
```

### 1.2 用户登出

**请求**
```
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "message": "登出成功"
}
```

### 1.3 刷新Token

**请求**
```
POST /api/v1/auth/refresh
Content-Type: application/json
```

**请求体**
```json
{
    "refresh_token": "string"
}
```

**响应 200**
```json
{
    "access_token": "string",
    "token_type": "bearer",
    "expires_in": 3600
}
```

### 1.4 获取当前用户信息

**请求**
```
GET /api/v1/auth/me
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "id": "uuid",
    "username": "string",
    "email": "string",
    "full_name": "string",
    "role": "string",
    "avatar": "string",
    "teams": [
        {
            "id": "uuid",
            "name": "string",
            "role": "string"
        }
    ],
    "created_at": "datetime",
    "last_login": "datetime"
}
```

---

## 2. 用户管理模块 API

### 2.1 获取用户列表

**请求**
```
GET /api/v1/users
Authorization: Bearer <token>
Query Parameters:
- page: int (default: 1)
- page_size: int (default: 20)
- role: string (optional)
- team_id: uuid (optional)
- search: string (optional)
```

**响应 200**
```json
{
    "total": 100,
    "page": 1,
    "page_size": 20,
    "items": [
        {
            "id": "uuid",
            "username": "string",
            "email": "string",
            "full_name": "string",
            "role": "string",
            "status": "active",
            "created_at": "datetime"
        }
    ]
}
```

### 2.2 创建用户

**请求**
```
POST /api/v1/users
Authorization: Bearer <token>
```

**请求体**
```json
{
    "username": "string",
    "email": "string",
    "password": "string",
    "full_name": "string",
    "role": "engineer"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "username": "string",
    "email": "string",
    "full_name": "string",
    "role": "string",
    "created_at": "datetime"
}
```

### 2.3 获取用户详情

**请求**
```
GET /api/v1/users/{id}
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "id": "uuid",
    "username": "string",
    "email": "string",
    "full_name": "string",
    "role": "string",
    "status": "string",
    "teams": [],
    "created_at": "datetime",
    "last_login": "datetime"
}
```

### 2.4 更新用户

**请求**
```
PUT /api/v1/users/{id}
Authorization: Bearer <token>
```

**请求体**
```json
{
    "full_name": "string",
    "email": "string",
    "role": "string"
}
```

**响应 200**
```json
{
    "id": "uuid",
    "username": "string",
    "email": "string",
    "full_name": "string",
    "role": "string",
    "updated_at": "datetime"
}
```

### 2.5 删除用户

**请求**
```
DELETE /api/v1/users/{id}
Authorization: Bearer <token>
```

**响应 204**

---

## 3. 团队管理模块 API

### 3.1 获取团队列表

**请求**
```
GET /api/v1/teams
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "total": 10,
    "items": [
        {
            "id": "uuid",
            "name": "string",
            "description": "string",
            "leader": {
                "id": "uuid",
                "username": "string",
                "full_name": "string"
            },
            "member_count": 5,
            "created_at": "datetime"
        }
    ]
}
```

### 3.2 创建团队

**请求**
```
POST /api/v1/teams
Authorization: Bearer <token>
```

**请求体**
```json
{
    "name": "string",
    "description": "string",
    "leader_id": "uuid"
}
```

### 3.3 获取团队详情

**请求**
```
GET /api/v1/teams/{id}
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "id": "uuid",
    "name": "string",
    "description": "string",
    "leader": {},
    "members": [
        {
            "id": "uuid",
            "username": "string",
            "full_name": "string",
            "role": "string",
            "status": "available"
        }
    ],
    "assets_count": 10,
    "created_at": "datetime"
}
```

### 3.4 添加团队成员

**请求**
```
POST /api/v1/teams/{id}/members
Authorization: Bearer <token>
```

**请求体**
```json
{
    "user_id": "uuid",
    "role": "engineer"
}
```

---

## 4. 资产管理模块 API

### 4.1 获取资产列表

**请求**
```
GET /api/v1/assets
Authorization: Bearer <token>
Query Parameters:
- page: int
- page_size: int
- team_id: uuid (optional)
- group_id: uuid (optional)
- target_type: string (optional)
- target_source: string (optional)
- status: string (optional: active/inactive/archived)
- search: string (optional)
```

**响应 200**
```json
{
    "total": 50,
    "page": 1,
    "page_size": 20,
    "items": [
        {
            "id": "uuid",
            "name": "string",
            "code": "string",
            "target_type": "string",
            "target_source": "string",
            "priority": "P0",
            "status": "active",
            "vendor_notified": false,
            "vendor_fixed": false,
            "groups": [
                {
                    "id": "uuid",
                    "name": "string"
                }
            ],
            "owner": {
                "id": "uuid",
                "username": "string"
            },
            "vulnerability_count": 15,
            "created_at": "datetime"
        }
    ]
}
```

### 4.2 创建资产

**请求**
```
POST /api/v1/assets
Authorization: Bearer <token>
```

**请求体**
```json
{
    "name": "string",
    "code": "string",
    "target_type": "string",
    "target_source": "string",
    "description": "string",
    "priority": "P1",
    "team_id": "uuid",
    "owner_id": "uuid"
}
```

### 4.3 获取资产详情

**请求**
```
GET /api/v1/assets/{id}
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "id": "uuid",
    "name": "string",
    "code": "string",
    "target_type": "string",
    "target_source": "string",
    "description": "string",
    "priority": "P0",
    "status": "active",
    "owner": {},
    "team": {},
    "vulnerabilities": [],
    "environments": [],
    "documents": [],
    "history": [],
    "created_at": "datetime",
    "updated_at": "datetime"
}
```

### 4.4 更新资产

**请求**
```
PUT /api/v1/assets/{id}
Authorization: Bearer <token>
```

**请求体**
```json
{
    "name": "string",
    "description": "string",
    "priority": "P0",
    "status": "active"
}
```

### 4.5 删除资产

**请求**
```
DELETE /api/v1/assets/{id}
Authorization: Bearer <token>
```

### 4.6 获取资产历史

**请求**
```
GET /api/v1/assets/{id}/history
Authorization: Bearer <token>
```

### 4.7 获取资产分组列表

**请求**
```
GET /api/v1/asset-groups
Authorization: Bearer <token>
```

### 4.8 创建资产分组

**请求**
```
POST /api/v1/asset-groups
Authorization: Bearer <token>
```

**请求体**
```json
{
    "name": "string",
    "type": "customer",
    "description": "string"
}
```

### 4.9 添加资产到分组

**请求**
```
POST /api/v1/asset-groups/{id}/assets
Authorization: Bearer <token>
```

**请求体**
```json
{
    "asset_id": "uuid"
}
```

### 4.10 从分组移除资产

**请求**
```
DELETE /api/v1/asset-groups/{group_id}/assets/{asset_id}
Authorization: Bearer <token>
```

---

## 5. 漏洞管理模块 API

### 5.1 获取漏洞列表

**请求**
```
GET /api/v1/vulnerabilities
Authorization: Bearer <token>
Query Parameters:
- page: int
- page_size: int
- asset_id: uuid (optional)
- severity: string (optional: critical/high/medium/low)
- status: string (optional)
- assignee_id: uuid (optional)
- discoverer_id: uuid (optional)
- search: string (optional)
```

**响应 200**
```json
{
    "total": 100,
    "page": 1,
    "page_size": 20,
    "items": [
        {
            "id": "uuid",
            "title": "string",
            "asset": {
                "id": "uuid",
                "name": "string"
            },
            "cve_id": "CVE-2024-1234",
            "type": "string",
            "severity": "critical",
            "cvss_score": 9.8,
            "status": "verified",
            "discoverer": {
                "id": "uuid",
                "username": "string"
            },
            "assigned_to": {
                "id": "uuid",
                "username": "string"
            },
            "created_at": "datetime"
        }
    ]
}
```

### 5.2 创建漏洞

**请求**
```
POST /api/v1/vulnerabilities
Authorization: Bearer <token>
```

**请求体**
```json
{
    "title": "string",
    "asset_id": "uuid",
    "type": "SQL注入",
    "severity": "high",
    "cvss_score": 7.5,
    "description": "string",
    "impact": "string",
    "assigned_to": "uuid"
}
```

### 5.3 获取漏洞详情

**请求**
```
GET /api/v1/vulnerabilities/{id}
Authorization: Bearer <token>
```

### 5.4 更新漏洞状态

**请求**
```
PUT /api/v1/vulnerabilities/{id}/status
Authorization: Bearer <token>
```

**请求体**
```json
{
    "status": "analyzing",
    "comment": "开始进行漏洞利用分析"
}
```

### 5.5 客户确认接收

**请求**
```
PUT /api/v1/vulnerabilities/{id}/customer-confirm
Authorization: Bearer <token>
```

**请求体**
```json
{
    "customer_confirmed": true,
    "comment": "客户确认接收漏洞"
}
```

### 5.6 更新利用阶段

**请求**
```
PUT /api/v1/vulnerabilities/{id}/exploit-stage
Authorization: Bearer <token>
```

**请求体**
```json
{
    "exploit_stage": "突破",
    "exploit_code": "利用代码内容"
}
```

### 5.7 提交出场测试

**请求**
```
POST /api/v1/vulnerabilities/{id}/field-test
Authorization: Bearer <token>
```

**请求体**
```json
{
    "field_test_status": "测试中",
    "test_code": "测试代码"
}
```

### 5.8 标记已交付

**请求**
```
PUT /api/v1/vulnerabilities/{id}/deliver
Authorization: Bearer <token>
```

**请求体**
```json
{
    "delivery_note": "交付备注"
}
```

### 5.9 标记厂商修复

**请求**
```
PUT /api/v1/vulnerabilities/{id}/vendor-fixed
Authorization: Bearer <token>
```

**请求体**
```json
{
    "vendor_fixed": true,
    "fixed_at": "datetime"
}
```

### 5.10 分配漏洞

**请求**
```
PUT /api/v1/vulnerabilities/{id}/assign
Authorization: Bearer <token>
```

**请求体**
```json
{
    "assignee_id": "uuid"
}
```

### 5.11 获取漏洞历史

**请求**
```
GET /api/v1/vulnerabilities/{id}/history
Authorization: Bearer <token>
```

### 5.12 上传漏洞附件

**请求**
```
POST /api/v1/vulnerabilities/{id}/attachments
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求表单**
```
file: binary
description: string
```

---

## 6. 环境管理模块 API

### 6.1 获取环境列表

**请求**
```
GET /api/v1/environments
Authorization: Bearer <token>
Query Parameters:
- asset_id: uuid (optional)
- status: string (optional)
```

### 6.2 创建环境

**请求**
```
POST /api/v1/environments
Authorization: Bearer <token>
```

**请求体**
```json
{
    "name": "string",
    "asset_id": "uuid",
    "template_id": "uuid",
    "config": {
        "cpu": "2",
        "memory": "4G",
        "disk": "50G"
    }
}
```

### 6.3 获取环境详情

**请求**
```
GET /api/v1/environments/{id}
Authorization: Bearer <token>
```

### 6.4 部署环境

**请求**
```
POST /api/v1/environments/{id}/deploy
Authorization: Bearer <token>
```

### 6.5 启动/停止环境

**请求**
```
POST /api/v1/environments/{id}/start
Authorization: Bearer <token>
```

```
POST /api/v1/environments/{id}/stop
Authorization: Bearer <token>
```

### 6.6 创建快照

**请求**
```
POST /api/v1/environments/{id}/snapshots
Authorization: Bearer <token>
```

**请求体**
```json
{
    "description": "string"
}
```

### 6.7 回滚到快照

**请求**
```
POST /api/v1/environments/{id}/snapshots/{snapshot_id}/rollback
Authorization: Bearer <token>
```

### 6.8 获取快照列表

**请求**
```
GET /api/v1/environments/{id}/snapshots
Authorization: Bearer <token>
```

### 6.9 获取环境模板列表

**请求**
```
GET /api/v1/environment-templates
Authorization: Bearer <token>
```

### 6.10 创建环境模板

**请求**
```
POST /api/v1/environment-templates
Authorization: Bearer <token>
```

**请求体**
```json
{
    "name": "string",
    "type": "docker",
    "config_schema": {},
    "deployment_script": "string"
}
```

---

## 7. 文档管理模块 API

### 7.1 获取文档列表

**请求**
```
GET /api/v1/documents
Authorization: Bearer <token>
Query Parameters:
- category: string (optional)
- asset_id: uuid (optional)
- author_id: uuid (optional)
- status: string (optional)
- search: string (optional)
```

### 7.2 创建文档

**请求**
```
POST /api/v1/documents
Authorization: Bearer <token>
```

**请求体**
```json
{
    "title": "string",
    "category": "guide",
    "content": "string",
    "asset_id": "uuid (optional)"
}
```

### 7.3 获取文档详情

**请求**
```
GET /api/v1/documents/{id}
Authorization: Bearer <token>
```

### 7.4 更新文档

**请求**
```
PUT /api/v1/documents/{id}
Authorization: Bearer <token>
```

**请求体**
```json
{
    "title": "string",
    "content": "string"
}
```

### 7.5 发布文档

**请求**
```
POST /api/v1/documents/{id}/publish
Authorization: Bearer <token>
```

### 7.6 获取文档版本历史

**请求**
```
GET /api/v1/documents/{id}/versions
Authorization: Bearer <token>
```

### 7.7 回滚到指定版本

**请求**
```
POST /api/v1/documents/{id}/versions/{version_id}/rollback
Authorization: Bearer <token>
```

---

## 8. 工具资源库模块 API

### 8.1 获取工具列表

**请求**
```
GET /api/v1/tools
Authorization: Bearer <token>
Query Parameters:
- category: string (optional)
- tags: string (optional, comma-separated)
- search: string (optional)
- status: string (optional)
```

### 8.2 上传工具

**请求**
```
POST /api/v1/tools
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求表单**
```
file: binary
name: string
category: string
tags: string
description: string
requirements: string
```

### 8.3 获取工具详情

**请求**
```
GET /api/v1/tools/{id}
Authorization: Bearer <token>
```

### 8.4 下载工具

**请求**
```
GET /api/v1/tools/{id}/download
Authorization: Bearer <token>
```

### 8.5 更新工具信息

**请求**
```
PUT /api/v1/tools/{id}
Authorization: Bearer <token>
```

### 8.6 获取工具版本列表

**请求**
```
GET /api/v1/tools/{id}/versions
Authorization: Bearer <token>
```

### 8.7 上传新版本

**请求**
```
POST /api/v1/tools/{id}/versions
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

---

## 9. 任务管理模块 API

### 9.1 获取任务列表

**请求**
```
GET /api/v1/tasks
Authorization: Bearer <token>
Query Parameters:
- status: string (optional)
- assignee_id: uuid (optional)
- type: string (optional)
- priority: string (optional)
- team_id: uuid (optional)
```

### 9.2 创建任务

**请求**
```
POST /api/v1/tasks
Authorization: Bearer <token>
```

**请求体**
```json
{
    "title": "string",
    "type": "vulnerability",
    "reference_id": "uuid",
    "assignee_id": "uuid",
    "priority": "high",
    "due_date": "datetime"
}
```

### 9.3 获取任务详情

**请求**
```
GET /api/v1/tasks/{id}
Authorization: Bearer <token>
```

### 9.4 更新任务状态

**请求**
```
PUT /api/v1/tasks/{id}/status
Authorization: Bearer <token>
```

**请求体**
```json
{
    "status": "in_progress"
}
```

### 9.5 分配任务

**请求**
```
PUT /api/v1/tasks/{id}/assign
Authorization: Bearer <token>
```

**请求体**
```json
{
    "assignee_id": "uuid"
}
```

### 9.6 添加任务评论

**请求**
```
POST /api/v1/tasks/{id}/comments
Authorization: Bearer <token>
```

**请求体**
```json
{
    "content": "string"
}
```

---

## 10. 知识库模块 API

### 10.1 获取知识文章列表

**请求**
```
GET /api/v1/knowledge
Authorization: Bearer <token>
Query Parameters:
- category: string (optional)
- tags: string (optional)
- search: string (optional)
- status: string (optional)
```

### 10.2 创建知识文章

**请求**
```
POST /api/v1/knowledge
Authorization: Bearer <token>
```

**请求体**
```json
{
    "title": "string",
    "category": "string",
    "content": "string",
    "tags": ["string"]
}
```

### 10.3 获取知识文章详情

**请求**
```
GET /api/v1/knowledge/{id}
Authorization: Bearer <token>
```

### 10.4 更新知识文章

**请求**
```
PUT /api/v1/knowledge/{id}
Authorization: Bearer <token>
```

### 10.5 添加知识评论

**请求**
```
POST /api/v1/knowledge/{id}/comments
Authorization: Bearer <token>
```

**请求体**
```json
{
    "content": "string"
}
```

---

## 11. 权限管理模块 API

### 11.1 获取角色列表

**请求**
```
GET /api/v1/roles
Authorization: Bearer <token>
```

### 11.2 创建角色

**请求**
```
POST /api/v1/roles
Authorization: Bearer <token>
```

**请求体**
```json
{
    "name": "string",
    "description": "string",
    "permissions": ["uuid"]
}
```

### 11.3 获取权限列表

**请求**
```
GET /api/v1/permissions
Authorization: Bearer <token>
```

### 11.4 分配角色

**请求**
```
POST /api/v1/users/{id}/roles
Authorization: Bearer <token>
```

**请求体**
```json
{
    "role_id": "uuid"
}
```

### 11.5 获取操作日志

**请求**
```
GET /api/v1/audit-logs
Authorization: Bearer <token>
Query Parameters:
- user_id: uuid (optional)
- action: string (optional)
- resource: string (optional)
- start_date: datetime (optional)
- end_date: datetime (optional)
```

---

## 12. 通用响应格式

### 成功响应

**200 OK**
```json
{
    "success": true,
    "data": {}
}
```

**201 Created**
```json
{
    "success": true,
    "data": {},
    "message": "创建成功"
}
```

### 错误响应

**400 Bad Request**
```json
{
    "success": false,
    "error": {
        "code": "VALIDATION_ERROR",
        "message": "请求参数验证失败",
        "details": [
            {
                "field": "email",
                "message": "邮箱格式不正确"
            }
        ]
    }
}
```

**401 Unauthorized**
```json
{
    "success": false,
    "error": {
        "code": "UNAUTHORIZED",
        "message": "未登录或登录已过期"
    }
}
```

**403 Forbidden**
```json
{
    "success": false,
    "error": {
        "code": "FORBIDDEN",
        "message": "没有操作权限"
    }
}
```

**404 Not Found**
```json
{
    "success": false,
    "error": {
        "code": "NOT_FOUND",
        "message": "资源不存在"
    }
}
```

**500 Internal Server Error**
```json
{
    "success": false,
    "error": {
        "code": "INTERNAL_ERROR",
        "message": "服务器内部错误"
    }
}
```

---

## 13. 分页响应格式

```json
{
    "success": true,
    "data": [],
    "pagination": {
        "total": 100,
        "page": 1,
        "page_size": 20,
        "total_pages": 5
    }
}
```
