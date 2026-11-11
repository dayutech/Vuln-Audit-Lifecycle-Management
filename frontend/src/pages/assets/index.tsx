import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Table, Button, Space, Input, Tag, Card, Modal, Select, Tooltip, Switch, message } from 'antd';
import type { TableColumnsType } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getAssetList, deleteAsset, createAssetAccessRequest } from '@/api/asset';
import { getTagOptions, inlineTagNames, joinTagNames } from '@/api/tag';
import SoftDeletedTable from '@/components/softDelete/SoftDeletedTable';
import type { AssetVO } from '@/types/asset';
import CustomerSelectEmpty from '@/components/customer/CustomerSelectEmpty';
import { useCustomerOptions } from '@/hooks/useCustomerOptions';
import { getProjectDetail, getProjectList, extractPhaseList } from '@/api/project';
import type { PhaseVO, ProjectVO } from '@/types/project';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { canCreateAsset } from '@/utils/accessRoles';
import { useT } from '@/i18n';
import type { MessageKey } from '@/i18n/messages/zh-CN';

/**
 * 资产中心列表页（t170 整页重写）。
 *
 * 为什么重写而不是局部删改：本页原 898 行里创建/编辑 Modal 及其表单态、三级联动、
 * 提交/校验分支占了约 35%，删 Modal 会连锁十几个 state 与 import；整页重写可以把
 * 「列表职责」收拢成 4 个互不耦合的 effect，避免多 effect 互相触发。
 *
 * 职责边界（**只做列表**）：
 * - 新建/编辑一律走整页：`/assets/new`、`/assets/:id/edit`（AssetEditorPage，t168 已就位）；
 * - 详情走 `/assets/:id`；本页**不再有任何创建/编辑表单与 Modal 表单**；
 * - 兼容旧入口：`/assets?new=1` → `/assets/new`、`/assets?open=<id>` → `/assets/<id>`（replace）。
 */
