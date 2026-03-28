import type { PaginatedResponse } from './common';

export interface DocumentVO {
  id: string;
  title: string;
  category: string;
  summary?: string;
  content?: string;
  asset_id?: string;
  author_id: string;
  status: string;
  version: number;
  word_count?: number;
  created_at: string;
  updated_at: string;
}

export type DocumentListResponse = PaginatedResponse<DocumentVO>;

export interface CreateDocumentRequest {
  title: string;
  category: string;
  summary?: string;
  content: string;
  asset_id?: string;
}

export interface UpdateDocumentRequest {
  title?: string;
  content?: string;
  changelog?: string;
}
