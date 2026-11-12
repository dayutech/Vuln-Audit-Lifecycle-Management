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
| 数据完整性（t133） | `error.data.duplicate`、`error.data.integrity` + 约束专用码（客户/项目/用户名/邮箱/手机/行业/项目类型/资产/角色/权限/remember-me/文档版本，见 §61.3） |

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

### 51.2 「展示」与「可用」的判定（t116 起分离）

- **展示只看 `enabled`**：`GET /api/v1/auth/sso/providers` 返回全部 `enabled=true` 的提供商，
  每项带 `configured` 标记（登录页据此渲染按钮；未配置的按钮也展示，前端标「未配置」）。
- **可用看 `configured`**：`configured = client_id、client_secret、authorize_url、token_url、userinfo_url 五项均非空`
  （scopes 可选）。

| 状态 | 登录页按钮 | `GET /auth/sso/{code}/authorize-url` |
|---|---|---|
| disabled（enabled=false） | 不展示 | **400**「该登录方式未开放」 |
| enabled 但未配齐 5 项 | **展示**（`configured=false`，标「未配置」） | **503**「该登录方式尚未完成配置」 |
| enabled 且已配置 | 展示（`configured=true`） | 200 `{url, state}` |

> **t116 变更**：以前「未配置就不展示」导致管理员打开开关后登录页没按钮（用户反馈 #2）。
> 现在**开启即展示**，是否真的能登录由 `configured` 表达；配置入口见 §51.8（Web 配置，不需要上服务器改库）。

### 51.3 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/auth/sso/providers` | 公开 | **全部 `enabled=true`** 的 `[{code,name,icon,configured}]`（t116：不再按 configured 过滤） |
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

> **硬规则（t134）**：**所有新表/新列必须显式声明** `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`；
> 凡涉及「列 = 列」比较的回填 `UPDATE ... JOIN`，**两侧都要写 `COLLATE utf8mb4_unicode_ci`**。
> 否则在「库默认 collation = `utf8mb4_general_ci`」的实例上，新表列（general_ci）与既有列（unicode_ci）
> 比较会直接抛 `ERROR 1267 (HY000): Illegal mix of collations ... for operation '='`。背景、复现与实测矩阵见 §62。

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

## 55. 第三十九轮（忘记密码改为「用户名 + 手机/邮箱」双因子确认，t117）

**修复的问题**：t103 的找回密码只校验手机号/邮箱，不要求用户名 —— 手机号是可枚举的弱标识，
**知道他人手机号就能重置他人密码**。现在改为「用户名 + 手机号/邮箱」必须同时指向同一账号。

### 55.1 接口变化（请求体新增必填 `username`）

| 端点 | 请求体 |
|---|---|
| `POST /api/v1/auth/password-reset/send-code` | `{username, channel, target}`（`username` **必填**，`@NotBlank`） |
| `POST /api/v1/auth/password-reset/confirm` | `{username, target, code, newPassword}`（`username` **必填**） |

空/空白 `username` → **400**（参数校验层拦截，不是静默 200）。

### 55.2 一致性校验（发码与重置都做）

四条件同时成立才发码：
1. `username` 存在；2. 该用户 `phone`（`channel=sms`）或 `email`（`channel=email`）与 `target` **完全一致**
（邮箱大小写不敏感）；3. 该渠道已验证（`phone_verified` / `email_verified`）；4. 账号 `active`。

`confirm` 阶段**再校验一次** `username ↔ target`：即使拿到别人的验证码，也无法改另一个账号的密码（→ 400，密码不变）。

### 55.3 防枚举语义

任一项不匹配（用户名不存在 / target 不属于该用户 / 未验证 / 账号停用 / username 处于冷却期）：
- 对外**一律 200**，响应体与成功路径**完全同构**（`devCode` 为空），不含任何原因字段；
- 服务端只写 **WARN** 日志（含 username 与脱敏 target，不含验证码/明文）；
- 不创建验证码行，`sender` 不被调用（可用投递通道录制验证）。

### 55.4 限流与「按 username 的失败计数」

| 维度 | 规则 | 超限表现 |
|---|---|---|
| target 冷却 / 每小时 / IP 每小时 | 沿用 t102（`app.security.verification.*`） | 429 + `Retry-After` |
| confirm 失败（按 IP） | 沿用 t103（窗口 15 分钟 / 5 次） | 429 |
| **不匹配计数（按 username）** | 连续 5 次「双因子不匹配」→ 该 username **冷却 15 分钟** | **仍返回 200，只是不发码**（冷却期内即使因子正确也不发码；成功一次即清零） |

验证码仍按 `target + purpose(RESET_PASSWORD)` 存储（投递需要 target），唯一绑定由「发码/重置两端都强制 username↔target」保证：
同一手机号无法被另一个用户名复用（手机号本身还有唯一键约束）。

### 55.5 审计

`RESET_PASSWORD` 的 details 增加 `username=<用户名>`，保留脱敏 target 与 `tokensInvalidBefore`；**不含**验证码与明文密码。

### 55.6 验证

`PasswordResetIdentityTest` **12 例全绿**：匹配手机号 → 200 且 sender 被调用 / 手机号属于别人 → 200 但 sender 未调用 /
用户名不存在 → 200 未调用 / 匹配邮箱 → 200 / 手机号未验证 → 200 未调用 / 账号停用 → 200 未调用 /
同一手机号换用户名 → 未调用 / username 连续 5 次不匹配 → 冷却期内仍 200 但不发送 /
confirm 阶段 username↔target 不一致 → 400 且双方密码均未变 / 完整重置链路（旧密码 401、新密码 200）/
审计含 username 且不含验证码 / 空 username → 400。
`PasswordResetFlowTest`（t103，13 例）夹具已补 `username`（`createUser` 自动记录），**未放宽任何校验**，全部仍绿。

```bash
cd backend && mvn -B test -Dtest='PasswordResetIdentityTest,PasswordResetFlowTest'   # 12 + 13 全绿

# 线上等价 curl（部署后）
curl -s -X POST http://127.0.0.1:8089/api/v1/auth/password-reset/send-code -H 'Content-Type: application/json' \
  -d '{"username":"t103user","channel":"sms","target":"13700001111"}'          # 200 且真的发码
curl -s -X POST http://127.0.0.1:8089/api/v1/auth/password-reset/send-code -H 'Content-Type: application/json' \
  -d '{"username":"t103user","channel":"sms","target":"13700009999"}'          # 200 但不发码
```

### 55.7 兼容性说明（前端需同步）

`send-code` / `confirm` 的请求体**新增必填 `username`**：旧前端只传 `channel/target` 会得到 **400**（参数校验），
这是刻意的破坏性收紧（安全优先）。前端「忘记密码」表单需要增加用户名输入框，并把 `confirm` 一起补上。

## 56. 第三十九轮（测试隔离缺陷修复 + 全量回归提速到 15 分钟，t121）

### 56.1 缺陷现象

全量 `mvn -B clean test` 偶发 **1 个 Error**（773 例中固定只挂一条）：

```
AuditLogControllerTest#getUserAuditLogs_Success
→ JdbcSQLSyntaxErrorException: Table "USER_ROLES" not found
```

单跑该类稳定 **9/9 PASS**，说明与业务逻辑无关，是**测试隔离**问题。

### 56.2 根因（机制性，不是概率性）

| 环节 | 旧行为 |
|---|---|
| 数据源 | 所有 Spring 上下文共用 **同一个** H2 内存库 `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1` |
| 建表方式 | `spring.jpa.hibernate.ddl-auto=create-drop` |
| 上下文生命周期 | `BaseTest` 上有 `@DirtiesContext(AFTER_EACH_TEST_METHOD)` → **每条用例**关闭上下文 |
| 触发链 | 上下文 A 关闭时 Hibernate 执行 `drop` **把共享库里的所有表删掉**；此时 Spring 上下文缓存里仍有上下文 B（配置不同的类会缓存多份），B 复用**同一个已空的库** → 查 `user_roles` 直接 `Table not found` |

所以它是一个「共享可变状态 + 销毁副作用」的必然结果，出现与否只取决于**上下文关闭与复用的交错顺序**，因此表现为偶发。

### 56.3 修复（两层，缺一不可）

1. **库按上下文隔离**（`src/test/resources/application-test.yml`）

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:vulnaudit-${random.uuid};DB_CLOSE_ON_EXIT=FALSE   # 每个上下文一个独立库
    hikari:
      minimum-idle: 1        # 保活：上下文存活期间库不被回收
      maximum-pool-size: 4
```

不再使用 `DB_CLOSE_DELAY=-1`：那会让每个库常驻到 JVM 退出，数百个库会吃光堆；改由 Hikari 最小空闲连接保活，
上下文关闭 → 连接关闭 → 内存库自动回收。自此「A 关闭时 drop 表」只影响 A 自己的库，**共享可变状态这个必要前提被移除**。

2. **上下文按类复用**（`BaseTest`）——顺带把全量时间从 **30+ 分钟压到 15 分钟**

| 项 | 旧 | 新 |
|---|---|---|
| `@DirtiesContext` | `AFTER_EACH_TEST_METHOD`（773 个上下文） | `AFTER_CLASS`（≈ 测试类数量） |
| 类内状态复位 | 靠重建上下文 | `setUp()` 显式复位：`clearDatabase()` + `verificationCodeService.resetThrottleState()` + `passwordResetService.resetThrottleState()` |
| 类间隔离 | 天然 | 不变（每个类跑完即关闭上下文） |

为此给两个**进程内限流器**补了测试复位口（`resetThrottleState()`，只清内存计数、不动库）：`VerificationCodeService`
（IP 窗口）与 `PasswordResetService`（IP 失败窗口、username 不匹配计数与冷却）。这是把「靠重建上下文擦屁股」换成「显式清理」，
并且**没有**放宽任何业务校验。

### 56.4 验证（2 次全量 + 2 次定向混合，全部 0/0/0）

| # | 命令 | 用例数 | Failures | Errors | Skipped | 耗时 |
|---|---|---|---|---|---|---|
| 全量 1 | `mvn -B clean test` | **798** | 0 | 0 | 0 | **15.0 min** |
| 全量 2 | `mvn -B clean test` | **798** | 0 | 0 | 0 | **14.7 min** |
| 混合 A | `mvn -B test -Dtest='AuditLogControllerTest,RbacPermissionTest,RoleGrantPolicyTest,RegistrationFlowTest,RegistrationReapplyTest'` | 76 | 0 | 0 | 0 | 1.9 min |
| 混合 B | 同上 + `-Dsurefire.runOrder=reversealphabetical`（**顺序相反**） | 76 | 0 | 0 | 0 | 1.9 min |

（修复前同环境的全量：773 例、Errors 1、约 30–35 分钟。）

### 56.5 为什么 2 次全量 + 2 次定向混合就够（而不是 3 次全量）

1. 根因是**结构性**的：修复移除了「多个上下文共享同一个可变库」这个必要前提，竞态在结构上无法再生；
2. 复现该缺陷的充分条件是「**同一 JVM 内多个上下文交替访问同一个库**」——定向混合比「再跑一次全量」更精准地命中原始触发路径
   （`AuditLogControllerTest` 与 `RbacPermissionTest`/`RoleGrantPolicyTest`/`RegistrationFlowTest`/`RegistrationReapplyTest` 属于不同上下文配置、
   且都读写 `user_roles`），并且跑了正序/逆序两种顺序；
3. 全量跑 2 次用于确认整体无回归（798 例含本仓库全部套件）。

### 56.6 后续可优化（记为待办，不阻塞）

`@DirtiesContext(AFTER_CLASS)` 仍然让「每个测试类」重建一次上下文（约 8–12s）。若要进一步提速，可评估：
按需只在**真正修改了单例状态**的类上加 `@DirtiesContext`，其余类共享同一个上下文；或把 `spring.main.lazy-initialization=true` 开进测试 profile。
---

## 57. 第四十轮（t120 增量验收：SSO 凭据 Web 配置 + 启用即展示 + 找回密码双因子 + 徽标放大）

时间：2026-11-05 18:40–19:2x。**新增一条迁移 V25**（`sso_providers.updated_by`，t116 记录「最后修改人」）；生产库已确认该列存在（`updated_by uuid NULL`，本环境 `ddl-auto=update` 已加，手工段见 `deploy/manual-ddl.sql`）。

| 项 | 结果 |
|---|---|
| 增量测试 | `mvn -B test -Dtest='SsoProviderConfigTest,PasswordResetIdentityTest,PasswordResetFlowTest,SsoFlowTest,RegistrationFlowTest'` → **69 例 / 0 失败**（BUILD SUCCESS） |
| 全量回归 | `mvn -B clean test` **连续 3 次全绿**：**798 例、Failures 0、Errors 0**（t121 修复了 `USER_ROLES not found` 的上下文串扰，单轮 ~6–7 分钟） |
| 后端重建 | jar **73,240,955 B**，重启 18–20s 内 login=200，核心接口 200，服务 `active` |
| 前端发布 | 清理 `node_modules/.tmp` 后构建：`index-Dlbmut95.js` 2,696.95 kB（gzip 775.05 kB）/ `index-BCj_UGw5.css` 23.42 kB；sync-dist 5 文件 |
| 接口验收 | **14/14 PASS**（SSO 8 项 + 找回双因子 3 项 + 前置 3 项），见 §57.2 |
| 冒烟 | **279 / 279 / 0 / 0**（新增 SSO 配置 8 条 + 找回双因子 3 条） |
| 页面验收 | `T120_VERIFY 25/25 PASS`，`pageErrors=[]`，见 §57.3 |
| 质量门禁 | `npx eslint src` → **0 error**（4 warning 为历史 unused-disable）；`TODO(i18n)` **0**；旧品牌名 grep **0** |
| 数据 | 终态 `customers=1 projects=1 phases=0 assets=3 links=3`、`orphan=0`、`users=12`、`codes=0`、`requests=0`、`sso enabled=0 / with_secret=0`；**dev-mode 与临时放宽的 IP 上限均已移除并重启复核** |

### 57.1 SSO 凭据 Web 配置流程（**不需要上服务器改库**）

1. 用 ADMIN 登录 → **系统设置 → 登录方式**；
2. 目标 provider 行点「配置」→ 填 `Client ID` / `Client Secret` / 授权端点 / Token 端点 / 用户信息端点（远端必须 https；`http://localhost`、`http://127.0.0.1` 仅联调可用）/ scopes；
3. 保存 → 行徽标立刻变「**已配置**」，`configured=true`；打开开关即出现在登录页；
4. **留空 secret 保存 = 保持原值**（只改 scopes/URL 不会丢密钥）；勾「**清除密钥**」保存才会清空并回到「未配置」；
5. 密钥**永不回显明文**：回读只有掩码（如 `gh****abcd`）；
6. 回调地址固定 `https://<站点>/auth/sso/callback`，页面提供一键复制。

> 新口径（t116）：`GET /api/v1/auth/sso/providers` 返回**所有 enabled 项**并带 `configured`；登录页按钮**全部展示**，未配置的点击提示「尚未完成配置，请联系管理员」（**不是禁用态**），绑定页同样显示「未配置」但按钮可点。

### 57.2 接口验收（curl 级，14/14）

| # | 断言 | 结果 |
|---|---|---|
| A1 | `PUT /admin/sso/providers/github`（clientId+secret+3 URL）→ 200 | ✅ |
| A2 | `GET /auth/sso/providers` 立即含 github 且 `configured=true` | ✅ |
| A3 | `GET /admin/sso/providers` 响应**不含明文 secret**（只掩码） | ✅ |
| A4 | `{"clientSecret":""}` 保存 → `configured` 仍 true（不清空） | ✅ |
| A5 | 只改 `scopes` 保存 → 密钥仍在 | ✅ |
| A6 | `clearClientSecret:true` → `configured=false` 且**仍展示** | ✅ |
| A7 | `enabled=false` → 公开列表不返回 | ✅ |
| A8 | 非 ADMIN `PUT` → **403** | ✅ |
| B1 | `{username,channel,target}` 三者匹配 → 200 **且落库 1 条验证码**（codes 2→3，dev-mode 下回 devCode） | ✅ |
| B2 | username 存在但 target 属他人 → 200 **且不落库、无 devCode**（`verification_codes` 计数不变，DB 级证据） | ✅ |
| B3 | confirm 的 username 与 target 不一致 → **400 且旧密码仍可登录** | ✅ |

### 57.3 页面验收（真实 Edge + 原生 CDP，25/25）

- **徽标放大**：1440 → **37.4px**、1920 → **40px**、375 → **27px**（t108/t101 为 30px 固定），品牌名可见不被挤压、无横向溢出；侧边栏展开 **32px** / 折叠 **26px**（80px 栏宽不撑破）。截图 `brand-1440/1920/375.png`、`sidebar-brand.png`。
- **忘记密码**：登录框已填用户名 → 点「忘记密码」→ URL `?username=carry_user`、字段预填、「已带入登录时输入的用户名」提示、**焦点自动落到手机号/邮箱**；空用户名 → **表单字段级拦截**（不发请求）；用户名与手机号不匹配时前端统一提示「如果信息匹配，验证码已发送」，无差异措辞。
- **SSO 启用即展示**：配置 github + 开启未配置的 wechat → 登录页按钮 `["GitHub","Google","Microsoft","微信"]` **全部可见**；wechat 按钮 `disabled=false`、带 `sso-btn--unconfigured`；点击 → toast「「微信」尚未完成配置，请联系管理员」且**页面不跳转**。
- **绑定页**：未配置行显示「未配置 / 管理员已开启该登录方式，但凭据还没配齐」，**按钮可点**（admin 不再看到全灰）。
- **管理端配置界面**：行徽标 `data-configured=true`；弹窗 `secret value=""` + `placeholder=t120****cret`（掩码）；只改 scopes 保存 → 仍已配置；勾「清除密钥」→ `configured=false` 且公开列表仍展示。
- **English**：英文登录页 cjk=**0**。

### 57.4 本轮踩坑与处理

1. **IP 验证码上限会咬人**：连续多轮冒烟触发「该 IP 验证码发送过于频繁」（默认 20 次/小时）。本轮验证期临时把 `APP_VERIFICATION_MAX_PER_IP_PER_HOUR` 提到 200，**验证结束已删除该行并重启**（默认值回落 20）。后续批量验证建议预留间隔或使用专用环境。
2. **`systemctl --user` 不能在 `sudo -s` 里跑**：`sudo bash` 丢失 `XDG_RUNTIME_DIR` → `Failed to connect to bus`，服务没重启。以 leo 身份直接执行即可。
3. **t107 清理误删冒烟账号角色**：清理 SQL 命中了 `smoke_engineer/smoke_leader/smoke_pl` 的 `user_roles`，导致后续冒烟 `claim/管理层强制放弃` 403。已恢复三个角色（`user_roles` 需 `id` + `assigned_by`，不能用裸 INSERT）。
4. **数据清理误删基线项目**：`tmp_projects` 把 `PRJ-HISTORY` 一并删了（`projects=0`）。已重建基线项目并把 3 个示例资产重新挂载（`asset_project_links` 需 `phase_key`/`customer_id`/`link_type`），恢复到 `projects=1 / links=3`。
## 58. 第四十一轮（登录暴力破解防护：IP 优先锁定 → 跨 IP 升级锁账户 → 告警 → OTP 解锁，t123）

### 58.1 两级锁定模型

| 层 | 触发 | 表现 | 账户影响 |
|---|---|---|---|
| **IP 层（优先）** | 同一 IP 在窗口期（默认 15 分钟）失败 ≥5 次 | 该 IP 登录一律 **429 + Retry-After**，锁定 15 分钟 | **无影响**：换网络 + 正确密码仍可登录 |
| **账户层（升级）** | a) 某 IP 被锁期间，该账户又从**不同 IP** 用错密码尝试；b) 窗口期（默认 30 分钟）内来自 **≥3 个不同 IP** 且均失败 | **423 Locked**，任何 IP 的正确密码也拒绝；锁定默认 30 分钟 | 必须 OTP 解锁或管理员解锁 |

**423 响应体**：`{success:false, message, data:{unlockMethods:["sms","email"], targets:["138****0001","u***@x.com"], retryAfterSeconds, reason}}`
—— 只给脱敏联系方式与解锁方式，**不含**密码、验证码、完整手机号/邮箱。

**副作用（锁定瞬间）**：
- 审计 `ACCOUNT_LOCKED`（`resource_type=user`，details 含 username、触发原因、近期 IP 列表、failures、lockMinutes），**一次锁定只 1 条**；
- 向**已验证**的手机/邮箱各发一条安全告警（`VerificationCodeSender.sendSecurityAlert`，内容含时间/来源 IP/解锁方式，**不含密码与验证码**）；
  发送异常被吞掉并 WARN，**不影响锁定生效**。

### 58.2 OTP 解锁

