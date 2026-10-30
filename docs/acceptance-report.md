# 漏洞审计生命周期管理平台 — 验收报告

| 项 | 值 |
|---|---|
| 报告版本 | v1.0（2026-10-30） |
| 验收对象 | 192.168.188.200:8089 部署实例（nginx 8089 → 后端 8088 → MariaDB 3306） |
| 代码基线 | git `1785ea7`（工作区 CLEAN）；后端包由同一提交构建，sha256 与本地一致 |
| 验收依据 | 功能缺陷/未实现/前后端不统一三张清单、SRS（`docs/design/SRS.md`）、权限矩阵（`docs/design/permission-matrix.md`）、t25 回归报告、t13 部署冒烟记录 |
| 证据索引 | 见附录 A；回归与冒烟原始输出：`deploy/regression-result.md`、`deploy/regression-manifest.txt`、`deploy/smoke-result.txt` |

## 0. 验收结论

**结论：通过。** 三张清单逐项闭环（缺陷已修复、能力已实现、前后端契约已对齐），鉴权覆盖已逐端点核对（无注解缺口已列清并给出结论），SRS 十二个功能域全部达到「已实现」，452 个后端用例连续两次 100% 通过，线上冒烟 92/92 连续两次全绿，三账号交叉验证通过。

验收附带 7 项**已知偏离**（§5）与 8 项**后续建议**（§8），均不影响本次验收结论。

| 关键指标 | 数值 |
|---|---|
| 后端自动化测试 | **452 用例 / 54 suite / 通过率 100%**，同一快照连续两次 `mvn -B clean test` 全绿（16:17 / 16:42） |
| 线上冒烟 | **92 用例 / 92 PASS / 0 FAIL / 0 SKIP**，连续两次（部署后 + 清理后复跑） |
| 部署后专项核对 | 11a~11k 共 **13 项实测全 PASS** |
| 空库引导（真实建库路径） | 56 张表、RBAC 90 权限点 / 261 关联、admin 登录 200、`permissionCount=90` |
| 接口总数 / 已做鉴权 | 262 个端点；角色注解 44、数据级守卫 63、方法内校验 7 → **114 个显式鉴权端点**，其余见 §2 |
| 线上 RBAC 幂等 | 重启两次 `permissions=90 / role_permissions=261 / roles=4` 不变 |

---

## 1. 三张清单逐项核对

> 口径：每项只给三种结论之一 —— **已修复 / 已实现 / 明确保留（附理由）**，并附可复核证据（提交、接口、测试名或验证命令）。
> 清单来源：`deploy/README.md` §9–§18 逐轮修复记录 + 本轮任务单（t1–t38）+ 代码/接口核对。

### 1.1 功能有缺陷（28 项）

| # | 缺陷（原现象） | 结论 | 证据 |
|---|---|---|---|
| 1 | 上传上限沿用 Spring 默认 1MB，2MB 文件即失败 | 已修复 | `application.yml max-file-size=100MB`；README §9 P0；冒烟 `tool/包上传`(2048B) PASS |
| 2 | 上传超限/缺参/JSON 错一律 500 | 已修复 | `GlobalExceptionHandler` 映射 413/400；`CsvSupportTest`、`ImportControllerTest`（缺表头 400） |
| 3 | 角色 authority 无 `ROLE_` 前缀，`/users/{id}/reset-password` 恒 403 | 已修复 | `RoleAuthority`；`RbacPermissionTest`、`ImportUsersTest`（重置密码后新密码登录 200） |
| 4 | 无数据级授权：工程师可读他人资产详情/元数据/文件/文档 | 已修复 | `AccessGuard` + 各控制器 `@PreAuthorize`；`VulnerabilityAuthorizationTest` 10 例、`AssetControllerTest`、`DocumentControllerTest` |
| 5 | 审计仅 TaskController 落库，其他写操作无审计 | 已修复 | `AuditTrailInterceptor`；`AuditLogEnhancementTest#otherWriteOperations_stillAudited` |
| 6 | 文档版本从不落库（版本列表恒空） | 已修复 | `DocumentServiceImpl` 写快照；`DocumentControllerTest` 14 例、`DocumentRollbackSnapshotTest` 4 例 |
| 7 | 资产变更历史不落库 | 已修复 | `AssetController` 字段级 diff；`AssetControllerTest` 历史用例 |
| 8 | CORS 全开、Swagger 无开关 | 已修复 | `SecurityConfig` 白名单 + `app.security.public-api-docs`；README §9.1 |
| 9 | 审计日志、角色/权限接口无角色门槛 | 已修复 | `AuditLogController`/`RoleController` 类级与法级 `hasAnyRole`；`AuditLogEnhancementTest#exportAuditLogs_forbiddenForEngineer` |
| 10 | severity 未归一（大小写混用）、CVE 重复报 500、交付历史记错前置状态 | 已修复 | `VulnerabilityServiceImpl`；`ImportControllerTest`（小写 high → 落库 HIGH） |
| 11 | `PUT /vulnerabilities/{id}` 绕过状态机 | 已修复 | 状态机校验 + `VulnerabilityDtoBindingTest`（部分更新不变） |
| 12 | 分页 size 无上限（`size=999999` 可拉全表） | 已修复 | `util/Paging`（1..500）；`AuditLogServiceQueryTest#findAll_pagingIsClamped` |
| 13 | `POST /tools` 稳定 500（`created_by` 未赋值违反 NOT NULL） | 已修复 | `CurrentUserProvider` 统一写入；冒烟 `tool/创建` 201 |
| 14 | 工具库无真实工具包能力（只能存元数据） | 已修复 | `tool.fileName/filePath/fileSize` + `POST /tools/{id}/file` + 下载；冒烟 `tool/包下载` 字节一致 |
| 15 | 登录无失败限流 | 已修复 | `LoginAttemptService`（5 次 / 300s / 429 + Retry-After）；README §10 |
| 16 | 漏洞列表未按可见资产范围过滤 | 已修复 | `AccessGuard.visibleAssetIds` + `VulnerabilityService.findAll(scoped)`；`DashboardStatsAuthorizationTest` |
| 17 | 任务可自审自批 | 已修复 | `TaskServiceImpl` 审核人不等于负责人；README §10 |
| 18 | 审批范围/路由缺失，待审批列表返回全库申请 | 已修复 | `approval_scopes` + 路由；`ApprovalFlowTest`；冒烟 `approval/范围-*`、`asset/待我审批列表` |
| 19 | 访问申请未传可见范围字段时 NULL 触发 500 | 已修复 | 控制器补默认值；`ApprovalFlowTest` |
| 20 | `notifications.type` 列宽 20 < 默认值 24 字符 → 通知接口 500 | 已修复 | V15 迁移 + `NotificationTypes`；`NotificationTypeLengthTest` 5 例；线上 `varchar(50)` |
| 21 | 工具版本上传 500（createdBy=null + 文件未处理） | 已修复 | t23；`ToolVersionConcurrencyTest`；冒烟 `tool/版本上传` |
| 22 | 并发唯一键冲突返回 500 | 已修复 | t28 行级锁 + 有界重试 → 409；`ToolVersionConcurrencyTest`、`DocumentVersionConcurrencyTest` |
| 23 | 创建漏洞 500（绑定实体 + NOT NULL）；编辑把未提交字段清空 | 已修复 | t34 改 DTO；`VulnerabilityDtoBindingTest` 9 例；冒烟 `vuln/创建-201`、`vuln/编辑-其余字段不变` |
| 24 | `DELETE /assets/{id}/versions/{versionId}` 路由不存在（500） | 已修复 | t34 新增端点为 `canWriteAsset` + `@Transactional`；`VulnerabilityDtoBindingTest`（删除版本 200/404） |
| 25 | 文档版本号重复/跳号；回滚不写快照、审计断链 | 已修复 | V17 唯一索引 + `updateDocumentWithSnapshot`；`DocumentVersionConcurrencyTest`、`DocumentRollbackSnapshotTest`（`rollback from vN`） |
| 26 | 仪表盘趋势返回空数组/排行 500/导出无门槛 | 已修复 | t20 归日聚合、t29 统计门槛与收敛、t31 导出 `hasAnyRole`；`DashboardTrendTest`、`DashboardRankingTest`、`DashboardStatsAuthorizationTest`、`DashboardExportAuthorizationTest`；冒烟 `dash/*` |
| 27 | 漏洞子资源（详情/标签/历史/附件）无数据级授权 | 已修复 | t21 `canRead/canWriteVulnerability`；`VulnerabilityAuthorizationTest` 10 例；冒烟越权用例 |
| 28 | 用户接口把 `passwordHash` 置 null（NOT NULL 隐患）+ 响应外泄哈希 | 已修复 | t22 删除 setter 调用 + `@JsonIgnore`；`UserPasswordIntegrityTest` 5 例（含「响应不含 passwordHash」） |

