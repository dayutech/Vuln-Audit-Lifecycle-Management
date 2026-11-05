# t120 验收契约（SSO 凭据 Web 配置 + 忘记密码双因子 + 徽标放大，增量部署与验收）

> QA 维护。t120 是**增量验收**，取代 t107 里被作废的两块断言（SSO「未配置不显示」、找回密码 target-only）。
> 执行前先确认门禁全绿；否则把 t120 置回 pending 并告知 captain。

## 0. 门禁

| 任务 | 内容 | 状态（2026-11-05 16:5x 快照） |
|---|---|---|
| t116 | 后端 SSO 凭据 Web 配置接口 + 公开列表「启用即展示」（`configured` 标记） | ✅ completed |
| t117 | 后端忘记密码改「用户名 + 手机/邮箱」双因子一致校验 | ✅ completed |
| t118 | 前端徽标放大 + 忘记密码补用户名（自动带入）+ SSO「启用即展示」按钮修正 | ⏳ in_progress |
| t119 | 前端系统设置 SSO 凭据 Web 配置界面（掩码 / 清除密钥 / 回调地址提示） | ✅ completed |
| t121 | 后端测试隔离修复（`USER_ROLES not found` 偶发 Error，须连续三次全绿） | ⏳ claimed |

**注意**：t121 修的正是 t107 报的 `AuditLogControllerTest#getUserAuditLogs_Success` 偶发 Error。t120 的全量回归必须**连续三次全绿**（失败即视为 t121 未达目的）。

## 1. 构建卫生（防止假错）

```bash
# 1) 先清过期构建缓存（否则 tsc 会报不存在的「i18n 缺键」/把别人在途改动算作回归失败）
rm -rf frontend/node_modules/.tmp/*.tsbuildinfo
# Windows:
#   Remove-Item -Recurse -Force frontend\node_modules\.tmp
# 2) 再构建
cd frontend && npm run build
```

判定规则：只以**清理后**的构建结果为准；若出现「词条缺键」类报错，先用 `grep` 复核键是否真的存在，再判断是否为缓存假错。

## 2. 提交卫生

`frontend/src/i18n/messages/domains/ssoMessages.ts` 同时含 t118（`sso.notConfigured*`、`ssoBinding.status.unconfigured`）与 t119 的词条。
**按任务 / 文件粒度分段暂存**，不要 `git add frontend/src` 整目录；后端同理（t116/t117/t121 各自文件）。

## 3. 新口径（取代 t107 旧断言）

### 3.1 SSO
- `GET /api/v1/auth/sso/providers` 返回**所有 enabled 项**，每项带 `configured`（boolean）。
- 登录页**按钮全部展示**；未配置的点击提示「尚未完成配置」（不是隐藏）。
- 管理端：系统设置 → SSO 凭据可 Web 配置（client_id / client_secret / authorize/token/userinfo/scopes），secret **掩码展示**，可「清除密钥」。

### 3.2 忘记密码
- `POST /api/v1/auth/password-reset/send-code` 与 `/confirm` 均必填 `username`，且需与该 `username` 的**手机号或邮箱一致**。
- 不一致 / 不存在 / 未验证：仍返回 **200 但不发码**（防枚举）；前端自动带入当前输入的用户名。

## 4. testid 清单（断言只用这些）

| 场景 | testid / 状态位 |
|---|---|
| 列表行 | `sso-row-<code>[data-configured][data-enabled]`、`admin-sso-meta-<code>`、`sso-row-warning-<code>`、`sso-updated-<code>`、`admin-sso-switch-<code>`、`sso-config-open-<code>` |
| 配置弹窗 | `sso-config-modal[data-provider]`、`sso-config-client-id`、`sso-config-client-secret`、`sso-config-clear-secret`、`sso-config-clear-hint`、`sso-config-authorize-url`（`-error` / `-local-hint`）、`sso-config-token-url`、`sso-config-userinfo-url`、`sso-config-scopes`、`sso-config-scopes-tags`、`sso-config-save`、`sso-config-cancel`、`sso-config-state` |
| 回调 | `sso-callback-url`、`sso-copy-callback` |

## 5. 真机验收必跑链路（需 t116 后端已部署）

1. 系统设置 → GitHub 行 → `sso-config-open-github` → 填 client_id/secret → `sso-config-save`；
2. 断言：行状态徽标「已配置」（`sso-row-github[data-configured=true]`）+ 公开列表 `configured=true`；
3. 重开弹窗：secret **为空**且 placeholder 为掩码（不回显明文）；
4. **只改 scopes 保存** → 断言密钥仍在（`data-configured` 仍 true，即「留空不丢密钥」）；
5. 勾「清除密钥」保存 → 变「未配置」（`data-configured=false`），但公开列表**仍展示**（因 `enabled=true`）；
6. 收尾：清空凭据 + `enabled=false`，恢复默认状态。

## 6. 其余验收项

1. **部署顺序**：`mvn -B clean test`（×3）→ `package` → `systemctl --user restart`；前端 `npm run build`（先清缓存）→ `sync-dist`。
2. **回归**：连续 **3 次** `mvn -B clean test` 全绿（Failures 0 / Errors 0），`@Disabled` 0。
3. **冒烟**：沿用现有 274 条 + 新增（SSO 凭据配置 / 启用即展示 / 找回双因子 / 徽标无回归），全绿 0 SKIP。
4. **页面**：徽标放大后 16/40px 可辨识性、8 主题 × 3 尺寸无回归；SSO 未配置按钮可见 + 点击提示；找回密码带 username 全链路（旧 401 / 新 200）。
5. **数据清理**：`registration_requests=0`、`verification_codes=0`、`sso_providers` 复原 `enabled=false` 且凭据清空、`customers=1 projects=1 assets=3 links=3`、`orphan=0`；dev-mode 不得留在 `backend.env`。
6. **文档**：报告 §3.4 更新为新口径（SSO 启用即展示 + configured、找回双因子）+ README 发布记录 + smoke-result；`git status` 干净（按任务粒度提交）。
