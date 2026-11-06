import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Modal, message } from 'antd';
import { CopyOutlined, ReloadOutlined } from '@ant-design/icons';
import { QRCodeSVG } from 'qrcode.react';
import { Link } from 'react-router-dom';
import { useT } from '@/i18n';
import { getSsoAuthorizeUrl } from '@/api/sso';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import './sso.css';

/**
 * 微信扫码登录模态（t138，需求 5）
 *
 * 桌面端（≥768px）点微信 logo 时打开：把 `GET /auth/sso/wechat/authorize-url` 返回的授权地址
 * 渲染成二维码，用户用手机微信扫码后在**手机端**完成授权。
 *
 * - 二维码内容就是 `authorize-url`（含一次性 `state`），**不塞任何额外敏感信息**；
 * - `state` 5 分钟有效（后端 `SsoStateStore`），因此模态内标注「二维码 5 分钟内有效」并提供「刷新」重新取地址；
 * - **已知边界（不做轮询）**：授权回调落在手机浏览器，桌面端无法自动感知，
 *   所以这里明确提示「授权完成后本页会自动继续；如长时间无响应请刷新本页」。
 *   真正的「桌面扫码 → 移动确认 → 桌面自动登录」需要后端提供 device code 轮询接口，属后续增强。
 * - 未配置（`configured=false`）：不显示二维码，改为「未配置 + 联系管理员 + 去配置」（去配置仅管理员可见）。
 * - 链接永远可复制（`复制链接`），便于无法扫码时在手机浏览器直接打开。
 */
export interface WechatQrModalProps {
  open: boolean;
  onClose: () => void;
  /** 该 provider 是否已配置凭据（false → 不显示二维码） */
  configured: boolean;
  /** 登录页主题（用于深色主题下的白色静区与文字配色） */
  themeTone?: 'light' | 'dark';
  /** 是否为管理员（决定「去配置」链接是否展示） */
  isAdmin?: boolean;
}

type Phase = 'loading' | 'ready' | 'error';

const WechatQrModal: React.FC<WechatQrModalProps> = ({ open, onClose, configured, themeTone = 'light', isAdmin = false }) => {
  const { t } = useT();
  const [phase, setPhase] = useState<Phase>('loading');
  const [authorizeUrl, setAuthorizeUrl] = useState('');
  const [errorText, setErrorText] = useState('');
  const [copied, setCopied] = useState(false);

  const loadAuthorizeUrl = useCallback(async () => {
    setPhase('loading');
    setErrorText('');
    try {
      const authorize = await getSsoAuthorizeUrl('wechat');
      if (!authorize.url) {
        setPhase('error');
        setErrorText(t('sso.wechatQr.errorUnavailable'));
        return;
      }
      setAuthorizeUrl(authorize.url);
      setPhase('ready');
    } catch (error) {
      const status = getApiErrorStatus(error);
      setPhase('error');
      setErrorText(
        status === 503 ? t('sso.wechatQr.unconfiguredTitle') : getApiErrorMessage(error, t('sso.wechatQr.errorUnavailable')),
      );
    }
  }, [t]);

  useEffect(() => {
    if (!open) return undefined;
    // 同步 setState 会被 react-hooks/set-state-in-effect 拦（级联渲染）：
    // 用 0ms 定时器推到微任务之后，语义不变（打开时才重置状态）
    const reset = window.setTimeout(() => {
      setCopied(false);
      if (!configured) {
        // 未配置：不请求、不出二维码
        setPhase('ready');
        setAuthorizeUrl('');
        return;
      }
      void loadAuthorizeUrl();
    }, 0);
    return () => window.clearTimeout(reset);
  }, [open, configured, loadAuthorizeUrl]);

  const copyLink = async () => {
    if (!authorizeUrl) return;
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(authorizeUrl);
      } else {
        // 非安全上下文（http）兜底
        const textarea = document.createElement('textarea');
        textarea.value = authorizeUrl;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      setCopied(true);
      message.success(t('sso.wechatQr.linkCopied'));
    } catch {
      message.error(t('sso.wechatQr.copyFailed'));
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      centered
      width={360}
      destroyOnHidden
      className={['sso-wechat-modal', `sso-wechat-modal--${themeTone}`].join(' ')}
      data-testid="sso-wechat-qr-modal"
      title={t('sso.wechatQr.title')}
    >
      <div className="sso-wechat" data-testid="sso-wechat-qr-body">
        {!configured ? (
          <Alert
            type="warning"
            showIcon
            message={t('sso.wechatQr.unconfiguredTitle')}
            description={t('sso.wechatQr.unconfiguredDesc')}
            data-testid="sso-wechat-unconfigured"
            action={
              isAdmin ? (
                <Link to="/settings" onClick={onClose} data-testid="sso-wechat-go-config">
                  {t('sso.wechatQr.goConfig')}
                </Link>
              ) : undefined
            }
          />
        ) : phase === 'error' ? (
          <Alert
            type="error"
            showIcon
            message={errorText || t('sso.wechatQr.errorUnavailable')}
            action={
              <Button size="small" icon={<ReloadOutlined />} onClick={() => void loadAuthorizeUrl()} data-testid="sso-wechat-retry">
                {t('sso.wechatQr.retry')}
              </Button>
            }
          />
        ) : (
          <>
            {/* 二维码：白底 + ≥12px 静区，保证深色主题下也能被相机识别 */}
            <div className="sso-wechat__frame" data-testid="sso-wechat-qr-code">
              {phase === 'ready' && authorizeUrl ? (
                <QRCodeSVG value={authorizeUrl} size={196} level="M" marginSize={2} bgColor="#ffffff" fgColor="#000000" />
              ) : (
                <div className="sso-wechat__placeholder">{t('sso.wechatQr.loading')}</div>
              )}
            </div>

            <p className="sso-wechat__hint" data-testid="sso-wechat-scan-hint">
              {t('sso.wechatQr.scanHint')}
            </p>
            <p className="sso-wechat__sub">{t('sso.wechatQr.mobileHint')}</p>
            <p className="sso-wechat__validity" data-testid="sso-wechat-validity">
              {t('sso.wechatQr.validity')}
            </p>

            <div className="sso-wechat__actions">
              <Button
                size="small"
                icon={<CopyOutlined />}
                onClick={() => void copyLink()}
                disabled={!authorizeUrl}
                data-testid="sso-wechat-copy-link"
              >
                {copied ? t('sso.wechatQr.linkCopied') : t('sso.wechatQr.copyLink')}
              </Button>
              <Button
                size="small"
                icon={<ReloadOutlined />}
                onClick={() => void loadAuthorizeUrl()}
                data-testid="sso-wechat-refresh"
              >
                {t('sso.wechatQr.refresh')}
              </Button>
            </div>

            <p className="sso-wechat__refresh-hint" data-testid="sso-wechat-refresh-hint">
              {t('sso.wechatQr.refreshHint')}
            </p>
          </>
        )}
      </div>
    </Modal>
  );
};

export default WechatQrModal;
