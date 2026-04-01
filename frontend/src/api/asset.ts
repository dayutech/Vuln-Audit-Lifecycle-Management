import request from '@/utils/request';
import type {
  AssetVO,
  CreateAssetRequest,
  UpdateAssetRequest,
  AssetMetadataVO,
  UpsertAssetMetadataRequest,
  AssetFileVO,
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

export const getAssetMetadata = (assetId: string): Promise<AssetMetadataVO> => {
  return request.get(`/assets/${assetId}/metadata`);
};

export const createAssetMetadata = (assetId: string, data: UpsertAssetMetadataRequest): Promise<AssetMetadataVO> => {
  return request.post(`/assets/${assetId}/metadata`, data);
};

export const updateAssetMetadata = (
  assetId: string,
  data: UpsertAssetMetadataRequest,
  changeReason?: string
): Promise<AssetMetadataVO> => {
  return request.put(`/assets/${assetId}/metadata`, data, {
    params: {
      changeReason,
    },
  });
};

export const getAssetFiles = (assetId: string): Promise<AssetFileVO[]> => {
  return request.get(`/assets/${assetId}/files`);
};

export const uploadAssetFile = (assetId: string, formData: FormData): Promise<AssetFileVO> => {
  return request.post(`/assets/${assetId}/files`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
};

export const getEnvironmentList = (params: PaginationParams & { search?: string }): Promise<any> => {
  return request.get('/environments', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size,
      keyword: params.search,
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
    templateId: data.template_id,
    config: data.config,
  });
};

export const updateEnvironment = (id: string, data: UpdateEnvironmentRequest): Promise<EnvironmentVO> => {
  return request.put(`/environments/${id}`, {
    name: data.name,
    templateId: data.template_id,
    config: data.config,
    status: data.status,
  });
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
