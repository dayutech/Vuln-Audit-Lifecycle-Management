import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';

/**
 * 漏洞创建失败提示（t27 起 POST /vulnerabilities 增加 canWriteAsset 校验）。
 *
 * 后端口径：assetId 为空 → 400；当前用户不是该资产负责人且无写权限 → 403。
 * 403 必须给出可执行的原因，而不是泛化的「创建失败」。
 */
export const VULN_CREATE_FORBIDDEN_MESSAGE =
  '无权在该资产下创建漏洞（需为该资产负责人或有写权限）';

/** 资产选择阶段的即时提示，用于「非负责人且非管理层」的自检 */
export const VULN_CREATE_FOREIGN_ASSET_HINT =
  '你当前不是该资产负责人，提交后可能被拒绝（403）——如需创建请联系资产负责人授权。';

/**
 * 按状态码给出创建漏洞的失败文案：
 * 403 → 明确的权限原因；其余（400/404/5xx/网络）→ 复用 utils/errorMessage 的后端 message。
 */
export const describeVulnCreateFailure = (error: unknown, fallback = '漏洞创建失败'): string => {
  const status = getApiErrorStatus(error);
  if (status === 403) {
    return VULN_CREATE_FORBIDDEN_MESSAGE;
  }
  return getApiErrorMessage(error, fallback);
};

/** 是否为表单校验失败（antd validateFields 抛出，无需提示） */
export const isFormValidationError = (error: unknown): boolean =>
  Boolean((error as { errorFields?: unknown } | undefined)?.errorFields);

/** 编辑保存时 403 的明确原因（t27 的 canWriteAsset 守卫在 PUT 上同样生效） */
export const VULN_UPDATE_FORBIDDEN_MESSAGE = '无权修改该漏洞（需为该资产负责人或有写权限）';

/**
 * 按状态码给出编辑保存的失败文案（复用 utils/errorMessage 提取后端 message）：
 * - 403 → 明确的写权限原因；
 * - 400 → 后端字段/业务校验原文（如「发现版本必须属于当前资产」）；
 * - 404 → 漏洞已被删除；
 * - 409/5xx/网络 → 后端 message 或兜底文案。
 */
export const describeVulnUpdateFailure = (error: unknown, fallback = '漏洞保存失败'): string => {
  const status = getApiErrorStatus(error);
  if (status === 403) {
    return VULN_UPDATE_FORBIDDEN_MESSAGE;
  }
  if (status === 404) {
    return '漏洞不存在或已被删除，请返回列表刷新后重试';
  }
  if (status === 400) {
    return getApiErrorMessage(error, '保存失败：提交的数据未通过后端校验（可检查发现版本是否属于该资产）');
  }
  return getApiErrorMessage(error, fallback);
};

/**
 * 对比保存前后的字段，找出「未提交却发生变化」的字段并生成可读提示。
 *
 * 后端部分更新语义下，未提交字段理应保持不变；一旦变化说明后端行为异常（或并发修改），
 * 需要显式告知用户，而不是静默刷新。
 */
export interface FieldDiff {
  label: string;
  before: string;
  after: string;
}

const normalize = (value: unknown): string => {
  if (value === null || value === undefined) return '';
  if (typeof value === 'boolean') return value ? '是' : '否';
  return String(value);
};

/** 只比较给定字段；submittedKeys 中出现的字段视为「用户主动修改」，不参与差异告警 */
export const collectUnexpectedFieldDiffs = (
  before: Record<string, any> | null | undefined,
  after: Record<string, any> | null | undefined,
  candidates: Array<{ key: string; label: string }>,
  submittedKeys: string[] = [],
): FieldDiff[] => {
  if (!before || !after) return [];
  const submitted = new Set(submittedKeys);
  return candidates
    .filter(({ key }) => !submitted.has(key))
    .map(({ key, label }) => ({
      label,
      before: normalize(before[key]),
      after: normalize(after[key]),
    }))
    .filter((diff) => diff.before !== diff.after);
};
