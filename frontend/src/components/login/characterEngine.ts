import { useEffect, useRef, useState } from 'react';

/**
 * 登录页角色的交互引擎（t95）。
 *
 * 设计要点：
 * - **零依赖**：只用 React + DOM，不引入 lottie / framer-motion；
 * - 瞳孔跟随用 `requestAnimationFrame` + `Math.atan2` 算方向，输出归一化向量（-1..1），
 *   具体位移量由各主题按自己的眼睛半径限幅，再做 CSS `transition` 平滑（不生硬跳变）；
 * - 仅当向量变化超过阈值才 setState，且按 ~30fps 节流，避免每帧都触发重渲染；
 * - `prefers-reduced-motion: reduce`：眨眼/呼吸/摇头全部停掉，**瞳孔仍然跟随**（CSS 里不加缓动）。
 */

export interface PupilVector {
  /** -1..1，水平方向 */
  x: number;
  /** -1..1，垂直方向 */
  y: number;
}

const IDLE_VECTOR: PupilVector = { x: 0, y: 0 };

export const prefersReducedMotion = (): boolean => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
};

/** 订阅 prefers-reduced-motion（运行中改系统设置也能立即生效） */
export const useReducedMotion = (): boolean => {
  const [reduced, setReduced] = useState<boolean>(prefersReducedMotion);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    // 初始值由 useState(prefersReducedMotion) 提供，effect 内只订阅变化（避免同步 setState 触发级联渲染）
    const handler = (event: MediaQueryListEvent) => setReduced(event.matches);
    if (typeof query.addEventListener === 'function') {
      query.addEventListener('change', handler);
      return () => query.removeEventListener('change', handler);
    }
    // 老浏览器兜底
    query.addListener(handler);
    return () => query.removeListener(handler);
  }, []);

  return reduced;
};

/** 指针相对 SVG 中心的归一化方向向量（超出画布时按单位圆限幅） */
export const computePupilVector = (
  rect: { left: number; top: number; width: number; height: number },
  pointer: { x: number; y: number },
): PupilVector => {
  const cx = rect.left + rect.width / 2;
  const cy = rect.top + rect.height / 2;
  const dx = pointer.x - cx;
  const dy = pointer.y - cy;
  const distance = Math.hypot(dx, dy);
  if (distance < 1) return IDLE_VECTOR;

  // atan2 求方向角；半径用「画布短边的一半」做归一化基准，远处自动饱和到 1
  const angle = Math.atan2(dy, dx);
  const radius = Math.max(rect.width, rect.height) / 2;
  const magnitude = Math.min(1, distance / radius);

  return {
    x: Math.round(Math.cos(angle) * magnitude * 1000) / 1000,
    y: Math.round(Math.sin(angle) * magnitude * 1000) / 1000,
  };
};

const SAME_VECTOR_EPSILON = 0.012;
const FRAME_INTERVAL_MS = 33; // ~30fps，足够顺滑且不会每帧重渲染

/** 瞳孔跟随：监听 window mousemove，rAF 里计算向量 */
export const usePupilFollow = (
  targetRef: React.RefObject<SVGSVGElement | null>,
  enabled = true,
): PupilVector => {
  const [vector, setVector] = useState<PupilVector>(IDLE_VECTOR);
  const pointerRef = useRef<{ x: number; y: number } | null>(null);
  const latestRef = useRef<PupilVector>(IDLE_VECTOR);

  useEffect(() => {
    if (!enabled || typeof window === 'undefined') return;

    const handleMove = (event: MouseEvent | PointerEvent) => {
      pointerRef.current = { x: event.clientX, y: event.clientY };
    };
    const handleLeave = () => {
      pointerRef.current = null;
    };

    window.addEventListener('mousemove', handleMove, { passive: true });
    window.addEventListener('pointermove', handleMove, { passive: true });
    window.addEventListener('mouseout', handleLeave);
    window.addEventListener('blur', handleLeave);

    let rafId = 0;
    let lastFrame = 0;
    const loop = (timestamp: number) => {
      rafId = window.requestAnimationFrame(loop);
      if (timestamp - lastFrame < FRAME_INTERVAL_MS) return;
      lastFrame = timestamp;

      const node = targetRef.current;
      const pointer = pointerRef.current;
      if (!node || !pointer) {
        if (latestRef.current !== IDLE_VECTOR) {
          latestRef.current = IDLE_VECTOR;
          setVector(IDLE_VECTOR);
        }
        return;
      }
      const rect = node.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) return;

      const next = computePupilVector(rect, pointer);
      const prev = latestRef.current;
      if (Math.abs(next.x - prev.x) > SAME_VECTOR_EPSILON || Math.abs(next.y - prev.y) > SAME_VECTOR_EPSILON) {
        latestRef.current = next;
        setVector(next);
      }
    };
    rafId = window.requestAnimationFrame(loop);

    return () => {
      window.cancelAnimationFrame(rafId);
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('pointermove', handleMove);
      window.removeEventListener('mouseout', handleLeave);
      window.removeEventListener('blur', handleLeave);
    };
  }, [targetRef, enabled]);

  return enabled ? vector : IDLE_VECTOR;
};

