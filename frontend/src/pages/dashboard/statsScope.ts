/**
 * 仪表盘统计口径（t29/t30）。
 *
 * 实现已统一收敛到 `@/utils/accessRoles`（纯函数、无运行时依赖），
 * 这里保留原路径的再导出，避免页面 import 变更。
 */
export {
  resolveStatsScope,
  statsScopeTagColor,
  canExportReports,
  MANAGEMENT_ROLES,
  isManagementProfile,
} from '@/utils/accessRoles';
export type { StatsScopeInfo, StatsScopeTone, MyAccessProfile } from '@/utils/accessRoles';
