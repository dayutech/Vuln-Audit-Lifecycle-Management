# Tasks

## 阶段一：补充缺失实体类

- [x] Task 1: 创建工具资源库关联实体
  - [x] SubTask 1.1: 创建ToolVersion实体类
  - [x] SubTask 1.2: 创建ToolTag实体类
  - [x] SubTask 1.3: 创建ToolDownload实体类
  - [x] SubTask 1.4: 创建对应Repository接口
  - [x] SubTask 1.5: 回归检查：验证实体与数据库映射正确

- [x] Task 2: 创建任务管理关联实体
  - [x] SubTask 2.1: 创建TaskComment实体类
  - [x] SubTask 2.2: 创建TaskAttachment实体类
  - [x] SubTask 2.3: 创建对应Repository接口
  - [x] SubTask 2.4: 回归检查：验证实体与数据库映射正确

- [x] Task 3: 创建知识库关联实体
  - [x] SubTask 3.1: 创建KnowledgeComment实体类
  - [x] SubTask 3.2: 创建KnowledgeTag实体类
  - [x] SubTask 3.3: 创建对应Repository接口
  - [x] SubTask 3.4: 回归检查：验证实体与数据库映射正确

- [x] Task 4: 创建通知相关实体
  - [x] SubTask 4.1: 创建NotificationSetting实体类
  - [x] SubTask 4.2: 创建SystemAnnouncement实体类
  - [x] SubTask 4.3: 创建AssetSubscription实体类
  - [x] SubTask 4.4: 创建对应Repository接口
  - [x] SubTask 4.5: 回归检查：验证实体与数据库映射正确

- [x] Task 5: 创建仪表盘和审批相关实体
  - [x] SubTask 5.1: 创建DashboardConfig实体类
  - [x] SubTask 5.2: 创建StatCache实体类
  - [x] SubTask 5.3: 创建ApprovalScope实体类
  - [x] SubTask 5.4: 创建对应Repository接口
  - [x] SubTask 5.5: 回归检查：验证实体与数据库映射正确

## 阶段二：补充资产管理服务层

- [x] Task 6: 实现AssetService
  - [x] SubTask 6.1: 创建AssetService接口
  - [x] SubTask 6.2: 创建AssetServiceImpl实现类
  - [x] SubTask 6.3: 实现资产CRUD操作
  - [x] SubTask 6.4: 实现资产分组管理
  - [x] SubTask 6.5: 实现资产搜索功能
  - [x] SubTask 6.6: 回归检查：验证服务功能正确

- [x] Task 7: 实现AssetFileService
  - [x] SubTask 7.1: 创建AssetFileService接口
  - [x] SubTask 7.2: 创建AssetFileServiceImpl实现类
  - [x] SubTask 7.3: 实现文件上传下载
  - [x] SubTask 7.4: 实现文件版本管理
  - [x] SubTask 7.5: 回归检查：验证文件管理功能正确

- [x] Task 8: 实现AssetAccessService
  - [x] SubTask 8.1: 创建AssetAccessService接口
  - [x] SubTask 8.2: 创建AssetAccessServiceImpl实现类
  - [x] SubTask 8.3: 实现访问申请创建
  - [x] SubTask 8.4: 实现访问申请审批
  - [x] SubTask 8.5: 实现审批路由逻辑
  - [x] SubTask 8.6: 回归检查：验证审批流程正确

- [x] Task 9: 实现AssetClaimService
  - [x] SubTask 9.1: 创建AssetClaimService接口
  - [x] SubTask 9.2: 创建AssetClaimServiceImpl实现类
  - [x] SubTask 9.3: 实现资产认领
  - [x] SubTask 9.4: 实现认领状态管理
  - [x] SubTask 9.5: 回归检查：验证认领功能正确

- [x] Task 10: 实现AssetMetadataService
  - [x] SubTask 10.1: 创建AssetMetadataService接口
  - [x] SubTask 10.2: 创建AssetMetadataServiceImpl实现类
  - [x] SubTask 10.3: 实现元数据CRUD
  - [x] SubTask 10.4: 实现元数据历史版本管理
  - [x] SubTask 10.5: 回归检查：验证元数据管理正确

## 阶段三：补充团队管理服务层

- [x] Task 11: 实现TeamService
  - [x] SubTask 11.1: 创建TeamService接口
  - [x] SubTask 11.2: 创建TeamServiceImpl实现类
  - [x] SubTask 11.3: 实现团队CRUD操作
  - [x] SubTask 11.4: 实现团队成员管理
  - [x] SubTask 11.5: 实现团队层级结构
  - [x] SubTask 11.6: 回归检查：验证团队管理功能正确

## 阶段四：补充文档管理服务层

