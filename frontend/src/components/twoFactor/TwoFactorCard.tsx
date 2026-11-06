import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Input, Modal, Space, Spin, Tag, Typography, message } from 'antd';
import { CopyOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { QRCodeSVG } from 'qrcode.react';
import {
  confirmTwoFactor,
  disableTwoFactor,
  formatBase32Secret,
  getTwoFactorStatus,
  initTwoFactor,
  isTwoFactorSetupRequiredError,
  type TwoFactorInitResult,
  type TwoFactorStatus,
} from '@/api/twoFactor';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useUserStore } from '@/store/userStore';
import { useT } from '@/i18n';
import RecoveryCodesModal from './RecoveryCodesModal';

/**
 * 两步验证卡片（t137，后端 t125/t135）。
 *
 * 三段状态机：
 * 1. **未启用**：说明 + 「启用两步验证」→ `POST /users/me/2fa/init` → 展示二维码（自绘 SVG，离线）+ Base32 密钥（4 位分组、可复制）+ 6 位动态码输入 → `confirm`；
 * 2. **恢复码弹窗**：confirm 成功后**必须**保存恢复码（勾选确认才能关闭），关闭后卡片刷新为已启用；
 * 3. **已启用**：启用时间 + 剩余恢复码数 + 「关闭两步验证」（密码 + 动态码/恢复码；管理员强制时后端 403 → 提示不可关闭）。
 *
 * 约定：
 * - secret 只在 init 响应里出现，**不写 localStorage**、不日志；
 * - 动态码输入只接受 6 位数字（恢复码在关闭弹窗里走另一个输入框，长度不限）；
 * - 管理员强制（`status.forced`）时卡片顶部给出提示，并在关闭按钮上直接拦（后端仍会 403 兜底）。
 */
