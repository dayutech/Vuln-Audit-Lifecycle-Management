import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import type { TableColumnsType } from 'antd';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useT } from '@/i18n';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { useUserStore } from '@/store/userStore';
import { canCreateProject, canManageProject, resolveCurrentUserId } from '@/utils/accessRoles';
import { extractCustomerPage, getCustomerOptions } from '@/api/customer';
import type { CustomerVO } from '@/types/customer';
import {
  changeProjectStatus,
  deleteProject,
  extractPhaseList,
  extractProjectDetail,
  extractProjectPage,
  getProjectDetail,
  getProjectList,
} from '@/api/project';
import {
  PROJECT_STATUS_COLORS,
  PROJECT_STATUS_ORDER,
  PROJECT_TYPE_ORDER,
  isProjectClosed,
  nextProjectStatuses,
  projectStatusKey,
  projectTypeKey,
  transitionLabelKey,
  type ProjectQuery,
  type ProjectVO,
} from '@/types/project';
import { AppEmpty } from '@/components/Feedback';
import ProjectFormModal from './ProjectFormModal';
import { describeProjectFailure } from './projectFailure';

interface RowCounts {
  phaseCount?: number;
  assetCount?: number;
}

/**
 * 项目管理列表（t72）。
 *
 * - 筛选：客户 / 状态 / 类型 / 关键词（`?customerId=` 从客户详情跳转带入）
 * - 写入口：admin / project_leader 全量；team_leader 仅自己担任经理的项目；工程师只读
 * - 状态机：按后端合法流转渲染按钮（DRAFT→ACTIVE→{PAUSED,CLOSED}→CLOSED），CLOSED 后全部置灰
 * - 期次数 / 资产数来自 `GET /projects/{id}` 的聚合字段（列表接口不返回），按当前页并发补齐
 */
