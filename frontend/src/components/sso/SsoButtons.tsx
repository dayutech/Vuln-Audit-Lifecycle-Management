import React, { useEffect, useState } from 'react';
import { Button, message } from 'antd';
import { useT } from '@/i18n';
import SsoIcon from './SsoIcon';
import { beginSsoRedirect, getSsoAuthorizeUrl, getSsoProviders } from '@/api/sso';
import type { SsoProvider } from '@/api/sso';
import { knownSsoLabelKey } from './providers';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import './sso.css';

/**
 * 登录页的第三方登录按钮组（t106，后端 t104）。
 *
 * - 数据源 `GET /auth/sso/providers`（后端只返回「已开启 **且** 已配置」的，未配置的不会出现）；
 * - **没有 provider 或接口失败 → 整块不渲染**（不留空壳，也不阻塞登录框）；
 * - 点击：`GET /auth/sso/{code}/authorize-url?redirectUri=…` → 记下「登录流程 + provider」
 *   （回调页据此调 callback 并决定文案）→ 整页跳转外链；
 * - 失败分级：`400` 该登录方式未开启、`503` 尚未完成配置、其它走通用提示（`getApiErrorMessage`）；
 * - 视觉：跟随登录框 `formSkin`（`.login-sso` 样式在 t105 已建立，这里只加图标与换行）。
 */
export interface SsoButtonsProps {
  /** 测试/埋点用前缀，默认 `login-sso` */
  testIdPrefix?: string;
  /** 跳转前的钩子（例如绑定场景复用同一套按钮） */
  beforeRedirect?: () => void;
}

const SsoButtons: React.FC<SsoButtonsProps> = ({ testIdPrefix = 'login-sso', beforeRedirect }) => {
  const { t } = useT();
  const [providers, setProviders] = useState<SsoProvider[]>([]);
  const [starting, setStarting] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getSsoProviders().then((list) => {
      if (!cancelled) setProviders(list);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  if (providers.length === 0) return null;

  const labelOf = (provider: SsoProvider): string => {
    const key = knownSsoLabelKey(provider.code);
    return key ? t(key) : provider.name || provider.code;
  };

  const start = async (provider: SsoProvider) => {
    setStarting(provider.code);
    try {
      const authorize = await getSsoAuthorizeUrl(provider.code);
      if (!authorize.url) {
        message.error(t('sso.error.unavailable'));
        return;
      }
      beginSsoRedirect(provider.code, 'login', authorize.url);
      beforeRedirect?.();
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 503) {
        message.error(t('sso.error.notConfigured'));
      } else if (status === 400) {
        message.error(t('sso.error.disabled'));
      } else {
        message.error(getApiErrorMessage(error, t('sso.error.startFailed')));
      }
      setStarting(null);
    }
  };

  return (
    <div className="login-sso" data-testid={testIdPrefix}>
      <div className="login-sso__divider">{t('login.sso.title')}</div>
      <div className="login-sso__row">
        {providers.map((provider) => (
          <Button
            key={provider.code}
            onClick={() => void start(provider)}
            loading={starting === provider.code}
            data-testid={`${testIdPrefix}-${provider.code}`}
            data-sso-provider={provider.code}
          >
            <SsoIcon code={provider.icon || provider.code} size={16} className="sso-btn__icon" />
            <span className="sso-btn__label">{labelOf(provider)}</span>
          </Button>
        ))}
      </div>
    </div>
  );
};

export default SsoButtons;
