import request from '@/utils/request';
import type { ToolVO } from '@/types/tool';
import type { PaginationParams } from '@/types/common';

export interface ToolQueryParams extends PaginationParams {
  category?: string;
  tags?: string;
  status?: string;
}

export const getToolList = (params: ToolQueryParams): Promise<any> => {
  return request.get('/tools', {
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

export const getToolDetail = (id: string): Promise<ToolVO> => {
  return request.get(`/tools/${id}`);
};

export const uploadTool = (data: FormData): Promise<ToolVO> => {
  return request.post('/tools', data, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
};

export const updateTool = (id: string, data: any): Promise<ToolVO> => {
  return request.put(`/tools/${id}`, data);
};

export const downloadTool = (id: string): Promise<any> => {
  return request.post(`/tools/${id}/download`);
};
