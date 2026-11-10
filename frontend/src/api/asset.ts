import request from '@/utils/request';
import type {
  AssetProjectLinkVO,
  AssetVO,
  CreateAssetRequest,
  UpdateAssetRequest,
  AssetFileVO,
  AssetDeliverableVO,
  AssetVersionVO,
  CreateAssetVersionRequest,
  EnvironmentVO,
  CreateEnvironmentRequest,
  UpdateEnvironmentRequest,
  AssetAccessVO,
  AssetClaimVO,
  CreateAssetAccessRequest,
  AssetTagVO,
  PageEnvelope,
} from '@/types/asset';
import type { PaginationParams } from '@/types/common';

export interface AssetQueryParams extends PaginationParams {
  team_id?: string;
  target_type?: string;
  target_source?: string;
  status?: string;
  /** 归属筛选（t143 修复：此前前端拼了参数但请求里没带上，导致筛选不生效） */
  customer_id?: string;
  project_id?: string;
  phase_id?: string;
  /** t141 标签筛选：AND 语义（同时满足全部） */
  tags?: string;
  /** t141 标签筛选：OR 语义（命中任一） */
  tags_any?: string;
}

export interface AssetOptionItem {
  value: string;
  label: string;
}

export interface AssetOptionsResponse {
  targetTypes: AssetOptionItem[];
  targetSources: AssetOptionItem[];
  priorities: AssetOptionItem[];
  statuses: AssetOptionItem[];
}

export const getAssetList = (params: AssetQueryParams): Promise<any> => {
  return request.get('/assets', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
      keyword: params.search,
      teamId: params.team_id,
      targetType: params.target_type,
      targetSource: params.target_source,
      status: params.status,
      // t143：客户/项目/期次归属筛选（后端与标签筛选取交集）
      customerId: params.customer_id,
      projectId: params.project_id,
      phaseId: params.phase_id,
      // t141：tags=AND / tagsAny=OR
      tags: params.tags,
      tagsAny: params.tags_any,
    },
  });
};

export const getAssetDetail = (id: string): Promise<AssetVO> => {
  return request.get(`/assets/${id}`);
};

export const createAsset = (data: CreateAssetRequest): Promise<AssetVO> => {
  return request.post('/assets', {
    name: data.name,
    targetType: data.target_type,
    targetSource: data.target_source,
    description: data.description,
    priority: data.priority,
    status: data.status,
    teamId: data.team_id,
    // t69：创建资产必须挂项目；phaseId 仅在分期项目且用户选择时提交
    projectId: data.project_id,
    phaseId: data.phase_id || undefined,
    linkType: data.link_type || undefined,
    remark: data.remark || undefined,
  }, { silent: true } as any);
};

export const getAssetOptions = (): Promise<AssetOptionsResponse> => {
  return request.get('/assets/options');
};

export const updateAsset = (id: string, data: UpdateAssetRequest): Promise<AssetVO> => {
  return request.put(`/assets/${id}`, {
    name: data.name,
    targetType: data.target_type,
    targetSource: data.target_source,
    description: data.description,
    priority: data.priority,
    status: data.status,
    teamId: data.team_id,
    ownerId: data.owner_id,
  });
};

export const deleteAsset = (id: string): Promise<any> => {
  return request.delete(`/assets/${id}`);
};

export const getAssetMetadata = (assetId: string, module: 'base' | 'network' | 'env' | 'account' | 'extra', assetVersionId?: string): Promise<any> => {
  return request.get(`/assets/${assetId}/metadata/${module}`, {
    params: {
      assetVersionId,
    },
  });
};

export const updateAssetMetadata = (
  assetId: string,
  module: 'base' | 'network' | 'env' | 'account' | 'extra',
  data: any,
  assetVersionId?: string
): Promise<any> => {
  return request.put(`/assets/${assetId}/metadata/${module}`, data, {
    params: {
      assetVersionId,
    },
  });
};

/**
 * 文件/交付物列表查询参数（t149，后端 t148 分页 + 筛选 + 排序）。
 *
 * `sort` 是**白名单字符串** `字段,方向`：文件 `uploadedAt|createdAt|name|size|fileSize|category`，
 * 交付物另有 `type|deliverableType`；非法字段/方向后端直接 400。
 */
export interface AttachmentQueryParams {
  /** 0 基；前端 1 基页码在调用处换算 */
  page?: number;
  /** ≤100，超限后端 400 */
  size?: number;
  sort?: string;
  keyword?: string;
  category?: string;
  uploaderId?: string;
  /** `YYYY-MM-DD` */
  dateFrom?: string;
  dateTo?: string;
  assetVersionId?: string;
  allVersions?: boolean;
}

