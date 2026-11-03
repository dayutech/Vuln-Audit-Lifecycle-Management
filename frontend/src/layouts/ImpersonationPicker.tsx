import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Empty, Input, Modal, Space, Spin, Tag, Typography, message } from 'antd';
import { startImpersonation } from '@/api/impersonation';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT, type UserDirectoryEntry } from '@/api/user';
import { getRoleList, extractRoleList } from '@/api/role';
import { getUserList } from '@/api/user';
import { useT } from '@/i18n';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { beginImpersonation, readOriginalSession } from '@/utils/impersonation';

/**
 * 管理员模拟视角选择器（t91）。
 *
 * 数据源：`/users/directory`（t48 PII 收敛，任何登录角色可读）。
 * 但目录项只有 id/username/fullName，**没有角色**，而需求要过滤掉 ADMIN 用户：
 * 入口本身只对 ADMIN 开放（ADMIN 可读 `/users`），所以这里额外拉一次 `/users` 拿到
 * 角色信息来过滤；`/users` 不可用时退回目录（提交时后端仍会以 409 拒绝模拟 ADMIN）。
 */
interface ImpersonationPickerProps {
  open: boolean;
  onClose: () => void;
}

const ImpersonationPicker: React.FC<ImpersonationPickerProps> = ({ open, onClose }) => {
  const { t, lang } = useT();
  const [keyword, setKeyword] = useState('');
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [adminIds, setAdminIds] = useState<Set<string>>(new Set());
  const [selected, setSelected] = useState<UserDirectoryEntry | null>(null);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 拉一次用户列表（ADMIN 才能读），建立「管理员用户」集合用于过滤
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    (async () => {
      try {
        const [roleRes, userRes] = await Promise.all([getRoleList(), getUserList({ page: 1, page_size: 500 })]);
        const roles = extractRoleList(roleRes) as Array<{ id: string; code?: string }>;
        const adminRoleId = roles.find((role) => role.code === 'admin')?.id;
        if (!adminRoleId) return;
        const payload: any = (userRes as any)?.data ?? userRes;
        const items: any[] = payload?.content || payload?.items || [];
        const ids = new Set<string>();
        items.forEach((item) => {
          const roleIds: string[] = Array.isArray(item?.roleIds)
            ? item.roleIds
            : Array.isArray(item?.roles)
              ? item.roles.map((role: any) => (typeof role === 'string' ? role : role?.id))
              : [];
          const roleCodes: string[] = Array.isArray(item?.roleCodes) ? item.roleCodes : [];
          if (roleIds.includes(adminRoleId) || roleCodes.includes('admin')) ids.add(String(item.id));
        });
        if (!cancelled) setAdminIds(ids);
      } catch {
        // 读不到用户列表（个别部署 admin 也被 PII 收敛）→ 不过滤，交给后端 409
        if (!cancelled) setAdminIds(new Set());
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open]);

  // 目录搜索（300ms 防抖由输入事件触发，这里直接按关键字拉取）
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    getUserDirectory({ keyword: keyword.trim() || undefined, limit: USER_DIRECTORY_DEFAULT_LIMIT })
      .then((list) => {
        if (!cancelled) setUsers(list);
      })
      .catch((error) => {
        if (!cancelled) setLoadError(getApiErrorMessage(error));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, keyword]);

  const options = useMemo(
    () => users.filter((item) => !adminIds.has(String(item.id))),
    [users, adminIds],
  );

  const handleSubmit = async () => {
    if (!selected) {
      message.warning(t('impersonation.selectRequired'));
      return;
    }
    const original = readOriginalSession();
    const currentToken = (() => {
      try {
        return window.localStorage.getItem('access_token');
      } catch {
        return null;
      }
    })();
    if (!original && !currentToken) {
      message.error(t('impersonation.needRelogin'));
      return;
    }
    setSubmitting(true);
    try {
      const res = await startImpersonation(selected.id, reason.trim() || undefined);
      if (!res.token || !res.target) throw new Error('impersonate response incomplete');
      beginImpersonation({
        // 首次模拟时把当前 admin token 存为原始会话
        original: original ?? {
          token: currentToken as string,
          username: selected.username ? undefined : undefined,
        },
        token: res.token,
        target: { ...res.target, expiresAt: res.expiresAt },
      });
      message.success(t('impersonation.started', { username: res.target.username }));
      // 整页刷新最稳：清掉 admin 态缓存（画像、菜单、列表数据）
      window.location.reload();
    } catch (error) {
      const status = getApiErrorStatus(error);
      // 409（目标是 ADMIN）/404/400 一律原样展示后端文案
      message.error(getApiErrorMessage(error, t('impersonation.startFailed')));
      if (status === 409) message.warning(t('impersonation.targetIsAdmin'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={t('impersonation.pickerTitle')}
      open={open}
      onCancel={onClose}
      width={560}
      destroyOnClose
      footer={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="primary" loading={submitting} onClick={() => void handleSubmit()}>
            {t('impersonation.start')}
          </Button>
        </Space>
      }
    >
      <Alert type="info" showIcon style={{ marginBottom: 12 }} message={t('impersonation.pickerHint')} />
      <Input.Search
        allowClear
        placeholder={t('impersonation.searchPlaceholder')}
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        onSearch={(value) => setKeyword(value)}
        style={{ marginBottom: 12 }}
      />
      <div style={{ maxHeight: 280, overflow: 'auto' }}>
        {loading ? (
          <div style={{ padding: 24, textAlign: 'center' }}>
            <Spin />
          </div>
        ) : loadError ? (
          <Alert type="error" showIcon message={loadError} />
        ) : options.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('impersonation.noUsers')} />
        ) : (
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            {options.map((item) => (
              <Button
                key={item.id}
                block
                type={selected?.id === item.id ? 'primary' : 'default'}
                style={{ textAlign: 'left' }}
                onClick={() => setSelected(item)}
              >
                <Space>
                  <span>{item.fullName || item.username}</span>
                  <Typography.Text type="secondary">@{item.username}</Typography.Text>
                </Space>
              </Button>
            ))}
          </Space>
        )}
      </div>
      <div style={{ marginTop: 12 }}>
        <Typography.Text type="secondary">{t('impersonation.reasonLabel')}</Typography.Text>
        <Input.TextArea
          rows={2}
          maxLength={200}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          placeholder={t('impersonation.reasonPlaceholder')}
          style={{ marginTop: 4 }}
        />
      </div>
      <div style={{ marginTop: 8 }}>
        <Typography.Text type="secondary">
          {t('impersonation.currentLang', { lang: lang === 'en-US' ? 'English' : '中文' })}
        </Typography.Text>
        {selected ? (
          <Tag color="blue" style={{ marginLeft: 8 }}>
            {selected.fullName || selected.username}
          </Tag>
        ) : null}
      </div>
    </Modal>
  );
};

export default ImpersonationPicker;
