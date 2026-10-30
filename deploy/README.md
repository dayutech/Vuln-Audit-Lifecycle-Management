# 部署说明（192.168.188.200）

漏洞挖掘生命周期管理系统（Vuln-Audit-Lifecycle-Management）的部署记录与运维手册。

## 1. 访问入口

| 项 | 值 |
|---|---|
| 前端地址 | http://192.168.188.200:8089 |
| 反向代理 | nginx 1.24 → `/api` 转发至 `http://127.0.0.1:8088` |
| Swagger | http://192.168.188.200:8089/swagger-ui/index.html |
| OpenAPI | http://192.168.188.200:8089/v3/api-docs |
| 账号 / 口令 | **`admin` / `admin`** |

> 口令已按要求设置为 `admin`（弱口令，评测环境可用；若对外暴露请尽快更换，见 8.3）。

## 2. 端口与进程规划

宿主 192.168.188.200 为 Ubuntu 24.04，已运行 k3s 及其业务（6443、10250、10248-10259、2181、1527、18082-18086、22、53）。本项目刻意错开：

| 端口 | 归属 | 监听地址 | 说明 |
|---|---|---|---|
| 8089 | nginx（静态资源 + 反向代理） | 0.0.0.0 | 对外唯一入口 |
| 8088 | Spring Boot 后端 | 127.0.0.1 | 仅本机，经 nginx 转发 |
| 3306 | MariaDB 10.11 | 127.0.0.1 | 仅本机 |

nginx 默认站点已移除（`/etc/nginx/sites-enabled/default` 删除），**不占用 80/443**。

验证：

```bash
ss -lntp | grep -E ':(8088|8089|3306)\b'
sudo k3s kubectl get pods -A
```

## 3. 目录结构

```
/home/leo/apps/vuln-audit/
├── backend/            # 源码 + target/vuln-audit-backend-1.0.0-SNAPSHOT.jar
├── frontend/           # 源码 + node_modules + dist/
├── deploy/
│   ├── backend.env                  # 后端环境变量（DB 口令、JWT 密钥，600）
│   ├── nginx-vuln-audit.conf        # nginx 站点配置源文件
│   ├── sync-dist.sh                 # 构建产物 → /var/www/vuln-audit
│   ├── static-server.js             # 备用 Node 反代（已停用，留作回退）
│   ├── seed.py                      # 初始化基础数据
│   └── vuln-audit-backend.service   # 前端已不再需要 systemd
└── logs/backend.log

/var/www/vuln-audit/                 # nginx 站点根目录（root:root 755/644）
/etc/nginx/sites-available/vuln-audit
/home/leo/opt/{apache-maven-3.9.9,node-v22.14.0-linux-x64}
```

> 静态文件放 `/var/www` 是因为 `/home/leo` 权限为 750，nginx 的 `www-data` 无法穿越。**前端重新构建后必须执行 `bash ~/apps/vuln-audit/deploy/sync-dist.sh`**，否则 nginx 仍服务旧文件。

## 4. 服务管理

| 组件 | 托管方式 | 命令 |
|---|---|---|
| 后端 | systemd 用户级 + linger | `systemctl --user restart vuln-audit-backend` |
| nginx | 系统级 | `sudo systemctl reload nginx` |

```bash
export XDG_RUNTIME_DIR=/run/user/$(id -u)
systemctl --user status vuln-audit-backend
journalctl --user -u vuln-audit-backend -f
tail -f ~/apps/vuln-audit/logs/backend.log
sudo nginx -t && sudo systemctl reload nginx
sudo tail -f /var/log/nginx/vuln-audit.error.log
```

已执行 `loginctl enable-linger leo`：退出 SSH 或主机重启后后端仍自启；nginx 由系统 systemd 自启。

## 5. 数据库

| 项 | 值 |
|---|---|
| 库 | `vuln_audit`（utf8mb4 / utf8mb4_unicode_ci） |
| 应用账号 | `vulnapp` / 见 `deploy/backend.env` `DB_PASSWORD` |
| 管理员账号 | MariaDB `root` 走 unix_socket（`sudo mariadb`） |
| 表数量 | 56 张（Hibernate 依据实体生成） |

```bash
mysqldump -uvulnapp -p vuln_audit > ~/vuln_audit_$(date +%F).sql
```

## 6. 与仓库默认配置的差异（重要）

1. **Flyway 已关闭**（`SPRING_FLYWAY_ENABLED=false`），改用 `ddl-auto=update`。
   原因：V2/V3/V4 会对 `tasks`、`asset_files`、`asset_versions` 做 `ALTER`，而这些表只由 Hibernate 实体创建、无迁移负责建表，空库按 V1→V12 必在 V2 失败；V5 的 `ADD COLUMN copied_from_asset_version_id` 无 `IF NOT EXISTS`，而该字段已在实体中。
2. **漏洞类型字典**：单独补种 V6 中的 13 条 `INSERT`。
3. **角色映射**：`init.sql` 只插 admin 用户、不插 `user_roles`，已补 `admin → admin`，否则所有 `@PreAuthorize` 管理接口 403。
4. **初始数据**：1 个团队 + 3 个示例资产（"部署初始化示例，可删除"）。资产类型/来源下拉框取自 `/assets/options` 的**已有数据去重**，空库时下拉为空、无法创建首个资产。

## 7. 重建流程

```bash
export PATH=$HOME/opt/node-v22.14.0-linux-x64/bin:$PATH
export JAVA_HOME=$(dirname $(dirname $(readlink -f /usr/bin/java)))
export XDG_RUNTIME_DIR=/run/user/$(id -u)

# 后端
cd ~/apps/vuln-audit/backend && ~/opt/apache-maven-3.9.9/bin/mvn -B -DskipTests package
systemctl --user restart vuln-audit-backend

# 前端（react-quill 2 的 peer 只到 React 18，必须 legacy-peer-deps）
cd ~/apps/vuln-audit/frontend && npm ci --legacy-peer-deps && npm run build
bash ~/apps/vuln-audit/deploy/sync-dist.sh
```

镜像源：`~/.m2/settings.xml`（阿里云）、`~/.npmrc`（npmmirror）。

## 8. 已知风险与建议

### 8.1 弱口令
`admin / admin` 为本次按要求设置的弱口令，且 8089 对整个局域网开放。

