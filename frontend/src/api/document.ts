import request from '@/utils/request';
import type {
  DocumentListResponse,
  DocumentVO,
  CreateDocumentRequest,
  UpdateDocumentRequest,
  DocumentTagVO,
} from '@/types/document';
import type { PaginationParams } from '@/types/common';

export interface DocumentQueryParams extends PaginationParams {
  category?: string;
  asset_id?: string;
  task_id?: string;
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
      taskId: params.task_id,
      // t174：状态筛选走服务端（DRAFT|PUBLISHED|all），数据库过滤、跨页 total 正确
      status: params.status,
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

/** 文档版本历史（t159 详情页用）：GET /documents/{id}/versions */
export const getDocumentVersions = (id: string): Promise<any> => {
  return request.get(`/documents/${id}/versions`);
};

/* ==================== 文档标签（t66） ==================== */

/**
 * 文档标签接口（路径与参数以后端 DocumentController 为准）：
 * - GET    /documents/{id}/tags                → DocumentTag[]（全员可读）
 * - POST   /documents/{id}/tags?tag=xxx        → DocumentTag（作者本人或管理层，t61）
 * - DELETE /documents/{documentId}/tags/{tagId} → 204（作者本人或管理层；标签不属于该文档时 404，t65）
 */
export const getDocumentTags = (documentId: string): Promise<any> => {
  return request.get(`/documents/${documentId}/tags`);
};

export const addDocumentTag = (documentId: string, tag: string): Promise<any> => {
  return request.post(`/documents/${documentId}/tags`, null, { params: { tag } });
};

export const removeDocumentTag = (documentId: string, tagId: string): Promise<any> => {
  return request.delete(`/documents/${documentId}/tags/${tagId}`);
};

/** 统一解包文档标签响应（兼容裸数组 / 分页包装 / ApiResponse） */
export const extractDocumentTags = (res: any): DocumentTagVO[] => {
  const payload = res?.data ?? res;
  const items = Array.isArray(payload)
    ? payload
    : Array.isArray(payload?.content)
      ? payload.content
      : Array.isArray(payload?.items)
        ? payload.items
        : [];
  return items.filter(
    (item: any) => item && typeof item === 'object' && typeof item.tag === 'string' && item.tag.length > 0,
  );
};