const attachmentParams = (params: AttachmentQueryParams) => ({
  page: params.page ?? 0,
  size: params.size ?? 20,
  sort: params.sort,
  keyword: params.keyword,
  category: params.category,
  uploaderId: params.uploaderId,
  dateFrom: params.dateFrom,
  dateTo: params.dateTo,
  assetVersionId: params.assetVersionId,
  allVersions: params.allVersions,
});

/** 资产文件列表：**返回分页信封**（t148 起不再返回裸数组） */
export const getAssetFiles = (assetId: string, params: AttachmentQueryParams = {}): Promise<PageEnvelope<AssetFileVO>> => {
  return request.get(`/assets/${assetId}/files`, { params: attachmentParams(params) });
};

export const uploadAssetFile = (
  assetId: string,
  formData: FormData,
  assetVersionId?: string,
  onProgress?: (percent: number) => void,
): Promise<AssetFileVO> => {
  return request.post(`/assets/${assetId}/files`, formData, {
    params: {
      assetVersionId,
    },
    // t146：真实上传进度（浏览器上报 event.total 时才有百分比；否则只会在完成时到 100）
    onUploadProgress: onProgress
      ? (event) => {
          if (event.total) onProgress(Math.round((event.loaded / event.total) * 100));
        }
      : undefined,
  });
};

export const deleteAssetFile = (id: string): Promise<any> => {
  return request.delete(`/asset-files/${id}`);
};

export const downloadAssetFile = (id: string): Promise<Blob> => {
  return request.get(`/asset-files/${id}/download`, {
    responseType: 'blob',
  });
};

/** 资产交付物列表：**返回分页信封**（t148 起不再返回裸数组） */
export const getAssetDeliverables = (
  assetId: string,
  params: AttachmentQueryParams = {},
): Promise<PageEnvelope<AssetDeliverableVO>> => {
  return request.get(`/assets/${assetId}/deliverables`, { params: attachmentParams(params) });
};

export const uploadAssetDeliverable = (
  assetId: string,
  formData: FormData,
  assetVersionId?: string,
  onProgress?: (percent: number) => void,
): Promise<AssetDeliverableVO> => {
  return request.post(`/assets/${assetId}/deliverables`, formData, {
    params: {
      assetVersionId,
    },
    onUploadProgress: onProgress
      ? (event) => {
          if (event.total) onProgress(Math.round((event.loaded / event.total) * 100));
        }
      : undefined,
  });
};

export const deleteAssetDeliverable = (id: string): Promise<any> => {
  return request.delete(`/asset-deliverables/${id}`);
};

export const downloadAssetDeliverable = (id: string): Promise<Blob> => {
  return request.get(`/asset-deliverables/${id}/download`, {
    responseType: 'blob',
  });
};

export const getEnvironmentList = (params: PaginationParams & { search?: string; assetId?: string; assetVersionId?: string }): Promise<any> => {
  return request.get('/environments', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
      keyword: params.search,
      assetId: params.assetId,
      assetVersionId: params.assetVersionId,
    },
  });
};

export const getEnvironmentDetail = (id: string): Promise<EnvironmentVO> => {
  return request.get(`/environments/${id}`);
};

export const createEnvironment = (data: CreateEnvironmentRequest): Promise<EnvironmentVO> => {
  return request.post('/environments', {
    name: data.name,
    assetId: data.asset_id,
    assetVersionId: data.asset_version_id,
    templateId: data.template_id,
    config: data.config,
  });
};

export const updateEnvironment = (id: string, data: UpdateEnvironmentRequest): Promise<EnvironmentVO> => {
  return request.put(`/environments/${id}`, {
    name: data.name,
    assetVersionId: data.asset_version_id,
    templateId: data.template_id,
    config: data.config,
    status: data.status,
  });
};

export const getAssetVersions = (assetId: string): Promise<AssetVersionVO[]> => {
  return request.get(`/assets/${assetId}/versions`);
};

export const createAssetVersion = (
  assetId: string,
  data: CreateAssetVersionRequest,
  setDefault?: boolean
): Promise<AssetVersionVO> => {
  return request.post(`/assets/${assetId}/versions`, {
    name: data.name,
    description: data.description,
    status: data.status,
    inheritBaseOverview: data.inherit_base_overview,
    inheritFromVersionId: data.inherit_from_version_id,
  }, {
    params: {
      setDefault,
    },
  });
};

export const setDefaultAssetVersion = (assetId: string, versionId: string): Promise<AssetVersionVO> => {
  return request.put(`/assets/${assetId}/versions/${versionId}/default`);
};

export const setLatestAssetVersion = (assetId: string, versionId: string): Promise<AssetVersionVO> => {
  return request.put(`/assets/${assetId}/versions/${versionId}/latest`);
};

export const deleteEnvironment = (id: string): Promise<any> => {
  return request.delete(`/environments/${id}`, { silent: true });
};

