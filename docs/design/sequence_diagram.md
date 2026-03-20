# 时序图设计

## 1. 用户认证流程

```mermaid
sequenceDiagram
    participant User as 用户
    participant Frontend as 前端应用
    participant AuthAPI as 认证API
    participant UserRepo as 用户仓储
    participant TokenService as Token服务
    participant Redis as Redis缓存

    User->>Frontend: 输入用户名密码
    Frontend->>AuthAPI: POST /api/auth/login
    AuthAPI->>UserRepo: 验证用户凭据
    UserRepo-->>AuthAPI: 返回用户信息
    AuthAPI->>TokenService: 生成JWT Token
    TokenService->>Redis: 存储Token黑名单
    TokenService-->>AuthAPI: 返回Token
    AuthAPI-->>Frontend: 返回登录结果和Token
    Frontend-->>User: 登录成功，跳转首页
```

## 2. 漏洞提交流程

```mermaid
sequenceDiagram
    participant Engineer as 漏洞挖掘工程师
    participant Frontend as 前端应用
    participant VulnAPI as 漏洞API
    participant VulnService as 漏洞服务
    participant AssetService as 资产服务
    participant VulnRepo as 漏洞仓储
    participant NotifyService as 通知服务
    participant TeamLeader as 团队负责人

    Engineer->>Frontend: 填写漏洞信息表单
    Frontend->>VulnAPI: POST /api/vulnerabilities
    VulnAPI->>VulnService: create_vulnerability()
    VulnService->>AssetService: 验证资产存在
    AssetService-->>VulnService: 返回资产信息
    VulnService->>VulnRepo: 保存漏洞记录
    VulnRepo-->>VulnService: 返回创建结果
    VulnService->>NotifyService: 发送漏洞发现通知(通知所有上级领导+指定用户)
    NotifyService->>EmailSender: 发送邮件通知
    NotifyService->>SmsSender: 发送短信通知
    NotifyService->>InApp: 站内消息通知
    NotifyService-->>TeamLeader: 通知有新漏洞待审核
    NotifyService-->>SpecificUser: 通知指定用户
    VulnService-->>VulnAPI: 返回漏洞信息
    VulnAPI-->>Frontend: 返回创建结果
    Frontend-->>Engineer: 显示漏洞创建成功
```

## 3. 漏洞状态变更流程

```mermaid
sequenceDiagram
    participant Engineer as 漏洞挖掘工程师
    participant Frontend as 前端应用
    participant VulnAPI as 漏洞API
    participant VulnService as 漏洞服务
    participant VulnRepo as 漏洞仓储
    participant HistoryRepo as 历史记录仓储
    participant NotifyService as 通知服务

    Engineer->>Frontend: 更新漏洞状态
    Frontend->>VulnAPI: PUT /api/vulnerabilities/{id}/status
    VulnAPI->>VulnService: update_status()
    VulnService->>VulnRepo: 获取当前漏洞信息
    VulnRepo-->>VulnService: 返回漏洞数据
    VulnService->>VulnService: 验证状态转换合法性
    VulnService->>VulnRepo: 更新漏洞状态
    VulnService->>HistoryRepo: 记录状态变更历史
    HistoryRepo-->>VulnService: 返回保存结果
    VulnService->>NotifyService: 发送状态变更通知
    VulnService-->>VulnAPI: 返回更新结果
    VulnAPI-->>Frontend: 返回更新结果
    Frontend-->>Engineer: 显示状态更新成功
```

## 4. 环境创建与部署流程

> **说明**：本模块为靶场环境管理功能，相关流程暂保持为空实现，待后续需求明确后再行开发。

```mermaid
sequenceDiagram
    participant Engineer as 漏洞挖掘工程师
    participant Frontend as 前端应用
    participant EnvAPI as 环境API
    participant EnvService as 环境服务
    participant TemplateService as 模板服务
    participant DockerService as Docker服务
    participant EnvRepo as 环境仓储
    participant SnapshotRepo as 快照仓储

    Engineer->>Frontend: 选择模板并配置环境
    Frontend->>EnvAPI: POST /api/environments
    EnvAPI->>EnvService: create_environment()
    EnvService->>TemplateService: 获取模板配置
    TemplateService-->>EnvService: 返回模板信息
    EnvService->>EnvService: 验证配置参数
    EnvService->>DockerService: 创建Docker容器
    DockerService-->>EnvService: 容器创建成功
    EnvService->>EnvRepo: 保存环境记录
    EnvService->>SnapshotRepo: 创建初始快照
    EnvService-->>EnvAPI: 返回环境信息
    EnvAPI-->>Frontend: 返回创建结果
    Frontend-->>Engineer: 显示环境创建成功
```

