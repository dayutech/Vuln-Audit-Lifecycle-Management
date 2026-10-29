import axios from 'axios';
import request from '@/utils/request';
import type { ToolVO, ToolVersionVO, CreateToolRequest, UploadToolVersionPayload } from '@/types/tool';
import type { PaginationParams } from '@/types/common';
import { parseContentDispositionFilename } from '@/utils/download';

/** 与 utils/request 的 baseURL 保持一致（独立 axios 调用下载接口时使用） */
const API_BASE = '/api/v1';

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

/**
 * 创建工具（JSON）。后端 `POST /tools` 接收 Tool 实体而非 multipart，
 * 工具包文件需要再用 `POST /tools/{id}/file` 单独上传。
 */
export const createTool = (data: CreateToolRequest): Promise<ToolVO> => {
  return request.post('/tools', data);
};

export const updateTool = (id: string, data: Partial<ToolVO>): Promise<ToolVO> => {
  return request.put(`/tools/${id}`, data);
};

export const deleteTool = (id: string): Promise<any> => {
  return request.delete(`/tools/${id}`);
};

/** 上传/替换工具包：POST /tools/{id}/file（multipart，字段名 file） */
export const uploadToolPackage = (id: string, file: File | Blob, fileName?: string): Promise<ToolVO> => {
  const formData = new FormData();
  formData.append('file', file, fileName);
  return request.post(`/tools/${id}/file`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

/**
 * 下载工具包：GET /tools/{id}/download
 *
 * 后端返回真实文件流（application/octet-stream）并累加下载次数；
 * 未上传工具包时返回 404「该工具未上传工具包」。
 */
export const downloadToolFile = (id: string): Promise<Blob> => {
  return request.get(`/tools/${id}/download`, { responseType: 'blob' });
};

/** 仅记录一次下载（POST /tools/{id}/download，不返回文件） */
export const recordToolDownload = (id: string): Promise<any> => {
  return request.post(`/tools/${id}/download`);
};

/** 下载次数：GET /tools/{id}/download-count */
export const getToolDownloadCount = (id: string): Promise<any> => {
  return request.get(`/tools/${id}/download-count`);
};

// ==================== 版本管理 ====================

/** 版本列表：GET /tools/{id}/versions（按创建时间倒序） */
export const getToolVersions = (id: string): Promise<ToolVersionVO[]> => {
  return request.get(`/tools/${id}/versions`);
};

/**
 * 上传新版本：POST /tools/{id}/versions
 *
 * 后端签名：`@RequestParam(required=false) version`（留空自动生成 vN）、
 * `@RequestParam(required=false) changelog`、`@RequestParam(value="file", required=false) MultipartFile`。
 * 不带文件时创建「仅元数据」版本（hasFile=false）。
 *
 * silent：409（版本号冲突）/404/403 由页面按状态码给不同文案，避免全局提示语焉不详。
 */
export const uploadToolVersion = (
  id: string,
  payload: UploadToolVersionPayload,
): Promise<ToolVersionVO> => {
  const formData = new FormData();
  if (payload.file) {
    formData.append('file', payload.file, payload.fileName);
  }
  return request.post(`/tools/${id}/versions`, formData, {
    params: {
      version: payload.version?.trim() ? payload.version.trim() : undefined,
      changelog: payload.changelog,
    },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
    silent: true,
  });
};

/**
 * 下载某个版本的文件：GET /tools/{id}/versions/{versionId}/download
 *
 * 这里绕开全局 request 实例：全局响应拦截器只返回 `response.data`，
 * 拿不到 `Content-Disposition`（后端用 filename*=UTF-8'' 传中文名）。
 * 独立调用保持同样的 token 鉴权，失败抛出结构与全局一致（error.response.status/data），
 * 调用方可直接复用 utils/errorMessage 的 getBlobErrorMessage。
 *
 * 404 三种含义：版本不存在/不属于该工具、仅元数据版本、文件缺失。
 */
export const downloadToolVersion = async (
  toolId: string,
  versionId: string,
): Promise<{ blob: Blob; fileName?: string }> => {
  const token = localStorage.getItem('access_token');
  const response = await axios.get(
    `${API_BASE}/tools/${toolId}/versions/${versionId}/download`,
    {
      responseType: 'blob',
      timeout: 120000,
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    },
  );
  const disposition = (response.headers?.['content-disposition'] as string | undefined) ?? undefined;
  return {
    blob: response.data as Blob,
    fileName: parseContentDispositionFilename(disposition),
  };
};

// ==================== 标签 ====================

/** 工具标签：GET /tools/{id}/tags（返回字符串数组） */
export const getToolTags = (id: string): Promise<string[]> => {
  return request.get(`/tools/${id}/tags`);
};

/** 添加标签：POST /tools/{id}/tags?tag=*** */
export const addToolTag = (id: string, tag: string): Promise<any> => {
  return request.post(`/tools/${id}/tags`, null, { params: { tag } });
};

/** 移除标签：DELETE /tools/{id}/tags?tag=*** */
export const removeToolTag = (id: string, tag: string): Promise<any> => {
  return request.delete(`/tools/${id}/tags`, { params: { tag } });
};

/** 统一解包字符串数组响应 */
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

/** 通用列表解包（兼容 ApiResponse / 裸数组） */
export const extractList = <T,>(res: any): T[] => {
  const payload = res?.data ?? res;
  return Array.isArray(payload) ? (payload as T[]) : [];
};
