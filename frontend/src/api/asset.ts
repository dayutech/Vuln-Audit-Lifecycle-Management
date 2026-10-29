import request from '@/utils/request';
import type {
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
} from '@/types/asset';
import type { PaginationParams } from '@/types/common';

export interface AssetQueryParams extends PaginationParams {
  team_id?: string;
  group_id?: string;
  target_type?: string;
  target_source?: string;
  status?: string;
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
      groupId: params.group_id,
      targetType: params.target_type,
      targetSource: params.target_source,
      status: params.status,
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
  });
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

export const getAssetFiles = (assetId: string, assetVersionId?: string, allVersions?: boolean): Promise<AssetFileVO[]> => {
  return request.get(`/assets/${assetId}/files`, {
    params: {
      assetVersionId,
      allVersions,
    },
  });
};

export const uploadAssetFile = (assetId: string, formData: FormData, assetVersionId?: string): Promise<AssetFileVO> => {
  return request.post(`/assets/${assetId}/files`, formData, {
    params: {
      assetVersionId,
    },
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

export const getAssetDeliverables = (assetId: string, assetVersionId?: string, allVersions?: boolean): Promise<AssetDeliverableVO[]> => {
  return request.get(`/assets/${assetId}/deliverables`, {
    params: {
      assetVersionId,
      allVersions,
    },
  });
};

export const uploadAssetDeliverable = (assetId: string, formData: FormData, assetVersionId?: string): Promise<AssetDeliverableVO> => {
  return request.post(`/assets/${assetId}/deliverables`, formData, {
    params: {
      assetVersionId,
    },
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
  return request.delete(`/environments/${id}`);
};

export const deployEnvironment = (id: string): Promise<any> => {
  return request.post(`/environments/${id}/deploy`);
};

export const startEnvironment = (id: string): Promise<any> => {
  return request.post(`/environments/${id}/start`);
};

export const stopEnvironment = (id: string): Promise<any> => {
  return request.post(`/environments/${id}/stop`);
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
