import type { PaginatedResponse } from './common';

export interface PermissionVO {
  id: string;
  name: string;
  code: string;
  api_path?: string;
  http_method?: string;
  description?: string;
  created_at: string;
}

export type PermissionListResponse = PaginatedResponse<PermissionVO>;

export interface RoleVO {
  id: string;
  name: string;
  code: string;
  description?: string;
  is_system: boolean;
  permissions?: PermissionVO[];
  created_at: string;
}

export type RoleListResponse = PaginatedResponse<RoleVO>;

export interface CreateRoleRequest {
  name: string;
  code: string;
  description?: string;
  permission_ids: string[];
}

export interface UpdateRoleRequest {
  name?: string;
  description?: string;
  permission_ids?: string[];
}
