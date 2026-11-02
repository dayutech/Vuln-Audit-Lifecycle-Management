import request from '@/utils/request';
import type {
  CustomerVO,
  CustomerDetailVO,
  CustomerCreateRequest,
  CustomerUpdateRequest,
  CustomerQueryParams,
  ProjectVO,
  ProjectDetailVO,
  CustomerSummaryVO,
  CustomerAssetVO,
} from '@/types/customer';

/**
 * 客户 / 项目 / 期次 API（t71，对接后端 t68）。
 *
 * 后端契约（CustomerController / ProjectController）：
 * - 读：所有登录角色开放；写：ADMIN / PROJECT_LEADER（客户删除同样是 ADMIN/PL）
 * - 删除保护：客户下仍有项目 → 409；项目/期次下仍有资产挂靠 → 409
 * - 分页参数是 page(从 0) / size
 */
export const getCustomerList = (params: CustomerQueryParams & { size?: number }): Promise<any> => {
  return request.get('/customers', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      // 兼容项目页（t72）的 size 写法与本页的 page_size 写法
      size: params.size ?? params.page_size,
      keyword: params.keyword || undefined,
      status: params.status || undefined,
      level: params.level || undefined,
    },
  });
};

export const getCustomerDetail = (id: string): Promise<CustomerDetailVO> => {
  return request.get(`/customers/${id}`);
};

export const createCustomer = (data: CustomerCreateRequest): Promise<CustomerVO> => {
  return request.post('/customers', data);
};

export const updateCustomer = (id: string, data: CustomerUpdateRequest): Promise<CustomerVO> => {
  return request.put(`/customers/${id}`, data);
};

/** PATCH /customers/{id}/status —— active ↔ inactive */
export const changeCustomerStatus = (id: string, status: 'active' | 'inactive'): Promise<CustomerVO> => {
  // 后端 PATCH /customers/{id}/status 用 query 参数 status（不是请求体），见 CustomerController
  return request.patch(`/customers/${id}/status`, null, { params: { status } });
};

/** DELETE /customers/{id} —— 客户下仍有项目时后端返回 409 */
export const deleteCustomer = (id: string): Promise<any> => {
  return request.delete(`/customers/${id}`);
};

/* ==================== 客户详情页所需的项目数据 ==================== */

/** GET /projects?customerId=... —— 该客户下的项目列表 */
export const getProjectsByCustomer = (
  customerId: string,
  params: { page?: number; page_size?: number; status?: string; keyword?: string } = {},
): Promise<any> => {
  return request.get('/projects', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.page_size ?? 50,
      customerId,
      status: params.status || undefined,
      keyword: params.keyword || undefined,
    },
  });
};

/** GET /projects/{id} —— 项目详情（取 phases.length 与 assetCount 作为「期次数 / 资产数」） */
export const getProjectDetail = (id: string): Promise<ProjectDetailVO> => {
  return request.get(`/projects/${id}`);
};

/* ==================== 统一解包 ==================== */

export const extractCustomerPage = (res: any): { items: CustomerVO[]; total: number } => {
  const payload = res?.data ?? res ?? {};
  const items = (payload.content || payload.items || []) as CustomerVO[];
  return { items: Array.isArray(items) ? items : [], total: payload.totalElements ?? payload.total ?? 0 };
};

export const extractProjectList = (res: any): ProjectVO[] => {
  const payload = res?.data ?? res ?? {};
  const items = payload.content || payload.items || (Array.isArray(payload) ? payload : []);
  return Array.isArray(items) ? (items as ProjectVO[]) : [];
};

/**
 * 客户下拉选项（t72 项目页「所属客户」使用）：返回 {items,total}，调用方自行 map。
 * silent=true：下拉场景不弹全局错误提示，由调用方决定兜底文案。
 */
export const getCustomerOptions = (keyword?: string, limit = 200): Promise<any> => {
  return request.get('/customers', {
    params: { page: 0, size: limit, keyword: keyword || undefined },
    silent: true,
  } as any);
};

/* ==================== t77：客户详情聚合 + 资产列表 ==================== */

/** GET /customers/{id}/summary —— 一次拿全 customer + stats + projects(含计数) + recentAssets */
export const getCustomerSummary = (id: string): Promise<CustomerSummaryVO> => {
  return request.get(`/customers/${id}/summary`);
};

/** GET /assets?customerId=&page=&size= —— 客户下资产分页（t69 提供） */
export const getCustomerAssets = (
  customerId: string,
  params: { page?: number; page_size?: number; size?: number; keyword?: string } = {},
): Promise<any> => {
  return request.get('/assets', {
    params: {
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.size ?? params.page_size ?? 10,
      customerId,
      keyword: params.keyword || undefined,
    },
  });
};

export const extractSummary = (res: any): CustomerSummaryVO | null => {
  const payload = res?.data ?? res;
  return payload && payload.customer ? (payload as CustomerSummaryVO) : null;
};

/** 资产分页解包（兼容 PageResponse 的 content/totalElements 与 items/total） */
export const extractCustomerAssetPage = (
  res: any,
): { items: CustomerAssetVO[]; total: number } => {
  const payload = res?.data ?? res ?? {};
  const items = (payload.content || payload.items || []) as CustomerAssetVO[];
  return {
    items: Array.isArray(items) ? items : [],
    total: payload.totalElements ?? payload.total ?? 0,
  };
};
