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

### 1.5 第三方登录（预留）

> **说明**：以下接口为预留接口，暂不实现，留待后续扩展。

### 1.5.1 企业微信登录

**请求**
```
POST /api/v1/auth/oauth/wechat
Content-Type: application/json
```

**请求体**
```json
{
    "code": "string"
}
```

### 1.5.2 LDAP 登录

**请求**
```
POST /api/v1/auth/oauth/ldap
Content-Type: application/json
```

**请求体**
```json
{
    "username": "string",
    "password": "string"
}
```

### 1.5.3 绑定第三方账号

**请求**
```
POST /api/v1/auth/oauth/bind
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**
```json
{
    "provider": "wechat/ldap",
    "code": "string"
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

### 4.11 获取资产的认领列表

**请求**
```
GET /api/v1/assets/{id}/claims
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "total": 5,
    "items": [
        {
            "id": "uuid",
            "asset_id": "uuid",
            "user": {
                "id": "uuid",
                "username": "string",
                "full_name": "string"
            },
            "status": "approved",
            "claimed_at": "datetime",
            "completed_at": null,
            "note": "string",
            "created_at": "datetime"
        }
    ]
}
```

### 4.12 申请访问资产

**请求**
```
POST /api/v1/assets/{id}/access-request
Authorization: Bearer <token>
```

**请求体**
```json
{
    "access_scope": {
        "documents": true,
        "files": ["系统镜像", "安装包"],
        "environments": true
    },
    "request_note": "需要进行漏洞挖掘，需要访问相关文档和文件"
}
```

> **说明**：漏洞挖掘工程师申请访问某个资产，可指定需要访问的内容范围（文档、文件类型、环境等）

**响应 201**
```json
{
    "id": "uuid",
    "asset_id": "uuid",
    "user_id": "uuid",
    "status": "pending",
    "access_scope": {
        "documents": true,
        "files": ["系统镜像", "安装包"],
        "environments": true
    },
    "request_note": "需要进行漏洞挖掘，需要访问相关文档和文件",
    "created_at": "datetime"
}
```

### 4.13 获取我发起的资产访问申请

**请求**
```
GET /api/v1/my/access-requests
Authorization: Bearer <token>
Query Parameters:
- status: string (optional: pending/approved/rejected)
- asset_id: uuid (optional)
```

**响应 200**
```json
{
    "total": 5,
    "items": [
        {
            "id": "uuid",
            "asset_id": "uuid",
            "asset_name": "Web应用系统A",
            "status": "pending",
            "access_scope": {
                "documents": true,
                "files": ["系统镜像"],
                "environments": false
            },
            "created_at": "datetime"
        }
    ]
}
```

### 4.14 获取待审批的资产访问申请（部门领导）

**请求**
```
GET /api/v1/my/access-approval-requests
Authorization: Bearer <token>
Query Parameters:
- status: string (optional: pending)
```

> **说明**：返回其他用户申请访问资产的列表，部门领导可审批

### 4.15 审批资产访问申请（部门领导）

**请求**
```
POST /api/v1/access-requests/{id}/review
Authorization: Bearer <token>
```

**请求体**
```json
{
    "action": "approve",
    "access_scope": {
        "documents": true,
        "files": ["系统镜像", "安装包", "配置文件"],
        "environments": true
    },
    "review_note": "已授权访问，请注意保密"
}
```

> **说明**：部门领导审批访问申请，可调整授权的访问范围

**响应 200**
```json
{
    "id": "uuid",
    "asset_id": "uuid",
    "user_id": "uuid",
    "status": "approved",
    "access_scope": {
        "documents": true,
        "files": ["系统镜像", "安装包", "配置文件"],
        "environments": true
    },
    "reviewed_by": "uuid",
    "reviewed_at": "datetime",
    "review_note": "已授权访问，请注意保密"
}
```

### 4.16 获取我已获授权的资产

**请求**
```
GET /api/v1/my/authorized-assets
Authorization: Bearer <token>
```

> **说明**：返回当前用户已获授权可以访问的资产列表

### 4.17 认领资产

**请求**
```
POST /api/v1/assets/{id}/claims
Authorization: Bearer <token>
```

**请求体**
```json
{
    "note": "string"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "asset_id": "uuid",
    "user_id": "uuid",
    "status": "approved",
    "claimed_at": "datetime",
    "note": "string",
    "created_at": "datetime"
}
```

> **说明**：资产认领为自动批准，无需人工审核

### 4.18 获取我的认领列表

**请求**
```
GET /api/v1/my/claims
Authorization: Bearer <token>
Query Parameters:
- status: string (optional: pending/approved/rejected/completed/abandoned)
- page: int
- page_size: int
```

**响应 200**
```json
{
    "total": 10,
    "items": [
        {
            "id": "uuid",
            "asset": {
                "id": "uuid",
                "name": "string",
                "code": "string"
            },
            "status": "approved",
            "claimed_at": "datetime",
            "completed_at": null,
            "note": "string"
        }
    ]
}
```

### 4.19 更新认领状态（工程师完成/放弃认领）

**请求**
```
PUT /api/v1/claims/{id}
Authorization: Bearer <token>
```

**请求体**
```json
{
    "status": "completed",
    "note": "string"
}
```

### 4.20 获取资产的文件列表

**请求**
```
GET /api/v1/assets/{id}/files
Authorization: Bearer <token>
Query Parameters:
- category: string (optional: 系统镜像/磁盘文件/安装包/提取程序/配置文件/其他)
- status: string (optional: active/archived)
```

> **说明**：返回所有用户上传的资产文件列表（不含文件内容）。如需下载文件，需发起申请。

**响应 200**
```json
{
    "total": 10,
    "items": [
        {
            "id": "uuid",
            "name": "windows_server_2019.iso",
            "file_type": "iso",
            "category": "系统镜像",
            "description": "Windows Server 2019 镜像",
            "file_size": 5368709120,
            "checksum": "sha256:abc123...",
            "version": 2,
            "status": "active",
            "uploaded_by": {
                "id": "uuid",
                "username": "string"
            },
            "created_at": "datetime"
        }
    ]
}
```

### 4.21 上传资产文件

**请求**
```
POST /api/v1/assets/{id}/files
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求体**
```
- file: binary (文件)
- category: string (系统镜像/磁盘文件/安装包/提取程序/配置文件/其他)
- description: string (文件描述)
- version_note: string (版本说明，首次上传时可选)
```

**响应 201**
```json
{
    "id": "uuid",
    "name": "windows_server_2019.iso",
    "file_type": "iso",
    "category": "系统镜像",
    "file_size": 5368709120,
    "checksum": "sha256:abc123...",
    "version": 1,
    "status": "active",
    "created_at": "datetime"
}
```

### 4.22 获取资产文件详情

**请求**
```
GET /api/v1/asset-files/{id}
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "id": "uuid",
    "asset_id": "uuid",
    "name": "windows_server_2019.iso",
    "file_type": "iso",
    "category": "系统镜像",
    "description": "Windows Server 2019 镜像",
    "file_size": 5368709120,
    "file_path": "/storage/asset-files/xxx.iso",
    "checksum": "sha256:abc123...",
    "version": 2,
    "status": "active",
    "uploaded_by": {
        "id": "uuid",
        "username": "string"
    },
    "versions": [
        {
            "version": 2,
            "file_size": 5368709120,
            "checksum": "sha256:abc123...",
            "changelog": "更新到SP1版本",
            "created_at": "datetime"
        },
        {
            "version": 1,
            "file_size": 5368709120,
            "checksum": "sha256:def456...",
            "changelog": "初始版本",
            "created_at": "datetime"
        }
    ],
    "created_at": "datetime",
    "updated_at": "datetime"
}
```

### 4.23 上传新版本文件

**请求**
```
POST /api/v1/asset-files/{id}/versions
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求体**
```
- file: binary (文件)
- changelog: string (版本变更说明)
```

