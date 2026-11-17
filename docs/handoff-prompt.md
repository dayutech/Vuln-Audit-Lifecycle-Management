# 项目交接提示词（可直接复制给下一个 agent）

> 用法：把下方分隔线之间的内容整段作为新会话的系统/初始提示词。

---

# 项目交接：猫头鹰漏洞审计生命周期管理系统（继续开发）

你接手一个**已完成主体交付、需要继续演进**的项目。请先读完本提示再动手。

## 1. 项目是什么

「猫头鹰漏洞审计生命周期管理系统」（Owl Vulnerability Audit Lifecycle Management）——
客户/项目/资产/漏洞的审计全生命周期管理平台，含 RBAC、审批、通知、知识库、工具库、审计日志、
双因子认证、自定义填表系统。中文界面 + 英文 i18n 双语文案。

**当前状态：19 条既定用户需求已全部实现、部署并通过验收**（后端 1134 例测试 x2 轮全绿、
冒烟 336 条 0 失败、真机验证通过）。你的任务是**在其之上继续开发/改进**，不要推倒重来。

## 2. 仓库与环境

```bash
git clone git@github.com:dayutech/Vuln-Audit-Lifecycle-Management.git
cd Vuln-Audit-Lifecycle-Management
git checkout master          # 交付基线 HEAD = 9475995（188 提交）
```

- 分支：`master`（唯一活跃分支，`remote == local`，工作树应保持干净）
- 关键目录：
  - `backend/`        Spring Boot 3.5.13 后端（`src/main/java/com/vulnaudit`）
  - `frontend/`       React 19 + TypeScript + Vite 前端
  - `backend/src/main/resources/db/migration/`   Flyway 迁移 **V1–V36**
  - `deploy/`         `README.md`（5300+ 行部署手册，**必读 §73 新主机**）、`manual-ddl.sql`、
                     `nginx-vuln-audit.conf`、`vuln-audit-backend.service`、`sync-dist.sh`、`smoke-result.txt`
  - `docs/acceptance-report.md`  2490 行验收报告（**必读「第三部分：最终交付验收」**）
  - `scripts/smoke.sh`           冒烟脚本（336 条用例）
- 本地开发机（Windows）：`D:\WorkDirQiax\Vuln-Audit-Lifecycle-Management`

## 3. 技术栈与构建

后端：
- Spring Boot 3.5.13 / Spring Security + JJWT / JPA(Hibernate) / MariaDB / springdoc
- **`java.version=17`，必须用 JDK 17 构建**：Windows 下 `$env:JAVA_HOME='D:\ProgramFiles\Java\jdk-17'`
  （机器上还有 jdk1.8/jdk11/jdk21，**用错版本会因 text block/record 语法直接编译失败**）
- 构建：`cd backend && mvn -B -DskipTests package`（产物 `target/vuln-audit-backend-1.0.0-SNAPSHOT.jar`）
- 生产 `SPRING_FLYWAY_ENABLED=false`：**迁移不会自动执行**，改表必须同时
  (1) 加 `V{n}__xxx.sql` (2) 在 `deploy/manual-ddl.sql` 补等价段（幂等） (3) 在目标库手工执行

前端：
- React 19 + TS + Vite + antd **6** + zustand + react-router 7，自建 i18n（`zh-CN.ts`/`en-US.ts` + `domains/*`）
- 构建/校验：
  ```bash
  cd frontend
  npm install --legacy-peer-deps
  rm -rf node_modules/.tmp          # 必须清：陈旧 tsbuildinfo 会报假错
  npx tsc -b                        # 真正的类型门禁（tsc -p 是空操作）
  npm run build
  ```
- antd v6 注意：`.ant-select-selector` 已不存在（点击要落到内部 `input`）；
  `Tag`/`Table` 不转发 `data-testid`（需外层容器承载）

## 4. 部署目标（当前生产）

| 项 | 值 |
|---|---|
| 主机 | `192.168.188.226`（主机名 `leopc`，Ubuntu 24.04） |
| 登录 | `ssh leo@192.168.188.226`，密码 `IOThacking123.`（sudo 同密码） |
| 访问地址 | **http://192.168.188.226:8089** |
| 应用账号 | `admin` / `admin` |
| 数据库 | MariaDB 10.11.14，库 `vuln_audit`（`utf8mb4_unicode_ci`），账号 `vulnapp` / `VulnApp_226#Db7q` |
| 后端 | 端口 8088；systemd **user** 单元 `vuln-audit-backend`（已 `enable-linger`）；日志 `~/apps/vuln-audit/logs/backend.log` |
| 后端目录 | `~/apps/vuln-audit/{backend/target, deploy/backend.env, uploads/{pending,approved}, logs}` |
| 前端 | nginx 8089 -> 127.0.0.1:8088；静态站点 `/var/www/vuln-audit` |
| nginx 站点 | `/etc/nginx/sites-available/vuln-audit`（`client_max_body_size 200m`） |
| 关键环境变量 | `APP_TOTP_SECRET_KEY`（32 字节 Base64，**缺失则 2FA init/confirm 返回 503；轮换会让已开启用户 TOTP 失效**） |