- [x] Task 12: 实现DocumentService
  - [x] SubTask 12.1: 创建DocumentService接口
  - [x] SubTask 12.2: 创建DocumentServiceImpl实现类
  - [x] SubTask 12.3: 实现文档CRUD操作
  - [x] SubTask 12.4: 实现文档版本管理
  - [x] SubTask 12.5: 实现文档访问控制
  - [x] SubTask 12.6: 实现文档搜索
  - [x] SubTask 12.7: 回归检查：验证文档管理功能正确

## 阶段五：补充环境管理服务层

- [x] Task 13: 实现EnvironmentService（空实现）
  - [x] SubTask 13.1: 创建EnvironmentService接口
  - [x] SubTask 13.2: 创建EnvironmentServiceImpl实现类（空实现）
  - [x] SubTask 13.3: 创建EnvironmentTemplateService接口和实现类
  - [x] SubTask 13.4: 创建EnvironmentSnapshotService接口和实现类
  - [x] SubTask 13.5: 回归检查：验证环境服务结构正确

## 阶段六：补充工具资源库服务层

- [x] Task 14: 实现ToolService
  - [x] SubTask 14.1: 创建ToolService接口
  - [x] SubTask 14.2: 创建ToolServiceImpl实现类
  - [x] SubTask 14.3: 实现工具CRUD操作
  - [x] SubTask 14.4: 实现工具版本管理
  - [x] SubTask 14.5: 实现工具下载统计
  - [x] SubTask 14.6: 实现工具标签管理
  - [x] SubTask 14.7: 回归检查：验证工具管理功能正确

## 阶段七：补充任务管理服务层

- [x] Task 15: 实现TaskService
  - [x] SubTask 15.1: 创建TaskService接口
  - [x] SubTask 15.2: 创建TaskServiceImpl实现类
  - [x] SubTask 15.3: 实现任务CRUD操作
  - [x] SubTask 15.4: 实现任务分配
  - [x] SubTask 15.5: 实现任务评论管理
  - [x] SubTask 15.6: 实现任务状态管理
  - [x] SubTask 15.7: 回归检查：验证任务管理功能正确

## 阶段八：补充知识库服务层

- [x] Task 16: 实现KnowledgeService
  - [x] SubTask 16.1: 创建KnowledgeService接口
  - [x] SubTask 16.2: 创建KnowledgeServiceImpl实现类
  - [x] SubTask 16.3: 实现知识文章CRUD操作
  - [x] SubTask 16.4: 实现文章评论管理
  - [x] SubTask 16.5: 实现文章标签管理
  - [x] SubTask 16.6: 回归检查：验证知识库功能正确

## 阶段九：补充消息通知服务层

- [x] Task 17: 实现NotificationService
  - [x] SubTask 17.1: 创建NotificationService接口
  - [x] SubTask 17.2: 创建NotificationServiceImpl实现类
  - [x] SubTask 17.3: 实现通知创建和发送
  - [x] SubTask 17.4: 实现通知已读管理
  - [x] SubTask 17.5: 实现通知设置管理
  - [x] SubTask 17.6: 实现系统公告管理
  - [x] SubTask 17.7: 实现资产订阅管理
  - [x] SubTask 17.8: 回归检查：验证通知功能正确

## 阶段十：补充仪表盘统计服务层

- [x] Task 18: 实现DashboardService
  - [x] SubTask 18.1: 创建DashboardService接口
  - [x] SubTask 18.2: 创建DashboardServiceImpl实现类
  - [x] SubTask 18.3: 实现资产统计
  - [x] SubTask 18.4: 实现漏洞统计
  - [x] SubTask 18.5: 实现工作量统计
  - [x] SubTask 18.6: 实现趋势分析
  - [x] SubTask 18.7: 实现仪表盘配置
  - [x] SubTask 18.8: 回归检查：验证统计功能正确

## 阶段十一：补充审计日志服务层

- [x] Task 19: 实现AuditLogService
  - [x] SubTask 19.1: 创建AuditLogService接口
  - [x] SubTask 19.2: 创建AuditLogServiceImpl实现类
  - [x] SubTask 19.3: 实现审计日志记录
  - [x] SubTask 19.4: 实现审计日志查询
  - [x] SubTask 19.5: 回归检查：验证审计日志功能正确

## 阶段十二：补充DTO类

- [x] Task 20: 创建资产相关DTO
  - [x] SubTask 20.1: 创建AssetCreateRequest
  - [x] SubTask 20.2: 创建AssetUpdateRequest
  - [x] SubTask 20.3: 创建AssetAccessRequest
  - [x] SubTask 20.4: 创建AssetAccessReviewRequest
  - [x] SubTask 20.5: 创建AssetClaimRequest
  - [x] SubTask 20.6: 创建AssetFileUploadRequest
  - [x] SubTask 20.7: 创建AssetMetadataRequest
  - [x] SubTask 20.8: 创建AssetResponse
  - [x] SubTask 20.9: 回归检查：验证DTO字段完整

