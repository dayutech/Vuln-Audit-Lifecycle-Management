import { DEFAULT_LOGIN_THEME, isLoginThemeId, type LoginThemeId } from '@/components/login/types';

/**
 * 登录页主题的解析与本机标记（t110 重写，取代 t97 的「服务端偏好自动回写」模型）。
 *
 * ## 解析顺序（唯一事实来源，页面/弹窗都必须走 `resolveLoginTheme`）
 *
 * ```
 * ?theme=            ← 仅预览/深链，本次生效、**不落盘**、不参与后续判断
 *   > 本机标记        localStorage('login.theme')   ← 用户主动选择过才有
 *   > 管理员缺省      GET /auth/login-theme 的 defaultTheme（sessionStorage 缓存 5 分钟）
 *   > pencil
 * ```
 *
 * ## 本机标记只在三种时机被写/清
 *
 * 1. 用户在偏好设置里主动选主题 → **写**；
 * 2. 用户点「从账号同步到本机」→ **写**（把 `users.login_theme` 搬成本机标记）；
 * 3. 用户点「跟随系统默认」→ **清除**。
 *
 * **登录成功、`GET /auth/me` 一律不写本机标记**（v2 语义：跨设备不自动生效；
 * 需要跨设备时由用户在偏好设置里自己同步一次）。这也是为什么本模块**不再导出**
 * `syncLoginThemeFromServer` —— 那个函数的存在本身就是"自动回写"的入口。
 */

/** 本机标记（用户主动选择过的主题） */
export const LOGIN_THEME_STORAGE_KEY = 'login.theme';
/** 管理员缺省的会话缓存（sessionStorage：关掉标签页即失效，避免长期陈旧） */
export const DEFAULT_THEME_CACHE_KEY = 'login.defaultTheme';
export const DEFAULT_THEME_CACHE_AT_KEY = 'login.defaultTheme.at';
/** 管理员缺省缓存有效期：5 分钟（管理员改完缺省，最迟 5 分钟内在同标签页生效） */
export const DEFAULT_THEME_TTL_MS = 5 * 60 * 1000;

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
 * 归一化任意来源的主题值（localStorage / sessionStorage / 后端响应 / URL 参数）。
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

// ------------------------------------------------------------------ storage 安全封装

const safeLocalGet = (key: string): string | null => {
  try {
    return window.localStorage.getItem(key);
  } catch {
    // 隐私模式 / 禁用存储的浏览器：当作没有标记
    return null;
  }
};

const safeLocalSet = (key: string, value: string) => {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    /* 隐私模式：仅本次会话生效 */
  }
};

const safeLocalRemove = (key: string) => {
  try {
    window.localStorage.removeItem(key);
  } catch {
    /* ignore */
  }
};

const safeSessionGet = (key: string): string | null => {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
};

const safeSessionSet = (key: string, value: string) => {
  try {
    window.sessionStorage.setItem(key, value);
  } catch {
    /* ignore */
  }
};

// ------------------------------------------------------------------ 本机标记（localStorage）

/**
 * 读本机标记：**没有标记时返回 null**（而不是 pencil）。
 *
 * 这是 t110 的关键区分点：「没选过」必须能被识别出来，才能回落到管理员缺省；
 * 旧 API `readLoginThemeCache()` 把「没选过」和「选了 pencil」混成一个值，
 * 会让管理员缺省永远不生效。故此处返回 `LoginThemeId | null`。
 */
export const readLocalLoginThemeMark = (): LoginThemeId | null => normalizeLoginTheme(safeLocalGet(LOGIN_THEME_STORAGE_KEY));

/** 是否已有本机标记（等价于「用户主动选择过」） */
export const hasLocalLoginThemeMark = (): boolean => readLocalLoginThemeMark() !== null;

/**
 * 写本机标记。只接受白名单内的主题 id（非法值静默忽略，绝不落盘）。
 *
 * @returns 实际生效的本机标记（非法值为 null，即保持"未标记"状态）
 */
