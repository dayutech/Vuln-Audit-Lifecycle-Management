import React from 'react';
import { abstractThemeClassName, useAbstractThemeMotion } from '../abstractEngine';
import type { LoginCharacterProps } from '../types';

/**
 * 主题 `mono-grid`：极简网格（浅灰底 + 1px 低对比网格）。
 *
 * 视觉约束：颜色只有 2 个（浅灰 `#f4f5f7`、墨色线 `rgba(20,24,33,.07)`），
 * 1px 线用 repeating 线性渐变实现，无阴影、无噪点，四周大片留白。
 *
 * 动态：
 * - 光标 120px 半径内的网格交叉点轻微加深（径向渐隐），随指针移动；
 * - 聚焦密码：网格整体淡出到 20% 不透明度，把注意力让给表单；
 * - 登录失败：整层 1px 位移一次。
 */
const MonoGridTheme: React.FC<LoginCharacterProps> = ({ passwordFocused, hasError = false, className }) => {
  const { layerRef, nudging } = useAbstractThemeMotion(hasError);

  return (
    <div
      ref={layerRef}
      data-testid="login-abstract"
      data-theme="mono-grid"
      className={abstractThemeClassName('mono-grid', { focused: passwordFocused, nudging, extra: className })}
      aria-hidden="true"
    >
      <div className="mono-grid__lines" />
      <div className="mono-grid__spot" />
    </div>
  );
};

export default MonoGridTheme;