/**
 * 部署 / 启动 / 停止环境。
 *
 * 宿主机未启用容器编排时后端返回 503（message=当前部署未启用容器编排），
 * 并把 environment.status 置为 failed、写入 failReason；这里统一 silent，
 * 由页面按状态码展示后端 message + 失败原因，避免被全局提示吞成「服务器内部错误」。
 */
export const deployEnvironment = (id: string): Promise<any> => {
  return request.post(`/environments/${id}/deploy`, null, { silent: true });
};

export const startEnvironment = (id: string): Promise<any> => {
  return request.post(`/environments/${id}/start`, null, { silent: true });
};

export const stopEnvironment = (id: string): Promise<any> => {
  return request.post(`/environments/${id}/stop`, null, { silent: true });
};

export const createEnvironmentSnapshot = (id: string, name: string): Promise<any> => {
  return request.post(`/environments/${id}/snapshots`, null, {
    params: {
      name,
    },
  });
};

export const getEnvironmentSnapshots = (id: string): Promise<any> => {
  return request.get(`/environments/${id}/snapshots`);
};

export const rollbackEnvironmentSnapshot = (id: string, snapshotId: string): Promise<any> => {
  return request.post(`/environments/${id}/snapshots/${snapshotId}/rollback`);
};

export const getEnvironmentTemplateList = (): Promise<any> => {
  return request.get('/environment-templates');
};

// ==================== 访问申请 ====================

/** 提交资产访问申请（后端自动路由审批人） */
export const createAssetAccessRequest = (assetId: string, data: CreateAssetAccessRequest): Promise<AssetAccessVO> => {
  return request.post(`/assets/${assetId}/access-requests`, {
    canViewDocuments: data.can_view_documents ?? false,
    canViewEnvironments: data.can_view_environments ?? false,
    canViewFiles: data.can_view_files ?? false,
    allowedFileTypes: data.allowed_file_types,
    requestNote: data.request_note,
  });
};

/** 我发起的访问申请 */
export const getMyAccessRequests = (params?: { page?: number; page_size?: number; status?: string }): Promise<any> => {
  return request.get('/assets/my/access-requests', {
    params: {
      page: Math.max((params?.page ?? 1) - 1, 0),
      size: params?.page_size ?? 50,
      status: params?.status,
    },
  });
};

/** 某资产的访问申请列表 */
export const getAssetAccessRequests = (assetId: string, params?: { page?: number; page_size?: number; status?: string }): Promise<any> => {
  return request.get(`/assets/${assetId}/access-requests`, {
    params: {
      page: Math.max((params?.page ?? 1) - 1, 0),
      size: params?.page_size ?? 50,
      status: params?.status,
    },
  });
};

/** 待我审批的访问申请 */
export const getMyAccessApprovalRequests = (params?: { page?: number; page_size?: number }): Promise<any> => {
  return request.get('/assets/my/access-approval-requests', {
    params: {
      page: Math.max((params?.page ?? 1) - 1, 0),
      size: params?.page_size ?? 20,
    },
  });
};

/** 审批历史视角（t156）：reviewer=我审批过的（默认），requester=我申请的 */
export type ApprovalHistoryAs = 'reviewer' | 'requester';
/** 审批历史状态筛选：approved / rejected / all */
export type ApprovalHistoryStatus = 'approved' | 'rejected' | 'all';

export interface ApprovalHistoryQuery {
  as?: ApprovalHistoryAs;
  status?: ApprovalHistoryStatus;
  page?: number;
  page_size?: number;
  assetId?: string;
  keyword?: string;
}

/** 审批历史条目（后端 t156 `ApprovalHistoryItem`，只含**已处理**记录） */
export interface ApprovalHistoryItemVO {
  id: string;
  assetId: string;
  assetCode?: string | null;
  assetName?: string | null;
  assetDeleted?: boolean;
  applicant?: { id?: string | null; username?: string | null } | null;
  status: 'approved' | 'rejected' | string;
  requestedAt?: string | null;
  reviewedAt?: string | null;
  reviewer?: { id?: string | null; username?: string | null } | null;
  reviewNote?: string | null;
  scope?: {
    canViewDocuments?: boolean;
    canViewEnvironments?: boolean;
    canViewFiles?: boolean;
    allowedFileTypes?: string | null;
  } | null;
}

/**
 * 我的审批历史（t156）：`GET /assets/my/access-approval-history`。
 *
 * 默认 `as=reviewer`、`status=all`、`reviewedAt desc`；`as`/`status` 传非法值后端 400
 * （前端只用上面的字面量联合类型，正常路径不会触发）。
 */
