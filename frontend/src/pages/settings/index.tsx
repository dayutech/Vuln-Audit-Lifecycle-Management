import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Result, Space, Spin, Switch, Tag, message } from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import { LOGIN_THEMES, getLoginTheme } from '@/components/login/themes';
import type { LoginThemeId } from '@/components/login/types';
import LoginThemeThumbnail from '@/components/preferences/LoginThemeThumbnail';
import { getMyAccessProfile } from '@/api/permission';
import {
  adminListSsoProviders,
  adminToggleSsoProvider,
  type AdminSsoProvider,
} from '@/api/sso';
import SsoIcon from '@/components/sso/SsoIcon';
import SsoProviderConfigModal from '@/components/sso/SsoProviderConfigModal';
import { knownSsoLabelKey } from '@/components/sso/providers';
import { getApiErrorMessage } from '@/utils/errorMessage';
import '@/components/sso/sso.css';
import {
  describeAdminSettingFailure,
  getAdminLoginThemeSetting,
  updateAdminLoginThemeSetting,
  type AdminLoginThemeSetting,
  type AdminSettingFailure,
} from '@/api/systemSetting';
import { useT } from '@/i18n';

/**
 * 系统设置（t110）：登录页默认主题。
 *
 * - **仅管理员**：菜单入口按画像隐藏；直接访问 /settings 时画像非 admin → 403 视图
 *   （后端 `GET/PUT /admin/settings/login-theme` 同样只放行 ADMIN，前端只是入口收敛）。
 * - **主题列表来自注册表** `LOGIN_THEMES`（缩略图复用 `LoginThemeThumbnail`），禁止硬编码 8 个 id。
 * - 展示当前默认值 + 最后修改人/时间（t109 的 `updatedBy` / `updatedAt`；自愈插入的默认行两者为 null）。
 * - 保存走 `PUT /admin/settings/login-theme`，失败按 400/401/403/5xx/网络分级提示
 *   （400 原样透出后端「支持: …」的完整列表）。
 * - 语义：「新访客默认看到该主题；已自行选择过的用户不受影响」——文案与后端行为一致
 *   （后端只写 system_settings，绝不写 `users.login_theme`）。
 *
 * 「登录方式」区块是给 t106（SSO 开关 / 注册审批）预留的位置，当前只放说明，
 * 不伪造未上线的开关。
 */
