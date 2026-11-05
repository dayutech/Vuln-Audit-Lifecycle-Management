import request from '@/utils/request';

/**
 * 自助注册 / 忘记密码 / SSO 提供商（t105 前端接入层）。
 *
 * 契约来自后端 t102（注册）、t103（忘记密码）、t104（SSO），**全部为公开接口**：
 * - `POST /auth/register/send-code  {channel: 'sms'|'email', target, purpose: 'REGISTER'}`
 *   → `{channel, target, purpose, expiresInSeconds, devCode?}`；
 * - `POST /auth/register  {username, password, phone, code, email?, emailCode?}` → **201** + `RegistrationRequestVO`
 *   （只落 PENDING 申请，不产生可登录账号）；
 * - `POST /auth/password-reset/send-code  {username, channel, target}` → 同上结构（purpose=RESET_PASSWORD；
 *   t117 起 `username` 必填，「用户名 ↔ 手机/邮箱」不匹配时仍 200 但**不发码**）；
 * - `POST /auth/password-reset/confirm  {username, target, code, newPassword}` → 200（身份不一致 400）；
 * - `GET  /auth/sso/providers` → `[{code, name, icon, configured}]`（t116 起「启用即返回」，
 *   `configured=false` 表示凭据没配齐，登录页仍展示按钮但不可真正跳转）。
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
  /** t117：用户名 + 手机/邮箱 双因子，缺一会 400 */
  username: string;
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

/**
 * 忘记密码发码（t117 契约：`{username, channel, target}`）。
 *
 * 「用户名 ↔ 手机/邮箱」不匹配时后端仍返回 **200 但不发码**（防枚举），
 * 所以调用方**不能**根据响应判断匹配性，只能统一提示「如果信息匹配，验证码已发送」。
 */
export const sendPasswordResetCode = async (params: {
  username: string;
  channel: VerificationChannel;
  target: string;
}): Promise<SendCodeResult> => {
  const res = await request.post(
    '/auth/password-reset/send-code',
    { username: params.username, channel: params.channel, target: params.target },
    { silent: true },
  );
  return (unwrap(res) ?? {}) as SendCodeResult;
};

export const confirmPasswordReset = async (payload: PasswordResetConfirmPayload): Promise<void> => {
  await request.post('/auth/password-reset/confirm', payload, { silent: true });
};