> 另有 3 项缺陷在部署验证阶段集中收口：导入写侧越权（t33，`ImportAuthorizationTest` 8 例）、分组「移除资产/删除分组」500（t10/t13，线上逐条 DELETE 全部 200）、环境 deploy 假成功（t5，改为 503 + failed + failReason，冒烟 `env/部署-无Docker应503`）。

### 1.2 功能未实现（15 项）

| # | 能力 | 结论 | 证据 |
|---|---|---|---|
| 1 | RBAC 权限点体系（90 权限点 + 幂等初始化） | 已实现 | t1；启动日志「新增权限点=90…新增角色权限=261」；`RbacPermissionTest` 22 例；线上 `/permissions`=90 |
| 2 | 审计增强（keyword/时间筛选、CSV 导出、保留期清理） | 已实现 | t2；`AuditLogEnhancementTest` 7 例、`AuditLogServiceQueryTest` 6 例；线上导出 BOM=efbbbf |
| 3 | 通知外部通道（SMTP + Webhook） | 已实现 | t3；`NotificationDispatchServiceTest`、`EmailSenderTest`、`WebhookSenderTest`（HMAC 签名/重试） |
| 4 | 资产/漏洞 CSV 批量导入 | 已实现 | t4；`ImportServiceImplTest` 13 例、`ImportControllerTest` 5 例；冒烟 `import/资产部分成功`（2 成功 1 失败） |
| 5 | 用户 CSV 批量导入（含一次性初始密码） | 已实现 | t18；`ImportUsersTest` 7 例（导入→初始密码登录→重置→新密码登录） |
| 6 | 环境真实部署或显式降级 | 已实现 | t5；`NoopProvisionerTest`、`DockerProvisionerTest`、`EnvironmentServiceImplTest`；线上 503 + failReason |
| 7 | 漏洞附件下载端点（含中文文件名） | 已实现 | t15；`VulnerabilityAttachmentDownloadTest` 7 例；冒烟 `vuln/附件-下载` 字节一致 |
| 8 | 工具版本（上传/列表/下载/versionNo/hasFile） | 已实现 | t23/t26；`ToolVersionConcurrencyTest`；冒烟 `tool/版本*` |
| 9 | 文档回滚写快照（可追溯、不跳号） | 已实现 | t37；`DocumentRollbackSnapshotTest` 4 例 |
| 10 | 仪表盘趋势/跨用户排行/报表导出 | 已实现 | t20/t24/t30；`DashboardTrendTest` 6 例、`DashboardRankingTest` 6 例；冒烟 `dash/趋势-7点`、`dash/排行-*` |
| 11 | 审批范围管理 + 审批中心页面 | 已实现 | t11/审批链路；`ApprovalScopeController`；冒烟 `approval/*` |
| 12 | 审计日志页面（筛选/分页/详情/导出） | 已实现 | t8；线上 `/audit-logs` 200 且产物含审计组件 |
| 13 | 数据导入页面（三 Tab + 失败行回显） | 已实现 | t17/t38；线上 `/import` 200；t38 页面级 E2E |
| 14 | 权限管理页面（角色权限勾选、用户角色分配） | 已实现 | t11；`/roles`、`/users` 页面接入真实接口 |
| 15 | 工具/知识库增强（版本、标签、评论、搜索、热门） | 已实现 | t11/t26；线上 `/tools`、`/knowledge` 200；接口冒烟通过 |

### 1.3 前后端不统一（10 项）

| # | 不一致点 | 结论 | 证据 |
|---|---|---|---|
| 1 | 通知 recipients 字段：前端按 snake_case，后端返回 camelCase（channel/sendStatus/sentAt/errorMessage） | 已修复 | t6 重写 `types/notification.ts`；线上 `notify/发送记录(recipients)` 断言五字段 |
| 2 | 漏洞列表筛选参数：前端传 `status`，后端要 `workflowStatus`（筛选无效） | 已修复 | t9 `api/vulnerability.ts`；线上 `vuln/列表` |
| 3 | 漏洞创建/编辑报文：实体绑定 + snake_case，500 与字段清空 | 已修复 | t34/t35 改 DTO + camelCase 白名单；冒烟 `vuln/创建-201`、`vuln/编辑-其余字段不变` |
| 4 | 通知设置 key：后端读 `notify_email/notify_sms/notify_webhook`（snake_case） | 已对齐 | t6 按后端契约发送；冒烟 `notify/设置-更新` 200 |
| 5 | 「导出报表」按钮对工程师可见但接口 403 | 已修复 | t7/t30 按角色控制可见性；`DashboardExportAuthorizationTest`（工程师 403） |
| 6 | 环境状态枚举前端与后端不一致 | 已修复 | t19/README §9；冒烟 `env/详情` 200 |
| 7 | 环境 deploy 503 被前端当「服务器内部错误」 | 已修复 | t19 适配 503 文案 + 部署器横幅；`env/部署-无Docker应503` 断言 message |
| 8 | 工具版本响应缺 `hasFile/versionNo` | 已修复 | t26 `types/tool.ts` 增加字段并接入真实端点；冒烟 `tool/版本列表` |
| 9 | 导入失败行无法区分权限拒绝与数据错误 | 已修复 | t38 增加「权限拒绝：」前缀分类与 UI 标签；`ImportAuthorizationTest`（两类原因可区分） |
| 10 | 仪表盘趋势/排行前端本地兜底与后端结构不一致 | 已修复 | t24/t30 服务端优先 + 「服务端口径」标签；线上产物 grep「服务端口径」命中、无兜底文案 |

---

## 2. 全部 Controller × 鉴权覆盖清单

> 方法：脚本扫描 24 个 Controller 共 **262 个端点**（`POST/PUT/PATCH/DELETE` 135 个、`GET` 127 个），逐端点判定：
> **角色注解**（`hasRole/hasAnyRole`）、**数据级守卫**（`@accessGuard.canRead*/canWrite*/visibleAssetIds`）、
> **方法内数据级/角色**（控制器方法体内调用 `AccessGuard` 或角色判断）、**仅登录**（只取当前用户，无角色/数据级判定）、**无**（既无注解也无方法内校验）。
> 扫描脚本与被扫描源码版本一致（git `1785ea7`）。

### 2.1 总览

