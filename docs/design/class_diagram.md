# 类图与对象关系设计

## 1. 核心领域模型类图

### 1.1 用户与权限类图

```mermaid
classDiagram
    class User {
        +UUID id
        +string username
        +string email
        +string password_hash
        +string full_name
        +string avatar
        +datetime created_at
        +datetime updated_at
        +create() User
        +update() User
        +delete() bool
        +check_password() bool
    }

    class Role {
        +UUID id
        +string name
        +string description
        +datetime created_at
        +create() Role
        +update() Role
    }

    class Permission {
        +UUID id
        +string name
        +string resource
        +string action
        +string description
    }

    class Team {
        +UUID id
        +string name
        +string description
        +UUID leader_id
        +datetime created_at
        +create() Team
        +add_member() bool
        +remove_member() bool
    }

    class TeamMember {
        +UUID id
        +UUID user_id
        +UUID team_id
        +string role
        +string status
        +datetime joined_at
    }

    User "1" --> "*" Role : has
    Role "1" --> "*" Permission : contains
    Team "1" --> "*" TeamMember : has
    User "1" --> "*" TeamMember : belongs_to
```

### 1.2 资产管理类图

```mermaid
classDiagram
    class Asset {
        +UUID id
        +string name
        +string code
        +UUID team_id
        +string target_type
        +string target_source
        +string description
        +string priority
        +string status
        +UUID owner_id
        +boolean vendor_notified
        +boolean vendor_fixed
        +datetime fixed_at
        +datetime created_at
        +datetime updated_at
        +create() Asset
        +update() Asset
        +delete() bool
        +add_vulnerability() Vulnerability
        +add_to_group() bool
        +remove_from_group() bool
    }

    class AssetGroup {
        +UUID id
        +string name
        +string type
        +string description
        +datetime created_at
        +create() AssetGroup
        +add_asset() bool
        +remove_asset() bool
    }

    class AssetGroupRelation {
        +UUID asset_id
        +UUID group_id
    }

    Asset "1" --> "*" AssetGroupRelation : has
    AssetGroup "1" --> "*" AssetGroupRelation : has

    class AssetHistory {
        +UUID id
        +UUID asset_id
        +string field_name
        +string old_value
        +string new_value
        +UUID changed_by
        +datetime created_at
        +create() AssetHistory
    }

    class Vulnerability {
        +UUID id
        +string title
        +UUID asset_id
        +string cve_id
        +string type
        +string severity
        +float cvss_score
        +string status
        +boolean vendor_notified
        +boolean vendor_fixed
        +datetime fixed_at
        +text description
        +text impact
        +UUID discoverer_id
        +UUID assigned_to
        +datetime created_at
        +datetime updated_at
        +create() Vulnerability
        +update_status() bool
        +assign() bool
        +mark_vendor_fixed() bool
    }

    class VulnerabilityHistory {
        +UUID id
        +UUID vulnerability_id
        +string from_status
        +string to_status
        +UUID changed_by
        +text comment
        +datetime created_at
        +create() VulnerabilityHistory
    }

    Asset "1" --> "*" Vulnerability : contains
    Asset "1" --> "*" AssetHistory : has
    Vulnerability "1" --> "*" VulnerabilityHistory : tracks
```

### 1.3 环境管理类图

```mermaid
classDiagram
    class Environment {
        +UUID id
        +string name
        +UUID asset_id
        +UUID template_id
        +JSON config
        +string status
        +int version
        +UUID created_by
        +datetime created_at
        +datetime updated_at
        +create() Environment
        +start() bool
        +stop() bool
        +snapshot() EnvironmentSnapshot
        +rollback() bool
    }

    class EnvironmentTemplate {
        +UUID id
        +string name
        +string type
        +JSON config_schema
        +text deployment_script
        +datetime created_at
        +datetime updated_at
        +create() Environment
    }

    class EnvironmentSnapshot {
        +UUID id
        +UUID environment_id
        +int version
        +JSON config_snapshot
        +text description
        +UUID created_by
        +datetime created_at
        +create() EnvironmentSnapshot
        +restore() bool
    }

    class Asset {
        +UUID id
        +string name
    }

    EnvironmentTemplate "1" --> "*" Environment : creates
    Asset "1" --> "*" Environment : hosts
    Environment "1" --> "*" EnvironmentSnapshot : has
```

