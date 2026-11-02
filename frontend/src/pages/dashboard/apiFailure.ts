import type { MessageKey } from '@/i18n';

/**
 * 仪表盘接口失败态分级（t24 引入，t59 改为 message key）。
 *
 * 需求：loading/错误态要区分「无权限（403）」「接口不存在（404）」「服务端错误（5xx）」，
 * 不能把 404/5xx 也渲染成「暂无数据」。
 *
 * 注意后端现状：Spring 对未映射的路径可能走全局异常处理返回 500（而不是 404），
 * 因此 5xx 的提示里保留「也可能是接口未部署」这一层解释。
 *
 * t59：返回 labelKey / hintKey（由调用方 t(...) 渲染），避免中文硬编码；
 * 原 label/hint 仍保留（标 deprecated）以兼容未迁移的调用方。
 */

export type ApiFailureKind = 'forbidden' | 'not-found' | 'server-error' | 'network';

export interface ApiFailure {
  kind: ApiFailureKind;
  status?: number;
  /** 简短标题词条：用于 Alert message */
  labelKey: MessageKey;
  /** 详细说明词条：用于 Alert description */
  hintKey: MessageKey;
  /** 该词条可能带 {status} 占位符，调用方需传入 */
  vars?: Record<string, string | number>;
  /** Alert 类型 */
  level: 'warning' | 'error' | 'info';
  /** @deprecated 中文标题（调用方请改用 labelKey + t()） */
  label: string;
  /** @deprecated 中文说明（调用方请改用 hintKey + t()） */
  hint: string;
}

const withLegacy = (base: Omit<ApiFailure, 'label' | 'hint'>, legacyLabel: string, legacyHint: string): ApiFailure => ({
  ...base,
  label: legacyLabel,
  hint: legacyHint,
});

export const describeApiFailure = (status?: number): ApiFailure => {
  if (status === 401) {
    return withLegacy(
      {
        kind: 'forbidden',
        status,
        labelKey: 'api.failure.loginExpired',
        hintKey: 'api.failure.loginExpiredHint',
        level: 'warning',
      },
      '登录已过期（401）',
      '请重新登录后再查看该模块。'
    );
  }
  if (status === 403) {
    return withLegacy(
      {
        kind: 'forbidden',
        status,
        labelKey: 'api.failure.forbidden',
        hintKey: 'api.failure.forbiddenHint',
        level: 'warning',
      },
      '无权限访问（403）',
      '当前角色不在该接口的准入范围内，换用有权限的账号或联系管理员授权。'
    );
  }
  if (status === 404) {
    return withLegacy(
      {
        kind: 'not-found',
        status,
        labelKey: 'api.failure.notFound',
        hintKey: 'api.failure.notFoundHint',
        level: 'warning',
      },
      '接口不存在（404）',
      '服务端没有注册该接口，通常是后端版本较旧（缺少本轮新增能力）。'
    );
  }
  if (typeof status === 'number' && status >= 500) {
    return withLegacy(
      {
        kind: 'server-error',
        status,
        labelKey: 'api.failure.serverError',
        hintKey: 'api.failure.serverErrorHint',
        vars: { status },
        level: 'error',
      },
      `服务端错误（${status}）`,
      '服务端处理该请求时异常；若后端版本较旧，Spring 也可能把「接口不存在」返回成 500，可结合后端日志确认。'
    );
  }
  if (typeof status === 'number') {
    return withLegacy(
      {
        kind: 'server-error',
        status,
        labelKey: 'api.failure.rejected',
        hintKey: 'api.failure.rejectedHint',
        vars: { status },
        level: 'warning',
      },
      `请求被拒绝（${status}）`,
      '接口返回了非预期的状态码，可稍后重试或联系管理员。'
    );
  }
  return withLegacy(
    {
      kind: 'network',
      labelKey: 'api.failure.network',
      hintKey: 'api.failure.networkHint',
      level: 'warning',
    },
    '网络请求失败',
    '未收到服务端响应（断网、代理或跨域），请检查网络后重试。'
  );
};