### 8.2 应用自身缺陷（不影响运行）
- 数据级权限未落地：`/assets/{id}`、元数据、文件下载、文档内容均不校验授权。
- 漏洞 `PUT /vulnerabilities/{id}` 可绕过工作流状态机。
- 任务详情/删除接口无权限校验。
- 后端 CORS 为 `*` + `allowCredentials=true`。
- 角色 authority 为裸 `admin`（无 `ROLE_` 前缀），与 `@PreAuthorize("hasRole('ADMIN')")` 不匹配，导致 `/users/{id}/reset-password` 等接口恒定 403。

### 8.3 修改密码（`admin` 只有 5 位，接口要求 ≥6，需走数据库）

```bash
HASH=$(python3 -c "import bcrypt;print(bcrypt.hashpw(b'新口令', bcrypt.gensalt(rounds=10, prefix=b'2a')).decode())")
MYSQL_PWD='VulnApp_2026#Db9x' mariadb -uvulnapp vuln_audit \
  -e "UPDATE users SET password_hash='$HASH', updated_at=NOW() WHERE username='admin';"
```

长度 ≥6 的口令也可以走接口（`POST /api/v1/auth/change-password`，需带 Bearer Token）。

---

## 9. 本次代码修复（部署版本已包含）

| 级别 | 修复项 | 位置 | 验证结果 |
|---|---|---|---|
| P0 | 上传上限配置为 100MB（原 Spring 默认 1MB，传 2MB 即失败） | `application.yml` | 3MB/5MB 上传返回 201 |
| P0 | `MaxUploadSizeExceededException` 映射 413 并给出准确文案；缺参/类型错/JSON 错映射 400 | `GlobalExceptionHandler` | 上传缺 `category` 返回 400（原 500） |
| P0 | 统一 authority 为 `ROLE_*` 大写：DB 角色码 → `ROLE_ADMIN / ROLE_PROJECT_LEADER / ROLE_TEAM_LEADER / ROLE_VULNERABILITY_ENGINEER` | `RoleAuthority`、`UserDetailsServiceImpl`、`AuthController`、`TaskController` | `/users/{id}/reset-password` 由恒 403 变为正常鉴权（不存在用户→404） |
| P1 | 数据级授权 `AccessGuard`：资产/元数据/文件/交付物/文档/任务按「负责人、已批准访问、团队负责人、管理层」判定 | `service/AccessGuard`、各控制器 `@PreAuthorize` | 工程师读他人资产详情/元数据/文件、读文档内容 → 403；列表仍 200 |
| P1 | 审计日志全覆盖：写操作拦截器统一落库（原仅 TaskController） | `config/AuditTrailInterceptor`、`WebMvcConfig` | `audit_logs` 出现 `PUT AssetController.updateAsset` 等记录 |
| P1 | 文档版本真正落库：创建写 v1、正文变更写新版本并递增版本号 | `DocumentController`、`DocumentServiceImpl` | 版本列表 v1「创建文档」+ v2「编辑保存」 |
| P1 | 资产变更历史真正落库（字段级 diff） | `AssetController` | `asset_history` 出现 name/description/targetType 等变更行 |
| P1 | CORS 改为显式白名单（默认关闭），Swagger 由 `app.security.public-api-docs` 控制 | `SecurityConfig` | 跨域请求不再返回 allow-origin；Swagger 仍 200 |
| P1 | 审计日志、角色/权限管理加角色门槛 | `AuditLogController`、`RoleController` | 工程师访问 `/audit-logs` → 403 |
| P2 | severity 归一为大写枚举；CVE 重复改用专用错误码；交付历史记录真实前置状态 | `VulnerabilityServiceImpl`、`ErrorCode` | 单测覆盖 |
| P2 | `PUT /vulnerabilities/{id}` 也校验工作流状态机 | `VulnerabilityServiceImpl` | 非法跳转返回 400 |
| P2 | 分页 size 收敛到 1..500 | `util/Paging` 及 15 个 ServiceImpl | — |
| P2 | 前端：漏洞状态接口改调真实端点；环境状态枚举对齐后端 | `api/vulnerability.ts`、`pages/assets/Detail.tsx` | 前端构建通过 |

回归：`mvn test` 226 个用例全绿；前端 `npm run build` 通过；nginx 静态资源已同步。

### 仍未处理（明确保留）
- 审批路由与 `ApprovalScope` 继承（实体存在、业务未接入）
- 环境部署/启停仍为模拟实现（无 Docker 集成）
- 报表导出仍为空实现；邮件/短信/Webhook 无发送通道
- 工具库「下载」仍只计数、无文件流端点
- 约 150 个后端接口仍无前端入口（访问申请、认领、标签、漏洞历史/附件、通知设置等）
### 9.1 CORS 相关补充（登录报 "Invalid CORS request" 的处置）

现象：CORS 收紧为白名单后，`admin` 登录返回 403 `Invalid CORS request`。

原因：nginx 用 `proxy_set_header Host $host` 转发时**丢失了端口**，后端看到的请求 Host 是
`192.168.188.200`，而浏览器 `Origin` 是 `http://192.168.188.200:8089`，两者端口不一致，
Spring 判定为跨域；此时白名单为空 → 直接拒绝。

处置（两处同时生效）：
1. nginx 改用 `proxy_set_header Host $http_host`，保留端口，同源判定重新成立；
2. 后端在 `app.cors.allowed-origins` 为空时**不安装 CORS 过滤器**（而非安装一个空配置），
   同源/反代场景不再因为携带 `Origin` 而被拒。

如需真正的跨域访问（前端独立域名），在 `backend.env` 设置
`APP_CORS_ALLOWED_ORIGINS=https://your-frontend` 即可，此时过滤器才会启用。
---

## 10. 第二轮完善（本次）

