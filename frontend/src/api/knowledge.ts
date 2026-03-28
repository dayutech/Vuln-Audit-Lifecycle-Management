import request from '@/utils/request';
import type { KnowledgeArticleVO, CreateKnowledgeRequest, UpdateKnowledgeRequest } from '@/types/knowledge';
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

export const createKnowledge = (data: CreateKnowledgeRequest): Promise<KnowledgeArticleVO> => {
  const currentUserId = useUserStore.getState().user?.id;
  const tags = Array.isArray(data.tags) ? data.tags.join(',') : undefined;
  return request.post('/knowledge', {
    title: data.title,
    category: data.category,
    content: data.content,
    tags,
    authorId: currentUserId,
  });
};

export const updateKnowledge = (id: string, data: UpdateKnowledgeRequest): Promise<KnowledgeArticleVO> => {
  return request.put(`/knowledge/${id}`, data);
};

export const deleteKnowledge = (id: string): Promise<any> => {
  return request.delete(`/knowledge/${id}`);
};
