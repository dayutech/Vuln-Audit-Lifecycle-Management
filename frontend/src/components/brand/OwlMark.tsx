import React from 'react';

/**
 * 平台徽标（t108）：一只卡通猫头鹰，重点突出「视力 / 洞察入微」。
 *
 * 设计要点：
 * - **扁平**：纯色块，零滤镜、零渐变、零阴影，配色 3 个（深墨 `#1f2328`、琥珀 `#e8901f`、米白 `#fdf6e8`）；
 * - **眼睛是主角**：两眼虹膜跨度约占头宽 62%（眼睛是视觉焦点），虹膜 + 瞳孔双层，
 *   瞳孔上带 2 个高光点（镜头感），眉线内低外高（「专注 / 洞察」的压迫角度，而非呆萌圆眼）；
 * - **猫头鹰特征**：两只耳羽三角、两个交叠圆面盘、小菱形喙；
 * - **小尺寸**：`size <= 20` 自动切简化版（去掉高光、加粗虹膜/眉线/喙），保证 16px 可辨认；
 * - **两种用法**：`variant="monochrome"`（全 currentColor + 透明度分层，用在深色侧边栏）/
 *   `variant="color"`（三色，用在登录页浅色舞台与 favicon）。深色登录主题用 mono 版。
 *
 * 每个部件都带 `data-owl="head|tuft|disc|iris|pupil|highlight|brow|beak"`，
 * 供自动化验证按结构（而不是按颜色）核对几何与配色 —— 两种 variant 都能测。
 *
 * 不引入任何图标库或图片资源，纯内联 SVG。
 */

export type OwlMarkVariant = 'color' | 'monochrome';

export interface OwlMarkProps {
  /** 边长（px）；`<= 20` 时切简化路径（16px favicon 档） */
  size?: number;
  /** 配色方案：`color` 三色 / `monochrome` 单色（currentColor） */
  variant?: OwlMarkVariant;
  /** 无障碍标题；提供则作为 `role="img"` 的可读名，否则整体 `aria-hidden` */
  title?: string;
  className?: string;
  style?: React.CSSProperties;
}

/** 三色调色板（深墨头 + 琥珀虹膜 + 米白面盘/高光） */
const INK = '#1f2328';
const IRIS = '#e8901f';
const FACE = '#fdf6e8';

const OwlMark: React.FC<OwlMarkProps> = ({ size = 32, variant = 'color', title, className, style }) => {
  const compact = size <= 20;
  const mono = variant === 'monochrome';

  const head = mono ? 'currentColor' : INK;
  const headOpacity = mono ? 0.16 : 1;
  const tuft = mono ? 'currentColor' : INK;
  const tuftOpacity = mono ? 0.55 : 1;
  const disc = mono ? 'currentColor' : FACE;
  const discOpacity = mono ? 0.24 : 1;
  const irisFill = mono ? 'none' : IRIS;
  const irisStroke = mono ? 'currentColor' : 'none';
  const irisStrokeWidth = compact ? 4.6 : 2.8;
  const pupilFill = mono ? 'currentColor' : INK;
  const browStroke = mono ? 'currentColor' : INK;
  const beakFill = mono ? 'currentColor' : IRIS;

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      className={className}
      style={{ display: 'block', flex: '0 0 auto', ...style }}
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
      focusable="false"
    >
      {title ? <title>{title}</title> : null}

      {/* 耳羽：两只小三角 */}
      <path data-owl="tuft" d="M13 22 16.5 7 27 14.5Z" fill={tuft} opacity={tuftOpacity} />
      <path data-owl="tuft" d="M51 22 47.5 7 37 14.5Z" fill={tuft} opacity={tuftOpacity} />

      {/* 头部 */}
      <circle data-owl="head" cx="32" cy="34" r="22" fill={head} opacity={headOpacity} />

      {/* 面盘：两个交叠圆 */}
      <circle data-owl="disc" cx="24.6" cy="33" r="13" fill={disc} opacity={discOpacity} />
      <circle data-owl="disc" cx="39.4" cy="33" r="13" fill={disc} opacity={discOpacity} />

      {/* 虹膜 + 瞳孔（眼睛是视觉焦点） */}
      <circle
        data-owl="iris"
        cx="24.6"
        cy="33"
        r={compact ? 7 : 6.4}
        fill={irisFill}
        stroke={irisStroke}
        strokeWidth={irisStrokeWidth}
      />
      <circle
        data-owl="iris"
        cx="39.4"
        cy="33"
        r={compact ? 7 : 6.4}
        fill={irisFill}
        stroke={irisStroke}
        strokeWidth={irisStrokeWidth}
      />
      <circle data-owl="pupil" cx="24.6" cy="33" r={compact ? 4.2 : 3.1} fill={pupilFill} />
      <circle data-owl="pupil" cx="39.4" cy="33" r={compact ? 4.2 : 3.1} fill={pupilFill} />

      {/* 瞳孔高光（简化版去掉：16px 下会糊成一团） */}
      {compact ? null : (
        <>
          <circle data-owl="highlight" cx="23.5" cy="31.8" r="1.35" fill={FACE} />
          <circle data-owl="highlight" cx="25.7" cy="34.6" r="0.72" fill={FACE} />
          <circle data-owl="highlight" cx="38.3" cy="31.8" r="1.35" fill={FACE} />
          <circle data-owl="highlight" cx="40.5" cy="34.6" r="0.72" fill={FACE} />
        </>
      )}

      {/* 眉线：内低外高 → 专注 / 洞察 */}
      <path
        data-owl="brow"
        d="M14 21.5 26 25.6"
        stroke={browStroke}
        strokeWidth={compact ? 4.2 : 3.2}
        strokeLinecap="round"
        fill="none"
      />
      <path
        data-owl="brow"
        d="M50 21.5 38 25.6"
        stroke={browStroke}
        strokeWidth={compact ? 4.2 : 3.2}
        strokeLinecap="round"
        fill="none"
      />

      {/* 喙：小菱形 */}
      <path
        data-owl="beak"
        d={compact ? 'M32 38.4 37.2 43.6 32 49 26.8 43.6Z' : 'M32 39.5 36.4 44.2 32 48.8 27.6 44.2Z'}
        fill={beakFill}
      />
    </svg>
  );
};

export default OwlMark;
