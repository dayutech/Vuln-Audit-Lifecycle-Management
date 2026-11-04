import request from '@/utils/request';
import { isLoginThemeId, type LoginThemeId } from '@/components/login/types';
import { translate } from '@/i18n';

/**
 * 用户偏好：登录页主题（t97，接口来自 t96）。
 *
 * 后端契约（`UserController`）：
 * - `GET  /api/v1/users/me/preferences` → `{ loginTheme }`，登录即可读；
 * - `PUT  /api/v1/users/me/preferences` body `{ loginTheme }` → `{ loginTheme }`
 *   - 白名单校验，非法值 400（中英由 t60 MessageSource 决定）
 *   - 路径固定 `/me`，用户ID 由后端从 token 解析，**前端无法也不能指定他人**
 *   - 写审计 `UPDATE_LOGIN_THEME`
 * - 模拟视角（t90 守卫）下 PUT `/users/**` 会被 403 拦截：读可以、写不行。
 *
 * 这里统一 `silent: true`：失败提示由偏好弹窗按状态码分级给出
 * （400=服务端未启用 / 403=无权限 / 401=登录过期 / 5xx=服务端错误 / 网络），
 * 避免与全局拦截器重复弹两条。
 */

export interface UserPreferences {
  loginTheme: LoginThemeId;
}

/** 后端响应可能是 `ApiResponse` 包装或裸对象，两种都兼容 */
const unwrap = (res: unknown): any => (res as any)?.data ?? res;

/**
 * 解析偏好响应里的主题。
 *
 * 后端读取时对脏值已回落 pencil（t96），这里再兜一层：
 * 只有落在 `LOGIN_THEME_IDS` 里的值才被接受，否则返回 null 由调用方决定回落。
 */
export const extractLoginTheme = (res: unknown): LoginThemeId | null => {
  const payload = unwrap(res);
  const value = payload?.loginTheme ?? payload?.login_theme;
  return isLoginThemeId(value) ? value : null;
};

/** 读取自己的偏好（登录即可） */
export const getMyPreferences = async (): Promise<UserPreferences> => {
  const res = await request.get('/users/me/preferences', { silent: true });
  return { loginTheme: extractLoginTheme(res) ?? 'pencil' };
};

/** 更新自己的登录页主题（返回后端最终落库值） */
export const updateMyPreferences = async (loginTheme: LoginThemeId): Promise<UserPreferences> => {
  const res = await request.put('/users/me/preferences', { loginTheme }, { silent: true });
  return { loginTheme: extractLoginTheme(res) ?? loginTheme };
};

/** 偏好保存失败的类别（与 i18n `preferences.loginTheme.err*` 一一对应） */
export type PreferenceFailureKind = 'badRequest' | 'unauthorized' | 'forbidden' | 'server' | 'network' | 'generic';

export interface PreferenceFailure {
  kind: PreferenceFailureKind;
  status?: number;
  /** 后端 message（t60 起按 Accept-Language 本地化），没有则为空串 */
  backendMessage: string;
  /** 已本地化的可读提示 */
  text: string;
}

/**
 * 把保存失败的异常翻译成「可读提示」。
 *
 * 400 单独成一类：t99 的四套抽象主题已进入前端注册表，但服务端白名单要等
 * t100 扩展到 8 项 —— 这段过渡期里用户点了抽象主题会拿到 400，
 * 必须给「服务端未启用该主题」这种明确解释，而不是笼统的"参数错误"。
 */
export const describePreferenceFailure = (error: unknown): PreferenceFailure => {
  const response = (error as any)?.response;
  const status: number | undefined = response?.status;
  const backendMessage: string = response?.data?.message ?? response?.data?.error?.message ?? '';

  if (!response) {
    return { kind: 'network', backendMessage, text: translate('preferences.loginTheme.errNetwork') };
  }
  if (status === 400) {
    return {
      kind: 'badRequest',
      status,
      backendMessage,
      text: translate('preferences.loginTheme.err400', { message: backendMessage || '400' }),
    };
  }
  if (status === 401) {
    return { kind: 'unauthorized', status, backendMessage, text: translate('preferences.loginTheme.err401') };
  }
  if (status === 403) {
    return { kind: 'forbidden', status, backendMessage, text: translate('preferences.loginTheme.err403') };
  }
  if (typeof status === 'number' && status >= 500) {
    return {
      kind: 'server',
      status,
      backendMessage,
      text: translate('preferences.loginTheme.err5xx', { status }),
    };
  }
  return {
    kind: 'generic',
    status,
    backendMessage,
    text: translate('preferences.loginTheme.errGeneric', { status: status ?? '?' }),
  };
};
