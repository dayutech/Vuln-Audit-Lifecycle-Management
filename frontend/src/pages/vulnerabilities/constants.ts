import type { MessageKey } from '@/i18n/messages/zh-CN';

/**
 * 漏洞状态机常量。
 *
 * 与后端保持一致：
 * - WorkflowStatus#canTransitionTo（工作流状态机）
 * - VulnerabilityServiceImpl#updateCustomerStatus / #updateDisclosureStatus
 * - VulnerabilityHistory#statusType（workflow / customer / disclosure）
 *
 * i18n（t57）：常量里不再放中文，只保留 `labelKey`（MessageKey），
 * 展示层用 `t(labelKey)` 渲染；为此额外导出 `*Key` 版本给非组件代码使用。
 */

export interface StatusOption {
  value: string;
  labelKey: MessageKey;
  color: string;
}

/** 工作流状态（顺序即流程顺序） */
export const WORKFLOW_STATUS_OPTIONS: StatusOption[] = [
  { value: 'discovered', labelKey: 'vuln.status.discovered', color: 'default' },
  { value: 'verified', labelKey: 'vuln.status.verified', color: 'blue' },
  { value: 'analyzing', labelKey: 'vuln.status.analyzing', color: 'geekblue' },
  { value: 'analyzed', labelKey: 'vuln.status.analyzed', color: 'purple' },
  { value: 'exploit_development', labelKey: 'vuln.status.exploitDevelopment', color: 'orange' },
  { value: 'field_testing', labelKey: 'vuln.status.fieldTesting', color: 'gold' },
  { value: 'delivered', labelKey: 'vuln.status.delivered', color: 'green' },
  { value: 'closed', labelKey: 'vuln.status.closed', color: 'default' },
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
  { value: 'pending', labelKey: 'vuln.customer.pending', color: 'default' },
  { value: 'confirmed', labelKey: 'vuln.customer.confirmed', color: 'green' },
  { value: 'rejected', labelKey: 'vuln.customer.rejected', color: 'red' },
];

/** 公开披露状态 */
export const DISCLOSURE_STATUS_OPTIONS: StatusOption[] = [
  { value: 'undisclosed', labelKey: 'vuln.disclosure.undisclosed', color: 'default' },
  { value: 'disclosed', labelKey: 'vuln.disclosure.disclosed', color: 'cyan' },
  { value: 'has_cve', labelKey: 'vuln.disclosure.hasCve', color: 'blue' },
];

/** 利用阶段 */
export const EXPLOIT_STAGE_OPTIONS: StatusOption[] = [
  { value: '探测', labelKey: 'vuln.stage.probe', color: 'default' },
  { value: '突破', labelKey: 'vuln.stage.breakthrough', color: 'orange' },
  { value: '控守', labelKey: 'vuln.stage.hold', color: 'geekblue' },
  { value: '已完成', labelKey: 'vuln.stage.done', color: 'green' },
];

/** 出场测试状态（仅编辑表单可写） */
export const FIELD_TEST_STATUS_OPTIONS: StatusOption[] = [
  { value: '待测试', labelKey: 'vuln.fieldTest.pending', color: 'default' },
  { value: '测试中', labelKey: 'vuln.fieldTest.testing', color: 'blue' },
  { value: '测试完成', labelKey: 'vuln.fieldTest.done', color: 'green' },
  { value: '测试失败', labelKey: 'vuln.fieldTest.failed', color: 'red' },
];

/** 状态类型（历史记录 status_type） */
export const HISTORY_STATUS_TYPES: Record<string, { labelKey: MessageKey; color: string }> = {
  workflow: { labelKey: 'vuln.history.workflow', color: 'blue' },
  customer: { labelKey: 'vuln.history.customer', color: 'gold' },
  disclosure: { labelKey: 'vuln.history.disclosure', color: 'cyan' },
};

/** 严重程度 */
export const SEVERITY_OPTIONS: StatusOption[] = [
  { value: 'CRITICAL', labelKey: 'vuln.severity.critical', color: 'red' },
  { value: 'HIGH', labelKey: 'vuln.severity.high', color: 'orange' },
  { value: 'MEDIUM', labelKey: 'vuln.severity.medium', color: 'blue' },
  { value: 'LOW', labelKey: 'vuln.severity.low', color: 'green' },
  { value: 'INFO', labelKey: 'vuln.severity.info', color: 'default' },
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

/** 展示用：状态 → message key（未知值返回 undefined，由调用方回落原始值） */
export const workflowKey = (value?: string | null): MessageKey | undefined => pick(WORKFLOW_MAP, value)?.labelKey;
export const customerKey = (value?: string | null): MessageKey | undefined => pick(CUSTOMER_MAP, value)?.labelKey;
export const disclosureKey = (value?: string | null): MessageKey | undefined => pick(DISCLOSURE_MAP, value)?.labelKey;
export const exploitStageKey = (value?: string | null): MessageKey | undefined => pick(EXPLOIT_MAP, value)?.labelKey;
export const fieldTestKey = (value?: string | null): MessageKey | undefined => pick(FIELD_TEST_MAP, value)?.labelKey;
export const severityKey = (value?: string | null): MessageKey | undefined => pick(SEVERITY_MAP, value)?.labelKey;

export const workflowColor = (value?: string | null) => pick(WORKFLOW_MAP, value)?.color ?? 'default';
export const customerColor = (value?: string | null) => pick(CUSTOMER_MAP, value)?.color ?? 'default';
export const disclosureColor = (value?: string | null) => pick(DISCLOSURE_MAP, value)?.color ?? 'default';
export const exploitStageColor = (value?: string | null) => pick(EXPLOIT_MAP, value)?.color ?? 'default';
export const fieldTestColor = (value?: string | null) => pick(FIELD_TEST_MAP, value)?.color ?? 'default';
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

/** 文件大小格式化（单位符号与语言无关） */
export const formatFileSize = (size?: number) => {
  if (!size || size <= 0) {
    return '-';
  }
  if (size >= 1024 * 1024 * 1024) return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(2)} MB`;
  if (size >= 1024) return `${(size / 1024).toFixed(2)} KB`;
  return `${size} B`;
};
