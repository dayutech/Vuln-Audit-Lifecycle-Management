import { useCallback, useEffect, useState } from 'react';

/**
 * 获取验证码的 60 秒倒计时（t105）。
 *
 * - `start()` 从 `seconds` 开始倒数，归零后可再次发送；
 * - 卸载或重新 `start()` 都会清掉旧定时器（不会出现"点了两次、倒数跳两秒"）。
 */
export interface CodeCooldown {
  remaining: number;
  active: boolean;
  start: (seconds?: number) => void;
  reset: () => void;
}

export const useCooldown = (defaultSeconds = 60): CodeCooldown => {
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (remaining <= 0) return;
    const timer = window.setTimeout(() => setRemaining((value) => (value > 0 ? value - 1 : 0)), 1000);
    return () => window.clearTimeout(timer);
  }, [remaining]);

  const start = useCallback(
    (seconds?: number) => setRemaining(Math.max(1, Math.round(seconds ?? defaultSeconds))),
    [defaultSeconds],
  );
  const reset = useCallback(() => setRemaining(0), []);

  return { remaining, active: remaining > 0, start, reset };
};