const Assets: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { profile, loaded: profileLoaded } = useAccessProfile();
  // t49：POST /assets 仅 admin/project_leader/team_leader → 无权限不渲染新建入口
  const allowCreateAsset = profileLoaded && canCreateAsset(profile);
  const isAdmin = profileLoaded && Boolean(profile?.roles?.includes('admin'));

  const [data, setData] = useState<AssetVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState<{
    page: number;
    page_size: number;
    search: string;
    customer_id?: string;
    project_id?: string;
    phase_id?: string;
    /** 标签筛选：名称列表（AND / OR 由 tag_mode 决定） */
    tags?: string[];
    tag_mode?: 'and' | 'or';
  }>({ page: 1, page_size: 10, search: '' });

  /** 标签下拉（带 assetCount，后端按可见范围收敛） */
  const [tagOptions, setTagOptions] = useState<{ label: string; value: string }[]>([]);
  /** 每行标签：直接取列表内联字段 `AssetVO.tags`（零额外请求） */
  const [tagsByAsset, setTagsByAsset] = useState<Record<string, string[]>>({});
  /** 筛选区的项目 / 期次选项 */
  const [filterProjects, setFilterProjects] = useState<ProjectVO[]>([]);
  const [filterPhases, setFilterPhases] = useState<PhaseVO[]>([]);
  /** 含已删面板（仅 ADMIN）；数据源与恢复动作由 SoftDeletedTable 自己负责 */
  const [showDeleted, setShowDeleted] = useState(false);

  const customerOptionsState = useCustomerOptions();
  const customers = customerOptionsState.items;

  const unwrapPayload = (res: any) => (res?.data ? res.data : res);
  const normalizeAsset = (item: AssetVO): AssetVO => ({
    ...item,
    team_id: item.team_id ?? item.teamId,
    team_name: item.team_name ?? item.teamName,
    owner_id: item.owner_id ?? item.ownerId,
    owner_name: item.owner_name ?? item.ownerName,
    target_type: item.target_type ?? item.targetType ?? '',
    target_source: item.target_source ?? item.targetSource ?? '',
    created_at: item.created_at ?? item.createdAt ?? '',
    updated_at: item.updated_at ?? item.updatedAt,
    customers: item.customers ?? [],
    projects: item.projects ?? [],
  });

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      // 标签按模式选 tags(AND) / tagsAny(OR)，与客户/项目/期次一起交给后端取交集
      const tagNames = joinTagNames(params.tags);
      const res = await getAssetList({
        page: params.page,
        page_size: params.page_size,
        search: params.search,
        customer_id: params.customer_id,
        project_id: params.project_id,
        phase_id: params.phase_id,
        tags: params.tag_mode === 'or' ? undefined : tagNames,
        tags_any: params.tag_mode === 'or' ? tagNames : undefined,
      });
      const payload = unwrapPayload(res);
      const items = payload.content || payload.items || [];
      setData((items as AssetVO[]).map((item) => normalizeAsset(item)));
      setTotal(payload.totalElements || payload.total || 0);
    } catch {
      // 错误提示由全局拦截器负责，这里只保证表格不卡在 loading
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [params.page, params.page_size, params.search, params.customer_id, params.project_id, params.phase_id, params.tags, params.tag_mode]);

  /* ---------------- effect ①：参数变化 → 拉数据（唯一的取数入口） ---------------- */
  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  /* ---------------- effect ②：标签下拉只加载一次 ---------------- */
  useEffect(() => {
    getTagOptions()
      .then((options) => {
        setTagOptions(
          options
            .filter((option) => option.name)
            .map((option) => ({ label: `${option.name}（${option.assetCount}）`, value: option.name })),
        );
      })
      .catch(() => setTagOptions([]));
  }, []);

  /* ---------------- effect ③：行标签从内联字段派生（不发请求） ---------------- */
  useEffect(() => {
    const map: Record<string, string[]> = {};
    (data ?? []).forEach((item) => {
      map[item.id] = inlineTagNames((item as any).tags);
    });
    setTagsByAsset(map);
  }, [data]);

  /* ---------------- effect ④：兼容旧入口（独立于取数，不参与数据流） ---------------- */
  const legacyRedirectDone = useRef(false);
  useEffect(() => {
    if (legacyRedirectDone.current) return;
    const wantNew = searchParams.get('new') === '1';
    const openId = searchParams.get('open');
    if (!wantNew && !openId) return;
    legacyRedirectDone.current = true;
    const next = new URLSearchParams(searchParams);
    next.delete('new');
    next.delete('open');
    setSearchParams(next, { replace: true });
    if (wantNew) {
      message.info(t('asset.list.legacyNewRedirect'));
      navigate('/assets/new', { replace: true });
    } else if (openId) {
      navigate(`/assets/${openId}`, { replace: true });
    }
  }, [searchParams, setSearchParams, navigate, t]);

  /* ---------------- 筛选区：客户 → 项目 → 期次 ---------------- */
  const loadFilterProjects = async (customerId?: string) => {
    if (!customerId) {
      setFilterProjects([]);
      setFilterPhases([]);
      return;
    }
    try {
      const res = await getProjectList({ page: 1, page_size: 200, customerId });
      const payload = unwrapPayload(res);
      const items = payload?.content || payload?.items || [];
      setFilterProjects(Array.isArray(items) ? items : []);
    } catch {
      setFilterProjects([]);
    }
  };

  const loadFilterPhases = async (projectId?: string) => {
    if (!projectId) {
      setFilterPhases([]);
      return;
    }
    try {
      const res = await getProjectDetail(projectId);
      const payload = (res as any)?.data ?? res;
      const phases = Array.isArray(payload?.phases) ? (payload.phases as PhaseVO[]) : extractPhaseList(res);
      setFilterPhases(phases);
    } catch {
      setFilterPhases([]);
    }
  };

  /* ---------------- 行操作 ---------------- */
  const handleDelete = (id: string) => {
    Modal.confirm({
      title: t('common.deleteConfirmTitle'),
      content: t('asset.list.deleteContent'),
      onOk: async () => {
        try {
          await deleteAsset(id);
          message.success(t('asset.list.deleteSuccess'));
          void fetchData();
        } catch {
          // 拦截器已提示
        }
      },
    });
  };

  /**
   * 列表页直接发起访问申请：未获授权的用户打不开详情页（详情 403），
   * 申请入口必须留在列表，否则工程师走不通「申请 → 审批 → 可见」。
   */
  const handleRequestAccess = (record: AssetVO) => {
    let canViewFiles = true;
    Modal.confirm({
      title: t('asset.request.title', { name: record.name }),
      content: (
        <div style={{ marginTop: 12 }}>
          <div style={{ marginBottom: 8, color: '#888' }}>{t('asset.request.tip')}</div>
          <Select
            defaultValue={true}
            style={{ width: '100%' }}
            onChange={(value: boolean) => {
              canViewFiles = value;
            }}
            options={[
              { value: true, label: t('asset.request.needFiles') },
              { value: false, label: t('asset.request.basicOnly') },
            ]}
          />
        </div>
      ),
      okText: t('asset.request.submit'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await createAssetAccessRequest(record.id, {
            can_view_files: canViewFiles,
            request_note: t('asset.request.note'),
          });
          message.success(t('asset.request.success'));
        } catch (error: any) {
          message.error(error?.response?.data?.message || t('asset.request.failed'));
        }
      },
    });
  };

  /* ---------------- 列定义 ---------------- */
  const linkTags = (
    list: Array<{ id: string; name?: string | null; code?: string | null }>,
    color: string,
    onClick?: (id: string) => void,
    testPrefix?: string,
  ) => {
    if (!list || list.length === 0) return '-';
    const visible = list.slice(0, 2);
    const rest = list.length - visible.length;
    return (
      <Space size={4} wrap>
        {visible.map((item) => (
          <Tag
            key={item.id}
            color={color}
            style={onClick ? { cursor: 'pointer' } : undefined}
            onClick={onClick ? () => onClick(item.id) : undefined}
          >
            {item.name ?? item.code ?? item.id}
          </Tag>
        ))}
        {rest > 0 ? (
          <Tooltip title={list.slice(2).map((item) => item.name ?? item.code ?? item.id).join('、')}>
            <Tag data-testid={testPrefix ? `${testPrefix}-more` : undefined}>
              {t('asset.list.moreLinks', { count: rest })}
            </Tag>
          </Tooltip>
        ) : null}
      </Space>
    );
  };

  const columns = useMemo<TableColumnsType<AssetVO>>(
    () => [
      {
        title: t('asset.list.colName'),
        dataIndex: 'name',
        key: 'name',
        render: (text: string, record: AssetVO) => (
          <a onClick={() => navigate(`/assets/${record.id}`)} style={{ fontWeight: 500 }} data-testid={`asset-name-${record.id}`}>
            {text}
          </a>
        ),
      },
      { title: t('asset.list.colCode'), dataIndex: 'code', key: 'code' },
      { title: t('asset.list.colTeam'), dataIndex: 'team_name', key: 'team_name', render: (text?: string) => text || '-' },
      { title: t('asset.list.colOwner'), dataIndex: 'owner_name', key: 'owner_name', render: (text?: string) => text || '-' },
      {
        title: t('asset.list.colCustomer'),
        key: 'customers',
        width: 200,
        // 只读展示：客户详情入口在客户模块，这里不做跳转
        render: (_: unknown, record: AssetVO) => linkTags(record.customers ?? [], 'geekblue'),
      },
      {
        title: t('asset.list.colProject'),
        key: 'projects',
        width: 240,
        render: (_: unknown, record: AssetVO) =>
          linkTags(record.projects ?? [], 'blue', (id) => navigate(`/projects/${id}`), `asset-projects-${record.id}`),
      },
      { title: t('asset.list.colType'), dataIndex: 'target_type', key: 'target_type' },
      { title: t('asset.list.colSource'), dataIndex: 'target_source', key: 'target_source' },
      {
        title: t('asset.list.colPriority'),
        dataIndex: 'priority',
        key: 'priority',
        render: (text: string) => {
          const colors: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue', P3: 'green' };
          return <Tag color={colors[text] || 'default'}>{text}</Tag>;
        },
      },
      {
        title: t('asset.list.colStatus'),
        dataIndex: 'status',
        key: 'status',
        render: (text: string) => {
          const colors: Record<string, string> = {
            active: 'success',
            under_test: 'processing',
            suspended: 'warning',
            archived: 'default',
          };
          const labels: Record<string, MessageKey> = {
            active: 'asset.status.active',
            under_test: 'asset.status.underTest',
            suspended: 'asset.status.suspended',
            archived: 'asset.status.archived',
          };
          return <Tag color={colors[text] || 'default'}>{labels[text] ? t(labels[text]) : text}</Tag>;
        },
      },
      {
        title: t('asset.list.colTags'),
        key: 'tags',
        width: 220,
        render: (_: unknown, record: AssetVO) => {
          const tags = tagsByAsset[record.id] ?? [];
          if (tags.length === 0) {
            return (
              <span style={{ color: '#bfbfbf' }} data-testid={`asset-tags-empty-${record.id}`}>
                {t('asset.list.tagsEmpty')}
              </span>
            );
          }
          const visible = tags.slice(0, 3);
          const rest = tags.length - visible.length;
          return (
            <Space size={4} wrap data-testid={`asset-tags-${record.id}`} data-tag-count={tags.length}>
              {visible.map((tag) => (
                <Tag key={tag} color="blue">
                  {tag}
                </Tag>
              ))}
              {rest > 0 ? (
                <Tag data-testid={`asset-tags-more-${record.id}`}>{t('asset.list.tagsMore', { count: rest })}</Tag>
              ) : null}
            </Space>
          );
        },
      },
      {
        title: t('asset.list.colAction'),
        key: 'action',
        width: 230,
        // t176：1680 宽下操作列会被挤出视口（编辑链接 x≈1959），固定到右侧保证可点
        fixed: 'right' as const,
        render: (_: unknown, record: AssetVO) => (
          <Space size="middle">
            <a onClick={() => navigate(`/assets/${record.id}`)} data-testid={`asset-open-${record.id}`}>
              {t('asset.list.enter')}
            </a>
            <a onClick={() => handleRequestAccess(record)}>{t('asset.list.requestAccess')}</a>
            <a onClick={() => navigate(`/assets/${record.id}/edit`)} data-testid={`asset-edit-${record.id}`}>
              {t('asset.list.editAsset')}
            </a>
            <a style={{ color: 'red' }} onClick={() => handleDelete(record.id)}>
              {t('asset.list.deleteAsset')}
            </a>
          </Space>
        ),
      },
    ],
     
    [tagsByAsset, t, navigate],
  );

  const hasLinkFilter = Boolean(params.customer_id || params.project_id || params.phase_id);

  return (
    <Card
      title={t('asset.list.title')}
      extra={
        allowCreateAsset ? (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/assets/new')}
            data-testid="asset-list-create"
          >
            {t('asset.list.new')}
          </Button>
        ) : null
      }
      data-testid="asset-list-page"
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          placeholder={t('asset.list.searchPlaceholder')}
          prefix={<SearchOutlined />}
          style={{ width: 260 }}
          allowClear
          defaultValue={params.search}
          onPressEnter={(e) => setParams((prev) => ({ ...prev, search: e.currentTarget.value, page: 1 }))}
          data-testid="asset-filter-search"
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          style={{ width: 200 }}
          placeholder={t('asset.list.filterCustomerPlaceholder')}
          value={params.customer_id}
          options={customers.map((customer) => ({ value: customer.id, label: customer.name }))}
          loading={customerOptionsState.status === 'loading'}
          data-testid="asset-filter-customer-select"
          notFoundContent={
            <CustomerSelectEmpty
              compact
              status={customerOptionsState.status}
              detail={customerOptionsState.detail}
              onRetry={() => void customerOptionsState.reload()}
            />
          }
          onChange={(value?: string) => {
            setParams((prev) => ({ ...prev, customer_id: value, project_id: undefined, phase_id: undefined, page: 1 }));
            void loadFilterProjects(value);
          }}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          style={{ width: 220 }}
          disabled={!params.customer_id}
          placeholder={t('asset.list.filterProjectPlaceholder')}
          value={params.project_id}
          options={filterProjects.map((project) => ({ value: project.id, label: `${project.name}（${project.code}）` }))}
          onChange={(value?: string) => {
            setParams((prev) => ({ ...prev, project_id: value, phase_id: undefined, page: 1 }));
            void loadFilterPhases(value);
          }}
        />
        <Select
          allowClear
          style={{ width: 180 }}
          disabled={!params.project_id || filterPhases.length === 0}
          placeholder={t('asset.list.filterPhasePlaceholder')}
          value={params.phase_id}
          options={filterPhases.map((phase) => ({ value: phase.id, label: `${phase.phaseNo ?? '-'}. ${phase.name ?? ''}` }))}
          onChange={(value?: string) => setParams((prev) => ({ ...prev, phase_id: value, page: 1 }))}
        />
        {hasLinkFilter ? (
          <Button
            onClick={() => {
              setParams((prev) => ({ ...prev, customer_id: undefined, project_id: undefined, phase_id: undefined, page: 1 }));
              setFilterProjects([]);
              setFilterPhases([]);
            }}
          >
            {t('asset.list.clearLinkFilter')}
          </Button>
        ) : null}
        {/* 标签筛选（多选，AND/OR 可切换）：外层 div 承载 testid（antd Select 不转发 data-testid） */}
        <div data-testid="asset-filter-tags-select">
          <Select
            mode="multiple"
            allowClear
            showSearch
            optionFilterProp="label"
            style={{ minWidth: 220, maxWidth: 360 }}
            placeholder={t('asset.list.filterTagsPlaceholder')}
            value={params.tags ?? []}
            options={tagOptions}
            onChange={(value: string[]) => setParams((prev) => ({ ...prev, tags: value, page: 1 }))}
          />
        </div>
        <Space size={6} data-testid="asset-filter-tags-mode" data-mode={params.tag_mode ?? 'and'}>
          <Switch
            size="small"
            checked={params.tag_mode === 'or'}
            onChange={(checked) => setParams((prev) => ({ ...prev, tag_mode: checked ? 'or' : 'and', page: 1 }))}
            data-testid="asset-filter-tags-mode-switch"
          />
          <span style={{ fontSize: 12, color: '#6b7280' }}>
            {params.tag_mode === 'or' ? t('asset.list.tagModeOr') : t('asset.list.tagModeAnd')}
          </span>
        </Space>
        {isAdmin ? (
          <Space size={6}>
            <Switch
              size="small"
              checked={showDeleted}
              onChange={(checked) => setShowDeleted(checked)}
              data-testid="asset-show-deleted-switch"
            />
            <span style={{ fontSize: 12, color: '#6b7280' }}>{t('asset.list.showDeleted')}</span>
          </Space>
        ) : null}
      </Space>

      {/* 分组已并入标签（t141/t143） */}
      <Alert
        type="info"
        showIcon
        closable
        style={{ marginBottom: 12 }}
        message={t('asset.list.groupMergedNotice')}
        data-testid="asset-groups-merged-notice"
      />

      {showDeleted && isAdmin ? (
        <SoftDeletedTable kind="assets" keyword={params.search} onRestored={() => void fetchData()} />
      ) : (
        <Table<AssetVO>
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          data-testid="asset-table"
          scroll={{ x: 'max-content' }}
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

export default Assets;


