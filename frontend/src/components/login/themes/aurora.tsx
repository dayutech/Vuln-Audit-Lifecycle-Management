import React from 'react';
import { abstractThemeClassName, useAbstractThemeMotion } from '../abstractEngine';
import type { LoginCharacterProps } from '../types';

/**
 * 主题 `aurora`：极简光晕（深墨底 + 两团柔和径向光斑）。
 *
 * 视觉约束：颜色只有 3 个（墨底 `#0a0c11`、光晕 `#7c9cff`、留白处的近白文字由 CSS 变量给），
 * 无阴影、无描边噪点；两团光斑都是单一 `radial-gradient`，不做多层叠加。
 *
 * 动态：
 * - 光斑 17s / 14s 缓慢漂移（幅度只有几个 vmax，绝不弹跳）；
 * - 光标处一层**极淡**光晕跟随（半径 360px，alpha 0.13，低强度大半径）；
 * - 聚焦密码：光斑整体收拢（scale .86）并降亮（opacity .74）；
 * - 登录失败：整层 1px 位移一次。
 *
 * 该主题铺满整页做背景层（不占角色位），表单卡片始终在上层。
 */
const AuroraTheme: React.FC<LoginCharacterProps> = ({ passwordFocused, hasError = false, className }) => {
  const { layerRef, nudging } = useAbstractThemeMotion(hasError);

  return (
    <div
      ref={layerRef}
      data-testid="login-abstract"
      data-theme="aurora"
      className={abstractThemeClassName('aurora', { focused: passwordFocused, nudging, extra: className })}
      aria-hidden="true"
    >
      <div className="aurora-field">
        <div className="aurora-blob aurora-blob--a">
          <span className="aurora-blob__core" />
        </div>
        <div className="aurora-blob aurora-blob--b">
          <span className="aurora-blob__core" />
        </div>
      </div>
      <div className="aurora-follow" />
    </div>
  );
};

export default AuroraTheme;
