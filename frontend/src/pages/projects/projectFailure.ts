import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';

/** 后端「仍有 N 个资产挂靠」里抽数量，抽不到则返回 null（文案回落到通用提示） */
export const parseLinkedCount = (message?: string): number | null => {
  if (!message) return null;
  const matched = message.match(/(\d+)/);
  if (!matched) return null;
  const value = Number(matched[1]);
  return Number.isFinite(value) ? value : null;
};

type Translate = (key: never) => string;

interface FailureCopy {
  forbidden: string;
  notFound: string;
  /** 409 的模板，需带 {count} 占位符 */
  blockedTemplate: string;
  blockedFallback: string;
  invalidTransition: string;
  fallback: string;
}

/**
 * 项目写失败 → 可读文案。
 *
 * 分级：400（状态机拒绝，后端 message 已含「当前状态/合法下一步」）> 403（越权）> 404（不存在）
 * > 409（删除保护，带数量）> 其他（后端 message 或通用兜底）。
 * 写接口在 api/project.ts 里统一 `silent`，因此这里必须自己提示，不能依赖全局拦截器。
 */
export const describeProjectFailure = (
  error: unknown,
  copy: FailureCopy,
  vars?: (template: string, count: number) => string,
): string => {
  const status = getApiErrorStatus(error);
  const backendMessage = (error as any)?.response?.data?.message as string | undefined;

  if (status === 403) return copy.forbidden;
  if (status === 404) return copy.notFound;
  if (status === 409) {
    const count = parseLinkedCount(backendMessage);
    if (count !== null) {
      return vars ? vars(copy.blockedTemplate, count) : copy.blockedFallback;
    }
    return copy.blockedFallback;
  }
  if (status === 400) {
    return getApiErrorMessage(error, copy.invalidTransition);
  }
  return getApiErrorMessage(error, copy.fallback);
};

export type { FailureCopy, Translate };
