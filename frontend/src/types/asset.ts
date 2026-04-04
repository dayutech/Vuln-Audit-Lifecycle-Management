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
  team_id?: string;
  teamId?: string;
  team_name?: string;
  teamName?: string;
  target_type: string;
  targetType?: string;
  target_source: string;
  targetSource?: string;
  description?: string;
  priority: string;
  status: string;
  owner_id?: string;
  ownerId?: string;
  owner_name?: string;
  ownerName?: string;
  groups?: AssetGroup[];
  owner?: UserVO;
  team?: any;
  vulnerability_count?: number;
  created_at: string;
  createdAt?: string;
  updated_at?: string;
  updatedAt?: string;
}

export interface AssetMetadataVO {
  id: string;
  assetId: string;
  assetVersionId?: string;
  claimedBy?: string;
  version?: number;
  systemArchitecture?: string;
  runtimePlatform?: string;
  deploymentMethod?: string;
  programmingLanguagesJson?: string;
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
  accountCredentialsJson?: string;
  servicePorts?: string;
  apiEndpoints?: string;
  specialNotes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UpsertAssetMetadataRequest {
  systemArchitecture?: string;
  runtimePlatform?: string;
  deploymentMethod?: string;
  programmingLanguagesJson?: string;
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
  accountCredentialsJson?: string;
  servicePorts?: string;
  apiEndpoints?: string;
  specialNotes?: string;
}

export interface AssetFileVO {
  id: string;
  assetId: string;
  assetVersionId?: string;
  name: string;
  category: string;
  description?: string;
  fileSize?: number;
  checksum?: string;
  status?: string;
  createdAt?: string;
}

export interface AssetDeliverableVO {
  id: string;
  assetId: string;
  assetVersionId?: string;
  name: string;
  deliverableType: string;
  description?: string;
  fileSize?: number;
  checksum?: string;
  status?: string;
  createdAt?: string;
}

export interface EnvironmentVO {
  id: string;
  name: string;
  assetId: string;
  assetVersionId?: string;
  templateId: string;
  config: string;
  status: string;
  version?: number;
  containerId?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateEnvironmentRequest {
  name: string;
  asset_id: string;
  asset_version_id?: string;
  template_id: string;
  config: string;
}

export interface UpdateEnvironmentRequest {
  name?: string;
  asset_version_id?: string;
  template_id?: string;
  config?: string;
  status?: string;
}

export interface AssetVersionVO {
  id: string;
  assetId: string;
  versionNo: number;
  name: string;
  description?: string;
  isDefault: boolean;
  isLatest?: boolean;
  status: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateAssetVersionRequest {
  name?: string;
  description?: string;
  status?: string;
}

export type AssetListResponse = PaginatedResponse<AssetVO>;

export interface CreateAssetRequest {
  name: string;
  target_type: string;
  target_source: string;
  description?: string;
  priority: string;
  status: string;
  team_id: string;
}

export interface UpdateAssetRequest {
  name?: string;
  description?: string;
  priority?: string;
  status?: string;
  target_type?: string;
  target_source?: string;
  team_id?: string;
  owner_id?: string;
}
