import React from 'react';
import { abstractThemeClassName, useAbstractThemeMotion } from '../abstractEngine';
import { useT } from '@/i18n';
import type { LoginCharacterProps } from '../types';

/**
 * 主题 `big-type`：大字排版（纯色底 + 超大产品名，字重/字号对比强）。
 *
 * 视觉约束：颜色只有 2 个（米白底 `#f4f2ee`、墨色字 `#111318`）；无阴影、无图形，
 * 只靠「细字重 / 粗字重 + 字号差」建立层次，四周大片留白。
 *
 * 动态：
 * - 鼠标移动给文字带来 ±6px 视差（CSS 变量驱动，700ms 缓出，跟手但不晃眼）；
 * - 聚焦密码：文字下沉 10px 并淡到 30%，把注意力让给表单；
 * - 登录失败：整层 1px 位移一次（不抖动）。
 *
 * 该主题自带产品名，因此登录页品牌区不再重复渲染标题（见 `themes.ts` 的 `carriesWordmark`）。
 */
const BigTypeTheme: React.FC<LoginCharacterProps> = ({ passwordFocused, hasError = false, className }) => {
  const { layerRef, nudging } = useAbstractThemeMotion(hasError);
  const { t } = useT();

  return (
    <div
      ref={layerRef}
      data-testid="login-abstract"
      data-theme="big-type"
      className={abstractThemeClassName('big-type', { focused: passwordFocused, nudging, extra: className })}
      aria-hidden="true"
    >
      <div className="big-type__word">
        <span className="big-type__line big-type__line--thin">{t('login.theme.bigType.line1')}</span>
        <span className="big-type__line big-type__line--thick">{t('login.theme.bigType.line2')}</span>
      </div>
    </div>
  );
};

export default BigTypeTheme;
