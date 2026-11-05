# 漏洞审计生命周期管理平台 — 验收报告

| 项 | 值 |
|---|---|
| 报告版本 | v1.0（2026-10-30） |
| 验收对象 | 192.168.188.200:8089 部署实例（nginx 8089 → 后端 8088 → MariaDB 3306） |
| 代码基线 | git `1785ea7`（工作区 CLEAN）；后端包由同一提交构建，sha256 与本地一致 |
| 验收依据 | 功能缺陷/未实现/前后端不统一三张清单、SRS（`docs/design/SRS.md`）、权限矩阵（`docs/design/permission-matrix.md`）、t25 回归报告、t13 部署冒烟记录 |
| 证据索引 | 见附录 A；回归与冒烟原始输出：`deploy/regression-result.md`、`deploy/regression-manifest.txt`、`deploy/smoke-result.txt` |

## 0. 验收结论

**结论：通过。** 三张清单逐项闭环（缺陷已修复、能力已实现、前后端契约已对齐），鉴权覆盖已逐端点核对（无注解缺口已列清并给出结论），SRS 十二个功能域全部达到「已实现」，**680 个后端用例 100% 通过**（历批累计，第五批新增 t82 聚合 8 例 + t85 导入 10 例；第七批 t93 审计 9 例 + t94 事务守卫 1 例 + t96 登录页偏好 11 例），线上冒烟 **228/228 全绿**（t13 的 92 + t41 的 16 + t47 的 20 + 后续批次增量），三账号交叉验证通过。

第二批（t49/t50/t51/t55）之后**鉴权缺口清零（15 → 0 组）**：资产域 6 组、协作域 4 组、组织/工具域 2 组、工具域追加 3 个端点全部收口，资产可见性与团队可见性均统一为单一事实来源：3 组「高」severity 缺口（团队 CRUD、环境增删改与部署、公告写）已修复，资产认领「完成/放弃」状态机已打通（此前恒 403）。

验收附带 8 项**已知偏离**（§5）与 8 项**后续建议**（§8），均不影响本次验收结论。

| 关键指标 | 数值 |
|---|---|
| 后端自动化测试 | **680 用例 / 通过率 100%**（含客户/项目/挂靠域 CustomerProjectApi 14、AssetProjectLink 15、CustomerSummary 8、ImportAssetProject 10 等），`mvn -B clean test` 全绿（2026-11-04 第七批复核 680/680，含 t93 审计 9 + t94 事务守卫 1 + t96 偏好 11） |
| 线上冒烟 | **228 用例 / 228 PASS / 0 FAIL / 0 SKIP**（含 t94 新增审计语义用例），发布后复跑全绿 |
| 部署后专项核对 | 11a~11k 共 **13 项实测全 PASS** |
| 空库引导（真实建库路径） | 56 张表、RBAC 90 权限点 / 261 关联、admin 登录 200、`permissionCount=90` |
| 接口总数 / 已做鉴权 | 263 个端点；角色注解 74、数据级守卫 79、方法内校验 12 → **165 个显式鉴权端点**（写端点 108/135 = 80%），其余见 §2 |
| 鉴权缺口 | 15 组 → **2 组（中 1 / 低 1，高 0）**；已修复：3 组高 severity（t40）、认领状态机（t39）、用户读 PII（t45）、列表/详情口径（t46）、资产域写权 6 组（t49）、知识库/通知/任务域 4 组（t50） |
| 线上 RBAC 幂等 | 重启多次 `permissions=90 / role_permissions=261 / roles=4` 不变 |

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
| 全部端点 | 263 | 74 | 79 | 12 | 52 | 46 |
| **写端点**（POST/PUT/PATCH/DELETE） | 135 | 55 | 47 | 6 | 23 | 4 |
| GET 端点 | 128 | 19 | 32 | 6 | 29 | 42 |

**关键结论**：写端点中 **108 个（80%）有显式鉴权**（角色注解 55 + 数据级守卫 47 + 方法内 6），其余 27 个写端点均为「合理开放」（本人数据、全员协作的评论/申请、工具库创建与下载、认证域）；**四类资源标签写权已于 t61 统一**（资产=资产写权、工具=管理层、文章/文档=作者或管理层，见 §2.6），故「标签」不再属于合理开放项；全端点显式鉴权 **169 个**（较第一批的 140 再增 29，t61 再 +4）。

> **鉴权缺口轨迹**：`15 → 12（t40）→ 6（t49）→ 2（t50）→ 0（t51）→ 0（t55 收尾工具域）`，§2.3.2 现为**已知缺口 0 组**。
> **可见性口径（t46 已统一）**：`GET /assets` 列表与 `GET /assets/{id}` 详情共用 `AccessGuard.visibleAssetIds`；`canWriteAsset ⊂ canReadAsset`。
> **团队读口径（t51）**：`GET /teams/{id}/members|users` 收敛到「可见团队」（管理层 / 自己领导或所属团队及其子团队），fail-closed 403。

### 2.2 分控制器明细（写端点 + 敏感读端点）

### ApprovalScopeController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/approval-scopes` | 角色注解 |
| DELETE | `/api/v1/approval-scopes/{id}` | 角色注解 |

### AssetController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/assets` | 数据级守卫 |
| PUT | `/api/v1/assets/{id}` | 数据级守卫 |
| DELETE | `/api/v1/assets/{id}` | 数据级守卫 |
| PUT | `/api/v1/assets/{id}/status` | 数据级守卫 |
| POST | `/api/v1/assets/{id}/access-requests` | 仅登录(取当前用户) |
| GET | `/api/v1/assets/{id}/access-requests` | 数据级守卫 |
| POST | `/api/v1/assets/access-requests/{requestId}/review` | 角色注解 |
| POST | `/api/v1/assets/{id}/claims` | 方法内数据级 |
| GET | `/api/v1/assets/{id}/claims` | 数据级守卫 |
| POST | `/api/v1/assets/claims/{claimId}/review` | 角色注解 |
| POST | `/api/v1/assets/claims/{claimId}/complete` | 方法内数据级 |
| POST | `/api/v1/assets/claims/{claimId}/abandon` | 方法内数据级 |
| POST | `/api/v1/assets/{id}/tags` | 数据级守卫 |
| DELETE | `/api/v1/assets/{assetId}/tags/{tagId}` | 数据级守卫 |
| GET | `/api/v1/assets/my/access-requests` | 仅登录(取当前用户) |
| GET | `/api/v1/assets/my/claims` | 方法内数据级 |

### AssetDeliverableController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/assets/{assetId}/deliverables` | 数据级守卫 |
| GET | `/api/v1/asset-deliverables/{id}/download` | 数据级守卫 |
| DELETE | `/api/v1/asset-deliverables/{id}` | 数据级守卫 |

### AssetFileController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/assets/{assetId}/files` | 数据级守卫 |
| GET | `/api/v1/asset-files/{id}/download` | 数据级守卫 |
| DELETE | `/api/v1/asset-files/{id}` | 数据级守卫 |
| POST | `/api/v1/asset-files/{id}/versions` | 数据级守卫 |

### AssetGroupController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/asset-groups` | 角色注解 |
| PUT | `/api/v1/asset-groups/{id}` | 角色注解 |
| DELETE | `/api/v1/asset-groups/{id}` | 角色注解 |
| POST | `/api/v1/asset-groups/{id}/assets` | 角色注解 |
| DELETE | `/api/v1/asset-groups/{groupId}/assets/{assetId}` | 角色注解 |

### AssetMetadataController
| 方法 | 端点 | 校验 |
|---|---|---|
| GET | `/api/v1/assets/{assetId}/metadata/base` | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/metadata/base` | 数据级守卫 |
| GET | `/api/v1/assets/{assetId}/metadata/network` | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/metadata/network` | 数据级守卫 |
| GET | `/api/v1/assets/{assetId}/metadata/env` | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/metadata/env` | 数据级守卫 |
| GET | `/api/v1/assets/{assetId}/metadata/account` | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/metadata/account` | 数据级守卫 |
| GET | `/api/v1/assets/{assetId}/metadata/extra` | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/metadata/extra` | 数据级守卫 |

### AssetVersionController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/assets/{assetId}/versions` | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/versions/{id}/default` | 数据级守卫 |
| PUT | `/api/v1/assets/{assetId}/versions/{id}/latest` | 数据级守卫 |
| DELETE | `/api/v1/assets/{assetId}/versions/{id}` | 数据级守卫 |

### AuditLogController
| 方法 | 端点 | 校验 |
|---|---|---|
| GET | `/api/v1/audit-logs/{id}` | 角色注解 |
| GET | `/api/v1/audit-logs` | 角色注解 |
| GET | `/api/v1/audit-logs/export` | 角色注解 |
| GET | `/api/v1/audit-logs/users/{userId}` | 角色注解 |
| GET | `/api/v1/audit-logs/resources/{resourceType}/{resourceId}` | 角色注解 |

### AuthController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/auth/login` | 无 |
| POST | `/api/v1/auth/logout` | 仅登录(取当前用户) |
| GET | `/api/v1/auth/me` | 仅登录(取当前用户) |
| POST | `/api/v1/auth/change-password` | 仅登录(取当前用户) |
| POST | `/api/v1/auth/refresh` | 仅登录(取当前用户) |

### DashboardController
| 方法 | 端点 | 校验 |
|---|---|---|
| PUT | `/api/v1/dashboard/my` | 仅登录(取当前用户) |
| GET | `/api/v1/dashboard/workload/stats/claims` | 仅登录(取当前用户) |
| POST | `/api/v1/dashboard/config` | 仅登录(取当前用户) |
| DELETE | `/api/v1/dashboard/config` | 方法内数据级 |
| GET | `/api/v1/dashboard/export` | 角色注解 |

### DocumentController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/documents` | 仅登录(取当前用户) |
| PUT | `/api/v1/documents/{id}` | 数据级守卫 |
| DELETE | `/api/v1/documents/{id}` | 数据级守卫 |
| POST | `/api/v1/documents/{id}/access` | 数据级守卫 |
| DELETE | `/api/v1/documents/{id}/access` | 数据级守卫 |
| POST | `/api/v1/documents/{id}/publish` | 数据级守卫 |
| POST | `/api/v1/documents/{id}/unpublish` | 数据级守卫 |
| POST | `/api/v1/documents/{id}/versions/{version}/rollback` | 数据级守卫 |
| POST | `/api/v1/documents/{id}/tags` | 仅登录(取当前用户) |
| DELETE | `/api/v1/documents/{documentId}/tags/{tagId}` | 无 |

### EnvironmentController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/environments` | 角色注解 |
| PUT | `/api/v1/environments/{id}` | 角色注解 |
| DELETE | `/api/v1/environments/{id}` | 角色注解 |
| POST | `/api/v1/environments/{id}/deploy` | 角色注解 |
| POST | `/api/v1/environments/{id}/start` | 角色注解 |
| POST | `/api/v1/environments/{id}/stop` | 角色注解 |
| POST | `/api/v1/environments/{id}/snapshots` | 角色注解 |
| POST | `/api/v1/environments/{id}/snapshots/{snapshotId}/rollback` | 角色注解 |

### ImportController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/import/assets` | 角色注解 |
| POST | `/api/v1/import/vulnerabilities` | 方法内数据级 |
| POST | `/api/v1/import/users` | 角色注解 |

### KnowledgeController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/knowledge` | 仅登录(取当前用户) |
| PUT | `/api/v1/knowledge/{id}` | 无 |
| DELETE | `/api/v1/knowledge/{id}` | 数据级守卫 |
| POST | `/api/v1/knowledge/{id}/comments` | 仅登录(取当前用户) |
| POST | `/api/v1/knowledge/{id}/tags` | 无 |
| DELETE | `/api/v1/knowledge/{id}/tags` | 仅登录(取当前用户) |

### NotificationController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/notifications/{id}/read` | 仅登录(取当前用户) |
| POST | `/api/v1/notifications/read-all` | 仅登录(取当前用户) |
| DELETE | `/api/v1/notifications/{id}` | 仅登录(取当前用户) |
| POST | `/api/v1/notifications/vulnerability-discovered` | 角色注解 |
| GET | `/api/v1/notifications/{notificationId}/recipients` | 数据级守卫 |
| GET | `/api/v1/notifications/settings` | 仅登录(取当前用户) |
| PUT | `/api/v1/notifications/settings/{type}` | 仅登录(取当前用户) |
| POST | `/api/v1/notifications/announcements` | 角色注解 |
| PUT | `/api/v1/notifications/announcements/{id}` | 角色注解 |
| DELETE | `/api/v1/notifications/announcements/{id}` | 角色注解 |
| POST | `/api/v1/notifications/subscriptions/{assetId}` | 仅登录(取当前用户) |
| DELETE | `/api/v1/notifications/subscriptions/{assetId}` | 仅登录(取当前用户) |

### PermissionController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/permissions` | 角色注解 |
| GET | `/api/v1/permissions/{id}` | 角色注解 |
| GET | `/api/v1/permissions` | 角色注解 |
| PUT | `/api/v1/permissions/{id}` | 角色注解 |
| DELETE | `/api/v1/permissions/{id}` | 角色注解 |

### RoleController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/roles` | 角色注解 |
| GET | `/api/v1/roles/{id}` | 角色注解 |
| GET | `/api/v1/roles` | 角色注解 |
| PUT | `/api/v1/roles/{id}` | 角色注解 |
| DELETE | `/api/v1/roles/{id}` | 角色注解 |
| POST | `/api/v1/roles/{id}/permissions` | 角色注解 |
| GET | `/api/v1/roles/{id}/permissions` | 角色注解 |
| DELETE | `/api/v1/roles/{id}/permissions` | 角色注解 |
| POST | `/api/v1/roles/{id}/users/{userId}` | 角色注解 |
| DELETE | `/api/v1/roles/{id}/users/{userId}` | 角色注解 |

### TaskController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/tasks` | 角色注解 |
| PUT | `/api/v1/tasks/{id}` | 仅登录(取当前用户) |
| PUT | `/api/v1/tasks/{id}/status` | 仅登录(取当前用户) |
| POST | `/api/v1/tasks/{id}/submit-review` | 数据级守卫 |
| POST | `/api/v1/tasks/{id}/approve` | 角色注解 |
| POST | `/api/v1/tasks/{id}/reject` | 角色注解 |
| DELETE | `/api/v1/tasks/{id}` | 数据级守卫 |
| POST | `/api/v1/tasks/{id}/assign` | 角色注解 |
| POST | `/api/v1/tasks/{id}/comments` | 仅登录(取当前用户) |
| POST | `/api/v1/tasks/{id}/collaborators` | 角色注解 |
| DELETE | `/api/v1/tasks/{id}/collaborators/{userId}` | 角色注解 |
| POST | `/api/v1/tasks/{id}/attachments` | 仅登录(取当前用户) |

### TeamController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/teams` | 角色注解 |
| PUT | `/api/v1/teams/{id}` | 数据级守卫 |
| DELETE | `/api/v1/teams/{id}` | 数据级守卫 |
| POST | `/api/v1/teams/{id}/members` | 数据级守卫 |
| DELETE | `/api/v1/teams/{id}/members/{userId}` | 数据级守卫 |
| GET | `/api/v1/teams/{id}/members` | 数据级守卫 |
| GET | `/api/v1/teams/{id}/users` | 数据级守卫 |

### ToolController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/tools` | 仅登录(取当前用户) |
| PUT | `/api/v1/tools/{id}` | 角色注解 |
| DELETE | `/api/v1/tools/{id}` | 角色注解 |
| POST | `/api/v1/tools/{id}/download` | 仅登录(取当前用户) |
| POST | `/api/v1/tools/{id}/file` | 角色注解 |
| GET | `/api/v1/tools/{id}/download` | 仅登录(取当前用户) |
| GET | `/api/v1/tools/{id}/download-count` | 无 |
| POST | `/api/v1/tools/{id}/tags` | 角色注解 |
| DELETE | `/api/v1/tools/{id}/tags` | 角色注解 |
| POST | `/api/v1/tools/{id}/versions` | 角色注解 |
| GET | `/api/v1/tools/{id}/versions/{versionId}/download` | 仅登录(取当前用户) |

### UserController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/users` | 角色注解 |
| GET | `/api/v1/users/{id}` | 角色注解 |
| GET | `/api/v1/users/directory` | 无 |
| GET | `/api/v1/users` | 角色注解 |
| PUT | `/api/v1/users/{id}` | 方法内角色 |
| PATCH | `/api/v1/users/{id}/status` | 角色注解 |
| DELETE | `/api/v1/users/{id}` | 角色注解 |
| POST | `/api/v1/users/{id}/reset-password` | 角色注解 |
| GET | `/api/v1/users/check-username` | 角色注解 |
| GET | `/api/v1/users/check-email` | 角色注解 |
| GET | `/api/v1/users/{id}/roles` | 角色注解 |
| POST | `/api/v1/users/{id}/roles` | 角色注解 |
| DELETE | `/api/v1/users/{id}/roles/{roleId}` | 角色注解 |
| POST | `/api/v1/users/{id}/permissions` | 角色注解 |
| DELETE | `/api/v1/users/{id}/permissions/{permissionId}` | 角色注解 |
| GET | `/api/v1/users/me/permissions` | 仅登录(取当前用户) |

