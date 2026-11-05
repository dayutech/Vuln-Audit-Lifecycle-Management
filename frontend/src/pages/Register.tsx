import React, { useMemo, useState } from 'react';
import { Alert, Button, Form, Input, message } from 'antd';
import {
  CheckCircleFilled,
  LockOutlined,
  MailOutlined,
  MobileOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useT } from '@/i18n';
import AuthShell from '@/components/auth/AuthShell';
import CodeField from '@/components/auth/CodeField';
import PasswordStrength from '@/components/auth/PasswordStrength';
import { useCooldown } from '@/components/auth/useCooldown';
import { useLoginPageTheme } from '@/components/login/useLoginPageTheme';
import { registerAccount, sendRegisterCode } from '@/api/authSelfService';
import type { RegistrationResult } from '@/api/authSelfService';
import { describeAuthError } from '@/utils/authErrors';
import { evaluatePassword } from '@/utils/passwordPolicy';
import '@/components/auth/auth.css';

/**
 * 自助注册页（t105，后端 t102）。
 *
 * 流程：用户名 + 密码 + 确认密码 + **手机号（必填）** + 邮箱（可选）
 *   → 手机号/邮箱各自「获取验证码」（60s 倒计时；429 单独提示）
 *   → `POST /auth/register` 201 → **非跳转**的结果页「等待管理员审批」。
 *
 * 约定：
 * - 密码强度与后端 `PasswordPolicy` 同口径（≥8 位 + 三类字符），不达标时提交按钮禁用；
 * - dev-mode 下后端返回 `devCode` → 自动填入验证码并在表单下方小字提示（生产无该字段则不显示）；
 * - 错误按状态码分级：400 字段级（`data.data` 映射）/ 409 占用 / 429 限流 / 5xx 与网络；
 * - 视觉完全复用 t101/t108 的登录页外壳（全屏主题 + 左上角品牌 + 主题皮肤）。
 */

interface RegisterFormValues {
  username: string;
  password: string;
  confirmPassword: string;
  phone: string;
  code: string;
  email?: string;
  emailCode?: string;
}

interface FormErrorState {
  message: string;
  detail?: string;
}

const PHONE_PATTERN = /^1[3-9]\d{9}$/;
const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,50}$/;
const CODE_PATTERN = /^\d{6}$/;

