import type { PaginatedResponse } from './common';

/**
 * 权限点（`permissions` 表）。
 *
 * 后端字段为 camelCase：`apiPath` / `httpMethod` / `createdAt`。
 * 权限码格式固定为 `资源:操作`（如 `asset:read`），模块名 = 冒号前一段。
 */
export interface PermissionVO {
  id: string;
  name: string;
  code: string;
  apiPath?: string;
  httpMethod?: string;
  description?: string;
  createdAt?: string;

  /** @deprecated 后端已不使用；仅为兼容历史代码保留 */
  api_path?: string;
  /** @deprecated 后端已不使用 */
  http_method?: string;
  /** @deprecated 后端已不使用 */
  created_at?: string;
}

export type PermissionListResponse = PaginatedResponse<PermissionVO>;

/**
 * 角色（`roles` 表）。
 *
 * `permissions` 仅在 `GET /roles?includePermissions=true` 或 `GET /roles/{id}/permissions`
 * 显式要求时返回（后端用 @JsonInclude(NON_NULL) 省略 null）。
 */
export interface RoleVO {
  id: string;
  name: string;
  code: string;
  description?: string;
  /** 是否系统内置角色：内置角色不可删除、角色码只读 */
  isSystem: boolean;
  permissions?: PermissionVO[];
  createdAt?: string;
  updatedAt?: string;

  /** @deprecated 后端已不使用；仅为兼容历史代码保留 */
  is_system?: boolean;
  /** @deprecated 后端已不使用 */
  created_at?: string;
}

export type RoleListResponse = PaginatedResponse<RoleVO>;

/** 请求体字段后端同时接受 permissionIds 与 permission_ids（JsonAlias） */
export interface CreateRoleRequest {
  name: string;
  code: string;
  description?: string;
  permissionIds?: string[];
}

export interface UpdateRoleRequest {
  name?: string;
  code?: string;
  description?: string;
  /** 提交即按目标集合覆盖式同步；不提交则不改动权限关联 */
  permissionIds?: string[];
}

/** 判断角色是否系统内置（兼容旧字段） */
export const isSystemRole = (role: RoleVO): boolean => Boolean(role.isSystem ?? role.is_system);
