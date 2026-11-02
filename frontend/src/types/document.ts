import type { PaginatedResponse } from './common';

export interface DocumentVO {
  id: string;
  title: string;
  category: string;
  summary?: string;
  content?: string;
  asset_id?: string;
  task_id?: string;
  taskId?: string;
  author_id: string;
  status: string;
  version: number;
  word_count?: number;
  created_at: string;
  updated_at: string;
}

export type DocumentListResponse = PaginatedResponse<DocumentVO>;

/** 文档标签（后端 DocumentTag 实体，camelCase）。t66：前端此前无标签 UI，这里补齐类型。 */
export interface DocumentTagVO {
  id: string;
  documentId: string;
  tag: string;
  createdBy?: string;
  createdAt?: string;
}

export interface CreateDocumentRequest {
  title: string;
  category: string;
  summary?: string;
  content: string;
  asset_id?: string;
  task_id?: string;
}

export interface UpdateDocumentRequest {
  title?: string;
  content?: string;
  changelog?: string;
}
