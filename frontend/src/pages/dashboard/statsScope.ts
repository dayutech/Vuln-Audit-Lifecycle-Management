import type { MyAccessProfile } from '@/api/permission';

/**
 * 仪表盘统计口径（t29）。
 *
 * 后端口径：
 * - `/dashboard/assets/stats*` 对所有角色 200，但数值收敛到「当前可见资产范围」；
 * - `/dashboard/vulnerabilities/stats*` 仅 admin / project_leader / team_leader 可访问（工程师 403），
 *   且数值同样收敛到各自可见范围（团队长=本团队，管理员/项目负责人=全量）。
 *
 * 前端据此把卡片口径标成「全量」或「当前可见范围」，并在无权限时给出「仅管理层可见」，
 * 避免把 403 或收敛后的较小数值误判成「数据缺失」。
 */
export type StatsScopeTone = 'full' | 'scoped' | 'unknown';

export interface StatsScopeInfo {
  tone: StatsScopeTone;
  /** 用于卡片角标的文案 */
  label: string;
  /** 该角色是否能访问漏洞统计接口 */
  canViewVulnStats: boolean;
  /** 角色说明，用于无权限提示 */
  roleLabel: string;
}

const ROLE_LABELS: Record<string, string> = {
  admin: '系统管理员',
  project_leader: '项目负责人',
  team_leader: '小组长',
  vulnerability_engineer: '漏洞挖掘工程师',
};

export const resolveStatsScope = (profile: MyAccessProfile | null): StatsScopeInfo => {
  const roles = profile?.roles ?? [];
  const roleLabel = roles.length
    ? roles.map((role) => ROLE_LABELS[role] ?? role).join('、')
    : '未识别角色';

  if (roles.includes('admin') || roles.includes('project_leader')) {
    return { tone: 'full', label: '全量', canViewVulnStats: true, roleLabel };
  }
  if (roles.includes('team_leader')) {
    return { tone: 'scoped', label: '当前可见范围（本团队）', canViewVulnStats: true, roleLabel };
  }
  // 画像未返回（接口失败/新账号无角色）时按“未知”处理：允许发起请求，403 再兜底
  if (roles.length === 0) {
    return { tone: 'unknown', label: '当前可见范围', canViewVulnStats: true, roleLabel };
  }
  return { tone: 'scoped', label: '当前可见范围', canViewVulnStats: false, roleLabel };
};

/** 角标颜色：全量=蓝、可见范围=默认灰、未知=灰 */
export const statsScopeTagColor = (tone: StatsScopeTone): string => (tone === 'full' ? 'blue' : 'default');

/**
 * 报表导出权限（/dashboard/export 与权限矩阵 2.13 一致：仅 admin / project_leader）。
 * 团队长也不可见，因此导出入口按此直接隐藏，而不是点了才 403。
 */
export const canExportReports = (profile: MyAccessProfile | null): boolean =>
  Boolean(profile?.roles?.some((role) => role === 'admin' || role === 'project_leader'));
