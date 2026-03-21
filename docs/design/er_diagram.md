# 数据库ER图设计

## 1. 核心实体关系图

> **设计原则**：
> - 多对多关系：通过中间表管理（如 asset_group_relations）
> - 一对多/多对一：通过外键管理
> - 关联详情见各实体字段定义

```mermaid
erDiagram
    TEAMS ||--o{ ASSETS : "管理"
    TEAMS ||--o{ TASKS : "分配"
    ASSETS }o--o{ ASSET_GROUPS : "属于"
    ASSET_GROUPS ||--o{ ASSET_GROUP_RELATIONS : ""
    ASSETS ||--o{ ASSET_GROUP_RELATIONS : ""
    ASSETS ||--o{ ASSET_CLAIMS : "认领"
    ASSETS ||--o{ ASSET_FILES : "包含"
    ASSETS ||--o{ ASSET_ACCESS : "访问申请"
    ASSETS ||--o{ ASSET_APP_METADATA : "应用元数据"
    ASSET_APP_METADATA ||--o{ ASSET_APP_METADATA_HISTORY : "版本历史"
    ASSETS }o--o{ ASSET_TAGS : "标签"
    ASSET_FILES ||--o{ ASSET_FILE_VERSIONS : "版本"
    USERS ||--o{ ASSET_CLAIMS : "认领"
    USERS ||--o{ ASSET_FILES : "上传"
    USERS ||--o{ ASSET_ACCESS : "申请"
    
    USERS {
        uuid id PK
        string username
        string email
        string phone
        string password_hash
        string full_name
        string position
        string role
        timestamp created_at
        timestamp updated_at
    }
    
    TEAMS {
        uuid id PK
        string name
        string description
        uuid leader_id FK
        uuid created_by FK
        timestamp created_at
        timestamp updated_at
    }
    
    ASSETS {
        uuid id PK
        string name
        string code
        uuid team_id FK
        string target_type
        string target_source
        text description
        string priority
        string status
        uuid owner_id FK
        timestamp created_at
        timestamp updated_at
    }
    
    ASSET_GROUPS {
        uuid id PK
        string name
        string type
        text description
        timestamp created_at
    }
    
    ASSET_GROUP_RELATIONS {
        uuid id PK
        uuid asset_id FK
        uuid group_id FK
        timestamp created_at
    }
    
    ASSET_TAGS {
        uuid id PK
        uuid asset_id FK
        string tag
        uuid created_by FK
        timestamp created_at
    }
    
    ASSET_HISTORY {
        uuid id PK
        uuid asset_id FK
        string field_name
        string old_value
        string new_value
        uuid changed_by FK
        timestamp created_at
    }
    
    ASSET_CLAIMS {
        uuid id PK
        uuid asset_id FK
        uuid user_id FK
        string status
        timestamp claimed_at
        timestamp completed_at
        text note
        timestamp created_at
        timestamp updated_at
    }
    
    ASSET_ACCESS {
        uuid id PK
        uuid asset_id FK
        uuid user_id FK
        string status
        boolean can_view_documents
        boolean can_view_environments
        boolean can_view_files
        string allowed_file_types
        text request_note
        uuid reviewed_by FK
        timestamp reviewed_at
        text review_note
        timestamp created_at
        timestamp updated_at
    }
    
    ASSET_FILES {
        uuid id PK
        uuid asset_id FK
        string name
        string file_type
        string category
        text description
        bigint file_size
        string file_path
        string checksum
        int version
        string status
        uuid uploaded_by FK
        timestamp created_at
        timestamp updated_at
    }
    
    ASSET_FILE_VERSIONS {
        uuid id PK
        uuid file_id FK
        int version
        string file_path
        bigint file_size
        string checksum
        text changelog
        uuid uploaded_by FK
        timestamp created_at
    }
    
    ASSET_APP_METADATA {
        uuid id PK
        uuid asset_id FK
        uuid claimed_by FK
        int version
        string system_architecture
        string install_path
        string startup_script_path
        string config_file_path
        string startup_command
        string programming_language
        string framework
        string database_type
        string database_connection
        string admin_username
        string admin_password_encrypted
        string service_ports
        string api_endpoints
        text special_notes
        timestamp created_at
        timestamp updated_at
    }
    
    ASSET_APP_METADATA_HISTORY {
        uuid id PK
        uuid metadata_id FK
        int version
        uuid changed_by FK
        string change_reason
        string system_architecture
        string install_path
        string startup_script_path
        string config_file_path
        string startup_command
        string programming_language
        string framework
        string database_type
        string database_connection
        string admin_username
        string admin_password_encrypted
        string service_ports
        string api_endpoints
        text special_notes
        timestamp created_at
    }
```

