import React, { useMemo, useState } from 'react';
import { Alert, Button, Form, Input, Radio, message } from 'antd';
import { LockOutlined, MailOutlined, MobileOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
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
 * 忘记密码页（t105，后端 t103）——两步：
 * 1. 选渠道（手机号 / 邮箱）+ 填接收方 + 获取验证码（60s 倒计时、429 单独提示、devCode 自动填入）；
 * 2. 验证码 + 新密码 + 确认新密码 → `POST /auth/password-reset/confirm` → 提示并回登录页。
 *
 * 与后端一致的两点约定：
 * - 发码对**不存在的账号**同样返回 200（防枚举），前端照常进入第二步；
 * - 新密码强度与 `PasswordPolicy` 同口径（≥8 位 + 三类字符），不达标时提交按钮禁用。
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

const ForgotPassword: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { theme, entry } = useLoginPageTheme();
  const [form] = Form.useForm<ResetFormValues>();

  const [step, setStep] = useState<1 | 2>(1);
  const [channel, setChannel] = useState<VerificationChannel>('sms');
  const [target, setTarget] = useState('');
  const [targetError, setTargetError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<FormErrorState | null>(null);
  const [devCode, setDevCode] = useState<string>('');
  const [passwordFocused, setPasswordFocused] = useState(false);
  const cooldown = useCooldown(60);

  const newPassword = Form.useWatch('newPassword', form) ?? '';
  const confirmPassword = Form.useWatch('confirmPassword', form) ?? '';
  const strength = useMemo(() => evaluatePassword(newPassword), [newPassword]);
  const canSubmit = strength.valid && newPassword.length > 0 && newPassword === confirmPassword && !submitting;

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

  const checkTarget = (): string | null => {
    const value = target.trim();
    const error = channel === 'sms' ? validateTarget(value) : validateEmailTarget(value);
    setTargetError(error);
    return error ? null : value;
  };

  const sendCode = async (): Promise<boolean> => {
    const value = checkTarget();
    if (!value) return false;
    setSending(true);
    try {
      const result = await sendPasswordResetCode({ channel, target: value });
      cooldown.start(result.expiresInSeconds && result.expiresInSeconds < 60 ? result.expiresInSeconds : 60);
      if (result.devCode) {
        setDevCode(result.devCode);
        form.setFieldsValue({ code: result.devCode });
      }
      message.success(t('forgotPassword.codeSent'));
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
          <div className="auth-field">
            <div className="auth-field__label">{t('forgotPassword.channel')}</div>
            <div data-testid="forgot-channel">
              <Radio.Group
                value={channel}
                onChange={(event) => {
                  setChannel(event.target.value as VerificationChannel);
                  setTargetError(null);
                  cooldown.reset();
                }}
                optionType="button"
                buttonStyle="solid"
              >
                <Radio.Button value="sms" data-testid="forgot-channel-sms">
                  {t('forgotPassword.channelSms')}
                </Radio.Button>
                <Radio.Button value="email" data-testid="forgot-channel-email">
                  {t('forgotPassword.channelEmail')}
                </Radio.Button>
              </Radio.Group>
            </div>
          </div>

          <Form.Item
            label={channel === 'sms' ? t('forgotPassword.targetSms') : t('forgotPassword.targetEmail')}
            validateStatus={targetError ? 'error' : undefined}
            help={targetError ?? undefined}
            required
          >
            <Input
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

          <p className="login-card__subtitle" style={{ margin: 0, fontSize: 11 }}>
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
