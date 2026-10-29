import type { PaginatedResponse } from './common';

/**
 * 工具资源（`tools` 表）。
 *
 * 后端字段为 camelCase：`repositoryUrl` / `installCommand` / `filePath` /
 * `isOfficial` / `downloadCount` / `createdBy` / `createdAt`。
 * 注意：`createdBy` 是用户 UUID（不是嵌套对象），页面展示时按用户表映射。
 */
export interface ToolVO {
  id: string;
  name: string;
  category: string;
  description?: string;
  repositoryUrl?: string;
  documentationUrl?: string;
  installCommand?: string;
  usageExample?: string;
  /** 逗号分隔的历史标签字符串（真实标签以 GET /tools/{id}/tags 为准） */
  tags?: string;
  /** 工具包文件名；为空表示该工具仅有外链 */
  fileName?: string;
  filePath?: string;
  fileSize?: number;
  isOfficial?: boolean;
  downloadCount?: number;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;

  /** @deprecated 后端已不使用；仅为兼容历史代码保留 */
  created_at?: string;
  /** @deprecated 后端已不使用 */
  download_count?: number;
  /** @deprecated 后端已不使用 */
  is_official?: boolean;
}

export type ToolListResponse = PaginatedResponse<ToolVO>;

/**
 * 工具版本（`tool_versions` 表 + 后端派生字段）。
 *
 * t23 之后后端补充：
 * - `versionNo`：同工具下自增序号（1 起），(tool_id, version_no) 唯一，冲突返回 409；
 * - `fileName` / `fileSize` / `filePath`：真实落盘文件信息，仅元数据版本为空；
 * - `hasFile`：派生属性（filePath 非空），仅元数据版本为 false；
 * - `createdBy`：上传者用户 ID（后端用 CurrentUserProvider 写入，不再为空）。
 */
export interface ToolVersionVO {
  id: string;
  toolId: string;
  /** 人类可读版本号（如 v1 / 1.2.0；留空时后端自动生成 vN） */
  version: string;
  /** 版本序号（1 起）；历史数据/未升级的后端可能缺失 */
  versionNo?: number;
  fileName?: string;
  fileSize?: number;
  filePath?: string;
  /** 是否含可下载文件（仅元数据版本为 false） */
  hasFile?: boolean;
  changelog?: string;
  createdBy?: string;
  createdAt?: string;
}

/** 版本上传参数（multipart：file；query：version / changelog） */
export interface UploadToolVersionPayload {
  /** 留空则由后端自动生成 vN */
  version?: string;
  changelog?: string;
  file?: File | Blob;
  fileName?: string;
}

export interface CreateToolRequest {
  name: string;
  category: string;
  description?: string;
  repositoryUrl?: string;
  documentationUrl?: string;
  installCommand?: string;
  usageExample?: string;
}

/** 版本上传所需参数（multipart：file；query：version / changelog） */
export interface UploadToolVersionPayload {
  /** 留空则由后端自动生成 vN */
  version?: string;
  changelog?: string;
  file?: File | Blob;
  fileName?: string;
}