**响应 201**
```json
{
    "id": "uuid",
    "version": 3,
    "file_size": 5368709120,
    "checksum": "sha256:new123...",
    "changelog": "修复安装问题",
    "created_at": "datetime"
}
```

### 4.24 下载资产文件

**请求**
```
GET /api/v1/asset-files/{id}/download
Authorization: Bearer <token>
```

> **说明**：已授权的用户可直接下载文件内容。未经授权的用户需先申请下载权限。

**响应 200**
```
二进制文件流
```

### 4.25 删除资产文件

**请求**
```
DELETE /api/v1/asset-files/{id}
Authorization: Bearer <token>
```

**响应 204**

### 4.26 获取资产的标签列表

**请求**
```
GET /api/v1/assets/{id}/tags
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "items": [
        {
            "id": "uuid",
            "tag": "高危",
            "created_by": {
                "id": "uuid",
                "username": "string"
            },
            "created_at": "datetime"
        }
    ]
}
```

### 4.27 添加标签到资产

**请求**
```
POST /api/v1/assets/{id}/tags
Authorization: Bearer <token>
```

**请求体**
```json
{
    "tag": "高危"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "asset_id": "uuid",
    "tag": "高危",
    "created_by": "uuid",
    "created_at": "datetime"
}
```

### 4.28 移除资产的标签