const Settings: React.FC = () => {
  const { t } = useT();
  /** null = 画像未返回（加载中）；false = 非管理员 */
  const [isAdmin, setIsAdmin] = useState<boolean | null>(null);
  const [setting, setSetting] = useState<AdminLoginThemeSetting | null>(null);
  const [selected, setSelected] = useState<LoginThemeId | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadFailure, setLoadFailure] = useState<AdminSettingFailure | null>(null);
  /** t106：SSO 登录方式开关（4 家 provider；仅管理员可读写） */
  const [ssoProviders, setSsoProviders] = useState<AdminSsoProvider[]>([]);
  const [ssoLoading, setSsoLoading] = useState(false);
  const [ssoLoadFailed, setSsoLoadFailed] = useState(false);
  const [ssoSaving, setSsoSaving] = useState<string | null>(null);
  /** t119：正在配置凭据的 provider（null = 弹窗关闭） */
  const [configTarget, setConfigTarget] = useState<AdminSsoProvider | null>(null);

  /** t106：加载 SSO 提供商（含未开启 / 未配置）；仅管理员会走到这里 */
  const loadSso = useCallback(async () => {
    setSsoLoading(true);
    try {
      const list = await adminListSsoProviders();
      setSsoProviders(list);
      setSsoLoadFailed(false);
    } catch (error) {
      setSsoLoadFailed(true);
      message.error(getApiErrorMessage(error, t('admin.sso.loadFailed')));
    } finally {
      setSsoLoading(false);
    }
  }, [t]);

  const toggleSso = async (provider: AdminSsoProvider, enabled: boolean) => {
    setSsoSaving(provider.code);
    try {
      const updated = await adminToggleSsoProvider(provider.code, enabled);
      setSsoProviders((list) => list.map((item) => (item.code === updated.code ? updated : item)));
      message.success(
        t('admin.sso.toggleSuccess', {
          name: knownSsoLabelKey(provider.code) ? t(knownSsoLabelKey(provider.code)!) : provider.name,
          state: enabled ? t('admin.sso.stateOn') : t('admin.sso.stateOff'),
        }),
      );
    } catch (error) {
      message.error(
        t('admin.sso.toggleFailed', { reason: getApiErrorMessage(error, t('admin.sso.loadFailed')) }),
      );
    } finally {
      setSsoSaving(null);
    }
  };


  useEffect(() => {
    let cancelled = false;
    getMyAccessProfile()
      .then((profile) => {
        if (cancelled) return;
        const admin = profile.roles.includes('admin');
        setIsAdmin(admin);
        if (!admin) {
          setLoading(false);
          return null;
        }
        // t106：管理员才加载 SSO 开关（非管理员连请求都不发）
        void loadSso();
        return getAdminLoginThemeSetting()
          .then((value) => {
            if (cancelled) return;
            setSetting(value);
            setSelected(value.defaultTheme);
            setLoadFailure(null);
          })
          .catch((error) => {
            if (cancelled) return;
            const failure = describeAdminSettingFailure(error);
            if (failure.kind === 'forbidden') {
              // 画像说 admin、后端说 403：以后端为准（token 过期/降权都会走到这里）
              setIsAdmin(false);
              return;
            }
            setLoadFailure(failure);
            if (failure.kind !== 'unauthorized') message.error(failure.text);
          })
          .finally(() => {
            if (!cancelled) setLoading(false);
          });
      })
      .catch(() => {
        if (cancelled) return;
        setIsAdmin(false);
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [loadSso]);

  const currentLabel = useMemo(
    () => (setting ? t(getLoginTheme(setting.defaultTheme).labelKey) : '-'),
    [setting, t],
  );
  const changed = Boolean(setting && selected && selected !== setting.defaultTheme);

  const save = async () => {
    if (!selected || saving || !changed) {
      if (!changed) message.info(t('admin.settings.loginTheme.unchanged'));
      return;
    }
    setSaving(true);
    try {
      const updated = await updateAdminLoginThemeSetting(selected);
      setSetting(updated);
      setSelected(updated.defaultTheme);
      setLoadFailure(null);
      message.success(t('admin.settings.loginTheme.saved', { name: t(getLoginTheme(updated.defaultTheme).labelKey) }));
    } catch (error) {
      const failure = describeAdminSettingFailure(error);
      if (failure.kind === 'forbidden') {
        setIsAdmin(false);
        return;
      }
      if (failure.kind !== 'unauthorized') message.error(failure.text);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }} data-testid="admin-settings-loading">
        <Spin /> <span style={{ marginLeft: 8, color: '#6b7280' }}>{t('admin.settings.loginTheme.loading')}</span>
      </div>
    );
  }

  if (isAdmin === false) {
    return (
      <div data-testid="admin-settings-forbidden">
        <Result
          status="403"
          title={t('admin.settings.loginTheme.forbiddenTitle')}
          subTitle={t('admin.settings.loginTheme.forbiddenDesc')}
        />
      </div>
    );
  }

  return (
    <div data-testid="admin-settings-page">
      <div style={{ marginBottom: 16 }}>
        <h2 style={{ margin: 0, fontSize: 20, fontWeight: 700 }}>{t('admin.settings.title')}</h2>
        <div style={{ marginTop: 4, fontSize: 13, color: '#6b7280' }}>{t('admin.settings.subtitle')}</div>
      </div>

      {/* 登录方式（t106）：4 家 SSO 提供商开关；未配置凭据的即使开启也不会出现在登录页 */}
      <Card
        size="small"
        style={{ marginBottom: 16 }}
        data-testid="admin-login-methods-card"
        title={<span>{t('admin.settings.loginMethods.title')}</span>}
        extra={
          <Button size="small" onClick={() => void loadSso()} loading={ssoLoading} data-testid="admin-sso-refresh">
            {t('ssoBinding.refresh')}
          </Button>
        }
      >
        <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 6 }}>{t('admin.settings.loginMethods.desc')}</div>
        <Alert type="info" showIcon message={t('admin.sso.note')} style={{ marginBottom: 10 }} data-testid="admin-sso-note" />

        {ssoLoadFailed ? (
          <Alert type="warning" showIcon message={t('admin.sso.loadFailed')} style={{ marginBottom: 10 }} data-testid="admin-sso-load-failed" />
        ) : null}

        <div data-testid="admin-sso-list">
          {ssoProviders.map((provider) => {
            const labelKey = knownSsoLabelKey(provider.code);
            const label = labelKey ? t(labelKey) : provider.name;
            /** t119：开启但没配好凭据 → 行内警告（用户点登录会被提示"未完成配置"） */
            const enabledButUnconfigured = provider.enabled && !provider.configured;
            return (
              <div
                className="admin-sso-row"
                key={provider.code}
                data-testid={`sso-row-${provider.code}`}
                data-configured={provider.configured ? 'true' : 'false'}
                data-enabled={provider.enabled ? 'true' : 'false'}
              >
                <span className="admin-sso-row__icon">
                  <SsoIcon code={provider.icon || provider.code} size={16} />
                </span>
                <div className="admin-sso-row__main">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <span>{label}</span>
                    <Tag color={provider.configured ? 'green' : 'orange'} data-testid={`admin-sso-meta-${provider.code}`}>
                      {provider.configured ? t('admin.sso.configured') : t('admin.sso.notConfigured')}
                    </Tag>
                    {enabledButUnconfigured ? (
                      <Tag color="red" data-testid={`sso-row-warning-${provider.code}`}>
                        {t('admin.ssoConfig.rowWarning')}
                      </Tag>
                    ) : null}
                  </div>
                  <div className="admin-sso-row__meta" data-testid={`sso-updated-${provider.code}`}>
                    {provider.updatedAt
                      ? t('admin.ssoConfig.updatedLine', {
                          time: provider.updatedAt.replace('T', ' ').slice(0, 19),
                          user: provider.updatedBy || '-',
                        })
                      : t('admin.ssoConfig.neverUpdated')}
                  </div>
                </div>
                <div className="admin-sso-row__tags">
                  <Tag color={provider.enabled ? 'green' : 'default'} data-testid={`admin-sso-state-${provider.code}`}>
                    {provider.enabled ? t('admin.sso.stateOn') : t('admin.sso.stateOff')}
                  </Tag>
                  <Switch
                    checked={provider.enabled}
                    loading={ssoSaving === provider.code}
                    onChange={(checked) => void toggleSso(provider, checked)}
                    data-testid={`admin-sso-switch-${provider.code}`}
                  />
                  <Button size="small" onClick={() => setConfigTarget(provider)} data-testid={`sso-config-open-${provider.code}`}>
                    {t('admin.ssoConfig.open')}
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      <Card
        size="small"
        title={<span data-testid="admin-login-theme-title">{t('admin.settings.loginTheme.title')}</span>}
        extra={
          <Tag color="blue" data-testid="admin-theme-current" data-theme={setting?.defaultTheme ?? ''}>
            {t('admin.settings.loginTheme.current', { name: currentLabel })}
          </Tag>
        }
        data-testid="admin-login-theme-card"
      >
        <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 10 }}>{t('admin.settings.loginTheme.desc')}</div>

        {loadFailure ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message={t('admin.settings.loginTheme.loadFailed')}
            description={loadFailure.text}
            data-testid="admin-theme-load-failed"
          />
        ) : null}

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, fontSize: 12, color: '#4b5563', marginBottom: 12 }}>
          <span data-testid="admin-theme-updated-by">
            {t('admin.settings.loginTheme.updatedBy', { name: setting?.updatedBy ?? '-' })}
          </span>
          <span data-testid="admin-theme-updated-at">
            {t('admin.settings.loginTheme.updatedAt', { time: setting?.updatedAt ?? '-' })}
          </span>
          {setting && !setting.updatedBy && !setting.updatedAt ? (
            <span data-testid="admin-theme-never-updated">{t('admin.settings.loginTheme.never')}</span>
          ) : null}
        </div>

        <div className="admin-theme-grid" data-testid="admin-theme-grid">
          {LOGIN_THEMES.map((entry) => {
            const active = selected === entry.id;
            const isCurrentDefault = setting?.defaultTheme === entry.id;
            return (
              <button
                key={entry.id}
                type="button"
                className={['admin-theme-card', active ? 'is-active' : ''].filter(Boolean).join(' ')}
                aria-pressed={active}
                aria-label={t(entry.labelKey)}
                data-testid={`admin-theme-card-${entry.id}`}
                data-active={active ? 'true' : 'false'}
                data-current-default={isCurrentDefault ? 'true' : 'false'}
                disabled={saving}
                onClick={() => setSelected(entry.id)}
              >
                <LoginThemeThumbnail entry={entry} />
                <div className="admin-theme-card__body">
                  <div className="admin-theme-card__name">{t(entry.labelKey)}</div>
                  {isCurrentDefault ? (
                    <Tag color="green" className="admin-theme-card__tag">
                      {t('admin.settings.loginTheme.selectedTag')}
                    </Tag>
                  ) : null}
                </div>
              </button>
            );
          })}
        </div>

        <Space style={{ marginTop: 14 }} align="center">
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            disabled={!changed}
            onClick={save}
            data-testid="admin-theme-save"
          >
            {t('admin.settings.loginTheme.save')}
          </Button>
          <span style={{ fontSize: 12, color: '#6b7280' }} data-testid="admin-theme-order-hint">
            {t('admin.settings.loginTheme.orderHint')}
          </span>
        </Space>
      </Card>

      <style>{`
        .admin-theme-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
          gap: 10px;
        }
        .admin-theme-card {
          appearance: none;
          background: #fff;
          border: 1px solid #e5e7eb;
          border-radius: 12px;
          padding: 8px;
          cursor: pointer;
          text-align: left;
          display: flex;
          flex-direction: column;
          gap: 6px;
          font: inherit;
          color: inherit;
          transition: border-color 160ms ease, box-shadow 160ms ease;
        }
        .admin-theme-card:hover {
          border-color: #ff9a6c;
          box-shadow: 0 6px 18px rgba(31, 35, 40, 0.08);
        }
        .admin-theme-card:focus-visible {
          outline: 2px solid #ff6b35;
          outline-offset: 2px;
        }
        .admin-theme-card.is-active {
          border-color: #1677ff;
          box-shadow: 0 0 0 1px #1677ff inset;
        }
        .admin-theme-card__body {
          display: flex;
          align-items: center;
          gap: 6px;
          flex-wrap: wrap;
        }
        .admin-theme-card__name {
          font-size: 13px;
          font-weight: 600;
          color: #1f2328;
        }
        .admin-theme-card__tag {
          margin-inline-end: 0;
          font-size: 10px;
          line-height: 16px;
          padding: 0 6px;
        }
      `}</style>

      {/* t119：SSO 凭据配置弹窗（每家一个；保存成功后原地更新该行徽标与更新时间） */}
      <SsoProviderConfigModal
        provider={configTarget}
        onClose={() => setConfigTarget(null)}
        onSaved={(updated) => {
          setSsoProviders((list) => list.map((item) => (item.code === updated.code ? updated : item)));
          setConfigTarget(null);
        }}
      />
    </div>
  );
};

export default Settings;
