import React from 'react';
import OwlMark from '@/components/brand/OwlMark';
import { useT } from '@/i18n';
import type { LoginThemeEntry } from './themes';

/**
 * 登录族页面的左上角品牌区（t108 建立，t105 抽出复用）。
 *
 * 登录 / 注册 / 忘记密码三个页面共用：猫头鹰徽标（深色舞台用 mono 版、浅色用三色版）
 * + 全名（桌面）+ 短名（<768px 由 CSS 切换）+ slogan。纯展示，不含交互。
 */
export interface LoginBrandProps {
  entry: LoginThemeEntry;
}

const LoginBrand: React.FC<LoginBrandProps> = ({ entry }) => {
  const { t } = useT();

  return (
    <header className="login-brand" data-testid="login-brand">
      <span className="login-brand__mark">
        <OwlMark size={30} variant={entry.tone === 'dark' ? 'monochrome' : 'color'} />
      </span>
      <div className="login-brand__text">
        <span className="login-brand__name login-brand__name--full" data-testid="login-brand-name">
          {t('app.brand.name')}
        </span>
        <span className="login-brand__name login-brand__name--short" data-testid="login-brand-name-short">
          {t('app.brand.nameShort')}
        </span>
        <span className="login-brand__slogan" data-testid="login-brand-slogan">
          {t('app.brand.slogan')}
        </span>
      </div>
    </header>
  );
};

export default LoginBrand;
