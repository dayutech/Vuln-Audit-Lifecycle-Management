# Tasks

## 阶段一：项目基础结构

- [x] Task 1: 创建Spring Boot项目基础结构
  - [x] SubTask 1.1: 创建pom.xml，配置Maven依赖（Spring Boot、JPA、JWT、Redis等）
  - [x] SubTask 1.2: 创建application.yml配置文件（数据库、Redis、JWT配置）
  - [x] SubTask 1.3: 创建主启动类VulnAuditApplication.java
  - [x] SubTask 1.4: 创建通用响应类ApiResponse.java和PageResponse.java
  - [x] SubTask 1.5: 创建全局异常处理器GlobalExceptionHandler.java
  - [x] SubTask 1.6: 创建业务异常类BusinessException.java和错误码枚举ErrorCode.java
  - [x] SubTask 1.7: 回归检查：验证项目能否正常启动

- [x] Task 2: 实现JWT认证基础设施
  - [x] SubTask 2.1: 创建JWT工具类JwtUtils.java
  - [x] SubTask 2.2: 创建JWT过滤器JwtAuthenticationFilter.java
  - [x] SubTask 2.3: 创建安全配置SecurityConfig.java
  - [x] SubTask 2.4: 回归检查：验证JWT认证流程是否正常

## 阶段二：用户与权限管理

- [x] Task 3: 实现用户实体和数据访问层
  - [x] SubTask 3.1: 创建User实体类（包含phone字段）
  - [x] SubTask 3.2: 创建UserRepository接口
  - [x] SubTask 3.3: 回归检查：验证实体与数据库表映射正确

- [x] Task 4: 实现角色和权限实体
  - [x] SubTask 4.1: 创建Role实体类
  - [x] SubTask 4.2: 创建Permission实体类
  - [x] SubTask 4.3: 创建RolePermission关联实体
  - [x] SubTask 4.4: 创建UserRole关联实体
  - [x] SubTask 4.5: 创建UserPermission关联实体
  - [x] SubTask 4.6: 回归检查：验证角色权限关联关系正确

- [x] Task 5: 实现团队实体
  - [x] SubTask 5.1: 创建Team实体类（包含created_by字段）
  - [x] SubTask 5.2: 创建TeamMember关联实体
  - [x] SubTask 5.3: 回归检查：验证团队层级结构正确

- [x] Task 6: 实现用户服务层
  - [x] SubTask 6.1: 创建UserService接口和实现类
  - [x] SubTask 6.2: 实现用户CRUD操作
  - [x] SubTask 6.3: 实现用户登录验证
  - [x] SubTask 6.4: 实现密码加密
  - [x] SubTask 6.5: 回归检查：验证用户服务功能完整

- [x] Task 7: 实现权限服务层
  - [x] SubTask 7.1: 创建RoleService接口和实现类
  - [x] SubTask 7.2: 创建PermissionService接口和实现类
  - [x] SubTask 7.3: 实现权限验证逻辑
  - [x] SubTask 7.4: 回归检查：验证RBAC权限控制正确

- [x] Task 8: 实现用户控制器
  - [x] SubTask 8.1: 创建AuthController（登录、登出）
  - [x] SubTask 8.2: 创建UserController（用户CRUD）
  - [x] SubTask 8.3: 回归检查：验证API响应格式正确

## 阶段三：资产管理

- [x] Task 9: 实现资产实体
  - [x] SubTask 9.1: 创建Asset实体类（移除vendor_notified等旧字段）
  - [x] SubTask 9.2: 创建AssetGroup实体类
  - [x] SubTask 9.3: 创建AssetTag实体类
  - [x] SubTask 9.4: 创建AssetHistory实体类
  - [x] SubTask 9.5: 回归检查：验证资产实体字段与设计文档一致

- [x] Task 10: 实现资产认领和访问申请实体
  - [x] SubTask 10.1: 创建AssetClaim实体类
  - [x] SubTask 10.2: 创建AssetAccess实体类（使用can_view_documents等独立字段）
  - [x] SubTask 10.3: 回归检查：验证访问权限字段正确

