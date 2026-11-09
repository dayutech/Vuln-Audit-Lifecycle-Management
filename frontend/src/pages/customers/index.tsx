import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Input, Modal, Select, Space, Switch, Table, Tag, message } from 'antd';
import { BankOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  changeCustomerStatus,
  deleteCustomer,
  extractCustomerPage,
  getCustomerList,
} from '@/api/customer';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import type { CustomerQueryParams, CustomerVO } from '@/types/customer';
import { useT } from '@/i18n';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import SoftDeletedTable from '@/components/softDelete/SoftDeletedTable';
import { isManagementProfile } from '@/utils/accessRoles';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { AppEmpty } from '@/components/Feedback';

/** 客户级别 → Tag 配色（A/B/C；后端是自由字符串，未知级别走 default） */
const LEVEL_COLORS: Record<string, string> = { A: 'gold', B: 'blue', C: 'default' };
const LEVEL_OPTIONS = ['A', 'B', 'C'];

/**
 * 客户列表（t71；t142 起「新建/编辑/详情」全部改为**独立页面**）。
 *
 * 变化点（t142）：
 * - 原来的右侧 Drawer（新建/编辑）与详情 Drawer 全部移除，改为路由跳转：
 *   `/customers/new`、`/customers/:id`、`/customers/:id/edit`；
 * - 兼容旧入口：`/customers?new=1` 自动重定向到 `/customers/new`（t139 的「去创建客户」依赖它），
 *   并给一条 `message.info` 说明；
 * - 列表本身仍保留删除、启停用（二次确认弹窗，属于允许的「确认类弹窗」）。
 */
