import type { ImpersonateTarget } from '@/api/impersonation';

/**
 * 模拟视角的本地状态（t91）。
 *
 * 存储约定（都与后端 t90 对齐）：
 * - `access_token`：**当前生效**的 token（模拟态下就是受限 token）
 * - `impersonation.original`：发起模拟前的 ADMIN token 快照 `{token, userId, username}`
 * - `impersonation.target`：被模拟用户 `{id, username, roles, expiresAt}`
 *
 * 所有读写都走本模块，页面不直接碰 localStorage，避免键名散落。
 */

export const ORIGINAL_TOKEN_KEY = 'impersonation.original';
export const TARGET_KEY = 'impersonation.target';
export const ACCESS_TOKEN_KEY = 'access_token';

export interface OriginalSession {
  token: string;
  userId?: string;
  username?: string;
}

export interface ImpersonationTargetInfo extends ImpersonateTarget {
  /** ISO 时间戳（后端 ImpersonateResponse.expiresAt） */
  expiresAt?: string;
}

const safeGet = (key: string): string | null => {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
};

const safeSet = (key: string, value: string) => {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    /* 隐私模式：仅本次会话生效 */
  }
};

const safeRemove = (key: string) => {
  try {
    window.localStorage.removeItem(key);
  } catch {
    /* ignore */
  }
};

const parseJson = <T>(raw: string | null): T | null => {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
};

export const readOriginalSession = (): OriginalSession | null => {
  const parsed = parseJson<OriginalSession>(safeGet(ORIGINAL_TOKEN_KEY));
  return parsed?.token ? parsed : null;
};

export const readImpersonationTarget = (): ImpersonationTargetInfo | null => {
  const parsed = parseJson<ImpersonationTargetInfo>(safeGet(TARGET_KEY));
  return parsed?.id && parsed?.username ? parsed : null;
};

/** 当前是否处于模拟态（有 target 记录即视为模拟态；token 是否仍有效由调用方判断） */
export const isImpersonating = (): boolean => Boolean(readImpersonationTarget());

/** 进入模拟态：先保存原 admin token 快照，再写入受限 token 与目标信息 */
export const beginImpersonation = (params: {
  original: OriginalSession;
  token: string;
  target: ImpersonationTargetInfo;
}) => {
  safeSet(ORIGINAL_TOKEN_KEY, JSON.stringify(params.original));
  safeSet(TARGET_KEY, JSON.stringify(params.target));
  safeSet(ACCESS_TOKEN_KEY, params.token);
};

/** 结束模拟态：把原 admin token 写回，并清理两个键 */
export const endImpersonation = (): OriginalSession | null => {
  const original = readOriginalSession();
  if (original?.token) safeSet(ACCESS_TOKEN_KEY, original.token);
  safeRemove(TARGET_KEY);
  safeRemove(ORIGINAL_TOKEN_KEY);
  return original;
};

/** 目标 token 是否已按 expiresAt 过期（无法判断时返回 false，交给后端 401 兜底） */
export const isImpersonationExpired = (target: ImpersonationTargetInfo | null): boolean => {
  if (!target?.expiresAt) return false;
  const ts = Date.parse(target.expiresAt);
  return Number.isFinite(ts) && Date.now() >= ts;
};

/** 剩余毫秒（用于横幅倒计时；无 expiresAt 返回 null） */
export const impersonationRemainingMs = (target: ImpersonationTargetInfo | null): number | null => {
  if (!target?.expiresAt) return null;
  const ts = Date.parse(target.expiresAt);
  if (!Number.isFinite(ts)) return null;
  return Math.max(ts - Date.now(), 0);
};

/** 把毫秒格式化成 mm:ss（超过 1 小时显示 h:mm:ss） */
export const formatRemaining = (ms: number): string => {
  const total = Math.floor(ms / 1000);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
};

/** 角色码 → 展示文案（角色名来自后端数据，这里给常用码的中英标签兜底） */
export const ROLE_LABELS: Record<string, { zh: string; en: string }> = {
  admin: { zh: '系统管理员', en: 'Admin' },
  project_leader: { zh: '项目负责人', en: 'Project lead' },
  team_leader: { zh: '小组长', en: 'Team lead' },
  vulnerability_engineer: { zh: '漏洞挖掘工程师', en: 'Vulnerability engineer' },
};

export const roleLabel = (role: string, lang: 'zh-CN' | 'en-US'): string =>
  ROLE_LABELS[role]?.[lang === 'en-US' ? 'en' : 'zh'] ?? role;
