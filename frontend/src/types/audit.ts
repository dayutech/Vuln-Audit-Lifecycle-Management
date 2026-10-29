/**
 * 审计日志类型。
 *
 * 契约来源：backend AuditLogController（/api/v1/audit-logs），实测 2026-10-29：
 * - GET /audit-logs?page=0&size=N&userId&action&resourceType&startTime&endTime&keyword
 *   返回 PageResponse<AuditLog>（camelCase：userId/action/resourceType/resourceId/ipAddress/userAgent/createdAt）；
 * - startTime/endTime 为 ISO-8601 闭区间；keyword 对 action/resourceType/details 模糊匹配；
 * - GET /audit-logs/export 返回 text/csv（UTF-8 BOM），参数与列表一致，仅 admin/project_leader；
 * - 类级 @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")：工程师访问列表 403「无权限访问该资源」。
 */

export interface AuditLogVO {
  id: string;
  userId: string;
  action: string;
  resourceType: string;
  resourceId?: string | null;
  details?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
  createdAt?: string;
}

export interface AuditLogPage {
  content: AuditLogVO[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

export interface AuditLogQuery {
  /** 1 基页码，API 层转 0 基 */
  page?: number;
  page_size?: number;
  user_id?: string;
  action?: string;
  resource_type?: string;
  /** ISO-8601，如 2026-10-29T00:00:00 */
  start_time?: string;
  end_time?: string;
  keyword?: string;
}

/** 常见的资源类型（用于筛选下拉；后端实际值随拦截器记录而定，可搜索清空） */
export const AUDIT_RESOURCE_TYPE_OPTIONS: { label: string; value: string }[] = [
  { label: '资产 assets', value: 'assets' },
  { label: '漏洞 vulnerabilities', value: 'vulnerabilities' },
  { label: '任务 tasks', value: 'tasks' },
  { label: '团队 teams', value: 'teams' },
  { label: '用户 users', value: 'users' },
  { label: '角色 roles', value: 'roles' },
  { label: '权限点 permissions', value: 'permissions' },
  { label: '文档 documents', value: 'documents' },
  { label: '工具 tools', value: 'tools' },
  { label: '知识库 knowledge', value: 'knowledge' },
  { label: '环境 environments', value: 'environments' },
  { label: '通知 notifications', value: 'notifications' },
  { label: '仪表盘 dashboard', value: 'dashboard' },
  { label: '审批 approval-scopes', value: 'approval-scopes' },
  { label: '导入 import', value: 'import' },
];

/** action 形如「DELETE TeamController.deleteTeam」，这里给出按 HTTP 动词归类的说明 */
export const AUDIT_ACTION_HINT = 'action 由「HTTP 方法 + 控制器.方法」组成，例如 DELETE TeamController.deleteTeam；等值匹配，模糊查询请用关键词';
