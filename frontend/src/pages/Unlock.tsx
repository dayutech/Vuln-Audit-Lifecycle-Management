import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, message } from 'antd';
import { LockOutlined, MailOutlined, MobileOutlined, SafetyOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useT } from '@/i18n';
import AuthShell from '@/components/auth/AuthShell';
import { useCooldown } from '@/components/auth/useCooldown';
import { useLoginPageTheme } from '@/components/login/useLoginPageTheme';
import { NO_VERIFIED_CONTACT, confirmUnlock, parseRetryAfter, sendUnlockCode } from '@/api/authUnlock';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import '@/components/auth/auth.css';

/**
 * 账户锁定后的 OTP 解锁页（t127，后端 t123）。
 *
 * 入口：登录返回 **423** → 登录页「去解锁」→ `/unlock?username=…&targets=…&methods=…[&noContact=1]`
 *
 * 行为：
 * - 用户名：从 query 带入后**只读展示**（附「换个账号」回登录页）；没有 query 时要求手动填写；
 * - 渠道：**只展示后端给的 `unlockMethods`**（`sms` / `email`），对应脱敏 target 如 `138****0001`；
 * - 获取验证码：60s 倒计时；429 → 行内「发送过于频繁」；
 *   `hint=NO_VERIFIED_CONTACT`（或 query 带 `noContact=1`）→ 隐藏发送按钮 + 提示联系管理员；
 * - 解锁成功：展示「已解锁，请重新登录」→ 自动跳 `/login?username=…`（登录页预填用户名）；
 * - 失败分级：400 定位到验证码字段、429 行内限流、其它走 `getApiErrorMessage`。
 *
 * 视觉：与注册 / 忘记密码共用 `AuthShell`（8 套主题全屏舞台 + formSkin）。
 */

type Channel = 'sms' | 'email';

interface FormErrorState {
  message: string;
  detail?: string;
}

const CODE_PATTERN = /^\d{6}$/;
const USERNAME_MAX = 50;

