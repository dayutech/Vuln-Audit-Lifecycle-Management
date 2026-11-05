import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Tag, message } from 'antd';
import { LinkOutlined, ReloadOutlined } from '@ant-design/icons';
import { useT } from '@/i18n';
import SsoIcon from '@/components/sso/SsoIcon';
import { KNOWN_SSO_PROVIDERS, knownSsoLabelKey } from '@/components/sso/providers';
import {
  beginSsoRedirect,
  getMySsoBindings,
  getSsoProviders,
  isProviderConfigured,
  startSsoBind,
  unbindSso,
} from '@/api/sso';
import type { SsoBinding, SsoProvider } from '@/api/sso';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import '@/components/sso/sso.css';

/**
 * 账号设置：社交账号绑定（t106）。
 *
 * - 列表来自「前端已知 4 家 + 后端实际绑定」的并集：**已绑定 / 未绑定 / 未启用** 三态完整；
 *   后端 `GET /auth/sso/providers` 只暴露「已开启且已配置」的，所以不在其中且未绑定的即「未启用」。
 * - 绑定：`POST /users/me/sso-bindings/{code}/start` → 记下「绑定流程」→ 跳第三方授权 →
 *   回调页 `/auth/sso/callback` 处理（成功回本页刷新）。
 * - 解绑：弹窗输入**当前密码** → `DELETE /users/me/sso-bindings/{code}`；
 *   密码错误 403 定位到密码字段，重复绑定 409、未配置 503 分级提示。
 */
interface RowModel {
  code: string;
  label: string;
  binding: SsoBinding | null;
  /** 在公开列表里（= 管理员已启用） */
  available: boolean;
  /** 已启用但凭据没配齐（t116 `configured=false`）：显示「未配置」，按钮仍可点 */
  configured: boolean;
}

