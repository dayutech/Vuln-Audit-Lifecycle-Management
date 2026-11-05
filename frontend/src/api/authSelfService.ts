import request from '@/utils/request';

/**
 * 自助注册 / 忘记密码 / SSO 提供商（t105 前端接入层）。
 *
 * 契约来自后端 t102（注册）、t103（忘记密码）、t104（SSO），**全部为公开接口**：
 * - `POST /auth/register/send-code  {channel: 'sms'|'email', target, purpose: 'REGISTER'}`
 *   → `{channel, target, purpose, expiresInSeconds, devCode?}`；
 * - `POST /auth/register  {username, password, phone, code, email?, emailCode?}` → **201** + `RegistrationRequestVO`
 *   （只落 PENDING 申请，不产生可登录账号）；
 * - `POST /auth/password-reset/send-code  {channel, target}` → 同上结构（purpose=RESET_PASSWORD，target 不存在也 200）；
 * - `POST /auth/password-reset/confirm  {target, code, newPassword}` → 200；
 * - `GET  /auth/sso/providers` → `[{code, name, icon}]`（仅返回已开启且已配置凭据的提供商）。
 *
 * 统一 `silent: true`：这些表单要**把错误定位到具体字段/行内提示**，
 * 不能让全局拦截器先弹一条 toast（会与行内提示重复）。
 * 401 仍由拦截器处理（公开接口正常不会触发）。
 */

export type VerificationChannel = 'sms' | 'email';

export interface SendCodeResult {
  channel: string;
  target: string;
  purpose: string;
  expiresInSeconds: number;
  /** 仅 dev-mode（`app.security.verification.dev-mode=true`）下出现 */
  devCode?: string;
}

export interface RegistrationResult {
  id?: string;
  username?: string;
  phone?: string;
  email?: string;
  status?: string;
  createdAt?: string;
  source?: string;
}

export interface RegisterPayload {
  username: string;
  password: string;
  phone: string;
  code: string;
  email?: string;
  emailCode?: string;
}

export interface PasswordResetConfirmPayload {
  target: string;
  code: string;
  newPassword: string;
}

const unwrap = (res: unknown): any => (res as any)?.data ?? res;

export const sendRegisterCode = async (params: {
  channel: VerificationChannel;
  target: string;
}): Promise<SendCodeResult> => {
  const res = await request.post(
    '/auth/register/send-code',
    { channel: params.channel, target: params.target, purpose: 'REGISTER' },
    { silent: true },
  );
  return (unwrap(res) ?? {}) as SendCodeResult;
};

export const registerAccount = async (payload: RegisterPayload): Promise<RegistrationResult> => {
  const res = await request.post('/auth/register', payload, { silent: true });
  return (unwrap(res) ?? {}) as RegistrationResult;
};

export const sendPasswordResetCode = async (params: {
  channel: VerificationChannel;
  target: string;
}): Promise<SendCodeResult> => {
  const res = await request.post(
    '/auth/password-reset/send-code',
    { channel: params.channel, target: params.target },
    { silent: true },
  );
  return (unwrap(res) ?? {}) as SendCodeResult;
};

export const confirmPasswordReset = async (payload: PasswordResetConfirmPayload): Promise<void> => {
  await request.post('/auth/password-reset/confirm', payload, { silent: true });
};
