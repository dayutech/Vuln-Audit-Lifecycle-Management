import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button } from 'antd';
import { CheckCircleFilled, CloseCircleFilled, ClockCircleOutlined, LoadingOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useT } from '@/i18n';
import AuthShell from '@/components/auth/AuthShell';
import { useLoginPageTheme } from '@/components/login/useLoginPageTheme';
import { useUserStore } from '@/store/userStore';
import { clearSsoPending, postSsoCallback, readSsoPending } from '@/api/sso';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import '@/components/sso/sso.css';

/**
 * 第三方登录回调页（t106）——路由 `/auth/sso/callback`（公开）。
 *
 * 后端 t104 的三种结果：
 * - **200 + status=LOGIN**：已绑定且账号可用 → 保存 token → 进 `/dashboard`；
 * - **200 + status=BOUND**：刚完成「绑定」（在账号设置里发起的流程）→ 提示成功 → 回账号设置；
 * - **202 + status=PENDING_APPROVAL**：陌生身份，已建关联申请 → **只展示指引，不保存 token、不提供重试**
 *   （避免用户连点重复提交）；
 * - **403 / 400 / 503**：分级提示（账号不可用 / 授权失效 / 未完成配置）。
 *
 * provider 从哪里来：发起时写在 `sessionStorage('sso.pending')`（登录 / 绑定两种模式），
 * 因此 `redirect_uri` 对四家 provider 都是同一个固定地址，不需要各注册一条带参数的 URI。
 */
type CallbackState =
  | { kind: 'working' }
  | { kind: 'bound' }
  | { kind: 'pending' }
  | { kind: 'error'; reason: 'missingParams' | 'invalid' | 'unavailable' | 'notConfigured' | 'failed'; detail?: string };

const SsoCallback: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { theme, entry } = useLoginPageTheme();
  const { setToken, setUser } = useUserStore();
  const [state, setState] = useState<CallbackState>({ kind: 'working' });
  const started = useRef(false);

  const params = useMemo(
    () => ({
      code: searchParams.get('code') ?? '',
      state: searchParams.get('state') ?? '',
      provider: searchParams.get('provider') ?? '',
      providerError: searchParams.get('error') ?? '',
    }),
    [searchParams],
  );

  useEffect(() => {
    // 防止重复回调（同一 code/state 只能消费一次）
    if (started.current) return;
    started.current = true;

    const pending = readSsoPending();
    const provider = params.provider || pending?.provider || '';

    if (params.providerError) {
      // 用户在第三方页面取消授权：按「不可用」提示，不重试
      clearSsoPending();
      // 用 0ms 定时器把 setState 推到微任务之后（react-hooks/set-state-in-effect）
      window.setTimeout(() => setState({ kind: 'error', reason: 'unavailable' }), 0);
      return;
    }
    if (!provider || !params.code || !params.state) {
      clearSsoPending();
      window.setTimeout(() => setState({ kind: 'error', reason: 'missingParams' }), 0);
      return;
    }

    let cancelled = false;
    postSsoCallback(provider, params.code, params.state)
      .then((result) => {
        if (cancelled) return;
        clearSsoPending();
        if (result.status === 'LOGIN' && result.login?.token) {
          setToken(result.login.token);
          setUser({
            id: result.login.userId,
            username: result.login.username,
            full_name: result.login.fullName,
            email: result.login.email,
          } as any);
          navigate('/dashboard', { replace: true });
          return;
        }
        if (result.status === 'BOUND') {
          setState({ kind: 'bound' });
          return;
        }
        // 202：陌生身份已提交审批（不保存 token、不提供重试）
        setState({ kind: 'pending' });
      })
      .catch((error) => {
        if (cancelled) return;
        clearSsoPending();
        const status = getApiErrorStatus(error);
        const detail = getApiErrorMessage(error, '');
        if (status === 503) setState({ kind: 'error', reason: 'notConfigured', detail });
        else if (status === 403) setState({ kind: 'error', reason: 'unavailable', detail });
        else if (status === 400) setState({ kind: 'error', reason: 'invalid', detail });
        else setState({ kind: 'error', reason: 'failed', detail });
      });

    return () => {
      cancelled = true;
    };
  }, [navigate, params, setToken, setUser]);

  const body = (() => {
    if (state.kind === 'working') {
      return (
        <div className="sso-result" data-testid="sso-callback-working">
          <LoadingOutlined className="sso-result__icon" />
          <p className="sso-result__desc">{t('sso.callback.working')}</p>
        </div>
      );
    }
    if (state.kind === 'pending') {
      return (
        <div className="sso-result" data-testid="sso-callback-pending">
          <ClockCircleOutlined className="sso-result__icon sso-result__icon--pending" />
          <h2 className="sso-result__title" data-testid="sso-callback-title">
            {t('sso.callback.pendingTitle')}
          </h2>
          <p className="sso-result__desc">{t('sso.callback.pendingDesc')}</p>
          <p className="sso-result__desc" data-testid="sso-callback-contact-admin">
            {t('sso.callback.contactAdmin')}
          </p>
          <div className="sso-result__actions">
            <Button type="primary" onClick={() => navigate('/login', { replace: true })} data-testid="sso-callback-back">
              {t('sso.callback.backToLogin')}
            </Button>
          </div>
        </div>
      );
    }
    if (state.kind === 'bound') {
      return (
        <div className="sso-result" data-testid="sso-callback-bound">
          <CheckCircleFilled className="sso-result__icon sso-result__icon--ok" />
          <h2 className="sso-result__title" data-testid="sso-callback-title">
            {t('sso.callback.boundTitle')}
          </h2>
          <p className="sso-result__desc">{t('sso.callback.boundDesc')}</p>
          <div className="sso-result__actions">
            <Button
              type="primary"
              onClick={() => navigate('/account/sso-bindings', { replace: true })}
              data-testid="sso-callback-to-bindings"
            >
              {t('sso.callback.backToBindings')}
            </Button>
          </div>
        </div>
      );
    }
    return (
      <div className="sso-result" data-testid="sso-callback-error" data-reason={state.reason}>
        <CloseCircleFilled className="sso-result__icon sso-result__icon--error" />
        <h2 className="sso-result__title" data-testid="sso-callback-title">
          {t('sso.callback.errorTitle')}
        </h2>
        <p className="sso-result__desc" data-testid="sso-callback-desc">
          {t(`sso.callback.${state.reason}`)}
        </p>
        {state.detail ? <div className="sso-result__detail">{state.detail}</div> : null}
        <div className="sso-result__actions">
          <Button type="primary" onClick={() => navigate('/login', { replace: true })} data-testid="sso-callback-back">
            {t('sso.callback.backToLogin')}
          </Button>
        </div>
      </div>
    );
  })();

  return (
    <AuthShell
      theme={theme}
      entry={entry}
      title={t('sso.callback.pageTitle')}
      pageLabel={t('sso.callback.pageTitle')}
      testId="sso-callback-page"
    >
      {body}
    </AuthShell>
  );
};

export default SsoCallback;

