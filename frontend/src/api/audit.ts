import request from '@/utils/request';
import type { AuditLogPage, AuditLogQuery, AuditLogVO } from '@/types/audit';

const unwrap = <T,>(res: any): T | null => (res && typeof res === 'object' && 'data' in res ? ((res.data as T) ?? null) : ((res as T) ?? null));

/** 把 Query 转成后端参数（page 1 基 → 0 基，空值不下发） */
const toParams = (query: AuditLogQuery) => ({
  page: Math.max((query.page ?? 1) - 1, 0),
  size: query.page_size ?? 20,
  userId: query.user_id || undefined,
  action: query.action || undefined,
  resourceType: query.resource_type || undefined,
  startTime: query.start_time || undefined,
  endTime: query.end_time || undefined,
  keyword: query.keyword?.trim() || undefined,
});

export const getAuditLogList = async (query: AuditLogQuery = {}): Promise<AuditLogPage> => {
  // silent：403 由页面渲染完整的「无权限」结果页，不用再弹全局提示
  const payload = unwrap<AuditLogPage>(await request.get('/audit-logs', { params: toParams(query), silent: true }));
  return {
    content: Array.isArray(payload?.content) ? payload!.content : [],
    totalElements: payload?.totalElements ?? 0,
    totalPages: payload?.totalPages ?? 0,
    currentPage: payload?.currentPage ?? 0,
    pageSize: payload?.pageSize ?? (query.page_size ?? 20),
  };
};

export const getAuditLogDetail = async (id: string): Promise<AuditLogVO | null> =>
  unwrap<AuditLogVO>(await request.get(`/audit-logs/${id}`, { silent: true }));

/**
 * 导出审计日志 CSV。
 *
 * 后端返回 text/csv（UTF-8 BOM）；旧版本部署未挂载 /export 时会被 /{id} 路由吞掉并返回
 * 400 JSON（"参数格式错误: id"），因此这里以 blob 取回，由调用方按 content-type 分流。
 * silent：错误体是 Blob，全局拦截器读不到 message，统一交给调用方解析后端原始提示。
 */
export const exportAuditLogs = (query: AuditLogQuery = {}): Promise<Blob> => {
  const { page: _page, size: _size, ...params } = toParams(query);
  return request.get('/audit-logs/export', { params, responseType: 'blob', silent: true });
};