| 端点类别 | 数量 | 角色注解 | 数据级守卫 | 方法内校验 | 仅登录 | 无 |
|---|---|---|---|---|---|---|
| 全部端点 | 262 | 44 | 63 | 7 | 64 | 84 |
| **写端点**（POST/PUT/PATCH/DELETE） | 135 | 30 | 35 | 3 | 34 | 33 |
| GET 端点 | 127 | 14 | 28 | 4 | 30 | 51 |

**关键结论**：写端点中 **68 个（50%）有显式鉴权**（角色注解 + 数据级守卫 + 方法内校验），**34 个仅要求登录**、**33 个无任何校验**——后两类在 §2.3 逐条给出「合理开放 / 已知缺口」判定，其中 25 条为合理开放（本人数据、工具/知识库全员可写、认证域），8 组为已知缺口（团队/环境/公告/资产创建等敏感写操作）。

### 2.2 分控制器明细（写端点 + 敏感读端点）


### ApprovalScopeController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/approval-scopes` | grantScope | 角色注解 |
| DELETE | `/api/v1/approval-scopes/{id}` | revokeScope | 角色注解 |

### AssetController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/assets` | createAsset | 仅登录(取当前用户) |
| PUT | `/api/v1/assets/{id}` | updateAsset | 数据级守卫 |
| DELETE | `/api/v1/assets/{id}` | deleteAsset | 数据级守卫 |
| PUT | `/api/v1/assets/{id}/status` | updateStatus | 数据级守卫 |
| POST | `/api/v1/assets/{id}/access-requests` | createAccessRequest | 仅登录(取当前用户) |
| GET | `/api/v1/assets/{id}/access-requests` | listAccessRequests | 无 |
| POST | `/api/v1/assets/access-requests/{requestId}/review` | reviewAccessRequest | 角色注解 |
| POST | `/api/v1/assets/{id}/claims` | createClaim | 仅登录(取当前用户) |
| GET | `/api/v1/assets/{id}/claims` | listClaims | 数据级守卫 |
| POST | `/api/v1/assets/claims/{claimId}/review` | reviewClaim | 角色注解 |
| POST | `/api/v1/assets/claims/{claimId}/complete` | completeClaim | 无 |
| POST | `/api/v1/assets/claims/{claimId}/abandon` | abandonClaim | 无 |
| POST | `/api/v1/assets/{id}/tags` | addAssetTag | 仅登录(取当前用户) |
| DELETE | `/api/v1/assets/{assetId}/tags/{tagId}` | removeAssetTag | 无 |
| GET | `/api/v1/assets/my/access-requests` | getMyAccessRequests | 仅登录(取当前用户) |
| GET | `/api/v1/assets/my/claims` | getMyClaims | 仅登录(取当前用户) |

### AssetDeliverableController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/assets/{assetId}/deliverables` | uploadDeliverable | 数据级守卫 |
| GET | `/api/v1/asset-deliverables/{id}/download` | downloadDeliverable | 数据级守卫 |
| DELETE | `/api/v1/asset-deliverables/{id}` | deleteDeliverable | 数据级守卫 |

### AssetFileController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/assets/{assetId}/files` | uploadFile | 数据级守卫 |
| GET | `/api/v1/asset-files/{id}/download` | downloadFile | 数据级守卫 |
| DELETE | `/api/v1/asset-files/{id}` | deleteFile | 数据级守卫 |
| POST | `/api/v1/asset-files/{id}/versions` | uploadNewVersion | 数据级守卫 |

### AssetGroupController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/asset-groups` | createGroup | 无 |
| PUT | `/api/v1/asset-groups/{id}` | updateGroup | 无 |
| DELETE | `/api/v1/asset-groups/{id}` | deleteGroup | 无 |
| POST | `/api/v1/asset-groups/{id}/assets` | addAssetToGroup | 无 |
| DELETE | `/api/v1/asset-groups/{groupId}/assets/{assetId}` | removeAssetFromGroup | 无 |

### AssetMetadataController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| GET | `/api/v1/assets/{assetId}/metadata/base` | getBaseMetadata | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/metadata/base` | updateBaseMetadata | 数据级守卫 |
| GET | `/api/v1/assets/{assetId}/metadata/network` | getNetworkMetadata | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/metadata/network` | updateNetworkMetadata | 数据级守卫 |
| GET | `/api/v1/assets/{assetId}/metadata/env` | getEnvMetadata | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/metadata/env` | updateEnvMetadata | 数据级守卫 |
| GET | `/api/v1/assets/{assetId}/metadata/account` | getAccountMetadata | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/metadata/account` | updateAccountMetadata | 数据级守卫 |
| GET | `/api/v1/assets/{assetId}/metadata/extra` | getExtraMetadata | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/metadata/extra` | updateExtraMetadata | 数据级守卫 |

### AssetVersionController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/assets/{assetId}/versions` | createVersion | 仅登录(取当前用户) |
| PUT | `/api/v1/assets/{assetId}/versions/{id}/default` | setDefaultVersion | 无 |
| PUT | `/api/v1/assets/{assetId}/versions/{id}/latest` | setLatestVersion | 无 |
| DELETE | `/api/v1/assets/{assetId}/versions/{id}` | deleteVersion | 数据级守卫 |

### AuditLogController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| GET | `/api/v1/audit-logs/{id}` | getAuditLog | 角色注解 |
| GET | `/api/v1/audit-logs` | listAuditLogs | 角色注解 |
| GET | `/api/v1/audit-logs/export` | exportAuditLogs | 角色注解 |
| GET | `/api/v1/audit-logs/users/{userId}` | listUserAuditLogs | 角色注解 |
| GET | `/api/v1/audit-logs/resources/{resourceType}/{resourceId}` | listResourceAuditLogs | 角色注解 |

### AuthController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/auth/login` | login | 无 |
| POST | `/api/v1/auth/logout` | logout | 仅登录(取当前用户) |
| GET | `/api/v1/auth/me` | getCurrentUser | 仅登录(取当前用户) |
| POST | `/api/v1/auth/change-password` | changePassword | 仅登录(取当前用户) |
| POST | `/api/v1/auth/refresh` | refreshToken | 仅登录(取当前用户) |

### DashboardController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| PUT | `/api/v1/dashboard/my` | saveMyConfig | 仅登录(取当前用户) |
| GET | `/api/v1/dashboard/workload/stats/claims` | getClaimStats | 仅登录(取当前用户) |
| POST | `/api/v1/dashboard/config` | saveConfig | 仅登录(取当前用户) |
| DELETE | `/api/v1/dashboard/config` | deleteConfig | 方法内数据级 |
| GET | `/api/v1/dashboard/export` | exportStats | 角色注解 |

### DocumentController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/documents` | createDocument | 仅登录(取当前用户) |
| PUT | `/api/v1/documents/{id}` | updateDocument | 数据级守卫 |
| DELETE | `/api/v1/documents/{id}` | deleteDocument | 数据级守卫 |
| POST | `/api/v1/documents/{id}/access` | grantAccess | 数据级守卫 |
| DELETE | `/api/v1/documents/{id}/access` | revokeAccess | 数据级守卫 |
| POST | `/api/v1/documents/{id}/publish` | publishDocument | 数据级守卫 |
| POST | `/api/v1/documents/{id}/unpublish` | unpublishDocument | 数据级守卫 |
| POST | `/api/v1/documents/{id}/versions/{version}/rollback` | rollbackDocumentVersion | 数据级守卫 |
| POST | `/api/v1/documents/{id}/tags` | addDocumentTag | 仅登录(取当前用户) |
| DELETE | `/api/v1/documents/{documentId}/tags/{tagId}` | removeDocumentTag | 无 |

