# RBAC 权限点体系

本文档描述平台内置权限点（`permissions`）、角色默认授权（`role_permissions`）与「我的权限」接口的落地方式。
权限点清单的**唯一事实来源**是代码：`backend/src/main/java/com/vulnaudit/security/PermissionCatalog.java`。

## 1. 设计定位：双轨并存

| 轨道 | 载体 | 现状 |
|---|---|---|
| 角色轨 | `@PreAuthorize("hasRole('ADMIN')")` 等注解 | **保持原语义不变**，仍承担当前全部 API 准入 |
| 权限点轨 | `permissions` / `role_permissions` / `user_permissions` | 本轮落地数据面，为后续 ABAC 提供基础 |

权限点是后续 **ABAC** 的基础：编码统一为 `资源:操作`（如 `asset:read`、`user:manage`），并携带
`api_path` + `http_method`，因此可以「按接口反查所需权限 → 比对用户有效权限集合」，
在不改动业务代码的前提下把硬编码角色名逐步替换为权限判定。为保证按接口反查可行，
目录内每条权限点的 `(api_path, http_method)` 组合全局唯一（由单元测试守护）。

## 2. 初始化机制

- 实现：`PermissionBootstrapService#bootstrap()` + 启动初始化器 `RbacBootstrapRunner`（`ApplicationRunner`）。
- 开关：`app.rbac.bootstrap-enabled`（默认 `true`，可用环境变量 `APP_RBAC_BOOTSTRAP_ENABLED` 覆盖）。
  测试环境置 `false`，RBAC 用例改为显式调用 Service，避免污染其他用例数据。
- **为什么不用 Flyway**：生产部署以 `SPRING_FLYWAY_ENABLED=false` + `ddl-auto=update` 运行（见 `deploy/README.md` 第 6 节），
  迁移脚本不会执行；且历史库的表由 Hibernate 生成。放在 Java 侧可同时满足幂等、库无关（MariaDB / H2）与可重复执行。
- 幂等键：权限点用 `code`，角色权限用 `(role_id, permission_id)`，用户授权用 `(user_id, permission_id)`。
  重复执行只做「补差」，不重复插入、不删除管理员额外调整过的授权；失败只记 ERROR 日志，不阻断启动。

## 3. 权限点清单（90 项）

按 `docs/design/api_design.md` 的模块划分：

| 模块 | 权限点 |
|---|---|
| 用户（8） | `user:read` `user:read_detail` `user:write` `user:manage` `user:delete` `user:role:read` `user:role:write` `user:permission:write` |
| 角色（6） | `role:read` `role:read_detail` `role:write` `role:update` `role:delete` `role:permission:write` |
| 权限点（5） | `permission:read` `permission:read_detail` `permission:write` `permission:update` `permission:delete` |
| 团队（6） | `team:read` `team:write` `team:update` `team:delete` `team:member:read` `team:member:write` |
| 资产（15） | `asset:read` `asset:read_detail` `asset:write` `asset:update` `asset:delete` `asset:claim` `asset:access_request` `asset:access_review` `asset:metadata:read` `asset:metadata:write` `asset:file:read` `asset:file:write` `asset:group:read` `asset:group:write` `asset:history:read` |
| 漏洞（10） | `vulnerability:read` `vulnerability:read_detail` `vulnerability:write` `vulnerability:update` `vulnerability:workflow` `vulnerability:delete` `vulnerability:export` `vulnerability:attachment:write` `vulnerability:tag:write` `vulnerability:history:read` |
| 环境（6） | `environment:read` `environment:write` `environment:update` `environment:delete` `environment:deploy` `environment:snapshot` |
| 文档（5） | `document:read` `document:write` `document:update` `document:delete` `document:access_request` |
| 工具（5） | `tool:read` `tool:write` `tool:update` `tool:delete` `tool:download` |
| 任务（5） | `task:read` `task:write` `task:update` `task:delete` `task:review` |
| 知识库（5） | `knowledge:read` `knowledge:write` `knowledge:update` `knowledge:delete` `knowledge:comment:write` |
| 通知（5） | `notification:read` `notification:update` `notification:setting` `notification:announcement:write` `notification:subscribe` |
| 仪表盘（3） | `dashboard:read` `dashboard:my` `dashboard:export` |
| 审计（2） | `audit:read` `audit:export` |
| 审批（3） | `approval:scope:read` `approval:scope:write` `approval:review` |
| 导入（1） | `import:write` |

