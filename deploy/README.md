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

---

## 21. 第十二轮（用户端点 PII 收敛 + 认证/授权状态码语义修正，t45）

**问题**（验收报告 §2.3.2 原 #13 + §5 偏离 8）：
1) `GET /users`、`GET /users/{id}`、`GET /users/{id}/roles`、`GET /users/check-username|email` **仅需登录即可读**，
   而权限矩阵 2.2 规定工程师不可读用户管理数据 → 任意登录用户可批量枚举用户、邮箱、手机号、角色、最后登录时间（PII 面）；
2) 未配置 `AuthenticationEntryPoint`，**未认证请求统一返回 403**，客户端无法区分「需要重新登录」与「确实无权限」。

### 21.1 授权矩阵（用户域）

| 端点 | 角色门槛 | 说明 |
|---|---|---|
| `GET /api/v1/users/directory` | **仅需登录（全员可用）** | 最小暴露目录：**只返回 `id / username / fullName`**（专用 record `UserDirectoryEntry`），不含 email/phone/status/roles/lastLoginAt；支持 `keyword`（只匹配 username/fullName）与 `limit`（默认 200，上限 500）；供任务负责人下拉、审批人展示、知识库作者名等 |
| `GET /api/v1/users` | `hasAnyRole('ADMIN','PROJECT_LEADER')` | 用户列表（分页 + keyword/status 过滤） |
| `GET /api/v1/users/{id}` | 同上 | 用户详情（响应仍不含 `passwordHash`，t22 的 `@JsonIgnore` 回归） |
| `GET /api/v1/users/{id}/roles` | 同上 | 用户角色列表 |
| `GET /api/v1/users/check-username`、`check-email` | 同上 | 存在性检查（用户管理场景） |
| `GET /api/v1/users/me/permissions`、`GET /api/v1/auth/me` | **仅需登录** | 不能把自己锁死 |

> 字段级兜底：目录接口用专用 record 而不是返回 `User` 实体，实体新增字段也不会意外泄漏；
> 管理视角的详情复用 `User`（`passwordHash` 由 `@JsonIgnore` 保证不外泄）。

### 21.2 认证 / 授权状态码语义

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 未携带 token / token 无效 / 过期 | 403 | **401**（`AuthenticationEntryPoint`，统一 JSON `{"success":false,"code":401,...}`） |
| 已认证但角色或数据级校验不通过 | 403 | 403（`AccessDeniedHandler`，统一 JSON `{"success":false,"code":403,...}`） |

### 21.3 前端联动（必须同步，否则下拉会 403）

以下页面当前用 `getUserList()`（`GET /users`）做人员下拉，需改调 `GET /users/directory`：
`approval-scopes`、`assets/Detail`、`assets/AssetTagsPanel`、`audit-logs`、`knowledge`、`tasks`、`teams`、`tools`、
`vulnerabilities/index`、`vulnerabilities/Detail`（共 10 处）；`pages/users`（账号管理页）继续用 `GET /users`（管理员页面）。

### 21.4 验证

```bash
cd backend && mvn -B test -Dtest=UserDirectoryAuthorizationTest
cd backend && mvn -B test -Dtest=UserControllerTest
```

三账号交叉验证：
- 工程师：`/users`、`/users/{id}`、`/{id}/roles`、`check-*` → **403**；`/users/directory`、`/users/me/permissions` → **200** 且响应不含 email/phone/roles/status/lastLoginAt/passwordHash；
- admin / project_leader：上述全部 → **200**（详情不含 `passwordHash`）；
- 未认证：任意受保护端点 → **401**（不再是 403）；无效 token → **401**。

---

## 22. 第十三轮（资产可见性口径统一：列表与详情同源，t46）

**问题**：`GET /assets` 列表此前**完全不做范围过滤**（任何登录用户看到全部资产），而 `GET /assets/{id}` 走
`AccessGuard.canReadAsset` 的成员判定。两者不同源，导致「列表里能看到的资产，点进去 403」——
t44 页面实测：工程师列表 5 个资产里 3 个详情 403，用户点列表项直接掉进错误页。

**修复**：可见性收敛为**单一事实来源** `AccessGuard.visibleAssetIds(auth)`：

| 位置 | 改动 |
|---|---|
| `AccessGuard.canReadAsset` | 直接实现为 `visibleAssetIds(...).contains(assetId)`；管理层 `null` = 不限制（不存在资源交由控制器返回 404） |
| `GET /assets`（列表） | 控制器取 `visibleAssetIds` 传给 `AssetService.findAllWithDetails(..., visibleAssetIds)`；`null` = 不限制，**空集合直接返回空页**（不生成 `IN ()`） |
| `canWriteAsset` | **写 ⊂ 读**：先要求 `canReadAsset` 通过，再判「管理层 or 资产负责人」 |
| `canReadAssetFile` / `canReadDeliverable` / `canWriteAssetFile` / `canWriteDeliverable` | 去掉各自的 `isManager` 短路，全部委托 `canReadAsset` / `canWriteAsset` |
| `canReadVulnerability` / `canWriteVulnerability` | 原本已委托资产口径，保持不变（漏洞列表与详情同源，t21 + t46 断言） |
| `canReadDocument` | 作者/显式授权之外，资产部分仍走 `canReadAsset`（文档有独立 ACL，属合理叠加） |

**语义变化（收窄，不是扩大）**：
1. 列表从「全部资产」收窄为「负责人 ∪ 已获 approved 授权 ∪ 自己领导团队的资产」——与详情一致；
2. `team_leader` 的写权由「任意资产」收窄为「本团队（自己领导）可见资产」，消除原先「能写但读不到」的不自洽；
3. 普通团队成员仍**不**自动获得团队资产读权（SRS 3.7.3 口径未变）。

**附带修掉的潜在 500**：`AssetServiceImpl.resolveTeamNames/resolveOwnerNames` 在“无团队/无负责人”时返回不可变
`Map.of()`，调用方以 `asset.getTeamId()`（可能为 null）取值会抛 NPE —— 列表收敛后（单条资产、teamId 为空）必然触发；
现改为可接受 null 键的 `HashMap`。

**不变量（关键验收）**：

```bash
cd backend && mvn -B test -Dtest=AssetVisibilityConsistencyTest
cd backend && mvn -B test -Dtest=AssetControllerTest
```

`AssetVisibilityConsistencyTest` 7 例，对 **负责人 / 团队成员 / 无关工程师 / 团队长 / 管理员** 逐个执行
「列表 → 逐条取详情」，断言：

| 断言 | 说明 |
|---|---|
| 列表内每个资产 → `GET /assets/{id}` **200** | 消灭「列表可见、详情 403」 |
| 列表外资产 → 详情 **403** | 防枚举（无关工程师对全部资产 403，且列表为空） |
| 漏洞列表内 → 详情 200；列表外 → 403 | 漏洞侧同源校验 |
| 团队长对越界资产：详情 403 且 `PUT` 也 403 | 写 ⊂ 读 |

**运维/前端注意**：资产列表数量会因角色不同而减少（工程师只看到自己负责/已授权资产），属预期；
前端「我的资产」等页面无需改动；后端无 DDL、无迁移。

## 22. 最终发布（t45 用户 PII/401 + t46 资产可见性统一 + t48 人员目录）

时间：2026-10-30 13:0x–13:4x（Asia/Shanghai）。**无新增迁移**（V1…V17 不变），无需手工 DDL。

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test` → **Tests run: 490, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS（20:58） |
| 用例增量 | 475 + t45 `UserDirectoryAuthorizationTest` **8** + t46 `AssetVisibilityConsistencyTest` **7** = **490** |
| 前端 | `npm run build` → `dist/static/index-BWmznSNU.js` 2,231.44 kB（gzip 667.08 kB）；sync-dist --delete 后站点仅 5 个文件，旧 `index-CRqqK9hb.js` 已清 |
| 后端重建 | 远端 package BUILD SUCCESS 10.9s；jar **72,874,974 B**；restart 后 login=200、api-docs=200、未认证 `/api/v1/users`=**401** |
| 冒烟 | **TOTAL=128 / PASS=128 / FAIL=0 / SKIP=0** |
| 备份 | `~/backups/backend_20261030_*.jar`、`backend_src_20261030_*.tar.gz` |

**t45（用户域）**：`GET /users`、`/users/{id}`、`/users/{id}/roles`、`check-username`、`check-email` → `hasAnyRole('ADMIN','PROJECT_LEADER')`；
新增 `GET /users/directory`（仅登录，返回 `id/username/fullName`，不泄漏 email/phone/roles/status/passwordHash，支持 keyword/limit）；
`AuthenticationEntryPoint`（未认证 401 + 统一 JSON）与 `AccessDeniedHandler`（无权限 403）已生效。
**t46（可见性）**：`AccessGuard.visibleAssetIds` 成为唯一事实来源，列表与详情同源；`canWriteAsset ⊂ canReadAsset`；顺带修列表 NPE。

**冒烟新增 20 条断言**：未认证严格 401、无效 token 401、工程师用户管理读 403×4、`/users/directory` 200 且无 PII×2、
`/auth/me` 与 `/users/me/permissions` 三角色均 200×6、资产可见性不变量（列表内每条详情 200；列表外 403）三账号×6。

**页面补验（Playwright）**：①工程师侧栏无「账号管理」菜单、直连 `/users` 无数据；②工程师资产列表点进详情正常（有 Tab、无 403）；
③任务页人员下拉 5 个选项（目录接口 200）；④`/teams` 与公告区写入口按角色隐藏、管理员全可见。四项全 PASS。

```bash
cd backend && mvn -B clean test                                   # 期望 490 passed
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 128/128
SUDO_PASS='***' bash deploy/sync-dist.sh                          # 前端发布
```

---

## 23. 冒烟残留清理（运维手册）

`scripts/smoke.sh` 每次运行会创建 3 个夹具资产（`冒烟资产` 1 条 + `冒烟导入A/B` 2 条），并通过 `DELETE /api/v1/assets/{id}` 做**软删除**（置 `archived`）。由于脚本没有数据库权限，**软删除的记录会持续累积**，长期运行后资产列表会被历史夹具淹没。

清理方式（把 `AST-YYYYMMDD-` 前缀换成实际运行日期；确认只删夹具、不动真实资产）：

```sql
-- 1) 先看清楚要删什么（必须是冒烟夹具，切勿误删业务数据）
SELECT id, code, name, status FROM assets WHERE code LIKE 'AST-20261030-%';

-- 2) 事务内按 asset_id 清理所有子表，再删主表
START TRANSACTION;
-- 子表清单来自 information_schema（表名不同版本可能增减，执行前请重查）
DELETE FROM asset_metadata_base    WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_metadata_account WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_metadata_network WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_metadata_extra   WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_metadata_env     WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_history          WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_versions         WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_files            WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_deliverables     WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_tags             WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_access           WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_claims           WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_group_relations  WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM asset_subscriptions    WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM vulnerabilities        WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM tasks                  WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM documents              WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM environments           WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE 'AST-20261030-%');
DELETE FROM assets                 WHERE code LIKE 'AST-20261030-%';
COMMIT;

-- 3) 校验：应只剩业务示例资产
SELECT code, status FROM assets ORDER BY created_at;
```

批量生成子表删除语句（避免漏表）：

```sql
SELECT CONCAT('DELETE FROM ', TABLE_NAME, ' WHERE asset_id IN (SELECT id FROM assets WHERE code LIKE ''AST-20261030-%'');')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='vuln_audit' AND COLUMN_NAME='asset_id' AND TABLE_NAME<>'assets';
```

**建议**：每次冒烟后按此清理，或在专用测试库上跑冒烟，避免污染生产实例。

### 23.1 客户 / 项目 / 期次 / 挂靠域（t67 起新增三张表，冒烟每轮必造数据）

`scripts/smoke.sh` 自 t75 起会先建 **客户 ×2 → 项目（分期/不分期）→ 期次 ×2 → 资产（带 projectId+phaseId）→ 挂靠**，
这些数据不会被 API 清理（客户/项目删除受 409 保护），因此需要单独清理。**只删夹具前缀，勿动真实数据**：

| 表 | 夹具识别特征 |
|---|---|
| `customers` | `code LIKE 'SMK-%'`（保留 `CUST-DEFAULT`） |
| `projects` | `code LIKE 'SMK-%'`（保留 `PRJ-HISTORY`） |
| `project_phases` | 属于被删项目的期次 |
| `asset_project_links` | 指向被删资产 / 被删项目的挂靠 |
| `assets` | `name LIKE '冒烟%'`（兼容历史 `smoke%` / `探针%` / `T7%` 前缀） |

```sql
-- 校验：清理前先看清要删什么
SELECT code, name FROM customers WHERE code LIKE 'SMK-%';
SELECT code, name FROM projects  WHERE code LIKE 'SMK-%';
SELECT id, name FROM assets WHERE name LIKE '冒烟%';

-- 清理（外键关闭仅限本次会话，脚本记录在 scripts/qa 之外，仅供手工执行）
SET FOREIGN_KEY_CHECKS = 0;
DELETE l FROM asset_project_links l JOIN assets a   ON a.id = l.asset_id
 WHERE a.name LIKE '冒烟%' OR a.name LIKE 'smoke%' OR a.name LIKE '探针%' OR a.name LIKE 'T7%';
DELETE l FROM asset_project_links l JOIN projects p ON p.id = l.project_id
 WHERE p.code LIKE 'SMK-%';
DELETE FROM asset_tags          WHERE asset_id IN (SELECT id FROM assets WHERE name LIKE '冒烟%');
DELETE FROM asset_versions      WHERE asset_id IN (SELECT id FROM assets WHERE name LIKE '冒烟%');
DELETE FROM vulnerabilities     WHERE asset_id IN (SELECT id FROM assets WHERE name LIKE '冒烟%');
DELETE FROM tasks               WHERE asset_id IN (SELECT id FROM assets WHERE name LIKE '冒烟%');
DELETE FROM environments        WHERE asset_id IN (SELECT id FROM assets WHERE name LIKE '冒烟%');
DELETE FROM documents           WHERE asset_id IN (SELECT id FROM assets WHERE name LIKE '冒烟%');
DELETE FROM assets              WHERE name LIKE '冒烟%' OR name LIKE 'smoke%' OR name LIKE '探针%' OR name LIKE 'T7%';
DELETE FROM project_phases      WHERE project_id IN (SELECT id FROM projects WHERE code LIKE 'SMK-%');
DELETE FROM projects            WHERE code LIKE 'SMK-%' AND code <> 'PRJ-HISTORY';
DELETE FROM customers           WHERE code LIKE 'SMK-%' AND code <> 'CUST-DEFAULT';
SET FOREIGN_KEY_CHECKS = 1;

-- 校验（终态）：customers=1(CUST-DEFAULT) / projects=1(PRJ-HISTORY) / assets=3 / links=3
SELECT (SELECT COUNT(*) FROM customers)           AS customers,
       (SELECT COUNT(*) FROM projects)            AS projects,
       (SELECT COUNT(*) FROM project_phases)      AS phases,
       (SELECT COUNT(*) FROM assets)              AS assets,
       (SELECT COUNT(*) FROM asset_project_links) AS links;

-- 硬断言【必须为 0】：无挂靠资产
SELECT COUNT(*) AS orphan_assets FROM assets a
WHERE NOT EXISTS (SELECT 1 FROM asset_project_links l WHERE l.asset_id = a.id);
```

> t81 实测终态：`assets=3 / customers=1 / projects=1 / phases=0 / links=3 / orphan=0`。
---

## 24. 第十三轮（资产域 6 组鉴权缺口收口，t49）

**问题**（验收报告 §2.3.2 资产域 #1–#6）：建资产、加资产版本、切换默认/最新版本、读访问申请明细、
删除资产标签、资产分组增删改 —— 全部「仅登录即可」，工程师既可建资产、也可改他人资产版本、删他人标签、
读他人访问申请（含申请说明）。

### 24.1 授权矩阵（资产域）

| 端点 | 角色门槛 | 数据级守卫 |
|---|---|---|
| `POST /api/v1/assets` | `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')` | `@accessGuard.canCreateAssetInTeam(#request.teamId)`：admin/PL 不限；team_leader 的 `teamId` 必须是**自己领导或所属**的团队 |
| `POST /api/v1/assets/{assetId}/versions` | 仅登录（写权来自资产） | `canWriteAsset(#assetId)` |
| `PUT /api/v1/assets/{assetId}/versions/{id}/default`、`/latest` | 同上 | `canWriteAsset(#assetId)` |
| `GET /api/v1/assets/{id}/access-requests` | 仅登录 | `canReadAsset(#id)` **且** `canSeeAssetAccessDetails(#id)`（负责人 / 管理层 / 该资产申请的审批人或评审人） |
| `DELETE /api/v1/assets/{assetId}/tags/{tagId}` | 仅登录 | `canWriteAsset(#assetId)` |
| `POST /api/v1/asset-groups`、`PUT/DELETE /api/v1/asset-groups/{id}`、`POST/DELETE /api/v1/asset-groups/{id}/assets` | `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')` | —（分组为展示用元数据） |
| `GET /api/v1/asset-groups**`（列表/详情/类型/组内资产） | **全员开放（未变）** | 前端分组页依赖 |

**新增守卫**：`AccessGuard#canCreateAssetInTeam(teamId, auth)`（team_leader 必须显式指定自己领导/所属团队，
未指定或越界一律拒绝，fail-closed）、`AccessGuard#canSeeAssetAccessDetails(assetId, auth)`（AND 语义：
先过读范围，再看是否负责人/管理层/审批人 —— 审批人身份不能越过读范围）。

### 24.2 状态码（资产域）

| 场景 | 状态码 |
|---|---|
| 工程师建资产 / 加他人资产版本 / 切默认版本 / 读他人申请明细 / 删他人标签 / 任意分组写 | **403** |
| team_leader 在他人团队下建资产 | **403** |
| team_leader 建资产未传 `teamId` | **400**（`@NotNull` 参数校验先于方法级守卫；两条路径都拒绝） |
| 资产负责人写自己资产（版本/默认/标签）与读自己资产的申请明细 | **200/201** |
| admin / project_leader 上述全部 | **200/201** |

### 24.3 验证

```bash
cd backend && mvn -B test -Dtest=AssetDomainAuthorizationTest
cd backend && mvn -B test -Dtest=AssetGroupControllerTest,AssetVersionControllerTest
```

三账号交叉验证：工程师 6 组写操作全 403 且分组查询仍 200；team_leader 本团队 201 / 跨团队 403；
资产负责人可写自有资产；admin 全通。

> 遗留（未在本轮处理）：`POST /api/v1/assets/{id}/tags`（新增标签）仍为「仅登录」——与 #5 的删除标签属同类写权，
> 建议下一轮与 `POST /assets/{id}/access-requests`（创建申请，本身是普通用户动作）一并复核。

---

## 25. 第十四轮（知识库/通知/任务 4 组鉴权缺口收口，t50）

验收报告 §2.3.2 剩余缺口中的协作域 4 组已收口，缺口由 **6 组降至 2 组**（余：工具写权、团队成员列表）。

### 25.1 授权矩阵（协作域）

| 端点 | 规则 | 说明 |
|---|---|---|
| `DELETE /knowledge/{id}` | `@accessGuard.canDeleteKnowledge(#id, authentication)` | 管理层（admin/PL/team_leader）**或作者本人**；他人文章 403 |
| `PUT /knowledge/{id}` | 仅登录（**未改动**） | 矩阵 2.10 允许全角色协作编辑，避免打坏前端 |
| `POST /notifications/vulnerability-discovered` | `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')` | 见 25.2 取舍说明 |
| `GET /notifications/{id}/recipients` | `@accessGuard.canReadNotificationRecipients(...)` | admin/PL 全量；其余**仅接收人本人**；发送者与其他 403 |
| `POST /tasks` | `hasAnyRole(ADMIN,PROJECT_LEADER,TEAM_LEADER)` **and** `@accessGuard.canWriteAssetOrAbsent(#request.assetId, ...)` | 不带资产的任务放行；带资产需资产写权 |
| `POST /tasks/{id}/submit-review` | `@accessGuard.canSubmitTaskReview(#id, authentication)` | 负责人 / 协作者 / 管理层；服务层同步移除「仅 assignedTo」硬编码 |

查询类端点（知识库列表/详情、通知列表、任务列表）**保持现状**，不影响前端。

### 25.2 #8 的取舍（为什么不做「自己为发送者 + 收件人可见性交集」）

`POST /notifications/vulnerability-discovered` 本质是**群发广播**（`recipientIds` 可为任意用户列表），与漏洞归属无耦合。
按「发送者=自己 + 收件人对该漏洞可见」放行后，任意工程师仍能对**自己可见**的漏洞向任意收件人群发，
骚扰/钓鱼面并未消除，只是把范围从「全部漏洞」缩到「自己可见漏洞」；同时会新增逐收件人可见性交集计算与 N 次查询。
因此选择**角色门槛（管理层）**，与 §2.3.2 建议「限管理层或系统内部调用」一致，且便于审计追责。

### 25.3 顺带修复

- `TaskServiceImpl.submitForReview` 原先硬编码「仅任务负责人可提交」，导致管理层代办与协作者提交恒 403；
  现在授权统一在 `@PreAuthorize(canSubmitTaskReview)` 收敛，服务层只保留「操作者必填 + 审计结论必填 + 状态机」校验。
- `TaskControllerTest.createTask_Success` 原先用随机 `assetId`（等于无权资产），t50 后正确返回 403；
  测试改为先建「组长自己的资产」再建任务（**修测试不放宽校验**）。

### 25.4 状态码

| 场景 | 状态码 |
|---|---|
| 工程师删他人文章 / 发漏洞通知 / 读他人投递记录 / 建任务 / 非负责人提交审核 | **403** |
| 工程师删自己文章 | **200** |
| 接收人本人读自己的投递记录、admin 读任意 | **200** |
| team_leader 在自己的资产上建任务 | **201** |
| 带他人资产建任务（虽为管理层） | **403** |

### 25.5 验证

```bash
cd backend && mvn -B test -Dtest=CollaborationDomainAuthorizationTest
cd backend && mvn -B test -Dtest=TaskControllerTest,KnowledgeControllerTest,NotificationControllerTest
```

`CollaborationDomainAuthorizationTest` 15 例（#7 删除 4 例、#8 通知 2 例、#9 投递记录 3 例、#10 任务 6 例），
连同三个既有控制器测试共 **57 例全绿**。无需 DDL / 迁移。

---

## 26. 第十五轮（团队读与工具写收口：鉴权矩阵缺口清零，t51）

**问题**（验收报告 §2.3.2 最后 2 组）：
1) `GET /teams/{id}/members`、`GET /teams/{id}/users` 任意登录可读任意团队成员列表（组织架构数据）；
2) `PUT/DELETE /tools/{id}`、`POST /tools/{id}/versions` 任意登录可改删工具、上传版本（矩阵 2.8 有明确分级）。

### 26.1 #11 的处理方式：**收敛到「可见团队」**（不是登记偏离）

理由：成员列表属组织架构数据，且前端只在本团队语境下使用（团队树/成员页签），
跨团队读没有真实需求；`username` 已可通过 `/users/directory` 全员获取，不需要为成员列表额外开口子。

