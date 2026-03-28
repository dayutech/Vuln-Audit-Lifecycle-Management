import request from '@/utils/request';
import type { UserVO, CreateUserRequest, UpdateUserRequest } from '@/types/user';
import type { PaginationParams } from '@/types/common';

export const getUserList = (params: PaginationParams & { role?: string, team_id?: string }): Promise<any> => {
  return request.get('/users', { params });
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
