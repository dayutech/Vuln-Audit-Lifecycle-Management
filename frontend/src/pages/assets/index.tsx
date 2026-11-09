import React, { useEffect, useState } from 'react';
import {
  Alert,
  Table,
  Button,
  Space,
  Input,
  Tag,
  Card,
  Modal,
  Form,
  Select,
  Tooltip,
  Switch,
  message,
} from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import {
  getAssetList,
  createAsset,
  updateAsset,
  deleteAsset,
  getAssetOptions,
  createAssetAccessRequest,
} from '@/api/asset';
import { getTagOptions, inlineTagNames, joinTagNames } from '@/api/tag';
import SoftDeletedTable from '@/components/softDelete/SoftDeletedTable';
import type {
  AssetVO,
  CreateAssetRequest,
  UpdateAssetRequest,
} from '@/types/asset';
import { getTeamList } from '@/api/team';
import CustomerSelectEmpty from '@/components/customer/CustomerSelectEmpty';
import { useCustomerOptions } from '@/hooks/useCustomerOptions';
import { getProjectDetail, getProjectList, extractPhaseList } from '@/api/project';
import type { PhaseVO, ProjectVO } from '@/types/project';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { canCreateAsset } from '@/utils/accessRoles';
import type { TeamVO } from '@/types/team';
import { useT } from '@/i18n';
import type { MessageKey } from '@/i18n/messages/zh-CN';

const { TextArea } = Input;

/** 项目详情响应里的期次在 data.phases（不是数组/分页），统一取一次 */
const extractDetailPhases = (res: any): PhaseVO[] => {
  const payload = res?.data ?? res;
  const fromDetail = payload?.phases;
  if (Array.isArray(fromDetail)) return fromDetail as PhaseVO[];
  return extractPhaseList(res);
};

/** POST /assets 403 兜底（后端 canCreateAssetInTeam 拒绝） */
const ASSET_CREATE_FORBIDDEN_FALLBACK = '无权在该团队下创建资产（需为管理员 / 项目负责人 / 小组长且有团队范围）';