| 主体 | 可见范围 |
|---|---|
| admin / project_leader | 全量 |
| 团队长 / 团队成员 | **自己领导的团队 ∪ 自己所属的团队 ∪ 这些团队的子团队**（团队树展开需要） |
| 其它（含解析不到用户、团队不存在） | **403**（fail-closed） |

实现：`AccessGuard#canReadTeam(teamId, auth)`；子团队用 `findByParentId` 逐层下溯（**不**用团队仓储里的
native 递归 CTE —— 该 CTE 在 H2 测试库不可用，会在方法级守卫里抛异常变 500），层级上限 `MAX_TEAM_DEPTH=20` 防脏数据成环。

### 26.2 #12 工具写

| 端点 | 角色门槛 |
|---|---|
| `PUT /api/v1/tools/{id}` | `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')` |
| `POST /api/v1/tools/{id}/versions` | `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')`（t23 的 `createdBy` 落库逻辑未动，回归用例断言 `createdBy=操作者` 且 `hasFile=true`） |
| `DELETE /api/v1/tools/{id}` | `hasAnyRole('ADMIN','PROJECT_LEADER')` |
| `GET /tools`、`/{id}`、`/{id}/versions`、`/{id}/download`、`/{id}/download-count`、`/{id}/tags` | **全员开放（未变）** |

### 26.3 顺带收口（t49 上报，captain 并入）

`POST /api/v1/assets/{id}/tags`（新增标签）→ `@accessGuard.canWriteAsset(#id)`，与已收口的「删除标签」同一口径。

### 26.4 验证

```bash
cd backend && mvn -B test -Dtest=OrgToolDomainAuthorizationTest
cd backend && mvn -B test -Dtest=TeamControllerTest,ToolControllerTest,AssetControllerTest
```

三账号交叉验证：工程师读自己团队 200 / 读他团队 403、工具改删与版本上传全 403、工具查询与下载 200；
团队长读本团队 200 / 无关团队 403、改工具与上传版本 200（`createdBy` 落库）、删工具 403；admin 全通。

> **鉴权矩阵缺口清零**：验收报告 §2.3.2 的「已知缺口」由 15 组降到 **0 组**（t39/t40/t45/t46/t49/t50/t51 逐批收口）。

---

## 27. 第十六轮（工具域写端点收尾：标签增删与工具包上传，t55）

**问题**（t51 上报的同类遗留）：工具域仍有 3 个写端点「仅需登录」——`POST /tools/{id}/file`（上传工具包）、
`POST /tools/{id}/tags`、`DELETE /tools/{id}/tags`，与已收口的 #12「工具修改权」属同一类。

### 27.1 授权矩阵（工具域写）

| 端点 | 角色门槛 | 备注 |
|---|---|---|
| `PUT /api/v1/tools/{id}` | ADMIN / PROJECT_LEADER / TEAM_LEADER | t51 已收口 |
| `POST /api/v1/tools/{id}/versions` | ADMIN / PROJECT_LEADER / TEAM_LEADER | t51 已收口（createdBy 落库未动） |
| `POST /api/v1/tools/{id}/file` | ADMIN / PROJECT_LEADER / TEAM_LEADER | **t55**；工具不存在 → 404 |
| `POST /api/v1/tools/{id}/tags` | ADMIN / PROJECT_LEADER / TEAM_LEADER | **t55**；工具不存在 → 404 |
| `DELETE /api/v1/tools/{id}/tags` | ADMIN / PROJECT_LEADER / TEAM_LEADER | **t55**；工具不存在 → 404 |
| `DELETE /api/v1/tools/{id}` | ADMIN / PROJECT_LEADER | t51 已收口（删除更严） |
| `GET /tools`、`/{id}`、`/{id}/versions`、`/{id}/tags`、`/{id}/download`、`/{id}/download-count`、`/category/*`、`/popular` | 全员开放（未变） | 查询与下载不得收紧 |

### 27.2 顺带修的真 bug

`ToolServiceImpl.addTag/removeTag` 此前对**不存在的工具**静默成功（写入孤儿标签行 / 删除 0 行返回 200）。
现按资源存在性 fail-closed：工具不存在 → `BusinessException(TOOL_NOT_FOUND)` → **404**。
（工具包上传 `storeToolFile` 原本就有该判断，行为一致。）

### 27.3 验证

```bash
cd backend && mvn -B test -Dtest=ToolWriteAuthorizationTest
cd backend && mvn -B test -Dtest=ToolControllerTest,ToolVersionUploadTest,OrgToolDomainAuthorizationTest
```

三账号交叉验证：工程师三个写端点 **403**；团队长/管理员 加标签 200、删标签 200、上传工具包 200
（`fileName/fileSize` 有值、下载字节与上传一致）；工具不存在时三个写端点 **404**；
工程师对工具列表/详情/版本/标签/下载计数仍 **200**。

## 28. 第二批发布（t49/t50/t51/t55 鉴权收口 + t52/t53/t56 前端对齐与 i18n）

时间：2026-10-30 17:5x–19:0x（Asia/Shanghai）。**无新增迁移**（V1…V17 不变）。

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test` → **Tests run: 524, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS（26:51）；`@Disabled/@Ignore` 0 命中 |
| 用例增量 | 第一批 490 + t49 `AssetDomainAuthorizationTest` 7 + t50 `CollaborationDomainAuthorizationTest` 15 + t51 `OrgToolDomainAuthorizationTest` 7 + t55 `ToolWriteAuthorizationTest` 5 = **524** |
| 前端 | `npm run build` → `dist/static/index-D2WA8xPO.js` 2,256.33 kB（gzip 674.73 kB），3697 modules；产物含中英双套文案 |
| 后端重建 | 远端 package BUILD SUCCESS 10.7s，jar **72,877,443 B**；重启后 login=200、api-docs=200 |
| 前端发布 | sync-dist --delete 后 `/var/www/vuln-audit` 仅 5 个文件（`index-D2WA8xPO.js` + css + 3 个静态资源），旧 hash 已清 |
| 冒烟 | **TOTAL=154 / PASS=154 / FAIL=0 / SKIP=0** |
| 备份 | `~/backups/backend_20261030_*.jar`、`backend_src_20261030_*.tar.gz` |

**鉴权缺口清零（15 → 0）**：t49 资产域 6 组、t50 协作域 4 组、t51 组织/工具域 2 组、t55 工具域追加 3 个写端点（工具包上传 / 标签增删）。
**本轮新回归点（冒烟常态化）**：工程师 6 组资产域写 403、分组写 403、协作域 4 条 403、组织工具域 6 条 403；查询（资产/知识库/通知/任务/工具）对工程师 200；工具不存在时加标签 404。

**回归期修的两处测试契约（未放宽校验）**
1) `NotificationTypeLengthTest` 3 例改用 adminToken —— t50 已把 `POST /notifications/vulnerability-discovered` 收敛为 ADMIN/PL/TEAM_LEADER；
2) `PrivilegedWriteAuthorizationTest#readEndpoints_stillOpenForEngineer` 的 `GET /teams/{id}/members` 断言改为「团队长 200 / 非成员工程师 403」—— t51 的可见团队口径。

**前端（t52/t53/t56）**：入口可见性与后端门槛对齐（工程师无建资产/建分组/建任务入口，工具上传 disabled、删除不渲染；团队长/管理员逐级开放）；工具标签抽屉非管理层只读；i18n 首批覆盖登录/导航/通用组件/通知/仪表盘，中英切换即时生效、刷新保持、`<html lang>` 同步。

```bash
cd backend && mvn -B clean test                                   # 期望 524 passed
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 154/154
SUDO_PASS='***' bash deploy/sync-dist.sh                          # 前端发布
```

---

## 29. 第十七轮（四类资源标签写权限统一，t61）

**问题**：同一个「标签」概念口径不一致——资产标签 t49 已按资产写权收敛、工具标签 t55 已限管理层，
但**知识库文章标签与文档标签仍对全员开放**（任何登录用户可增删他人内容的标签）。

### 29.1 统一规则（四类资源一张表）

| 资源 | 写标签所需权限 | 守卫实现 | 说明 |
|---|---|---|---|
| 资产 | **资产写权**（负责人 或 管理层） | `@accessGuard.canWriteAsset(#assetId)` | t49；标签影响资产检索与分组 |
| 工具 | **管理层**（ADMIN / PROJECT_LEADER / TEAM_LEADER） | `hasAnyRole(...)` | t55；工具库是共享资产 |
| 知识库文章 | **作者本人 或 管理层** | `@accessGuard.canWriteKnowledgeTags(#id)` | **t61**；文章属个人产出，协作走 `PUT /knowledge/{id}` |
| 文档 | **作者本人 或 管理层** | `@accessGuard.canWriteDocumentTags(#id)` | **t61**；文档作者取 `documents.author_id` |

**为什么知识库/文档不是纯「管理层」**：这两类内容由工程师本人产出、日常需要自己打标签；
但标签会影响全站检索与聚合，所以不能允许任意人改他人内容的标签 → 取「作者本人 ∪ 管理层」的折中。
资产与工具则是组织级共享资源，故按「资产写权 / 管理层」。

### 29.2 两条不变量

1) **查询类标签接口保持全员开放**：四类资源的 `GET /{id}/tags` 均未加门槛（资产标签跟随资产读范围，
即 t46 的 `canReadAsset`；看不到的资产其标签也 403，这是资产可见性口径而非标签口径）。
2) **资源不存在 → 404，不用 403**：`canWriteKnowledgeTags` / `canWriteDocumentTags` 对不存在的资源
返回 true，由控制器抛 `KNOWLEDGE_NOT_FOUND` / `DOCUMENT_NOT_FOUND`——避免用 403 泄露资源是否存在。
   顺带修：`POST /knowledge/{id}/tags`、`POST /documents/{id}/tags` 此前对不存在的资源**静默写入孤儿标签行**，现统一 404。

### 29.3 状态码

| 场景 | 状态码 |
|---|---|
| 工程师改他人文章/文档标签（增或删） | **403** |
| 作者本人改自己的文章/文档标签 | 200（知识库）/ 201（文档新增） |
| 管理层改任意文章/文档标签 | 200 / 201 |
| 文章/文档不存在 | **404** |
| 工具标签：工程师 | **403**（t55 不回归） |
| 资产标签：非负责人且非管理层 | **403**（t49 不回归） |

### 29.4 验证

```bash
cd backend && mvn -B test -Dtest=TagWriteAuthorizationTest
cd backend && mvn -B test -Dtest=KnowledgeControllerTest,DocumentControllerTest,ToolWriteAuthorizationTest
```

三账号交叉验证：工程师改他人文章/文档标签 403、改自己 200/201；管理层改他人 200；工具与资产标签口径未回归；
四类资源标签查询对工程师 200（资产标签随资产可见性）。

---

## 30. 第十八轮（后端错误消息 i18n 基础设施，t60）

### 30.1 机制

| 组件 | 作用 |
|---|---|
| `i18n/I18nConfig` | 定义 `MessageSource`（basename `messages`，`defaultEncoding=UTF-8`、`fallbackToSystemLocale=false`、`useCodeAsDefaultMessage=false`） |
| `resources/messages.properties` | 中文默认包（部署机 locale 不影响：未知语言回退中文） |
| `resources/messages_en.properties` | 英文包（缺键自动回退中文包，再回退 `ErrorCode#getMessage()`） |
| `i18n/ApiMessageResolver` | 语言选择顺序：`?lang=` 参数 → `Accept-Language` → 默认中文；提供 `resolve / resolveOrKeep / resolveForErrorCode`，**任何缺失都不抛异常** |
| `ErrorCode#messageKey()` | 错误码 → 消息码（`null` 表示本轮未覆盖，仍输出中文 `message`） |
| `GlobalExceptionHandler` | 所有出口统一经 resolver：业务异常、参数校验（逐字段）、认证/鉴权、404/405/500 |

> 设计取舍：**没有**把 `ErrorCode.message` 直接替换成消息码，而是「消息码 + 中文 message 双字段」。
> 原因：项目里仍有 `ErrorCode.X.getMessage()` 的直接引用（6 处，已全部改为 resolver）与大量“自定义中文文案”的业务异常；
> 双字段能保证「未迁移的错误码/文案一字不变」，同时新增英文能力，回归风险最小。

### 30.2 覆盖范围（四类高频，其余保留中文）

| 域 | 消息码 |
|---|---|
| 认证 | `error.auth.unauthorized`、`error.auth.invalid_credentials`、`error.auth.password_error`、`error.auth.token_invalid` |
| 资产 | `error.asset.not_found`、`error.asset.access_denied` |
| 漏洞 | `error.vulnerability.not_found`、`error.vulnerability.version_not_in_asset`、`error.vulnerability.number_conflict` |
| 任务 | `error.task.not_found`（+ 通用 `error.status.invalid`） |
| 通用 | `error.bad_request`、`error.forbidden`、`error.not_found`、`error.conflict`、`error.internal`、`error.validation`、`error.method_not_allowed` |
| 参数校验 | `validation.auth.*`（LoginRequest）、`validation.asset.*`（AssetCreateRequest） |

**未覆盖（保留中文）**：用户/角色/权限/文档/环境/工具/知识库/通知/团队等错误码；各服务方法内自定义中文文案（如「请先填写审计结论」）；
登录限流提示；上传/请求体解析类兜底文案。列入后续排期。

### 30.3 行为契约

| 请求 | 结果 |
|---|---|
| `Accept-Language: en` | 英文消息（如 `Asset not found` / `Validation failed` + 字段英文提示） |
| `Accept-Language: zh-CN` 或**不传** | 中文消息（与 t60 之前完全一致） |
| `?lang=en` | 覆盖请求头（即使头是 zh-CN） |
| 其他语言（如 `fr-FR`） | 回退中文 |
| 消息码缺失 | 回退 `ErrorCode#getMessage()`（中文），不抛异常、不把 `error.xxx` 原样返回 |

响应结构不变：`{success, code, message, data}`。

### 30.4 验证

```bash
cd backend && mvn -B test -Dtest=ApiMessageI18nTest
cd backend && mvn -B test -Dtest=AuthControllerTest,AssetControllerTest,VulnerabilityControllerTest,TaskControllerTest
```

curl 对照（部署后）：

```bash
# 英文
curl -s -H "Accept-Language: en" http://192.168.188.200:8089/api/v1/assets/00000000-0000-0000-0000-000000000000 \
  -H "Authorization: Bearer $TOKEN" | grep message      # -> Asset not found
# 中文（不传头）
curl -s http://192.168.188.200:8089/api/v1/assets/00000000-0000-0000-0000-000000000000 \
  -H "Authorization: Bearer $TOKEN" | grep message      # -> 资产不存在
```

`ApiMessageI18nTest` 10 例：404/403/400 的 en 与 zh 对照、`?lang=` 覆盖、认证失败文案、缺失码回退、非消息码文案原样保留、语言归一、未知码不泄漏。

**无 DDL / 无迁移**；`messages*.properties` 随 jar 打包。

> 顺带改动：资产/漏洞/任务详情端点的 404 由控制器内硬编码中文改为抛 `BusinessException`，
> 使文案能经 i18n 解析（响应结构与中文文案均未变）。

---

## 31. 第十九轮（子资源写操作存在性统一，t65）

### 31.1 为什么做这一轮

同类缺陷在本轮被**发现三次**（t55 工具标签、t61 文章/文档标签、更早的工具包上传）：
「路径里带父资源 id，写的是子资源，父资源不存在时静默成功」——写孤儿标签行、删 0 行仍返回 200。
这些不是零散 bug，而是**没有统一约定**导致的系统性问题，因此做一次全量扫描 + 统一守卫。

### 31.2 统一规则与实现

| 规则 | 说明 |
|---|---|
| 父资源不存在 | **404**（不是 200 / 400 / 403） |
| 子资源不属于该父资源 | **404**（与「不存在」同码，防止用状态码枚举资源） |
| 正常路径 | 语义不变（`POST /assets/{id}/tags` 成功仍 201） |

实现集中在 `service/SubresourceGuard`（单点入口，控制器只调一行）：

```java
public void requireAsset / requireVulnerability / requireKnowledgeArticle / requireDocument
           / requireTool / requireTeam / requireEnvironment / requireTask / requireNotification
public AssetTag requireAssetTag(UUID assetId, UUID tagId)              // 存在 + 归属
public VulnerabilityTag requireVulnerabilityTag(...)
public void requireDocumentTag(...) / requireVulnerabilityAttachment(...)
public void requireTeamMember(teamId, userId) / requireEnvironmentSnapshot(envId, snapId)
public void requireNotificationRecipient(notificationId, userId)
```

### 31.3 扫描清单（端点 → 修复前 → 现在）

| 端点 | 修复前行为 | 结论 |
|---|---|---|
| `POST /assets/{id}/tags` | 资产不存在仍写入孤儿标签（管理员守卫放行）→ 201 | **已修 → 404** |
| `DELETE /assets/{assetId}/tags/{tagId}` | `deleteById` 不校验归属：删 0 行返回 200，可删**他人资产**标签 | **已修 → 404**（归属不符也 404） |
| `PUT /assets/{aid}/versions/{vid}/default` | 版本不存在返回 **HTTP 200 + body code 404** | **已修 → HTTP 404** |
| `PUT /assets/{aid}/versions/{vid}/latest` | 同上 | **已修 → HTTP 404** |
| `POST /vulnerabilities/{id}/tags` | 漏洞不存在仍写孤儿标签 → 201 | **已修 → 404** |
| `DELETE /vulnerabilities/{vid}/tags/{tagId}` | 可删**其他漏洞**的标签，删 0 行 200 | **已修 → 404** |
| `POST /vulnerabilities/{id}/attachments` | 漏洞不存在仍落库附件并创建上传目录 | **已修 → 404** |
| `DELETE /vulnerabilities/{vid}/attachments/{aid}` | 可删**其他漏洞**附件，不存在也 200 | **已修 → 404** |
| `GET /vulnerabilities/{id}/attachments/{aid}/download` | 已在 t15 校验归属 → 404 | 已正确 404 |
| `POST /knowledge/{id}/comments` | 文章不存在仍写孤儿评论 → 200 | **已修 → 404** |
| `POST /knowledge/{id}/tags`、`DELETE /knowledge/{id}/tags` | t61 已加 `requireArticleExists` | 已正确 404 |
| `POST /documents/{id}/tags` | t61 已加 `requireDocumentExists` | 已正确 404 |
| `DELETE /documents/{docId}/tags/{tagId}` | 可删**其他文档**标签，任意 tagId 都 200 | **已修 → 404** |
| `POST /documents/{id}/versions/{version}/rollback` | t37 已校验目标版本 → 404 | 已正确 404 |
| `POST /tools/{id}/file` | t23/t55 已校验工具存在 → 404 | 已正确 404 |
| `POST /tools/{id}/tags`、`DELETE /tools/{id}/tags` | t55 已加 `existsById` → 404 | 已正确 404 |
| `POST /tools/{id}/versions` | 服务层校验工具存在 → 404 | 已正确 404 |
| `POST /teams/{id}/members` | `TeamServiceImpl` 校验团队与用户 → 404 | 已正确 404 |
| `DELETE /teams/{id}/members/{userId}` | 团队不存在仍 200；非成员删 0 行仍 200 | **已修 → 404** |
| `POST /notifications/{id}/read` | 通知不存在 / 非接收人静默 no-op → 200 | **已修 → 404** |
| `DELETE /notifications/{id}` | 同上 | **已修 → 404** |
| `POST /environments/{id}/snapshots` | 服务层校验环境存在 → 404 | 已正确 404 |
| `POST /environments/{id}/snapshots/{sid}/rollback` | 快照不属于该环境返回 **400** | **已修 → 404** |
| `POST /tasks/{id}/submit-review` | 服务层 `TASK_NOT_FOUND` → 404 | 已正确 404 |
| `POST /tasks/{id}/comments` | 控制器显式 404 | 已正确 404 |
| `POST /tasks/{id}/attachments` | 任务不存在仍写附件行 → 200 | **已修 → 404** |
| `POST /assets/{id}/access-requests` | 资产不存在仍写访问申请 → 201 | **已修 → 404** |
| `POST /assets/{id}/claims`、`/claims/{claimId}/*` | claim 自身按 claimId 定位，父资产不存在由 `canReadAsset` 拦成 403（不泄露） | 保持现状（非本类缺陷） |
| `GET /*/tags` 等查询类 | 只读，不做存在性拦截 | 不涉及 |

### 31.4 顺带修掉的真实缺陷（返回 500 的坑）

`GlobalExceptionHandler` 的业务码区间上限写成了 `< 2000`，团队域错误码（2001/2002/2003）落到 else 分支，
被 clamp 成 `599` 后 `HttpStatus.valueOf(599)` 抛 `IllegalArgumentException` → **500**（团队不存在本该 404）。
现改为 `< 3000` 覆盖团队域，并把 else 分支换成 `HttpStatus.resolve(...)` + 兜底 400，**任何非法码都不再抛异常**。

### 31.5 验证

```bash
cd backend && mvn -B test -Dtest=SubresourceExistenceTest
cd backend && mvn -B -DskipTests compile
```

`SubresourceExistenceTest` 16 例（自建脚手架，不依赖 `BaseTest` 事务夹具）：每个修复点「父不存在 → 404」「子不属于父 → 404」，
并保留正常路径断言（201/200）与已正确端点防回归；另断言孤儿行**未被写入**（`findByAssetId(MISSING).isEmpty()`）。

同步修测试（未放宽校验）：`NotificationControllerTest` 夹具补「工程师也是接收人」（用例本来就用 engineerToken 调用）；
`TagWriteAuthorizationTest` 里「删除随机 tagId → 200」改为「→ 404 + 删除真实标签 → 200」。

**无 DDL / 无迁移。**

## 32. 第三批发布（t60 后端消息 i18n + t61 标签权限统一 + t58/t59/t62/t64/t66 前端 i18n 二期）

时间：2026-11-02 11:5x–13:2x（Asia/Shanghai）。**无新增迁移**（V1…V17 不变），无需手工 DDL。

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test` → **Tests run: 557, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS；`@Disabled/@Ignore` 0 命中 |
| 用例增量 | 第二批 524 + t60 `ApiMessageI18nTest` 10 + t61 `TagWriteAuthorizationTest` 7 + t65 `SubresourceExistenceTest` 16 = **557**；`ToolVersionConcurrencyTest` 在全量中通过 |
| 前端 | `npm run build` → 3701 modules，`index-BZouiz1c.js` 2,391.75 kB（gzip 701.34 kB）；词条 **553/553**（中英对等）；31/33 tsx 使用 `useT` |
| i18n 残留 | `grep -rn "TODO(i18n)" frontend/src` → **1 处**：`pages/notifications/SettingsTab.tsx` |
| 后端重建 | 远端 package BUILD SUCCESS 14.4s，jar **72,890,833 B**；重启后 login=200、api-docs=200；`messages.properties` / `messages_en.properties` 已随包 |
| 前端发布 | sync-dist --delete 后 `/var/www/vuln-audit` 仅 5 个文件（`index-BZouiz1c.js`），旧 hash 已清 |
| 冒烟 | **TOTAL=167 / PASS=167 / FAIL=0 / SKIP=0**（新增 13 条） |

**后端消息 i18n（t60）**：`?lang=` → `Accept-Language` → 默认中文；线上实测
`Accept-Language: en` → `You do not have permission to access this resource` / 默认 → `无权限访问该资源`；
管理员取不存在资产：en → 英文 404、默认 → 「不存在」。

**标签写权限（t61）**：资产 = 资产写权；工具 = 管理层；知识库/文档 = 作者本人或管理层；`GET /{id}/tags` 四类均保持全员可读；父资源不存在一律 404（t65）。

**回归期修测试契约（未放宽校验）**：`AssetVersionControllerTest#setDefaultVersion_NotFoundWhenVersionNotBelongs` —— t65 把「版本不属于资产」由 `200 + body 404` 改为真 404，断言同步 `isNotFound()`。

