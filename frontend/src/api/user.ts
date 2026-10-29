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
  });
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
