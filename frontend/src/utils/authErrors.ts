import axios from 'axios';
import { readStoredLang } from '@/i18n/langStorage';
import type { MessageKey } from '@/i18n/messages/zh-CN';

/**
 * 自助注册 / 忘记密码的**错误分级**（t105）。
 *
 * 需求要求「按状态码分别处理」：
 * - `400` → 校验失败：优先用后端返回的**字段级**消息（`data.data` 是 `{字段: 消息}` 映射，
 *   来自 `@Valid` 校验），没有字段映射时作为行内提示；
 * - `409` → 用户名 / 手机号 / 邮箱已被占用：明确提示（后端消息里含具体是哪一项，作为 detail 展示）；
 * - `429` → 发送过于频繁：带 Retry-After 秒数；
 * - `5xx` → 通用服务异常；无响应 → 网络异常。
 *
 * **英文界面纯净**：后端 t102/t103 的服务层消息目前是硬编码中文，英文界面下若原样展示会混入中文，
 * 因此 `en-US` 下遇到含中文的后端消息时改用本地英文兜底文案（detail 一并过滤）。
 */

export type AuthErrorKind = 'field' | 'conflict' | 'rateLimit' | 'badRequest' | 'server' | 'network' | 'unknown';

export interface AuthErrorInfo {
  kind: AuthErrorKind;
  status?: number;
  /** 主提示（已本地化，英文界面一定是英文） */
  message: string;
  /** 后端原始消息（英文界面下含中文时为 undefined） */
  detail?: string;
  /** 字段级错误（后端 @Valid 校验映射） */
  fields?: Record<string, string>;
  /** 429 的等待秒数（来自 Retry-After 或响应体） */
  retryAfterSeconds?: number;
}

type MessageKeys =
  | 'auth.error.badRequest'
  | 'auth.error.conflict'
  | 'auth.error.rateLimit'
  | 'auth.error.server'
  | 'auth.error.network'
  | 'auth.error.unknown';

export type AuthTranslator = (key: MessageKey, vars?: Record<string, string | number>) => string;

const CJK = /[\u4e00-\u9fa5]/;
const FALLBACK_KEYS: Record<AuthErrorKind, MessageKeys> = {
  field: 'auth.error.badRequest',
  conflict: 'auth.error.conflict',
  rateLimit: 'auth.error.rateLimit',
  badRequest: 'auth.error.badRequest',
  server: 'auth.error.server',
  network: 'auth.error.network',
  unknown: 'auth.error.unknown',
};

/** 后端字段名与前端表单名一致（username/password/phone/code/email/emailCode），可直接回填 */
const acceptedField = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() !== '' ? value : null;

export const describeAuthError = (error: unknown, t: AuthTranslator): AuthErrorInfo => {
  const englishUi = readStoredLang() === 'en-US';
  /** 英文界面下丢掉中文后端消息，保证页面不混入中文 */
  const localize = (raw?: string): string | undefined => {
    if (!raw) return undefined;
    if (englishUi && CJK.test(raw)) return undefined;
    return raw;
  };

  if (!axios.isAxiosError(error)) {
    return { kind: 'unknown', message: t(FALLBACK_KEYS.unknown) };
  }

  const status = error.response?.status;
  const payload: any = error.response?.data ?? {};
  const rawMessage: string | undefined = payload?.message || payload?.error?.message;
  const detail = localize(rawMessage);

  const build = (kind: AuthErrorKind, extra?: Partial<AuthErrorInfo>): AuthErrorInfo => ({
    kind,
    status,
    message: detail ?? t(FALLBACK_KEYS[kind]),
    detail,
    ...extra,
  });

  if (!error.response) {
    return { kind: 'network', message: t(FALLBACK_KEYS.network) };
  }

  if (status === 400) {
    const rawFields = payload?.data;
    const fields: Record<string, string> = {};
    if (rawFields && typeof rawFields === 'object' && !Array.isArray(rawFields)) {
      Object.entries(rawFields as Record<string, unknown>).forEach(([field, value]) => {
        const text = acceptedField(value);
        if (text) fields[field] = localize(text) ?? t(FALLBACK_KEYS.field);
      });
    }
    if (Object.keys(fields).length > 0) {
      return { kind: 'field', status, message: t(FALLBACK_KEYS.field), detail, fields };
    }
    return build('badRequest');
  }

  if (status === 409) {
    return build('conflict');
  }

  if (status === 429) {
    const header = error.response.headers?.['retry-after'];
    const fromHeader = Array.isArray(header) ? Number(header[0]) : Number(header);
    const fromBody = Number(payload?.retryAfter ?? payload?.data?.retryAfter);
    const retryAfterSeconds = Number.isFinite(fromHeader) && fromHeader > 0
      ? fromHeader
      : Number.isFinite(fromBody) && fromBody > 0
        ? fromBody
        : undefined;
    return build('rateLimit', { retryAfterSeconds });
  }

  if (status && status >= 500) {
    return build('server');
  }

  return build('unknown');
};
