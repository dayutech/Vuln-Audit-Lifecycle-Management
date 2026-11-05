import type React from 'react';
import type { LoginFormSkin } from './types';

/**
 * 把注册表里的 `formSkin` 转成卡片上的 CSS 变量（t101 定义，t105 抽出复用）。
 *
 * 登录 / 注册 / 忘记密码三个页面共用同一套皮肤：`loginStage.css` 统一消费 `--skin-*`，
 * 页面只负责把数据塞成变量，不再各写一份卡片样式。
 */
export const toSkinStyle = (skin: LoginFormSkin): React.CSSProperties => {
  const vars: Record<string, string | number> = {
    '--skin-card-bg': skin.cardBg,
    '--skin-card-border': skin.cardBorder,
    '--skin-card-radius': skin.cardRadius,
    '--skin-card-padding': skin.cardPadding,
    '--skin-card-shadow': skin.cardShadow ?? 'none',
    '--skin-card-backdrop': skin.cardBackdrop ?? 'none',
    '--skin-ink': skin.ink,
    '--skin-ink-soft': skin.inkSoft,
    '--skin-input-border': skin.inputBorder,
    '--skin-input-bg': skin.inputBg,
    '--skin-input-focus': skin.inputFocus,
    '--skin-label-weight': skin.labelWeight,
    '--skin-btn-radius': skin.buttonRadius,
    '--skin-btn-bg': skin.buttonBg,
    '--skin-btn-color': skin.buttonColor,
  };
  return vars as unknown as React.CSSProperties;
};
