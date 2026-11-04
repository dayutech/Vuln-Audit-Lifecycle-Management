import React from 'react';
import type { LoginThemeEntry } from '@/components/login/themes';
import './loginThemeThumbnail.css';

/**
 * 主题缩略图（t97 偏好弹窗用）。
 *
 * 设计约束（来自 t99 注册表注释）：缩略图必须是**静态的**，不要把 8 个动画实例塞进弹窗。
 * 因此：
 * - `character` 分组：渲染主题自带的迷你 SVG 角色（纯受控，`passwordFocused=false`，
 *   不挂大画布交互），配 palette 渐变底 —— 这是用户最关心的"长什么样"；
 * - `abstract` 分组：**不渲染整页背景组件**（它们 `position: fixed` 铺满视口，
 *   塞进卡片会盖住弹窗），改用 palette 色块构图呈现配色差异；真实效果走弹窗里的「预览」
 *   （iframe 加载真实登录页）。
 *
 * 缩略图纯展示，不参与点击（点击由卡片承担），因此 `aria-hidden`。
 */

const gradientOf = (palette?: string[]): string => {
  const [base = '#f2f4f7', accent = '#1f2328', third = '#ffffff'] = palette ?? [];
  return [
    `radial-gradient(circle at 78% 20%, ${third} 0%, transparent 42%)`,
    `radial-gradient(circle at 22% 82%, ${accent} 0%, transparent 46%)`,
    `linear-gradient(135deg, ${base} 0%, ${base} 100%)`,
  ].join(', ');
};

export interface LoginThemeThumbnailProps {
  entry: LoginThemeEntry;
}

const LoginThemeThumbnail: React.FC<LoginThemeThumbnailProps> = ({ entry }) => {
  const Character = entry.Component;
  return (
    <div className="theme-thumb" data-testid={`theme-thumb-${entry.id}`} aria-hidden="true">
      <div className="theme-thumb__bg" style={{ background: gradientOf(entry.palette) }} />
      {entry.group === 'character' ? (
        <div className="theme-thumb__art">
          <Character passwordFocused={false} hasError={false} size={64} />
        </div>
      ) : (
        <div className="theme-thumb__swatches">
          {(entry.palette ?? []).map((color, index) => (
            <span key={`${color}-${index}`} style={{ background: color }} />
          ))}
        </div>
      )}
    </div>
  );
};

export default LoginThemeThumbnail;
