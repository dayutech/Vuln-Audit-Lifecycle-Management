import request from '@/utils/request';
import type { TaskListResponse, TaskVO, CreateTaskRequest, UpdateTaskStatusRequest, AssignTaskRequest } from '@/types/task';
import type { PaginationParams } from '@/types/common';

export interface TaskQueryParams extends PaginationParams {
  status?: string;
  priority?: string;
  assignee_id?: string;
  type?: string;
  reference_id?: string;
}

const taskPriorityMap: Record<string, string> = {
  high: 'P1',
  medium: 'P2',
  low: 'P3',
};

export const getTaskList = (params: TaskQueryParams): Promise<any> => {
  return request.get('/tasks', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
      keyword: params.search,
      status: params.status,
      assigneeId: params.assignee_id,
      referenceId: params.reference_id,
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
    type: data.type,
    referenceId: data.reference_id,
    assigneeId: data.assignee_id,
    priority: taskPriorityMap[data.priority] || data.priority,
    dueDate: data.due_date,
  });
};

export const updateTaskStatus = (id: string, data: UpdateTaskStatusRequest): Promise<any> => {
  return request.put(`/tasks/${id}/status`, null, { params: { status: data.status } });
};

export const assignTask = (id: string, data: AssignTaskRequest): Promise<any> => {
  return request.post(`/tasks/${id}/assign`, null, { params: { assigneeId: data.assignee_id } });
};