## 2. 漏洞管理实体关系

> **设计说明**：一对多关系通过外键管理

```mermaid
erDiagram
    ASSETS ||--o{ VULNERABILITIES : "包含"
    USERS ||--o{ VULNERABILITIES : "发现"
    USERS ||--o{ VULNERABILITIES : "分配"
    VULNERABILITIES ||--o{ VULNERABILITY_HISTORY : "记录"
    VULNERABILITIES ||--o{ VULNERABILITY_ATTACHMENTS : "附件"
    VULNERABILITIES }o--o{ VULNERABILITY_TAGS : "标签"
    
    VULNERABILITIES {
        uuid id PK
        string title
        uuid asset_id FK
        string cve_id
        string type
        string severity
        float cvss_score
        string workflow_status
        string customer_status
        string disclosure_status
        timestamp customer_confirmed_at
        timestamp customer_rejected_at
        text customer_reject_reason
        string exploit_stage
        text exploit_code
        string field_test_status
        text field_test_result
        timestamp delivered_at
        text delivery_note
        timestamp disclosure_date
        string disclosure_source
        boolean vendor_fixed
        timestamp vendor_fixed_at
        string vendor_fix_version
        text description
        text detail
        text impact
        text poc_code
        uuid discoverer_id FK
        uuid assigned_to FK
        timestamp created_at
        timestamp updated_at
    }
    
    VULNERABILITY_HISTORY {
        uuid id PK
        uuid vulnerability_id FK
        string status_type
        string from_status
        string to_status
        uuid changed_by FK
        text comment
        timestamp created_at
    }
    
    VULNERABILITY_ATTACHMENTS {
        uuid id PK
        uuid vulnerability_id FK
        string file_name
        string file_path
        string file_type
        uuid uploaded_by FK
        timestamp created_at
    }
    
    VULNERABILITY_TAGS {
        uuid id PK
        uuid vulnerability_id FK
        string tag
        uuid created_by FK
        timestamp created_at
    }
```

## 3. 环境管理实体关系

> **说明**：本模块为靶场环境管理功能，相关表结构暂保持为空实现，待后续需求明确后再行开发。
> **设计说明**：一对多关系通过外键管理

```mermaid
erDiagram
    ASSETS ||--o{ ENVIRONMENTS : "一对多"
    
    ENVIRONMENTS {
        uuid id PK
        string name
        uuid asset_id FK
        uuid template_id FK
        json config
        string status
        int version
        uuid created_by FK
        timestamp created_at
        timestamp updated_at
    }
    
    ENVIRONMENT_SNAPSHOTS {
        uuid id PK
        uuid environment_id FK
        int version
        json config_snapshot
        text description
        uuid created_by FK
        timestamp created_at
    }
    
    ENVIRONMENT_TEMPLATES {
        uuid id PK
        string name
        string type
        json config_schema
        text deployment_script
        timestamp created_at
        timestamp updated_at
    }
```

## 4. 文档管理实体关系

> **设计说明**：一对多关系通过外键管理

```mermaid
erDiagram
    USERS ||--o{ DOCUMENTS : "创建"
    ASSETS ||--o{ DOCUMENTS : "关联"
    DOCUMENTS ||--o{ DOCUMENT_VERSIONS : "版本"
    DOCUMENTS ||--o{ DOCUMENT_ACCESS : "访问申请"
    DOCUMENTS }o--o{ DOCUMENT_TAGS : "标签"
    USERS ||--o{ DOCUMENT_ACCESS : "申请"
    
    DOCUMENTS {
        uuid id PK
        string title
        string category
        string summary
        text content
        uuid asset_id FK
        uuid author_id FK
        string status
        int version
        int word_count
        timestamp created_at
        timestamp updated_at
    }
    
    DOCUMENT_VERSIONS {
        uuid id PK
        uuid document_id FK
        int version
        text content
        text changelog
        uuid created_by FK
        timestamp created_at
    }
    
    DOCUMENT_ACCESS {
        uuid id PK
        uuid document_id FK
        uuid user_id FK
        string status
        text request_note
        uuid reviewed_by FK
        timestamp reviewed_at
        text review_note
        timestamp created_at
    }
    
    DOCUMENT_TAGS {
        uuid id PK
        uuid document_id FK
        string tag
        uuid created_by FK
        timestamp created_at
    }
```

## 5. 工具资源库实体关系

> **设计说明**：一对多关系通过外键管理

