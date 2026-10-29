import type { PaginatedResponse } from './common';

/**
 * 知识库文章（`knowledge_articles` 表）。
 *
 * 后端字段为 camelCase：`authorId` / `viewCount` / `summary` / `createdAt`。
 * `tags` 在实体里是逗号分隔字符串；`GET /knowledge/{id}/tags` 返回拆分后的字符串数组，
 * 页面统一用 normalizeKnowledgeTags 归一化成 { id, tag }。
 */
export interface KnowledgeArticleVO {
  id: string;
  title: string;
  category: string;
  summary?: string;
  content: string;
  tags?: string;
  authorId?: string;
  status?: string;
  viewCount?: number;
  createdAt?: string;
  updatedAt?: string;

  /** @deprecated 后端已不使用；仅为兼容历史代码保留 */
  view_count?: number;
  /** @deprecated 后端已不使用 */
  like_count?: number;
  /** @deprecated 后端已不使用 */
  created_at?: string;
}

export type KnowledgeListResponse = PaginatedResponse<KnowledgeArticleVO>;

export interface KnowledgeTagVO {
  id: string;
  tag: string;
}

export interface CreateKnowledgeRequest {
  title: string;
  category: string;
  content: string;
  summary?: string;
  status?: string;
  tags?: string[];
}

export interface UpdateKnowledgeRequest {
  title?: string;
  category?: string;
  content?: string;
  summary?: string;
  status?: string;
  tags?: string[];
}
