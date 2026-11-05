import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Checkbox, Input, Modal, Tag, Typography, message } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import {
  adminUpdateSsoProviderConfig,
  ssoRedirectUri,
  type AdminSsoProvider,
  type AdminSsoProviderUpdate,
} from '@/api/sso';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { knownSsoLabelKey } from '@/components/sso/providers';
import SsoIcon from '@/components/sso/SsoIcon';
import { useT, type MessageKey } from '@/i18n';

/**
 * SSO 凭据配置弹窗（t119，后端 t116）。
 *
 * 设计要点：
 * - **只提交改动过的字段**（部分更新）：留空/未改动 = 后端保持原值，
 *   这样「打开弹窗不改 secret 直接保存」绝不会把密钥冲掉；
 * - **secret 永不回显明文**：密码框只把 `clientSecretMasked`（如 `gh_****abcd`）放在 placeholder，
 *   输入为空即"不修改"；要清空必须显式勾「清除密钥」；
 * - **URL 前端先校验**：必须 https（`http://localhost` / `http://127.0.0.1` 放宽，供本地联调），
 *   不合法直接拦下并就地提示，避免打一次无谓的 400；
 * - 底部固定展示回调地址 `<origin>/auth/sso/callback` + 一键复制（各平台后台都要填这个）；
 * - 失败按 400（字段格式/URL 非法，透出后端 message）/403/404/5xx/网络分级。
 */
export interface SsoProviderConfigModalProps {
  provider: AdminSsoProvider | null;
  onClose: () => void;
  /** 保存成功：回传后端最新 provider（页面据此更新该行徽标） */
  onSaved: (updated: AdminSsoProvider, patch: AdminSsoProviderUpdate) => void;
}

interface FormState {
  clientId: string;
  clientSecret: string;
  clearClientSecret: boolean;
  authorizeUrl: string;
  tokenUrl: string;
  userinfoUrl: string;
  scopes: string;
}

const emptyForm = (provider: AdminSsoProvider | null): FormState => ({
  clientId: provider?.clientId ?? '',
  clientSecret: '',
  clearClientSecret: false,
  authorizeUrl: provider?.authorizeUrl ?? '',
  tokenUrl: provider?.tokenUrl ?? '',
  userinfoUrl: provider?.userinfoUrl ?? '',
  scopes: provider?.scopes ?? '',
});

const LOCAL_HOSTS = ['localhost', '127.0.0.1', '[::1]'];

/**
 * URL 校验：https 必填；本地回环允许 http（与后端 t116 一致）。
 *
 * @returns 错误 i18n key（无效）或 null（有效）；空串视为"未填"，由调用方决定是否提交
 */
export const validateSsoUrl = (value: string): 'scheme' | 'format' | 'localHttp' | null => {
  const trimmed = value.trim();
  if (!trimmed) return null;
  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    return 'format';
  }
  if (parsed.protocol === 'https:') return null;
  if (parsed.protocol === 'http:') {
    return LOCAL_HOSTS.includes(parsed.hostname) ? 'localHttp' : 'scheme';
  }
  return 'scheme';
};

const isBlockingUrlError = (result: ReturnType<typeof validateSsoUrl>): boolean => result === 'scheme' || result === 'format';

