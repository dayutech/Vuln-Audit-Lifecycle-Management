# t107 验收契约（登录页重构 + 注册审批 + 找回密码 + SSO + 平台更名 + 主题解析新规则）

> 本文件由 QA 维护，作为 t107 的验收清单，同时也是给 t108（更名/徽标）、t109/t110（主题解析）、t112/t113（第三态）的精确期望。
> 执行 t107 前必须先确认门禁清单里所有任务 `completed`；否则不得对旧品牌 / 旧主题逻辑跑断言。

## 0. 依赖门禁（t107 的历史 deps 缺失后续追加任务）

t107 的 deps 仅 `t101,t102,t103,t104,t105,t106`；以下任务**由 captain 追加进 t107 验收范围，但不在任务图里**
（任务图因历史任务指向已移除成员而无法编辑）：

| 任务 | 内容 | 状态（2026-11-05 12:1x 快照） |
|---|---|---|
| t108 | 平台更名 + 猫头鹰徽标 | ✅ completed |
| t109 | 后端全局缺省主题（system_settings + 公开只读端点） | ✅ completed |
| t110 | 前端解析改为「本地标记 > 管理员缺省」+ 管理员设置界面 | ✅ completed |
| **t112** | 后端用户偏好第三态（NULL = 跟随系统默认） | ⏳ claimed |
| **t113** | 前端偏好弹窗改用第三态 | ⏳ pending |
| t111 | 拒绝注册后唯一键占用导致无法重新申请（注册链路缺陷） | ⏳ pending（**建议一并纳入门禁**：t107 的注册用例会走「拒绝 → 重新申请」） |

执行规则：

1. 被调度到 t107 时，**先逐个确认上表所有任务 `completed`**（含 t108 / t109 / t110 / t112 / t113，建议含 t111）；
2. 全部完成 → 正常执行；
3. 任一未完成 → **不跑品牌类 / 主题解析类断言**，把 t107 置回 `pending` 并告知 captain 等待；
4. 已全部完成但线上未发布 → 先按 t107 部署步骤（`clean test → package → restart` + 前端 `sync-dist`）发布后再验证。

## 0.1 主题解析新规则（t109/t110/t112/t113，本次修正核心）

```
?theme=            （仅预览，不进登录流程、不落盘）
  >  localStorage['login.theme']          （用户本机标记；三种时机才写/清）
  >  管理员设置的全局缺省（system_settings，V22）
  >  pencil（代码兜底）
```

**关键变化**：登录成功响应与 `GET /auth/me` **不得覆盖** `localStorage['login.theme']`。
t110 已把 t97 的 `syncLoginThemeFromServer()` **整个删除**（不是改只读），故 A4「登录前后本地标记完全相同」必过。

**`users.login_theme` 不参与登录页自动解析**（captain 2026-11-05 拍板）：
登录页在认证前渲染，拿不到访客身份，物理上读不到该用户的 `users.login_theme`；
靠「上次登录的是谁」去读，本身就等于一个本地标记，与 `localStorage` 等价。
用户偏好保留但只作两处显式用途：① 弹窗打开时**预选**；② 「**从账号同步到本机**」按钮（用户点击后才写本地标记）。

**本地标记只在三种时机写 / 清**（其余一律不动）：

| 时机 | 本地标记 | 账号偏好（t112 第三态后） |
|---|---|---|
| ① 用户在弹窗里主动选主题 | 写 `login.theme = <所选>` | 写 `login_theme = <所选>`（`followDefault=false`） |
| ② 点「从账号同步到本机」 | 写 `login.theme = <账号当前偏好>`；**即使等于管理员缺省也必须物化**（见 A6） | 不变 |
| ③ 点「跟随系统默认」 | **删除** `login.theme`（`getItem` 返回 `null`，见 A7） | `login_theme = NULL`、`followDefault=true`（t112） |

**跨设备不自动生效**：换设备 / 清缓存后按管理员缺省渲染，需用户点「从账号同步」——设计选择，写入报告 §3.3「已知限制」，不是缺陷。


### 三场景（缺一不可）