**请求**
```
DELETE /api/v1/assets/{asset_id}/tags/{tag_id}
Authorization: Bearer <token>
```

**响应 204**

### 4.29 获取资产的应用元数据

> **说明**：获取资产的应用元数据信息（当前版本），只有认领人才能查看和编辑

**请求**
```
GET /api/v1/assets/{asset_id}/metadata
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "id": "uuid",
    "asset_id": "uuid",
    "claimed_by": {
        "id": "uuid",
        "username": "string"
    },
    "version": 2,
    "system_architecture": "x86_64",
    "install_path": "/opt/app",
    "startup_script_path": "/opt/app/start.sh",
    "config_file_path": "/opt/app/config/application.yml",
    "startup_command": "./start.sh",
    "programming_language": "Java",
    "framework": "Spring Boot",
    "database_type": "MySQL",
    "database_connection": "jdbc:mysql://localhost:3306/db",
    "admin_username": "admin",
    "admin_password_encrypted": "***",
    "service_ports": "8080,3306",
    "api_endpoints": "/api/v1/users,/api/v1/products",
    "special_notes": "需要先启动MySQL服务",
    "created_at": "datetime",
    "updated_at": "datetime"
}
```

**响应 404**（未填写元数据）
```json
{
    "success": false,
    "code": "METADATA_NOT_FOUND",
    "message": "该资产尚未填写应用元数据"
}
```

### 4.30 创建资产应用元数据（首次）

> **说明**：认领资产后首次填写应用元数据

**请求**
```
POST /api/v1/assets/{asset_id}/metadata
Authorization: Bearer <token>
```

**请求体**
```json
{
    "system_architecture": "x86_64",
    "install_path": "/opt/app",
    "startup_script_path": "/opt/app/start.sh",
    "config_file_path": "/opt/app/config/application.yml",
    "startup_command": "./start.sh",
    "programming_language": "Java",
    "framework": "Spring Boot",
    "database_type": "MySQL",
    "database_connection": "jdbc:mysql://localhost:3306/db",
    "admin_username": "admin",
    "admin_password": "password123",
    "service_ports": "8080,3306",
    "api_endpoints": "/api/v1/users,/api/v1/products",
    "special_notes": "需要先启动MySQL服务"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "asset_id": "uuid",
    "claimed_by": {
        "id": "uuid",
        "username": "string"
    },
    "version": 1,
    "system_architecture": "x86_64",
    "install_path": "/opt/app",
    "startup_script_path": "/opt/app/start.sh",
    "config_file_path": "/opt/app/config/application.yml",
    "startup_command": "./start.sh",
    "programming_language": "Java",
    "framework": "Spring Boot",
    "database_type": "MySQL",
    "database_connection": "jdbc:mysql://localhost:3306/db",
    "admin_username": "admin",
    "admin_password_encrypted": "***",
    "service_ports": "8080,3306",
    "api_endpoints": "/api/v1/users,/api/v1/products",
    "special_notes": "需要先启动MySQL服务",
    "created_at": "datetime",
    "updated_at": "datetime"
}
```

**响应 403**（非认领人）
```json
{
    "success": false,
    "code": "NOT_CLAIMED",
    "message": "只有认领人才能填写应用元数据，请先认领该资产"
}
```

### 4.31 更新资产应用元数据

> **说明**：更新应用元数据，每次更新必须填写修改理由。系统会保留所有历史版本

**请求**
```
PUT /api/v1/assets/{asset_id}/metadata
Authorization: Bearer <token>
```

**请求体**
```json
{
    "change_reason": "发现新的配置信息，更新数据库连接字符串",
    "system_architecture": "x86_64",
    "install_path": "/opt/app",
    "startup_script_path": "/opt/app/start.sh",
    "config_file_path": "/opt/app/config/application.yml",
    "startup_command": "./start.sh",
    "programming_language": "Java",
    "framework": "Spring Boot",
    "database_type": "MySQL",
    "database_connection": "jdbc:mysql://db-server:3306/db",
    "admin_username": "admin",
    "admin_password": "newpassword",
    "service_ports": "8080,3306,6379",
    "api_endpoints": "/api/v1/users,/api/v1/products,/api/v1/orders",
    "special_notes": "需要先启动MySQL服务"
}
```

