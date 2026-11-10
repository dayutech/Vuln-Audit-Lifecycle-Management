import request from '@/utils/request';
import type {
  PhaseRequest,
  ProjectSummaryQuery,
  ProjectSummaryVO,
  PhaseVO,
  ProjectAssetLinkVO,
  ProjectDetailVO,
  ProjectQuery,
  ProjectRequest,
  ProjectVO,
} from '@/types/project';

/**
 * 项目 / 期次 API（t72，对接后端 t68）。
 *
 * 后端契约（ProjectController / ProjectPhaseController）：
 * - 读：所有登录角色开放；写：ADMIN / PROJECT_LEADER 或该项目的经理（期次写按 phase → project → managerId 解析）
 * - 创建项目没有 id，只能 ADMIN / PROJECT_LEADER
 * - 分页参数 page 从 0 起、size；状态机 PATCH /projects/{id}/status?status=XXX
 * - 删除保护：项目 / 期次下仍有资产挂靠 → 409（message 带数量）
 *
 * 写操作一律 `silent`：状态机 400、删除保护 409、越权 403 都需要页面给专用文案
 * （见 pages/projects/index.tsx 的 describeProjectFailure / describePhaseFailure）。
 */

/* ==================== 项目 ==================== */

export const getProjectList = (params: ProjectQuery = {}): Promise<any> => {
  return request.get('/projects', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size ?? 10,
      customerId: params.customerId || undefined,
      status: params.status || undefined,
      type: params.type || undefined,
      keyword: params.keyword || undefined,
    },
  });
};

/**
 * GET /projects/summary —— 分页 + 每项带 phaseCount / assetCount / openTaskCount / managerName / customerName（t82）。
 *
 * 用这一个请求取代「先列表再逐项目查详情」的 N+1；sort 支持 createdAt（默认）/ assetCount。
 */
export const getProjectSummaryList = (params: ProjectSummaryQuery = {}): Promise<any> => {
  return request.get('/projects/summary', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size ?? 10,
      customerId: params.customerId || undefined,
      status: params.status || undefined,
      type: params.type || undefined,
      keyword: params.keyword || undefined,
      sort: params.sort || undefined,
    },
  });
};

export const getProjectDetail = (id: string): Promise<any> => {
  return request.get(`/projects/${id}`);
};

export const createProject = (data: ProjectRequest): Promise<any> => {
  return request.post('/projects', data, { silent: true } as any);
};

export const updateProject = (id: string, data: ProjectRequest): Promise<any> => {
  return request.put(`/projects/${id}`, data, { silent: true } as any);
};

/** PATCH /projects/{id}/status?status=ACTIVE —— 后端状态机拒绝非法流转返回 400 */
export const changeProjectStatus = (id: string, status: string): Promise<any> => {
  return request.patch(`/projects/${id}/status`, null, { params: { status }, silent: true } as any);
};

/** DELETE /projects/{id} —— 有资产挂靠时后端 409 */
export const deleteProject = (id: string): Promise<any> => {
  return request.delete(`/projects/${id}`, { silent: true } as any);
};

/* ==================== 期次 ==================== */

export const getProjectPhases = (projectId: string): Promise<any> => {
  return request.get(`/projects/${projectId}/phases`);
};

export const createProjectPhase = (projectId: string, data: PhaseRequest): Promise<any> => {
  return request.post(`/projects/${projectId}/phases`, data, { silent: true } as any);
};

export const updateProjectPhase = (phaseId: string, data: PhaseRequest): Promise<any> => {
  return request.put(`/phases/${phaseId}`, data, { silent: true } as any);
};

/** DELETE /phases/{phaseId} —— 有资产挂靠时后端 409 */
export const deleteProjectPhase = (phaseId: string): Promise<any> => {
  return request.delete(`/phases/${phaseId}`, { silent: true } as any);
};

/* ==================== 资产挂靠（后端 t69，未上线时 404） ==================== */

/** GET /projects/{id}/assets（后端 t69 ProjectAssetItem，分页）—— phaseId 可选 */
export const getProjectAssets = (
  projectId: string,
  params: { phaseId?: string; page?: number; page_size?: number } = {},
): Promise<any> => {
  return request.get(`/projects/${projectId}/assets`, {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size ?? 200,
      phaseId: params.phaseId || undefined,
    },
    silent: true,
  } as any);
};

/* ==================== 项目侧批量资产操作（t154，t155 前端接入） ==================== */

/** 可选资产条目（后端 t154 AvailableAssetItem） */
export interface AvailableAssetItem {
  id: string;
  code: string;
  name: string;
  targetType?: string | null;
  status?: string | null;
  /** 当前挂靠总数（0 = 孤儿资产，前端可提示） */
  linkCount: number;
}

/** 批量添加结果（后端 t154 BulkAddResult） */
export interface BulkAddAssetsResult {
  added: number;
  skipped: number;
  failed: Array<{ assetId?: string | null; code?: string | null; reason?: string | null }>;
}

export type ProjectAssetLinkType = 'TARGET' | 'SHARED' | 'REFERENCE';

export const PROJECT_ASSET_LINK_TYPES: ProjectAssetLinkType[] = ['TARGET', 'SHARED', 'REFERENCE'];

