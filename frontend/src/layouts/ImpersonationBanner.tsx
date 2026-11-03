import React, { useEffect, useState } from 'react';
import { Alert, Button, Space, Tag, message } from 'antd';
import { stopImpersonation } from '@/api/impersonation';
import { useT } from '@/i18n';
import {
  endImpersonation,
  formatRemaining,
  impersonationRemainingMs,
  isImpersonationExpired,
  readImpersonationTarget,
  roleLabel,
} from '@/utils/impersonation';

/**
 * 模拟态横幅（t91）：固定在内容区顶部。
 *
 * - 显示被模拟用户（用户名 + 角色）与剩余有效期倒计时
 * - 「退出模拟」：调 stop（失败也继续）→ 恢复原 admin token → 清理键 → 刷新
 * - 倒计时归零（或目标 token 已过期）：自动回退并提示「模拟已结束」
 */
const ImpersonationBanner: React.FC = () => {
  const { t, lang } = useT();
  const [target] = useState(readImpersonationTarget);
  const [remaining, setRemaining] = useState<number | null>(() => impersonationRemainingMs(readImpersonationTarget()));
  const [exiting, setExiting] = useState(false);

  // 后台定时刷新剩余时间；过期即自动回退
  useEffect(() => {
    if (!target) return undefined;
    const timer = window.setInterval(() => {
      const ms = impersonationRemainingMs(target);
      setRemaining(ms);
      if (ms === null) return;
      if (ms <= 0 || isImpersonationExpired(target)) {
        window.clearInterval(timer);
        const original = endImpersonation();
        message.info(t('impersonation.expired'));
        if (original?.token) {
          window.location.reload();
        } else {
          window.location.href = '/login';
        }
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [target, t]);

  if (!target) return null;

  const handleExit = async () => {
    setExiting(true);
    try {
      await stopImpersonation();
    } catch {
      // 后端失败（例如 token 已过期）仍要恢复本地状态
    }
    // 恢复动作放在 finally 之外，避免在 finally 里 return（no-unsafe-finally）
    const original = endImpersonation();
    if (!original?.token) {
      message.warning(t('impersonation.needRelogin'));
      window.location.href = '/login';
      return;
    }
    message.success(t('impersonation.stopped'));
    window.location.reload();
  };

  const roles = (target.roles || []).map((role) => roleLabel(role, lang)).join('、');

  return (
    <Alert
      banner
      type="warning"
      showIcon
      style={{ marginBottom: 12 }}
      message={
        <Space size={12} wrap>
          <span>
            {t('impersonation.banner', {
              username: target.username,
              roles: roles || '-',
            })}
          </span>
          {remaining !== null && (
            <Tag color={remaining < 60_000 ? 'red' : 'orange'}>
              {t('impersonation.remaining', { time: formatRemaining(remaining) })}
            </Tag>
          )}
        </Space>
      }
      action={
        <Button size="small" danger loading={exiting} onClick={() => void handleExit()}>
          {t('impersonation.exit')}
        </Button>
      }
    />
  );
};

export default ImpersonationBanner;