其他主机：`192.168.188.200`（旧主机）**已按要求彻底清理**，不要再往那里部署。

从 Windows 走 SSH（本机已装 PuTTY，226 的 sshd 对连续快速连接较敏感，失败先等几秒重试）：

```powershell
$HK='SHA256:dJkibXtkdOCaK3z5G+4I2Nd9mO74ot5tKrU1QQWuA4I'
D:\ProgramFiles\PuTTY\plink.exe -ssh -batch -hostkey $HK -pw IOThacking123. leo@192.168.188.226 "命令"
D:\ProgramFiles\PuTTY\pscp.exe -pw IOThacking123. -hostkey $HK 本地文件 leo@192.168.188.226:/tmp/
```

## 5. 测试与验收基线（改完必须复跑）

```bash
# 后端全量回归（约 24 分钟/轮）
cd backend && mvn -B clean test          # 基线：Tests run: 1134, Failures 0, Errors 0, Skipped 0

# 冒烟（336 条；基线 PASS 319 / FAIL 0 / SKIP 17）
BASE_URL=http://192.168.188.226:8089 ADMIN_USER=admin ADMIN_PASS=admin bash scripts/smoke.sh
```

**冒烟必读（否则会得到一片假失败）**：冒烟自带「错误密码期望 401」用例，会触发
IP 层登录锁定（默认 5 次/15 分钟 -> 429），导致 admin 拿不到 token、后续批量 SKIP/FAIL。
正确做法见 `deploy/README.md` **§73.5**：临时放宽 `APP_LOGIN_LOCKOUT_*` -> 跑完恢复默认 -> 重启复核。

部署顺序（**不可颠倒**）：

```
clean test  ->  package  ->  替换 jar  ->  重启服务
（运行中的 JVM 持有 jar 文件，先 package 再停服务会写不进去）
```

## 6. 已完成的功能（不要重复开发，改前先读代码）

| # | 功能 | 关键实现位置 |
|---|---|---|
| 1 | 登录暴力破解防护：IP 优先锁定 -> 跨 IP 升级锁账户 + 告警 + OTP 解锁 | `security/LoginLockoutTracker`、`AuthUnlockController` |
| 2 | 双因子（TOTP + 10 个恢复码 + 管理员强制） | `util/TotpUtils`、`security/SecretCipher`、`TwoFactorEnforcementInterceptor` |
| 3 | remember-me（独立长效 token 轮换 + 重放检测） | `RememberMeServiceImpl`、`/auth/refresh`、`owl_rm` cookie |
| 4 | 自助注册（手机必填+邮箱可选双验证码）+ 管理员审批 + 待审批拦截 | `RegistrationRequest`、`AdminRegistrationRequestController` |
| 5 | 忘记密码（用户名 + 手机/邮箱双因子一致校验 + 旧 token 失效） | `PasswordResetServiceImpl` |
| 6 | SSO 4 家 provider（开关 + 凭据 Web 配置 + 绑定/解绑 + 微信扫码入口） | `sso/*`、`components/sso/WechatQrModal` |
| 7 | 客户/项目编码自动生成（`CUST-`+7 位、`PRJ-yyyy-MM-dd-`+6 位随机大写） | `CustomerServiceImpl#resolveCode`、`ProjectServiceImpl` |
| 8 | 国标行业枚举（22 条）+ 行业/项目类型管理员可管理（内置不可改删） | `industry_types`、`project_types`、`AdminIndustryTypeController` |
| 9 | 客户/项目/资产**软删除**（`@SQLRestriction` + 恢复入口） | `deleted_at`、`/admin/{x}/{id}/restore` |
| 10 | 资产标签体系（分组已并入标签、自动打客户名+项目名标签、AND/OR 筛选、列表内联 tags 无 N+1） | `tag_definitions`、`AssetServiceImpl#loadTags` |
| 11 | 附件体系（资产文件/交付物、知识库/文档附件、分页筛选排序、上传人/备注） | `document_attachments`、`AttachmentListSupport` |
| 12 | 大文件上传需管理员审批（超 20MB 走临时区，**未批准业务表无记录**，拒绝即删） | `upload_approval_requests`、`UploadApprovalServiceImpl` |
| 13 | 自定义填表系统（模板/字段、**生成时快照**、指派、填写、**覆盖只影响导出**、确认/退回、CSV/XLSX 导出） | `form_definition/field/submission/value`、`FormValues.effectiveValue` |
| 14 | 审批中心（待审批/已通过/已拒绝 + 后端历史留存） | `/assets/my/access-approval-history` |
| 15 | 全站 i18n（中英）、登录页 8 套主题、品牌「猫头鹰」、移动端抽屉导航 | `frontend/src/i18n`、`components/login/themes`、`layouts/BasicLayout` |

## 7. 不可破坏的核心语义（改这些地方要先想清楚）