| # | 场景 | 期望 |
|---|---|---|
| T1 | **清空 localStorage 的新浏览器** 打开登录页 | 显示**管理员当前缺省**主题（缺省为 orbit 时应为 orbit），**不是写死的 pencil** |
| T2 | 用户选择 `owl` → 刷新 / 重开浏览器 | 仍为 `owl`（本地标记生效） |
| T3 | 管理员把全局缺省改为 `orbit` | **未选择过**主题的浏览器 → `orbit`；**选择过 `owl`** 的浏览器仍为 `owl`（互不覆盖） |

### 接口门禁

| # | 断言 | 判据 |
|---|---|---|
| A1 | `GET /api/v1/auth/login-theme` **未登录必须 200** | 不得被 Security 401 拦截；返回当前全局缺省主题 |
| A2 | `PUT /api/v1/admin/settings/login-theme` **非 ADMIN → 403** | 工程师 / 团队长 / 项目经理均 403 |
| A3 | `PUT` **非法主题值 → 400 且库内不变** | 400 后读 `GET /auth/login-theme` 仍为原值；`system_settings` 行未被改写 |
| A4 | **本地标记不被覆盖**（核心，强化版） | 选 `owl` → 登录 → 退出 → 登录页仍 `owl`；且**登录前后各读一次 `localStorage['login.theme']`，值必须完全相同**（登录响应 / `/auth/me` 均不得改写） |
| A5 | **「从账号同步」是唯一允许写入的路径** | 账号偏好为 `geometric`、本地标记为 `owl` 时：打开弹窗后本地仍 `owl`（仅预选）；点「从账号同步」后 `login.theme` 变为 `geometric`；再点「跟随默认」后该键被**清除** |
| A6 | **「已同步」必须物化键**（值等于缺省时也要写） | 账号偏好 = 管理员缺省（例如都为 `orbit`）：点 `pref-sync-from-account` 后 `localStorage.getItem('login.theme')` **不是 `null`**，且**等于 `orbit`**（键存在性 + 值双重断言，不得因「与缺省相同」而跳过写入） |
| A7 | **「跟随默认」必须删除键** | 点 `pref-follow-default` 后 `localStorage.getItem('login.theme')` **返回 `null`**（不是 `''`、不是缺省值）；随后刷新登录页显示管理员缺省 |

> **A6 / A7 的语义差异（必须有行为级证据，不能只比存储）**：两者当前渲染结果可能相同（都显示 `orbit`），
> 但「已同步」= 钉住该主题，「跟随默认」= 跟着管理员缺省走。因此追加一对行为断言：
> - **A6b**：A6 之后管理员把缺省改为 `owl` → 该浏览器**仍为 `orbit`**（已钉住）；
> - **A7b**：A7 之后管理员把缺省改为 `owl` → 该浏览器**变为 `owl`**（跟随缺省）。
>
> 前置与清理：执行 A6b / A7b 需要临时改管理员缺省，**测试结束必须恢复原缺省**（默认 `pencil`），并在报告里记录改前/改后值。

### 第三态断言（t112 后端 / t113 前端，完成后定稿）

| # | 断言 | 判据 |
|---|---|---|
| A8 | 「跟随系统默认」写入**第三态**（不再把旧缺省写进账号偏好） | 点 `pref-follow-default` → `GET /users/me/preferences` 返回 **`loginTheme:null` + `followDefault:true`** |
| A9 | 第三态**跟随**管理员缺省，不被冻结成旧值 | A8 之后管理员把缺省改为 `orbit` → 该用户偏好**仍为 `null`**（不被写成旧值），且登录页/`GET /auth/login-theme` 侧按 `orbit` 显示 |
| A10 | 参数互斥校验 | `PUT /users/me/preferences` 同时传 `followDefault=true` 与 `loginTheme` → **400**（且库内不变） |

> 配套回归：`GET /auth/me` 与登录响应在第三态下返回 **`loginTheme:null`**；前端落回「管理员缺省」而不是报错或闪成 pencil。
> 若 t113 的落地方式不同（例如不返回 `followDefault` 字段），以实际契约回写本节后再定稿。