### 1.4 文档管理类图

```mermaid
classDiagram
    class Document {
        +UUID id
        +string title
        +string category
        +text content
        +UUID asset_id
        +UUID author_id
        +string status
        +int version
        +datetime created_at
        +datetime updated_at
        +create() Document
        +update() Document
        +publish() bool
        +archive() bool
    }

    class DocumentVersion {
        +UUID id
        +UUID document_id
        +int version
        +text content
        +text changelog
        +UUID created_by
        +datetime created_at
        +create() DocumentVersion
        +rollback() bool
    }

    class DocumentPermission {
        +UUID id
        +UUID document_id
        +UUID user_id
        +string permission
    }

    class User {
        +UUID id
        +string username
    }

    Document "1" --> "*" DocumentVersion : has
    Document "1" --> "*" DocumentPermission : controls
    User "1" --> "*" Document : authors
```

### 1.5 工具资源库类图

```mermaid
classDiagram
    class Tool {
        +UUID id
        +string name
        +string category
        +text description
        +string version
        +string file_path
        +text requirements
        +UUID created_by
        +int download_count
        +string status
        +datetime created_at
        +datetime updated_at
        +create() Tool
        +update() Tool
        +download() bool
        +deprecate() bool
    }

    class ToolVersion {
        +UUID id
        +UUID tool_id
        +string version
        +string file_path
        +text changelog
        +UUID created_by
        +datetime created_at
        +create() ToolVersion
    }

    class ToolTag {
        +UUID id
        +UUID tool_id
        +string tag
    }

    class ToolDownload {
        +UUID id
        +UUID tool_id
        +UUID user_id
        +datetime downloaded_at
        +record() ToolDownload
    }

    Tool "1" --> "*" ToolVersion : has
    Tool "1" --> "*" ToolTag : has
    Tool "1" --> "*" ToolDownload : tracks
```

### 1.6 任务管理类图

```mermaid
classDiagram
    class Task {
        +UUID id
        +string title
        +string type
        +UUID reference_id
        +UUID assignee_id
        +string priority
        +string status
        +datetime due_date
        +UUID created_by
        +datetime created_at
        +datetime updated_at
        +create() Task
        +assign() bool
        +update_status() bool
        +close() bool
    }

    class TaskComment {
        +UUID id
        +UUID task_id
        +UUID user_id
        +text content
        +datetime created_at
        +create() TaskComment
    }

    class TaskAttachment {
        +UUID id
        +UUID task_id
        +string file_name
        +string file_path
        +UUID uploaded_by
        +datetime created_at
    }

    class User {
        +UUID id
        +string username
    }

    Task "1" --> "*" TaskComment : has
    Task "1" --> "*" TaskAttachment : has
    User "1" --> "*" Task : assigned_to
    User "1" --> "*" Task : created_by
```

### 1.7 知识库类图

```mermaid
classDiagram
    class KnowledgeArticle {
        +UUID id
        +string title
        +string category
        +text content
        +UUID author_id
        +int view_count
        +string status
        +datetime created_at
        +datetime updated_at
        +create() KnowledgeArticle
        +publish() bool
        +increment_view() bool
    }

    class KnowledgeComment {
        +UUID id
        +UUID article_id
        +UUID user_id
        +text content
        +datetime created_at
        +create() KnowledgeComment
    }

    class KnowledgeTag {
        +UUID id
        +UUID article_id
        +string tag
    }

    class KnowledgeCategory {
        +UUID id
        +string name
        +string description
        +UUID parent_id
        +int sort_order
    }

    KnowledgeArticle "1" --> "*" KnowledgeComment : has
    KnowledgeArticle "1" --> "*" KnowledgeTag : has
    KnowledgeArticle "1" --> "*" KnowledgeCategory : belongs_to
    KnowledgeCategory "1" --> "*" KnowledgeCategory : parent
```

## 2. 服务层类图

