import request from '@/utils/request';
import { isLoginThemeId, type LoginThemeId } from '@/components/login/types';
import { translate } from '@/i18n';

/**
 * 用户偏好：登录页主题（t96 建立 / t112 后端补第三态 / t113 前端接入）。
 *
 * 后端契约（`UserController`，t112 起为**三态**）：
 * - `GET  /api/v1/users/me/preferences` → `{ loginTheme: string|null, followDefault: boolean }`
 *   - `loginTheme = null` ⇔ `followDefault = true`（跟随系统默认）
 * - `PUT  /api/v1/users/me/preferences` body **二选一**：
 *   - `{ loginTheme: 'owl' }` → `{ loginTheme: 'owl', followDefault: false }`
 *   - `{ followDefault: true }` → `{ loginTheme: null, followDefault: true }`
 *   - 同传 / 都不传 → 400（`validation.user.login_theme.conflict|missing`，中英由 MessageSource 决定）
 * - 路径固定 `/me`，用户ID 由后端从 token 解析，**前端无法也不能指定他人**；写审计 `UPDATE_LOGIN_THEME`
 * - 模拟视角（t90 守卫）下 PUT `/users/**` 会被 403 拦截：读可以、写不行
 *
 * 这里统一 `silent: true`：失败提示由偏好弹窗按状态码分级给出
 * （400 / 403 / 401 / 5xx / 网络），避免与全局拦截器重复弹两条。
 */

export interface UserPreferences {
  /** 用户主动指定的主题；`null` 表示「跟随系统默认」（第三态） */
  loginTheme: LoginThemeId | null;
  /** 是否处于「跟随系统默认」；与 `loginTheme === null` 等价，后端显式给出以便前端判空 */
  followDefault: boolean;
}

/** 后端响应可能是 `ApiResponse` 包装或裸对象，两种都兼容 */
const unwrap = (res: unknown): any => (res as any)?.data ?? res;

/**
 * 解析偏好响应（三态）。
 *
 * - `loginTheme` 只有落在 `LOGIN_THEME_IDS` 才被接受（脏值 → null，不抛错）；
 * - `followDefault` 优先后端字段；旧后端没有该字段时按 `loginTheme === null` 推导，
 *   再退一步：老后端（t96 时代）返回具体值且没有 followDefault → false。
 */
export const extractPreferences = (res: unknown): UserPreferences => {
  const payload = unwrap(res);
  const rawTheme = payload?.loginTheme ?? payload?.login_theme ?? null;
  const loginTheme = isLoginThemeId(rawTheme) ? rawTheme : null;
  /**
   * 二者互斥：有具体主题 → 不是跟随默认；`null`（含脏值/缺失）→ 跟随默认。
   * 不再读后端 `followDefault` 布尔值做联合判断，避免「字段有值但互相矛盾」时前端状态撕裂。
   */
  return { loginTheme, followDefault: loginTheme === null };
};

/** 兼容旧调用方：只取主题（可能为 null） */
export const extractLoginTheme = (res: unknown): LoginThemeId | null => extractPreferences(res).loginTheme;

/** 读取自己的偏好（登录即可） */
export const getMyPreferences = async (): Promise<UserPreferences> => {
  const res = await request.get('/users/me/preferences', { silent: true });
  return extractPreferences(res);
};

/**
 * 指定一个具体主题（写账号偏好；**不**动本机标记——本机标记由弹窗在成功后再写）。
 *
 * @returns 后端最终落库值（正常情况下 `followDefault=false`）
 */
export const updateMyPreferences = async (loginTheme: LoginThemeId): Promise<UserPreferences> => {
  const res = await request.put('/users/me/preferences', { loginTheme }, { silent: true });
  const parsed = extractPreferences(res);
  return parsed.loginTheme ? parsed : { loginTheme, followDefault: false };
};

/**
 * 切换为「跟随系统默认」（后端第三态：把 `users.login_theme` 置 NULL）。
 *
 * 与 t110 的临时方案的关键差异：**不再把当前管理员缺省写成账号偏好**，
 * 因此管理员之后改缺省时，这些用户不会滞留旧值。
 */
export const updateMyFollowDefault = async (): Promise<UserPreferences> => {
  const res = await request.put('/users/me/preferences', { followDefault: true }, { silent: true });
  const parsed = extractPreferences(res);
  return { loginTheme: parsed.loginTheme, followDefault: parsed.loginTheme === null ? true : parsed.followDefault };
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

/** 把保存失败的异常翻译成「可读提示」（400/401/403/5xx/网络 分级） */
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