| 类别 | 内容 | 接口/位置 | 验证 |
|---|---|---|---|
| 安全 | 登录失败限流：同一「用户名+客户端IP」连续失败 5 次锁定 300 秒，返回 429 并带 `Retry-After` | `service/LoginAttemptService`、`exception/TooManyRequestsException`、`AuthController.login` | 连续 5 次 401 后第 6 次 429 |
| 数据一致性 | 漏洞列表按可见资产范围过滤：普通成员只看自己负责/已授权/所辖团队的资产下的漏洞，管理层不受限 | `AccessGuard.visibleAssetIds`、`VulnerabilityService.findAll(...,allowedAssetIds)` | 单测：工程师 0 条 / 管理员 1 条 |
| 业务规则 | 任务审核禁止自审自批：审核人不能是任务负责人（approve / reject 均校验） | `TaskServiceImpl` | 单测：组长审自己的任务 → 403 |
| 能力补齐 | 工具资源库支持真实工具包：新增 `file_name/file_path/file_size` 字段、`POST /tools/{id}/file` 上传、`GET /tools/{id}/download` 流式下载并计数 | `Tool`、`ToolController`、`ToolServiceImpl` | 上传 200 → 下载 4096 字节 → 计数 +1；未上传文件返回 404 |
| 缺陷修复 | `POST /tools` 此前稳定 500（`created_by` 未赋值，违反 NOT NULL） | `ToolController.createTool` | 创建返回 200 并带 ID |

回归：`mvn test` 229 个用例全绿（新增 3 个：登录限流、自审拒绝、漏洞范围过滤）。
### 10.1 前端产物目录与 SPA 路由冲突（资产中心打不开）

现象：浏览器访问 `/assets`（资产中心）返回 **301** 跳转，页面空白。

原因：Vite 默认把构建产物输出到 `dist/assets/`，而前端路由里 `/assets` 正是资产中心。
nginx 的 `try_files $uri $uri/ /index.html` 命中同名目录 → 301 到 `/assets/` → 目录无 index → 403/异常。

处置：
1. `vite.config.ts` 设置 `build.assetsDir: 'static'`，产物改到 `/static/`，从根源消除同名；
2. nginx 增加 `location = /assets` 与 `location = /assets/` 兜底直出 `index.html`；
3. `sync-dist.sh` 支持 `SUDO_PASS` 环境变量，便于非交互同步（此前在 ssh 非交互 shell 里
   因拿不到 sudo 口令而**静默使用旧产物**）。

验证：`/`、`/assets`、`/assets/{id}`、`/tasks/{id}`、`/vulnerabilities` 均 200；
index.html 引用 `/static/index-*.js|css` 且资源可 200 加载。
---

## 11. 第三轮完善（审批链路 + 报表导出）

| 类别 | 内容 | 验证 |
|---|---|---|
| 业务核心 | **审批范围与审批路由落地**（SRS 3.7.3）：申请按「直属领导（须具备覆盖该资产的审批范围）→ 项目负责人 → 管理员」自动路由，`asset_access.approver_id` 落库 | 线上实测：授权范围 201 → 申请 approver=组长 → 组长待办 1 条 → 审批后工程师访问资产 200 |
| 安全 | 审批权限校验：仅路由到的审批人、覆盖范围的团队负责人或 admin/project_leader 可审批；认领审批同样校验 | 单测：工程师审批自己的申请 → 403 |
| 安全 | 待审批列表按路由过滤（此前返回系统内全部待审申请，任何登录用户可见他人申请） | 单测：工程师 0 条 / 管理员 1 条 |
| 能力补齐 | 新增 `approval_scopes` 管理接口（授予/查询/撤销/我的范围） | `POST /api/v1/approval-scopes` → 201 |
| 缺陷修复 | 访问申请未传 `canViewDocuments/Environments/Files` 时写入 NULL 触发 NOT NULL 约束 → 500 | 单测覆盖 |
| 能力补齐 | **报表导出真实可用**：`GET /api/v1/dashboard/export?type=assets\|vulnerabilities\|workload` 输出 UTF-8 BOM CSV，xlsx 返回 400 | 实测 assets 633B/5 行、workload 97B/2 行、BOM 正确 |

回归：`mvn test` **235 个用例全绿**（新增 3 个审批用例 + 3 个导出用例）。
---

## 12. 第四轮完善（前端接入访问申请 / 认领 / 审批中心）

此前「访问申请」「资产认领」的后端接口齐全但**前端零入口**，工程师实际无法发起申请。
本轮补齐 UI：

| 页面 | 新增能力 |
|---|---|
| 资产列表 `/assets` | 操作列新增「申请访问」，通过弹窗选择是否查看文件后直接提交（**关键**：未获授权时详情页会 403，列表页入口是唯一可行路径） |
| 资产详情 `/assets/{id}` | 头部新增「申请访问」「认领资产」按钮 + 状态标签（已授权 / 待审批 / 被驳回 / 已认领），按钮按当前状态自动禁用；两个模态表单可填写范围与说明 |
| 审批中心 `/approvals` | 新页面：展示路由给当前用户的待审批访问申请，支持「通过 / 驳回」并填写审批备注；侧边栏「核心业务」组新增入口 |

前端新增 API 封装：`createAssetAccessRequest`、`getMyAccessRequests`、`getAssetAccessRequests`、`getMyAccessApprovalRequests`、`reviewAssetAccessRequest`、`createAssetClaim`、`getMyClaims`、`getAssetClaims`、`reviewAssetClaim`、`completeAssetClaim`、`abandonAssetClaim`。

构建产物校验：`/static/index-*.js` 中「申请访问」「认领资产」「审批中心」「访问申请待审批」等文案均已包含；`/`、`/assets`、`/approvals` 均返回 200。

---

## 13. 第五轮完善（漏洞附件下载端点）

此前 `GET /api/v1/vulnerabilities/{id}/attachments` 只返回附件元数据（含 `filePath`），
没有真正的文件流端点，前端「下载附件」只能降级提示。本轮补齐：

| 类别 | 内容 |
|---|---|
| 新端点 | `GET /api/v1/vulnerabilities/{id}/attachments/{attachmentId}/download` |
| 响应 | `Content-Type: application/octet-stream`，`Content-Length` 为真实字节数，`Content-Disposition: attachment; filename="原始文件名"; filename*=UTF-8''<RFC5987编码>`（ASCII 兜底 + 中文文件名编码，浏览器/下载工具均可正确落盘） |
| 归属校验 | `attachmentId` 必须属于路径中的 `{id}`，否则 **404** `附件不存在或不属于该漏洞` |
| 文件缺失 | 库中有记录但磁盘文件不存在/不可读 → **404** `附件文件不存在或已被移除`（不再是 500） |
| 数据级授权 | 方法级 `@PreAuthorize("@accessGuard.canReadVulnerability(#id, authentication)")`：按 vulnerabilityId → assetId 复用资产可见性口径（admin/project_leader 全局可见；team_leader 限本团队资产；普通成员限本人负责或已获 approved 授权的资产），无权返回 **403** |

