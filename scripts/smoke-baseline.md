# 冒烟脚本基线（scripts/smoke.sh）

> 用途：t13「部署验证」的执行载体，也是 t36 的交付物之一。
> 本文件记录**部署前（旧 jar）**的基线结果；t13 部署完成后请用同一脚本重跑，并与本基线逐行对照。

## 1. 运行方式

```bash
# 在 Linux 主机上（推荐；BASE_URL 走本机 nginx）
bash scripts/smoke.sh

# 从 Windows 主机经 plink 远程执行
plink -ssh -batch -pw '<密码>' user@192.168.188.200 \
  'cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh'

# 直连后端（无前端静态页时，页面类用例会 SKIP）
BASE_URL=http://192.168.188.200:8089 bash scripts/smoke.sh 2>&1 | tee smoke-$(date +%F-%H%M).log
```

可配置项（全部有默认值，脚本内无硬编码环境依赖，除默认口令）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `BASE_URL` | `http://127.0.0.1:8089` | 对外入口（nginx：静态页 + `/api` 反代） |
| `ADMIN_USER` / `ADMIN_PASS` | `admin` / `admin` | 管理员 |
| `ENGINEER_USER` / `ENGINEER_PASS` | `engineer` / `engineer` | 工程师 |
| `LEADER_USER` / `LEADER_PASS` | `leader` / `leader` | 组长 |
| `SMOKE_PROVISION` | `1` | 工程师/组长登录不上时，用 admin 自动建 `smoke_engineer` / `smoke_leader` 并绑角色 |
| `SMOKE_PASSWORD` | `Smoke@12345` | 自动建号口令 |
| `CURL_TIMEOUT` | `25` | 单请求超时（秒） |

依赖：`bash` + `curl` + coreutils（`grep/sed/od/head`/`tr`），**不需要 jq**。

## 2. 输出与判定口径

- 每行一个用例：`PASS|FAIL|SKIP  <用例名>  <HTTP状态>  <补充说明>`
- 末尾汇总 `TOTAL / PASS / FAIL / SKIP`，并列出失败用例名
- 退出码：`FAIL>0` → 1，否则 0（`SKIP` 不影响退出码）
- 判定：
  - `404 / 405 / 501` → **SKIP**（功能未上线，部署前不算失败）
  - `5xx`、状态码不符、状态码对但内容断言不过 → **FAIL**
  - 前置数据缺失（如没有通知、环境创建失败导致部署用例无法执行）→ **SKIP** 并写明原因
- 脚本自建数据（资产/标签/分组/漏洞/附件/任务/工具/环境/公告/审批范围）在退出时通过 `trap` 尽力清理；
  `smoke_engineer` / `smoke_leader` 两个夹具账号**不删除**（本系统 `DELETE /users` 为软删除，
  用户名占用且账号 deactivated，下次反而无法重建），`SMOKE_PROVISION=0` 可完全禁用自动建号。

## 3. 基线结果（部署前，旧 jar）

- 执行时间：2026-10-29 21:46（`BASE_URL=http://192.168.188.200:8089`）
- 目标环境：部署在 192.168.188.200:8089 的**旧 jar**（nginx 8089 → 后端 8088 → MariaDB 3306）
- **TOTAL 92 / PASS 65 / FAIL 21 / SKIP 6**，退出码 1

### 3.1 21 个 FAIL 的归因（全部为「新能力尚未部署」或「旧 jar 已知缺陷」）

