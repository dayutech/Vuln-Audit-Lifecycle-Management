import React from 'react';
import { Button, Space, Spin } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { useT } from '@/i18n';
import type { CustomerOptionsStatus } from '@/api/customer';

/**
 * 客户下拉的「非正常」内容区（t139 缺陷修复）。
 *
 * 历史缺陷：`catch(() => setCustomers([]))` 把 403/401/5xx/网络错误全吞成空数组，
 * 下拉展开后一片空白 —— 用户无法区分「没有客户」「没权限」「服务出错」。
 *
 * 现在按状态给出可执行的指引：
 * - `empty`        → 「暂无客户，请先在客户管理创建」+ 直达 `/customers?new=1`
 * - `forbidden`    → 无权限读取客户（含两步验证未完成的业务 403）→ 去账号安全设置 / 重试
 * - `unauthorized` → 会话已过期 → 去登录
 * - `error`        → 服务异常 / 网络 → 重试
 * - `loading`      → 加载中
 */
export interface CustomerSelectEmptyProps {
  status: CustomerOptionsStatus | 'loading';
  detail?: string;
  onRetry?: () => void;
  /** 紧凑模式（下拉内使用，行高更小） */
  compact?: boolean;
}

const CustomerSelectEmpty: React.FC<CustomerSelectEmptyProps> = ({ status, detail, onRetry, compact = false }) => {
  const { t } = useT();
  const style: React.CSSProperties = { padding: compact ? '10px 12px' : 16 };

  if (status === 'loading') {
    return (
      <div style={style} data-testid="customer-options-loading">
        <Spin size="small" /> <span style={{ marginInlineStart: 8, fontSize: 12.5 }}>{t('customerSelect.loading')}</span>
      </div>
    );
  }

  if (status === 'empty') {
    return (
      <div style={style} data-testid="customer-options-empty">
        <Space direction="vertical" size={4} style={{ width: '100%', textAlign: 'center' }}>
          <InboxOutlined style={{ fontSize: 18, color: '#bfbfbf' }} />
          <span style={{ fontSize: 12.5 }}>{t('customerSelect.empty')}</span>
          <Link to="/customers?new=1" data-testid="customer-options-create-link">
            {t('customerSelect.emptyAction')}
          </Link>
        </Space>
      </div>
    );
  }

  const message =
    status === 'forbidden'
      ? t('customerSelect.forbidden')
      : status === 'unauthorized'
        ? t('customerSelect.unauthorized')
        : t('customerSelect.error');

  return (
    <div style={style} data-testid={`customer-options-${status}`}>
      <Space direction="vertical" size={6} style={{ width: '100%' }}>
        <span style={{ fontSize: 12.5, color: '#cf1322' }}>{message}</span>
        {detail ? (
          <span style={{ fontSize: 11.5, color: '#6b7280', wordBreak: 'break-all' }}>{detail}</span>
        ) : null}
        <Space size={8} wrap>
          {status === 'forbidden' ? (
            <Link to="/account/security" data-testid="customer-options-security-link">
              {t('customerSelect.securityAction')}
            </Link>
          ) : null}
          {status === 'unauthorized' ? (
            <Link to="/login" data-testid="customer-options-login-link">
              {t('customerSelect.loginAction')}
            </Link>
          ) : null}
          {onRetry ? (
            <Button size="small" onClick={onRetry} data-testid="customer-options-retry">
              {t('customerSelect.retry')}
            </Button>
          ) : null}
        </Space>
      </Space>
    </div>
  );
};

export default CustomerSelectEmpty;