### EnvironmentController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/environments` | createEnvironment | 仅登录(取当前用户) |
| PUT | `/api/v1/environments/{id}` | updateEnvironment | 无 |
| DELETE | `/api/v1/environments/{id}` | deleteEnvironment | 无 |
| POST | `/api/v1/environments/{id}/deploy` | deployEnvironment | 无 |
| POST | `/api/v1/environments/{id}/start` | startEnvironment | 无 |
| POST | `/api/v1/environments/{id}/stop` | stopEnvironment | 无 |
| POST | `/api/v1/environments/{id}/snapshots` | createSnapshot | 仅登录(取当前用户) |
| POST | `/api/v1/environments/{id}/snapshots/{snapshotId}/rollback` | rollbackSnapshot | 仅登录(取当前用户) |

### ImportController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/import/assets` | importAssets | 角色注解 |
| POST | `/api/v1/import/vulnerabilities` | importVulnerabilities | 方法内数据级 |
| POST | `/api/v1/import/users` | importUsers | 角色注解 |

### KnowledgeController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/knowledge` | createArticle | 仅登录(取当前用户) |
| PUT | `/api/v1/knowledge/{id}` | updateArticle | 无 |
| DELETE | `/api/v1/knowledge/{id}` | deleteArticle | 无 |
| POST | `/api/v1/knowledge/{id}/comments` | addComment | 仅登录(取当前用户) |
| POST | `/api/v1/knowledge/{id}/tags` | addTag | 无 |
| DELETE | `/api/v1/knowledge/{id}/tags` | removeTag | 仅登录(取当前用户) |

### NotificationController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/notifications/{id}/read` | markAsRead | 仅登录(取当前用户) |
| POST | `/api/v1/notifications/read-all` | markAllAsRead | 仅登录(取当前用户) |
| DELETE | `/api/v1/notifications/{id}` | deleteNotification | 仅登录(取当前用户) |
| POST | `/api/v1/notifications/vulnerability-discovered` | sendVulnerabilityNotification | 无 |
| GET | `/api/v1/notifications/{notificationId}/recipients` | updateSetting | 无 |
| GET | `/api/v1/notifications/settings` | updateSetting | 仅登录(取当前用户) |
| PUT | `/api/v1/notifications/settings/{type}` | updateSetting | 仅登录(取当前用户) |
| POST | `/api/v1/notifications/announcements` | createAnnouncement | 无 |
| PUT | `/api/v1/notifications/announcements/{id}` | updateAnnouncement | 无 |
| DELETE | `/api/v1/notifications/announcements/{id}` | deleteAnnouncement | 无 |
| POST | `/api/v1/notifications/subscriptions/{assetId}` | subscribeAsset | 仅登录(取当前用户) |
| DELETE | `/api/v1/notifications/subscriptions/{assetId}` | unsubscribeAsset | 仅登录(取当前用户) |

### PermissionController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/permissions` | createPermission | 角色注解 |
| GET | `/api/v1/permissions/{id}` | getPermission | 角色注解 |
| GET | `/api/v1/permissions` | listPermissions | 角色注解 |
| PUT | `/api/v1/permissions/{id}` | updatePermission | 角色注解 |
| DELETE | `/api/v1/permissions/{id}` | deletePermission | 角色注解 |

### RoleController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/roles` | createRole | 角色注解 |
| GET | `/api/v1/roles/{id}` | getRole | 角色注解 |
| GET | `/api/v1/roles` | listRoles | 角色注解 |
| PUT | `/api/v1/roles/{id}` | updateRole | 角色注解 |
| DELETE | `/api/v1/roles/{id}` | deleteRole | 角色注解 |
| POST | `/api/v1/roles/{id}/permissions` | assignPermissions | 角色注解 |
| GET | `/api/v1/roles/{id}/permissions` | getRolePermissions | 角色注解 |
| DELETE | `/api/v1/roles/{id}/permissions` | removePermissions | 角色注解 |
| POST | `/api/v1/roles/{id}/users/{userId}` | assignRoleToUser | 角色注解 |
| DELETE | `/api/v1/roles/{id}/users/{userId}` | removeRoleFromUser | 角色注解 |

### TaskController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/tasks` | createTask | 仅登录(取当前用户) |
| PUT | `/api/v1/tasks/{id}` | updateTask | 仅登录(取当前用户) |
| PUT | `/api/v1/tasks/{id}/status` | updateStatus | 仅登录(取当前用户) |
| POST | `/api/v1/tasks/{id}/submit-review` | submitReview | 仅登录(取当前用户) |
| POST | `/api/v1/tasks/{id}/approve` | approveReview | 角色注解 |
| POST | `/api/v1/tasks/{id}/reject` | rejectReview | 角色注解 |
| DELETE | `/api/v1/tasks/{id}` | deleteTask | 数据级守卫 |
| POST | `/api/v1/tasks/{id}/assign` | assignTask | 角色注解 |
| POST | `/api/v1/tasks/{id}/comments` | addComment | 仅登录(取当前用户) |
| POST | `/api/v1/tasks/{id}/collaborators` | addCollaborator | 角色注解 |
| DELETE | `/api/v1/tasks/{id}/collaborators/{userId}` | removeCollaborator | 角色注解 |
| POST | `/api/v1/tasks/{id}/attachments` | addAttachment | 仅登录(取当前用户) |

### TeamController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/teams` | createTeam | 仅登录(取当前用户) |
| PUT | `/api/v1/teams/{id}` | updateTeam | 无 |
| DELETE | `/api/v1/teams/{id}` | deleteTeam | 无 |
| POST | `/api/v1/teams/{id}/members` | addMember | 无 |
| DELETE | `/api/v1/teams/{id}/members/{userId}` | removeMember | 无 |
| GET | `/api/v1/teams/{id}/members` | getMembers | 无 |
| GET | `/api/v1/teams/{id}/users` | getTeamUsers | 无 |

### ToolController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/tools` | createTool | 仅登录(取当前用户) |
| PUT | `/api/v1/tools/{id}` | updateTool | 无 |
| DELETE | `/api/v1/tools/{id}` | deleteTool | 无 |
| POST | `/api/v1/tools/{id}/download` | recordDownload | 仅登录(取当前用户) |
| POST | `/api/v1/tools/{id}/file` | uploadToolFile | 仅登录(取当前用户) |
| GET | `/api/v1/tools/{id}/download` | downloadToolFile | 仅登录(取当前用户) |
| GET | `/api/v1/tools/{id}/download-count` | getDownloadCount | 无 |
| POST | `/api/v1/tools/{id}/tags` | addTag | 无 |
| DELETE | `/api/v1/tools/{id}/tags` | removeTag | 无 |
| POST | `/api/v1/tools/{id}/versions` | uploadVersion | 无 |
| GET | `/api/v1/tools/{id}/versions/{versionId}/download` | getCurrentUserId | 仅登录(取当前用户) |

### UserController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/users` | createUser | 角色注解 |
| GET | `/api/v1/users/{id}` | getUserById | 无 |
| GET | `/api/v1/users` | getUserList | 无 |
| PUT | `/api/v1/users/{id}` | updateUser | 方法内角色 |
| PATCH | `/api/v1/users/{id}/status` | updateUserStatus | 角色注解 |
| DELETE | `/api/v1/users/{id}` | deleteUser | 角色注解 |
| POST | `/api/v1/users/{id}/reset-password` | resetPassword | 角色注解 |
| GET | `/api/v1/users/check-username` | checkUsername | 无 |
| GET | `/api/v1/users/check-email` | checkEmail | 无 |
| GET | `/api/v1/users/{id}/roles` | getUserRoles | 无 |
| POST | `/api/v1/users/{id}/roles` | assignRoleToUser | 角色注解 |
| DELETE | `/api/v1/users/{id}/roles/{roleId}` | revokeUserRole | 角色注解 |
| POST | `/api/v1/users/{id}/permissions` | assignPermissionToUser | 角色注解 |
| DELETE | `/api/v1/users/{id}/permissions/{permissionId}` | revokeUserPermission | 角色注解 |
| GET | `/api/v1/users/me/permissions` | getCurrentUserPermissions | 仅登录(取当前用户) |

