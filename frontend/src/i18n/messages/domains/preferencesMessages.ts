/**
 * 偏好设置文案（t97：登录页主题的选择 / 持久化 / 预览）。
 *
 * 约定：
 * - 主题名与副标题**不在这里重复定义** —— 它们已在 t95/t99 的
 *   `login.theme.*`（见 `domains/loginMessages.ts`），由
 *   `components/login/types.ts` 的 `LOGIN_THEME_LABEL_KEYS` /
 *   `LOGIN_THEME_SUBTITLE_KEYS` 以 `Record<LoginThemeId, MessageKey>` 引用。
 *   本文件只补「弹窗外壳 / 保存反馈 / 失败分级」这些新文案，前缀统一 `preferences.`。
 * - 失败分级与 `api/userPreferences.ts` 的状态码判定一一对应（400/401/403/5xx/网络），
 *   文案里保留后端 message 占位，后端 message 已被 t60 按 Accept-Language 本地化。
 */

export const preferencesZh = {
  'preferences.menu': '偏好设置',
  'preferences.title': '偏好设置',
  'preferences.close': '关闭',

  'preferences.loginTheme.title': '登录页主题',
  'preferences.loginTheme.desc': '选择登录页的主题风格：保存后立即写入账号偏好与本机缓存，下次进入登录页即生效。',
  'preferences.loginTheme.current': '当前主题：{name}',
  'preferences.loginTheme.selectedTag': '当前',
  'preferences.loginTheme.preview': '预览',
  'preferences.loginTheme.previewTitle': '预览 · {name}',
  'preferences.loginTheme.previewHint': '预览嵌入的是真实登录页（?theme={id}），只读查看，不会执行登录请求。',
  'preferences.loginTheme.orderHint':
    '登录页读取顺序：?theme= 深链（仅预览用）> 本机缓存 login.theme > 账号偏好（缺省 pencil）。',
  'preferences.loginTheme.loading': '正在读取账号偏好…',
  'preferences.loginTheme.loadFailed': '账号偏好读取失败，已按本机缓存显示（{theme}）',
  'preferences.loginTheme.saved': '登录页主题已切换为「{name}」',
  'preferences.loginTheme.cacheSynced': '本机缓存 login.theme 已更新为 {theme}',
  'preferences.loginTheme.unavailableTag': '服务端未启用',
  'preferences.loginTheme.err400': '服务端未启用该主题（{message}），已保持原主题',
  'preferences.loginTheme.err403': '当前无权修改偏好（模拟视角或权限受限时不可修改）',
  'preferences.loginTheme.err401': '登录已过期，请重新登录后再试',
  'preferences.loginTheme.err5xx': '服务端错误（HTTP {status}），主题未保存',
  'preferences.loginTheme.errNetwork': '网络请求失败，主题未保存',
  'preferences.loginTheme.errGeneric': '主题保存失败（HTTP {status}）',
} as const;

export const preferencesEn: Record<keyof typeof preferencesZh, string> = {
  'preferences.menu': 'Preferences',
  'preferences.title': 'Preferences',
  'preferences.close': 'Close',

  'preferences.loginTheme.title': 'Login page theme',
  'preferences.loginTheme.desc':
    'Pick the look of the sign-in page. Saving writes both the account preference and this browser cache, and applies the next time the sign-in page loads.',
  'preferences.loginTheme.current': 'Current theme: {name}',
  'preferences.loginTheme.selectedTag': 'Current',
  'preferences.loginTheme.preview': 'Preview',
  'preferences.loginTheme.previewTitle': 'Preview · {name}',
  'preferences.loginTheme.previewHint':
    'The preview embeds the real sign-in page (?theme={id}) in read-only mode; no login request is sent.',
  'preferences.loginTheme.orderHint':
    'Sign-in page resolution order: ?theme= deep link (preview only) > local cache login.theme > account preference (default pencil).',
  'preferences.loginTheme.loading': 'Loading account preference…',
  'preferences.loginTheme.loadFailed': 'Could not load the account preference; showing the local cache ({theme}) instead',
  'preferences.loginTheme.saved': 'Login page theme switched to "{name}"',
  'preferences.loginTheme.cacheSynced': 'Local cache login.theme updated to {theme}',
  'preferences.loginTheme.unavailableTag': 'Not enabled server-side',
  'preferences.loginTheme.err400': 'The server does not enable this theme ({message}); the previous theme is kept',
  'preferences.loginTheme.err403': 'Not allowed to change preferences (blocked while impersonating or under restricted permissions)',
  'preferences.loginTheme.err401': 'Session expired, please sign in again',
  'preferences.loginTheme.err5xx': 'Server error (HTTP {status}); the theme was not saved',
  'preferences.loginTheme.errNetwork': 'Network request failed; the theme was not saved',
  'preferences.loginTheme.errGeneric': 'Failed to save the theme (HTTP {status})',
};