**响应 200**
```json
{
    "id": "uuid",
    "asset_id": "uuid",
    "claimed_by": {
        "id": "uuid",
        "username": "string"
    },
    "version": 2,
    "system_architecture": "x86_64",
    "install_path": "/opt/app",
    "startup_script_path": "/opt/app/start.sh",
    "config_file_path": "/opt/app/config/application.yml",
    "startup_command": "./start.sh",
    "programming_language": "Java",
    "framework": "Spring Boot",
    "database_type": "MySQL",
    "database_connection": "jdbc:mysql://db-server:3306/db",
    "admin_username": "admin",
    "admin_password_encrypted": "***",
    "service_ports": "8080,3306,6379",
    "api_endpoints": "/api/v1/users,/api/v1/products,/api/v1/orders",
    "special_notes": "需要先启动MySQL服务",
    "created_at": "datetime",
    "updated_at": "datetime"
}
```

**响应 400**（未填写修改理由）
```json
{
    "success": false,
    "code": "CHANGE_REASON_REQUIRED",
    "message": "修改应用元数据时必须填写修改理由"
}
```

### 4.32 获取应用元数据历史版本

> **说明**：获取应用元数据的所有历史版本，点击"查看更多"可展开查看每个版本的修改理由

**请求**
```
GET /api/v1/assets/{asset_id}/metadata/history
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "current_version": 2,
    "items": [
        {
            "version": 2,
            "changed_by": {
                "id": "uuid",
                "username": "string"
            },
            "change_reason": "发现新的配置信息，更新数据库连接字符串",
            "created_at": "2024-02-15T10:30:00Z",
            "summary": "修改了database_connection, service_ports, api_endpoints"
        },
        {
            "version": 1,
            "changed_by": {
                "id": "uuid",
                "username": "string"
            },
            "change_reason": "首次填写应用元数据",
            "created_at": "2024-01-10T09:00:00Z",
            "summary": "初始版本"
        }
    ]
}
```

### 4.33 获取应用元数据指定版本详情

> **说明**：查看某个历史版本的完整元数据信息

**请求**
```
GET /api/v1/assets/{asset_id}/metadata/{version}
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "version": 1,
    "changed_by": {
        "id": "uuid",
        "username": "string"
    },
    "change_reason": "首次填写应用元数据",
    "system_architecture": "x86_64",
    "install_path": "/opt/app",
    "startup_script_path": "/opt/app/start.sh",
    "config_file_path": "/opt/app/config/application.yml",
    "startup_command": "./start.sh",
    "programming_language": "Java",
    "framework": "Spring Boot",
    "database_type": "MySQL",
    "database_connection": "jdbc:mysql://localhost:3306/db",
    "admin_username": "admin",
    "admin_password_encrypted": "***",
    "service_ports": "8080,3306",
    "api_endpoints": "/api/v1/users,/api/v1/products",
    "special_notes": "需要先启动MySQL服务",
    "created_at": "2024-01-10T09:00:00Z"
}
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
    "detail": "string (漏洞详情，Markdown格式)",
    "impact": "string",
    "poc_code": "string",
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

### 5.13 获取漏洞的标签列表

**请求**
```
GET /api/v1/vulnerabilities/{id}/tags
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "items": [
        {
            "id": "uuid",
            "tag": "严重",
            "created_by": {
                "id": "uuid",
                "username": "string"
            },
            "created_at": "datetime"
        }
    ]
}
```

### 5.14 添加标签到漏洞

**请求**
```
POST /api/v1/vulnerabilities/{id}/tags
Authorization: Bearer <token>
```

**请求体**
```json
{
    "tag": "严重"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "vulnerability_id": "uuid",
    "tag": "严重",
    "created_by": "uuid",
    "created_at": "datetime"
}
```

### 5.15 移除漏洞的标签

**请求**
```
DELETE /api/v1/vulnerabilities/{vulnerability_id}/tags/{tag_id}
Authorization: Bearer <token>
```

**响应 204**

---

## 6. 环境管理模块 API

> **说明**：本模块为靶场环境管理功能，相关 API 暂保持空接口实现，待后续需求明确后再行开发。

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

### 7.1 获取文档列表（查看所有用户文档）

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
- page: int
- page_size: int
```

> **说明**：返回所有用户的文档列表（仅包含标题、作者等基本信息，不含内容），用户可查看其他用户有哪些文档。如需查看具体内容，需发起申请。

### 7.2 创建文档（在线创建）

**请求**
```
POST /api/v1/documents
Authorization: Bearer <token>
```

> **说明**：在线创建新文档，支持富文本内容

**请求体**
```json
{
    "title": "Web应用安全测试指南",
    "category": "guide",
    "content": "<p>这里是文档内容，支持HTML富文本...</p>",
    "asset_id": "uuid (optional)"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "title": "Web应用安全测试指南",
    "category": "guide",
    "content": "<p>这里是文档内容，支持HTML富文本...</p>",
    "asset_id": "uuid",
    "author_id": "uuid",
    "status": "draft",
    "version": 1,
    "word_count": 50,
    "created_at": "datetime",
    "updated_at": "datetime"
}
```