- [x] Task 21: 创建漏洞相关DTO
  - [x] SubTask 21.1: 创建VulnerabilityCreateRequest
  - [x] SubTask 21.2: 创建VulnerabilityUpdateRequest
  - [x] SubTask 21.3: 创建VulnerabilityStatusUpdateRequest
  - [x] SubTask 21.4: 创建VulnerabilityResponse
  - [x] SubTask 21.5: 回归检查：验证DTO字段完整

- [x] Task 22: 创建文档相关DTO
  - [x] SubTask 22.1: 创建DocumentCreateRequest
  - [x] SubTask 22.2: 创建DocumentUpdateRequest
  - [x] SubTask 22.3: 创建DocumentResponse
  - [x] SubTask 22.4: 回归检查：验证DTO字段完整

- [x] Task 23: 创建任务相关DTO
  - [x] SubTask 23.1: 创建TaskCreateRequest
  - [x] SubTask 23.2: 创建TaskUpdateRequest
  - [x] SubTask 23.3: 创建TaskResponse
  - [x] SubTask 23.4: 回归检查：验证DTO字段完整

- [x] Task 24: 创建通知相关DTO
  - [x] SubTask 24.1: 创建NotificationResponse
  - [x] SubTask 24.2: 创建NotificationSettingRequest
  - [x] SubTask 24.3: 创建SystemAnnouncementRequest
  - [x] SubTask 24.4: 回归检查：验证DTO字段完整

- [x] Task 25: 创建统计相关DTO
  - [x] SubTask 25.1: 创建DashboardStatsResponse
  - [x] SubTask 25.2: 创建AssetStatsResponse
  - [x] SubTask 25.3: 创建VulnerabilityStatsResponse
  - [x] SubTask 25.4: 创建WorkloadStatsResponse
  - [x] SubTask 25.5: 回归检查：验证DTO字段完整

## 阶段十三：补充控制器层

- [x] Task 26: 实现AssetController
  - [x] SubTask 26.1: 创建AssetController类
  - [x] SubTask 26.2: 实现资产CRUD接口
  - [x] SubTask 26.3: 实现资产分组接口
  - [x] SubTask 26.4: 实现资产认领接口
  - [x] SubTask 26.5: 实现访问申请接口
  - [x] SubTask 26.6: 实现文件管理接口
  - [x] SubTask 26.7: 实现元数据接口
  - [x] SubTask 26.8: 添加Swagger注解
  - [x] SubTask 26.9: 回归检查：验证API功能正确

- [x] Task 27: 实现AssetGroupController
  - [x] SubTask 27.1: 创建AssetGroupController类
  - [x] SubTask 27.2: 实现分组CRUD接口
  - [x] SubTask 27.3: 实现分组资产管理
  - [x] SubTask 27.4: 添加Swagger注解
  - [x] SubTask 27.5: 回归检查：验证API功能正确

- [x] Task 28: 实现TeamController
  - [x] SubTask 28.1: 创建TeamController类
  - [x] SubTask 28.2: 实现团队CRUD接口
  - [x] SubTask 28.3: 实现成员管理接口
  - [x] SubTask 28.4: 添加Swagger注解
  - [x] SubTask 28.5: 回归检查：验证API功能正确

- [x] Task 29: 实现DocumentController
  - [x] SubTask 29.1: 创建DocumentController类
  - [x] SubTask 29.2: 实现文档CRUD接口
  - [x] SubTask 29.3: 实现版本管理接口
  - [x] SubTask 29.4: 实现访问控制接口
  - [x] SubTask 29.5: 实现搜索接口
  - [x] SubTask 29.6: 添加Swagger注解
  - [x] SubTask 29.7: 回归检查：验证API功能正确

- [x] Task 30: 实现EnvironmentController
  - [x] SubTask 30.1: 创建EnvironmentController类
  - [x] SubTask 30.2: 实现环境CRUD接口（空实现）
  - [x] SubTask 30.3: 实现模板管理接口
  - [x] SubTask 30.4: 实现快照管理接口
  - [x] SubTask 30.5: 添加Swagger注解
  - [x] SubTask 30.6: 回归检查：验证API结构正确

- [x] Task 31: 实现ToolController
  - [x] SubTask 31.1: 创建ToolController类
  - [x] SubTask 31.2: 实现工具CRUD接口
  - [x] SubTask 31.3: 实现下载接口
  - [x] SubTask 31.4: 实现版本管理接口
  - [x] SubTask 31.5: 实现标签管理接口
  - [x] SubTask 31.6: 添加Swagger注解
  - [x] SubTask 31.7: 回归检查：验证API功能正确

