import React, { useMemo, useState } from 'react';
import { Button, Checkbox, Modal, Typography, message } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { buildRecoveryCodesText, downloadRecoveryCodes } from '@/api/twoFactor';
import { useT } from '@/i18n';

/**
 * 恢复码弹窗（t137）。
 *
 * 强制约定：**必须勾选「我已妥善保存」才能关闭** —— 恢复码只在 confirm 响应里出现一次，
 * 关了就拿不回来（后端只存哈希），所以这里不给「先关掉待会儿再说」的机会。
 *
 * 提供三种落地方式：复制全部（剪贴板）、下载 txt（纯前端 Blob）、肉眼抄写（等宽字体逐行）。
 */
export interface RecoveryCodesModalProps {
  codes: string[];
  username?: string;
  onClose: () => void;
}

const RecoveryCodesModal: React.FC<RecoveryCodesModalProps> = ({ codes, username, onClose }) => {
  const { t } = useT();
  const [ack, setAck] = useState(false);
  const [copying, setCopying] = useState(false);

  const text = useMemo(() => codes.join('\n'), [codes]);

  const copyAll = async () => {
    try {
      setCopying(true);
      const payload = buildRecoveryCodesText(codes, username);
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(payload);
      } else {
        const el = document.createElement('textarea');
        el.value = payload;
        el.style.position = 'fixed';
        el.style.opacity = '0';
        document.body.appendChild(el);
        el.select();
        document.execCommand('copy');
        document.body.removeChild(el);
      }
      message.success(t('twoFactor.recovery.copied'));
    } catch {
      message.warning(t('twoFactor.recovery.ackRequired'));
    } finally {
      setCopying(false);
    }
  };

  return (
    <Modal
      open
      title={t('twoFactor.recovery.title')}
      closable={false}
      maskClosable={false}
      keyboard={false}
      footer={
        <Button type="primary" disabled={!ack} onClick={onClose} data-testid="twofa-recovery-close">
          {t('twoFactor.recovery.close')}
        </Button>
      }
    >
      <div data-testid="twofa-recovery-modal">
        <Typography.Paragraph style={{ marginBottom: 10 }}>{t('twoFactor.recovery.desc')}</Typography.Paragraph>
        <div
          data-testid="twofa-recovery-list"
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
            gap: '6px 16px',
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
            fontSize: 14,
            background: '#fafafa',
            border: '1px solid #f0f0f0',
            borderRadius: 8,
            padding: 12,
            marginBottom: 12,
          }}
        >
          {codes.map((code) => (
            <span key={code} style={{ letterSpacing: 1 }}>
              {code}
            </span>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 8, marginBottom: 14, flexWrap: 'wrap' }}>
          <Button size="small" onClick={() => void copyAll()} loading={copying} data-testid="twofa-recovery-copy">
            {t('twoFactor.recovery.copy')}
          </Button>
          <Button
            size="small"
            icon={<DownloadOutlined />}
            onClick={() => downloadRecoveryCodes(codes, 'two-factor-recovery-codes.txt', username)}
            data-testid="twofa-recovery-download"
          >
            {t('twoFactor.recovery.download')}
          </Button>
        </div>
        <Checkbox checked={ack} onChange={(event) => setAck(event.target.checked)} data-testid="twofa-recovery-ack">
          {t('twoFactor.recovery.ack')}
        </Checkbox>
        {/* 供测试与读屏使用的隐藏文本（避免依赖视觉抓取） */}
        <span data-testid="twofa-recovery-raw" style={{ display: 'none' }}>
          {text}
        </span>
      </div>
    </Modal>
  );
};

export default RecoveryCodesModal;
