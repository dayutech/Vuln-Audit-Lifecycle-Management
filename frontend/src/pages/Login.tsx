import React, { useMemo, useState } from 'react';
import { Alert, Button, Form, Input, message } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { login } from '@/api/auth';
import { useUserStore } from '@/store/userStore';
import { useT } from '@/i18n';
import LangSwitcher from '@/i18n/LangSwitcher';
import LoginCharacter from '@/components/login/LoginCharacter';
import { getLoginTheme } from '@/components/login/themes';
import { isLoginThemeId } from '@/components/login/types';
import { readLoginThemeCache, syncLoginThemeFromServer } from '@/utils/loginTheme';

/**
 * 登录页（t95 新版布局 / t99 抽象主题适配）。
 *
 * - 左右分栏：左侧品牌 + 动态角色，右侧表单；窄屏（≤768px）上下堆叠；
 * - **角色主题**（pencil/geometric/owl/pixel-eyes）：角色占左侧角色位，旁白在角色下方；
 * - **抽象主题**（aurora/mono-grid/orbit/big-type）：不做角色，主题铺满整页做背景层
 *   （`.login-abstract-layer`），左侧转为纯品牌区，表单卡片去投影，留白由背景构图承担；
 *   `big-type` 自带超大产品名，品牌区不再重复渲染标题；
 * - 登录逻辑与改造前**完全一致**（`api/auth.login` + store 写入 + 跳 /dashboard），
 *   只新增两处交互状态：密码焦点（角色遮眼 / 抽象主题降噪）与登录失败（抖动 / 1px 位移）；
 * - **主题来源（t97）**：`?theme=` 深链（预览/验证用，优先）> `localStorage('login.theme')` > 默认 pencil。
 *   认证前无法调 `/users/me/preferences`（401），所以登录页只读本机缓存；
 *   登录成功后再用响应里的 `loginTheme`（t96 已附带）回写缓存，**不整页刷新**（下次进登录页生效）。
 */
