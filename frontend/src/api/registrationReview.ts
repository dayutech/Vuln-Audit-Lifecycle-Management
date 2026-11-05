import request from '@/utils/request';
import { getApiErrorMessage } from '@/utils/errorMessage';

/**
 * 注册审批（t114，对接 t102 列表/审批/拒绝 + t111 角色显式化与清理端点）。
 *
 * 后端契约（`RegistrationRequestAdminController`，`/api/v1/admin/registration-requests`）：
 * - `GET    /` → `PageResponse<RegistrationRequestVO>`；`status` 可选 `PENDING|APPROVED|REJECTED`，`page` 从 **0** 开始
 *   - **没有 keyword 参数**：关键词只能在前端对当前页做过滤（见 `filterRegistrationRequests`）
 * - `POST   /{id}/approve` body `{roles: string[]}` → `{request,userId,username,loginEnabled}`
 *   - `roles` 空数组/省略 = **建号但不授角色**（登录后只有默认 ROLE_USER）；后端对未知角色码返回 400
 *   - 已处理过的申请 → 409；模拟视角下 `/admin/**` 写方法被守卫 403
 * - `POST   /{id}/reject` body `{note}`（后端 1–500 字，前端按任务口径限制 ≤200）→ 申请置 REJECTED
 *   - t111：拒绝会**释放**占位用户的用户名/手机号唯一键，因此同一手机号可重新申请
 * - `DELETE /{id}`（仅 ADMIN）→ 204，幂等；已通过（APPROVED）→ 409
 *
 * 统一 `silent: true`：失败提示由页面按状态码分级（400/403/409/5xx），避免拦截器重复弹。
 */

export type RegistrationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 注册申请视图（不含任何密码字段） */
export interface RegistrationRequestVO {
  id: string;
  username: string;
  phone?: string | null;
  email?: string | null;
  phoneVerified: boolean;
  emailVerified: boolean;
  status: RegistrationStatus | string;
  reviewerId?: string | null;
  reviewNote?: string | null;
  createdAt?: string | null;
  reviewedAt?: string | null;
  /** LOCAL（自助注册）/ SSO（第三方首次登录） */
  source?: string | null;
  providerCode?: string | null;
  externalId?: string | null;
  displayName?: string | null;
}