验证（curl，`$TOKEN` 为登录后的 Bearer Token）：

```bash
# 1) 上传
curl -s -X POST "http://192.168.188.200:8089/api/v1/vulnerabilities/$VULN_ID/attachments" \
  -H "Authorization: Bearer $TOKEN" -F "file=@poc.zip"

# 2) 下载并比对字节数
curl -s -D /tmp/h.txt -o /tmp/poc.zip \
  "http://192.168.188.200:8089/api/v1/vulnerabilities/$VULN_ID/attachments/$ATTACHMENT_ID/download" \
  -H "Authorization: Bearer $TOKEN"
grep -i content-disposition /tmp/h.txt   # attachment; filename="poc.zip"; filename*=UTF-8''poc.zip
cmp poc.zip /tmp/poc.zip && echo "字节一致"

# 3) 异常用例
curl -s -o /dev/null -w '%{http_code}\n' \
  "http://192.168.188.200:8089/api/v1/vulnerabilities/$OTHER_VULN_ID/attachments/$ATTACHMENT_ID/download" \
  -H "Authorization: Bearer $TOKEN"   # 404（跨漏洞）
curl -s -o /dev/null -w '%{http_code}\n' \
  "http://192.168.188.200:8089/api/v1/vulnerabilities/$FOREIGN_VULN_ID/attachments/$FOREIGN_ATTACHMENT_ID/download" \
  -H "Authorization: Bearer $ENGINEER_TOKEN"   # 403（无数据级权限）
```

后端单测：`cd backend && mvn -B test -Dtest=VulnerabilityAttachmentDownloadTest`
（7 个用例：正常下载字节一致 + Content-Disposition、中文文件名 RFC5987 编码、跨漏洞 404、
无权限 403、管理员放行、文件缺失 404、附件不存在 404）。

前端无需改动（t9 已按该路径调用，仅需后端提供文件流即可从「降级提示」切换为真实下载）。

---

## 14. 第六轮完善（漏洞子资源数据级授权补齐）

此前 `VulnerabilityController` 只有 `DELETE /{id}`（ADMIN）与新加的附件下载端点受保护，**其余端点只要知道 UUID 就能读写**：
任意登录用户可读他人资产漏洞的详情/标签/历史/附件列表，并可上传/删除附件、增删标签、改状态。
本轮按资产口径补齐子资源授权，与 `AssetController` 的数据级授权保持一致。

### 14.1 授权矩阵（漏洞域）

判定入口：`AccessGuard`，均以 `vulnerabilityId → assetId` 反查后复用资产口径。
口径：admin / project_leader 全局；team_leader 限本团队资产；普通成员限本人负责（`assets.owner_id`）或已获 `approved` 授权的资产。

| 方法级守卫 | 语义 | 覆盖端点 |
|---|---|---|
| `@accessGuard.canReadVulnerability(#id, authentication)` | 可读：管理层 / 资产负责人 / 已获批访问 / 所属团队负责人 | `GET /{id}`、`GET /{id}/tags`、`GET /{id}/history`、`GET /{id}/attachments`、`GET /{id}/attachments/{attachmentId}/download` |
| `@accessGuard.canWriteVulnerability(#id, authentication)` | **可写**：管理层 / 资产负责人（不是只读的降级） | `PUT /{id}`、`PATCH /{id}/(workflow-status\|customer-status\|disclosure-status\|exploit-stage\|vendor-fix)`、`POST /{id}/assign`、`POST /{id}/deliver`、`POST /{id}/tags`、`DELETE /{vulnerabilityId}/tags/{tagId}`、`POST /{id}/attachments`、`DELETE /{vulnerabilityId}/attachments/{attachmentId}` |
| `@accessGuard.canWriteAsset(#vulnerability.assetId, authentication)` | **可写资产**才能建漏洞：管理层 / 资产负责人（方法体内再校验一次，防 TOCTOU） | `POST /`（创建漏洞） |
| `@accessGuard.canReadAsset(#assetId, authentication)` | 可读：按资产可见性 | `GET /by-asset/{assetId}` |
| `hasRole('ADMIN')` | 仅管理员 | `DELETE /{id}`（保持原有角色轨，未降级为数据级） |

### 14.2 统计端点的数据边界（t27 补齐）

| 端点 | 收敛方式 |
|---|---|
| `GET /statistics/workflow-status` | 先取 `AccessGuard.visibleAssetIds(authentication)`：`null`（admin/project_leader）→ 全量 group by；否则只统计 `asset_id ∈ 可见集合`（负责人 ∪ `approved` 授权 ∪ 所带团队），与列表接口共用同一个 Specification 判定 |
| `GET /statistics/severity` | 同上 |

> 口径一致性：列表、统计共用 `VulnerabilityServiceImpl.buildSpec(...)`，不存在第二套可见性判定；
> 越权创建漏洞（`POST /` 指向他人资产）在执行方法级守卫时即 403，库中不会留下记录。

### 14.2.1 仪表盘统计的授权与边界（t29 补齐）

仪表盘侧的漏洞统计此前既无角色门槛、也未收敛范围（任何登录用户可读全量分布）。现改为「角色门槛 + 可见范围收敛」两层：

| 端点 | 角色门槛（权限矩阵 2.13） | 数据收敛 |
|---|---|---|
| `GET /api/v1/dashboard/vulnerabilities/stats` | `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')` | `visibleAssetIds`：admin/project_leader 全量，team_leader 仅本团队可见资产 |
| `GET /api/v1/dashboard/vulnerabilities/stats/workflow-status` | 同上 | 同上（委托 `VulnerabilityService.countByWorkflowStatus(scoped)`） |
| `GET /api/v1/dashboard/vulnerabilities/stats/severity` | 同上 | 同上（委托 `countBySeverity(scoped)`） |
| `GET /api/v1/dashboard/assets/stats`、`/status`、`/type`、`/priority` | **不加门槛**（首屏对所有角色展示） | 按 `visibleAssetIds` 收敛：工程师/普通成员只统计自己负责或已获批的资产 |
| `GET /api/v1/dashboard/overview`、`/my` | 不加门槛 | 概览内的资产与漏洞统计均按 `visibleAssetIds` 收敛（避免成为绕过通道） |