### 7.3 获取文档详情

**请求**
```
GET /api/v1/documents/{id}
Authorization: Bearer <token>
```

> **说明**：获取文档详细内容，包含富文本内容

### 7.4 更新文档（在线编辑）

**请求**
```
PUT /api/v1/documents/{id}
Authorization: Bearer <token>
```

> **说明**：在线编辑文档内容，自动创建新版本

**请求体**
```json
{
    "title": "Web应用安全测试指南（修订版）",
    "content": "<p>更新后的文档内容...</p>",
    "changelog": "补充了SQL注入测试方法"
}
```

**响应 200**
```json
{
    "id": "uuid",
    "title": "Web应用安全测试指南（修订版）",
    "category": "guide",
    "content": "<p>更新后的文档内容...</p>",
    "status": "draft",
    "version": 2,
    "word_count": 100,
    "updated_at": "datetime"
}
```

### 7.5 删除文档

**请求**
```
DELETE /api/v1/documents/{id}
Authorization: Bearer <token>
```

> **说明**：删除自己创建的文档（草稿或已发布）

**响应 204**

### 7.6 发布文档

**请求**
```
POST /api/v1/documents/{id}/publish
Authorization: Bearer <token>
```

> **说明**：将文档从草稿状态发布为已发布状态

### 7.7 取消发布文档

**请求**
```
POST /api/v1/documents/{id}/unpublish
Authorization: Bearer <token>
```

> **说明**：将已发布文档撤回到草稿状态

### 7.8 获取文档版本历史

**请求**
```
GET /api/v1/documents/{id}/versions
Authorization: Bearer <token>
```

> **说明**：获取文档的所有历史版本

### 7.9 回滚到指定版本

**请求**
```
POST /api/v1/documents/{id}/versions/{version_id}/rollback
Authorization: Bearer <token>
```

> **说明**：将文档恢复到指定历史版本

### 7.10 获取指定版本详情

**请求**
```
GET /api/v1/documents/{id}/versions/{version_id}
Authorization: Bearer <token>
```

> **说明**：获取文档某个历史版本的详细内容

### 7.11 获取我的文档列表（我的文档空间）

**请求**
```
GET /api/v1/my/documents
Authorization: Bearer <token>
Query Parameters:
- asset_id: uuid (optional)
- category: string (optional)
- status: string (optional)
- search: string (optional)
- page: int
- page_size: int
```

> **说明**：返回当前用户创建的文档，文档按用户ID目录结构隔离存储

### 7.12 搜索文档内容

**请求**
```
GET /api/v1/documents/search
Authorization: Bearer <token>
Query Parameters:
- q: string (搜索关键词)
- category: string (optional)
- asset_id: uuid (optional)
```

> **说明**：全文检索文档内容

### 7.13 获取文档的标签列表

**请求**
```
GET /api/v1/documents/{id}/tags
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "items": [
        {
            "id": "uuid",
            "tag": "重要",
            "created_by": {
                "id": "uuid",
                "username": "string"
            },
            "created_at": "datetime"
        }
    ]
}
```

### 7.14 添加标签到文档

**请求**
```
POST /api/v1/documents/{id}/tags
Authorization: Bearer <token>
```

**请求体**
```json
{
    "tag": "重要"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "document_id": "uuid",
    "tag": "重要",
    "created_by": "uuid",
    "created_at": "datetime"
}
```

### 7.15 移除文档的标签

**请求**
```
DELETE /api/v1/documents/{document_id}/tags/{tag_id}
Authorization: Bearer <token>
```

**响应 204**

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

### 8.8 获取工具的标签列表

**请求**
```
GET /api/v1/tools/{id}/tags
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "items": [
        {
            "id": "uuid",
            "tag": "string",
            "created_by": {
                "id": "uuid",
                "username": "string"
            },
            "created_at": "datetime"
        }
    ]
}
```

### 8.9 添加标签到工具

**请求**
```
POST /api/v1/tools/{id}/tags
Authorization: Bearer <token>
```

**请求体**
```json
{
    "tag": "string"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "tool_id": "uuid",
    "tag": "string",
    "created_by": "uuid",
    "created_at": "datetime"
}
```

### 8.10 移除工具的标签

**请求**
```
DELETE /api/v1/tools/{tool_id}/tags/{tag_id}
Authorization: Bearer <token>
```

**响应 204**

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

### 10.6 获取知识文章的标签列表

**请求**
```
GET /api/v1/knowledge/{id}/tags
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "items": [
        {
            "id": "uuid",
            "tag": "string",
            "created_by": {
                "id": "uuid",
                "username": "string"
            },
            "created_at": "datetime"
        }
    ]
}
```