export interface RegistrationPage {
  content: RegistrationRequestVO[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

export interface ApproveRegistrationResult {
  request: RegistrationRequestVO;
  userId: string;
  username: string;
  loginEnabled: boolean;
}

const unwrap = (res: unknown): any => (res as any)?.data ?? res;

/**
 * 审批/拒绝/清理成功后派发的 window 事件名。
 *
 * 侧边栏角标（`BasicLayout`）监听它立即刷新待审批数量，
 * 避免"批完还要整页刷新才看到角标变化"。
 */
export const REGISTRATION_REVIEW_CHANGED_EVENT = 'registration-review:changed';

const toPage = (res: unknown): RegistrationPage => {
  const payload = unwrap(res) ?? {};
  const content = Array.isArray(payload.content) ? payload.content : [];
  return {
    content: content as RegistrationRequestVO[],
    totalElements: Number(payload.totalElements ?? content.length) || 0,
    totalPages: Number(payload.totalPages ?? 0) || 0,
    currentPage: Number(payload.currentPage ?? 0) || 0,
    pageSize: Number(payload.pageSize ?? content.length) || 0,
  };
};

export interface ListRegistrationParams {
  status?: RegistrationStatus | '';
  /** 1-based（页面用）；下发时转 0-based */
  page?: number;
  size?: number;
}

/** 分页查询注册申请 */
export const listRegistrationRequests = async (params: ListRegistrationParams = {}): Promise<RegistrationPage> => {
  const res = await request.get('/admin/registration-requests', {
    params: {
      status: params.status || undefined,
      page: Math.max((params.page ?? 1) - 1, 0),
      size: params.size ?? 20,
    },
    silent: true,
  });
  return toPage(res);
};

/**
 * 待审批数量（侧边栏角标）。
 *
 * 直接复用列表接口：`status=PENDING&size=1` 只为拿 `totalElements`。
 * 静默 + 吞错 → 失败返回 null（角标不显示，而不是显示 0 误导）。
 */
export const getPendingRegistrationCount = async (): Promise<number | null> => {
  try {
    const page = await listRegistrationRequests({ status: 'PENDING', page: 1, size: 1 });
    return page.totalElements;
  } catch {
    return null;
  }
};

/**
 * 审批通过：**必须显式传角色**（可为空数组 = 不授角色）。
 *
 * 这里刻意不设置默认值：调用方（审批弹窗）默认勾选为空，用户勾了才传，
 * 禁止前端偷偷塞 `vulnerability_engineer`。
 */
export const approveRegistration = async (id: string, roles: string[]): Promise<ApproveRegistrationResult> => {
  const res = await request.post(`/admin/registration-requests/${id}/approve`, { roles }, { silent: true });
  const payload = unwrap(res) ?? {};
  return {
    request: payload.request as RegistrationRequestVO,
    userId: String(payload.userId ?? ''),
    username: String(payload.username ?? ''),
    loginEnabled: Boolean(payload.loginEnabled),
  };
};

/** 审批拒绝（note 必填，前端 <=200 字；后端上限 500） */
export const rejectRegistration = async (id: string, note: string): Promise<RegistrationRequestVO> => {
  const res = await request.post(`/admin/registration-requests/${id}/reject`, { note: note.trim() }, { silent: true });
  return (unwrap(res) ?? {}) as RegistrationRequestVO;
};

/** 彻底清理申请（仅 ADMIN；204 幂等，APPROVED → 409） */
export const purgeRegistration = async (id: string): Promise<void> => {
  await request.delete(`/admin/registration-requests/${id}`, { silent: true });
};

// ------------------------------------------------------------------ 纯函数（可单测/复用）

/** 手机号脱敏：138****1234（非 11 位也尽量保留首 3 尾 4） */
export const maskPhone = (phone?: string | null): string => {
  const value = (phone ?? '').trim();
  if (!value) return '';
  if (value.length <= 4) return `${value[0]}***`;
  if (value.length <= 7) return `${value.slice(0, 1)}****${value.slice(-1)}`;
  return `${value.slice(0, 3)}****${value.slice(-4)}`;
};

/** 邮箱脱敏：ab***@example.com */
export const maskEmail = (email?: string | null): string => {
  const value = (email ?? '').trim();
  if (!value) return '';
  const at = value.indexOf('@');
  if (at <= 0) return `${value.slice(0, 1)}***`;
  const name = value.slice(0, at);
  const domain = value.slice(at);
  if (name.length <= 2) return `${name[0]}***${domain}`;
  return `${name.slice(0, 2)}***${domain}`;
};

/**
 * 关键词过滤（用户名/手机号/邮箱）。
 *
 * 后端列表**没有** keyword 参数（t102 契约），因此只能对当前页做前端过滤；
 * 页面里有一行说明（`registrationReview.keywordHint`）把这个局限明确告诉操作者，
 * 避免"翻了 3 页才搜到"这种隐性误导。
 */
export const filterRegistrationRequests = (
  list: RegistrationRequestVO[],
  keyword: string,
): RegistrationRequestVO[] => {
  const needle = keyword.trim().toLowerCase();
  if (!needle) return list;
  return list.filter((item) =>
    [item.username, item.phone, item.email].some((field) => (field ?? '').toLowerCase().includes(needle)),
  );
};

/** 状态 → i18n key（表格 Tag 用） */
export const REGISTRATION_STATUS_META: Record<string, { color: string; labelKey: string }> = {
  PENDING: { color: 'gold', labelKey: 'registrationReview.status.pending' },
  APPROVED: { color: 'green', labelKey: 'registrationReview.status.approved' },
  REJECTED: { color: 'red', labelKey: 'registrationReview.status.rejected' },
};

/**
 * 角色是否允许在「注册审批」里分配。
 *
 * 后端只校验角色码是否存在（`RegistrationServiceImpl.assignRoles`），不区分"可分配"，
 * 因此前端按最小权限原则自行收敛：
 * - 非 ADMIN 的审批人（PROJECT_LEADER）**看不到 `admin`**（否则等于可以自助提权）；
 * - 若后端将来返回显式 `assignable: false`，一并排除；
 * - 其余角色（含系统内置的业务角色）都可勾选 —— 它们正是审批要授予的对象。
 */
export const isAssignableRole = (
  role: { code: string; isSystem?: boolean; assignable?: boolean },
  viewerIsAdmin: boolean,
): boolean => {
  if (role.assignable === false) return false;
  if (!viewerIsAdmin && role.code === 'admin') return false;
  return true;
};

export { getApiErrorMessage };