| 组 | 用例 | 旧 jar 表现 | 归因 |
|---|---|---|---|
| 导入（5） | `import/模板-assets`、`import/模板-vulnerabilities`、`import/模板-users`、`import/资产部分成功`、`import/用户导入-工程师403` | 500 `服务器内部错误` | 旧 jar 没有 `/api/v1/import/*`（未实现被全局异常处理器兜成 500） |
| RBAC（2） | `rbac/权限点-90`、`rbac/admin权限数-90` | 200 但权限点 0 个 / 缺 `permissionCount` | 权限点初始化（RbacBootstrap/PermissionCatalog）未部署 |
| 仪表盘（4） | `dash/趋势-7点`、`dash/排行-admin`、`dash/排行-工程师1行`、`dash/导出-工程师403` | 趋势返回空数组、排行 500、工程师导出 **200** | 趋势/排行服务端实现与导出鉴权未部署 |
| 审计（3） | `audit/关键词筛选`、`audit/时间筛选`、`audit/导出CSV` | 筛选参数被忽略（结果不变）、导出 400 | 旧 jar 无 keyword/startTime 过滤、`/export` 被 `/audit-logs/{id}` 路由吃掉 |
| 漏洞（2） | `vuln/附件-下载`、`vuln/编辑-仅改标题` | 500 | 附件下载端点（t15）与「编辑不清空其它字段」（t34）未部署 |
| 工具（2） | `tool/版本上传`、`tool/版本列表` | 500 / 空数组 | 工具版本文件与 `versionNo`（t23/t33）未部署 |
| 环境（1） | `env/创建` | 500 | 旧 jar `environments.created_by` NOT NULL 未兜底（t5 已修，未部署） |
| 资产分组（2） | `asset/分组-移除资产`、`asset/分组-删除` | 500 | 旧 jar 已知缺陷（源码已修，未部署） |

### 3.2 6 个 SKIP 的原因

| 用例 | 原因 |
|---|---|
| `tool/版本下载` | 版本上传失败 → 无版本 ID，无法下载 |
| `notify/标记已读`、`notify/发送记录(recipients)` | 该环境没有任何通知数据（admin 与工程师列表均为空），无 ID 可用 |
| `env/部署-无Docker应503`、`env/详情(含部署器信息)` | 环境创建失败（上表）→ 无环境 ID |
| `vuln/编辑-其余字段不变` | 编辑请求本身 500 → 跳过字段比对（避免一个根因记两次失败） |

## 4. 部署后（t13）期望

- 全部 21 个 FAIL → PASS，`SKIP` 中的 `notify/*` 在真实产生通知后也应转为 PASS；
- 需要人工确认的项（脚本无法判定）：`smoke_engineer/smoke_leader` 角色是否正确、重启幂等、
  `notifications.type` 手工 DDL 后 `SHOW COLUMNS` 为 `varchar(50)/NO`；
- 环境部署用例要求**目标主机没有可用 Docker** 才应返回 503；若宿主已装 Docker，该项会变成 2xx，
  此时应在报告里注明「宿主已启用容器编排，503 语义不适用」。

## 5. 与「部署后必测清单」的对应关系

| 必测项 | 脚本覆盖 | 用例名 |
|---|---|---|
| 创建漏洞 camelCase → 201 | ✅ | `vuln/创建-201` |
| 漏洞编辑只改标题，其余字段不变 | ✅（新增） | `vuln/编辑-仅改标题` + `vuln/编辑-其余字段不变` |
| 工具版本上传 + 下载字节一致 | ✅ | `tool/版本上传`、`tool/版本列表`、`tool/版本下载` |
| 工具版本 `hasFile=true` / `versionNo` 递增 / 并发 409 | ⚠️ 部分 | 字节一致已覆盖；`versionNo` 递增与并发 409 建议 t13 人工复核 |
| 文档编辑/回滚快照 | ❌ 脚本未覆盖 | 建议 t13 按 a~k 清单人工执行（文档域上下文较多，未纳入冒烟） |
| 排行 admin 多行 / 工程师 1 行；导出对工程师 403 | ✅ | `dash/排行-admin`、`dash/排行-工程师1行`、`dash/导出-工程师403` |
| 导入部分成功语义（2 成功 1 失败） | ✅ | `import/资产部分成功` |
| 用户导入仅 ADMIN | ✅ | `import/用户导入-工程师403` |
| 环境无 Docker 时 503 + 文案 | ✅ | `env/部署-无Docker应503` |
| 审计筛选/导出/工程师 403 | ✅ | `audit/关键词筛选`、`audit/时间筛选`、`audit/导出CSV`、`audit/工程师403` |
| 页面路由 200 + text/html | ✅ | `page/*`（11 条） |
