import React, { useEffect } from 'react';
import LangSwitcher from '@/i18n/LangSwitcher';
import { useT } from '@/i18n';
import LoginCharacter from '@/components/login/LoginCharacter';
import LoginBrand from '@/components/login/LoginBrand';
import { toSkinStyle } from '@/components/login/skinVars';
import type { LoginThemeEntry } from '@/components/login/themes';
import type { LoginThemeId } from '@/components/login/types';
import '@/components/login/loginStage.css';
import './auth.css';

/**
 * 登录族页面的外壳（t105）：注册 / 忘记密码复用登录页的三层结构，
 * **视觉与 t101 完全一致**（全屏主题 + 左上角品牌 + 居中偏上卡片 + 同一套 `formSkin`）。
 *
 * - 主题层 `.login-stage`：与登录页同一个 `LoginCharacter`，密码焦点时角色照常遮眼/降噪；
 * - 品牌区：`LoginBrand`（猫头鹰徽标 + 全名/短名 + slogan）；
 * - 卡片：`loginStage.css` 里的 `.login-card` + 注入的 `--skin-*`，本组件只负责标题/副标题/内容/页脚。
 *
 * 标签页标题按「品牌名 · 页面名」设置，离开时还原（与登录页同一约定）。
 */
export interface AuthShellProps {
  theme: LoginThemeId;
  entry: LoginThemeEntry;
  /** 卡片标题（已本地化） */
  title: string;
  /** 卡片副标题（已本地化，可空） */
  subtitle?: string;
  /** 当前标签页名称（用于 document.title） */
  pageLabel: string;
  passwordFocused?: boolean;
  hasError?: boolean;
  /** 卡片之外的页脚（例如「已有账号？去登录」） */
  footer?: React.ReactNode;
  testId?: string;
  children: React.ReactNode;
}

const AuthShell: React.FC<AuthShellProps> = ({
  theme,
  entry,
  title,
  subtitle,
  pageLabel,
  passwordFocused = false,
  hasError = false,
  footer,
  testId = 'auth-page',
  children,
}) => {
  const { t } = useT();
  const skin = entry.formSkin;

  useEffect(() => {
    const previous = document.title;
    document.title = `${t('app.brand.name')} · ${pageLabel}`;
    return () => {
      document.title = previous;
    };
  }, [t, pageLabel]);

  return (
    <div
      className="login-page auth-page"
      data-theme={theme}
      data-group={entry.group}
      data-testid={testId}
    >
      {/* 1. 主题层：与登录页同款，铺满整个视口 */}
      <div className="login-stage" data-testid="auth-stage" aria-hidden="true">
        <LoginCharacter theme={theme} passwordFocused={passwordFocused} hasError={hasError} />
      </div>

      {/* 2. 品牌区：左上角 */}
      <LoginBrand entry={entry} />

      {/* 3. 卡片 */}
      <main className="login-main">
        <section
          className={['login-card', `login-card--${skin.inputVariant}`, 'auth-card'].join(' ')}
          style={toSkinStyle(skin)}
          data-testid="auth-card"
          data-skin={theme}
        >
          <h1 className="login-card__title">{title}</h1>
          {subtitle ? <p className="login-card__subtitle">{subtitle}</p> : null}
          {children}
          {footer ? <div className="auth-footer">{footer}</div> : null}
        </section>
      </main>

      <div className="login-lang">
        <LangSwitcher />
      </div>
    </div>
  );
};

export default AuthShell;
