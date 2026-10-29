/**
 * 仪表盘接口失败态分级。
 *
 * 需求（t24）：loading/错误态要区分「无权限（403）」「接口不存在（404）」「服务端错误（5xx）」，
 * 不能把 404/5xx 也渲染成「暂无数据」。
 *
 * 注意后端现状：Spring 对未映射的路径可能走全局异常处理返回 500（而不是 404），
 * 因此 5xx 的提示里保留「也可能是接口未部署」这一层解释。
 */

export type ApiFailureKind = 'forbidden' | 'not-found' | 'server-error' | 'network';

export interface ApiFailure {
  kind: ApiFailureKind;
  status?: number;
  /** 简短标题：用于 Alert message */
  label: string;
  /** 详细说明：用于 Alert description */
  hint: string;
  /** Alert 类型 */
  level: 'warning' | 'error' | 'info';
}

export const describeApiFailure = (status?: number): ApiFailure => {
  if (status === 401) {
    return {
      kind: 'forbidden',
      status,
      label: '登录已过期（401）',
      hint: '请重新登录后再查看该模块。',
      level: 'warning',
    };
  }
  if (status === 403) {
    return {
      kind: 'forbidden',
      status,
      label: '无权限访问（403）',
      hint: '当前角色不在该接口的准入范围内，换用有权限的账号或联系管理员授权。',
      level: 'warning',
    };
  }
  if (status === 404) {
    return {
      kind: 'not-found',
      status,
      label: '接口不存在（404）',
      hint: '服务端没有注册该接口，通常是后端版本较旧（缺少本轮新增能力）。',
      level: 'warning',
    };
  }
  if (typeof status === 'number' && status >= 500) {
    return {
      kind: 'server-error',
      status,
      label: `服务端错误（${status}）`,
      hint: '服务端处理该请求时异常；若后端版本较旧，Spring 也可能把「接口不存在」返回成 500，可结合后端日志确认。',
      level: 'error',
    };
  }
  if (typeof status === 'number') {
    return {
      kind: 'server-error',
      status,
      label: `请求被拒绝（${status}）`,
      hint: '接口返回了非预期的状态码，可稍后重试或联系管理员。',
      level: 'warning',
    };
  }
  return {
    kind: 'network',
    label: '网络请求失败',
    hint: '未收到服务端响应（断网、代理或跨域），请检查网络后重试。',
    level: 'warning',
  };
};