**冒烟新增 13 条**：i18n 404 英/中（管理员）、403 英/中（工程师用户域）、越权访问 403 英文；知识库标签（他人 403 / 作者 2xx / 管理员 2xx / 不存在 404）；文档标签（他人 403 / 作者 201 / 管理员 201）；标签查询仍开放。

```bash
cd backend && mvn -B clean test                                   # 期望 557 passed
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 167/167
curl -s -H 'Accept-Language: en' http://127.0.0.1:8089/api/v1/users            # 期望英文 401
```

---

## 33. 第二十轮（客户 / 项目 / 期次 / 资产挂靠 领域模型 + V18 迁移，t67）

第一批只落**领域模型与迁移**，不暴露 API；前端与接口留到下一批。

### 33.1 为什么不用 `assets.project_id` 单值外键

任务书要求「`Asset` 不加 projectId」，这里记录判断依据：

1. **业务是集合而非归属**：同一资产（域名/系统）常同时服务多个客户或连续多个项目（售前 POC、年度复测、多客户共用一套靶场），单值外键只能表达「此刻属于谁」，会把历史关系覆盖掉；
2. **单值外键与审计语义冲突**：变更归属 = UPDATE，历史挂靠记录消失；而审计平台恰恰需要「这个资产在某年某期次里被谁测过」的可追溯链；
3. **挂靠需要自身属性**：`link_type`（TARGET/SHARED/REFERENCE）、`remark`、`linked_by/linked_at`、可选的期次，都不是资产自身的字段；
4. **回填成本**：存量资产一律挂到「历史项目」，若写成外键，等于断言所有历史资产同属一个项目，后续再拆需要额外迁移数据。

因此采用 **`asset_project_links` 多对多关联表**，`Asset` 实体一字未改。

### 33.2 表结构与索引

| 表 | 关键列 | 约束 / 索引 |
|---|---|---|
| `customers` | code(64) name(200) industry level(A/B/C) contact_* owner_id status description created_* | `UNIQUE(code)`；`idx_customers_status`、`idx_customers_owner` |
| `projects` | code(64) customer_id name type(PENTEST/CODE_AUDIT/EMERGENCY/COMPLIANCE/TRAINING/OTHER) status(DRAFT/ACTIVE/PAUSED/CLOSED) manager_id start/end_date is_phased contract_no | `UNIQUE(code)`；`idx_projects_customer`、`idx_projects_manager`、`idx_projects_status` |
| `project_phases` | project_id phase_no name start/end_date status(PLANNED/ACTIVE/DONE) | `UNIQUE(project_id, phase_no)`；`idx_project_phases_project`、`idx_project_phases_status` |
| `asset_project_links` | asset_id project_id phase_id(可空) **phase_key** customer_id(冗余) link_type(TARGET/SHARED/REFERENCE) remark linked_by linked_at | `UNIQUE(asset_id, project_id, phase_key)`；`idx_..._asset/project/phase/customer` |

`customer_id` 在挂靠表里**冗余**：按客户统计（资产数/项目数）不必每次 join `projects`，写入时由业务层从项目带出。

### 33.3 唯一约束的 NULL 方案（要求 5）

问题：MariaDB 的唯一索引**对 NULL 不去重**（多行 NULL 视为互不相同），因此
`UNIQUE(asset_id, project_id, phase_id)` 允许「同一资产 + 同一项目 + 无期次」插入任意多条。

**选定方案：`phase_key` 计算列（应用维护），而不是 MariaDB 生成列**

| 方案 | 结论 |
|---|---|
| MariaDB 生成列 `phase_key ... AS (COALESCE(phase_id, '00000000-...')) STORED` | 语义正确，但 `AS (...) STORED` 是 MariaDB/MySQL 方言，H2（测试库）语法不同 → 迁移脚本无法在测试里原样验证 |
| **`phase_key` 普通列 + `@PrePersist/@PreUpdate` 维护** | 两端行为完全一致；迁移里可显式写入；可写 SQL 复核（`phase_key = phase_id or 哨兵`） |

实现要点：
- `phase_id` 保留 **NULL 语义**（无期次 = NULL，业务含义清晰，前端不用理解哨兵值）；
- `phase_key` NOT NULL，`AssetProjectLink.syncPhaseKey()` 在 persist/update 前写入 `phaseId != null ? phaseId : PHASE_KEY_NONE`；
- 唯一约束建在 `(asset_id, project_id, phase_key)`，于是「项目级」与「具体期次」都各自唯一；
- 测试 `projectLevelLink_isUniqueDespiteNullPhase` 实测：同 (asset, project, null) 第二次插入被拒（`DataIntegrityViolationException`），换成有期次则可并存。

### 33.4 迁移 V18 与回填

`db/migration/V18__customer_project_phase_and_asset_links.sql`：

1. 四张表 + 索引，全部 `CREATE TABLE/INDEX IF NOT EXISTS`；
2. 默认客户 `CUST-DEFAULT`（名「历史数据」，level=C）→ `INSERT ... SELECT ... WHERE NOT EXISTS`；
3. 历史项目 `PRJ-HISTORY`（customer=默认客户、status=ACTIVE、type=OTHER、is_phased=false）→ 同理；
   `manager_id` 取**第一个 admin**（`user_roles ⋈ roles where code='admin' order by user_id limit 1`），
   退化顺序：首个用户 → 全 0 哨兵（满足 NOT NULL，不至于中断迁移）；
4. 存量资产回填：`INSERT INTO asset_project_links SELECT ... FROM assets WHERE NOT EXISTS(...)`，
   `phase_id=NULL`、`phase_key=全 0`、`link_type=SHARED`、`customer_id=默认客户`。

**幂等性**：所有建表/建索引 `IF NOT EXISTS`；种子与回填都是 `WHERE NOT EXISTS`；配合唯一索引构成双保险。
不使用窗口函数，全部语句为 MariaDB 10.x 与 H2 共同支持的标准子集（因此测试能直接执行真实脚本，而不是另写一份 SQL）。

### 33.5 验证

```bash
cd backend && mvn -B -DskipTests compile
cd backend && mvn -B test -Dtest=CustomerProjectModelTest
```

`CustomerProjectModelTest` 7 例：
- 空库执行迁移 → 默认客户/历史项目就位，**脚本连跑两次**不报错不重复；
- 3 个存量资产 → 各 1 条历史挂靠（project/phase/customer/link_type/phase_key 逐项断言）；
- 迁移前已有业务挂靠 → 不覆盖、只补缺失；新资产再跑一次只补新行；
- 仓储查询：`findByCustomerId`/`findByManagerId`/`findByProjectIdOrderByPhaseNo`/`findByAssetId`/`findByProjectId`/`findByProjectIdAndPhaseId`（含 `phaseId=null` 的项目级语义）/`existsByAssetIdAndProjectIdAndPhaseId`/`findByCustomerId`；
- 唯一约束三级验证：挂靠 `phase_key`、期次 `(project_id, phase_no)`、客户/项目 `code`。

> 迁移用例不使用 `@Transactional`：脚本由独立连接执行，必须能看见已提交的资产数据；
> 用例前后用 `wipeModelData()` 清空四张新表与 `code LIKE 'T67-%'` 的测试资产，避免污染其它 suite。

**生产执行注意**：V18 为纯新增（不 ALTER 既有表），可在业务运行期执行；
`SPRING_FLYWAY_ENABLED=false` 的部署需手动执行该脚本（或临时开启 Flyway），重复执行安全。

---

## 34. 第二十一轮（客户 / 项目 / 期次 API：CRUD + 状态机 + 删除保护 + 权限 + 审计，t68）

### 34.1 接口清单

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/customers` | 分页 + `keyword`/`status`/`level` 筛选（size 上限 500） | 全员 |
| GET | `/api/v1/customers/{id}` | 详情 + `projectCount` / `activeProjectCount` / `assetCount` | 全员 |
| POST | `/api/v1/customers` | 创建（`code` 全局唯一） | ADMIN / PROJECT_LEADER |
| PUT | `/api/v1/customers/{id}` | 更新 | 同上 |
| PATCH | `/api/v1/customers/{id}/status?status=` | active ↔ inactive | 同上 |
| DELETE | `/api/v1/customers/{id}` | 删除（有项目 → 409） | 同上 |
| GET | `/api/v1/projects` | 分页 + `customerId`/`status`/`type`/`keyword` | 全员 |
| GET | `/api/v1/projects/{id}` | 详情 + `customer`(brief) / `phases` / `assetCount` | 全员 |
| POST | `/api/v1/projects` | 创建（初始 DRAFT） | ADMIN / PROJECT_LEADER |
| PUT | `/api/v1/projects/{id}` | 更新（含关闭分期的约束） | ADMIN/PL 或该项目经理 |
| PATCH | `/api/v1/projects/{id}/status?status=` | 状态机流转 | 同上 |
| DELETE | `/api/v1/projects/{id}` | 删除（有挂靠 → 409；同期清理期次） | 同上 |
| GET | `/api/v1/projects/{projectId}/phases` | 期次列表（phaseNo 升序） | 全员 |
| POST | `/api/v1/projects/{projectId}/phases` | 创建期次（仅分期项目） | ADMIN/PL 或该项目经理 |
| PUT | `/api/v1/phases/{phaseId}` | 更新期次 | ADMIN/PL 或所属项目经理 |
| DELETE | `/api/v1/phases/{phaseId}` | 删除期次（有挂靠 → 409） | 同上 |

### 34.2 项目状态机

```
        ┌────────────► CLOSED（终态，不可再流转）
        │                  ▲
DRAFT ──► ACTIVE ──► PAUSED│
            ▲  │            │
            └──┘            │
            └───────────────┘