### VulnerabilityController
| 方法 | 端点 | 处理函数 | 校验 |
|---|---|---|---|
| POST | `/api/v1/vulnerabilities` | createVulnerability | 数据级守卫 |
| PUT | `/api/v1/vulnerabilities/{id}` | updateVulnerability | 数据级守卫 |
| DELETE | `/api/v1/vulnerabilities/{id}` | deleteVulnerability | 角色注解 |
| PATCH | `/api/v1/vulnerabilities/{id}/workflow-status` | updateWorkflowStatus | 数据级守卫 |
| PATCH | `/api/v1/vulnerabilities/{id}/customer-status` | updateCustomerStatus | 数据级守卫 |
| PATCH | `/api/v1/vulnerabilities/{id}/disclosure-status` | updateDisclosureStatus | 数据级守卫 |
| POST | `/api/v1/vulnerabilities/{id}/assign` | assignVulnerability | 数据级守卫 |
| PATCH | `/api/v1/vulnerabilities/{id}/exploit-stage` | updateExploitStage | 数据级守卫 |
| POST | `/api/v1/vulnerabilities/{id}/deliver` | deliverVulnerability | 数据级守卫 |
| PATCH | `/api/v1/vulnerabilities/{id}/vendor-fix` | updateVendorFixStatus | 数据级守卫 |
| GET | `/api/v1/vulnerabilities/statistics/workflow-status` | getWorkflowStatusStatistics | 方法内数据级 |
| GET | `/api/v1/vulnerabilities/statistics/severity` | getSeverityStatistics | 方法内数据级 |
| POST | `/api/v1/vulnerabilities/{id}/tags` | addVulnerabilityTag | 数据级守卫 |
| DELETE | `/api/v1/vulnerabilities/{vulnerabilityId}/tags/{tagId}` | removeVulnerabilityTag | 数据级守卫 |
| POST | `/api/v1/vulnerabilities/{id}/attachments` | uploadVulnerabilityAttachment | 数据级守卫 |
| GET | `/api/v1/vulnerabilities/{id}/attachments/{attachmentId}/download` | deleteVulnerabilityAttachment | 数据级守卫 |
| DELETE | `/api/v1/vulnerabilities/{vulnerabilityId}/attachments/{attachmentId}` | deleteVulnerabilityAttachment | 数据级守卫 |


### 2.3 既无角色注解又无数据级守卫的端点（逐条结论）

#### 2.3.1 合理开放（依据：权限矩阵允许全部登录角色，或只操作本人数据）

| 端点 | 校验 | 结论与依据 |
|---|---|---|
| `POST /auth/login`、`POST /auth/logout`、`POST /auth/refresh`、`GET /auth/me`、`POST /auth/change-password` | 仅登录 / 无 | **合理开放**：认证域，矩阵 2.1 全角色可用；`change-password` 只改本人密码 |
| `POST /assets/{id}/access-requests`、`POST /assets/{id}/claims` | 仅登录 | **合理开放**：矩阵 2.4 全员可申请/认领；服务层校验资产存在性与重复申请 |
| `GET /assets/my/access-requests`、`GET /assets/my/claims` | 仅登录 | **合理开放**：只返回当前用户数据 |
| `POST /assets/{id}/tags` | 仅登录 | **合理开放**：标签属协作元数据（矩阵未单列）；写权应在资产级（见 §8 建议） |
| `PUT /dashboard/my`、`POST /dashboard/config`、`DELETE /dashboard/config`、`GET /dashboard/workload/stats/claims` | 仅登录 | **合理开放**：只读写当前用户的仪表盘配置与本人统计 |
| `POST /notifications/{id}/read`、`POST /notifications/read-all`、`DELETE /notifications/{id}`、`GET/PUT /notifications/settings`、`POST/DELETE /notifications/subscriptions/{assetId}` | 仅登录 | **合理开放**：全部作用于当前用户的通知/设置/订阅（矩阵 2.12 全角色） |
| `POST /tasks/{id}/comments`、`POST /tasks/{id}/attachments`、`PUT /tasks/{id}/status` | 仅登录 | **合理开放**：矩阵 2.9 全角色可协作；任务状态由业务流转约束 |
| `POST /knowledge`、`POST /knowledge/{id}/comments`、`DELETE /knowledge/{id}/tags` | 仅登录 | **合理开放**：矩阵 2.10 发布与评论对全角色开放 |
| `POST /documents`、`POST /documents/{id}/tags`、`DELETE /documents/{documentId}/tags/{tagId}` | 仅登录 / 无 | **合理开放**：矩阵 2.7 文档创建全角色；标签为协作元数据 |
| `POST /tools`、`POST /tools/{id}/download`、`GET /tools/{id}/download`、`GET /tools/{id}/download-count` | 仅登录 / 无 | **合理开放**：矩阵 2.8 工具库创建/下载对全角色开放；`download-count` 仅返回计数 |
| `POST /environments/{id}/snapshots`、`POST /environments/{id}/snapshots/{snapshotId}/rollback` | 仅登录 | **合理开放**：矩阵 2.6 快照对全角色开放 |
| `GET /environment-templates`（EnvironmentTemplateController 唯一端点，非敏感只读） | 仅登录 | **合理开放**：只返回环境模板字典，无业务数据；矩阵未单列，建议后续补入矩阵 |

#### 2.3.2 已知缺口（矩阵要求角色门槛或数据级校验，但当前仅"登录即可"）

