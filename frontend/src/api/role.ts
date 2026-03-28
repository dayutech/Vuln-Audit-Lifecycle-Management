import request from '@/utils/request';
import type { RoleVO, CreateRoleRequest, UpdateRoleRequest } from '@/types/role';

export const getRoleList = (params?: { include_permissions?: boolean }): Promise<any> => {
  return request.get('/roles', { params });
};

export const createRole = (data: CreateRoleRequest): Promise<RoleVO> => {
  return request.post('/roles', data);
};

export const updateRole = (id: string, data: UpdateRoleRequest): Promise<RoleVO> => {
  return request.put(`/roles/${id}`, data);
};

export const deleteRole = (id: string): Promise<any> => {
  return request.delete(`/roles/${id}`);
};

export const getPermissionList = (params?: { page?: number; page_size?: number; search?: string }): Promise<any> => {
  return request.get('/permissions', { params });
};