## 4. 内置角色默认授权

| 角色 | 权限点数 | 口径 |
|---|---|---|
| `admin` | 90 | 全部权限点（随目录增长自动扩展，避免新增权限点后管理员漏授） |
| `project_leader` | 78 | 除「账号/角色/权限点维护」「公告管理」外的全部业务与管理权限 |
| `team_leader` | 57 | 本团队范围的业务读写 + 组内审批；无资产/漏洞删除与全局导出 |
| `vulnerability_engineer` | 36 | 资产只读 + 认领/申请、漏洞提交与流转、文档/工具/知识库协作 |

合计写入 261 条 `role_permissions` 关联。角色权限矩阵见 `docs/design/permission-matrix.md`。

## 5. 接口

| 接口 | 说明 |
|---|---|
| `GET /api/v1/permissions` | 权限点清单（admin / project_leader，权限矩阵 2.11） |
| `GET /api/v1/permissions/{id}` | 权限点详情 |
| `GET /api/v1/roles?includePermissions=true` | 角色列表，附带每个角色的权限点（兼容 `include_permissions` 参数名） |
| `GET /api/v1/roles/{id}/permissions` | 角色已分配权限点（供前端勾选回显） |
| `POST /api/v1/roles` | 创建角色；请求体带 `permissionIds`（或 `permission_ids`）时一并分配权限点 |
| `PUT /api/v1/roles/{id}` | 更新角色；带 `permissionIds` 时按目标集合**覆盖式同步**（缺则补、多则删，幂等） |
| `POST /api/v1/roles/{id}/permissions` | 为角色追加权限点（幂等，已分配自动跳过） |
| `DELETE /api/v1/roles/{id}/permissions` | 移除角色权限点（幂等） |
| `POST /api/v1/users/{id}/roles` | 为用户分配角色（幂等；`assigned_by` 记录操作人） |
| `DELETE /api/v1/users/{id}/roles/{roleId}` | 撤销用户角色（幂等） |
| `POST /api/v1/users/{id}/permissions` | 直接授权（幂等；支持 `expiresAt`） |
| `DELETE /api/v1/users/{id}/permissions/{permissionId}` | 撤销直接授权（幂等） |
| `GET /api/v1/users/me/permissions` | 当前用户有效权限码（角色并集 + 直接授权，排除已过期） |

「我的权限」返回体：

```json
{
  "userId": "...",
  "username": "admin",
  "roles": ["admin"],
  "permissions": ["asset:access_request", "asset:claim", "..."],
  "permissionCount": 90
}
```

`permissions` 去重并按字典序排序，便于前端缓存与断言比对。

## 6. 分配接口的幂等与存在性

| 场景 | 行为 |
|---|---|
| 重复分配角色 / 重复直接授权 | 不新增行、不续期、不报错（返回 200 与现有关联） |
| 已过期直接授权再次授予 | 复用同一行刷新 `expires_at`，重新生效 |
| 重复撤销 | 200，无关联时静默跳过 |
| 覆盖式同步提交相同集合 | 不做任何写入（幂等） |
| 用户 / 角色 / 权限点不存在 | `404`（`USER_NOT_FOUND` / `ROLE_NOT_FOUND` / `PERMISSION_NOT_FOUND`） |
| 批量分配权限点中含不存在项 | 整体失败，避免部分成功的脏授权 |

## 7. 验证

```bash
# 后端：RBAC 用例
cd backend && mvn test -Dtest=RbacPermissionTest

# 线上核对（需后端已部署）
TOKEN=$(curl -s -X POST http://HOST:PORT/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .data.token)
curl -s http://HOST:PORT/api/v1/permissions -H "Authorization: Bearer $TOKEN" | jq '.data | length'          # 期望 90
curl -s http://HOST:PORT/api/v1/users/me/permissions -H "Authorization: Bearer $TOKEN" | jq '.data.permissionCount'  # 期望 ≥10（admin 为 90）
```

重启后再次执行上述命令，`permissions` 与 `role_permissions` 行数不变即证明初始化幂等。