### VulnerabilityController
| 方法 | 端点 | 校验 |
|---|---|---|
| POST | `/api/v1/vulnerabilities` | 数据级守卫 |
| PUT | `/api/v1/vulnerabilities/{id}` | 数据级守卫 |
| DELETE | `/api/v1/vulnerabilities/{id}` | 角色注解 |
| PATCH | `/api/v1/vulnerabilities/{id}/workflow-status` | 数据级守卫 |
| PATCH | `/api/v1/vulnerabilities/{id}/customer-status` | 数据级守卫 |
| PATCH | `/api/v1/vulnerabilities/{id}/disclosure-status` | 数据级守卫 |
| POST | `/api/v1/vulnerabilities/{id}/assign` | 数据级守卫 |
| PATCH | `/api/v1/vulnerabilities/{id}/exploit-stage` | 数据级守卫 |
| POST | `/api/v1/vulnerabilities/{id}/deliver` | 数据级守卫 |
| PATCH | `/api/v1/vulnerabilities/{id}/vendor-fix` | 数据级守卫 |
| GET | `/api/v1/vulnerabilities/statistics/workflow-status` | 方法内数据级 |
| GET | `/api/v1/vulnerabilities/statistics/severity` | 方法内数据级 |
| POST | `/api/v1/vulnerabilities/{id}/tags` | 数据级守卫 |
| DELETE | `/api/v1/vulnerabilities/{vulnerabilityId}/tags/{tagId}` | 数据级守卫 |
| POST | `/api/v1/vulnerabilities/{id}/attachments` | 数据级守卫 |
| GET | `/api/v1/vulnerabilities/{id}/attachments/{attachmentId}/download` | 数据级守卫 |
| DELETE | `/api/v1/vulnerabilities/{vulnerabilityId}/attachments/{attachmentId}` | 数据级守卫 |

### 2.3 既无角色注解又无数据级守卫的端点（逐条结论）

#### 2.3.1 合理开放（依据：权限矩阵允许全部登录角色，或只操作本人数据）

| 端点 | 校验 | 结论与依据 |
|---|---|---|
| `POST /auth/login`、`POST /auth/logout`、`POST /auth/refresh`、`GET /auth/me`、`POST /auth/change-password` | 仅登录 / 无 | **合理开放**：认证域，矩阵 2.1 全角色可用；`change-password` 只改本人密码 |
| `POST /assets/{id}/access-requests`、`POST /assets/{id}/claims` | 仅登录 | **合理开放**：矩阵 2.4 全员可申请/认领。（资产标签写权已于 **t49** 收口为 `canWriteAsset`，见 §2.4 与 §2.6 标签统一规则） |
| `GET /assets/my/access-requests`、`GET /assets/my/claims` | 仅登录 | **合理开放**：只返回当前用户数据（t39 起未认证一律拒绝，不再退化为全量） |
| `PUT /dashboard/my`、`POST /dashboard/config`、`DELETE /dashboard/config`、`GET /dashboard/workload/stats/claims` | 仅登录 | **合理开放**：只读写当前用户的仪表盘配置与本人统计 |
| `POST /notifications/{id}/read`、`POST /notifications/read-all`、`DELETE /notifications/{id}`、`GET/PUT /notifications/settings`、`POST/DELETE /notifications/subscriptions/{assetId}` | 仅登录 | **合理开放**：全部作用于当前用户的通知/设置/订阅（矩阵 2.12 全角色） |
| `PUT /tasks/{id}`、`PUT /tasks/{id}/status`、`POST /tasks/{id}/comments`、`POST /tasks/{id}/attachments` | 仅登录 | **合理开放**：矩阵 2.9 全角色可协作；驳回/通过另有角色门槛 |
| `POST /knowledge`、`PUT /knowledge/{id}`、`POST /knowledge/{id}/comments` | 仅登录 | **合理开放**：矩阵 2.10 文章发布/编辑/评论对全角色开放。（文章删除 t50 已收口；**文章标签增删 t61 收口为「作者本人 或 管理层」**，见 §2.6） |
| `POST /documents` | 仅登录 | **合理开放**：矩阵 2.7 文档创建对全角色开放。（**文档标签增删 t61 收口为「作者本人 或 管理层」**，见 §2.6） |
| `POST /tools`、`POST /tools/{id}/download`、`GET /tools/{id}/download`、`GET /tools/{id}/download-count`、`GET /tools/{id}/tags`、`GET /tools/{id}/versions` | 仅登录 | **合理开放**：矩阵 2.8 工具库创建/下载对全角色开放；`download-count` 仅返回计数（**写类已收口见 §2.4**：`POST /tools/{id}/file`、`POST|DELETE /tools/{id}/tags` 已于 t55 加 ADMIN/PL/TEAM_LEADER 门槛） |
| `GET /users/me/permissions` | 仅登录 | **合理开放**：只返回当前用户权限点（`permissionCount=90` 校验点） |
| `GET /environment-templates`（EnvironmentTemplateController 唯一端点） | 仅登录 | **合理开放**：环境模板字典，无业务数据；建议后续补入矩阵 |

#### 2.3.2 已知缺口（矩阵要求角色门槛或数据级校验，但当前仅"登录即可"）

**已知缺口：0 组**（高 0 / 中 0 / 低 0）。轨迹：`15 → 12（t40 高危 3 组）→ 6（t49 资产域）→ 2（t50 协作域）→ 0（t51 组织/工具域）`，t55 又把工具域剩余 3 个写端点（工具包上传、标签增删）补齐。

| 关闭批次 | 关闭的组 | 证据 |
|---|---|---|
| t40 | 团队 CRUD、环境增删改与部署、公告写（3 组「高」） | `PrivilegedWriteAuthorizationTest` 10 例 |
| t49 | 建资产、资产版本、默认/最新版本、申请明细、标签删除、分组写（6 组） | `AssetDomainAuthorizationTest` 7 例 |
| t50 | 删他人文章、漏洞通知群发、投递记录、任务创建（4 组） | `CollaborationDomainAuthorizationTest` 15 例 |
| t51 | 团队读（成员列表）、工具改删（2 组） + `POST /assets/{id}/tags` 追加项 | `OrgToolDomainAuthorizationTest` 7 例 |
| t55 | 工具包上传、工具标签增删（3 个写端点，工具域收尾） | `ToolWriteAuthorizationTest` 5 例 |
| t61 | 知识库文章标签、文档标签（统一为「作者本人 或 管理层」） | `TagWriteAuthorizationTest` 7 例 |

> 仍为「仅登录 / 无注解」的端点（见 §2.3.1）全部经判定属**合理开放**：本人数据（我的申请/认领、仪表盘配置、通知已读与订阅）、认证域、矩阵对全角色开放的协作写（知识文章与评论、文档与任务协作、工具创建与下载）。
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
| 团队 CRUD/成员管理无角色门槛（原 §2.3.2 #7，「高」） | t40：建团队 `hasAnyRole(ADMIN,PROJECT_LEADER)`；改/删团队与成员增删 `hasAnyRole(...) or @accessGuard.canManageTeam(#id, authentication)`（团队长仅自己领导的团队） | `PrivilegedWriteAuthorizationTest`（工程师建/改/删团队与加成员 403；团队长管理他人团队 403、自己团队 200；查询仍 200） |
| 环境增删改/部署无角色门槛（原 §2.3.2 #9，「高」） | t40：`POST/PUT/DELETE /environments**`、`/deploy|/start|/stop`、`POST /snapshots`、`/snapshots/{id}/rollback` 统一 `hasAnyRole(ADMIN,PROJECT_LEADER,TEAM_LEADER)`；`GET` 保持开放 | 同上（工程师全部写操作 403；团队长/管理员 200） |
| 公告写无角色门槛（原 §2.3.2 #11，「高」） | t40：`POST/PUT/DELETE /notifications/announcements**` → `hasAnyRole(ADMIN,PROJECT_LEADER)`；`GET` 列表/active 保持开放 | 同上（工程师与团队长发公告均 403、管理员 201 且列表可读） |
| 认领「完成/放弃」恒 403、`my/claims` 可能退化为全量（原 §2.3.2 #5） | t39：控制器改用 `CurrentUserProvider.requireCurrentUserId()`（缺失 401）；服务层保留认领人校验（完成仅本人、管理员不放行），管理层可强制放弃并写 `reviewed_by/review_note`；`getMyClaims` 未认证不再走 `findAll` | `AssetClaimLifecycleTest` 13 例（本人完成 200、非本人与管理员完成 403、管理层强制放弃 200、pending 400、不存在 404、生命周期闭环） |
| 用户读接口 PII 枚举面（原 §2.3.2 #13） | t45：`GET /users`、`/users/{id}`、`/{id}/roles`、`check-username`、`check-email` → `hasAnyRole(ADMIN,PROJECT_LEADER)`；新增最小暴露 `GET /users/directory`（专用 record，仅 id/username/fullName，全员可用）；`/users/me/permissions`、`/auth/me` 保持对所有登录用户开放 | `UserDirectoryAuthorizationTest` 8 例（工程师 5 个管理读 403、目录 200 且无 email/phone/roles/status/lastLoginAt/passwordHash、admin/PL 全通、未认证 401、无权限仍 403） |
| 匿名请求统一 403（原 §5 偏离 8） | t45：配置 `AuthenticationEntryPoint`（未认证 → 401 + 统一 JSON）与 `AccessDeniedHandler`（无权限 → 403） | 同上（`unauthenticated_returns401`、`authenticatedWithoutPermission_returns403`、`invalidToken_returns401`） |
| 资产域写权缺口 6 组（原 §2.3.2 #1–#6） | t49：`POST /assets` → `hasAnyRole(ADMIN,PROJECT_LEADER,TEAM_LEADER)` **且** `@accessGuard.canCreateAssetInTeam(#request.teamId)`（team_leader 只能在自己领导/所属团队下建资产）；版本创建 / default / latest → `canWriteAsset`；`GET /assets/{id}/access-requests` → `canReadAsset` **且** `canSeeAssetAccessDetails`（负责人 / 管理层 / 审批人）；标签删除 → `canWriteAsset`；分组写 → `hasAnyRole(ADMIN,PROJECT_LEADER,TEAM_LEADER)`，分组查询保持全员开放 | `AssetDomainAuthorizationTest` 7 例（工程师 6 组全 403、团队长跨团队 403/本团队 201、负责人可写自有资产、管理员全通、分组查询对工程师 200、审批人不越过读范围） |
| 团队读与工具写 2 组（原 §2.3.2 #11/#12） | t51：`GET /teams/{id}/members`、`/{id}/users` → `@accessGuard.canReadTeam(#id)`（**选择收敛到可见团队**：自己领导/所属团队及其子团队可见，管理层全量，其余 403）；`PUT /tools/{id}`、`POST /tools/{id}/versions` → `hasAnyRole(ADMIN,PROJECT_LEADER,TEAM_LEADER)`；`DELETE /tools/{id}` → `hasAnyRole(ADMIN,PROJECT_LEADER)`；工具查询/下载/计数保持全员开放；顺带把 `POST /assets/{id}/tags` 改为 `canWriteAsset` | `OrgToolDomainAuthorizationTest` 7 例（工程师读自己团队 200 / 他团队 403、团队长本团队 200 / 无关团队 403、管理员全量、工程师工具写 403、团队长改+上传 200 且 createdBy 落库 / 删除 403、管理员全通、工具查询下载 200、标签新增 403/201） |
| 工具域写权 3 端点（t51 上报遗留） | t55：`POST /tools/{id}/file`（上传工具包）、`POST /tools/{id}/tags`、`DELETE /tools/{id}/tags` → `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')`（与 `PUT /tools/{id}` 同权）；工具不存在 → **404**；查询/下载/计数/标签读保持全员开放；t23 的真实落盘能力未受影响 | `ToolWriteAuthorizationTest` 5 例（工程师 3 写 403；团队长加/删标签 200 + 上传 200 且 `fileName/fileSize` 有值、下载字节一致；管理员 200；工具不存在 404×3；工程师 5 个读端点 200） |
| 知识库删除（原 §2.3.2 #7） | t50：`DELETE /knowledge/{id}` → `@accessGuard.canDeleteKnowledge(#id, authentication)`（管理层 **或作者本人**）；`PUT` 按矩阵保持全角色开放 | `CollaborationDomainAuthorizationTest`：工程师删他人文章 403、删自己文章 200、管理层删他人 200、`PUT` 仍 200 |
| 漏洞通知群发（原 §2.3.2 #8） | t50：`POST /notifications/vulnerability-discovered` → `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')`。选角色门槛而非「自己为发送者 + 收件人可见性交集」：该端点本质是群发广播、与漏洞归属无耦合；按可见性放行后工程师仍可对自己可见漏洞向任意收件人群发，骚扰面未消除（详见控制器注释） | 同文件：工程师 403、team_leader 200 |
| 通知投递记录读取（原 §2.3.2 #9） | t50：`GET /notifications/{notificationId}/recipients` → `@accessGuard.canReadNotificationRecipients(...)`（admin/project_leader 全量；其余仅接收人本人） | 同文件：非接收人 403、接收人本人 200（响应含自身接收记录）、admin 200 |
| 任务创建与提交审核（原 §2.3.2 #10） | t50：`POST /tasks` → `hasAnyRole(ADMIN,PROJECT_LEADER,TEAM_LEADER)` **且** `@accessGuard.canWriteAssetOrAbsent(#request.assetId, ...)`；`POST /tasks/{id}/submit-review` → `@accessGuard.canSubmitTaskReview(...)`（负责人/协作者/管理层），并从 `TaskServiceImpl.submitForReview` 移除「仅 assignedTo」的硬编码（曾导致管理层代办恒 403） | 同文件：工程师建任务 403、team_leader 自有资产 201 / 越界资产 403、非负责人（普通工程师）提交审核 403、负责人 200、管理层代办 200 |

### 2.5 权限点目录与实际守卫的口径差异

**结论：RBAC 权限点（90 个）当前不参与鉴权判定，是描述性目录 + 为后续 ABAC 预留；实际执行以 `@PreAuthorize` 角色注解 + 数据级守卫（`AccessGuard`）为准。权限点不得作为前端授权依据。**

| 维度 | 权限点（`permissions` / `role_permissions`） | 实际鉴权（代码） |
|---|---|---|
| 数据来源 | `security/PermissionCatalog` 定义 90 条，启动幂等写入（t1），`GET /users/me/permissions` 返回 | `@PreAuthorize` 角色注解 + `AccessGuard.canRead*/canWrite*/canManageTeam/visibleAssetIds` |
| 是否参与判定 | **否**：`PermissionService.hasPermission(...)` 在主代码中无调用点，`hasAuthority(...)` 仅出现在注释 | 是：262 端点中 134 个显式鉴权（角色 56 / 数据级 67 / 方法内 11） |
| 变更影响 | 改权限点不影响任何接口放行/拒绝 | 改注解 / 守卫才影响 |

**典型差异（必须按「角色 + 数据归属」而非权限点判定）**

| 场景 | 权限点口径 | 实际鉴权口径 | 前端若按权限点判定的后果 |
|---|---|---|---|
| 团队长改自己团队 | `team_leader` 权限点里**没有** `team:update` | `PUT /teams/{id}`：`hasAnyRole(ADMIN,PL) or @accessGuard.canManageTeam(#id)` → **放行** | 会错误隐藏「编辑」入口（用户明明有权） |
| 团队长删团队 | 无 `team:delete` | `DELETE /teams/{id}`：`hasAnyRole(ADMIN,PL) **and** canManageTeam` → 团队长被拒 | 结果正确但理由不同，易误导 |
| 工程师被设为某团队 `leader_id` | 无任何 `team:*` | `isLeaderOfTeam` 只看 `teams.leader_id` → 可管理该团队 | 会隐藏实际可用的入口（t44 实测：smoke_engineer 因测试数据被设为负责人，页面显示「团队长视角」） |
| 工程师读资产列表/详情 | 有 `asset:read` | 列表 200、详情按 `canReadAsset` 可能 403（§2.3.2 #13） | 权限点无法表达数据级范围，只能以接口返回为准 |

**前端处置（t42/t43 已落地）**：`pages/teams/teamAccess.ts` 只按「角色 + `teams.leaderId`」判定，并在注释中写明「不要用 `team:update` 权限点」；`utils/accessRoles.ts` 同样以角色为主、权限点仅兜底。冒烟与页面验证均以接口实际返回（200/403）为准。

**证据**：`grep -rn "hasPermission(" backend/src/main/java | grep -v PermissionService` 无业务调用点；`grep -rn "hasAuthority" backend/src/main/java` 仅注释命中；`scripts/qa/scan-auth-*.py` 输出 134 个显式鉴权端点；`PrivilegedWriteAuthorizationTest` 10 例按角色/归属验证放行与拒绝。
---

### 2.6 四类资源「标签」写权限统一规则（t61）

历史遗留问题：同一个「标签」概念在不同资源上口径不一致 —— 资产标签 t49 已按资产写权收敛、
工具标签 t55 已限管理层，但**知识库文章标签与文档标签仍对全员开放**（任何人可改他人内容的标签）。
t61 统一为下表（规则同时写入 `deploy/README.md` §29）：

| 资源 | 写标签所需权限 | 守卫 | 依据 |
|---|---|---|---|
| 资产 | **资产写权**（负责人 或 管理层） | `canWriteAsset(#assetId)` | t49；标签影响资产检索与分组归属，属资产元数据 |
| 工具 | **管理层**（ADMIN / PROJECT_LEADER / TEAM_LEADER） | `hasAnyRole(...)` | t55；工具库是共享资产，标签用于分类检索 |
| 知识库文章 | **作者本人 或 管理层** | `canWriteKnowledgeTags(#id)` | t61；文章属个人产出，协作编辑走 `PUT /knowledge/{id}`，标签不应被任意人改 |
| 文档 | **作者本人 或 管理层** | `canWriteDocumentTags(#id)` | t61；同上（文档作者取 `documents.author_id`） |

