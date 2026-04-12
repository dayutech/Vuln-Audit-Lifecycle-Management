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
  collaborators?: Array<{
    id: string;
    username: string;
    full_name: string;
  }>;
  estimated_hours?: number;
  estimatedHours?: number;
  actual_hours?: number;
  actualHours?: number;
  due_date?: string;
  created_at: string;
  updated_at: string;
  description?: string;
  audit_conclusion?: string;
  review_signature?: string;
  approval_comment?: string;
  reviewed_by?: string;
  reviewed_at?: string;
  env_collect_doc_id?: string;
  env_setup_doc_id?: string;
  code_audit_doc_id?: string;
  envCollectDocId?: string;
  envSetupDocId?: string;
  codeAuditDocId?: string;
}

export interface TaskCommentVO {
  id: string;
  taskId: string;
  userId: string;
  content: string;
  spentHours?: number;
  spent_hours?: number;
  workType?: string;
  work_type?: string;
  evidencePath?: string;
  evidence_path?: string;
  createdAt: string;
  created_at?: string;
}

export interface TaskAttachmentVO {
  id: string;
  taskId: string;
  fileName: string;
  filePath: string;
  uploadedBy: string;
  createdAt: string;
}

export interface AuditLogVO {
  id: string;
  userId: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  details?: string;
  createdAt: string;
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
  audit_conclusion?: string;
  estimated_hours?: number;
  env_collect_doc_id?: string;
  env_setup_doc_id?: string;
  code_audit_doc_id?: string;
}

export interface UpdateTaskStatusRequest {
  status: string;
}

export interface AssignTaskRequest {
  assignee_id: string;
}