口径来源统一：控制器 `currentVisibleAssets()` → `AccessGuard.visibleAssetIds(authentication)`；
漏洞分组计数委托 `VulnerabilityService` 的同名重载（与 `/vulnerabilities/statistics/*` 完全同一判定），
仪表盘不另写过滤规则。`severity` 在库中为大写，响应仍归一为小写 key（`critical/high/medium/low/info`），保持既有前端结构。

### 14.2.2 导出/下载类端点授权清单（t31 审计）

`GET /api/v1/dashboard/export` 此前只要求登录，任何用户都能一次性导出**全量**资产/漏洞明细 CSV。
现按权限矩阵 2.13 收紧为 admin / project_leader，并对全部「导出/下载/模板」类端点做了一轮复核：

| 端点 | 当前校验 | 结论 |
|---|---|---|
| `GET /api/v1/dashboard/export` | `@PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")`（**t31 新增**） | ✅ 已修复。导出内容为**全量**，仅管理层可用；**若日后放开给 team_leader，必须按 `visibleAssetIds` 收敛导出范围**（方法注释已写明） |
| `GET /api/v1/audit-logs/export` | 方法级 + 类级 `hasAnyRole('ADMIN','PROJECT_LEADER')` | ✅ 恰当 |
| `GET /api/v1/import/templates/{type}` | 仅登录 | ✅ 可接受：只返回 CSV 表头模板，无业务数据。⚠️ 但同控制器 `POST /import/assets|vulnerabilities` 同样**无角色/数据级校验**，任何登录用户可批量写入——建议单开任务收紧 |
| `GET /api/v1/asset-deliverables/{id}/download` | `@accessGuard.canReadDeliverable(#id)` | ✅ 数据级授权 |
| `GET /api/v1/asset-files/{id}/download` | `@accessGuard.canReadAssetFile(#id)` | ✅ 数据级授权 |
| `GET /api/v1/asset-files/{fileId}/versions/{version}` | `@accessGuard.canReadAssetFile(#fileId)` | ✅ 数据级授权 |
| `GET /api/v1/vulnerabilities/{id}/attachments/{attachmentId}/download` | `@accessGuard.canReadVulnerability(#id)` + 附件归属校验 | ✅ 数据级授权 |
| `GET/POST /api/v1/tools/{id}/download`、`/assets…` 见上、`GET /api/v1/tools/{id}/versions/{versionId}/download` | 仅登录 | ✅ 符合矩阵 2.8（工具库下载对所有角色开放）；版本必须属于该工具（跨工具 404），无越权资源 |
| `GET /api/v1/tools/{id}/download-count` | 仅登录 | ✅ 仅返回计数，无明细 |

> 结论：需要的只有 `/dashboard/export` 一处缺门槛，已修复；其余导出/下载端点要么走数据级授权，要么按矩阵本来就对所有角色开放。
> 唯一新增的遗留项是 `POST /api/v1/import/*`（批量写入无授权收口），已在上表标注。


### 14.3 状态码约定

| 场景 | 状态码 |
|---|---|
| 非管理层访问他人资产的漏洞（读/写） | **403** |
| 非管理层在他人资产下创建漏洞 | **403**（且库中无新增） |
| 创建漏洞未传 `assetId` | **400** |
| 管理层访问不存在的漏洞 | **404**（先放行，再由控制器判定资源存在性） |
| 非管理层访问不存在的漏洞 | **403**（与资产口径一致，避免用状态码枚举资源） |
| 工程师调用 `DELETE /{id}` | **403**（角色轨拦截，管理员 200） |

### 14.4 验证

```bash
# 后端用例（交叉访问 + 子资源读写 + 创建边界 + 统计边界 + 管理员删除）
cd backend && mvn -B test -Dtest=VulnerabilityAuthorizationTest
cd backend && mvn -B test -Dtest=VulnerabilityControllerTest
```

两个不同资产的负责人互相访问对方漏洞时，读与写均返回 403：
工程师令牌访问 `$FOREIGN_VULN_ID` 的 `GET /{id}`、`POST /{id}/tags`、`POST /{id}/attachments` → 全部 403；
反向（对方访问工程师资产漏洞）同样 403。

统计边界示例（两账号交叉）：工程师账号 `GET /vulnerabilities/statistics/severity` 只含自己资产的数据，
管理员账号同端点返回全量；两者总数差即为他人资产漏洞数。

仪表盘统计的验证：

```bash
cd backend && mvn -B test -Dtest=DashboardStatsAuthorizationTest
cd backend && mvn -B test -Dtest=DashboardControllerTest
```

工程师令牌访问 `/dashboard/vulnerabilities/stats`、`/stats/workflow-status`、`/stats/severity` → 三个都 **403**；
小组长令牌看到的漏洞总数小于管理员（差值即其不可见团队的漏洞数）；资产统计与概览同样按可见范围收敛。

导出授权的验证：

```bash
cd backend && mvn -B test -Dtest=DashboardExportAuthorizationTest
```

工程师与小组长访问 `GET /api/v1/dashboard/export?type=assets` → 均 **403**；admin / project_leader → **200** 且返回带 BOM 的非空 CSV。

---

## 15. 第七轮完善（工具版本并发唯一键冲突：409 语义）

**问题**：并发上传同一工具的版本时，`(tool_id, version_no)` 唯一索引冲突发生在**事务提交**阶段，
原实现把 `DataIntegrityViolationException` 的捕获放在 `save()` 外层，异常逃逸到全局处理器 → 记为「系统异常」返回 **500**。
实测 10 并发：`201×5 + 500×5, 409=0`，前端「409 后自动重试一次」的契约从未生效。

**修复（三层）**：
1. `saveAndFlush(...)`：把 flush 提前到受控代码块内，唯一键冲突在 `try` 内抛出；
2. **乐观重试 + 退避抖动**：捕获后在新事务中重算 `versionNo = max + 1`，最多 3 次（含首次），
   每次失败后等待 `n×15ms + 0~20ms`；仍失败返回 **409**「版本号冲突，请重试」；
3. **行级锁串行化**（关键）：同一工具的版本号分配在 `SELECT ... FOR UPDATE`（`ToolRepository#findByIdForUpdate`）
   下完成，从根上避免并发线程算出相同 `version_no`；重试作为跨库/极端情况兜底。

