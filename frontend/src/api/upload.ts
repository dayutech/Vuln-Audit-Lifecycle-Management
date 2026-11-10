import request from '@/utils/request';

/**
 * 大文件上传审批 API（t160 后端，t161 前端接入）。
 *
 * 语义（README §70.6）：
 * - `POST /uploads/pending`：**统一上传入口**。≤ 门限（默认 20MB）→ `status=DIRECT` 且业务记录已建；
 *   超门限 → `status=PENDING`（只写临时区 + 审批单，**业务表没有这条记录**）；超上限（500MB）→ 413。
 * - `GET /uploads/pending/{id}`：只能查自己提交的请求（他人 404，不泄露存在性）。
 * - `GET /admin/upload-approvals` + `approve|reject`：仅 ADMIN。
 *
 * 注意：**PENDING 时绝不能把记录塞进业务列表** —— 后端根本没建记录，塞进去会凭空出现一条幽灵数据。
 */
export type UploadTargetType = 'TOOL' | 'COMPONENT' | 'KNOWLEDGE' | 'DOCUMENT';

export type UploadApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED';

export interface UploadUserBrief {
  id?: string | null;
  username?: string | null;
}

export interface UploadApprovalItem {
  id: string;
  targetType?: string | null;
  targetId?: string | null;
  fileName?: string | null;
  fileSize?: number | null;
  contentType?: string | null;
  status?: UploadApprovalStatus | string | null;
  requester?: UploadUserBrief | null;
  requestedAt?: string | null;
  reviewer?: UploadUserBrief | null;
  reviewedAt?: string | null;
  reviewNote?: string | null;
  purgeAfter?: string | null;
  pendingFilePresent?: boolean;
}

export interface SubmitUploadResult {
  requestId?: string | null;
  status: 'DIRECT' | 'PENDING' | string;
  targetType?: string | null;
  targetId?: string | null;
  fileName?: string | null;
  fileSize?: number | null;
  purgeAfter?: string | null;
}

const payloadOf = (res: any) => res?.data ?? res ?? {};

/**
 * 统一上传入口：`POST /uploads/pending`（multipart，字段 `file`）。
 *
 * `name` / `description` 是后端接受的元数据（不是 `title`），PENDING 批准后作为业务记录字段。
 * `onProgress` 用于沿用 t146 的进度条，`timeout` 拉长到 10 分钟（大文件）。
 */
export const submitPendingUpload = (
  params: {
    file: File | Blob;
    fileName?: string;
    targetType: UploadTargetType;
    name?: string;
    description?: string;
    onProgress?: (percent: number) => void;
  },
): Promise<any> => {
  const formData = new FormData();
  formData.append('file', params.file, params.fileName);
  formData.append('targetType', params.targetType);
  if (params.name) formData.append('name', params.name);
  if (params.description) formData.append('description', params.description);

  return request.post('/uploads/pending', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000,
    silent: true,
    onUploadProgress: (event: { loaded: number; total?: number }) => {
      if (!params.onProgress) return;
      const total = event.total || (params.file as File).size || 0;
      if (total > 0) params.onProgress(Math.min(100, Math.round((event.loaded / total) * 100)));
    },
  } as any);
};

export const extractSubmitUpload = (res: any): SubmitUploadResult => {
  const payload = payloadOf(res);
  return {
    requestId: payload?.requestId ?? null,
    status: String(payload?.status ?? 'DIRECT').toUpperCase(),
    targetType: payload?.targetType ?? null,
    targetId: payload?.targetId ?? null,
    fileName: payload?.fileName ?? null,
    fileSize: payload?.fileSize ?? null,
    purgeAfter: payload?.purgeAfter ?? null,
  };
};

export const extractUploadApproval = (res: any): UploadApprovalItem | null => {
  const payload = payloadOf(res);
  return payload?.id ? (payload as UploadApprovalItem) : null;
};

export const extractUploadApprovalPage = (
  res: any,
): { items: UploadApprovalItem[]; total: number } => {
  const payload = payloadOf(res);
  const items = (payload?.content || payload?.items || (Array.isArray(payload) ? payload : [])) as UploadApprovalItem[];
  return {
    items: Array.isArray(items) ? items : [],
    total: payload?.totalElements ?? payload?.total ?? (Array.isArray(items) ? items.length : 0),
  };
};

/** 查询自己的上传请求（他人 → 404） */
export const getMyPendingUpload = (id: string): Promise<any> => {
  return request.get(`/uploads/pending/${id}`, { silent: true } as any);
};

/** 管理员：审批列表（status 可空 = 全部） */
export const listUploadApprovals = (
  params: { status?: string; page?: number; size?: number } = {},
): Promise<any> => {
  return request.get('/admin/upload-approvals', {
    params: {
      status: params.status || undefined,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
    silent: true,
  } as any);
};

/** 管理员：批准（临时文件移入正式存储并创建业务记录） */
export const approveUpload = (id: string): Promise<any> => {
  return request.post(`/admin/upload-approvals/${id}/approve`, undefined, { silent: true } as any);
};

/** 管理员：拒绝（**立即删除临时文件**） */
export const rejectUpload = (id: string, note?: string): Promise<any> => {
  return request.post(`/admin/upload-approvals/${id}/reject`, { note: note || null }, { silent: true } as any);
};
