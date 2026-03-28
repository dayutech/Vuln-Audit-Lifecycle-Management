import request from '@/utils/request';
import type { AssetVO, CreateAssetRequest, UpdateAssetRequest } from '@/types/asset';
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
      search: params.search,
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
    description: data.description,
    priority: data.priority,
    status: data.status,
  });
};

export const deleteAsset = (id: string): Promise<any> => {
  return request.delete(`/assets/${id}`);
};
