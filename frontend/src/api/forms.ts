import request from '@/utils/request';

/**
 * 自定义填表系统（后端 t178–t181，前端 t182）。
 *
 * ## 契约要点（README §72）
 * - 模板/字段：仅 ADMIN；`code` 创建后**不可改值**（改了会 403）；模板被实例引用时删除 409。
 * - 字段：`sourceType` 空 = 手填；非空 = 库字段（13 项白名单，见 `FORM_FIELD_SOURCES`）。
 * - 生成实例：`sourceType!=null` 当场从业务库取**快照**（`sourceValue`），之后业务数据变化不影响它。
 * - 生效值口径：`effectiveValue = overrideValue ?? filledValue ?? sourceValue`。
 */
export interface PageEnvelope<T> {
  content: T[];
  totalElements: number;
  totalPages?: number;
  currentPage?: number;
  pageSize?: number;
}

export interface FormFieldVO {
  id: string;
  fieldKey: string;
  label: string;
  fieldType: string;
  required: boolean;
  sortOrder: number;
  sourceType?: string | null;
  sourceLabel?: string | null;
  options?: string | null;
}

export interface FormDefinitionVO {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  enabled: boolean;
  fieldCount: number;
  createdAt?: string | null;
  updatedAt?: string | null;
  fields?: FormFieldVO[] | null;
}

export interface FormValueVO extends FormFieldVO {
  /** 后端 FormValueVO 用 fieldId（模板字段是 id；这里两者都兜住） */
  fieldId?: string;
  instanceField: boolean;
  sourceValue?: string | null;
  filledValue?: string | null;
  overrideValue?: string | null;
  effectiveValue?: string | null;
}

export interface FormSubmissionVO {
  id: string;
  definitionId: string;
  definitionCode?: string | null;
  definitionName: string;
  title?: string | null;
  subjectType: string;
  subjectId: string;
  subjectName?: string | null;
  status: string;
  createdBy?: string | null;
  createdByName?: string | null;
  assignedTo?: string | null;
  assignedToName?: string | null;
  confirmedBy?: string | null;
  assignedAt?: string | null;
  submittedAt?: string | null;
  confirmedAt?: string | null;
  note?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  filledCount: number;
  fieldCount: number;
  fields?: FormValueVO[] | null;
}

export interface FormSubmissionSummaryVO {
  id: string;
  definitionName: string;
  title?: string | null;
  subjectType: string;
  subjectId: string;
  subjectName?: string | null;
  status: string;
  assignedTo?: string | null;
  assignedToName?: string | null;
  filledCount: number;
  fieldCount: number;
  note?: string | null;
  createdAt?: string | null;
}

export interface FormSubmissionCreatedVO {
  id: string;
  definitionId: string;
  status: string;
  fieldCount: number;
  snapshotFilled: number;
  snapshotNull: number;
  skippedSources?: string[];
}

export type FormSubjectType = 'CUSTOMER' | 'PROJECT' | 'ASSET';

/** 字段类型白名单（与后端 `FormFieldType` 一致） */
export const FORM_FIELD_TYPES = ['TEXT', 'TEXTAREA', 'NUMBER', 'DATE', 'SELECT', 'CHECKBOX', 'FILE'] as const;

/** 库字段来源白名单（与后端 `FormFieldSource` 一一对应，13 项） */
export const FORM_FIELD_SOURCES: Array<{ value: string; zh: string; en: string; subject: FormSubjectType | 'TEAM' | 'USER' }> = [
  { value: 'CUSTOMER.NAME', zh: '客户名称', en: 'Customer name', subject: 'CUSTOMER' },
  { value: 'CUSTOMER.CODE', zh: '客户编号', en: 'Customer code', subject: 'CUSTOMER' },
  { value: 'CUSTOMER.INDUSTRY', zh: '所属行业', en: 'Customer industry', subject: 'CUSTOMER' },
  { value: 'PROJECT.NAME', zh: '项目名称', en: 'Project name', subject: 'PROJECT' },
  { value: 'PROJECT.CODE', zh: '项目编号', en: 'Project code', subject: 'PROJECT' },
  { value: 'PROJECT.TYPE', zh: '项目类型', en: 'Project type', subject: 'PROJECT' },
  { value: 'ASSET.NAME', zh: '资产名称', en: 'Asset name', subject: 'ASSET' },
  { value: 'ASSET.CODE', zh: '资产编号', en: 'Asset code', subject: 'ASSET' },
  { value: 'ASSET.TARGET_TYPE', zh: '资产类型', en: 'Asset target type', subject: 'ASSET' },
  { value: 'ASSET.PRIORITY', zh: '资产优先级', en: 'Asset priority', subject: 'ASSET' },
  { value: 'ASSET.STATUS', zh: '资产状态', en: 'Asset status', subject: 'ASSET' },
  { value: 'TEAM.NAME', zh: '团队名称', en: 'Team name', subject: 'TEAM' },
  { value: 'USER.FULL_NAME', zh: '用户姓名', en: 'User full name', subject: 'USER' },
];