**接口契约**：

| 场景 | HTTP | 说明 |
|---|---|---|
| 上传成功（含并发排队成功） | **201** | 返回 `versionNo`、`createdBy`、`hasFile`、`fileName/fileSize` |
| 3 次重试仍撞唯一键 | **409** | `{"success":false,"message":"版本号冲突，请重试"}`，前端应重试一次 |
| 工具不存在 | 404 | `工具不存在` |
| 未登录 / 用户不存在 | 401 | 审计字段 `created_by` 必填，不再 500 |
| 跨工具下载版本 | 404 | `版本不存在或不属于该工具` |

**验收命令（并发压测）**：

```bash
# 8 并发上传同一工具版本，统计 201/409/500
for i in $(seq 1 8); do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST \
    "http://192.168.188.200:8089/api/v1/tools/$TOOL_ID/versions" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@poc.zip" -F "changelog=concurrent-$i" &
done | sort | uniq -c
# 期望：201=8, 409=0, 500=0（高竞争下允许少量 409，绝不允许 500）
mysql -e "SELECT tool_id, version_no, COUNT(*) c FROM tool_versions GROUP BY tool_id, version_no HAVING c > 1;" vuln_audit
# 期望：Empty set
```

后端自动化用例：`cd backend && mvn -B test -Dtest=ToolVersionConcurrencyTest`
（8 线程并发上传 → 输出 `并发上传结果: 201=N, 409=M, 500=0`，断言 versionNo 唯一；另测重试耗尽 → 409）。

**同类收敛**（“先查 max/count 再插入唯一键”）：
- 漏洞编号 `vulnerability_no`（count+1 生成）：`saveAndFlush` + 冲突转 **409「漏洞编号冲突，请重试」**；
- 资产编号 `assets.code`（6 位随机 + 20 次预检）：`saveAndFlush` + 冲突转 **409「资产编号冲突，请重试」**；
- 资产版本号 `(asset_id, version_no)`：`saveAndFlush` + 冲突转 **409「资产版本号冲突，请重试」**；
- 文档版本号 `(document_id, version)`：**已收敛（t32 推翻此处此前的“不改”结论）**——版本号重复是数据正确性缺陷：
  两份内容不同的修订拿到同一 `version` 后，历史排序、回滚、审计追溯全部失真且不可逆（无法事后判断哪份才是真正的 vN）。因此：
  - **V17 迁移** 加唯一索引 `uk_document_versions_document_version`；迁移前用「自连接 + GROUP BY」按 `(created_at, id)`
    重排冲突文档的版本号（兼容 MariaDB 10.0/10.1，不依赖窗口函数；语句幂等可重复执行）；
  - 版本分配沿用三层方案：`DocumentRepository#findByIdForUpdate` 行级锁串行化 + `saveAndFlush` 提前暴露冲突 +
    有界重试（新事务重算 `version = max + 1`，最多 3 次）；
  - 冲突耗尽 → **409「文档版本冲突，请重试」**，其他完整性错误 → 400；
  - 实测 8 并发更新同一文档：`200=8, 409=0, 500=0`，`document_versions.version` 唯一且连续（1..N），
    `documents.version` 与最新快照保持一致；
  - 后端用例：`cd backend && mvn -B test -Dtest=DocumentVersionConcurrencyTest`。

---

## 16. 第八轮完善（批量导入的写侧授权与逐行数据边界）

**问题**：`POST /api/v1/import/assets` 与 `POST /api/v1/import/vulnerabilities` 此前**没有任何角色或数据级校验**——
任何登录用户都能批量写入资产与漏洞。相比导出（读泄漏），导入是**写**越权，可造成大规模数据污染。
同控制器 `POST /import/users` 早已限定 ADMIN，说明接口设计本来就该分级。

### 16.1 授权矩阵（导入域）

| 端点 | 角色门槛 | 数据级校验 |
|---|---|---|
| `POST /api/v1/import/assets` | `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')` | admin / project_leader 不限团队；team_leader 每行 `teamId`/`teamCode` 必须属于「自己领导的团队 ∪ 自己所属的团队」，否则该行 failed 且不落库 |
| `POST /api/v1/import/vulnerabilities` | 所有登录用户可用（逐行判定） | 每行按目标资产 `accessGuard.canWriteAsset(assetId)`；无权行 failed 且**不产生任何写入**（含不会自动创建资产版本） |
| `POST /api/v1/import/users` | `hasRole('ADMIN')`（原有） | — |
| `GET /api/v1/import/templates/{type}` | 仅登录 | 仅返回 CSV 表头模板，无业务数据 |

**fail-closed**：团队范围解析不到用户/权限时返回空集合（一律拒绝）；资产可写判定抛异常时按拒绝处理。
不存在「未指定团队 → 默认放行」的分支。

### 16.2 失败原因分类（同为 failedRows，文案可区分）

| 类别 | 示例 reason |
|---|---|
| 校验失败 | `无效的优先级: P9，可选值 P0/P1/P2/P3`、`缺少 teamId 或 teamCode（二者填其一）`、`资产编号不存在: XXX` |
| 权限拒绝 | `权限拒绝：无权导入该团队（teamId=...）`、`权限拒绝：无权在该资产下创建漏洞（assetCode=...）` |

响应结构不变：`{totalRows, successRows, failedRows:[{line, reason}]}`，逐行独立，部分成功不影响其他行。

### 16.3 验证

```bash
cd backend && mvn -B test -Dtest=ImportAuthorizationTest
cd backend && mvn -B test -Dtest=ImportControllerTest
```

三账号交叉验证：
- 工程师 `POST /import/assets` → **403**（整单拒绝，库中无新增）；
- 小组长导入含「本团队 + 他人团队」两行 → 成功 1 行、失败 1 行（`权限拒绝：无权导入该团队`），他人团队资产未落库；
- 工程师导入漏洞到「自己负责资产 + 他人资产」两行 → 成功 1 行、失败 1 行（`权限拒绝：无权在该资产下创建漏洞`），他人资产下无漏洞、无版本；
- admin 导入同一份文件 → 全部成功。

---

## 17. 第九轮完善（文档回滚写快照：修复审计断链与版本跳号）