## 5. 环境快照与回滚流程

```mermaid
sequenceDiagram
    participant Engineer as 漏洞挖掘工程师
    participant Frontend as 前端应用
    participant EnvAPI as 环境API
    participant EnvService as 环境服务
    participant DockerService as Docker服务
    participant SnapshotRepo as 快照仓储

    Engineer->>Frontend: 请求创建快照
    Frontend->>EnvAPI: POST /api/environments/{id}/snapshots
    EnvAPI->>EnvService: create_snapshot()
    EnvService->>DockerService: 获取当前容器配置
    DockerService-->>EnvService: 返回容器配置
    EnvService->>SnapshotRepo: 保存快照记录
    SnapshotRepo-->>EnvService: 返回快照信息
    EnvService-->>EnvAPI: 返回快照创建结果
    EnvAPI-->>Frontend: 返回快照信息
    
    Note over Engineer,Frontend: 后续需要回滚时
    
    Engineer->>Frontend: 请求回滚到快照
    Frontend->>EnvAPI: POST /api/environments/{id}/rollback
    EnvAPI->>EnvService: rollback_to_snapshot()
    EnvService->>SnapshotRepo: 获取快照配置
    SnapshotRepo-->>EnvService: 返回快照数据
    EnvService->>DockerService: 重建容器配置
    DockerService-->>EnvService: 回滚成功
    EnvService-->>EnvAPI: 返回回滚结果
    EnvAPI-->>Frontend: 返回回滚结果
    Frontend-->>Engineer: 显示回滚成功
```

## 6. 文档创建与版本管理流程

```mermaid
sequenceDiagram
    participant Engineer as 工程师
    participant Frontend as 前端应用
    participant DocAPI as 文档API
    participant DocService as 文档服务
    participant DocRepo as 文档仓储
    participant VersionRepo as 版本仓储
    participant SearchService as 搜索服务

    Engineer->>Frontend: 创建/编辑文档
    Frontend->>DocAPI: POST/PUT /api/documents
    DocAPI->>DocService: save_document()
    DocService->>DocRepo: 保存文档
    DocRepo-->>DocService: 返回文档信息
    DocService->>VersionRepo: 创建新版本记录
    VersionRepo-->>DocService: 返回版本信息
    DocService->>SearchService: 索引文档内容
    SearchService-->>DocService: 索引完成
    DocService-->>DocAPI: 返回保存结果
    DocAPI-->>Frontend: 返回保存结果
    Frontend-->>Engineer: 显示保存成功
    
    Note over Engineer,Frontend: 查看历史版本
    
    Engineer->>Frontend: 查看版本历史
    Frontend->>DocAPI: GET /api/documents/{id}/versions
    DocAPI->>DocService: get_versions()
    DocService->>VersionRepo: 获取版本列表
    VersionRepo-->>DocService: 返回版本列表
    DocService-->>DocAPI: 返回版本列表
    DocAPI-->>Frontend: 返回版本列表
    Frontend-->>Engineer: 显示版本历史
    
    Note over Engineer,Frontend: 回滚版本
    
    Engineer->>Frontend: 选择版本回滚
    Frontend->>DocAPI: POST /api/documents/{id}/rollback
    DocAPI->>DocService: rollback_to_version()
    DocService->>VersionRepo: 获取目标版本内容
    VersionRepo-->>DocService: 返回版本内容
    DocService->>DocRepo: 更新文档内容
    DocService->>VersionRepo: 创建新版本记录
    DocService-->>DocAPI: 返回回滚结果
    DocAPI-->>Frontend: 返回回滚结果
    Frontend-->>Engineer: 显示回滚成功
```

## 7. 任务分配流程

```mermaid
sequenceDiagram
    participant Leader as 团队负责人
    participant Frontend as 前端应用
    participant TaskAPI as 任务API
    participant TaskService as 任务服务
    participant TaskRepo as 任务仓储
    participant NotifyService as 通知服务
    participant Engineer as 漏洞挖掘工程师

    Leader->>Frontend: 创建任务并分配
    Frontend->>TaskAPI: POST /api/tasks
    TaskAPI->>TaskService: create_task()
    TaskService->>TaskRepo: 保存任务
    TaskRepo-->>TaskService: 返回任务信息
    TaskService->>NotifyService: 发送任务分配通知
    NotifyService-->>Engineer: 通知有新任务
    TaskService-->>TaskAPI: 返回任务信息
    TaskAPI-->>Frontend: 返回创建结果
    Frontend-->>Leader: 显示任务创建成功
    
    Note over Engineer,Frontend: 工程师开始处理
    
    Engineer->>Frontend: 开始处理任务
    Frontend->>TaskAPI: PUT /api/tasks/{id}/status
    TaskAPI->>TaskService: update_status()
    TaskService->>TaskRepo: 更新任务状态
    TaskService->>NotifyService: 发送状态更新通知
    NotifyService-->>Leader: 通知任务状态更新
    TaskService-->>TaskAPI: 返回更新结果
    TaskAPI-->>Frontend: 返回更新结果
    Frontend-->>Engineer: 显示状态更新成功
```

