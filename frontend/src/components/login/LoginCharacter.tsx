import React from 'react';
import './loginCharacter.css';
import './loginAbstract.css';
import { getLoginTheme } from './themes';
import type { LoginCharacterProps, LoginThemeId } from './types';

export interface LoginCharacterWrapperProps extends Omit<LoginCharacterProps, 'passwordFocused'> {
  /** 由外层决定（t97 的选择器 / 登录页默认值） */
  theme: LoginThemeId;
  /** 焦点在密码框：角色抬臂遮眼 / 抽象主题收拢·淡出·加速·下沉 */
  passwordFocused: boolean;
}

/**
 * 登录页主题槽位：按 `theme` 渲染对应主题，八套主题接口一致（`LoginCharacterProps`）。
 *
 * - `group === 'character'`（t95）：小尺寸 SVG 角色，占左侧角色位；
 * - `group === 'abstract'`（t99）：铺满整页的抽象背景层，**不做角色**，
 *   外层由登录页放在 `.login-abstract-layer`（`position: fixed; inset: 0`）里，
 *   这里的槽位靠 `.login-theme-slot--abstract` 撑满。
 *
 * 纯受控组件：主题、密码焦点态、错误态都由 props 传入；主题内部只跑与视觉相关的
 * 效果（瞳孔跟随 / 眨眼 / 抖动，或 光晕 / 网格 / 轨道 / 视差）。
 */
const LoginCharacter: React.FC<LoginCharacterWrapperProps> = ({ theme, ...rest }) => {
  const entry = getLoginTheme(theme);
  const Theme = entry.Component;
  const abstract = entry.group === 'abstract';

  return (
    <div
      data-testid="login-character"
      data-theme={entry.id}
      data-group={entry.group}
      className={abstract ? 'login-theme-slot login-theme-slot--abstract' : 'login-theme-slot'}
    >
      <Theme {...rest} />
    </div>
  );
};

export default LoginCharacter;
