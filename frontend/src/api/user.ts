import request from '@/utils/request';
import type {
  UserVO,
  CreateUserRequest,
  UpdateUserRequest,
  UserStatus,
} from '@/types/user';
import type { RoleVO } from '@/types/role';
import type { PaginationParams } from '@/types/common';

/** 分页查询用户（后端参数：page 从 0 开始 / size / keyword / status） */
export const getUserList = (params: PaginationParams & { role?: string, team_id?: string, status?: string }): Promise<any> => {
  return request.get('/users', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
      keyword: params.search,
      status: params.status,
      role: params.role,
      teamId: params.team_id,
    },
    // 账号管理页（admin/project_leader）使用；其他页面请用 getUserDirectory
  });
};

// ==================== 用户目录（最小暴露，t45/t48） ====================

/**
 * 用户目录条目：后端 `UserDirectoryEntry`。
 *
 * 只有 id / username / fullName —— 不含 email、phone、status、roles、lastLoginAt。
 * 页面展示必须只用这三个字段（不要再读 email/phone 做 tooltip）。
 */
export interface UserDirectoryEntry {
  id: string;
  username: string;
  /** 显示名，可能为空（回落到 username） */
  fullName?: string;
}

/** 目录默认返回上限（与后端一致）；达到上限说明可能被截断，应提示用户输入关键字 */
export const USER_DIRECTORY_DEFAULT_LIMIT = 200;

/** 达到上限时的提示文案 */
export const USER_DIRECTORY_LIMIT_HINT = '仅显示前 200 人，输入关键字继续搜索';

/**
 * 查询用户目录：`GET /users/directory`
 *
 * - 仅需登录（工程师/团队长都可用），是人员下拉、审批人展示、作者名等场景的**唯一数据源**；
 * - `keyword` 走后端参数（匹配 username/fullName），不要前端全量拉取再过滤；
 * - `limit` 默认 200、后端上限 500；
 * - silent：调用方自己决定失败时的降级（下拉为空、作者名回落 ID），避免满屏报错。
 */
export const getUserDirectory = async (params?: {
  keyword?: string;
  limit?: number;
}): Promise<UserDirectoryEntry[]> => {
  const res: any = await request.get('/users/directory', {
    params: {
      keyword: params?.keyword?.trim() ? params.keyword.trim() : undefined,
      limit: params?.limit ?? USER_DIRECTORY_DEFAULT_LIMIT,
    },
    silent: true,
  });
  return extractUserDirectory(res);
};

/** 解包目录响应（ApiResponse<List> / 裸数组 都兼容） */
export const extractUserDirectory = (res: unknown): UserDirectoryEntry[] => {
  const payload = (res as any)?.data ?? res;
  const list = Array.isArray(payload) ? payload : Array.isArray(payload?.content) ? payload.content : [];
  return list
    .map((item: any) => ({
      id: String(item?.id ?? ''),
      username: String(item?.username ?? ''),
      fullName: item?.fullName ?? item?.full_name ?? undefined,
    }))
    .filter((item: UserDirectoryEntry) => Boolean(item.id));
};

/** 目录条目的展示名：fullName 优先，回落 username */
export const userDirectoryLabel = (user?: UserDirectoryEntry | null): string =>
  user?.fullName || user?.username || '';

/** 按 id 合并目录条目（下拉远程搜索时保留已选项，避免已选人员显示成 ID） */
export const mergeUserDirectory = (
  current: UserDirectoryEntry[],
  incoming: UserDirectoryEntry[],
): UserDirectoryEntry[] => {
  const map = new Map<string, UserDirectoryEntry>();
  current.forEach((item) => map.set(item.id, item));
  incoming.forEach((item) => map.set(item.id, { ...map.get(item.id), ...item }));
  return Array.from(map.values());
};

export const getUserDetail = (id: string): Promise<UserVO> => {
  return request.get(`/users/${id}`);
};

export const createUser = (data: CreateUserRequest): Promise<UserVO> => {
  return request.post('/users', data);
};

export const updateUser = (id: string, data: UpdateUserRequest): Promise<UserVO> => {
  return request.put(`/users/${id}`, data);
};

export const deleteUser = (id: string): Promise<any> => {
  return request.delete(`/users/${id}`);
};

// ==================== 用户状态 / 密码 ====================

/**
 * 启用/停用/注销用户：PATCH /users/{id}/status?status=active|suspended|deactivated
 *
 * 状态通过 query 参数传递（后端 @RequestParam String status），不是请求体。
 */
export const updateUserStatus = (id: string, status: UserStatus): Promise<any> => {
  return request.patch(`/users/${id}/status`, null, { params: { status } });
};

/**
 * 管理员重置密码：POST /users/{id}/reset-password?newPassword=***
 *
 * 同样走 query 参数（后端 @RequestParam String newPassword）。
 */
export const resetUserPassword = (id: string, newPassword: string): Promise<any> => {
  return request.post(`/users/${id}/reset-password`, null, { params: { newPassword } });
};

// ==================== 用户角色分配 ====================

/** 查询用户已分配的角色：GET /users/{id}/roles */
export const getUserRoles = (id: string): Promise<RoleVO[]> => {
  return request.get(`/users/${id}/roles`);
};

/**
 * 为用户分配角色：POST /users/{id}/roles?roleId=***
 *
 * 幂等：重复分配返回 200 与现有关联（不会重复插入）；
 * 用户或角色不存在返回 404。
 */
export const assignUserRole = (id: string, roleId: string): Promise<any> => {
  return request.post(`/users/${id}/roles`, null, { params: { roleId } });
};

/** 撤销用户角色：DELETE /users/{id}/roles/{roleId}（未分配同样返回 200，幂等） */
export const revokeUserRole = (id: string, roleId: string): Promise<any> => {
  return request.delete(`/users/${id}/roles/${roleId}`);
};
