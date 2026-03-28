import request from '@/utils/request';
import type { DocumentListResponse, DocumentVO, CreateDocumentRequest, UpdateDocumentRequest } from '@/types/document';
import type { PaginationParams } from '@/types/common';

export interface DocumentQueryParams extends PaginationParams {
  category?: string;
  asset_id?: string;
  author_id?: string;
  status?: string;
}

export const getDocumentList = (params: DocumentQueryParams): Promise<any> => {
  return request.get('/documents', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
      keyword: params.search,
      category: params.category,
      assetId: params.asset_id,
    },
  });
};

export const getMyDocumentList = (params: DocumentQueryParams): Promise<DocumentListResponse> => {
  return request.get('/documents/my/documents', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
    },
  });
};

export const getDocumentDetail = (id: string): Promise<DocumentVO> => {
  return request.get(`/documents/${id}`);
};

export const createDocument = (data: CreateDocumentRequest): Promise<DocumentVO> => {
  return request.post('/documents', data);
};

export const updateDocument = (id: string, data: UpdateDocumentRequest): Promise<DocumentVO> => {
  return request.put(`/documents/${id}`, data);
};

export const deleteDocument = (id: string): Promise<any> => {
  return request.delete(`/documents/${id}`);
};

export const publishDocument = (id: string): Promise<any> => {
  return request.post(`/documents/${id}/publish`);
};

export const unpublishDocument = (id: string): Promise<any> => {
  return request.post(`/documents/${id}/unpublish`);
};
