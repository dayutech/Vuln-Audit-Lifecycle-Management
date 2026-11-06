import request from '@/utils/request';

/**
 * 我的双因子认证（t137，后端 t125/t135）。
 *
 * 后端契约（`UserTwoFactorController`，`/api/v1/users/me/2fa`）：
 * - `GET    /` → `{enabled, confirmedAt, recoveryCodesLeft, forced, available}`；**不返回 secret**
 * - `POST   /init` → `{secretBase32, otpauthUri}`（只生成待确认密钥，**尚未开启**）
 * - `POST   /confirm {code}` → `string[]`（**10 个恢复码，仅此一次**），此后 `enabled=true`
 * - `POST   /disable {password, code}` → 需密码 + 当前 TOTP 或恢复码；**管理员强制开启时 403**
 * - 登录两阶段：`POST /auth/login` → 202 `{twoFactorRequired, challengeToken}`；`POST /auth/login/2fa {challengeToken, code, rememberMe}`
 * - 强制开关：`PUT /admin/settings/force-two-factor {enabled}`（ADMIN）；未开启 2FA 的用户访问业务接口
 *   返回 **403 `code=TWO_FACTOR_SETUP_REQUIRED`**
 *
 * 全部 `silent: true`：提示由调用方按状态码分级，避免和全局拦截器重复弹。
 */

export interface TwoFactorStatus {
  enabled: boolean;
  /** ISO 时间（后端 LocalDateTime 序列化，可能带微秒） */
  confirmedAt?: string | null;
  recoveryCodesLeft: number;
  /** 管理员是否强制全体开启（开启时用户不能关闭自己的 2FA） */
  forced: boolean;
  /** 服务端 2FA 是否可用（`app.security.two-factor.enabled` 等开关） */
  available: boolean;
}

export interface TwoFactorInitResult {
  secretBase32: string;
  otpauthUri: string;
}

const unwrap = (res: unknown): any => (res as any)?.data ?? res;

/** 403 响应里表示「必须先去设置 2FA」的错误码（后端 `TwoFactorEnforcementInterceptor.ERROR_CODE`） */
export const TWO_FACTOR_SETUP_REQUIRED = 'TWO_FACTOR_SETUP_REQUIRED';

/** 侧边栏/页面监听的全局事件：任何请求被 2FA 强制拦截时派发 */
export const TWO_FACTOR_REQUIRED_EVENT = 'twoFactor:setup-required';

/** 从错误响应里读业务错误码（后端顶层 `code` 字段） */
export const readApiErrorCode = (error: unknown): string | undefined => {
  const data = (error as any)?.response?.data;
  return typeof data?.code === 'string' ? data.code : undefined;
};

/** 是否为「强制 2FA 未设置」拦截 */
export const isTwoFactorSetupRequiredError = (error: unknown): boolean =>
  readApiErrorCode(error) === TWO_FACTOR_SETUP_REQUIRED;

export const getTwoFactorStatus = async (): Promise<TwoFactorStatus> => {
  const res = await request.get('/users/me/2fa', { silent: true });
  const payload = unwrap(res);
  return {
    enabled: Boolean(payload?.enabled),
    confirmedAt: payload?.confirmedAt ?? null,
    recoveryCodesLeft: Number(payload?.recoveryCodesLeft ?? 0) || 0,
    forced: Boolean(payload?.forced),
    available: payload?.available === undefined ? true : Boolean(payload.available),
  };
};

/** 生成待确认密钥（可重复调用：每次都会覆盖未确认的旧密钥） */
export const initTwoFactor = async (): Promise<TwoFactorInitResult> => {
  const res = await request.post('/users/me/2fa/init', null, { silent: true });
  const payload = unwrap(res) ?? {};
  return { secretBase32: String(payload.secretBase32 ?? ''), otpauthUri: String(payload.otpauthUri ?? '') };
};

/** 输入动态码确认开启；返回恢复码（**只在这一次返回**，务必让用户保存） */
export const confirmTwoFactor = async (code: string): Promise<string[]> => {
  const res = await request.post('/users/me/2fa/confirm', { code: code.trim() }, { silent: true });
  const payload = unwrap(res);
  return Array.isArray(payload) ? payload.map((item) => String(item)) : [];
};

/** 关闭 2FA：密码 + 动态码/恢复码（管理员强制开启时后端 403） */
export const disableTwoFactor = async (password: string, code: string): Promise<void> => {
  await request.post('/users/me/2fa/disable', { password, code: code.trim() }, { silent: true });
};

/** 把 Base32 密钥按 4 位分组展示（便于手输；`otpauthUri` 里的 secret 也是同一串） */
export const formatBase32Secret = (secret: string): string => {
  const clean = (secret ?? '').replace(/\s+/g, '').toUpperCase();
  return clean.match(/.{1,4}/g)?.join(' ') ?? '';
};

/** 恢复码 txt 内容（下载用）：一行一个 + 简短说明 */
export const buildRecoveryCodesText = (codes: string[], username?: string): string => {
  const header = [
    'Vulnerability Audit Lifecycle Management — two-factor recovery codes',
    username ? `Account: ${username}` : '',
    `Generated at: ${new Date().toISOString()}`,
    'Each code can be used once. Keep them offline.',
    '',
  ]
    .filter(Boolean)
    .join('\n');
  return `${header}${codes.join('\n')}\n`;
};

/** 触发浏览器下载（纯前端，不发网络请求） */
export const downloadRecoveryCodes = (codes: string[], filename = 'two-factor-recovery-codes.txt', username?: string): void => {
  const blob = new Blob([buildRecoveryCodesText(codes, username)], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};