/**
 * `GET /projects/{projectId}/available-assets` —— 未挂靠本项目且调用者可见的资产（分页）。
 *
 * 注意：这是**唯一**的可选资产数据源；不要用 `GET /assets` 全量再前端过滤
 * （会把不可见资产也带出来，且 linkCount 口径不一致）。
 */
export const getProjectAvailableAssets = (
  projectId: string,
  params: { keyword?: string; page?: number; size?: number } = {},
): Promise<any> => {
  return request.get(`/projects/${projectId}/available-assets`, {
    params: {
      keyword: params.keyword || undefined,
      page: params.page ?? 0,
      size: params.size ?? 10,
    },
    silent: true,
  } as any);
};

export const extractAvailableAssetPage = (res: any): { items: AvailableAssetItem[]; total: number } => {
  const payload = payloadOf(res);
  const items = (payload?.content || payload?.items || (Array.isArray(payload) ? payload : [])) as AvailableAssetItem[];
  return {
    items: Array.isArray(items) ? items : [],
    total: payload?.totalElements ?? payload?.total ?? (Array.isArray(items) ? items.length : 0),
  };
};

/**
 * `POST /projects/{projectId}/assets` —— 批量添加（1–100 个）。
 *
 * 后端口径：**不整体失败** —— 不存在/已软删/无权限进 `failed`，已挂靠同期次进 `skipped`，
 * 其余照常写库。因此必须把汇总展示给用户，而不是只弹「成功」。
 *
 * 硬规则（README §68.3）：**禁止**循环调 `POST /assets/{id}/project-links` ——
 * 那种做法失败会「删一半」，也拿不到 skipped/failed 汇总。
 */
export const bulkAddProjectAssets = (
  projectId: string,
  body: { assetIds: string[]; phaseId?: string | null; linkType?: ProjectAssetLinkType },
): Promise<any> => {
  return request.post(`/projects/${projectId}/assets`, {
    assetIds: body.assetIds,
    phaseId: body.phaseId || null,
    linkType: body.linkType || 'TARGET',
  });
};

export const extractBulkAddResult = (res: any): BulkAddAssetsResult => {
  const payload = payloadOf(res);
  const failed = Array.isArray(payload?.failed) ? payload.failed : [];
  return {
    added: Number(payload?.added ?? 0),
    skipped: Number(payload?.skipped ?? 0),
    failed: failed.map((item: any) => ({
      assetId: item?.assetId ?? null,
      code: item?.code ?? null,
      reason: item?.reason ?? null,
    })),
  };
};

/**
 * `DELETE /projects/{projectId}/assets` —— 批量移除。
 *
 * 后端口径：**先全量校验后执行** —— 任一资产移除后会变成「零挂靠」→ 409 且**一条都不删**，
 * 因此收到 409 时清单必须保持不变，只展示后端消息（含资产编号）。
 */
export const bulkRemoveProjectAssets = (projectId: string, assetIds: string[]): Promise<any> => {
  return request.delete(`/projects/${projectId}/assets`, { data: { assetIds } } as any);
};

export const extractBulkRemoveCount = (res: any): number => {
  const payload = payloadOf(res);
  return Number(payload?.removed ?? 0);
};

/* ==================== 解包 ==================== */

const payloadOf = (res: any) => res?.data ?? res ?? {};

export const extractProjectPage = (res: any): { items: ProjectVO[]; total: number } => {
  const payload = payloadOf(res);
  const items = (payload?.content || payload?.items || []) as ProjectVO[];
  return {
    items: Array.isArray(items) ? items : [],
    total: payload?.totalElements ?? payload?.total ?? 0,
  };
};

export const extractProjectSummaryPage = (res: any): { items: ProjectSummaryVO[]; total: number } => {
  const payload = payloadOf(res);
  const items = (payload?.content || payload?.items || []) as ProjectSummaryVO[];
  return {
    items: Array.isArray(items) ? items : [],
    total: payload?.totalElements ?? payload?.total ?? 0,
  };
};

export const extractProjectDetail = (res: any): ProjectDetailVO | null => {
  const payload = payloadOf(res);
  return payload?.project ? (payload as ProjectDetailVO) : payload?.id ? ({ project: payload } as ProjectDetailVO) : null;
};

export const extractPhaseList = (res: any): PhaseVO[] => {
  const payload = payloadOf(res);
  const items = Array.isArray(payload) ? payload : payload?.content || payload?.items || [];
  return Array.isArray(items) ? (items as PhaseVO[]) : [];
};

export const extractProjectAssets = (res: any): ProjectAssetLinkVO[] => {
  const payload = payloadOf(res);
  const items = Array.isArray(payload) ? payload : payload?.content || payload?.items || [];
  return Array.isArray(items) ? (items as ProjectAssetLinkVO[]) : [];
};

/** 单个项目响应（创建/更新/PATCH 状态后） */
export const extractProject = (res: any): ProjectVO | null => {
  const payload = payloadOf(res);
  return payload?.id ? (payload as ProjectVO) : null;
};

export const extractPhase = (res: any): PhaseVO | null => {
  const payload = payloadOf(res);
  return payload?.id ? (payload as PhaseVO) : null;
};