export const writeLocalLoginThemeMark = (theme: LoginThemeId | string | null | undefined): LoginThemeId | null => {
  const normalized = normalizeLoginTheme(typeof theme === 'string' ? theme : null);
  if (!normalized) return readLocalLoginThemeMark();
  safeLocalSet(LOGIN_THEME_STORAGE_KEY, normalized);
  return normalized;
};

/** 清除本机标记（「跟随系统默认」）。清除后登录页回到管理员缺省。 */
export const clearLocalLoginThemeMark = (): void => {
  safeLocalRemove(LOGIN_THEME_STORAGE_KEY);
};

// ------------------------------------------------------------------ 管理员缺省（sessionStorage 缓存）

export interface CachedDefaultTheme {
  theme: LoginThemeId;
  /** 写入缓存的时间戳（ms） */
  cachedAt: number;
  /** 是否已过期（过期仍返回，调用方决定是否重新拉取） */
  expired: boolean;
}

/**
 * 读管理员缺省的会话缓存；没有缓存 / 脏值返回 null。
 *
 * 过期也返回（`expired=true`）—— 登录页要**先渲染**再后台刷新，不能因为缓存过期就白屏。
 */
export const readCachedDefaultLoginTheme = (now: number = Date.now()): CachedDefaultTheme | null => {
  const theme = normalizeLoginTheme(safeSessionGet(DEFAULT_THEME_CACHE_KEY));
  if (!theme) return null;
  const at = Number(safeSessionGet(DEFAULT_THEME_CACHE_AT_KEY));
  const cachedAt = Number.isFinite(at) && at > 0 ? at : 0;
  return { theme, cachedAt, expired: cachedAt === 0 || now - cachedAt > DEFAULT_THEME_TTL_MS };
};

/** 写管理员缺省的会话缓存（非法值忽略） */
export const writeCachedDefaultLoginTheme = (theme: LoginThemeId | string | null | undefined, now: number = Date.now()): void => {
  const normalized = normalizeLoginTheme(typeof theme === 'string' ? theme : null);
  if (!normalized) return;
  safeSessionSet(DEFAULT_THEME_CACHE_KEY, normalized);
  safeSessionSet(DEFAULT_THEME_CACHE_AT_KEY, String(now));
};

/** 清管理员缺省缓存（测试/管理员改完缺省后强制刷新时用） */
export const clearCachedDefaultLoginTheme = (): void => {
  try {
    window.sessionStorage.removeItem(DEFAULT_THEME_CACHE_KEY);
    window.sessionStorage.removeItem(DEFAULT_THEME_CACHE_AT_KEY);
  } catch {
    /* ignore */
  }
};

// ------------------------------------------------------------------ 解析

export interface ResolveLoginThemeInput {
  /** `?theme=` 深链（预览用）；非法值忽略 */
  previewParam?: string | null;
  /** 本机标记（用户主动选择过才有） */
  localMark?: LoginThemeId | null;
  /** 管理员缺省（`GET /auth/login-theme` 或会话缓存） */
  adminDefault?: LoginThemeId | null;
}

export interface ResolveLoginThemeResult {
  theme: LoginThemeId;
  /** 命中的来源，便于页面上标注/排障（`?theme=` 命中即为 preview） */
  source: 'preview' | 'local' | 'adminDefault' | 'fallback';
}

/**
 * 解析登录页应显示的主题（纯函数，无副作用）。
 *
 * 顺序严格为 `?theme=` > 本机标记 > 管理员缺省 > pencil；
 * **注意**：本函数不写任何 storage —— `?theme=` 命中只影响本次渲染。
 */
export const resolveLoginTheme = ({
  previewParam,
  localMark,
  adminDefault,
}: ResolveLoginThemeInput): ResolveLoginThemeResult => {
  const preview = normalizeLoginTheme(previewParam);
  if (preview) return { theme: preview, source: 'preview' };
  const local = normalizeLoginTheme(localMark);
  if (local) return { theme: local, source: 'local' };
  const admin = normalizeLoginTheme(adminDefault);
  if (admin) return { theme: admin, source: 'adminDefault' };
  return { theme: DEFAULT_LOGIN_THEME, source: 'fallback' };
};