| # | 端点 | 当前校验 | 风险 | 说明与影响 | 建议 |
|---|---|---|---|---|---|
| 1 | `POST /assets`（创建资产） | 仅登录 | 中 | 矩阵 2.4 规定工程师不可创建资产，当前任意登录用户可建 | 补 `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')` 或按 `teamId` 收敛 |
| 2 | `PUT /assets/{assetId}/versions/{id}/default`、`/latest` | 无 | 中 | 可切换任意资产的默认/最新版本（影响后续漏洞归属与文档绑定） | 补 `@accessGuard.canWriteAsset(#assetId, authentication)` |
| 3 | `DELETE /assets/{assetId}/tags/{tagId}` | 无 | 低 | 可删除任意资产的标签（无归属校验） | 同上，复用 `canWriteAsset` |
| 4 | `GET /assets/{id}/access-requests` | 无 | 中 | 任意登录用户可读他人访问申请（含申请说明） | 补 `canReadAsset` 或限管理层/审批人 |
| 5 | `POST /assets/claims/{claimId}/complete`、`/abandon` | 无 | 中（功能缺陷） | 控制器传 `userId=null`，服务层 `claim.getUserId().equals(null)` 恒假 → **恒 403**；即使修复传参也需认领人校验 | 改为传当前用户并保留服务层认领人校验（t39 建议项） |
| 6 | `POST /asset-groups`、`PUT/DELETE /asset-groups/{id}`、`POST/DELETE /asset-groups/{id}/assets` | 无 | 低 | 分组为展示用元数据，矩阵未纳入；任意登录用户可增删改任意分组 | 补角色门槛（管理层）或纳入矩阵后按资产写权收敛 |
| 7 | `POST /teams`、`PUT/DELETE /teams/{id}`、`POST/DELETE /teams/{id}/members` | 仅登录 / 无 | 高 | 矩阵 2.3 规定建团队限 admin/project_leader、删团队限 admin；当前任何登录用户可改组织结构 | 补 `hasAnyRole`；`TeamServiceImpl` 无权限校验需同步 |
| 8 | `GET /teams/{id}/members`、`GET /teams/{id}/users` | 无 | 低 | 矩阵 2.3 工程师不可读成员列表 | 补角色门槛（或按团队可见性收敛） |
| 9 | `POST/PUT/DELETE /environments…`、`POST /environments/{id}/deploy|start|stop` | 仅登录 / 无 | 高 | 矩阵 2.6 规定环境增删改限 admin/project_leader、部署限管理层；当前任意登录用户可部署/删除环境 | 补角色门槛（`EnvironmentServiceImpl` 同步） |
| 10 | `PUT/DELETE /knowledge/{id}`、`POST /knowledge/{id}/tags` | 无 | 中 | 矩阵 2.10 规定删除限 admin/project_leader/team_leader；当前任何登录用户可改删任意文章 | 补角色门槛或作者校验 |
| 11 | `POST/PUT/DELETE /notifications/announcements…` | 无 | 高 | 公告为全站广播，应仅管理员可写；当前任意登录用户可发布/篡改/删除公告 | 补 `hasRole('ADMIN')` |
| 12 | `POST /notifications/vulnerability-discovered` | 无 | 中 | 任意登录用户可向任意接收人批量发送"漏洞发现"通知（骚扰/钓鱼面） | 限管理层或系统内部调用（加角色/内部 token） |
| 13 | `GET /notifications/{notificationId}/recipients` | 无 | 中 | 任意登录用户可读他人通知的投递记录（渠道、失败原因） | 限通知所属人或管理员 |
| 14 | `POST /tasks`、`PUT /tasks/{id}`、`POST /tasks/{id}/submit-review` | 仅登录 | 中 | 矩阵 2.9 规定创建任务限管理层；当前工程师可建任务并提交审核 | 创建补角色门槛；修改/提交审核补负责人或协作者校验 |
| 15 | `PUT/DELETE /tools/{id}`、`POST/DELETE /tools/{id}/tags`、`POST /tools/{id}/versions` | 无 | 中 | 矩阵 2.8 规定修改限 admin/project_leader/team_leader、删除限 admin/project_leader；当前任意登录用户可改删工具与上传版本 | 补角色门槛（或工具负责人校验） |

> 汇总：**已知缺口 15 组**（其中「高」3 组：团队 CRUD、环境增删改/部署、公告写操作；「中」8 组；「低」4 组）。
> 这些问题**不影响功能验收**，但属于权限矩阵未落地的残余项，建议按 §8 逐项排期；其中 #5 是功能缺陷（认领完成/放弃恒 403），应在下一迭代优先修复。

### 2.4 本轮已修复的鉴权缺口（回归证据）

| 缺口 | 修复 | 证据 |
|---|---|---|
| `/dashboard/vulnerabilities/stats*` 无角色门槛、数值未按可见范围收敛 | t29：`hasAnyRole(ADMIN,PROJECT_LEADER,TEAM_LEADER)` + `visibleAssetIds` | `DashboardStatsAuthorizationTest` 6 例（工程师 403、leader 只统计本团队） |
| `/dashboard/export` 任意登录可导出全量 | t31：`hasAnyRole(ADMIN,PROJECT_LEADER)` | `DashboardExportAuthorizationTest` 6 例（engineer/team_leader 403、admin 200） |
| 漏洞子资源（详情/标签/历史/附件）无数据级授权 | t21：`canReadVulnerability` / `canWriteVulnerability` | `VulnerabilityAuthorizationTest` 10 例（他人资产 403） |
| 创建漏洞不校验资产写权 | t27+t34：`canWriteAsset(#request.assetId)` + 方法内 TOCTOU 复检 | `VulnerabilityDtoBindingTest`（他人资产 403 且漏洞数不变） |
| 批量导入写侧越权（工程师可导入资产/漏洞） | t33：资产导入角色门槛 + 团队范围逐行校验；漏洞导入逐行 `canWriteAsset` | `ImportAuthorizationTest` 8 例（越权行不落库） |
| 用户接口泄密/约束隐患 | t22：删 `setPasswordHash(null)` + `@JsonIgnore` | `UserPasswordIntegrityTest` 5 例（响应不含 passwordHash） |
| 工具版本 `createdBy` 为空 | t23：`CurrentUserProvider` 统一注入 | `ToolVersionConcurrencyTest`（createdBy 非空） |

---

## 3. SRS 需求覆盖对照

| SRS 章节 | 状态 | 已实现内容 / 缺口说明 |
|---|---|---|
| 2. 用户与权限（角色、用户管理、RBAC） | **已实现** | 4 内置角色 + 90 权限点 + 角色/权限分配 + 用户启停/重置密码；`RbacPermissionTest` 22 例、`UserControllerTest`、`ImportUsersTest`；缺口见 §2.3 #7/#8（团队组织维护无角色门槛） |
| 3.1 目标资产管理 | **已实现** | 资产 CRUD（写侧数据级授权）、团队归属、标签、分组、5 类元数据、文件/交付物、版本（默认/最新/删除）、认领、访问申请、变更历史、订阅；`AssetControllerTest`、`AssetFileControllerTest`、`AssetVersionControllerTest`、`AssetGroupControllerTest`；缺口：分组与版本 default/latest 无写权校验（§2.3 #2/#6），认领完成/放弃恒 403（§2.3 #5） |
| 3.2 环境管理 | **已实现** | 环境 CRUD、Docker/Noop 部署器、部署/启停、快照与回滚、部署器可用性提示；无 Docker 时 503 + `failed` + `failReason`；`EnvironmentServiceImplTest` 等；缺口：环境增删改/部署无角色门槛（§2.3 #9），真实容器编排需宿主安装 Docker |
| 3.3 文档管理 | **已实现** | 文档 CRUD、版本快照（创建 v1、编辑递增、回滚写新快照 `rollback from vN`）、访问授权、发布/取消发布、标签；`DocumentControllerTest` 14 例、`DocumentVersionConcurrencyTest` 3 例、`DocumentRollbackSnapshotTest` 4 例；缺口：文档删除仅数据级（矩阵 2.7 允许全角色，一致） |
| 3.4 漏洞生命周期 | **已实现** | 创建/更新（DTO + camelCase + 部分更新）、状态机（workflow/customer/disclosure/exploitStage/vendorFix）、交付、指派、历史、标签、附件上传/下载、可见范围收敛、统计端点；`VulnerabilityDtoBindingTest` 9 例、`VulnerabilityAuthorizationTest` 10 例、`VulnerabilityAttachmentDownloadTest` 7 例 |
| 3.5 工具资源库 | **已实现** | 工具 CRUD、分类、热门、标签、真实工具包上传/下载、版本（`versionNo` 唯一 + `hasFile` + 下载字节一致）、下载计数；`ToolVersionConcurrencyTest`；缺口：工具改删/版本上传无角色门槛（§2.3 #15） |
| 3.6 团队协作 | **已实现** | 团队树（roots/tree/children）、成员管理、团队用户、任务（创建/指派/状态/评论/附件/协作者/审核）；`TeamControllerTest`、`TaskControllerTest`、`ApprovalFlowTest`；缺口：团队 CRUD/成员维护无角色门槛（§2.3 #7），任务创建无角色门槛（§2.3 #14） |
| 3.7 权限与安全控制 | **已实现** | 角色注解 + 数据级 `AccessGuard`（资产/漏洞/文件/文档/任务）+ JWT + 登录限流 + CORS 白名单 + 审计；缺口见 §2.3（15 组），均不阻断功能 |
| 3.8 知识沉淀与传承 | **已实现** | 文章 CRUD、分类、标签、评论、搜索、热门、按作者/分类列表；`KnowledgeControllerTest`；缺口：改删文章无角色/作者校验（§2.3 #10） |
| 3.9 消息通知模块 | **已实现** | 站内通知、未读数、已读/全部已读、删除、设置、公告、订阅、投递记录（channel/sendStatus/sentAt/errorMessage）、邮件/Webhook 通道（默认关闭）；缺口：公告写操作与主动发通知无角色门槛（§2.3 #11/#12/#13） |
| 3.10 仪表盘与统计 | **已实现** | 概览/资产统计/漏洞统计（角色门槛 + 可见范围收敛）、工作量、趋势（归日补零、`startDate/endDate`）、跨用户排行、配置、CSV 导出（管理层）；`DashboardControllerTest`、`DashboardTrendTest`、`DashboardRankingTest`、`DashboardStatsAuthorizationTest`、`DashboardExportAuthorizationTest` |
| 4.5 数据迁移（导入导出） | **已实现** | 资产/漏洞/用户 CSV 导入（模板带 BOM、部分成功语义、逐行权限与数据边界、失败行回显）；审计与仪表盘 CSV 导出；用户导入一次性密码 |
| 4.6 国际化 | **部分实现** | 界面与文案为中文单语；无 i18n 框架与语言切换（见 §8） |
| 4.7 接口需求 | **已实现** | REST `/api/v1/**` + 统一 `ApiResponse` 包装 + OpenAPI（`/v3/api-docs`）；262 个端点 |