const Assets: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  // t49 起 POST /assets 仅 admin/project_leader/team_leader：工程师不渲染新建入口
  const { profile, loaded: profileLoaded } = useAccessProfile();
  const allowCreateAsset = profileLoaded && canCreateAsset(profile);
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
    /** t141/t143 标签筛选：名称列表（AND / OR 由 tag_mode 决定） */
    tags?: string[];
    tag_mode?: 'and' | 'or';
  }>({ page: 1, page_size: 10, search: '' });
  /** t143：标签下拉（带 assetCount，后端按可见范围收敛） */
  const [tagOptions, setTagOptions] = useState<{ label: string; value: string }[]>([]);
  /** t143：每行标签（后端列表 VO 无 tags 字段，按行批量取；见 api/tag.ts 注释） */
  const [tagsByAsset, setTagsByAsset] = useState<Record<string, string[]>>({});
  /** t143：含已删面板（仅 ADMIN 可见） */
  const [showDeleted, setShowDeleted] = useState(false);
  const isAdmin = profileLoaded && Boolean(profile?.roles?.includes('admin'));
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [currentAsset, setCurrentAsset] = useState<AssetVO | null>(null);
  const [teams, setTeams] = useState<TeamVO[]>([]);
  const [targetTypeOptions, setTargetTypeOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [targetSourceOptions, setTargetSourceOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [priorityOptions, setPriorityOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [statusOptions, setStatusOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [form] = Form.useForm();

  /* ---- t73 项目挂靠：客户 → 项目 → 期次 三级联动 ---- */
  // t139：客户下拉带状态（失败不再静默变成空列表）
  const customerOptionsState = useCustomerOptions();
  const customers = customerOptionsState.items;
  /** 筛选区的项目/期次选项 */
  const [filterProjects, setFilterProjects] = useState<ProjectVO[]>([]);
  const [filterPhases, setFilterPhases] = useState<PhaseVO[]>([]);
  /** 新建表单的项目/期次选项（按表单里选择的客户过滤） */
  const [formProjects, setFormProjects] = useState<ProjectVO[]>([]);
  const [formPhases, setFormPhases] = useState<PhaseVO[]>([]);
  const [formCustomerId, setFormCustomerId] = useState<string | undefined>(undefined);
  const [projectMissing, setProjectMissing] = useState(false);

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

  const fetchData = async () => {
    setLoading(true);
    try {
      // t143：标签筛选按模式选 tags(AND)/tagsAny(OR)，与客户/项目/期次一起交给后端取交集
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
      const totalCount = payload.totalElements || payload.total || 0;
      setData(items.map((item: AssetVO) => normalizeAsset(item)));
      setTotal(totalCount);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  /** 筛选区：选定客户后按客户过滤项目（未选客户 = 该项目列表为空，避免全量拉取） */
  const loadFilterProjects = async (customerId?: string) => {
    if (!customerId) {
      setFilterProjects([]);
      setFilterPhases([]);
      return;
    }
    try {
      const res = await getProjectList({ page: 1, page_size: 200, customerId });
      const payload = (res as any)?.data ?? res;
      const items = payload?.content || payload?.items || [];
      setFilterProjects(Array.isArray(items) ? items : []);
    } catch {
      setFilterProjects([]);
    }
  };

  /** 筛选区：选定项目后拉该项目的期次（仅分期项目有期次） */
  const loadFilterPhases = async (projectId?: string) => {
    if (!projectId) {
      setFilterPhases([]);
      return;
    }
    try {
      const res = await getProjectDetail(projectId);
      setFilterPhases(extractDetailPhases(res));
    } catch {
      setFilterPhases([]);
    }
  };

  /** 新建表单：客户 → 项目 */
  const loadFormProjects = async (customerId?: string) => {
    if (!customerId) {
      setFormProjects([]);
      setFormPhases([]);
      return;
    }
    try {
      const res = await getProjectList({ page: 1, page_size: 200, customerId });
      const payload = (res as any)?.data ?? res;
      const items = payload?.content || payload?.items || [];
      setFormProjects(Array.isArray(items) ? items : []);
    } catch {
      setFormProjects([]);
    }
  };

  /** 新建表单：项目 → 期次（项目启用分期时才有意义） */
  const loadFormPhases = async (projectId?: string) => {
    const project = formProjects.find((item) => item.id === projectId);
    if (!projectId || !project?.isPhased) {
      setFormPhases([]);
      return;
    }
    try {
      const res = await getProjectDetail(projectId);
      setFormPhases(extractDetailPhases(res));
    } catch {
      setFormPhases([]);
    }
  };

  const fetchTeams = async () => {
    try {
      const res = await getTeamList();
      const payload = unwrapPayload(res);
      const items = payload.content || payload.items || [];
      setTeams(Array.isArray(items) ? items : []);
    } catch (error) {
      setTeams([]);
    }
  };

  const fetchAssetOptions = async () => {
    try {
      const res: any = await getAssetOptions();
      const payload = res?.data?.data ?? res?.data ?? res;
      const types = Array.isArray(payload?.targetTypes) ? payload.targetTypes : [];
      const sources = Array.isArray(payload?.targetSources) ? payload.targetSources : [];
      const priorities = Array.isArray(payload?.priorities) ? payload.priorities : [];
      const statuses = Array.isArray(payload?.statuses) ? payload.statuses : [];
      setTargetTypeOptions(types);
      setTargetSourceOptions(sources);
      setPriorityOptions(priorities);
      setStatusOptions(statuses);
      /**
       * 选项是异步到达的（handleCreate 时可能还是空数组），这里给「新建」表单补一次默认值，
       * 避免四个必填下拉全空导致「确定」被表单静默拦下（t73 实测：请选择资产类型/来源）。
       * 用户已手选的值不覆盖。
       */
      if (!form.getFieldValue('target_type') && types[0]) form.setFieldValue('target_type', types[0].value);
      if (!form.getFieldValue('target_source') && sources[0]) form.setFieldValue('target_source', sources[0].value);
      if (!form.getFieldValue('priority') && priorities[0]) form.setFieldValue('priority', priorities[0].value);
      if (!form.getFieldValue('status') && statuses[0]) form.setFieldValue('status', statuses[0].value);
    } catch (error) {
      setTargetTypeOptions([]);
      setTargetSourceOptions([]);
      setPriorityOptions([]);
      setStatusOptions([]);
    }
  };

  useEffect(() => {
    fetchData();
  }, [params.page, params.page_size, params.search, params.customer_id, params.project_id, params.phase_id, params.tags, params.tag_mode]);

  /** t143：标签下拉 + 加载一次（失败静默 → 空列表，不影响资产列表本身） */
  useEffect(() => {
    getTagOptions().then((options) => {
      setTagOptions(
        options
          .filter((option) => option.name)
          .map((option) => ({ label: `${option.name}（${option.assetCount}）`, value: option.name })),
      );
    });
  }, []);

  /**
   * t149：标签直接取列表内联字段（后端 t145 `AssetVO.tags`），**零额外请求**。
   * 旧实现按行 `GET /assets/{id}/tags`（一页 20 行 = 20 请求）已删除。
   */
  useEffect(() => {
    const map: Record<string, string[]> = {};
    (data ?? []).forEach((item) => {
      map[item.id] = inlineTagNames((item as any).tags);
    });
    setTagsByAsset(map);
  }, [data]);

  useEffect(() => {
    fetchTeams();
    fetchAssetOptions();
  }, []);

  const handleCreate = () => {
    setProjectMissing(true);
    setFormProjects([]);
    setFormPhases([]);
    setFormCustomerId(undefined);
    setModalMode('create');
    form.resetFields();
    form.setFieldsValue({
      priority: priorityOptions[0]?.value,
      status: statusOptions[0]?.value,
      // 类型/来源取首项默认值：必填项空着会让「确定」被表单静默拦下（t73 实测）
      target_type: targetTypeOptions[0]?.value,
      target_source: targetSourceOptions[0]?.value,
    });
    setCurrentAsset(null);
    setIsModalVisible(true);
  };

  const handleEdit = (record: AssetVO) => {
    setModalMode('edit');
    setCurrentAsset(record);
    form.setFieldsValue({
      name: record.name,
      team_id: record.team_id || record.teamId,
      target_type: record.target_type,
      target_source: record.target_source,
      description: record.description,
      priority: record.priority,
      status: record.status,
    });
    setIsModalVisible(true);
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: t('common.deleteConfirmTitle'),
      content: t('asset.list.deleteContent'),
      onOk: async () => {
        try {
          await deleteAsset(id);
          message.success(t('asset.list.deleteSuccess'));
          fetchData();
        } catch (error) {
          // Error handled by interceptor
        }
      }
    });
  };

  /**
   * 在列表页直接发起访问申请。
   *
   * 说明：未获授权的用户打不开资产详情页（详情接口会返回 403），
   * 因此申请入口必须同时存在于列表页，否则工程师无法走「申请 → 审批 → 可见」流程。
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
            onChange={(value) => { canViewFiles = value; }}
            options={[
              { value: true, label: t('asset.request.needFiles') },
              { value: false, label: t('asset.request.basicOnly') },
            ]}
          />
        </div>
      ),
      okText: t('asset.request.submit'),
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
      }
    });
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      if (modalMode === 'create') {
        // t69 硬约束：projectId 必填（表单里已禁用提交，这里再兜一层）
        if (!values.project_id) {
          setProjectMissing(true);
          return;
        }
        await createAsset({
          ...values,
          project_id: values.project_id,
          phase_id: values.phase_id || undefined,
          link_type: values.link_type || undefined,
          remark: values.remark || undefined,
        } as CreateAssetRequest);
        message.success(t('asset.list.createSuccess'));
      } else {
        if (currentAsset) {
          // 编辑只改资产本体：项目挂靠走详情页「所属项目」页签（t73），
          // 这里显式剔除挂靠字段（编辑态表单本来不渲染它们，保持防御）
          const rest = { ...values } as Record<string, any>;
          ['customer_id', 'project_id', 'phase_id', 'link_type', 'remark'].forEach((field) => {
            delete rest[field];
          });
          await updateAsset(currentAsset.id, rest as UpdateAssetRequest);
          message.success(t('asset.list.updateSuccess'));
        }
      }
      setIsModalVisible(false);
      fetchData();
    } catch (error) {
      if ((error as any)?.errorFields) {
        // 表单校验失败：交给 Form 自己展示；缺项目时同步置灰提交
        if ((error as any)?.errorFields?.some((item: any) => String(item?.name ?? '').includes('project_id'))) {
          setProjectMissing(true);
        }
        return;
      }
      const status = getApiErrorStatus(error);
      if (status === 400) {
        message.error(getApiErrorMessage(error, t('asset.projectLink.createRequired')));
        return;
      }
      if (status === 404) {
        message.error(t('asset.projectLink.notFound'));
        return;
      }
      if (status === 409) {
        message.error(getApiErrorMessage(error, t('asset.projectLink.duplicate')));
        return;
      }
      if (status === 403) {
        message.error(getApiErrorMessage(error, ASSET_CREATE_FORBIDDEN_FALLBACK));
        return;
      }
      message.error(getApiErrorMessage(error, t('common.operationFailed')));
    }
  };

  const columns = [
    { 
      title: t('asset.list.colName'), 
      dataIndex: 'name', 
      key: 'name',
      render: (text: string, record: AssetVO) => (
        <a onClick={() => navigate(`/assets/${record.id}`)} style={{ fontWeight: 500 }}>{text}</a>
      )
    },
    { title: t('asset.list.colCode'), dataIndex: 'code', key: 'code' },
    { 
      title: t('asset.list.colTeam'), 
      dataIndex: 'team_name', 
      key: 'team_name',
      render: (text: string) => text || '-'
    },
    { 
      title: t('asset.list.colOwner'), 
      dataIndex: 'owner_name', 
      key: 'owner_name',
      render: (text: string) => text || '-'
    },
    {
      title: t('asset.list.colCustomer'),
      key: 'customers',
      width: 200,
      render: (_: unknown, record: AssetVO) => {
        const list = record.customers ?? [];
        if (list.length === 0) return '-';
        const visible = list.slice(0, 2);
        const rest = list.length - visible.length;
        return (
          <Space size={4} wrap>
            {visible.map((item) => (
              <Tag key={item.id} color="geekblue">{item.name ?? item.code ?? item.id}</Tag>
            ))}
            {rest > 0 ? (
              <Tooltip title={list.slice(2).map((item) => item.name ?? item.code ?? item.id).join('、')}>
                <Tag>{t('asset.list.moreLinks', { count: rest })}</Tag>
              </Tooltip>
            ) : null}
          </Space>
        );
      },
    },
    {
      title: t('asset.list.colProject'),
      key: 'projects',
      width: 240,
      render: (_: unknown, record: AssetVO) => {
        const list = record.projects ?? [];
        if (list.length === 0) return '-';
        const visible = list.slice(0, 2);
        const rest = list.length - visible.length;
        return (
          <Space size={4} wrap>
            {visible.map((item) => (
              <Tag key={item.id} color="blue" style={{ cursor: 'pointer' }} onClick={() => navigate(`/projects/${item.id}`)}>
                {item.name ?? item.code ?? item.id}
              </Tag>
            ))}
            {rest > 0 ? (
              <Tooltip title={list.slice(2).map((item) => item.name ?? item.code ?? item.id).join('、')}>
                <Tag>{t('asset.list.moreLinks', { count: rest })}</Tag>
              </Tooltip>
            ) : null}
          </Space>
        );
      },
    },
    {
      title: t('asset.list.colCustomer'),
      key: 'customers',
      width: 200,
      render: (_: unknown, record: AssetVO) => {
        const list = record.customers ?? [];
        if (list.length === 0) return '-';
        const visible = list.slice(0, 2);
        const rest = list.length - visible.length;
        return (
          <Space size={4} wrap>
            {visible.map((item) => (
              <Tag key={item.id} color="geekblue">{item.name ?? item.code ?? item.id}</Tag>
            ))}
            {rest > 0 ? (
              <Tooltip title={list.slice(2).map((item) => item.name ?? item.code ?? item.id).join('、')}>
                <Tag>{t('asset.list.moreLinks', { count: rest })}</Tag>
              </Tooltip>
            ) : null}
          </Space>
        );
      },
    },
    {
      title: t('asset.list.colProject'),
      key: 'projects',
      width: 240,
      render: (_: unknown, record: AssetVO) => {
        const list = record.projects ?? [];
        if (list.length === 0) return '-';
        const visible = list.slice(0, 2);
        const rest = list.length - visible.length;
        return (
          <Space size={4} wrap>
            {visible.map((item) => (
              <Tag
                key={item.id}
                color="blue"
                style={{ cursor: 'pointer' }}
                onClick={() => navigate(`/projects/${item.id}`)}
              >
                {item.name ?? item.code ?? item.id}
              </Tag>
            ))}
            {rest > 0 ? (
              <Tooltip title={list.slice(2).map((item) => item.name ?? item.code ?? item.id).join('、')}>
                <Tag>{t('asset.list.moreLinks', { count: rest })}</Tag>
              </Tooltip>
            ) : null}
          </Space>
        );
      },
    },
    { title: t('asset.list.colType'), dataIndex: 'target_type', key: 'target_type' },    { title: t('asset.list.colSource'), dataIndex: 'target_source', key: 'target_source' },
    { 
      title: t('asset.list.colPriority'), 
      dataIndex: 'priority', 
      key: 'priority',
      render: (text: string) => {
        const colors: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue', P3: 'green' };
        return <Tag color={colors[text] || 'default'}>{text}</Tag>;
      }
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
      }
    },
    {
      title: t('asset.list.colTags'),
      key: 'tags',
      width: 220,
      render: (_: unknown, record: AssetVO) => {
        const tags = tagsByAsset[record.id] ?? [];
        if (tags.length === 0) {
          return <span style={{ color: '#bfbfbf' }} data-testid={`asset-tags-empty-${record.id}`}>{t('asset.list.tagsEmpty')}</span>;
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
            {rest > 0 ? <Tag data-testid={`asset-tags-more-${record.id}`}>{t('asset.list.tagsMore', { count: rest })}</Tag> : null}
          </Space>
        );
      },
    },
    {
      title: t('asset.list.colAction'),
      key: 'action',
      render: (_: any, record: AssetVO) => (
        <Space size="middle">
          <a onClick={() => navigate(`/assets/${record.id}`)}>{t('asset.list.enter')}</a>
          <a onClick={() => handleRequestAccess(record)}>{t('asset.list.requestAccess')}</a>
          <a onClick={() => handleEdit(record)}>{t('asset.list.editAsset')}</a>
          <a style={{ color: 'red' }} onClick={() => handleDelete(record.id)}>{t('asset.list.deleteAsset')}</a>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={t('asset.list.title')}
      extra={
        allowCreateAsset ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>{t('asset.list.new')}</Button>
        ) : null
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          placeholder={t('asset.list.searchPlaceholder')}
          prefix={<SearchOutlined />}
          style={{ width: 260 }}
          allowClear
          onPressEnter={(e) => setParams({ ...params, search: e.currentTarget.value, page: 1 })}
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
          // t139：失败/无客户时给出指引，而不是空白下拉
          notFoundContent={
            <CustomerSelectEmpty
              compact
              status={customerOptionsState.status}
              detail={customerOptionsState.detail}
              onRetry={() => void customerOptionsState.reload()}
            />
          }
          onChange={(value) => {
            setParams((prev) => ({ ...prev, customer_id: value, project_id: undefined, phase_id: undefined, page: 1 }));
            loadFilterProjects(value);
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
          onChange={(value) => {
            setParams((prev) => ({ ...prev, project_id: value, phase_id: undefined, page: 1 }));
            loadFilterPhases(value);
          }}
        />
        <Select
          allowClear
          style={{ width: 180 }}
          disabled={!params.project_id || filterPhases.length === 0}
          placeholder={t('asset.list.filterPhasePlaceholder')}
          value={params.phase_id}
          options={filterPhases.map((phase) => ({ value: phase.id, label: `${phase.phaseNo ?? '-'}. ${phase.name ?? ''}` }))}
          onChange={(value) => setParams((prev) => ({ ...prev, phase_id: value, page: 1 }))}
        />
        {params.customer_id || params.project_id || params.phase_id ? (
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
        {/* t143：标签筛选（多选，AND/OR 可切换）；外层 div 承载 testid（antd Select 不转发 data-testid）*/}
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
        {/* t143：含已删（仅 ADMIN；后端 /admin/assets?includeDeleted=true）*/}
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
      {/* t143：分组已并入标签 */}
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
      <Table 
        columns={columns} 
        dataSource={data} 
        rowKey="id"
        loading={loading}
        data-testid="asset-table"
        pagination={{
          current: params.page,
          pageSize: params.page_size,
          total,
          onChange: (page, pageSize) => setParams({ ...params, page, page_size: pageSize })
        }}
      />
      )}

      <Modal
        title={modalMode === 'create' ? t('asset.form.createTitle') : t('asset.form.editTitle')}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
        okButtonProps={{ disabled: modalMode === 'create' && projectMissing }}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('asset.list.colName')} rules={[{ required: true, message: t('asset.form.nameRequired') }]}>
            <Input />
          </Form.Item>
          {modalMode === 'create' ? (
            <>
              <Alert
                type={projectMissing ? 'warning' : 'info'}
                showIcon
                style={{ marginBottom: 12 }}
                message={t('asset.projectLink.createRequired')}
              />
              <Form.Item
                name="customer_id"
                label={t('asset.projectLink.customerLabel')}
                rules={[{ required: true, message: t('asset.projectLink.customerPlaceholder') }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder={t('asset.projectLink.customerPlaceholder')}
                  options={customers.map((customer) => ({ value: customer.id, label: customer.name }))}
                  loading={customerOptionsState.status === 'loading'}
                  data-testid="asset-form-customer-select"
                  // t139：三级联动的第一级同样要能解释「为什么是空的」
                  notFoundContent={
                    <CustomerSelectEmpty
                      compact
                      status={customerOptionsState.status}
                      detail={customerOptionsState.detail}
                      onRetry={() => void customerOptionsState.reload()}
                    />
                  }
                  onChange={(value) => {
                    setFormCustomerId(value);
                    form.setFieldValue('project_id', undefined);
                    form.setFieldValue('phase_id', undefined);
                    setFormPhases([]);
                    setProjectMissing(true);
                    loadFormProjects(value);
                  }}
                />
              </Form.Item>
              <Form.Item
                name="project_id"
                label={t('asset.projectLink.projectLabel')}
                rules={[{ required: true, message: t('asset.projectLink.projectRequired') }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  disabled={!formCustomerId}
                  placeholder={t('asset.projectLink.projectPlaceholder')}
                  options={formProjects.map((project) => ({ value: project.id, label: `${project.name}（${project.code}）` }))}
                  onChange={(value) => {
                    form.setFieldValue('phase_id', undefined);
                    setProjectMissing(!value);
                    loadFormPhases(value);
                  }}
                />
              </Form.Item>
              <Form.Item
                name="phase_id"
                label={t('asset.projectLink.phaseLabel')}
                extra={formPhases.length === 0 ? t('asset.projectLink.phasePlaceholder') : undefined}
              >
                <Select
                  allowClear
                  disabled={formPhases.length === 0}
                  placeholder={t('asset.projectLink.phasePlaceholder')}
                  options={formPhases.map((phase) => ({ value: phase.id, label: `${phase.phaseNo ?? '-'}. ${phase.name ?? ''}` }))}
                />
              </Form.Item>
              <Form.Item name="link_type" label={t('asset.projectLink.linkTypeLabel')} initialValue="TARGET">
                <Select
                  options={[
                    { value: 'TARGET', label: t('asset.projectLink.linkTypeTARGET') },
                    { value: 'SHARED', label: t('asset.projectLink.linkTypeSHARED') },
                    { value: 'REFERENCE', label: t('asset.projectLink.linkTypeREFERENCE') },
                  ]}
                />
              </Form.Item>
              <Form.Item name="remark" label={t('asset.projectLink.remarkLabel')}>
                <Input.TextArea rows={2} maxLength={500} placeholder={t('asset.projectLink.remarkPlaceholder')} />
              </Form.Item>
            </>
          ) : null}
          <Form.Item name="team_id" label={t('asset.list.colTeam')} rules={[{ required: true, message: t('asset.form.teamRequired') }]}>
            <Select placeholder={t('asset.form.teamRequired')} showSearch optionFilterProp="label" options={teams.map((team) => ({ label: team.name, value: team.id }))} />
          </Form.Item>
          <Form.Item name="target_type" label={t('asset.form.targetType')} rules={[{ required: true, message: t('asset.form.targetTypeRequired') }]}>
            <Select
              placeholder={t('asset.form.targetTypeRequired')}
              options={targetTypeOptions}
              disabled={targetTypeOptions.length === 0}
            />
          </Form.Item>
          <Form.Item name="target_source" label={t('asset.form.targetSource')} rules={[{ required: true, message: t('asset.form.targetSourceRequired') }]}>
            <Select
              placeholder={t('asset.form.targetSourceRequired')}
              options={targetSourceOptions}
              disabled={targetSourceOptions.length === 0}
            />
          </Form.Item>
          <Form.Item name="priority" label={t('asset.list.colPriority')} rules={[{ required: true, message: t('asset.form.priorityRequired') }]}>
            <Select placeholder={t('asset.form.priorityRequired')} options={priorityOptions} />
          </Form.Item>
          <Form.Item name="status" label={t('asset.list.colStatus')} rules={[{ required: true, message: t('asset.form.statusRequired') }]}>
            <Select placeholder={t('asset.form.statusRequired')} options={statusOptions} />
          </Form.Item>
          <Form.Item name="description" label={t('asset.form.description')}>
            <TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Assets;





