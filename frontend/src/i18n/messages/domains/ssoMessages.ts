/**
 * 第三方登录（SSO）文案（t106，后端 t104）。
 *
 * - `sso.*`：登录页按钮、发起失败分级、回调结果三态（LOGIN / 202 待审批 / 403 等）；
 * - `ssoBinding.*`：账号设置里的「社交账号绑定」区块；
 * - `admin.sso.*`：管理员「登录方式」开关（标题/描述沿用 `admin.settings.loginMethods.*`）。
 *
 * 与其它域同一约定：中英各一份，`Record<keyof typeof ssoZh, string>` 约束下缺键即 tsc 报错。
 */

export const ssoZh = {
  // ---------- 提供商名称 ----------
  'sso.provider.github': 'GitHub',
  'sso.provider.microsoft': 'Microsoft',
  'sso.provider.google': 'Google',
  'sso.provider.wechat': '微信',

  // ---------- 发起失败分级 ----------
  'sso.error.disabled': '该登录方式已关闭，请使用账号密码登录',
  'sso.error.notConfigured': '该登录方式尚未完成配置，请联系管理员',
  'sso.error.unavailable': '无法发起第三方登录，请稍后再试',
  'sso.error.startFailed': '发起第三方登录失败',

  // ---------- 回调结果 ----------
  'sso.callback.pageTitle': '第三方登录',
  'sso.callback.working': '正在确认第三方账号…',
  'sso.callback.pendingTitle': '已提交关联申请',
  'sso.callback.pendingDesc': '该社交账号首次登录，已提交关联申请并等待管理员审批；审批通过后即可用该账号直接登录。本页不会保存登录状态。',
  'sso.callback.contactAdmin': '请联系系统管理员完成审批。为避免重复提交，本页不提供重试按钮。',
  'sso.callback.boundTitle': '绑定成功',
  'sso.callback.boundDesc': '该第三方账号已与你的账号绑定，下次可直接用它登录。',
  'sso.callback.errorTitle': '第三方登录未完成',
  'sso.callback.invalid': '授权已失效或不匹配，请重新发起登录。',
  'sso.callback.unavailable': '该账号当前不可用（待审批 / 已停用 / 已被拒绝），请联系管理员。',
  'sso.callback.notConfigured': '该登录方式尚未完成配置，请联系管理员。',
  'sso.callback.missingParams': '缺少授权参数，请从登录页重新发起。',
  'sso.callback.failed': '第三方登录未完成，请稍后重试。',
  'sso.callback.backToLogin': '返回登录',
  'sso.callback.backToBindings': '返回账号设置',

  // ---------- 账号设置：绑定列表 ----------
  'ssoBinding.menu': '社交账号绑定',
  'ssoBinding.title': '社交账号绑定',
  'ssoBinding.subtitle': '绑定第三方账号后可直接用它登录；解绑需要输入当前密码。',
  'ssoBinding.status.bound': '已绑定',
  'ssoBinding.status.unbound': '未绑定',
  'ssoBinding.status.disabled': '未启用',
  'ssoBinding.external': '外部账号：{name}',
  'ssoBinding.boundAt': '绑定时间：{time}',
  'ssoBinding.bind': '绑定',
  'ssoBinding.unbind': '解绑',
  'ssoBinding.unbindTitle': '解绑「{name}」',
  'ssoBinding.unbindDesc': '解绑后该第三方账号将无法直接登录，需要重新提交审批。请输入当前密码确认。',
  'ssoBinding.password': '当前密码',
  'ssoBinding.passwordRequired': '请输入当前密码',
  'ssoBinding.passwordWrong': '当前密码不正确',
  'ssoBinding.unbindSuccess': '已解绑「{name}」',
  'ssoBinding.bindSuccess': '已绑定「{name}」',
  'ssoBinding.duplicate': '该账号已绑定 {name}，请先解绑',
  'ssoBinding.notConfigured': '该登录方式尚未完成配置，请联系管理员',
  'ssoBinding.failed': '操作失败，请稍后再试',
  'ssoBinding.refresh': '刷新',
  'ssoBinding.loadFailed': '加载绑定信息失败',
  'ssoBinding.disabledHint': '管理员未开启该登录方式，暂不可绑定',
  'ssoBinding.unbindNote': '解绑后再次使用需重新审批',

  // ---------- 管理员：登录方式开关 ----------
  'admin.sso.configured': '已配置凭据',
  'admin.sso.notConfigured': '未配置凭据',
  'admin.sso.note': '未配置凭据的提供商即使开启也不会显示在登录页（授权会返回 503）。',
  'admin.sso.loadFailed': '加载登录方式失败',
  'admin.sso.toggleFailed': '切换失败：{reason}',
  'admin.sso.toggleSuccess': '已{state}「{name}」',
  'admin.sso.stateOn': '开启',
  'admin.sso.stateOff': '关闭',
  'admin.sso.needAdmin': '仅管理员可修改',
} as const;

