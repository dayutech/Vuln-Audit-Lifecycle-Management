import { DEFAULT_LOGIN_THEME, isLoginThemeId, type LoginThemeId } from '@/components/login/types';

/**
 * 登录页主题的本机缓存（t97）。
 *
 * 时序（这是本模块存在的唯一理由）：
 * 1. **认证前**登录页拿不到 `/users/me/preferences`（未登录 → 401），
 *    所以登录页只读 `localStorage('login.theme')`，缺省 `pencil`；
 * 2. **登录成功后**用登录响应里的 `loginTheme`（t96 已附带）回写缓存 ——
 *    **不强制刷新**，下次进入登录页生效，避免刚登录就整页 reload；
 * 3. **`GET /auth/me` 返回后**再同步一次（跨设备换过主题时本机缓存会过期）；
 * 4. 偏好弹窗里改主题成功后立即写缓存（不等下次登录）。
 *
 * 键名与读取入口都收敛在这里，页面/弹窗不直接碰 localStorage。
 */

export const LOGIN_THEME_STORAGE_KEY = 'login.theme';

/** 历史/别名写法 → 规范 id（i18n key 用驼峰 pixelEyes，主题 id 用连字符 pixel-eyes） */
const THEME_ALIASES: Record<string, LoginThemeId> = {
  pixeleyes: 'pixel-eyes',
  'pixel-eyes': 'pixel-eyes',
  pixel_eyes: 'pixel-eyes',
  monogrid: 'mono-grid',
  'mono-grid': 'mono-grid',
  mono_grid: 'mono-grid',
  bigtype: 'big-type',
  'big-type': 'big-type',
  big_type: 'big-type',
};

/**
 * 归一化任意来源的主题值（localStorage / 后端响应 / URL 参数）。
 *
 * - 去空格、小写、别名映射；
 * - 不认识的值返回 null（**不要**在这里回落默认值，调用方要能区分「脏值」与「pencil」）。
 */
export const normalizeLoginTheme = (raw?: string | null): LoginThemeId | null => {
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim().toLowerCase();
  if (!trimmed) return null;
  if (isLoginThemeId(trimmed)) return trimmed;
  return THEME_ALIASES[trimmed] ?? null;
};

const safeGet = (): string | null => {
  try {
    return window.localStorage.getItem(LOGIN_THEME_STORAGE_KEY);
  } catch {
    // 隐私模式 / 禁用 cookie 的浏览器：当作没有缓存
    return null;
  }
};

const safeSet = (value: string) => {
  try {
    window.localStorage.setItem(LOGIN_THEME_STORAGE_KEY, value);
  } catch {
    /* 隐私模式：仅本次会话生效 */
  }
};

/**
 * 登录页读主题：`localStorage('login.theme')`，缺省 `pencil`。
 *
 * 脏值（历史写法、手工改坏）按缺省处理，不抛错、不把非法值传给主题组件。
 */
export const readLoginThemeCache = (): LoginThemeId => normalizeLoginTheme(safeGet()) ?? DEFAULT_LOGIN_THEME;

/**
 * 写本机缓存。只接受白名单内的主题 id（非法值静默忽略，绝不落盘）。
 *
 * @returns 实际写入缓存的主题（非法值时为当前缓存值）
 */
export const writeLoginThemeCache = (theme: LoginThemeId | string | null | undefined): LoginThemeId => {
  const normalized = normalizeLoginTheme(typeof theme === 'string' ? theme : null);
  if (!normalized) return readLoginThemeCache();
  safeSet(normalized);
  return normalized;
};

export interface LoginThemeSyncResult {
  /** 同步后缓存里的主题（也是登录页下次会用的主题） */
  theme: LoginThemeId;
  /** 本次是否真的改写了缓存 */
  changed: boolean;
  /** 服务端给的值是否可用（脏值/缺失 → false，此时缓存保持不变） */
  accepted: boolean;
}

/**
 * 用服务端返回值同步本机缓存（登录响应 / `/auth/me` / 偏好接口都走这里）。
 *
 * 服务端没给值（旧后端 / 老记录返回 null）或值不认识时**不动缓存** ——
 * 宁可保留本机上一次的主题，也不要被空值刷回 pencil。
 */
export const syncLoginThemeFromServer = (serverValue?: string | null): LoginThemeSyncResult => {
  const normalized = normalizeLoginTheme(serverValue);
  if (!normalized) {
    return { theme: readLoginThemeCache(), changed: false, accepted: false };
  }
  /**
   * 比较的是**原始缓存字符串**而不是 `readLoginThemeCache()` 的结果。
   *
   * 否则「缓存为空 + 服务端值恰好是默认 pencil」会被判成"没变化"而不落盘 ——
   * 缓存键就一直不存在；一旦之后服务端改了偏好，本机仍会先渲染 pencil 再被纠正，
   * 且排查时无法区分"没写过"与"写过 pencil"。这里要求键必须被**显式物化**。
   */
  const raw = safeGet();
  if (raw === normalized) {
    return { theme: normalized, changed: false, accepted: true };
  }
  safeSet(normalized);
  return { theme: normalized, changed: true, accepted: true };
};