const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [passwordFocused, setPasswordFocused] = useState(false);
  const [hasError, setHasError] = useState(false);
  const navigate = useNavigate();
  const { setToken, setUser } = useUserStore();
  const { t } = useT();
  const [searchParams] = useSearchParams();
  /**
   * 主题解析：深链 `?theme=` 仅用于预览/验证（偏好弹窗的 iframe 预览也走它）；
   * 常规进入登录页读本机缓存 —— 认证前拿不到账号偏好，缓存就是唯一可用的来源。
   */
  const themeParam = searchParams.get('theme');
  const theme = useMemo(
    () => (isLoginThemeId(themeParam) ? themeParam : readLoginThemeCache()),
    [themeParam],
  );
  const themeEntry = useMemo(() => getLoginTheme(theme), [theme]);
  /** 抽象极简主题：不占角色位，铺满整页做背景层 */
  const isAbstractTheme = themeEntry.group === 'abstract';

  const onFinish = async (values: any) => {
    setLoading(true);
    setHasError(false);
    try {
      const res = await login(values);
      // The backend returns { success: true, data: { token, ... } }
      if (res && res.data && res.data.token) {
        message.success(t('login.success'));
        setToken(res.data.token);
        /**
         * t97：用登录响应里的账号偏好回写本机缓存（跨设备/换浏览器后这里会纠正缓存）。
         * 不比对、不刷新：当前页面已经渲染完，下次进登录页自然生效；
         * 也不在此处清掉缓存 —— 退出登录后还要用它渲染登录页主题。
         */
        syncLoginThemeFromServer(res.data.loginTheme);
        // Transform the flat response to user object format expected by store
        setUser({
          id: res.data.userId,
          username: res.data.username,
          full_name: res.data.fullName,
          email: res.data.email,
          role: 'admin', // Or whatever default role until fetched
        } as any);
        navigate('/dashboard');
      }
    } catch (error) {
      // 错误提示仍由 request 拦截器统一弹出（后端 message 优先）
      setHasError(true);
    } finally {
      setLoading(false);
    }
  };

  const hint = hasError
    ? t('login.hint.error')
    : passwordFocused
      ? t('login.hint.shielding')
      : t('login.hint.idle');

  return (
    <div
      className={isAbstractTheme ? 'login-page login-page--abstract' : 'login-page'}
      data-abstract-theme={isAbstractTheme ? theme : undefined}
    >
      {isAbstractTheme ? (
        // 抽象主题背景层：固定铺满视口，不接收指针事件，不参与读屏
        <div className="login-abstract-layer" aria-hidden="true">
          <LoginCharacter theme={theme} passwordFocused={passwordFocused} hasError={hasError} />
        </div>
      ) : null}

      <div className="login-shell">
        {/* 左：品牌 + 角色（抽象主题下只有品牌区，背景由主题层承担） */}
        <section className="login-hero">
          <div className="login-hero__brand">
            <span className="login-hero__mark" aria-hidden="true">
              <svg width="26" height="26" viewBox="0 0 32 32" focusable="false">
                <path
                  d="M16 2.5 27 6.6v9.1c0 6.3-4.3 11.6-11 13.8-6.7-2.2-11-7.5-11-13.8V6.6L16 2.5Z"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  strokeLinejoin="round"
                />
                <circle cx="14" cy="14.5" r="4" fill="none" stroke="currentColor" strokeWidth="1.8" />
                <path d="M17.2 17.9 21 21.7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </span>
            <div>
              {/* big-type 主题自带超大产品名，这里不再重复标题 */}
              {themeEntry.carriesWordmark ? null : <div className="login-hero__title">{t('login.brand.title')}</div>}
              <div className="login-hero__tagline">{t('login.brand.tagline')}</div>
            </div>
          </div>

          {isAbstractTheme ? null : (
            <div className="login-hero__character">
              <LoginCharacter theme={theme} passwordFocused={passwordFocused} hasError={hasError} />
            </div>
          )}

          {isAbstractTheme ? null : (
            <div className="login-hero__hint" data-testid="login-hint" role="status" aria-live="polite">
              {hint}
            </div>
          )}

          <ul className="login-hero__points">
            <li>{t('login.brand.point.assets')}</li>
            <li>{t('login.brand.point.workflow')}</li>
            <li>{t('login.brand.point.audit')}</li>
          </ul>
        </section>

        {/* 右：表单 */}
        <section className="login-form-panel">
          <div className="login-form-panel__lang">
            <LangSwitcher />
          </div>
          <div className="login-form-panel__inner">
            <h1 className="login-form-panel__title">{t('login.title')}</h1>
            <p className="login-form-panel__subtitle">{t('login.form.subtitle')}</p>

            {hasError ? (
              <Alert
                type="error"
                showIcon
                style={{ marginBottom: 16 }}
                message={t('login.hint.error')}
                data-testid="login-error-alert"
              />
            ) : null}

            <Form name="login" layout="vertical" initialValues={{ remember: true }} onFinish={onFinish} size="large">
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

              <Form.Item style={{ marginBottom: 0 }}>
                <Button type="primary" htmlType="submit" style={{ width: '100%' }} loading={loading}>
                  {t('login.submit')}
                </Button>
              </Form.Item>
            </Form>
          </div>
        </section>
      </div>

      <style>{`
        .login-page {
          min-height: 100vh;
          box-sizing: border-box;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: clamp(16px, 3vw, 40px);
          background: radial-gradient(1100px 620px at 12% 18%, #fff3e8 0%, #fbf7f2 42%, #f2f4f7 100%);
        }
        .login-shell {
          width: 100%;
          max-width: 1040px;
          display: grid;
          grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
          gap: clamp(20px, 4vw, 56px);
          align-items: center;
        }
        .login-hero {
          display: flex;
          flex-direction: column;
          gap: 18px;
          min-width: 0;
        }
        .login-hero__brand {
          display: flex;
          align-items: center;
          gap: 12px;
          color: #1f2328;
        }
        .login-hero__mark {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 44px;
          height: 44px;
          border-radius: 12px;
          background: #ff6b35;
          color: #fff;
          flex: 0 0 auto;
        }
        .login-hero__title {
          font-size: clamp(18px, 2.2vw, 22px);
          font-weight: 700;
          letter-spacing: 0.2px;
        }
        .login-hero__tagline {
          font-size: 13px;
          color: #6b7280;
          margin-top: 2px;
        }
        .login-hero__character {
          display: flex;
          justify-content: center;
          padding: 4px 0;
        }
        .login-hero__hint {
          text-align: center;
          font-size: 13px;
          color: #8c6a53;
          min-height: 20px;
        }
        .login-hero__points {
          list-style: none;
          margin: 0;
          padding: 0;
          display: flex;
          flex-direction: column;
          gap: 8px;
          font-size: 13px;
          color: #4b5563;
        }
        .login-hero__points li {
          position: relative;
          padding-left: 18px;
        }
        .login-hero__points li::before {
          content: '';
          position: absolute;
          left: 2px;
          top: 7px;
          width: 7px;
          height: 7px;
          border-radius: 50%;
          background: #ff6b35;
        }
        .login-form-panel {
          position: relative;
          background: #fff;
          border-radius: 18px;
          padding: clamp(22px, 3vw, 36px);
          box-shadow: 0 18px 50px rgba(31, 35, 40, 0.08);
          min-width: 0;
        }
        .login-form-panel__lang {
          position: absolute;
          top: 14px;
          right: 16px;
        }
        .login-form-panel__inner {
          max-width: 360px;
          margin: 0 auto;
        }
        .login-form-panel__title {
          font-size: 20px;
          font-weight: 700;
          margin: 8px 0 4px;
          color: #1f2328;
        }
        .login-form-panel__subtitle {
          margin: 0 0 20px;
          font-size: 13px;
          color: #6b7280;
        }
        /* 窄屏：上下堆叠，角色等比缩小，不压住表单 */
        @media (max-width: 768px) {
          .login-shell {
            grid-template-columns: minmax(0, 1fr);
            max-width: 460px;
            gap: 20px;
          }
          .login-hero {
            align-items: center;
            text-align: center;
            gap: 10px;
          }
          .login-hero__points {
            display: none;
          }
          .login-form-panel {
            box-shadow: 0 10px 30px rgba(31, 35, 40, 0.08);
          }
        }
        @media (prefers-reduced-motion: reduce) {
          .login-page {
            background: #f4f5f7;
          }
        }
      `}</style>
    </div>
  );
};

export default Login;
