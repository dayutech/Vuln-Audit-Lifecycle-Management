# 审计日志（查询 / 导出 / 保留期清理）

本文档说明审计日志的写入来源、查询与导出接口、去重规则与保留期清理策略。

## 1. 写入来源

| 来源 | 覆盖范围 | 记录内容 |
|---|---|---|
| `AuditTrailInterceptor` | `/api/v1/**` 下所有 POST/PUT/PATCH/DELETE 且返回 2xx 的请求 | `action` = 方法 + 控制器 + 方法名（如 `PUT AssetController.updateAsset`），`resourceType` 取自 URI 首段，`resourceId` 取 URI 中第一个 UUID，`details` 为请求 URI |
| `TaskController`（手工） | 任务的创建/更新/状态/审核/删除/分配/评论/协作者/附件 | 带业务语义的 `action`（`task_create`、`task_approve` 等）与可读详情 |
| `ImportServiceImpl`（手工） | 资产/漏洞 CSV 导入 | `action` = `POST ImportController.importAssets`，`details` 含文件名、总行数、成功/失败行数与首条失败原因 |

### 1.1 去重规则（重要）

同一个操作只能产生一条审计记录。因此**已手工写审计的控制器必须从拦截器中排除**：

```java
// config/AuditTrailInterceptor.java
private static final String[] SKIP_CONTROLLERS = {"TaskController", "ImportController"};
```

此前 `ImportController` 未加入白名单：Service 手工写了一条带上下文的记录，拦截器又写了一条只有 URI 的记录，
一次导入会出现两条 import 审计。新增手工审计的控制器时，必须同步补进 `SKIP_CONTROLLERS`。

## 2. 查询接口

```
GET /api/v1/audit-logs
Authorization: Bearer <token>        # admin / project_leader
```

| 参数 | 说明 |
|---|---|
| `page` / `size` | 分页，`size` 由 `Paging` 收敛（1..500） |
| `userId` | 用户ID（等值） |
| `action` | 操作（等值） |
| `resourceType` | 资源类型（等值） |
| `startTime` / `endTime` | ISO-8601（如 `2026-10-29T00:00:00`），`created_at` 闭区间 |
| `keyword` | 模糊匹配 `action` / `resourceType` / `details`（大小写不敏感） |

返回统一 `ApiResponse<PageResponse<AuditLog>>`，按 `createdAt` 倒序。

## 3. 导出接口

```
GET /api/v1/audit-logs/export?startTime=...&endTime=...&keyword=...
Authorization: Bearer <token>        # admin / project_leader
```

- 响应：`text/csv; charset=UTF-8`，`Content-Disposition: attachment; filename="audit-logs.csv"`。
- 首三字节为 UTF-8 BOM（`EF BB BF`），Excel 双击打开中文不乱码；首行为表头
  `时间,用户ID,操作,资源类型,资源ID,IP地址,详情`。
- 筛选参数与列表接口一致；单次最多导出 10,000 条（`AuditLogService.EXPORT_LIMIT`），按时间倒序。
- 字段内换行折成空格，含逗号/引号时按 CSV 规则加引号并转义。

## 4. 保留期清理

| 项 | 值 |
|---|---|
| 任务类 | `service/AuditLogCleanupJob`（`@Scheduled`） |
| 执行时间 | 默认每天 03:30（`app.audit.cleanup-cron`，服务器本地时区） |
| 保留天数 | 默认 180 天（`app.audit.retention-days`，可用 `APP_AUDIT_RETENTION_DAYS` 覆盖） |
| 日志 | `审计日志定时清理完成: 保留天数=180, 清理条数=N`；异常只记 ERROR，不影响主流程 |

删除条件：`created_at < now() - retention-days`。清理按保留期天然幂等（重复执行清理条数为 0）。

## 5. 验证

```bash
# 后端用例（列表过滤 / 导出 BOM / 清理 / 导入去重）
cd backend && mvn test -Dtest=AuditLogEnhancementTest,AuditLogControllerTest,ImportControllerTest

# 线上核对
TOKEN=$(curl -s -X POST http://HOST:PORT/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .data.token)
curl -s "http://HOST:PORT/api/v1/audit-logs?keyword=asset&startTime=2026-01-01T00:00:00" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.totalElements'
curl -s -D- -o /tmp/audit.csv "http://HOST:PORT/api/v1/audit-logs/export" \
  -H "Authorization: Bearer $TOKEN" | head -5
head -c 3 /tmp/audit.csv | xxd        # 期望 efbbbf
```
