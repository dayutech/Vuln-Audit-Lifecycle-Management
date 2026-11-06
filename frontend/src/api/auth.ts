import request from '@/utils/request';
import { LOGIN_THEMES } from '@/components/login/themes';
import { normalizeLoginTheme } from '@/utils/loginTheme';
import type { LoginThemeId } from '@/components/login/types';

/**
 * 登录（t127：改为 `silent`）。
 *
 * 登录页要把失败**全部走行内提示**，并且要区分三种「不是密码错」的情况：
 * - 401 密码错误（行内「用户名或密码错误」）；
 * - 429 IP 层限流（行内倒计时，读 `Retry-After`）—— 不能弹全局 toast；
 * - 423 账户锁定（行内锁定说明 + 「去解锁」）—— 不能弹全局 toast。
 *
 * 因此这里关掉拦截器的全局提示，由页面按状态码自行分级。
 */
export const login = (data: any): Promise<any> => {
  return request.post('/auth/login', data, { silent: true });
};

/**
 * 登录第二步（t137，后端 t125）：带挑战 token + 动态码/恢复码换正式 JWT。
 *
 * - `challengeToken` 由第一步 202 响应给出，**只存内存**（不写 localStorage）；
 * - `rememberMe` 必须在第二步再传一次：第一步的勾选对 2FA 用户不生效（后端如此设计）；
 * - 成功后后端会下发 remember-me 的 httpOnly cookie，前端不保存任何长效凭证。
 */
export const loginWithTwoFactor = (payload: {
  challengeToken: string;
  code: string;
  rememberMe?: boolean;
}): Promise<any> => {
  return request.post('/auth/login/2fa', payload, { silent: true });
};

export const logout = (): Promise<any> => {
  return request.post('/auth/logout');
};

export const getCurrentUser = (): Promise<any> => {
  return request.get('/auth/me');
};

/**
 * 登录页公开配置（t109 后端 / t110 前端接入）。
 *
 * `GET /auth/login-theme` —— **免认证**，登录页在认证前就要用：
 * 返回 `{ defaultTheme, themes: [{ code }] }`，缺省值由管理员在系统设置里维护，
 * 后端对「配置缺失 / 脏值」已回落 `pencil`。
 *
 * - `silent: true`：登录页不允许因为这个小请求弹错误提示；
 * - 失败/超时都由调用方静默回落（`pencil` 或会话缓存），**不得阻塞登录页渲染**；
 * - `themes` 只用于告知服务端支持哪些 code，前端渲染仍以本地注册表为准
 *   （注册表是缩略图 / 文案 / 皮肤的唯一来源，避免两处列表漂移）。
 */
export interface PublicLoginThemeConfig {
  defaultTheme: LoginThemeId;
  themes: LoginThemeId[];
}

const unwrap = (res: unknown): any => (res as any)?.data ?? res;

export const getPublicLoginThemeConfig = async (): Promise<PublicLoginThemeConfig> => {
  const res = await request.get('/auth/login-theme', { silent: true, timeout: 5000 });
  const payload = unwrap(res);
  const defaultTheme = normalizeLoginTheme(payload?.defaultTheme) ?? 'pencil';
  const rawThemes = Array.isArray(payload?.themes) ? payload.themes : [];
  const themes = rawThemes
    .map((item: any) => normalizeLoginTheme(typeof item === 'string' ? item : item?.code))
    .filter((code: LoginThemeId | null): code is LoginThemeId => Boolean(code));
  return { defaultTheme, themes: themes.length > 0 ? themes : LOGIN_THEMES.map((entry) => entry.id) };
};
