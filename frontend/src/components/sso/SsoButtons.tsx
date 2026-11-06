import React, { useEffect, useState } from 'react';
import { Tooltip, message } from 'antd';
import { useT } from '@/i18n';
import SsoIcon from './SsoIcon';
import WechatQrModal from './WechatQrModal';
import { beginSsoRedirect, getSsoAuthorizeUrl, getSsoProviders, isProviderConfigured } from '@/api/sso';
import type { SsoProvider } from '@/api/sso';
import { knownSsoLabelKey } from './providers';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useUserStore } from '@/store/userStore';
import './sso.css';

/**
 * 登录页的第三方登录按钮组（t106 建立，t118「启用即展示」，t138 微信扫码）。
 *
 * - 数据源 `GET /auth/sso/providers`：t116 起后端**返回全部已启用项**并带 `configured` 标记
 *   （老后端只返回已配置项、无该字段 → 视为 `configured=true`，行为不变）；
 * - **按钮全部渲染**（有多少 enabled 就有多少按钮）；没有 provider 或接口失败才整块不渲染；
 * - 点击：
 *   - **微信（t138）**：桌面端（≥768px）弹「微信扫码登录」模态（二维码 = authorize-url，手机微信扫码后
 *     在手机端完成授权）；移动端（<768px）自己就是扫码端，直接整页跳转（与其它 provider 一致）；
 *   - 其它 provider 且 `configured=true` → `GET /auth/sso/{code}/authorize-url` → 记录「登录流程 + provider」→ 跳转；
 *   - `configured=false` → 微信走「未配置」模态（提示联系管理员，不用禁用态）；
 *     其它 provider 直接提示「该登录方式尚未完成配置，请联系管理员」；
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

/** 桌面断点：≥768px 视为桌面端（微信走二维码，不走整页跳转） */
const DESKTOP_MIN_WIDTH = 768;
/** 微信 provider code */
const WECHAT_CODE = 'wechat';

const isDesktopViewport = (): boolean =>
  typeof window === 'undefined' ? true : window.innerWidth >= DESKTOP_MIN_WIDTH;

const SsoButtons: React.FC<SsoButtonsProps> = ({ testIdPrefix = 'login-sso', beforeRedirect }) => {
  const { t } = useT();
  const [providers, setProviders] = useState<SsoProvider[]>([]);
  const [starting, setStarting] = useState<string | null>(null);
  /** t138：微信扫码模态 */
  const [wechatOpen, setWechatOpen] = useState(false);
  const user = useUserStore((state) => state.user);
  const isAdmin = user?.role === 'admin';

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

  /** 整页跳转（其它 provider 与「移动端微信」共用） */
  const redirect = async (provider: SsoProvider) => {
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

  const start = async (provider: SsoProvider) => {
    const isWechat = provider.code === WECHAT_CODE;
    // t138：微信在桌面端走二维码模态（未配置也进模态，里面给「未配置」提示，不用禁用态）
    if (isWechat && isDesktopViewport()) {
      setWechatOpen(true);
      return;
    }
    // 未配置凭据：不跳转，直接提示（按钮仍可点，不是禁用态）
    if (!isProviderConfigured(provider)) {
      message.warning(t('sso.notConfiguredNotice', { name: labelOf(provider) }));
      return;
    }
    await redirect(provider);
  };

  const wechatProvider = providers.find((item) => item.code === WECHAT_CODE) ?? null;

  return (
    <div className="login-sso" data-testid={testIdPrefix}>
      <div className="login-sso__divider">{t('login.sso.title')}</div>
      <div className="login-sso__row">
        {providers.map((provider) => {
          const configured = isProviderConfigured(provider);
          const label = labelOf(provider);
          // t122：只显示 logo（无文字、无按钮底），tooltip 显示平台名；未配置的建议追加后缀
          const tip = configured ? label : `${label} · ${t('sso.notConfiguredBadge')}`;
          const isWechat = provider.code === WECHAT_CODE;
          return (
            <span
              key={provider.code}
              className="sso-logo-wrap"
              data-testid={`sso-provider-${provider.code}`}
              data-sso-configured={configured ? 'true' : 'false'}
            >
              <Tooltip title={tip} placement="top">
                <button
                  type="button"
                  className="sso-logo"
                  onClick={() => void start(provider)}
                  aria-label={tip}
                  disabled={starting === provider.code}
                  data-testid={isWechat ? 'sso-wechat-qr-trigger' : `${testIdPrefix}-${provider.code}`}
                  data-sso-provider={provider.code}
                  data-sso-configured={configured ? 'true' : 'false'}
                  data-sso-wechat={isWechat ? 'true' : 'false'}
                >
                  {starting === provider.code ? (
                    <span className="sso-logo__loading" aria-hidden="true" />
                  ) : (
                    <SsoIcon code={provider.icon || provider.code} size={22} className="sso-logo__icon" />
                  )}
                  {configured ? null : (
                    <span className="sso-logo__dot" data-testid={`${testIdPrefix}-${provider.code}-dot`} aria-hidden="true" />
                  )}
                </button>
              </Tooltip>
            </span>
          );
        })}
      </div>

      {/* t138：微信扫码模态（桌面端）；移动端不会打开 */}
      <WechatQrModal
        open={wechatOpen}
        onClose={() => setWechatOpen(false)}
        configured={wechatProvider ? isProviderConfigured(wechatProvider) : false}
        isAdmin={isAdmin}
      />
    </div>
  );
};

export default SsoButtons;
