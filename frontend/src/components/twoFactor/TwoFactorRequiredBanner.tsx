import React, { useEffect, useState } from 'react';
import { Alert, Button } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { TWO_FACTOR_REQUIRED_EVENT, TWO_FACTOR_SETUP_REQUIRED } from '@/api/twoFactor';
import { logout } from '@/api/auth';
import { useUserStore } from '@/store/userStore';
import { useT } from '@/i18n';

/**
 * 强制 2FA 引导（t137）。
 *
 * 触发条件：任何业务请求返回 **403 `code=TWO_FACTOR_SETUP_REQUIRED`**（后端 `TwoFactorEnforcementInterceptor`）。
 * 请求拦截器把这件事派发成 window 事件，这里挂一条**不可关闭**的横幅（`Alert` 无 close 按钮），
 * 引导到 `/account/security`；同时给一个「退出登录」出口，避免用户被卡死。
 *
 * 为什么不做成模态：模态会挡住 2FA 设置页本身（用户还要在那里操作二维码），横幅不挡交互。
 * `/api/v1/auth/**`、`/api/v1/users/me/2fa`、`/api/v1/users/me/preferences` 都在后端白名单里，
 * 所以设置 2FA 与登出不会被这条引导循环拦截。
 */
const TwoFactorRequiredBanner: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const location = useLocation();
  const { clearAuth } = useUserStore();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const handler = () => setVisible(true);
    window.addEventListener(TWO_FACTOR_REQUIRED_EVENT, handler);
    return () => window.removeEventListener(TWO_FACTOR_REQUIRED_EVENT, handler);
  }, []);

  // 已经在 2FA 设置页时不再重复提示（用户正在处理）
  if (!visible || location.pathname === '/account/security') return null;

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
      clearAuth();
      window.location.href = '/login';
    }
  };

  return (
    <Alert
      type="warning"
      showIcon
      banner
      data-testid="twofa-required-banner"
      data-error-code={TWO_FACTOR_SETUP_REQUIRED}
      message={t('twoFactor.required.title')}
      description={t('twoFactor.required.desc')}
      action={
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Button size="small" type="primary" onClick={() => navigate('/account/security')} data-testid="twofa-required-action">
            {t('twoFactor.required.action')}
          </Button>
          <Button size="small" onClick={() => void handleLogout()} data-testid="twofa-required-logout">
            {t('twoFactor.required.logout')}
          </Button>
        </div>
      }
    />
  );
};

export default TwoFactorRequiredBanner;
