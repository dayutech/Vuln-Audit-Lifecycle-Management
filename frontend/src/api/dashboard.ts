import request from '@/utils/request';
import type {
  AssetStats,
  DashboardConfigPayload,
  ExportType,
  TrendResponse,
  VulnerabilityStats,
  WorkloadRankingItem,
  WorkloadStats,
} from '@/types/dashboard';

const unwrap = <T,>(res: any): T | null => (res && typeof res === 'object' && 'data' in res ? ((res.data as T) ?? null) : ((res as T) ?? null));

/* ==================== 统计 ==================== */

export const getDashboardOverview = async (): Promise<{ assets?: AssetStats; vulnerabilities?: VulnerabilityStats } | null> =>
  unwrap(await request.get('/dashboard/overview'));

/**
 * 统计类接口统一 silent：t29 起漏洞统计限管理层（工程师 403），
 * 403/5xx 由大盘按角色渲染「仅管理层可见」或分级提示，不再弹全局错误框。
 */
export const getAssetStats = async (): Promise<AssetStats | null> =>
  unwrap<AssetStats>(await request.get('/dashboard/assets/stats', { silent: true }));

export const getVulnerabilityStats = async (): Promise<VulnerabilityStats | null> =>
  unwrap<VulnerabilityStats>(await request.get('/dashboard/vulnerabilities/stats', { silent: true }));

export const getWorkloadStats = async (): Promise<WorkloadStats | null> =>
  unwrap<WorkloadStats>(await request.get('/dashboard/workload/stats', { silent: true }));

export const getAssetCountByStatus = async (): Promise<Record<string, number>> =>
  unwrap<Record<string, number>>(await request.get('/dashboard/assets/stats/status')) ?? {};

export const getAssetCountByType = async (): Promise<Record<string, number>> =>
  unwrap<Record<string, number>>(await request.get('/dashboard/assets/stats/type')) ?? {};

export const getAssetCountByPriority = async (): Promise<Record<string, number>> =>
  unwrap<Record<string, number>>(await request.get('/dashboard/assets/stats/priority')) ?? {};

export const getVulnerabilityCountBySeverity = async (): Promise<Record<string, number>> =>
  unwrap<Record<string, number>>(await request.get('/dashboard/vulnerabilities/stats/severity')) ?? {};

export const getVulnerabilityCountByWorkflowStatus = async (): Promise<Record<string, number>> =>
  unwrap<Record<string, number>>(await request.get('/dashboard/vulnerabilities/stats/workflow-status')) ?? {};

export const getTaskCountByStatus = async (): Promise<Record<string, number>> =>
  unwrap<Record<string, number>>(await request.get('/dashboard/workload/stats/tasks')) ?? {};

export const getClaimCountByStatus = async (): Promise<Record<string, number>> =>
  unwrap<Record<string, number>>(await request.get('/dashboard/workload/stats/claims')) ?? {};

/* ==================== 趋势 ==================== */

export const getTrend = async (
  kind: 'assets' | 'vulnerabilities' | 'workload',
  days = 30
): Promise<TrendResponse | null> =>
  // silent：趋势失败由 TrendChart 统一降级并给出分级提示，避免每条曲线弹一次全局错误
  unwrap<TrendResponse>(await request.get(`/dashboard/trends/${kind}`, { params: { days }, silent: true }));

/* ==================== 工作量排行 ==================== */

/**
 * 跨用户工作量排行（t20 新增）。
 *
 * silent：404（未部署）/ 403（角色边界）/ 5xx 的降级与提示由 WorkloadRank 统一处理。
 */
export const getWorkloadRanking = async (params: { limit?: number; days?: number } = {}): Promise<WorkloadRankingItem[]> => {
  const payload = unwrap<WorkloadRankingItem[]>(
    await request.get('/dashboard/workload/ranking', {
      params: { limit: params.limit ?? 10, days: params.days ?? 30 },
      silent: true,
    })
  );
  return Array.isArray(payload) ? payload : [];
};

/* ==================== 布局配置 ==================== */

export const getDashboardConfig = async (): Promise<DashboardConfigPayload | null> =>
  unwrap<DashboardConfigPayload>(await request.get('/dashboard/config'));

export const saveDashboardConfig = (payload: DashboardConfigPayload): Promise<any> =>
  request.post('/dashboard/config', payload);

export const deleteDashboardConfig = (): Promise<any> => request.delete('/dashboard/config');

/* ==================== 导出 ==================== */

/**
 * 导出统计报表。
 *
 * 后端返回 text/csv（带 UTF-8 BOM）；错误时返回 JSON（400），
 * 因此这里以 blob 取回，由调用方判断 content-type 后决定下载还是提示。
 */
export const exportDashboardReport = (type: ExportType, format: 'csv' = 'csv'): Promise<Blob> =>
  request.get('/dashboard/export', { params: { type, format }, responseType: 'blob' });

/* ==================== 排行/趋势兜底用的列表读取 ==================== */

export const listAssetsForDashboard = async (size = 200): Promise<any[]> => {
  const payload: any = unwrap(await request.get('/assets', { params: { page: 0, size } }));
  return Array.isArray(payload?.content) ? payload.content : Array.isArray(payload?.items) ? payload.items : [];
};

export const listVulnerabilitiesForDashboard = async (size = 200): Promise<any[]> => {
  const payload: any = unwrap(await request.get('/vulnerabilities', { params: { page: 0, size } }));
  return Array.isArray(payload?.content) ? payload.content : Array.isArray(payload?.items) ? payload.items : [];
};

export const listTasksForDashboard = async (size = 200): Promise<any[]> => {
  const payload: any = unwrap(await request.get('/tasks', { params: { page: 0, size } }));
  return Array.isArray(payload?.content) ? payload.content : Array.isArray(payload?.items) ? payload.items : [];
};
