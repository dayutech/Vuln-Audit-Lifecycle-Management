/**
 * 漏洞创建 / 更新的请求体映射（snake_case 表单值 → 后端 camelCase 字段）。
 *
 * 背景：
 * - t34 起 `POST /api/v1/vulnerabilities` 绑定 `VulnerabilityCreateRequest`、
 *   `PUT /api/v1/vulnerabilities/{id}` 绑定 `VulnerabilityUpdateRequest`（DTO + @JsonAlias），
 *   两者都只认 camelCase 为主键名；更新 DTO 采用「部分更新」语义：只覆盖请求里出现的字段。
 * - 更新 DTO **不包含**状态字段（workflowStatus / customerStatus / disclosureStatus /
 *   exploitStage / fieldTestStatus / vendorFixed …），这些只能走各自的 PATCH 端点；
 *   因此更新载荷里绝不能带状态字段，否则要么被忽略、要么（严格 Jackson 配置下）400。
 */

type FormValues = Record<string, any>;

const pick = (values: FormValues, snakeKey: string, camelKey?: string): any => {
  const value = values?.[snakeKey] ?? (camelKey ? values?.[camelKey] : undefined);
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'string' && value.trim() === '') return undefined;
  return value;
};

const assignIfPresent = (target: Record<string, unknown>, key: string, value: unknown) => {
  if (value !== undefined) {
    target[key] = value;
  }
};

/** 表单值 → 后端可绑定的 camelCase 请求体（创建漏洞） */
export const toVulnerabilityCreatePayload = (values: FormValues, assetId: string): Record<string, unknown> => {
  const payload: Record<string, unknown> = {};
  assignIfPresent(payload, 'title', pick(values, 'title'));
  assignIfPresent(payload, 'assetId', assetId || pick(values, 'asset_id', 'assetId'));
  assignIfPresent(payload, 'discoveredVersionId', pick(values, 'discovered_version_id', 'discoveredVersionId'));
  assignIfPresent(payload, 'affectedVersions', pick(values, 'affected_versions', 'affectedVersions'));
  assignIfPresent(payload, 'cveId', pick(values, 'cve_id', 'cveId'));
  assignIfPresent(payload, 'type', pick(values, 'type'));
  assignIfPresent(payload, 'severity', pick(values, 'severity'));
  assignIfPresent(payload, 'cvssScore', pick(values, 'cvss_score', 'cvssScore'));
  assignIfPresent(payload, 'assignedTo', pick(values, 'assigned_to', 'assignedTo'));
  assignIfPresent(payload, 'exploitCode', pick(values, 'exploit_code', 'exploitCode'));
  assignIfPresent(payload, 'fieldTestStatus', pick(values, 'field_test_status', 'fieldTestStatus'));
  assignIfPresent(payload, 'fieldTestResult', pick(values, 'field_test_result', 'fieldTestResult'));
  assignIfPresent(payload, 'deliveryNote', pick(values, 'delivery_note', 'deliveryNote'));
  assignIfPresent(payload, 'description', pick(values, 'description'));
  assignIfPresent(payload, 'detail', pick(values, 'detail'));
  assignIfPresent(payload, 'impact', pick(values, 'impact'));
  assignIfPresent(payload, 'pocCode', pick(values, 'poc_code', 'pocCode'));
  return payload;
};

/** 更新 DTO 接受的字段白名单（与后端 VulnerabilityUpdateRequest 一一对应） */
const UPDATE_ALLOWED_KEYS = [
  'title',
  'cveId',
  'discoveredVersionId',
  'affectedVersions',
  'type',
  'severity',
  'cvssScore',
  'description',
  'detail',
  'impact',
  'pocCode',
  'assignedTo',
] as const;

/**
 * 表单值 → PUT /vulnerabilities/{id} 的 camelCase 请求体。
 *
 * 只输出更新 DTO 白名单内的字段（部分更新语义：没出现的字段后端保持原值）；
 * 状态字段一律不带，状态变更走 PATCH（详情页按钮），编辑表单只做只读展示。
 */
export const toVulnerabilityUpdatePayload = (values: FormValues): Record<string, unknown> => {
  const payload: Record<string, unknown> = {};
  assignIfPresent(payload, 'title', pick(values, 'title'));
  assignIfPresent(payload, 'cveId', pick(values, 'cve_id', 'cveId'));
  assignIfPresent(payload, 'discoveredVersionId', pick(values, 'discovered_version_id', 'discoveredVersionId'));
  assignIfPresent(payload, 'affectedVersions', pick(values, 'affected_versions', 'affectedVersions'));
  assignIfPresent(payload, 'type', pick(values, 'type'));
  assignIfPresent(payload, 'severity', pick(values, 'severity'));
  assignIfPresent(payload, 'cvssScore', pick(values, 'cvss_score', 'cvssScore'));
  assignIfPresent(payload, 'description', pick(values, 'description'));
  assignIfPresent(payload, 'detail', pick(values, 'detail'));
  assignIfPresent(payload, 'impact', pick(values, 'impact'));
  assignIfPresent(payload, 'pocCode', pick(values, 'poc_code', 'pocCode'));
  assignIfPresent(payload, 'assignedTo', pick(values, 'assigned_to', 'assignedTo'));

  // 双保险：即使将来有人往表单里塞了状态字段，也不会进入 PUT 载荷
  const allowed = new Set<string>(UPDATE_ALLOWED_KEYS);
  Object.keys(payload).forEach((key) => {
    if (!allowed.has(key)) {
      delete payload[key];
    }
  });

  return payload;
};
