import request from '@/utils/request';
import type {
  RoleVO,
  PermissionVO,
  CreateRoleRequest,
  UpdateRoleRequest,
} from '@/types/role';

/**
 * 角色列表。
 *
 * `includePermissions=true` 时后端会为每个角色附带 permissions 数组（勾选回显用）；
 * 兼容旧参数名 `include_permissions`。
 */
export const getRoleList = (params?: { includePermissions?: boolean; include_permissions?: boolean }): Promise<any> => {
  const includePermissions = params?.includePermissions ?? params?.include_permissions;
  return request.get('/roles', {
    params: includePermissions === undefined ? undefined : { includePermissions },
  });
};

/** 角色详情：GET /roles/{id} */
export const getRoleDetail = (id: string): Promise<RoleVO> => {
  return request.get(`/roles/${id}`);
};

/**
 * 创建角色：POST /roles
 *
 * 请求体带 permissionIds 时后端一并写入 role_permissions（避免连调两个接口）。
 */
export const createRole = (data: CreateRoleRequest): Promise<RoleVO> => {
  return request.post('/roles', data);
};

/**
 * 更新角色：PUT /roles/{id}
 *
 * 提交 permissionIds 时后端按目标集合**覆盖式同步**（缺则补、多则删，幂等）。
 */
export const updateRole = (id: string, data: UpdateRoleRequest): Promise<RoleVO> => {
  return request.put(`/roles/${id}`, data);
};

/** 删除角色：DELETE /roles/{id}（系统内置角色后端会拒绝） */
export const deleteRole = (id: string): Promise<any> => {
  return request.delete(`/roles/${id}`);
};

/** 角色已分配的权限点：GET /roles/{id}/permissions */
export const getRolePermissions = (id: string): Promise<PermissionVO[]> => {
  return request.get(`/roles/${id}/permissions`);
};

/** 为角色追加权限点：POST /roles/{id}/permissions（body 为权限ID数组，已分配自动跳过） */
export const assignRolePermissions = (id: string, permissionIds: string[]): Promise<any> => {
  return request.post(`/roles/${id}/permissions`, permissionIds);
};

/** 移除角色权限点：DELETE /roles/{id}/permissions（body 为权限ID数组，幂等） */
export const removeRolePermissions = (id: string, permissionIds: string[]): Promise<any> => {
  return request.delete(`/roles/${id}/permissions`, { data: permissionIds });
};

/**
 * 权限点清单：GET /permissions
 *
 * 后端返回 ApiResponse<List<Permission>>（**不分页**，共 90 个权限点）。
 * 响应字段为 camelCase：apiPath / httpMethod / createdAt。
 */
export const getPermissionList = (): Promise<any> => {
  return request.get('/permissions');
};

/** 把权限响应统一解包成数组（兼容 ApiResponse / 裸数组 / 旧分页结构） */
export const extractPermissionList = (res: any): PermissionVO[] => {
  const payload = res?.data ?? res;
  if (Array.isArray(payload)) return payload as PermissionVO[];
  const items = payload?.content ?? payload?.items;
  return Array.isArray(items) ? (items as PermissionVO[]) : [];
};

/** 把角色响应统一解包成数组 */
export const extractRoleList = (res: any): RoleVO[] => {
  const payload = res?.data ?? res;
  if (Array.isArray(payload)) return payload as RoleVO[];
  const items = payload?.content ?? payload?.items;
  return Array.isArray(items) ? (items as RoleVO[]) : [];
};