**问题**：`POST /api/v1/documents/{id}/versions/{version}/rollback` 此前只把目标版本内容拷进 `documents`，
**不写 `document_versions` 快照**，导致两个后果：
1) 历史里查不到「谁在何时回滚到哪个版本」，审计追溯断链；
2) 回滚后 `documents.version` 与历史序列脱节，下一次编辑的版本号出现跳号/悬空。

**修复**（`DocumentService#rollbackToVersion`，与 t32 的编辑保存共用同一套并发语义）：

| 行为 | 说明 |
|---|---|
| 新增快照 | `version = max(历史最大版本, documents.version) + 1`，内容取目标版本（title/summary/content） |
| 历史不可变 | 只读取目标版本快照，**不 UPDATE 任何历史行** |
| 审计可追溯 | 新快照 `changelog = "rollback from v{目标版本}"`，`created_by = 操作者` |
| 并发安全 | `DocumentRepository#findByIdForUpdate` 行级锁 + `saveAndFlush` + 有界重试（3 次，退避抖动） |
| 状态码 | 成功 **200**；目标版本不存在 **404**「版本不存在: vN」；重试耗尽 **409**「文档版本冲突，请重试」；其他完整性错误 **400** |

**等价 SQL 校验（回滚后）**：

```sql
-- 版本号连续、无重复
SELECT version, COUNT(*) c FROM document_versions WHERE document_id = ? GROUP BY version HAVING c > 1;
-- 最新快照的 changelog 应能看到回滚来源
SELECT version, changelog, created_by, created_at FROM document_versions
 WHERE document_id = ? ORDER BY version DESC LIMIT 3;
```

**验证**：

```bash
cd backend && mvn -B test -Dtest=DocumentRollbackSnapshotTest
# 输出「文档并发回滚/编辑结果: 200=8, 409=0, 500=0」，且版本号连续、无重复
```


## 18. 第十轮：部署验证与数据清理（t13）

本节记录 2026-10-29 这次**部署验收**的执行方式、结果与重置方法，完整原始输出见 `deploy/smoke-result.txt`。

### 18.1 部署动作（按顺序）

1. **前端构建**：`cd frontend && npm install --legacy-peer-deps && npm run build`；产物 `dist/`（index.html + static/index-*.js/css）。
2. **后端构建**（在部署主机上执行，避免 72MB 包上传）：
   `cd ~/apps/vuln-audit/backend && ~/opt/apache-maven-3.9.9/bin/mvn -B -DskipTests package`
   → `target/vuln-audit-backend-1.0.0-SNAPSHOT.jar`（本次 72,869,597 B）。构建前先把旧 jar 与旧 src 备份到 `~/backups/`。
3. **手工 DDL**：`MYSQL_PWD=<pwd> mariadb -uvulnapp vuln_audit < deploy/manual-ddl.sql`
   （三段：notifications.type 扩宽；tool_versions 版本号/文件字段 + 唯一索引；document_versions 唯一索引）。
   执行前 `mysqldump` 备份（本次 38 KB）。
4. **时区**：`~/.config/systemd/user/vuln-audit-backend.service` 的 `[Service]` 段加
   `Environment=TZ=Asia/Shanghai` → `systemctl --user daemon-reload && systemctl --user restart vuln-audit-backend`。
   验证：`Started VulnAuditApplication` 日志时间戳由 UTC 变为 `+08:00`。
5. **静态资源**：`SUDO_PASS='<sudo 口令>' bash deploy/sync-dist.sh`。
   **只同步前端 dist 不会生效**，后端 jar 必须重新构建并重启。
6. **RBAC**：首次启动日志出现
   `RBAC 权限点初始化完成: 新增权限点=90, 刷新权限点=0, 新增角色=0, 新增角色权限=261, 权限点总数=90`；
   连续重启两次 `permissions=90 / role_permissions=261 / roles=4` 不变（幂等）。

### 18.2 本次结果

| 项 | 结果 |
|---|---|
| 冒烟 `scripts/smoke.sh`（92 例） | **92 PASS / 0 FAIL / 0 SKIP** |
| 前轮核对项 11a~11k | **13/13 PASS**（recipients 字段、type 50/51、TZ、环境 503、分组删除、审计 403/筛选/BOM、页面产物、排行趋势、工具版本） |
| 数据清理 | 20 个残留分组（13 回归/调试/诊断/空分组 + 7 冒烟）逐个 DELETE 均 200；8 个冒烟/乱码资产清理；notification_settings 3→0；演练审批范围 1 条；资产表剩 3 条示例 |

### 18.3 已知偏离

1. **未引入 spring-boot-starter-actuator**：`/actuator/health` 返回 500（`NoResourceFoundException`）。
   健康检查请用 `curl -o /dev/null -w '%{http_code}' http://127.0.0.1:8088/v3/api-docs`（期望 200）或直接调登录接口。
2. **工具版本并发 409 难以自然触发**：t28 的行级锁把版本号分配串行化，实测 3 并发上传得到 201/201/201
   且 versionNo 唯一（无 500）；409 语义由 `ToolVersionConcurrencyTest` 单测覆盖。
3. **`DELETE /api/v1/assets/{id}` 是软删除**（置 archived），要真正移除需按 id 清理关联表后再删主表。

### 18.4 重置方式

```bash
# A. 恢复到本次部署前（有备份时）
ls ~/backups/                       # vuln_audit_pre_t13_*.sql.gz / backend_*.jar / backend_src_*.tar.gz
gunzip -c ~/backups/vuln_audit_pre_t13_*.sql.gz | MYSQL_PWD='<pwd>' mariadb -uvulnapp vuln_audit
systemctl --user restart vuln-audit-backend

# B. 只清业务数据（保留 users/roles/permissions/role_permissions）
export MYSQL_PWD='<pwd>'
mariadb -uvulnapp vuln_audit <<'SQL'
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE vulnerabilities; TRUNCATE TABLE vulnerability_tags; TRUNCATE TABLE vulnerability_history;
TRUNCATE TABLE vulnerability_attachments; TRUNCATE TABLE asset_tags; TRUNCATE TABLE asset_groups;
TRUNCATE TABLE asset_group_relations; TRUNCATE TABLE asset_access; TRUNCATE TABLE asset_claims;
TRUNCATE TABLE audit_logs;
SET FOREIGN_KEY_CHECKS=1;
SQL

# C. 重建初始演示数据（团队 + 3 个示例资产）
cd ~/apps/vuln-audit && python3 deploy/seed.py

# D. 前端重新发布（改了前端代码后）
cd frontend && npm run build && SUDO_PASS='<sudo 口令>' bash ../deploy/sync-dist.sh
```