export const getAccessApprovalHistory = (params: ApprovalHistoryQuery = {}): Promise<any> => {
  return request.get('/assets/my/access-approval-history', {
    params: {
      as: params.as ?? 'reviewer',
      status: params.status ?? 'all',
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size ?? 20,
      assetId: params.assetId || undefined,
      keyword: params.keyword?.trim() ? params.keyword.trim() : undefined,
    },
  });
};

/** 审批历史计数（t156）：`{approved, rejected}`，给角标用，避免全量拉取 */
export const getAccessApprovalHistoryCount = (
  as: ApprovalHistoryAs = 'reviewer',
): Promise<{ approved: number; rejected: number }> => {
  return request.get('/assets/my/access-approval-history/count', { params: { as } });
};

/** 审批访问申请 */
export const reviewAssetAccessRequest = (requestId: string, approved: boolean, reviewNote?: string): Promise<AssetAccessVO> => {
  return request.post(`/assets/access-requests/${requestId}/review`, { approved, reviewNote });
};

// ==================== 资产认领 ====================

/** 认领资产 */
export const createAssetClaim = (assetId: string, note?: string): Promise<AssetClaimVO> => {
  return request.post(`/assets/${assetId}/claims`, { note });
};

/** 我的认领记录 */
export const getMyClaims = (params?: { page?: number; page_size?: number; status?: string }): Promise<any> => {
  return request.get('/assets/my/claims', {
    params: {
      page: Math.max((params?.page ?? 1) - 1, 0),
      size: params?.page_size ?? 50,
      status: params?.status,
    },
  });
};

/** 某资产的认领记录 */
export const getAssetClaims = (assetId: string, params?: { page?: number; page_size?: number; status?: string }): Promise<any> => {
  return request.get(`/assets/${assetId}/claims`, {
    params: {
      page: Math.max((params?.page ?? 1) - 1, 0),
      size: params?.page_size ?? 50,
      status: params?.status,
    },
  });
};

/** 审批认领申请 */
export const reviewAssetClaim = (claimId: string, approved: boolean, reviewNote?: string): Promise<AssetClaimVO> => {
  return request.post(`/assets/claims/${claimId}/review`, { approved, reviewNote });
};

/** 完成认领 */
export const completeAssetClaim = (claimId: string): Promise<any> => {
  return request.post(`/assets/claims/${claimId}/complete`);
};

/** 放弃认领 */
export const abandonAssetClaim = (claimId: string, note?: string): Promise<any> => {
  return request.post(`/assets/claims/${claimId}/abandon`, null, { params: { note } });
};

// ==================== 资产标签 ====================

/** 资产标签列表（GET /assets/{id}/tags） */
export const getAssetTags = (assetId: string): Promise<AssetTagVO[]> => {
  return request.get(`/assets/${assetId}/tags`);
};

/** 添加资产标签（POST /assets/{id}/tags?tag=） */
export const addAssetTag = (assetId: string, tag: string): Promise<AssetTagVO> => {
  return request.post(`/assets/${assetId}/tags`, null, { params: { tag } });
};

/** 移除资产标签（DELETE /assets/{assetId}/tags/{tagId}） */
export const deleteAssetTag = (assetId: string, tagId: string): Promise<any> => {
  return request.delete(`/assets/${assetId}/tags/${tagId}`);
};

/* ==================== 项目挂靠（t69 / t73） ==================== */

/** GET /assets/{id}/project-links —— 资产的项目挂靠明细（项目/客户摘要 + linkType/备注） */
export const getAssetProjectLinks = (assetId: string): Promise<AssetProjectLinkVO[]> => {
  return request.get(`/assets/${assetId}/project-links`);
};

/** POST /assets/{id}/project-links —— 追加挂靠；重复 (资产,项目,期次) 409；允许跨客户 */
export const addAssetProjectLink = (
  assetId: string,
  data: { project_id: string; phase_id?: string | null; link_type?: string | null; remark?: string | null },
): Promise<AssetProjectLinkVO> => {
  return request.post(
    `/assets/${assetId}/project-links`,
    {
      projectId: data.project_id,
      phaseId: data.phase_id || null,
      linkType: data.link_type || undefined,
      remark: data.remark || undefined,
    },
    { silent: true } as any,
  );
};

/** DELETE /assets/{id}/project-links/{linkId} —— 不存在/不属于该资产 404；最后一条 409 */
export const removeAssetProjectLink = (assetId: string, linkId: string): Promise<any> => {
  return request.delete(`/assets/${assetId}/project-links/${linkId}`, { silent: true } as any);
};

/** 解包挂靠列表（兼容 ApiResponse / 裸数组） */
export const extractAssetProjectLinks = (res: any): AssetProjectLinkVO[] => {
  const payload = res?.data ?? res ?? [];
  const items = Array.isArray(payload) ? payload : payload?.content || payload?.items || [];
  return Array.isArray(items) ? (items as AssetProjectLinkVO[]) : [];
};