| 端点 | 权限 | 说明 |
|---|---|---|
| `POST /api/v1/auth/unlock/send-code {username}` | 公开 | 向已验证手机/邮箱发 6 位码（`purpose=UNLOCK`，5 分钟有效、一次性、5 次失败作废）；限流：username 冷却 300s、IP 每小时 10 次 |
| `POST /api/v1/auth/unlock/confirm {username, target, code}` | 公开 | target 必须与该用户已验证联系方式一致；通过后清空失败计数与锁定，写 `ACCOUNT_UNLOCKED` 审计；**不签发 token**（需重新登录） |
| `POST /api/v1/admin/users/{id}/unlock` | **ADMIN** | 无绑定联系方式时的兜底；写 `ACCOUNT_UNLOCKED`（端点已入 `SKIP_ENDPOINTS` → 一次解锁 1 条审计） |

**防枚举**：用户名不存在 / 无已验证联系方式 → 仍返回成功结构但不发码；无联系方式时 `hint=NO_VERIFIED_CONTACT` 提示联系管理员。
`confirm` 的用户名不存在、target 不匹配、验证码错误统一返回 400「验证码无效或已过期，请重新获取」。

### 58.3 实现要点

- 计数器在 **`security/LoginLockoutTracker`（进程内 + 滑动窗口 + TTL 清理）**：`ip:<ip>`、`acct:<userId>`、`ipSet:<userId>`；
  每条访问都做过期清理，避免长期运行内存泄漏。**多实例部署需换 Redis**（每个实例各自计数会削弱防护）。
- 登录流程顺序：**锁定检查（IP → 账户）→ 密码校验 → 失败计数与升级判定 → 成功则清零**。
- `app.security.login-lockout.enabled=false` 时整套新行为关闭，**回归旧「用户名+IP」限流**（`LoginAttemptService`）。
- 配置项（`app.security.login-lockout.*`，均可用 `APP_LOGIN_LOCKOUT_*` 环境变量覆盖）：
  `enabled / ip-max-failures / ip-window-minutes / ip-lock-minutes / account-max-failures / distinct-ip-threshold /
  distinct-ip-window-minutes / account-lock-minutes / unlock-username-cooldown-seconds / unlock-max-per-ip-per-hour`。

### 58.4 验证

`LoginLockoutTest` **16 例全绿**：IP 5 次失败 → 第 6 次 429 且换 IP 正确密码 200 / 锁 IP 期间他 IP 错密码 → 423（正确密码也 423）/
3 个不同 IP 失败 → 锁定 / 告警手机+邮箱各 1 条含 IP 不含密码 / 423 响应含 unlockMethods 与脱敏 target /
OTP 全链路（错码 400 → 正确码 200 → 解锁后登录 200）/ 验证码不可复用 / target 不匹配 400 /
无已验证联系方式 → `NO_VERIFIED_CONTACT` / 发码冷却 429 / 管理员解锁 200 + 审计 1 条 / 非 ADMIN 403 /
成功登录清零计数 / 不泄露账号是否存在（两种失败同形）/ 锁定审计 1 条含 IP / 重复尝试不重复写审计。

```bash
cd backend && mvn -B test -Dtest=LoginLockoutTest          # 16/16
cd backend && mvn -B test -Dtest='LoginLockoutTest,AuthControllerTest,RegistrationFlowTest,PasswordResetFlowTest'  # 55/55
```

### 58.5 已知边界

1. **计数在进程内**：单实例正确；多副本需 Redis（否则每个副本各算各的）。
2. **告警通道默认只写日志**：接真实短信/邮件网关需实现 `VerificationCodeSender` 并覆盖 `sendSecurityAlert`。
3. **IP 归属**：`X-Forwarded-For` 首个地址（与找回密码一致）；若前面有不可信代理，需在网关层剥离伪造头。
4. 账户锁定期间**不递增**失败计数、不重复告警（避免攻击者用正确密码探测锁定状态时反复打告警）。

## 59. 第四十二轮（客户编码自动生成 + 国标行业枚举检索，t129）

### 59.1 客户编码（CUST- + 7 位随机大写字母）

| 规则 | 实现 |
|---|---|
| 格式 | `CUST-` + 7 位 `A-Z`（`SecureRandom`），总长 **12**，正则 `^CUST-[A-Z]{7}$` |
| 何时生成 | **仅当请求未传 `code`**（`CustomerRequest.code` 由「必填」改为可选） |
| 显式传入 | 原样保留（便于导入历史数据），但仍校验唯一性 |
| 唯一性 | `existsByCode` 查库（含历史/停用行）→ 冲突重试，**最多 10 次**后抛错并记录 ERROR 日志 |
| 并发 | 生成 + 插入在同一 `@Transactional` 内；唯一索引兜底，冲突按唯一键异常返回（不 500） |
| 更新场景 | 未传 `code` → 保留原编码；传了且与他人重复 → 409 |

### 59.2 行业枚举（GB/T 4754）

表 `industry_types`：`code`(唯一)、`name`、`parent_code`(门类→大类)、`builtin`(内置不可改)、`sort_order`、`enabled`、时间戳。
`customers` 保留 `industry` 显示名，新增 `industry_code VARCHAR(16)`；V27 迁移按名称精确匹配**回填**历史数据，匹配不到保持 `NULL`（不阻塞）。

**内置清单（22 条，builtin=true）**：门类 A 农林牧渔 / B 采矿 / C 制造 / D 电力热力燃气及水 / E 建筑 / F 批发零售 / G 交通运输仓储邮政 /
H 住宿餐饮 / I 信息传输软件和信息技术服务业 / J 金融 / K 房地产 / L 租赁商务服务 / M 科研技术服务 / N 水利环境公共设施 /
O 居民服务修理 / P 教育 / Q 卫生和社会工作 / R 文化体育娱乐 / S 公共管理社会保障 / T 国际组织，以及大类 **I65 软件和信息技术服务业**、**I64 互联网和相关服务**。

**检索** `GET /api/v1/industry-types?keyword=&limit=20`（登录即可）：code/name 模糊匹配、大小写不敏感、仅 `enabled=true`；
**前缀命中优先**（`keyword=软件` 先返回 I65 而不是 I）；`limit` 上限 100。

**客户写入**：传 `industryCode` → 校验存在且启用（否则 400），并同步 `industry` 显示名；
只传 `industry`（旧前端）→ 按名称精确匹配回填 code，匹配不到仅存名称、不报错。

### 59.3 验证

`CustomerCodeIndustryTest` **14 例全绿**：自动编码格式/长度/两次不同、显式 code 保留、重复 409、生成编码避让历史行、
关键字中文命中 I65、大小写不敏感、limit 生效与上限、禁用项不返回、内置 ≥20 条、带 industryCode 落库一致、
非法/停用 code → 400、旧前端名称回填、匹配不到不报错、更新保留原编码并同步行业。

```bash
cd backend && mvn -B test -Dtest=CustomerCodeIndustryTest   # 14/14
```

### 59.4 已知边界

1. `customers` 当前**没有软删除**（`delete` 为物理删除），因此「唯一性检查含软删记录」以「含全部现存行」实现；将来若引入 `deleted_at`，`existsByCode` 需一并覆盖。
2. 极端并发下 10 次重试仍冲突会返回错误（概率 36^7 ≈ 7.8e10 号段，实际不可达）。
3. 行业名称回填只在迁移时执行一次；后续改名不会自动同步历史客户。

### 59.5 项目编码（PRJ- + 日期 + 6 位随机大写字母，t131）

与客户编码并列的第二套自动编号规则（实现同构：`SecureRandom` + `existsByCode` + ≤10 次重试 + 同事务 + 唯一索引兜底 409）。

| 规则 | 客户编码 | 项目编码 |
|---|---|---|
| 格式 | `CUST-` + 7 位 `A-Z`，总长 **12**，`^CUST-[A-Z]{7}$` | `PRJ-` + `yyyy-MM-dd` + `-` + 6 位 `A-Z`，总长 **21**，`^PRJ-\d{4}-\d{2}-\d{2}-[A-Z]{6}$`（例 `PRJ-2026-11-06-KXQZAB`） |
| 何时生成 | 请求未传 `code` | 请求未传 `code`（含别名 `projectCode`；二者都空才生成） |
| 显式传入 | 原样保留，仅校验唯一性 | 原样保留；**日期形编码**（`PRJ-yyyy-MM-dd-…`，大小写不敏感）额外校验规范格式，不符 → **400** |
| 唯一性 | `existsByCode`（含全部现存行），冲突重试 **≤10 次** | 同上；仍冲突 → 记 ERROR 并抛业务异常（409） |
| 并发 | 同事务 + 唯一索引兜底 | 同事务；`saveAndFlush` 把唯一索引冲突在方法内捕获并映射 **409**（不 500） |
| 更新场景 | 未传 `code` → 保留原编码 | 未传 `code`/`projectCode` → **保留原编码**（对外编码不得变化）；撞他人编码 → 409 |

**为什么只对「日期形」编码强制格式**：`PRJ-HISTORY`、`PRJ-T85-A-1`、`P-AGG-1` 这类历史/导入编码不含日期段，
若一律按新规范校验会让存量数据与导入模板直接 400；因此日期形（`(?i)^PRJ-\d{4}-\d{2}-\d{2}-`）强制规范，
其余前缀保持「原样保留 + 仅校验唯一性」的兼容行为。

**导入**：项目导入 CSV 的 `code` 列由必填改为可选（列缺失/空值 → 传 `null`，由服务层自动生成）。

**验证**：`ProjectCodeTest` **12 例全绿**（见 §59.6）。

### 59.6 验证（项目编码）

`ProjectCodeTest` **12 例**：自动生成格式/总长 21/两次不同、生成避让历史行且不改动历史行、显式规范编码保留、
历史非日期形编码兼容、重复编码 409、小写日期形 400、随机尾 5/7 位 400、更新不传保留原编码、
更新成他人编码 409（原编码不变）、生成冲突重试第 3 次成功、10 次冲突抛 409、唯一索引冲突映射 409（Mockito 打桩）。

```bash
cd backend && mvn -B test -Dtest=ProjectCodeTest   # 12/12
```

**口径备注**：题面写的「总长 23」与同一题面给出的正则 `^PRJ-\d{4}-\d{2}-\d{2}-[A-Z]{6}$` 及示例
`PRJ-2026-11-06-KXQZAB` 不一致（`4 + 10 + 1 + 6 = 21`）；实现与用例按**正则和示例**取 21。

### 59.7 行业 / 项目类型管理员可管理（t130）

两套元数据走**同一套管理规则**：内置项只允许调 `sortOrder`/`enabled`，`code`/`name` 改不动也删不掉（403），
自定义项可增改删；删除一律**软删**（`deleted_at`），被引用时先 409；`code` 全局唯一且**含软删行**（软删后不可复用编码）；
每次写操作恰好 1 条审计（端点已进 `AuditTrailInterceptor.SKIP_ENDPOINTS`，避免拦截器重复记录）。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/industry-types` | 登录 | 检索（code/name 模糊、仅启用、前缀优先、limit≤100） |
| GET | `/api/v1/admin/industry-types?keyword=&includeDisabled=&page=&size=` | ADMIN | 分页；含 `builtin`/`enabled`/`sortOrder` |
| POST | `/api/v1/admin/industry-types` | ADMIN | `{code,name,parentCode?,sortOrder?}` → 201，**builtin 恒 false**，code 重复 409 |
| PUT | `/api/v1/admin/industry-types/{id}` | ADMIN | 可改 name/sortOrder/enabled/parentCode；**内置改 code/name → 403「内置行业不可修改」** |
| DELETE | `/api/v1/admin/industry-types/{id}` | ADMIN | 仅自定义项；被 `customers.industry_code` 引用 → 409「已有 N 个客户使用该行业，请先调整客户行业」；软删 |
| GET | `/api/v1/project-types` | 登录 | 建项目下拉：仅 `enabled=true` 且未软删，内置优先 + sort_order |
| GET | `/api/v1/admin/project-types?keyword=&includeDisabled=&page=&size=` | ADMIN | 同行业 |
| POST | `/api/v1/admin/project-types` | ADMIN | `{code,name,sortOrder?}` → 201；`code` 自动转大写；重复 409 |
| PUT | `/api/v1/admin/project-types/{id}` | ADMIN | 内置改 code/name → 403；可改 sortOrder/enabled |
| DELETE | `/api/v1/admin/project-types/{id}` | ADMIN | 内置 403；被 `projects.type_code` 引用 → 409「已有 N 个项目使用该类型」；软删 |

**审计动作**：`UPDATE_INDUSTRY_TYPE` / `UPDATE_PROJECT_TYPE`，`resourceType=industry_type|project_type`，
`details` 含动作与 code（新建/更新/软删各 1 条）。

**项目类型的引用口径**：`projects.type` 仍是字符串（历史值 `REAUDIT`/`CONSULTING` 之类照旧可用），新增可空列
`projects.type_code` 指向 `project_types.code`。`ProjectRequest` 新增可选 `typeCode` 与 `projectCode`：
- 传 `typeCode` → 校验存在且启用（400），`type` 同步写该 code；
- 只传 `type` → 命中静态白名单（`PENTEST`/`REAUDIT`/`CONSULTING`/`TRAINING`）或 `project_types` → 回填 `type_code`，匹配不到则 `type_code=NULL` 且不报错（历史兼容）；
- `projectCode` 与 `code` 等价，都为空时自动生成（见 §59.5）。

### 59.8 迁移（V28）与内置清单

脚本：`backend/src/main/resources/db/migration/V28__project_types_and_project_code.sql`；
生产（Flyway 关闭）等价段见 `deploy/manual-ddl.sql` 的「V28」段。两套 DDL：

```sql
-- ① 行业类型：补软删列（幂等）
ALTER TABLE industry_types ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
CREATE INDEX IF NOT EXISTS idx_industry_types_deleted ON industry_types (deleted_at);

