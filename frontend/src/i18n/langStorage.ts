/**
 * 语言偏好的最小存储层（t64 抽出）。
 *
 * 只做「读写 localStorage('app.lang')」这一件事，不引入 React/antd/dayjs，
 * 因此可以被 axios 拦截器（utils/langHeader、utils/request）等非组件环境直接复用，
 * 也便于用 esbuild + node 做纯函数单测。
 */

export type Lang = 'zh-CN' | 'en-US';

export const DEFAULT_LANG: Lang = 'zh-CN';
export const LANG_STORAGE_KEY = 'app.lang';

/** 读取本地语言偏好（localStorage 不可用/值非法时回落默认语言） */
export const readStoredLang = (): Lang => {
  try {
    const stored = window.localStorage.getItem(LANG_STORAGE_KEY);
    return stored === 'en-US' || stored === 'zh-CN' ? stored : DEFAULT_LANG;
  } catch {
    return DEFAULT_LANG;
  }
};
