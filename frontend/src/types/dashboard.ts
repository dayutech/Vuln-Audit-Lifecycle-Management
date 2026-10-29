/**
 * 仪表盘相关类型。
 *
 * 契约来源：backend DashboardController（/api/v1/dashboard），实测 2026-10-29：
 * - GET /dashboard/assets/stats → { total, by_status, by_type, by_priority }
 * - GET /dashboard/vulnerabilities/stats → { total, by_severity, by_workflow_status, by_customer_status, by_disclosure_status }
 * - GET /dashboard/workload/stats → { tasks: {pending,in_progress,completed}, claims: {pending,approved,completed} }
 * - GET /dashboard/trends/{assets|vulnerabilities|workload}?days=N → { days, data: [...] }（当前后端为占位实现，data 为空数组）
 * - GET /dashboard/config → 无配置时 data 字段被 @JsonInclude(NON_NULL) 省略（前端按默认布局兜底）
 * - POST /dashboard/config ← { layout, cards }（两者均为 JSON 字符串，且数据库列 NOT NULL）
 * - GET /dashboard/export?type=assets|vulnerabilities|workload&format=csv → text/csv（含 UTF-8 BOM）
 */

export type CountMap = Record<string, number>;

export interface AssetStats {
  total: number;
  by_status: CountMap;
  by_type: CountMap;
  by_priority: CountMap;
}

export interface VulnerabilityStats {
  total: number;
  by_severity: CountMap;
  by_workflow_status: CountMap;
  by_customer_status?: CountMap;
  by_disclosure_status?: CountMap;
}

export interface WorkloadStats {
  tasks: CountMap;
  claims: CountMap;
}

/** /dashboard/trends/* 的原始响应（data 元素结构由后端决定，前端做兼容归一） */
export interface TrendResponse {
  days?: number;
  userId?: string;
  data?: unknown[];
}

/** 归一到图表用的时间序列点 */
export interface TrendPoint {
  date: string;
  label: string;
  assets: number;
  vulnerabilities: number;
  workload: number;
}

/** 仪表盘卡片定义 */
export interface DashboardCardDef {
  id: string;
  title: string;
  /** 默认栅格宽度（24 栅格制） */
  defaultSpan: number;
  description?: string;
}

export interface DashboardCardLayout {
  id: string;
  span: number;
  visible: boolean;
}

/** 后端 dashboard_configs 的两列（均为 JSON 字符串） */
export interface DashboardConfigPayload {
  layout: string;
  cards: string;
}

/** 任务排行聚合行（本地兜底口径，仅在后端排行接口不可用时使用） */
export interface WorkloadRankRow {
  key: string;
  assignee: string;
  total: number;
  pending: number;
  inProgress: number;
  completed: number;
  estimatedHours: number;
  actualHours: number;
}

/**
 * 跨用户工作量排行项（t20 新增 GET /dashboard/workload/ranking）。
 *
 * 契约：按 taskCount 倒序（同数 username 升序）；limit<=0→10、>100→100；
 * 数据边界：admin / project_leader / team_leader 返回全量，其余角色只返回自己一行
 * （无任务时也返回 taskCount=0）。
 */
export interface WorkloadRankingItem {
  userId: string;
  username: string;
  taskCount: number;
  estimatedHours: number;
  actualHours: number;
}

export type ExportType = 'assets' | 'vulnerabilities' | 'workload';
