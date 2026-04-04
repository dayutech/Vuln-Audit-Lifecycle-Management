import type { PaginatedResponse } from './common';

export interface TaskVO {
  id: string;
  title: string;
  reference_id?: string;
  asset_id?: string;
  asset_version_id?: string;
  status: string;
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
  title?: string;
  reference_id?: string;
  asset_id?: string;
  asset_version_id?: string;
  assignee_id?: string;
  due_date?: string;
  description?: string;
}

export interface UpdateTaskStatusRequest {
  status: string;
}

export interface AssignTaskRequest {
  assignee_id: string;
}
