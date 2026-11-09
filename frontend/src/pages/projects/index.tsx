import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Input, Modal, Select, Space, Switch, Table, Tag, Tooltip, message } from 'antd';
import type { TableColumnsType } from 'antd';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useT } from '@/i18n';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import SoftDeletedTable from '@/components/softDelete/SoftDeletedTable';
import { useUserStore } from '@/store/userStore';
import { canCreateProject, canManageProject, resolveCurrentUserId } from '@/utils/accessRoles';
import CustomerSelectEmpty from '@/components/customer/CustomerSelectEmpty';
import { useCustomerOptions } from '@/hooks/useCustomerOptions';
import {
  changeProjectStatus,
  deleteProject,
  extractProjectSummaryPage,
  getProjectSummaryList,
} from '@/api/project';
import {
  PROJECT_STATUS_COLORS,
  PROJECT_STATUS_ORDER,
  PROJECT_TYPE_ORDER,
  isProjectClosed,
  nextProjectStatuses,
  isKnownProjectType,
  projectStatusKey,
  projectTypeKey,
  transitionLabelKey,
  type ProjectSummaryQuery,
  type ProjectSummarySort,
  type ProjectSummaryVO,
} from '@/types/project';
import { AppEmpty } from '@/components/Feedback';
import { describeProjectFailure } from './projectFailure';

/**
 * 项目管理列表（t72）。
 *
 * - 筛选：客户 / 状态 / 类型 / 关键词（`?customerId=` 从客户详情跳转带入）
 * - 写入口：admin / project_leader 全量；team_leader 仅自己担任经理的项目；工程师只读
 * - 状态机：按后端合法流转渲染按钮（DRAFT→ACTIVE→{PAUSED,CLOSED}→CLOSED），CLOSED 后全部置灰
 * - 聚合列（期次数 / 资产数 / 经理 / 客户名）一次取自 `GET /projects/summary`（t82），
 *   不再逐项目查详情（原 N+1 已消除）；`assetCount` 为「你的可见范围」，`phaseCount` 为全量口径
 */
const Projects: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { profile, loaded: profileLoaded } = useAccessProfile();
  const storeUser = useUserStore((state) => state.user);
  const currentUserId = resolveCurrentUserId(profile, storeUser?.id);

  const canCreate = profileLoaded && canCreateProject(profile);
  /** t143：含已删列表与恢复仅 ADMIN（后端 /admin/projects 只放行 ADMIN） */
  const isAdmin = profileLoaded && Boolean(profile?.roles?.includes('admin'));
  const [showDeleted, setShowDeleted] = useState(false);

  const [data, setData] = useState<ProjectSummaryVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [total, setTotal] = useState(0);
  // t139：客户下拉带状态（失败不再静默变成空列表）
  const customerOptionsState = useCustomerOptions();
  const customers = customerOptionsState.items;
  const [statusChanging, setStatusChanging] = useState<string | null>(null);

  const customerId = searchParams.get('customerId') ?? undefined;
  const [params, setParams] = useState<ProjectSummaryQuery>({
    page: 1,
    page_size: 10,
    customerId,
    status: undefined,
    type: undefined,
    keyword: '',
    // 默认按创建时间倒序（后端默认即 createdAt）
    sort: 'createdAt',
  });
  const [keyword, setKeyword] = useState('');


  useEffect(() => {
    // 只在 URL 里的 customerId 真的变化时同步，避免挂载时多打一次列表请求
    setParams((prev) => (prev.customerId === customerId ? prev : { ...prev, customerId, page: 1 }));
  }, [customerId]);

  const fetchProjects = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      // 单次聚合请求：期次数 / 资产数 / 经理名 / 客户名都在响应里（t82），无逐项目请求
      const res = await getProjectSummaryList(params);
      const page = extractProjectSummaryPage(res);
      setData(page.items);
      setTotal(page.total);
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

  const handleStatusChange = async (project: ProjectSummaryVO, target: string) => {
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

  const handleDelete = (project: ProjectSummaryVO) => {
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

  const columns: TableColumnsType<ProjectSummaryVO> = [
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
        record.type ? (
          <Tag>{isKnownProjectType(record.type) ? t(projectTypeKey(record.type)) : record.type}</Tag>
        ) : (
          '-'
        ),
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
      render: (_: unknown, record) => record.phaseCount ?? '-',
    },
    {
      title: (
        <Tooltip
          title={`${t('project.scopeHintTitle')}: ${t('dashboard.customerDist.scopeHint')}`}
        >
          <span style={{ borderBottom: '1px dashed #bfbfbf', cursor: 'help' }}>
            {t('project.colAssetCount')}
          </span>
        </Tooltip>
      ),
      key: 'assetCount',
      width: 120,
      render: (_: unknown, record) => (
        <Tooltip title={t('dashboard.customerDist.scopeHint')}>
          <span style={{ borderBottom: '1px dashed #d9d9d9', cursor: 'help' }}>{record.assetCount ?? '-'}</span>
        </Tooltip>
      ),
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
              <a onClick={() => navigate(`/projects/${record.id}/edit`)}>
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
              onClick={() => navigate('/projects/new')}
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
          loading={customerOptionsState.status === 'loading'}
          data-testid="project-filter-customer-select"
          // t139：失败/空客户时给出指引，而不是空白下拉
          notFoundContent={
            <CustomerSelectEmpty
              compact
              status={customerOptionsState.status}
              detail={customerOptionsState.detail}
              onRetry={() => void customerOptionsState.reload()}
            />
          }
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
        <Select
          style={{ width: 210 }}
          value={params.sort ?? 'createdAt'}
          aria-label={t('project.sort.label')}
          options={[
            { value: 'createdAt', label: t('project.sort.createdAt') },
            { value: 'assetCount', label: t('project.sort.assetCount') },
          ] as Array<{ value: ProjectSummarySort; label: string }>}
          onChange={(value) => setParams((prev) => ({ ...prev, sort: value, page: 1 }))}
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
        {/* t143：含已删（仅 ADMIN）*/}
        {isAdmin ? (
          <Space size={6}>
            <Switch
              size="small"
              checked={showDeleted}
              onChange={(checked) => setShowDeleted(checked)}
              data-testid="project-show-deleted-switch"
            />
            <span style={{ fontSize: 12, color: '#6b7280' }}>{t('softDelete.showDeleted')}</span>
          </Space>
        ) : null}
      </Space>

      {showDeleted && isAdmin ? (
        <SoftDeletedTable kind="projects" keyword={params.keyword} onRestored={() => void fetchProjects()} />
      ) : (
      <Table<ProjectSummaryVO>
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
      )}
    </Card>
  );
};

export default Projects;





