import request from '@/utils/request';
import type { TaskListResponse, TaskVO, CreateTaskRequest, UpdateTaskStatusRequest, AssignTaskRequest } from '@/types/task';
import type { PaginationParams } from '@/types/common';

export interface TaskQueryParams extends PaginationParams {
  status?: string;
  assignee_id?: string;
  reference_id?: string;
  asset_id?: string;
  asset_version_id?: string;
}

export const getTaskList = (params: TaskQueryParams): Promise<any> => {
  return request.get('/tasks', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
      keyword: params.search,
      status: params.status,
      assigneeId: params.assignee_id,
      referenceId: params.reference_id,
      assetId: params.asset_id,
      assetVersionId: params.asset_version_id,
    },
  });
};

export const getMyTaskList = (params: TaskQueryParams): Promise<TaskListResponse> => {
  return request.get('/tasks', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
      keyword: params.search,
      status: params.status,
      assigneeId: params.assignee_id,
    },
  });
};

export const getTaskDetail = (id: string): Promise<TaskVO> => {
  return request.get(`/tasks/${id}`);
};

export const createTask = (data: CreateTaskRequest): Promise<TaskVO> => {
  return request.post('/tasks', {
    title: data.title,
    referenceId: data.reference_id,
    assetId: data.asset_id,
    assetVersionId: data.asset_version_id,
    assigneeId: data.assignee_id,
    dueDate: data.due_date,
    description: data.description,
  });
};

export const updateTaskStatus = (id: string, data: UpdateTaskStatusRequest): Promise<any> => {
  return request.put(`/tasks/${id}/status`, null, { params: { status: data.status } });
};

export const updateTask = (id: string, data: Partial<CreateTaskRequest>): Promise<TaskVO> => {
  return request.put(`/tasks/${id}`, data);
};

export const deleteTask = (id: string): Promise<any> => {
  return request.delete(`/tasks/${id}`);
};

export const assignTask = (id: string, data: AssignTaskRequest): Promise<any> => {
  return request.post(`/tasks/${id}/assign`, null, { params: { assigneeId: data.assignee_id } });
};
