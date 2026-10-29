import type { PaginatedResponse } from './common';
import type { RoleVO } from './role';

/**
 * 用户视图对象。
 *
 * 后端（Spring Boot + Jackson 默认命名策略）返回 **camelCase** 字段：
 * `fullName` / `createdAt` / `lastLogin` / `active` / `suspended` / `deactivated`。
 * 历史上前端按 snake_case 读取（`full_name` / `created_at` …），拿到的一直是 undefined，
 * 因此这里以 camelCase 为准，snake_case 仅作为过渡期兼容别名保留（可选、不参与渲染）。
 */
export interface UserVO {
  id: string;
  username: string;
  email: string;
  phone?: string;
  fullName?: string;
  avatar?: string;
  position?: string;
  /** 用户状态：active / suspended / deactivated */
  status: string;
  active?: boolean;
  suspended?: boolean;
  deactivated?: boolean;
  /** 主角色编码（历史字段，后端 User 实体已不再返回，仅兼容旧数据） */
  role?: string;
  teams?: any[];
  createdAt?: string;
  updatedAt?: string;
  lastLogin?: string;
  /** GET /users/{id}/roles 返回的角色列表（camelCase 字段见 RoleVO） */
  roles?: RoleVO[];

  /** @deprecated 后端已不使用；仅兼容旧页面读取，勿在新代码使用 */
  full_name?: string;
  /** @deprecated 后端已不使用 */
  created_at?: string;
  /** @deprecated 后端已不使用 */
  last_login?: string;
}

export type UserListResponse = PaginatedResponse<UserVO>;

export interface CreateUserRequest {
  username: string;
  email: string;
  phone?: string;
  password?: string;
  fullName: string;
  position?: string;
}

export interface UpdateUserRequest {
  email?: string;
  phone?: string;
  fullName?: string;
  avatar?: string;
  position?: string;
}

/** 用户状态取值（PATCH /users/{id}/status?status=…） */
export type UserStatus = 'active' | 'suspended' | 'deactivated';

/** 新建用户时后端返回的 User 实体（passwordHash 会被置空） */
export type CreatedUser = UserVO;
