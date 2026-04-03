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
} from '@/types/asset';
import type { PaginationParams } from '@/types/common';

export interface AssetQueryParams extends PaginationParams {
  team_id?: string;
  group_id?: string;
  target_type?: string;
  target_source?: string;
  status?: string;
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
    code: data.code,
    targetType: data.target_type,
    targetSource: data.target_source,
    description: data.description,
    priority: data.priority,
    teamId: data.team_id,
    ownerId: data.owner_id,
  });
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

export const getAssetFiles = (assetId: string, assetVersionId?: string): Promise<AssetFileVO[]> => {
  return request.get(`/assets/${assetId}/files`, {
    params: {
      assetVersionId,
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

export const getAssetDeliverables = (assetId: string, assetVersionId?: string): Promise<AssetDeliverableVO[]> => {
  return request.get(`/assets/${assetId}/deliverables`, {
    params: {
      assetVersionId,
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
  return request.post(`/assets/${assetId}/versions`, data, {
    params: {
      setDefault,
    },
  });
};

export const setDefaultAssetVersion = (assetId: string, versionId: string): Promise<AssetVersionVO> => {
  return request.put(`/assets/${assetId}/versions/${versionId}/default`);
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
