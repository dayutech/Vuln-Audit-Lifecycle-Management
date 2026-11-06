import request from '@/utils/request';

/**
 * 账户锁定后的 OTP 解锁（t127，后端 t123）。
 *
 * 契约：
 * - `POST /auth/unlock/send-code {username}`：公开。同 username 5 分钟 1 次、同 IP 每小时 10 次；
 *   向「已绑定且已验证」的手机/邮箱发码；**没有可用联系方式时**返回体会带
 *   `hint: 'NO_VERIFIED_CONTACT'`（前端据此隐藏发送按钮、改为提示联系管理员）；
 * - `POST /auth/unlock/confirm {username, target, code}`：`target` 必须是该账号自己的联系方式，
 *   成功 200（**不签发 token**，需要回登录页重新登录）；验证码错误/过期 → 400。
 *
 * 两个接口都走 `silent: true`：解锁页要用**行内**文案分级（400 定位到验证码字段、429 提示过于频繁），
 * 不能让全局拦截器先弹一条 toast。
 */

export interface UnlockSendCodeResult {
  /** `NO_VERIFIED_CONTACT` 表示该账号没有任何已验证的手机/邮箱 */
  hint?: string;
  /** 仅 dev-mode 下返回，便于联调自动填入 */
  devCode?: string;
  expiresInSeconds?: number;
  /** 可选：后端若回传可用渠道与脱敏 target，前端可直接展示 */
  unlockMethods?: string[];
  targets?: string[];
}

export interface UnlockConfirmPayload {
  username: string;
  target: string;
  code: string;
}

const unwrap = (res: unknown): any => (res as any)?.data ?? res;

export const NO_VERIFIED_CONTACT = 'NO_VERIFIED_CONTACT';

export const sendUnlockCode = async (username: string): Promise<UnlockSendCodeResult> => {
  const res = await request.post('/auth/unlock/send-code', { username }, { silent: true });
  const payload = unwrap(res) ?? {};
  return {
    hint: payload.hint ? String(payload.hint) : undefined,
    devCode: payload.devCode ? String(payload.devCode) : undefined,
    expiresInSeconds: typeof payload.expiresInSeconds === 'number' ? payload.expiresInSeconds : undefined,
    unlockMethods: Array.isArray(payload.unlockMethods) ? payload.unlockMethods.map(String) : undefined,
    targets: Array.isArray(payload.targets) ? payload.targets.map(String) : undefined,
  };
};

export const confirmUnlock = async (payload: UnlockConfirmPayload): Promise<void> => {
  await request.post('/auth/unlock/confirm', payload, { silent: true });
};

/** 把 423 响应体里的解锁信息解析出来（缺失字段一律兜底，页面不因脏数据崩） */
export interface LockInfo {
  unlockMethods: string[];
  targets: string[];
  retryAfterSeconds?: number;
  reason?: string;
  noVerifiedContact: boolean;
}

export const parseLockInfo = (data: unknown): LockInfo => {
  const payload = (data as any)?.data ?? data ?? {};
  const methods = Array.isArray(payload.unlockMethods)
    ? payload.unlockMethods.filter((m: unknown) => m === 'sms' || m === 'email').map(String)
    : [];
  const targets = Array.isArray(payload.targets) ? payload.targets.map((t: unknown) => String(t)) : [];
  return {
    unlockMethods: methods.length > 0 ? methods : ['sms', 'email'],
    targets,
    retryAfterSeconds: typeof payload.retryAfterSeconds === 'number' ? payload.retryAfterSeconds : undefined,
    reason: payload.reason ? String(payload.reason) : undefined,
    noVerifiedContact: payload.hint === NO_VERIFIED_CONTACT || (methods.length === 0 && targets.length === 0),
  };
};

/** `Retry-After` 既可能是秒数，也可能是 HTTP 日期；两种都支持 */
export const parseRetryAfter = (headerValue: unknown): number | undefined => {
  const raw = Array.isArray(headerValue) ? headerValue[0] : headerValue;
  if (raw === undefined || raw === null || raw === '') return undefined;
  const asNumber = Number(raw);
  if (Number.isFinite(asNumber) && asNumber > 0) return Math.ceil(asNumber);
  const asDate = Date.parse(String(raw));
  if (Number.isFinite(asDate)) {
    const seconds = Math.ceil((asDate - Date.now()) / 1000);
    return seconds > 0 ? seconds : undefined;
  }
  return undefined;
};
