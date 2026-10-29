/**
 * 漏洞状态机常量。
 *
 * 与后端保持一致：
 * - WorkflowStatus#canTransitionTo（工作流状态机）
 * - VulnerabilityServiceImpl#updateCustomerStatus / #updateDisclosureStatus
 * - VulnerabilityHistory#statusType（workflow / customer / disclosure）
 */

export interface StatusOption {
  value: string;
  label: string;
  color: string;
}

/** 工作流状态（顺序即流程顺序） */
export const WORKFLOW_STATUS_OPTIONS: StatusOption[] = [
  { value: 'discovered', label: '已发现', color: 'default' },
  { value: 'verified', label: '已验证', color: 'blue' },
  { value: 'analyzing', label: '分析中', color: 'geekblue' },
  { value: 'analyzed', label: '已分析', color: 'purple' },
  { value: 'exploit_development', label: '利用开发中', color: 'orange' },
  { value: 'field_testing', label: '出场测试中', color: 'gold' },
  { value: 'delivered', label: '已交付', color: 'green' },
  { value: 'closed', label: '已关闭', color: 'default' },
];

/** 后端 WorkflowStatus#canTransitionTo 的前端镜像，用于渲染合法流转按钮 */
export const WORKFLOW_TRANSITIONS: Record<string, string[]> = {
  discovered: ['verified'],
  verified: ['analyzing'],
  analyzing: ['analyzed'],
  analyzed: ['exploit_development', 'delivered'],
  exploit_development: ['field_testing', 'delivered'],
  field_testing: ['delivered', 'exploit_development'],
  delivered: ['closed'],
  closed: [],
};

/** 客户确认状态 */
export const CUSTOMER_STATUS_OPTIONS: StatusOption[] = [
  { value: 'pending', label: '待确认', color: 'default' },
  { value: 'confirmed', label: '已确认', color: 'green' },
  { value: 'rejected', label: '已拒绝', color: 'red' },
];

/** 公开披露状态 */
export const DISCLOSURE_STATUS_OPTIONS: StatusOption[] = [
  { value: 'undisclosed', label: '未披露', color: 'default' },
  { value: 'disclosed', label: '已披露', color: 'cyan' },
  { value: 'has_cve', label: '已分配CVE', color: 'blue' },
];

/** 利用阶段 */
export const EXPLOIT_STAGE_OPTIONS: StatusOption[] = [
  { value: '探测', label: '探测', color: 'default' },
  { value: '突破', label: '突破', color: 'orange' },
  { value: '控守', label: '控守', color: 'geekblue' },
  { value: '已完成', label: '已完成', color: 'green' },
];

/** 出场测试状态（仅编辑表单可写） */
export const FIELD_TEST_STATUS_OPTIONS: StatusOption[] = [
  { value: '待测试', label: '待测试', color: 'default' },
  { value: '测试中', label: '测试中', color: 'blue' },
  { value: '测试完成', label: '测试完成', color: 'green' },
  { value: '测试失败', label: '测试失败', color: 'red' },
];

/** 状态类型（历史记录 status_type） */
export const HISTORY_STATUS_TYPES: Record<string, { label: string; color: string }> = {
  workflow: { label: '工作流状态', color: 'blue' },
  customer: { label: '客户确认状态', color: 'gold' },
  disclosure: { label: '公开披露状态', color: 'cyan' },
};

/** 严重程度 */
export const SEVERITY_OPTIONS: StatusOption[] = [
  { value: 'CRITICAL', label: '严重', color: 'red' },
  { value: 'HIGH', label: '高危', color: 'orange' },
  { value: 'MEDIUM', label: '中危', color: 'blue' },
  { value: 'LOW', label: '低危', color: 'green' },
  { value: 'INFO', label: '信息', color: 'default' },
];

const optionsToMap = (options: StatusOption[]): Record<string, StatusOption> =>
  options.reduce<Record<string, StatusOption>>((acc, item) => {
    acc[item.value] = item;
    return acc;
  }, {});

const WORKFLOW_MAP = optionsToMap(WORKFLOW_STATUS_OPTIONS);
const CUSTOMER_MAP = optionsToMap(CUSTOMER_STATUS_OPTIONS);
const DISCLOSURE_MAP = optionsToMap(DISCLOSURE_STATUS_OPTIONS);
const EXPLOIT_MAP = optionsToMap(EXPLOIT_STAGE_OPTIONS);
const FIELD_TEST_MAP = optionsToMap(FIELD_TEST_STATUS_OPTIONS);
const SEVERITY_MAP = optionsToMap(SEVERITY_OPTIONS);

/** 兼容大小写：接口可能返回 CRITICAL / critical */
const pick = (map: Record<string, StatusOption>, value?: string | null): StatusOption | undefined => {
  if (!value) {
    return undefined;
  }
  return map[value] ?? map[value.toUpperCase()] ?? map[value.toLowerCase()];
};

export const workflowLabel = (value?: string | null) => pick(WORKFLOW_MAP, value)?.label ?? (value || '-');
export const workflowColor = (value?: string | null) => pick(WORKFLOW_MAP, value)?.color ?? 'default';
export const customerLabel = (value?: string | null) => pick(CUSTOMER_MAP, value)?.label ?? (value || '-');
export const customerColor = (value?: string | null) => pick(CUSTOMER_MAP, value)?.color ?? 'default';
export const disclosureLabel = (value?: string | null) => pick(DISCLOSURE_MAP, value)?.label ?? (value || '-');
export const disclosureColor = (value?: string | null) => pick(DISCLOSURE_MAP, value)?.color ?? 'default';
export const exploitStageLabel = (value?: string | null) => pick(EXPLOIT_MAP, value)?.label ?? (value || '-');
export const exploitStageColor = (value?: string | null) => pick(EXPLOIT_MAP, value)?.color ?? 'default';
export const fieldTestLabel = (value?: string | null) => pick(FIELD_TEST_MAP, value)?.label ?? (value || '-');
export const fieldTestColor = (value?: string | null) => pick(FIELD_TEST_MAP, value)?.color ?? 'default';
export const severityLabel = (value?: string | null) => pick(SEVERITY_MAP, value)?.label ?? (value || '-');
export const severityColor = (value?: string | null) => pick(SEVERITY_MAP, value)?.color ?? 'default';

/** 当前工作流状态在流程中的序号（-1 表示未知） */
export const workflowIndex = (value?: string | null) => {
  if (!value) {
    return -1;
  }
  return WORKFLOW_STATUS_OPTIONS.findIndex((item) => item.value === value);
};

/** 该状态允许流转到的目标状态 */
export const nextWorkflowStatuses = (value?: string | null) => {
  if (!value) {
    return [];
  }
  return WORKFLOW_TRANSITIONS[value] ?? [];
};

/** 文件大小格式化 */
export const formatFileSize = (size?: number) => {
  if (!size || size <= 0) {
    return '-';
  }
  if (size >= 1024 * 1024 * 1024) return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(2)} MB`;
  if (size >= 1024) return `${(size / 1024).toFixed(2)} KB`;
  return `${size} B`;
};
