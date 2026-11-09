import { useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * 「返回上一步」统一实现（t142）。
 *
 * 需求：详情/编辑改成独立页面后，左上角必须有「← 返回」，且要能在**直接粘贴 URL / 新标签页打开**
 * 的情况下也知道该退到哪（此时浏览器没有站内历史，`navigate(-1)` 会跳出站点）。
 *
 * 判定依据：React Router（v6/v7）把「当前历史条目索引」写在 `window.history.state.idx`。
 * - `idx > 0` → 站内有来源页，`navigate(-1)` 回到真正的上一页；
 * - `idx` 缺失或为 0（首条历史，或非路由跳转进来的）→ 用 `replace` 跳到调用方给的兜底列表路由，
 *   避免污染历史栈（返回键不会又跳回详情页）。
 */
export interface BackNavigation {
  /** 是否可以用 navigate(-1) 回真正的上一页 */
  canGoBack: boolean;
  /** 执行返回：有历史则 -1，否则 replace 到 fallback */
  goBack: () => void;
}

const readHistoryIndex = (): number => {
  if (typeof window === 'undefined') return 0;
  const state = window.history.state as { idx?: number } | null;
  const idx = state?.idx;
  return typeof idx === 'number' && Number.isFinite(idx) ? idx : 0;
};

export const useBackNavigation = (fallback: string): BackNavigation => {
  const navigate = useNavigate();
  const canGoBack = useMemo(() => readHistoryIndex() > 0, []);

  const goBack = useCallback(() => {
    if (readHistoryIndex() > 0) {
      navigate(-1);
      return;
    }
    navigate(fallback, { replace: true });
  }, [navigate, fallback]);

  return { canGoBack, goBack };
};

export default useBackNavigation;