const Register: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { theme, entry } = useLoginPageTheme();
  const [form] = Form.useForm<RegisterFormValues>();

  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState<RegistrationResult | null>(null);
  const [formError, setFormError] = useState<FormErrorState | null>(null);
  const [sending, setSending] = useState<'sms' | 'email' | null>(null);
  const [devCodes, setDevCodes] = useState<{ sms?: string; email?: string }>({});
  const [passwordFocused, setPasswordFocused] = useState(false);

  const smsCooldown = useCooldown(60);
  const emailCooldown = useCooldown(60);

  const password = Form.useWatch('password', form) ?? '';
  const confirmPassword = Form.useWatch('confirmPassword', form) ?? '';
  const email = (Form.useWatch('email', form) ?? '').trim();
  const strength = useMemo(() => evaluatePassword(password), [password]);
  const canSubmit = strength.valid && password.length > 0 && password === confirmPassword && !submitting;

  const sendSms = async () => {
    const phone = String(form.getFieldValue('phone') ?? '').trim();
    if (!PHONE_PATTERN.test(phone)) {
      form.setFields([{ name: 'phone', errors: [t('register.phoneInvalid')] }]);
      return;
    }
    setSending('sms');
    try {
      const result = await sendRegisterCode({ channel: 'sms', target: phone });
      smsCooldown.start(result.expiresInSeconds && result.expiresInSeconds < 60 ? result.expiresInSeconds : 60);
      if (result.devCode) {
        setDevCodes((prev) => ({ ...prev, sms: result.devCode }));
        form.setFieldsValue({ code: result.devCode });
      }
      form.setFields([{ name: 'phone', errors: [] }]);
      message.success(t('register.codeSent'));
    } catch (error) {
      const info = describeAuthError(error, t);
      if (info.kind === 'rateLimit') {
        message.error(t('auth.error.rateLimit'));
      } else if (info.kind === 'badRequest' || info.kind === 'field') {
        form.setFields([{ name: 'phone', errors: [info.message] }]);
      } else {
        message.error(info.message);
      }
    } finally {
      setSending(null);
    }
  };

  const sendEmail = async () => {
    const target = String(form.getFieldValue('email') ?? '').trim();
    if (!target) return;
    setSending('email');
    try {
      const result = await sendRegisterCode({ channel: 'email', target });
      emailCooldown.start(result.expiresInSeconds && result.expiresInSeconds < 60 ? result.expiresInSeconds : 60);
      if (result.devCode) {
        setDevCodes((prev) => ({ ...prev, email: result.devCode }));
        form.setFieldsValue({ emailCode: result.devCode });
      }
      form.setFields([{ name: 'email', errors: [] }]);
      message.success(t('register.emailCodeSent'));
    } catch (error) {
      const info = describeAuthError(error, t);
      if (info.kind === 'rateLimit') {
        message.error(t('auth.error.rateLimit'));
      } else if (info.kind === 'badRequest' || info.kind === 'field') {
        form.setFields([{ name: 'email', errors: [info.message] }]);
      } else {
        message.error(info.message);
      }
    } finally {
      setSending(null);
    }
  };

  const onFinish = async (values: RegisterFormValues) => {
    setSubmitting(true);
    setFormError(null);
    const emailValue = (values.email ?? '').trim();
    try {
      const result = await registerAccount({
        username: (values.username ?? '').trim(),
        password: values.password,
        phone: (values.phone ?? '').trim(),
        code: (values.code ?? '').trim(),
        email: emailValue || undefined,
        emailCode: emailValue ? (values.emailCode ?? '').trim() : undefined,
      });
      setSubmitted(result);
    } catch (error) {
      const info = describeAuthError(error, t);
      if (info.fields) {
        // 只回填本表单认识的字段（后端字段名与表单名一致），其余走顶部提示
        const known: (keyof RegisterFormValues)[] = [
          'username',
          'password',
          'confirmPassword',
          'phone',
          'code',
          'email',
          'emailCode',
        ];
        const fieldData = Object.entries(info.fields)
          .filter(([name]) => (known as string[]).includes(name))
          .map(([name, text]) => ({ name: name as keyof RegisterFormValues, errors: [text] }));
        if (fieldData.length > 0) form.setFields(fieldData);
        setFormError({ message: info.message, detail: info.detail });
      } else if (info.kind === 'conflict') {
        setFormError({ message: t('auth.error.conflict'), detail: info.detail });
      } else if (info.kind === 'rateLimit') {
        setFormError({ message: t('auth.error.rateLimit') });
      } else {
        setFormError({ message: info.message, detail: info.detail });
      }
    } finally {
      setSubmitting(false);
    }
  };

  /** 结果页（非跳转）：等待管理员审批 */
  const resultView = submitted ? (
    <div className="auth-result" data-testid="register-result">
      <CheckCircleFilled className="auth-result__icon" />
      <h2 className="auth-result__title" data-testid="register-result-title">
        {t('register.successTitle')}
      </h2>
      <p className="auth-result__desc">{t('register.successDesc')}</p>
      <div className="auth-result__meta">
        <div className="auth-result__meta-row">
          <span className="auth-result__meta-label">{t('register.resultUsername')}</span>
          <strong data-testid="register-result-username">{submitted.username ?? form.getFieldValue('username')}</strong>
        </div>
        <div className="auth-result__meta-row">
          <span className="auth-result__meta-label">{t('register.resultPhone')}</span>
          <strong>{submitted.phone ?? form.getFieldValue('phone')}</strong>
        </div>
        <div className="auth-result__meta-row">
          <span className="auth-result__meta-label">{t('register.resultStatus')}</span>
          <strong data-testid="register-result-status">{t('register.pendingStatus')}</strong>
        </div>
      </div>
      <div className="auth-result__actions">
        <Button type="primary" onClick={() => navigate('/login')} data-testid="register-back-login">
          {t('register.backToLogin')}
        </Button>
      </div>
    </div>
  ) : null;

  return (
    <AuthShell
      theme={theme}
      entry={entry}
      title={t('register.pageTitle')}
      subtitle={t('register.subtitle')}
      pageLabel={t('register.pageTitle')}
      passwordFocused={passwordFocused}
      hasError={Boolean(formError)}
      testId="register-page"
      footer={
        submitted ? null : (
          <>
            <span>{t('register.haveAccount')}</span>
            <button type="button" className="auth-link" onClick={() => navigate('/login')} data-testid="register-go-login">
              {t('register.goLogin')}
            </button>
          </>
        )
      }
    >
      {resultView ?? (
        <>
          {formError ? (
            <Alert
              type="error"
              showIcon
              className="auth-alert"
              message={formError.message}
              description={formError.detail && formError.detail !== formError.message ? formError.detail : undefined}
              data-testid="register-error"
            />
          ) : null}

          <Form<RegisterFormValues>
            form={form}
            name="register"
            layout="vertical"
            size="large"
            requiredMark={false}
            onFinish={onFinish}
            autoComplete="off"
          >
            <Form.Item
              name="username"
              label={t('register.username')}
              rules={[
                { required: true, message: t('register.usernameRequired') },
                { pattern: USERNAME_PATTERN, message: t('register.usernameRule') },
              ]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder={t('register.usernamePlaceholder')}
                autoComplete="username"
                data-testid="register-username"
              />
            </Form.Item>

            <Form.Item
              name="password"
              label={t('register.password')}
              rules={[{ required: true, message: t('register.passwordRequired') }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder={t('register.password')}
                autoComplete="new-password"
                data-testid="register-password"
                onFocus={() => setPasswordFocused(true)}
                onBlur={() => setPasswordFocused(false)}
              />
            </Form.Item>

            <PasswordStrength value={password} />

            <Form.Item
              name="confirmPassword"
              label={t('register.confirmPassword')}
              dependencies={['password']}
              rules={[
                { required: true, message: t('register.confirmRequired') },
                ({ getFieldValue }) => ({
                  validator: (_, value) =>
                    !value || value === getFieldValue('password')
                      ? Promise.resolve()
                      : Promise.reject(new Error(t('register.passwordMismatch'))),
                }),
              ]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder={t('register.confirmPassword')}
                autoComplete="new-password"
                data-testid="register-confirm-password"
                onFocus={() => setPasswordFocused(true)}
                onBlur={() => setPasswordFocused(false)}
              />
            </Form.Item>

            <Form.Item
              name="phone"
              label={t('register.phone')}
              rules={[
                { required: true, message: t('register.phoneRequired') },
                { pattern: PHONE_PATTERN, message: t('register.phoneInvalid') },
              ]}
            >
              <Input
                prefix={<MobileOutlined />}
                placeholder={t('register.phonePlaceholder')}
                autoComplete="tel"
                maxLength={11}
                data-testid="register-phone"
              />
            </Form.Item>

            <Form.Item
              name="code"
              label={t('register.smsCode')}
              rules={[
                { required: true, message: t('register.codeRequired') },
                { pattern: CODE_PATTERN, message: t('register.codeRule') },
              ]}
            >
              <CodeField
                onSend={sendSms}
                cooldown={smsCooldown.remaining}
                sending={sending === 'sms'}
                placeholder={t('register.smsCode')}
                testId="register-code"
                sendTestId="register-send-sms"
              />
            </Form.Item>

            {devCodes.sms ? (
              <div className="auth-devcode" data-testid="register-devcode">
                <span>{t('auth.devCodeLabel')}</span>
                <span className="auth-devcode__code">{devCodes.sms}</span>
              </div>
            ) : null}

            <Form.Item name="email" label={t('register.email')} rules={[{ type: 'email', message: t('register.emailInvalid') }]}>
              <Input
                prefix={<MailOutlined />}
                placeholder={t('register.emailPlaceholder')}
                autoComplete="email"
                data-testid="register-email"
              />
            </Form.Item>

            {email ? (
              <Form.Item
                name="emailCode"
                label={t('register.emailCode')}
                rules={[
                  { required: true, message: t('register.codeRequired') },
                  { pattern: CODE_PATTERN, message: t('register.codeRule') },
                ]}
              >
                <CodeField
                  onSend={sendEmail}
                  cooldown={emailCooldown.remaining}
                  sending={sending === 'email'}
                  placeholder={t('register.emailCode')}
                  testId="register-email-code"
                  sendTestId="register-send-email"
                />
              </Form.Item>
            ) : null}

            {devCodes.email ? (
              <div className="auth-devcode" data-testid="register-email-devcode">
                <span>{t('auth.devCodeLabel')}</span>
                <span className="auth-devcode__code">{devCodes.email}</span>
              </div>
            ) : null}

            <Form.Item style={{ marginBottom: 8 }}>
              <Button
                type="primary"
                htmlType="submit"
                block
                loading={submitting}
                disabled={!canSubmit}
                data-testid="register-submit"
              >
                {t('register.submit')}
              </Button>
            </Form.Item>

            <p className="login-card__subtitle" style={{ margin: 0, fontSize: 11 }}>
              {t('register.terms')}
            </p>
          </Form>
        </>
      )}
    </AuthShell>
  );
};

export default Register;
