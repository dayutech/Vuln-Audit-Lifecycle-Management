import React, { useEffect, useState } from 'react';
import { Button, Tooltip, message } from 'antd';
import { useT } from '@/i18n';
import SsoIcon from './SsoIcon';
import { beginSsoRedirect, getSsoAuthorizeUrl, getSsoProviders, isProviderConfigured } from '@/api/sso';
import type { SsoProvider } from '@/api/sso';
import { knownSsoLabelKey } from './providers';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import './sso.css';

/**
 * 登录页的第三方登录按钮组（t106 建立，t118 改为「启用即展示」）。
 *
 * - 数据源 `GET /auth/sso/providers`：t116 起后端**返回全部已启用项**并带 `configured` 标记
 *   （老后端只返回已配置项、无该字段 → 视为 `configured=true`，行为不变）；
 * - **按钮全部渲染**（有多少 enabled 就有多少按钮）；没有 provider 或接口失败才整块不渲染；
 * - 点击：
 *   - `configured=true` → `GET /auth/sso/{code}/authorize-url` → 记「登录流程 + provider」→ 整页跳转；
 *   - `configured=false` → **不跳转**，直接提示「该登录方式尚未完成配置，请联系管理员」
 *     （后端 authorize-url 也会 503，这里提前拦一次，少一次往返且文案更明确）；
 * - **不用禁用态**：禁用的按钮会被误读成「没有权限」；这里用可点按钮 + 未配置小圆点 + tooltip；
 * - 失败分级：`400` 未开启、`503` 未配置、其它走 `getApiErrorMessage`；
 * - 视觉：跟随登录框 `formSkin`（`.login-sso` 样式在 t105 已建立）。
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
    // 未配置凭据：不跳转，直接提示（按钮仍可点，不是禁用态）
    if (!isProviderConfigured(provider)) {
      message.warning(t('sso.notConfiguredNotice', { name: labelOf(provider) }));
      return;
    }
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
        {providers.map((provider) => {
          const configured = isProviderConfigured(provider);
          return (
            <Tooltip
              key={provider.code}
              title={configured ? undefined : t('sso.notConfiguredHint')}
              placement="top"
            >
              <Button
                onClick={() => void start(provider)}
                loading={starting === provider.code}
                className={configured ? undefined : 'sso-btn--unconfigured'}
                data-testid={`${testIdPrefix}-${provider.code}`}
                data-sso-provider={provider.code}
                data-sso-configured={configured ? 'true' : 'false'}
              >
                <SsoIcon code={provider.icon || provider.code} size={16} className="sso-btn__icon" />
                <span className="sso-btn__label">{labelOf(provider)}</span>
                {configured ? null : (
                  <span className="sso-btn__dot" data-testid={`${testIdPrefix}-${provider.code}-dot`} aria-hidden="true" />
                )}
              </Button>
            </Tooltip>
          );
        })}
      </div>
    </div>
  );
};

export default SsoButtons;
