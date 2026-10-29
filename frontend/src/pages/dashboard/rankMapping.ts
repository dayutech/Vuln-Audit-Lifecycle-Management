import type { WorkloadRankingItem } from '@/types/dashboard';

/** 排行表格行（服务端 / 本地兜底共用同一渲染结构） */
export interface RankRow {
  key: string;
  assignee: string;
  total: number;
  estimatedHours: number;
  actualHours: number;
  pending?: number;
  inProgress?: number;
  completed?: number;
}

export const toNumber = (value: unknown): number => {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
};

/**
 * 服务端排行 → 表格行。
 *
 * t20 契约：GET /dashboard/workload/ranking 返回 [{userId,username,taskCount,estimatedHours,actualHours}]，
 * 已由后端按 taskCount 倒序（同数 username 升序），前端不再重排，只做字段映射与数值兜底。
 */
export const mapServerRows = (items: WorkloadRankingItem[]): RankRow[] =>
  (Array.isArray(items) ? items : []).map((item, index) => ({
    key: String(item?.userId ?? item?.username ?? `row-${index}`),
    assignee: item?.username || String(item?.userId ?? '未知用户'),
    total: toNumber(item?.taskCount),
    estimatedHours: toNumber(item?.estimatedHours),
    actualHours: toNumber(item?.actualHours),
  }));

/** 本地兜底：按 /tasks 的负责人聚合（仅在后端排行接口不可用时使用） */
export const buildLocalRows = (tasks: any[]): RankRow[] => {
  const map = new Map<string, RankRow>();
  (Array.isArray(tasks) ? tasks : []).forEach((task) => {
    const assigneeId = task?.assignedTo ?? task?.assigned_to ?? task?.assignee?.id ?? 'unassigned';
    const assigneeName =
      task?.assignee?.fullName ?? task?.assignee?.full_name ?? task?.assignee?.username ?? task?.assigneeName ?? '未分配';
    const key = String(assigneeId);
    const row =
      map.get(key) ??
      ({
        key,
        assignee: assigneeName,
        total: 0,
        estimatedHours: 0,
        actualHours: 0,
        pending: 0,
        inProgress: 0,
        completed: 0,
      } as RankRow);
    row.total += 1;
    const status = String(task?.status ?? '').toLowerCase();
    if (status === 'pending') row.pending = (row.pending ?? 0) + 1;
    else if (status === 'in_progress') row.inProgress = (row.inProgress ?? 0) + 1;
    else if (status === 'completed') row.completed = (row.completed ?? 0) + 1;
    row.estimatedHours += toNumber(task?.estimatedHours);
    row.actualHours += toNumber(task?.actualHours);
    map.set(key, row);
  });
  return Array.from(map.values()).sort(
    (a, b) => b.total - a.total || (b.completed ?? 0) - (a.completed ?? 0)
  );
};
