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