import request from '@/utils/request';
import type {
  KnowledgeArticleVO,
  CreateKnowledgeRequest,
  UpdateKnowledgeRequest,
} from '@/types/knowledge';
import type { PaginationParams } from '@/types/common';
import { useUserStore } from '@/store/userStore';

export interface KnowledgeQueryParams extends PaginationParams {
  category?: string;
  tags?: string;
  status?: string;
}

export const getKnowledgeList = (params: KnowledgeQueryParams): Promise<any> => {
  return request.get('/knowledge', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
      keyword: params.search,
      category: params.category,
      tags: params.tags,
      status: params.status,
    },
  });
};

export const getKnowledgeDetail = (id: string): Promise<KnowledgeArticleVO> => {
  return request.get(`/knowledge/${id}`);
};

/**
 * 创建知识文章：POST /knowledge
 *
 * 后端接收 KnowledgeArticle 实体：authorId 必填，tags 为逗号分隔字符串。
 */
export const createKnowledge = (data: CreateKnowledgeRequest): Promise<KnowledgeArticleVO> => {
  const currentUserId = useUserStore.getState().user?.id;
  const tags = Array.isArray(data.tags) && data.tags.length > 0 ? data.tags.join(',') : undefined;
  return request.post('/knowledge', {
    title: data.title,
    category: data.category,
    summary: data.summary,
    content: data.content,
    tags,
    status: data.status ?? 'published',
    authorId: currentUserId,
  });
};

export const updateKnowledge = (id: string, data: UpdateKnowledgeRequest): Promise<KnowledgeArticleVO> => {
  const tags = Array.isArray(data.tags) ? data.tags.join(',') : undefined;
  return request.put(`/knowledge/${id}`, { ...data, tags });
};

export const deleteKnowledge = (id: string): Promise<any> => {
  return request.delete(`/knowledge/${id}`);
};

// ==================== 搜索 / 热门 ====================

/** 全文搜索：GET /knowledge/search?keyword=***（返回列表，不分页） */
export const searchKnowledge = (keyword: string): Promise<KnowledgeArticleVO[]> => {
  return request.get('/knowledge/search', { params: { keyword } });
};

/** 热门文章：GET /knowledge/popular?limit=N（按浏览量倒序） */
export const getPopularKnowledge = (limit = 5): Promise<KnowledgeArticleVO[]> => {
  return request.get('/knowledge/popular', { params: { limit } });
};

// ==================== 评论 ====================

/**
 * 发表评论：POST /knowledge/{id}/comments?content=***
 *
 * 评论内容走 query 参数（后端 @RequestParam String content）。
 */
export const addKnowledgeComment = (id: string, content: string): Promise<any> => {
  return request.post(`/knowledge/${id}/comments`, null, { params: { content } });
};

// ==================== 标签 ====================

/** 文章标签：GET /knowledge/{id}/tags（返回字符串数组） */
export const getKnowledgeTags = (id: string): Promise<string[]> => {
  return request.get(`/knowledge/${id}/tags`);
};

/** 添加标签：POST /knowledge/{id}/tags?tag=*** */
export const addKnowledgeTag = (id: string, tag: string): Promise<any> => {
  return request.post(`/knowledge/${id}/tags`, null, { params: { tag } });
};

/** 移除标签：DELETE /knowledge/{id}/tags?tag=*** */
export const removeKnowledgeTag = (id: string, tag: string): Promise<any> => {
  return request.delete(`/knowledge/${id}/tags`, { params: { tag } });
};

/** 统一解包列表响应 */
export const extractKnowledgeList = (res: any): KnowledgeArticleVO[] => {
  const payload = res?.data ?? res;
  if (Array.isArray(payload)) return payload as KnowledgeArticleVO[];
  const items = payload?.content ?? payload?.items;
  return Array.isArray(items) ? (items as KnowledgeArticleVO[]) : [];
};

/** 统一解包字符串数组响应（标签接口） */
export const extractStringList = (res: any): string[] => {
  const payload = res?.data ?? res;
  if (!Array.isArray(payload)) return [];
  return payload
    .map((item) => {
      if (typeof item === 'string') return item;
      if (item && typeof item === 'object' && typeof (item as any).tag === 'string') return (item as any).tag as string;
      return '';
    })
    .filter(Boolean);
};