## 8. 工具上传与下载流程

```mermaid
sequenceDiagram
    participant Engineer as 漏洞挖掘工程师
    participant Frontend as 前端应用
    participant ToolAPI as 工具API
    participant ToolService as 工具服务
    participant FileService as 文件服务(本地存储，预留对象存储接口)
    participant ToolRepo as 工具仓储
    participant SearchService as 搜索服务

    Engineer->>Frontend: 上传工具文件
    Frontend->>ToolAPI: POST /api/tools (multipart)
    ToolAPI->>FileService: 保存文件
    FileService-->>ToolAPI: 返回文件路径
    ToolAPI->>ToolService: create_tool()
    ToolService->>ToolRepo: 保存工具元数据
    ToolRepo-->>ToolService: 返回工具信息
    ToolService->>SearchService: 索引工具信息
    SearchService-->>ToolService: 索引完成
    ToolService-->>ToolAPI: 返回工具信息
    ToolAPI-->>Frontend: 返回上传结果
    Frontend-->>Engineer: 显示上传成功
    
    Note over Engineer,Frontend: 其他工程师下载工具
    
    Engineer2->>Frontend: 搜索并下载工具
    Frontend->>ToolAPI: GET /api/tools/{id}/download
    ToolAPI->>ToolService: download_tool()
    ToolService->>ToolRepo: 更新下载次数
    ToolService->>FileService: 获取文件
    FileService-->>ToolService: 返回文件
    ToolService-->>ToolAPI: 返回文件
    ToolAPI-->>Frontend: 返回文件
    Frontend-->>Engineer2: 开始下载
```

## 9. 权限验证流程

```mermaid
sequenceDiagram
    participant User as 用户
    participant Frontend as 前端应用
    participant API as API网关
    participant AuthMiddleware as 认证中间件
    participant PermissionService as 权限服务
    participant Cache as 权限缓存

    User->>Frontend: 发起请求
    Frontend->>API: GET /api/vulnerabilities (with Token)
    API->>AuthMiddleware: 验证Token
    AuthMiddleware->>Cache: 检查权限缓存
    Cache-->>AuthMiddleware: 缓存命中
    AuthMiddleware->>PermissionService: 验证权限
    PermissionService-->>AuthMiddleware: 有权限
    AuthMiddleware-->>API: 验证通过
    API-->>Frontend: 返回数据
    Frontend-->>User: 显示数据
    
    Note over User,API: 无权限情况
    
    User->>Frontend: 发起请求
    Frontend->>API: DELETE /api/assets/{id} (with Token)
    API->>AuthMiddleware: 验证Token
    AuthMiddleware->>PermissionService: 验证权限
    PermissionService-->>AuthMiddleware: 无权限
    AuthMiddleware-->>API: 返回403
    API-->>Frontend: 返回403 Forbidden
    Frontend-->>User: 显示无权限提示
```

## 10. 资产认领流程

> **说明**：资产认领为自动批准，无需人工审核

```mermaid
sequenceDiagram
    participant Engineer as 漏洞挖掘工程师
    participant Frontend as 前端应用
    participant ClaimAPI as 资产认领API
    participant ClaimService as 认领服务
    participant ClaimRepo as 认领仓储
    participant NotifyService as 通知服务
    participant Leader as 团队负责人

    Engineer->>Frontend: 选择资产并提交认领
    Frontend->>ClaimAPI: POST /api/assets/{id}/claims
    ClaimAPI->>ClaimService: create_claim()
    ClaimService->>ClaimRepo: 保存认领记录并自动批准
    ClaimRepo-->>ClaimService: 返回认领信息(status=approved)
    ClaimService->>NotifyService: 发送认领成功通知
    NotifyService-->>Leader: 通知有新的认领记录
    NotifyService-->>Engineer: 通知认领已批准
    ClaimService-->>ClaimAPI: 返回认领信息
    ClaimAPI-->>Frontend: 返回认领结果
    Frontend-->>Engineer: 显示认领已批准，可以开始挖掘工作

    Note over Engineer,Frontend: 工程师开始漏洞挖掘工作

    Engineer->>Frontend: 完成漏洞挖掘，标记认领完成
    Frontend->>ClaimAPI: PUT /api/claims/{id}
    ClaimAPI->>ClaimService: complete_claim()
    ClaimService->>ClaimRepo: 更新认领状态为completed
    ClaimService-->>ClaimAPI: 返回完成结果
    ClaimAPI-->>Frontend: 返回完成结果
    Frontend-->>Engineer: 显示认领已完成
```