---

## 4. 本轮新增覆盖的薄弱分支（服务层专项）

以下用例由 qa-verify 在 t12/t25 中新增或补齐，覆盖此前**完全无测试**的实现分支（全部在 452 例内且全绿）：

| 用例类 | 用例数 | 覆盖的薄弱分支 |
|---|---|---|
| `AuditLogServiceQueryTest` | 6 | 多条件**交集**语义、空白条件等同不传、等值条件 trim、CSV 字段转义（逗号加引号 / 内部引号翻倍 / 换行折空格）、导出复用筛选与 `EXPORT_LIMIT=10000`、**`cleanOldLogs(≤0)` 回落 180 天**并返回删除条数且可重复执行、分页 size 收敛（负页→0 / 999999→500 / 0→1） |
| `PermissionBootstrapRepairTest` | 5 | RBAC **被破坏后自愈**：权限点展示字段/接口映射被篡改后按目录刷新、`role_permissions` 误删后补齐、目录外自定义权限点不删不改、内置角色删除后重建并重挂默认权限、重复初始化不撤销手工追加授权 |
| `EnvironmentProvisionerSelectorTest` | 4 | 部署器 **docker↔noop 动态切换**、可用性不缓存（同实例可随探测结果切换）、Noop 的 provision/start/stop/remove **全部失败且不改环境状态** |
| `DashboardTrendServiceTest` | 4 | workload 趋势 `coalesce(completed_at, updated_at)` **回退归日**、completed_at 优先不被更新动作拉平、只统计 completed、排行 limit 归一（≤0→10）+ 窗口外剔除 + 非 allUsers 只返回自己一行（无任务时 0） |
| `ToolVersionConcurrencyTest` / `DocumentVersionConcurrencyTest` | 2+3 | **并发唯一键**冲突：行级锁串行化版本号分配、冲突返 409 而非 500、重试耗尽语义、版本号连续无重复 |
| `VulnerabilityAuthorizationTest` / `ImportAuthorizationTest` | 10+8 | 漏洞子资源与导入的**写侧越权**：他人资产读写 403、越权行不落库、跨团队导入 fail-closed |
| `UserPasswordIntegrityTest` | 5 | passwordHash **不为 null** 且响应不含该字段、PATCH→PUT→DELETE 顺序不触发约束、改密/重置密码链路 |
| `DashboardStatsAuthorizationTest` / `DashboardExportAuthorizationTest` | 6+6 | 统计/导出的**角色门槛**与**可见范围收敛**、导出 BOM + 表头 + 实际数据 |
| `DocumentRollbackSnapshotTest` | 4 | 回滚**写新快照**（不修改历史）、`rollback from vN`、版本连续、8 线程回滚+编辑无 5xx |
| `NotificationTypeLengthTest` / `ImportUsersTest` / `AuditLogEnhancementTest` | 5+7+7 | 通知 type 长度三态（200/200/400）、用户导入一次性密码全链路、审计筛选/导出/清理/去重 |

---

## 5. 已知偏离与限制

| # | 偏离/限制 | 原因与现状 | 影响 |
|---|---|---|---|
| 1 | **Flyway 与 Hibernate 建库分工**：迁移脚本不能从空库串联（V2/V3/V4 `ALTER` 的是实体建的表；迁移中仅 7 条 `CREATE TABLE`），生产 `SPRING_FLYWAY_ENABLED=false` + `ddl-auto=update` | 历史遗留的表创建路径（`deploy/README.md` §6） | 空库建表依赖 Hibernate（已验证：56 张表、约 12s 完成）；Flyway 仅作增量记录，版本号 V1…V17 唯一无缺号 |
| 2 | **通知外部通道默认关闭**（`app.notify.mail.enabled=false` / `webhook.enabled=false`） | 避免未配置 SMTP/Webhook 时误发与启动失败；缺配置自动降级为仅站内 | 开箱即用为站内信；启用外部通道需配置 SMTP 与 Webhook URL/密钥 |
| 3 | **环境容器编排需 Docker** | 无 Docker 守护进程时 `NoopProvisioner` 显式失败 | 接口返回 503 + `failed` + `failReason`（前端有横幅提示），不会伪造成功；启用需宿主安装并暴露 socket |
| 4 | **文档版本号约束的迁移历史** | 早期无唯一索引，并发更新会产生重复版本；t32 用 V17「先重排重复数据再建唯一索引」（自连接方案，兼容 MariaDB 10.0/10.1 无窗口函数） | 线上已建 `uk_document_versions_document_version`；重复校验查询为空集 |
| 5 | **无 actuator**：`/actuator/health` 返回 500（`NoResourceFoundException`） | 项目未引入 `spring-boot-starter-actuator` | 健康检查用 `/v3/api-docs` 200 + `POST /auth/login` 200（t25 与空库引导均按此验证） |
| 6 | **工具版本并发 409 线上未自然触发** | t28 行级锁把版本号分配串行化，3 并发实测 201/201/201 且 versionNo 唯一 | 409 分支由 `ToolVersionConcurrencyTest` 覆盖；线上不会出现 500 |
| 7 | **`DELETE /assets/{id}` 为软删除**（置 archived） | 保留审计与引用完整性 | 需要物理删除时按 id 清关联表（`deploy/README.md` §18.4 已给 SQL 思路） |

---

## 6. 部署与运维信息

| 项 | 值 |
|---|---|
| 前端地址 | `http://192.168.188.200:8089`（nginx 静态资源 + `/api` 反代） |
| 后端 | `http://127.0.0.1:8088`（Spring Boot 3.5.13 / JDK 21 运行，jar 由 JDK 17 目标编译） |
| 数据库 | MariaDB 10.11 `vuln_audit`（utf8mb4），56 张表 |
| 账号 | 管理员 `admin` / `admin`（弱口令，评测环境用；改密见 README §8.3） |
| 端口 | 8089 nginx（0.0.0.0）/ 8088 后端（127.0.0.1）/ 3306 MariaDB（127.0.0.1） |
| systemd 单元 | `~/.config/systemd/user/vuln-audit-backend.service`（含 `Environment=TZ=Asia/Shanghai`、`EnvironmentFile=deploy/backend.env`）；`systemctl --user restart vuln-audit-backend` |
| 前端发布 | `cd frontend && npm run build` → `SUDO_PASS=<pwd> bash deploy/sync-dist.sh`（同步 `/var/www/vuln-audit` 并 reload nginx） |
| 冒烟 | `BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh`（92 例） |
| 备份 | `mysqldump -uvulnapp --single-transaction vuln_audit \| gzip > ~/backups/vuln_audit_$(date +%F).sql.gz`；本次部署前备份 38 KB |

