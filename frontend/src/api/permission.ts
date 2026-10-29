import request from '@/utils/request';

/**
 * 当前账号的权限画像。
 *
 * 后端：GET /api/v1/users/me/permissions
 * 实测响应：{ username, userId, roles: ["admin"], permissions: [] }
 * - roles 是角色 code 列表（admin / project_leader / team_leader / vulnerability_engineer）；
 * - permissions 是 RBAC 权限点列表，权限目录未初始化时为空数组。
 */
export interface MyAccessProfile {
  userId?: string;
  username?: string;
  roles: string[];
  permissions: string[];
}

export const getMyAccessProfile = async (): Promise<MyAccessProfile> => {
  try {
    const res: any = await request.get('/users/me/permissions');
    const payload = res?.data ?? res ?? {};
    const roles = Array.isArray(payload.roles) ? payload.roles.map((item: unknown) => String(item)) : [];
    const permissions = Array.isArray(payload.permissions)
      ? payload.permissions.map((item: unknown) => String(item))
      : [];
    return { userId: payload.userId, username: payload.username, roles, permissions };
  } catch {
    return { roles: [], permissions: [] };
  }
};

/** 公告管理权限：后端权限点 notification:announcement:write 仅授予 admin */
export const canManageAnnouncements = (profile: MyAccessProfile): boolean =>
  profile.permissions.includes('notification:announcement:write') || profile.roles.includes('admin');

/**
 * 管理层角色（与后端 @PreAuthorize hasAnyRole('ADMIN','PROJECT_LEADER') 对齐）。
 *
 * 用于审计日志等「仅管理层」入口：菜单可见性 + 页面级 403 兜底。
 * 权限点 audit:read / audit:export 默认也只授予这两个角色，故二者任一满足即可。
 */
export const MANAGEMENT_ROLES = ['admin', 'project_leader'] as const;

export const isManagementProfile = (profile: MyAccessProfile): boolean =>
  profile.roles.some((role) => (MANAGEMENT_ROLES as readonly string[]).includes(role)) ||
  profile.permissions.includes('audit:read') ||
  profile.permissions.includes('audit:export');

/** 是否具备审计日志读权限（列表 + 导出） */
export const canReadAuditLogs = (profile: MyAccessProfile): boolean =>
  isManagementProfile(profile);

