import React, { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, message } from 'antd';
import { LockOutlined, SafetyOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { login, loginWithTwoFactor } from '@/api/auth';
import { parseLockInfo, parseRetryAfter, type LockInfo } from '@/api/authUnlock';
import { getApiErrorStatus } from '@/utils/errorMessage';
import SsoButtons from '@/components/sso/SsoButtons';
import { useUserStore } from '@/store/userStore';
import { useT } from '@/i18n';
import LangSwitcher from '@/i18n/LangSwitcher';
import LoginCharacter from '@/components/login/LoginCharacter';
import LoginBrand from '@/components/login/LoginBrand';
import { useLoginPageTheme } from '@/components/login/useLoginPageTheme';
import { toSkinStyle } from '@/components/login/skinVars';
import '@/components/login/loginStage.css';
import '@/components/auth/auth.css';

/**
 * 登录页（t95 主题 / t99 抽象主题 / t101 视觉重构 / t108 猫头鹰品牌）。
 *
 * 结构（三层，互不挤占）：
 * 1. **主题层** `.login-stage`（`position: fixed; inset: 0`）：主题铺满整个视口 ——
 *    角色向主题把角色放大成左下构图，抽象向主题本身就是全屏背景；
 * 2. **品牌区** `.login-brand`：固定左上角 = 猫头鹰徽标（`OwlMark`，深色舞台用 mono 版）
 *    + 产品名（≤19px，移动端换短名）+ slogan（≤13px，低对比度）；
 * 3. **登录框** `.login-main > .login-card`：视口内垂直居中偏上，宽度 `clamp(320px, 26vw, 420px)`，
 *    皮肤来自注册表 `formSkin`（每条主题一套，`--skin-*` 变量注入）。
 *
 * 页面上**没有**营销卖点、统计数字和鼠标提示文案：只有主题 + 品牌 + 登录框。
 *
 * 不变的部分：登录逻辑（`api/auth.login` → store → `/dashboard`）、密码焦点与失败态
 * （角色遮眼/抖动、抽象主题降噪/1px 位移）。
 *
 * 主题来源（t110 更正版）：`?theme=`（仅预览）> 本机标记 `localStorage('login.theme')`
 * > 管理员缺省 `GET /auth/login-theme` > `pencil`。登录成功与 `/auth/me` **不再**写本机标记。
 */
const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [passwordFocused, setPasswordFocused] = useState(false);
  const [hasError, setHasError] = useState(false);
  /** t127：429（IP 层限流）与 423（账户锁定）各走各的行内提示 */
  const [rateLimit, setRateLimit] = useState<{ seconds: number } | null>(null);
  const [lockInfo, setLockInfo] = useState<LockInfo | null>(null);
  /** t137：两步验证第二阶段 */
  const [challenge, setChallenge] = useState<string | null>(null);
  const [twoFaCode, setTwoFaCode] = useState('');
  const [useRecoveryCode, setUseRecoveryCode] = useState(false);
  const [twoFaError, setTwoFaError] = useState<string | null>(null);
  /** 回到第一步时的原因（挑战过期 / 尝试次数用尽）——必须渲染在两步之外才看得见 */
  const [twoFaNotice, setTwoFaNotice] = useState<string | null>(null);
  const [twoFaAttempts, setTwoFaAttempts] = useState(0);
  const [twoFaLoading, setTwoFaLoading] = useState(false);
  /** t137：记住我（30 天）——只作为请求体字段，前端不保存任何长效凭证 */
  const [rememberMe, setRememberMe] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  /** 解锁成功回跳时会带 `?username=xxx`：预填用户名，省得用户再敲一遍 */
  const presetUsername = (searchParams.get('username') ?? '').trim();
  /** t118：读登录框里已填的用户名，点「忘记密码」时带进 query（自动带入） */
  const [form] = Form.useForm<{ username?: string; password?: string }>();

  /** 跳到忘记密码页：用户名非空则预填并聚焦下一项，空则让用户自己填 */
  const goForgotPassword = () => {
    const username = String(form.getFieldValue('username') ?? '').trim();
    navigate(username ? `/forgot-password?username=${encodeURIComponent(username)}` : '/forgot-password');
  };
  const { setToken, setUser } = useUserStore();
  const { t } = useT();
  /**
   * 主题解析（t110，已抽到 `useLoginPageTheme` 供登录 / 注册 / 忘记密码共用）：
   * `?theme=` > 本机标记 > 管理员缺省 `GET /auth/login-theme` > `pencil`；
   * 公开端点失败静默降级，不阻塞页面。
   */
  const { theme, entry: themeEntry } = useLoginPageTheme();
  const skin = themeEntry.formSkin;

  /** 标签页标题：`<品牌全名> · 登录`，离开时还原（避免影响其它路由） */
  useEffect(() => {
    const previous = document.title;
    document.title = `${t('app.brand.name')} · ${t('login.title')}`;
    return () => {
      document.title = previous;
    };
  }, [t]);

  /** 皮肤 → CSS 变量（t101 抽出为 `toSkinStyle`，注册 / 忘记密码页共用） */
  const skinVars = toSkinStyle(skin);

  /** 登录成功（两阶段共用）：写 token/用户 → 跳仪表盘 */
  const finishLogin = (payload: any) => {
    message.success(t('login.success'));
    setToken(payload.token);
    /**
     * t110：登录成功**不再**回写本机标记（t97 的 `syncLoginThemeFromServer` 已删除）。
     * 主题只由「用户主动选择 / 从账号同步 / 跟随默认」驱动；
     * 跨设备想要一致就进偏好设置点一次「从账号同步到本机」。
     */
    // Transform the flat response to user object format expected by store
    setUser({
      id: payload.userId,
      username: payload.username,
      full_name: payload.fullName,
      email: payload.email,
      role: 'admin', // Or whatever default role until fetched
    } as any);
    navigate('/dashboard');
  };

  const onFinish = async (values: any) => {
    setLoading(true);
    setHasError(false);
    setRateLimit(null);
    setLockInfo(null);
    try {
      const res = await login({ ...values, rememberMe });
      const payload = res?.data ?? res;
      /**
       * t137：已开启两步验证的账号，第一步登录返回 **202** `{twoFactorRequired, challengeToken}`。
       * 这里切到第二阶段；`challengeToken` 只放在 state 里（**不写 localStorage**），
       * 页面刷新/关闭即失效（后端本身就是一次性 + 5 分钟）。
       */
      if (payload?.twoFactorRequired && payload?.challengeToken) {
        setChallenge(String(payload.challengeToken));
        setTwoFaCode('');
        setTwoFaAttempts(0);
        setTwoFaError(null);
        setTwoFaNotice(null);
        setUseRecoveryCode(false);
        return;
      }
      if (payload && payload.token) {
        finishLogin(payload);
      }
    } catch (error) {
      /**
       * t127：登录失败分三路（都不弹全局 toast，全部行内）——
       * - **429** IP 层限流：读 `Retry-After` → 「登录尝试过于频繁，请 X 分钟后重试」+ 倒计时禁用提交；
       * - **423** 账户锁定：展示锁定说明 + 「去解锁」按钮（**不显示「密码错误」**）；
       * - 其它（401/400/5xx）：沿用原有行内 Alert「用户名或密码错误」。
       */
      const status = getApiErrorStatus(error);
      if (status === 429) {
        const retryAfter = parseRetryAfter(
          (error as any)?.response?.headers?.['retry-after'] ?? (error as any)?.response?.headers?.['Retry-After'],
        );
        setRateLimit({ seconds: retryAfter ?? 60 });
      } else if (status === 423) {
        setLockInfo(parseLockInfo((error as any)?.response?.data));
      } else {
        setHasError(true);
      }
    } finally {
      setLoading(false);
    }
  };

  /** 回到第一步（挑战过期 / 尝试次数用尽 / 用户主动返回） */
  const resetToPasswordStep = (reason?: 'expired') => {
    setChallenge(null);
    setTwoFaCode('');
    setTwoFaAttempts(0);
    setTwoFaError(null);
    setTwoFaNotice(reason === 'expired' ? t('login.twoFactor.expired') : null);
  };

  /** 第二阶段提交（动态码或恢复码都走同一端点，后端两种都认） */
  const submitTwoFactor = async () => {
    const code = twoFaCode.trim();
    if (!challenge) return;
    if (!code) {
      setTwoFaError(t('login.twoFactor.codeRequired'));
      return;
    }
    setTwoFaLoading(true);
    setTwoFaError(null);
    try {
      const res = await loginWithTwoFactor({
        challengeToken: challenge,
        code,
        rememberMe,
      });
      const payload = res?.data ?? res;
      if (payload?.token) finishLogin(payload);
      else setTwoFaError(t('login.twoFactor.wrongCode'));
    } catch (error) {
      const status = getApiErrorStatus(error);
      const nextAttempts = twoFaAttempts + 1;
      setTwoFaAttempts(nextAttempts);
      // 挑战是一次性的：过期/被作废（401/403）或失败达到 5 次 → 一律回第一步重新登录
      if (status === 401 || status === 403 || nextAttempts >= 5) {
        resetToPasswordStep('expired');
      } else {
        setTwoFaError(t('login.twoFactor.wrongCode'));
      }
    } finally {
      setTwoFaLoading(false);
    }
  };

  /** 去解锁页：把用户名 / 脱敏 targets / 可用渠道一起带过去，避免解锁页再猜 */
  const goUnlock = () => {
    const username = String(form.getFieldValue('username') ?? '').trim();
    const params = new URLSearchParams();
    if (username) params.set('username', username);
    if (lockInfo) {
      if (lockInfo.targets.length > 0) params.set('targets', lockInfo.targets.join(','));
      params.set('methods', lockInfo.unlockMethods.join(','));
      if (lockInfo.noVerifiedContact) params.set('noContact', '1');
    }
    navigate(`/unlock${params.toString() ? `?${params.toString()}` : ''}`);
  };

  /** 429 倒计时：只用来渲染「X 分钟后重试」并在倒计时期间禁提交 */
  useEffect(() => {
    if (!rateLimit || rateLimit.seconds <= 0) return undefined;
    const timer = window.setInterval(() => {
      setRateLimit((prev) => (prev && prev.seconds > 1 ? { seconds: prev.seconds - 1 } : prev));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [rateLimit]);

  return (
    <div className="login-page" data-theme={theme} data-group={themeEntry.group} data-testid="login-page">
      {/* 1. 主题层：铺满整个视口，纯装饰（不接收指针、不参与读屏） */}
      <div className="login-stage" data-testid="login-stage" aria-hidden="true">
        <LoginCharacter theme={theme} passwordFocused={passwordFocused} hasError={hasError} />
      </div>

      {/* 2. 品牌区：左上角（t108 抽出为 LoginBrand，注册 / 忘记密码页共用） */}
      <LoginBrand entry={themeEntry} />

      {/* 3. 登录框：垂直居中偏上，皮肤随主题 */}
      <main className="login-main">
        <section
          className={['login-card', `login-card--${skin.inputVariant}`, 'login-form-panel'].join(' ')}
          style={skinVars}
          data-testid="login-card"
          data-skin={theme}
          data-input-variant={skin.inputVariant}
        >
          <h1 className="login-card__title">{t('login.title')}</h1>
          <p className="login-card__subtitle">{t('login.form.subtitle')}</p>

          {hasError ? (
            <Alert
              type="error"
              showIcon
              message={t('login.form.error')}
              data-testid="login-error-alert"
            />
          ) : null}

          {/* t127：IP 层限流（429）—— 行内倒计时，不弹全局 toast */}
          {rateLimit ? (
            <Alert
              type="warning"
              showIcon
              message={t('login.rateLimited', { minutes: Math.max(1, Math.ceil(rateLimit.seconds / 60)) })}
              description={t('login.rateLimitedDesc', { seconds: rateLimit.seconds })}
              data-testid="login-rate-limited"
              data-retry-after={rateLimit.seconds}
            />
          ) : null}

          {/* t127：账户锁定（423）—— 不显示「密码错误」，直接给出解锁入口 */}
          {lockInfo ? (
            <Alert
              type="error"
              showIcon
              icon={<SafetyOutlined />}
              message={t('login.accountLocked')}
              description={t('login.accountLockedDesc')}
              data-testid="login-account-locked"
              action={
                <Button size="small" type="primary" onClick={goUnlock} data-testid="login-go-unlock">
                  {t('login.goUnlock')}
                </Button>
              }
            />
          ) : null}

          {/* t137：挑战过期 / 尝试用尽后回到第一步时的原因提示（放在两步之外，回到第一步也看得见） */}
          {twoFaNotice && !challenge ? (
            <Alert type="warning" showIcon message={twoFaNotice} data-testid="login-2fa-notice" style={{ marginBottom: 12 }} />
          ) : null}

          {/* t137：第二阶段 —— 已开启两步验证的账号（第一步返回 202） */}
          {challenge ? (
            <div data-testid="login-two-factor">
              <h2 className="login-card__title" style={{ fontSize: 16, marginBottom: 4 }}>
                {t('login.twoFactor.title')}
              </h2>
              <p className="login-card__subtitle">{t('login.twoFactor.desc')}</p>

              {twoFaError ? (
                <Alert type="error" showIcon message={twoFaError} data-testid="login-2fa-error" style={{ marginBottom: 12 }} />
              ) : null}

              <div style={{ marginBottom: 8, fontSize: 12, color: '#6b7280' }}>
                {useRecoveryCode ? t('login.twoFactor.recoveryLabel') : t('login.twoFactor.codeLabel')}
              </div>
              <Input
                value={twoFaCode}
                onChange={(event) => {
                  const value = useRecoveryCode ? event.target.value : event.target.value.replace(/\D/g, '').slice(0, 6);
                  setTwoFaCode(value);
                  if (twoFaError) setTwoFaError(null);
                }}
                onPressEnter={() => void submitTwoFactor()}
                placeholder={useRecoveryCode ? t('login.twoFactor.recoveryPlaceholder') : t('login.twoFactor.codePlaceholder')}
                size="large"
                autoFocus
                style={{
                  letterSpacing: useRecoveryCode ? 0 : 4,
                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
                }}
                data-testid="login-2fa-code"
                data-mode={useRecoveryCode ? 'recovery' : 'totp'}
              />

              {twoFaAttempts > 0 && twoFaAttempts < 5 ? (
                <div style={{ fontSize: 12, color: '#d97706', marginTop: 6 }} data-testid="login-2fa-attempts">
                  {t('login.twoFactor.attemptsLeft', { count: 5 - twoFaAttempts })}
                </div>
              ) : null}

              <Button
                type="primary"
                size="large"
                block
                style={{ marginTop: 12 }}
                loading={twoFaLoading}
                onClick={() => void submitTwoFactor()}
                data-testid="login-2fa-submit"
              >
                {t('login.twoFactor.submit')}
              </Button>

              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 10, gap: 8, flexWrap: 'wrap' }}>
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0 }}
                  onClick={() => {
                    setUseRecoveryCode((prev) => !prev);
                    setTwoFaCode('');
                    setTwoFaError(null);
                  }}
                  data-testid="login-2fa-recovery-toggle"
                >
                  {useRecoveryCode ? t('login.twoFactor.useTotp') : t('login.twoFactor.useRecovery')}
                </Button>
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0 }}
                  onClick={() => resetToPasswordStep()}
                  data-testid="login-2fa-back"
                >
                  {t('login.twoFactor.back')}
                </Button>
              </div>
            </div>
          ) : (
          <Form
            form={form}
            name="login"
            layout="vertical"
            initialValues={{ remember: true, username: presetUsername }}
            onFinish={onFinish}
            size="large"
          >
            <Form.Item
              name="username"
              label={t('login.username')}
              rules={[{ required: true, message: t('login.usernameRequired') }]}
            >
              <Input prefix={<UserOutlined />} placeholder={t('login.username')} autoComplete="username" />
            </Form.Item>

            <Form.Item
              name="password"
              label={t('login.password')}
              rules={[{ required: true, message: t('login.passwordRequired') }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder={t('login.password')}
                autoComplete="current-password"
                onFocus={() => setPasswordFocused(true)}
                onBlur={() => setPasswordFocused(false)}
              />
            </Form.Item>

            {/* t137：记住我（30 天）——勾选后请求体带 rememberMe:true，长效凭证由后端 httpOnly cookie 承担 */}
            <Form.Item style={{ marginBottom: 12 }}>
              <Checkbox
                checked={rememberMe}
                onChange={(event) => setRememberMe(event.target.checked)}
                data-testid="login-remember-me"
              >
                {t('rememberMe.label')}
              </Checkbox>
              <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 2 }}>{t('rememberMe.hint')}</div>
            </Form.Item>

            <Form.Item style={{ marginBottom: 0 }}>
              <Button
                type="primary"
                htmlType="submit"
                style={{ width: '100%' }}
                loading={loading}
                disabled={Boolean(rateLimit && rateLimit.seconds > 0)}
                data-testid="login-submit"
              >
                {t('login.submit')}
              </Button>
            </Form.Item>
          </Form>
          )}

          {/* t105：登录框下方的自助入口（注册 / 忘记密码）+ 第三方登录（有无 provider 由后端决定） */}
          <div className="login-entries" data-testid="login-entries">
            <button
              type="button"
              className="login-entries__item"
              onClick={() => navigate('/register')}
              data-testid="login-entry-register"
            >
              {t('login.entry.register')}
            </button>
            <button
              type="button"
              className="login-entries__item"
              onClick={goForgotPassword}
              data-testid="login-entry-forgot"
            >
              {t('login.entry.forgotPassword')}
            </button>
          </div>

          {/* t106：第三方登录（无 provider 或接口失败时组件自身返回 null，不留空壳） */}
          <SsoButtons />
        </section>
      </main>

      {/* 4. 语言切换：右上角，不占登录框空间 */}
      <div className="login-lang">
        <LangSwitcher />
      </div>
    </div>
  );
};

export default Login;