/** 随机眨眼：间隔 4–6s，单次闭合 ~150ms（reduced-motion 时不启动） */
export const useBlink = (reduced: boolean, minDelay = 4000, maxDelay = 6000, closeMs = 150): boolean => {
  const [blinking, setBlinking] = useState(false);

  useEffect(() => {
    if (reduced || typeof window === 'undefined') {
      return;
    }
    let openTimer: number | undefined;
    let closeTimer: number | undefined;

    const schedule = () => {
      const delay = minDelay + Math.random() * Math.max(0, maxDelay - minDelay);
      openTimer = window.setTimeout(() => {
        setBlinking(true);
        closeTimer = window.setTimeout(() => {
          setBlinking(false);
          schedule();
        }, closeMs);
      }, delay);
    };
    schedule();

    return () => {
      if (openTimer) window.clearTimeout(openTimer);
      if (closeTimer) window.clearTimeout(closeTimer);
    };
  }, [reduced, minDelay, maxDelay, closeMs]);

  return reduced ? false : blinking;
};

/** hasError 由 false → true 时抖动一次（reduced-motion 时跳过） */
export const useErrorShake = (hasError: boolean, reduced: boolean, durationMs = 620): boolean => {
  const [shaking, setShaking] = useState(false);
  const previous = useRef(hasError);

  useEffect(() => {
    const rising = hasError && !previous.current;
    previous.current = hasError;
    if (!rising || reduced) {
      // 用 0ms 定时器兜住「复位」，效果内不做同步 setState
      const reset = window.setTimeout(() => setShaking(false), 0);
      return () => window.clearTimeout(reset);
    }
    const start = window.setTimeout(() => setShaking(true), 0);
    const end = window.setTimeout(() => setShaking(false), durationMs);
    return () => {
      window.clearTimeout(start);
      window.clearTimeout(end);
    };
  }, [hasError, reduced, durationMs]);

  return shaking;
};

export interface LoginCharacterEffects {
  svgRef: React.RefObject<SVGSVGElement | null>;
  pupil: PupilVector;
  blinking: boolean;
  shaking: boolean;
  reducedMotion: boolean;
}

/** 四个主题共用的效果总入口：瞳孔跟随 + 眨眼 + 错误抖动 + reduced-motion 检测 */
export const useLoginCharacterEffects = (hasError = false): LoginCharacterEffects => {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const reducedMotion = useReducedMotion();
  const pupil = usePupilFollow(svgRef);
  const blinking = useBlink(reducedMotion);
  const shaking = useErrorShake(hasError, reducedMotion);

  return { svgRef, pupil, blinking, shaking, reducedMotion };
};

/** 各主题通用的 class 组装 */
export const characterClassName = (
  themeClassName: string,
  options: { blinking: boolean; shaking: boolean; shielding: boolean },
): string =>
  [
    'login-char__svg',
    themeClassName,
    options.blinking ? 'is-blinking' : '',
    options.shaking ? 'is-shaking' : '',
    options.shielding ? 'is-shielding' : '',
  ]
    .filter(Boolean)
    .join(' ');
