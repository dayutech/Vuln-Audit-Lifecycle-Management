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

export interface AssetMetadataVO {
  id: string;
  assetId: string;
  claimedBy?: string;
  version?: number;
  systemArchitecture?: string;
  installPath?: string;
  startupScriptPath?: string;
  configFilePath?: string;
  startupCommand?: string;
  programmingLanguage?: string;
  framework?: string;
  databaseType?: string;
  databaseConnection?: string;
  adminUsername?: string;
  adminPasswordEncrypted?: string;
  servicePorts?: string;
  apiEndpoints?: string;
  specialNotes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UpsertAssetMetadataRequest {
  systemArchitecture?: string;
  installPath?: string;
  startupScriptPath?: string;
  configFilePath?: string;
  startupCommand?: string;
  programmingLanguage?: string;
  framework?: string;
  databaseType?: string;
  databaseConnection?: string;
  adminUsername?: string;
  adminPasswordEncrypted?: string;
  servicePorts?: string;
  apiEndpoints?: string;
  specialNotes?: string;
}

export interface AssetFileVO {
  id: string;
  assetId: string;
  name: string;
  fileType: string;
  category: string;
  description?: string;
  fileSize?: number;
  checksum?: string;
  version?: number;
  status?: string;
  createdAt?: string;
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
