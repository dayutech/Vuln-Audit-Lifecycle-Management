import { useEffect, useRef } from 'react';
import type { RefObject } from 'react';
import { useErrorShake, useReducedMotion } from './characterEngine';

/**
 * 抽象极简主题（t99）的动效引擎 —— 与 `characterEngine`（t95 卡通角色）并列，互不依赖。
 *
 * 设计要点：
 * - **零依赖**：只用 React + DOM；
 * - 鼠标位置**不经过 React state**：`requestAnimationFrame`（~30fps）里直接把归一化向量 / 像素坐标
 *   写成 CSS 自定义属性（`--lp-x/--lp-y/--lp-nx/--lp-ny`），由各主题在 CSS 里用 `calc()` 做视差/倾斜。
 *   这样每帧只有一次行内属性写入，**不触发重渲染，也不触发重排**（transform / background-position 都在合成层）；
 * - 只有指针真的动了（键值变化）才写 DOM，静止时零开销；
 * - `prefers-reduced-motion: reduce`：不订阅指针、不写变量、CSS 动画全部关闭 → 画面完全静态；
 * - `hasError` 只是「1px 位移一次」，复用 `useErrorShake` 的上升沿脉冲（幅度由 CSS 控制成 1px），
 *   **不做**卡通式抖动。
 */

export interface PointerSample {
  /** 指针在元素内的像素坐标（供径向光斑定位） */
  px: number;
  py: number;
  /** 相对元素中心的归一化向量，-1..1（供视差/倾斜用） */
  nx: number;
  ny: number;
}

const clampUnit = (value: number): number => (value < -1 ? -1 : value > 1 ? 1 : value);

/** 指针 → 归一化向量 + 像素坐标（元素中心为 0，超出元素时按 ±1 限幅） */
export const computePointerSample = (
  rect: { left: number; top: number; width: number; height: number },
  pointer: { x: number; y: number },
): PointerSample => {
  const px = pointer.x - rect.left;
  const py = pointer.y - rect.top;
  const halfWidth = rect.width / 2 || 1;
  const halfHeight = rect.height / 2 || 1;

  return {
    px,
    py,
    nx: Math.round(clampUnit((px - halfWidth) / halfWidth) * 1000) / 1000,
    ny: Math.round(clampUnit((py - halfHeight) / halfHeight) * 1000) / 1000,
  };
};

const FRAME_INTERVAL_MS = 33; // ~30fps：足够顺滑，且不会每帧读写样式
const POINTER_VARS = ['--lp-x', '--lp-y', '--lp-nx', '--lp-ny'];

const clearPointerVars = (node: HTMLElement) => {
  POINTER_VARS.forEach((name) => node.style.removeProperty(name));
};

/**
 * 把指针位置写成 CSS 变量（组件根元素的 style 上）。
 *
 * 使用方在 CSS 里消费：
 * - `background: radial-gradient(circle 120px at var(--lp-x) var(--lp-y), …)`；
 * - `transform: translate3d(calc(var(--lp-nx) * 6px), calc(var(--lp-ny) * 6px), 0)`。
 *
 * 默认值（50% / 42% / 0）写在 `loginAbstract.css` 的 `.login-abstract` 上，
 * 因此在 reduced-motion、指针未进入、组件首次渲染时都不会出现「跳到左上角」。
 */
export const usePointerVars = (ref: RefObject<HTMLElement | null>, enabled = true): void => {
  const reducedMotion = useReducedMotion();

  useEffect(() => {
    if (!enabled || reducedMotion || typeof window === 'undefined') return;
    const node = ref.current;
    if (!node) return;

    let pointer: { x: number; y: number } | null = null;
    let frame = 0;
    let lastTime = 0;
    let lastKey = '';

    const onMove = (event: MouseEvent | PointerEvent) => {
      pointer = { x: event.clientX, y: event.clientY };
    };
    const onLeave = () => {
      pointer = null;
      lastKey = '';
      clearPointerVars(node);
    };

    const loop = (time: number) => {
      frame = window.requestAnimationFrame(loop);
      if (time - lastTime < FRAME_INTERVAL_MS) return;
      lastTime = time;

      const sample = pointer;
      if (!sample) return;
      const rect = node.getBoundingClientRect();
      if (rect.width < 1 || rect.height < 1) return;

      // 指针没动（且尺寸没变）就不写样式，静止时零开销
      const key = `${Math.round(sample.x)}|${Math.round(sample.y)}|${Math.round(rect.width)}|${Math.round(rect.height)}`;
      if (key === lastKey) return;
      lastKey = key;

      const next = computePointerSample(rect, sample);
      const style = node.style;
      style.setProperty('--lp-x', `${next.px.toFixed(1)}px`);
      style.setProperty('--lp-y', `${next.py.toFixed(1)}px`);
      style.setProperty('--lp-nx', next.nx.toFixed(3));
      style.setProperty('--lp-ny', next.ny.toFixed(3));
    };

    window.addEventListener('mousemove', onMove, { passive: true });
    window.addEventListener('pointermove', onMove, { passive: true });
    window.addEventListener('mouseout', onLeave);
    window.addEventListener('blur', onLeave);
    frame = window.requestAnimationFrame(loop);

    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('mouseout', onLeave);
      window.removeEventListener('blur', onLeave);
      clearPointerVars(node);
    };
  }, [ref, enabled, reducedMotion]);
};

/** 错误态「1px 位移一次」的时长（比卡通抖动的 620ms 更短、更克制） */
export const ABSTRACT_NUDGE_MS = 480;

export interface AbstractThemeMotion {
  /** 挂到主题根元素上：指针坐标写在这个节点的 style 上 */
  layerRef: RefObject<HTMLDivElement | null>;
  /** 系统是否要求降低动效 */
  reducedMotion: boolean;
  /** hasError 上升沿：整层 1px 位移一次 */
  nudging: boolean;
}

/** 四套抽象主题共用的动效入口（指针变量 + 错误位移脉冲 + reduced-motion 检测） */
export const useAbstractThemeMotion = (hasError = false): AbstractThemeMotion => {
  const layerRef = useRef<HTMLDivElement | null>(null);
  const reducedMotion = useReducedMotion();
  const nudging = useErrorShake(hasError, reducedMotion, ABSTRACT_NUDGE_MS);

  usePointerVars(layerRef, !reducedMotion);

  return { layerRef, reducedMotion, nudging };
};

/** 抽象主题根元素的 class 组装（与卡通主题的 `characterClassName` 对称） */
export const abstractThemeClassName = (
  theme: string,
  options: { focused: boolean; nudging: boolean; extra?: string },
): string =>
  [
    'login-abstract',
    `theme-${theme}`,
    options.focused ? 'is-focused' : '',
    options.nudging ? 'is-nudged' : '',
    options.extra ?? '',
  ]
    .filter(Boolean)
    .join(' ');
