import type { PaginatedResponse } from './common';
import type { UserVO } from './user';

export interface AssetGroup {
  id: string;
  name: string;
}

/** 资产分组（后端 /asset-groups，type 用于区分客户/项目等归类维度） */
export interface AssetGroupVO {
  id: string;
  name: string;
  type: string;
  description?: string;
  createdAt?: string;
  created_at?: string;
}

export interface CreateAssetGroupRequest {
  name: string;
  type: string;
  description?: string;
}

/** 资产标签（后端 /assets/{id}/tags） */
export interface AssetTagVO {
  id: string;
  assetId?: string;
  asset_id?: string;
  tag: string;
  createdBy?: string | null;
  created_by?: string | null;
  createdAt?: string;
  created_at?: string;
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
  /** created / provisioning / running / stopped / failed */
  status: string;
  /** 部署/启停失败原因（后端 markFailed 写入） */
  failReason?: string | null;
  version?: number;
  containerId?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  /** 当前生效部署器类型：docker / noop（仅详情接口返回） */
  provisioner?: string;
  /** 部署器是否可用（false 时部署/启停一律 503） */
  provisionerAvailable?: boolean;
  /** 部署器说明（不可用时含原因） */
  provisionerMessage?: string;
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
  inherit_base_overview?: boolean;
  inherit_from_version_id?: string;
}

export type AssetListResponse = PaginatedResponse<AssetVO>;

/** 资产访问申请（与后端 AssetAccess 对齐，camelCase） */
export interface AssetAccessVO {
  id: string;
  assetId: string;
  userId: string;
  status: 'pending' | 'approved' | 'rejected' | string;
  canViewDocuments?: boolean;
  canViewEnvironments?: boolean;
  canViewFiles?: boolean;
  allowedFileTypes?: string | null;
  requestNote?: string | null;
  approverId?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  reviewNote?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/** 资产认领申请（与后端 AssetClaim 对齐） */
export interface AssetClaimVO {
  id: string;
  assetId: string;
  userId: string;
  status: 'pending' | 'approved' | 'rejected' | 'completed' | 'abandoned' | string;
  note?: string | null;
  claimedAt?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  reviewNote?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateAssetAccessRequest {
  can_view_documents?: boolean;
  can_view_environments?: boolean;
  can_view_files?: boolean;
  allowed_file_types?: string;
  request_note?: string;
}


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