```

| 当前状态 | 允许的下一步 |
|---|---|
| `DRAFT` | `ACTIVE` |
| `ACTIVE` | `PAUSED`、`CLOSED` |
| `PAUSED` | `ACTIVE`、`CLOSED` |
| `CLOSED` | 无（终态） |

非法流转 → **400**，消息形如
`项目状态不能从 DRAFT 流转到 PAUSED；当前状态 DRAFT 允许的下一步: ACTIVE`。

### 34.3 业务规则与状态码

| 场景 | 状态码 | 消息要点 |
|---|---|---|
| 客户/项目 `code` 重复 | **409** | `客户编码已存在: XXX` / `项目编码已存在: XXX` |
| 客户下仍有项目时删除 | **409** | `该客户下仍有 N 个项目，无法删除` |
| 项目下仍有资产挂靠时删除 | **409** | `该项目下仍有 N 个资产挂靠，请先解绑` |
| 期次下仍有资产挂靠时删除 | **409** | `该期次下仍有 N 个资产挂靠，请先解绑` |
| 关闭分期（isPhased true→false）但已有按期次挂靠 | **409** | `该项目已存在按期次挂靠的记录（N 条），无法关闭分期` |
| 未启用分期的项目建期次 | **400** | `该项目未启用分期（isPhased=false），不能创建期次` |
| 期次号在同项目内重复 | **409** | `该项目的期次号已存在: N` |
| `customerId` / `managerId` 不存在 | **404** | `客户不存在: …` / `项目经理不存在: …` |
| 资源不存在（客户/项目/期次） | **404** | 统一 `NOT_FOUND` 语义 |

**权限表达式**：写操作为
`hasAnyRole('ADMIN','PROJECT_LEADER') or @accessGuard.canManageProject(#id, authentication)`
（期次为 `canManagePhase(#phaseId, ...)`，沿 phase→project→managerId 解析）；
`team_leader` 仅能写 `projects.manager_id == 自己` 的项目。**读对所有登录用户开放**（项目是组织维度，不作为权限边界，与 t46 一致）。

### 34.4 审计

所有写操作由 `AuditTrailInterceptor` 自动记录，资源类型映射为单数：
`/customers*` → `customer`、`/projects*` → `project`、`/projects/{id}/phases` 与 `/phases*` → `project-phase`
（t68 在 `resolveResourceType` 中新增「最后一段是否为 phases」的嵌套识别）。

### 34.5 验证

```bash
cd backend && mvn -B test -Dtest=CustomerProjectApiTest
```

14 个用例覆盖：客户/项目 CRUD、编码冲突 409、删除保护 409 ×3、状态机合法/非法、期次仅在分期项目可建、
关闭分期的期次挂靠约束、team_leader 自己项目 200 / 他人 403、工程师写 403 读 200、审计落库断言。

---

## 35. 第二十二轮（资产—项目挂靠 API + 建资产必须挂项目，t69）

### 35.1 新增/变更端点

| 端点 | 说明 |
|---|---|
| `POST /assets` | **projectId 必填**（DTO `@NotNull`）。校验项目 404 / 客户 404 / 期次 400，随后**同一事务**内建资产 + 挂靠（linkType 缺省 TARGET） |
| `GET /assets/{assetId}/project-links` | 资产的全部挂靠（项目/客户/期次摘要）；`canReadAsset` |
| `POST /assets/{assetId}/project-links` | 追加挂靠（body：projectId、phaseId?、linkType、remark）；`canWriteAsset` |
| `DELETE /assets/{assetId}/project-links/{linkId}` | 解绑；`canWriteAsset`；挂靠不存在或不属于该资产 → 404 |
| `GET /projects/{projectId}/assets` | 反向查询，分页；`phaseId` 可选（不传 = 项目全部挂靠 / 传 = 只看该期次）；读全员开放 |
| `GET /assets` | 新增 `customerId` / `projectId` / `phaseId` 筛选；每条资产附 `customers` / `projects` 摘要 |
| `GET /assets/{id}` | 新增 `projectLinks` 完整明细（列表接口不返回，避免响应体膨胀） |

### 35.2 三条不变式与状态码

| 规则 | 状态码 |
|---|---|
| 建资产不带 `projectId` | **400**「创建资产必须指定所属项目」（字段消息 `validation.asset.project.required`，中英双语） |
| 项目 / 客户不存在 | **404** |
| 期次不存在 | **404**；期次不属于该项目 / 项目未启用分期 → **400** |
| 同一 `(asset, project, phase)` 重复挂靠 | **409**「该资产已挂靠此项目（期次）」 |
| 解绑最后一条挂靠 | **409**「资产必须至少挂靠一个项目，请先挂靠到其他项目再解绑」 |
| 挂靠记录不存在 / 不属于该资产 | **404**（与不存在同码，防枚举） |

**跨客户挂靠允许**：同一资产可同时挂到 A 客户的项目和 B 客户的项目（真实场景：一套靶场服务多个客户）。
挂靠行上的 `customer_id` 始终取自**该项目**的客户，不做「资产只能属于一个客户」的限制。

**兼容策略**：不提供「无项目资产」的创建路径。历史资产已由 V18 回填到 `PRJ-HISTORY`（t67），
因此 `assets` 表结构不变，唯一的硬约束落在创建接口上。

### 35.3 避免 N+1

`GET /assets` 每条资产要带 `customers` / `projects`，最直观的写法是逐条查挂靠 → 挂靠数 + 页大小 次查询。
实际实现固定 **3 次**查询（与页大小无关）：

1. `AssetProjectLinkRepository.findByAssetIdIn(本页 ids)`；
2. `ProjectRepository.findAllById(项目 ids)`；
3. `CustomerRepository.findAllById(客户 ids)`。

然后在内存里按 `assetId → 客户/项目` 聚合（`LinkedHashMap` 去重 + 按名称排序，保证同一资产的多个客户顺序稳定）。
详情接口只查一个资产，直接复用挂靠列表 + `ownershipOf(List.of(id))`。

### 35.4 列表筛选与可见性的叠加

`GET /assets` 现在有两种「ID 集合约束」：`visibleAssetIds`（鉴权口径，t46）与
`ownershipFilteredIds`（归属筛选）。两者在同一个 `Specification` 里做 `id IN (...)`，语义是**交集**：

- 归属筛选无命中 → 直接返回空页（不生成 `IN ()` 非法 SQL，也不退化成全量）；
- 可见集合为空 → 同样空页（t46 既有行为不变）。

### 35.5 既有测试的适配（改夹具，不放宽约束）

`POST /assets` 新增必填字段后，三个受影响 suite 补了「客户 + 项目」夹具：

| suite | 改动 |
|---|---|
| `AssetControllerTest` | `setUpAsset` 增建 `Customer` + `Project`；`createAsset_Success` 设 `projectId`；同文件 `createAsset_ValidationError` 仍不带 `projectId`（本就期望 400） |
| `AssetDomainAuthorizationTest` | `assetBody(...)` 统一追加 `projectId`（5 处调用点无需改动） |
| `ApiMessageI18nTest` | 资产校验断言补 `$.data.projectId` 中英文案，覆盖新增消息码 |

即**只补齐前置数据与断言**，没有为了让老用例通过而放宽「必须挂项目」。

### 35.6 验证

```bash
cd backend && mvn -B test -Dtest=AssetProjectLinkTest
cd backend && mvn -B clean test
```

`AssetProjectLinkTest` **15 例**：建资产带项目 201 + 挂靠落库、不带 400、项目不存在 404、期次错配/未分期 400、
重复挂靠 409、跨客户挂靠成功、解绑最后一条 409、解绑非最后一条 200、他人资产解绑 404、
列表按 customerId/projectId/phaseId 过滤、列表摘要、详情 projectLinks、挂靠列表、
`/projects/{id}/assets` 按期次过滤与分组、工程师写他人资产 403 / 自己 201。

---

## 36. 第二十三轮（客户详情聚合接口，消除前端 N+1，t76）

### 36.1 问题

前端做客户详情时缺两样东西：

1. `GET /customers/{id}` 只返回 `projectCount / activeProjectCount / assetCount` 三个计数，
   **没有项目列表**，前端只能 `GET /projects?customerId=` 拿列表后，**再对每个项目** `GET /projects/{id}`
   取 `phases` 与 `assetCount` —— 上限 20 个并行请求，典型 N+1；
2. 客户下的资产明细没有专用入口（`GET /assets?customerId=` 在 t69 才有，前端当时未接）。

### 36.2 新接口

`GET /api/v1/customers/{id}/summary`（读开放，与 `GET /customers/{id}` 一致）：

```json
{ "customer": { "...CustomerResponse..." },
  "stats": { "projectCount": 2, "activeProjectCount": 1, "phasedProjectCount": 1,
             "assetCount": 2, "vulnerabilityCount": 1 },
  "projects": [{ "id": "...", "code": "PRJ-1", "name": "一期渗透", "type": "PENTEST",
                 "status": "ACTIVE", "isPhased": true, "phaseCount": 2, "assetCount": 2,
                 "managerName": "张三" }],
  "recentAssets": [{ "id": "...", "code": "...", "name": "...", "targetType": "web",
                     "status": "active", "projectNames": ["一期渗透"] }] }
```

- `projects[].phaseCount / assetCount`：**各一次 group by 聚合**算出（`countGroupedByProjectIds`、
  `countAssetsGroupedByProjectIds`），不逐项目查询；
- `recentAssets`：客户挂靠按 `linked_at desc` 取前 **10** 个去重资产，`projectNames` 从**同一次查询结果**里
  过滤本客户的项目名，不再回表。

### 36.3 数据边界（重要）

| 字段 | 口径 |
|---|---|
| `stats.assetCount`、`stats.vulnerabilityCount`、`recentAssets` | **按 `AccessGuard.visibleAssetIds(auth)` 收敛** —— 资产是权限边界，与 `GET /assets` 列表口径一致；非管理层只看到自己可见的资产及其漏洞 |
| `projects` 列表、`stats.projectCount/activeProjectCount/phasedProjectCount`、`projects[].phaseCount/assetCount` | **全量组织口径，不收敛** —— 客户与项目是组织维度（`GET /projects` 本来就对全员开放），项目级资产数用全量统计，否则「客户下有几个项目、每个项目几期」对非管理层会失真 |

这个差异是刻意的：**资产列表会因权限变短，但组织结构不会**。前端可以据此用同一份响应对不同角色渲染
（工程师看到「本项目 2 期 / 全量 5 个资产」中的资产数，但 recentAssets 只有自己有权的资产）。

### 36.4 查询次数（固定，与项目数/资产数无关）

客户 1 + 项目列表 1 + 期次 group by 1 + 项目资产 group by 1 + 客户挂靠 1 + 资产 1 + 项目名 0（复用）+ 经理名 1 + 漏洞数 1 = **约 8 次**，
对比旧流程 `1 + 1 + 项目数 × 1`（20 个项目 = 22 次）。

### 36.5 验证

```bash
cd backend && mvn -B test -Dtest=CustomerSummaryTest
```

`CustomerSummaryTest` **8 例**：完整结构、`projects[].phaseCount/assetCount` 与库内一致、经理名解析、
工程师视角 `recentAssets` 只含可见资产且 `stats.assetCount` 小于 admin（且 `projects` 列表两人一致）、
空客户空数组、客户不存在 404、`recentAssets` 上限 10 + `projectNames` 正确、团队长可读。

---

## 37. 第二十四轮（客户/项目统计 + 仪表盘分布 + 挂靠越权复核，t70）

### 37.1 新增接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/customers/{id}/stats` | `projectCount / activeProjectCount / phasedProjectCount / assetCount / distinctAssetCount / vulnerabilityCount / byStatus` |
| GET | `/api/v1/projects/{id}/stats` | `phaseCount / assetCount / vulnerabilityCount / taskCount / byPhase:[{phaseId,phaseName,assetCount}]` |
| GET | `/api/v1/dashboard/customer-distribution` | 按客户聚合 `assetCount`+`projectCount`，资产数倒序 Top N（默认 10 / 上限 100） |
| GET | `/api/v1/dashboard/project-distribution` | 按项目聚合 `assetCount`，支持 `customerId` 过滤 + Top N |

### 37.2 计数口径差异（**重要**）

| 计数类别 | 是否随可见性收敛 | 说明 |
|---|---|---|
| `projectCount` / `activeProjectCount` / `phasedProjectCount` / `byStatus` / `phaseCount` | **不收敛** | 项目与期次是组织结构事实，对所有登录用户可见（与 t46 「项目不是权限边界」一致） |
| `assetCount` / `distinctAssetCount` / `vulnerabilityCount` / `taskCount` / `byPhase` / 两个 distribution 的 `assetCount` | **收敛** | 按 `AccessGuard.visibleAssetIds(auth)` 过滤：非管理层只统计自己可见的资产及其漏洞/任务；管理层（`null`）= 全量 |

实现方式：先用挂靠表取「客户/项目 → 资产集合」，与可见集合求交，再对交集做一次 `in (...)` 统计
（无逐资产 N+1）。

### 37.3 越权复核结论（两条）

| # | 检查项 | 结论 |
|---|---|---|
| ① | `POST /assets/{assetId}/project-links` 是否可给他人资产挂靠 | **已受控**：t69 已挂 `@PreAuthorize("@accessGuard.canWriteAsset(#assetId, authentication)")`，工程师对他人资产 → **403** 且不写入（本轮新增用例断言「挂靠数不变」） |
| ② | `GET /projects/{projectId}/assets` 是否泄露不可见资产 | **修复**：原实现只按项目维度返回全部挂靠资产 → 改为传入 `visibleAssetIds` 过滤（项目可见 ≠ 项目下资产可见）。工程师在含 3 个资产的项目下只看到自己那 1 个 |

删除一致性（复核项 ③）：客户有项目 → 409、项目有挂靠 → 409，因此不可能出现「客户/项目已删但 link 残留」；
用例 `deleteConsistency` 断言「解绑后删项目 200 且该项目挂靠记录为空」。

### 37.4 验证

```bash
cd backend && mvn -B test -Dtest=CustomerProjectStatsTest
```

10 个用例：客户/项目统计与库内一致、工程师视角计数收敛、两个越权复核（挂靠 403 / 项目资产过滤）、
Top N 与 customerId 过滤、删除一致性、不存在资源 404。

---

## 38. 第二十五轮（CSV 导入资产必须挂项目，t78）

### 38.1 问题

t69 把「建资产必须挂项目」加在 **DTO 层**（`AssetCreateRequest.projectId @NotNull`）与控制器上，
但**批量导入走 service 层**（`ImportServiceImpl` → `AssetService.createAsset`），不经过该 DTO ——
CSV 导入出来的资产**没有任何 `AssetProjectLink`**，直接违反「资产必须至少挂靠一个项目」的不变式，
也会让「无挂靠资产 = 0」的验收 SQL 失败。

### 38.2 资产导入模板（新列）

```csv
name,targetType,targetSource,priority,status,teamCode,customerCode,projectCode,phaseNo,description
示例-客户门户Web应用,Web应用,客户委托,P2,active,红队一组,CUST-20250101-AB12,PRJ-20250101-CD34,1,示例资产（导入前请删除本行）
示例-未指定项目的资产,Web应用,客户委托,P3,active,红队一组,,,,不填 projectCode 将自动挂靠历史项目 PRJ-HISTORY（导入前请删除本行）
```

- `customerCode` / `projectCode` / `phaseNo` **均为可选列**（顺序固定，缺列不报错）；
- `GET /import/templates/assets` 同步更新，仍由控制器统一加 **UTF-8 BOM**（Excel 中文不乱码）。

### 38.3 逐行规则

| 输入 | 行为 |
|---|---|
| 有 `projectCode` | 按 code 查项目；不存在 → 该行 failedRows「项目不存在: XXX」 |
| 有 `projectCode` + `customerCode` | 还需客户一致，否则 failedRows「客户与项目不匹配」（避免挂到别的客户下） |
| 有 `projectCode` + `phaseNo` | 项目必须 `isPhased=true`（否则「未启用分期」）且期次存在（否则「期次不存在」） |
| **无 `projectCode`** | 挂到 `CUST-DEFAULT` 的 `PRJ-HISTORY`，`linkType=SHARED`；这是**成功行**，但响应 `notes` 给出「未指定项目，已挂靠历史项目（PRJ-HISTORY）」——**不静默** |
| 只给 `customerCode`/`phaseNo` 不给 `projectCode` | failedRows「必须同时提供 projectCode」（无法确定唯一项目时不猜） |
| 默认客户/历史项目缺失 | **按需补齐**（codes 唯一、幂等）：自动创建 `CUST-DEFAULT` + `PRJ-HISTORY` 后挂靠，任何环境都不产生无挂靠资产 |

### 38.4 事务一致性

`resolveImportTarget(...)` 只做只读校验；落库走 `AssetProjectLinkService.createAssetWithLink(...)`
（t69 同一实现路径：`@Transactional` 内 `assetService.createAsset` → 保存 link）。
任一步抛异常整体回滚 → **失败行不留资产、也不留挂靠**（用例逐条断言 `assets` 与 `asset_project_links` 均无残留）。

### 38.5 响应结构变化（向后兼容）

`ImportResult` 新增第 4 个字段 `notes: [{line, message}]`（成功行提示）；
3 参工厂 `ImportResult.of(total, success, failed)` 保留并等价于 `notes=[]`，
前端旧代码不受影响；新前端可在结果面板把 notes 与 failedRows 分开展示。

### 38.6 验证

```bash
cd backend && mvn -B test -Dtest=ImportAssetProjectTest
cd backend && mvn -B clean test
```

`ImportAssetProjectTest` **10 例**：带项目成功且挂靠正确（含期次）、项目不存在 → failedRows 且零残留、
客户与项目不匹配 → failedRows、未带项目 → 成功 + `notes` 提示 + `SHARED` 挂靠、
期次不存在 / 项目未分期 → failedRows、混合批次「全库无挂靠资产 = 0」、
只给期次不给项目 → failedRows、模板含新列、历史项目缺失时按需补齐（幂等）、
导入结果可经 `/assets/{id}/project-links` 与客户筛选查回。

**同步适配**（改夹具/断言，不放宽校验）：`ImportControllerTest#downloadTemplate` 断言新表头；
`ImportServiceImplTest` 追加 4 个新依赖的 mock，并把「行级失败」与「建资产调用次数」的验证目标
从 `assetService.createAsset` 改到 `assetProjectLinkService.createAssetWithLink`（资产创建已移入该方法）。

## 33. 第四批发布（客户 / 项目 / 期次 / 资产挂靠，t67–t78）

时间：2026-11-02 18:1x–19:2x（Asia/Shanghai）。**新增迁移 V18**（纯新增四表，可在运行期执行）；生产 Flyway 关闭 → **等价手工 DDL 已并入 `deploy/manual-ddl.sql` 第 4 段**。

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test` → **Tests run: 621, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS（35:26）；`@Disabled/@Ignore` 0 命中 |
| 迁移（空库） | 新建 `vuln_audit_tmp` → jar 建出 56 表基线 → V18 连续执行两次均成功（幂等）；四表 + 四唯一索引齐全；种子 CUST-DEFAULT/PRJ-HISTORY 各 1；孤儿资产 0；验后删库 |
| 迁移（生产） | mysqldump 备份（~105 KB）→ V18 执行成功 → customers=1 / projects=1 / project_phases=0 / asset_project_links=3；**孤儿资产 = 0** |
| 手工 DDL 校验 | `manual-ddl.sql` 整体在线上执行通过；输出：4 个唯一索引、cust_default=1、prj_history=1、**orphan_assets=0** |
| 后端重建 | package BUILD SUCCESS 15.7s，jar **73,020,174 B**；重启后 login=200、`/customers`=200、`/projects`=200、`/dashboard/customer-distribution`=200 |
| 前端发布 | 3712 modules，`index-DUbPqUnS.js` 2,481.31 kB（gzip 722.46 kB）；词条 **648/648**；站点仅 5 文件；`/customers`、`/projects` 路由 200 |
| 冒烟 | **TOTAL=195 / PASS=195 / FAIL=0 / SKIP=0**（新增 28 条）；**冒烟后孤儿资产复查 = 0** |
| 备份 | `~/backups/vuln_audit_pre_t75_*.sql.gz`、`backend_*.jar`、`backend_src_*.tar.gz` |

**V18 手工 DDL 要点**（`deploy/manual-ddl.sql` 第 4 段，全部幂等）
- 建表：`customers`（uk code）、`projects`（uk code）、`project_phases`（uk project_id+phase_no）、`asset_project_links`（uk asset+project+phase）；
- 种子：`CUST-DEFAULT`、`PRJ-HISTORY`（`WHERE NOT EXISTS` 守卫）；
- 回填：为每个存量资产插入一条 `PRJ-HISTORY` 挂靠（`linkType=SHARED`，`WHERE NOT EXISTS` 双保险）；
- 校验：四表存在、四唯一索引、种子存在、**`orphan_assets = 0`**。

**本批冒烟新增（28 条）**：客户×2 → 项目（分期/不分期）→ 两期 → 建资产（projectId+phaseId）→ 详情 projectLinks；缺 projectId 400 / projectId 不存在 404 / 期次不属于项目 400；跨客户挂靠 2xx、重复挂靠 409、解绑一条 2xx、解绑最后一条 409；删有项目客户 409、删有挂靠项目 409；工程师建客户/项目 403；团队长改自己项目 2xx / 他人 403；客户 summary assetCount≥1；`projects/{id}/assets` 工程师按可见范围；仪表盘两分布接口 200。

**冒烟适配（破坏性变更）**：`POST /assets` 自 t69/t78 起强制 `projectId` —— 冒烟所有建资产用例已改为先建客户/项目/期次并带 `projectId`；`failedRows` 统计改按 `"reason"` 计数（t78 响应新增 `notes[]` 也含 `line`，旧按 `line` 统计会多计）。

**已知问题（不阻塞）**：`POST /projects` 缺 `manager_id` 时数据库约束报 **500**（应改 400 校验）；项目列表的期次数/资产数仍按页并发 `GET /projects/{id}`（上限 20），详情页已用 summary 聚合。

```bash
cd backend && mvn -B clean test                                   # 期望 621 passed
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 195/195
MYSQL_PWD='***' mariadb -uvulnapp vuln_audit < deploy/manual-ddl.sql           # 幂等执行 V18（含校验输出）
```

---

## 39. 第二十六轮（新域 NOT NULL 兜底 + 四实体扫描，t80）

### 39.1 修复本体：`POST/PUT /projects` 缺 `managerId` 由 500 → 400

`projects.manager_id` 是 NOT NULL，但 `ProjectRequest.managerId` 此前**没有校验注解**：Jackson 反序列化出
`null` → 服务层 `requireUser(null)` 直接 return → `INSERT` 撞数据库约束 → **500**。

现在 `@NotNull(message = "项目经理不能为空")`，校验失败由 `GlobalExceptionHandler` 统一返回
**400 + `data: {managerId: "项目经理不能为空"}`**（字段名可见）。服务层仍保留存在性校验：
客户不存在 → 404、经理不存在 → 404。

### 39.2 四实体 NOT NULL 扫描表

「校验位置」列不存在「依赖 DB 约束」的条目，即没有任何 NOT NULL 列靠数据库兜底。

| 实体 | NOT NULL 列 | 校验 / 兜底位置 |
|---|---|---|
| `customers` | `code` | `CustomerRequest` `@NotBlank` → 400（另 `existsByCode` → 409） |
| | `name` | `CustomerRequest` `@NotBlank` → 400 |
| | `status` | 服务层 `normalizeStatus(..., "active")`，非法值 → 400 |
| | `created_at` / `updated_at` | 实体 `@PrePersist` / `@PreUpdate` 自动填充 |
| `projects` | `code` | `ProjectRequest` `@NotBlank` → 400（另 `existsByCode` → 409） |
| | `name` | `ProjectRequest` `@NotBlank` → 400 |
| | `customer_id` | `ProjectRequest` `@NotNull` → 400；`requireCustomer` 不存在 → 404 |
| | `manager_id` | **`ProjectRequest` `@NotNull`（t80 新增）→ 400**；`requireUser` 不存在 → 404 |
| | `type` | 服务层 `normalizeType(..., "PENTEST")`，非法值 → 400 |
| | `status` | 服务层固定 `DRAFT`；变更只走状态机 `PATCH /status`（非法流转 400） |
| | `is_phased` | 服务层 `Boolean.TRUE.equals(...)` 兜底为 false |
| | `created_at` / `updated_at` | `@PrePersist` / `@PreUpdate` |
| `project_phases` | `project_id` | 路径参数 → `requireProject` 不存在 → 404 |
| | `phase_no` | 请求可空 → 服务层取 `max(phaseNo)+1` 自增；显式重复 → 409 |
| | `name` | `PhaseRequest` `@NotBlank` → 400 |
| | `status` | 服务层默认 `PLANNED` |
| | `created_at` / `updated_at` | `@PrePersist` / `@PreUpdate` |
| `asset_project_links` | `asset_id` | 路径参数 → `canWriteAsset`（403，fail-closed）+ 资产不存在 404 |
| | `project_id` | `AssetProjectLinkRequest` `@NotNull` → 400；项目不存在 → 404 |
| | `phase_key` | 服务层 `phase != null ? phase.getId() : AssetProjectLink.PHASE_KEY_NONE`（哨兵值，永不 NULL） |
| | `customer_id` | 服务层从项目反查写入（项目本身 customer_id NOT NULL） |
| | `link_type` | 服务层 `normalizeLinkType(...)` 默认 `TARGET` |
| | `linked_at` | `@PrePersist` 自动填充 |

同类端点复核：`POST /projects/{id}/phases`（`name` 必填、`phaseNo` 服务层自增）、
`POST /assets/{id}/project-links`（`projectId` 必填、`linkType` 默认 TARGET、`phaseKey` 哨兵）均符合标准。

### 39.3 已知模式「NOT NULL 必须由 DTO/服务层兜底」

同类历史缺陷：t22 `passwordHash`、t34 漏洞创建必填字段、t40 公告 `publishedAt`、t75 发现的 `managerId`。
统一规则：**新增 NOT NULL 列时，必须同时给出 DTO 注解（400）或服务层默认值，不得留给数据库约束**；
漏掉时症状是 500 而非 400，且 `data` 里没有字段名，前端无法定位。

### 39.4 验证

```bash
cd backend && mvn -B test -Dtest=NewDomainValidationTest    # 9 例
cd backend && mvn -B clean test                             # 全量回归
```

`NewDomainValidationTest` 断言：缺 `managerId`/`customerId`/`code`/`name` 逐个 400 且 `data` 带字段名；
客户/经理不存在 404；期次 `name` 缺失 400 且 `phaseNo` 留空能自增；挂靠缺 `projectId` 400 且
`linkType` 落库默认 `TARGET`、`phaseKey` 非空；最后一条统一断言「状态码只能是 400/404，绝无 5xx」。

## 40. 第二十七轮（t80 上线 + 冒烟补 3 条 + 残留清理手册扩展，t81）

时间：2026-11-02 19:2x–19:4x（Asia/Shanghai）。**无新增迁移**。

| 项 | 结果 |
|---|---|
| 修复上线 | `ProjectRequest.managerId` 补 `@NotNull` → `POST/PUT /projects` 缺经理由 **500 → 400 + data.managerId**；存在性校验保留（404） |
| 后端重建 | `mvn -B -DskipTests package` BUILD SUCCESS 11.3s，jar **73,020,205 B**；重启后 login=200、`/projects`=200 |
| 线上实测 | 缺 `managerId` → **400** `data.{"managerId":"项目经理不能为空"}`；不存在 `managerId` → **404** `项目经理不存在: …ff81a` |
| 冒烟 | 新增 3 条守卫（`proj/new-缺managerId-400`、`proj/update-缺managerId-400`、`proj/new-managerId不存在-404`）→ **TOTAL=198 / PASS=198 / FAIL=0 / SKIP=0** |
| 冒烟适配 | 工程师「建项目 403」用例补 `managerId`（否则参数校验 400 先于角色守卫，掩盖越权断言） |
| 残留清理 | 按 §23.1 清理后终态 **assets=3 / customers=1 / projects=1 / phases=0 / links=3 / orphan=0** |
| 回归 | 未重跑（t80 已 `mvn -B clean test` 630/630 全绿） |

```bash
cd backend && mvn -B -DskipTests package && systemctl --user restart vuln-audit-backend
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 198/198
```

---

## 41. 第二十八轮（项目列表批量聚合，消除逐项目 N+1，t82）

### 41.1 背景（t75 遗留 #2 → 已解决）

项目列表此前只返回项目本体，前端渲染「期次数 / 资产数」只能对当页**每个项目**再发一次
`GET /projects/{id}`（每页最多 20 个 → 20 个并发请求）。客户详情在 t76 已用聚合解决，
项目列表沿用旧写法，属于同一类 N+1。

### 41.2 新接口

`GET /api/v1/projects/summary?customerId=&status=&type=&keyword=&page=&size=&sort=`

```json
{ "content": [ { "id": "...", "code": "PRJ-1", "name": "一期渗透",
                 "customerId": "...", "customerName": "某银行",
                 "managerId": "...", "managerName": "张三",
                 "type": "PENTEST", "status": "ACTIVE", "isPhased": true,
                 "phaseCount": 2, "assetCount": 3, "openTaskCount": 1, "...": "..." } ],
  "totalElements": 12, "totalPages": 1, "currentPage": 0, "pageSize": 20 }
```

| 参数 | 说明 |
|---|---|
| `sort` | `createdAt`（默认，倒序，tiebreak = id asc）或 `assetCount`（倒序） |
| `size` | 由 `Paging` 收敛到 1..500 |

### 41.3 查询次数（固定，与页大小无关）

| # | 查询 | 次数 |
|---|---|---|
| 1 | 项目分页（数据 + count） | 2 |
| 2 | 期次数 group by（`countGroupedByProjectIds`，t76 复用） | 1 |
| 3 | 资产数 group by（`countVisibleAssetsGroupedByProjectIds`，带可见范围） | 1 |
| 4 | 未完成任务数 group by（`countOpenTasksGroupedByProjectIds`） | 1 |
| 5 | 客户名 `findAllById` / 经理名 `findAllById` | 2 |

合计约 **7 条**，与一页 1 个还是 20 个项目无关；旧写法是 `1 + 页内项目数`（20 个项目 ≈ 22 条 + 前端并发）。

`sort=assetCount` 时聚合字段无法在 SQL 里 order by：先取筛选命中的全部项目 → 算计数 → 内存排序 → 切页
（组织级项目数量可控）。默认排序仍走数据库分页。

### 41.4 口径（与 t70 / t76 一致，勿混用）

| 字段 | 是否随可见性收敛 | 依据 |
|---|---|---|
| `phaseCount` | **否** | 期次是组织结构事实，对所有登录用户可见 |
| `assetCount` | **是**（`AccessGuard.visibleAssetIds`） | 资产是权限边界，与 `GET /assets`、`GET /customers/{id}/summary` 一致 |
| `openTaskCount` | **是**（随资产口径） | 任务挂在资产上；资产不可见 → 其任务计数不可见（排除 completed/cancelled） |
| `managerName` / `customerName` | 否 | 组织维度展示字段 |

### 41.5 验证

```bash
cd backend && mvn -B test -Dtest=ProjectSummaryTest
cd backend && mvn -B clean test
```

`ProjectSummaryTest` **8 例**：结构与三个计数（含 managerName/customerName）、计数与库内独立计算逐项一致、
工程师 `assetCount` 收敛（2→1）而 `phaseCount` 不变、`customerId` 过滤、空结果不报错、
分页正确（两页不重复，`size` 上限 500）、`sort=assetCount` 倒序、
**查询次数不随页大小变化**（Hibernate Statistics 实采：`size=10` 仍在固定预算内，且与 `size=1` 相差 ≤2 条）。

---

## 42. 第二十九轮（客户 / 项目 CSV 批量导入，t85）

### 42.1 端点

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/api/v1/import/customers`（multipart，字段名 `file`） | `hasAnyRole('ADMIN','PROJECT_LEADER')`，工程师/团队长 → **403** |
| POST | `/api/v1/import/projects` | 同上 |
| GET | `/api/v1/import/templates/customers`、`/templates/projects` | 登录即可（与既有模板一致），**带 UTF-8 BOM + 两行示例** |

### 42.2 客户 CSV

```csv
code,name,industry,level,ownerName,contactName,contactPhone,contactEmail,description
CUST-20250101-AB12,示例-某银行,金融,A,zhangsan,张三,13800000000,zhangsan@example.com,示例客户（导入前请删除本行）
CUST-20250101-CD34,示例-某制造业,制造,B,,李四,13900000000,lisi@example.com,不填 ownerName 则不设客户经理
```

| 规则 | 不满足时 |
|---|---|
| `code` / `name` 必填 | 该行 failedRows |
| `code` 全局唯一 | failedRows「客户编码已存在: X」，不覆盖已有记录 |
| `level` ∈ A/B/C（大小写归一） | failedRows「客户级别非法: X」 |
| `ownerName` 按 **username** 解析 | failedRows「客户经理不存在: X」，**不猜测、不给默认负责人** |
| 成功行 | 逐条审计：`action=import_customer`、`resourceType=customer`、`resourceId=客户ID` |

### 42.3 项目 CSV

```csv
code,name,customerCode,type,managerName,startDate,endDate,isPhased,contractNo,description
PRJ-20250101-CD34,示例-年度渗透测试,CUST-20250101-AB12,PENTEST,zhangsan,2025-01-01,2025-12-31,true,HT-2025-001,示例项目
PRJ-20250101-EF56,示例-代码审计,CUST-20250101-AB12,CONSULTING,lisi,2025-03-01,,0,,不分期项目
```

| 规则 | 不满足时 |
|---|---|
| `code` 唯一 | failedRows「项目编码已存在: X」 |
| `customerCode` 必须存在 | failedRows「客户不存在: X」 |
| `managerName` 按 username 解析 | failedRows「项目经理不存在: X」 |
| `type` ∈ PENTEST / REAUDIT / CONSULTING / TRAINING（留空则默认 PENTEST） | failedRows「项目类型非法: X」 |
| `startDate` / `endDate` 为 `YYYY-MM-DD` | failedRows「startDate 日期格式错误: X（应为 YYYY-MM-DD）」 |
| `endDate` 早于 `startDate` | failedRows「结束日期不能早于开始日期」 |
| `isPhased` 接受 `true/false/1/0`（大小写不敏感） | failedRows「isPhased 取值非法: X」 |
| 成功行 | 逐条审计：`action=import_project`、`resourceType=project`、`resourceId=项目ID` |

### 42.4 部分成功语义

沿用 `ImportResult`（`totalRows` / `successRows` / `failedRows` / `notes`）：逐行独立事务，
任一行失败只进 `failedRows`（带行号，**行号含表头**，第 2 条数据行 = 第 3 行），其余行照常落库；
失败行在 `customerService.create` / `projectService.create` 之前就已被校验拦下，**零残留**。
复用现有服务的唯一性与存在性校验，不绕过服务层。

### 42.5 验证

```bash
cd backend && mvn -B test -Dtest=ImportCustomerProjectTest
cd backend && mvn -B clean test
```

`ImportCustomerProjectTest` **10 例**：客户 2 行成功（列表可查 + 逐行审计）、重复 code → failedRows 不新增、
ownerName 不存在 → failedRows 不落库、level 非法 → failedRows、
项目 2 行成功（`isPhased` true/0、日期、审计、列表可查）、customerCode 不存在、
managerName 不存在 / type 非法 / 日期格式错三种 failedRows、部分成功（成功行落库 + 失败行零残留 + 行号）、
两个模板 BOM + 两行示例、工程师与团队长 403。

> 既有 `ImportServiceImplTest` 因构造函数新增 3 个依赖（CustomerService / ProjectService / UserRepository）
> 已同步补 mock，验证目标不变（未放宽校验）。

## 43. 第五批发布（项目列表批量聚合 + i18n 收尾 + 客户/项目导入，t82–t88）

时间：2026-11-03 12:3x–13:0x（Asia/Shanghai）。**无新增迁移**（仍 V1…V18，无需手工 DDL）。

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test` → **Tests run: 648, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS；`@Disabled/@Ignore` 0 命中 |
| 用例增量 | 630 → **648**（t82 `ProjectSummaryTest` 8 + t85 `ImportCustomerProjectTest` 10） |
| 后端重建 | package BUILD SUCCESS 14.0s，jar **73,030,973 B**；重启后 login=200、`/projects/summary`=200、`/import/templates/customers`=200（首字节 `efbbbf`） |
| 前端发布 | `index-C7DvIDyd.js` 2,522.43 kB（gzip 731.23 kB）；词条 **961/961** 中英对等；`TODO(i18n)` **0 处**；站点仅 5 文件 |
| 冒烟 | **TOTAL=208 / PASS=208 / FAIL=0 / SKIP=0**（新增 10 条） |
| 数据清理 | 按 §23.1 清理后 **assets=3 / customers=1 / projects=1 / phases=0 / links=3 / orphan=0** |

**新增能力**
- `GET /projects/summary`：一次请求返回 `phaseCount / assetCount / openTaskCount / managerName / customerName`，查询数约 7 条与页大小无关（N+1 消除）；`phaseCount` 全量、`assetCount`/`openTaskCount` 按可见范围收敛；支持 `sort=createdAt|assetCount`。
- `POST /import/customers`、`POST /import/projects` + 两个模板（BOM + 两行示例）；仅 ADMIN/PROJECT_LEADER，逐行 failedRows + 逐行审计。
- i18n 全覆盖：前端 `message.*` 274 个调用中文 0 处；词条 961/961。

**冒烟新增 10 条**：`proj/summary-聚合`、`proj/summary-assetCount`、`proj/summary-工程师phaseCount不收敛`（admin=4/engineer=4，assetCount admin=9/engineer=2）、`proj/summary-工程师可见`、`import/客户-部分成功`、`import/客户-失败原因可读`、`import/客户-工程师403`、`import/项目-部分成功`、`import/模板-customers-BOM`、`import/模板-projects-BOM`。

```bash
cd backend && mvn -B clean test                                   # 期望 648 passed
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 208/208
```

---

## 44. 第三十轮（管理员模拟视角：受限 token + 审计归属 + 安全约束，t90）

### 44.1 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/v1/admin/impersonate` | **仅 ADMIN**（模拟态 token 一律 403） | body `{userId, reason?}`；签发受限 token |
| POST | `/api/v1/admin/impersonate/stop` | 普通态 ADMIN；模拟态允许（`act` 必须是 ADMIN） | 写 STOP 审计，返回 **204**，前端自行换回原 token |

响应体：

```json
{ "token": "<受限 JWT>", "expiresAt": "2026-11-03T15:30:00Z",
  "target": { "id": "...", "username": "engineer1", "roles": ["vulnerability_engineer"] },
  "actor":  { "id": "...", "username": "admin" } }
```

### 44.2 受限 token 的构成

| 项 | 值 |
|---|---|
| `sub` | **被模拟用户**（因此后续请求以该用户身份与权限鉴权） |
| `roles` | 被模拟用户的真实角色（仍从 DB 读取 authorities，与普通 token 一致） |
| `act` | 真实操作者（ADMIN）用户ID |
| `imp` | `true`（守卫过滤器与审计归属据此识别） |
| `exp` | 默认 **2 小时**（`app.security.impersonation.ttl-seconds`，默认 7200） |

### 44.3 模拟态禁止的端点清单（`ImpersonationGuardFilter`）

只拦**写方法**（POST/PUT/PATCH/DELETE），读接口不封禁幂等（审阅权限分配需要看列表）：

| 前缀 | 覆盖端点（写） |
|---|---|
| `/api/v1/users/**` | `POST /users`、`PUT /users/{id}`、`DELETE /users/{id}`、`POST /users/{id}/reset-password`、`POST/DELETE /users/{id}/roles*`、`POST/DELETE /users/{id}/permissions*` |
| `/api/v1/roles/**` | `POST /roles`、`PUT /roles/{id}`、`DELETE /roles/{id}`、角色权限点增删 |
| `/api/v1/admin/**` | 全部管理员端点（含 `POST /admin/impersonate` → **防链式模拟**） |
| 显式放行 | `POST /api/v1/admin/impersonate/stop`（否则无法退出模拟） |

返回体：`{"success":false,"code":403,"message":"模拟视角下禁止修改用户、角色或管理员配置"}`。
**其余业务权限完全保留**：目标是团队长时，他能做团队长能做的一切（除了上面三类身份/权限端点）。

### 44.4 审计归属规则

| 事件 | `user_id` | `action` | `resourceType` | `resourceId` | details |
|---|---|---|---|---|---|
| 发起模拟 | **真实操作者（ADMIN）** | `IMPERSONATE_START` | `user` | 目标用户 | `模拟开始: actor=..., target=..., ttlSeconds=..., reason=<用户填写>` |
| 停止模拟 | 真实操作者 | `IMPERSONATE_STOP` | `user` | 目标用户 | `模拟结束: actor=..., target=...` |
| 模拟期间的**业务写** | **目标用户**（真实执行者） | 原动作（如 `POST AssetController.addAssetTag`） | 按 URI | 按 URI | URI + **`; impersonatedBy=<adminId>`** |

即：业务动作「看起来是目标用户做的」，但每条都带真实操作者，可完整回溯「谁以谁的身份做了什么」。

#### 44.4.1 审计落地细节（t93 修订，实测口径）

| 项 | 结论 |
|---|---|
| 谁写 START/STOP | **服务层显式写入**（`ImpersonationServiceImpl`），不依赖拦截器 |
| 拦截器是否重复 | 否 —— `AdminImpersonationController` 已加入 `AuditTrailInterceptor.SKIP_CONTROLLERS`，**一次模拟只产生 1 条**审计（t90 时曾出现「拦截器行 + 服务层行」两条，t93 修正） |
| `details` 是否被截断 | 不会。`audit_logs.details` 列类型为 **TEXT**（`AuditLog#details` 用 `columnDefinition = "TEXT"`）；拦截器的 40/50 字符截断只作用于 `action`（`varchar(50)`），详情行另限 500。服务层写入的 details（约 150–220 字符）完整落库 |
| 可检索性 | `SELECT action, resource_id, details FROM audit_logs WHERE action LIKE 'IMPERSONATE%'` 可直接读出 actor / target / reason / ttlSeconds |

发起审计示例（实测格式）：

```
action       IMPERSONATE_START
resourceType user
resourceId   <目标用户ID>
user_id      <管理员ID>
details      模拟开始: actor=admin(5f1c…), target=engineer1(8c2e…), ttlSeconds=7200, reason=季度权限审阅
```

停止审计（无 `reason=` 键）：

```
details      模拟结束: actor=admin(5f1c…), target=engineer1(8c2e…)
```

### 44.4.2 顺带统一：资产访问申请的状态码（t93）

| 场景 | 修复前 | 现在 |
|---|---|---|
| `POST /assets/{id}/access-requests`，调用者与资产**无工作关系**（跨团队、非负责人、无已批权限） | 一路走到服务层 → **400**「无权访问该资产」 | **403**（新增 `AccessGuard.canRequestAssetAccess` 守卫，不泄露存在性） |
| 同上，资产**不存在** | 404 | 404（`SubresourceGuard`，保持「存在性用 404、权限用 403」） |
| 同上，已有 **approved** 权限、重复申请 | 400（`ASSET_ACCESS_DENIED` 的 code 1203 被 handler 兜成 400） | **403**（`ASSET_ACCESS_DENIED` 的 code 改为 403，与其它守卫统一） |
| 同上，已有 **pending** 申请 | 409 | 409（不变） |
| 本团队成员 / 资产负责人 / 管理层 | 201 | 201（`canRequestAssetAccess` 放行，功能不被误伤） |

`canRequestAssetAccess` 口径：管理层 → 放行；资产负责人 → 放行；资产所属**团队成员（含组长）** → 放行；已获批访问 → 放行；其余 → **403**。

### 44.5 安全约束（逐条实现 + 测试）

| # | 约束 | 实现位置 | 测试 |
|---|---|---|---|
| 1 | 非 ADMIN 发起 → 403 | `@PreAuthorize("hasRole('ADMIN')")` | `nonAdminCannotImpersonate` |
| 2 | 目标不存在 → 404；已停用 → 400 | `ImpersonationServiceImpl` | `targetValidation` |
| 3 | 不得模拟 ADMIN → 409 | 同上（读目标角色含 `admin`） | `cannotImpersonateAdmin` |
| 4 | 链式模拟 → 403 | 守卫过滤器的 `/admin/**` | `chainedImpersonationForbidden` |
| 5 | 模拟态写身份端点 → 403 | 守卫过滤器 | `impersonationCannotWriteIdentityEndpoints` |
| 6 | token 过期 → 401 | 既有 `AuthenticationEntryPoint` | `expiredImpersonationTokenUnauthorized` |
| 7 | start/stop 审计 + 业务审计归属（一次模拟仅 1 条） | 服务层显式写 + `SKIP_CONTROLLERS` + `AuditTrailInterceptor` | `ImpersonationAuditTest`（9 例）/ `ImpersonationTest`（11 例） |
| 8 | 普通态非 ADMIN 调 stop → 403 | 服务层校验 `act`/当前用户的 admin 角色 | `nonAdminCannotStop` |

### 44.6 风险与关闭方式

- **风险**：模拟期间以他人身份产生数据（业务表无差异，但审计可追溯）；若管理员账号被盗用，攻击者可借此横向观察。
- **缓解**：默认 2 小时短 TTL、不得模拟 ADMIN、身份/权限端点全面封禁、全量审计、`reason` 必填建议。
- **生产关闭**：

```yaml
app:
  security:
    impersonation:
      enabled: false   # 关闭后 /admin/impersonate 与 /stop 均返回 403
```

环境变量形式：`APP_IMPERSONATION_ENABLED=false`、`APP_IMPERSONATION_TTL_SECONDS=3600`。

### 44.7 验证

```bash
cd backend && mvn -B test -Dtest=ImpersonationTest   # 11 例
cd backend && mvn -B clean test                      # 全量回归
```

## 45. 第六批发布（菜单选中态修复 + 品牌区 + 管理员模拟视角，t89–t91）

时间：2026-11-03 15:0x–15:3x（Asia/Shanghai）。**无新增迁移**（仍 V1…V18）。

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test` → **Tests run: 659, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS；648 → 659（t90 `ImpersonationTest` 11 例） |
| 后端重建 | package BUILD SUCCESS 20.8s，jar **73,052,099 B**；重启后 login=200、`/projects`=200 |
| 模拟视角接口实测 | `POST /admin/impersonate` → **200**（token 311 字符、含 target/actor）；链式 → **403**；`/stop` → **204** |
| 前端发布 | `index-GiOC38Fc.js` 2,533.86 kB（gzip 735.20 kB）；词条 **980/980**；站点 5 文件 |
| 冒烟 | **TOTAL=222 / PASS=222 / FAIL=0 / SKIP=0**（新增 14 条模拟视角用例） |
| 数据清理 | assets 15→3、customers 13→1、projects 17→1、phases 8→0、links 15→3；**orphan=0** |

**模拟视角要点**（详见 README §44 / 报告 §2.16、§3.2）
- `POST /admin/impersonate`（仅 ADMIN）签发受限 token（`sub`=目标、`act`=adminId、`imp=true`、默认 2 小时）；
- 禁止链式模拟；模拟态禁止 `/users/**`、`/roles/**`、`/admin/**` 的写方法（`/admin/impersonate/stop` 放行）；
- 模拟期间业务写 `userId`=目标用户、`details` 追加 `impersonatedBy=<adminId>`（可回溯真实管理员）；
- 关闭：`app.security.impersonation.enabled=false`。

**t92 发现（已入报告 §2.16 备注 / §8 #15）**
1. 发起/停止审计的 `action` 实为拦截器派生值 `POST AdminImpersonationController.impersonate`（且被截断到 40 字符 → `…stopImpersonatio`），**不是**文档写的 `IMPERSONATE_START/STOP`；发起行 `details` 只有 URI，缺 actor/target/reason。安全目标（可回溯）已达成，但文档与实现需对齐（改文档或补专用审计）。
2. `POST /assets/{id}/access-requests` 对不可见资产返回 **400**「无权访问该资产」（同类守卫多为 403），状态码语义待统一（§8 #16）。

```bash
cd backend && mvn -B clean test                                   # 期望 659 passed
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 222/222
```

## 46. 第七批发布（模拟视角审计实测与事务修复，t93/t94）

时间：2026-11-03 17:0x 首轮，**2026-11-04 14:55–15:05 复核（本轮口径以复核为准）**。**无新增迁移**，前端无改动（未 sync-dist）。

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test`（本地 JDK 17 + Maven 3.9.4 复核 / 服务端 JDK21 亦跑过，独占 target）→ **Tests run: 680, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS；659 → 680（t93 9 + t94 守卫 1 + t96 偏好 11） |
| 后端重建 | package BUILD SUCCESS，jar **73,060,869 B**（md5 0ccf01e4bef511c29357d1e813ab26ad）；`systemctl --user restart` 后 14s 内 login=200，`/projects`、`/projects/summary`、`/users/me/preferences` 均 200，服务 `active` |
| 线上审计实测 | `IMPERSONATE_START` / `IMPERSONATE_STOP` 落库（累积 11 / 10）；`details` = `模拟开始: actor=admin(<id>), target=smoke_engineer(<id>), ttlSeconds=7200, reason=…`；一次模拟 START **+1**；**复核日拦截器重复行增量 0**（库内 19 条历史行来自 t90/t92，保留）；`details` 列 TEXT，最长 165 字符完整落库 |
| 冒烟 | **TOTAL=228 / PASS=228 / FAIL=0 / SKIP=0** |
| 数据清理 | assets 18→3、customers 16→1、projects 21→1、phases 10→0、links 18→3、documents 1→0；**orphan=0**，终态 `customers=1 / projects=1 / assets=3 / links=3` |

**t94 发现并修复的缺陷（重要）**
t93 上线后线上仍写不出 `IMPERSONATE_START`：日志有「记录审计日志」，库中无行。
根因：`ImpersonationServiceImpl.impersonate()/stop()` 标注 `@Transactional(readOnly = true)` —— Hibernate 只读会话以 `FlushMode.MANUAL` **静默丢弃插入**（不报错也不落库）；而 `BaseTest` 的 `@Transactional` 会把该标记并入读写事务，导致 `ImpersonationAuditTest` 9 例全绿仍漏检。
修复：改为可写事务 `@Transactional`；新增反射守卫用例 `impersonationServiceTransactionsMustBeWritable`（断言 `readOnly=false`）。

**状态码统一（t93）**：`POST /assets/{id}/access-requests` 跨团队 → **403**（原 400）、资产不存在 **404**、同团队/负责人/管理层放行。

**冒烟适配（旧用例跟随新契约，未放宽断言）**
1. `asset/访问申请-创建`：t93 后工程师对跨团队资产会被 403，正例改由管理员发起（跨团队 403 由新增 `access-request/跨团队-403` 覆盖）；
2. `audit/无拦截器重复行`：历史拦截器行无法清除，改为「发起前后拦截器行数不变」的差值判定；
3. 脚本缺陷修正：`$BASE`（未定义，`set -u` 下报 `unbound variable`）改为 `$BASE_URL`，消除 stderr 噪音（判定结果不变，仅取 admin userId 的兜底路径不再被打断）。

```bash
cd backend && mvn -B clean test                                   # 期望 680 passed
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 228/228
MYSQL_PWD='***' mariadb -uvulnapp -e "SELECT action, details FROM vuln_audit.audit_logs WHERE action LIKE 'IMPERSONATE%' ORDER BY created_at DESC LIMIT 5"
```

## 47. 第三十一轮（用户登录页主题偏好：V19 + /users/me/preferences + 登录响应携带，t96）

登录页提供 **8 套主题**（角色组：`pencil` / `geometric` / `owl` / `pixel-eyes`；抽象极简组：`aurora` / `mono-grid` / `orbit` / `big-type`），
用户在登录页选择后希望**跨设备保留**。t96 建后端存储与接口，t97/t99 接前端选择与四套抽象主题，t100 把白名单扩到 8 项并收敛为单一来源。

### 47.1 数据模型

| 项 | 值 |
|---|---|
| 列 | `users.login_theme VARCHAR(32) NOT NULL DEFAULT 'pencil'` |
| 迁移 | `backend/src/main/resources/db/migration/V19__add_user_login_theme.sql`（`ADD COLUMN IF NOT EXISTS`，幂等；MariaDB 10.x 与 H2 2.x 均可） |
| 生产手写 DDL | `deploy/manual-ddl.sql`「V19」段（生产 `spring.flyway.enabled=false`，需手工执行后再重启） |
| 实体 | `User.loginTheme`（`@Builder.Default` = `pencil`） |

```sql
-- 手工 DDL（与 V19 等价）
ALTER TABLE users ADD COLUMN IF NOT EXISTS login_theme VARCHAR(32) NOT NULL DEFAULT 'pencil';
```

### 47.2 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/users/me/preferences` | 登录即可 | 返回 `{"loginTheme":"pencil"}`（默认 pencil） |
| PUT | `/api/v1/users/me/preferences` | 登录即可 | 请求体 `{"loginTheme":"owl"}` → 200，返回更新后的偏好 |
| — | `POST /api/v1/auth/login` | 公开 | 响应新增 `loginTheme`（登录后无需再请求一次） |
| — | `GET /api/v1/auth/me` | 登录即可 | 响应含 `loginTheme` |
| — | `POST /api/v1/auth/refresh` | 登录即可 | 响应含 `loginTheme` |

**只能改自己**：路径固定 `/me`，服务层从 `CurrentUserProvider` 取当前用户；请求体/查询参数里**即便带 `userId` 也不会被使用**
（`UpdateUserPreferencesRequest` 只有 `loginTheme` 一个字段，未知字段被 Jackson 忽略），因此不存在「改他人偏好」的入口。

**白名单与归一化**：**8 项** —— `pencil` / `geometric` / `owl` / `pixel-eyes` / `aurora` / `mono-grid` / `orbit` / `big-type`
（注意 `mono-grid` 用**连字符**，`mono_grid` / `monoGrid` 均视为非法）；大小写与首尾空格自动归一（` OWL ` → `owl`）；
非法值 → **400**，中文 `不支持的登录页主题: neon（支持: pencil, geometric, owl, pixel-eyes, aurora, mono-grid, orbit, big-type）`、
英文 `Unsupported login page theme: neon (supported: pencil, geometric, owl, pixel-eyes, aurora, mono-grid, orbit, big-type)`
（消息走 t60 的 `MessageSource`，按 `?lang=` → `Accept-Language` → 中文 的顺序选语言）。
读取时若库内值异常（历史脏数据）回落到 `pencil`，不会把非法值回吐给前端。

**单一来源**：白名单、默认值、错误消息里的「支持: …」列表、DTO 长度上限、单测遍历**全部派生自**
`com.vulnaudit.entity.enums.LoginTheme`（8 个枚举值 + `isValid/fromCode/fromCodeOrDefault/codes/supportedCodes`），
服务层不再手写字符串列表；加主题只改枚举与前端 `frontend/src/components/login/themes.ts` 的注册表，
`UserPreferenceTest#enumAndWhitelistStayInSync` 会守住「枚举 / 服务层白名单 / 默认值 / 期望列表」四处不脱节。

三个错误码对应的消息键：

| 键 | 中文 | 英文 |
|---|---|---|
| `validation.user.login_theme.required` | 登录页主题不能为空 | Login page theme must not be blank |
| `validation.user.login_theme.too_long` | 登录页主题长度不能超过32个字符 | Login page theme must not exceed 32 characters |
| `error.user.login_theme.unsupported` | 不支持的登录页主题: {0}（支持: {1}） | Unsupported login page theme: {0} (supported: {1}) |

`{0}` 为提交的非法值，`{1}` 由 `LoginTheme.supportedCodes()` 生成，扩展主题后自动同步。

### 47.3 审计（一次改偏好只有 1 条）

| 事件 | `action` | `resource_type` | `resource_id` | `details` |
|---|---|---|---|---|
| 修改主题 | `UPDATE_LOGIN_THEME` | `user` | 本人用户ID | `登录页主题变更: from=pencil -> to=owl` |

- 由 `UserPreferenceServiceImpl` 写（语义化），**值未变化时不写**（避免来回切换产生噪声）；
- `AuditTrailInterceptor` 新增 `SKIP_ENDPOINTS = {"PUT /api/v1/users/me/preferences"}` 精确跳过该端点，
  避免再生成一条 `PUT UserController.updateMyPreferences`；
  **没有**把 `UserController` 整体加入 `SKIP_CONTROLLERS`（用户 CRUD / 角色分配 / 重置密码的审计仍由拦截器覆盖）。
- 检索：`SELECT action, resource_id, details, created_at FROM audit_logs WHERE action = 'UPDATE_LOGIN_THEME' ORDER BY created_at DESC LIMIT 5;`

### 47.4 验证

```bash
# 1) 只读默认值
curl -s -H "Authorization: Bearer $TOKEN" http://192.168.188.200:8089/api/v1/users/me/preferences
# → {"success":true,"data":{"loginTheme":"pencil"},...}

# 2) 改为 owl（再次登录应带回 owl）
curl -s -X PUT "http://192.168.188.200:8089/api/v1/users/me/preferences" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"loginTheme":"owl"}'

# 3) 非法值 → 400（中/英）
curl -s -X PUT "http://192.168.188.200:8089/api/v1/users/me/preferences" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"loginTheme":"neon"}'
curl -s -X PUT "http://192.168.188.200:8089/api/v1/users/me/preferences" \
  -H "Authorization: Bearer $TOKEN" -H 'Accept-Language: en' \
  -H 'Content-Type: application/json' -d '{"loginTheme":"neon"}'
# 4) 8 个主题逐个保存（全部 200），再登录应带回最后一次
for t in pencil geometric owl pixel-eyes aurora mono-grid orbit big-type; do
  curl -s -X PUT "http://192.168.188.200:8089/api/v1/users/me/preferences" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"loginTheme\":\"$t\"}"; echo
done

# 5) 非法值 → 400，消息里带完整「支持: …」列表
curl -s -X PUT "http://192.168.188.200:8089/api/v1/users/me/preferences" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"loginTheme":"mono_grid"}'
```

单测：`UserPreferenceTest` **14 例**（默认 pencil / 合法值更新且库内生效 / 大小写归一二 / **8 主题逐个 PUT + `/auth/me` 回显** /
非法值 400 中英各一且**消息含支持列表** / **枚举-白名单-默认值一致性守护** / **库内脏值回落 pencil** / 只能改自己 /
login 与 me 响应含 `loginTheme` / 未认证 401 / 审计落库 / 同值不重复写审计 / 一次改偏好仅 1 条审计）。

### 47.5 已知边界

- 主题**只影响登录页外观**，登录后界面不随主题变化（避免与 t53 的 i18n / 布局主题混淆）；
- 前端未登录时无法读取偏好 → 登录页默认主题为 `pencil`，登录成功后按 `loginTheme` 切换（t97 实现）；
- `login_theme` 参与用户导出/详情响应？**否**（只在 `/auth/login`、`/auth/me`、`/auth/refresh`、`/users/me/preferences` 出现）。

### 47.6 发布与线上复核（2026-11-04）

| 项 | 结果 |
|---|---|
| 后端重建 | 远端 `mvn -B -DskipTests package` BUILD SUCCESS 16.6s，jar **73,061,357 B**；旧 jar/旧 src 备份到 `~/backups/` |
| 重启 | `systemctl --user restart vuln-audit-backend` → `active`；login=200（`loginTheme=pencil`，兜底生效）、`/projects`=200、`/users/me/preferences`=200 |
| 冒烟 | **TOTAL=228 PASS=228 FAIL=0 SKIP=0** |
| 偏好接口 | `PUT {"loginTheme":"owl"}` → 200，再次登录 `loginTheme=owl`；`{"loginTheme":"neon"}` → 400 中文 / `Accept-Language: en` → 400 英文 |
| 审计去重 | `keyword=updateMyPreferences` 行数不变（**+0**）；`action=UPDATE_LOGIN_THEME` **+1**，details 含 `from=pencil -> to=geometric` |
| 迁移 | 线上 `spring.flyway.enabled=false`，`users.login_theme` 由手工 DDL 建立（`deploy/manual-ddl.sql`「V19」段）；本次重启未触发新迁移 |
| 数据清理 | 冒烟夹具清理后 assets=3 / customers=1 / projects=1 / phases=0 / links=3，`orphan=0` |

### 47.7 t100：白名单扩到 8 项并收敛为单一来源

t97/t99 前端已提供 8 套主题（角色组 `pencil` / `geometric` / `owl` / `pixel-eyes`，抽象极简组 `aurora` / `mono-grid` / `orbit` / `big-type`），
后端白名单必须同步，否则选新主题会 400。本轮不改表结构（**无新增迁移**），也不改字段名。

| 项 | 说明 |
|---|---|
| 单一来源 | `com.vulnaudit.entity.enums.LoginTheme`：8 个枚举值 + `DEFAULT`（`pencil`）+ `MAX_CODE_LENGTH`（32）+ `codes()` / `supportedCodes()` / `normalize()` / `isValid()` / `fromCode()` / `fromCodeOrDefault()` |
| 服务层 | `UserPreferenceService.SUPPORTED_LOGIN_THEMES = LoginTheme.codes()`、`DEFAULT_LOGIN_THEME = LoginTheme.DEFAULT.getCode()`（不再手写列表） |
| 校验与归一化 | `LoginTheme.fromCode()`（trim + 小写后比对）；非法值 → 400，文案 `不支持的登录页主题: xxx（支持: …）` |
| DTO | `@Size(max = LoginTheme.MAX_CODE_LENGTH)`；取值合法性交给服务层，避免两处各写一份白名单 |
| 登录链路 | `/auth/login`、`/auth/refresh` 的 `loginTheme` 兜底改为 `LoginTheme.fromCodeOrDefault(...)`；`/auth/me` 直接回显库内值 |
| 前端对齐 | 枚举 code 与 `frontend/src/components/login/themes.ts` 的 `LoginThemeEntry.id` 一一对应（含 `mono-grid` 连字符） |
| 单测 | `UserPreferenceTest` **14 例**：8 主题逐个 PUT 200 + `/auth/me` 回显；非法值中英各一（含支持列表断言）；枚举-白名单-默认值一致性守护（含 `mono_grid` / `monoGrid` / `big_type` 反例）；库内脏值回落 `pencil` |

```bash
# 8 个主题逐个保存（均 200），再登录应带回最后一次
for t in pencil geometric owl pixel-eyes aurora mono-grid orbit big-type; do
  curl -s -X PUT "http://192.168.188.200:8089/api/v1/users/me/preferences" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"loginTheme\":\"$t\"}" | head -c 120; echo
done

cd backend && mvn -B test -Dtest=UserPreferenceTest   # 期望 14/14
```

**t100 线上复核（2026-11-04 17:0x，jar 73,064,121 B）**

| 项 | 实测 |
|---|---|
| 部署 | 远端 `mvn -B -DskipTests package` BUILD SUCCESS 15.1s；旧 jar/src 备份 `~/backups/vuln-audit-backend-t100_*.jar`；重启后 `active` |
| 8 主题保存 | `pencil / geometric / owl / pixel-eyes / aurora / mono-grid / orbit / big-type` 逐个 `PUT /users/me/preferences` 均 200，且 `GET /auth/me` 逐个回显相同值 |
| 非法值 | `mono_grid` → 400 `不支持的登录页主题: mono_grid（支持: pencil, geometric, owl, pixel-eyes, aurora, mono-grid, orbit, big-type）`；`Accept-Language: en` + `monoGrid` → 英文同款列表 |
| 默认值 | 复核后已把 admin 改回 `pencil`；`POST /auth/login` 回包 `loginTheme=pencil` |
| 其它接口 | `/projects`=200、`/customers`=200（未受影响） |

### 47.8 t112：用户偏好补「第三态」（NULL = 跟随系统默认）

**为什么补**（t110 自报的局限）：t96 的 `users.login_theme` 是 `NOT NULL DEFAULT 'pencil'`，只有两态。
「跟随系统默认」被迫实现成「把**当前**管理员缺省写进用户行」——管理员之后改缺省，这类用户跟随不上，
而且「从账号同步」会把旧值搬回来（两态混淆）。

**三态语义（当前口径）**

| `users.login_theme` | 含义 | 管理员改缺省（`system_settings.login.defaultTheme`）时 |
|---|---|---|
| `NULL` | **跟随系统默认** | 自动跟随（用户行仍是 NULL，不写任何值） |
| 具体 code（`pencil`/`owl`/…） | 用户**亲自指定** | 不受影响 |
| 历史行（V19 时期的 `pencil` 等） | 保持原值，**视为用户指定**（不迁移、不改写） | 不受影响 |

- 数据：`V23__user_login_theme_follow_default.sql` = `ALTER TABLE users MODIFY COLUMN login_theme VARCHAR(32) NULL DEFAULT NULL;`
  （MariaDB 10.x 没有 PG 的 `ALTER COLUMN … DROP NOT NULL` 语法，用 `MODIFY COLUMN` 表达同一语义；重复执行幂等，**不修改任何既有值**）。
  生产 Flyway 关闭 → `deploy/manual-ddl.sql`「V23」段（含 `IS_NULLABLE` 与三态分布校验 SQL）。
- 新建用户不再预置 `pencil`（实体默认值为 NULL），即新用户默认跟随系统缺省。

**接口（`PUT /users/me/preferences`，二选一）**

| 请求体 | 行为 | 响应 |
|---|---|---|
| `{"loginTheme":"owl"}` | 写入具体主题 | `{"loginTheme":"owl","followDefault":false}` |
| `{"followDefault":true}` | 置 NULL（跟随系统默认） | `{"loginTheme":null,"followDefault":true}` |
| 两者同传 | **400** `登录页主题与「跟随系统默认」只能二选一` | — |
| 都不传 | **400** `必须指定 loginTheme，或将 followDefault 设为 true` | — |

`GET /users/me/preferences` → `{loginTheme: null|code, followDefault: boolean}`（NULL ⇒ true）。
登录链路 `/auth/login`、`/auth/me`、`/auth/refresh` 对跟随默认用户返回 **`loginTheme: null` + `followDefault: true`**，
**不再回填**管理员缺省（否则又退回两态混淆）；`User` 实体提供只读 `followDefault` JSON 字段，`/auth/me` 直接带出。
审计 `UPDATE_LOGIN_THEME` 的 details 用 `<follow-default>` 标注 NULL：`from=owl -> to=<follow-default>`。

```bash
# 跟随系统默认（清空个人主题）
curl -s -X PUT "http://127.0.0.1:8089/api/v1/users/me/preferences" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"followDefault":true}'
# → {"success":true,"data":{"loginTheme":null,"followDefault":true}}

curl -s "http://127.0.0.1:8089/api/v1/users/me/preferences" -H "Authorization: Bearer $TOKEN"
# 管理员随后把缺省改成 orbit：该用户仍是 loginTheme=null（跟随），不会被写成 orbit
curl -s -X PUT "http://127.0.0.1:8089/api/v1/admin/settings/login-theme" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d '{"defaultTheme":"orbit"}'

cd backend && mvn -B test -Dtest=LoginThemeThirdStateTest   # 期望 9/9
```

单测 `LoginThemeThirdStateTest` **9 例**：followDefault=true → 库内 NULL 且 GET `{null,true}` / 具体 code → 写入且 `followDefault:false` /
两者同传 400（中英）/ 都不传 400（中英）/ login+refresh 对跟随默认用户返回 `null + true` 且不回填管理员缺省 /
管理员改缺省 orbit 后用户仍 NULL / 历史 `pencil` 保持且可清回默认 / 审计 `<follow-default>` 标记 /
V23 空表与已有数据双场景幂等且既有值不变 + 改后可写 NULL。原 `UserPreferenceTest` 同步改为「默认=跟随系统默认（NULL）」口径。


---

## 48. 第八批发布与验收（登录页主题 + 模拟视角审计，t95–t100 / t98）

时间：2026-11-04 17:20–18:10（Asia/Shanghai）。**无新增迁移**（V19 已随 t96 上线），前端**已 sync-dist**（`index-DWByq4yl.js` 2,574.60 kB / gzip 745.32 kB）。

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test`（服务端 JDK21 + Maven 3.9.9，独占 target）→ **Tests run: 683, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS；680 → 683（t100 新增 3 例） |
| 后端重建 | package BUILD SUCCESS，jar **73,064,121 B**（md5 `6615616644f9c56442b49d866cf7b2a7`）；重启 15s 内 login=200、`/projects`=200、`/customers`=200、`/users/me/preferences`=200，服务 `active` |
| 前端发布 | `dist/` 上传后 `SUDO_PASS=… bash deploy/sync-dist.sh` → `/var/www/vuln-audit` **5 个文件**，旧 hash 已清；产物含 8 套主题 id 与 `preferences.*`(22×2) / `login.theme.*` 中英词条 |
| 冒烟 | **TOTAL=244 / PASS=244 / FAIL=0 / SKIP=0**（新增 16 条，见下） |
| 页面验证 | 真实 Edge + 原生 CDP（线上站点）：**47/47 PASS，`pageErrors=[]`** |
| 数据清理 | assets 6→3、customers 4→1、projects 5→1、phases 2→0、links 6→3、documents 0；**orphan=0** |

### 48.1 V19 迁移双场景（t98 实测）

| 场景 | 做法 | 结果 |
|---|---|---|
| 空库 | 新建库 + 仅 `users(id, username)`，执行 V19 | `login_theme varchar(32) NOT NULL DEFAULT 'pencil'` |
| 已有数据 | 先插入 2 条历史行再加列；随后改一行为 `owl` 并**重复执行** V19 | 历史行回填 `pencil`；重复执行不报错且 `owl` 未被覆盖 |
| 生产库幂等 | 对 `vuln_audit.users` 重复执行 V19 | exit 0，值不变 |
| 生产列形态 | `information_schema` 查列 | 列存在；**DEFAULT 为 NULL**（该列由 Hibernate `ddl-auto=update` 建出，非 V19 建出）→ 历史 9 行 `login_theme=''`，靠应用侧 `LoginTheme.fromCodeOrDefault` 回落 `pencil`（实测：人为置 `''` 后登录响应与 preferences 读取均为 `pencil`） |

> 结论：`deploy/manual-ddl.sql` 的 V19 段（含 `IF NOT EXISTS` 与 `DEFAULT 'pencil'`）适用于**新建库**；线上既有库因列已由 Hibernate 建出，`ADD COLUMN IF NOT EXISTS` 为 no-op，行为由应用默认值兜底。若希望库层也有默认值，需单独执行 `ALTER TABLE users ALTER COLUMN login_theme SET DEFAULT 'pencil'`（本次未改线上 schema）。

### 48.2 冒烟新增 16 条

`pref/未认证-401`、`pref/PUT-归位pencil-200`、`pref/GET-默认pencil`、`pref/主题-geometric|owl|pixel-eyes|pencil`（PUT 200 且 `/auth/me` 回显一致）、`pref/抽象主题-4套`（aurora / mono-grid / orbit / big-type）、`pref/非法值-中文400`、`pref/非法值-英文400`（`Accept-Language: en`）、`pref/只能改自己-PUT200`、`pref/只能改自己-管理员不受影响`、`pref/登录响应含loginTheme`、`pref/审计单条`（`UPDATE_LOGIN_THEME` +1、拦截器行 +0）、`access-request/同团队-201`、`access-request/重复pending-409`。

### 48.3 页面验证（真实浏览器）

- 8 套主题渲染：角色组 `[data-testid=login-character][data-group=character]` + SVG `aria-hidden`；抽象组 `data-group=abstract` + 底色 `#0a0c11 / #f4f5f7 / #0f1218 / #f4f2ee` 全部命中。
- 交互：瞳孔三方向（x=+3.20 / -3.19 / y=+2.80）、密码焦点 `is-shielding` 与失焦解除、12s 内 2 次眨眼、reduced-motion 下 `.char-bob` 动画关闭且瞳孔过渡 0s、375px 窄屏 8 套 `brandBottom ≤ cardTop` 且无横向溢出。
- 偏好与持久化：弹窗 8 卡片 / 2 分组 / 8 缩略图；选 `owl` → localStorage 与 `GET /users/me/preferences` 双落库 → 退出后登录页仍 `owl` → 清空 localStorage 回落 `pencil` → 重新登录后缓存被服务端纠正为 `owl`。
- 英文：登录页与偏好弹窗 `cjk=0`（`Login page theme` / `CHARACTERS` / `Current theme: Night owl`）。
- 截图：`.tmp-t98/shot-theme-*.png`、`shot-eye-*.png`、`shot-narrow-375.png`、`shot-pref-modal-zh|en.png`。

### 48.4 踩坑记档（重要）

1. **`mvn clean test` 会删掉正在运行的 jar**：后端由 systemd 从 `backend/target/vuln-audit-backend-1.0.0-SNAPSHOT.jar` 启动，`clean` 后运行中的 JVM 懒加载类（如 JDBC 驱动 `ServiceLoader`）会抛 `NoSuchFileException`，表现为**登录 500**。顺序必须是 **先 `mvn clean test` → 再 `package` → 再 `systemctl --user restart`**；只想跑回归时可用 `-Dmaven.clean.skip=true` 或先 `package` 备份 jar。
2. **登录失败限流会挡住后续验证**：`app.security.login.max-attempts=5` / `lock-seconds=300`，按「用户名+客户端 IP」计数。页面脚本若把口令填错（例如复用了持久化 profile 导致输入拼接），连续 5 次后 admin 被锁 **5 分钟**（`429`），期间冒烟与页面验证都会失败；浏览器验证请用**每次新建的 `--user-data-dir`**，并用 React 原生 setter 写值而不是 `insertText` 叠加。
3. **选择偏好弹窗的菜单项要精确点**：头部有两个 `.ant-dropdown-trigger`（语言切换 + 用户菜单），必须先按 `.ant-avatar` 定位用户菜单，再点 `[role=menuitem]`，否则会点到语言菜单上。

```bash
cd backend && mvn -B clean test                                   # 期望 683 passed
cd backend && mvn -B -DskipTests package && systemctl --user restart vuln-audit-backend
cd frontend && npm run build && SUDO_PASS='***' bash ../deploy/sync-dist.sh
cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh   # 期望 244/244
```
## 48. 第三十二轮（自助注册 + 双验证码 + 管理员审批 + 待审批登录拦截，t102）

自助注册采用「**验证码验证 → 提交申请 → 管理员审批 → 才能登录**」的三段式，注册成功**不产生可登录账号**。

### 48.1 数据模型（V20）

| 表 / 列 | 说明 |
|---|---|
| `users.phone` | 已有列（可空），本轮补**唯一索引** `uk_users_phone`；历史用户为 NULL 不参与冲突 |
| `users.email_verified` / `users.phone_verified` | `BOOLEAN NOT NULL DEFAULT FALSE`（t102 新增） |
| `users.status` 新增值 | `pending_approval`（待审批占位用户）/ `rejected`（申请被拒）；**既有 active/suspended/deactivated 未改** |
| `registration_requests` | id / username / password_hash / phone（必填）/ email（可空）/ phone_verified / email_verified / status(PENDING·APPROVED·REJECTED) / reviewer_id / review_note / created_at / reviewed_at |
| `verification_codes` | id / target / purpose(REGISTER·RESET_PASSWORD·BIND) / **code_hash** / expires_at（默认 +300s）/ attempts / consumed_at / created_at |

- 迁移：`backend/src/main/resources/db/migration/V20__self_registration_and_verification_codes.sql`（`ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS`，幂等）。
- 生产 Flyway 关闭 → 执行 `deploy/manual-ddl.sql`「V20」段（含校验 SQL）。

**为什么注册时就建一条 pending_approval 用户**：用户名/手机号/邮箱的唯一性由 `users` 唯一约束直接兜住（并发安全）；「待审批尝试登录 → 403」有真实主体可判定；审批通过只需把该用户置 active 并补齐验证标记。

### 48.2 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/v1/auth/register/send-code` | **公开** | body `{channel:'sms'|'email', target, purpose:'REGISTER'}` → 200；`dev-mode` 下响应含 `devCode` |
| POST | `/api/v1/auth/register` | **公开** | body `{username,password,phone,code,email?,emailCode?}` → **201**（只建 PENDING 申请 + 占位用户，不可登录） |
| GET | `/api/v1/admin/registration-requests?status=PENDING&page=&size=` | ADMIN / PROJECT_LEADER | 分页；`status` 可为 PENDING/APPROVED/REJECTED，空则全部 |
| POST | `/api/v1/admin/registration-requests/{id}/approve` | ADMIN / PROJECT_LEADER | 占位用户转 active + phone/email 落库并标记 verified + 申请 APPROVED + 审计 |
| POST | `/api/v1/admin/registration-requests/{id}/reject` | ADMIN / PROJECT_LEADER | body `{note}`（必填）→ REJECTED，该用户登录 403 |

**注册校验顺序**（任一不通过都不会留下数据）：用户名格式 → 唯一性（用户名/手机号/邮箱，与既有用户与 PENDING 申请比对，冲突 409）→ 手机验证码（必验）→ 邮箱验证码（填了邮箱必验）→ 落库。

**邮箱可选**：不填时占位用户的 `email` 记为 `<username>@no-email.local`（`users.email` 为 NOT NULL 且唯一）。

### 48.3 登录拦截（要求 7）

| 用户状态 | 登录结果 |
|---|---|
| `pending_approval` | **403** `账号待管理员审批，请耐心等待`（`error.auth.pending_approval`），不签发 token |
| `rejected` | **403** `注册申请已被拒绝`（`error.auth.rejected`），不签发 token |
| `active` | 正常签发 token |

判定发生在 `UserServiceImpl.authenticate` **密码校验之前**，因此待审批/被拒用户即使密码正确也拿不到 token。

### 48.4 验证码策略与投递抽象

- 生成：6 位数字（`SecureRandom`），**只存 BCrypt 哈希**，明文只交给 Sender；**任何接口都不回传明文**（除 `dev-mode`）。
- 有效期：`code-ttl-seconds`（默认 300s）；一次性：校验成功写 `consumed_at`；失败累计 `attempts`，达到 `max-attempts`（默认 5）后作废。
- 限流三层：① 同 target+purpose 冷却 `resend-interval-seconds`（默认 300s）→ **429**；② 同 target+purpose 每小时 `max-per-hour`（默认 5）→ 429；③ 单 IP 每小时 `max-per-ip-per-hour`（默认 20）→ 429（进程内滑动窗口）。
- 投递抽象：`VerificationCodeSender#sendSms/sendEmail`；默认 `LoggingVerificationCodeSender` 只打 WARN（**不打印明文验证码**，只打脱敏 target + 长度）。生产接真实网关时提供自己的 Bean 即可顶上（`@ConditionalOnMissingBean`），业务代码不动。

```yaml
app:
  security:
    verification:
      dev-mode: false            # 生产必须 false
      code-length: 6
      code-ttl-seconds: 300
      resend-interval-seconds: 300
      max-per-hour: 5
      max-attempts: 5
      max-per-ip-per-hour: 20
```

环境变量：`APP_VERIFICATION_DEV_MODE` / `APP_VERIFICATION_CODE_TTL_SECONDS` / `APP_VERIFICATION_RESEND_INTERVAL_SECONDS` / `APP_VERIFICATION_MAX_PER_HOUR` / `APP_VERIFICATION_MAX_ATTEMPTS` / `APP_VERIFICATION_MAX_PER_IP_PER_HOUR`。

### 48.5 审计

| 事件 | action | user_id | resource_type / id | details |
|---|---|---|---|---|
| 提交申请 | `REGISTER_SUBMIT` | 申请人（占位用户） | registration-request / 申请ID | username、手机号脱敏、email、status=PENDING_APPROVAL |
| 审批通过 | `REGISTRATION_APPROVE` | 审批管理员 | registration-request / 申请ID | username、手机号脱敏、userId |
| 审批拒绝 | `REGISTRATION_REJECT` | 审批管理员 | registration-request / 申请ID | username、手机号脱敏、note |

审批两个端点已加入 `AuditTrailInterceptor.SKIP_ENDPOINTS`（支持 `{id}` 路径变量），**一次审批只有 1 条审计**。
> 注：`audit_logs.user_id` 为 NOT NULL，因此「提交申请」这条记录挂申请人本人，而不是 null。

### 48.6 验证

单元/集成：`RegistrationFlowTest` **17 例**（手机号格式非法 400、邮箱格式非法 400、未知渠道/用途 400、未发码直接注册 400、验证码错误 400、过期 400、已消费 400、邮箱未验证 400、用户名/手机号/邮箱重复 409、注册成功 201 且 PENDING 且登录 403、审批通过后可登录且 phone_verified=true、重复审批 409、拒绝后登录 403、非管理员 403、5 分钟内重复发码 429、dev-mode 关闭不含 devCode、审批审计仅 1 条、邮箱注册 email_verified=true）。

```bash
cd backend && mvn -B test -Dtest=RegistrationFlowTest     # 期望 17/17

# 线上手工复核（dev-mode 打开时才能拿到 devCode）
curl -s -X POST http://127.0.0.1:8089/api/v1/auth/register/send-code \
  -H 'Content-Type: application/json' -d '{"channel":"sms","target":"13800001111","purpose":"REGISTER"}'
curl -s -X POST http://127.0.0.1:8089/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"zhangsan","password":"Passw0rd!","phone":"13800001111","code":"<devCode>"}'
# 管理员审批
curl -s -H "Authorization: Bearer $ADMIN" "http://127.0.0.1:8089/api/v1/admin/registration-requests?status=PENDING"
curl -s -X POST -H "Authorization: Bearer $ADMIN" \
  http://127.0.0.1:8089/api/v1/admin/registration-requests/<id>/approve
curl -s -X POST http://127.0.0.1:8089/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"zhangsan","password":"Passw0rd!"}'
```

### 48.7 已知边界

1. **被拒后同名不可再申请**：占位用户仍占用 username/phone/email 唯一键（`rejected` 状态）。重新申请需换用户名/手机号，或由管理员清理该占位行——按需求「拒绝即终态」设计，未做自助重投。
2. **IP 限流是单实例内存**：多实例部署时按实例数放大，真实防刷需网关层再叠加。
3. **`dev-mode` 生产必须关闭**：打开时验证码随响应回传，等于绕过验证。
4. **审批通过默认分配 `vulnerability_engineer` 角色**（存在该角色时；不存在则仅 `ROLE_USER`，登录后无业务权限）。

### 48.8 线上复核（2026-11-05）

部署：远端 `mvn -B -DskipTests package` BUILD SUCCESS → jar **73,118,449 B**（旧 jar / 旧 src 备份 `~/backups/vuln-audit-backend-t101_*.jar`）；
`deploy/manual-ddl.sql`「V20」段已在生产库执行（`registration_requests` / `verification_codes` 建表、`users.email_verified/phone_verified` 建列、`uk_users_phone` 唯一索引；存量 10 个用户 `*_verified` 全为 0，未受影响）。

| 场景 | 实测 |
|---|---|
| 手机号格式非法 | `POST /auth/register/send-code {"channel":"sms","target":"12345"}` → **400** `手机号格式不正确（需为中国大陆 11 位手机号…）` |
| 发码（dev-mode 临时打开） | 200，`expiresInSeconds=300` 且回传 `devCode` |
| 5 分钟内重复发码 | **429** `验证码发送过于频繁，请 299 秒后再试` |
| 注册 | **201**，`status=PENDING`，`phoneVerified=true`，`message=注册申请已提交，请等待管理员审批` |
| 待审批登录 | **403** `账号待管理员审批，请耐心等待`（无 token） |
| 审批列表 | `GET /admin/registration-requests?status=PENDING` → 200，含该申请 |
| 审批通过 | 200，`loginEnabled=true`；随后登录 **200**；`GET /auth/me` → `status=active`、`phoneVerified=true`、`phone=13900008888` |
| 重复审批 | **409** `该注册申请已处理（当前状态: APPROVED）` |
| 拒绝 | 200 `REJECTED` + `reviewNote`；该用户登录 **403** `注册申请已被拒绝` |
| 非管理员（smoke_engineer） | 审批列表 **403** |
| 审计 | `REGISTER_SUBMIT`（user_id=申请人占位用户）、`REGISTRATION_APPROVE`、`REGISTRATION_REJECT`（user_id=管理员 admin），各 1 条 |
| dev-mode 关闭后（生产默认） | 发码响应**不含 devCode**（`{"channel":"sms","target":"13900007777","purpose":"REGISTER","expiresInSeconds":300}`） |

复核数据已清理（`users` 回到 10、`registration_requests=0`、`verification_codes=0`、`orphan_user_roles=0`），`APP_VERIFICATION_DEV_MODE` 已从 `deploy/backend.env` 移除并重启，`/projects`、`/users/me/preferences`、`/admin/registration-requests` 均 200。

## 49. 第三十三轮（管理员统一登录页缺省主题：system_settings + 公开端点 + 审计，t109）

**需求更正**：登录页主题的**缺省值应由管理员统一设置**（每个新访客默认看到它）；
用户若自行选择主题，则以**本人偏好**为准（浏览器缓存 + 服务端 `users.login_theme`）。
此前缺省值硬编码 `pencil`，管理员无法控制。

### 49.1 数据模型（V22）

| 项 | 值 |
|---|---|
| 表 | `system_settings`（key/value，便于以后扩展全局开关） |
| 列 | `id VARCHAR(36) PK` / `setting_key VARCHAR(100) NOT NULL`（唯一） / `setting_value TEXT NULL` / `updated_by VARCHAR(36) NULL` / `updated_at DATETIME(6) NOT NULL` |
| 迁移 | `backend/src/main/resources/db/migration/V22__system_settings.sql`（`CREATE TABLE IF NOT EXISTS` + `CREATE UNIQUE INDEX IF NOT EXISTS`，幂等） |
| 生产手写 DDL | `deploy/manual-ddl.sql`「V22」段（生产 `spring.flyway.enabled=false`） |
| 默认值 | **不在迁移里 INSERT**：应用启动 `SystemSettingServiceImpl#ensureDefaults()` 幂等补 `login.defaultTheme=pencil`（多实例并发由唯一键兜底） |

```sql
-- 手工 DDL（与 V22 等价）
CREATE TABLE IF NOT EXISTS system_settings (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL,
    setting_value TEXT         NULL,
    updated_by    VARCHAR(36)  NULL,
    updated_at    DATETIME(6)  NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_system_settings_key ON system_settings (setting_key);
```

### 49.2 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/auth/login-theme` | **无需认证** | `{defaultTheme, themes:[{code}]}`；登录页在认证前调用；已在 `SecurityConfig` 的 permitAll 清单，不会被 `AuthenticationEntryPoint` 拦成 401 |
| GET | `/api/v1/admin/settings/login-theme` | ADMIN | `{defaultTheme, updatedBy, updatedAt}` |
| PUT | `/api/v1/admin/settings/login-theme` | ADMIN | body `{defaultTheme}` → 200；白名单 8 项（复用 `LoginTheme`），非法 **400** 且消息列出支持列表 |

**读写语义**

- 缺省值只存 `system_settings`，**不写任何用户的 `users.login_theme`**；
- 用户个人偏好仍走 `PUT /api/v1/users/me/preferences`（t96 语义不变）；
- 读取容错：行缺失 → 自愈插入 `pencil`；值非法（如手工塞 `neon`）→ **回落 `pencil` + WARN**，不抛异常、不静默改库（便于运维发现脏值）；
- 模拟视角（t90）下 `PUT /admin/**` 由 `ImpersonationGuardFilter` 拦为 403，避免「借他人身份改全站配置」。

**审计**：`UPDATE_SYSTEM_SETTING`、`resource_type=system_setting`、`resource_id=设置行ID`、
`details=登录页缺省主题变更: from=pencil -> to=owl`；值未变化不写；`AuditTrailInterceptor` 用
`SKIP_ENDPOINTS` 精确跳过该端点，**一次修改只有 1 条**审计（不重复记 `PUT AdminSettingsController…`）。

**新访客 → 用户选择 的时序**

1. 登录页加载 → `GET /auth/login-theme`（匿名）→ 用 `defaultTheme` 渲染；
2. 用户在选择器里选主题 → `PUT /users/me/preferences` → 服务端存 `users.login_theme`，前端写 `localStorage('login.theme')`；
3. 之后进登录页：`?theme=` 深链 > `localStorage` > 公开端点缺省值；
4. 管理员的改动**不会**覆盖已选择过的用户（第 3 步优先本地/个人偏好）。

### 49.3 验证

```bash
# 匿名可读（不带 token）
curl -s http://192.168.188.200:8089/api/v1/auth/login-theme
# → {"success":true,"data":{"defaultTheme":"pencil","themes":[{"code":"pencil"},...8 项]}}

# 管理员改缺省为 owl
curl -s -X PUT http://192.168.188.200:8089/api/v1/admin/settings/login-theme \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"defaultTheme":"owl"}'

# 非法值 → 400（含支持列表）
curl -s -X PUT http://192.168.188.200:8089/api/v1/admin/settings/login-theme \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"defaultTheme":"neon"}'

cd backend && mvn -B test -Dtest=SystemSettingTest    # 期望 9/9
```

单测 `SystemSettingTest` **9 例**：匿名公开端点 200 且返回 pencil + 8 主题 / 管理员改 owl 后公开端点立即生效 /
非法值 400（中英，含支持列表）且库内不变 / 非管理员读写 403、匿名读 401 / 审计 from→to 且仅 1 条 /
缺行自愈插入且幂等 / 脏值 `neon` 回落 pencil 不抛异常不改库 / V22 空库与已有数据双场景幂等 + 唯一键兜底 /
管理员缺省值不写用户偏好。

## 50. 第三十四轮（忘记密码：验证码重置 + 旧 token 失效 + 审计，t103）

忘记密码**复用 t102 的验证码基础设施**（`verification_codes` 表 + `VerificationCodeService` + `VerificationCodeSender`），不新增表；只补两处数据与一条链路约束。

### 50.1 数据（V21 幂等迁移 + manual-ddl「V21」段）

| 变更 | 说明 |
|---|---|
| `users.token_invalid_before DATETIME(6) NULL` | 重置成功后写入「当前时间（秒级）」；`JwtAuthenticationFilter` 拒绝 `iat < token_invalid_before` 的 token，实现**改密后全端下线**（无状态 JWT 不维护黑名单） |
| 回填 `email_verified = TRUE` | `email_verified` 是 t102 才有的列；管理员代建的历史账号邮箱在创建时已核对，不回填会让「忘记密码」对所有历史账号静默不可用。占位邮箱 `<username>@no-email.local` 保持未验证（不可达） |

### 50.2 接口（公开，全部限流）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/password-reset/send-code` | body `{channel:'sms'|'email', target}`（purpose 固定 `RESET_PASSWORD`）→ **200**；target 必须是**已绑定且 `phone_verified=true` 的手机号**或 **`email_verified=true` 的邮箱** |
| POST | `/api/v1/auth/password-reset/confirm` | body `{target, code, newPassword}` → 200；验证码哈希比对 + 过期 + 尝试上限 + 一次性消费；密码强度 `PasswordPolicy`；成功后改密 + 全端下线 + 写审计 |

**防账号枚举**：`send-code` 对「不存在 / 未验证 / 已停用」的 target 一律返回 **200**，内部不发码也不落库（日志记 `target 不存在或未验证…`）。
`devCode` 仅在 `app.security.verification.dev-mode=true` 且**验证码真的发出**时出现，因此测试环境可据 `devCode` 是否为空断言「未发送」。

**限流**（沿用 t102 配置）：同一 target 冷却 `resend-interval-seconds`（默认 300s）→ 429；target 每小时 `max-per-hour`（5）→ 429；单 IP 每小时 `max-per-ip-per-hour`（20）→ 429。
**新增**：`confirm` 连续失败按 IP 计数，窗口 `reset-failed-window-minutes`（默认 15 分钟）内达到 `reset-max-failed-attempts`（默认 5 次）→ **429**（带 Retry-After）。

### 50.3 密码强度（单一实现）

`com.vulnaudit.util.PasswordPolicy`：**≥8 位**，且「大写 / 小写 / 数字 / 符号」四类中**至少满足三类**；上限 64。
不满足 → 400 `密码强度不足：至少 8 位，且需包含大写字母、小写字母、数字、符号中的至少三类`。

> **口径差异（重要）**：仓库里既有的 `/auth/change-password`、`/users/{id}/reset-password` 历史上只有长度校验（6–100），
> **不存在**任务书所说的「≥8 + 三类」实现。本轮把该策略实现为**单一可复用工具类**并只在新链路启用；
> 若直接套到老接口，会改变既有契约并让已发布用例（如 `brandNew789`、`resetPass789` 这类仅「小写+数字」的取值）失败。
> 统一收紧建议见报告 §8。

### 50.4 重置后的安全效果

1. `password_hash` 更新为 BCrypt 新哈希；
2. `users.token_invalid_before = now`（秒级）→ 该用户**所有更早签发的 token 立即失效**（受保护端点 401）；
3. 验证码标记 `consumed_at`（一次性，复用 t102 实现）；
4. 审计 `RESET_PASSWORD`：`resource_type=user`、`resource_id=用户`、`details=忘记密码重置: channel=sms, target=137****1111, tokensInvalidBefore=…`
   —— **不含验证码、不含明文新旧密码**；
5. 新旧密码相同 → 400「新密码不能与旧密码相同」（在消费验证码之前判定，不白白烧掉验证码）。

> **已知窗口**：JWT `iat` 为秒级精度，与重置**同一秒**内签发的 token 仍可用（≤1 秒）。这是「不维护 token 黑名单」的代价，已在 §50.7 记录。

### 50.5 验证

`PasswordResetFlowTest` **13 例**：已验证手机号发码 200 且落库 / 5 分钟内重复 429 / 未验证手机号与邮箱 200 但不发码（码表 0 行，等价于 Sender 未被调用）/
不存在 target 200 且无 devCode 且不落库 / 错误码 400 / 过期 400 / 已消费 400 / 强度不足 400（且不消费验证码）/ 新旧密码相同 400 /
重置后旧密码 401 新密码 200 / 旧 token 401 而新登录 token 200 / 邮箱渠道 + 审计（含 channel、不含验证码与明文密码、仅 1 条）/ 未知 target confirm 400 / 连续失败 429 / 重置后偏好接口仍 200。

```bash
cd backend && mvn -B test -Dtest=PasswordResetFlowTest    # 期望 13/13

# 线上手工复核（dev-mode 打开时才能拿到 devCode）
curl -s -X POST http://127.0.0.1:8089/api/v1/auth/password-reset/send-code \
  -H 'Content-Type: application/json' -d '{"channel":"sms","target":"<已验证手机号>"}'
curl -s -X POST http://127.0.0.1:8089/api/v1/auth/password-reset/confirm \
  -H 'Content-Type: application/json' \
  -d '{"target":"<已验证手机号>","code":"<devCode>","newPassword":"NewPass@2026"}'
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8089/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"<user>","password":"<旧密码>"}'   # 期望 401
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8089/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"<user>","password":"NewPass@2026"}' # 期望 200
```

### 50.6 配置

```yaml
app:
  security:
    verification:
      dev-mode: false                 # 生产必须 false（发码响应才不回传明文）
      reset-max-failed-attempts: 5    # ENV: APP_VERIFICATION_RESET_MAX_FAILED
      reset-failed-window-minutes: 15 # ENV: APP_VERIFICATION_RESET_FAILED_WINDOW_MINUTES
```

### 50.7 已知边界

1. **同一秒窗口**：与重置同一秒签发的 token 仍有效（≤1 秒）；需要严格即时失效时改为 `token_invalid_before = iat 精度 + 1s` 并让登录侧签发 `iat+1`，或引入 token 版本号（`token_version` 递增 + 写进 JWT claim）。
2. **密码策略未回填老接口**：`/auth/change-password`、管理员重置仍是 6–100 长度校验（见 §50.3）；建议下轮统一。
3. **历史账号必须先回填 `email_verified`**（V21 已做）；新注册账号由审批流程置位。
4. **IP 失败计数为进程内**：多实例部署按实例数放大，需网关层再叠加。

### 50.8 线上复核（2026-11-05）

- 部署：远端 `mvn -B -DskipTests package` BUILD SUCCESS → jar **73,152,953 B**（旧 jar / 旧 src 备份 `~/backups/vuln-audit-backend-t102_*.jar`）；
  `deploy/manual-ddl.sql`「V21」段已在生产库执行（`users.token_invalid_before` 建列 + `email_verified` 回填，回填后仅占位邮箱仍为未验证）。
- 复核用的临时账号 `t103user`（手机 13600001111，经 t102 注册 + 审批得到已验证手机号）：

| 场景 | 实测 |
|---|---|
| 发码 | `POST /auth/password-reset/send-code {"channel":"sms","target":"13600001111"}` → 200，dev-mode 下 `devCode=180550` |
| 重置 | `POST /auth/password-reset/confirm {"target":"13600001111","code":"180550","newPassword":"NewPass@2026"}` → 200 `密码已重置，请使用新密码登录` |
| 旧密码 | 登录 **401** |
| 新密码 | 登录 **200** |
| 旧 token | 重置前签发的 token 访问 `GET /auth/me` → **401**（`token_invalid_before` 生效） |
| 不存在 target 发码 | `{"channel":"sms","target":"13911112222"}` → **200**，响应**不含 devCode**（不泄露账号是否存在，也不发码） |
| 不存在邮箱发码 | `{"channel":"email","target":"nobody@nowhere.local"}` → **200** 且无 devCode |
| 审计 | `action=RESET_PASSWORD, resource_type=user, resource_id=<t103user>`，`details=忘记密码重置: channel=sms, target=136****1111, tokensInvalidBefore=2026-11-05T11:51:40`（**无验证码、无明文密码**），仅 1 条 |

- 复核数据已清理（`users` 回到 10、`registration_requests=0`、`verification_codes=0`、`orphan_user_roles=0`）；
  `APP_VERIFICATION_DEV_MODE` 已从 `deploy/backend.env` 移除并重启，生产发码响应不再含 devCode；`/projects`、`/users/me/preferences`、`/audit-logs` 均 200。
- 回归：隔离副本 `mvn -B clean test` → **722/722**（基线 700 + 本任务 13 + 并行任务在途 9；`backend/target` 存在并发构建，故用副本独占）。

## 51. 第三十五轮（第三方 SSO 登录：4 家 provider + 管理员开关 + 待审批关联 + 绑定/解绑，t104）

支持 GitHub / Microsoft / Google / WeChat 四家 OAuth 登录；**开放哪些由管理员决定**，凭据只从表里读、不写死在代码里；
陌生第三方身份**仍需管理员审批**才能建本地账号。

### 51.1 数据（V24 幂等迁移 + manual-ddl「V24」段）

| 表 / 列 | 说明 |
|---|---|
| `sso_providers` | code(github/microsoft/google/wechat)、name、icon、**enabled**、client_id、client_secret、authorize_url、token_url、userinfo_url、scopes、created_at/updated_at |
| `sso_bindings` | user_id ↔ (provider_code, external_id)，`(provider_code, external_id)` 与 `(user_id, provider_code)` 各唯一 |
| `sso_pending_bindings` | SSO 首次登录的待审批绑定（PENDING → ACTIVATED），关联 registration_request_id |
| `registration_requests` 追加 | `source`（LOCAL/SSO）、`provider_code`、`external_id`、`display_name`；`phone` 改为**可空**（SSO 没有手机号） |

**默认数据**：启动时若 `sso_providers` 为空，`SsoProviderBootstrapRunner` 插入 4 条 **enabled=false** 的记录
（authorize/token/userinfo URL 与 scopes 按各平台默认值预填；`client_id`/`client_secret` 留空）。

### 51.2 「可用」判定（关键）

`usable = enabled && configured`，其中 `configured = client_id 与 client_secret 均非空`。

| 状态 | 登录页按钮 | `GET /auth/sso/{code}/authorize-url` |
|---|---|---|
| disabled | 不展示 | **400**「该登录方式未开放」 |
| enabled 但未配凭据 | **不展示** | **503**「该登录方式尚未完成配置」 |
| enabled 且已配置 | 展示 | 200 `{url, state}` |

> **真实对接方式**：在 `sso_providers` 里为对应平台填入 OAuth 应用的 `client_id`/`client_secret`
>（以及按需覆盖 authorize/token/userinfo URL 与 scopes），再由管理员 `PATCH /admin/sso/providers/{code} {"enabled":true}`。
> 本地无凭据时按钮天然不显示——这就是「未配置即不可见」的实现口径。

### 51.3 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/auth/sso/providers` | 公开 | 仅 `enabled && configured` 的 `[{code,name,icon}]` |
| GET | `/api/v1/auth/sso/{code}/authorize-url?redirectUri=` | 公开 | `{url, state}`；state **5 分钟**有效、一次性（内存 Map） |
| POST | `/api/v1/auth/sso/{code}/callback` | 公开 | body `{code, state}`；三态见下 |
| PATCH | `/api/v1/admin/sso/providers/{code}` | **ADMIN** | body `{enabled}`；审计 `UPDATE_SSO_PROVIDER`（一次开关仅 1 条） |
| GET | `/api/v1/admin/sso/providers` | ADMIN | 全部 provider（含未开启/未配置，凭据不回传） |
| GET | `/api/v1/users/me/sso-bindings` | 登录 | 我的绑定列表 |
| POST | `/api/v1/users/me/sso-bindings/{code}/start` | 登录 | 返回带「绑定态」的授权地址；已绑同一 provider → **409** |
| DELETE | `/api/v1/users/me/sso-bindings/{code}` | 登录 | body `{password}`；密码错 **403**；成功后该 provider 回到「待审批」路径 |

**回调三态**

| 场景 | 状态码 | 响应 |
|---|---|---|
| 已绑定且账号 `active` | 200 | `{status:"LOGIN", login:{token,...}, provider}` —— 直接签发本地 JWT，并写 `SSO_LOGIN` 审计 |
| 陌生身份（含曾被解绑） | **202** | `{status:"PENDING_APPROVAL"}` —— 不发 token；建 `registration_requests(source=SSO)` + `sso_pending_bindings(PENDING)` + `pending_approval` 占位用户，写 `SSO_REGISTER_SUBMIT` 审计 |
| 绑定账号非 active | 403 | 待审批 → 「账号待管理员审批，请耐心等待」；已拒绝 → 「注册申请已被拒绝」；停用 → 「账号已被停用」 |
| state 无效/过期/重放 | 400 | 「登录状态已失效，请重新发起第三方登录」 |

**审批联动（复用 t102 的审批）**：管理员在 `/admin/registration-requests/{id}/approve` 通过 SSO 申请时，
占位用户转 active、`sso_pending_bindings` 置 ACTIVATED **并落 `sso_bindings`**（写 `SSO_BIND` 审计）；
拒绝则占位用户转 rejected。SSO 用户的本地密码是 32 位随机串（**不告诉用户**），需要本地密码时走「忘记密码」。

### 51.4 抽象与实现

- `SsoClient`：`authorizeUrl(state, redirectUri)` + `exchange(code) → SsoUserInfo(provider, externalId, email, displayName)`。
- 四个实现（`GithubSsoClient` / `MicrosoftSsoClient` / `GoogleSsoClient` / `WechatSsoClient`）继承 `AbstractSsoClient`，
  只做 URL/表单拼装与 JSON 取值，**凭据与端点全部来自 `SsoProvider`**；微信走 QRConnect（`appid` + `#wechat_redirect`）。
- `SsoClientFactory` 按 code 选实现；测试用 `FakeSsoClientFactory`（`@Primary`）整链替换，**不触网**。

### 51.5 验证

`SsoFlowTest` **14 例**：providers 只返回可用项 / 未配置 authorize-url 503 / 未开启 400 / 正常返回 url+state /
state 无效与重放 400 / 首次回调 202 且无 token 且建 PENDING 申请+绑定+占位用户 / 重复首次回调幂等 /
审批通过后回调用 200 且 token 可访问 `/auth/me` / 绑定账号非 active 403 / 绑定成功且重复绑定 409 /
解绑密码错 403、解绑成功后列表空且该身份回调回到 202 / 非 ADMIN 改开关 403 / 管理员开关写 1 条审计 / 关闭后列表与 authorize-url 同步变化。

```bash
cd backend && mvn -B test -Dtest=SsoFlowTest            # 期望 14/14

# 线上联调（需先在 sso_providers 填好某平台凭据并开启）
curl -s http://127.0.0.1:8089/api/v1/auth/sso/providers
curl -s "http://127.0.0.1:8089/api/v1/auth/sso/github/authorize-url?redirectUri=http://127.0.0.1:8089/sso/callback"
```

### 51.6 已知边界

1. **state 是进程内 Map**：多实例部署需换 Redis 或改为签名 token（无状态）；单实例当前满足 5 分钟 TTL + 一次性。
2. **`client_secret` 明文存表**：当前按任务要求「加密或仅占位」取后者（仅占位），生产建议接 KMS/环境变量注入并加密列。
3. **SSO 用户没有手机号**：只能走邮箱渠道找回密码；provider 未给邮箱时连邮箱也没有 → 只能管理员重置。
4. **解绑后需重新审批**：解绑只删 `sso_bindings`，对应 `sso_pending_bindings` 已是 ACTIVATED，因此该第三方身份再登录会重新进入 202 审批流程（不会自动恢复）。

### 51.7 与登录页主题三态（t112）的衔接

SSO 回调签发本地 JWT 时，`login` 对象与 `POST /auth/login` 完全同构：`login_theme` 为 `NULL` 的用户
（含审批新建的 SSO 用户）返回 **`loginTheme: null` + `followDefault: true`**，**不回填** `pencil` 或管理员缺省值；
管理员随后改缺省，这类 SSO 用户自动跟随。`SsoFlowTest#approveThenCallbackIssuesToken` 已加断言守住这一点。

## 52. 第三十六轮（拒绝即释放唯一键 + 管理端清理入口 + 审批角色显式化，t111）

**修复 t102 自报的可用性缺陷**：申请被拒后，同一用户名/手机号被 `pending_approval` 占位用户占用 → 重新注册 409，
只能换号或人工清库。同时补上管理员自助清理入口，并把「审批角色」改为显式指定。

### 52.1 拒绝即释放唯一键（策略：先删后归档）

`POST /admin/registration-requests/{id}/reject` 在把申请置 REJECTED 之后，处理占位用户：

| 条件 | 处理 | 效果 |
|---|---|---|
| **从未登录**（`last_login` 为空）**且无关联数据**（无 user_roles / 无任务 / 无资产 / 无 SSO 绑定） | **直接删除用户行** | 唯一键自然释放（注册占位用户的常态路径） |
| 存在关联数据或曾登录 | **归档释放**：username → `rejected_<id8>_原值`（截断 50），phone → `rej_<id8>_尾号4`，状态置 `rejected` | 保留行与外键语义，同时释放唯一键 |

- `registration_requests` 记录**不删除**，作为审批审计证据（状态 REJECTED，含 review_note/reviewer/时间）。
- 释放策略写入 `REGISTRATION_REJECT` 审计的 `details`（`release=deleted(...)` / `release=archived(...)`）。
- 为什么先删后归档：占位用户在审批前**无法登录**、也不会被分配角色/任务/资产，删除是最干净的释放方式；
  只有在未来允许「预分配」时才需要归档（当前归档分支主要用于防御性兜底与历史脏数据）。

### 52.2 被拒后登录 = 401（口径：不泄露「曾被拒绝」）

占位用户被删除（或用户名已归档改写）后，用**原用户名+原密码**登录就是「用户不存在」→ **401「用户名或密码错误」**。

- 刻意**不**返回 403「注册申请已被拒绝」：那会向攻击者确认「该用户名/手机号确实申请过、只是被拒」，属于账号枚举信息泄露；
  与 t103「忘记密码对不存在 target 也返回 200」的不泄露口径一致。
- 若采用归档分支（行仍在、状态 `rejected`）：**原用户名**登录 401；**归档用户名**登录 403（内部口径，外部不知道该用户名）。
- 保留 403 语义仅用于「占位用户仍存在且状态为 rejected」的场景（如管理员手工改库造成）。

### 52.3 管理员清理入口

`DELETE /api/v1/admin/registration-requests/{id}`（**仅 ADMIN**）：

- 删除申请记录 + 占位用户（同样走「先删后归档」释放逻辑），并清理该申请下仍 PENDING 的 `sso_pending_bindings`；
- **幂等**：申请不存在 → **204**；删两次 → 第二次仍 204，且**不重复写审计**；
- 已通过（APPROVED）的申请 → **409**「该申请已通过审批并生成正式账号，不能清理」（否则删号后同名可重复注册）；
- 审计 `REGISTRATION_PURGE`（一次清理仅 1 条；端点已加入 `SKIP_ENDPOINTS`）。

### 52.4 并发与幂等

- 审批 / 拒绝 / 清理三条路径统一用 `findByIdForUpdate`（`PESSIMISTIC_WRITE` 行锁）读取申请：
  并发时串行化，**先到者胜**，后到者看到状态已变更 → **409**。
- 重复 reject → 409（沿用 t102）；approve 之后再 purge → 409；purge 之后再 approve → 404。

### 52.5 审批角色显式化（用户拍板）

`POST /admin/registration-requests/{id}/approve` 支持 body `{roles: string[]}`：

| roles | 行为 |
|---|---|
| `[]` 或省略 | **建号但不授角色**（登录后仅默认 `ROLE_USER`，无业务权限） |
| `["vulnerability_engineer"]` 等 | 按角色码逐条授予；**未知角色码 → 400「角色不存在: xxx」**（显式失败，不静默跳过） |

**不再隐式分配 `vulnerability_engineer`**：避免「批了账号 = 批了工程师权限」的越权默认。

### 52.6 验证

`RegistrationReapplyTest` **15 例**：拒绝后同手机号重新注册 201 / 同用户名 201 / 同手机号不会有两个 active·pending 用户 /
有关联角色时归档而非删除（`rejected_` 前缀 + 手机号改写 + 状态 rejected）且仍可重注册 / 重复 reject 409 /
拒绝审计 1 条且含释放策略 / purge 删除申请与用户后同手机号可再注册 / purge 幂等 204 / purge 审计仅 1 条 /
已通过申请 purge 409 / approve·purge 互斥 / 非 ADMIN 403 / approve 空 roles 不授角色 / approve 指定 roles 精确授权且未知码 400 /
归档方案下原用户名 401、归档用户名 403。
`RegistrationFlowTest` 的 `rejectThenLogin` 已同步为 **401 + 通用文案**（并断言不得出现「注册申请已被拒绝」）。

```bash
cd backend && mvn -B test -Dtest='RegistrationReapplyTest,RegistrationFlowTest'   # 15 + 17 全绿
```

## 53. 第三十七轮（角色授予权限边界：非 ADMIN 不得授予管理类角色，t115）

**修复的问题**：后端 `assignRoles` 只校验角色码存在、没有「可授予」判断 —— 前端隐藏 admin 角色只是 UI 收敛，
PROJECT_LEADER 直接调 `POST /admin/registration-requests/{id}/approve {"roles":["admin"]}` 即可**自助提权**。

### 53.1 单一事实来源：`security/RoleGrantPolicy`

| 概念 | 判定 |
|---|---|
| **受限角色** | `roles.is_system = true` 的任意角色，**或**代码为 `admin` / `project_leader` 的角色（即便库里 `is_system=false` 也不下放） |
| **可授予者** | `ROLE_ADMIN`（当前请求认证上下文判定） |
| **业务角色** | `vulnerability_engineer` / `team_leader` / `auditor` 等非受限角色 → ADMIN 与 PROJECT_LEADER 均可授予 |
| **越权结果** | **403「无权授予管理类角色: admin」**，且调用方**先行失败**：不建号、不改角色、不写成功审计 |
| **内部调用** | 无认证上下文（启动初始化 / 批处理内部调用）按系统调用放行并打 WARN；Web 请求一律有上下文，不成为绕过口 |

其它规则：角色码必须存在（**400「角色不存在: xxx」**，ADMIN 也一样）；空数组/省略 = 合法（不授角色）；重复码去重。

### 53.2 角色授予权限矩阵（入口 × 授予者 × 可授予集合）

| 入口 | 授予者 | 可授予集合 |
|---|---|---|
| `POST /api/v1/admin/registration-requests/{id}/approve`（body `{roles}`） | ADMIN | 全部角色（含 `admin` / `project_leader` / `is_system`） |
| 同上 | PROJECT_LEADER | **仅业务角色**；含受限角色 → 403 且申请状态不变、账号仍 `pending_approval` |
| SSO 首次登录审批联动（`RegistrationServiceImpl.activateSsoBindings` 同一 approve 路径） | ADMIN / PL | 同上（角色校验在审批开头统一执行） |
| `POST /api/v1/users/{id}/roles?roleId=` | ADMIN | 全部角色（服务层同样过策略，PL 上下文 → 403） |
| `POST /api/v1/roles/{id}/users/{userId}` | ADMIN | 全部角色（`RoleServiceImpl.assignRoleToUser` 统一强制） |
| 用户批量导入（`ImportServiceImpl` → `assignRoleToUser`） | ADMIN | 全部角色（同样经过策略，导入端点本身仅 ADMIN） |
| 移除角色（`DELETE .../roles/...`） | ADMIN | 不受授予策略限制（移除不构成提权） |

### 53.3 实现要点

- `RegistrationServiceImpl.approve`：**先** `resolveGrantableRoles(roleCodes)`（存在性 400 → 策略 403 → 去重），**再**改用户/申请状态；
  审批审计 `REGISTRATION_APPROVE` 的 details 追加 `roles=xxx`（无角色时 `roles=[]`）。
- `RoleServiceImpl.assignRoleToUser(...)`：在**幂等短路之前**执行策略校验，越权请求不会因「已分配」被静默放过。
- 策略本身无状态（`@Component`），只依赖 `SecurityContextHolder`，便于单测直接构造认证上下文。

### 53.4 验证

`RoleGrantPolicyTest` **13 例全绿**：PL 审批传 `[admin]` → 403 且账号仍待审批·无角色·无审批成功审计 / PL 传业务角色 → 200 /
ADMIN 传 `[admin]` → 200 / PL 传 `team_leader` → 200 / PL 传 `project_leader` → 403 / 非法角色码 → 400 且不建号 /
空数组 → 200 无角色 / 重复码去重 / 审批审计含授予角色 / PL 调用户角色接口 → 403 /
服务层：PL 上下文授予 team_leader 成功、授予 admin 抛 403 / ADMIN 上下文可授 admin、不存在角色 → ROLE_NOT_FOUND /
策略分类断言（is_system、admin、project_leader 受限；业务角色不受限）。

```bash
cd backend && mvn -B test -Dtest=RoleGrantPolicyTest        # 期望 13/13

# 线上等价 curl（部署本版本后）
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  -H "Authorization: Bearer $PL_TOKEN" -H 'Content-Type: application/json' \
  -d '{"roles":["admin"]}' \
  http://127.0.0.1:8089/api/v1/admin/registration-requests/$REQUEST_ID/approve   # 期望 403
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"roles":["admin"]}' \
  http://127.0.0.1:8089/api/v1/admin/registration-requests/$REQUEST_ID/approve   # 期望 200
```

### 53.5 已知边界

1. 受限集合里 `admin` / `project_leader` 是**硬编码兜底**：即使有人把库里 `is_system` 改成 false，也不会被下放；新增管理类角色应同时置 `is_system=true`。
2. 策略只看 `ROLE_ADMIN`：如果要引入「角色管理员」这类细分权限，需要扩展为权限点（`ROLE_ADMIN` 或 `role:grant:*` 权限）。
3. 无认证上下文放行是给初始化/批处理留的口子，已在代码里 WARN 留痕；若未来有「无上下文的任务线程授予角色」，应改为显式传 actor。
---

## 54. 第三十八轮（t107 验收发布：注册审批 / 找回密码 / SSO / 登录页视觉 + 主题三态 + 更名）

时间：2026-11-05 14:1x–15:3x（Asia/Shanghai）。**无新增业务迁移**（V20–V24 随 t102/t103/t104/t109/t112 已入库，本轮**在生产库补执行 V23/V24**）。前端 `sync-dist` 已发布。

| 项 | 结果 |
|---|---|
| 后端回归 | `mvn -B clean test`（宿主 JDK21，pom `java.version=17`）复跑两次：**760 → 773 例**（第二次含 t115 的 10 例 + t111 增量），两次均 **Failures: 0 / Errors: 1**，唯一 ERROR 恒为 `AuditLogControllerTest#getUserAuditLogs_Success`（`Table "USER_ROLES" not found`，H2 上下文串扰**偶发**；单跑 `-Dtest=AuditLogControllerTest` → **9/9 PASS**）。`@Disabled` 0 命中 |
| 后端重建 | `package` BUILD SUCCESS，jar **73,232,580 B**（旧 jar 备份 `~/backups/vuln-audit-backend-t107_*.jar`），重启 23s 内 login=200，服务 `active` |
| 前端发布 | `index-BbaH0LZQ.js` 2,679.68 kB / `index-BtKt5F9b.css` 23.0 kB；`sync-dist.sh` 同步 5 文件并 reload nginx |
| 冒烟 | **TOTAL=274 / PASS=274 / FAIL=0 / SKIP=0**（新增 26 条，见 §54.2）；重建重启后复跑仍 274/274 |
| 页面验证 | `T107_VERIFY 43/43` + `T107_FLOW 21/21`，`pageErrors=[]`（见 §54.3） |
| 迁移双场景 | V20–V24 在**空库**与**生产克隆**各执行两遍均幂等；生产库执行后 `users=10` 不变、`login_theme` 可空、SSO 三表就绪 |
| 数据清理 | 终端 `customers=1 projects=1 phases=0 assets=3 links=3`、`orphan_assets=0`、`registration_requests=0`、`verification_codes=0` |

### 54.1 迁移执行顺序与幂等验证（V20–V24）

```bash
# 逐个执行（每个都可重复执行，第二遍必须同样成功且不改数据）
MIG=~/apps/vuln-audit/backend/src/main/resources/db/migration
for f in V20__self_registration_and_verification_codes.sql \
         V21__password_reset_token_invalidation.sql \
         V22__system_settings.sql \
         V23__user_login_theme_follow_default.sql \
         V24__sso_login_and_bindings.sql; do
  sudo mariadb vuln_audit < "$MIG/$f" || echo "FAIL $f"
done
```

实测：空库（模拟 V19 后 `users` 形态）→ `login_theme` 变为可空、`email_verified/phone_verified/token_invalid_before` 就绪、`uk_users_phone` 唯一索引 1 个、`registration_requests.phone` 可空、SSO 三表 3 张；回填断言 `admin=1 / engineer1=1 / @no-email.local=0 / 邮箱 NULL=0`。生产克隆执行后 `users` 10 → 10、`login_theme` 分布 `'owl'=1 / 'pencil'=9` 不变。

> `sso_providers` 默认 4 条由**应用启动**补种（`SsoProviderBootstrapRunner`），DDL 只建表；本轮重启后表内 4 行 `enabled=false`。

### 54.2 冒烟新增 26 条

注册 8：手机号非法 400 / 发码 200 / 重复发码 429 / 未发码直接注册 400 / 邮箱未验 400 / 错误码 400 / 注册成功 201 / 重复用户名 409；
审批 5：非管理员列表 403 / 管理员列表含申请 / approve 200 / 审批后登录 200 / reject 200 + 拒绝后不可登录（t111 拒绝即删占位用户 → 401，脚本接受 401/403）；
找回 8：发码 200 / 重复发码 429 / 错误码 400 / 重置成功 200 / 旧密码 401 / 新密码 200 / 未知账号 200 且无 devCode；
SSO 6：providers 200 / 非管理员改开关 403 / 管理员开启 200 / 未配置 authorize-url 503 / 开启但未配置登录页不可见 / 回收关闭 200；
RBAC 2（t115）：`rbac/PL授予admin-403`、`rbac/ADMIN授予admin-200`。

### 54.3 页面验证（真实 Edge + 原生 CDP）

- **品牌（t108）**：`document.title` 前缀 = `猫头鹰漏洞审计生命周期管理系统`（登录页后缀 `· 登录`）；favicon `/favicon.svg`（猫头鹰）；登录页 `login-brand` 徽标 30px + 全名 + 短名 + slogan「洞察入微 · 审计可溯」；侧边栏展开全名、折叠仅徽标 + `aria-label`/Tooltip 短名「猫头鹰审计」；英文 `Owl Vulnerability Audit Lifecycle Management System` / `Owl Audit` / slogan「See deeper. Audit further.」。
- **视觉（t101/t105）**：8 主题 × 3 尺寸（1440×900 / 1920×1080 / 375×812）共 24 张截图，断言舞台铺满视口、品牌在左上（x<120,y<120）、卡片可见、`overflowX=0`；登录框皮肤 8/8 互不相同（圆角 18/26/22/0/6/2/4/0 px）；页面**无**「动一动鼠标」提示、**无**资产/项目营销文案。
- **主题解析三态**：T1 新浏览器显示管理员缺省（orbit）；T2 选 owl 后刷新仍 owl；T3 缺省改 geometric → 有标记仍 owl、无标记跟 geometric；A1 未登录 `GET /auth/login-theme` 200；A2 非 ADMIN PUT 403；A3 非法值 400 且库内不变；A4 登录前后本地标记完全相同；A5 弹窗只预选；A6 从账号同步写入标记（等于缺省也物化）；A7 跟随默认删键（`getItem=null`）；A8 第三态 `loginTheme:null + followDefault:true`；A9 改缺省后仍 null 且登录页跟随；A10 同传 → 400。
- **流程**：注册页真机（真实按键）→ devCode 自动填入 → 提交 → 「待审批」结果页 → 管理员审批 → 新账号登录成功；找回密码 → 发码 → 重置 → 跳回登录 → 旧密码 401 / 新密码 200；SSO 未配置不显示按钮、开启仍不显示、`authorize-url` 503；绑定页展示 4 家 provider 与「未启用」状态。

### 54.4 口径变更（t120 取代本轮的 SSO/找回密码断言）\n\n1. **SSO「未配置不显示」已作废**：新口径为 `GET /auth/sso/providers` 返回**所有 enabled 项**并带 `configured` 标记，登录页按钮**全部展示**，未配置的点击提示「尚未完成配置」。本轮 §54.3 中「开启但未配置 → 登录页不显示」的断言属旧口径，**由 t120 增量验收取代**，代码未为旧口径改动。\n2. **忘记密码新增 `username`**：t116–t119 后 `send-code`/`confirm` 均要求 `username` 且需与手机号/邮箱一致（不匹配仍 200 但不发码）。本轮冒烟 `reset/*` 用例传的是 target-only 版本，**t120 需同步补 `username`**。\n3. 与上述两块无关的部分（部署、全量回归、8 主题×3 尺寸、徽标/更名、注册审批、主题三态）仍以本轮结论为准。\n\n### 54.5 踩坑记档（本轮新增）

1. **dev-mode 只用于验证码链路**：冒烟需要 `devCode` 才能跑完注册/找回；本轮临时在 `backend.env` 加 `APP_VERIFICATION_DEV_MODE=true`，**冒烟与页面验证结束后已移除并重启**，并复核发码响应不再包含 `devCode`。
2. **antd 受控输入在 CDP 下必须用真实按键**：给 `input.value` 赋值 + `dispatchEvent('input')` 对登录页有效，但注册/找回表单（Form + 自定义校验）会判为空 → 表现为「提交无反应/提示请输入验证码」。改用 `Input.insertText`（先 focus）后全部通过。
3. **`mvn clean test` 会删除运行中的 jar**（老坑，再次确认）：必须 `clean test → package → restart`，否则运行中的 JVM 懒加载类时抛 `NoSuchFileException`，表现为登录 500。
4. **H2 上下文串扰偶发**：全量跑时 `AuditLogControllerTest` 曾出现 `Table "USER_ROLES" not found (this database is empty)`，单跑该类 9/9 通过，属测试执行顺序/上下文隔离的偶发，未复现。
### 51.8 t116：凭据 Web 配置 + 「启用即展示」

**两条用户反馈的修复**

| 反馈 | 原因 | 现在的行为 |
|---|---|---|
| 社交凭据没有 Web 配置入口，只能上服务器改库 | 只有 t104 的 `PATCH {enabled}` | 新增 `PUT /api/v1/admin/sso/providers/{code}`（部分更新 clientId/clientSecret/三个端点/scopes）与 `GET` 配置列表；**本项目不需要上服务器改库** |
| 开关打开后登录页仍不显示按钮 | 公开列表按 `enabled && configured` 过滤，未配凭据直接被滤掉 | `GET /api/v1/auth/sso/providers` 改为**返回全部 enabled=true**，每项带 `configured`；未配置的按钮也展示（前端标「未配置」），点进去 authorize-url 才 503 |

**configured 判定**：`client_id`、`client_secret`、`authorize_url`、`token_url`、`userinfo_url` 五项都非空才为 true（scopes 可选）。

**接口（全部 ADMIN，非 ADMIN 403 / 匿名 401）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/admin/sso/providers` | 全部 4 家的配置：`code/name/icon/enabled/configured/clientId/clientSecretMasked/authorizeUrl/tokenUrl/userinfoUrl/scopes/updatedAt/updatedBy` |
| PUT | `/api/v1/admin/sso/providers/{code}` | 部分更新；code 不属于 4 家 → **404**；URL 非 https（且非本地）→ **400** |
| PATCH | `/api/v1/admin/sso/providers/{code}` | t104 兼容，等价于 PUT 只传 enabled |

**部分更新与 secret 语义（重点）**

- 未传的字段（JSON 省略 / `null`）**保持不变**；传空字符串表示清空该字段（secret 除外）。
- `clientSecret` 省略或传 `""` → **保持不变**（避免「只改 URL 却把 secret 清空」）；
  要清空必须显式 `{"clearClientSecret": true}`。
- 响应里 `clientSecretMasked` 形如 `gh_****abcd`（最多露首尾各 4 位，长度 ≤4 时只回 `****`）；
  **接口 / 日志 / 审计都不出现明文**；审计只记 `clientSecret=***` 与其它变更字段名。
- URL 必须 `https`；本地联调放宽 `http://localhost` 与 `http://127.0.0.1`（其余 http 一律 400）。
- `scopes` 逗号分隔、自动去重（`a, b ,a` → `a, b`）；传空串清空。
- 保存**即时生效**：公开列表下一个请求就能看到新的 `configured`（无需重启）。

**Web 配置流程（管理员操作，截图步骤）**

1. 用管理员账号登录 → 左侧菜单 **系统管理 → 第三方登录**（页面路径 `/admin/sso`；若菜单未接入，可直接打开该路由）。
2. 列表 4 行（GitHub / Google / Microsoft / 微信），每行显示「已开启 / 未配置」等标签。
   截图要点：*① 行内 `未配置` 红色标签、②「配置」按钮、③「启用」开关*。
3. 点「配置」→ 抽屉表单填 `Client ID`、`Client Secret`、三个端点（默认已预填各平台官方地址，通常不用改）、`scopes`；
   保存后抽屉回显 `configured=true`。截图要点：*④ secret 输入框为密码型、保存后回显 `gh_****abcd`*。
4. 打开「启用」开关 → 保存。截图要点：*⑤ 开关变蓝 + 行内标签变「已配置」*。
5. 打开登录页（无痕窗口）→「使用第三方账号登录」区域出现对应按钮。截图要点：*⑥ 登录页三个按钮*。
   - 若显示「未配置」徽标，说明还有必填项没填（`configured=false`），点按钮会提示「该登录方式尚未完成配置」。

```bash
# 配好一家（示例：GitHub），无需重启
curl -s -X PUT http://127.0.0.1:8089/api/v1/admin/sso/providers/github \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"enabled":true,"clientId":"Ov23liXXXX","clientSecret":"8f2cXXXXXXXXXXXX",
       "scopes":"read:user, user:email"}'

# 公开列表立即出现该方式，configured=true
curl -s http://127.0.0.1:8089/api/v1/auth/sso/providers
# → {"data":[{"code":"github","name":"GitHub","icon":"github","configured":true}]}

# 管理端列表：secret 只回掩码
curl -s http://127.0.0.1:8089/api/v1/admin/sso/providers -H "Authorization: Bearer $ADMIN_TOKEN"
# → "clientSecretMasked":"8f2c****XXXX"

cd backend && mvn -B test -Dtest=SsoProviderConfigTest   # 期望 14/14
```

**安全与风险**

- `client_secret` 目前在本表**明文存储**（t104 既定）。本任务至少做到：接口不回传明文（掩码）、
  日志不打印 secret、审计只记 `***`；**加密存储（Jasypt / AES + 主密钥 / KMS）已列入报告 §8 建议项**。
- `GET /api/v1/auth/sso/providers` 仍然公开（登录页需要），但**不含任何凭据**，只有 code/name/icon/configured。
