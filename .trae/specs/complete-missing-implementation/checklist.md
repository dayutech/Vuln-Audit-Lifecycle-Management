# 后端补充实现检查清单

## 阶段一：补充缺失实体类

- [ ] ToolVersion实体类正确创建，包含tool_id、version、file_path、changelog字段
- [ ] ToolTag实体类正确创建，包含tool_id、tag字段
- [ ] ToolDownload实体类正确创建，包含tool_id、user_id、downloaded_at字段
- [ ] TaskComment实体类正确创建，包含task_id、user_id、content字段
- [ ] TaskAttachment实体类正确创建，包含task_id、file_name、file_path字段
- [ ] KnowledgeComment实体类正确创建，包含article_id、user_id、content字段
- [ ] KnowledgeTag实体类正确创建，包含article_id、tag字段
- [ ] NotificationSetting实体类正确创建，包含user_id、type、enabled、notify_email、notify_sms、notify_webhook字段
- [ ] SystemAnnouncement实体类正确创建，包含title、content、target_roles、priority、published_at、expires_at字段
- [ ] AssetSubscription实体类正确创建，包含user_id、asset_id字段
- [ ] DashboardConfig实体类正确创建，包含user_id、layout、cards字段
- [ ] StatCache实体类正确创建，包含stat_type、stat_key、stat_value字段
- [ ] ApprovalScope实体类正确创建，包含approver_id、scope_type、scope_id、granted_by字段
- [ ] 所有新实体对应的Repository接口正确创建

## 阶段二：资产管理服务层

- [ ] AssetService接口定义完整，包含所有CRUD方法
- [ ] AssetServiceImpl实现类正确实现所有方法
- [ ] 资产创建功能正确，返回创建的资产详情
- [ ] 资产更新功能正确，支持部分更新
- [ ] 资产删除功能正确，支持软删除
- [ ] 资产查询功能正确，支持分页和搜索
- [ ] 资产分组管理功能正确
- [ ] AssetFileService接口和实现类正确创建
- [ ] 文件上传功能正确，支持多版本
- [ ] 文件下载功能正确，包含权限检查
- [ ] AssetAccessService接口和实现类正确创建
- [ ] 访问申请创建功能正确
- [ ] 访问申请审批功能正确
- [ ] 审批路由逻辑正确（查找直属领导→项目负责人→管理员）
- [ ] AssetClaimService接口和实现类正确创建
- [ ] 资产认领功能正确，自动批准
- [ ] AssetMetadataService接口和实现类正确创建
- [ ] 元数据CRUD功能正确
- [ ] 元数据历史版本管理正确

## 阶段三：团队管理服务层

- [ ] TeamService接口定义完整
- [ ] TeamServiceImpl实现类正确实现所有方法
- [ ] 团队CRUD功能正确
- [ ] 团队成员管理功能正确
- [ ] 团队层级结构支持正确

## 阶段四：文档管理服务层

- [ ] DocumentService接口定义完整
- [ ] DocumentServiceImpl实现类正确实现所有方法
- [ ] 文档CRUD功能正确
- [ ] 文档版本管理正确，自动创建版本记录
- [ ] 文档访问控制正确
- [ ] 文档搜索功能正确

## 阶段五：环境管理服务层

- [ ] EnvironmentService接口定义完整
- [ ] EnvironmentServiceImpl实现类结构正确（空实现）
- [ ] EnvironmentTemplateService接口和实现类正确创建
- [ ] EnvironmentSnapshotService接口和实现类正确创建

## 阶段六：工具资源库服务层

- [ ] ToolService接口定义完整
- [ ] ToolServiceImpl实现类正确实现所有方法
- [ ] 工具CRUD功能正确
- [ ] 工具版本管理正确
- [ ] 工具下载统计正确
- [ ] 工具标签管理正确

## 阶段七：任务管理服务层

- [ ] TaskService接口定义完整
- [ ] TaskServiceImpl实现类正确实现所有方法
- [ ] 任务CRUD功能正确
- [ ] 任务分配功能正确
- [ ] 任务评论管理正确
- [ ] 任务状态管理正确

## 阶段八：知识库服务层

- [ ] KnowledgeService接口定义完整
- [ ] KnowledgeServiceImpl实现类正确实现所有方法
- [ ] 知识文章CRUD功能正确
- [ ] 文章评论管理正确
- [ ] 文章标签管理正确

## 阶段九：消息通知服务层

- [ ] NotificationService接口定义完整
- [ ] NotificationServiceImpl实现类正确实现所有方法
- [ ] 通知创建和发送功能正确
- [ ] 通知已读管理正确
- [ ] 通知设置管理正确
- [ ] 系统公告管理正确
- [ ] 资产订阅管理正确

