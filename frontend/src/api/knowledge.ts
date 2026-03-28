import request from '@/utils/request';
import type { KnowledgeArticleVO, CreateKnowledgeRequest, UpdateKnowledgeRequest } from '@/types/knowledge';
import type { PaginationParams } from '@/types/common';

export interface KnowledgeQueryParams extends PaginationParams {
  category?: string;
  tags?: string;
  status?: string;
}

export const getKnowledgeList = (params: KnowledgeQueryParams): Promise<any> => {
  return request.get('/knowledge', { params });
};

export const getKnowledgeDetail = (id: string): Promise<KnowledgeArticleVO> => {
  return request.get(`/knowledge/${id}`);
};

export const createKnowledge = (data: CreateKnowledgeRequest): Promise<KnowledgeArticleVO> => {
  return request.post('/knowledge', data);
};

export const updateKnowledge = (id: string, data: UpdateKnowledgeRequest): Promise<KnowledgeArticleVO> => {
  return request.put(`/knowledge/${id}`, data);
};

export const deleteKnowledge = (id: string): Promise<any> => {
  return request.delete(`/knowledge/${id}`);
};
