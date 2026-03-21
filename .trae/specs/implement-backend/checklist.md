# 后端实现检查清单

## 阶段一：项目基础结构

- [x] Spring Boot项目结构符合分层架构（Controller -> Service -> Repository -> Entity）
- [x] pom.xml包含所有必要依赖（Spring Boot、JPA、JWT、Redis、MariaDB驱动等）
- [x] application.yml配置正确（数据库连接、Redis配置、JWT配置）
- [x] 主启动类正确创建
- [x] 通用响应类ApiResponse.java正确实现
- [x] 分页响应类PageResponse.java正确实现
- [x] 全局异常处理器正确捕获和处理异常
- [x] 业务异常类和错误码枚举完整定义
- [x] 项目能够正常启动

## 阶段二：用户与权限管理

- [x] User实体包含phone字段
- [x] User实体密码字段使用加密存储
- [x] JWT工具类正确生成和验证Token
- [x] JWT过滤器正确拦截请求并验证Token
- [x] SecurityConfig正确配置安全规则
- [x] Role实体包含内置角色（admin、project_leader、team_leader、vulnerability_engineer）
- [x] Permission实体支持API级别权限控制
- [x] 用户角色关联正确实现
- [x] 角色权限关联正确实现
- [x] 用户权限关联正确实现
- [x] Team实体包含created_by字段
- [x] TeamMember关联实体正确实现
- [x] 用户登录返回有效JWT Token
- [x] 权限验证正确拦截无权限请求

## 阶段三：资产管理

- [x] Asset实体不包含vendor_notified、vendor_fixed、fixed_at字段
- [x] AssetGroup实体正确实现
- [x] AssetTag实体正确实现
- [x] AssetHistory实体正确记录变更历史
- [x] AssetClaim实体正确实现认领流程
- [x] AssetAccess实体使用can_view_documents、can_view_environments、can_view_files、allowed_file_types字段
- [x] AssetAccess不使用access_scope JSON字段
- [x] AssetFile实体正确实现
- [x] AssetFileVersion实体正确实现版本管理
- [x] AssetAppMetadata实体正确实现
- [x] AssetAppMetadataHistory实体正确实现历史记录
- [x] 资产CRUD API功能完整
- [x] 资产认领审批流程正确

## 阶段四：漏洞管理

- [x] WorkflowStatus枚举包含所有状态（discovered/verified/analyzing/analyzed/exploit_development/field_testing/delivered/closed）
- [x] CustomerStatus枚举包含所有状态（pending/confirmed/rejected）
- [x] DisclosureStatus枚举包含所有状态（undisclosed/disclosed/has_cve）
- [x] Vulnerability实体使用workflow_status字段（不是status）
- [x] Vulnerability实体使用customer_status字段
- [x] Vulnerability实体使用disclosure_status字段
- [x] Vulnerability实体包含disclosure_date字段
- [x] Vulnerability实体包含disclosure_source字段
- [x] Vulnerability实体包含vendor_fixed布尔字段
- [x] Vulnerability实体包含vendor_fixed_at字段
- [x] Vulnerability实体包含vendor_fix_version字段
- [x] Vulnerability实体不包含vendor_notified、vendor_status、vendor_notified_at字段
- [x] VulnerabilityHistory实体包含status_type字段（workflow/customer/disclosure）
- [x] 状态变更自动创建历史记录
- [x] 漏洞CRUD API功能完整
- [x] 漏洞状态流转API正确实现

## 阶段五：文档管理

- [x] Document实体包含summary字段
- [x] Document实体包含word_count字段
- [x] DocumentVersion实体正确实现版本管理
- [x] DocumentAccess实体正确实现访问控制
- [x] DocumentTag实体正确实现
- [x] 文档CRUD API功能完整
- [x] 文档版本管理正确

## 阶段六：环境管理

- [x] Environment实体正确实现
- [x] Environment.config字段使用JSONB类型
- [x] EnvironmentSnapshot实体正确实现
- [x] EnvironmentTemplate实体正确实现
- [x] 环境CRUD API功能完整

## 阶段七：工具资源库

- [x] Tool实体正确实现
- [x] 工具CRUD API功能完整

## 阶段八：任务管理

- [x] Task实体正确实现
- [x] 任务CRUD API功能完整
- [x] 任务分配功能正确

## 阶段九：知识库

- [x] KnowledgeArticle实体正确实现
- [x] 知识库CRUD API功能完整

## 阶段十：审计日志

- [x] AuditLog实体正确实现
- [x] 审计日志记录完整

## 阶段十一：消息通知

- [x] Notification实体不包含user_id字段
- [x] Notification实体不包含is_read字段
- [x] Notification实体不包含read_at字段
- [x] NotificationRecipient实体包含user_id字段
- [x] NotificationRecipient实体包含is_read字段
- [x] NotificationRecipient实体包含read_at字段
- [x] 通知CRUD API功能完整
- [x] 通知已读/未读状态正确管理

## 阶段十二：仪表盘统计

- [x] 漏洞统计API返回by_workflow_status、by_customer_status、by_disclosure_status
- [x] 统计数据正确计算

## 阶段十三：最终回归检查

- [x] 所有实体字段与data_dictionary.md一致
- [x] 所有API响应格式与api_design.md一致
- [x] 漏洞状态流转逻辑闭环（无死锁状态）
- [x] 权限控制逻辑正确（RBAC）
- [x] 审计日志记录完整
- [x] 通知模块结构正确（Notification + NotificationRecipient）
- [x] 无编译错误
- [x] 无运行时异常
- [x] 所有API端点可访问
