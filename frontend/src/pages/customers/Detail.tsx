import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Descriptions, Modal, Space, Table, Tag, Typography, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useT } from '@/i18n';
import PageHeader from '@/components/layout/PageHeader';
import { AppEmpty, AppLoading } from '@/components/Feedback';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { isManagementProfile } from '@/utils/accessRoles';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { formatDateTime } from '@/utils/datetime';
import {
  changeCustomerStatus,
  deleteCustomer,
  extractCustomerAssetPage,
  extractSummary,
  getCustomerAssets,
  getCustomerSummary,
} from '@/api/customer';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import type { CustomerAssetVO, CustomerSummaryVO } from '@/types/customer';

/**
 * 客户详情（t142）：由原来的右侧 Drawer 改为**独立整页**。
 *
 * 结构（与项目详情页统一）：PageHeader（← 返回 + 客户名 + 编码 + 右侧操作）
 * → 基本信息 → 统计 → 项目列表 → 资产列表（分页）。
 *
 * 交互保留：编辑 / 删除 / 启停用（按 `isManagementProfile` 显隐，等价于后端
 * `hasAnyRole('ADMIN','PROJECT_LEADER')`）；删除与启停用仍走二次确认弹窗（允许）。
 * 数据仍走 t77 的一次 `GET /customers/{id}/summary` + 一次资产分页，不逐项目请求。
 */