### V22 迁移（`system_settings`）双场景

- **空库**：仅建库 → 执行 V22 → 表/行/默认值就绪（缺省应为 `pencil`，除非产品定义另有初始值）；
- **已有数据**：已有 `system_settings` 行时重复执行 → 幂等、不改写已存在的缺省值；
- 生产 Flyway 关闭 → `deploy/manual-ddl.sql` 必须有等价语句（含 `IF NOT EXISTS` / `INSERT ... ON DUPLICATE KEY` 类幂等守卫与默认值）。


## 1. 品牌断言（t108 交付后生效）

| # | 断言 | 判据 | 取值来源 |
|---|---|---|---|
| B1 | 浏览器标签标题 | `document.title` **精确等于** `猫头鹰漏洞审计生命周期管理系统` | `frontend/index.html` `<title>` |
| B2 | favicon | `link[rel~="icon"]` 指向猫头鹰资源（`/favicon.svg` 或 `/favicon.ico`），且 16×16 / 40×40 缩略图**可辨识**（非纯色块） | `frontend/index.html` + `frontend/public/` |
| B3 | 侧边栏展开 | 品牌区文案 = `app.brand.name` = `猫头鹰漏洞审计生命周期管理系统`；徽标元素存在 | `layouts/AppBrand.tsx` + `i18n/messages/domains/appMessages.ts` |
| B4 | 侧边栏折叠 | 只显徽标；hover Tooltip 文案 = **`app.brand.nameShort`** = `猫头鹰审计`（**不得**用长名 `slice` 截断） | 同上 |
| B5 | 登录页品牌区 | 徽标 + 长名 + slogan `洞察入微 · 审计可溯`（`app.brand.slogan`） | `pages/Login.tsx` + `appMessages.ts` |
| B6 | 英文态 | 长名 `Owl Vulnerability Audit Lifecycle Management System`、短名 `Owl Audit`、slogan 英文词条 | `en-US` 词条 |
| B7 | 旧名门禁 | `grep -rn "漏洞审计平台\|Vuln Audit" frontend/src frontend/index.html` → **0 命中**（包名 `vuln-audit`、路由、localStorage 键、`package.json` 不算） | 全仓静态检查 |

> 新增词条（t108）：`app.brand.name` / `app.brand.nameShort` / `app.brand.slogan`（+ 英文对等）。
> B4 必须用 `app.brand.nameShort`；B5 的 slogan 必须用 `app.brand.slogan`。

## 2. 其余验收项（captain 2026-11-05 追加后的完整口径）

1. **顺序**：`mvn -B clean test` → `mvn -B -DskipTests package` → `systemctl --user restart vuln-audit-backend`（顺序不可颠倒，`clean` 会删掉 systemd 正在使用的 jar）。
2. **迁移双场景**：**V20 / V21 / V22** 在「空库」与「已有数据」两种情形均可执行；生产 Flyway 关闭 → 校验 `deploy/manual-ddl.sql` 有等价语句（含 DEFAULT 与幂等守卫）。
3. **冒烟**：注册（手机必填/邮箱可选、双验证码）、审批（管理员通过/驳回、待审批登录拦截）、找回密码（验证码 → 重置 → 旧 token 失效 → 审计）、SSO（4 家 provider、管理员开关、待审批关联、绑定/解绑）、限流（验证码/登录）；另加 A1–A3、A8–A10 的接口级用例。
   - **旧用例口径修正**：t98 的 `pref/*` 用例断言「PUT 200 + `/auth/me` 回显」仍然有效（API 语义未变），但**不得再声称「PUT 偏好后登录页主题随之改变」**——登录页只认本地标记与管理员缺省；`/auth/me` 的 `loginTheme` 仅表示账号偏好（第三态下为 `null`）。
