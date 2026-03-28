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
  return request.get('/assets', { params });
};

export const getAssetDetail = (id: string): Promise<AssetVO> => {
  return request.get(`/assets/${id}`);
};

export const createAsset = (data: CreateAssetRequest): Promise<AssetVO> => {
  return request.post('/assets', data);
};

export const updateAsset = (id: string, data: UpdateAssetRequest): Promise<AssetVO> => {
  return request.put(`/assets/${id}`, data);
};

export const deleteAsset = (id: string): Promise<any> => {
  return request.delete(`/assets/${id}`);
};
