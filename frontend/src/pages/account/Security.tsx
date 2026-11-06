import React from 'react';
import TwoFactorCard from '@/components/twoFactor/TwoFactorCard';
import { useT } from '@/i18n';

/**
 * 账号安全（t137）。
 *
 * 为什么单独一页而不是塞进 `/settings`：`/settings` 是**管理员专属**（t110 的页面级 gate，
 * 因为其中的登录页默认主题与 SSO 凭据都只有 ADMIN 能读写），而两步验证是**每个登录用户**
 * 都要能自助设置的安全项 —— 尤其管理员开启强制 2FA 后，普通用户必须能进到这里开通。
 *
 * 路由：`/account/security`（用户下拉菜单「账号安全」进入；强制 2FA 引导也指向这里）。
 */
const AccountSecurity: React.FC = () => {
  const { t } = useT();
  return (
    <div data-testid="account-security-page">
      <div style={{ marginBottom: 16 }}>
        <h2 style={{ margin: 0, fontSize: 20, fontWeight: 700 }}>{t('twoFactor.menu')}</h2>
        <div style={{ marginTop: 4, fontSize: 13, color: '#6b7280' }}>{t('twoFactor.pageSubtitle')}</div>
      </div>
      <TwoFactorCard />
    </div>
  );
};

export default AccountSecurity;