export const FORM_STATUSES = ['DRAFT', 'ASSIGNED', 'SUBMITTED', 'CONFIRMED', 'RETURNED'] as const;

const unwrap = (res: any) => res?.data ?? res;

// ==================== 模板（ADMIN） ====================

export const listFormDefinitions = async (params: {
  keyword?: string;
  enabled?: boolean;
  page?: number;
  size?: number;
}): Promise<PageEnvelope<FormDefinitionVO>> => {
  const res: any = await request.get('/admin/form-definitions', { params });
  const payload = unwrap(res) ?? {};
  return { content: payload.content ?? [], totalElements: payload.totalElements ?? 0, totalPages: payload.totalPages };
};

export const getFormDefinition = async (id: string): Promise<FormDefinitionVO> => {
  const res: any = await request.get(`/admin/form-definitions/${id}`);
  return unwrap(res);
};

export const createFormDefinition = async (data: {
  code: string;
  name: string;
  description?: string;
  enabled?: boolean;
}): Promise<FormDefinitionVO> => {
  const res: any = await request.post('/admin/form-definitions', data);
  return unwrap(res);
};

export const updateFormDefinition = async (
  id: string,
  data: { name?: string; description?: string; enabled?: boolean },
): Promise<FormDefinitionVO> => {
  const res: any = await request.put(`/admin/form-definitions/${id}`, data);
  return unwrap(res);
};

export const deleteFormDefinition = (id: string): Promise<any> => request.delete(`/admin/form-definitions/${id}`);

export interface FormFieldUpsert {
  fieldKey?: string;
  label: string;
  fieldType: string;
  required?: boolean;
  sortOrder?: number;
  sourceType?: string | null;
  options?: string | null;
}

export const addFormField = async (definitionId: string, data: FormFieldUpsert): Promise<FormFieldVO> => {
  const res: any = await request.post(`/admin/form-definitions/${definitionId}/fields`, data);
  return unwrap(res);
};

export const updateFormField = async (
  definitionId: string,
  fieldId: string,
  data: FormFieldUpsert,
): Promise<FormFieldVO> => {
  const res: any = await request.put(`/admin/form-definitions/${definitionId}/fields/${fieldId}`, data);
  return unwrap(res);
};

export const deleteFormField = (definitionId: string, fieldId: string): Promise<any> =>
  request.delete(`/admin/form-definitions/${definitionId}/fields/${fieldId}`);

/** 调整顺序：必须覆盖**全部**字段（后端 400 否则） */
export const reorderFormFields = async (definitionId: string, fieldIds: string[]): Promise<FormDefinitionVO> => {
  const res: any = await request.put(`/admin/form-definitions/${definitionId}/fields/reorder`, { fieldIds });
  return unwrap(res);
};

// ==================== 实例 ====================

export const createFormSubmission = async (data: {
  definitionId: string;
  subjectType: FormSubjectType;
  subjectId: string;
  title?: string;
  assignedTo?: string;
}): Promise<FormSubmissionCreatedVO> => {
  const res: any = await request.post('/forms/submissions', data);
  return unwrap(res);
};

export const getFormSubmission = async (id: string): Promise<FormSubmissionVO> => {
  const res: any = await request.get(`/forms/submissions/${id}`);
  return unwrap(res);
};

export const assignFormSubmission = async (id: string, data: { assigneeId: string; note?: string }): Promise<FormSubmissionVO> => {
  const res: any = await request.post(`/forms/submissions/${id}/assign`, data);
  return unwrap(res);
};

export const addInstanceField = async (
  submissionId: string,
  data: { label: string; fieldType: string; options?: string },
): Promise<FormValueVO> => {
  const res: any = await request.post(`/forms/submissions/${submissionId}/fields`, data);
  return unwrap(res);
};

export const deleteInstanceField = (submissionId: string, fieldId: string): Promise<any> =>
  request.delete(`/forms/submissions/${submissionId}/fields/${fieldId}`);

export const setFormConfirmer = async (submissionId: string, confirmerId: string): Promise<FormSubmissionVO> => {
  const res: any = await request.post(`/forms/submissions/${submissionId}/confirmers`, { confirmerId });
  return unwrap(res);
};

export const listMyFormSubmissions = async (params: {
  status?: string;
  page?: number;
  size?: number;
}): Promise<PageEnvelope<FormSubmissionSummaryVO>> => {
  const res: any = await request.get('/forms/submissions/mine', { params });
  const payload = unwrap(res) ?? {};
  return { content: payload.content ?? [], totalElements: payload.totalElements ?? 0, totalPages: payload.totalPages };
};

const enabledDefinitionsCache: { value: FormDefinitionVO[] | null } = { value: null };

/** 生成表单用的「启用模板」下拉（一次拉取，size 上限 100） */
export const listEnabledDefinitions = async (): Promise<FormDefinitionVO[]> => {
  if (enabledDefinitionsCache.value) return enabledDefinitionsCache.value;
  const res = await listFormDefinitions({ enabled: true, page: 0, size: 100 });
  enabledDefinitionsCache.value = res.content;
  return res.content;
};

export const resetEnabledDefinitionsCache = () => {
  enabledDefinitionsCache.value = null;
};

