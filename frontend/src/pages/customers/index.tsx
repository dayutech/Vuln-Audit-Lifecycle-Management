import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { BankOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import {
  changeCustomerStatus,
  createCustomer,
  deleteCustomer,
  extractCustomerAssetPage,
  extractCustomerPage,
  extractSummary,
  getCustomerAssets,
  getCustomerList,
  getCustomerSummary,
  updateCustomer,
} from '@/api/customer';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import type {
  CustomerAssetVO,
  CustomerQueryParams,
  CustomerSummaryVO,
  CustomerVO,
} from '@/types/customer';
import { useT } from '@/i18n';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { isManagementProfile } from '@/utils/accessRoles';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { formatDateTime } from '@/utils/datetime';
import { AppEmpty, AppLoading } from '@/components/Feedback';

const { TextArea } = Input;
const { Text } = Typography;

/** 客户级别 → Tag 配色（A/B/C；后端是自由字符串，未知级别走 default） */
const LEVEL_COLORS: Record<string, string> = { A: 'gold', B: 'blue', C: 'default' };
const LEVEL_OPTIONS = ['A', 'B', 'C'];

const Customers: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { profile, loaded: profileLoaded } = useAccessProfile();
  /** 客户写权：后端 @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")，团队长不在其中 */
  const canWrite = profileLoaded && isManagementProfile(profile);

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

  // 新建 / 编辑
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<CustomerVO | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [owners, setOwners] = useState<UserDirectoryEntry[]>([]);
  const [ownerLimitHit, setOwnerLimitHit] = useState(false);
  const ownerTimer = useRef<number | undefined>(undefined);

  // 详情（t77：单次 summary 请求渲染客户 + 统计 + 项目 + 最近资产）
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [summary, setSummary] = useState<CustomerSummaryVO | null>(null);
  const [detailCustomerId, setDetailCustomerId] = useState<string | null>(null);

  // 资产列表（分页，GET /assets?customerId=）
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
      setPageError(
        status === 403 ? t('customer.forbidden') : getApiErrorMessage(error, t('common.operationFailed')),
      );
    } finally {
      setLoading(false);
    }
  }, [params, t]);

  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  // 负责人下拉：走 /users/directory（t48 PII 收敛）
  useEffect(() => {
    const load = async () => {
      try {
        const list = await getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT });
        setOwners(list);
        setOwnerLimitHit(list.length >= USER_DIRECTORY_DEFAULT_LIMIT);
      } catch {
        setOwners([]);
      }
    };
    void load();
  }, []);

  const handleOwnerSearch = (keyword: string) => {
    if (ownerTimer.current) window.clearTimeout(ownerTimer.current);
    ownerTimer.current = window.setTimeout(async () => {
      try {
        const list = await getUserDirectory({ keyword, limit: USER_DIRECTORY_DEFAULT_LIMIT });
        setOwners((prev) => {
          const map = new Map(prev.map((item) => [item.id, item]));
          list.forEach((item) => map.set(item.id, item));
          return Array.from(map.values());
        });
        setOwnerLimitHit(list.length >= USER_DIRECTORY_DEFAULT_LIMIT);
      } catch {
        /* 下拉搜索失败不阻塞表单 */
      }
    }, 300);
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ status: 'active', level: 'B' });
    setFormOpen(true);
  };

  const openEdit = (record: CustomerVO) => {
    setEditing(record);
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      industry: record.industry ?? undefined,
      level: record.level ?? undefined,
      contactName: record.contactName ?? undefined,
      contactPhone: record.contactPhone ?? undefined,
      contactEmail: record.contactEmail ?? undefined,
      ownerId: record.ownerId ?? undefined,
      description: record.description ?? undefined,
    });
    setFormOpen(true);
  };

  /** 单个 summary 请求渲染详情主体；withSpinner=false 用于静默刷新 */
  const loadSummary = useCallback(
    async (customerId: string, withSpinner = true) => {
      if (withSpinner) {
        setDetailLoading(true);
        setSummary(null);
      }
      setDetailError(null);
      try {
        const res = await getCustomerSummary(customerId);
        const parsed = extractSummary(res);
        setSummary(parsed);
        if (!parsed) setDetailError(t('common.operationFailed'));
      } catch (error) {
        const status = getApiErrorStatus(error);
        setDetailError(
          status === 404
            ? t('customer.notFound')
            : status === 403
              ? t('customer.forbidden')
              : getApiErrorMessage(error, t('common.operationFailed')),
        );
      } finally {
        if (withSpinner) setDetailLoading(false);
      }
    },
    [t],
  );

  const loadAssets = useCallback(async (customerId: string, page = 1, pageSize = 10) => {
    setAssetLoading(true);
    try {
      const res = await getCustomerAssets(customerId, { page, page_size: pageSize });
      const { items, total: count } = extractCustomerAssetPage(res);
      setAssetRows(items);
      setAssetTotal(count);
    } catch (error) {
      setAssetRows([]);
      setAssetTotal(0);
      if (getApiErrorStatus(error) !== 403) {
        message.error(getApiErrorMessage(error));
      }
    } finally {
      setAssetLoading(false);
    }
  }, []);

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editing) {
        await updateCustomer(editing.id, values);
        message.success(t('customer.updateSuccess'));
      } else {
        await createCustomer(values);
        message.success(t('customer.createSuccess'));
      }
      setFormOpen(false);
      setEditing(null);
      await fetchData();
      // 详情打开时同步刷新聚合（t77：不用整页刷新）
      if (detailOpen && detailCustomerId) await loadSummary(detailCustomerId, false);
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 409) {
        message.error(t('customer.codeConflict'));
      } else if (status === 403) {
        message.error(t('customer.forbidden'));
      } else {
        message.error(getApiErrorMessage(error, t('common.operationFailed')));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const openDetail = async (record: CustomerVO) => {
    setDetailOpen(true);
    setDetailCustomerId(record.id);
    setAssetRows([]);
    setAssetTotal(0);
    setAssetPage({ page: 1, page_size: 10 });
    // 只发一个 summary 请求 + 一个资产分页请求（不再逐项目 GET）
    await Promise.all([loadSummary(record.id), loadAssets(record.id, 1, 10)]);
  };

  /** 详情打开时窗口重新获得焦点（如去项目页新建/编辑项目后返回）自动刷新 summary */
  useEffect(() => {
    if (!detailOpen || !detailCustomerId) return undefined;
    const onFocus = () => {
      void loadSummary(detailCustomerId, false);
      void loadAssets(detailCustomerId, assetPage.page, assetPage.page_size);
    };
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [detailOpen, detailCustomerId, assetPage.page, assetPage.page_size, loadSummary, loadAssets]);

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
          if (detailOpen && detailCustomerId === record.id) await loadSummary(record.id, false);
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
          <Text type="secondary">{t('customer.deleteBlockedHint')}</Text>
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
          <a onClick={() => void openDetail(record)}>{value}</a>
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
      {
        title: t('customer.colOwner'),
        dataIndex: 'ownerId',
        key: 'ownerId',
        render: (value?: string) => ownerName(value),
      },
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
            <a onClick={() => void openDetail(record)}>{t('customer.detail')}</a>
            {canWrite && <a onClick={() => openEdit(record)}>{t('common.edit')}</a>}
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
    // openDetail / handlers 为每次渲染新建，但只依赖稳定 setter；用 t/canWrite/ownerName 作为刷新依据
    [t, canWrite, ownerName],
  );

  /** 项目表：数据全部来自 summary.projects（t76 已算好 phaseCount / assetCount） */
  const projectColumns = useMemo(
    () => [
      {
        title: t('customer.colProjectName'),
        dataIndex: 'name',
        key: 'name',
        render: (value: string, record: { id: string }) => (
          <a onClick={() => navigate(`/projects/${record.id}`)}>{value}</a>
        ),
      },
      {
        title: t('customer.colProjectType'),
        dataIndex: 'type',
        key: 'type',
        width: 110,
        render: (v?: string) => v || '-',
      },
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
      {
        title: t('customer.colManager'),
        dataIndex: 'managerName',
        key: 'managerName',
        width: 130,
        render: (v?: string) => v || '-',
      },
      { title: t('customer.colPhases'), dataIndex: 'phaseCount', key: 'phaseCount', width: 90 },
      { title: t('customer.colProjectAssets'), dataIndex: 'assetCount', key: 'assetCount', width: 90 },
      {
        title: t('customer.colAction'),
        key: 'action',
        width: 110,
        render: (_: unknown, record: { id: string }) => (
          <a onClick={() => navigate(`/projects/${record.id}`)}>{t('customer.openProject')}</a>
        ),
      },
    ],
    [t, navigate],
  );

  /** 资产表（t77）：GET /assets?customerId= 分页；「所属项目」多值 Tag 折叠 */
  const assetColumns = useMemo(
    () => [
      { title: t('customer.colAssetCode'), dataIndex: 'code', key: 'code', width: 160 },
      {
        title: t('customer.colAssetName'),
        dataIndex: 'name',
        key: 'name',
        render: (value: string, record: CustomerAssetVO) => (
          <a onClick={() => navigate(`/assets/${record.id}`)}>{value}</a>
        ),
      },
      {
        title: t('customer.colAssetType'),
        dataIndex: 'targetType',
        key: 'targetType',
        width: 130,
        render: (v?: string) => v || '-',
      },
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
          const names = (record.projects ?? [])
            .map((item) => item.name || item.code || '-')
            .filter(Boolean);
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
    [t, navigate],
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
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              {t('customer.new')}
            </Button>
          ) : null}
        </Space>
      }
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
      </Space>

      {pageError ? <Alert type="error" showIcon style={{ marginBottom: 16 }} message={pageError} /> : null}

      <Table
        rowKey="id"
        loading={loading}
        dataSource={data}
        columns={columns}
        locale={{ emptyText: <AppEmpty description={t('customer.empty')} /> }}
        pagination={{
          current: params.page,
          pageSize: params.page_size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) => setParams((prev) => ({ ...prev, page, page_size: pageSize })),
        }}
      />

      {/* 新建 / 编辑 */}
      <Drawer
        title={editing ? t('customer.edit') : t('customer.new')}
        open={formOpen}
        width={520}
        onClose={() => setFormOpen(false)}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setFormOpen(false)}>{t('common.cancel')}</Button>
            <Button type="primary" loading={submitting} onClick={() => void handleSubmit()}>
              {t('common.save')}
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="code"
            label={t('customer.fieldCode')}
            rules={[{ required: true, message: t('customer.requiredCode') }]}
          >
            <Input placeholder={t('customer.placeholderCode')} maxLength={50} disabled={!!editing} />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('customer.fieldName')}
            rules={[{ required: true, message: t('customer.requiredName') }]}
          >
            <Input maxLength={200} />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="industry" label={t('customer.fieldIndustry')} style={{ flex: 1 }}>
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item name="level" label={t('customer.fieldLevel')} style={{ width: 140 }}>
              <Select allowClear options={LEVEL_OPTIONS.map((item) => ({ value: item, label: item }))} />
            </Form.Item>
          </Space>
          <Form.Item
            name="ownerId"
            label={t('customer.fieldOwner')}
            extra={ownerLimitHit ? t('customer.ownerLimitHint') : undefined}
          >
            <Select
              allowClear
              showSearch
              filterOption={false}
              onSearch={handleOwnerSearch}
              placeholder={t('customer.placeholderOwner')}
              options={owners.map((item) => ({
                value: item.id,
                label: item.fullName ? `${item.fullName}（${item.username}）` : item.username,
              }))}
            />
          </Form.Item>
          <Form.Item name="contactName" label={t('customer.fieldContactName')}>
            <Input maxLength={100} />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="contactPhone" label={t('customer.fieldContactPhone')} style={{ flex: 1 }}>
              <Input maxLength={50} />
            </Form.Item>
            <Form.Item name="contactEmail" label={t('customer.fieldContactEmail')} style={{ flex: 1 }}>
              <Input maxLength={200} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label={t('customer.fieldDescription')}>
            <TextArea rows={3} maxLength={1000} />
          </Form.Item>
        </Form>
      </Drawer>

      {/* 详情：单次 GET /customers/{id}/summary 渲染 + 资产分页表 */}
      <Drawer
        title={summary ? `${summary.customer.name} · ${summary.customer.code}` : t('customer.detail')}
        open={detailOpen}
        width={880}
        onClose={() => {
          setDetailOpen(false);
          setDetailCustomerId(null);
        }}
        destroyOnClose
        extra={
          <Button
            icon={<ReloadOutlined />}
            loading={detailLoading}
            onClick={() => {
              if (detailCustomerId) {
                void loadSummary(detailCustomerId);
                void loadAssets(detailCustomerId, assetPage.page, assetPage.page_size);
              }
            }}
          >
            {t('common.refresh')}
          </Button>
        }
      >
        {detailLoading ? (
          <AppLoading tip={t('common.loading')} />
        ) : detailError ? (
          <Alert type="error" showIcon message={detailError} />
        ) : summary ? (
          <>
            <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label={t('customer.fieldCode')}>{summary.customer.code}</Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldName')}>{summary.customer.name}</Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldIndustry')}>
                {summary.customer.industry || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldLevel')}>{summary.customer.level || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldOwner')}>
                {ownerName(summary.customer.ownerId)}
              </Descriptions.Item>
              <Descriptions.Item label={t('customer.colStatus')}>
                <Tag color={summary.customer.status === 'active' ? 'green' : 'default'}>
                  {summary.customer.status === 'active'
                    ? t('customer.statusActive')
                    : t('customer.statusInactive')}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldContactName')}>
                {summary.customer.contactName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldContactPhone')}>
                {summary.customer.contactPhone || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldContactEmail')}>
                {summary.customer.contactEmail || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('customer.createdAt')}>
                {formatDateTime(summary.customer.createdAt, '-')}
              </Descriptions.Item>
              <Descriptions.Item label={t('customer.fieldDescription')} span={2}>
                {summary.customer.description || '-'}
              </Descriptions.Item>
            </Descriptions>

            <Space size={24} wrap style={{ marginBottom: 16 }}>
              <Text>
                {t('customer.colProjects')}: <b>{summary.stats.projectCount}</b>
              </Text>
              <Text>
                {t('customer.statActiveProjects')}: <b>{summary.stats.activeProjectCount}</b>
              </Text>
              <Text>
                {t('customer.statPhasedProjects')}: <b>{summary.stats.phasedProjectCount}</b>
              </Text>
              <Text>
                {t('customer.colAssets')}: <b>{summary.stats.assetCount}</b>
              </Text>
              <Text>
                {t('customer.statVulnerabilities')}: <b>{summary.stats.vulnerabilityCount}</b>
              </Text>
            </Space>

            <Typography.Title level={5}>{t('customer.projects')}</Typography.Title>
            <Table
              size="small"
              rowKey="id"
              dataSource={summary.projects}
              columns={projectColumns}
              pagination={false}
              locale={{ emptyText: <AppEmpty description={t('customer.projectsEmpty')} /> }}
              style={{ marginBottom: 20 }}
            />

            <Typography.Title level={5}>{t('customer.assets')}</Typography.Title>
            <Table<CustomerAssetVO>
              size="small"
              rowKey="id"
              loading={assetLoading}
              dataSource={assetRows}
              columns={assetColumns}
              locale={{ emptyText: <AppEmpty description={t('customer.assetsEmpty')} /> }}
              pagination={{
                current: assetPage.page,
                pageSize: assetPage.page_size,
                total: assetTotal,
                showSizeChanger: true,
                onChange: (page, pageSize) => {
                  setAssetPage({ page, page_size: pageSize });
                  if (detailCustomerId) void loadAssets(detailCustomerId, page, pageSize);
                },
              }}
            />
          </>
        ) : (
          <AppEmpty description={t('customer.empty')} />
        )}
      </Drawer>
    </Card>
  );
};

export default Customers;
