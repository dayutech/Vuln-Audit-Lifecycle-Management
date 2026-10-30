import request from '@/utils/request';
import type { MyAccessProfile } from '@/utils/accessRoles';

/**
 * 当前账号的权限画像。
 *
 * 后端：GET /api/v1/users/me/permissions
 * 实测响应：{ username, userId, roles: ["admin"], permissions: [] }
 * - roles 是角色 code 列表（admin / project_leader / team_leader / vulnerability_engineer）；
 * - permissions 是 RBAC 权限点列表（目录初始化后 admin 为 90 个）。
 *
 * 纯判定的角色能力（公告写 / 环境写 / 审计读 / 导出 / 统计口径）已抽到
 * `@/utils/accessRoles`（无运行时依赖，便于单测），此处按原路径再导出以保持兼容。
 */
export type { MyAccessProfile } from '@/utils/accessRoles';
export {
  MANAGEMENT_ROLES,
  isManagementProfile,
  canReadAuditLogs,
  ANNOUNCEMENT_WRITE_ROLES,
  canManageAnnouncements,
  ANNOUNCEMENT_FORBIDDEN_MESSAGE,
  ANNOUNCEMENT_READONLY_HINT,
  ENVIRONMENT_WRITE_ROLES,
  canWriteEnvironment,
  ENVIRONMENT_FORBIDDEN_MESSAGE,
  ENVIRONMENT_READONLY_HINT,
  canExportReports,
  resolveStatsScope,
  statsScopeTagColor,
} from '@/utils/accessRoles';
export type { StatsScopeTone, StatsScopeInfo } from '@/utils/accessRoles';

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
