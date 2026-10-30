import type { MyAccessProfile } from '@/api/permission';
import type { TeamVO } from '@/types/team';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';

/**
 * 团队页的写权限口径（与后端 t40 的守卫严格对齐）：
 *
 * - `POST /teams`：`hasAnyRole('ADMIN','PROJECT_LEADER')` → 只有 admin / project_leader 能建团队/子团队；
 * - `PUT /teams/{id}`：`hasAnyRole(ADMIN,PL) or canManageTeam` → 团队长可改「自己领导」的团队；
 * - `DELETE /teams/{id}`：`hasAnyRole(ADMIN,PL) and canManageTeam` → 团队长不能删（and 语义）；
 * - `POST/DELETE /teams/{id}/members`：`hasAnyRole(ADMIN,PL) or canManageTeam` → 团队长可管理自己团队的成员；
 * - 所有查询端点保持开放，工程师/团队长都必须能看树与成员。
 *
 * 判定只用角色 + `teams.leaderId`（不新增接口）。注意不要用 `team:update` 权限点判定：
 * 后端 PUT 走的是角色/归属判断，smoke_leader 这类账号的权限点里并没有 team:update。
 */

export const TEAM_ADMIN_ROLES = ['admin', 'project_leader'] as const;

/** 角色码归一：去 ROLE_ 前缀、转小写（兼容 ROLE_TEAM_LEADER / Project_Leader 等写法） */
export const normalizeRole = (role?: string | null): string =>
  String(role ?? '')
    .trim()
    .replace(/^ROLE_/i, '')
    .toLowerCase();

export const normalizeRoles = (roles?: Array<string | null | undefined>): string[] =>
  Array.from(new Set((roles ?? []).map(normalizeRole).filter(Boolean)));

export interface TeamWriteAccess {
  roles: string[];
  currentUserId?: string;
  /** 是否为全局管理者（admin / project_leader） */
  isGlobalManager: boolean;
  /** 是否具备任一团队负责人身份（团队长且确有团队 leaderId 指向自己） */
  isAnyTeamLeader: boolean;
  /** 可创建团队 / 子团队 */
  canCreateTeam: boolean;
  /** 可编辑指定团队 */
  canEditTeam: (team?: TeamVO | null) => boolean;
  /** 可删除指定团队（后端 and 语义：仅 admin / project_leader） */
  canDeleteTeam: (team?: TeamVO | null) => boolean;
  /** 可管理指定团队成员（增删） */
  canManageMembers: (team?: TeamVO | null) => boolean;
}

const leaderIdOf = (team?: TeamVO | null): string | undefined =>
  team ? (team.leaderId ?? team.leader_id ?? undefined) : undefined;

export const resolveTeamWriteAccess = (
  profile: Pick<MyAccessProfile, 'roles' | 'userId'> | null | undefined,
  teams: TeamVO[],
  fallback?: { userId?: string; roles?: Array<string | null | undefined> },
): TeamWriteAccess => {
  const roles = normalizeRoles(
    profile?.roles && profile.roles.length > 0 ? profile.roles : fallback?.roles ?? [],
  );
  const currentUserId = profile?.userId ?? fallback?.userId;
  const isGlobalManager = roles.some((role) => (TEAM_ADMIN_ROLES as readonly string[]).includes(role));
  const isAnyTeamLeader = Boolean(currentUserId) && teams.some((team) => leaderIdOf(team) === currentUserId);

  const isLeaderOf = (team?: TeamVO | null) => Boolean(currentUserId) && leaderIdOf(team) === currentUserId;

  return {
    roles,
    currentUserId,
    isGlobalManager,
    isAnyTeamLeader,
    canCreateTeam: isGlobalManager,
    canEditTeam: (team) => isGlobalManager || isLeaderOf(team),
    canDeleteTeam: () => isGlobalManager,
    canManageMembers: (team) => isGlobalManager || isLeaderOf(team),
  };
};

/** 团队写操作 403 的可读原因（t42 要求：不得泛化成「操作失败」） */
export const TEAM_FORBIDDEN_MESSAGE = '无权管理该团队（需为管理员/项目负责人或该团队负责人）';

/**
 * 团队写操作失败文案：
 * - 403 → 明确的权限原因；
 * - 404 → 团队不存在（可能已被删除）；
 * - 400/409 → 后端校验原文（如「团队名称已存在」）；
 * - 其他 → 后端 message 或兜底文案。
 */
export const describeTeamWriteFailure = (error: unknown, fallback = '团队操作失败'): string => {
  const status = getApiErrorStatus(error);
  if (status === 403) {
    return TEAM_FORBIDDEN_MESSAGE;
  }
  if (status === 404) {
    return '团队不存在或已被删除，请刷新后重试';
  }
  if (status === 409) {
    return getApiErrorMessage(error, '团队名称已存在，请换一个名称');
  }
  return getApiErrorMessage(error, fallback);
};

/** 是否为表单校验失败（antd validateFields 抛出，无需再弹接口错误提示） */
export const isFormValidationError = (error: unknown): boolean =>
  Boolean((error as { errorFields?: unknown } | undefined)?.errorFields);