const TwoFactorCard: React.FC = () => {
  const { t } = useT();
  const { user } = useUserStore();
  const [status, setStatus] = useState<TwoFactorStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  /** 启用流程 */
  const [initResult, setInitResult] = useState<TwoFactorInitResult | null>(null);
  const [confirmCode, setConfirmCode] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [initLoading, setInitLoading] = useState(false);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);

  /** 关闭流程 */
  const [disableOpen, setDisableOpen] = useState(false);
  const [disablePassword, setDisablePassword] = useState('');
  const [disableCode, setDisableCode] = useState('');
  const [disabling, setDisabling] = useState(false);
  const [disableError, setDisableError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const value = await getTwoFactorStatus();
      setStatus(value);
      setLoadError(null);
    } catch (error) {
      setLoadError(getApiErrorMessage(error, t('twoFactor.err.generic', { message: '' })));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  const startEnable = async () => {
    setInitLoading(true);
    setCodeError(null);
    try {
      const result = await initTwoFactor();
      setInitResult(result);
      setConfirmCode('');
    } catch (error) {
      message.error(t('twoFactor.initFailed', { message: getApiErrorMessage(error) }));
    } finally {
      setInitLoading(false);
    }
  };

  const submitConfirm = async () => {
    const code = confirmCode.replace(/\s+/g, '');
    if (!/^\d{6}$/.test(code)) {
      setCodeError(t('twoFactor.codeInvalid'));
      return;
    }
    setConfirming(true);
    setCodeError(null);
    try {
      const codes = await confirmTwoFactor(code);
      setInitResult(null);
      setConfirmCode('');
      setRecoveryCodes(codes);
      message.success(t('twoFactor.enabledSuccess'));
      await load();
    } catch (error) {
      const statusCode = getApiErrorStatus(error);
      if (statusCode === 400) setCodeError(t('twoFactor.confirmWrongCode'));
      else message.error(t('twoFactor.confirmFailed', { message: getApiErrorMessage(error) }));
    } finally {
      setConfirming(false);
    }
  };

  const submitDisable = async () => {
    if (!disablePassword || !disableCode) {
      setDisableError(t('twoFactor.disable.err400', { message: '' }));
      return;
    }
    setDisabling(true);
    setDisableError(null);
    try {
      await disableTwoFactor(disablePassword, disableCode);
      setDisableOpen(false);
      setDisablePassword('');
      setDisableCode('');
      message.success(t('twoFactor.disable.success'));
      await load();
    } catch (error) {
      const statusCode = getApiErrorStatus(error);
      if (statusCode === 403) setDisableError(t('twoFactor.disable.forcedByAdmin'));
      else if (statusCode === 400) setDisableError(t('twoFactor.disable.err400', { message: getApiErrorMessage(error) }));
      else if (isTwoFactorSetupRequiredError(error)) setDisableError(t('twoFactor.disable.forcedByAdmin'));
      else setDisableError(t('twoFactor.disable.errGeneric', { message: getApiErrorMessage(error) }));
    } finally {
      setDisabling(false);
    }
  };

  if (loading) {
    return (
      <Card size="small" data-testid="twofa-card" data-state="loading">
        <Spin size="small" /> <span style={{ marginLeft: 8, color: '#6b7280' }}>{t('twoFactor.loading')}</span>
      </Card>
    );
  }

  if (loadError) {
    return (
      <Card size="small" data-testid="twofa-card" data-state="error">
        <Alert type="warning" showIcon message={t('twoFactor.loadFailed', { message: loadError })} />
      </Card>
    );
  }

  if (!status) return null;

  return (
    <Card
      size="small"
      data-testid="twofa-card"
      data-state={status.enabled ? 'enabled' : 'disabled'}
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <SafetyCertificateOutlined />
          <span data-testid="twofa-title">{t('twoFactor.title')}</span>
          <Tag color={status.enabled ? 'green' : 'default'} data-testid="twofa-status">
            {status.enabled ? t('twoFactor.status.enabled') : t('twoFactor.status.disabled')}
          </Tag>
        </span>
      }
    >
      <div style={{ fontSize: 13, color: '#4b5563', marginBottom: 10 }}>{t('twoFactor.subtitle')}</div>

      {status.forced ? (
        <Alert type="warning" showIcon message={t('twoFactor.forcedNotice')} style={{ marginBottom: 10 }} data-testid="twofa-forced-notice" />
      ) : null}
      {!status.available ? (
        <Alert type="info" showIcon message={t('twoFactor.unavailable')} style={{ marginBottom: 10 }} data-testid="twofa-unavailable" />
      ) : null}

      {/* ---------- 未启用：启用入口 ---------- */}
      {!status.enabled && !initResult ? (
        <Button type="primary" onClick={() => void startEnable()} loading={initLoading} disabled={!status.available} data-testid="twofa-enable">
          {t('twoFactor.enable')}
        </Button>
      ) : null}

      {/* ---------- 启用流程：二维码 + 手动密钥 + 动态码 ---------- */}
      {!status.enabled && initResult ? (
        <div data-testid="twofa-setup">
          <div style={{ fontSize: 13, marginBottom: 8 }}>{t('twoFactor.step.scan')}</div>
          <div
            data-testid="twofa-qr"
            style={{ display: 'inline-block', padding: 12, background: '#fff', border: '1px solid #f0f0f0', borderRadius: 8 }}
          >
            <QRCodeSVG value={initResult.otpauthUri} size={168} level="M" title={t('twoFactor.qrAlt')} />
          </div>

          <div style={{ margin: '12px 0 4px', fontSize: 13 }}>{t('twoFactor.step.secret')}</div>
          <Space wrap>
            <Typography.Text code data-testid="twofa-secret" style={{ fontSize: 14, letterSpacing: 1 }}>
              {formatBase32Secret(initResult.secretBase32)}
            </Typography.Text>
            <Button
              size="small"
              icon={<CopyOutlined />}
              data-testid="twofa-copy-secret"
              onClick={() => {
                const el = document.createElement('textarea');
                el.value = initResult.secretBase32;
                el.style.position = 'fixed';
                el.style.opacity = '0';
                document.body.appendChild(el);
                el.select();
                document.execCommand('copy');
                document.body.removeChild(el);
                message.success(t('twoFactor.secretCopied'));
              }}
            >
              {t('twoFactor.copySecret')}
            </Button>
          </Space>

          <div style={{ margin: '14px 0 4px', fontSize: 13 }}>{t('twoFactor.step.code')}</div>
          <Space wrap>
            <Input
              value={confirmCode}
              onChange={(event) => {
                setConfirmCode(event.target.value.replace(/\D/g, '').slice(0, 6));
                if (codeError) setCodeError(null);
              }}
              placeholder={t('twoFactor.codePlaceholder')}
              maxLength={6}
              style={{ width: 160, letterSpacing: 3, fontFamily: 'ui-monospace, Menlo, Consolas, monospace' }}
              status={codeError ? 'error' : undefined}
              data-testid="twofa-code"
              onPressEnter={() => void submitConfirm()}
            />
            <Button type="primary" loading={confirming} onClick={() => void submitConfirm()} data-testid="twofa-confirm">
              {t('twoFactor.confirm')}
            </Button>
            <Button
              onClick={() => {
                setInitResult(null);
                setConfirmCode('');
                setCodeError(null);
              }}
              data-testid="twofa-cancel"
            >
              {t('twoFactor.cancel')}
            </Button>
          </Space>
          {codeError ? (
            <div style={{ color: '#ef4444', fontSize: 12, marginTop: 6 }} data-testid="twofa-code-error">
              {codeError}
            </div>
          ) : null}
        </div>
      ) : null}

      {/* ---------- 已启用：状态 + 关闭 ---------- */}
      {status.enabled ? (
        <div data-testid="twofa-enabled">
          <div style={{ fontSize: 13, color: '#4b5563', marginBottom: 4 }} data-testid="twofa-enabled-at">
            {t('twoFactor.enabledAt', { time: (status.confirmedAt ?? '').replace('T', ' ').slice(0, 19) || '-' })}
          </div>
          <div style={{ fontSize: 13, color: '#4b5563', marginBottom: 8 }} data-testid="twofa-recovery-left">
            {t('twoFactor.recoveryLeft', { count: status.recoveryCodesLeft })}
          </div>
          {status.recoveryCodesLeft <= 2 ? (
            <Alert type="warning" showIcon message={t('twoFactor.recoveryLow')} style={{ marginBottom: 8 }} />
          ) : null}
          <Button
            danger
            onClick={() => {
              setDisableOpen(true);
              setDisableError(null);
            }}
            disabled={status.forced}
            data-testid="twofa-disable"
          >
            {t('twoFactor.disable')}
          </Button>
          {status.forced ? (
            <span style={{ marginLeft: 8, fontSize: 12, color: '#d97706' }} data-testid="twofa-disable-blocked">
              {t('twoFactor.disable.forcedByAdmin')}
            </span>
          ) : null}
        </div>
      ) : null}

      {/* 恢复码弹窗（关闭后卡片刷新为已启用） */}
      {recoveryCodes ? (
        <RecoveryCodesModal codes={recoveryCodes} username={user?.username} onClose={() => setRecoveryCodes(null)} />
      ) : null}

      {/* 关闭弹窗：密码 + 动态码/恢复码 */}
      <Modal
        open={disableOpen}
        title={t('twoFactor.disable.title')}
        onCancel={() => setDisableOpen(false)}
        onOk={() => void submitDisable()}
        okText={t('twoFactor.disable.submit')}
        cancelText={t('twoFactor.cancel')}
        confirmLoading={disabling}
        okButtonProps={{ danger: true, 'data-testid': 'twofa-disable-submit' }}
        destroyOnHidden
      >
        <div data-testid="twofa-disable-modal">
          <Typography.Paragraph style={{ fontSize: 13 }}>{t('twoFactor.disable.desc')}</Typography.Paragraph>
          <div style={{ fontSize: 12, color: '#4b5563', marginBottom: 4 }}>{t('twoFactor.disable.password')}</div>
          <Input.Password
            value={disablePassword}
            onChange={(event) => setDisablePassword(event.target.value)}
            data-testid="twofa-disable-password"
            autoComplete="current-password"
          />
          <div style={{ fontSize: 12, color: '#4b5563', margin: '10px 0 4px' }}>{t('twoFactor.disable.code')}</div>
          <Input
            value={disableCode}
            onChange={(event) => setDisableCode(event.target.value)}
            placeholder={t('twoFactor.disable.codePlaceholder')}
            data-testid="twofa-disable-code"
          />
          {disableError ? (
            <Alert type="error" showIcon style={{ marginTop: 10 }} message={disableError} data-testid="twofa-disable-error" />
          ) : null}
        </div>
      </Modal>
    </Card>
  );
};

export default TwoFactorCard;