- [x] Task 11: 实现资产文件实体
  - [x] SubTask 11.1: 创建AssetFile实体类
  - [x] SubTask 11.2: 创建AssetFileVersion实体类
  - [x] SubTask 11.3: 创建AssetAppMetadata实体类
  - [x] SubTask 11.4: 创建AssetAppMetadataHistory实体类
  - [x] SubTask 11.5: 回归检查：验证文件版本管理正确

- [x] Task 12: 实现资产服务层和控制器
  - [x] SubTask 12.1: 创建AssetService接口和实现类
  - [x] SubTask 12.2: 创建AssetController
  - [x] SubTask 12.3: 回归检查：验证资产API功能完整

## 阶段四：漏洞管理

- [x] Task 13: 实现漏洞状态枚举
  - [x] SubTask 13.1: 创建WorkflowStatus枚举（discovered/verified/analyzing/analyzed/exploit_development/field_testing/delivered/closed）
  - [x] SubTask 13.2: 创建CustomerStatus枚举（pending/confirmed/rejected）
  - [x] SubTask 13.3: 创建DisclosureStatus枚举（undisclosed/disclosed/has_cve）
  - [x] SubTask 13.4: 创建Severity枚举（critical/high/medium/low）
  - [x] SubTask 13.5: 创建ExploitStage枚举（探测/突破/控守/已完成）
  - [x] SubTask 13.6: 回归检查：验证枚举值与设计文档一致

- [x] Task 14: 实现漏洞实体
  - [x] SubTask 14.1: 创建Vulnerability实体类（使用workflow_status/customer_status/disclosure_status）
  - [x] SubTask 14.2: 创建VulnerabilityHistory实体类
  - [x] SubTask 14.3: 创建VulnerabilityAttachment实体类
  - [x] SubTask 14.4: 创建VulnerabilityTag实体类
  - [x] SubTask 14.5: 回归检查：验证漏洞实体包含disclosure_date、disclosure_source、vendor_fixed、vendor_fixed_at、vendor_fix_version字段

- [x] Task 15: 实现漏洞服务层和控制器
  - [x] SubTask 15.1: 创建VulnerabilityService接口和实现类
  - [x] SubTask 15.2: 实现状态流转逻辑（状态变更自动创建历史记录）
  - [x] SubTask 15.3: 创建VulnerabilityController
  - [x] SubTask 15.4: 回归检查：验证漏洞状态流转逻辑正确

## 阶段五：文档管理

- [x] Task 16: 实现文档实体
  - [x] SubTask 16.1: 创建Document实体类（包含summary字段）
  - [x] SubTask 16.2: 创建DocumentVersion实体类
  - [x] SubTask 16.3: 创建DocumentAccess实体类
  - [x] SubTask 16.4: 创建DocumentTag实体类
  - [x] SubTask 16.5: 回归检查：验证文档实体字段正确

- [x] Task 17: 实现文档服务层和控制器
  - [x] SubTask 17.1: 创建DocumentService接口和实现类
  - [x] SubTask 17.2: 创建DocumentController
  - [x] SubTask 17.3: 回归检查：验证文档版本管理正确

## 阶段六：环境管理

- [x] Task 18: 实现环境实体
  - [x] SubTask 18.1: 创建Environment实体类
  - [x] SubTask 18.2: 创建EnvironmentSnapshot实体类
  - [x] SubTask 18.3: 创建EnvironmentTemplate实体类
  - [x] SubTask 18.4: 回归检查：验证环境实体正确

- [x] Task 19: 实现环境服务层和控制器
  - [x] SubTask 19.1: 创建EnvironmentService接口和实现类
  - [x] SubTask 19.2: 创建EnvironmentController
  - [x] SubTask 19.3: 回归检查：验证环境管理功能正确

## 阶段七：工具资源库

