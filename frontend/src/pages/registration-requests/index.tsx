import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Empty,
  Input,
  Popconfirm,
  Result,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { CheckOutlined, CloseOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  filterRegistrationRequests,
  getApiErrorMessage,
  listRegistrationRequests,
  maskEmail,
  maskPhone,
  purgeRegistration,
  REGISTRATION_REVIEW_CHANGED_EVENT,
  REGISTRATION_STATUS_META,
  type RegistrationRequestVO,
  type RegistrationStatus,
} from '@/api/registrationReview';
import { getMyAccessProfile } from '@/api/permission';
import { getUserDirectory } from '@/api/user';
import { getApiErrorStatus } from '@/utils/errorMessage';
import { useT, type MessageKey } from '@/i18n';
import ApproveRegistrationModal from './ApproveRegistrationModal';
import RejectRegistrationModal from './RejectRegistrationModal';

/**
 * 注册审批页（t114）。
 *
 * 权限：ADMIN / PROJECT_LEADER（与后端 `hasAnyRole('ADMIN','PROJECT_LEADER')` 一致）。
 * - 菜单入口由 `BasicLayout` 按 `managerEligible` 收敛；
 * - 直接访问本路由时页面自己再判一次 → 非管理员/项目负责人看到 403 视图（不入表格）。
 *
 * 数据：`GET /admin/registration-requests?status=&page=0&size=20`。
 * 关键词过滤只能前端做（后端无 keyword 参数）—— 页面上有一行说明，避免误导。
 *
 * 审批口径（t111）：角色**默认空**，由审批人显式勾选；不勾选即"建号但不授角色"。
 * 拒绝后会释放用户名/手机号唯一键，所以同一手机号可再次申请（列表刷新后可见新申请）。
 *
 * 变更后（审批/拒绝/删除）都会派发 `registration-review:changed` 事件，
 * 让侧边栏角标立即刷新，不用等整页刷新。
 */
const PAGE_SIZE = 20;