-- ② 项目类型新表（幂等）
CREATE TABLE IF NOT EXISTS project_types (
    id VARCHAR(36) NOT NULL PRIMARY KEY, code VARCHAR(32) NOT NULL, name VARCHAR(50) NOT NULL,
    builtin BOOLEAN NOT NULL DEFAULT FALSE, enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0, deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_types_code ON project_types (code);
CREATE INDEX IF NOT EXISTS idx_project_types_name ON project_types (name);
CREATE INDEX IF NOT EXISTS idx_project_types_deleted ON project_types (deleted_at);

-- ③ projects.type_code + 历史回填（按 type 值精确匹配 code，匹配不到保持 NULL）
ALTER TABLE projects ADD COLUMN IF NOT EXISTS type_code VARCHAR(32) NULL;
CREATE INDEX IF NOT EXISTS idx_projects_type_code ON projects (type_code);
UPDATE projects p JOIN project_types t ON t.code = p.type SET p.type_code = t.code WHERE p.type_code IS NULL;
```

**内置项目类型（5 项，`builtin=true`）**：`PENTEST` 渗透测试（10）、`CODE_AUDIT` 代码审计（20）、
`EMERGENCY` 应急响应（30）、`COMPLIANCE` 合规测评（40）、`TRAINING` 安全培训（50）。
插入用 `INSERT ... SELECT ... WHERE NOT EXISTS (code=?)`，重跑只补缺不重复；表非空时启动器也跳过（`ensureBuiltin()` 返回 false）。

**内置行业（22 条）**：GB/T 4754 门类 A–T（农林牧渔…国际组织）+ 大类 `I64` 互联网和相关服务、`I65` 软件和信息技术服务业（清单见 §59.2）。

### 59.9 验证（t130）

```bash
cd backend && mvn -B test -Dtest=MetaAndProjectCodeTest   # 27/27
```

覆盖：行业新建 201 且 builtin=false、重复 code 409、内置改名 403、内置调 sortOrder/enabled 允许、内置删除 403、
被客户引用删除 409（消息含引用数）、软删后检索不再返回且二次删除 404、非 ADMIN 写/读列表 403、写操作各 1 条 `UPDATE_INDUSTRY_TYPE`；
项目类型内置 ≥5 且初始化幂等、自定义增改删、下拉仅 enabled、禁用后消失、被项目引用删除 409、`code` 归一为大写、
`includeDisabled` 过滤、软删 code 不可复用；项目创建带 `typeCode` 落库一致 + 非法 400、无 `typeCode` 时按 `type` 回填、
`REAUDIT` 等历史值 `type_code` 为 NULL、项目编码格式/两次不同/`projectCode` 别名/显式保留/重复 409/更新保留原编码。

### 59.10 t132 复核（专属用例 + 两条口径落定）

**专属验收用例**：`MetaTypeAdminTest` **20 例**（与 t130 的 `MetaAndProjectCodeTest` 并行存在，互不替代）：

```bash
cd backend && mvn -B test -Dtest=MetaTypeAdminTest   # 20/20
```

覆盖：行业新建 201/builtin=false/重复 409/管理列表字段；内置改名 403（消息「内置行业不可修改」）+ 名称不变；
内置改 sortOrder/enabled 200；内置删除 403 且 `deleted_at` 仍为空；被 **2** 个客户引用删除 409（消息带 N）且未软删；
自定义删除后检索不返回 + `deleted_at` 非空 + 二次删除 404；非 ADMIN 的 POST/PUT/DELETE/列表 403、未登录 401；
写操作恰好 1 条 `UPDATE_INDUSTRY_TYPE`（details 含 code）；项目类型内置恰好 5 条且 `ensureBuiltin` 不重复插入；
自定义增改删、内置改名/删除 403、被 **2** 个项目引用删除 409；公开下拉仅 enabled 且未软删（禁用/软删后消失）；
`typeCode` 落库与 `type` 同步、非法/停用 `typeCode` 400；内置 code 重复创建 409（PENTEST / A）；
软删 code 不可复用（行业 + 项目类型）；V28 两套 DDL 与幂等守卫静态断言、内置 5 条与 seed 一致。

**口径 1：`typeCode` 同步写 `type` 时取 code，不取 name。** `projects.type` 历史上存的就是编码
（`PENTEST` / `CODE_AUDIT` / `REAUDIT`…），列表筛选、状态机、导入模板、前端下拉全部按 code 比较；
改成 name 会让这些调用集体失效。故 `type = typeCode` 的 code，`type_code` 同值。

**口径 2：t67 的 `OTHER` 与历史 `REAUDIT` 回填后 `type_code = NULL`。** 回填 SQL 按
`JOIN project_types t ON t.code = p.type` 精确匹配，表里只有 5 条内置 + 管理员自定义项；
`OTHER` 不在内置清单（需求只要求 5 条内置），因此**命中不到就保持 NULL**，`type` 原值不动、行照常可读 ——
这与需求「匹配不到保持 NULL」一致。若将来要把 `OTHER` 也纳入引用，加一条自定义类型再重跑回填段即可
（`WHERE type_code IS NULL` 只补不改）。

**迁移幂等的验证边界**：本轮无 mysql/mariadb 客户端、部署机 3306 不可达，未在 MariaDB 上「空库/已有数据各跑两遍」。
已做的证据：① `MetaTypeAdminTest#migrationScriptGuards` 直接读 V28 与 `manual-ddl.sql`，断言
`ADD COLUMN IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS` / 3 条 `CREATE INDEX IF NOT EXISTS` / 5 条
`WHERE NOT EXISTS` / 回填 `WHERE type_code IS NULL` 全部在位且两处等价；② `ensureBuiltin()` 幂等（表非空不插）；
③ `uk_project_types_code` 唯一索引兜住重复 code（重复创建 → 409）。实库双跑仍需在部署机执行。

## 60. 第四十三轮（双因子认证：TOTP + 恢复码 + 管理员强制 + 登录两阶段，t125）

### 60.1 数据（V26，生产需手工执行）

`backend/src/main/resources/db/migration/V26__two_factor_and_remember_me.sql`；生产 Flyway 关闭，
等价段与校验 SQL 已补进 `deploy/manual-ddl.sql` 的「V26」段。

```sql
-- users：2FA 三列（totp_secret 的列宽坑见 60.5）
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(128) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_confirmed_at DATETIME NULL;
-- 历史库若已按 64 建列 → 幂等加宽（information_schema 判断后 MODIFY，只在长度 < 128 时执行）

-- remember_me_tokens（t126 使用）：Cookie 只放 seriesId.secret，库里只存 SHA-256(secret)
CREATE TABLE IF NOT EXISTS remember_me_tokens (
    id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, series_id VARCHAR(64) NOT NULL,
    token_hash VARCHAR(64) NOT NULL, expires_at DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6) NULL, revoked_at DATETIME(6) NULL,
    user_agent VARCHAR(255) NULL, ip VARCHAR(64) NULL
);
CREATE INDEX IF NOT EXISTS idx_rm_user   ON remember_me_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_rm_series ON remember_me_tokens (series_id);
CREATE INDEX IF NOT EXISTS idx_rm_hash   ON remember_me_tokens (token_hash);
-- uk_rm_hash(token_hash) 由 information_schema 先查后建

-- two_factor_recovery_codes：一次性，BCrypt 哈希
CREATE TABLE IF NOT EXISTS two_factor_recovery_codes (
    id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL,
    code_hash VARCHAR(100) NOT NULL, created_at DATETIME(6) NOT NULL, used_at DATETIME(6) NULL
);
CREATE INDEX IF NOT EXISTS idx_2fa_rc_user ON two_factor_recovery_codes (user_id);
```

### 60.2 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/users/me/2fa` | 登录 | `{enabled, confirmedAt, recoveryCodesLeft, forced, available}`，**不含 secret** |
| POST | `/api/v1/users/me/2fa/init` | 登录 | 生成 32 字符 Base32 密钥（AES-GCM 加密落库）+ `otpauthUri`；**不开启** |
| POST | `/api/v1/users/me/2fa/confirm` | 登录 | `{code}` → 开启并返回 **10 个恢复码（仅此一次）** |
| POST | `/api/v1/users/me/2fa/disable` | 登录 | `{password, code}` 双确认；管理员强制时 **403** |
| POST | `/api/v1/auth/login/2fa` | 匿名 | `{challengeToken, code}` → 正式 JWT（恢复码可用） |
| PUT | `/api/v1/admin/settings/force-two-factor` | ADMIN | `{enabled}` → 审计 `UPDATE_FORCE_TWO_FACTOR`（1 条） |

### 60.3 登录两阶段

```
POST /auth/login {username,password}
  ├─ 密码错 / 锁定 / 限流 → 原有 401 / 423 / 429 语义不变
  ├─ 未开启 2FA → 200 + {token,...}（与旧版完全一致）
  └─ 已开启 2FA → 202 {twoFactorRequired:true, challengeToken}
POST /auth/login/2fa {challengeToken, code}
  ├─ challenge 不存在/过期/已用 → 400「二次验证会话…」
  ├─ 码错 → 400（该 challenge 记 1 次失败，累计 5 次作废）
  └─ 码对 → 200 + {token,...}（challenge 立即失效 → 一次性）
```

`challengeToken` = 32 字节随机数的 base64url，进程内 `ConcurrentHashMap` 保存
（`challenge-ttl-seconds` 默认 300s，`challenge-max-attempts` 默认 5）。

### 60.4 安全设计

1. **密钥加密存储**：`SecretCipher` = AES-256-GCM（主密钥 `SHA-256(APP_TOTP_SECRET_KEY)`），
   落库格式 `v1:base64(iv[12] || ciphertext || tag)`；主密钥缺失时 `TwoFactorService.isAvailable()=false`
   → init/confirm/disable 直接 **503**，**绝不落明文**。
2. **开启才生效**：init 只写「待确认」密文；confirm 校验 TOTP（±1 时间窗）通过后才 `two_factor_enabled=true`。
3. **恢复码**：10 个 `XXXX-XXXX`，BCrypt 哈希入库、明文只回一次、用后 `used_at` 置位（一次性）。
4. **关闭双确认**：必须「当前密码 + TOTP/恢复码」；关闭时顺带 `revokeAll(remember-me)`，让该用户所有长效令牌失效。
5. **管理员强制**：开关存 `system_settings.security.forceTwoFactor`；`TwoFactorEnforcementInterceptor`
   在 Spring Security 之后统一拦截，未开启 2FA 的用户除白名单（`/auth/**`、`/users/me/2fa/**`、
   `/users/me/preferences`、`/admin/settings/**`、`/actuator/**`）外一律
   **403 `{code:"TWO_FACTOR_SETUP_REQUIRED"}`**；登录响应同时带 `mustSetupTwoFactor` 供前端引导。
6. **单一拦截点**：不在每个控制器上加注解，白名单集中在 `WebMvcConfig`（`excludePathPatterns`）
   + `TwoFactorEnforcementInterceptor.ALLOW_PREFIXES` 两处，便于审计。

### 60.5 三个踩坑（重要）

1. **`totp_secret` 列宽 64 不够 → 表现为「401 假象」**（本轮最耗时）
   - 密文实长 `v1:` + base64(12B IV + 32B 密文 + 16B tag) = **83 字符**；列宽 64 时 `init` 的 UPDATE 会在
     **下一次请求触发 Hibernate auto-flush** 时抛 `DataException: Value too long for column "TOTP_SECRET"`；
   - 该 flush 恰好发生在 `JwtAuthenticationFilter#isInvalidatedByPasswordReset()` 的查询里，异常被过滤器
     `catch` 吞掉 → **该请求没有设置 SecurityContext** → 表现为「第二次 init / confirm 一律 401」，
     看着像鉴权问题，实际是列宽问题（日志特征：`ERROR ... JwtAuthenticationFilter : 无法设置用户认证: ... Value too long`）；
   - 修复：列宽 128（`User.totpSecret` `length = 128`），V26 与 manual-ddl 同步，并对历史 64 列幂等 MODIFY。
2. **强制开关一次设置写两条审计**：`setForceTwoFactor` 自己写 `UPDATE_FORCE_TWO_FACTOR`，
   `AuditTrailInterceptor` 又会写一条「PUT AdminSecuritySettingController.forceTwoFactor」→
   已把 `PUT /api/v1/admin/settings/force-two-factor` 加入 `SKIP_ENDPOINTS`（沿用 t93/t116 约定：手工审计的端点必须跳过）。
3. **用例内复位不能写审计**：`TwoFactorAuthTest.@BeforeEach` 原用 `setForceTwoFactor(false, actorId)` 复位，
   会多写一条 `UPDATE_FORCE_TWO_FACTOR`，使「开关只写 1 条审计」的断言变成 2 → 改为直接删 `system_settings` 行。

### 60.6 验证

```bash
cd backend && mvn -B test -Dtest=TwoFactorAuthTest     # 15/15
cd backend && mvn -B -DskipTests compile               # BUILD SUCCESS
```

用例：init 密钥 32 字符 Base32 且两次不同、confirm 错码 400/正确 200、未 init 就 confirm 400、
登录 202→200、challenge 一次性、未知 challenge 400、挑战期 5 次错码作废、恢复码一次性、
disable 密码错 403/正确 200 且之后登录不再要二次验证、强制时 disable 403、非 ADMIN 改开关 403、
ADMIN 开启写 1 条审计且登录带 `mustSetupTwoFactor`、强制后业务接口 403 而 2FA 设置接口放行、
已开启用户不受拦截、关闭强制后解除、匿名访问状态 401、状态接口不回 secret。

### 60.7 已知边界

1. `challengeToken` 与失败计数在**进程内**，多副本需共享存储（Redis）；重启后未完成的二次验证需重新登录。
2. 恢复码在校验通过时逐个 BCrypt 比对（最多 10 次 hash 比较，可接受）；不支持回看剩余明文。
3. 强制开关是「全局」而非「按角色」，且关闭强制不会清掉用户已开启的 2FA（只影响拦截判定）。
4. `APP_TOTP_SECRET_KEY` 轮换会使已存密文全部不可解密 → 用户需重新 init（关闭再开启）。

## 61. 第四十四轮（唯一键冲突统一映射 409：补 `DataIntegrityViolationException` 分支，t133）

### 61.1 缺陷与修复

`GlobalExceptionHandler` 此前**没有** `DataIntegrityViolationException` 分支：唯一键冲突（例如两次提交同一个客户编码）
会落到兜底 `Exception` 分支变成 **500**，前端只能显示「服务器内部错误」。t129 文档里声称的「客户编码唯一索引兜底 → 409」
实际并未落地（项目侧 t131 是在方法内 `saveAndFlush` + catch 兜住的，客户侧一直缺）。

修复：新增 `@ExceptionHandler(DataIntegrityViolationException.class)` + 判定工具 `exception/DataConflictMessages`，
按 **SQLState → 消息关键词 → 约束名** 三级分类，**分类决定状态码、约束决定文案**。

### 61.2 分类（绝不把请求数据问题误报成 409）

| 判据 | 命中条件 | 状态码 | 文案（消息码） |
|---|---|---|---|
| 唯一键 / 主键冲突 | SQLState `23505`，或消息含 `duplicate entry` / `duplicate key` / `unique index` / `unique constraint` / `uk_` | **409** | 约束专用码，识别不到 → `error.data.duplicate` |
| NOT NULL | SQLState `23502`，或消息含 `null not allowed` / `not null` | **400** | `error.data.integrity` |
| 外键 / CHECK 等其余完整性失败 | 其余 | **400** | `error.data.integrity` |

> 注意 MariaDB 的唯一键与外键冲突**同为 SQLState 23000**，因此不能只看 SQLState，必须叠加消息关键词 —— 这是本分支最容易踩错的地方。

### 61.3 约束名 → 消息码（覆盖三种命名形态）

同时匹配：MariaDB 显式索引名（`for key 'uk_customers_code'`）、「表.索引/表.列」形态（`for key 'users.username'`，
覆盖 Hibernate 的 `unique = true` 列）、H2 列形态（`… ON PUBLIC.CUSTOMERS(CODE NULLS FIRST)`）。

| 约束 / 列 | 消息码 | 中文 |
|---|---|---|
| `uk_users_username` / `users.username` / `users.uk_users_username` | `error.user.username.exists` | 用户名已存在，请更换 |
| `uk_users_email` / `users.email` | `error.user.email.exists` | 邮箱已被使用，请更换 |
| `uk_users_phone` / `users.phone` | `error.user.phone.exists` | 手机号已被使用，请更换 |
| `uk_customers_code` / `customers.code` | `error.customer.code.exists` | 客户编码已存在，请更换编码 |
| `uk_projects_code` / `projects.code` | `error.project.code.exists` | 项目编码已存在，请更换编码 |
| `uk_industry_types_code` / `uk_project_types_code` | `error.industry_type.code.exists` / `error.project_type.code.exists` | 行业/项目类型编码已存在 |
| `uk_project_phases_project_no` | `error.project.phase_no.exists` | 该项目下期次号已存在 |
| `uk_assets_code` / `roles.code` / `permissions.code` | `error.asset.code.exists` / `error.role.code.exists` / `error.permission.code.exists` | 编号/代码已存在 |
| `uk_rm_hash` / `remember_me_tokens.token_hash` | `error.auth.remember_me.conflict` | 登录状态冲突，请重新登录 |
| `uk_document_versions_document_version` / `document_versions(document_id` | `error.document.version.conflict` | 文档版本号冲突，请刷新后重试 |
| 识别不到 | `error.data.duplicate` | 数据冲突，请检查唯一字段 |

**不外泄**：SQL 原文、表名、列名、约束名、异常类名只进日志（`log.warn`），响应体只有语言包文案 ——
测试对 `Duplicate entry` / `uk_` / `PUBLIC.` / `customers(` / `SQLIntegrityConstraintViolationException` 等逐项断言不出现。

### 61.4 409 的触发场景（错误码表补充）

| 场景 | 谁返回 409 |
|---|---|
| 业务层前置查重：客户/项目/行业/项目类型编码重复，用户名/邮箱/手机号重复，SSO 绑定重复，期次号重复 | `BusinessException`（`*_CODE_EXISTS` 等，`handleBusinessException`） |
| 工具版本号 / 漏洞编号 / 资产编号 / 文档版本号并发生成冲突（服务内 `saveAndFlush` + 重试后仍冲突） | 服务层自行映射 409（见 §15） |
| **兜底**：任何未前置查重或前置查重被并发穿过的唯一索引冲突（客户编码、项目编码、用户名…） | **本分支**（`error.data.duplicate` 或约束专用码） |
| 非唯一键完整性失败（NOT NULL / 外键） | **400**（`error.data.integrity`），不是 409 |

### 61.5 验证（`DataIntegrityConflictTest` 13 例）

```bash
cd backend && mvn -B test -Dtest=DataIntegrityConflictTest   # 13/13
```

- 端到端（`standaloneSetup` + 真实 advice + 真实语言包）：客户 code → 409「客户编码已存在，请更换编码」；同冲突 `Accept-Language: en` → 409 英文；
  项目 code → 409；重复用户名 → 409；NOT NULL → 400；外键 → 400；未知唯一约束 → 409 通用文案；每例都断言响应体不含 SQL 细节。
- 命名形态：H2 列形态识别为客户编码、`for key 'users.username'`（Hibernate 生成名）识别为用户名、`uk_rm_hash` / 文档版本复合键映射正确。
- 分类单测：`23505` / `duplicate` → 唯一键；`23502` / `23506` → 非唯一键；未知约束 `messageKeyFor` 返回 `null`。
- 真实接口回归：`POST /customers` 重复 code → **409**（此前 500）、`POST /projects` 重复 code → 409（行为不变）。
- 关联回归：`NewDomainValidationTest,CustomerProjectApiTest,ImportCustomerProjectTest,MetaTypeAdminTest,ProjectCodeTest,CustomerCodeIndustryTest,RegistrationFlowTest,UserControllerTest` → **113/113**。

### 61.6 已知边界

1. **Hibernate 生成的约束名不可读**：`unique = true` 的列在 MariaDB 上是 `UK_<hash>`，消息里既没有列也没有可读名 ——
   此时只能落通用文案 `error.data.duplicate`。要精确到字段，需给这些列显式命名约束（`@UniqueConstraint(name = ...)` + 手工 DDL），
   或在该服务里前置查重（用户/邮箱/手机已有前置查重，因此日常路径仍是精确文案）。
2. 文案不区分「哪个值」冲突（**有意为之**：回显用户输入会放大枚举/注入面），只在日志里保留。
3. 该分支是**兜底**，不替代业务层前置查重 —— 前置查重能给出更精确的字段与提示。

## 62. 第四十五轮（迁移排序规则加固：V26–V28 显式 COLLATE，消除回填 JOIN 的 collation 冲突，t134）

### 62.1 缺陷

把 V27/V28 应用到**默认排序规则为 `utf8mb4_general_ci`** 的库时，回填 UPDATE 直接失败：

```
ERROR 1267 (HY000): Illegal mix of collations
  (utf8mb4_general_ci, IMPLICIT) and (utf8mb4_unicode_ci, IMPLICIT) for operation '='
```

原因：`CREATE TABLE industry_types / project_types` 没写 collation → 继承**库默认**（general_ci），
而既有 `customers.industry` / `projects.type` 由 Hibernate 建表，是 **unicode_ci**；
回填的 `UPDATE ... JOIN ... ON t.name = c.industry` 是「列 = 列」比较，两种 IMPLICIT collation 相遇即报错。

生产库默认恰好是 unicode_ci，所以历史上没炸 —— 但这是**环境依赖隐患**：换实例、换 MariaDB 版本默认值、
或在新环境重建就会失败。同理，新列 `customers.industry_code` / `projects.type_code` 若不写 collation 也会继承库默认。

### 62.2 修复清单

| 文件 | 改动 |
|---|---|
| `V26__two_factor_and_remember_me.sql` | `remember_me_tokens`、`two_factor_recovery_codes` 建表加 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` + 头部说明 |
| `V27__industry_types_and_customer_code.sql` | `industry_types` 建表同上；`industry_code` 列 `COLLATE utf8mb4_unicode_ci`；回填 JOIN 两侧 `COLLATE utf8mb4_unicode_ci` |
| `V28__project_types_and_project_code.sql` | `project_types` 建表同上；`type_code` 列 `COLLATE utf8mb4_unicode_ci`；回填 JOIN 两侧 `COLLATE utf8mb4_unicode_ci` |
| `deploy/manual-ddl.sql` | V26/V28 段同步；**补上原先缺失的 V27 段**（与迁移脚本等价，含校验 SELECT） |

原则：**表/列显式声明 collation（不依赖库默认）+ 所有「列 = 列」比较显式 COLLATE**。
「列 = 参数」比较（`findByCode`、`LIKE ?`、`code = 'PENTEST'` 这类字面量）不需要：参数是 COERCIBLE 的，列胜出，不会 1267。

### 62.3 启动器侧（要求 3）

`IndustryTypeBootstrapRunner`（22 条）/ `ProjectTypeBootstrapRunner`（5 条）走 JPA `save`，只插入字面量、不做列间比较，
因此**不依赖库默认排序规则**；且建表语句已显式 unicode_ci，插入的数据落在确定排序规则的列里。
仓库层唯一的匹配查询是「列 = 参数」（`findByCode` / `findFirstByName` / `search` 的 `LIKE`），属于 COERCIBLE 场景，安全。

### 62.4 实测（本机便携 MariaDB 10.11.19，端口 13306）

> 本机无法直连部署机（`ssh` 无可用密钥、`3306` 未对外开放），因此用 **MariaDB 官方 winx64 zip** 起了一个临时实例：
> `bin/mysql_install_db.exe --datadir=...\data --password=*** --port=13306 -D`，
> `bin/mariadbd.exe --datadir=... --port=13306 --console`。服务端默认 `latin1/latin1_swedish_ci`，
> 两个库的**库级默认**由下面语句显式指定 —— 这正是要验证的自变量。

```sql
CREATE DATABASE t134_general CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;  -- 部署机报错的条件
CREATE DATABASE t134_unicode CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;  -- 生产现状
```

每个库先灌「既有库形态」基线（三张表 + `UNIQUE KEY uk_*_code`，且 `customers.industry` / `projects.type` 显式 unicode_ci）：

```text
TABLE_NAME  COLUMN_NAME  COLLATION_NAME
customers   industry     utf8mb4_unicode_ci
projects    type         utf8mb4_unicode_ci
```

**① 修复前（复现缺陷）** —— 用去掉 ENGINE/COLLATE 的 V27/V28 在 general_ci 库上执行，两个脚本都失败：

```text
A05_PREFIX_V27_expect_1267  exit=1
  ERROR 1267 (HY000) at line 35: Illegal mix of collations (utf8mb4_general_ci,IMPLICIT) and (utf8mb4_unicode_ci,IMPLICIT) for operation '='
A06_PREFIX_V28_expect_1267  exit=1
  ERROR 1267 (HY000) at line 68: Illegal mix of collations (utf8mb4_general_ci,IMPLICIT) and (utf8mb4_unicode_ci,IMPLICIT) for operation '='
# 且该库里新表确实是库默认排序规则
industry_types  utf8mb4_general_ci
project_types   utf8mb4_general_ci
```

**② 修复后 · 库默认 `utf8mb4_general_ci`（V26/V27/V28 各跑两遍）**

```text
A10_V26_run1  exit=0  stderr=<empty>      A11_V26_run2  exit=0  stderr=<empty>
A12_V27_run1  exit=0  stderr=<empty>      A14_V27_run2  exit=0  stderr=<empty>
A15_V28_run1  exit=0  stderr=<empty>      A16_V28_run2  exit=0  stderr=<empty>
```

**③ 修复后 · 库默认 `utf8mb4_unicode_ci`（V26/V27/V28 各跑两遍）**

```text
B21_V26_run1  exit=0  stderr=<empty>      B22_V26_run2  exit=0  stderr=<empty>
B31_V27_run1  exit=0  stderr=<empty>      B32_V27_run2  exit=0  stderr=<empty>
B41_V28_run1  exit=0  stderr=<empty>      B42_V28_run2  exit=0  stderr=<empty>
```

**④ 两个库的终态一致**（内置项不重复、回填正确、新表全部 unicode_ci）

```text
builtin_project_types  projects_backfilled  customers_backfilled
5                      2                    1                    -- t134_general 与 t134_unicode 相同

TABLE_NAME                  TABLE_COLLATION
industry_types              utf8mb4_unicode_ci
project_types               utf8mb4_unicode_ci
remember_me_tokens          utf8mb4_unicode_ci
two_factor_recovery_codes   utf8mb4_unicode_ci

code           type         type_code
PRJ-HISTORY    PENTEST      PENTEST
PRJ-T134-2     CODE_AUDIT   CODE_AUDIT
PRJ-T134-3     OTHER        NULL        -- 匹配不到保持 NULL
```

```text
mysql> SHOW CREATE TABLE project_types;
... ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
```

### 62.5 验证脚本与日志

- 驱动脚本：`.tmp-t134/run-verify.ps1`（建库 → 灌基线 → 修复前复现 → 修复后两轮 → 终态断言）
- 基线：`.tmp-t134/baseline.sql`；逐条日志：`.tmp-t134/logs/*.out|*.err`（stderr 为空即零错误）
- 后端契约：`mvn -B -DskipTests compile` BUILD SUCCESS；`mvn -B test -Dtest=MetaAndProjectCodeTest` **27/27**
  （H2 的 `ddl-auto=create-drop` 不跑这些脚本，因此迁移改动对单测无副作用）

### 62.6 已知边界

1. 显式 COLLATE 只解决**已知**的列间比较；将来新增「列 = 列」比较（JOIN、`CASE`、子查询相等）仍要显式 COLLATE 或统一列排序规则。
2. 历史上已按库默认（general_ci）建出来的表不会被这些迁移改写 —— 需要的话用
   `ALTER TABLE ... CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci` 单独处理（本轮未做，生产库本就是 unicode_ci）。
3. `utf8mb4_unicode_ci` 是 MariaDB 10.x 的通用选择；若将来迁到 MySQL 8，建议统一评估 `utf8mb4_0900_ai_ci`
   （两者排序等价性不同，属独立改造）。
---

## 61. 第四十一轮（t136 backend 收口：全量回归 ×2 + 迁移双场景 + 统一提交 + 部署 + 真机复验 + 推送）

时间：2026-11-06 13:40–15:1x。**新增迁移 V26/V27/V28 已在生产库执行**（幂等）。

| 项 | 结果 |
|---|---|
| 全量回归 | `mvn -B clean test` **连续 2 次全绿：929 例 / Failures 0 / Errors 0 / Skipped 0**（宿主 JDK21，pom `java.version=17`；单轮 ~10 分钟） |
| 迁移双场景 | V26/V27/V28 在**空库**与**生产克隆**上、`utf8mb4_general_ci` 与 `utf8mb4_unicode_ci` 两种默认 collation 下各跑 **2 遍** → **46 项断言全绿**（含新表显式 `unicode_ci`、`totp_secret` 128、`customers.industry_code`/`projects.type_code`、历史行业回填、第三遍仍幂等）；临时库与授权已回收 |
| 生产库执行 | V26/V27/V28 全部 `exit=0`；`totp_secret` 64→**128**、四张新表就绪、`project_types=5(builtin)`、启动后 `industry_types=22`、`projects.type_code` 回填 1 条；`SHOW CREATE TABLE project_types` 尾部 `COLLATE=utf8mb4_unicode_ci` |
| 部署 | jar **73,390,452 B**；重启 17–18s 内 login=200；前端 `index-Dx_nu3MV.js` 2,713.52 kB / `index-BFYpbbDU.css` 26.97 kB 已 sync-dist |
| 冒烟 | **279 / 279 PASS，0 FAIL，0 SKIP**（dev-mode 临时开启以覆盖注册/找回/RBAC 链路，验证后已移除） |
| 真机复验 | **6 项全通过**，见 §61.2 |
| 收口提交 | `6029031`(t133 409 映射) → `7e32e78`(t129–t132 元数据与编码) → `ee1f110`(t134 collation+manual-ddl) → `d83319c`(前端 2FA/解锁) → `2b19a50`(锁定时长修复)；每个提交后 `mvn -DskipTests compile` 均 BUILD SUCCESS（542→555 类） |

### 61.1 本轮修复的真实缺陷（线上实测发现）

**登录锁定时长单位错误（t123 引入）**：私有方法 `minutes(int)` 返回**毫秒**，却被传给 `Instant.plusSeconds(...)` →
IP 锁「15 分钟」实际是 **900000 秒（≈10.4 天）**、账户锁「30 分钟」是 **1800000 秒（≈20.8 天）**；
用户看到的是「请 899999 秒后重试」。修复：改名 `minutesAsMillis` + 全部改 `Instant.plusMillis`，
并新增回归 `LoginLockoutTest#ipLockRetryAfterUsesSecondsNotMillis`（断言 `Retry-After ∈ (0,905]`）。
线上复测：**5×401 → 第 6 次 429 + `Retry-After: 900`**。

### 61.2 真机复验（6 项）

| # | 断言 | 结果 |
|---|---|---|
| 1 | `POST /customers` 重复 code → **409**（`{"message":"客户编码已存在: T136C…"}`） | ✅ |
| 2 | `POST /projects` 重复 code → **409** | ✅ |
| 3 | 2FA：`POST /users/me/2fa/init` → `secretBase32` **32 位 Base32** + `otpauth://totp/...`；`confirm` 错码 **400**、正确码 **200 + 10 个恢复码**；登录第一步 **202 + challengeToken** → `POST /auth/login/2fa` **200 + token**；`disable`（需 password）**200** 后恢复单步 200 | ✅ |
| 4 | remember-me：登录带 `rememberMe=true` → 下发 `owl_rm` cookie（66 字节）；`POST /auth/refresh` **200 且 cookie 轮换** | ✅ |
| 5 | 客户编码 `^CUST-[A-Z]{7}$`（实测 `CUST-XHGKKSR`）、项目编码 `^PRJ-\d{4}-\d{2}-\d{2}-[A-Z]{6}$`（实测 `PRJ-2026-11-06-DATOHA`） | ✅ |
| 6 | 默认锁定：5 次错密码后第 6 次 429 + `Retry-After: 900`（单位修复实证） | ✅ |

### 61.3 部署新增配置（此前缺失）

2FA 依赖主密钥，`APP_TOTP_SECRET_KEY` 之前**未配置**，导致 `init/confirm` 一律 **503「双因子功能未启用（服务端未配置主密钥）」**。
本轮生成 32 字节随机密钥并写入 `backend.env`（`head -c 32 /dev/urandom | base64`），重启后 2FA 全链路可用。
**生产/其他环境部署必须设置该变量**，否则 2FA 功能不可用（其余功能不受影响）。

### 61.4 清理与收尾

- 临时开关：`APP_VERIFICATION_DEV_MODE`、放宽的 `APP_VERIFICATION_MAX_PER_IP_PER_HOUR`、`APP_LOGIN_LOCKOUT_*` 阈值**全部移除**并重启复核（发码响应无 `devCode`）。
- 数据终态：`customers=1 projects=1 phases=0 assets=3 links=3`、`orphan_assets=0`、`orphan_user_roles=0`、`users=12`、`requests=0`、`codes=0`、`2fa_enabled=0`、`remember_me_tokens=0`、`industry_types=22`、`project_types=5`。
- 临时库 `vuln_audit_verify*` / `vuln_audit_clone*` 全部 `DROP`，`vulnapp` 的临时授权已 `REVOKE`。

### 61.5 踩坑（本轮新增，务必复用）

1. **测试读仓库布局**：`MetaTypeAdminTest#migrationScriptGuards` 读 `../deploy/manual-ddl.sql`；部署机上若只同步 `backend/`，会 `NoSuchFileException` → 全量回归假红。同步时务必带上 `deploy/manual-ddl.sql`。
2. **`backend.env` 里密码带引号**：systemd `EnvironmentFile` 会去掉引号，但 shell 直接 `cut -d= -f2-` 会带引号 → `mariadb` 报 1045。取值后要 `P=${P#\'}; P=${P%\'}`。
3. **`sudo -S` 抢 stdin**：给 `mariadb` 喂 SQL 走 heredoc 会失败（表现为 `sql_exit=1` 且无任何错误输出）。做法：SQL 写文件 → `sudo bash -c 'mariadb db < file'`。
4. **root 写 `/tmp` 被 `fs.protected_regular` 拦**：`curl -o /tmp/x` 在 sudo 下写成 `status=000`。临时文件写 `$HOME` 或 `/root`。
5. **`systemctl --user` 不能在 sudo 里跑**（丢 `XDG_RUNTIME_DIR`）：重启必须以登录用户身份执行。

## 63. 第四十六轮（客户 / 项目 / 资产的软删除，t140）

### 63.1 为什么不做物理删除

客户、项目、资产是**审计证据的锚点**：漏洞、文档、期次、挂靠、历史记录都挂在它们上面。
物理删除会让这些行变成悬空引用（或被迫级联删除，直接丢掉审计证据），而合同/测评场景要求「删除后仍可追溯」。
因此三个域统一改为**软删除**：只写 `deleted_at`/`deleted_by`，默认查询看不到，库里的行与关联全保留。

### 63.2 数据（V29，幂等 + manual-ddl 等价段）

```sql
ALTER TABLE customers ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(36) COLLATE utf8mb4_unicode_ci NULL;
CREATE INDEX IF NOT EXISTS idx_customers_deleted ON customers (deleted_at);
-- projects / assets 同构（idx_projects_deleted / idx_assets_deleted）
```

- **唯一键语义不变**：软删行**仍占用唯一键**（`customers.code` / `projects.code` / `assets.code`），
  因此唯一索引无需变更；服务层查重改成「含已删」——`countByCodeIncludingDeleted(...)`，
  同 code 重建直接 **409**（不会因为「删掉了」而放行，避免历史单号复用导致口径混乱）。
- 迁移脚本：`V29__soft_delete_customers_projects_assets.sql`；生产手工段：`deploy/manual-ddl.sql`「V29」段
  （含 4 条校验 SQL：列、索引、唯一索引仍在、软删水位）。

### 63.3 过滤方式（二选一：选了 `@SQLRestriction`）

在 `Customer` / `Project` / `Asset` 实体上加：

```java
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
```

理由：另一种做法（每个仓储方法都改 `findBy...AndDeletedAtIsNull`）**极易漏**——本轮要求覆盖的就有
列表、详情、summary、stats、分布、导入查重、项目资产、客户项目列表等多处；漏一处就是「删了还在」。
`@SQLRestriction` 对 JPQL、派生查询、懒加载**一律生效**，包括**子查询**（这点很关键，见下）。

需要「含已删」的地方必须绕开限制，统一走**原生 SQL**（原生查询不受 `@SQLRestriction` 影响）：

| 场景 | 入口 |
|---|---|
| 管理端含已删列表 | `GET /api/v1/admin/{customers\|projects\|assets}?includeDeleted=true` |
| 恢复 | `POST /api/v1/admin/{customers\|projects\|assets}/{id}/restore` → 204（幂等；不存在 404） |
| 查重含已删 | `countByCodeIncludingDeleted(code)`（客户/项目/资产） |

**过滤点清单（都靠 `@SQLRestriction` 自动生效，无需逐个改）**：
客户列表/详情/`/customers/{id}/summary`/`/customers/{id}/stats`；
项目列表/`/projects/summary`/详情/`/projects/{id}/stats`/`/projects/{id}/assets`；
资产列表/详情/`/assets/{id}/project-links`/`/assets/options`/标签与历史；仪表盘 `assets/stats`、
`customer-distribution`、`project-distribution`、工作负载；导入查重（`findByCode`）；
以及**聚合计数里的资产子查询**（`countAssetsGroupedByProjectIds` 等已显式加
`and l.assetId in (select a.id from Asset a)`，子查询同样被 restriction 过滤）。

### 63.4 行为口径

| 场景 | 结果 |
|---|---|
| `DELETE /customers/{id}`、`DELETE /projects/{id}`、`DELETE /assets/{id}` | 写 `deleted_at`/`deleted_by` → **204**（不再 200 + body） |
| 对被软删对象做详情/更新/挂靠/统计 | **404**（与 t65 的存在性语义一致，不泄露「曾存在」） |
| 删客户：其下有**未删**项目 | **409**（已软删的项目不算阻塞） |
| 删项目：仍有**未删资产**挂靠 | **409**（挂靠指向已软删资产时不算阻塞，见 `countByProjectIdAndAssetNotDeleted`） |
| 级联 | **不做**级联软删：项目保留期次与挂靠行、资产保留挂靠行，全部历史可追溯 |
| 恢复 | 清 `deleted_at`/`deleted_by`，状态机/stage 等其它字段一律不动；审计 `RESTORE_CUSTOMER`/`RESTORE_PROJECT`/`RESTORE_ASSET`（**一次恢复 1 条**，重复恢复不重复审计） |
| 删除审计 | 仍由 `AuditTrailInterceptor` 记 `DELETE <Controller>.<method>`（恢复端点已进 `SKIP_ENDPOINTS`，避免双写） |

### 63.5 统计口径

- 挂靠行（`asset_project_links`）**不因软删而删除**，所以「孤儿资产」这类**基于挂靠**的查询不受影响
  （软删资产仍保留挂靠，不会被误判成孤儿）。
- 但**基于资产的统计**必须排除已删：客户 summary 的 `stats.assetCount`、项目 `assetCount`、
  仪表盘分布等，均由 `@SQLRestriction` + 资产子查询保证（客户 summary 另外用
  `assetRepository.findAllById` 做一次「存活资产」交集，取代原来的纯挂靠推导）。

### 63.6 验证（`SoftDeleteTest` 16 例）

```bash
cd backend && mvn -B test -Dtest=SoftDeleteTest   # 16/16
```

覆盖：客户删除后列表不含/详情 404/`deleted_at` 非空且记录 `deleted_by`、同 code 重建 **409**、
管理端 `includeDeleted=true` 可见、恢复后详情 200 且只写 1 条审计、重复恢复幂等（不重复审计）、
不存在对象恢复 404、有未删项目时删客户 409；
项目删除后列表/`summary`/详情/`/assets`/`/stats` 全部不可见、有未删挂靠 409、仅剩已删资产挂靠时**可删**、
恢复后可查、软删保留挂靠行；
资产删除后列表/详情/`project-links`/项目资产/仪表盘统计均不计、`deleted_at` 非空、
管理端可见与恢复、客户 summary 的 `assetCount`/`projectCount` 随软删归零；非 ADMIN 访问管理端点 403；删除写审计。

### 63.7 已知边界

1. `@SQLRestriction` 对**原生 SQL** 不生效 —— 新增原生查询时若涉及这三张表，必须自己写 `deleted_at IS NULL`
   （本轮的 `searchAdmin`/`restoreById`/`findByIdIncludingDeleted`/`countByCodeIncludingDeleted` 是有意绕过的）。
2. 恢复只清软删标记，**不重建**已发生的其他状态变化（例如删除后有人复用了同一个 name，恢复不会改名）。
3. 软删不释放唯一键（设计如此）；若业务确实需要复用单号，需另做「归档改名」流程（当前不做）。

## 64. 第四十七轮（资产分组并入标签体系：V30 迁移 + 标签管理 + 自动标签 + 标签筛选，t141）

### 64.1 结论先说

「资产分组」并入资产中心的**标签**：`asset_groups` 与 `asset_group_relations` **保留不删**（历史可回溯），
新增 `tag_definitions` 作为标签目录；分组关系转换为资产标签关联（沿用既有 `asset_tags(asset_id, tag)` 表）；
标签由管理员统一维护；资产挂靠客户/项目时自动打上「客户名」「项目名」两个标签。

### 64.2 数据（V30，幂等 + manual-ddl 等价段）

```sql
CREATE TABLE IF NOT EXISTS tag_definitions (
    id VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(50) NOT NULL, color VARCHAR(20) NULL,
    builtin BOOLEAN NOT NULL DEFAULT FALSE, deleted_at DATETIME(6) NULL,
    sort_order INT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE UNIQUE INDEX IF NOT EXISTS uk_tag_definitions_name ON tag_definitions (name);   -- 含软删行
ALTER TABLE asset_groups ADD COLUMN IF NOT EXISTS migrated_at DATETIME(6) NULL;       -- 迁移水位
ALTER TABLE asset_groups ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL;        -- 软删分组不迁移
-- ① 未软删分组 → 标签定义（NOT EXISTS 守卫）  ② 分组关系 → asset_tags（同资产同标签去重）
-- ③ 标记 migrated_at（只标记未软删、未迁过的）
```

- 内置标签 3 条：`核心系统` / `对外服务` / `测试环境`（`builtin=true`，名称不可改、不可删）。
- 迁移**幂等**：定义用 `NOT EXISTS(name)`、关联用 `NOT EXISTS(asset_id, tag)`、水位用 `migrated_at IS NULL`；
  反复执行只补缺。**旧数据不删**，`asset_groups` 只补 `migrated_at` / `deleted_at` 两列。
- Java 侧同一套规则：`GroupToTagMigrator`（供测试驱动与运维重跑；H2 单测不执行迁移脚本，故用它与 SQL 同语义），
  见 `AssetTagSystemTest#migratesGroupsToTags / #migrationIsIdempotent / #softDeletedGroupSkipped / #migrationScriptGuards`。

### 64.3 标签管理（ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/tags` | 登录即可：`[{id,name,color,builtin,assetCount}]`，**assetCount 按可见范围收敛** |
| GET | `/api/v1/admin/tag-definitions?keyword=&includeDeleted=` | 分页；`includeDeleted=true` 含软删行 |
| POST | `/api/v1/admin/tag-definitions` | 新建（builtin 恒 false）；**name 重复（含软删行）→ 409** |
| PUT | `/api/v1/admin/tag-definitions/{id}` | 改 name/color/sortOrder；**内置改 name → 403** |
| DELETE | `/api/v1/admin/tag-definitions/{id}` | 内置 403；**被资产引用 → 409（带引用数）**；软删 |

审计：每次写操作恰好 1 条 `UPDATE_TAG_DEFINITION`（details 含名称/动作），端点已进 `SKIP_ENDPOINTS`。

### 64.4 自动标签规则

- 触发点：`POST /assets/{id}/project-links`（追加挂靠）与建资产时带 `projectId`（`createAssetWithLink`，
  内含 CSV 导入路径）。两个入口共用 `AssetProjectLinkServiceImpl#autoTag`。
- 打两个标签：**客户名** + **项目名**（取实体 `name`，不是 code）；标签定义不存在 → **自动创建**（`builtin=false`）。
- **幂等**：同一资产重复挂靠同一项目不会产生重复 `asset_tags` 行；跨项目挂靠时客户名标签只有一行。
- **解绑不删标签**：用户可能手工加过同名标签，自动删除会误伤；需要下线请走管理员删除标签（被引用会 409 提示引用数）。
- 唯一编号/名称的副作用：如果客户/项目改名，已打的标签仍是旧名（标签是快照，不做联动改名）。

### 64.5 按标签筛选

- `GET /api/v1/assets?tags=a,b` → **AND**（全部命中）；`?tagsAny=a,b` → **OR**（命中任一）；两者可同时给（结果再求交）。
  选型说明：**两种都支持**，因为「按一组标签收窄」与「按多个可选标签扩展」是两个不同诉求，前端筛选面板可以按需组合。
- 与既有过滤**取交集**：与 `customerId/projectId/phaseId`（归属）∩，再与 `AccessGuard.visibleAssetIds`（权限）∩，
  不可越权：工程师按标签筛选时只看到自己可见的资产（用例 `filterByTagsRespectsVisibility` 断言列表为空、`assetCount=0`）。
- `/api/v1/tags` 的 `assetCount` 同样按 `visibleAssetIds` 收敛（管理层为 null → 全量）。

### 64.6 与软删除的关系

- 标签定义删除是**软删**（`deleted_at`），软删后不出现在 `/tags`，但 `includeDeleted=true` 可见；名称唯一**含软删行**。
- 资产软删（t140）后：其标签行仍在（历史），但 `/tags` 的 `assetCount` 与 `/assets?tags=` 结果都**不含**软删资产
  （`assetCount` 用 `assetRepository.findAllById` 取「存活资产」，筛选结果落到 `Asset` 查询时受 `@SQLRestriction` 过滤）。

### 64.7 验证（`AssetTagSystemTest` 18 例）

```bash
cd backend && mvn -B test -Dtest=AssetTagSystemTest   # 18/18
```

覆盖：分组→标签定义/关联生成 + `migrated_at`、重复迁移幂等、软删分组不迁移、V30 脚本幂等守卫（含 collation 与两处 NOT EXISTS）；
内置改名 403 / 改颜色排序 200、重名 409、非 ADMIN 403、删内置 403、删被引用 409（消息带「1 个资产」）、
软删后不出现在 `/tags` 但含已删列表可见且同名不可复用、写操作恰好 1 条 `UPDATE_TAG_DEFINITION`；
建资产带 projectId 自动打客户名+项目名（含 `/assets/{id}/tags` 回读、定义自动创建且 builtin=false）、
追加挂靠同样打标签、解绑不删标签且重挂不产生重复行、多项目挂靠客户名只有一行、标签取 name 不取 code；
`tags` AND 与 `tagsAny` OR 语义、可见性交集（工程师 0 条且 assetCount=0）、`/tags` 形状与内置标签。
关联回归：`AssetControllerTest,AssetProjectLinkTest,SoftDeleteTest,ImportAssetProjectTest,ProjectSummaryTest` → **74/74**。

### 64.8 已知边界

1. 标签与分组**不是双向同步**：迁移是一次性的（V30 / `GroupToTagMigrator`），此后改分组不会回写标签（分组已下线使用）。
2. 标签名唯一（含软删行），改名会与既有标签冲突直接 409；改名**不**回填已打标签的资产（`asset_tags.tag` 是快照）。
3. 标签筛选先取 id 集合再求交，超大租户下集合可能较大（当前量级可接受）；后续可改成 `EXISTS` 子查询或物化视图。
4. 自动标签只在**挂靠时**触发；直接改项目/客户名称不会重打标签。

## 65. 第四十八轮（资产列表内联标签：禁止按行拉取，批量端点 + 查询数常数，t145）

### 65.1 问题

t143 报告的后端缺口：`AssetResponse` 没有 `tags` 字段、也没有批量端点，前端只能按行
`GET /assets/{id}/tags`（并发上限 6）——**一页 20 行 = 20 个请求**，首屏明显变慢。

### 65.2 结论：列表内联 + 批量端点（**禁止按行拉取**）

| 场景 | 用法 |
|---|---|
| 资产列表 | `GET /api/v1/assets?page&size` → 每项自带 `tags: [{id,name,color,builtin}]` |
| 项目资产 | `GET /api/v1/projects/{id}/assets` → 每项自带 `tags` |
| 客户 summary | `GET /api/v1/customers/{id}/summary` → `recentAssets[].tags` |
| 按需刷新 | `GET /api/v1/assets/tags?ids=a,b,c`（≤100）→ `{"<assetId>": [{id,name,color,builtin}]}` |
| 单资产详情 | `GET /api/v1/assets/{id}/tags`（**仅在详情页/编辑弹窗里用**） |

**前端硬规则**：列表页**不要**遍历行去调 `/assets/{id}/tags`；要刷新标签就调批量端点（一次 ≤100）。
`tags` 恒为数组——**空标签是 `[]` 而不是 `null`**，可直接 `map`。

### 65.3 实现：固定 2 次查询，与页大小无关

`AssetServiceImpl#loadTags(ids)`：

```java
List<AssetTag> rows = assetTagRepository.findByAssetIdIn(assetIds);          // ① 本页所有资产标签行
Map<String, TagDefinition> defs = tagDefinitionRepository.findByNameIn(names); // ② 这些标签名的定义
```

- `asset_tags` 存的是**标签名**（t141 的设计），所以第 2 步按 `name` 批量取定义（不是 `findAllById`），
  得到 `id/color/builtin`；定义缺失（历史脏数据）仍返回条目，`id=null`、`builtin=false`，不让标签凭空消失。
- 排序：**内置优先 + 名称升序**，保证同一页刷新顺序不跳动。
- 三个列表入口共用这一个方法；批量端点也复用它（同一口径，不会出现「列表和批量端点不一致」）。

### 65.4 性能断言（Hibernate Statistics）

测试侧开启 `spring.jpa.properties.hibernate.generate_statistics=true`（`application-test.yml`），
在**同一事务内**比较 `size=5` 与 `size=20` 两次请求的 `prepareStatementCount` 增量：

```java
long delta5  = after5  - before5;   // 例：11
long delta20 = after20 - before20;  // 例：11  → assertEquals(delta5, delta20)
```

`AssetTagsBatchTest#queryCountDoesNotGrowWithPageSize` 断言**严格相等**，并额外断言 `delta20 <= 15`
（常数级上限），防止后续有人在 `attachTags` 里加 per-asset 查询。

### 65.5 批量端点的边界

- `ids` 逗号分隔，**最多 100 个**，第 101 个开始 **400**（消息「一次最多查询 100 个资产的标签」）；
- 非法 UUID → **400**（不静默丢弃，避免前端拼错却看不到错误）；
- **与 `visibleAssetIds` 取交集**：不可见资产与不存在的 id 一律**跳过**（返回体里没有该 key），
  不报错、也不泄露「这个 id 存在」；
- 空 `ids` → `{}`。

### 65.6 验证（`AssetTagsBatchTest` 11 例）

```bash
cd backend && mvn -B test -Dtest=AssetTagsBatchTest   # 11/11
```

覆盖：每项都有 `tags` 数组且无标签为 `[]`、列表 tags 与 `/assets/{id}/tags` 一致（名称集合相等）、
**size=5 与 size=20 查询数严格相等**且 ≤15、`tags` 筛选下 tags 正确、`tagsAny` 筛选下 tags 正确、
批量端点返回 map 且多 id 正确、101 个 id → 400 / 正好 100 个 → 200、不可见与不存在 id 被跳过（管理员可见）、
非法 id → 400 与空 ids → `{}`、项目资产列表带 tags（含自动标签）、客户 summary `recentAssets[].tags`。

### 65.7 已知边界

1. 批量端点上限 100：超过请分页调用（前端列表一页通常 ≤50，不需要分批）。
2. `tags` 是**快照**（`asset_tags.tag` 存名称）：标签定义改名后，已打标签的资产仍显示旧名（与 t141 一致）。
3. 顺序为「内置优先 + 名称升序」的固定规则，不跟随后台的 `sort_order`（列表内联为了稳定，`/api/v1/tags` 下拉才按 `sort_order`）。


## 66. 第四十九轮（资产文件 / 交付物元数据与列表能力：remark + 上传人 + 分页筛选排序，t148）

### 66.1 三个缺口的实际结论

t146 前端反馈的三点，逐一核对代码后的结论：

| 前端反馈 | 实际情况 | 本轮处理 |
|---|---|---|
| 没有上传人字段 | **列一直存在**（`uploaded_by NOT NULL`，上传时已写当前用户），但**接口没返回展示名** | 列表每项补 `uploadedBy` + `uploaderName`（批量解析） |
| 没有独立备注 | 确实没有，前端把备注拼进 `description` | 新增 `remark VARCHAR(500)`（V31）+ 上传表单项 |
| 列表无分页/筛选/排序 | 确实没有，只按版本/全版本返回 `List` | 改为分页结构 + sort 白名单 + 4 个筛选参数 |

> 因此 V31 **没有**「`uploaded_by` 新增列」和「从 `created_by` 回填」这两步——两表既没有 `created_by` 列，
> `uploaded_by` 也早已有值。脚本只加 `remark` + 排序索引，并输出覆盖率校验（README 写法与实际一致，避免后人误以为漏做）。

### 66.2 接口契约（本轮之后）

**上传（有变更）**

```
POST /api/v1/assets/{assetId}/files
  multipart: file（必填）、category（必填）、description（可空）、remark（可空，≤500）、assetVersionId（可空）
POST /api/v1/assets/{assetId}/deliverables
  multipart: file（必填）、deliverableType（必填）、description（可空）、remark（可空，≤500）、assetVersionId（可空）
```

- `remark` 超过 500 字符 → **400**（在写文件/写库**之前**校验，不会留下半成功数据）；
- `uploaded_by` 由后端写当前登录用户，前端不要传。

**列表（破坏性变更：数组 → 分页对象）**

```
GET /api/v1/assets/{assetId}/files
GET /api/v1/assets/{assetId}/deliverables

参数：
  page     0-based，默认 0
  size     默认 20，**上限 100**（>100 → 400）
  sort     白名单，见下表；非法字段/方向 → 400
  keyword  文件名模糊（忽略大小写）
  文件独有：category（精确匹配）
  交付物独有：category 等价于 deliverableType（精确匹配）
  uploaderId  上传人 UUID
  dateFrom / dateTo  ISO 日期（yyyy-MM-dd，**闭区间含当日**）
  assetVersionId / allVersions  语义与旧版一致（指定版本优先，该版本无数据时回落到「无版本」）
```

响应：

```json
{
  "code": 200,
  "data": {
    "content": [
      { "id": "...", "name": "poc.py", "remark": "独立备注", "fileSize": 1024,
        "createdAt": "2026-11-09T16:00:00", "uploadedBy": "<uuid>", "uploaderName": "admin_1" }
    ],
    "totalElements": 25, "totalPages": 2, "currentPage": 0, "pageSize": 20
  }
}
```

### 66.3 排序白名单

| 文件 | 交付物 | 说明 |
|---|---|---|
| `uploadedAt`、`createdAt` | 同 | 都映射到 `created_at`（默认 `uploadedAt,desc`） |
| `name` | `name` | 文件名 |
| `size`、`fileSize` | 同 | 文件大小 |
| `category` | `type`、`deliverableType` | 分类 / 交付物类型 |
| `assetVersionId`、`version`（t151） | `assetVersionId`（t151） | 见下方「版本列」口径 |
| `uploadedBy`、`uploaderId`（t151） | 同（t151） | 见下方「上传人列」口径 |

方向 `asc`/`desc`（默认 `desc`）；写成 `sort=name` 等价 `name,desc`；未知字段/未知方向 → 400，
**错误消息里带完整可选值列表**（前端可据此对齐，见用例 `sortErrorMessageListsAllOptions`）。

**版本列（`assetVersionId`）口径 —— 文件与交付物不同，必须分开看**：

- **文件**：`assetVersionId`（及别名 `version`）映射到 **`asset_files.version`（文件自身版本号，整数）**。
  刻意**不**按 `asset_version_id`（UUID）排——UUID 排序无业务含义且 null 会乱序；
  按版本号排 = 前端「版本」列意义上的升/降序。
- **交付物**：`asset_deliverables` **没有版本号列**，`assetVersionId` 只能映射到 `asset_version_id`（UUID），
  结果**确定性但不可读**；交付物要业务可读排序请用 `type`/`name`/`uploadedAt`。

**上传人列（`uploadedBy`）口径**：映射到 **`uploaded_by`（UUID）**，跨页稳定、可复现。
**不是**「显示名拼音/字母序」——显示名在 `users` 表，按名排序要么改用原生 SQL 破坏 Specification 分页，
要么把用户拉进内存排序（违反「禁止内存排序」）。若产品坚持按姓名字典序，需要另做「用户 + 附件」聚合端点。

### 66.4 性能

- 分页/排序/筛选**全部走数据库**（`JpaSpecificationExecutor` + `Pageable`），没有内存过滤；
- `uploaderName` 用**一次** `userRepository.findAllById(ids)` 批量解析（`AttachmentListSupport#uploaderNames`），
  与页大小无关；测试 `queryCountDoesNotGrowWithPageSize` 用 Hibernate Statistics 断言
  `size=5` 与 `size=20` 的 `prepareStatementCount` 增量**严格相等**且 ≤8。

### 66.5 前端需要跟进的调用点（重要）

| 位置 | 现状 | 改成 |
|---|---|---|
| 资产详情「文件」页签 | `GET /assets/{id}/files` 当作数组 `.map` | 读 `data.content`（数组），用 `data.totalElements` 做分页器 |
| 资产详情「交付物」页签 | 同上 | 同上 |
| 文件/交付物筛选条 | 本地过滤已加载数据 | 传 `keyword`/`category`/`uploaderId`/`dateFrom`/`dateTo` 给后端 |
| 排序表头 | 本地排序 | 传 `sort=<白名单字段>,<asc|desc>` |
| 上传弹窗 | 备注拼进 `description` | 单独一个 `remark` 字段（≤500 前端也拦一下） |
| 「上传人」列 / 筛选下拉 | 显示"数据未提供"、置灰 | 直接用 `uploaderName`／用 `GET /api/v1/users?keyword=` 或团队成员列表填 `uploaderId` |

> 不传任何参数时行为 = `page=0&size=20`（**方案①**），因此**老前端不会 500，但会把对象当数组用而渲染空**——
> 两个页签必须一起改，已在上面列出。

### 66.6 验证（`AssetFileMetaTest` 20 例）

```bash
cd backend && mvn -B test -Dtest=AssetFileMetaTest   # 20/20
```

覆盖：上传 remark 落库 + `uploadedBy`=操作者 + 列表可读；remark 501 字符 → 400（文件与交付物各一）、500 字符边界通过；
交付物上传 remark/uploaderName；默认分页 20 + total 一致；page/size 生效；size=101 → 400；
`sort=uploadedAt,desc`（映射 created_at）/`name,asc`/`size,desc` 生效；非法字段与非法方向 → 400（含交付物）；
keyword/category/uploaderId/dateFrom+dateTo 逐项生效；uploaderName 正确且未知上传人 → null；
查询数不随页大小增长（≤8，严格相等）；历史行（无 remark、有 uploaded_by）仍能列出；
V31 脚本守卫（两表 remark、两个排序索引、uploaded_by 覆盖率校验、manual-ddl 等价段）；
交付物分页 + type 排序、交付物 category/uploaderId 筛选；指定版本无数据时回落到「无版本」文件（旧语义保留）。

### 66.7 已知边界

1. 列表**只返回 `status=active`**（与旧语义一致）；已归档附件不进列表，需要时另开 includeArchived。
2. `dateFrom`/`dateTo` 按服务器时区解释（与 `created_at` 同源）；跨时区租户需前端换算。
3. `remark` 只是备注，不参与搜索（搜索只匹配文件名）；如需全文检索另开任务。
4. 排序白名单是**代码常量**（`AttachmentListSupport#fileSortWhitelist`），新增可排序字段要改代码而不是配置。
---

## 66. 第四十二轮（t147 backend 收口二：软删除 + 标签体系 + 内联标签，V29/V30 上线）

时间：2026-11-09 15:1x–17:2x。**迁移 V29/V30 已在生产库执行**（幂等）。

| 项 | 结果 |
|---|---|
| 全量回归 | 隔离树（HEAD + 本轮 in-scope 文件）`mvn -B clean test` **连续 2 次全绿：975 例 / 0 / 0 / 0**（宿主 JDK21，pom target 17） |
| 迁移双场景 | V29/V30 × 空库/生产克隆 × `general_ci`/`unicode_ci` × 各 2 遍 → **40 项断言全绿**；生产库两脚本各跑两遍 `exit=0`：`deleted_at/deleted_by` 6 列 + 3 索引、既有行 `deleted_at IS NULL`、`tag_definitions=3(builtin)`、`asset_groups.migrated_at` 就位、`SHOW CREATE TABLE tag_definitions` 尾部 `COLLATE=utf8mb4_unicode_ci` |
| 部署 | 用 HEAD 隔离树打包 jar **73,441,963 B**（旧 jar 备份），重启 17–21s 内 login=200；前端 dist **保持原发布版本未动**（前端另有任务在飞） |
| 冒烟 | **292 / 292 PASS，0 FAIL，0 SKIP**（新增 13 条：软删 7 + 标签 6） |
| 真机验收 | 软删除 10 项、标签 5 项、N+1 观测、回归（2FA/remember-me/锁定）全通过，见 §66.2 |
| 收口提交 | `4676826`(t140 软删除) → `d91f3e7`(t141+t145 标签与内联标签)，两个提交在隔离树 `mvn -DskipTests compile` 均 BUILD SUCCESS（555→573 类） |

### 66.1 本轮修复的真实缺陷（V30 排序规则）

`V30__asset_groups_to_tags.sql` 的两处「标签名 = 分组名」是**列 = 列**比较：
`tag_definitions.name`（新建，显式 unicode_ci）与 `asset_groups.name` / `asset_tags.tag`（继承**库默认**）。
在默认 `utf8mb4_general_ci` 的实例上直接抛
`ERROR 1267 (HY000): Illegal mix of collations (utf8mb4_unicode_ci,IMPLICIT) and (utf8mb4_general_ci,IMPLICIT)`，
**第 50 行起整个 V30 中断**（分组迁移与水位全不执行）。生产库默认恰好 unicode_ci 所以历史没炸，属于**环境依赖缺陷**。
修复：两处比较**两侧都加 `COLLATE utf8mb4_unicode_ci`**，`deploy/manual-ddl.sql` 的 V30 段同步；
`AssetTagSystemTest#migrationScriptGuards` 从「断言 `t.name = g.name` 字面量」改为「断言两侧 COLLATE」。
复验：空库 general_ci 场景 V29/V30 各两遍全绿（40 项断言）。

### 66.2 真机验收（curl 级，全部通过）

**软删除（10 项）**：`DELETE /customers/{id}` → **204**；列表不含；`GET` → **404**；同 code 重建 → **409**；`GET /admin/customers?includeDeleted=true` 可见；`POST /admin/customers/{id}/restore` → **204** → 详情 200；库中 `deleted_at` 由非空回 **NULL**；再删后为 **NOT NULL**。

**标签（5 项）**：建客户→建项目→建资产（带 projectId）后 `GET /assets/{id}/tags` 含 **客户名 + 项目名** 两条；`GET /assets?page=0&size=20` 每项含 `tags`；`GET /assets/tags?ids=` 返回含标签的 map；`GET /tags` 的 `assetCount` 管理员=18 / 工程师=**0**（按可见范围收敛）。

**内联标签无 N+1**：`GET /assets?size=5` 与 `size=20` 各 3 次，总耗时 141ms vs 142ms（无随页增长趋势）；严格证据见单测 `AssetTagsBatchTest#queryCountDoesNotGrowWithPageSize`（size=5 与 20 的 `prepareStatementCount` **严格相等**）。

**回归**：2FA `init→confirm→两步登录(202→200)→disable` 全通过；remember-me `owl_rm` 下发 + `/auth/refresh` 轮换通过；恢复默认配置后 6 次错密码 → **429 + `Retry-After: 899`**（t136 单位修复仍生效）。

### 66.3 冒烟脚本更新（13 条）

软删 7 条（建客户 201 / DELETE 204 / 列表不含 / 详情 404 / 同 code 409 / includeDeleted 可见 / restore 204 + 恢复后可查）、
标签 6 条（挂靠自动打项目名 / 列表内联 tags / 批量端点 / `/tags` 下拉 / 管理端列表 / 非 ADMIN 写 403）。
另修两处**脚本自身**问题：① `api_json GET` 少传 payload 触发 `$4: unbound`；② 可见性检查 `vis/*-列表内详情全200` 把「列表→详情之间夹具已被删除（404）」判成失败，改为 404 跳过、只把 401/403/5xx 计失败。

### 66.4 部署与清理

- 临时开关（`APP_VERIFICATION_DEV_MODE`、放宽的 IP/重置/锁定阈值）**全部移除**并重启复核（发码响应无 `devCode`）。
- 数据终态：`customers=1 projects=1 phases=0 assets=3 links=3`、`orphan_assets=0`、`soft_deleted_rows=0`、`tag_definitions=3(builtin)`、`asset_tags=0`、`users=12`、`requests=0`、`codes=0`、`rm_tokens=0`。
- 临时验证库 `vuln_audit_verify_*` / `vuln_audit_clone_*` 已 DROP。
- **工作树仍有他人未提交改动**（`AssetDeliverable/AssetFile` + `V31`，另一任务在飞），本任务未触碰、未提交。

### 66.5 踩坑（本轮新增）

1. **部署机跑全量回归必须同步 `deploy/manual-ddl.sql`**（t136 已记，本轮再次命中 `MetaTypeAdminTest#migrationScriptGuards`）。
2. **共享工作树会污染构建**：`git status` 里他人的半成品（`AssetDeliverableController` 缺 `StringUtils` 导入）会让 `mvn clean test` 直接编译失败；正确做法是 `git archive HEAD` + 覆盖本轮 in-scope 文件做**隔离树**回归。
3. **任务粒度提交会破坏可编译性**：`CustomerServiceImpl`（t140 的统计改动）依赖 t145 的 `AssetTagBrief/tagsByAssetIds`，按 t140 单独提交编译不过 → 最终按 `t140` 与 `t141+t145` 两个提交落地（已在提交信息注明）。
4. **连续多轮冒烟会撞限流**：除 IP 验证码上限外，还有「重置密码失败次数」限流（默认 5/15min），需临时放宽 `APP_VERIFICATION_RESET_MAX_FAILED` 才能跑完整链路。
5. **401 的假象**：真机脚本里用 `/users/me` 取 admin id 拿不到（返回体结构不同），导致后续创建项目缺 `managerId` → 400，连锁使标签断言全红；改用登录响应 `data.userId`。
---

## 67. 第四十三轮（t150 部署：V31 附件元数据 + 分页契约 + 内联标签，前后端同批）

时间：2026-11-09 17:30–18:2x。**后端 jar 与前端 dist 同一批次发布**（契约变更必须同批，否则页签空白）。

| 项 | 结果 |
|---|---|
| 全量回归 | `mvn -B clean test` **连续 2 次全绿：995 例 / 0 / 0 / 0**（宿主 JDK21，pom target 17） |
| V31 迁移双场景 | V31 × 空库/生产克隆 × `general_ci`/`unicode_ci` × 各 2 遍 → **18 项断言全绿**；生产库执行两遍 `exit=0`：`asset_files.remark` / `asset_deliverables.remark` 各 `varchar(500)`、两个排序索引就位、`uploaded_by IS NULL` 覆盖率 **0/0**、数据未受影响（files=4） |
| 部署 | jar **73,458,082 B**（旧 jar 备份），重启 18–20s 内 login=200；前端清 `.tmp` 后构建 **`index-BJk1I-ZA.js` 2,862.40 kB / `index-C9wuC1eN.css` 30.80 kB**，`sync-dist` 5 文件，线上 `index.html` 已引用新 chunk |
| 冒烟 | **292 / 292 PASS，0 FAIL，0 SKIP**（无需改断言：软删 204 与标签接口自 t147 起已覆盖） |
| 真机 API | **23 项全通过**（分页契约 13 + 内联标签 2 + 回归 4 + 前置），见 §67.1 |
| 真机浏览器 | **T150_UI 11/11 PASS，pageErrors=[]**，见 §67.2 |

### 67.1 真机 API 验收（关键项）

- **分页契约**：`GET /assets/{id}/files?page=0&size=20` → `{content,totalElements,totalPages,currentPage,pageSize}`；`size=1` → 1 条、`size=20` → 2 条；`size=101` → **400**；`sort=name,asc` 生效、`sort=hack,asc` → **400**；`keyword=t150-a` → 1 条；`category=REPORT` → 1 条；`uploaderId=<admin>` → 2 条；交付物同构（`deliverableType` 必填，`sort=deliverableType` → 200）。
- **上传备注**：上传带 `remark` → 列表回读 `remark` 与 `uploadedBy.uploaderName`；`remark` 501 字符 → **400**（落盘前校验）。
- **内联标签**：`GET /assets?size=20` 每项含 `tags`；挂靠后 `GET /assets/{id}/tags` 含客户名 + 项目名。
- **回归**：软删除 204 → 详情 404 → 同 code 409 → restore 204；2FA 全链路；remember-me 下发与轮换；恢复默认后 6 次错密码 **429 + Retry-After 900**。

### 67.2 真机浏览器验收（真实 Edge + 原生 CDP，11 项）

| # | 断言 | 结果 |
|---|---|---|
| 1 | 资产列表页**未按行**请求 `/assets/{id}/tags`（内联标签生效） | **per-asset=0**，batch=0 |
| 2 | 列表有数据行 + 标签列渲染（客户名/项目名） | 6 行，命中 `t150客户`/`t150项目` |
| 3 | 资产详情「**资产文件**」页签不空白 | **2 行**：`t150-b.txt REPORT 20B admin t150备注B`、`t150-a.txt SCREENSHOT 20B admin t150备注A` |
| 4 | 文件列表含**备注列**与**上传人列** | 命中 `t150备注A/B`、`admin` |
| 5 | 「**交付物管理**」页签不空白 + 备注 | **1 行**：`t150-d2.txt REPORT 21B admin t150交付备注` |
| 6 | 页面上下文取 `files` 接口 → 分页信封 | `{envelope:true,total:2}` |
| 7 | 页面无 JS 异常 | `pageErrors=[]` |

截图：`.tmp-t150/shot-asset-list.png`、`shot-asset-files.png`、`shot-asset-deliverables.png`。

### 67.3 排查记档（页签「空白」的三次误判）

首次自动化报「文件页签 rows=0」，逐层排查后确认**页面本身正常**，全部是验证脚本的错：
1. 页签文案是「**资产文件**」和「**交付物管理**」，脚本按 `^文件$` / `^交付物$` 匹配 → 点不到；
2. 行选择器用了 `tbody tr`，该表用虚拟/普通表混排时应取 `.ant-table-row`；
3. 页签面板需按 `aria-hidden` 判断当前面板，否则统计到隐藏面板（概览）的 0 行。

另记：页面在拿到分页信封后仍显示「筛选对已加载的数据生效」是 t149 的**双模式**设计（本地降级模式文案），不是契约不一致——接口返回的是 `{content,totalElements}`（页面上下文实测）。

### 67.4 环境与清理

- 临时开关（dev-mode / IP / 重置 / 锁定阈值）全部移除并重启复核：发码响应无 `devCode`、锁定仍 6 次触发 **429 + Retry-After 900**。
- 临时库 `vuln_audit_verify_*`/`vuln_audit_clone_*` 全部 DROP，`vulnapp` 临时授权已 REVOKE。
- 数据终态：`customers=1 projects=1 phases=0 assets=3 links=3`、`orphan_assets=0`、`tag_definitions=3(builtin)`、`asset_tags=0`、`files=4`（示例资产原有附件）、`deliverables=0`、`users=12`、`requests=0`、`codes=0`、`rm_tokens=0`。
### 66.8 t151 增补：排序白名单支持「版本列」与「上传人列」

t149/t150 报告的两个小缺口（前端这两列只能页内本地排序、误传会 400）已修：

| 字段 | 文件列表映射 | 交付物列表映射 | 说明 |
|---|---|---|---|
| `assetVersionId`、`version` | `version`（文件版本号，整数） | `assetVersionId`（UUID） | 文件按版本号；交付物**没有版本号列**，只能按所属版本 UUID |
| `uploadedBy`、`uploaderId` | `uploadedBy`（UUID） | 同 | 跨页稳定；**不是**姓名拼音序 |

- 全部走 `Pageable`+`Sort`（数据库排序），无内存排序；
- 非法字段/方向仍是 **400**，消息里带**完整**可选值列表（用例 `sortErrorMessageListsAllOptions` 逐项断言）；
- 跨页正确性用例：`sortByAssetVersionIdAcrossPages`（25 条、size=20 → 第 2 页首行版本 **21**）、
  `sortByUploadedByAcrossPages`（3 个上传人、size=1 → 第 2 页为 UUID 排序居中者）；
- **前端可以去掉这两列的「页内排序」降级**，直接传 `sort=assetVersionId,asc` / `sort=uploadedBy,desc`。
## 68. 第五十轮（项目侧批量添加 / 移除资产 + 可选资产列表，t154）

### 67.1 为什么需要

此前只有「资产侧挂靠」（`POST /assets/{id}/project-links`，一次一个资产）。项目详情页要「批量添加已有资产 /
移除资产」时，前端得循环 N 次，既慢又会踩「同一资产最后一条挂靠不能删」的不变式；选择器也拿不到「还没挂靠本项目的资产」。

### 67.2 接口契约

**批量添加**（不整体失败、幂等）

```
POST /api/v1/projects/{projectId}/assets
{ "assetIds": ["<uuid>", ...], "phaseId": "<uuid>|null", "linkType": "TARGET|SHARED|REFERENCE" }

200 { "added": 2, "skipped": 0, "failed": [{ "assetId": "...", "code": "A-1", "reason": "..." }] }
```

- `assetIds` **1–100**（空 / 超限 → **400**），自动去重保序；
- 逐条判定：资产不存在或**已软删** → `failed`（原因「资产不存在或已删除」）；已挂靠**同项目同期次** → `skipped`；
  不在可见范围 / 非负责人且非管理层 → `failed`（原因区分「不可见」与「无写权限」）；
- 项目不存在 → **404**；项目未启用分期却传 `phaseId` → 400（沿用 `validatePhase`）；
- 新挂靠的资产**自动打「客户名 + 项目名」标签**（t141 规则，幂等）。

**批量移除**（先全量校验再执行）

```
DELETE /api/v1/projects/{projectId}/assets
{ "assetIds": ["<uuid>", ...] }

200 { "removed": 1 }
409 资产清单（任一资产移除后不再挂靠任何项目 → 整体不执行）
```

- 移除该项目下这些资产的**全部**挂靠（含各期次）；
- 未挂靠本项目的资产 → 幂等跳过（不计入 removed，不报错）；
- **不变式**：「资产必须至少挂靠一个项目」——先算「移除后剩余挂靠数」，任一条为 0 → **409 并在消息里列出资产编号**，
  **一条都不删**（不是删一半再报错）。

**可选资产列表（选择器）**

```
GET /api/v1/projects/{projectId}/available-assets?keyword=&page=0&size=20

200 { content:[{id,code,name,targetType,status,linkCount}], totalElements, totalPages, currentPage, pageSize }
```

- 只返回**未挂靠本项目**且**调用者可见**的资产（`visibleAssetIds` 收敛，不绕过 t70/t140 口径）；
- `linkCount` = 该资产当前的挂靠总数（一次分组查询，不做 per-asset 查询）；0 表示孤儿资产，前端可提示；
- `keyword` 匹配名称或编号；`size ≤100`。

**审计**：批量添加/移除**各写 1 条**（`BULK_ADD_PROJECT_ASSETS` / `BULK_REMOVE_PROJECT_ASSETS`，
details 含 `project=<code>, added=, skipped=, failed=` / `removed=, requested=`），两个端点已进
`AuditTrailInterceptor.SKIP_ENDPOINTS`，避免和拦截器重复写。

### 67.3 前端调用方式

| 场景 | 调用 |
|---|---|
| 打开「添加资产」弹窗 | `GET /projects/{id}/available-assets?keyword=&page=0&size=20`（表格勾选，最多 100） |
| 点「确定添加」 | `POST /projects/{id}/assets` body `{assetIds, phaseId?, linkType?}`；按 `added/skipped/failed` 分别提示（failed 用 `code + reason` 逐条列出） |
| 详情页表格「移除」多选 | `DELETE /projects/{id}/assets` body `{assetIds}`；409 时把消息里的资产编号回显，引导「挂到其它项目」或「删除资产」 |
| 项目详情页资产表 | 仍用 `GET /projects/{id}/assets`（已有，含 tags） |

> 不要在前端循环调用资产侧的 `POST /assets/{id}/project-links` —— 那样失败会「删一半」、慢，而且拿不到 skipped/failed 汇总。

### 67.4 验证（`ProjectBulkAssetsTest` 14 例）

```bash
cd backend && mvn -B test -Dtest=ProjectBulkAssetsTest   # 14/14
```

覆盖：批量添加 2 个 → added=2 且挂靠落库 + 自动打客户名/项目名标签；不存在与已软删资产 → 计入 failed 且其余成功；
重复挂靠 → skipped 且不产生重复行；assetIds 空 / 101 个 → 400；项目不存在 → 404；
无写权限（工程师挂管理员资产）→ failed 而自己的成功；批量移除 removed=1 且只删本项目挂靠；
移除导致零挂靠 → 409 且**一条都没删**；未挂靠本项目 → removed=0 幂等；
可选资产只含未挂靠且可见（linkCount 正确）、可见性不绕过（工程师看不到管理员专属）、分页正确、keyword 筛选；
审计各 1 条且 details 含数量。

### 67.5 已知边界

1. 「可选资产」上限由分页控制（`size ≤100`），没有「一次取全部」的开关；超大租户请用 keyword 收窄。
2. 批量添加的 `linkType` 对**整批**生效（不能逐条不同）；需要不同 linkType 请分批调用。
3. 批量移除会移除**该项目下该资产的所有期次挂靠**（不按期次细粒度移除）；按期次移除仍走资产侧 `DELETE /assets/{id}/project-links/{linkId}`。
4. 批量期间若并发有人删掉某资产，可能有个别条目落 `failed`——**已经写入的挂靠不回滚**（设计如此：不整体失败）。
## 69. 第五十一轮（审批历史查询端点：替换前端 localStorage 兜底，t156）

### 69.1 问题

`GET /assets/my/access-approval-requests` 只返回 **pending**，已通过/已拒绝的记录后端取不到 → 前端只能用
`localStorage` 兜底（换浏览器/清缓存即丢）。用户需求「审批完的内容要留存显示」因此并没有真正落地。

### 69.2 接口契约

**历史列表**

```
GET /api/v1/assets/my/access-approval-history
    ?as=reviewer|requester      # 默认 reviewer
    &status=approved|rejected|all   # 默认 all，非法 → 400
    &page=0&size=20             # size ≤100
    &assetId=<uuid>&keyword=<资产名/编号模糊>

200 { content:[{
        id, assetId, assetCode, assetName,
        assetDeleted,                    // 资产已软删 → true（历史不隐藏，只标注）
        applicant:{id,username}, status, // approved / rejected
        requestedAt, reviewedAt,
        reviewer:{id,username}, reviewNote,
        scope:{canViewDocuments, canViewEnvironments, canViewFiles, allowedFileTypes}
      }],
      totalElements, totalPages, currentPage, pageSize }
```

**计数（角标）**

```
GET /api/v1/assets/my/access-approval-history/count?as=reviewer
200 { "approved": 2, "rejected": 1 }
```

### 69.3 语义（明确写死，避免歧义）

| 项 | 口径 |
|---|---|
| `as=reviewer`（默认） | `reviewed_by = 当前用户` —— **我审批过的** |
| `as=requester` | `user_id = 当前用户` 且已被处理 —— **我申请且已被处理** |
| 只含已处理 | 历史永远是 `approved`/`rejected`；**pending 不在此列**（还在待办里） |
| 排序 | `reviewedAt desc`（最新处理在前），无其他排序参数 |
| 权限 | 只能看与自己相关的记录；**没有**跨用户/管理员全量视图（如需另开 `/admin/access-approval-history`） |
| 软删除 | 资产软删后记录**仍然可见**，用 `assetDeleted=true` 标注（历史不因资产消失） |
| keyword | 匹配资产**名称/编号**，且**含已软删资产**（历史可搜） |
| 审计 | 只读端点**不写审计** |

实现要点：历史查询用 JPQL（`status in :statuses` 在**数据库**过滤，不是取一页再内存筛）；
资产摘要（含已软删）用原生 SQL **返回实体**（`select * ... where id in :ids`）——
H2 里 id 是二进制列，用 `Object[]` + `UUID.fromString` 会报 `Invalid UUID string: [B@...`，交给 Hibernate 实体映射最稳。

### 69.4 前端可以移除 localStorage 兜底了（替换点）

| 位置 | 原来 | 改成 |
|---|---|---|
| 「审批历史」列表 | 读 `localStorage.approvalHistory` 拼接 | `GET /assets/my/access-approval-history?as=reviewer&status=all&page=0&size=20`，渲染 `content` |
| 「我申请过的」列表 | 同上 | 同端点 `as=requester` |
| 审批中心角标 | 本地计数 | `GET .../access-approval-history/count?as=reviewer`（`approved`/`rejected`） |
| 已通过的申请详情 | 本地缓存里的字段 | 直接用列表项（已含 `applicant`/`reviewer`/`reviewNote`/`scope`） |

> 待审批列表仍用**原来的** `GET /assets/my/access-approval-requests`（pending 专用），两者不要混。

### 69.5 验证（`ApprovalHistoryTest` 11 例）

```bash
cd backend && mvn -B test -Dtest=ApprovalHistoryTest   # 11/11
```

覆盖：pending 不出现在历史（approved/rejected 出现且字段完整：assetCode/assetName/申请人/审批人/reviewNote/scope/时间）；
`status=approved|rejected|all` 分别过滤且非法 status/as → 400；默认 `reviewedAt desc`；分页 total/content/页码正确；
两个审批人互不可见；`as=requester` 与 `as=reviewer` 语义互不串；资产软删后记录仍可见且 `assetDeleted=true` 并保留 assetCode；
`assetId`/`keyword` 筛选（含已软删资产可搜）；count 与列表一致（approved/rejected 分开）；count 的 `as=requester` 与非法 as；
未登录 401；只读端点不写审计。

### 69.6 已知边界

1. 没有管理员全量视图（任务明确「本任务先不做」）；需要时另开 `/admin/access-approval-history`。
2. `requestedAt` 取 `asset_accesses.created_at`（申请时间）；重新提交会新增一行，历史因此可能有多条。
3. keyword 走「先解析资产 id 再 `in` 过滤」，超大租户下 keyword 命中集合可能较大（当前量级可接受）。
4. 历史不含 `requestNote`（申请理由）——只有 `reviewNote`（审批意见）；需要申请理由请用待审批接口或另加字段。
---

## 68. 第四十四轮（t158 收口三：菜单排序 / 审批历史 / 批量资产 / 产品概览 / 附件排序，前后端同批）

时间：2026-11-10 15:50–17:1x。本轮 **无新迁移**（生产仍为 V31；`manual-ddl.sql` 覆盖 V18–V31，与 migration 目录一致 —— V1–V17 属 Flyway 关闭前的历史基线，不在手工段内）。

| 项 | 结果 |
|---|---|
| 全量回归 | `mvn -B clean test` **连续 2 次全绿：1024 例 / 0 / 0 / 0**（宿主 JDK21，pom target 17） |
| 部署 | 后端 jar **73,482,723 B**（旧 jar 备份），重启 14–20s 内 login=200；前端清 `.tmp` 后构建 **`index-Bht1i7kT.js` 2,894.53 kB / `index-C9wuC1eN.css` 30.80 kB**，`sync-dist` 5 文件，线上 `index.html` 已引用新 chunk（前后端同批） |
| 冒烟 | **302 / 302 PASS，0 FAIL，0 SKIP**（新增 10 条：批量资产 6 + 审批历史 4） |
| 真机浏览器 | **T158_UI_A 16/16 + T158_UI_B 15/15，pageErrors=[]**，见 §68.2 / §68.3 |

### 68.1 冒烟新增 10 条

批量资产（t154）：可选资产列表 200 / 批量添加 `added=1` / 重复添加 `skipped=1` / 移除一条 `removed=1` / **移除唯一挂靠 409** / 项目不存在 404。
审批历史（t156）：历史列表 200（`content`）/ 非法 `as` 400 / count 200（含 `approved`）/ 未登录 401。

### 68.2 真机验收 A（菜单 / 审批 / 产品概览 / 所属项目，16 项）

- **菜单**：`安全审计任务` 索引 **4** < `全局漏洞视图` **5**（`安全态势大盘/客户管理/项目管理/产品与资产中心/安全审计任务/全局漏洞视图/审批中心` 七项逐一点击，选中态与文案完全一致）。
- **审批三分类**：Tab 为 `待审批 | 已通过 30 | 已拒绝 46`，默认停「待审批」；「已通过」发 `GET /assets/my/access-approval-history?as=reviewer&status=approved&page=0&size=10`，「已拒绝」带 `status=rejected`。
- **localStorage 证据**：清空除 `access_token` 外所有键（`keys=["access_token"]`）后刷新，**已拒绝仍 10 行** —— 历史数据确实来自后端。
- **软删资产**：记录带「资产已删除」Tag（4 处），目标渲染为不可点文本（`hasAnchor=false`）。
- **产品概览**：默认只读，只读区输入控件 **0**（版本选择器按 `.ant-select` 排除）；点「修改」后表单控件 **9** 个。
- **所属项目**：解绑/移除按钮 **0**，有「请在项目管理中操作」说明 + 跳转链接。

### 68.3 真机验收 B（批量资产 / 附件排序，15 项）

- **批量添加**：弹窗勾选 2 个资产 → `POST /projects/{id}/assets` 响应 `{"added":2,"skipped":0,"failed":[]}`；添加后这些资产**不再出现在可选列表**（重复提交走 `skipped`，由冒烟 `bulk/重复添加-skipped1` 覆盖）。
- **移除唯一挂靠**：勾选唯一挂靠资产 → 确认框文案「如果某个资产移除后不再属于任何项目，后端会拒绝整批操作（一条都不会删）」→ `DELETE` 返回 **409**「以下资产移除后将不再挂靠任何项目…: AST-20261110-13020B」，**列表行数 3 → 3 不变**。
- **DELETE 请求体证据**：`{"assetIds":["4a87a50d-8fbe-46f5-8861-0cdd9a82eb6a"]}` —— 是 **assetId（36 位 UUID）**，不是 linkId。
- **未再调用资产侧挂靠**：`/assets/{id}/project-links` 请求数 **0**。
- **附件排序**：文件表「版本」「上传人」列均可排序；点「版本」→ `?sort=assetVersionId,asc&allVersions=true`，点「上传人」→ `?sort=uploadedBy,asc`；上传人表头有「按账号排序」提示；**交付物「版本」列 `sortable=false`**（无排序图标，符合 t151 决策 —— 交付物无版本号列）。

### 68.4 部署与清理

- 临时开关（dev-mode / IP / 重置 / 锁定阈值）全部移除并重启复核：发码无 `devCode`、锁定第 6 次 **429 + Retry-After 900**。
- 顺带修复：**admin 的 2FA 处于开启状态**（历史测试遗留），导致部署后首轮 `login` 返回 202；已复位 `two_factor_enabled=false / totp_secret=NULL` 并清理恢复码。**后续验证脚本若开启 2FA 必须确保关闭**。
- 数据终态：`customers=1 projects=1 phases=0 assets=3 links=3`、`orphan=0`、`tag_definitions=3(builtin)`、`asset_tags=0`、`files=4`、`deliverables=0`、`users=12`、`codes=0`、`requests=0`、`rm_tokens=0`、`2fa_on=0`；临时库 `temp_dbs=0`、`vulnapp` 临时授权已 REVOKE。
- 遗留：`asset_access` 保留 70 条历史申请（引用基线示例资产的冒烟历史），不影响页面与统计。

### 68.5 踩坑（本轮新增）

1. **脚本先探结构再断言**：审批页签、项目页按钮、概览只读区都可点/可读，但名称与结构必须先 dump（`资产文件`/`交付物管理`/`添加资产`/`移除所选（0）`），否则「空白/失败」多半是选择器错。
2. **`api_json GET` 必须带 payload**（t147 已记，本轮又踩一次）：GET 用 `request GET`，否则 `$4: unbound`。
3. **清理脚本用错表名会整体中断**：访问申请表是 **`asset_access`**（不是 `asset_access_requests`）；`mariadb < file` 遇错即停，表现为「sql_exit=1 但无任何输出」。
4. **重复添加无法在 UI 复现**：资产一旦挂靠就不再出现在可选列表，`skipped` 语义只能在 API/冒烟层验证（UI 侧改为断言「已挂靠资产不再出现」）。

## 70. 第五十二轮（大文件上传需管理员审批：临时区 + 状态机 + 清理，t160）

### 70.1 语义（一句话）

超过门限的文件**不进业务库**：只写临时区 + 一条 PENDING 审批记录；管理员批准才移入正式存储并创建业务记录，
**拒绝立即删临时文件**，超过 `purge_after` 未处理的由定时任务标记 EXPIRED 并删文件。
因此 PENDING/REJECTED 的文件**不可能**出现在任何列表、下载、统计里（不是靠过滤隐藏，而是根本没写）。

### 70.2 配置（`app.upload.*`）

| 配置项 | 默认 | 说明 |
|---|---|---|
| `app.upload.approval-threshold-bytes` | `20971520`（20MB） | **超过**该值走审批；≤ 则直传（保持既有体验） |
| `app.upload.max-bytes` | `524288000`（500MB） | 单文件硬上限，超过 → **413**（不受审批保护） |
| `app.upload.pending-dir` | `./uploads/pending` | 临时区目录（PENDING 文件只在这里） |
| `app.upload.approved-dir` | `./uploads/approved` | 正式区目录（批准后才移入） |
| `app.upload.purge-days` | `7` | `purge_after = now + purge-days` |
| `app.upload.purge-cron` | `0 17 * * * *` | 过期清理频率（每小时第 17 分） |

### 70.3 状态机

```
         超门限提交
            │
            ▼
        ┌────────┐  管理员批准   ┌──────────┐
        │PENDING │ ───────────▶ │ APPROVED │  文件移入正式区 + 业务记录（target_id）
        └────────┘              └──────────┘
            │      管理员拒绝
            ├──────────────────▶  REJECTED   立即删除临时文件
            │      purge_after 到期（定时任务）
            └──────────────────▶  EXPIRED    删除临时文件
```

`PENDING` 才能被批准/拒绝；已处理再操作 → **409**（不重复处理、不重复审计）。

### 70.4 接口

| 角色 | 方法/路径 | 说明 |
|---|---|---|
| 申请人 | `POST /api/v1/uploads/pending`（multipart：`file`、`targetType`、`name?`、`description?`） | 超门限 → `201 {requestId, status:"PENDING", purgeAfter}`；未超 → `201 {status:"DIRECT", targetId}`；超上限 → **413**；非法 `targetType` → 400 |
| 申请人 | `GET /api/v1/uploads/pending/{id}` | 只能看自己的（他人 → **404**，不泄露存在性） |
| 管理员 | `GET /api/v1/admin/upload-approvals?status=PENDING&page=&size=` | 分页，`status` 可空（全部） |
| 管理员 | `POST /api/v1/admin/upload-approvals/{id}/approve` | 移入正式区 + 创建业务记录，返回 `{status:"APPROVED", targetId}` |
| 管理员 | `POST /api/v1/admin/upload-approvals/{id}/reject` body `{note?}` | 删临时文件 + `{status:"REJECTED", reviewNote}` |

审计：提交 / 批准 / 拒绝各 **1 条**（`SUBMIT_UPLOAD_APPROVAL` / `APPROVE_UPLOAD` / `REJECT_UPLOAD`），
三个端点已进 `AuditTrailInterceptor.SKIP_ENDPOINTS`。

### 70.5 安全约束

1. **临时区不可按路径访问**：没有任何「按 storage_key 下载」的端点；文件只能经 approve 进入正式区并由业务实体下载。
2. **文件名净化**：去目录部分（`../../etc/passwd.zip` → `passwd.zip`）、去 `..`、去控制字符、裁剪到 200 字符；
   落盘名再加 UUID 前缀，**展示名与磁盘名分离**。
3. **路径解析**：只取 `storage_key` 的**文件名部分**再拼到临时区目录下，`normalize()` 后落盘 → 无法穿越。
4. **大小上限**：`max-bytes` 之上直接 413（错误码 `PAYLOAD_TOO_LARGE`，i18n key `error.payload_too_large`）。
5. **权限**：管理端三个端点 `hasRole('ADMIN')`；申请人查询带 `requested_by` 过滤。

### 70.6 前端调用方式

| 场景 | 调用 |
|---|---|
| 工具/组件库上传 | `POST /api/v1/uploads/pending`（FormData：file/targetType/name?/description?） |
| 返回 `status="DIRECT"` | 直传成功，用 `targetId` 刷新列表即可（与旧体验一致） |
| 返回 `status="PENDING"` | 提示「已提交，等待管理员批准」，用 `requestId` 轮询 `GET /api/v1/uploads/pending/{id}` |
| 管理端审批页 | `GET /api/v1/admin/upload-approvals?status=PENDING` 列表；批准/拒绝分别调 approve / reject |
| 展示 | 列表项含 `fileName/fileSize/requester{id,username}/requestedAt/purgeAfter/pendingFilePresent` |

> 前端**不要**在上传后立刻把该文件塞进业务列表——它还没被批准；以 `pendingFilePresent` 与 `status` 为准。

### 70.7 验证（`UploadApprovalTest` 14 例）

```bash
cd backend && mvn -B test -Dtest=UploadApprovalTest   # 14/14
```

门限被压到 1KB、目录指到 `target/test-uploads/*`（不污染仓库）。覆盖：超门限 → PENDING 且**业务表零记录**、
临时文件存在、有 `purgeAfter`；PENDING 不在 `GET /tools` 中且无 `targetId`；未超门限 → DIRECT + 业务记录立即存在；
批准 → 临时文件消失、正式区有文件、`targetId` 可查、状态 APPROVED；拒绝 → 临时文件删除、REJECTED、业务表不变；
重复审批 → 409；文件名穿越被净化且落盘仍在临时区；超上限 413、非法 targetType 400；
非 ADMIN 列表/批准/拒绝 403（且仍 PENDING）；申请人只能查自己的（他人 404）；
过期清理 → EXPIRED + 文件删除；审计各 1 条；管理端按 status 过滤；门限值与配置一致。

### 70.8 已知边界

1. 业务记录创建目前只实现 **TOOL**（`target_type` 的其他取值会正常走审批但 `target_id` 为 null，需按业务补建）。
2. 批准是「移动文件 + 建记录」的本地事务；若对象存储在远端（S3/OSS），需要改成「两阶段 + 补偿」。
3. 过期清理是**单实例定时任务**；多副本需分布式锁，否则多个实例同时清理（`deleteIfExists` 幂等，但会有重复日志）。
4. 直传分支（≤门限）**写死**在 `target_type=TOOL` 上创建 `tools` 记录；其他类型直传需要补对应业务写入。


## 71. 第五十三轮（知识库/文档附件：独立附件模型替代正文 base64，t162）

### 71.1 为什么换掉 base64 方案

t159 前端只能把附件塞进正文（`<!--owl-attachment:{...}-->`），受 `content TEXT(64KB)` 限制，
**二进制实际只能 ≤40KB**，PDF/PPT/压缩包根本传不上去。本轮换成**独立附件模型**：文件落磁盘、DB 存元数据，
单附件上限 **50MB**，下载走带归属校验的接口。

### 71.2 数据（V33，幂等 + manual-ddl 等价段）

```sql
CREATE TABLE IF NOT EXISTS document_attachments (
  id, owner_type(KNOWLEDGE|DOCUMENT), owner_id, file_name, stored_name,
  content_type, file_size, storage_path, uploaded_by, uploaded_at, deleted_at,
  created_at, updated_at
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_document_attachments_owner ON document_attachments (owner_type, owner_id);
CREATE INDEX idx_document_attachments_deleted ON document_attachments (deleted_at);
```

存储：`app.attachments.dir`（默认 `./uploads/attachments`）下 `{owner_type}/{owner_id}/{uuid}_{原文件名}`；
**展示名与磁盘名分离**。

### 71.3 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/knowledge/{id}/attachments` | multipart `file`；≤50MB（`app.attachments.max-bytes`），超限 **413**；权限：`canWriteKnowledgeTags` |
| GET | `/api/v1/knowledge/{id}/attachments` | 列表：`fileName/fileSize/contentType/uploadedAt/uploadedBy/uploaderName`（uploaderName 一次批量解析） |
| GET | `/api/v1/knowledge/{id}/attachments/{attachmentId}/download` | 文件流；`Content-Disposition` 同时给 ASCII 回退与 **RFC 5987** `filename*=UTF-8''…`；**归属不符 → 404** |
| DELETE | `/api/v1/knowledge/{id}/attachments/{attachmentId}` | 软删（`deleted_at`）+ 删物理文件；之后列表不含、下载 404 |
| POST/GET/DELETE | `/api/v1/documents/{id}/attachments[...]` | 同构；权限 `canWriteDocument` |

**没有按 `storage_path` 直读的端点** —— 这是刻意的：防路径穿越与越权读。

### 71.4 列表 `status` 参数（知识库 / 文档）

```
GET /api/v1/knowledge?status=DRAFT|PUBLISHED|all&page=&size=&keyword=&category=
GET /api/v1/documents?status=DRAFT|PUBLISHED|all&page=&size=&keyword=&category=&assetId=&taskId=
```

- 大小写不敏感；**非法值 → 400**（`StatusFilter.normalize`），空/`all` = 不过滤；
- **在数据库过滤**（Specification 谓词 + `lower(status)=?`），不是当前页过滤 —— 所以 `totalElements/totalPages` 跨页正确；
- 这修掉了 t159 的遗留问题：前端原来只能对当前页筛「草稿/已发布」，翻页后结果就是错的。**前端可以把「当前页过滤」改成传 `status` 参数。**

### 71.5 前端可以移除 base64 方案了（替换点）

| 位置 | 现在 | 改成 |
|---|---|---|
| `utils/attachments.ts#buildAttachmentBlock` | 把文件编码成 base64 块塞进正文 | **删除**；上传改用 `POST /{knowledge\|documents}/{id}/attachments`（先建记录拿 id，再传附件） |
| `utils/attachments.ts#downloadRecordFile` | 从正文解析 base64 还 Blob | **删除**；改用 `GET .../attachments/{attachmentId}/download`（后端已给正确的中文 `Content-Disposition`，浏览器直接下载即可） |
| 详情页附件展示 | 解析正文块 | `GET .../attachments` 列表渲染（含 `uploaderName`/`uploadedAt`） |
| 上传页 `/knowledge/upload`、`/documents/upload` | 超 40KB 拦下 | 可以放到 **50MB**；流程：先建草稿记录 → 传附件 → 跳详情 |
| 列表页状态筛选 | 当前页本地过滤 | 传 `status=DRAFT/PUBLISHED`（服务端过滤，翻页正确） |

> 旧的正文 base64 块**不做迁移**：历史正文里的块仍能被前端解析显示（保留兼容），新上传一律走附件端点。

### 71.6 验证（`DocumentAttachmentTest` 15 例）

```bash
cd backend && mvn -B test -Dtest=DocumentAttachmentTest   # 15/15
```

上传+列表（元数据完整、uploaderName、落盘目录）；下载中文名 `filename*=UTF-8''…` 且内容长度一致；
删除（软删 + 物理文件删除 + 列表不含 + 下载 404）；**跨实体下载 404**；超上限 413、空文件 400；
文件名穿越净化（`../../etc/evil.pdf` → `evil.pdf`）；非作者/非管理层上传 403；
文档附件全链路（上传/列表/下载/删除）；**uploaderName 查询数与附件条数无关**（服务层 1 条 vs 5 条相等，
测量前 `flush+clear` 避免 auto-flush 把 INSERT 计入）；知识库 `status=DRAFT` 跨页 total 正确（7 草稿 + 1 已发布）；
文档 `status=PUBLISHED`/`all` 与非法 400；V33 脚本守卫（建表/两索引/collation/软删统计/manual-ddl 等价）；
未登录 401；归属隔离（ownerType 不串）；下载/删除不存在附件 404。

### 71.7 已知边界

1. 附件**不写审计**（只读/薄写操作，任务未要求）；如需审计在 service 加 `AuditLogService.log` 即可。
2. 删除是「软删 DB + 立即删物理文件」；若删除文件失败只 WARN（DB 已标删），需靠运维巡检清理孤儿文件。
3. 历史 base64 正文块不迁移（保持可读）；新能力只走附件端点。
4. 单附件 50MB 是应用层限制；nginx 的 `client_max_body_size` 需同步放大，否则 413 会先被网关拦下。
---

## 69. 第四十五轮（t163 收口四：V32/V33 + 上传审批 + 知识库/文档附件 + 整页化，前后端同批）

时间：2026-11-10 18:42–20:1x。

| 项 | 结果 |
|---|---|
| 全量回归 | `mvn -B clean test` **连续 2 次全绿：1053 例 / 0 / 0 / 0**（任务预估 1060+，实测 1053 —— 预估偏差，非缺陷） |
| 迁移双场景 | **V29–V33 五份一起** × 空库/生产克隆 × `general_ci`/`unicode_ci` × 各 2 遍 → **56 项断言全绿**；生产库 V32/V33 各执行两遍 `exit=0`：两表就位、**5 个新索引**、表 collation `utf8mb4_unicode_ci`、`upload_approval_requests` 16 列 / `document_attachments` 13 列 |
| nginx | `client_max_body_size 200m`、`/api/` 内 `proxy_request_buffering off`、`client_body_timeout/proxy_*_timeout 300s` 均在；**50MB 经 8089 上传成功（201 PENDING，未被网关 413）** |
| 部署 | 后端 jar **73,535,828 B**（旧 jar 备份），重启 18s 内 login=200；前端清 `.tmp` 后构建 **`index-OTmOKSRW.js` 2,938.10 kB / `index-C9wuC1eN.css` 30.80 kB**，`sync-dist` 5 文件，线上已引用新 chunk |
| 冒烟 | **314 / 314 PASS，0 FAIL，0 SKIP**（新增 12 条：上传审批 4 + 附件 6 + status 2… 合计 12） |

### 69.1 上传审批真机验收（API 11/11）

| 断言 | 证据 |
|---|---|
| 50MB 经 nginx → PENDING | `201 {requestId, status:PENDING, fileSize:52428800}`；pending 目录 0→1 |
| PENDING 不进业务列表 | `GET /tools?keyword=…` 未命中；管理员列表 `GET /admin/upload-approvals?status=PENDING` 可见 |
| 批准 → 记录出现 + 落正式区 | `approve` 200 → `APPROVED`；`GET /tools` 命中；正式区文件 **52,428,800 字节**完整；该工具 `filePath=approved/…` |
| 拒绝 → 临时文件删除 | `reject` 200 → `REJECTED`；pending 目录 1→0；该名不在 `/tools` |
| 超上限 → 413 | 把 `app.upload.max-bytes` 压到 1MB 后 2MB 上传 → **413**「文件超过上限 1048576 字节（当前 2097152）」；恢复默认后同一文件 → **201 DIRECT** |

### 69.2 附件与 status 真机验收

- 5MB PDF 上传（中文名 `渗透测试报告.pdf`）→ 列表可见 → 下载 `Content-Disposition` 同时给 ASCII 回退与 **`filename*=UTF-8''…`**，回读 **5,242,880 字节**完全一致。
- 跨实体（用 `documents/{id}` 路径取知识库附件）→ **404**；非作者上传附件 → **403**；删除 → 200，随后下载 **404**。
- **status 数据库过滤**：`all=16`、`DRAFT=11`、`PUBLISHED=5`（`11+5=16` 合计一致）；`status=PUBLISHED&size=2` → `totalPages=3`，第 3 页有数据（跨页正确）；非法 status → **400**。

### 69.3 前端真机验收（真实 Edge + CDP）

- `/knowledge/new`、`/documents/new` **整页**（`.ant-modal` = 0，编辑器控件就位）。
- `/knowledge/upload`：选文件 → 标题自动带出（`t163-note2`）→ 选分类 → 点「**生成知识条目**」→ 发 `POST /api/v1/knowledge` → 跳到 `/knowledge/{id}`（草稿记录）。
- 工具页：`上传工具` → 25MB 文件 → **`POST /api/v1/uploads/pending` 被发出**（大文件统一入口生效）。
- 管理员 `/admin/upload-approvals`：PENDING 行可见（`共 1 条`）→ 拒绝成功，列表反映拒绝状态。
- 非管理员：`/admin/upload-approvals` 显示无权限、侧栏**无「上传审批」入口**。

### 69.4 本轮发现并记录的问题（未改代码，交由任务方决定）

1. **PENDING 上传丢失业务名/描述**：`V32` 表没有 `name`/`description` 列，提交时的 `name` 在批准后没被采用 —— 批准生成的工具名回退为**文件名去扩展名**（实测传 `name=t163_big_<ts>`、文件名 `t163-big50.bin`，最终工具名 `t163-big50`）。要修需在 V32 加两列并在批准时回填。
2. **工具页 PENDING 提示不稳定**：`POST /uploads/pending` 确实发出，但「文件已提交，等待管理员审核」toast 与持久面板在 3 次自动化里仅 1 次可稳定观察到，`localStorage.owl_my_uploads` 两次为空 → 需人工点一次确认（也可能是 CDP 设置文件的时序导致 `packageFile` 未进 antd fileList）。
3. **工具元数据行不即时刷新**：PENDING 分支在 `catch/return` 与 `fetchData()` 之前返回，列表要手动刷新才出现新建的元数据行（小 UX 问题）。
4. **回归总数 1053**：任务预估 1060+，实测 1053，属预估偏差（两轮一致）。
5. 部署机存在**陈旧的 `backend/uploads` 目录**（656 个历史文件，非线上存储目录），已清理；线上存储为 `~/apps/vuln-audit/uploads/`（`WorkingDirectory` 决定）。

### 69.5 清理

- 临时开关（dev-mode / IP / 重置 / 锁定阈值）与 `APP_UPLOAD_MAX_BYTES` 覆盖全部移除并重启复核：**发码无 devCode**、锁定第 6 次 **429 + Retry-After 900**、**2FA 开启用户数 = 0**（t158 教训）。
- 临时库 `vuln_audit_verify_*`/`vuln_audit_clone_*` 全 DROP，`vulnapp` 临时授权已 REVOKE；`uploads/pending`、`uploads/approved` 清空；`upload_approval_requests`、`document_attachments` 归零。
- 数据终态：`customers=1 projects=1 phases=0 assets=3 links=3`、`orphan=0`、`tag_definitions=3`、`files=4`、`deliverables=0`、`users=12`、`codes/requests/rm_tokens=0`、`2fa_on=0`。
### 70.9 t165 增补：PENDING 也保留 `displayName` / `description`

**问题**（t163 真机验收发现）：V32 的 `upload_approval_requests` 没有这两列，用户填的工具名/描述在「走审批」这条路径上丢失，
批准后工具名回退成「文件名去扩展名」（实测 `t163_big_<ts>` → `t163-big50`）。

**数据**：V34（幂等 + manual-ddl 等价段）给 `upload_approval_requests` 加
`display_name VARCHAR(200)`、`description VARCHAR(1000)`（均显式 `COLLATE utf8mb4_unicode_ci`）。

**上传契约（更新）**

```
POST /api/v1/uploads/pending
  multipart: file（必填）、targetType、displayName（≤200，可空）、description（≤1000，可空）
  # 兼容：老的 name 表单字段仍可用（与 displayName 同义，displayName 优先）
```

- `displayName` 超过 200 字符、`description` 超过 1000 字符 → **400**（在落盘/落库之前校验）；
- **两条分支都保留**：超门限 → 写进 PENDING 记录；未超门限（DIRECT）→ 直接用于建业务记录。

**批准时的回填规则**

| 字段 | 优先取 | 回落 |
|---|---|---|
| 业务实体名（Tool.name） | `display_name` | 文件名去扩展名（旧行为） |
| 业务实体描述（Tool.description） | `description` | `经管理员批准的大文件上传`（旧行为） |

**响应**：`GET /api/v1/uploads/pending/{id}` 与 `GET /api/v1/admin/upload-approvals` 现在都返回
`displayName` / `description`（增量字段，不破坏既有契约，前端可选用作列表标题）。

**验证**：`UploadApprovalTest` **20/20**（原 14 + 新 6）：PENDING 保留两字段并可查、批准后工具名 = displayName 且 description 落库、
不传时回落行为不变、DIRECT 分支同样生效、超长 400（200/1000 边界）、V34 脚本守卫。

### 70.10 t166 增补：「我的上传」列表 + 撤回（替换前端本地登记）

**问题**（t164）：前端「我的上传」只能靠自己 `localStorage` 登记 requestId，再逐条 `GET /uploads/pending/{id}` 回查，
**换设备/清缓存即丢**。

**新端点**

```
GET /api/v1/uploads/my?status=PENDING|APPROVED|REJECTED|EXPIRED|CANCELLED|all&page=&size=&keyword=
DELETE /api/v1/uploads/my/{id}          # 仅本人 PENDING：撤回
```

- `GET /uploads/my`：**只含 `requested_by = 当前用户`**；默认 `status=all`；按 `requestedAt desc`；
  `size ≤100`（默认 20）；`keyword` 匹配 `fileName` 或 `displayName`（大小写不敏感）；`status` 非法 → **400**；
- 每项：`id, targetType, fileName, displayName, description, fileSize, status, requestedAt, reviewedAt,
  reviewer{id,username}, reviewNote, purgeAfter, targetId`（`targetId` 仅 APPROVED 有值）；
- `DELETE /uploads/my/{id}`：**只能撤回自己的 PENDING** → 删临时文件 + 状态置 **CANCELLED**；
  他人的请求 → **403**；非 PENDING（已批准/已拒绝/已过期/已撤回）→ **409**；不存在 → 404；
- 撤回后管理员也批不了（状态非 PENDING → 409），临时文件已不存在。

**状态机补充**：`PENDING` →（申请人撤回）**`CANCELLED`**（与 APPROVED / REJECTED / EXPIRED 并列）。

**管理端对齐**：`GET /api/v1/admin/upload-approvals` 增加 `keyword`（fileName/displayName）与 `requestedBy`（按人查），
`status` 白名单含 `CANCELLED`。

**前端可以删掉本地登记了**

| 位置 | 现在 | 改成 |
|---|---|---|
| `utils/myUploads.ts`（整体） | localStorage 存 requestId 列表 + 逐条回查状态 | **删除**；直接 `GET /api/v1/uploads/my?status=all&page=&size=` 渲染 |
| 「我的上传」页/角标 | 本地计数 | 用列表的 `totalElements`，需要分状态就传 `status=PENDING` |
| 「撤回」按钮 | 没有 | `DELETE /api/v1/uploads/my/{id}`（只有 PENDING 显示；403/409 要处理） |

### 70.11 t166 的验证补充

`UploadApprovalTest` **30/30**（原 20 + 新 10）：三态都能查到且 `APPROVED` 带 `targetId`、他人请求不在我的列表、
`status=PENDING` 只返回待审 + 非法 status 400、分页结构、keyword 命中 fileName 与 displayName、
撤回自己的 PENDING（文件删除 + CANCELLED + 管理员再批 409）、撤回他人 403、撤回已批准 409、
管理端 keyword/requestedBy 过滤 + 非法 status 400、未登录 401、撤回不存在 404。
---

## 70. 第四十六轮（t173 收口五：V34 + 上传审批完善 + 资产/漏洞列表页整页重写）

时间：2026-11-10 21:18 – 2026-11-11 09:2x（跨夜，含两次被中断后的续跑）。

| 项 | 结果 |
|---|---|
| 全量回归 | `mvn -B clean test` **连续 2 次全绿：1069 例 / 0 / 0 / 0**（任务预估 1090+，实测 1069，两轮一致，属预估偏差） |
| 迁移双场景 | **V29–V34 六份一起** × 空库/生产克隆 × `general_ci`/`unicode_ci` × 各 2 遍 → **58 项断言全绿**；生产库 V34 两遍 `exit=0`：`display_name varchar(200)` / `description varchar(1000)`，collation 均 `utf8mb4_unicode_ci` |
| 部署 | 后端 jar **73,539,997 B**（旧 jar 备份），重启 19s 内 login=200；前端清 `.tmp` 后构建 **`index-CxaHm_0M.js` 2,938,525 B / `index-C9wuC1eN.css` 30,800 B**，`sync-dist` 5 文件，线上已引用新 chunk |
| 冒烟 | **319 / 319 PASS，0 FAIL，0 SKIP**（新增 5 条：`/uploads/my` 列表 / status=PENDING / 非法 status 400 / 撤回不存在 404 / 管理端 CANCELLED+keyword） |

### 70.1 t172 修掉的既有缺陷：资产列表「客户/项目列重复定义」

整页重写前 `pages/assets/index.tsx` 里 **客户列与项目列各定义两次**（同 key 重复的列描述），表现为表头出现两份「客户」「项目」并触发 React 重复 key 告警。重写后单一定义。
**真机证据**：表头 12 列为 `资产名称|资产编码|所属团队|负责人|客户|项目|类型|来源|优先级|状态|标签|操作`，`客户` 出现 **1** 次、`项目` 出现 **1** 次。

### 70.2 真机验收 A：资产列表（12/12）

- 表头 **12 列**；客户/项目各 **1** 次（上节证据）。
- **标签列**：第 11 列 `.ant-tag` 有内容（示例「历史数据 / 历史项目」），2/5 行有标签。
- **逐行 `/assets/{id}/tags` 请求数 = 0**（`per-asset=0`，仅靠内联 `tags`）。
- 筛选区 5 个选择器（客户/项目/期次/标签等）。
- 「新建」→ `/assets/new`；**`?new=1` 重定向到 `/assets/new` 且 query 已清**。
- 行「编辑」→ `/assets/{id}/edit`（实测 `db5f8582-…/edit`）。

### 70.3 真机验收 B：漏洞列表整页重写（10/10 + 1 条未完成断言）

- **`<Drawer`/`<Modal` 双验为 0**：源码 grep `pages/vulnerabilities/index.tsx` = **0**（304 行，原 890 行）；DOM 实测 `drawer=0 modal=0`。
- 表头 **10 列**、分页可用：点第 2 页 → `GET /vulnerabilities?page=1&size=10&keyword=`。
- 严重程度筛选进参数：`GET /vulnerabilities?...&severity=HIGH`。
- **详情/编辑整页可达**：直接打开 `/vulnerabilities/{id}` 为整页（无 Drawer/Modal）→「编辑」→ `/vulnerabilities/{id}/edit` →「返回」回到详情。
- ⚠️ **未完成断言（新建全链路）**：`/vulnerabilities/new` 整页渲染正常（表单 + 提交按钮 + 5 个 Select），点击提交确实发出 `POST /api/v1/vulnerabilities`，但**请求体缺 `assetId`/`discoveredVersionId`**：
  `{"title":"…","type":"other","severity":"medium","description":"…","detail":"…"}` → **400 参数校验失败**。
  已尝试三种真实交互（下拉选项真实鼠标坐标点击、`ArrowDown+Enter`、先输入搜索词再 `ArrowDown+Enter`）——`type`/`severity` 提交的是**表单初值**（`other`/`medium`），说明我的交互没有把值写进表单。
  **结论**：该断言未能通过自动化完成，**需人工点一次确认**是「antd v6 可搜索 Select 与 Form 绑定」问题还是自动化局限；未以放宽断言的方式绕过。

### 70.4 真机验收 C：上传审批完善 + 我的上传（API 8/8，UI 关键项通过）

**API 8/8**：
- **V34 回填**：`POST /uploads/pending` 带 `displayName=我的工具` → `201 PENDING`（响应回显 `displayName=我的工具`）→ `GET /uploads/my?status=PENDING` 可见 → **approve 后 `GET /tools` 里名字就是「我的工具」**（不再回落 `t173_v34`）。
- `/uploads/my`：`status=PENDING` 有 2 条 → 撤回自己 PENDING → **200 + CANCELLED**，临时区文件 **2→1**；**已撤回再批准 → 409**（状态机严格）；撤回不存在 → 404。

**UI（真实 Edge）**：
- 工具页连续 3 次 25MB 上传：**提示命中数 2 → 4 → 4**（每张卡片命中 2 次，等效 1 → 2 → 2+）、**`GET /tools` 刷新 4 次**、上传走 `POST /uploads/pending`。
- **「我的上传」来自服务端**：清空 `localStorage`（仅留 `access_token`）后打开面板仍有 **5 行**、`GET /uploads/my` 请求 2 次。
- 撤回按钮存在并可点，撤回后行状态显示「已撤回」。
- ⚠️ 两处自动化观测不稳（已记录，不影响结论）：① 首次打开「我的上传」面板读到 `rows=0`（读得太早，未等 `GET /uploads/my` 返回）；② 3 次上传只捕获到 2 次 `POST /uploads/pending`（第 3 次可能被表单校验/时序挡下），但提示条与 `GET /tools` 刷新都显示上传确实发生。

### 70.5 其他观察

- 1680 宽视口下资产列表「编辑」链接的 x≈1959（在视口外，需横向滚动）；DOM 点击可用，属**布局小问题**。
- 造数需要用 `POST /api/v1/vulnerabilities` 时**必须带 `discoveredVersionId`**（缺则 400「发现版本ID不能为空」），真机造 12 条用资产 v1 的版本 id。

### 70.6 清理

- 临时开关（dev-mode / IP / 重置 / 锁定阈值 / upload 覆盖）全部移除并重启复核：**发码无 devCode**、锁定第 6 次 **429 + Retry-After 900**、**2FA 开启用户 = 0**。
- 临时库 `vuln_audit_verify_*` / `vuln_audit_clone_*` 全 DROP、`vulnapp` 临时授权 REVOKE；`uploads/pending`、`uploads/approved` 清空。
- 数据终态：`customers=1 projects=1 phases=0 assets=3 links=3`、`orphan=0`、`tag_definitions=3`、`files=4`、`vulns=0`、`users=12`、`codes/requests/rm_tokens=0`、`2fa_on=0`、`upload_approvals=0`、`attachments=0`、`tools=0`。

## 72. 第五十四轮（自定义填表系统 1/5：V35 数据模型 + 模板/字段 CRUD，t178）

### 72.1 这轮做了什么、没做什么

**做了**：四张表的数据模型（V35）+ 模板/字段的 ADMIN CRUD（含全部限制规则与审计）+ `FormFieldSource` 白名单枚举。
**没做（后续块）**：生成实例时取快照、填写/提交、确认/退回、CSV/XLSX 导出、字段级指派逻辑。

### 72.2 数据模型（四张表，V35）

| 表 | 作用 | 关键列 |
|---|---|---|
| `form_definition` | 表单**模板** | `code`（唯一，含软删行，创建后不可改）、`name`、`description`、`enabled`、`deleted_at` |
| `form_field` | 模板**字段** | `field_key`（同模板唯一）、`label`、`field_type`、`required`、`sort_order`、`source_type`（空=手填）、`options`（SELECT 的 JSON 数组） |
| `form_submission` | 表单**实例** | `definition_id`、`title`、`status`（DRAFT/ASSIGNED/SUBMITTED/CONFIRMED/RETURNED）、`assigned_to`（**第一期只做表单级指派**）、`confirmed_by`（可选）、三个时间戳 |
| `form_value` | 实例的**每个字段值** | `source_value` / `filled_value` / `override_value` 三层 + `assigned_to`（字段级指派**预留位**，第一期不使用） |

索引：`uk_form_definition_code`、`uk_form_field_key(definition_id,field_key)`、`uk_form_value_submission_field(submission_id,field_id)`、
`(status,assigned_to)`、`(created_by)`、`(definition_id,sort_order)`；四表全部显式
`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`。

### 72.3 三层值语义（本系统最关键的设计）

| 列 | 谁写 | 可变性 | 用途 |
|---|---|---|---|
| `source_value` | 系统（生成实例时从业务库取） | **生成后不可变**（快照） | 记录「当时的」客户名/资产状态等，业务后续改名不影响历史表单 |
| `filled_value` | 填表人 | 可改（提交前） | 人工填写的内容 |
| `override_value` | 有权限的修改人 | 可改 | **只影响导出**的覆盖值 |

**为什么修改不回写业务库**：表单是「某一时点的一次记录/申报」。回写会让历史失真（原来填的是 A，后来业务被改成 B，
历史表单就"篡改"了），也和审计口径冲突（谁在什么时候填的、填的是什么必须可追溯）。
需要更新业务数据的场景，走各业务模块自己的编辑流程并留审计。

### 72.4 `source_type` 白名单（`FormFieldSource`，单一事实来源）

`CUSTOMER.NAME|CODE|INDUSTRY`、`PROJECT.NAME|CODE|TYPE`、`ASSET.NAME|CODE|TARGET_TYPE|PRIORITY|STATUS`、
`TEAM.NAME`、`USER.FULL_NAME`。枚举同时提供 `label`（中文显示名，前端下拉直接用）与 `resolve(subject)`
（**本块只留接口**：真正的取值依赖业务实体字段与软删/权限口径，放到生成实例的块实现）。

- `source_type` **非空 = 库字段**（生成时快照）；为空 = 手填字段；
- 前端可用 `GET /admin/form-definitions/{id}` 返回的 `sourceLabel` 直接显示「来源：客户名称」。

### 72.5 接口（全部 `hasRole('ADMIN')`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/admin/form-definitions?keyword=&enabled=&page=&size=` | 分页，含 `fieldCount` |
| POST | `/api/v1/admin/form-definitions` | `{code,name,description?,enabled?}` → **201**；code 重复（**含软删行**）→ 409 |
| GET | `/api/v1/admin/form-definitions/{id}` | 含 `fields[]`（按 `sort_order`） |
| PUT | `/api/v1/admin/form-definitions/{id}` | 改 name/description/enabled；**改 code → 403** |
| DELETE | `/api/v1/admin/form-definitions/{id}` | 软删；已被实例引用 → 409（带引用数「N 个表单实例」） |
| POST | `.../{id}/fields` | 新增字段；`field_key` 同模板唯一 → 409 |
| PUT | `.../{id}/fields/{fieldId}` | 更新；**已有表单数据时禁止改 `field_type`/`field_key`** → 409 |
| DELETE | `.../{id}/fields/{fieldId}` | 软删；已有数据 → 409 |
| PUT | `.../{id}/fields/reorder` | `{fieldIds:[...]}` 必须正好覆盖全部字段 → `sort_order` 连续 0..n-1 |

字段校验：`field_type` ∈ TEXT/TEXTAREA/NUMBER/DATE/SELECT/CHECKBOX/FILE（非法 400，消息带可选值）；
`source_type` 白名单（非法 400）；**`SELECT` 必须给非空 `options`（JSON 数组）**，其它类型忽略 options。

审计：`UPDATE_FORM_DEFINITION`（模板增/改/删）与 `UPDATE_FORM_FIELD`（字段增/改/删/重排）**每次操作恰好 1 条**，
端点已进 `AuditTrailInterceptor.SKIP_ENDPOINTS`。

### 72.6 验证（`FormDefinitionTest` 17 例）

```bash
cd backend && mvn -B test -Dtest=FormDefinitionTest   # 17/17
```

覆盖：模板 CRUD 全链路（创建 201/详情/改名/列表 keyword/软删后列表不含+详情 404）；code 重复 409 **且软删后仍不可复用**；
改 code 403（传原值 200）；被实例引用删除 409 且**未软删**；三个字段按序返回（库字段带 `sourceLabel`、手填 `sourceType=null`、SELECT 带 options）；
`field_key` 同模板重复 409 而跨模板可同名；SELECT 缺 options 400 而非 SELECT 的 options 被忽略；
非法 `field_type`/`source_type` 400（消息含关键字）；reorder 生效且 `sort_order` 连续、覆盖不全 400；
**已有表单数据的字段禁止改类型/删除（409）但可改 label**、无数据时可改类型并删除；
非 ADMIN 403、未登录 401；审计条数（模板 2 条 + 字段 3 条）；跨模板越权访问字段 404；列表 `enabled` 过滤；
V35 脚本守卫（四表 + 三个唯一索引 + 四表都显式 collation + 三层值列 + manual-ddl 等价段）。

### 72.7 已知边界（第 1/5 块）

1. `FormFieldSource.resolve(subject)` 只留接口，调用会抛 `UnsupportedOperationException`（生成实例的块实现）。
2. 字段级指派只有列（`form_value.assigned_to`），第一期不写不读。
3. 模板/字段删除都是**软删**；字段软删后其历史 `form_value` 仍保留（导出按需 join）。
4. 模板 `code` 不可改、编码不复用（含软删行唯一）——历史表单/导出靠它定位口径。

### 72.8 t179 增补：生成实例（库快照）+ 指派 + 发起人加字段

### 72.8.1 V36（补充迁移，两件事一起做）

| 变更 | 原因 |
|---|---|
| `form_field.submission_id`（可空） | **实例级字段**（发起人在某个表单里临时加的）。`NULL` = 模板字段；非空 = 只属于该实例。选了 task 的**方案 ①**（加一列），比另建存储改动小且导出能一条 SQL 拉平 |
| `form_submission.subject_type` / `subject_id` | V35 建表时没落「业务主体」，而生成快照、详情/列表的「主体名」都必须知道挂的是谁。**不能靠标题反解**，所以在同一个迁移里补上（避免再开 V37） |

> 因此模板侧的所有查询都加了 `submission_id IS NULL` 条件（`FormDefinitionServiceImpl` 已同步改），
> 否则管理员会在模板详情里看到别人实例里的临时字段。

### 72.8.2 生成实例：快照怎么取

```
POST /api/v1/forms/submissions
{ definitionId, subjectType: CUSTOMER|PROJECT|ASSET, subjectId, title?, assignedTo? }
→ 201 { id, definitionId, status, fieldCount, snapshotFilled, snapshotNull, skippedSources[] }
```

1. 模板必须存在且 `enabled`（否则 400/404）；**主体必须存在、未软删**，且对调用者可见
   （ASSET 走 `canReadAsset`，越权 → 403；CUSTOMER/PROJECT 沿用列表读口径 → 404）；
2. 遍历模板字段：`sourceType != null` 的按 `FormFieldSource.resolve(主体)` **当场取值**写进 `source_value`；
   手填字段 `source_value = null`；
3. **取不到值不报错**：对象字段为空 → `source_value = null` 并计入 `snapshotNull`；
   `sourceType` 不在枚举里 → 跳过 + **WARN**（`skippedSources` 回给前端），**绝不 500**；
4. `assignedTo` 有值 → `status=ASSIGNED` 且写 `assigned_at`；否则 `DRAFT`；
5. 返回 `snapshotFilled / snapshotNull` 给前端提示「本次从库取到 N 个字段，M 个为空」。

### 72.8.3 「修改不回写库」是由模型保证的（不是靠自觉）

- 服务层对 `customer / project / asset` **只有读**（取快照、取名字），没有任何 setter 调用；
- `form_value` 三列各有唯一来源：`source_value` 只在生成时写一次，`filled_value` 只能由填写接口写，
  `override_value` 只能由覆盖接口写（后续块）；**没有任何代码路径把 `form_value` 写回业务表**；
- 因此业务数据后来改名/改状态，历史表单里的快照**保持原样**（有专门测试用例守这条）。

### 72.8.4 指派

```
POST /api/v1/forms/submissions/{id}/assign  { assigneeId, note? }
```
- 仅**发起人**或 **ADMIN**（其他人 403）；被指派人必须存在且 `status=active`（404/400）；
- `DRAFT/ASSIGNED/RETURNED` → `ASSIGNED`（刷新 `assigned_at`）；`SUBMITTED/CONFIRMED` → **409**；
- 审计：`ASSIGN_FORM_SUBMISSION`（每次指派 1 条）。

### 72.8.5 发起人加字段（实例级，不污染模板）

```
POST   /api/v1/forms/submissions/{id}/fields  { label, fieldType, required?, options? }
DELETE /api/v1/forms/submissions/{id}/fields/{fieldId}
```
- 只允许**手填类型**（接口不接受 `sourceType`，想加库字段只能传非法类型 → 400）；
- `field_key` 自动生成 `instance_<序号>`，`sort_order` 接在模板字段之后；
- 仅**发起人**（他人 403）；`DRAFT/ASSIGNED/RETURNED` 才能加，已提交 → 409；
- 删除：仅**发起人**、仅实例级字段（模板字段经此接口 → 404）、已被填写 → **409**。

### 72.8.6 查询

| 方法 | 路径 | 规则 |
|---|---|---|
| GET | `/api/v1/forms/submissions/mine?status=&page=&size=` | **我发起的**；含模板名、主体名、指派对象、进度 `filledCount/fieldCount`（批量两次统计，无 N+1） |
| GET | `/api/v1/forms/submissions/{id}` | 详情含全部字段与**三层取值**；发起人/被指派人/确认人/ADMIN 可读，**其他人 404**（不泄露存在性） |

### 72.8.7 已知边界（第 2/5 块）

1. `filled_value` 的写入接口（填写/提交）在下一块；本块详情里它一律为 null。
2. `override_value` 的写入接口在后续块（只影响导出）。
3. 字段级指派仍只有列（`form_value.assigned_to`），本块不读不写。
4. 主体被删除后，表单仍可读，`subjectName` 为 null（正文靠快照，不依赖业务库）。
5. `resolve()` 对未知主体类型返回 null（不抛异常）；枚举覆盖的 13 个来源全部实现。