- [x] Task 20: 实现工具实体
  - [x] SubTask 20.1: 创建Tool实体类
  - [x] SubTask 20.2: 创建ToolRepository接口
  - [x] SubTask 20.3: 回归检查：验证工具实体正确

- [x] Task 21: 实现工具服务层和控制器
  - [x] SubTask 21.1: 创建ToolService接口和实现类
  - [x] SubTask 21.2: 创建ToolController
  - [x] SubTask 21.3: 回归检查：验证工具管理功能正确

## 阶段八：任务管理

- [x] Task 22: 实现任务实体和服务
  - [x] SubTask 22.1: 创建Task实体类
  - [x] SubTask 22.2: 创建TaskRepository接口
  - [x] SubTask 22.3: 回归检查：验证任务管理功能正确

## 阶段九：知识库

- [x] Task 23: 实现知识库实体和服务
  - [x] SubTask 23.1: 创建KnowledgeArticle实体类
  - [x] SubTask 23.2: 创建KnowledgeArticleRepository接口
  - [x] SubTask 23.3: 回归检查：验证知识库功能正确

## 阶段十：审计日志

- [x] Task 24: 实现审计日志
  - [x] SubTask 24.1: 创建AuditLog实体类
  - [x] SubTask 24.2: 创建AuditLogRepository接口
  - [x] SubTask 24.3: 回归检查：验证审计日志记录正确

## 阶段十一：消息通知

- [x] Task 25: 实现通知实体
  - [x] SubTask 25.1: 创建Notification实体类（不包含user_id/is_read/read_at）
  - [x] SubTask 25.2: 创建NotificationRecipient实体类（包含user_id/is_read/read_at）
  - [x] SubTask 25.3: 回归检查：验证通知结构正确

- [x] Task 26: 实现通知服务层和控制器
  - [x] SubTask 26.1: 创建NotificationRepository接口
  - [x] SubTask 26.2: 创建NotificationRecipientRepository接口
  - [x] SubTask 26.3: 回归检查：验证通知功能正确

## 阶段十二：仪表盘统计

- [x] Task 27: 实现仪表盘统计
  - [x] SubTask 27.1: 创建统计服务基础结构
  - [x] SubTask 27.2: 回归检查：验证统计功能正确

## 阶段十三：最终回归检查

- [x] Task 28: 全面回归检查
  - [x] SubTask 28.1: 检查所有实体字段与设计文档一致性
  - [x] SubTask 28.2: 检查所有API响应格式与api_design.md一致性
  - [x] SubTask 28.3: 检查漏洞状态流转逻辑闭环
  - [x] SubTask 28.4: 检查权限控制逻辑正确
  - [x] SubTask 28.5: 检查审计日志记录完整
  - [x] SubTask 28.6: 检查通知模块结构正确

# Task Dependencies

- [Task 2] depends on [Task 1]
- [Task 3] depends on [Task 2]
- [Task 4] depends on [Task 3]
- [Task 5] depends on [Task 3]
- [Task 6] depends on [Task 3, Task 4]
- [Task 7] depends on [Task 4]
- [Task 8] depends on [Task 6, Task 7]
- [Task 9] depends on [Task 3, Task 5]
- [Task 10] depends on [Task 9]
- [Task 11] depends on [Task 9]
- [Task 12] depends on [Task 9, Task 10, Task 11]
- [Task 13] depends on [Task 1]
- [Task 14] depends on [Task 9, Task 13]
- [Task 15] depends on [Task 14]
- [Task 16] depends on [Task 3, Task 9]
- [Task 17] depends on [Task 16]
- [Task 18] depends on [Task 9]
- [Task 19] depends on [Task 18]
- [Task 20] depends on [Task 3]
- [Task 21] depends on [Task 20]
- [Task 22] depends on [Task 3]
- [Task 23] depends on [Task 3]
- [Task 24] depends on [Task 3]
- [Task 25] depends on [Task 3]
- [Task 26] depends on [Task 25]
- [Task 27] depends on [Task 3, Task 9, Task 14]
- [Task 28] depends on [Task 1-27]
