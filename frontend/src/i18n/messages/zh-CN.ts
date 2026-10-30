/**
 * 中文文案（默认语言）。
 *
 * 约定：key 用「域.语义」两级命名（menu.dashboard / notifications.markAllRead）。
 * 新增文案时先加这里，再补 en-US.ts —— en-US 用 `Record<MessageKey, string>` 约束，
 * 缺键会在 `tsc` 阶段报错，避免中英不同步。
 */
export const zhCN = {
  /* ==================== 通用 ==================== */
  'common.appName': '漏洞审计生命周期管理系统',
  'common.loading': '加载中…',
  'common.empty': '暂无数据',
  'common.refresh': '刷新',
  'common.search': '查询',
  'common.reset': '重置',
  'common.confirm': '确定',
  'common.cancel': '取消',
  'common.delete': '删除',
  'common.edit': '编辑',
  'common.save': '保存',
  'common.close': '关闭',
  'common.back': '返回',
  'common.total': '共 {count} 条',
  'common.deleteConfirmTitle': '确认删除',
  'common.deleteConfirmContent': '删除后不可恢复，确定继续？',
  'common.language': '语言',
  'common.langZh': '中文',
  'common.langEn': 'English',
  'common.operationFailed': '操作失败，请稍后重试',

  /* ==================== 403 / 404 ==================== */
  'error.forbiddenTitle': '无权限访问',
  'error.forbiddenDesc': '当前账号没有该页面的访问权限，如需访问请联系管理员开通。',
  'error.notFoundTitle': '页面不存在',
  'error.notFoundDesc': '你访问的地址不存在，可能已被移除或输错。',
  'error.backHome': '返回大盘',

  /* ==================== 登录页 ==================== */
  'login.title': '漏洞审计生命周期管理系统',
  'login.username': '用户名',
  'login.password': '密码',
  'login.usernameRequired': '请输入用户名',
  'login.passwordRequired': '请输入密码',
  'login.submit': '登录',
  'login.success': '登录成功',

  /* ==================== 侧边栏菜单 / 页头 ==================== */
  'menu.groupCore': '核心业务',
  'menu.groupKnowledge': '知识与资源',
  'menu.groupSystem': '系统与管理',
  'menu.dashboard': '安全态势大盘',
  'menu.assets': '产品与资产中心',
  'menu.assetGroups': '资产分组',
  'menu.vulnerabilities': '全局漏洞视图',
  'menu.tasks': '安全审计任务',
  'menu.approvals': '审批中心',
  'menu.knowledge': '安全知识库',
  'menu.documents': '规范与文档',
  'menu.tools': '工具与组件库',
  'menu.teams': '团队架构',
  'menu.approvalScopes': '审批范围',
  'menu.users': '账号管理',
  'menu.roles': '角色与权限',
  'menu.auditLogs': '审计日志',
  'menu.import': '数据导入',
  'menu.notifications': '系统消息',
  'header.userMenu': '账号',
  'header.logout': '退出登录',
  'header.unreadNotifications': '{count} 条未读消息',

  /* ==================== 通知中心 ==================== */
  'notifications.title': '通知中心',
  'notifications.tabInbox': '站内信',
  'notifications.tabAnnouncements': '公告区',
  'notifications.tabSettings': '通知设置',
  'notifications.markAllRead': '全部标为已读',
  'notifications.markRead': '标为已读',
  'notifications.refresh': '刷新',
  'notifications.emptyInbox': '暂无消息：任务分配、漏洞流转、审批与公告提醒会集中出现在这里',
  'notifications.emptyUnread': '没有未读消息，说明动态都已处理完毕',
  'notifications.announcements.title': '生效中的公告',
  'notifications.announcements.all': '全部公告',
  'notifications.announcements.publish': '发布公告',
  'notifications.announcements.readonly': '只读视图：公告的创建 / 编辑 / 删除仅管理员与项目负责人可用',
  'notifications.settings.reload': '重新加载',
  'notifications.settings.title': '按通知类型配置渠道',

  /* ==================== 仪表盘 ==================== */
  'dashboard.title': '安全态势大盘',
  'dashboard.export': '导出报表',
  'dashboard.exportAssets': '导出资产清单 CSV',
  'dashboard.exportVulnerabilities': '导出漏洞清单 CSV',
  'dashboard.exportWorkload': '导出工作量 CSV',
  'dashboard.layout': '布局设置',
  'dashboard.refresh': '刷新',
  'dashboard.cardAssets': '资产总数',
  'dashboard.cardVulnerabilities': '漏洞总数',
  'dashboard.cardMyTasks': '我的任务',
  'dashboard.cardMyClaims': '我的资产认领',
  'dashboard.cardTrends': '趋势折线',
  'dashboard.cardAssetDistribution': '资产分布（类型 / 优先级）',
  'dashboard.cardVulnDistribution': '漏洞分布（严重程度 / 工作流状态）',
  'dashboard.cardWorkloadRank': '工作量排行',
  'dashboard.scope.full': '全量',
  'dashboard.scope.scoped': '当前可见范围',
  'dashboard.scope.scopedTeam': '当前可见范围（本团队）',
  'dashboard.vulnForbiddenTitle': '漏洞统计仅管理层可见',
  'dashboard.vulnForbiddenDesc': '漏洞统计仅管理层（管理员 / 项目负责人 / 小组长）可见，当前角色：{role}。',
  'dashboard.vulnUnavailableTitle': '漏洞统计暂不可用',
  'dashboard.layoutModalTitle': '自定义大盘布局',
  'dashboard.layoutSave': '保存布局',
  'dashboard.layoutReset': '恢复默认',
} as const;

export type MessageKey = keyof typeof zhCN;
export type MessageCatalog = Record<MessageKey, string>;