1. **填表系统三层取值**：`effectiveValue = override ?? filled ?? source`
   - `source_value` 是**生成时快照，永不更新**
   - `override_value` **只影响导出**，服务层对 customer/project/asset 只有读、**无 setter**
   - 导出必须复用 `FormValues.effectiveValue`，不得另写取值逻辑
2. **软删除**：唯一键仍占用（同 code 重建 -> 409）；所有列表/详情/统计默认过滤 `deleted_at IS NULL`
3. **可见性单一来源**：`AccessGuard#visibleAssetIds` 同时约束列表与详情，新加查询/筛选不得绕过
4. **上传审批**：PENDING/REJECTED 的文件不得出现在任何业务列表与下载端点
5. **审计**：写操作由 `AuditTrailInterceptor` 统一记录；新增端点若自身已写审计，
   必须加入 `SKIP_ENDPOINTS` 以免重复
6. **i18n 对等**：`en-US` 用 `Record<MessageKey,string>` 强制与中文词条一一对应，缺键 `tsc` 报错
7. **迁移 collation 硬规则**：新表显式 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`；
   迁移里所有**列与列比较**（JOIN/WHERE）显式 `COLLATE utf8mb4_unicode_ci`
   —— 否则在默认 `general_ci` 的实例上会抛 `ERROR 1267` 并**中断迁移**（历史踩过 3 次）

## 8. 已知限制（真实边界，别当成 bug 去"修"）

- 微信桌面扫码：授权回调落在手机浏览器，桌面端需刷新（无 device code 轮询）
- SSO 需真实 provider 凭据；未配置时按「未配置」提示
- 短信/邮件网关未接：默认只记 WARN，`dev-mode` 仅开发态回传 `devCode`（生产必须 false）
- CSV 导入候选上限 500（项目资产批量导入按 `available-assets` 分页匹配）
- 字段级指派：`form_value.assigned_to` 列已预留，仅实现表单级指派
- 上传审批目前仅 `TOOL` 批准后会建业务记录
- 交付物「版本」列不提供排序（该表无版本号列，只能按 UUID 排）
- 表单导出上限 5000 行（`app.forms.export.max-rows` 可配）
- `GET /users/me` 返回 400 属既有行为；前端用 `/users/me/preferences`、`/permissions`、`/2fa`

## 9. 环境级踩坑清单（会反复咬人）

1. **`sudo -S` 会抢走 stdin**：`cat cfg | sudo -S tee /etc/nginx/...` 会把密码当输入、
   把配置内容丢弃 -> 生成**空文件**；而 `nginx -t` 对空文件仍通过（表现：配置测试成功但端口不监听）。
   -> 先写普通文件，再 `sudo cp`。
2. **`users` 表有 NOT NULL 且无默认值的列**（如 `two_factor_enabled`、`user_roles.assigned_by`）：
   直接 `INSERT` 会报 `Field ... doesn't have a default value`。
3. **nginx `X-Forwarded-For` 必须写 `$remote_addr`**（不是 `$proxy_add_x_forwarded_for`），
   否则客户端可伪造 IP 绕过登录锁定。
4. **前端并行开发会互踩**：改同一批 i18n/路由文件容易产生"构建时红时绿"，
   统一由一人按模块提交，避免"提交后不能编译"的中间提交。
5. **`mvn test` 与 `npm run build` 不要并行**跑（CPU 打满会触发环境过载，任务中断）。

## 10. 协作与交付规则（来自本项目的实际教训）

1. **一条任务只做一件事**：多次失败都源于"一个任务打包两个功能"或"一次改两个模块"。
2. **改既有页面先量体积**：行数 / `Drawer`+`Modal` 数 / `useEffect` 数；
   删除 > 150 行或连锁未使用符号 > 15 个 -> **整页重写**，不要局部手术，
   更不要用"按花括号配平自动删声明"的脚本（两次事故都源于此）。
3. **不接受"看起来绿"**：凡声称"兜底/幂等/重试/不改业务库"的，必须有**能失败的测试**或**真机证据**。
4. **不伪造兼容**：任务描述与代码实际冲突时（列已存在、字段名不同），以代码为准并在报告中说明，
   不要为了对齐文字写假 SQL/假回填。
5. **提交粒度**：按模块提交，每次提交后必须能编译；`git status` 收尾要干净。
6. **上线前后端同批**：接口契约变更（如列表从数组改分页对象）必须前后端同批部署，否则页面空白。

## 11. 起步建议（第一小时）

1. 读 `deploy/README.md`（尤其 §73 新主机部署、§73.5 冒烟与锁定）与
   `docs/acceptance-report.md` 第三部分（19 条需求对照 + 失败任务映射 + 已知限制）。
2. 本地起一遍构建：后端 `mvn -B -DskipTests package`、前端 `tsc -b && npm run build`。
3. 确认线上可用：`curl -s -o /dev/null -w '%{http_code}' http://192.168.188.226:8089/` -> 200。
4. 跑一轮后端回归建立基线（约 24 分钟），确认仍是 1134 例全绿。
5. 如需改数据库：按 §3 三步走，并在**空库与生产克隆**上各跑两遍验证幂等。
