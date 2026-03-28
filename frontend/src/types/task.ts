import type { PaginatedResponse } from './common';

export interface TaskVO {
  id: string;
  title: string;
  type: string;
  reference_id?: string;
  status: string;
  priority: string;
  assignee?: {
    id: string;
    username: string;
    full_name: string;
  };
  creator?: {
    id: string;
    username: string;
    full_name: string;
  };
  due_date?: string;
  created_at: string;
  updated_at: string;
}

export type TaskListResponse = PaginatedResponse<TaskVO>;

export interface CreateTaskRequest {
  title: string;
  type: string;
  reference_id?: string;
  assignee_id?: string;
  priority: string;
  due_date?: string;
}

export interface UpdateTaskStatusRequest {
  status: string;
}

export interface AssignTaskRequest {
  assignee_id: string;
}
