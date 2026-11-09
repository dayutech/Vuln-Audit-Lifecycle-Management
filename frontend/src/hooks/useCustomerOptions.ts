import { useCallback, useEffect, useState } from 'react';
import { loadCustomerOptions, type CustomerOptionsResult, type CustomerOptionsStatus } from '@/api/customer';
import type { CustomerVO } from '@/types/customer';

/**
 * 客户下拉的统一加载状态（t139）。
 *
 * 为什么要有这个 hook：t72 起各页面的客户下拉都是
 * `getCustomerOptions().then(...).catch(() => setCustomers([]))` ——
 * 请求一旦失败（403 / 401 / 5xx / 网络）就**静默变成空下拉**，
 * 用户只看到「客户下拉框是空的」，看不出是「没有客户」还是「请求失败了」。
 *
 * 这里把状态显式暴露给 UI（`ok / empty / forbidden / unauthorized / error`），
 * 配合 `CustomerSelectEmpty` 渲染「暂无客户，请先在客户管理创建」或「无权限/重试」，
 * 让空下拉不再是无信息的白板。
 */
export interface UseCustomerOptionsResult {
  items: CustomerVO[];
  status: CustomerOptionsStatus | 'loading';
  detail?: string;
  httpStatus?: number;
  reload: () => Promise<CustomerOptionsResult>;
}

export const useCustomerOptions = (active = true, limit = 200): UseCustomerOptionsResult => {
  const [items, setItems] = useState<CustomerVO[]>([]);
  const [status, setStatus] = useState<CustomerOptionsStatus | 'loading'>('loading');
  const [detail, setDetail] = useState<string | undefined>(undefined);
  const [httpStatus, setHttpStatus] = useState<number | undefined>(undefined);

  const reload = useCallback(async () => {
    setStatus('loading');
    const result = await loadCustomerOptions(limit);
    setItems(result.items);
    setStatus(result.status);
    setDetail(result.detail);
    setHttpStatus(result.httpStatus);
    return result;
  }, [limit]);

  useEffect(() => {
    if (!active) return undefined;
    let cancelled = false;
    // 同步 setStatus('loading') 会被 react-hooks/set-state-in-effect 拦（级联渲染）；
    // 推到 0ms 定时器后语义不变，且能在卸载时取消
    const kickoff = window.setTimeout(() => {
      if (cancelled) return;
      setStatus('loading');
      void loadCustomerOptions(limit).then((result) => {
        if (cancelled) return;
        setItems(result.items);
        setStatus(result.status);
        setDetail(result.detail);
        setHttpStatus(result.httpStatus);
      });
    }, 0);
    return () => {
      cancelled = true;
      window.clearTimeout(kickoff);
    };
  }, [active, limit]);

  return { items, status, detail, httpStatus, reload };
};