const RegistrationRequests: React.FC = () => {
  const { t } = useT();
  /** null = 画像未返回；false = 无权限 */
  const [allowed, setAllowed] = useState<boolean | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [rows, setRows] = useState<RegistrationRequestVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState<RegistrationStatus>('PENDING');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  /** 「显示完整」toggle：仅管理员可见可用 */
  const [showFull, setShowFull] = useState(false);
  /** reviewerId → username（来自 /users/directory） */
  const [userNames, setUserNames] = useState<Record<string, string>>({});

  const [approveTarget, setApproveTarget] = useState<RegistrationRequestVO | null>(null);
  const [rejectTarget, setRejectTarget] = useState<RegistrationRequestVO | null>(null);
  const [purgingId, setPurgingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listRegistrationRequests({ status, page, size: PAGE_SIZE });
      setRows(result.content);
      setTotal(result.totalElements);
      setLoadError(null);
    } catch (error) {
      setRows([]);
      setTotal(0);
      setLoadError(getApiErrorMessage(error));
      if (getApiErrorStatus(error) === 403) setAllowed(false);
    } finally {
      setLoading(false);
    }
  }, [status, page]);

  const notifyChanged = useCallback(() => {
    window.dispatchEvent(new CustomEvent(REGISTRATION_REVIEW_CHANGED_EVENT));
  }, []);

  useEffect(() => {
    let cancelled = false;
    getMyAccessProfile()
      .then((profile) => {
        if (cancelled) return;
        const admin = profile.roles.includes('admin');
        const projectLeader = profile.roles.includes('project_leader');
        setIsAdmin(admin);
        setAllowed(admin || projectLeader);
      })
      .catch(() => {
        if (!cancelled) setAllowed(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (allowed) load();
  }, [allowed, load]);

  // 审批人用户名（reviewerId 只是 UUID）：目录接口登录即可读，失败则静默显示占位
  useEffect(() => {
    let cancelled = false;
    getUserDirectory({ limit: 500 })
      .then((directory) => {
        if (cancelled) return;
        const map: Record<string, string> = {};
        directory.forEach((entry) => {
          map[entry.id] = entry.fullName || entry.username;
        });
        setUserNames(map);
      })
      .catch(() => {
        /* 静默：显示 reviewerId 兜底 */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const visibleRows = useMemo(() => filterRegistrationRequests(rows, keyword), [rows, keyword]);

  const handlePurge = async (id: string) => {
    setPurgingId(id);
    try {
      await purgeRegistration(id);
      message.success(t('registrationReview.purge.success'));
      notifyChanged();
      await load();
    } catch (error) {
      const s = getApiErrorStatus(error);
      if (s === 409) message.warning(t('registrationReview.purge.err409'));
      else if (s === 403) message.error(t('registrationReview.purge.err403'));
      else message.error(t('registrationReview.purge.errGeneric', { message: getApiErrorMessage(error) }));
    } finally {
      setPurgingId(null);
    }
  };

  if (allowed === null) {
    return <Card loading data-testid="registration-review-loading" />;
  }

  if (allowed === false) {
    return (
      <div data-testid="registration-review-forbidden">
        <Result status="403" title={t('registrationReview.forbidden.title')} subTitle={t('registrationReview.forbidden.desc')} />
      </div>
    );
  }

  const statusTag = (value: string) => {
    const meta = REGISTRATION_STATUS_META[value];
    return (
      <Tag color={meta?.color ?? 'default'} data-testid={`registration-status-${value}`}>
        {meta ? t(meta.labelKey as MessageKey) : t('registrationReview.status.unknown')}
      </Tag>
    );
  };

  const sourceLabel = (row: RegistrationRequestVO) => {
    if (row.source === 'SSO') {
      const providerName = row.providerCode ? t(`registrationReview.provider.${row.providerCode}` as MessageKey) : row.providerCode;
      return t('registrationReview.source.sso', { provider: providerName ?? '-' });
    }
    return t('registrationReview.source.local');
  };

  const columns = [
    {
      title: t('registrationReview.col.createdAt'),
      dataIndex: 'createdAt',
      width: 170,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
    {
      title: t('registrationReview.col.username'),
      dataIndex: 'username',
      render: (value: string) => <span data-testid="registration-username">{value}</span>,
    },
    {
      title: t('registrationReview.col.phone'),
      dataIndex: 'phone',
      render: (value: string) => (
        <Tooltip title={isAdmin ? undefined : t('registrationReview.fullValueAdminOnly')}>
          <span data-testid="registration-phone">{showFull && isAdmin ? value || '—' : maskPhone(value) || '—'}</span>
        </Tooltip>
      ),
    },
    {
      title: t('registrationReview.col.email'),
      dataIndex: 'email',
      render: (value: string) => (
        <span data-testid="registration-email">{value ? (showFull && isAdmin ? value : maskEmail(value)) : '—'}</span>
      ),
    },
    {
      title: t('registrationReview.col.source'),
      dataIndex: 'source',
      render: (_: unknown, row: RegistrationRequestVO) => <span style={{ fontSize: 12 }}>{sourceLabel(row)}</span>,
    },
    {
      title: t('registrationReview.col.status'),
      dataIndex: 'status',
      width: 110,
      render: (value: string) => statusTag(value),
    },
    {
      title: t('registrationReview.col.reviewer'),
      dataIndex: 'reviewerId',
      render: (value: string | null) =>
        value ? userNames[value] ?? <span style={{ color: '#9ca3af' }}>{value.slice(0, 8)}…</span> : '—',
    },
    {
      title: t('registrationReview.col.reviewedAt'),
      dataIndex: 'reviewedAt',
      width: 170,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
    {
      title: t('registrationReview.col.actions'),
      key: 'actions',
      width: 240,
      render: (_: unknown, row: RegistrationRequestVO) => {
        const pending = row.status === 'PENDING';
        return (
          <Space size={4} wrap>
            {pending ? (
              <>
                <Button
                  size="small"
                  type="primary"
                  icon={<CheckOutlined />}
                  onClick={() => setApproveTarget(row)}
                  data-testid={`registration-approve-${row.id}`}
                >
                  {t('registrationReview.action.approve')}
                </Button>
                <Button size="small" danger icon={<CloseOutlined />} onClick={() => setRejectTarget(row)} data-testid={`registration-reject-${row.id}`}>
                  {t('registrationReview.action.reject')}
                </Button>
              </>
            ) : null}
            {isAdmin && row.status !== 'APPROVED' ? (
              <Popconfirm
                title={t('registrationReview.purge.title')}
                description={t('registrationReview.purge.content')}
                okText={t('registrationReview.purge.ok')}
                cancelText={t('preferences.close')}
                okButtonProps={{ danger: true, 'data-testid': `registration-purge-confirm-${row.id}` } as any}
                onConfirm={() => handlePurge(row.id)}
              >
                <Button
                  size="small"
                  icon={<DeleteOutlined />}
                  loading={purgingId === row.id}
                  data-testid={`registration-purge-${row.id}`}
                >
                  {t('registrationReview.action.purge')}
                </Button>
              </Popconfirm>
            ) : null}
          </Space>
        );
      },
    },
  ];

  return (
    <div data-testid="registration-review-page">
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12, marginBottom: 12 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 20, fontWeight: 700 }}>{t('registrationReview.title')}</h2>
          <div style={{ marginTop: 4, fontSize: 13, color: '#6b7280' }}>{t('registrationReview.subtitle')}</div>
        </div>
        <Space wrap>
          {isAdmin ? (
            <Button
              size="small"
              onClick={() => setShowFull((value) => !value)}
              data-testid="registration-toggle-full"
              data-active={showFull ? 'true' : 'false'}
            >
              {showFull ? t('registrationReview.hideFull') : t('registrationReview.showFull')}
            </Button>
          ) : null}
          <Button size="small" icon={<ReloadOutlined />} onClick={load} loading={loading} data-testid="registration-refresh">
            {t('registrationReview.refresh')}
          </Button>
        </Space>
      </div>

      <Card size="small">
        <Space wrap style={{ marginBottom: 12 }}>
          <span style={{ fontSize: 13, color: '#6b7280' }}>{t('registrationReview.filter.status')}</span>
          <Select
            value={status}
            style={{ width: 160 }}
            onChange={(value: RegistrationStatus | '') => {
              setPage(1);
              setStatus((value || 'PENDING') as RegistrationStatus);
            }}
            options={[
              { value: 'PENDING', label: t('registrationReview.status.pending') },
              { value: 'APPROVED', label: t('registrationReview.status.approved') },
              { value: 'REJECTED', label: t('registrationReview.status.rejected') },
              { value: '', label: t('registrationReview.filter.statusAll') },
            ]}
            data-testid="registration-status-filter"
          />
          <Input
            allowClear
            style={{ width: 260 }}
            placeholder={t('registrationReview.filter.keywordPlaceholder')}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            data-testid="registration-keyword"
          />
          {keyword ? <Badge color="blue" text={`${visibleRows.length}/${rows.length}`} /> : null}
        </Space>
        {keyword ? (
          <Alert type="info" showIcon style={{ marginBottom: 10 }} message={t('registrationReview.keywordHint')} data-testid="registration-keyword-hint" />
        ) : null}
        {loadError ? (
          <Alert type="error" showIcon style={{ marginBottom: 10 }} message={t('registrationReview.loadFailed')} description={loadError} data-testid="registration-load-error" />
        ) : null}

        <Table<RegistrationRequestVO>
          rowKey="id"
          size="middle"
          loading={loading}
          dataSource={visibleRows}
          columns={columns as any}
          locale={{
            emptyText: (
              <Empty description={<span><div>{t('registrationReview.empty.title')}</div><div style={{ fontSize: 12, color: '#9ca3af' }}>{t('registrationReview.empty.desc')}</div></span>} />
            ),
          }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            onChange: (next) => setPage(next),
            showTotal: (count) => t('common.total', { count }),
          }}
          data-testid="registration-table"
        />
      </Card>

      <ApproveRegistrationModal
        request={approveTarget}
        viewerIsAdmin={isAdmin}
        onClose={() => setApproveTarget(null)}
        onSucceeded={() => {
          setApproveTarget(null);
          notifyChanged();
          load();
        }}
      />
      <RejectRegistrationModal
        request={rejectTarget}
        onClose={() => setRejectTarget(null)}
        onSucceeded={() => {
          setRejectTarget(null);
          notifyChanged();
          load();
        }}
      />
    </div>
  );
};

export default RegistrationRequests;
