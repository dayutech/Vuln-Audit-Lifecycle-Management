import type { PaginatedResponse } from './common';
import type { UserVO } from './user';

export interface AssetGroup {
  id: string;
  name: string;
}

export interface AssetVO {
  id: string;
  name: string;
  code: string;
  target_type: string;
  target_source: string;
  description?: string;
  priority: string;
  status: string;
  groups?: AssetGroup[];
  owner?: UserVO;
  team?: any;
  vulnerability_count?: number;
  created_at: string;
  updated_at?: string;
}

export type AssetListResponse = PaginatedResponse<AssetVO>;

export interface CreateAssetRequest {
  name: string;
  code: string;
  target_type: string;
  target_source: string;
  description?: string;
  priority: string;
  team_id?: string;
  owner_id?: string;
}

export interface UpdateAssetRequest {
  name?: string;
  description?: string;
  priority?: string;
  status?: string;
}