不变量：
1) **查询类标签接口对所有登录用户开放**（列表/检索要用）：`GET /{id}/tags` 四类资源均未加门槛（资产标签跟随资产读范围 `canReadAsset`，t46 口径）；
2) **资源不存在 → 404**：`canWriteKnowledgeTags`/`canWriteDocumentTags` 对不存在的资源返回 true，由控制器抛 `KNOWLEDGE_NOT_FOUND`/`DOCUMENT_NOT_FOUND`（避免用 403 暴露存在性）；
3) 顺带修：`POST /knowledge/{id}/tags`、`POST /documents/{id}/tags` 此前对不存在的资源静默写入孤儿标签行，现均返回 404。

### 2.7 子资源写操作的存在性统一（t65）

同类缺陷在一个迭代内被**发现三次**（t55 工具标签、t61 文章/文档标签、更早的工具包上传），说明缺少统一约定，
t65 做了一次全量扫描并在 `service/SubresourceGuard` 收敛为单点守卫。

**统一规则**：父资源不存在 → **404**；子资源不属于该父资源 → **404**（与不存在同码，避免用状态码枚举）；正常路径语义不变。

| 端点 | 修复前 | 现在 |
|---|---|---|
| `POST /assets/{id}/tags` | 写孤儿标签 → 201 | **404** |
| `DELETE /assets/{assetId}/tags/{tagId}` | 可删他人资产标签，删 0 行 200 | **404** |
| `PUT /assets/{aid}/versions/{vid}/default`、`/latest` | HTTP **200 + body code 404** | **HTTP 404** |
| `POST /vulnerabilities/{id}/tags` | 写孤儿标签 → 201 | **404** |
| `DELETE /vulnerabilities/{vid}/tags/{tagId}` | 可删其他漏洞标签 | **404** |
| `POST /vulnerabilities/{id}/attachments` | 落库孤儿附件 + 建目录 | **404** |
| `DELETE /vulnerabilities/{vid}/attachments/{aid}` | 可删其他漏洞附件 | **404** |
| `POST /knowledge/{id}/comments` | 写孤儿评论 → 200 | **404** |
| `DELETE /documents/{docId}/tags/{tagId}` | 可删其他文档标签 | **404** |
| `DELETE /teams/{id}/members/{userId}` | 团队不存在也 200；非成员 200 | **404** |
| `POST /notifications/{id}/read`、`DELETE /notifications/{id}` | 静默 no-op → 200 | **404** |
| `POST /environments/{id}/snapshots/{sid}/rollback` | 快照不属于该环境 → 400 | **404** |
| `POST /tasks/{id}/attachments` | 写孤儿附件 → 200 | **404** |
| `POST /assets/{id}/access-requests` | 写孤儿访问申请 → 201 | **404** |
| 工具标签/工具包/工具版本、文章标签、文档标签、文档版本回滚、任务提交审核、团队成员添加、环境快照创建、漏洞附件下载 | t23/t37/t55/t61 已正确 | 已正确 404（防回归） |

**顺带修掉的 500**：`GlobalExceptionHandler` 业务码区间上限写成 `< 2000`，团队域错误码 2001/2002/2003 落到 else 分支
被 clamp 成 599 → `HttpStatus.valueOf(599)` 抛 `IllegalArgumentException` → 500（团队不存在本该 404）。
改为 `< 3000` 并用 `HttpStatus.resolve(...)` + 兜底 400，**非法业务码不再抛异常**。

证据：`SubresourceExistenceTest` 16 例（含「孤儿行未被写入」断言）、回归 151 例全绿（Asset/Vulnerability/Document/Knowledge/Tool/Team/Task/Environment/Notification + 四个鉴权专项 + i18n）。
规则与完整清单同步写入 `deploy/README.md` §31。

### 2.8 客户 / 项目 / 期次 / 资产挂靠 领域模型（t67，第一批：仅模型与迁移）

新增业务归属层：**客户 → 项目 → 期次 → 资产挂靠**。本批只落实体、仓储与 V18 迁移，不含 API（下一批）。

| 实体 / 表 | 要点 |
|---|---|
| `Customer` / `customers` | code 唯一(64)、name、industry、level(A/B/C)、contact_*、owner_id(客户经理)、status(active/inactive)；索引 status/owner |
| `Project` / `projects` | code 唯一、customer_id 非空、type(PENTEST/CODE_AUDIT/EMERGENCY/COMPLIANCE/TRAINING/OTHER)、status(DRAFT/ACTIVE/PAUSED/CLOSED)、manager_id 非空、start/end_date、is_phased 默认 false、contract_no；索引 customer/manager/status |
| `ProjectPhase` / `project_phases` | project_id 非空、phase_no、status(PLANNED/ACTIVE/DONE)；唯一 `(project_id, phase_no)` |
| `AssetProjectLink` / `asset_project_links` | asset_id/project_id/customer_id(冗余) 非空、phase_id 可空、link_type(TARGET/SHARED/REFERENCE)、remark、linked_by/linked_at；唯一 `(asset_id, project_id, phase_key)` |

**唯一约束的 NULL 方案**：MariaDB 唯一索引对 NULL 不去重，`UNIQUE(asset_id, project_id, phase_id)` 会放过重复的「项目级」挂靠。
采用任务书允许的 **`phase_key` 计算列**（非 MariaDB 生成列）：`phase_key` 为普通 NOT NULL 列，
`phase_id` 为空时写全 0 哨兵，由 `@PrePersist/@PreUpdate` 维护，唯一索引建在 `(asset_id, project_id, phase_key)`。
不用 `AS (...) STORED` 生成列的原因：那是 MariaDB/MySQL 方言，H2 测试库语法不同，迁移脚本就无法在测试里原样执行验证。

**`Asset` 不加 projectId 的原因**：资产常同时服务多个客户/项目（多对多）；单值外键会把历史挂靠覆盖掉，
与审计可追溯相冲突；挂靠还需携带 link_type/remark/期次等自身属性；存量回填若走外键等于断言所有历史资产同属一个项目。

**迁移 V18**：四张表 + 索引（`IF NOT EXISTS`）→ 默认客户 `CUST-DEFAULT`（「历史数据」）→ 历史项目 `PRJ-HISTORY`
（customer=默认客户、ACTIVE、manager 取首个 admin，退化到首个用户/全 0 哨兵）→ 存量资产全部回填一条
`phase_id=NULL / phase_key=全 0 / link_type=SHARED` 的挂靠。全部语句 `WHERE NOT EXISTS`，**可重复执行**；
不使用窗口函数，MariaDB 10.x 与 H2 共同可执行。

证据：`CustomerProjectModelTest` 7 例（空库执行迁移、存量回填、重复执行不重复、迁移不覆盖已有业务挂靠、
仓储按 project/phase/customer 查询、`phase_key` 唯一性、期次号与 code 唯一）、回归 88 例全绿（Asset/Dashboard/Team/RBAC/Subresource）。
详细表结构与选型见 `deploy/README.md` §33。

## 3. SRS 需求覆盖对照

| SRS 章节 | 状态 | 已实现内容 / 缺口说明 |
|---|---|---|
| 2. 用户与权限（角色、用户管理、RBAC） | **已实现** | 4 内置角色 + 90 权限点 + 角色/权限分配 + 用户启停/重置密码；`RbacPermissionTest` 22 例、`UserControllerTest`、`ImportUsersTest`；缺口见 §2.3 #7/#8（团队组织维护无角色门槛） |
| 3.1 目标资产管理 | **已实现** | 资产 CRUD（写侧数据级授权）、团队归属、标签、分组、5 类元数据、文件/交付物、版本（默认/最新/删除）、认领、访问申请、变更历史、订阅；`AssetControllerTest`、`AssetFileControllerTest`、`AssetVersionControllerTest`、`AssetGroupControllerTest`；缺口：分组与版本 default/latest 无写权校验（§2.3 #2/#6），认领完成/放弃恒 403（§2.3 #5） |
| 3.2 环境管理 | **已实现** | 环境 CRUD、Docker/Noop 部署器、部署/启停、快照与回滚、部署器可用性提示；无 Docker 时 503 + `failed` + `failReason`；`EnvironmentServiceImplTest` 等；缺口：环境增删改/部署无角色门槛（§2.3 #9），真实容器编排需宿主安装 Docker |
| 3.3 文档管理 | **已实现** | 文档 CRUD、版本快照（创建 v1、编辑递增、回滚写新快照 `rollback from vN`）、访问授权、发布/取消发布、标签；`DocumentControllerTest` 14 例、`DocumentVersionConcurrencyTest` 3 例、`DocumentRollbackSnapshotTest` 4 例；缺口：文档删除仅数据级（矩阵 2.7 允许全角色，一致） |
| 3.4 漏洞生命周期 | **已实现** | 创建/更新（DTO + camelCase + 部分更新）、状态机（workflow/customer/disclosure/exploitStage/vendorFix）、交付、指派、历史、标签、附件上传/下载、可见范围收敛、统计端点；`VulnerabilityDtoBindingTest` 9 例、`VulnerabilityAuthorizationTest` 10 例、`VulnerabilityAttachmentDownloadTest` 7 例 |
| 3.5 工具资源库 | **已实现** | 工具 CRUD、分类、热门、标签、真实工具包上传/下载、版本（`versionNo` 唯一 + `hasFile` + 下载字节一致）、下载计数；`ToolVersionConcurrencyTest`；缺口：工具改删/版本上传无角色门槛（§2.3 #15） |
| 3.6 团队协作 | **已实现** | 团队树（roots/tree/children）、成员管理、团队用户、任务（创建/指派/状态/评论/附件/协作者/审核）；`TeamControllerTest`、`TaskControllerTest`、`ApprovalFlowTest`、`PrivilegedWriteAuthorizationTest`；团队 CRUD/成员维护的角色与团队归属门槛已于 t40 收口，任务创建无角色门槛仍是已知缺口（§2.3.2 #14） |
| 3.7 权限与安全控制 | **已实现** | 角色注解 + 数据级 `AccessGuard`（资产/漏洞/文件/文档/任务）+ JWT + 登录限流 + CORS 白名单 + 审计；缺口见 §2.3（13 组：中 10 / 低 3），均不阻断功能 |
| 3.8 知识沉淀与传承 | **已实现** | 文章 CRUD、分类、标签、评论、搜索、热门、按作者/分类列表；`KnowledgeControllerTest`；缺口：改删文章无角色/作者校验（§2.3 #10） |
| 3.9 消息通知模块 | **已实现** | 站内通知、未读数、已读/全部已读、删除、设置、公告、订阅、投递记录（channel/sendStatus/sentAt/errorMessage）、邮件/Webhook 通道（默认关闭）；公告写操作的角色门槛已于 t40 收口；主动发通知与投递记录读取仍是已知缺口（§2.3.2 #12/#13） |
| 3.10 仪表盘与统计 | **已实现** | 概览/资产统计/漏洞统计（角色门槛 + 可见范围收敛）、工作量、趋势（归日补零、`startDate/endDate`）、跨用户排行、配置、CSV 导出（管理层）；`DashboardControllerTest`、`DashboardTrendTest`、`DashboardRankingTest`、`DashboardStatsAuthorizationTest`、`DashboardExportAuthorizationTest` |
| 4.5 数据迁移（导入导出） | **已实现** | 资产/漏洞/用户 CSV 导入（模板带 BOM、部分成功语义、逐行权限与数据边界、失败行回显）；审计与仪表盘 CSV 导出；用户导入一次性密码 |
| 4.6 国际化 | **已实现（全覆盖：961/961 词条中英对等，`TODO(i18n)` 0 处）** | 自建 i18n：`I18nProvider/useT` + `translate()`（非组件环境）、`LangSwitcher`、localStorage 持久化、`<html lang>`/dayjs/antd locale 同步、`ConfigProvider.form.validateMessages`；**前端自有 `message.*` 文案中文 0 处**（274 个调用全部 key 化）；请求头 `Accept-Language` + 后端 `MessageSource`（`messages[_en].properties`）→ 错误消息随语言切换（`?lang=` → `Accept-Language` → 默认中文）。覆盖：登录、侧边栏 18 菜单、通用组件与 403/404、通知中心（3 页签/公告/设置）、仪表盘（含图表内部文案）、任务、文档、工具、知识库、团队、角色、用户、审批、审批范围、导入（5 类 Tab + 结果区）、审计、资产、漏洞、客户、项目。**仍保留**：①后端 `data.message` 与 blob 错误体（由后端 i18n 决定，前端透传）；②部分页面正文（tasks/users/teams/roles/approvals/asset-groups，约 552 行）；③`@deprecated` 兼容常量（key 版在用）；④语言偏好仅 localStorage |
| 4.7 接口需求 | **已实现** | REST `/api/v1/**` + 统一 `ApiResponse` 包装 + OpenAPI（`/v3/api-docs`）；262 个端点 |

---

### 2.8 客户 / 项目 / 期次 API（t68）

四个新 Controller 端点共 **16 个**（客户 6 / 项目 7 / 期次 3），权限口径与状态码：

| 资源 | 读 | 写 | 备注 |
|---|---|---|---|
| 客户 | 全员 | ADMIN / PROJECT_LEADER | 删除保护：有项目 → 409 |
| 项目 | 全员 | ADMIN/PL 或该项目经理（`canManageProject`） | 状态机 + 删除保护（有挂靠 409） |
| 期次 | 全员 | ADMIN/PL 或所属项目经理（`canManagePhase`） | 仅分期项目可建；有挂靠删 409 |

项目状态机（`DRAFT→ACTIVE→PAUSED↔ACTIVE→CLOSED`，CLOSED 为终态，非法流转 400 且消息含当前状态与合法下一步）：

```
DRAFT ──► ACTIVE ──► PAUSED ──► ACTIVE
             │           │
             └──► CLOSED ◄┘     （CLOSED 终态）
```

证据：`CustomerProjectApiTest` 14 例（CRUD / 编码冲突 409 / 删除保护 409 ×3 / 状态机合法与非法 /
期次仅分期可建 / 关闭分期约束 409 / team_leader 自己项目 200 他人 403 / 工程师写 403 读 200 / 审计落库）。
审计资源类型经 `AuditTrailInterceptor` 映射为 `customer` / `project` / `project-phase`
（其中 `/projects/{id}/phases` 通过「最后一段是否为 phases」识别，t68 新增）。
### 3.1 客户与项目管理（t67–t78）

**领域模型**：`customers 1 ── N projects 1 ── N project_phases`，`assets ↔ projects` 通过 `asset_project_links` 多对多（同一资产可同时服务多个客户/项目）。

```
customers(id, code uk, name, industry, level, owner_id, status) 
   └─ projects(id, code uk, customer_id fk, name, type, status, manager_id, is_phased, start/end_date, contract_no)
        ├─ project_phases(id, project_id fk, phase_no, name, status)     uk(project_id, phase_no)
        └─ asset_project_links(id, asset_id, project_id, customer_id, phase_id, link_type, remark)
             uk(asset_id, project_id, phase_id)   ← 同一资产同一项目同一期次唯一
```

| 接口 | 权限 | 说明 |
|---|---|---|
| `GET /customers`、`GET /customers/{id}`、`GET /customers/{id}/summary`、`/{id}/stats` | 全员可读 | summary 一次返回 stats + projects(含 phaseCount/assetCount/managerName) + recentAssets（t76，固定约 8 次查询） |
| `POST/PUT /customers`、`PATCH /customers/{id}/status`、`DELETE /customers/{id}` | ADMIN / PROJECT_LEADER | `PATCH` 的 `status` 是 **query 参数**；删除有项目的客户 → 409 |
| `GET /projects`、`GET /projects/{id}`、`/{id}/stats`、`/{projectId}/phases`、`/{projectId}/assets` | 全员可读（`/assets` 按 `visibleAssetIds` 收敛） | 列表支持 customerId/status/type/keyword |
| `POST/PUT /projects`、`PATCH /projects/{id}/status`、`DELETE /projects/{id}` | ADMIN / PROJECT_LEADER | PUT 需 `code`；删除有挂靠的项目 → 409 |
| `POST /projects/{projectId}/phases`、`PUT/DELETE /phases/{phaseId}` | ADMIN / PROJECT_LEADER | 期次号 `phase_no` 由服务端分配 |
| `POST/GET /assets/{id}/project-links`、`DELETE /assets/{id}/project-links/{linkId}` | 写需资产写权（`canWriteAsset`） | 跨客户挂靠允许；重复挂靠 → 409；**解绑最后一条 → 409** |
| `GET /dashboard/customer-distribution`、`/project-distribution` | 管理层（300/301） | `assetCount` 按可见范围收敛，`projectCount` 为组织全量口径 |

**状态机**：`DRAFT → ACTIVE → PAUSED → ACTIVE`，`ACTIVE/PAUSED → CLOSED`，**CLOSED 为终态**（不可编辑/删除/新建期次/流转）。
**类型枚举**：`PENTEST / REAUDIT / CONSULTING / TRAINING`。

**约束与不变式（已在测试与冒烟中硬断言）**
1. `assets` 必须挂至少一个项目：`POST /assets` 缺 `projectId` → **400**；`projectId` 不存在 → **404**；`phaseId` 不属于该项目 → **400**；解绑最后一条挂靠 → **409**。
2. **无挂靠资产数必须为 0**：`SELECT COUNT(*) FROM assets a WHERE NOT EXISTS(SELECT 1 FROM asset_project_links l WHERE l.asset_id=a.id)` —— 迁移后与冒烟后各查一次均为 **0**。
3. 客户下仍有项目 → 删除客户 **409**；项目下仍有资产挂靠 → 删除项目 **409**。
4. 写权限：客户/项目写仅 ADMIN/PROJECT_LEADER；项目更新/期次另需「经理本人或管理层」；工程师建客户/项目 **403**。
5. CSV 导入也遵守上表：未给 `projectCode` 时自动挂 `CUST-DEFAULT / PRJ-HISTORY`（`linkType=SHARED`）并在 `notes[]` 提示（成功行，不静默；t78）。