**手工 DDL 清单**（线上 Flyway 关闭 + `ddl-auto=update` 不会做以下三件事，脚本：`deploy/manual-ddl.sql`）

| # | 变更 | 语句要点 | 校验 |
|---|---|---|---|
| 1 | `notifications.type` 扩宽 | `ALTER TABLE notifications MODIFY COLUMN type VARCHAR(50) NOT NULL;` | `SHOW COLUMNS ... LIKE 'type'` → `varchar(50)` / `NO` |
| 2 | `tool_versions` 版本号与文件字段 | `ADD COLUMN version_no INT NULL, file_name VARCHAR(255), file_size BIGINT` → 按 `created_at` 回填 → `MODIFY version_no INT NOT NULL` → `CREATE UNIQUE INDEX uk_tool_versions_tool_version_no(tool_id, version_no)` | 重复值查询为空集 |
| 3 | `document_versions` 版本号唯一 | 自连接重排重复 `version` → `CREATE UNIQUE INDEX uk_document_versions_document_version(document_id, version)` | 重复值查询为空集 |

**重置方式**（详见 `deploy/README.md` §18.4）：A) 从 `~/backups/vuln_audit_pre_t13_*.sql.gz` 恢复；B) 只清业务数据（保留 users/roles/permissions）；C) `python3 deploy/seed.py` 重建演示数据；D) 前端重新 build + `sync-dist.sh`。

---

## 7. 测试与验证汇总

| 维度 | 结果 | 证据 |
|---|---|---|
| 后端自动化（t25 验收级） | **452 用例 / 54 suite / 100% 通过**，同一快照连续两次 `mvn -B clean test`（16:17、16:42），Skipped=0、无 `@Disabled/@Ignore` | `deploy/regression-result.md`、`deploy/regression-manifest.txt`（manifest sha256 `77535ad2…`） |
| 空库引导 | 56 张表、RBAC 90/261/4、admin 登录 200、`permissionCount=90`、`/v3/api-docs` 200 | t25 报告 §4 |
| 线上冒烟（t13） | **92/92 PASS ×2**（部署后 + 清理后复跑），0 FAIL / 0 SKIP | `deploy/smoke-result.txt` |
| 部署后专项核对 11a~11k | **13/13 PASS** | `deploy/smoke-result.txt` 第二节 |
| 三账号交叉验证 | 管理员：全量可见 + 全部管理接口 200；小组长：统计/排行按本团队收敛、审计与导出 403、导入限本团队（越权行 `权限拒绝：` 且不落库）；工程师：资产/漏洞越权 403、审计/仪表盘统计与导出 403、导入资产 403、仅漏洞导入可用（自己的资产 200） | 冒烟脚本 `auth/*`、`audit/工程师403`、`dash/导出-工程师403`、`import/用户导入-工程师403`；`ImportAuthorizationTest`、`DashboardStatsAuthorizationTest`、`VulnerabilityAuthorizationTest` |
| 前端构建 | `tsc -b && vite build` 成功（3680 modules，`index-DmCRvmQl.js` 2,224.37 kB），产物已上线 | t13 报告 A |

---

## 8. 仍建议后续跟进的事项（不阻塞验收）

| # | 事项 | 理由/建议 |
|---|---|---|
| 1 | 收紧 §2.3 的 15 组鉴权缺口（优先「高」：团队 CRUD、环境增删改/部署、公告写操作） | 权限矩阵未落地部分，建议按模块补 `@PreAuthorize` 并在服务层同步校验 |
| 2 | 修复认领「完成/放弃」恒 403 | 控制器传 `userId=null` 导致服务层恒抛 FORBIDDEN；改为传当前用户并保留认领人校验 |
| 3 | 引入 `spring-boot-starter-actuator` 并提供 `/actuator/health` | 当前只能用 `/v3/api-docs` + 登录做健康检查，运维体验不足 |
| 4 | 国际化（4.6） | 目前中文单语；如需多语言，建议引入 i18n 资源与文案抽离 |
| 5 | 对象存储抽象 | 文件/附件/工具包当前落本地磁盘，多实例部署需共享存储或对象存储（S3/OSS） |
| 6 | 真实容器编排启用 | 需宿主安装 Docker 并暴露 socket（或 K8s 编排），当前为 Noop 显式降级 |
| 7 | 把「批量导入」纳入权限矩阵并补 `team_leader` 可见范围说明 | 矩阵 2.x 尚未覆盖导入域，建议补表以免后续回归 |
| 8 | 增加前端 E2E 常态化（Playwright/Cypress） | 目前为页面级人工/Headless 验证；建议纳入 CI 以便长期回归 |

---

## 附录 A：证据索引

| 证据 | 位置 |
|---|---|
| 后端全量回归报告（452 用例两次全绿 + 空库引导） | `deploy/regression-result.md` |
| 回归快照 manifest（334 文件 sha256） | `deploy/regression-manifest.txt` |
| 部署冒烟与 11a~11k 实测原始输出 | `deploy/smoke-result.txt` |
| 冒烟脚本与部署前基线 | `scripts/smoke.sh`、`scripts/smoke-baseline.md`、`scripts/baseline/smoke-20261029-oldjar.log` |
| 手工 DDL 与部署手册（含 §18 部署验收与重置） | `deploy/manual-ddl.sql`、`deploy/README.md` |
| 权限矩阵（角色 × 接口） | `docs/design/permission-matrix.md` |
| 接口设计（含 7.9 文档快照语义） | `docs/design/api_design.md` |
| 提交记录 | `58f3b56`(backend) / `1459150`(frontend) / `17233c8`+`1785ea7`(deploy) / `49e98fb`(docs) / `7d26d76`(scripts) / `e88e111`(t25 回归报告) |

## 附录 B：复核命令（抽查任意 3 项即可复现）

```bash
# 1) 后端全量回归（约 17 分钟）
cd backend && mvn -B clean test            # 期望 Tests run: 452, Failures: 0, Errors: 0, Skipped: 0

# 2) 线上冒烟（92 例）
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 92 PASS / 0 FAIL / 0 SKIP

# 3) 鉴权抽查：工程师访问审计导出/仪表盘导出/他人资产漏洞 → 403
T=$(curl -s -X POST http://127.0.0.1:8089/api/v1/auth/login -H 'Content-Type: application/json' \
      -d '{"username":"smoke_engineer","password":"Smoke@12345"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8089/api/v1/audit-logs/export  -H "Authorization: Bearer $T"   # 403
curl -s -o /dev/null -w '%{http_code}\n' 'http://127.0.0.1:8089/api/v1/dashboard/export?type=assets' -H "Authorization: Bearer $T"   # 403

# 4) RBAC 幂等与权限点数量
A=$(curl -s -X POST http://127.0.0.1:8089/api/v1/auth/login -H 'Content-Type: application/json' \
      -d '{"username":"admin","password":"admin"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s http://127.0.0.1:8089/api/v1/permissions -H "Authorization: Bearer $A" | grep -o '"code"' | wc -l   # 90

# 5) 手工 DDL 效果
MYSQL_PWD='<pwd>' mariadb -uvulnapp vuln_audit -e "SHOW COLUMNS FROM notifications LIKE 'type';"   # varchar(50) / NO
```
