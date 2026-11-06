import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Form, Input, message } from 'antd';
import { LockOutlined, MailOutlined, MobileOutlined, UserOutlined } from '@ant-design/icons';
import type { InputRef } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useT } from '@/i18n';
import AuthShell from '@/components/auth/AuthShell';
import CodeField from '@/components/auth/CodeField';
import PasswordStrength from '@/components/auth/PasswordStrength';
import { useCooldown } from '@/components/auth/useCooldown';
import { useLoginPageTheme } from '@/components/login/useLoginPageTheme';
import { confirmPasswordReset, sendPasswordResetCode } from '@/api/authSelfService';
import type { VerificationChannel } from '@/api/authSelfService';
import { describeAuthError } from '@/utils/authErrors';
import { evaluatePassword } from '@/utils/passwordPolicy';
import '@/components/auth/auth.css';

/**
 * 忘记密码页（t105 建立，t117/t118 改造）。
 *
 * 两步：
 * 1. **用户名**（t118 新增，必填，置顶）+ 渠道（手机 / 邮箱）+ 接收方 + 获取验证码；
 * 2. 验证码 + 新密码 + 确认新密码 → `POST /auth/password-reset/confirm` → 提示并回登录页。
 *
 * 与后端（t117）一致的三点约定：
 * - `send-code` / `confirm` **都必须带 `username`**（缺一 400）；
 * - 「用户名 ↔ 手机/邮箱」不匹配时后端**仍返回 200 但不发码**（防枚举）→
 *   前端**不得**据此给出「用户名或手机号不匹配」这类差异提示，统一展示
 *   「如果信息匹配，验证码已发送」，且照常进入第二步；
 * - 新密码强度与 `PasswordPolicy` 同口径（≥8 位 + 三类字符），不达标时提交按钮禁用。
 *
 * 自动带入（t118）：从登录页点「忘记密码」时会把登录框里已填的用户名拼进
 * `/forgot-password?username=xxx`；这里预填并**聚焦到下一项**（手机/邮箱输入框），
 * 没有用户名时保持空、聚焦用户名本身，由用户手动填写。
 *
 * 视觉：与登录 / 注册页共用 `AuthShell`（全屏主题 + 左上角品牌 + 主题皮肤）。
 */

interface ResetFormValues {
  code: string;
  newPassword: string;
  confirmPassword: string;
}

interface FormErrorState {
  message: string;
  detail?: string;
}

const PHONE_PATTERN = /^1[3-9]\d{9}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODE_PATTERN = /^\d{6}$/;
/** 用户名长度上限与登录表单保持一致（后端 users.username 上限 50） */
const USERNAME_MAX = 50;