```mermaid
classDiagram
    class BaseService {
        +Repository repository
        +get_by_id() Entity
        +get_list() List~Entity~
        +create() Entity
        +update() Entity
        +delete() bool
    }

    class AssetService {
        +create_asset() Asset
        +update_asset() Asset
        +delete_asset() bool
        +get_assets_by_team() List~Asset~
        +search_assets() List~Asset~
    }

    class VulnerabilityService {
        +create_vulnerability() Vulnerability
        +update_status() bool
        +assign_vulnerability() bool
        +get_vulnerabilities_by_asset() List~Vulnerability~
        +get_vulnerabilities_by_status() List~Vulnerability~
    }

    class EnvironmentService {
        +create_environment() Environment
        +deploy_environment() bool
        +snapshot_environment() EnvironmentSnapshot
        +rollback_environment() bool
        +monitor_environment() EnvironmentStatus
    }

    class AuthService {
        +authenticate() Token
        +authorize() bool
        +create_token() Token
        +verify_token() User
    }

    class NotificationService {
        +send_email() bool
        +send_webhook() bool
        +send_in_app() bool
    }

    class SearchService {
        +index_document() bool
        +search() SearchResult
        +reindex() bool
    }

    BaseService <|-- AssetService
    BaseService <|-- VulnerabilityService
    BaseService <|-- EnvironmentService
```

## 3. 控制器层类图

```mermaid
classDiagram
    class BaseController {
        +Service service
        +get() Response
        +post() Response
        +put() Response
        +delete() Response
    }

    class AssetController {
        +AssetService asset_service
        +create_asset() Response
        +get_asset() Response
        +list_assets() Response
        +update_asset() Response
        +delete_asset() Response
    }

    class VulnerabilityController {
        +VulnerabilityService vuln_service
        +create_vulnerability() Response
        +update_status() Response
        +list_vulnerabilities() Response
        +assign_vulnerability() Response
    }

    class EnvironmentController {
        +EnvironmentService env_service
        +create_environment() Response
        +deploy_environment() Response
        +snapshot_environment() Response
        +rollback_environment() Response
    }

    class AuthController {
        +AuthService auth_service
        +login() Response
        +logout() Response
        +register() Response
        +refresh_token() Response
    }

    BaseController <|-- AssetController
    BaseController <|-- VulnerabilityController
    BaseController <|-- EnvironmentController
    BaseController <|-- AuthController
```

## 4. 仓储层类图

```mermaid
classDiagram
    class BaseRepository {
        +Session session
        +get_by_id() Entity
        +get_all() List~Entity~
        +add() Entity
        +update() Entity
        +delete() bool
        +commit() bool
        +rollback() void
    }

    class AssetRepository {
        +get_by_team() List~Asset~
        +get_by_priority() List~Asset~
        +search() List~Asset~
        +get_with_vulnerabilities() Asset
    }

    class VulnerabilityRepository {
        +get_by_asset() List~Vulnerability~
        +get_by_status() List~Vulnerability~
        +get_by_assignee() List~Vulnerability~
        +get_by_severity() List~Vulnerability~
    }

    class UserRepository {
        +get_by_username() User
        +get_by_email() User
        +get_by_role() List~User~
    }

    class AuditRepository {
        +log_action() bool
        +get_logs_by_user() List~AuditLog~
        +get_logs_by_resource() List~AuditLog~
    }

    BaseRepository <|-- AssetRepository
    BaseRepository <|-- VulnerabilityRepository
    BaseRepository <|-- UserRepository
    BaseRepository <|-- AuditRepository
```

## 5. 领域事件类图

```mermaid
classDiagram
    class DomainEvent {
        +UUID event_id
        +string event_type
        +datetime occurred_at
        +JSON payload
    }

    class VulnerabilityDiscovered {
        +UUID vulnerability_id
        +UUID discoverer_id
        +string severity
    }

    class VulnerabilityStatusChanged {
        +UUID vulnerability_id
        +string from_status
        +string to_status
        +UUID changed_by
    }

    class TaskAssigned {
        +UUID task_id
        +UUID assignee_id
        +UUID assigned_by
    }

    class EnvironmentDeployed {
        +UUID environment_id
        +UUID deployed_by
        +string status
    }

    class AssetCreated {
        +UUID asset_id
        +UUID created_by
        +string asset_name
    }

    DomainEvent <|-- VulnerabilityDiscovered
    DomainEvent <|-- VulnerabilityStatusChanged
    DomainEvent <|-- TaskAssigned
    DomainEvent <|-- EnvironmentDeployed
    DomainEvent <|-- AssetCreated
```