### 10.7 添加标签到知识文章

**请求**
```
POST /api/v1/knowledge/{id}/tags
Authorization: Bearer <token>
```

**请求体**
```json
{
    "tag": "string"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "article_id": "uuid",
    "tag": "string",
    "created_by": "uuid",
    "created_at": "datetime"
}
```

### 10.8 移除知识文章的标签

**请求**
```
DELETE /api/v1/knowledge/{article_id}/tags/{tag_id}
Authorization: Bearer <token>
```

**响应 204**

---

## 11. 权限管理模块 API

### 11.1 获取权限列表

**请求**
```
GET /api/v1/permissions
Authorization: Bearer <token>
Query Parameters:
- page: int
- page_size: int
- search: string (optional, 搜索权限名称或代码)
```

> **说明**：获取所有API权限定义，支持分页和搜索

**响应 200**
```json
{
    "total": 100,
    "items": [
        {
            "id": "uuid",
            "name": "资产查看",
            "code": "asset:read",
            "api_path": "/api/v1/assets",
            "http_method": "GET",
            "description": "查看资产列表和详情",
            "created_at": "datetime"
        }
    ]
}
```

### 11.2 创建权限

**请求**
```
POST /api/v1/permissions
Authorization: Bearer <token>
```

> **说明**：管理员创建新的API权限定义

**请求体**
```json
{
    "name": "漏洞提交",
    "code": "vulnerability:create",
    "api_path": "/api/v1/vulnerabilities",
    "http_method": "POST",
    "description": "提交新发现的漏洞"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "name": "漏洞提交",
    "code": "vulnerability:create",
    "api_path": "/api/v1/vulnerabilities",
    "http_method": "POST",
    "description": "提交新发现的漏洞",
    "created_at": "datetime"
}
```

### 11.3 获取角色列表

**请求**
```
GET /api/v1/roles
Authorization: Bearer <token>
Query Parameters:
- include_permissions: boolean (是否包含权限列表)
```

> **说明**：获取所有角色，可选择包含权限信息

**响应 200**
```json
{
    "total": 5,
    "items": [
        {
            "id": "uuid",
            "name": "漏洞挖掘工程师",
            "code": "engineer",
            "description": "负责漏洞挖掘工作",
            "is_system": true,
            "permissions": [
                {
                    "id": "uuid",
                    "name": "资产查看",
                    "code": "asset:read"
                }
            ],
            "created_at": "datetime"
        }
    ]
}
```

### 11.4 创建角色

**请求**
```
POST /api/v1/roles
Authorization: Bearer <token>
```

> **说明**：创建新角色并分配权限

**请求体**
```json
{
    "name": "小组长",
    "code": "team_leader",
    "description": "管理小组团队成员",
    "permission_ids": ["uuid1", "uuid2"]
}
```

**响应 201**
```json
{
    "id": "uuid",
    "name": "小组长",
    "code": "team_leader",
    "description": "管理小组团队成员",
    "is_system": false,
    "created_at": "datetime"
}
```

### 11.5 更新角色权限

**请求**
```
PUT /api/v1/roles/{id}
Authorization: Bearer <token>
```

> **说明**：修改角色的权限集合

**请求体**
```json
{
    "name": "小组长",
    "description": "管理小组团队成员",
    "permission_ids": ["uuid1", "uuid2", "uuid3"]
}
```

**响应 200**
```json
{
    "id": "uuid",
    "name": "小组长",
    "code": "team_leader",
    "description": "管理小组团队成员",
    "permissions": [...],
    "updated_at": "datetime"
}
```

### 11.6 删除角色

**请求**
```
DELETE /api/v1/roles/{id}
Authorization: Bearer <token>
```

> **说明**：删除自定义角色，系统内置角色不可删除

**响应 204**

### 11.7 获取用户角色

**请求**
```
GET /api/v1/users/{id}/roles
Authorization: Bearer <token>
```

> **说明**：获取指定用户被分配的角色

### 11.8 分配角色给用户

**请求**
```
POST /api/v1/users/{id}/roles
Authorization: Bearer <token>
```

> **说明**：将角色分配给用户，支持一个用户多个角色

**请求体**
```json
{
    "role_id": "uuid"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "user_id": "uuid",
    "role_id": "uuid",
    "role_name": "漏洞挖掘工程师",
    "assigned_by": "uuid",
    "assigned_at": "datetime"
}
```

### 11.9 撤销用户角色

**请求**
```
DELETE /api/v1/users/{id}/roles/{role_id}
Authorization: Bearer <token>
```

> **说明**：撤销用户的某个角色

**响应 204**

### 11.10 直接分配权限给用户