**迁移（V18）**：纯新增四表 + 唯一约束 + 种子行（`CUST-DEFAULT`、`PRJ-HISTORY`）+ 存量资产回填，全部 `IF NOT EXISTS` / `WHERE NOT EXISTS` 幂等。生产 Flyway 关闭，等价脚本已并入 `deploy/manual-ddl.sql` 第 4 段（含校验查询）。

**已知限制**
1. 客户下「资产明细」走 `GET /assets?customerId=` 分页（t77），不是客户内嵌数组；
2. `projectCount`/`phaseCount` 为组织全量口径，与 `assetCount`（可见范围）不同，页面上已用口径 Tag 标注；
3. ~~项目列表的期次数/资产数来自按页并发 `GET /projects/{id}`~~ → **已解决（t82）**：
   新增 `GET /projects/summary` 批量返回 `phaseCount/assetCount/openTaskCount/managerName/customerName`，
   查询数固定约 7 条、与页大小无关（见 §2.14）；前端切换该接口后可彻底去掉并发拉取；
4. ~~`POST /projects` 缺 `manager_id` 时数据库约束报 500~~ → **已解决（t80）**：DTO `@NotNull` → 400 + `data.managerId`。

### 3.2 管理员模拟视角（t90/t91）

**用途**：管理员在线复现用户反馈的问题（“我这个账号看不到某资产/按钮”），无需索取对方密码、也无需临时改角色。

**接口**
| 接口 | 权限 | 说明 |
|---|---|---|
| `POST /api/v1/admin/impersonate` | **仅 ADMIN** | body `{userId, reason?}` → `{token, expiresAt, target{id,username,roles[]}, actor{id,username}}` |
| `POST /api/v1/admin/impersonate/stop` | ADMIN（普通态）或持有模拟 token（模拟态，服务层校验 `act` 必为 ADMIN） | **204** |

**受限 token**：`sub`=目标用户、`roles`=目标真实角色、`act`=adminId、`imp=true`、默认 TTL **2 小时**（`app.security.impersonation.ttl-seconds` / `APP_IMPERSONATION_TTL_SECONDS`）。
即：模拟期间权限**完全等于目标用户**（目标是团队长就能做团队长的一切），不是提权也不是降权。

**限制（后端强制，逐条有测试）**
1. 仅 ADMIN 可发起；非 ADMIN **403**；目标不存在 **404**；目标停用 **400**；目标是 ADMIN **409**。
2. **禁止链式模拟**：模拟态再调 `POST /admin/impersonate` → **403**。
3. **敏感写禁用**（仅拦 POST/PUT/PATCH/DELETE）：`/users/**`（建改删、重置密码、改角色、改权限点）、`/roles/**`、`/admin/**`；`/admin/impersonate/stop` 显式放行。读到这些路径不拦（看到什么仍由目标权限决定）。
4. token 过期 → **401**；前端会自动回退原 token 并刷新。
5. 关闭开关：`app.security.impersonation.enabled=false`（或 `APP_IMPERSONATION_ENABLED=false`）→ 两个端点均 **403**。

**审计**：发起/停止由**服务层**写专用事件 `IMPERSONATE_START` / `IMPERSONATE_STOP`（`details` 含 `actor` / `target` / `ttlSeconds` / `reason`，一次模拟仅 1 条；t93 修掉「拦截器重复写 + 截断」，t94 修掉「`@Transactional(readOnly=true)` 静默丢弃插入」，实测见 §2.16）；模拟期间的业务写 `userId`=**目标用户**、`details` 追加 `impersonatedBy=<adminId>`，因此**任何一次模拟期内的操作都能回溯到真实管理员**。

**前端（t91）**：用户菜单「以其他用户视角查看」（`isAdmin && !impersonating` 才渲染）→ 选择用户（过滤 admin）→ 整页 reload → 顶部横幅「正在以 <用户名>（角色）的视角查看 · 管理员模拟模式」+ 剩余倒计时（<1 分钟变红）+ 退出模拟；localStorage 三键 `access_token` / `impersonation.original` / `impersonation.target`。

**风险与建议**：TTL 内 token 等效目标用户，建议生产开启时配合「仅内网可用 + 审计日检 + reason 必填」；当前 `reason` 为可选字段。

### 3.3 登录页主题与动态角色（t95 / t97 / t99 / t100，t98 验收）

**用途**：登录页从「一张静态表单」升级为**可换肤的入口** —— 内置 4 套会跟随鼠标的卡通角色主题，另有 4 套「大气简约」抽象主题；用户选定的主题**跨设备保留**（存服务端 `users.login_theme`）。

**八套主题（后端 `LoginTheme` 枚举 = 前端 `themes.ts` 注册表，一一对应）**

| id | 组 | 视觉 | 动态 |
|---|---|---|---|
| `pencil` | 角色 | 米白 / 墨黑 / 橙，手绘线条小人 | 瞳孔跟随光标；呼吸浮动；4–6s 随机眨眼；聚焦密码时**抬臂遮眼 + 低头** |
| `geometric` | 角色 | 浅蓝 / 深蓝 / 白，圆头方身大圆眼 | 同上；聚焦密码时**歪头 + 眼神偏移** |
| `owl` | 角色 | 芥末黄 / 深棕 / 米色，猫头鹰 | 同上；聚焦密码时**抬翅膀遮眼** |
| `pixel-eyes` | 角色 | 深绿 / 荧光绿 / 黑，像素方块脸 | 眨眼为 `steps()` 明暗跳变；聚焦密码时**脸侧过去** |
| `aurora` | 抽象 | 深墨底 + 两团柔和径向光斑（≤3 色） | 光斑 17s / 14s 缓慢漂移；光标处极淡光晕跟随；聚焦密码时收拢 + 降亮 |
| `mono-grid` | 抽象 | 浅灰底 + 1px 低对比网格 | 光标 120px 内交叉点轻微加深；聚焦密码时网格淡到 20% |
| `orbit` | 抽象 | 纯色底 + 两个同心细环 + 一个点 | 点 12s 一圈；鼠标带来 ±2° 倾斜；聚焦密码时点缩小并加速到 4.5s |
| `big-type` | 抽象 | 纯色底 + 超大产品名（细/粗字重对照） | ±6px 文字视差；聚焦密码时文字下沉 10px 并淡到 30% |

**交互规格（角色组：纯受控组件 + 零新增依赖）**

| 交互 | 实现 | t98 实测判据 |
|---|---|---|
| 眼睛跟随 | `mousemove` → `atan2` 归一化向量 → SVG `translate`，CSS `transition 180ms` 平滑，~30fps rAF 节流 | 光标在右 / 左 / 下时瞳孔位移 `x=+3.20 / -3.19 / y=+2.80`（方向相反、限幅正确；截图 `shot-eye-right|left|down.png`） |
| 密码遮挡 | 密码框 focus 时给角色根 `<svg>` 加 `is-shielding`，各主题用 CSS 变换表达「遮眼 / 撇头」 | focus → `is-shielding` 且 `.pencil-arm` 旋转到位；blur → 类名移除 |
| 眨眼 | `useBlink`：4–6s 随机间隔、单次闭合 ~150ms，卸载即清定时器 | 12s 观察窗内捕获 **2 次** `is-blinking` |
| 错误反馈 | `hasError` 上升沿触发一次抖动（角色 620ms；抽象主题整层 1px 位移 480ms）+ 行内 Alert + 旁白切换 | 真实 401 下 `is-shaking` 出现、旁白「这次没成功，再试一次吧」 |
| 减少动效 | `prefers-reduced-motion: reduce` → 呼吸 / 眨眼 / 抖动与过渡全关，**瞳孔仍跟随但不加缓动** | `.char-bob` `animation-name: none`、瞳孔 `transition-duration: 0s` |

