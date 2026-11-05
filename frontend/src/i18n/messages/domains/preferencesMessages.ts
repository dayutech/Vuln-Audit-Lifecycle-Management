/**
 * 偏好设置文案（t97 建立 / t110 按 v2 语义更新）。
 *
 * 约定：
 * - 主题名与副标题**不在这里重复定义** —— 它们已在 t95/t99 的
 *   `login.theme.*`（见 `domains/loginMessages.ts`），由
 *   `components/login/types.ts` 的 `LOGIN_THEME_LABEL_KEYS` /
 *   `LOGIN_THEME_SUBTITLE_KEYS` 以 `Record<LoginThemeId, MessageKey>` 引用。
 * - 失败分级与 `api/userPreferences.ts` 的状态码判定一一对应（400/401/403/5xx/网络），
 *   文案里保留后端 message 占位（t60 起后端 message 已按 Accept-Language 本地化）。
 * - t110 新增：本机标记状态、「从账号同步到本机」、「跟随系统默认」三段文案。
 */

export const preferencesZh = {
  'preferences.menu': '偏好设置',
  'preferences.title': '偏好设置',
  'preferences.close': '关闭',

  'preferences.loginTheme.title': '登录页主题',
  'preferences.loginTheme.desc':
    '选择登录页的主题风格。选中后会同时写入账号偏好与本机标记，下次进入登录页即生效；「跟随系统默认」会把账号偏好置为跟随默认（后端存 NULL）、清除本机标记，登录页改用管理员设置的缺省主题。',
  'preferences.loginTheme.current': '当前主题：{name}',
  'preferences.loginTheme.selectedTag': '已选',
  'preferences.loginTheme.preview': '预览',
  'preferences.loginTheme.previewTitle': '预览 · {name}',
  'preferences.loginTheme.previewHint': '预览嵌入的是真实登录页（?theme={id}），只读查看，不会执行登录请求。',
  'preferences.loginTheme.orderHint':
    '登录页读取顺序：?theme= 深链（仅预览）> 本机标记 login.theme > 管理员缺省（GET /auth/login-theme）> pencil。',
  'preferences.loginTheme.loading': '正在读取账号偏好…',
  'preferences.loginTheme.loadFailed': '账号偏好读取失败，当前按本机/管理员缺省显示（{theme}）',
  'preferences.loginTheme.saved': '登录页主题已切换为「{name}」，已写入本机标记',
  'preferences.loginTheme.unavailableTag': '服务端未启用',
  'preferences.loginTheme.err400': '服务端未启用该主题（{message}），已保持原主题',
  'preferences.loginTheme.err403': '当前无权修改偏好（模拟视角或权限受限时不可修改）',
  'preferences.loginTheme.err401': '登录已过期，请重新登录后再试',
  'preferences.loginTheme.err5xx': '服务端错误（HTTP {status}），主题未保存',
  'preferences.loginTheme.errNetwork': '网络请求失败，主题未保存',
  'preferences.loginTheme.errGeneric': '主题保存失败（HTTP {status}）',

  /* t110：本机标记 / 同步 / 跟随默认；t113：账号三态 */
  'preferences.loginTheme.markLocal': '本机标记：{name}',
  'preferences.loginTheme.followingDefault': '未设置本机标记，登录页按管理员缺省渲染：{name}',
  'preferences.loginTheme.accountTheme': '账号偏好：{name}',
  'preferences.loginTheme.accountFollowDefault': '账号偏好：跟随系统默认（当前缺省 {name}）',
  'preferences.loginTheme.syncFromAccount': '从账号同步到本机',
  'preferences.loginTheme.syncHint': '把账号偏好「{name}」写成本机标记（跨设备时手动同步一次）',
  'preferences.loginTheme.synced': '已从账号同步到本机：{name}',
  'preferences.loginTheme.followDefault': '跟随系统默认',
  'preferences.loginTheme.followDefaultHint': '把账号偏好置为「跟随系统默认」并清除本机标记，登录页显示管理员缺省「{name}」',
  'preferences.loginTheme.followedDefault': '已跟随系统默认：{name}',
  'preferences.loginTheme.needAccountTheme': '账号偏好尚未取到，请稍后重试',
  /* t113：跟随态下的按钮状态文案（qa 断言不依赖它们，但需要中英齐备） */
  'preferences.followDefaultActive': '当前已在跟随系统默认（{name}）',
  'preferences.syncDisabledFollowDefault': '当前账号为跟随系统默认，没有可同步的具体主题',
} as const;