const Customers: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { profile, loaded: profileLoaded } = useAccessProfile();
  /** 客户写权：后端 @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")，团队长不在其中 */
  const canWrite = profileLoaded && isManagementProfile(profile);
  /** t143：含已删列表与恢复仅 ADMIN（后端 /admin/customers 只放行 ADMIN） */
  const isAdmin = profileLoaded && Boolean(profile?.roles?.includes('admin'));
  const [showDeleted, setShowDeleted] = useState(false);

  const [data, setData] = useState<CustomerVO[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [params, setParams] = useState<CustomerQueryParams>({
    page: 1,
    page_size: 10,
    keyword: '',
    status: '',
    level: '',
  });
  const [searchText, setSearchText] = useState('');
  const [pageError, setPageError] = useState<string | null>(null);
  const [owners, setOwners] = useState<UserDirectoryEntry[]>([]);
  const legacyRedirectDone = useRef(false);

  /** 兼容旧 query 入口：`/customers?new=1` → `/customers/new`（t139 的链接依赖） */
  useEffect(() => {
    if (legacyRedirectDone.current) return;
    if (searchParams.get('new') !== '1') return;
    legacyRedirectDone.current = true;
    message.info(t('customerPage.legacyNewRedirect'));
    const next = new URLSearchParams(searchParams);
    next.delete('new');
    setSearchParams(next, { replace: true });
    navigate('/customers/new', { replace: true });
  }, [searchParams, setSearchParams, navigate, t]);

  const ownerName = useCallback(
    (ownerId?: string | null) => {
      if (!ownerId) return '-';
      const hit = owners.find((item) => item.id === ownerId);
      return hit ? hit.fullName || hit.username : ownerId.slice(0, 8);
    },
    [owners],
  );

  const fetchData = useCallback(async () => {
    setLoading(true);
    setPageError(null);
    try {
      const res = await getCustomerList(params);
      const { items, total: count } = extractCustomerPage(res);
      setData(items);
      setTotal(count);
    } catch (error) {
      setData([]);
      setTotal(0);
      const status = getApiErrorStatus(error);
      setPageError(status === 403 ? t('customer.forbidden') : getApiErrorMessage(error, t('common.operationFailed')));
    } finally {
      setLoading(false);
    }
  }, [params, t]);

  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  // 负责人姓名回显：走 /users/directory（t48 PII 收敛）
  useEffect(() => {
    let cancelled = false;
    getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT })
      .then((list) => {
        if (!cancelled) setOwners(list);
      })
      .catch(() => {
        if (!cancelled) setOwners([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleToggleStatus = (record: CustomerVO) => {
    const next = record.status === 'active' ? 'inactive' : 'active';
    Modal.confirm({
      title: next === 'inactive' ? t('customer.statusInactive') : t('customer.statusActive'),
      content: next === 'inactive' ? t('customer.disableHint') : record.name,
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await changeCustomerStatus(record.id, next);
          message.success(t('customer.statusSuccess'));
          await fetchData();
        } catch (error) {
          message.error(getApiErrorMessage(error, t('common.operationFailed')));
        }
      },
    });
  };

  const handleDelete = (record: CustomerVO) => {
    Modal.confirm({
      title: t('customer.deleteTitle'),
      content: (
        <div>
          <p style={{ marginBottom: 8 }}>{t('customer.deleteContent')}</p>
          <span style={{ color: 'rgba(0,0,0,0.45)' }}>{t('customer.deleteBlockedHint')}</span>
        </div>
      ),
      okText: t('common.delete'),
      okButtonProps: { danger: true },
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await deleteCustomer(record.id);
          message.success(t('customer.deleteSuccess'));
          await fetchData();
        } catch (error) {
          const status = getApiErrorStatus(error);
          if (status === 409) {
            message.error(getApiErrorMessage(error, t('customer.deleteBlockedHint')));
          } else if (status === 403) {
            message.error(t('customer.forbidden'));
          } else {
            message.error(getApiErrorMessage(error, t('common.operationFailed')));
          }
        }
      },
    });
  };

  const columns = useMemo(
    () => [
      { title: t('customer.colCode'), dataIndex: 'code', key: 'code', width: 150 },
      {
        title: t('customer.colName'),
        dataIndex: 'name',
        key: 'name',
        render: (value: string, record: CustomerVO) => (
          <a onClick={() => navigate(`/customers/${record.id}`)} data-testid={`customer-open-${record.id}`}>
            {value}
          </a>
        ),
      },
      { title: t('customer.colIndustry'), dataIndex: 'industry', key: 'industry', render: (v?: string) => v || '-' },
      {
        title: t('customer.colLevel'),
        dataIndex: 'level',
        key: 'level',
        width: 90,
        render: (value?: string) => (value ? <Tag color={LEVEL_COLORS[value] ?? 'default'}>{value}</Tag> : '-'),
      },
      { title: t('customer.colOwner'), dataIndex: 'ownerId', key: 'ownerId', render: (value?: string) => ownerName(value) },
      {
        title: t('customer.colStatus'),
        dataIndex: 'status',
        key: 'status',
        width: 100,
        render: (value?: string) => (
          <Tag color={value === 'active' ? 'green' : 'default'}>
            {value === 'active' ? t('customer.statusActive') : t('customer.statusInactive')}
          </Tag>
        ),
      },
      {
        title: t('customer.colAction'),
        key: 'action',
        width: 220,
        render: (_: unknown, record: CustomerVO) => (
          <Space size="small">
            <a onClick={() => navigate(`/customers/${record.id}`)}>{t('customer.detail')}</a>
            {canWrite && <a onClick={() => navigate(`/customers/${record.id}/edit`)}>{t('common.edit')}</a>}
            {canWrite && (
              <a onClick={() => handleToggleStatus(record)}>
                {record.status === 'active' ? t('customer.statusInactive') : t('customer.statusActive')}
              </a>
            )}
            {canWrite && (
              <a style={{ color: 'red' }} onClick={() => handleDelete(record)}>
                {t('common.delete')}
              </a>
            )}
          </Space>
        ),
      },
    ],
    // handleToggleStatus / handleDelete 每次渲染重建，但只依赖稳定 setter；以 t/canWrite/ownerName 作为刷新依据
    [t, canWrite, ownerName, navigate],
  );

  return (
    <Card
      title={
        <Space>
          <BankOutlined />
          {t('customer.title')}
        </Space>
      }
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void fetchData()}>
            {t('common.refresh')}
          </Button>
          {canWrite ? (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => navigate('/customers/new')}
              data-testid="customer-new-button"
            >
              {t('customer.new')}
            </Button>
          ) : null}
        </Space>
      }
      data-testid="customer-list-page"
    >
      {profileLoaded && !canWrite ? (
        <Alert type="info" showIcon style={{ marginBottom: 16 }} message={t('customer.readonlyHint')} />
      ) : null}

      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          allowClear
          prefix={<SearchOutlined />}
          style={{ width: 280 }}
          placeholder={t('customer.searchPlaceholder')}
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          onPressEnter={() => setParams((prev) => ({ ...prev, keyword: searchText.trim(), page: 1 }))}
        />
        <Select
          allowClear
          style={{ width: 140 }}
          placeholder={t('customer.statusAll')}
          value={params.status || undefined}
          onChange={(value) => setParams((prev) => ({ ...prev, status: value ?? '', page: 1 }))}
          options={[
            { value: 'active', label: t('customer.statusActive') },
            { value: 'inactive', label: t('customer.statusInactive') },
          ]}
        />
        <Select
          allowClear
          style={{ width: 140 }}
          placeholder={t('customer.levelAll')}
          value={params.level || undefined}
          onChange={(value) => setParams((prev) => ({ ...prev, level: value ?? '', page: 1 }))}
          options={LEVEL_OPTIONS.map((item) => ({ value: item, label: item }))}
        />
        {/* t143：含已删（仅 ADMIN）*/}
        {isAdmin ? (
          <Space size={6}>
            <Switch
              size="small"
              checked={showDeleted}
              onChange={(checked) => setShowDeleted(checked)}
              data-testid="customer-show-deleted-switch"
            />
            <span style={{ fontSize: 12, color: '#6b7280' }}>{t('softDelete.showDeleted')}</span>
          </Space>
        ) : null}
      </Space>

      {pageError ? <Alert type="error" showIcon style={{ marginBottom: 16 }} message={pageError} /> : null}

      {showDeleted && isAdmin ? (
        <SoftDeletedTable kind="customers" keyword={params.keyword} onRestored={() => void fetchData()} />
      ) : (
      <Table
        rowKey="id"
        loading={loading}
        dataSource={data}
        columns={columns}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: <AppEmpty description={t('customer.empty')} /> }}
        pagination={{
          current: params.page,
          pageSize: params.page_size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) => setParams((prev) => ({ ...prev, page, page_size: pageSize })),
        }}
      />
      )}
    </Card>
  );
};

export default Customers;


