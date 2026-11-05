/**
 * 系统设置页文案（t110：登录页默认主题）。
 *
 * 命名：`admin.settings.*`（页面本体）+ `menu.settings`（侧边栏入口）。
 * 失败分级与 `api/systemSetting.ts` 的状态码判定一一对应（400/401/403/5xx/网络），
 * 400 保留后端 message 占位（后端会给出「支持: …」的完整支持列表）。
 */

export const settingsZh = {
  'menu.settings': '系统设置',

  'admin.settings.title': '系统设置',
  'admin.settings.subtitle': '全局登录与安全策略：以下设置对全站生效，仅管理员可修改。',

  'admin.settings.loginMethods.title': '登录方式',
  'admin.settings.loginMethods.desc': '登录方式相关的全局开关（单点登录、注册审批等）会集中在本区块。',
  'admin.settings.loginMethods.pending': '单点登录与注册审批开关将在后续迭代接入本区块，当前只提供登录页默认主题。',

  'admin.settings.loginTheme.title': '登录页默认主题',
  'admin.settings.loginTheme.desc': '新访客默认看到该主题；已自行选择过的用户不受影响。',
  'admin.settings.loginTheme.current': '当前默认：{name}',
  'admin.settings.loginTheme.updatedBy': '最后修改人：{name}',
  'admin.settings.loginTheme.updatedAt': '修改时间：{time}',
  'admin.settings.loginTheme.never': '从未修改（系统初始化默认值）',
  'admin.settings.loginTheme.loading': '正在读取全局设置…',
  'admin.settings.loginTheme.loadFailed': '默认主题读取失败，请稍后重试',
  'admin.settings.loginTheme.save': '保存默认主题',
  'admin.settings.loginTheme.saved': '登录页默认主题已更新为「{name}」',
  'admin.settings.loginTheme.unchanged': '当前已是该主题，无需保存',
  'admin.settings.loginTheme.selectedTag': '当前默认',
  'admin.settings.loginTheme.orderHint':
    '生效链路：未设置本机标记的访客按此缺省渲染（?theme= 深链与用户本机标记仍然优先）。',
  'admin.settings.loginTheme.forbiddenTitle': '仅管理员可访问系统设置',
  'admin.settings.loginTheme.forbiddenDesc': '登录页默认主题属于全局策略，仅管理员可读写；如需调整请联系管理员。',
  'admin.settings.loginTheme.err400': '不支持的登录页主题（{message}）',
  'admin.settings.loginTheme.err401': '登录已过期，请重新登录后再试',
  'admin.settings.loginTheme.err403': '无权修改全局设置（仅管理员可用）',
  'admin.settings.loginTheme.err5xx': '服务端错误（HTTP {status}），默认主题未保存',
  'admin.settings.loginTheme.errNetwork': '网络请求失败，默认主题未保存',
  'admin.settings.loginTheme.errGeneric': '保存失败（HTTP {status}）',
} as const;

export const settingsEn: Record<keyof typeof settingsZh, string> = {
  'menu.settings': 'System settings',

  'admin.settings.title': 'System settings',
  'admin.settings.subtitle': 'Global sign-in and security policy. These settings apply site-wide and can only be changed by administrators.',

  'admin.settings.loginMethods.title': 'Sign-in methods',
  'admin.settings.loginMethods.desc': 'Global switches for sign-in methods (SSO, registration approval, …) live in this block.',
  'admin.settings.loginMethods.pending':
    'SSO and registration-approval switches will be added to this block in a later iteration; today only the default login theme is configurable.',

  'admin.settings.loginTheme.title': 'Default login page theme',
  'admin.settings.loginTheme.desc': 'New visitors see this theme by default; users who picked their own theme are unaffected.',
  'admin.settings.loginTheme.current': 'Current default: {name}',
  'admin.settings.loginTheme.updatedBy': 'Last changed by: {name}',
  'admin.settings.loginTheme.updatedAt': 'Changed at: {time}',
  'admin.settings.loginTheme.never': 'Never changed (system initialised default)',
  'admin.settings.loginTheme.loading': 'Loading global settings…',
  'admin.settings.loginTheme.loadFailed': 'Could not load the default theme, please retry later',
  'admin.settings.loginTheme.save': 'Save default theme',
  'admin.settings.loginTheme.saved': 'Default login page theme updated to "{name}"',
  'admin.settings.loginTheme.unchanged': 'This is already the current default, nothing to save',
  'admin.settings.loginTheme.selectedTag': 'Current default',
  'admin.settings.loginTheme.orderHint':
    'Resolution: visitors without a local mark get this theme (?theme= deep links and the user\u2019s own local mark still take precedence).',
  'admin.settings.loginTheme.forbiddenTitle': 'System settings are administrator-only',
  'admin.settings.loginTheme.forbiddenDesc':
    'The default login page theme is a global policy and can only be read or changed by administrators.',
  'admin.settings.loginTheme.err400': 'Unsupported login page theme ({message})',
  'admin.settings.loginTheme.err401': 'Session expired, please sign in again',
  'admin.settings.loginTheme.err403': 'Not allowed to change global settings (administrators only)',
  'admin.settings.loginTheme.err5xx': 'Server error (HTTP {status}); the default theme was not saved',
  'admin.settings.loginTheme.errNetwork': 'Network request failed; the default theme was not saved',
  'admin.settings.loginTheme.errGeneric': 'Failed to save (HTTP {status})',
};