const Projects: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { profile, loaded: profileLoaded } = useAccessProfile();
  const storeUser = useUserStore((state) => state.user);
  const currentUserId = resolveCurrentUserId(profile, storeUser?.id);

  const canCreate = profileLoaded && canCreateProject(profile);

  const [data, setData] = useState<ProjectVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<Record<string, RowCounts>>({});
  const [customers, setCustomers] = useState<CustomerVO[]>([]);
  const [statusChanging, setStatusChanging] = useState<string | null>(null);

  const customerId = searchParams.get('customerId') ?? undefined;
  const [params, setParams] = useState<ProjectQuery>({
    page: 1,
    page_size: 10,
    customerId,
    status: undefined,
    type: undefined,
    keyword: '',
  });
  const [keyword, setKeyword] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ProjectVO | null>(null);

  useEffect(() => {
    setParams((prev) => ({ ...prev, customerId, page: 1 }));
  }, [customerId]);

  useEffect(() => {
    getCustomerOptions(undefined, 200)
      .then((res) => setCustomers(extractCustomerPage(res).items))
      .catch(() => setCustomers([]));
  }, []);

  const fetchProjects = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await getProjectList(params);
      const page = extractProjectPage(res);
      setData(page.items);
      setTotal(page.total);

      // 期次数 / 资产数只在详情接口里，按当前页补一次（失败不影响列表）
      const detailEntries = await Promise.all(
        page.items.map(async (item) => {
          try {
            const detailRes = await getProjectDetail(item.id);
            const detail = extractProjectDetail(detailRes);
            return [
              item.id,
              {
                phaseCount: detail?.phases ? extractPhaseList(detailRes).length : 0,
                assetCount: detail?.assetCount ?? 0,
              },
            ] as const;
          } catch {
            return [item.id, {} as RowCounts] as const;
          }
        }),
      );
      setCounts(Object.fromEntries(detailEntries));
    } catch (error) {
      setData([]);
      setTotal(0);
      setLoadError(t('project.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [params, t]);

  useEffect(() => {
    fetchProjects();
  }, [fetchProjects]);

  const customerOptions = useMemo(
    () =>
      customers.map((customer) => ({
        value: customer.id,
        label: `${customer.name}${customer.code ? `（${customer.code}）` : ''}`,
      })),
    [customers],
  );

  const currentCustomer = useMemo(
    () => customers.find((customer) => customer.id === customerId),
    [customers, customerId],
  );

  const customerLabel = (id?: string | null) => {
    if (!id) return '-';
    return customers.find((customer) => customer.id === id)?.name ?? id;
  };

  const handleStatusChange = async (project: ProjectVO, target: string) => {
    const from = String(project.status ?? 'DRAFT').toUpperCase();
    Modal.confirm({
      title: t('project.confirm.statusTitle'),
      content: t('project.confirm.statusContent', {
        name: project.name,
        from: t(projectStatusKey(from)),
        to: t(projectStatusKey(target)),
      }),
      okText: t(transitionLabelKey(from, target)),
      cancelText: t('common.cancel'),
      onOk: async () => {
        setStatusChanging(project.id);
        try {
          await changeProjectStatus(project.id, target);
          message.success(t('project.statusChanged'));
          await fetchProjects();
        } catch (error) {
          message.error(
            describeProjectFailure(error, {
              forbidden: t('project.writeForbidden'),
              notFound: t('project.detail.notFound'),
              blockedTemplate: t('project.deleteBlocked'),
              blockedFallback: t('project.deleteBlockedHint'),
              invalidTransition: t('project.statusInvalid'),
              fallback: t('common.operationFailed'),
            }),
          );
        } finally {
          setStatusChanging(null);
        }
      },
    });
  };

  const handleDelete = (project: ProjectVO) => {
    Modal.confirm({
      title: t('project.deleteTitle'),
      content: t('project.deleteContent'),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await deleteProject(project.id);
          message.success(t('project.deleted'));
          await fetchProjects();
        } catch (error) {
          message.error(
            describeProjectFailure(
              error,
              {
                forbidden: t('project.writeForbidden'),
                notFound: t('project.detail.notFound'),
                blockedTemplate: t('project.deleteBlocked'),
                blockedFallback: t('project.deleteBlockedHint'),
                invalidTransition: t('project.statusInvalid'),
                fallback: t('common.operationFailed'),
              },
              (template, count) => template.replace('{count}', String(count)),
            ),
          );
        }
      },
    });
  };

  const columns: TableColumnsType<ProjectVO> = [
    {
      title: t('project.colCode'),
      dataIndex: 'code',
      key: 'code',
      width: 170,
      render: (value: string, record) => <a onClick={() => navigate(`/projects/${record.id}`)}>{value || '-'}</a>,
    },
    { title: t('project.colName'), dataIndex: 'name', key: 'name', width: 220, ellipsis: true },
    {
      title: t('project.colCustomer'),
      key: 'customer',
      width: 180,
      ellipsis: true,
      render: (_: unknown, record) => record.customerName || customerLabel(record.customerId),
    },
    {
      title: t('project.colType'),
      key: 'type',
      width: 120,
      render: (_: unknown, record) =>
        record.type ? <Tag>{t(projectTypeKey(record.type))}</Tag> : '-',
    },
    {
      title: t('project.colStatus'),
      key: 'status',
      width: 110,
      render: (_: unknown, record) => {
        const status = String(record.status ?? 'DRAFT').toUpperCase();
        return <Tag color={PROJECT_STATUS_COLORS[status] ?? 'default'}>{t(projectStatusKey(status))}</Tag>;
      },
    },
    {
      title: t('project.colManager'),
      key: 'manager',
      width: 140,
      render: (_: unknown, record) => record.managerName || '-',
    },
    {
      title: t('project.colPeriod'),
      key: 'period',
      width: 200,
      render: (_: unknown, record) =>
        record.startDate || record.endDate ? `${record.startDate ?? '-'} ~ ${record.endDate ?? '-'}` : '-',
    },
    {
      title: t('project.colPhased'),
      key: 'isPhased',
      width: 100,
      render: (_: unknown, record) => (
        <Tag color={record.isPhased ? 'blue' : 'default'}>
          {record.isPhased ? t('project.multiphaseYes') : t('project.multiphaseNo')}
        </Tag>
      ),
    },
    {
      title: t('project.colPhaseCount'),
      key: 'phaseCount',
      width: 100,
      render: (_: unknown, record) => counts[record.id]?.phaseCount ?? '-',
    },
    {
      title: t('project.colAssetCount'),
      key: 'assetCount',
      width: 100,
      render: (_: unknown, record) => counts[record.id]?.assetCount ?? '-',
    },
    {
      title: t('project.colAction'),
      key: 'action',
      width: 260,
      fixed: 'right',
      render: (_: unknown, record) => {
        const writable = profileLoaded && canManageProject(profile, record.managerId, currentUserId);
        const closed = isProjectClosed(record);
        const transitions = writable && !closed ? nextProjectStatuses(record.status) : [];
        return (
          <Space size="small" wrap>
            <a onClick={() => navigate(`/projects/${record.id}`)}>{t('project.detail')}</a>
            {writable ? (
              <a
                onClick={() => {
                  setEditing(record);
                  setFormOpen(true);
                }}
              >
                {t('common.edit')}
              </a>
            ) : null}
            {transitions.map((target) => (
              <Button
                key={target}
                type="link"
                size="small"
                style={{ padding: 0 }}
                loading={statusChanging === record.id}
                onClick={() => handleStatusChange(record, target)}
              >
                {t(transitionLabelKey(record.status, target))}
              </Button>
            ))}
            {writable ? (
              <Button
                type="link"
                size="small"
                danger
                style={{ padding: 0 }}
                disabled={closed}
                onClick={() => handleDelete(record)}
              >
                {t('project.delete')}
              </Button>
            ) : null}
          </Space>
        );
      },
    },
  ];

  // 列表级只读判定：没有具体项目时 managerId 传 undefined，否则会把工程师误判成「该项目经理」
  const readOnly = profileLoaded && !canManageProject(profile, undefined, currentUserId);

  return (
    <Card
      title={t('project.title')}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchProjects} loading={loading}>
            {t('common.refresh')}
          </Button>
          {canCreate ? (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(null);
                setFormOpen(true);
              }}
            >
              {t('project.new')}
            </Button>
          ) : null}
        </Space>
      }
    >
      {readOnly ? (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={t('project.readonlyHint')}
        />
      ) : null}

      {customerId ? (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={t('project.filteredByCustomer', { name: currentCustomer?.name ?? customerId })}
          action={
            <Button
              size="small"
              type="link"
              onClick={() => {
                const next = new URLSearchParams(searchParams);
                next.delete('customerId');
                setSearchParams(next);
              }}
            >
              {t('project.clearCustomerFilter')}
            </Button>
          }
        />
      ) : null}

      {loadError ? (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message={loadError}
          action={
            <Button size="small" onClick={fetchProjects}>
              {t('common.refresh')}
            </Button>
          }
        />
      ) : null}

      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          placeholder={t('project.searchPlaceholder')}
          prefix={<SearchOutlined />}
          style={{ width: 260 }}
          value={keyword}
          allowClear
          onChange={(event) => setKeyword(event.target.value)}
          onPressEnter={() => setParams((prev) => ({ ...prev, keyword: keyword.trim(), page: 1 }))}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          style={{ width: 220 }}
          placeholder={t('project.filterCustomerPlaceholder')}
          value={params.customerId}
          options={customerOptions}
          onChange={(value) => setParams((prev) => ({ ...prev, customerId: value, page: 1 }))}
        />
        <Select
          allowClear
          style={{ width: 150 }}
          placeholder={t('project.filterStatusPlaceholder')}
          value={params.status}
          options={PROJECT_STATUS_ORDER.map((status) => ({ value: status, label: t(projectStatusKey(status)) }))}
          onChange={(value) => setParams((prev) => ({ ...prev, status: value, page: 1 }))}
        />
        <Select
          allowClear
          style={{ width: 160 }}
          placeholder={t('project.filterTypePlaceholder')}
          value={params.type}
          options={PROJECT_TYPE_ORDER.map((type) => ({ value: type, label: t(projectTypeKey(type)) }))}
          onChange={(value) => setParams((prev) => ({ ...prev, type: value, page: 1 }))}
        />
        <Button
          type="primary"
          onClick={() => setParams((prev) => ({ ...prev, keyword: keyword.trim(), page: 1 }))}
        >
          {t('project.query')}
        </Button>
        <Button
          onClick={() => {
            setKeyword('');
            setParams({ page: 1, page_size: params.page_size, keyword: '' });
          }}
        >
          {t('project.reset')}
        </Button>
      </Space>

      <Table<ProjectVO>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1720 }}
        locale={{ emptyText: <AppEmpty description={t('project.empty')} /> }}
        pagination={{
          current: params.page ?? 1,
          pageSize: params.page_size ?? 10,
          total,
          showSizeChanger: true,
          showTotal: (count) => t('project.total', { count }),
          onChange: (page, pageSize) => setParams((prev) => ({ ...prev, page, page_size: pageSize })),
        }}
      />

      <ProjectFormModal
        open={formOpen}
        project={editing}
        defaultCustomerId={customerId}
        onCancel={() => setFormOpen(false)}
        onSaved={async (saved, mode) => {
          setFormOpen(false);
          setEditing(null);
          await fetchProjects();
          if (mode === 'create' && saved?.id) {
            navigate(`/projects/${saved.id}`);
          }
        }}
      />
    </Card>
  );
};

export default Projects;
