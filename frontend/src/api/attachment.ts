import request from '@/utils/request';

/**
 * 知识库 / 文档附件（t162 后端，t174 前端接入）。
 *
 * 后端四组端点（knowledge / documents 各一套）：
 * - `POST   /api/v1/{owner}/{id}/attachments`               multipart `file`（≤50MB，超限 413）
 * - `GET    /api/v1/{owner}/{id}/attachments`               列表（含 uploaderName）
 * - `GET    /api/v1/{owner}/{id}/attachments/{aid}/download` 文件流（中文名走 RFC 5987）
 * - `DELETE /api/v1/{owner}/{id}/attachments/{aid}`          软删 + 删物理文件
 */
export type AttachmentOwnerType = 'knowledge' | 'documents';

export interface AttachmentVO {
  id: string;
  ownerType?: string;
  ownerId?: string;
  fileName: string;
  fileSize?: number | null;
  contentType?: string | null;
  uploadedAt?: string | null;
  uploadedBy?: string | null;
  uploaderName?: string | null;
}

const base = (ownerType: AttachmentOwnerType, ownerId: string) => `/${ownerType}/${ownerId}/attachments`;

const unwrapList = (res: any): AttachmentVO[] => {
  const payload = res?.data ?? res;
  const items = Array.isArray(payload) ? payload : payload?.content ?? payload?.items;
  return Array.isArray(items) ? (items as AttachmentVO[]) : [];
};

export const listAttachments = async (ownerType: AttachmentOwnerType, ownerId: string): Promise<AttachmentVO[]> => {
  const res: any = await request.get(base(ownerType, ownerId));
  return unwrapList(res);
};

/** 上传单个附件（multipart）；`onProgress` 给出 0–100 的百分比 */
export const uploadAttachment = (
  ownerType: AttachmentOwnerType,
  ownerId: string,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<AttachmentVO> => {
  const formData = new FormData();
  formData.append('file', file, file.name);
  return request.post(base(ownerType, ownerId), formData, {
    onUploadProgress: onProgress
      ? (event) => {
          if (event.total) onProgress(Math.round((event.loaded / event.total) * 100));
        }
      : undefined,
  });
};

export const deleteAttachment = (ownerType: AttachmentOwnerType, ownerId: string, attachmentId: string): Promise<any> =>
  request.delete(`${base(ownerType, ownerId)}/${attachmentId}`);

/**
 * 下载附件。
 *
 * 走 `responseType: 'blob'` 拿文件流；文件名由**列表里的 fileName** 决定（后端已用 RFC 5987
 * 编好 header，但 blob 响应在 axios 里拿不到 header 的原始中文，直接用列表值更稳，也避免
 * 「中文名落盘成乱码」）。
 */
export const downloadAttachment = async (
  ownerType: AttachmentOwnerType,
  ownerId: string,
  attachmentId: string,
  fileName: string,
): Promise<void> => {
  const blob: any = await request.get(`${base(ownerType, ownerId)}/${attachmentId}/download`, { responseType: 'blob' });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
};