const Unlock: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { theme, entry } = useLoginPageTheme();
  const [form] = Form.useForm<{ code: string }>();

  const queryUsername = (searchParams.get('username') ?? '').trim().slice(0, USERNAME_MAX);
  const queryTargets = (searchParams.get('targets') ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  const queryMethods = (searchParams.get('methods') ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter((item): item is Channel => item === 'sms' || item === 'email');
  const queryNoContact = searchParams.get('noContact') === '1';

  const [username, setUsername] = useState(queryUsername);
  const [usernameError, setUsernameError] = useState<string | null>(null);
  const [methods, setMethods] = useState<Channel[]>(queryMethods.length > 0 ? queryMethods : ['sms', 'email']);
  const [targets, setTargets] = useState<string[]>(queryTargets);
  const [channel, setChannel] = useState<Channel>(queryMethods[0] ?? 'sms');
  const [noContact, setNoContact] = useState(queryNoContact);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [devCode, setDevCode] = useState('');
  const [formError, setFormError] = useState<FormErrorState | null>(null);
  const [rateLimit, setRateLimit] = useState<{ seconds: number } | null>(null);
  const [unlocked, setUnlocked] = useState(false);
  const cooldown = useCooldown(60);

  /** 当前渠道对应的脱敏 target：后端按 unlockMethods / targets 同序给出，长度不足时退回第一个 */
  const currentTarget = useMemo(() => {
    const index = methods.indexOf(channel);
    if (index >= 0 && targets[index]) return targets[index];
    return targets[0] ?? '';
  }, [channel, methods, targets]);

  /** 解锁成功后自动回登录页（带用户名，登录页会预填） */
  useEffect(() => {
    if (!unlocked) return undefined;
    const timer = window.setTimeout(() => {
      navigate(`/login${username ? `?username=${encodeURIComponent(username)}` : ''}`, { replace: true });
    }, 1400);
    return () => window.clearTimeout(timer);
  }, [unlocked, username, navigate]);

  /** 429 倒计时（渲染用，顺带在倒计时期间禁用发送） */
  useEffect(() => {
    if (!rateLimit || rateLimit.seconds <= 0) return undefined;
    const timer = window.setInterval(() => {
      setRateLimit((prev) => (prev && prev.seconds > 1 ? { seconds: prev.seconds - 1 } : prev));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [rateLimit]);

  const resolveUsername = (): string | null => {
    const value = username.trim();
    if (!value) {
      setUsernameError(t('unlock.usernameRequired'));
      return null;
    }
    setUsernameError(null);
    return value;
  };

  const sendCode = async () => {
    const name = resolveUsername();
    if (!name) return;
    setSending(true);
    setFormError(null);
    setRateLimit(null);
    try {
      const result = await sendUnlockCode(name);
      if (result.hint === NO_VERIFIED_CONTACT) {
        setNoContact(true);
        return;
      }
      if (result.unlockMethods && result.unlockMethods.length > 0) {
        const next = result.unlockMethods.filter((m): m is Channel => m === 'sms' || m === 'email');
        if (next.length > 0) {
          setMethods(next);
          if (!next.includes(channel)) setChannel(next[0]);
        }
      }
      if (result.targets && result.targets.length > 0) setTargets(result.targets);
      if (result.devCode) {
        setDevCode(result.devCode);
        form.setFieldsValue({ code: result.devCode });
      }
      cooldown.start(result.expiresInSeconds && result.expiresInSeconds < 60 ? result.expiresInSeconds : 60);
      setSent(true);
      message.success(t('unlock.codeSent'));
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 429) {
        const retryAfter = parseRetryAfter(
          (error as any)?.response?.headers?.['retry-after'] ?? (error as any)?.response?.headers?.['Retry-After'],
        );
        setRateLimit({ seconds: retryAfter ?? 60 });
      } else {
        setFormError({ message: getApiErrorMessage(error, t('unlock.sendFailed')) });
      }
    } finally {
      setSending(false);
    }
  };

  const onFinish = async (values: { code: string }) => {
    const name = resolveUsername();
    if (!name) return;
    setSubmitting(true);
    setFormError(null);
    try {
      await confirmUnlock({ username: name, target: currentTarget, code: (values.code ?? '').trim() });
      setUnlocked(true);
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 429) {
        const retryAfter = parseRetryAfter(
          (error as any)?.response?.headers?.['retry-after'] ?? (error as any)?.response?.headers?.['Retry-After'],
        );
        setRateLimit({ seconds: retryAfter ?? 60 });
        return;
      }
      const payload = (error as any)?.response?.data;
      if (payload?.data?.hint === NO_VERIFIED_CONTACT) {
        setNoContact(true);
        return;
      }
      if (status === 400) {
        const fieldMap = payload?.data && typeof payload.data === 'object' ? payload.data : null;
        const codeMessage = fieldMap?.code ? String(fieldMap.code) : t('unlock.codeInvalid');
        form.setFields([{ name: 'code', errors: [codeMessage] }]);
        setFormError({ message: codeMessage });
        return;
      }
      setFormError({ message: getApiErrorMessage(error, t('unlock.failed')) });
    } finally {
      setSubmitting(false);
    }
  };

  const footer = (
    <>
      <span>{t('unlock.remembered')}</span>
      <button type="button" className="auth-link" onClick={() => navigate('/login')} data-testid="unlock-back-login">
        {t('unlock.backToLogin')}
      </button>
    </>
  );

  return (
    <AuthShell
      theme={theme}
      entry={entry}
      title={t('unlock.pageTitle')}
      subtitle={t('unlock.subtitle')}
      pageLabel={t('unlock.pageTitle')}
      hasError={Boolean(formError) || noContact}
      testId="unlock-page"
      footer={footer}
    >
      {unlocked ? (
        <div className="sso-result" data-testid="unlock-success">
          <SafetyOutlined className="sso-result__icon sso-result__icon--ok" />
          <h2 className="sso-result__title">{t('unlock.successTitle')}</h2>
          <p className="sso-result__desc">{t('unlock.successDesc')}</p>
          <div className="sso-result__actions">
            <Button
              type="primary"
              onClick={() => navigate(`/login${username ? `?username=${encodeURIComponent(username)}` : ''}`)}
              data-testid="unlock-success-go-login"
            >
              {t('unlock.backToLogin')}
            </Button>
          </div>
        </div>
      ) : (
        <>
          {/* 顶部说明：因多次异地失败被临时锁定 */}
          <Alert type="info" showIcon message={t('unlock.notice')} style={{ marginBottom: 12 }} data-testid="unlock-notice" />

          {noContact ? (
            <Alert
              type="warning"
              showIcon
              message={t('unlock.noContact')}
              description={t('unlock.noContactDesc')}
              style={{ marginBottom: 12 }}
              data-testid="unlock-no-contact"
            />
          ) : null}

          {formError ? (
            <Alert
              type="error"
              showIcon
              className="auth-alert"
              message={formError.message}
              description={formError.detail}
              data-testid="unlock-error"
            />
          ) : null}

          {rateLimit ? (
            <Alert
              type="warning"
              showIcon
              message={t('unlock.rateLimited', { seconds: rateLimit.seconds })}
              style={{ marginBottom: 12 }}
              data-testid="unlock-rate-limited"
            />
          ) : null}

          {/* 用户名：query 带入时只读；没带入则要求填写 */}
          {queryUsername ? (
            <div className="auth-field">
              <div className="auth-field__label">{t('unlock.username')}</div>
              <div className="unlock-username-row" data-testid="unlock-username" data-readonly="true">
                <UserOutlined />
                <span className="unlock-username-row__value">{queryUsername}</span>
              </div>
              <button
                type="button"
                className="auth-link"
                onClick={() => navigate('/login')}
                data-testid="unlock-switch-account"
                style={{ marginTop: 6 }}
              >
                {t('unlock.switchAccount')}
              </button>
            </div>
          ) : (
            <Form.Item
              label={t('unlock.username')}
              validateStatus={usernameError ? 'error' : undefined}
              help={usernameError ?? undefined}
              required
            >
              <Input
                value={username}
                onChange={(event) => {
                  setUsername(event.target.value.slice(0, USERNAME_MAX));
                  if (usernameError) setUsernameError(null);
                }}
                placeholder={t('unlock.usernamePlaceholder')}
                prefix={<UserOutlined />}
                autoComplete="username"
                maxLength={USERNAME_MAX}
                data-testid="unlock-username"
              />
            </Form.Item>
          )}

          {/* 渠道：只展示后端给的 unlockMethods */}
          <div className="auth-field">
            <div className="auth-field__label">{t('unlock.channel')}</div>
            <div className="auth-segment" role="radiogroup" aria-label={t('unlock.channel')}>
              {methods.map((item) => {
                const active = channel === item;
                return (
                  <button
                    key={item}
                    type="button"
                    role="radio"
                    aria-checked={active}
                    className={['auth-segment__item', active ? 'is-active' : ''].join(' ')}
                    data-testid={`unlock-channel-${item}`}
                    data-active={active ? 'true' : 'false'}
                    onClick={() => setChannel(item)}
                  >
                    {item === 'sms' ? t('forgotPassword.channelSms') : t('forgotPassword.channelEmail')}
                  </button>
                );
              })}
            </div>
          </div>

          <Form.Item label={t('unlock.target')} required>
            <Input
              value={currentTarget || t('unlock.targetUnknown')}
              prefix={channel === 'sms' ? <MobileOutlined /> : <MailOutlined />}
              readOnly
              disabled
              data-testid="unlock-target"
            />
          </Form.Item>

          {!noContact ? (
            <Form.Item style={{ marginBottom: 10 }}>
              <Button
                block
                onClick={() => void sendCode()}
                loading={sending}
                disabled={cooldown.active || Boolean(rateLimit && rateLimit.seconds > 0)}
                data-testid="unlock-send-code"
                data-cooling={cooldown.active ? 'true' : 'false'}
              >
                {cooldown.active ? t('unlock.resendIn', { seconds: cooldown.remaining }) : t('unlock.sendCode')}
              </Button>
            </Form.Item>
          ) : null}

          {sent ? <div className="auth-devcode" data-testid="unlock-sent">{t('unlock.codeSent')}</div> : null}

          {devCode ? (
            <div className="auth-devcode" data-testid="unlock-devcode">
              <span>{t('auth.devCodeLabel')}</span>
              <span className="auth-devcode__code">{devCode}</span>
            </div>
          ) : null}

          <Form<{ code: string }>
            form={form}
            name="unlock"
            layout="vertical"
            size="large"
            requiredMark={false}
            onFinish={onFinish}
            autoComplete="off"
          >
            <Form.Item
              name="code"
              label={t('unlock.code')}
              rules={[
                { required: true, message: t('register.codeRequired') },
                { pattern: CODE_PATTERN, message: t('register.codeRule') },
              ]}
            >
              <Input
                prefix={<LockOutlined />}
                placeholder={t('unlock.code')}
                maxLength={6}
                inputMode="numeric"
                autoComplete="one-time-code"
                data-testid="unlock-code"
              />
            </Form.Item>

            <Form.Item style={{ marginBottom: 8 }}>
              <Button
                type="primary"
                htmlType="submit"
                block
                loading={submitting}
                disabled={noContact}
                data-testid="unlock-submit"
              >
                {t('unlock.submit')}
              </Button>
            </Form.Item>
          </Form>
        </>
      )}
    </AuthShell>
  );
};

export default Unlock;
