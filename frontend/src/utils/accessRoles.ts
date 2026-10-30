/**
 * 角色 → 能力判定（纯函数，无运行时依赖，便于单测与复用）。
 *
 * 与后端 @PreAuthorize 口径对齐（t40 起）：
 * - 公告写 POST/PUT/DELETE /notifications/announcements**：ADMIN / PROJECT_LEADER
 * - 环境写 POST/PUT/DELETE /environments**、/deploy|/start|/stop、/snapshots**：ADMIN / PROJECT_LEADER / TEAM_LEADER
 * - 审计日志读 / 报表导出：ADMIN / PROJECT_LEADER
 * 查询接口（团队/环境/公告列表）保持对全部登录角色开放，前端不据此隐藏只读入口。
 */

export interface MyAccessProfile {
  userId?: string;
  username?: string;
  roles: string[];
  permissions: string[];
}

const includesRole = (profile: MyAccessProfile | null, roles: readonly string[]): boolean =>
  Boolean(profile?.roles?.some((role) => roles.includes(role)));

/* ==================== 管理层 ==================== */

export const MANAGEMENT_ROLES = ['admin', 'project_leader'] as const;

export const isManagementProfile = (profile: MyAccessProfile | null): boolean =>
  includesRole(profile, MANAGEMENT_ROLES) ||
  Boolean(
    profile?.permissions?.includes('audit:read') || profile?.permissions?.includes('audit:export')
  );

export const canReadAuditLogs = (profile: MyAccessProfile | null): boolean => isManagementProfile(profile);

/* ==================== 公告 ==================== */

export const ANNOUNCEMENT_WRITE_ROLES = ['admin', 'project_leader'] as const;

/** 公告写权限：角色命中优先；权限点作为 RBAC 目录已初始化时的补充 */
export const canManageAnnouncements = (profile: MyAccessProfile | null): boolean =>
  includesRole(profile, ANNOUNCEMENT_WRITE_ROLES) ||
  Boolean(profile?.permissions?.includes('notification:announcement:write'));

export const ANNOUNCEMENT_FORBIDDEN_MESSAGE = '当前角色无权管理公告（仅管理员 / 项目负责人可写）';
export const ANNOUNCEMENT_READONLY_HINT = '只读视图：公告的创建 / 编辑 / 删除仅管理员与项目负责人可用';

/* ==================== 环境 ==================== */

export const ENVIRONMENT_WRITE_ROLES = ['admin', 'project_leader', 'team_leader'] as const;

export const canWriteEnvironment = (profile: MyAccessProfile | null): boolean =>
  includesRole(profile, ENVIRONMENT_WRITE_ROLES);

export const ENVIRONMENT_FORBIDDEN_MESSAGE =
  '当前角色无权写入环境（仅管理员 / 项目负责人 / 小组长可写）';
export const ENVIRONMENT_READONLY_HINT = '当前角色仅可查看环境与快照';

/* ==================== 报表导出 ==================== */

/** GET /dashboard/export 仅 admin / project_leader（团队长也不可见） */
export const canExportReports = (profile: MyAccessProfile | null): boolean =>
  includesRole(profile, MANAGEMENT_ROLES);

/* ==================== 统计口径 ==================== */

export type StatsScopeTone = 'full' | 'scoped' | 'unknown';

export interface StatsScopeInfo {
  tone: StatsScopeTone;
  label: string;
  canViewVulnStats: boolean;
  roleLabel: string;
}

const ROLE_LABELS: Record<string, string> = {
  admin: '系统管理员',
  project_leader: '项目负责人',
  team_leader: '小组长',
  vulnerability_engineer: '漏洞挖掘工程师',
};

export const resolveStatsScope = (profile: MyAccessProfile | null): StatsScopeInfo => {
  const roles = profile?.roles ?? [];
  const roleLabel = roles.length ? roles.map((role) => ROLE_LABELS[role] ?? role).join('、') : '未识别角色';

  if (includesRole(profile, MANAGEMENT_ROLES)) {
    return { tone: 'full', label: '全量', canViewVulnStats: true, roleLabel };
  }
  if (roles.includes('team_leader')) {
    return { tone: 'scoped', label: '当前可见范围（本团队）', canViewVulnStats: true, roleLabel };
  }
  if (roles.length === 0) {
    // 画像未返回（接口失败 / 新账号无角色）→ 未知，允许发起请求，403 再兜底
    return { tone: 'unknown', label: '当前可见范围', canViewVulnStats: true, roleLabel };
  }
  return { tone: 'scoped', label: '当前可见范围', canViewVulnStats: false, roleLabel };
};

export const statsScopeTagColor = (tone: StatsScopeTone): string => (tone === 'full' ? 'blue' : 'default');