const SsoBindings: React.FC = () => {
  const { t } = useT();
  const [bindings, setBindings] = useState<SsoBinding[]>([]);
  const [providers, setProviders] = useState<SsoProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [starting, setStarting] = useState<string | null>(null);
  const [unbindTarget, setUnbindTarget] = useState<RowModel | null>(null);
  const [unbinding, setUnbinding] = useState(false);
  const [form] = Form.useForm<{ password: string }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, available] = await Promise.all([getMySsoBindings(), getSsoProviders()]);
      setBindings(list);
      setProviders(available);
      setLoadError(null);
    } catch (error) {
      setLoadError(getApiErrorMessage(error, t('ssoBinding.loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  const rows = useMemo<RowModel[]>(() => {
    const codes: string[] = [...KNOWN_SSO_PROVIDERS.map((item) => item.code)];
    bindings.forEach((binding) => {
      if (!codes.includes(binding.code)) codes.push(binding.code);
    });
    return codes.map((code) => {
      const labelKey = knownSsoLabelKey(code);
      const fromProviders = providers.find((item) => item.code === code);
      return {
        code,
        label: labelKey ? t(labelKey) : fromProviders?.name ?? code,
        binding: bindings.find((item) => item.code === code) ?? null,
        available: Boolean(fromProviders),
        configured: isProviderConfigured(fromProviders),
      };
    });
  }, [bindings, providers, t]);

  const startBind = async (row: RowModel) => {
    // 已启用但凭据未配置：按钮可点，只提示「联系管理员」，不发起跳转
    if (!row.configured) {
      message.warning(t('sso.notConfiguredNotice', { name: row.label }));
      return;
    }
    setStarting(row.code);
    try {
      const authorize = await startSsoBind(row.code);
      if (!authorize.url) {
        message.error(t('sso.error.unavailable'));
        return;
      }
      beginSsoRedirect(row.code, 'bind', authorize.url);
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 409) {
        message.error(t('ssoBinding.duplicate', { name: row.label }));
      } else if (status === 503) {
        message.error(t('ssoBinding.notConfigured'));
      } else if (status === 400) {
        message.error(t('sso.error.disabled'));
      } else {
        message.error(getApiErrorMessage(error, t('ssoBinding.failed')));
      }
      setStarting(null);
    }
  };

  const submitUnbind = async () => {
    if (!unbindTarget) return;
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    setUnbinding(true);
    try {
      await unbindSso(unbindTarget.code, values.password);
      message.success(t('ssoBinding.unbindSuccess', { name: unbindTarget.label }));
      setUnbindTarget(null);
      form.resetFields();
      await load();
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 403) {
        form.setFields([{ name: 'password', errors: [t('ssoBinding.passwordWrong')] }]);
      } else if (status === 503) {
        message.error(t('ssoBinding.notConfigured'));
      } else {
        message.error(getApiErrorMessage(error, t('ssoBinding.failed')));
      }
    } finally {
      setUnbinding(false);
    }
  };

  return (
    <div data-testid="sso-bindings-page">
      <div style={{ marginBottom: 16 }}>
        <h2 style={{ margin: 0, fontSize: 20, fontWeight: 700 }}>{t('ssoBinding.title')}</h2>
        <div style={{ marginTop: 4, fontSize: 13, color: '#6b7280' }}>{t('ssoBinding.subtitle')}</div>
      </div>

      <Card
        size="small"
        title={
          <span>
            <LinkOutlined style={{ marginRight: 6 }} />
            {t('ssoBinding.title')}
          </span>
        }
        extra={
          <Button size="small" icon={<ReloadOutlined />} onClick={() => void load()} data-testid="sso-bindings-refresh">
            {t('ssoBinding.refresh')}
          </Button>
        }
        loading={loading}
      >
        {loadError ? (
          <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={loadError} data-testid="sso-bindings-error" />
        ) : null}

        <div className="sso-bindings" data-testid="sso-bindings-list">
          {rows.map((row) => {
            // t118：已启用但未配置 → 'unconfigured'（可点、提示联系管理员），只有「未启用」才禁用
            const status = row.binding
              ? 'bound'
              : !row.available
                ? 'disabled'
                : row.configured
                  ? 'unbound'
                  : 'unconfigured';
            return (
              <div className="sso-bindings__row" key={row.code} data-testid={`sso-binding-row-${row.code}`} data-status={status}>
                <span className="sso-bindings__icon">
                  <SsoIcon code={row.code} size={18} />
                </span>
                <div className="sso-bindings__main">
                  <div className="sso-bindings__name">
                    {row.label}
                    <Tag
                      color={status === 'bound' ? 'green' : status === 'unbound' ? 'default' : 'orange'}
                      data-testid={`sso-binding-status-${row.code}`}
                    >
                      {t(`ssoBinding.status.${status}`)}
                    </Tag>
                  </div>
                  <div className="sso-bindings__meta">
                    {row.binding ? (
                      <>
                        <div>{t('ssoBinding.external', { name: row.binding.displayName || row.binding.externalId })}</div>
                        <div>{t('ssoBinding.boundAt', { time: row.binding.boundAt ?? '-' })}</div>
                      </>
                    ) : (
                      <div>
                        {status === 'disabled'
                          ? t('ssoBinding.disabledHint')
                          : status === 'unconfigured'
                            ? t('ssoBinding.unconfiguredHint')
                            : t('ssoBinding.unbindNote')}
                      </div>
                    )}
                  </div>
                </div>
                <div className="sso-bindings__actions">
                  {row.binding ? (
                    <Button danger size="small" onClick={() => { form.resetFields(); setUnbindTarget(row); }} data-testid={`sso-unbind-${row.code}`}>
                      {t('ssoBinding.unbind')}
                    </Button>
                  ) : (
                    <Button
                      type="primary"
                      size="small"
                      disabled={status === 'disabled'}
                      loading={starting === row.code}
                      onClick={() => void startBind(row)}
                      data-testid={`sso-bind-${row.code}`}
                      data-sso-configured={row.configured ? 'true' : 'false'}
                    >
                      {t('ssoBinding.bind')}
                    </Button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      <Modal
        open={Boolean(unbindTarget)}
        title={unbindTarget ? t('ssoBinding.unbindTitle', { name: unbindTarget.label }) : ''}
        onCancel={() => { setUnbindTarget(null); form.resetFields(); }}
        onOk={() => void submitUnbind()}
        confirmLoading={unbinding}
        okText={t('ssoBinding.unbind')}
        cancelText={t('common.cancel')}
        okButtonProps={{ danger: true, 'data-testid': 'sso-unbind-confirm' } as any}
        data-testid="sso-unbind-modal"
      >
        <p style={{ fontSize: 12.5, color: '#6b7280', marginTop: 0 }}>{t('ssoBinding.unbindDesc')}</p>
        <Form form={form} layout="vertical">
          <Form.Item
            name="password"
            label={t('ssoBinding.password')}
            rules={[{ required: true, message: t('ssoBinding.passwordRequired') }]}
          >
            <Input.Password placeholder={t('ssoBinding.password')} autoComplete="current-password" data-testid="sso-unbind-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default SsoBindings;
