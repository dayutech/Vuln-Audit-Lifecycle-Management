import type { PaginatedResponse } from './common';

export interface UserVO {
  id: string;
  username: string;
  email: string;
  phone?: string;
  full_name: string;
  role: string;
  status: string;
  teams?: any[];
  created_at: string;
  last_login?: string;
}

export type UserListResponse = PaginatedResponse<UserVO>;

export interface CreateUserRequest {
  username: string;
  email: string;
  phone?: string;
  password?: string;
  full_name: string;
  role: string;
}

export interface UpdateUserRequest {
  full_name?: string;
  email?: string;
  role?: string;
}