**preferences 接口**（安全边界与单测见 §2.17）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/users/me/preferences` | `{loginTheme}`；库内脏值回落 `pencil` |
| PUT | `/api/v1/users/me/preferences` | body `{loginTheme}`；白名单 8 项、大小写/空格归一；非法 → **400**（中英）；只能改自己 |
| POST | `/api/v1/auth/login`、`GET /auth/me` | 响应自带 `loginTheme`，登录后无需再查 |
| 审计 | `UPDATE_LOGIN_THEME` | `resource_id`=本人；同值不写；`SKIP_ENDPOINTS` 精确跳过该端点，一次改动仅 1 条 |

**持久化时序（前端 `utils/loginTheme.ts`）**

1. **未认证进入登录页**：`?theme=` 深链（预览/验证）> `localStorage['login.theme']` > `pencil`；**认证前不调 preferences**（会 401）。
2. **登录成功**：用登录响应里的 `loginTheme` 回写 localStorage（不比对、不刷新页面，下次进登录页生效）。
3. **进入应用后**：`GET /auth/me` 返回后再同步一次，纠正「换设备 / 缓存过期」的偏差。
4. **退出登录**：**不清**缓存（下次登录页仍显示上次主题）。
5. **修改偏好**：用户菜单「偏好设置」→ 卡片选择 → `PUT /users/me/preferences` → **服务端成功后**才写 localStorage（避免「页面 A / 账号 B」分叉），并 toast 提示。

**已知限制**

1. **登录前只能读本地缓存**：换浏览器 / 清缓存后首次进登录页显示 `pencil`，登录成功（或 `/auth/me` 返回）后才纠正为该账号主题 —— 这是「认证前拿不到服务端偏好」的必然结果，不是缺陷。
2. **主题白名单以后端为准**：前端注册表与后端 `LoginTheme` 需同时扩展；只改前端会收到 400，界面显示「服务端未启用该主题，已保持原主题」并置灰该卡片（过渡期行为，t97 已实测）。
3. **偏好弹窗的「预览」是只读 iframe**（`/login?theme=<id>`），不触发任何认证请求。
4. 抽象主题占据整页背景层（`pointer-events: none`），表单卡片始终在上层；装饰 `aria-hidden`，读屏器不朗读。

**t98 验收证据**

- 页面（真实 Edge + 原生 CDP，线上站点 192.168.188.200:8089）：**47/47 断言 PASS、`pageErrors=[]`** —— 8 套主题渲染与底色、瞳孔三方向跟随、密码遮挡与解除、12s 内眨眼 2 次、reduced-motion 关闭动效、375px 窄屏 8 套均不重叠、偏好弹窗 8 卡片 / 2 分组 / 8 缩略图、选 `owl` 落库 + 退出后仍 `owl`、清缓存回落 `pencil` 并在重新登录后被服务端纠正、英文登录页与偏好弹窗零中文残留。
- 冒烟：**244/244**（新增 16 条：pref/未认证 401、默认 pencil、四套角色主题 PUT + `/auth/me` 回显、抽象四套、非法值中/英 400、只能改自己、登录响应含 `loginTheme`、审计单条（+1 且无拦截器重复行）、访问申请同团队 201 / 重复 pending 409）。
- 截图与日志：`.tmp-t98/shot-theme-*.png`、`shot-eye-*.png`、`shot-narrow-375.png`、`shot-pref-modal-zh|en.png`、`verify.log`、`result.json`。

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
| 8 | ~~匿名请求统一返回 403~~ **已于 t45 修复** | t45 配置 `AuthenticationEntryPoint`（未认证 → 401）与 `AccessDeniedHandler`（无权限 → 403），语义符合 HTTP 规范 | 客户端可据此区分「需要重新登录」与「确实无权限」；冒烟原「接受 401/403」的宽松断言可收紧为 401 |

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
| 后端自动化（t25 + t41） | **475 用例 / 100% 通过**：t25 基线 452（同一快照连续两次全绿，manifest `77535ad2…`）+ t39 `AssetClaimLifecycleTest` 13 + t40 `PrivilegedWriteAuthorizationTest` 10；t41 在真实工作区 `mvn -B clean test` 56:54 全绿，Skipped=0、无 `@Disabled/@Ignore` | `deploy/regression-result.md`、`deploy/regression-manifest.txt`、`deploy/README.md` §20 |
| 空库引导 | 56 张表、RBAC 90/261/4、admin 登录 200、`permissionCount=90`、`/v3/api-docs` 200 | t25 报告 §4 |
| 线上冒烟（t13 + t41） | **108/108 PASS**（92 基线 + t41 新增 16 条：认领生命周期/非本人完成 403/管理层强制放弃/鉴权收紧 403 与查询 200/未认证拒绝/公告不传 publishedAt/快照缺 name 400），t13 另有 92/92 连续两次全绿 | `deploy/smoke-result.txt`、`deploy/README.md` §20 |
| 部署后专项核对 11a~11k | **13/13 PASS** | `deploy/smoke-result.txt` 第二节 |
| 三账号交叉验证 | 管理员：全量可见 + 全部管理接口 200；小组长：统计/排行按本团队收敛、审计与导出 403、导入限本团队（越权行 `权限拒绝：` 且不落库）；工程师：资产/漏洞越权 403、审计/仪表盘统计与导出 403、导入资产 403、仅漏洞导入可用（自己的资产 200） | 冒烟脚本 `auth/*`、`audit/工程师403`、`dash/导出-工程师403`、`import/用户导入-工程师403`；`ImportAuthorizationTest`、`DashboardStatsAuthorizationTest`、`VulnerabilityAuthorizationTest` |
| 前端构建 | `tsc -b && vite build` 成功（3680 modules，`index-DmCRvmQl.js` 2,224.37 kB），产物已上线 | t13 报告 A |

### 7.1 前端发布与页面级验证（t44）

| 项 | 结果 |
|---|---|
| 构建 | `npm install --legacy-peer-deps && npm run build` → 3682 modules，`dist/static/index-CRqqK9hb.js` 2,230.05 kB（gzip 666.32 kB），built in 4.64s |
| 同步 | `SUDO_PASS=*** bash deploy/sync-dist.sh`（rsync --delete）→ `/var/www/vuln-audit` 仅 5 个文件：index.html、favicon.svg、icons.svg、`static/index-CRqqK9hb.js`、`static/index-TaRZIJNx.css`；旧 `index-DmCRvmQl.js` 已清除；`GET /`=200、`GET /static/index-CRqqK9hb.js`=200 |
| 验证方式 | Playwright（msedge headless）直连线上 `http://192.168.188.200:8089`，三账号（admin / smoke_leader / smoke_engineer），脚本 `scripts/qa/verify-t44.mjs` |

| # | 验证项 | 结果 |
|---|---|---|
| 1 | 工程师 `/teams` 无任何写入口 | **PASS**：新建/子团队/编辑/删除/添加成员/移除 六项全为 0；团队树渲染、成员表可读 |
| 2 | 团队长仅能改自己领导团队，无新建/删除 | **PASS**：自己团队「编辑 + 添加成员」可见，「新增子团队 / 删除」不可见；切到他人团队后入口消失并提示「仅可查看成员（无管理权限）」 |
| 3 | admin/PL 写入口全可见 | **PASS**：创建团队/新增子团队/编辑/删除/添加成员 5 项全真 |
| 4 | 公告区写入口仅 admin/PL | **PASS**：admin 见「发布公告」且「生效中的公告」渲染；团队长与工程师无写入口、见只读提示、列表正常 |
| 5 | 环境快照空 name 前端拦截 + 只读角色隐藏创建区 | **PASS**：admin 清空 name 点「创建快照」→ 提示「请先填写快照名称…」且 **POST /snapshots 请求数 0**；工程师「添加环境」为 disabled、快照入口与创建区不渲染 |
| 附加 | 运行期无 JS 异常 | **PASS**：`pageErrors=[]`，无 5xx |

> 数据依赖说明（非代码缺陷）：`teams.leader_id` 指向谁，谁就能管理该团队（`AccessGuard.isLeaderOfTeam` 只按数据判定）。实测环境里 `smoke_engineer` 被某测试数据设为团队负责人，故该账号 `/teams` 顶部显示「团队长视角」而非「只读视角」，与后端行为一致。

### 7.2 最终发布与全链路验收（t47）

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test` → **Tests run: 490, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS（20:58）；增量 15 例 = t45 `UserDirectoryAuthorizationTest` 8 + t46 `AssetVisibilityConsistencyTest` 7 |
| 前端发布 | `npm run build` → `dist/static/index-BWmznSNU.js` 2,231.44 kB（gzip 667.08 kB）；`sync-dist.sh`（rsync --delete）→ `/var/www/vuln-audit` 仅 5 个文件，旧 `index-CRqqK9hb.js` 已清除；`/`=200、js=200 |
| 后端重建 | 远端 `mvn -B -DskipTests package` BUILD SUCCESS 10.9s，jar **72,874,974 B**；`systemctl --user restart` 后 login=200、api-docs=200、**未认证 `/api/v1/users`=401** |
| 冒烟 | **TOTAL=128 / PASS=128 / FAIL=0 / SKIP=0**（新增 20 条断言：严格 401 / 无效 token 401 / 工程师用户域 403×4 / 目录 200 且无 PII×2 / `/auth/me` 与 `/users/me/permissions` 三角色 200×6 / 资产可见性不变量三账号×6） |

| # | 页面补验（要求） | 结果 |
|---|---|---|
| 1 | 账号管理页对工程师隐藏或 403 可读提示 | **PASS**：侧栏「账号管理」菜单对工程师不渲染；直接访问 `/users` 无用户表数据（拦截/空态），管理员访问正常显示 10 行 |
| 2 | 资产列表点进详情不再掉 403 | **PASS**：工程师列表中资产 `/assets/{id}` 正常加载（有 Tab、标题为「冒烟资产…/AST-…」），无「不存在或已被删除 / 403」字样 |
| 3 | 任务负责人下拉正常取数 | **PASS**：任务页人员下拉展开 **5 个选项**（数据源 `/users/directory`=200，响应无 email/phone/roles/passwordHash） |
| 4 | `/teams`、公告区写入口仍按角色隐藏 | **PASS**：工程师与团队长均无「创建团队/编辑/删除」与「发布公告」；管理员全部可见 |

> 说明：规格里写的 `GET /users/me` 实际不存在（会被 `/users/{id}` 当作 id 解析返回 400）；「查看自己」的等价端点是 `GET /auth/me` 与 `GET /users/me/permissions`，两者对三种角色均 200，冒烟已按此断言。


### 7.3 第二批发布与回归（t54：缺口清零 + i18n 首批）

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test` → **Tests run: 524, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS；`@Disabled/@Ignore` 0 命中。524 = 第一批 490 + t49 `AssetDomainAuthorizationTest` 7 + t50 `CollaborationDomainAuthorizationTest` 15 + t51 `OrgToolDomainAuthorizationTest` 7 + t55 `ToolWriteAuthorizationTest` 5 |
| 回归修复（测试跟随新契约，未放宽校验） | ①`NotificationTypeLengthTest` 3 例改用 adminToken（t50 已把漏洞通知群发收敛为管理层）；②`PrivilegedWriteAuthorizationTest#readEndpoints_stillOpenForEngineer` 对 `GET /teams/{id}/members` 的断言改为「团队长 200 / 非成员工程师 403」（t51 可见团队口径） |
| 前端构建 | `npm run build` → 3697 modules，`dist/static/index-D2WA8xPO.js` 2,256.33 kB（gzip 674.73 kB）；产物同时含中英文串（`Sign in`/`Dashboard`/`Notifications`/`Page not found` 与 `登录`/`仪表盘`/`通知中心` 均在） |
| 部署 | 后端 package BUILD SUCCESS，jar **72,877,443 B**，重启后 login=200、api-docs=200；前端 sync-dist 后站点仅 5 个文件（`index-D2WA8xPO.js`），旧 hash 已清；**无新增迁移** |
| 冒烟 | **TOTAL=154 / PASS=154 / FAIL=0 / SKIP=0**（新增 26 条：6 组资产域写 403、分组写 403×2、协作域 403×4、组织工具域 403×6、查询正向 200×5、工具不存在 404×1、资产标签写 403×1） |

| # | 页面验证（t52/t53 口径） | 结果 |
|---|---|---|
| 1 | 工程师：新建资产/新建分组/新建任务 **不渲染**；工具「上传」disabled、删除链接不渲染 | **PASS**（`assets.create=0 / groups.create=0 / tasks.create=0 / tools.upload=disabled / tools.delete=0`） |
| 2 | 团队长：可建资产与建任务；工具上传可用、删除不可用 | **PASS**（`assets.create=1 / tasks.create=1 / tools.upload=enabled / tools.delete=0`） |
| 3 | 管理员：全部可用（含删除工具） | **PASS**（`tools.upload=enabled / tools.delete=1 / assets.create=1 / tasks.create=1`） |
| 4 | 中英切换：登录页立即英文、`<html lang=en-US>`、刷新保持；仪表盘/通知中心英文；切回中文恢复 | **PASS**（6 项断言全真，`pageErrors=[]`） |

### 7.4 i18n 二批：资产域与漏洞域文案迁移（t57）

| 项 | 结果 |
|---|---|
| 覆盖文件（7 个，全部移除首行 `// TODO(i18n)`） | `pages/assets/index.tsx`、`pages/assets/Detail.tsx`、`pages/assets/AssetTagsPanel.tsx`、`pages/asset-groups/index.tsx`、`pages/vulnerabilities/index.tsx`、`pages/vulnerabilities/Detail.tsx`、`pages/vulnerabilities/constants.ts` |
| 新增词条 | **670 条**（中英各一份）：资产域 **436**（`asset.list.*` / `asset.status.*` / `asset.request.*` / `asset.form.*` / `asset.tags.*` / `asset.detail.*` / `assetGroup.*`）+ 漏洞域 **234**（`vuln.status.*` / `vuln.customer.*` / `vuln.disclosure.*` / `vuln.stage.*` / `vuln.fieldTest.*` / `vuln.severity.*` / `vuln.history.*` / `vuln.list.*` / `vuln.form.*` / `vuln.edit.*` / `vuln.detail.*`） |
| 词条组织 | 新增 `i18n/messages/domains/assetMessages.ts` 与 `domains/vulnMessages.ts`（`assetZh/assetEn`、`vulnZh/vulnEn`），由 `zh-CN.ts` / `en-US.ts` 展开合并；**主词条表是并发任务共用文件，域文件拆分避免写冲突**，`MessageKey` 仍由 `keyof typeof zhCN` 统一推导，`en-US.ts` 的 `Record<MessageKey, string>` 继续兜底缺键 |
| 枚举展示 | `vulnerabilities/constants.ts` 不再返回中文：`StatusOption` 改为 `labelKey: MessageKey`，导出 `workflowKey/customerKey/disclosureKey/exploitStageKey/fieldTestKey/severityKey`，颜色函数保持不变；组件内在 `useT()` 之上包一层同名 `workflowLabel/customerLabel/...` 兼容旧调用点，未知值回落原始枚举值。**接口枚举值与 `console` 日志未翻译**（如 `'探测'` 仍作为 PATCH 请求体） |
| 验证（`T57_VERIFY: PASS`，`pageErrors=[]`） | 本地新 dist + 反代已部署后端（`192.168.188.200:8089`），`app.lang=en-US` 下：资产列表 → `Asset management`/`Asset name`/`Asset code`/`New asset`/状态 `Active·Under test·Suspended·Archived` 全英文且无中文标题；资产详情 → 页头按钮 `Request access · Claim asset · Subscribe · New version · Set as default · Set as latest · Back to list`、7 个 Tab（`Asset files / Product overview / Asset tags / Environments / Audit tasks / Vulnerabilities / Deliverables`）、5 步概览（`Basics & storage … Additional info`）、表单标签与 placeholder 全英文；资产分组 → `Asset groups` + `Name/Type/Description`；漏洞列表 → `Vulnerability management`、列头 `No./Title/Asset/Type/Severity/CVSS/Workflow/Customer/Disclosure/Actions`、筛选项与状态标签英文；漏洞详情 → `Workflow status / Customer status / Disclosure status` 三卡、`Description / Impact analysis` 分节、按钮 `Deliver to customer / Assign / Update fix status / Mark as disclosed` 英文；列表抽屉同样英文；切回 `zh-CN` 后恢复「漏洞管理 / 严重程度 / 查 询」。线上库当时资产与漏洞均为空，脚本临时 `POST /assets` + `POST /assets/{id}/versions` + `POST /vulnerabilities` 造数，验证后 `DELETE` 清理（返回 200/200） |
| 构建 / 类型 / 规范 | `cd frontend && npm install --legacy-peer-deps && npm run build` → `✓ 3699 modules`，`dist/static/index-BZouiz1c.js` 2,391.75 kB（gzip 701.34 kB）；`npx tsc -p tsconfig.json --noEmit` 0 error；`npx eslint src/pages/assets src/pages/asset-groups src/pages/vulnerabilities src/i18n` 0 warning |
| 仍留 `// TODO(i18n)` 的文件 | **本任务范围内 0 个**。全仓剩余 1 个：`pages/notifications/SettingsTab.tsx`（属 t53 遗留清单里的通知 Tab 内部明细，不在本任务覆盖范围，由后续批次接手） |


### 7.5 第三批发布与回归（t63：i18n 二期 + 标签权限统一）

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test` → **Tests run: 557, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS；`@Disabled/@Ignore` 0 命中。557 = 第二批 524 + t60 `ApiMessageI18nTest` 10 + t61 `TagWriteAuthorizationTest` 7 + t65 `SubresourceExistenceTest` 16；`ToolVersionConcurrencyTest` 在全量中通过（t51 曾漏测的点） |
| 回归修复（测试跟随新契约，未放宽校验） | `AssetVersionControllerTest#setDefaultVersion_NotFoundWhenVersionNotBelongs`：t65 把「版本不属于资产」由「HTTP 200 + body 404」改为**真 404**，断言同步为 `isNotFound()` |
| 前端构建 | `npm run build` → 3701 modules，`dist/static/index-BZouiz1c.js` 2,391.75 kB（gzip 701.34 kB）；产物含中英双套文案（Asset name / Vulnerability / Task / Tool / Audit / Notifications / Severity / Owner / Actual / estimated 等英文串 + 中文串均命中） |
| i18n 覆盖 | 词条 **553 / 553**（中英对等），**31/33** 个 tsx 使用 `useT`；`grep -rn "TODO(i18n)" frontend/src` 仅剩 **1** 处：`pages/notifications/SettingsTab.tsx` |
| 部署 | 后端 package BUILD SUCCESS 14.4s，jar **72,890,833 B**；重启后 login=200、api-docs=200；前端 sync-dist 后站点仅 5 个文件（`index-BZouiz1c.js`）；**无新增迁移** |
| 冒烟 | **TOTAL=167 / PASS=167 / FAIL=0 / SKIP=0**（新增 13 条：i18n 404 英/中、403 英/中、越权 403 英文、知识库标签 5、文档标签 4、标签查询与父不存在 404 共 2） |

| # | 页面验证（English 6 页 + 标签只读） | 结果 |
|---|---|---|
| 1 | 资产列表 / 资产详情英文 | **PASS**（Asset name / Target type / Owner / Priority；详情 Basic / Environment / Vulnerabilit / Metadata） |
| 2 | 漏洞列表英文 | **PASS**（Vulnerabilit / Severity / Workflow） |
| 3 | 任务页英文 | **PASS**（Task / Assignee / Status / Priority / Review） |
| 4 | 工具页英文 | **PASS**（Tool / Categor / Download / Version / Tag） |
| 5 | 审计日志英文 | **PASS**（Time / Action / Resource type / Resource ID / User / Details） |
| 6 | 切回中文恢复 | **PASS**（资产名称 / 目标类型 / 负责人 / 优先级） |
| 7 | 标签只读说明按角色 | **PASS**：管理员标签面板有输入框（可写）；工程师看他人文章 → 无输入框且显示「只读视图：仅作者或管理员可修改标签」 |

> 后端消息 i18n 实测（线上 curl）：`Accept-Language: en` → `You do not have permission to access this resource` / 默认 → `无权限访问该资源`；管理员取不存在资产 → `Accept-Language: en` 得英文 404、默认得「不存在」。


### 7.6 客户/项目/挂靠发布与回归（t75）

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test`（独占 target）→ **Tests run: 621, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS（35:26）；`@Disabled/@Ignore` 0 命中 |
| 用例增量 | 557 + t67–t78 新增（CustomerProjectApi 14、AssetProjectLink 15、Dashboard 16、CustomerSummary 8、ImportAssetProject 10 等）= **621** |
| 迁移验证（空库） | 新建 `vuln_audit_tmp` → jar 以 `ddl-auto=update` 建出基线 56 表 → 执行 V18 **两次均成功（幂等）**；四表（customers/projects/project_phases/asset_project_links）+ 四个唯一索引齐全；种子 `CUST-DEFAULT`/`PRJ-HISTORY` 各 1；空库孤儿资产 0；验后删库 |
| 迁移验证（生产） | 执行前 mysqldump 备份（约 105 KB）→ V18 执行成功 → customers=1、projects=1、project_phases=0（尚无分期项目）、asset_project_links=3；**孤儿资产 = 0**（硬断言） |
| 手工 DDL | `deploy/manual-ddl.sql` 追加第 4 段（V18 等价脚本 + 回填 + 4 条校验查询），整体在线上库执行通过（幂等，orphan_assets=0） |
| 部署 | 后端 package BUILD SUCCESS 15.7s，jar **73,020,174 B**；重启后 login=200、`/customers`=200、`/projects`=200、`/dashboard/customer-distribution`=200；前端 `index-DUbPqUnS.js` 2,481.31 kB，sync-dist 后站点仅 5 文件；`/customers`、`/projects` 路由 200 |
| 前端构建 | 3712 modules；词条 **648 / 648**（中英对等）；`/customers`、`/projects` 页面文案与 `Customers/Projects/Customer code/Manager/Phases/Linked projects/By customer/By project` 均在产物中 |
| 冒烟 | **TOTAL=195 / PASS=195 / FAIL=0 / SKIP=0**（新增 28 条，见下）；**冒烟后孤儿资产复查 = 0** |

**冒烟新增（客户/项目/挂靠全链路）**：建客户 ×2 → 建项目（分期/不分期）→ 建两期 → 建资产（带 projectId+phaseId）→ 资产详情 projectLinks 正确；缺 projectId **400**；projectId 不存在 **404**；期次不属于项目 **400**；跨客户挂靠 2xx；重复挂靠 **409**；解绑一条 2xx；解绑最后一条 **409**；删除有项目的客户 **409**；删除有挂靠的项目 **409**；工程师建客户/项目 **403**；团队长改自己项目 2xx / 他人项目 **403**；客户 summary 的 assetCount ≥1；`projects/{id}/assets` 对工程师按可见范围返回；仪表盘两张分布接口 200。

| # | 页面验证 | 结果 |
|---|---|---|
| 1 | 客户管理（列表/详情/新建入口） | **PASS**：列表含新建客户，详情抽屉含「项目」与「资产」聚合 |
| 2 | 项目管理（期次/状态机/资产清单） | **PASS**：详情含期次「一期-」、状态流转按钮、资产清单区 |
| 3 | 资产详情「所属项目」页签 | **PASS**：页签存在，点开后可见项目/客户/期次挂靠行 |
| 4 | 资产列表客户/项目列与筛选 | **PASS**：列表含客户/项目列，新建资产可见 |
| 5 | 仪表盘「按客户分布」「按项目分布」 | **PASS**：两张卡片均渲染 |
| 6 | English 切换（新增页面） | **PASS**：`/customers`、`/projects` 文案英文；切回中文恢复 |


### 7.7 第五批发布与回归（t87：N+1 消除 + i18n 收尾 + 客户项目导入）

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test`（独占 target）→ **Tests run: 648, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS；`@Disabled/@Ignore` 0 命中；630 → 648（t82 `ProjectSummaryTest` 8 + t85 `ImportCustomerProjectTest` 10） |
| 前端构建 | `npm run build` exit 0，`index-C7DvIDyd.js` 2,522.43 kB（gzip 731.23 kB）；词条 **961 / 961** 中英对等；`TODO(i18n)` **0 处**；产物含 `Customer import` / `Project import` / `Customer list` / `Project list` / `Most assets first` / `Notification settings saved` / `Tools & components` |
| 部署 | 后端 package BUILD SUCCESS 14.0s，jar **73,030,973 B**；重启后 login=200、`/projects/summary`=200、`/import/templates/customers`=200（首字节 `efbbbf`）；前端 sync-dist 后站点 5 文件；**无新增迁移**（仍 V1…V18） |
| 冒烟 | **TOTAL=208 / PASS=208 / FAIL=0 / SKIP=0**（新增 10 条） |
| 数据清理 | 按 README §23.1：assets 9→3、customers 7→1、projects 9→1、phases 4→0、links 9→3；**orphan_assets=0**；终态 `customers=1(CUST-DEFAULT) / projects=1(PRJ-HISTORY) / assets=3 / links=3` |

**冒烟新增 10 条**
- `proj/summary-聚合` / `proj/summary-assetCount`：`GET /projects/summary` 200 且每项含 `phaseCount`、`assetCount`；
- `proj/summary-工程师phaseCount不收敛`：双账号合计对比 —— phaseCount admin=4 / engineer=4（组织结构全量），assetCount admin=9 / engineer=2（按可见范围收敛）；
- `import/客户-部分成功`（1 成功 + 重复 code + ownerName 不存在）、`import/客户-失败原因可读`、`import/客户-工程师403`；
- `import/项目-部分成功`（1 成功 + customerCode 不存在 + type 非法 + 日期格式错各 1）；
- `import/模板-customers-BOM` / `import/模板-projects-BOM`（首字节 EF BB BF）。

| # | 页面验证 | 结果 |
|---|---|---|
| 1 | 项目列表 Network | **PASS**：`/projects/summary` 请求 **1 个**，`/projects/{id}` **0 个**，旧 `/projects` 列表 **0 个**（N+1 已消除） |
| 2 | 导入页 Tab 按角色 | **PASS**：管理员 5 个 Tab（Asset/Vulnerability/User/Customer/Project import）；工程师仅 1 个（漏洞导入） |
| 3 | 通知设置页 English | **PASS**：表头/类型说明/banner 英文 |
| 4 | 操作成功提示 English | **PASS**：下载模板 → `Template downloaded: customers_template.csv`；切回中文恢复 |
| 5 | 项目列表 English | **PASS**：Phases / Assets / Manager / Customer |

> i18n 回归：`Accept-Language: en` → `Asset not found`；默认 → `资产不存在`。


### 7.8 第六批发布与回归（t92：菜单/品牌修复 + 管理员模拟视角）

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test`（独占 target）→ **Tests run: 659, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS；648 → 659（t90 `ImpersonationTest` 11 例） |
| 前端构建 | `npm run build` exit 0，`index-GiOC38Fc.js` 2,533.86 kB（gzip 735.20 kB）；词条 **980/980**；产物含 `impersonation.*`（19 条）与 `app.brand.*`（漏洞审计平台 / Vuln Audit）以及「正在以 / Viewing as / 退出模拟 / Exit impersonation / 以其他用户视角查看」 |
| 部署 | 后端 package BUILD SUCCESS 20.8s，jar **73,052,099 B**；重启后 login=200、`/projects`=200、`impersonate`=200（token 311 字符）、链式 **403**、stop **204**；前端 sync-dist 后站点 5 文件；**无新增迁移** |
| 冒烟 | **TOTAL=222 / PASS=222 / FAIL=0 / SKIP=0**（新增 14 条模拟视角用例） |
| 数据清理 | 按 README §23.1：assets 15→3、customers 13→1、projects 17→1、phases 8→0、links 15→3、documents 0；**orphan_assets=0** |

**冒烟新增（模拟视角，14 条）**：发起 200 + 返回 target/actor；工程师发起 403；模拟管理员 409；模拟态「me 权限」200 / 用户列表按目标权限 403 / 资产列表 200；链式模拟 403；模拟态改用户 403；模拟态业务写（`POST /documents`）2xx；业务写审计含 `impersonatedBy`；退出 204；发起与停止审计事件可检索。

| # | 页面验证（真实浏览器） | 结果 |
|---|---|---|
| 1 | 菜单选中态 18/18 | **PASS**（含 安全态势大盘 / 客户管理 / 项目管理 / 账号管理 / 系统消息 等） |
| 2 | 详情路由高亮 3/3 | **PASS**（`/customers/{id}`、`/projects/{id}`、`/assets/{id}` 对应菜单保持高亮） |
| 3 | 品牌区 | **PASS**（展开「漏洞审计平台」+ Logo；折叠隐藏名称仅留图标；English 显示 Vuln Audit） |
| 4 | 模拟视角 | **PASS**（入口可见 → 选择器可开 → 发起 200 → 横幅含用户名与倒计时 → 账号管理菜单隐藏、客户管理写入口（新建客户）隐藏 → 退出后横幅消失、admin 视图恢复、localStorage 三键清除） |
| 5 | 工程师登录 | **PASS**（无「以其他用户视角查看」入口） |


### 7.9 第七批发布与回归（t94：模拟视角审计实测与事务修复）

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test`（独占 target）→ **Tests run: 680, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS（2026-11-04 复核）；659 → 680（t93 9 例 + t94 守卫 1 例 + t96 偏好 11 例） |
| 后端重建 | package BUILD SUCCESS，jar **73,060,869 B**（md5 0ccf01e4…）；重启后 login=200、`/projects`=200、`/projects/summary`=200、`/users/me/preferences`=200，服务 active |
| 线上审计实测 | `IMPERSONATE_START/STOP` 落库（START=11 / STOP=10）；details 四要素齐全（actor/target/ttlSeconds/reason）；一次模拟 START **+1**；今日拦截器重复行增量 **0**（全库 19 条均为 t90/t92 历史行）；`details` 列 TEXT、最长 165 字符未被截断 |
| 缺陷修复 | `ImpersonationServiceImpl.impersonate()/stop()` 由 `@Transactional(readOnly=true)` 改为可写事务（原标记导致审计插入被静默丢弃，t93 的 H2 用例因测试事务读写而漏检）+ 新增反射守卫用例 |
| 冒烟 | **TOTAL=228 / PASS=228 / FAIL=0 / SKIP=0**（新增 6 条；另修正 2 条旧用例：访问申请正例改由管理员发起、拦截器重复行改用前后差值判定；另修掉脚本 `$BASE` 未定义变量的噪音） |
| 数据清理 | 按 README §23.1：assets 18→3、customers 16→1、projects 21→1、phases 10→0、links 18→3、documents 1→0；**orphan=0**，终态 `customers=1 / projects=1 / assets=3 / links=3` |
| 前端 | 无改动，无需 sync-dist |

| # | 校验点 | 结果 |
|---|---|---|
| 1 | 发起审计四要素 | **PASS**（details 含 actor= / target= / ttlSeconds= / reason=） |
| 2 | 一次模拟仅 1 条 START | **PASS**（before=8 → after=9，差值恒为 1） |
| 3 | 无拦截器重复行 | **PASS**（拦截器行数 before=19 after=19，历史行保留） |
| 4 | 停止审计 | **PASS**（before=7 → after=8，details 含 actor/target） |
| 5 | 访问申请：资产不存在 404 / 跨团队 403 | **PASS**（t93 前为 400） |

---

## 8. 仍建议后续跟进的事项（不阻塞验收）

| # | 事项 | 理由/建议 |
|---|---|---|
| 1 | 收紧 §2.3.2 剩余的 13 组鉴权缺口 | 「高」3 组已于 t40 收口、认领 403 已于 t39 修复；其余 10 中 / 3 低建议按模块补 `@PreAuthorize` 与数据级守卫，优先 **#13 用户列表 PII**、**#1/#2/#3 资产与版本写权**、**#12 工具改删** |
| 2 | ~~配置 `AuthenticationEntryPoint`，匿名请求返回 401~~ **已完成（t45）** | 已配置 `AuthenticationEntryPoint`（401）与 `AccessDeniedHandler`（403）；冒烟脚本中「接受 401/403」的宽松断言可收紧为 401 |
| 3 | 引入 `spring-boot-starter-actuator` 并提供 `/actuator/health` | 当前只能用 `/v3/api-docs` + 登录做健康检查，运维体验不足 |
| 4 | ~~国际化（4.6）~~ **已完成（t53–t88）** | 自建 i18n 框架 + **961/961 中英词条**；`TODO(i18n)` **0 处**；前端 `message.*` 文案中文 0 处；后端错误消息随 `Accept-Language` 切换；见 §3.6 |
| 5 | 对象存储抽象 | 文件/附件/工具包当前落本地磁盘，多实例部署需共享存储或对象存储（S3/OSS） |
| 6 | 真实容器编排启用 | 需宿主安装 Docker 并暴露 socket（或 K8s 编排），当前为 Noop 显式降级 |
| 7 | ~~把「批量导入」纳入权限矩阵~~ **已完成（t85/t87）** | 导入域已进冒烟与矩阵：资产/漏洞/用户/客户/项目五类导入的 403 与部分成功语义均有断言（`import/*` 共 9 条），见 §2.15 |
| 8 | 增加前端 E2E 常态化（Playwright/Cypress） | 目前为页面级人工/Headless 验证；建议纳入 CI 以便长期回归 |
| 9 | 统一「列表可见」与「详情可读」的判定 | §2.3.2 #13：t44 实测工程师列表中 3/5 个资产详情 403；建议列表范围复用 `canReadAsset` 同源集合 |
| 10 | 明确「团队负责人」与角色的解耦语义 | `canManageTeam` 只看 `teams.leader_id`；若产品要求「必须 team_leader 角色」，需在 `isLeaderOfTeam` 加角色判定并同步前端 |
| 11 | ~~客户/项目仍需逐个手工建档~~ **已完成（t85）** | 已补 `POST /import/customers`、`POST /import/projects` 与两个模板（BOM + 两行示例），逐行 failedRows + 逐行审计，见 §2.15 |
| 12 | ~~项目列表逐项目查询详情（N+1）~~ **已完成（t82/t83）** | 新增 `GET /projects/summary` 批量聚合（phaseCount/assetCount/openTaskCount + 客户名/经理名），前端一次请求拿全；查询数约 7 条与页大小无关，页面实测 `/projects/{id}` 请求 **0 个**，见 §2.14 |
| 13 | ~~`message.success` 等提示文案仍中文~~ **已完成（t84/t88）** | `message.success/info/warning/error` 前端自有文案中文 **0 处**；导入/工具/知识库/文档四页正文同步迁移，见 §3.6 |
| 14 | ~~未迁移页面正文仍有中文~~ **部分完成（t88 第二批）** | 已迁：导入/工具/知识库/文档/任务表格以外的主路径；仍余 tasks/users/teams/roles/approvals/asset-groups 等页面正文约 552 行，建议按「tasks → 组织域（users+roles+teams）→ 审批域」分批 |
| 15 | ~~模拟视角审计事件名与字段对齐~~ **已完成（t93/t94）** | 线上实测 `IMPERSONATE_START/STOP` 落库、details 含 actor/target/ttlSeconds/reason、一次模拟仅 1 条且无拦截器重复；期间修掉 `@Transactional(readOnly=true)` 导致插入被静默丢弃的缺陷（见 §2.16） |
| 16 | ~~统一“不可见资源”的状态码~~ **已完成（t93）** | 访问申请对跨团队资产改为 **403**，资产不存在仍 **404**（`AccessGuard.canRequestAssetAccess`） |
| 17 | 模拟视角上线运维要求 | TTL 内 token 等效目标用户：建议生产开启时限制内网、日检审计、把 `reason` 改为必填 |

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

### 2.9 资产—项目挂靠 API + 「建资产必须挂项目」硬约束（t69）

在 t67（模型+迁移）与 t68（客户/项目 API）之上补齐**资产与项目的关联能力**，并把「添加资产必须挂靠项目」落成硬约束。

| 能力 | 端点 | 权限 |
|---|---|---|
| 建资产（含挂靠，同一事务） | `POST /assets`（**projectId 必填**） | ADMIN / PROJECT_LEADER / TEAM_LEADER + 团队归属 |
| 挂靠查询 / 追加 / 解绑 | `GET`/`POST` `/assets/{assetId}/project-links`、`DELETE .../{linkId}` | 读 `canReadAsset`；写 `canWriteAsset` |
| 反向查询项目资产 | `GET /projects/{projectId}/assets?phaseId&page&size` | 全员可读（与项目读口径一致） |
| 资产列表归属筛选 | `GET /assets?customerId&projectId&phaseId` | 全员（结果再与可见范围取交集） |
| 资产详情挂靠明细 | `GET /assets/{id}` → `projectLinks[]` | `canReadAsset` |

**状态码与不变式**：

| 场景 | 状态码 |
|---|---|
| 缺 `projectId` | 400「创建资产必须指定所属项目」 |
| 项目 / 客户 / 期次不存在 | 404 |
| 期次不属于该项目、项目未启用分期 | 400 |
| 重复挂靠同一 (资产, 项目, 期次) | 409「该资产已挂靠此项目（期次）」 |
| 解绑最后一条挂靠 | 409「资产必须至少挂靠一个项目，请先挂靠到其他项目再解绑」 |
| 挂靠不存在 / 不属于该资产 | 404（防枚举） |

**跨客户挂靠允许**：同一资产可同时服务多个客户的项目，挂靠行的 `customer_id` 取自该项目所属客户；
这正是 t67 不给 `assets` 加 `project_id` 单值外键的原因（多对多）。

**性能**：`GET /assets` 的 `customers` / `projects` 摘要是**固定 3 次批量查询**（挂靠 → 项目 → 客户）后在内存聚合，
与页大小无关，不做 per-asset 查询；归属筛选与 `visibleAssetIds` 在同一 `Specification` 内取交集，筛选无命中直接空页。

**既有测试适配（改夹具，不放宽约束）**：`POST /assets` 新增必填字段影响 3 个 suite 的前置数据 ——
`AssetControllerTest`（补 Customer/Project 夹具 + `createAsset_Success` 设 projectId）、
`AssetDomainAuthorizationTest`（`assetBody()` 统一追加 projectId，5 处调用点用例数不变）、
`ApiMessageI18nTest`（补 `$.data.projectId` 中英文案断言）。

证据：`AssetProjectLinkTest` 15 例全绿（建资产带项目 201+挂靠落库、不带 400、项目 404、期次错配/未分期 400、
重复 409、跨客户成功、解绑最后一条 409 / 非最后一条 200、他人资产 404、列表三种筛选、列表摘要、
详情 projectLinks、挂靠列表、项目资产按期次过滤与分组、工程师 403/201）；接口与不变式见 README §35。
### 2.10 客户/项目统计 + 仪表盘分布 + 挂靠越权复核（t70）

**新增接口**：`GET /customers/{id}/stats`、`GET /projects/{id}/stats`、
`GET /dashboard/customer-distribution`（Top N 默认 10/上限 100）、
`GET /dashboard/project-distribution`（支持 `customerId` 过滤）。

**计数口径差异**（同一接口里两类数字语义不同，前端展示需注意）：

| 类别 | 是否随可见性收敛 | 依据 |
|---|---|---|
| `projectCount` / `activeProjectCount` / `phasedProjectCount` / `byStatus` / `phaseCount` | **否** | 项目与期次是组织结构事实，对所有登录用户可见（t46 决策：项目不作为权限边界） |
| `assetCount` / `distinctAssetCount` / `vulnerabilityCount` / `taskCount` / `byPhase` / distribution 的 `assetCount` | **是** | 按 `AccessGuard.visibleAssetIds(auth)` 过滤；管理层 `null` = 全量 |

**越权复核两条结论**：

| # | 检查项 | 结论 |
|---|---|---|
| ① | `POST /assets/{assetId}/project-links` 能否给他人资产挂靠 | **已受控**（t69 已挂 `canWriteAsset`）：工程师 → 403 且挂靠数不变（本轮补断言） |
| ② | `GET /projects/{projectId}/assets` 是否泄露不可见资产 | **本次修复**：原实现按项目维度返回全部挂靠资产，现传入 `visibleAssetIds` 过滤（项目可见 ≠ 资产可见）；工程师在含 3 个资产的项目下只看到自己那 1 个 |

复核 ③（删除一致性）：客户有项目 → 409、项目有挂靠 → 409，故不存在「客户/项目已删而 link 残留」；
用例断言「解绑后删项目 200 且挂靠记录为空」。

证据：`CustomerProjectStatsTest` 10 例（统计与库内一致 / 工程师计数收敛 / 两条越权复核 / Top N 与
customerId 过滤 / 删除一致性 / 不存在资源 404）。接口与口径见 README §37。

### 2.11 客户详情聚合接口（t76：消除前端 N+1）

**问题**：`GET /customers/{id}` 只给三个计数，前端要渲染客户详情必须
`GET /projects?customerId=` → 再对每个项目 `GET /projects/{id}` 取 `phases`/`assetCount`（上限 20 个并行请求）。

**新增**：`GET /api/v1/customers/{id}/summary`，一次返回
`{customer, stats{projectCount, activeProjectCount, phasedProjectCount, assetCount, vulnerabilityCount},
projects[{id,code,name,type,status,isPhased,phaseCount,assetCount,managerName}],
recentAssets[{id,code,name,targetType,status,projectNames[]}]}`（最近 10 条）。

| 实现要点 | 说明 |
|---|---|
| 聚合查询 | `projects[].phaseCount` 与 `projects[].assetCount` 各用**一次 group by**（`countGroupedByProjectIds`、`countAssetsGroupedByProjectIds`），禁止逐项目查询 |
| recentAssets | 客户挂靠按 `linked_at desc` 一次取出 → 去重取前 10 → `projectNames` 从同一结果集过滤（不回表） |
| 查询次数 | 固定约 8 次，与项目数/资产数无关（旧流程 `1 + 1 + 项目数`，20 个项目 = 22 次） |

**数据边界（与 t70 §2.10 口径一致）**：`stats.assetCount` / `stats.vulnerabilityCount` / `recentAssets`
按 `AccessGuard.visibleAssetIds(auth)` 收敛（资产是权限边界）；`projects` 列表与
`stats.projectCount/activeProjectCount/phasedProjectCount`、项目级 `phaseCount/assetCount` 为**全量组织口径**，
不随权限收敛 —— 「资产列表会因权限变短，但组织结构不会」。

证据：`CustomerSummaryTest` 8 例（完整结构 / 与库内一致 / 工程师 recentAssets 只含可见资产且
`stats.assetCount` 小于 admin 而 `projects` 列表一致 / 空客户 / 404 / recentAssets 上限 10 与 projectNames /
团队长可读）。回归 53 例（AssetProjectLink 15 + CustomerProjectApi 14 + Dashboard 16 + 本测试 8）全绿。

### 2.12 CSV 导入资产必须挂项目（t78）

**缺口**：t69 的「建资产必须挂项目」只落在 DTO/控制器；批量导入走 `ImportServiceImpl → AssetService.createAsset`，
绕过该约束 → 导入资产**无任何挂靠**，破坏「资产至少挂靠一个项目」不变式（也让「无挂靠资产 = 0」的验收 SQL 失败）。

**修复**：

| 项 | 内容 |
|---|---|
| 模板 | `name,targetType,targetSource,priority,status,teamCode,customerCode,projectCode,phaseNo,description`（新列可选，模板仍带 BOM） |
| 有 `projectCode` | 按 code 查项目；`customerCode` 必须与项目客户一致；`phaseNo` 要求项目已分期且期次存在 |
| 无 `projectCode` | 挂 `CUST-DEFAULT` / `PRJ-HISTORY`（`linkType=SHARED`），**成功行**但在 `ImportResult.notes` 提示「未指定项目，已挂靠历史项目」——不静默 |
| 兜底缺失 | 默认客户 / 历史项目缺失时**按需补齐**（幂等），任何环境都不产生无挂靠资产 |
| 事务 | 复用 t69 `AssetProjectLinkService.createAssetWithLink`（同一事务建资产 + 挂靠），失败行零残留 |
| 兼容 | `ImportResult` 新增 `notes`，3 参 `of(...)` 保留 → 前端旧代码不受影响 |

证据：`ImportAssetProjectTest` 10 例（带项目成功 / 项目不存在 / 客户不匹配 / 未带项目走历史项目 + notes /
期次不存在 / 未分期 / 混合批次「无挂靠资产 = 0」/ 只给期次 / 模板新列 / 历史项目按需补齐幂等 / 接口回查）；
导入相关四个 suite 36 例全绿（ImportAssetProject 10 + ImportController 5 + ImportAuthorization 8 + ImportServiceImpl 13）。
接口与规则见 README §38。
### 2.13 新域 NOT NULL 兜底 + 四实体扫描（t80）

**缺陷**：`POST /api/v1/projects` 缺 `managerId` 时返回 **500**（`projects.manager_id` NOT NULL 撞库），
而非 400 —— 与 t22（passwordHash）、t34（漏洞必填）、t40（公告 publishedAt）同一「依赖数据库约束」模式。

**修复**：`ProjectRequest.managerId` 补 `@NotNull`（400 + `data.managerId`），服务层保留
客户/经理存在性校验（404）。`PUT /projects/{id}` 同一 DTO，同步生效。

**四实体 NOT NULL 扫描表**（无「缺校验 → 依赖 DB 约束」条目）：

| 实体 | NOT NULL 列 | 校验 / 兜底 |
|---|---|---|
| customers | code / name | DTO `@NotBlank` → 400（code 重复 409） |
| | status | 服务层默认 `active`，非法值 400 |
| | created_at / updated_at | `@PrePersist` / `@PreUpdate` |
| projects | code / name | DTO `@NotBlank` → 400 |
| | customer_id | DTO `@NotNull` → 400；不存在 404 |
| | manager_id | **DTO `@NotNull`（本次新增）→ 400**；不存在 404 |
| | type / status / is_phased | 服务层默认 `PENTEST` / `DRAFT` / false |
| | created_at / updated_at | `@PrePersist` / `@PreUpdate` |
| project_phases | project_id | 路径 → 不存在 404 |
| | phase_no | 服务层 `max+1` 自增；重复 409 |
| | name | DTO `@NotBlank` → 400 |
| | status | 服务层默认 `PLANNED` |
| asset_project_links | asset_id / project_id | 路径守卫（403/404）/ DTO `@NotNull` → 400 |
| | phase_key | 服务层哨兵 `PHASE_KEY_NONE`（永不 NULL） |
| | customer_id | 服务层从项目反查 |
| | link_type | 服务层默认 `TARGET` |
| | linked_at | `@PrePersist` |

同类端点：`POST /projects/{id}/phases`、`POST /assets/{id}/project-links` 已按同标准核对通过。

**已知模式（列入团队规则）**：新增 NOT NULL 列必须同时给出 DTO 注解（400）或服务层默认值；
漏掉的症状是 **500 且 `data` 无字段名**，前端无法定位。

证据：`NewDomainValidationTest` 9 例（缺 4 个必填字段逐个 400 带字段名 / 客户与经理不存在 404 /
更新路径同样兜底 / 期次 name 缺失 400 且 phaseNo 留空自增 / 挂靠缺 projectId 400 且 linkType 默认 TARGET /
phaseKey 非空 / 统一「绝无 5xx」断言）。接口与扫描表见 README §39。

**线上证据（t81 部署后实测，2026-11-02）**：
- `POST /api/v1/projects` 不带 `managerId` → **400** `{"success":false,"message":"参数校验失败","data":{"managerId":"项目经理不能为空"}}`（修复前为 500）；
- `POST /api/v1/projects` 带不存在 `managerId` → **404** `项目经理不存在: …ff81a`（存在性校验保留）；
- 冒烟新增 3 条长期守卫（`proj/new-缺managerId-400`、`proj/update-缺managerId-400`、`proj/new-managerId不存在-404`），全量 **198/198 全绿**。

### 2.14 项目列表批量聚合（t82：t75 遗留 #2 已解决）

**遗留项**（t75 §六 #2）：项目列表的「期次数 / 资产数」由前端逐项目并发拉取（每页最多 20 → 20 个
`GET /projects/{id}`），与 t76 已解决的客户详情属同类 N+1。

**新增**：`GET /api/v1/projects/summary`，每项附带 `phaseCount` / `assetCount` / `openTaskCount` /
`managerName` / `customerName`。

| 维度 | 实现 |
|---|---|
| 期次数 | 一次 group by（复用 t76 `countGroupedByProjectIds`） |
| 资产数 | 一次 group by（新增 `countVisibleAssetsGroupedByProjectIds`，带可见范围条件） |
| 未完成任务数 | 一次 group by（新增 `countOpenTasksGroupedByProjectIds`：任务资产 ∈ 项目挂靠资产，排除 completed/cancelled） |
| 客户名 / 经理名 | 各一次 `findAllById` 批量取名 |
| 总查询数 | **约 7 条，与页大小无关**（旧写法 `1 + 页内项目数`） |

**口径（与 t70/t76 一致，勿混用）**：`phaseCount` **不收敛**（组织结构）；`assetCount` 与
`openTaskCount` **按 `visibleAssetIds` 收敛**（资产是权限边界）；`managerName/customerName` 为组织维度。

`sort` 支持 `createdAt`（默认，tiebreak = id asc，保证分页稳定）与 `assetCount`
（倒序；聚合字段无法 SQL 排序 → 全量算计数后内存排序切页）。

证据：`ProjectSummaryTest` 8 例（结构与三计数 / 计数与库内逐项一致 / 工程师 assetCount 2→1 而 phaseCount 不变 /
customerId 过滤 / 空结果 / 分页两页不重复且 size 上限 500 / assetCount 排序 /
**Hibernate Statistics 实采：查询次数不随页大小变化**）；回归 56 例（CustomerProjectApi 14 + CustomerSummary 8 +
CustomerProjectStats 10 + Dashboard 16 + 本测试 8）全绿。接口与口径见 README §41。

### 2.15 客户 / 项目 CSV 批量导入（t85）

**背景**：导入框架已支持资产/漏洞/用户，客户与项目仍需逐个手工建档（报告 §8 待办项）。

**新增端点**（权限与对应写接口一致）：

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/api/v1/import/customers` | `ADMIN` / `PROJECT_LEADER`（工程师、团队长 → 403） |
| POST | `/api/v1/import/projects` | 同上 |
| GET | `/api/v1/import/templates/{customers\|projects}` | 登录即可，带 BOM + 两行示例 |

**逐行规则**：

| 域 | 校验（不满足 → 该行 failedRows） |
|---|---|
| 客户 | `code`/`name` 必填；`code` 唯一（「客户编码已存在: X」）；`level` ∈ A/B/C；`ownerName` 按 **username** 解析，找不到 →「客户经理不存在: X」（**不猜测、不给默认负责人**） |
| 项目 | `code` 唯一；`customerCode` 必须存在；`managerName` 按 username 解析；`type` ∈ PENTEST/REAUDIT/CONSULTING/TRAINING（留空默认 PENTEST）；`startDate`/`endDate` 为 `YYYY-MM-DD`，且 `endDate` 不早于 `startDate`；`isPhased` 接受 `true/false/1/0` |

**审计**：成功行逐条写入 —— `action=import_customer` / `resourceType=customer`，
`action=import_project` / `resourceType=project`，`resourceId` 为新建记录 ID；另有一次整体导入审计（resourceType=import）。

**部分成功**：沿用 `ImportResult`（totalRows/successRows/failedRows/notes），逐行独立，
失败行在服务层调用前即被拦下（零残留）；行号含表头（第 2 条数据行 = 第 3 行）。

证据：`ImportCustomerProjectTest` 10 例（客户 2 行成功+列表可查+逐行审计 / 重复 code / ownerName 不存在 /
level 非法 / 项目 2 行成功含 isPhased 变体+审计 / customerCode 不存在 / managerName·type·日期三种失败 /
部分成功与行号 / 两模板 BOM+两行示例 / 工程师与团队长 403）；导入相关 7 个 suite **67 例全绿**。
规则与模板示例见 README §42。
### 2.16 管理员模拟用户视角（t90）

**目标**：管理员以指定用户的身份与权限查看系统，用于审阅角色分配是否得当、各权限用户的 UI 与可见资源是否正确；
同时保证「不能借此提权、不能借此改权限、全程可追溯」。

**接口**：`POST /admin/impersonate`（仅 ADMIN）、`POST /admin/impersonate/stop`（普通态 ADMIN / 模拟态要求 `act` 为 ADMIN）。

**受限 token**：`sub=目标用户`、`roles=目标用户真实角色`、`act=ADMIN 用户ID`、`imp=true`、`exp=2 小时`
（`app.security.impersonation.ttl-seconds`，可用 `APP_IMPERSONATION_TTL_SECONDS` 覆盖）。

**模拟态禁止的端点清单**（`ImpersonationGuardFilter`，只拦写方法 POST/PUT/PATCH/DELETE）：

| 前缀 | 影响端点 |
|---|---|
| `/api/v1/users/**` | 建用户、改用户、删用户、重置密码、改用户角色、改用户权限点 |
| `/api/v1/roles/**` | 建角色、改角色、删角色、角色权限点增删 |
| `/api/v1/admin/**` | 全部管理员端点，含 `POST /admin/impersonate` → **防链式模拟** |
| 显式放行 | `POST /api/v1/admin/impersonate/stop` |

其余业务权限**完全保留**（目标是团队长即可做团队长的一切），读接口不封禁（审阅需要看用户/角色列表；
能否看到仍取决于目标用户自身权限）。

**审计归属规则**：

| 事件 | `user_id` | `action` | `details` |
|---|---|---|---|
| 发起 | 真实操作者（ADMIN） | `IMPERSONATE_START` | `模拟开始: actor=..., target=..., ttlSeconds=..., reason=...` |
| 停止 | 真实操作者 | `IMPERSONATE_STOP` | `模拟结束: actor=..., target=...` |
| 模拟期间业务写 | **目标用户**（真实执行者） | 原动作 | URI + `; impersonatedBy=<adminId>` |

**安全约束逐条**：非 ADMIN 发起 403 / 目标不存在 404 / 目标停用 400 / 模拟 ADMIN 409 / 链式模拟 403 /
身份端点写 403 / 过期 401 / start·stop 审计 + 业务审计归属 / 普通态非 ADMIN 调 stop 403。

**关闭方式**：`app.security.impersonation.enabled=false`（或 `APP_IMPERSONATION_ENABLED=false`）→ 两个端点均 403。

证据：`ImpersonationTest` 11 例（签发与 claims、START/STOP 审计、业务审计 `impersonatedBy` + 普通态对照组、
非 admin 发起、模拟 admin 409、目标 404/停用 400、链式 403、身份端点写 403 ×5 + 读放行、过期 401）。

**t93 修订（审计补全 + 状态码统一）**

| 问题（t92 实测） | 根因 | 修复 |
|---|---|---|
| 发起/停止记为 `POST AdminImpersonationController.impersonate`，details 只有 URI | `AuditTrailInterceptor` 按「方法名」生成 action（且截断到 50），同时服务层已写专用审计 → **两条** | `AdminImpersonationController` 加入 `SKIP_CONTROLLERS`，只保留服务层 `IMPERSONATE_START/STOP`（一次模拟 1 条） |
| 无法回答「谁以什么理由模拟了谁」 | 拦截器行不含语义字段 | 服务层 details 固定四要素：`actor=<username(id)>`、`target=<username(id)>`、`ttlSeconds=`、`reason=`（未填记 `(未填写)`）；STOP 无 `reason=` 键 |
| details 是否被截断 | 截断只作用于 `action`（varchar 50），详情另限 500 | `audit_logs.details` 为 **TEXT**，服务层详情完整落库（无需迁移） |
| `POST /assets/{id}/access-requests` 对不可见资产返回 400「无权访问该资产」 | `ASSET_ACCESS_DENIED` 的 code=1203（非 HTTP 状态）被 handler 兜成 400；且端点此前**没有任何可见性守卫** | ① `ASSET_ACCESS_DENIED` 改 **403**；② 新增 `AccessGuard.canRequestAssetAccess`（管理层/负责人/资产所属团队成员/已获批 → 放行，其余 **403**），既统一语义又保留「同团队可申请」的功能路径；③ 资产不存在仍 **404**（存在性用 404、权限用 403） |

证据：`ImpersonationAuditTest` 9 例（START 四要素齐全 / 未填 reason 显式标注 / 一次模拟仅 1 条且无 `AdminImpersonationController` 行 /
STOP 含 actor+target 且无 reason / 模拟期业务写仍含 `impersonatedBy` / 跨团队资产申请 403 / 可见资产 201 / 重复申请 409 / 不存在资产 404）；
回归 `ImpersonationTest`(11) + `AssetControllerTest` + `ApprovalFlowTest` + `AssetDomainAuthorizationTest` 共 55 例全绿（`AssetControllerTest` 夹具补「工程师加入团队」，未放宽任何守卫）。
接口、清单与风险说明见 README §44。


**t92 实测备注 → t93/t94 已修复（当前线上口径）**

t92 发现：发起/停止只留下拦截器生成的 `POST AdminImpersonationController.impersonate`（details 仅 URI，action 被截断 40 字符），设计文档里的 `IMPERSONATE_START/STOP` 未落库。t93 修复根因（控制器加入 `SKIP_CONTROLLERS`，由服务层写专用事件），t94 线上复核如下：

| 项 | 线上实测（2026-11-04 14:5x–15:0x 复核，jar 73,060,869 B，md5 0ccf01e4…） |
|---|---|
| `action` | **`IMPERSONATE_START` / `IMPERSONATE_STOP`**（专用事件，不再是 Controller 行） |
| 发起 `details` | `模拟开始: actor=admin(<id>), target=smoke_engineer(<id>), ttlSeconds=7200, reason=…` —— **四要素齐全** |
| 停止 `details` | `模拟结束: actor=…, target=…`（无 reason 键，符合设计） |
| 一次模拟的 START 条数 | **恰好 +1**（冒烟断言 before/after 差值为 1） |
| 拦截器重复行增量 | **0**（`keyword=AdminImpersonationController` 总数不变；复核日新增行数为 **0**，库内 19 条历史行来自 t90/t92 旧版本，保留可追溯） |
| 累积计数 | `action='IMPERSONATE_START'` **11 条** / `IMPERSONATE_STOP` **10 条**（第 11 条 START 为停止前中断所留，非重复写入） |
| `details` 是否被截断 | 列类型 `TEXT`，实测最长 **165 字符** 完整落库（截断只作用于 `action` varchar(50)） |
| 模拟期业务写 | `userId`=目标用户、`details` 含 `impersonatedBy=<adminId>`（回归通过） |

**t94 额外发现并已修复的缺陷（重要）**

t93 上线后线上**依然写不出** `IMPERSONATE_START`：日志有「记录审计日志」但库中无行。根因是 `ImpersonationServiceImpl.impersonate()/stop()` 标了 `@Transactional(readOnly = true)` —— Hibernate 只读会话以 `FlushMode.MANUAL` 丢弃插入，**既不报错也不落库**；而 `BaseTest` 的 `@Transactional` 会把该标记「合并」进读写事务，所以 `ImpersonationAuditTest` 9 例全绿却漏检。
t94 修复：两个方法改为可写事务 `@Transactional`，并新增守卫用例 `impersonationServiceTransactionsMustBeWritable`（反射断言 `readOnly=false`），防止再次退化。

**状态码统一（t93）**：`POST /assets/{id}/access-requests` 对跨团队不可见资产 **403**（原 400），资产不存在仍 **404**，同团队/负责人/管理层放行。

### 2.17 用户登录页主题偏好（t96）

**目标**：登录页提供 4 套主题（`pencil` / `geometric` / `owl` / `pixel-eyes`，t100 已扩到 **8 套**，见本节末尾），用户选择后**跨设备保留**。
本轮为后端部分（前端应用见 t97）。

**数据模型**：`users.login_theme VARCHAR(32) NOT NULL DEFAULT 'pencil'`，
迁移 `V19__add_user_login_theme.sql`（`ADD COLUMN IF NOT EXISTS`，幂等；H2 2.x 与 MariaDB 10.x 均可），
生产 Flyway 关闭 → `deploy/manual-ddl.sql` 等价段。

**接口**

| 方法 | 路径 | 权限 | 返回 |
|---|---|---|---|
| GET | `/api/v1/users/me/preferences` | 登录即可 | `{loginTheme}`（默认 `pencil`） |
| PUT | `/api/v1/users/me/preferences` | 登录即可 | 200 + 更新后的 `{loginTheme}` |
| POST | `/api/v1/auth/login` | 公开 | 响应新增 `loginTheme`（省一次请求） |
| GET | `/api/v1/auth/me`、`POST /auth/refresh` | 登录即可 | 响应含 `loginTheme` |

**安全边界**

1. **只能改自己**：路径固定 `/me`，用户ID 来自 `CurrentUserProvider`；`UpdateUserPreferencesRequest` 只有 `loginTheme` 字段，
   请求体/查询参数带 `userId` 会被忽略（单测用「工程师带 admin 的 userId」验证 admin 偏好未被改动）。
2. **白名单 + 归一化**：t96 首版 4 项（`pencil/geometric/owl/pixel-eyes`），**t100 扩到 8 项并收敛为 `LoginTheme` 枚举单一来源**
   （新增 `aurora/mono-grid/orbit/big-type`）；` OWL ` → `owl`；非法值 **400** 且**不落库**；
   读取时库内脏值回落 `pencil`（不把非法值回吐）。
3. **i18n**（t60 MessageSource）：中文 `不支持的登录页主题: neon（支持: …）`，英文 `Unsupported login page theme: neon (supported: …)`，
   支持列表由 `LoginTheme.supportedCodes()` 生成；另补 `validation.user.login_theme.required/too_long` 两个校验消息码，避免 400 时回显原始 key。
4. **审计单条**：`action=UPDATE_LOGIN_THEME`、`resource_type=user`、`resource_id=本人`、
   `details=登录页主题变更: from=pencil -> to=owl`；**同值不写**；
   `AuditTrailInterceptor` 用 `SKIP_ENDPOINTS` 精确跳过该端点，避免再生成一条 `PUT UserController.updateMyPreferences`
   （未把 `UserController` 整体加入 `SKIP_CONTROLLERS`，用户 CRUD 审计不受影响）。
5. **未认证 → 401**（GET 与 PUT 均是）。

证据：`UserPreferenceTest` **11 例**（默认 pencil / 合法值 200 且库内生效 / 大写归一 / 非法值 400 中文 / 非法值 400 英文 /
只能改自己 / login+me 响应含 `loginTheme` / 未认证 401 / 审计落库 / 同值不写审计 / 一次改动仅 1 条审计）。
规则、DDL 与 curl 复核见 README §47。


**线上复核（2026-11-04，jar 73,061,357 B）**

| 项 | 实测 |
|---|---|
| 登录响应 | `loginTheme=pencil`（历史 NULL 行已被兜底为 pencil，不再返回 null） |
| PUT 合法值 | `{"loginTheme":"owl"}` → 200，再次登录 `loginTheme=owl` |
| PUT 非法值 | `{"loginTheme":"neon"}` → 400 `不支持的登录页主题: neon`；`Accept-Language: en` → 400 `Unsupported login page theme: neon` |
| 审计去重 | `keyword=updateMyPreferences` 行数 2 → 2（+0）；`action=UPDATE_LOGIN_THEME` 2 → 3（+1），details=`登录页主题变更: from=pencil -> to=geometric`、resourceType=user、resourceId=本人 |
| 回归 | `mvn -B clean test` **680/680**（`UserPreferenceTest` 11 例） |
| 冒烟 | 重建重启后 **228/228**；清理后 assets=3 / customers=1 / projects=1 / links=3、`orphan=0` |


**t100：白名单扩到 8 项并收敛为单一来源**

t97/t99 前端已提供 8 套主题（角色组 4 + 抽象极简组 4），后端白名单必须同步，否则选新主题会 400。

| 项 | 结果 |
|---|---|
| 白名单 | **8 项**：`pencil` / `geometric` / `owl` / `pixel-eyes` / `aurora` / `mono-grid` / `orbit` / `big-type`（`mono-grid` 用连字符，`mono_grid` / `monoGrid` 均判非法） |
| 单一来源 | 新增枚举 `com.vulnaudit.entity.enums.LoginTheme`（`code` + `DEFAULT` + `MAX_CODE_LENGTH` + `isValid/fromCode/fromCodeOrDefault/codes/supportedCodes/normalize`）；服务层白名单、默认值、错误文案列表、DTO 长度上限、登录/刷新响应的兜底值全部派生自它 |
| 默认值 | 仍为 `pencil`（`LoginTheme.DEFAULT`），**无新增迁移**，既有用户数据不变 |
| 错误文案 | `error.user.login_theme.unsupported` 改为 `不支持的登录页主题: {0}（支持: {1}）` / `Unsupported login page theme: {0} (supported: {1})`，`{1}` 自动跟随枚举 |
| 字段名 | 保持 `loginTheme`（请求体 / `GET /me/preferences` / `/auth/me` / login / refresh 均不变），前端 t97 无需改动 |
| 单测 | `UserPreferenceTest` **14 例**：新增「8 主题逐个 PUT 200 + `/auth/me` 回显」「枚举-白名单-默认值一致性守护（含 `mono_grid` 反例）」「库内脏值回落 pencil」；非法值两例增断言「消息含完整支持列表」 |

证据：`mvn -B test -Dtest=UserPreferenceTest` → **14/14**；全量 `mvn -B clean test` → **683/683**，BUILD SUCCESS。
规则与 curl 复核见 README §47.2。

**t100 线上复核（2026-11-04，jar 73,064,121 B）**：8 个主题逐个 `PUT /users/me/preferences` 均 200，`GET /auth/me` 逐个回显；
`mono_grid`（下划线错写）与 `monoGrid` 均 400 且消息含 8 项支持列表（中英）；复核后 admin 恢复 `pencil`；
`/projects`、`/customers` 未受影响。

### 2.18 自助注册 + 双验证码 + 管理员审批 + 待审批登录拦截（t102）

**目标**：把「只能由管理员建号」扩展为「自助注册（手机必填 + 邮箱可选，双验证码）→ 管理员审批 → 才能登录」。

**数据（V20，幂等；生产 Flyway 关闭 → `deploy/manual-ddl.sql` V20 段）**

| 变更 | 内容 |
|---|---|
| `users` | `phone` 补唯一索引（可空，历史 NULL 不冲突）；新增 `email_verified` / `phone_verified`（BOOLEAN NOT NULL DEFAULT FALSE） |
| `users.status` | 追加 `pending_approval` / `rejected`（既有 active/suspended/deactivated 未改） |
| `registration_requests`（新） | username / password_hash / phone（必填）/ email / phone_verified / email_verified / status / reviewer_id / review_note / created_at / reviewed_at |
| `verification_codes`（新） | target / purpose / **code_hash** / expires_at / attempts / consumed_at / created_at |

**接口**：`POST /auth/register/send-code`（公开）、`POST /auth/register`（公开，201）、
`GET /admin/registration-requests`（ADMIN/PL，分页）、`POST .../{id}/approve`、`POST .../{id}/reject`（ADMIN/PL）。

**关键安全设计**

1. **注册不产生可登录账号**：落 `users.status=pending_approval` 占位用户 + `registration_requests(PENDING)`；
   用户名/手机号/邮箱的唯一性由 DB 唯一约束兜住（并发安全）。
2. **登录拦截**：`UserServiceImpl.authenticate` 在密码校验前判定 —— `pending_approval` → **403「账号待管理员审批，请耐心等待」**、
   `rejected` → **403「注册申请已被拒绝」**，均不签发 token（文案走 MessageSource，中英双语）。
3. **验证码**：6 位数字、**只存 BCrypt 哈希**、300s 有效、一次性、失败 5 次作废；
   target 冷却 300s（429）、target 每小时 5 次（429）、单 IP 每小时 20 次（429）；`dev-mode` 之外任何接口不回传明文。
4. **投递抽象**：`VerificationCodeSender`（sendSms/sendEmail），默认 `LoggingVerificationCodeSender` 只打 WARN 且不打印明文；
   生产换真实网关只需提供新 Bean（`@ConditionalOnMissingBean`）。
5. **审计**：`REGISTER_SUBMIT`（挂申请人占位用户）/ `REGISTRATION_APPROVE` / `REGISTRATION_REJECT`（挂审批管理员），
   审批两个端点加入 `SKIP_ENDPOINTS`（支持 `{id}` 路径变量）→ 一次审批仅 1 条。
6. **权限**：审批三端点 `hasAnyRole('ADMIN','PROJECT_LEADER')`；模拟态下 `/api/v1/admin/**` 写方法仍由
   `ImpersonationGuardFilter` 一律 403（防借壳批号）。

**证据**：`RegistrationFlowTest` **17 例全绿**（手机号/邮箱格式 400、未知渠道/用途 400、未发码 400、验证码错误/过期/已消费 400、
邮箱未验证 400、用户名/手机号/邮箱重复 409、注册成功 201 且 PENDING 且登录 403、审批通过可登录且 `phone_verified=true`、
重复审批 409、拒绝后登录 403、非管理员 403、5 分钟内重复发码 429、dev-mode 关闭不含 devCode、审批审计仅 1 条、带邮箱注册 `email_verified=true`）；
全量 `mvn -B clean test` **700/700**。规则、DDL 与 curl 复核见 README §48。

**线上复核（2026-11-05，jar 73,118,449 B）**：手机号格式 400、发码 200（dev-mode 临时打开时含 devCode）、5 分钟内重复 429、
注册 201（PENDING）、待审批登录 403「账号待管理员审批，请耐心等待」、管理员列表可见、审批通过后登录 200 且 `phoneVerified=true`、
重复审批 409、拒绝后登录 403「注册申请已被拒绝」、非管理员 403、三类审计各 1 条；dev-mode 关闭后响应不含 devCode。
V20 DDL 已在生产库执行（两张新表 + 两列 + phone 唯一索引），存量 10 个用户 `*_verified` 全 0 未受影响；复核数据已清理
（`registration_requests=0` / `verification_codes=0` / `orphan_user_roles=0`），`APP_VERIFICATION_DEV_MODE` 已移除并重启。

### 2.20 忘记密码（验证码重置 + 旧 token 失效 + 审计，t103）

**目标**：复用 t102 的验证码基础设施，提供「验证码 → 重置密码 → 全端下线」的公开链路，不新增表。

**数据（V21，幂等；生产 Flyway 关闭 → `deploy/manual-ddl.sql` V21 段）**

| 变更 | 内容 |
|---|---|
| `users.token_invalid_before` | 重置成功后写当前时间（秒级）；`JwtAuthenticationFilter` 拒绝 `iat < token_invalid_before` 的 token |
| 回填 `email_verified=TRUE` | 历史（管理员代建）账号邮箱视为已验证，否则忘记密码对其静默不可用；占位邮箱 `@no-email.local` 不回填 |

**接口（公开）**：`POST /auth/password-reset/send-code`、`POST /auth/password-reset/confirm`；
均已在 `SecurityConfig` permitAll，且都有服务层防线。

**安全设计**

1. **防账号枚举**：target 不存在/未验证/已停用时 `send-code` 仍 200，内部不发码不落库；`confirm` 对未知 target 统一返回
   400「验证码无效或已过期，请重新获取」。
2. **只用已验证联系方式**：手机号需 `phone_verified=true`、邮箱需 `email_verified=true`，且账号必须 `active`。
3. **验证码**：只存 BCrypt 哈希、300s、一次性、失败 5 次作废（复用 t102 实现）；发码限流沿用 t102（冷却/小时/IP）。
4. **密码强度单一实现**：`util/PasswordPolicy`（≥8 位 + 四类至少三类），**先在强度校验、再消费验证码**，弱密码不烧码。
5. **新旧密码相同 → 400**。
6. **改密即全端下线**：`users.token_invalid_before = now`；旧 token 访问受保护端点 → **401**，新登录 token 正常。
7. **审计**：`RESET_PASSWORD`（resource_type=user、resource_id=用户、details 含 channel 与脱敏 target，**不含验证码/明文密码**），一次重置仅 1 条
   （公开端点无登录主体，拦截器本就不写）。
8. **连续失败按 IP 限流**：窗口 15 分钟内 5 次失败 → 429（带 Retry-After）。

**证据**：`PasswordResetFlowTest` **13 例全绿**（发码 200 且落库、5 分钟内重复 429、未验证联系方式不发码、不存在 target 200 且不落库、
错误/过期/已消费 400、强度不足 400 且不烧码、新旧相同 400、重置后旧密码 401 新密码 200、旧 token 401 新 token 200、
邮箱渠道 + 审计内容断言、未知 target confirm 400、连续失败 429、重置后偏好接口 200）；全量回归在**隔离副本**执行
`mvn -B clean test` → **722/722**（基线 700 + 本任务 13 + 并行任务在途 9；`backend/target` 存在并发构建，故用副本独占）。
规则、DDL、配置与 curl 复核见 README §49。

**线上复核（2026-11-05，jar 73,152,953 B）**：发码 200（dev-mode 临时打开时含 devCode）→ confirm 200「密码已重置」→ 旧密码登录 **401**、
新密码登录 **200**、重置前签发的旧 token 访问 `/auth/me` **401**；不存在 target/邮箱发码仍 **200 且不含 devCode**（防枚举）；
审计 `RESET_PASSWORD`（resource_type=user、details 含 channel=sms 与脱敏手机号、不含验证码与明文密码）仅 1 条。
V21 已上生产库（`token_invalid_before` 建列 + `email_verified` 回填），复核数据已清理（users=10、requests=0、codes=0、orphan=0）。

> **口径差异（已记录 §8）**：任务书所述「≥8 位 + 三类」密码规则在既有代码中并不存在（老接口只校验长度 6–100）；
> 本轮实现为单一工具类并只在新链路启用，避免破坏既有契约与已发布用例。

### 2.21 第三方 SSO 登录（4 家 provider + 管理员开关 + 待审批关联 + 绑定/解绑，t104）

**目标**：GitHub / Microsoft / Google / WeChat OAuth 登录；开放哪些由管理员决定；陌生身份仍需审批；已建账号可绑定/解绑。

**数据（V24，幂等；生产 Flyway 关闭 → `deploy/manual-ddl.sql` V24 段）**：`sso_providers`（含 enabled + 凭据 + 三个端点 + scopes）、
`sso_bindings`（`(provider_code, external_id)` 与 `(user_id, provider_code)` 各唯一）、`sso_pending_bindings`（PENDING→ACTIVATED）、
`registration_requests` 追加 `source/provider_code/external_id/display_name` 且 `phone` 改可空。
启动时表为空则插入 4 条 `enabled=false` 默认记录（凭据留空）。

**可用判定**：`usable = enabled && configured`（client_id 与 client_secret 均非空）——
未开启 → authorize-url 400；开启但未配凭据 → **503**「该登录方式尚未完成配置」且登录页不展示按钮；两者都满足才返回 200 `{url,state}`。

**接口**：`GET /auth/sso/providers`、`GET /auth/sso/{code}/authorize-url`、`POST /auth/sso/{code}/callback`（公开）；
`PATCH /admin/sso/providers/{code}`（ADMIN，审计 `UPDATE_SSO_PROVIDER`）；`GET/POST/DELETE /users/me/sso-bindings`（登录用户，解绑需密码）。

**回调语义**：已绑定且 active → 200 签发本地 JWT（`SSO_LOGIN` 审计）；陌生身份 → **202** 不发 token，
建 `registration_requests(source=SSO)` + `sso_pending_bindings(PENDING)` + `pending_approval` 占位用户（`SSO_REGISTER_SUBMIT` 审计）；
绑定账号非 active → 403；state 无效/过期/重放 → 400（5 分钟一次性）。

**审批联动**：t102 的 approve 在 SSO 申请上额外把待审批绑定转成 `sso_bindings`（`SSO_BIND` 审计）；
SSO 用户本地密码为 32 位随机串（不告知），需要本地密码走忘记密码。

**抽象**：`SsoClient`（authorizeUrl / exchange → SsoUserInfo）+ 四个实现（继承 `AbstractSsoClient`，凭据全部取自表）+
`SsoClientFactory` 按 code 选择；测试用 `FakeSsoClientFactory`（@Primary）不触网。

**证据**：`SsoFlowTest` **14 例全绿**（providers 仅可用项、未配置 503、未开启 400、url+state、state 无效与重放 400、
首次回调 202 无 token 且建 PENDING 申请/绑定/占位用户、重复回调幂等、审批后回调 200 且 token 可用、非 active 403、
绑定成功与重复绑定 409、解绑密码错 403 与解绑后回到 202、非 ADMIN 403、开关审计 1 条、关闭后列表与 authorize-url 同步变化）；
全量回归在隔离副本执行（`backend/target` 有并发构建），结果见 §7.10 / 部署记录。
规则、DDL 与已知边界见 README §51。

### 2.22 拒绝即释放唯一键 + 管理端清理 + 审批角色显式化（t111）

**背景**：t102 自报边界「被拒后同名/同手机被占位用户唯一键占用，只能换号或人工清库」被判定为需修缺陷。

**修复方式（先删后归档）**：`reject` 之后，占位用户若**从未登录且无关联数据**（无角色/任务/资产/SSO 绑定）→ **直接删除行**；
否则**归档释放**（username → `rejected_<id8>_原值`、phone → `rej_<id8>_尾号4`、状态 `rejected`）。
`registration_requests` 记录保留为审批证据；释放策略写入 `REGISTRATION_REJECT` 审计的 details。

**被拒后登录 = 401（captain 口径，不泄露拒绝状态）**：删除/改写后原用户名登录即「用户不存在」→ 401「用户名或密码错误」；
刻意不返回 403「注册申请已被拒绝」（会确认账号存在性）。归档分支下：原用户名 401、归档用户名 403。403 仅保留给「占位行仍在且 rejected」的场景。

**管理端清理**：`DELETE /admin/registration-requests/{id}`（仅 ADMIN）删除申请 + 占位用户（走同一释放逻辑）并清理 PENDING 的 SSO 待绑定；
幂等（不存在 → 204，重复删除不重复写审计）；APPROVED 申请 → **409**（否则删号后同名可重复注册）；审计 `REGISTRATION_PURGE`。

**并发**：approve / reject / purge 统一 `PESSIMISTIC_WRITE` 行锁，先到者胜，后到者 409。

**审批角色显式化（用户拍板）**：`approve` 接受 `{roles: string[]}`；**空数组 = 建号但不授角色**（仅默认 ROLE_USER）；
未知角色码 → 400；**不再隐式给 `vulnerability_engineer`**。

**证据**：`RegistrationReapplyTest` **15 例全绿**（见 README §52.6 列表）；`RegistrationFlowTest` 的 `rejectThenLogin` 同步为 401 + 通用文案；
`SsoFlowTest` 14 例仍全绿（approve 无 body 时按空 roles 处理，不影响 SSO 绑定激活）。
规则与策略细节见 README §52。