export const preferencesEn: Record<keyof typeof preferencesZh, string> = {
  'preferences.menu': 'Preferences',
  'preferences.title': 'Preferences',
  'preferences.close': 'Close',

  'preferences.loginTheme.title': 'Login page theme',
  'preferences.loginTheme.desc':
    'Pick the look of the sign-in page. Selecting a theme writes both the account preference and a local mark on this browser; "Follow system default" stores the follow-default state on the account (NULL server-side) and clears the local mark, so the sign-in page uses the theme your administrator configured.',
  'preferences.loginTheme.current': 'Current theme: {name}',
  'preferences.loginTheme.selectedTag': 'Selected',
  'preferences.loginTheme.preview': 'Preview',
  'preferences.loginTheme.previewTitle': 'Preview · {name}',
  'preferences.loginTheme.previewHint':
    'The preview embeds the real sign-in page (?theme={id}) in read-only mode; no login request is sent.',
  'preferences.loginTheme.orderHint':
    'Sign-in page resolution order: ?theme= deep link (preview only) > local mark login.theme > administrator default (GET /auth/login-theme) > pencil.',
  'preferences.loginTheme.loading': 'Loading account preference…',
  'preferences.loginTheme.loadFailed': 'Could not load the account preference; showing the local / administrator default ({theme})',
  'preferences.loginTheme.saved': 'Login page theme switched to "{name}" and marked on this browser',
  'preferences.loginTheme.unavailableTag': 'Not enabled server-side',
  'preferences.loginTheme.err400': 'The server does not enable this theme ({message}); the previous theme is kept',
  'preferences.loginTheme.err403': 'Not allowed to change preferences (blocked while impersonating or under restricted permissions)',
  'preferences.loginTheme.err401': 'Session expired, please sign in again',
  'preferences.loginTheme.err5xx': 'Server error (HTTP {status}); the theme was not saved',
  'preferences.loginTheme.errNetwork': 'Network request failed; the theme was not saved',
  'preferences.loginTheme.errGeneric': 'Failed to save the theme (HTTP {status})',

  'preferences.loginTheme.markLocal': 'Local mark: {name}',
  'preferences.loginTheme.followingDefault': 'No local mark — the sign-in page uses the administrator default: {name}',
  'preferences.loginTheme.accountTheme': 'Account preference: {name}',
  'preferences.loginTheme.accountFollowDefault': 'Account preference: follow system default (current default {name})',
  'preferences.loginTheme.syncFromAccount': 'Sync from account',
  'preferences.loginTheme.syncHint': 'Copy the account preference "{name}" into this browser\u2019s local mark',
  'preferences.loginTheme.synced': 'Synced from account to this browser: {name}',
  'preferences.loginTheme.followDefault': 'Follow system default',
  'preferences.loginTheme.followDefaultHint':
    'Set the account preference to "follow system default" and clear the local mark; the sign-in page shows the administrator default "{name}"',
  'preferences.loginTheme.followedDefault': 'Following the system default: {name}',
  'preferences.loginTheme.needAccountTheme': 'Account preference not available yet, please try again in a moment',
  'preferences.followDefaultActive': 'Already following the system default ({name})',
  'preferences.syncDisabledFollowDefault': 'This account follows the system default, so there is no concrete theme to sync',
};