## 11. 数据备份流程

```mermaid
sequenceDiagram
    participant System as 系统
    participant BackupScheduler as 备份调度器
    participant BackupService as 备份服务
    participant Database as MariaDB
    participant FileStorage as 文件存储(本地，预留对象存储接口)
    participant BackupStorage as 备份存储

    BackupScheduler->>BackupService: 触发每日备份
    BackupService->>Database: 执行数据库备份
    Database-->>BackupService: 返回备份文件
    BackupService->>FileStorage: 备份文件存储
    FileStorage-->>BackupService: 返回存储路径
    BackupService->>BackupStorage: 复制到备份存储
    BackupStorage-->>BackupService: 返回备份完成
    BackupService->>System: 记录备份日志
```

## 12. 资产访问申请与审批流程

```mermaid
sequenceDiagram
    participant Engineer as 漏洞挖掘工程师
    participant Frontend as 前端
    participant AssetAPI as 资产API
    participant AssetService as 资产服务
    participant ApprovalScopeService as 审批范围服务
    participant NotificationService as 通知服务
    participant Leader as 部门领导

    Engineer->>Frontend: 申请访问资产
    Frontend->>AssetAPI: POST /api/v1/assets/{id}/access-request
    AssetAPI->>AssetService: 创建访问申请
    AssetService->>ApprovalScopeService: 验证审批权限
    ApprovalScopeService-->>AssetService: 返回审批人列表
    AssetService->>NotificationService: 发送审批通知
    NotificationService-->>Engineer: 通知已发送
    NotificationService->>Leader: 发送审批请求通知
    
    Leader->>Frontend: 查看待审批列表
    Frontend->>AssetAPI: GET /api/v1/my/access-approval-requests
    AssetAPI-->>Leader: 返回待审批列表
    
    Leader->>Frontend: 审批申请（通过/拒绝）
    Frontend->>AssetAPI: POST /api/v1/access-requests/{id}/review
    AssetAPI->>AssetService: 更新审批状态
    AssetService->>NotificationService: 发送审批结果通知
    NotificationService->>Engineer: 通知审批结果
```

## 13. 消息通知流程

```mermaid
sequenceDiagram
    participant User as 用户
    participant Frontend as 前端
    participant NotificationAPI as 通知API
    participant NotificationService as 通知服务
    participant EventPublisher as 事件发布器
    participant Asset as 资产系统

    User->>Frontend: 访问系统
    Frontend->>NotificationAPI: GET /api/v1/notifications
    NotificationAPI-->>User: 返回通知列表
    
    EventPublisher->>NotificationService: 触发事件(资产审批/任务分配等)
    NotificationService->>NotificationService: 创建通知记录
    NotificationService->>User: 推送实时通知
    
    User->>Frontend: 点击通知
    Frontend->>NotificationAPI: PUT /api/v1/notifications/{id}/read
    NotificationAPI-->>User: 标记已读
```

## 14. 仪表盘数据统计流程

```mermaid
sequenceDiagram
    participant Manager as 管理人员
    participant Frontend as 前端
    participant DashboardAPI as 仪表盘API
    participant StatService as 统计服务
    participant AssetRepo as 资产仓储
    participant VulnRepo as 漏洞仓储

    Manager->>Frontend: 访问仪表盘
    Frontend->>DashboardAPI: GET /api/v1/dashboard/assets
    DashboardAPI->>StatService: 获取资产统计数据
    StatService->>AssetRepo: 查询资产数据
    AssetRepo-->>StatService: 返回统计数据
    StatService-->>DashboardAPI: 返回统计结果
    DashboardAPI-->>Manager: 返回资产统计
    
    Manager->>Frontend: 查看漏洞统计
    Frontend->>DashboardAPI: GET /api/v1/dashboard/vulnerabilities
    DashboardAPI->>StatService: 获取漏洞统计数据
    StatService->>VulnRepo: 查询漏洞数据
    VulnRepo-->>StatService: 返回统计数据
    StatService-->>DashboardAPI: 返回统计结果
    DashboardAPI-->>Manager: 返回漏洞统计
```