const CustomerDetail: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { profile, loaded: profileLoaded } = useAccessProfile();
  const canWrite = profileLoaded && isManagementProfile(profile);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [summary, setSummary] = useState<CustomerSummaryVO | null>(null);

  const [owners, setOwners] = useState<UserDirectoryEntry[]>([]);
  const [assetRows, setAssetRows] = useState<CustomerAssetVO[]>([]);
  const [assetTotal, setAssetTotal] = useState(0);
  const [assetLoading, setAssetLoading] = useState(false);
  const [assetPage, setAssetPage] = useState({ page: 1, page_size: 10 });

  const ownerName = useCallback(
    (ownerId?: string | null) => {
      if (!ownerId) return '-';
      const hit = owners.find((item) => item.id === ownerId);
      return hit ? hit.fullName || hit.username : ownerId.slice(0, 8);
    },
    [owners],
  );

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

  const loadSummary = useCallback(
    async (withSpinner = true) => {
      if (!id) return;
      if (withSpinner) setLoading(true);
      setError(null);
      try {
        const parsed = extractSummary(await getCustomerSummary(id));
        setSummary(parsed);
        if (!parsed) setError(t('customerPage.loadFailed'));
      } catch (err) {
        const status = getApiErrorStatus(err);
        setError(
          status === 404
            ? t('customerPage.notFound')
            : status === 403
              ? t('customer.forbidden')
              : getApiErrorMessage(err, t('customerPage.loadFailed')),
        );
      } finally {
        if (withSpinner) setLoading(false);
      }
    },
    [id, t],
  );

  const loadAssets = useCallback(async (page = 1, pageSize = 10) => {
    if (!id) return;
    setAssetLoading(true);
    try {
      const res = await getCustomerAssets(id, { page, page_size: pageSize });
      const { items, total } = extractCustomerAssetPage(res);
      setAssetRows(items);
      setAssetTotal(total);
    } catch (err) {
      setAssetRows([]);
      setAssetTotal(0);
      if (getApiErrorStatus(err) !== 403) message.error(getApiErrorMessage(err));
    } finally {
      setAssetLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (!id) return;
    setAssetPage({ page: 1, page_size: 10 });
    void loadSummary();
    void loadAssets(1, 10);
  }, [id, loadSummary, loadAssets]);

  /** 从项目页/资产页返回本页时自动刷新（原来 Drawer 的 window.focus 刷新语义保留） */
  useEffect(() => {
    if (!id) return undefined;
    const onFocus = () => {
      void loadSummary(false);
      void loadAssets(assetPage.page, assetPage.page_size);
    };
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [id, assetPage.page, assetPage.page_size, loadSummary, loadAssets]);

  const handleToggleStatus = () => {
    if (!summary) return;
    const record = summary.customer;
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
          await loadSummary(false);
        } catch (err) {
          message.error(getApiErrorMessage(err, t('common.operationFailed')));
        }
      },
    });
  };

  const handleDelete = () => {
    if (!summary) return;
    const record = summary.customer;
    Modal.confirm({
      title: t('customer.deleteTitle'),
      content: (
        <div>
          <p style={{ marginBottom: 8 }}>{t('customer.deleteContent')}</p>
          <Typography.Text type="secondary">{t('customer.deleteBlockedHint')}</Typography.Text>
        </div>
      ),
      okText: t('common.delete'),
      okButtonProps: { danger: true },
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await deleteCustomer(record.id);
          message.success(t('customer.deleteSuccess'));
          navigate('/customers', { replace: true });
        } catch (err) {
          const status = getApiErrorStatus(err);
          if (status === 409) {
            message.error(getApiErrorMessage(err, t('customer.deleteBlockedHint')));
          } else if (status === 403) {
            message.error(t('customer.forbidden'));
          } else {
            message.error(getApiErrorMessage(err, t('common.operationFailed')));
          }
        }
      },
    });
  };

  const projectColumns = useMemo(
    () => [
      {
        title: t('customer.colProjectName'),
        dataIndex: 'name',
        key: 'name',
        render: (value: string, record: { id: string }) => <Link to={`/projects/${record.id}`}>{value}</Link>,
      },
      { title: t('customer.colProjectType'), dataIndex: 'type', key: 'type', width: 110, render: (v?: string) => v || '-' },
      {
        title: t('customer.colProjectStatus'),
        dataIndex: 'status',
        key: 'status',
        width: 110,
        render: (value?: string) => {
          const map: Record<string, string> = {
            DRAFT: t('customer.projectStatusDraft'),
            ACTIVE: t('customer.projectStatusActive'),
            PAUSED: t('customer.projectStatusPaused'),
            CLOSED: t('customer.projectStatusClosed'),
          };
          return value ? <Tag>{map[value.toUpperCase()] ?? value}</Tag> : '-';
        },
      },
      { title: t('customer.colManager'), dataIndex: 'managerName', key: 'managerName', width: 130, render: (v?: string) => v || '-' },
      { title: t('customer.colPhases'), dataIndex: 'phaseCount', key: 'phaseCount', width: 90 },
      { title: t('customer.colProjectAssets'), dataIndex: 'assetCount', key: 'assetCount', width: 90 },
      {
        title: t('customer.colAction'),
        key: 'action',
        width: 110,
        render: (_: unknown, record: { id: string }) => <Link to={`/projects/${record.id}`}>{t('customer.openProject')}</Link>,
      },
    ],
    [t],
  );

  const assetColumns = useMemo(
    () => [
      { title: t('customer.colAssetCode'), dataIndex: 'code', key: 'code', width: 160 },
      {
        title: t('customer.colAssetName'),
        dataIndex: 'name',
        key: 'name',
        render: (value: string, record: CustomerAssetVO) => <Link to={`/assets/${record.id}`}>{value}</Link>,
      },
      { title: t('customer.colAssetType'), dataIndex: 'targetType', key: 'targetType', width: 130, render: (v?: string) => v || '-' },
      {
        title: t('customer.colAssetStatus'),
        dataIndex: 'status',
        key: 'status',
        width: 110,
        render: (value?: string) => (value ? <Tag>{value}</Tag> : '-'),
      },
      {
        title: t('customer.colAssetProjects'),
        key: 'projects',
        render: (_: unknown, record: CustomerAssetVO) => {
          const names = (record.projects ?? []).map((item) => item.name || item.code || '-').filter(Boolean);
          if (names.length === 0) return '-';
          return (
            <Space size={[4, 4]} wrap>
              {names.map((name) => (
                <Tag key={name} color="blue">
                  {name}
                </Tag>
              ))}
            </Space>
          );
        },
      },
    ],
    [t],
  );

  const customer = summary?.customer ?? null;
  const title = customer ? customer.name : t('customerPage.detailTitle');
  const subtitle = customer ? t('customerPage.subtitleTemplate', { code: customer.code }) : undefined;

  const extra = summary ? (
    <>
      <Button icon={<ReloadOutlined />} loading={loading} onClick={() => { void loadSummary(); void loadAssets(assetPage.page, assetPage.page_size); }} data-testid="customer-detail-refresh">
        {t('common.refresh')}
      </Button>
      <Button
        icon={<PlusOutlined />}
        onClick={() => navigate(`/projects/new?customerId=${encodeURIComponent(customer!.id)}`)}
        data-testid="customer-detail-new-project"
      >
        {t('customer.newProject')}
      </Button>
      {canWrite ? (
        <>
          <Button type="primary" icon={<EditOutlined />} onClick={() => navigate(`/customers/${customer!.id}/edit`)} data-testid="customer-detail-edit">
            {t('common.edit')}
          </Button>
          <Button onClick={handleToggleStatus} data-testid="customer-detail-toggle">
            {customer!.status === 'active' ? t('customer.statusInactive') : t('customer.statusActive')}
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete} data-testid="customer-detail-delete">
            {t('common.delete')}
          </Button>
        </>
      ) : null}
    </>
  ) : null;

  return (
    <div className="page" data-testid="customer-detail-page">
      <PageHeader title={title} subtitle={subtitle} fallbackTo="/customers" extra={extra} testId="customer-page-header" />

      {!profileLoaded ? null : !canWrite ? (
        <Alert type="info" showIcon message={t('customer.readonlyHint')} />
      ) : null}

      {loading ? (
        <AppLoading tip={t('common.loading')} />
      ) : error ? (
        <Alert
          type="error"
          showIcon
          message={error}
          action={
            <Space>
              <Button size="small" onClick={() => void loadSummary()}>
                {t('common.retry')}
              </Button>
              <Button size="small" type="link" onClick={() => navigate('/customers')}>
                {t('customerPage.backToList')}
              </Button>
            </Space>
          }
        />
      ) : summary ? (
        <>
          <Card className="page-group" size="small" title={t('customerPage.basicInfo')}>
            <Descriptions column={{ xs: 1, sm: 1, md: 2 }} size="small" bordered>
              <Descriptions.Item label={t('customer.fieldCode')}>{customer!.code}</Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldName')}>{customer!.name}</Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldIndustry')}>{customer!.industry || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldLevel')}>{customer!.level || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldOwner')}>{ownerName(customer!.ownerId)}</Descriptions.Item>
              <Descriptions.Item label={t('customer.colStatus')}>
                <Tag color={customer!.status === 'active' ? 'green' : 'default'}>
                  {customer!.status === 'active' ? t('customer.statusActive') : t('customer.statusInactive')}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldContactName')}>{customer!.contactName || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldContactPhone')}>{customer!.contactPhone || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldContactEmail')}>{customer!.contactEmail || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('customer.createdAt')}>{formatDateTime(customer!.createdAt, '-')}</Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldDescription')} span={2}>
                {customer!.description || '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>

          <Card className="page-group" size="small" title={t('customerPage.stats')}>
            <Descriptions column={{ xs: 1, sm: 2, md: 5 }} size="small">
              <Descriptions.Item label={t('customer.colProjects')}>{summary.stats.projectCount}</Descriptions.Item>
              <Descriptions.Item label={t('customer.statActiveProjects')}>{summary.stats.activeProjectCount}</Descriptions.Item>
              <Descriptions.Item label={t('customer.statPhasedProjects')}>{summary.stats.phasedProjectCount}</Descriptions.Item>
              <Descriptions.Item label={t('customer.colAssets')}>{summary.stats.assetCount}</Descriptions.Item>
              <Descriptions.Item label={t('customer.statVulnerabilities')}>{summary.stats.vulnerabilityCount}</Descriptions.Item>
            </Descriptions>
          </Card>

          <Card className="page-group" size="small" title={t('customerPage.projects')} data-testid="customer-detail-projects">
            <Table
              size="small"
              rowKey="id"
              dataSource={summary.projects}
              columns={projectColumns}
              pagination={false}
              scroll={{ x: 'max-content' }}
              locale={{ emptyText: <AppEmpty description={t('customer.projectsEmpty')} /> }}
            />
          </Card>

          <Card className="page-group" size="small" title={t('customerPage.assets')} extra={<Typography.Text type="secondary" style={{ fontSize: 12 }}>{t('customerPage.assetsHint')}</Typography.Text>} data-testid="customer-detail-assets">
            <Table<CustomerAssetVO>
              size="small"
              rowKey="id"
              loading={assetLoading}
              dataSource={assetRows}
              columns={assetColumns}
              scroll={{ x: 'max-content' }}
              locale={{ emptyText: <AppEmpty description={t('customer.assetsEmpty')} /> }}
              pagination={{
                current: assetPage.page,
                pageSize: assetPage.page_size,
                total: assetTotal,
                showSizeChanger: true,
                onChange: (page, pageSize) => {
                  setAssetPage({ page, page_size: pageSize });
                  void loadAssets(page, pageSize);
                },
              }}
            />
          </Card>
        </>
      ) : (
        <AppEmpty description={t('customerPage.notFound')} />
      )}
    </div>
  );
};

export default CustomerDetail;
