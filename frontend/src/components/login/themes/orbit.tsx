import React from 'react';
import { abstractThemeClassName, useAbstractThemeMotion } from '../abstractEngine';
import type { LoginCharacterProps } from '../types';

/**
 * 主题 `orbit`：轨道（纯色底 + 两个同心细圆环 + 一个小实心点）。
 *
 * 视觉约束：颜色只有 3 个（深底 `#0f1218`、细环近白 `#e8ecf5`（低透明度）、
 * 点 `#9db4ff`）；环是 1px stroke，无阴影。
 *
 * 动态：
 * - 点沿外环 12s 一圈匀速运行（linear，绝无弹跳）；
 * - 鼠标移动给圆环带来 ±2° 的 3D 倾斜（透视 + 归一化向量，CSS 变量驱动）；
 * - 聚焦密码：点缩小到 55% 并加速到 4.5s 一圈；
 * - 登录失败：整层 1px 位移一次。
 */
const OrbitTheme: React.FC<LoginCharacterProps> = ({ passwordFocused, hasError = false, className }) => {
  const { layerRef, nudging } = useAbstractThemeMotion(hasError);

  return (
    <div
      ref={layerRef}
      data-testid="login-abstract"
      data-theme="orbit"
      className={abstractThemeClassName('orbit', { focused: passwordFocused, nudging, extra: className })}
      aria-hidden="true"
    >
      <svg className="orbit-svg" viewBox="0 0 600 600" focusable="false" aria-hidden="true">
        <g className="orbit-rings">
          <circle className="orbit-ring" cx="300" cy="300" r="120" />
          <circle className="orbit-ring orbit-ring--outer" cx="300" cy="300" r="205" />
          <g className="orbit-runner">
            <circle className="orbit-dot" cx="300" cy="95" r="4.5" />
          </g>
        </g>
      </svg>
    </div>
  );
};

export default OrbitTheme;