const ForgotPassword: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { theme, entry } = useLoginPageTheme();
  const [form] = Form.useForm<ResetFormValues>();
  const usernameRef = useRef<InputRef>(null);
  const targetRef = useRef<InputRef>(null);

  /** 登录页带过来的用户名（trim 后为空则视为未带入） */
  const presetUsername = (searchParams.get('username') ?? '').trim().slice(0, USERNAME_MAX);

  const [step, setStep] = useState<1 | 2>(1);
  const [username, setUsername] = useState(presetUsername);
  const [usernameError, setUsernameError] = useState<string | null>(null);
  const [channel, setChannel] = useState<VerificationChannel>('sms');
  const [target, setTarget] = useState('');
  const [targetError, setTargetError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<FormErrorState | null>(null);
  const [devCode, setDevCode] = useState<string>('');
  const [passwordFocused, setPasswordFocused] = useState(false);
  const cooldown = useCooldown(60);

  /**
   * 首帧聚焦：带入用户名 → 直接聚焦「手机号/邮箱」（下一步该填的）；
   * 未带入 → 聚焦用户名。
   */
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (presetUsername) targetRef.current?.focus();
      else usernameRef.current?.focus();
    }, 120);
    return () => window.clearTimeout(timer);
  }, [presetUsername]);

  const newPassword = Form.useWatch('newPassword', form) ?? '';
  const confirmPassword = Form.useWatch('confirmPassword', form) ?? '';
  const strength = useMemo(() => evaluatePassword(newPassword), [newPassword]);
  const canSubmit = strength.valid && newPassword.length > 0 && newPassword === confirmPassword && !submitting;

  const validateUsername = (value: string): string | null => {
    if (!value.trim()) return t('forgotPassword.usernameRequired');
    return null;
  };

  const validateTarget = (value: string): string | null => {
    const trimmed = value.trim();
    if (!trimmed) return t('forgotPassword.targetRequired');
    if (!PHONE_PATTERN.test(trimmed)) return t('register.phoneInvalid');
    return null;
  };

  const validateEmailTarget = (value: string): string | null => {
    const trimmed = value.trim();
    if (!trimmed) return t('forgotPassword.targetRequired');
    if (!EMAIL_PATTERN.test(trimmed)) return t('register.emailInvalid');
    return null;
  };

  /** 两个因子一起校验：都通过才返回（username, target） */
  const checkFactors = (): { username: string; target: string } | null => {
    const nameError = validateUsername(username);
    setUsernameError(nameError);
    if (nameError) {
      usernameRef.current?.focus();
      return null;
    }
    const value = target.trim();
    const error = channel === 'sms' ? validateTarget(value) : validateEmailTarget(value);
    setTargetError(error);
    if (error) {
      targetRef.current?.focus();
      return null;
    }
    return { username: username.trim(), target: value };
  };

  const sendCode = async (): Promise<boolean> => {
    const factors = checkFactors();
    if (!factors) return false;
    setSending(true);
    try {
      const result = await sendPasswordResetCode({
        username: factors.username,
        channel,
        target: factors.target,
      });
      cooldown.start(result.expiresInSeconds && result.expiresInSeconds < 60 ? result.expiresInSeconds : 60);
      if (result.devCode) {
        setDevCode(result.devCode);
        form.setFieldsValue({ code: result.devCode });
      }
      // 统一提示：不区分「匹配 / 不匹配」（后端不匹配也是 200 但不发码，防枚举）
      message.success(t('forgotPassword.codeSentUnified'));
      setFormError(null);
      return true;
    } catch (error) {
      const info = describeAuthError(error, t);
      if (info.kind === 'rateLimit') {
        message.error(
          info.retryAfterSeconds
            ? `${t('auth.error.rateLimit')} ${t('auth.error.retryAfter', { seconds: info.retryAfterSeconds })}`
            : t('auth.error.rateLimit'),
        );
      } else {
        setFormError({ message: info.message, detail: info.detail });
      }
      return false;
    } finally {
      setSending(false);
    }
  };

  const goStep2 = async () => {
    const sent = await sendCode();
    if (sent) setStep(2);
  };

  const backToStep1 = () => {
    setStep(1);
    setFormError(null);
  };

  const onFinish = async (values: ResetFormValues) => {
    setSubmitting(true);
    setFormError(null);
    try {
      await confirmPasswordReset({
        username: username.trim(),
        target: target.trim(),
        code: (values.code ?? '').trim(),
        newPassword: values.newPassword,
      });
      message.success(t('forgotPassword.success'));
      navigate('/login');
    } catch (error) {
      const info = describeAuthError(error, t);
      if (info.fields) {
        const known: (keyof ResetFormValues)[] = ['code', 'newPassword', 'confirmPassword'];
        const fieldData = Object.entries(info.fields)
          .filter(([name]) => (known as string[]).includes(name))
          .map(([name, text]) => ({ name: name as keyof ResetFormValues, errors: [text] }));
        if (fieldData.length > 0) form.setFields(fieldData);
        setFormError({ message: info.message, detail: info.detail });
      } else if (info.kind === 'rateLimit') {
        setFormError({
          message: t('auth.error.rateLimit'),
          detail: info.retryAfterSeconds
            ? t('auth.error.retryAfter', { seconds: info.retryAfterSeconds })
            : undefined,
        });
      } else {
        setFormError({ message: info.message, detail: info.detail });
      }
    } finally {
      setSubmitting(false);
    }
  };

  const steps = (
    <div className="auth-steps" data-testid="forgot-steps" data-step={step}>
      <span className={['auth-steps__item', step === 1 ? 'is-active' : 'is-done'].join(' ')}>
        <span className="auth-steps__index">1</span>
        {t('forgotPassword.step1')}
      </span>
      <span className="auth-steps__sep" />
      <span className={['auth-steps__item', step === 2 ? 'is-active' : ''].join(' ')}>
        <span className="auth-steps__index">2</span>
        {t('forgotPassword.step2')}
      </span>
    </div>
  );

  return (
    <AuthShell
      theme={theme}
      entry={entry}
      title={t('forgotPassword.pageTitle')}
      subtitle={t('forgotPassword.subtitle')}
      pageLabel={t('forgotPassword.pageTitle')}
      passwordFocused={passwordFocused}
      hasError={Boolean(formError)}
      testId="forgot-page"
      footer={
        <>
          <span>{t('forgotPassword.haveAccount')}</span>
          <button type="button" className="auth-link" onClick={() => navigate('/login')} data-testid="forgot-back-login">
            {t('forgotPassword.backToLogin')}
          </button>
        </>
      }
    >
      {steps}

      {formError ? (
        <Alert
          type="error"
          showIcon
          className="auth-alert"
          message={formError.message}
          description={formError.detail && formError.detail !== formError.message ? formError.detail : undefined}
          data-testid="forgot-error"
        />
      ) : null}

      {step === 1 ? (
        <div data-testid="forgot-step1">
          {/* t118：用户名置顶（t117 契约双因子之一），从登录页带入时预填 */}
          <Form.Item
            label={t('forgotPassword.username')}
            validateStatus={usernameError ? 'error' : undefined}
            help={usernameError ?? undefined}
            required
            extra={
              presetUsername && username === presetUsername ? (
                <span data-testid="forgot-username-prefilled">{t('forgotPassword.usernamePrefilled')}</span>
              ) : undefined
            }
          >
            <Input
              ref={usernameRef}
              value={username}
              onChange={(event) => {
                setUsername(event.target.value.slice(0, USERNAME_MAX));
                if (usernameError) setUsernameError(null);
              }}
              placeholder={t('forgotPassword.usernamePlaceholder')}
              prefix={<UserOutlined />}
              autoComplete="username"
              maxLength={USERNAME_MAX}
              data-testid="forgot-username"
              data-prefilled={presetUsername ? 'true' : 'false'}
              onPressEnter={() => targetRef.current?.focus()}
            />
          </Form.Item>

          <div className="auth-field">
            <div className="auth-field__label">{t('forgotPassword.channel')}</div>
            {/* t122：自绘分段控件，颜色全部取自主题 formSkin（--skin-*），
                不再用 antd Radio.Button 的写死灰/蓝底，8 套主题自动协调 */}
            <div className="auth-segment" role="radiogroup" aria-label={t('forgotPassword.channel')} data-testid="forgot-channel">
              {(['sms', 'email'] as VerificationChannel[]).map((item) => {
                const active = channel === item;
                return (
                  <button
                    key={item}
                    type="button"
                    role="radio"
                    aria-checked={active}
                    className={['auth-segment__item', active ? 'is-active' : ''].join(' ')}
                    data-testid={`forgot-channel-${item}`}
                    data-active={active ? 'true' : 'false'}
                    onClick={() => {
                      setChannel(item);
                      setTargetError(null);
                      cooldown.reset();
                    }}
                  >
                    {item === 'sms' ? t('forgotPassword.channelSms') : t('forgotPassword.channelEmail')}
                  </button>
                );
              })}
            </div>
          </div>

          <Form.Item
            label={channel === 'sms' ? t('forgotPassword.targetSms') : t('forgotPassword.targetEmail')}
            validateStatus={targetError ? 'error' : undefined}
            help={targetError ?? undefined}
            required
          >
            <Input
              ref={targetRef}
              value={target}
              onChange={(event) => {
                setTarget(event.target.value);
                if (targetError) setTargetError(null);
              }}
              placeholder={
                channel === 'sms'
                  ? t('forgotPassword.targetSmsPlaceholder')
                  : t('forgotPassword.targetEmailPlaceholder')
              }
              prefix={channel === 'sms' ? <MobileOutlined /> : <MailOutlined />}
              autoComplete={channel === 'sms' ? 'tel' : 'email'}
              maxLength={channel === 'sms' ? 11 : 120}
              data-testid="forgot-target"
              onPressEnter={() => void goStep2()}
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 8 }}>
            <Button
              type="primary"
              block
              loading={sending}
              disabled={cooldown.active}
              onClick={() => void goStep2()}
              data-testid="forgot-send-code"
            >
              {cooldown.active
                ? t('forgotPassword.resendIn', { seconds: cooldown.remaining })
                : t('forgotPassword.sendCode')}
            </Button>
          </Form.Item>

          <p className="login-card__subtitle" style={{ margin: 0, fontSize: 11 }} data-testid="forgot-notice">
            {t('forgotPassword.notice')}
          </p>
        </div>
      ) : (
        <div data-testid="forgot-step2">
          <Form<ResetFormValues>
            form={form}
            name="password-reset"
            layout="vertical"
            size="large"
            requiredMark={false}
            onFinish={onFinish}
            autoComplete="off"
          >
            <Form.Item
              name="code"
              label={t('forgotPassword.code')}
              rules={[
                { required: true, message: t('register.codeRequired') },
                { pattern: CODE_PATTERN, message: t('register.codeRule') },
              ]}
            >
              <CodeField
                onSend={() => void sendCode()}
                cooldown={cooldown.remaining}
                sending={sending}
                placeholder={t('forgotPassword.code')}
                testId="forgot-code"
                sendTestId="forgot-resend-code"
              />
            </Form.Item>

            {devCode ? (
              <div className="auth-devcode" data-testid="forgot-devcode">
                <span>{t('auth.devCodeLabel')}</span>
                <span className="auth-devcode__code">{devCode}</span>
              </div>
            ) : null}

            <Form.Item
              name="newPassword"
              label={t('forgotPassword.newPassword')}
              rules={[{ required: true, message: t('register.passwordRequired') }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder={t('forgotPassword.newPassword')}
                autoComplete="new-password"
                data-testid="forgot-new-password"
                onFocus={() => setPasswordFocused(true)}
                onBlur={() => setPasswordFocused(false)}
              />
            </Form.Item>

            <PasswordStrength value={newPassword} />

            <Form.Item
              name="confirmPassword"
              label={t('forgotPassword.confirmPassword')}
              dependencies={['newPassword']}
              rules={[
                { required: true, message: t('register.confirmRequired') },
                ({ getFieldValue }) => ({
                  validator: (_, value) =>
                    !value || value === getFieldValue('newPassword')
                      ? Promise.resolve()
                      : Promise.reject(new Error(t('register.passwordMismatch'))),
                }),
              ]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder={t('forgotPassword.confirmPassword')}
                autoComplete="new-password"
                data-testid="forgot-confirm-password"
                onFocus={() => setPasswordFocused(true)}
                onBlur={() => setPasswordFocused(false)}
              />
            </Form.Item>

            <Form.Item style={{ marginBottom: 10 }}>
              <Button
                type="primary"
                htmlType="submit"
                block
                loading={submitting}
                disabled={!canSubmit}
                data-testid="forgot-submit"
              >
                {t('forgotPassword.submit')}
              </Button>
            </Form.Item>
          </Form>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
            <button type="button" className="auth-link" onClick={backToStep1} data-testid="forgot-back-step1">
              {t('forgotPassword.back')}
            </button>
            <button type="button" className="auth-link" onClick={() => navigate('/login')} data-testid="forgot-cancel">
              {t('forgotPassword.backToLogin')}
            </button>
          </div>
        </div>
      )}
    </AuthShell>
  );
};

export default ForgotPassword;

