import type { PaginatedResponse } from './common';
import type { UserVO } from './user';

export interface KnowledgeTagVO {
  id: string;
  tag: string;
}

export interface KnowledgeArticleVO {
  id: string;
  title: string;
  category: string;
  content: string;
  author: UserVO;
  view_count: number;
  like_count: number;
  status: string;
  tags?: KnowledgeTagVO[];
  created_at: string;
  updated_at: string;
}

export type KnowledgeListResponse = PaginatedResponse<KnowledgeArticleVO>;

export interface CreateKnowledgeRequest {
  title: string;
  category: string;
  content: string;
  tags?: string[];
}

export interface UpdateKnowledgeRequest {
  title?: string;
  category?: string;
  content?: string;
  status?: string;
}
