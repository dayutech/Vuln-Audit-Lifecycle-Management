import React, { useEffect, useState } from 'react';
import { Alert, Button, Form, Input, message } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { login } from '@/api/auth';
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
  const navigate = useNavigate();
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
         * t110：登录成功**不再**回写本机标记（t97 的 `syncLoginThemeFromServer` 已删除）。
         * 主题只由「用户主动选择 / 从账号同步 / 跟随默认」驱动；
         * 跨设备想要一致就进偏好设置点一次「从账号同步到本机」。
         */
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
              onClick={() => navigate('/forgot-password')}
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
