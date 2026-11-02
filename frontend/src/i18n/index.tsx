import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { ConfigProvider } from 'antd';
import antdZhCN from 'antd/locale/zh_CN';
import antdEnUS from 'antd/locale/en_US';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import 'dayjs/locale/en';
import { zhCN, type MessageCatalog, type MessageKey } from './messages/zh-CN';
import { enUS } from './messages/en-US';
import { DEFAULT_LANG, LANG_STORAGE_KEY, readStoredLang, type Lang } from './langStorage';

export { DEFAULT_LANG, LANG_STORAGE_KEY, readStoredLang };
export type { Lang };

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

/** antd 表单默认校验文案（未自定义 rules 的 required 提示随语言切换） */
const formValidateMessages: Record<Lang, Record<string, Record<string, string>>> = {
  'zh-CN': {
    default: { required: '请填写该项' },
    string: { required: '请填写该项', min: '至少 {min} 个字符', max: '最多 {max} 个字符' },
    number: { required: '请填写该项' },
    array: { required: '请至少选择一项' },
    types: { email: '邮箱格式不正确', url: '链接格式不正确' },
  },
  'en-US': {
    default: { required: 'This field is required' },
    string: { required: 'This field is required', min: 'At least {min} characters', max: 'At most {max} characters' },
    number: { required: 'This field is required' },
    array: { required: 'Select at least one item' },
    types: { email: 'Invalid email address', url: 'Invalid URL' },
  },
};
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
      <ConfigProvider locale={antdLocales[lang]} form={{ validateMessages: formValidateMessages[lang] }}>{children}</ConfigProvider>
    </I18nContext.Provider>
  );
};

/**
 * 非组件环境的翻译入口（t64）。
 *
 * axios 拦截器 / 工具函数不在 React 树内，拿不到 useT()，这里按 localStorage 里的
 * 当前语言实时取词条（不缓存语言），因此语言切换后无需刷新即可生效。
 */
export const translate = (key: MessageKey, vars?: Record<string, string | number>): string => {
  const lang = readStoredLang();
  const template = catalogs[lang][key] ?? catalogs[DEFAULT_LANG][key];
  return interpolate(template ?? String(key), vars);
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