4. **页面截图**：三尺寸（1440 / 768 / 375）× 8 套登录主题；**另加**徽标 16px / 40px 可辨识性截图。
5. **数据清理**：按 README §23.1 清理冒烟数据，`orphan=0`，终态 `customers=1 projects=1 assets=3 links=3`。
6. **文档**：报告新增章节（登录页重构 / 注册审批 / 找回密码 / SSO / 更名 / **主题解析新规则与已知限制：跨设备不自动生效**）+ README 发布记录（含 V22 DDL）+ smoke-result。
7. **提交**：按模块 commit，`git status` 干净。

### 2.1 稳定 testid / 状态位清单（t110 已交付，断言只用这些，不依赖文案）

| testid | 元素 | 状态位 / 说明 | 承载断言 |
|---|---|---|---|
| `pref-current-theme` | Tag | `data-theme` = 生效主题；`data-source` = `local` \| `adminDefault` | A5、T1 |
| `pref-sync-from-account` | Button「从账号同步到本机」 | — | A5、A6 |
| `pref-follow-default` | Button「跟随系统默认」 | `data-active=true` = 当前无本机标记；**无标记时该按钮 `disabled`** | A5、A7 |
| `pref-mark-state` | 元素 | `data-has-mark` = `true`/`false`（本机标记存在性，与值无关） | A6/A7 的存在性断言 |
| `theme-card-<id>` | 主题卡片 | `data-active` | A5 |
| `pref-group-character` / `pref-group-abstract` | 分组 | — | 卡片分组 |
| 登录页 `login-character` | 主题槽位 | `data-theme`、`data-group` | T1–T3、B 系列 |
| 管理页 `admin-login-theme-card` | 管理卡片容器 | — | A2/A3、A6b/A7b |
| `admin-theme-current` | 当前缺省展示 | `data-theme` | A1/A3 |
| `admin-theme-card-<id>` | 可选主题卡片 | `data-active`、`data-current-default` | A6b/A7b |
| `admin-theme-save` | 保存按钮 | — | A2/A3 |
| `admin-theme-updated-by` / `admin-theme-updated-at` / `admin-theme-never-updated` | 审计回显 | 从未修改时显示 `never-updated` | 管理页信息 |

> **要点**：`data-has-mark` 与 `pref-current-theme[data-source]` 让「键存在且值=缺省」与「无标记」在**界面上可区分**，
> A6/A7 断言优先读这两个状态位，再读 `localStorage.getItem` 与 `localStorage.key()` 做交叉验证。


## 3. 验证脚本

`scripts/qa/verify-t107.cjs`（真实 Edge + 原生 CDP，零新增依赖）：
品牌类断言 B1–B6 + 主题解析三场景 T1–T3 + 接口门禁 A1–A4 + 登录页 8 主题 × 3 尺寸 + 注册/找回/SSO 页面级链路；
旧名门禁 B7 与「本地标记不被覆盖」的静态检查作为 CI 前置检查单独执行（不依赖浏览器）。

### 3.1 被本次修正作废的旧断言（重要）

`scripts/qa/verify-t98.cjs` 里有一条**与本次修正相反**的断言：

```
check('重新登录后缓存被服务端值纠正为 owl', relogin && afterLoginCache === 'owl', ...)
```

以及 `'再次进入登录页仍为 owl（服务端生效，跨设备）'`。这两条断言的语义是「登录响应/`/auth/me` 回写本地标记」，
**在新规则下属于缺陷**（会冲掉用户选择）。处理方式：

1. `verify-t98.cjs` 降级为**历史证据**（t98 当时的验收记录），**不再作为回归门禁**，头部加注「已被 t107 的 T1–T3/A4 取代」；
2. 新版期望写进 `verify-t107.cjs`：登录 / `/auth/me` 之后 `localStorage['login.theme']` **保持用户所选值**（A4：登录前后各读一次，值必须相同）；
3. 验收报告 §3.3「持久化时序」改写为：**只有三种时机写 / 清本地标记** —— ① 用户主动选主题（写）、② 点「从账号同步到本机」（写）、③ 选「跟随默认」（清除）；**登录与 `/auth/me` 一律不写**；
4. 报告 §3.3「已知限制」补一条：**跨设备不自动生效**（换设备/清缓存后按管理员缺省渲染，需用户点「从账号同步」），属设计选择，不是缺陷。

