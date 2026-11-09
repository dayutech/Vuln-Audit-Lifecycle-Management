import request from '@/utils/request';

/**
 * 软删管理（t143 前端，后端 t140）：含已删列表 + 恢复。
 *
 * - `GET  /api/v1/admin/{customers|projects|assets}?includeDeleted=&keyword=&page=&size=`（**仅 ADMIN**）
 *   → `PageResponse<{id, code, name, deletedAt, deletedBy}>`；
 * - `POST /api/v1/admin/{customers|projects|assets}/{id}/restore` → **204**，幂等（未删对象也 204，但不写审计）。
 *
 * 前端只在「显示已删除」打开时切到这些端点：普通列表接口本来就不返回软删行。
 */

export type SoftDeleteKind = 'customers' | 'projects' | 'assets';

export interface SoftDeletedItem {
  id: string;
  code?: string | null;
  name?: string | null;
  deletedAt?: string | null;
  deletedBy?: string | null;
}

const unwrap = (res: unknown): any => (res as any)?.data ?? res;

export const listSoftDeletable = async (
  kind: SoftDeleteKind,
  params: { keyword?: string; includeDeleted?: boolean; page?: number; page_size?: number } = {},
) => {
  const res = await request.get(`/admin/${kind}`, {
    params: {
      keyword: params.keyword || undefined,
      includeDeleted: Boolean(params.includeDeleted),
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size ?? 20,
    },
    silent: true,
  });
  const payload = unwrap(res) ?? {};
  return {
    items: (Array.isArray(payload.content) ? payload.content : []) as SoftDeletedItem[],
    total: Number(payload.totalElements ?? 0) || 0,
  };
};

/** 恢复（204；404 表示 id 不存在） */
export const restoreSoftDeleted = async (kind: SoftDeleteKind, id: string): Promise<void> => {
  await request.post(`/admin/${kind}/${id}/restore`, null, { silent: true });
};

/** 已删除行的视觉标记：返回是否已删（后端 `deletedAt` 非空即已删） */
export const isDeletedRow = (row: { deletedAt?: string | null; deleted_at?: string | null }): boolean =>
  Boolean(row?.deletedAt ?? row?.deleted_at);
