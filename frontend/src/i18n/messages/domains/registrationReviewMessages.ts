/**
 * 注册审批（t114）文案：`registrationReview.*`。
 *
 * 约定：
 * - 状态名、错误分级、行内操作、审批/拒绝弹窗全部在这里，中英各一份（`Record` 约束保证对等）；
 * - 错误提示的 400/403/409/5xx 分级与 `api/registrationReview.ts` + `utils/errorMessage` 一致：
 *   能拿到后端 message（t60 起按 Accept-Language 本地化）时优先原样展示，这里只提供兜底与标题。
 */

export const registrationReviewZh = {
  'registrationReview.menu': '注册审批',
  'registrationReview.title': '注册审批',
  'registrationReview.subtitle': '处理自助注册与第三方登录产生的账号申请；审批通过时必须显式选择角色。',
  'registrationReview.refresh': '刷新',

  'registrationReview.status.pending': '待审批',
  'registrationReview.status.approved': '已通过',
  'registrationReview.status.rejected': '已拒绝',
  'registrationReview.status.unknown': '未知状态',

  'registrationReview.filter.status': '状态',
  'registrationReview.filter.statusAll': '全部状态',
  'registrationReview.filter.keyword': '关键词',
  'registrationReview.filter.keywordPlaceholder': '用户名 / 手机号 / 邮箱',
  'registrationReview.keywordHint': '后端列表接口没有关键词参数：这里只过滤当前页已加载的数据。',

  'registrationReview.col.createdAt': '提交时间',
  'registrationReview.col.username': '用户名',
  'registrationReview.col.phone': '手机号',
  'registrationReview.col.email': '邮箱',
  'registrationReview.col.status': '状态',
  'registrationReview.col.source': '来源',
  'registrationReview.col.reviewer': '审批人',
  'registrationReview.col.reviewedAt': '审批时间',
  'registrationReview.col.actions': '操作',

  'registrationReview.showFull': '显示完整',
  'registrationReview.hideFull': '隐藏完整',
  'registrationReview.fullValueAdminOnly': '仅管理员可查看完整手机号/邮箱',
  'registrationReview.emptyPlaceholder': '—',
  'registrationReview.reviewerUnknown': '（未知，可能已被清理）',

  'registrationReview.source.local': '自助注册',
  'registrationReview.source.sso': '第三方登录（{provider}）',
  'registrationReview.provider.github': 'GitHub',
  'registrationReview.provider.microsoft': 'Microsoft',
  'registrationReview.provider.google': 'Google',
  'registrationReview.provider.wechat': '微信',

  'registrationReview.action.approve': '审批通过',
  'registrationReview.action.reject': '拒绝',
  'registrationReview.action.purge': '彻底删除',

  'registrationReview.empty.title': '没有符合条件的注册申请',
  'registrationReview.empty.desc': '切换状态或清空关键词后再试',
  'registrationReview.loadFailed': '注册申请加载失败',
  'registrationReview.forbidden.title': '无权限访问注册审批',
  'registrationReview.forbidden.desc': '注册审批仅管理员（admin）与项目负责人（project_leader）可用。',

  'registrationReview.approve.title': '审批通过注册申请',
  'registrationReview.approve.detailTitle': '申请详情',
  'registrationReview.approve.field.username': '用户名',
  'registrationReview.approve.field.phone': '手机号',
  'registrationReview.approve.field.email': '邮箱',
  'registrationReview.approve.field.createdAt': '提交时间',
  'registrationReview.approve.verified': '已验证',
  'registrationReview.approve.unverified': '未验证',
  'registrationReview.approve.rolesLabel': '授予角色',
  'registrationReview.approve.rolesPlaceholder': '选择要授予的角色（可多选，也可不选）',
  'registrationReview.approve.rolesEmptyHint': '不选择角色时，该账号登录后只能访问公开内容（仅默认 ROLE_USER，没有任何业务权限）。',
  'registrationReview.approve.rolesLoadFailed': '角色列表加载失败，暂时无法审批',
  'registrationReview.approve.rolesSystemTag': '系统内置',
  'registrationReview.approve.submit': '通过并创建账号',
  'registrationReview.approve.successWithRoles': '已通过「{username}」，授予 {count} 个角色',
  'registrationReview.approve.successNoRoles': '已通过「{username}」，未授予任何角色（仅默认 ROLE_USER）',
  'registrationReview.approve.err400': '角色无效：{message}',
  'registrationReview.approve.err403': '无权审批（仅管理员 / 项目负责人；模拟视角下禁止）',
  'registrationReview.approve.err409': '该申请已被处理（可能刚被其他人审批），请刷新后重试',
  'registrationReview.approve.err5xx': '服务端错误（HTTP {status}），审批未完成',
  'registrationReview.approve.errGeneric': '审批失败：{message}',

  'registrationReview.reject.title': '拒绝注册申请',
  'registrationReview.reject.noteLabel': '拒绝原因',
  'registrationReview.reject.notePlaceholder': '例如：手机号与工号不匹配（会写入审计与被拒记录）',
  'registrationReview.reject.noteRequired': '请填写拒绝原因',
  'registrationReview.reject.noteTooLong': '拒绝原因不超过 {max} 字',
  'registrationReview.reject.hint': '拒绝后会释放该用户名与手机号：申请人可以用同一手机号重新提交申请。',
  'registrationReview.reject.submit': '确认拒绝',
  'registrationReview.reject.success': '已拒绝「{username}」，唯一键已释放，可重新申请',
  'registrationReview.reject.err403': '无权拒绝（仅管理员 / 项目负责人）',
  'registrationReview.reject.err409': '该申请已被处理，请刷新后重试',
  'registrationReview.reject.errGeneric': '拒绝失败：{message}',

  'registrationReview.purge.title': '彻底删除该注册申请？',
  'registrationReview.purge.content':
    '将删除申请记录与对应的占位用户（并释放用户名/手机号）。已通过的申请不能删除（会返回 409）。此操作不可恢复。',
  'registrationReview.purge.ok': '删除',
  'registrationReview.purge.success': '已彻底删除该申请',
  'registrationReview.purge.err409': '已通过的申请不能删除',
  'registrationReview.purge.err403': '仅管理员可彻底删除',
  'registrationReview.purge.errGeneric': '删除失败：{message}',
} as const;

