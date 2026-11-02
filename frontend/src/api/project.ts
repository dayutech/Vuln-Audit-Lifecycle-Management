import request from '@/utils/request';
import type {
  PhaseRequest,
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