**请求**
```
POST /api/v1/users/{id}/permissions
Authorization: Bearer <token>
```

> **说明**：管理员直接分配特定权限给用户（可设置过期时间）

**请求体**
```json
{
    "permission_id": "uuid",
    "expires_at": "2024-12-31T23:59:59Z"
}
```

**响应 201**
```json
{
    "id": "uuid",
    "user_id": "uuid",
    "permission_id": "uuid",
    "granted_by": "uuid",
    "granted_at": "datetime",
    "expires_at": "datetime"
}
```

### 11.11 撤销用户权限

**请求**
```
DELETE /api/v1/users/{id}/permissions/{permission_id}
Authorization: Bearer <token>
```

> **说明**：撤销直接分配给用户的权限

**响应 204**

### 11.12 获取当前用户权限

**请求**
```
GET /api/v1/my/permissions
Authorization: Bearer <token>
```

> **说明**：获取当前登录用户的权限列表（角色权限+直接分配权限的并集）

### 11.13 获取团队成员列表

**请求**
```
GET /api/v1/teams/{id}/members
Authorization: Bearer <token>
```

> **说明**：获取团队/部门下的所有成员

### 11.14 创建团队

**请求**
```
POST /api/v1/teams
Authorization: Bearer <token>
```

> **说明**：创建新的团队/部门

**请求体**
```json
{
    "name": "Web安全组",
    "parent_id": "uuid (可选，上级团队)",
    "leader_id": "uuid (团队负责人)",
    "description": "负责Web应用安全测试"
}
```

### 11.15 更新团队信息

**请求**
```
PUT /api/v1/teams/{id}
Authorization: Bearer <token>
```

### 11.16 删除团队

**请求**
```
DELETE /api/v1/teams/{id}
Authorization: Bearer <token>
```