export const registrationReviewEn: Record<keyof typeof registrationReviewZh, string> = {
  'registrationReview.menu': 'Registration review',
  'registrationReview.title': 'Registration review',
  'registrationReview.subtitle':
    'Handle accounts requested via self-registration or third-party sign-in. Approving requires choosing roles explicitly.',
  'registrationReview.refresh': 'Refresh',

  'registrationReview.status.pending': 'Pending',
  'registrationReview.status.approved': 'Approved',
  'registrationReview.status.rejected': 'Rejected',
  'registrationReview.status.unknown': 'Unknown',

  'registrationReview.filter.status': 'Status',
  'registrationReview.filter.statusAll': 'All statuses',
  'registrationReview.filter.keyword': 'Keyword',
  'registrationReview.filter.keywordPlaceholder': 'Username / phone / email',
  'registrationReview.keywordHint':
    'The list endpoint has no keyword parameter — this only filters the currently loaded page.',

  'registrationReview.col.createdAt': 'Submitted at',
  'registrationReview.col.username': 'Username',
  'registrationReview.col.phone': 'Phone',
  'registrationReview.col.email': 'Email',
  'registrationReview.col.status': 'Status',
  'registrationReview.col.source': 'Source',
  'registrationReview.col.reviewer': 'Reviewer',
  'registrationReview.col.reviewedAt': 'Reviewed at',
  'registrationReview.col.actions': 'Actions',

  'registrationReview.showFull': 'Show full',
  'registrationReview.hideFull': 'Hide full',
  'registrationReview.fullValueAdminOnly': 'Only administrators can reveal the full phone / email',
  'registrationReview.emptyPlaceholder': '—',
  'registrationReview.reviewerUnknown': '(unknown, maybe purged)',

  'registrationReview.source.local': 'Self registration',
  'registrationReview.source.sso': 'Third-party sign-in ({provider})',
  'registrationReview.provider.github': 'GitHub',
  'registrationReview.provider.microsoft': 'Microsoft',
  'registrationReview.provider.google': 'Google',
  'registrationReview.provider.wechat': 'WeChat',

  'registrationReview.action.approve': 'Approve',
  'registrationReview.action.reject': 'Reject',
  'registrationReview.action.purge': 'Delete permanently',

  'registrationReview.empty.title': 'No registration requests match the filters',
  'registrationReview.empty.desc': 'Change the status filter or clear the keyword and try again',
  'registrationReview.loadFailed': 'Failed to load registration requests',
  'registrationReview.forbidden.title': 'No access to registration review',
  'registrationReview.forbidden.desc':
    'Registration review is available to administrators (admin) and project leads (project_leader) only.',

  'registrationReview.approve.title': 'Approve registration request',
  'registrationReview.approve.detailTitle': 'Request details',
  'registrationReview.approve.field.username': 'Username',
  'registrationReview.approve.field.phone': 'Phone',
  'registrationReview.approve.field.email': 'Email',
  'registrationReview.approve.field.createdAt': 'Submitted at',
  'registrationReview.approve.verified': 'Verified',
  'registrationReview.approve.unverified': 'Not verified',
  'registrationReview.approve.rolesLabel': 'Grant roles',
  'registrationReview.approve.rolesPlaceholder': 'Select roles to grant (multiple allowed, none required)',
  'registrationReview.approve.rolesEmptyHint':
    'If you grant no roles, the account can only access public content after signing in (default ROLE_USER only, no business permissions).',
  'registrationReview.approve.rolesLoadFailed': 'Failed to load roles, approval is unavailable',
  'registrationReview.approve.rolesSystemTag': 'Built-in',
  'registrationReview.approve.submit': 'Approve and create account',
  'registrationReview.approve.successWithRoles': 'Approved "{username}" with {count} role(s)',
  'registrationReview.approve.successNoRoles': 'Approved "{username}" without any role (default ROLE_USER only)',
  'registrationReview.approve.err400': 'Invalid role: {message}',
  'registrationReview.approve.err403': 'Not allowed to approve (admin / project lead only; blocked while impersonating)',
  'registrationReview.approve.err409': 'This request was already handled, please refresh and retry',
  'registrationReview.approve.err5xx': 'Server error (HTTP {status}); approval not completed',
  'registrationReview.approve.errGeneric': 'Approval failed: {message}',

  'registrationReview.reject.title': 'Reject registration request',
  'registrationReview.reject.noteLabel': 'Reason',
  'registrationReview.reject.notePlaceholder': 'For example: phone number does not match the employee record',
  'registrationReview.reject.noteRequired': 'Please provide a reason',
  'registrationReview.reject.noteTooLong': 'Reason must be at most {max} characters',
  'registrationReview.reject.hint':
    'Rejecting releases the username and phone number, so the applicant can submit a new request with the same phone number.',
  'registrationReview.reject.submit': 'Confirm rejection',
  'registrationReview.reject.success': 'Rejected "{username}"; the unique keys were released and re-application is possible',
  'registrationReview.reject.err403': 'Not allowed to reject (admin / project lead only)',
  'registrationReview.reject.err409': 'This request was already handled, please refresh and retry',
  'registrationReview.reject.errGeneric': 'Rejection failed: {message}',

  'registrationReview.purge.title': 'Permanently delete this registration request?',
  'registrationReview.purge.content':
    'This deletes the request and its placeholder user (releasing the username / phone number). Approved requests cannot be deleted (409). This cannot be undone.',
  'registrationReview.purge.ok': 'Delete',
  'registrationReview.purge.success': 'The registration request was permanently deleted',
  'registrationReview.purge.err409': 'Approved requests cannot be deleted',
  'registrationReview.purge.err403': 'Only administrators can delete permanently',
  'registrationReview.purge.errGeneric': 'Delete failed: {message}',
};