- [x] Task 32: 实现TaskController
  - [x] SubTask 32.1: 创建TaskController类
  - [x] SubTask 32.2: 实现任务CRUD接口
  - [x] SubTask 32.3: 实现任务分配接口
  - [x] SubTask 32.4: 实现评论接口
  - [x] SubTask 32.5: 实现状态管理接口
  - [x] SubTask 32.6: 添加Swagger注解
  - [x] SubTask 32.7: 回归检查：验证API功能正确

- [x] Task 33: 实现KnowledgeController
  - [x] SubTask 33.1: 创建KnowledgeController类
  - [x] SubTask 33.2: 实现文章CRUD接口
  - [x] SubTask 33.3: 实现评论接口
  - [x] SubTask 33.4: 实现标签接口
  - [x] SubTask 33.5: 添加Swagger注解
  - [x] SubTask 33.6: 回归检查：验证API功能正确

- [x] Task 34: 实现PermissionController
  - [x] SubTask 34.1: 创建PermissionController类
  - [x] SubTask 34.2: 实现权限CRUD接口
  - [x] SubTask 34.3: 添加Swagger注解
  - [x] SubTask 34.4: 回归检查：验证API功能正确

- [x] Task 35: 实现RoleController
  - [x] SubTask 35.1: 创建RoleController类
  - [x] SubTask 35.2: 实现角色CRUD接口
  - [x] SubTask 35.3: 实现权限分配接口
  - [x] SubTask 35.4: 实现用户角色分配接口
  - [x] SubTask 35.5: 添加Swagger注解
  - [x] SubTask 35.6: 回归检查：验证API功能正确

- [x] Task 36: 实现NotificationController
  - [x] SubTask 36.1: 创建NotificationController类
  - [x] SubTask 36.2: 实现通知列表接口
  - [x] SubTask 36.3: 实现已读管理接口
  - [x] SubTask 36.4: 实现设置管理接口
  - [x] SubTask 36.5: 实现公告接口
  - [x] SubTask 36.6: 实现订阅接口
  - [x] SubTask 36.7: 添加Swagger注解
  - [x] SubTask 36.8: 回归检查：验证API功能正确

- [x] Task 37: 实现DashboardController
  - [x] SubTask 37.1: 创建DashboardController类
  - [x] SubTask 37.2: 实现资产统计接口
  - [x] SubTask 37.3: 实现漏洞统计接口
  - [x] SubTask 37.4: 实现工作量统计接口
  - [x] SubTask 37.5: 实现趋势分析接口
  - [x] SubTask 37.6: 实现仪表盘配置接口
  - [x] SubTask 37.7: 实现导出接口
  - [x] SubTask 37.8: 添加Swagger注解
  - [x] SubTask 37.9: 回归检查：验证API功能正确

- [x] Task 38: 实现AuditLogController
  - [x] SubTask 38.1: 创建AuditLogController类
  - [x] SubTask 38.2: 实现日志查询接口
  - [x] SubTask 38.3: 添加Swagger注解
  - [x] SubTask 38.4: 回归检查：验证API功能正确

## 阶段十四：最终回归检查

- [x] Task 39: 全面回归检查
  - [x] SubTask 39.1: 检查所有实体字段与设计文档一致性
  - [x] SubTask 39.2: 检查所有API响应格式与api_design.md一致性
  - [x] SubTask 39.3: 检查所有Service方法签名正确
  - [x] SubTask 39.4: 检查所有Controller路由正确
  - [x] SubTask 39.5: 检查权限控制逻辑正确
  - [x] SubTask 39.6: 检查Swagger文档完整
  - [x] SubTask 39.7: 检查项目编译无错误

# Task Dependencies

- [Task 6-10] depends on [Task 1-5]
- [Task 11] depends on [Task 1-5]
- [Task 12] depends on [Task 1-5]
- [Task 13] depends on [Task 1-5]
- [Task 14] depends on [Task 1]
- [Task 15] depends on [Task 2]
- [Task 16] depends on [Task 3]
- [Task 17] depends on [Task 4]
- [Task 18] depends on [Task 5]
- [Task 19] depends on [Task 1-5]
- [Task 20-25] can run in parallel
- [Task 26-27] depends on [Task 6-10, Task 20]
- [Task 28] depends on [Task 11]
- [Task 29] depends on [Task 12, Task 22]
- [Task 30] depends on [Task 13]
- [Task 31] depends on [Task 14]
- [Task 32] depends on [Task 15, Task 23]
- [Task 33] depends on [Task 16]
- [Task 34] depends on [Task 1-5]
- [Task 35] depends on [Task 1-5]
- [Task 36] depends on [Task 17, Task 24]
- [Task 37] depends on [Task 18, Task 25]
- [Task 38] depends on [Task 19]
- [Task 39] depends on [Task 1-38]