export const ssoEn: Record<keyof typeof ssoZh, string> = {
  'sso.provider.github': 'GitHub',
  'sso.provider.microsoft': 'Microsoft',
  'sso.provider.google': 'Google',
  'sso.provider.wechat': 'WeChat',

  'sso.error.disabled': 'This sign-in method is disabled — use your username and password',
  'sso.error.notConfigured': 'This sign-in method is not configured yet — contact your administrator',
  'sso.error.unavailable': 'Could not start third-party sign-in, please try again later',
  'sso.error.startFailed': 'Failed to start third-party sign-in',

  'sso.callback.pageTitle': 'Third-party sign-in',
  'sso.callback.working': 'Verifying your third-party account…',
  'sso.callback.pendingTitle': 'Link request submitted',
  'sso.callback.pendingDesc': 'This is the first sign-in with this social account, so a link request has been submitted for administrator approval. You can sign in with it once approved. No session is stored on this page.',
  'sso.callback.contactAdmin': 'Contact your administrator to complete the approval. This page intentionally offers no retry button to avoid duplicate requests.',
  'sso.callback.boundTitle': 'Account linked',
  'sso.callback.boundDesc': 'This third-party account is now linked and can be used to sign in.',
  'sso.callback.errorTitle': 'Third-party sign-in did not complete',
  'sso.callback.invalid': 'The authorization has expired or does not match — please start again from the sign-in page.',
  'sso.callback.unavailable': 'This account is not available (pending, disabled or rejected). Please contact your administrator.',
  'sso.callback.notConfigured': 'This sign-in method is not configured yet — contact your administrator.',
  'sso.callback.missingParams': 'Authorization parameters are missing — please start again from the sign-in page.',
  'sso.callback.failed': 'Third-party sign-in did not complete, please try again later.',
  'sso.callback.backToLogin': 'Back to sign in',
  'sso.callback.backToBindings': 'Back to account settings',

  'ssoBinding.menu': 'Social accounts',
  'ssoBinding.title': 'Social account links',
  'ssoBinding.subtitle': 'Link a third-party account to sign in with it. Unlinking requires your current password.',
  'ssoBinding.status.bound': 'Linked',
  'ssoBinding.status.unbound': 'Not linked',
  'ssoBinding.status.disabled': 'Disabled',
  'ssoBinding.external': 'External account: {name}',
  'ssoBinding.boundAt': 'Linked on {time}',
  'ssoBinding.bind': 'Link',
  'ssoBinding.unbind': 'Unlink',
  'ssoBinding.unbindTitle': 'Unlink {name}',
  'ssoBinding.unbindDesc': 'After unlinking, this third-party account can no longer sign in and must be approved again. Enter your current password to confirm.',
  'ssoBinding.password': 'Current password',
  'ssoBinding.passwordRequired': 'Please enter your current password',
  'ssoBinding.passwordWrong': 'Current password is incorrect',
  'ssoBinding.unbindSuccess': '{name} unlinked',
  'ssoBinding.bindSuccess': '{name} linked',
  'ssoBinding.duplicate': 'This account is already linked to {name} — unlink it first',
  'ssoBinding.notConfigured': 'This sign-in method is not configured yet — contact your administrator',
  'ssoBinding.failed': 'Operation failed, please try again later',
  'ssoBinding.refresh': 'Refresh',
  'ssoBinding.loadFailed': 'Failed to load your linked accounts',
  'ssoBinding.disabledHint': 'This sign-in method is disabled by the administrator',
  'ssoBinding.unbindNote': 'Re-approval is required to use it again after unlinking',

  'admin.sso.configured': 'Credentials configured',
  'admin.sso.notConfigured': 'No credentials',
  'admin.sso.note': 'Providers without credentials stay hidden on the sign-in page even when enabled (authorization returns 503).',
  'admin.sso.loadFailed': 'Failed to load sign-in methods',
  'admin.sso.toggleFailed': 'Failed to switch: {reason}',
  'admin.sso.toggleSuccess': '{name} is now {state}',
  'admin.sso.stateOn': 'enabled',
  'admin.sso.stateOff': 'disabled',
  'admin.sso.needAdmin': 'Only administrators can change this',
};