const SsoProviderConfigModal: React.FC<SsoProviderConfigModalProps> = ({ provider, onClose, onSaved }) => {
  const { t } = useT();
  const [form, setForm] = useState<FormState>(() => emptyForm(provider));
  const [initial, setInitial] = useState<FormState>(() => emptyForm(provider));
  const [saving, setSaving] = useState(false);
  const [copying, setCopying] = useState(false);
  const [urlErrors, setUrlErrors] = useState<Record<string, string | null>>({});

  useEffect(() => {
    const next = emptyForm(provider);
    setForm(next);
    setInitial(next);
    setUrlErrors({});
  }, [provider]);

  const callbackUrl = useMemo(() => ssoRedirectUri(), []);
  const label = useMemo(() => {
    if (!provider) return '';
    const key = knownSsoLabelKey(provider.code);
    return key ? t(key) : provider.name;
  }, [provider, t]);

  /** scopes 以 Tag 展示（去重、保序；后端保存时也会去重） */
  const scopesTags = useMemo(() => {
    const seen = new Set<string>();
    form.scopes
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .forEach((item) => seen.add(item));
    return Array.from(seen);
  }, [form.scopes]);

  const setField = (field: keyof FormState, value: string | boolean) => {
    setForm((prev) => ({ ...prev, [field]: value }) as FormState);
  };

  const checkUrl = (field: 'authorizeUrl' | 'tokenUrl' | 'userinfoUrl', value: string) => {
    const result = validateSsoUrl(value);
    setUrlErrors((prev) => ({ ...prev, [field]: result }));
    return result;
  };

  const hasBlockingError = useMemo(
    () => Object.values(urlErrors).some((result) => isBlockingUrlError(result as ReturnType<typeof validateSsoUrl>)),
    [urlErrors],
  );

  /** 只收集"改动过"的字段（部分更新），secret 单独处理 */
  const buildPatch = (): AdminSsoProviderUpdate => {
    const patch: AdminSsoProviderUpdate = {};
    const clientId = form.clientId.trim();
    if (clientId !== initial.clientId.trim()) patch.clientId = clientId;
    const secret = form.clientSecret.trim();
    if (form.clearClientSecret) patch.clearClientSecret = true;
    else if (secret) patch.clientSecret = secret;
    const urls: Array<['authorizeUrl' | 'tokenUrl' | 'userinfoUrl', string]> = [
      ['authorizeUrl', form.authorizeUrl],
      ['tokenUrl', form.tokenUrl],
      ['userinfoUrl', form.userinfoUrl],
    ];
    urls.forEach(([field, value]) => {
      const next = value.trim();
      if (next !== initial[field].trim()) patch[field] = next;
    });
    const scopes = form.scopes
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .join(',');
    if (scopes !== initial.scopes.trim()) patch.scopes = scopes;
    return patch;
  };

  const save = async () => {
    if (!provider || saving) return;
    // 提交前对所有非空 URL 再校验一遍（防止用户没触发 onBlur 就点保存）
    const errors: Record<string, string | null> = {
      authorizeUrl: validateSsoUrl(form.authorizeUrl),
      tokenUrl: validateSsoUrl(form.tokenUrl),
      userinfoUrl: validateSsoUrl(form.userinfoUrl),
    };
    setUrlErrors(errors);
    if (Object.values(errors).some((result) => isBlockingUrlError(result as ReturnType<typeof validateSsoUrl>))) {
      message.error(t('admin.ssoConfig.errUrlInvalid'));
      return;
    }
    const patch = buildPatch();
    if (Object.keys(patch).length === 0) {
      message.info(t('admin.ssoConfig.nothingChanged'));
      return;
    }
    setSaving(true);
    try {
      const updated = await adminUpdateSsoProviderConfig(provider.code, patch);
      message.success(t('admin.ssoConfig.saveSuccess', { name: label }));
      onSaved(updated, patch);
    } catch (error) {
      const status = getApiErrorStatus(error);
      const backend = getApiErrorMessage(error);
      if (status === 400) message.error(t('admin.ssoConfig.err400', { message: backend }));
      else if (status === 403) message.error(t('admin.ssoConfig.err403'));
      else if (status === 404) message.error(t('admin.ssoConfig.err404'));
      else if (typeof status === 'number' && status >= 500) message.error(t('admin.ssoConfig.err5xx', { status }));
      else message.error(t('admin.ssoConfig.errGeneric', { message: backend }));
    } finally {
      setSaving(false);
    }
  };

  const copyCallback = async () => {
    try {
      setCopying(true);
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(callbackUrl);
      } else {
        // 非安全上下文（http + 非 localhost）没有 clipboard API：退回隐藏 textarea
        const el = document.createElement('textarea');
        el.value = callbackUrl;
        el.style.position = 'fixed';
        el.style.opacity = '0';
        document.body.appendChild(el);
        el.select();
        document.execCommand('copy');
        document.body.removeChild(el);
      }
      message.success(t('admin.ssoConfig.copied'));
    } catch {
      message.warning(t('admin.ssoConfig.copyFailed'));
    } finally {
      setCopying(false);
    }
  };

  if (!provider) return null;

  const urlField = (
    field: 'authorizeUrl' | 'tokenUrl' | 'userinfoUrl',
    labelKey: string,
    testId: string,
    placeholder: string,
  ) => {
    const result = urlErrors[field] as ReturnType<typeof validateSsoUrl>;
    return (
      <div style={{ marginBottom: 12 }}>
        <div style={{ fontSize: 12, color: '#4b5563', marginBottom: 4 }}>{t(labelKey as MessageKey)}</div>
        <Input
          value={form[field]}
          placeholder={placeholder}
          status={isBlockingUrlError(result) ? 'error' : undefined}
          onChange={(event) => {
            const value = event.target.value;
            setField(field, value);
            // 边输入边校验：远端 http / 格式错误立刻反馈，不必等 onBlur（保存按钮也随之禁用）
            setUrlErrors((prev) => ({ ...prev, [field]: validateSsoUrl(value) }));
          }}
          onBlur={() => checkUrl(field, form[field])}
          data-testid={testId}
        />
        {isBlockingUrlError(result) ? (
          <div style={{ fontSize: 12, color: '#ef4444', marginTop: 4 }} data-testid={`${testId}-error`}>
            {result === 'format' ? t('admin.ssoConfig.urlFormat') : t('admin.ssoConfig.urlScheme')}
          </div>
        ) : result === 'localHttp' ? (
          <div style={{ fontSize: 12, color: '#d97706', marginTop: 4 }} data-testid={`${testId}-local-hint`}>
            {t('admin.ssoConfig.localHttpHint')}
          </div>
        ) : null}
      </div>
    );
  };

  return (
    <Modal
      open
      title={t('admin.ssoConfig.title', { name: label })}
      onCancel={saving ? undefined : onClose}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose} disabled={saving} data-testid="sso-config-cancel">
            {t('admin.ssoConfig.cancel')}
          </Button>
          <Button type="primary" loading={saving} disabled={hasBlockingError} onClick={save} data-testid="sso-config-save">
            {t('admin.ssoConfig.save')}
          </Button>
        </div>
      }
      width={620}
      destroyOnHidden
    >
      <div data-testid="sso-config-modal" data-provider={provider.code}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <SsoIcon code={provider.icon || provider.code} size={18} />
          <span style={{ fontWeight: 600 }}>{label}</span>
          <Tag color={provider.configured ? 'green' : 'orange'} data-testid="sso-config-state">
            {provider.configured ? t('admin.sso.configured') : t('admin.sso.notConfigured')}
          </Tag>
          <Tag color={provider.enabled ? 'blue' : 'default'}>
            {provider.enabled ? t('admin.sso.stateOn') : t('admin.sso.stateOff')}
          </Tag>
        </div>

        <Alert type="info" showIcon message={t('admin.ssoConfig.hint')} style={{ marginBottom: 12 }} />

        <div style={{ marginBottom: 12 }}>
          <div style={{ fontSize: 12, color: '#4b5563', marginBottom: 4 }}>{t('admin.ssoConfig.clientId')}</div>
          <Input
            value={form.clientId}
            placeholder={t('admin.ssoConfig.clientIdPlaceholder')}
            onChange={(event) => setField('clientId', event.target.value)}
            data-testid="sso-config-client-id"
          />
        </div>

        <div style={{ marginBottom: 6 }}>
          <div style={{ fontSize: 12, color: '#4b5563', marginBottom: 4 }}>{t('admin.ssoConfig.clientSecret')}</div>
          <Input.Password
            value={form.clientSecret}
            disabled={form.clearClientSecret}
            // 掩码只出现在 placeholder：前端永远不把 secret 明文放到 value 里
            placeholder={provider.clientSecretMasked || t('admin.ssoConfig.clientSecretPlaceholder')}
            onChange={(event) => setField('clientSecret', event.target.value)}
            data-testid="sso-config-client-secret"
          />
          <div style={{ fontSize: 12, color: '#9ca3af', marginTop: 4 }}>
            {provider.clientSecretMasked
              ? t('admin.ssoConfig.secretMaskedHint', { masked: provider.clientSecretMasked })
              : t('admin.ssoConfig.secretUnsetHint')}
          </div>
        </div>

        <div style={{ marginBottom: 12 }}>
          <Checkbox
            checked={form.clearClientSecret}
            onChange={(event) => {
              setField('clearClientSecret', event.target.checked);
              if (event.target.checked) setField('clientSecret', '');
            }}
            data-testid="sso-config-clear-secret"
          >
            <span style={{ fontSize: 13 }}>{t('admin.ssoConfig.clearSecret')}</span>
          </Checkbox>
          {form.clearClientSecret ? (
            <div style={{ fontSize: 12, color: '#ef4444', marginTop: 4 }} data-testid="sso-config-clear-hint">
              {t('admin.ssoConfig.clearSecretHint')}
            </div>
          ) : null}
        </div>

        {urlField('authorizeUrl', 'admin.ssoConfig.authorizeUrl', 'sso-config-authorize-url', 'https://.../authorize')}
        {urlField('tokenUrl', 'admin.ssoConfig.tokenUrl', 'sso-config-token-url', 'https://.../token')}
        {urlField('userinfoUrl', 'admin.ssoConfig.userinfoUrl', 'sso-config-userinfo-url', 'https://.../userinfo')}

        <div style={{ marginBottom: 12 }}>
          <div style={{ fontSize: 12, color: '#4b5563', marginBottom: 4 }}>{t('admin.ssoConfig.scopes')}</div>
          <Input
            value={form.scopes}
            placeholder={t('admin.ssoConfig.scopesPlaceholder')}
            onChange={(event) => setField('scopes', event.target.value)}
            data-testid="sso-config-scopes"
          />
          <div style={{ marginTop: 6, display: 'flex', gap: 6, flexWrap: 'wrap' }} data-testid="sso-config-scopes-tags">
            {scopesTags.length > 0 ? scopesTags.map((scope) => <Tag key={scope}>{scope}</Tag>) : <span style={{ fontSize: 12, color: '#9ca3af' }}>{t('admin.ssoConfig.scopesEmpty')}</span>}
          </div>
        </div>

        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 4 }}
          message={t('admin.ssoConfig.callbackTitle')}
          description={
            <div>
              <div style={{ marginBottom: 6 }}>{t('admin.ssoConfig.callbackDesc')}</div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                <Typography.Text code copyable={false} data-testid="sso-callback-url" style={{ fontSize: 12 }}>
                  {callbackUrl}
                </Typography.Text>
                <Button size="small" icon={<CopyOutlined />} loading={copying} onClick={() => void copyCallback()} data-testid="sso-copy-callback">
                  {t('admin.ssoConfig.copy')}
                </Button>
              </div>
            </div>
          }
        />
      </div>
    </Modal>
  );
};

export default SsoProviderConfigModal;