### 18.5 复跑验收

```bash
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh
# 期望：TOTAL=92 PASS=92 FAIL=0 SKIP=0；有 FAIL 时脚本退出码为 1 并列出失败用例名
```

---

## 19. 第十轮完善（高危写端点鉴权收口：团队 CRUD / 环境 CRUD 与部署 / 公告写）

**问题**：验收报告 §2.3.2 的 3 组「高」severity 缺口——这些写端点**既无角色注解也无数据级守卫**，
任何登录用户都能建团队、删环境、触发部署、发全员公告。

### 19.1 授权矩阵（本轮收口）

| 端点 | 角色门槛 | 数据级守卫 |
|---|---|---|
| `POST /api/v1/teams` | `hasAnyRole('ADMIN','PROJECT_LEADER')` | — |
| `PUT /api/v1/teams/{id}` | `hasAnyRole('ADMIN','PROJECT_LEADER') or @accessGuard.canManageTeam(#id)` | 团队长仅可改**自己领导**的团队 |
| `DELETE /api/v1/teams/{id}` | `hasAnyRole('ADMIN','PROJECT_LEADER') and @accessGuard.canManageTeam(#id)` | 团队存在性 + 归属双校验 |
| `POST /api/v1/teams/{id}/members`、`DELETE .../members/{userId}` | `hasAnyRole('ADMIN','PROJECT_LEADER') or @accessGuard.canManageTeam(#id)` | 团队长仅可管理自己团队的成员 |
| `POST/PUT/DELETE /api/v1/environments**`、`/deploy`、`/start`、`/stop`、`POST /snapshots`、`/snapshots/{id}/rollback` | `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')` | —（环境归属 follow 资产，本轮按矩阵先落角色轨） |
| `POST/PUT/DELETE /api/v1/notifications/announcements**` | `hasAnyRole('ADMIN','PROJECT_LEADER')` | —（公告为全站广播） |

**查询类端点保持原状**（团队列表/详情/成员/树、环境列表/详情/快照列表、公告列表/active 对所有登录用户开放）：
否则前端团队树、环境页签、公告栏会白屏。

`AccessGuard#canManageTeam(teamId, authentication)`：admin/project_leader → 任意团队；team_leader →
仅 `teams.leader_id = 当前用户`；团队ID为空 / 解析不到用户 / 其它角色 → **拒绝（fail-closed）**。

### 19.2 顺带修复（公告创建 500）

`system_announcements.published_at` 为 NOT NULL，`NotificationServiceImpl#createAnnouncement` 此前未补默认值，
**任何不传 `publishedAt` 的创建都直接 500**。现补：未传 → `LocalDateTime.now()`（按「立即发布」处理），
`priority` 未传 → `normal`。

### 19.3 状态码与验证

| 场景 | 状态码 |
|---|---|
| 工程师建/改/删团队、加成员、建/删环境、部署、发公告 | **403**（方法级守卫，早于业务校验） |
| 团队长派改/管理**他人**团队 | **403** |
| 团队长改/管理**自己**团队 | **200** |
| admin / project_leader 上述写操作 | **200/201** |
| 上述查询端点对工程师 | **200**（不受写门槛波及） |

注意：`POST /environments/{id}/snapshots` 需要 `name` 参数，**缺参会先返回 400**（参数绑定早于方法级守卫）；
验证 403 时需带全参数。

```bash
cd backend && mvn -B test -Dtest=PrivilegedWriteAuthorizationTest
cd backend && mvn -B test -Dtest=TeamControllerTest,EnvironmentControllerTest,NotificationControllerTest
```

三账号交叉验证：工程师调三个域的写端点 → 全 403 且库中无新增；团队长改自己团队 200、改他人团队 403；
admin 全通；工程师读团队列表/环境详情/公告列表仍 200。

## 20. 第十一轮（t39/t40 修复后的回归、重建部署与冒烟复跑）

时间：2026-10-30 10:02–11:20（Asia/Shanghai）。本次**无新增迁移**（V1…V17 不变），无需手工 DDL。

| 项 | 结果 |
|---|---|
| 后端全量回归（真实工作区） | `mvn -B clean test` → **Tests run: 475, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS（56:54；机器同时有前端构建，耗时高于 t25 的 16:17） |
| 用例增量 | 452（t25 基线）+ **13**（t39 `AssetClaimLifecycleTest`）+ **10**（t40 `PrivilegedWriteAuthorizationTest`）= 475 |
| 后端重建 | 远端 `mvn -B -DskipTests package` → BUILD SUCCESS 9.8s；jar `72,871,064 B`；源码 257 main / 57 test |
| 重启 | `systemctl --user restart vuln-audit-backend` → 6 次重试后 `POST /auth/login` 200；`/v3/api-docs` 200 |
| 线上冒烟 | `BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh` → **TOTAL=108 / PASS=108 / FAIL=0 / SKIP=0** |
| 冒烟增量（16 条） | 认领生命周期（创建/审批/本人完成/状态 completed）、非本人完成 403（管理员亦 403）、管理层强制放弃 200、未认证读 `my/claims` 401/403（不返回全量）、工程师团队/环境/公告写 403 ×4、工程师团队列表/环境列表/公告列表 200 ×3、公告不传 `publishedAt` 200、环境快照缺 `name` 400 |
| 前端 | 本次仅后端变更；**dist 未同步**，前端 t42/t43 完成后由后续任务统一 build + `sync-dist.sh`（避免部署到不含新前端改动的产物） |
| 备份 | `~/backups/backend_20261030_*.jar`（旧 jar）、`backend_src_20261030_*.tar.gz`（旧源码） |

后端侧三账号交叉验证（冒烟覆盖）：工程师 → 团队/环境/公告写 403、查询 200、认领只能完成自己的、`my/claims` 未认证被拒；
组长 → 他人已批准认领可强制放弃 200；管理员 → 非认领人完成 403（t39 语义：完成仅本人）、其余管理面 200。

```bash
# 复跑
cd backend && mvn -B clean test          # 期望 475 passed
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 108/108
```