## 阶段十：仪表盘统计服务层

- [ ] DashboardService接口定义完整
- [ ] DashboardServiceImpl实现类正确实现所有方法
- [ ] 资产统计功能正确，返回by_status、by_type、by_priority
- [ ] 漏洞统计功能正确，返回by_workflow_status、by_customer_status、by_disclosure_status、by_severity
- [ ] 工作量统计功能正确
- [ ] 趋势分析功能正确
- [ ] 仪表盘配置功能正确

## 阶段十一：审计日志服务层

- [ ] AuditLogService接口定义完整
- [ ] AuditLogServiceImpl实现类正确实现所有方法
- [ ] 审计日志自动记录正确
- [ ] 审计日志查询功能正确

## 阶段十二：DTO类

- [ ] AssetCreateRequest包含所有必要字段（name、code、target_type、target_source、description、priority、team_id、owner_id）
- [ ] AssetUpdateRequest包含所有可更新字段
- [ ] AssetAccessRequest包含can_view_documents、can_view_environments、can_view_files、allowed_file_types、request_note字段
- [ ] AssetAccessReviewRequest包含action、review_note字段
- [ ] AssetClaimRequest包含note字段
- [ ] AssetMetadataRequest包含所有元数据字段和change_reason字段
- [ ] VulnerabilityCreateRequest包含所有必要字段
- [ ] VulnerabilityUpdateRequest包含所有可更新字段
- [ ] VulnerabilityStatusUpdateRequest包含status_type、status、comment字段
- [ ] DocumentCreateRequest包含title、category、summary、content、asset_id字段
- [ ] DocumentUpdateRequest包含title、content、changelog字段
- [ ] TaskCreateRequest包含title、type、reference_id、assignee_id、priority、due_date字段
- [ ] TaskUpdateRequest包含status字段
- [ ] NotificationResponse包含完整的通知信息
- [ ] NotificationSettingRequest包含type、enabled、notify_email、notify_sms、notify_webhook字段
- [ ] DashboardStatsResponse包含total、by_status、by_type、by_priority字段
- [ ] 所有DTO类添加必要的校验注解
- [ ] 所有DTO类添加Swagger @Schema注解

## 阶段十三：控制器层

- [ ] AssetController路由正确（/api/v1/assets）
- [ ] AssetController实现所有api_design.md定义的接口
- [ ] AssetController添加完整的Swagger注解
- [ ] AssetGroupController路由正确（/api/v1/asset-groups）
- [ ] AssetGroupController实现所有定义的接口
- [ ] TeamController路由正确（/api/v1/teams）
- [ ] TeamController实现所有定义的接口
- [ ] DocumentController路由正确（/api/v1/documents）
- [ ] DocumentController实现所有定义的接口
- [ ] EnvironmentController路由正确（/api/v1/environments）
- [ ] EnvironmentController结构正确（空实现）
- [ ] ToolController路由正确（/api/v1/tools）
- [ ] ToolController实现所有定义的接口
- [ ] TaskController路由正确（/api/v1/tasks）
- [ ] TaskController实现所有定义的接口
- [ ] KnowledgeController路由正确（/api/v1/knowledge）
- [ ] KnowledgeController实现所有定义的接口
- [ ] PermissionController路由正确（/api/v1/permissions）
- [ ] PermissionController实现所有定义的接口
- [ ] RoleController路由正确（/api/v1/roles）
- [ ] RoleController实现所有定义的接口
- [ ] NotificationController路由正确（/api/v1/notifications）
- [ ] NotificationController实现所有定义的接口
- [ ] DashboardController路由正确（/api/v1/dashboard）
- [ ] DashboardController实现所有定义的接口
- [ ] AuditLogController路由正确（/api/v1/audit-logs）
- [ ] AuditLogController实现所有定义的接口
- [ ] 所有Controller添加@SecurityRequirement(name = "bearerAuth")注解
- [ ] 所有Controller添加@Tag注解
- [ ] 所有接口方法添加@Operation注解
- [ ] 所有接口方法添加@ApiResponses注解

## 阶段十四：最终回归检查

- [ ] 项目编译无错误
- [ ] 所有实体字段与data_dictionary.md一致
- [ ] 所有API响应格式与api_design.md一致
- [ ] 所有Service方法签名正确
- [ ] 所有Controller路由正确
- [ ] 权限控制逻辑正确
- [ ] Swagger文档完整
- [ ] 无循环依赖
- [ ] 无未使用的导入
