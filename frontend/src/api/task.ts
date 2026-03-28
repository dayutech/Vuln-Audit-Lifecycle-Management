import request from '@/utils/request';
import type { TaskListResponse, TaskVO, CreateTaskRequest, UpdateTaskStatusRequest, AssignTaskRequest } from '@/types/task';
import type { PaginationParams } from '@/types/common';

export interface TaskQueryParams extends PaginationParams {
  status?: string;
  priority?: string;
  assignee_id?: string;
  type?: string;
}

export const getTaskList = (params: TaskQueryParams): Promise<any> => {
  return request.get('/tasks', { params });
};

export const getMyTaskList = (params: TaskQueryParams): Promise<TaskListResponse> => {
  return request.get('/my/tasks', { params });
};

export const getTaskDetail = (id: string): Promise<TaskVO> => {
  return request.get(`/tasks/${id}`);
};

export const createTask = (data: CreateTaskRequest): Promise<TaskVO> => {
  return request.post('/tasks', data);
};

export const updateTaskStatus = (id: string, data: UpdateTaskStatusRequest): Promise<any> => {
  return request.put(`/tasks/${id}/status`, data);
};

export const assignTask = (id: string, data: AssignTaskRequest): Promise<any> => {
  return request.put(`/tasks/${id}/assign`, data);
};