```mermaid
erDiagram
    USERS ||--o{ TOOLS : "一对多"
    
    TOOLS {
        uuid id PK
        string name
        string category
        text description
        string version
        string file_path
        text requirements
        uuid created_by FK
        int download_count
        string status
        timestamp created_at
        timestamp updated_at
    }
    
    TOOL_VERSIONS {
        uuid id PK
        uuid tool_id FK
        string version
        string file_path
        text changelog
        uuid created_by FK
        timestamp created_at
    }
    
    TOOL_TAGS {
        uuid id PK
        uuid tool_id FK
        string tag
    }
```

## 6. 任务管理实体关系

> **设计说明**：一对多关系通过外键管理

```mermaid
erDiagram
    USERS ||--o{ TASKS : "一对多"
    
    TASKS {
        uuid id PK
        string title
        string type
        uuid reference_id
        uuid assignee_id FK
        string priority
        string status
        timestamp due_date
        uuid created_by FK
        timestamp created_at
        timestamp updated_at
    }
    
    TASK_COMMENTS {
        uuid id PK
        uuid task_id FK
        uuid user_id FK
        text content
        timestamp created_at
    }
```

## 7. 知识库实体关系

> **设计说明**：一对多关系通过外键管理

```mermaid
erDiagram
    USERS ||--o{ KNOWLEDGE_ARTICLES : "一对多"
    
    KNOWLEDGE_ARTICLES {
        uuid id PK
        string title
        string category
        text content
        uuid author_id FK
        int view_count
        string status
        timestamp created_at
        timestamp updated_at
    }
    
    KNOWLEDGE_COMMENTS {
        uuid id PK
        uuid article_id FK
        uuid user_id FK
        text content
        timestamp created_at
    }
    
    KNOWLEDGE_TAGS {
        uuid id PK
        uuid article_id FK
        string tag
    }
```

## 8. 权限控制实体关系

