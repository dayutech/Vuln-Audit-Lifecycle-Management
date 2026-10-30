import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { ConfigProvider } from 'antd';
import antdZhCN from 'antd/locale/zh_CN';
import antdEnUS from 'antd/locale/en_US';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import 'dayjs/locale/en';
import { zhCN, type MessageCatalog, type MessageKey } from './messages/zh-CN';
import { enUS } from './messages/en-US';

/**
 * 轻量 i18n（自建，零新增依赖：~120 行）。
 *
 * 选型说明：未引入 react-i18next（约 +12 kB gzip 且需 i18next 依赖），
 * 本项目的诉求是「中英切换 + 文案集中管理」，用 React Context 即可满足；
 * 后续若需要复数/嵌套/命名空间再迁移代价也很低（useT 的调用面不变）。
 *
 * 能力：
 * - `useT()` 返回 { lang, setLang, t }，t 支持 `{name}` 占位符替换；
 * - 语言持久化到 localStorage('app.lang')，默认 zh-CN；
 * - 同步 `<html lang>`、antd ConfigProvider locale、dayjs locale（切换即时生效）。
 *
 * 已知限制：后端错误信息（ApiResponse.message / 校验提示）目前只有中文，
 * 本层只翻译前端自有文案，不处理后端返回的 message（见 t53 报告）。
 */

export type Lang = 'zh-CN' | 'en-US';

export const DEFAULT_LANG: Lang = 'zh-CN';
export const LANG_STORAGE_KEY = 'app.lang';

const catalogs: Record<Lang, MessageCatalog> = {
  'zh-CN': zhCN,
  'en-US': enUS,
};

const antdLocales: Record<Lang, typeof antdZhCN> = {
  'zh-CN': antdZhCN,
  'en-US': antdEnUS,
};

const dayjsLocales: Record<Lang, string> = {
  'zh-CN': 'zh-cn',
  'en-US': 'en',
};

/** 读取本地语言偏好（localStorage 不可用/值非法时回落默认语言） */
export const readStoredLang = (): Lang => {
  try {
    const stored = window.localStorage.getItem(LANG_STORAGE_KEY);
    return stored === 'en-US' || stored === 'zh-CN' ? stored : DEFAULT_LANG;
  } catch {
    return DEFAULT_LANG;
  }
};

const interpolate = (template: string, vars?: Record<string, string | number>): string => {
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    Object.prototype.hasOwnProperty.call(vars, name) ? String(vars[name]) : match
  );
};

export interface I18nContextValue {
  lang: Lang;
  setLang: (lang: Lang) => void;
  t: (key: MessageKey, vars?: Record<string, string | number>) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

export const I18nProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [lang, setLangState] = useState<Lang>(readStoredLang);

  const setLang = useCallback((next: Lang) => {
    setLangState(next);
    try {
      window.localStorage.setItem(LANG_STORAGE_KEY, next);
    } catch {
      // 隐私模式下 localStorage 可能不可用，语言仅本次会话生效
    }
  }, []);

  // 同步 html lang / dayjs locale（antd 走下面的 ConfigProvider）
  useEffect(() => {
    document.documentElement.lang = lang;
    dayjs.locale(dayjsLocales[lang]);
  }, [lang]);

  const t = useCallback(
    (key: MessageKey, vars?: Record<string, string | number>) => {
      const template = catalogs[lang][key] ?? catalogs[DEFAULT_LANG][key];
      // 缺键时回显 key，便于开发期发现遗漏（生产不会出现中文串错位）
      return interpolate(template ?? String(key), vars);
    },
    [lang]
  );

  const value = useMemo<I18nContextValue>(() => ({ lang, setLang, t }), [lang, setLang, t]);

  return (
    <I18nContext.Provider value={value}>
      <ConfigProvider locale={antdLocales[lang]}>{children}</ConfigProvider>
    </I18nContext.Provider>
  );
};

/** 取翻译函数与语言状态；必须在 I18nProvider 内使用 */
export const useT = (): I18nContextValue => {
  const ctx = useContext(I18nContext);
  if (!ctx) {
    throw new Error('useT() must be used inside <I18nProvider>');
  }
  return ctx;
};

export type { MessageKey, MessageCatalog };