### 11.17 获取操作日志

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
- page: int
- page_size: int
```

> **说明**：查询操作审计日志

---

## 12. 消息通知模块 API

### 12.1 获取通知列表

**请求**
```
GET /api/v1/notifications
Authorization: Bearer <token>
Query Parameters:
- type: string (optional) - 通知类型
- is_read: boolean (optional) - 已读/未读
- page: int
- page_size: int
```

**响应 200**
```json
{
    "success": true,
    "data": {
        "items": [
            {
                "id": "uuid",
                "type": "asset_access_approved",
                "title": "资产访问申请已通过",
                "content": "您申请的资产访问已通过审批",
                "related_type": "asset",
                "related_id": "uuid",
                "is_read": false,
                "created_at": "datetime"
            }
        ],
        "total": 100,
        "page": 1,
        "page_size": 20
    }
}
```

### 12.2 标记通知为已读

**请求**
```
PUT /api/v1/notifications/{id}/read
Authorization: Bearer <token>
```

### 12.3 批量标记通知为已读

**请求**
```
PUT /api/v1/notifications/batch-read
Authorization: Bearer <token>
```

**请求体**
```json
{
    "notification_ids": ["uuid1", "uuid2"]
}
```

### 12.4 获取未读通知数量

**请求**
```
GET /api/v1/notifications/unread-count
Authorization: Bearer <token>
```

### 12.5 删除通知

**请求**
```
DELETE /api/v1/notifications/{id}
Authorization: Bearer <token>
```

### 12.6 获取通知设置

**请求**
```
GET /api/v1/notifications/settings
Authorization: Bearer <token>
```

### 12.7 更新通知设置

**请求**
```
PUT /api/v1/notifications/settings
Authorization: Bearer <token>
```

**请求体**
```json
{
    "settings": [
        {
            "type": "asset_access_approved",
            "enabled": true,
            "notify_email": true,
            "notify_sms": true,
            "notify_webhook": false
        }
    ]
}
```

### 12.8 获取系统公告列表

**请求**
```
GET /api/v1/announcements
Authorization: Bearer <token>
Query Parameters:
- page: int
- page_size: int
```

### 12.9 订阅资产动态

**请求**
```
POST /api/v1/notifications/subscribe
Authorization: Bearer <token>
```

**请求体**
```json
{
    "asset_id": "uuid"
}
```

### 12.10 取消订阅资产动态

**请求**
```
DELETE /api/v1/notifications/subscribe/{asset_id}
Authorization: Bearer <token>
```

### 12.11 发送漏洞通知（内部接口）

> **说明**：漏洞发现时自动调用，通知所有上级领导

**请求**
```
POST /api/v1/notifications/vulnerability-discovered
Authorization: Bearer <token>
```

**请求体**
```json
{
    "vulnerability_id": "uuid",
    "title": "新漏洞发现：SQL注入",
    "content": "在资产XXX中发现SQL注入漏洞",
    "asset_id": "uuid",
    "severity": "high",
    "notify_leaders": true,
    "specific_user_ids": ["uuid1", "uuid2"]
}
```

**响应 200**
```json
{
    "success": true,
    "data": {
        "notification_id": "uuid",
        "recipient_count": 5,
        "recipients": [
            {
                "user_id": "uuid",
                "channel": "email",
                "send_status": "sent"
            },
            {
                "user_id": "uuid",
                "channel": "sms",
                "send_status": "pending"
            }
        ]
    }
}
```

### 12.12 获取通知发送记录

> **说明**：查看某条通知的发送记录（发送给哪些人、通过什么渠道）

**请求**
```
GET /api/v1/notifications/{notification_id}/recipients
Authorization: Bearer <token>
```

**响应 200**
```json
{
    "items": [
        {
            "id": "uuid",
            "user_id": "uuid",
            "username": "string",
            "recipient_type": "leader",
            "channel": "email",
            "send_status": "sent",
            "sent_at": "datetime"
        },
        {
            "id": "uuid",
            "user_id": "uuid",
            "username": "string",
            "recipient_type": "specific_user",
            "channel": "sms",
            "send_status": "failed",
            "error_message": "手机号格式错误",
            "sent_at": null
        }
    ]
}
```

---

## 13. 仪表盘与统计模块 API

### 13.1 获取资产统计

**请求**
```
GET /api/v1/dashboard/assets
Authorization: Bearer <token>
Query Parameters:
- date_from: datetime (optional)
- date_to: datetime (optional)
```

**响应 200**
```json
{
    "success": true,
    "data": {
        "total": 100,
        "by_status": {
            "available": 50,
            "in_progress": 30,
            "completed": 20
        },
        "by_type": {
            "web": 40,
            "mobile": 30,
            "api": 30
        },
        "by_priority": {
            "critical": 10,
            "high": 30,
            "medium": 40,
            "low": 20
        }
    }
}
```

### 13.2 获取漏洞统计

**请求**
```
GET /api/v1/dashboard/vulnerabilities
Authorization: Bearer <token>
Query Parameters:
- date_from: datetime (optional)
- date_to: datetime (optional)
```

**响应 200**
```json
{
    "success": true,
    "data": {
        "total": 200,
        "by_status": {
            "new": 50,
            "verified": 80,
            "fixed": 50,
            "accepted": 20
        },
        "by_severity": {
            "critical": 10,
            "high": 50,
            "medium": 80,
            "low": 60
        },
        "by_asset": [
            {
                "asset_id": "uuid",
                "asset_name": "string",
                "count": 20
            }
        ]
    }
}
```

### 13.3 获取工作量统计

**请求**
```
GET /api/v1/dashboard/workload
Authorization: Bearer <token>
Query Parameters:
- date_from: datetime (optional)
- date_to: datetime (optional)
- group_by: string (day/week/month)
```

**响应 200**
```json
{
    "success": true,
    "data": {
        "by_user": [
            {
                "user_id": "uuid",
                "username": "string",
                "vulnerabilities_count": 15,
                "documents_count": 5,
                "assets_count": 3
            }
        ],
        "trend": [
            {
                "date": "2024-01-01",
                "vulnerabilities": 10,
                "documents": 3,
                "assets": 1
            }
        ]
    }
}
```

### 13.4 获取趋势分析

**请求**
```
GET /api/v1/dashboard/trends
Authorization: Bearer <token>
Query Parameters:
- type: string (vulnerability/asset/workload)
- date_from: datetime
- date_to: datetime
- interval: string (day/week/month)
```

### 13.5 获取我的仪表盘

**请求**
```
GET /api/v1/dashboard/my
Authorization: Bearer <token>
```

### 13.6 保存仪表盘配置

**请求**
```
PUT /api/v1/dashboard/my
Authorization: Bearer <token>
```

**请求体**
```json
{
    "layout": {
        "columns": 2,
        "cards": [
            {"type": "asset_stats", "position": 0},
            {"type": "vulnerability_stats", "position": 1},
            {"type": "my_tasks", "position": 2}
        ]
    }
}
```

### 13.7 导出统计报表

**请求**
```
GET /api/v1/dashboard/export
Authorization: Bearer <token>
Query Parameters:
- type: string (assets/vulnerabilities/workload)
- format: string (excel/pdf)
- date_from: datetime (optional)
- date_to: datetime (optional)
```

**响应 200**
```json
{
    "success": true,
    "data": {
        "download_url": "/api/v1/files/export/xxx.xlsx"
    }
}
```

---

## 14. 通用响应格式

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

## 15. 分页响应格式

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
