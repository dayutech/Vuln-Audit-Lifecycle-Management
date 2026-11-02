/**
 * 客户 / 项目 / 期次 视图类型（t71，对应后端 t68 的 customer/project/phase DTO）。
 *
 * 字段命名以后端 camelCase 为准；列表接口只返回客户本体，
 * 聚合口径（projectCount / activeProjectCount / assetCount）在详情接口 `GET /customers/{id}` 里。
 */

export interface CustomerVO {
  id: string;
  code: string;
  name: string;
  industry?: string | null;
  /** 客户级别：A / B / C（后端为自由字符串，前端按 A/B/C 配色渲染） */
  level?: string | null;
  contactName?: string | null;
  contactPhone?: string | null;
  contactEmail?: string | null;
  /** 客户负责人（前端人员下拉走 /users/directory） */
  ownerId?: string | null;
  /** active / inactive */
  status?: string | null;
  description?: string | null;
  createdBy?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** 详情：客户本体 + 聚合计数（后端 CustomerDetailResponse） */
export interface CustomerDetailVO {
  customer: CustomerVO;
  projectCount: number;
  activeProjectCount: number;
  assetCount: number;
}

export interface CustomerCreateRequest {
  code: string;
  name: string;
  industry?: string;
  level?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  ownerId?: string | null;
  status?: string;
  description?: string;
}

export type CustomerUpdateRequest = CustomerCreateRequest;

export interface CustomerQueryParams {
  page?: number;
  page_size?: number;
  keyword?: string;
  status?: string;
  level?: string;
}

/** 项目视图（ProjectResponse 子集：列表需要的字段） */
export interface ProjectVO {
  id: string;
  code: string;
  customerId: string;
  customerName?: string | null;
  name: string;
  type?: string | null;
  status?: string | null;
  managerId?: string | null;
  managerName?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  isPhased?: boolean | null;
  contractNo?: string | null;
  description?: string | null;
}

export interface PhaseVO {
  id: string;
  projectId: string;
  phaseNo?: number | null;
  name?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  status?: string | null;
}

/** 项目详情（ProjectDetailResponse）：用于在客户详情里补「期次数 / 资产数」 */
export interface ProjectDetailVO {
  project: ProjectVO;
  customer?: { id: string; code: string; name: string } | null;
  phases?: PhaseVO[];
  assetCount?: number;
}

/**
 * 客户下拉/筛选查询（t72 项目管理页复用）。
 * 兼容两种分页字段写法：page_size（本页约定）与 size（后端原始名）。
 */
export type CustomerQuery = {
  page?: number;
  page_size?: number;
  size?: number;
  keyword?: string;
  status?: string;
  level?: string;
};

/** 客户下拉的最小形状（项目页只依赖 id/code/name，其余字段可空） */
export type CustomerBriefVO = Pick<CustomerVO, 'id' | 'code' | 'name'> &
  Partial<Pick<CustomerVO, 'industry' | 'level' | 'status' | 'ownerId'>>;

/* ==================== t77：客户详情聚合（t76） ==================== */

export interface CustomerStatsVO {
  projectCount: number;
  activeProjectCount: number;
  phasedProjectCount: number;
  assetCount: number;
  vulnerabilityCount: number;
}

/** 项目摘要（summary.projects 元素；phaseCount / assetCount 由后端 group by 一次算出） */
export interface CustomerProjectSummaryVO {
  id: string;
  code: string;
  name: string;
  type?: string | null;
  status?: string | null;
  isPhased?: boolean | null;
  phaseCount: number;
  assetCount: number;
  managerName?: string | null;
}

/** 最近资产（summary.recentAssets 元素；projectNames 为本客户下的项目名，可多个） */
export interface CustomerRecentAssetVO {
  id: string;
  code: string;
  name: string;
  targetType?: string | null;
  status?: string | null;
  projectNames?: string[];
}

/** GET /customers/{id}/summary 响应 */
export interface CustomerSummaryVO {
  customer: CustomerVO;
  stats: CustomerStatsVO;
  projects: CustomerProjectSummaryVO[];
  recentAssets: CustomerRecentAssetVO[];
}

/** GET /assets?customerId= 的行（t69：带 customers/projects 摘要，无 projectLinks） */
export interface CustomerAssetVO {
  id: string;
  code: string;
  name: string;
  targetType?: string | null;
  status?: string | null;
  priority?: string | null;
  ownerName?: string | null;
  projects?: Array<{ id?: string; code?: string; name?: string }>;
}
