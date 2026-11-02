import type { InternalAxiosRequestConfig } from 'axios';
import { readStoredLang, type Lang } from '@/i18n/langStorage';

/**
 * Accept-Language 协商（t64）。
 *
 * 背景：后端 t60 已支持错误消息 i18n，语言顺序为 `?lang=` → `Accept-Language` → 默认中文。
 * 前端界面语言存于 localStorage('app.lang')（与 t53 的 LANG_STORAGE_KEY 一致），
 * 这里在**每次请求时实时读取**，因此切换语言后无需刷新即可让后端返回对应语言的 message。
 *
 * 约定：
 * - 调用方显式设置了 Accept-Language（含空串以外任意值）时一律尊重，不覆盖；
 * - localStorage 不可用/值非法时回落到默认中文（与 I18nProvider 行为一致）。
 */

export const ACCEPT_LANGUAGE_HEADER = 'Accept-Language';

/** 当前界面语言 → Accept-Language 取值（zh-CN / en-US） */
export const currentAcceptLanguage = (): Lang => readStoredLang();

/** 请求头里是否已有调用方显式设置的 Accept-Language */
const hasExplicitAcceptLanguage = (config: InternalAxiosRequestConfig): boolean => {
  const headers = config.headers as unknown as
    | { get?: (name: string) => unknown; [key: string]: unknown }
    | undefined;
  if (!headers) return false;
  // axios v1 的 AxiosHeaders 实例用 get() 取值（大小写不敏感）
  if (typeof headers.get === 'function') {
    const value = headers.get(ACCEPT_LANGUAGE_HEADER);
    return typeof value === 'string' && value.trim().length > 0;
  }
  const raw = headers[ACCEPT_LANGUAGE_HEADER] ?? headers['accept-language'];
  return typeof raw === 'string' ? raw.trim().length > 0 : raw != null;
};

/**
 * 给请求补上 Accept-Language（幂等，可在拦截器里反复调用）。
 * 返回同一个 config，方便链式使用。
 */
export const attachAcceptLanguageHeader = <T extends InternalAxiosRequestConfig>(config: T): T => {
  if (hasExplicitAcceptLanguage(config)) {
    return config;
  }
  const lang = currentAcceptLanguage();
  if (config.headers) {
    if (typeof config.headers.set === 'function') {
      config.headers.set(ACCEPT_LANGUAGE_HEADER, lang);
    } else {
      (config.headers as Record<string, unknown>)[ACCEPT_LANGUAGE_HEADER] = lang;
    }
  }
  return config;
};