> **设计说明**：多对多关系通过中间表管理，一对多通过外键管理

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : "多对多-中间表"
    ROLES ||--o{ ROLE_PERMISSIONS : "多对多-中间表"
    TEAMS }o--o{ TEAM_MEMBERS : "多对多-中间表"
    USERS ||--o{ AUDIT_LOGS : "一对多"
    USERS ||--o{ APPROVAL_SCOPES : "审批范围"
    
    ROLES {
        uuid id PK
        string name
        string code
        string description
        boolean is_system
        timestamp created_at
        timestamp updated_at
    }
    
    PERMISSIONS {
        uuid id PK
        string name
        string code
        string api_path
        string http_method
        string description
        timestamp created_at
    }
    
    USER_ROLES {
        uuid id PK
        uuid user_id FK
        uuid role_id FK
        uuid assigned_by FK
        timestamp assigned_at
    }
    
    USER_PERMISSIONS {
        uuid id PK
        uuid user_id FK
        uuid permission_id FK
        uuid granted_by FK
        timestamp granted_at
        timestamp expires_at
    }
    
    ROLE_PERMISSIONS {
        uuid id PK
        uuid role_id FK
        uuid permission_id FK
        timestamp created_at
    }
    
    TEAMS {
        uuid id PK
        string name
        uuid parent_id FK
        uuid leader_id FK
        uuid created_by FK
        string description
        timestamp created_at
        timestamp updated_at
    }
    
    TEAM_MEMBERS {
        uuid id PK
        uuid user_id FK
        uuid team_id FK
        boolean is_leader
        string status
        timestamp joined_at
    }
    
    AUDIT_LOGS {
        uuid id PK
        uuid user_id FK
        string action
        string resource
        string resource_id
        json old_value
        json new_value
        string ip_address
        timestamp created_at
    }
    
    APPROVAL_SCOPES {
        uuid id PK
        uuid approver_id FK
        string scope_type
        uuid scope_id FK
        uuid granted_by FK
        timestamp granted_at
        timestamp expires_at
    }
```

## 9. 消息通知实体关系

> **设计说明**：多对多关系通过中间表管理，一对多通过外键管理

```mermaid
erDiagram
    USERS ||--o{ NOTIFICATIONS : "一对多"
    NOTIFICATIONS ||--o{ NOTIFICATION_RECIPIENTS : "一对多"
    ASSETS }o--o{ ASSET_SUBSCRIPTIONS : "多对多-中间表"
    
    SYSTEM_ANNOUNCEMENTS {
        uuid id PK
        string title
        text content
        json target_roles
        string priority
        timestamp published_at
        timestamp expires_at
        timestamp created_at
    }
    
    NOTIFICATIONS {
        uuid id PK
        string type
        string title
        text content
        string related_type
        uuid related_id
        timestamp created_at
    }
    
    NOTIFICATION_SETTINGS {
        uuid id PK
        uuid user_id FK
        string type
        boolean enabled
        boolean notify_email
        boolean notify_sms
        boolean notify_webhook
    }
    
    NOTIFICATION_RECIPIENTS {
        uuid id PK
        uuid notification_id FK
        uuid user_id FK
        string recipient_type
        string channel
        string send_status
        timestamp sent_at
        string error_message
        boolean is_read
        timestamp read_at
        timestamp created_at
    }
    
    ASSET_SUBSCRIPTIONS {
        uuid id PK
        uuid user_id FK
        uuid asset_id FK
        timestamp created_at
    }
```

## 10. 仪表盘与统计实体关系

> **设计说明**：多对多关系通过中间表管理，一对多通过外键管理

```mermaid
erDiagram
    USERS ||--o{ DASHBOARD_CONFIGS : "一对多"
    APPROVAL_SCOPES }o--o{ APPROVAL_SCOPE_RELATIONS : "多对多-中间表"
    
    DASHBOARD_CONFIGS {
        uuid id PK
        uuid user_id FK
        json layout
        json cards
        timestamp created_at
        timestamp updated_at
    }
    
    APPROVAL_SCOPES {
        uuid id PK
        uuid approver_id FK
        string scope_type
        uuid granted_by FK
        timestamp granted_at
        timestamp expires_at
    }
    
    APPROVAL_SCOPE_RELATIONS {
        uuid id PK
        uuid approval_scope_id FK
        string scope_type
        uuid scope_id FK
        uuid created_at
    }
    
    STAT_CACHE {
        uuid id PK
        string stat_type
        string stat_key
        json stat_value
        timestamp calculated_at
    }
```

## 11. 第三方登录实体关系（预留）

> **设计说明**：一对多关系通过外键管理

```mermaid
erDiagram
    USERS ||--o{ OAUTH_BINDINGS : "一对多"
    
    OAUTH_BINDINGS {
        uuid id PK
        uuid user_id FK
        string provider
        string provider_user_id
        text access_token
        text refresh_token
        timestamp expires_at
        timestamp created_at
        timestamp updated_at
    }
```

## 12. 完整ER图概览

```mermaid
erDiagram
    USERS ||--o{ TEAM_MEMBERS : ""
    USERS ||--o{ ASSETS : ""
    USERS ||--o{ VULNERABILITIES : ""
    USERS ||--o{ ENVIRONMENTS : ""
    USERS ||--o{ DOCUMENTS : ""
    USERS ||--o{ TOOLS : ""
    USERS ||--o{ TASKS : ""
    USERS ||--o{ KNOWLEDGE_ARTICLES : ""
    USERS ||--o{ AUDIT_LOGS : ""
    USERS ||--o{ ASSET_CLAIMS : ""
    USERS ||--o{ ASSET_FILES : ""
    
    TEAMS ||--o{ ASSETS : ""
    TEAMS ||--o{ TASKS : ""
    
    ASSETS ||--o{ VULNERABILITIES : ""
    ASSETS ||--o{ ENVIRONMENTS : ""
    ASSETS ||--o{ DOCUMENTS : ""
    ASSETS ||--o{ ASSET_CLAIMS : ""
    ASSETS ||--o{ ASSET_ACCESS : ""
    ASSETS ||--o{ ASSET_FILES : ""
    ASSETS ||--o{ ASSET_APP_METADATA : ""
    ASSET_APP_METADATA ||--o{ ASSET_APP_METADATA_HISTORY : ""
    ASSET_FILES ||--o{ ASSET_FILE_VERSIONS : ""
    USERS ||--o{ ASSET_ACCESS : ""
    
    ENVIRONMENT_TEMPLATES ||--o{ ENVIRONMENTS : ""
    
    VULNERABILITIES ||--o{ VULNERABILITY_HISTORY : ""
    
    DOCUMENTS ||--o{ DOCUMENT_VERSIONS : ""
    DOCUMENTS ||--o{ DOCUMENT_ACCESS : ""
    USERS ||--o{ DOCUMENT_ACCESS : ""
    
    TOOLS ||--o{ TOOL_VERSIONS : ""
    
    TASKS ||--o{ TASK_COMMENTS : ""
    
    KNOWLEDGE_ARTICLES ||--o{ KNOWLEDGE_COMMENTS : ""
    
    ROLES ||--o{ USER_ROLES : ""
    ROLES ||--o{ ROLE_PERMISSIONS : ""
    PERMISSIONS ||--o{ ROLE_PERMISSIONS : ""
```
