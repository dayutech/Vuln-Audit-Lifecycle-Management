import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ArrowLeftOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  createVulnerability,
  getVulnerabilityDetail,
  getVulnerabilityList,
  getVulnerabilityOptions,
  updateExploitStage,
  updateVulnerability,
} from '@/api/vulnerability';
import type { CreateVulnerabilityRequest, VulnerabilityVO } from '@/types/vulnerability';
import { getAssetList, getAssetVersions } from '@/api/asset';
import type { AssetVersionVO, AssetVO } from '@/types/asset';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT, USER_DIRECTORY_LIMIT_HINT, mergeUserDirectory } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import VulnerabilityDetail from './Detail';
import {
  collectUnexpectedFieldDiffs,
  describeVulnCreateFailure,
  describeVulnUpdateFailure,
  isFormValidationError,
  VULN_CREATE_FOREIGN_ASSET_HINT,
} from './createError';
import { toVulnerabilityCreatePayload, toVulnerabilityUpdatePayload } from './createPayload';
import { getMyAccessProfile, isManagementProfile, type MyAccessProfile } from '@/api/permission';
import { useT } from '@/i18n';
import type { MessageKey } from '@/i18n/messages/zh-CN';
import {
  SEVERITY_OPTIONS,
  WORKFLOW_STATUS_OPTIONS,
  customerColor,
  customerKey,
  disclosureColor,
  disclosureKey,
  fieldTestKey,
  severityColor,
  severityKey,
  workflowColor,
  workflowKey,
} from './constants';

const { Option } = Select;
const { TextArea } = Input;

const getPayload = (res: any) => (res as any)?.data ?? res;
const normalizeText = (value?: string | null) => {
  const nextValue = value?.trim();
  return nextValue ? nextValue : undefined;
};
const readText = (source: any, camel: string, snake: string): string | undefined => {
  const value = source?.[camel] ?? source?.[snake];
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  return String(value);
};

const Vulnerabilities: React.FC = () => {
  const { t } = useT();
  const location = useLocation();
  const navigate = useNavigate();
  const { id: vulnerabilityId } = useParams();
  const isEditPage = Boolean(vulnerabilityId && location.pathname.endsWith('/edit'));

  /** 状态 / 严重程度等枚举统一走 t()，避免「标签英文、表格中文」的割裂 */
  const labelOf = (key: MessageKey | undefined, fallback?: string | null) => (key ? t(key) : fallback || '-');

  const [data, setData] = useState<VulnerabilityVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<VulnerabilityVO | null>(null);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState<{ page: number; page_size: number; search: string; severity?: string; workflow_status?: string }>({
    page: 1,
    page_size: 10,
    search: '',
  });
  const [searchText, setSearchText] = useState('');

  // 详情抽屉
  const [drawerId, setDrawerId] = useState<string | null>(null);

  // 新建 / 编辑表单
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [createSubmitting, setCreateSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [editPageForm] = Form.useForm();
  const [typeOptions, setTypeOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [assets, setAssets] = useState<AssetVO[]>([]);
  const [assetVersions, setAssetVersions] = useState<AssetVersionVO[]>([]);
  const [createAssetVersions, setCreateAssetVersions] = useState<AssetVersionVO[]>([]);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  /** 处理人下拉改成后端搜索（keyword 走 /users/directory） */
  const [userDirectoryLimitHit, setUserDirectoryLimitHit] = useState(false);
  const userSearchTimer = useRef<number | undefined>(undefined);
  const handleUserSearch = (keyword: string) => {
    if (userSearchTimer.current) window.clearTimeout(userSearchTimer.current);
    userSearchTimer.current = window.setTimeout(async () => {
      const list = await getUserDirectory({ keyword, limit: USER_DIRECTORY_DEFAULT_LIMIT });
      setUsers((prev) => mergeUserDirectory(prev, list));
      setUserDirectoryLimitHit(list.length >= USER_DIRECTORY_DEFAULT_LIMIT);
    }, 300);
  };
  const [editPageSubmitting, setEditPageSubmitting] = useState(false);
  /** 当前账号权限画像：用于在「提交漏洞」表单里对非本人资产给出即时提示（t27 创建守卫） */
  const [profile, setProfile] = useState<MyAccessProfile | null>(null);
  const selectedCreateAssetId = Form.useWatch('asset_id', form);

  const assetNameMap = useMemo(() => {
    const map = new Map<string, string>();
    assets.forEach((asset) => {
      map.set(asset.id, asset.name || asset.code || asset.id);
    });
    return map;
  }, [assets]);

  useEffect(() => {
    getMyAccessProfile().then(setProfile);
  }, []);

  /**
   * 选中资产的写权限自检（无「可写资产」接口，只能按负责人 + 管理角色推断）：
   * 非负责人且非管理类角色时，在表单内即时提示，避免用户填完才吃 403。
   */
  const selectedAssetWritable = useMemo(() => {
    if (!selectedCreateAssetId || !profile) return true;
    const roles = profile.roles ?? [];
    if (roles.some((role) => ['admin', 'project_leader', 'team_leader'].includes(role))) return true;
    if (isManagementProfile(profile)) return true;
    const asset = assets.find((item) => item.id === selectedCreateAssetId);
    const ownerId = asset?.owner_id ?? asset?.ownerId;
    if (!ownerId || !profile.userId) return true;
    return String(ownerId) === String(profile.userId);
  }, [selectedCreateAssetId, profile, assets]);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getVulnerabilityList(params);
      const payload = getPayload(res);
      const items = payload?.content || payload?.items || [];
      const totalCount = payload?.totalElements || payload?.total || 0;
      setData(items);
      setTotal(totalCount);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [params]);

  const fetchDetail = useCallback(async (id: string) => {
    setDetailLoading(true);
    try {
      const res = await getVulnerabilityDetail(id);
      setDetail(getPayload(res) || null);
    } catch (error) {
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (vulnerabilityId) {
      fetchDetail(vulnerabilityId);
      return;
    }
    fetchData();
  }, [vulnerabilityId, fetchData, fetchDetail]);

  useEffect(() => {
    const search = new URLSearchParams(location.search).get('search') || '';
    setSearchText(search);
    setParams((prev) => (prev.search === search ? prev : { ...prev, search, page: 1 }));
  }, [location.search]);

  useEffect(() => {
    const fetchOptions = async () => {
      try {
        const res = await getVulnerabilityOptions();
        const payload = getPayload(res);
        const types = Array.isArray(payload?.types) ? payload.types : [];
        setTypeOptions(
          types
            .map((item: any) => ({
              value: String(item?.value ?? item?.label ?? ''),
              label: String(item?.label ?? item?.value ?? ''),
            }))
            .filter((item: { value: string }) => !!item.value),
        );
      } catch (error) {
        setTypeOptions([]);
      }
    };
    fetchOptions();
  }, []);

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        // 发现者/处理人下拉走用户目录（工程师可读）
        const list = await getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT });
        setUsers(list);
      } catch (error) {
        setUsers([]);
      }
    };
    fetchUsers();
  }, []);

  useEffect(() => {
    const fetchAssets = async () => {
      try {
        const res = await getAssetList({ page: 1, page_size: 200 });
        const payload = getPayload(res);
        setAssets(payload?.content || payload?.items || []);
      } catch (error) {
        setAssets([]);
      }
    };
    fetchAssets();
  }, []);

  const loadAssetVersions = useCallback(async (assetId?: string) => {
    if (!assetId) {
      return [];
    }
    try {
      const res = await getAssetVersions(assetId);
      const payload = getPayload(res);
      const source = Array.isArray(payload) ? payload : payload?.items || [];
      return source.map((item: any) => ({
        id: String(item?.id ?? ''),
        assetId: String(item?.assetId ?? item?.asset_id ?? assetId),
        versionNo: Number(item?.versionNo ?? item?.version_no ?? 0),
        name: item?.name ?? '',
        description: item?.description,
        isDefault: Boolean(item?.isDefault ?? item?.is_default),
        isLatest: Boolean(item?.isLatest ?? item?.is_latest),
        status: item?.status ?? 'active',
      })) as AssetVersionVO[];
    } catch (error) {
      return [] as AssetVersionVO[];
    }
  }, []);

  useEffect(() => {
    if (!detail || !isEditPage) {
      return;
    }
    const assetId = readText(detail, 'assetId', 'asset_id');
    loadAssetVersions(assetId).then(setAssetVersions);
    editPageForm.setFieldsValue({
      title: detail.title,
      cve_id: readText(detail, 'cveId', 'cve_id'),
      discovered_version_id: readText(detail, 'discoveredVersionId', 'discovered_version_id'),
      affected_versions: readText(detail, 'affectedVersions', 'affected_versions'),
      type: detail.type,
      severity: detail.severity,
      cvss_score: (detail as any).cvssScore ?? (detail as any).cvss_score,
      field_test_status: readText(detail, 'fieldTestStatus', 'field_test_status'),
      exploit_code: readText(detail, 'exploitCode', 'exploit_code'),
      field_test_result: readText(detail, 'fieldTestResult', 'field_test_result'),
      delivery_note: readText(detail, 'deliveryNote', 'delivery_note'),
      description: detail.description,
      detail: detail.detail,
      impact: readText(detail, 'impact', 'impact'),
      poc_code: readText(detail, 'pocCode', 'poc_code'),
      assigned_to: readText(detail, 'assignedTo', 'assigned_to'),
    });
  }, [detail, isEditPage, editPageForm, loadAssetVersions]);

  const handleCreate = () => {
    form.resetFields();
    setCreateAssetVersions([]);
    setIsModalVisible(true);
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      setCreateSubmitting(true);
      // 后端 @RequestBody Vulnerability 只认 camelCase，snake_case 会 400「资产ID不能为空」
      await createVulnerability(
        toVulnerabilityCreatePayload(values as Record<string, any>, (values as any).asset_id) as unknown as CreateVulnerabilityRequest
      );
      message.success(t('vuln.create.success'));
      setIsModalVisible(false);
      fetchData();
    } catch (error) {
      // 表单校验失败不再提示；接口失败按状态码给可读原因（403 → 无该资产写权限，t27 起生效）
      if (!isFormValidationError(error)) {
        message.error(describeVulnCreateFailure(error));
      }
    } finally {
      setCreateSubmitting(false);
    }
  };

  /** 编辑保存后用于「未修改字段是否被意外改动」的比对字段（t35 要求） */
  const UNEXPECTED_CHANGE_FIELDS: Array<{ key: string; labelKey: MessageKey }> = [
    { key: 'workflowStatus', labelKey: 'vuln.field.workflowStatus' },
    { key: 'customerStatus', labelKey: 'vuln.field.customerStatus' },
    { key: 'disclosureStatus', labelKey: 'vuln.field.disclosureStatus' },
    { key: 'exploitStage', labelKey: 'vuln.field.exploitStage' },
    { key: 'vendorFixed', labelKey: 'vuln.field.vendorFixed' },
    { key: 'severity', labelKey: 'vuln.field.severity' },
    { key: 'type', labelKey: 'vuln.field.type' },
    { key: 'cveId', labelKey: 'vuln.field.cveId' },
    { key: 'affectedVersions', labelKey: 'vuln.field.affectedVersions' },
    { key: 'discoveredVersionId', labelKey: 'vuln.field.discoveredVersion' },
    { key: 'assetId', labelKey: 'vuln.field.asset' },
  ];

  const handleEditPageSubmit = async () => {
    if (!detail || !vulnerabilityId) {
      return;
    }
    try {
      const values = await editPageForm.validateFields();
      setEditPageSubmitting(true);
      /**
       * t34 起 PUT /vulnerabilities/{id} 绑定 DTO（部分更新）：只覆盖请求里出现的字段，
       * 且 DTO 不含状态字段 → 状态一律不提交（PATCH 专用端点负责），这里只发 camelCase 白名单字段。
       */
      const payload = toVulnerabilityUpdatePayload(values);
      const snapshot = { ...(detail as unknown as Record<string, any>) };
      const submittedKeys = Object.keys(payload);

      await updateVulnerability(vulnerabilityId, payload);

      // 利用代码不在更新 DTO 内：改走 PATCH /exploit-stage（携带当前阶段，避免阶段被改）
      const nextExploitCode = normalizeText(values.exploit_code);
      const currentExploitCode = readText(detail, 'exploitCode', 'exploit_code');
      if (nextExploitCode !== undefined && nextExploitCode !== (currentExploitCode ?? undefined)) {
        const currentStage = readText(detail, 'exploitStage', 'exploit_stage') ?? '探测';
        await updateExploitStage(vulnerabilityId, currentStage, nextExploitCode);
        submittedKeys.push('exploitStage');
      }

      // 只刷新必要的详情（状态历史由详情页按需加载），用于稳定性校验与差异比对
      const freshRes = await getVulnerabilityDetail(vulnerabilityId);
      const fresh = (getPayload(freshRes) ?? null) as VulnerabilityVO | null;
      if (fresh) {
        setDetail(fresh);
        const diffs = collectUnexpectedFieldDiffs(
          snapshot,
          fresh as unknown as Record<string, any>,
          UNEXPECTED_CHANGE_FIELDS.map((field) => ({ key: field.key, label: t(field.labelKey) })),
          submittedKeys,
        );
        if (diffs.length > 0) {
          Modal.warning({
            title: t('vuln.edit.driftTitle'),
            width: 560,
            content: (
              <div>
                <p>{t('vuln.edit.driftIntro')}</p>
                <ul style={{ paddingLeft: 20, marginBottom: 0 }}>
                  {diffs.map((diff) => (
                    <li key={diff.label}>
                      <strong>{diff.label}</strong>：{diff.before || t('vuln.edit.driftEmpty')} → {diff.after || t('vuln.edit.driftEmpty')}
                    </li>
                  ))}
                </ul>
              </div>
            ),
          });
        } else {
          message.success(t('vuln.edit.success'));
        }
        // 用服务端最新值回填表单，保证「刷新后字段值稳定」
        editPageForm.setFieldsValue({
          title: fresh.title,
          cve_id: readText(fresh, 'cveId', 'cve_id'),
          discovered_version_id: readText(fresh, 'discoveredVersionId', 'discovered_version_id'),
          affected_versions: readText(fresh, 'affectedVersions', 'affected_versions'),
          type: fresh.type,
          severity: fresh.severity,
          cvss_score: (fresh as any).cvssScore ?? (fresh as any).cvss_score,
          exploit_code: readText(fresh, 'exploitCode', 'exploit_code'),
          description: fresh.description,
          detail: fresh.detail,
          impact: readText(fresh, 'impact', 'impact'),
          poc_code: readText(fresh, 'pocCode', 'poc_code'),
          assigned_to: readText(fresh, 'assignedTo', 'assigned_to'),
        });
      } else {
        message.success(t('vuln.edit.success'));
      }
    } catch (error) {
      if (!isFormValidationError(error)) {
        message.error(describeVulnUpdateFailure(error, t('vuln.edit.failed')));
      }
    } finally {
      setEditPageSubmitting(false);
    }
  };

  const columns = [
    {
      title: t('vuln.list.colNo'),
      key: 'vulnerabilityNo',
      width: 180,
      render: (_: unknown, record: VulnerabilityVO) => readText(record, 'vulnerabilityNo', 'vulnerability_no') ?? '-',
    },
    {
      title: t('vuln.list.colTitle'),
      dataIndex: 'title',
      key: 'title',
      render: (value: string, record: VulnerabilityVO) => (
        <a onClick={() => setDrawerId(record.id)}>{value || '-'}</a>
      ),
    },
    {
      title: t('vuln.list.colAsset'),
      key: 'asset',
      render: (_: unknown, record: VulnerabilityVO) => {
        const assetName = (record as any).asset?.name;
        const assetId = readText(record, 'assetId', 'asset_id');
        return assetName || (assetId ? assetNameMap.get(assetId) ?? assetId : '-');
      },
    },
    { title: t('vuln.list.colType'), dataIndex: 'type', key: 'type' },
    {
      title: t('vuln.list.colSeverity'),
      dataIndex: 'severity',
      key: 'severity',
      render: (text: string) => <Tag color={severityColor(text)}>{labelOf(severityKey(text), text)}</Tag>,
    },
    {
      title: t('vuln.list.colCvss'),
      key: 'cvssScore',
      width: 80,
      render: (_: unknown, record: VulnerabilityVO) => (record as any).cvssScore ?? (record as any).cvss_score ?? '-',
    },
    {
      title: t('vuln.list.colWorkflow'),
      key: 'workflowStatus',
      width: 120,
      render: (_: unknown, record: VulnerabilityVO) => {
        const status = readText(record, 'workflowStatus', 'workflow_status');
        return <Tag color={workflowColor(status)}>{labelOf(workflowKey(status), status)}</Tag>;
      },
    },
    {
      title: t('vuln.list.colCustomer'),
      key: 'customerStatus',
      width: 100,
      render: (_: unknown, record: VulnerabilityVO) => {
        const status = readText(record, 'customerStatus', 'customer_status');
        return <Tag color={customerColor(status)}>{labelOf(customerKey(status), status)}</Tag>;
      },
    },
    {
      title: t('vuln.list.colDisclosure'),
      key: 'disclosureStatus',
      width: 110,
      render: (_: unknown, record: VulnerabilityVO) => {
        const status = readText(record, 'disclosureStatus', 'disclosure_status');
        return <Tag color={disclosureColor(status)}>{labelOf(disclosureKey(status), status)}</Tag>;
      },
    },
    {
      title: t('vuln.list.colAction'),
      key: 'action',
      width: 180,
      render: (_: unknown, record: VulnerabilityVO) => (
        <Space size="middle">
          <a onClick={() => setDrawerId(record.id)}>{t('vuln.list.detail')}</a>
          <a onClick={() => navigate(`/vulnerabilities/${record.id}/edit`)}>{t('vuln.list.edit')}</a>
        </Space>
      ),
    },
  ];

  // ==================== 编辑页 ====================
  if (vulnerabilityId && isEditPage) {
    const workflowStatus = readText(detail ?? {}, 'workflowStatus', 'workflow_status');
    const customerStatus = readText(detail ?? {}, 'customerStatus', 'customer_status');
    const disclosureStatus = readText(detail ?? {}, 'disclosureStatus', 'disclosure_status');
    const fieldTestStatusValue = readText(detail ?? {}, 'fieldTestStatus', 'field_test_status');
    const fieldTestResultValue = readText(detail ?? {}, 'fieldTestResult', 'field_test_result');
    const deliveryNoteValue = readText(detail ?? {}, 'deliveryNote', 'delivery_note');

    return (
      <Card
        title={t('vuln.edit.title')}
        extra={
          <Space>
            <Button onClick={() => navigate(`/vulnerabilities/${vulnerabilityId}`)}>{t('vuln.edit.backDetail')}</Button>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/vulnerabilities')}>
              {t('vuln.edit.backList')}
            </Button>
            <Button type="primary" loading={editPageSubmitting} onClick={handleEditPageSubmit}>
              {t('vuln.edit.save')}
            </Button>
          </Space>
        }
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Descriptions
                title={detail.title || t('vuln.edit.unnamed')}
                column={{ xs: 1, sm: 2, md: 3 }}
                bordered
                size="small"
                items={[
                  {
                    key: 'no',
                    label: t('vuln.list.colNo'),
                    children: readText(detail, 'vulnerabilityNo', 'vulnerability_no') ?? '-',
                  },
                  {
                    key: 'asset',
                    label: t('vuln.field.asset'),
                    children:
                      assetNameMap.get(readText(detail, 'assetId', 'asset_id') ?? '') ??
                      readText(detail, 'assetId', 'asset_id') ??
                      '-',
                  },
                  {
                    key: 'createdAt',
                    label: t('vuln.detail.colCreatedAt'),
                    children: dayjs(readText(detail, 'createdAt', 'created_at')).format('YYYY-MM-DD HH:mm:ss'),
                  },
                  {
                    key: 'workflow',
                    label: t('vuln.field.workflowStatus'),
                    children: <Tag color={workflowColor(workflowStatus)}>{labelOf(workflowKey(workflowStatus), workflowStatus)}</Tag>,
                  },
                  {
                    key: 'customer',
                    label: t('vuln.field.customerStatus'),
                    children: <Tag color={customerColor(customerStatus)}>{labelOf(customerKey(customerStatus), customerStatus)}</Tag>,
                  },
                  {
                    key: 'disclosure',
                    label: t('vuln.field.disclosureStatus'),
                    children: <Tag color={disclosureColor(disclosureStatus)}>{labelOf(disclosureKey(disclosureStatus), disclosureStatus)}</Tag>,
                  },
                ]}
              />
              <Form form={editPageForm} layout="vertical">
                <Card title={t('vuln.edit.basicInfo')} size="small">
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item name="title" label={t('vuln.form.title')} rules={[{ required: true, message: t('vuln.form.titleRequired') }]}>
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="cve_id" label={t('vuln.form.cve')}>
                        <Input placeholder={t('vuln.form.cvePlaceholder')} />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="type" label={t('vuln.form.type')} rules={[{ required: true, message: t('vuln.form.typeRequired') }]}>
                        <Select options={typeOptions} placeholder={t('vuln.form.typePlaceholder')} />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="severity" label={t('vuln.form.severity')} rules={[{ required: true, message: t('vuln.form.severityRequired') }]}>
                        <Select options={SEVERITY_OPTIONS.map((item) => ({ value: item.value, label: t(item.labelKey) }))} />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="cvss_score" label={t('vuln.form.cvss')}>
                        <InputNumber min={0} max={10} step={0.1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="assigned_to" label={t('vuln.form.assignee')} extra={userDirectoryLimitHit ? USER_DIRECTORY_LIMIT_HINT : undefined}>
                        <Select
                          allowClear
                          showSearch
                          onSearch={handleUserSearch}
                          filterOption={false}
                          optionFilterProp="label"
                          placeholder={t('vuln.form.assigneePlaceholder')}
                          options={users.map((user) => ({
                            value: user.id,
                            label: user.fullName || user.username,
                          }))}
                        />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item
                        name="discovered_version_id"
                        label={t('vuln.form.discoveredVersion')}
                        rules={[{ required: true, message: t('vuln.create.versionRequired') }]}
                      >
                        <Select placeholder={t('vuln.form.discoveredVersionPlaceholder')}>
                          {assetVersions.map((item) => (
                            <Option key={item.id} value={item.id}>
                              {item.name || `v${item.versionNo}`}
                              {item.isLatest ? t('vuln.form.versionLatest') : ''}
                              {item.isDefault ? t('vuln.form.versionDefault') : ''}
                            </Option>
                          ))}
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="affected_versions" label={t('vuln.form.affectedVersions')}>
                        <Input placeholder={t('vuln.form.affectedPlaceholder')} />
                      </Form.Item>
                    </Col>
                  </Row>
                </Card>
                <Card
                  title={t('vuln.edit.statusInfo')}
                  size="small"
                  extra={
                    <Button type="link" onClick={() => navigate(`/vulnerabilities/${vulnerabilityId}`)}>
                      {t('vuln.edit.goStatus')}
                    </Button>
                  }
                >
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    {t('vuln.edit.statusNote')}
                  </Typography.Paragraph>
                </Card>
                <Card title={t('vuln.edit.exploitAndTest')} size="small">
                  <Row gutter={16}>
                    <Col span={24}>
                      <Form.Item
                        name="exploit_code"
                        label={t('vuln.edit.exploitCode')}
                        extra={t('vuln.edit.exploitCodeExtra')}
                      >
                        <TextArea rows={4} />
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Descriptions size="small" column={1} items={[
                        { key: 'fieldTestStatus', label: t('vuln.edit.fieldTestStatus'), children: labelOf(fieldTestKey(fieldTestStatusValue), fieldTestStatusValue) },
                      ]} />
                    </Col>
                    <Col span={8}>
                      <Descriptions size="small" column={1} items={[
                        { key: 'deliveryNote', label: t('vuln.edit.deliveryNote'), children: deliveryNoteValue || '-' },
                      ]} />
                    </Col>
                    <Col span={8}>
                      <Descriptions size="small" column={1} items={[
                        { key: 'fieldTestResult', label: t('vuln.edit.fieldTestResult'), children: fieldTestResultValue || '-' },
                      ]} />
                    </Col>
                    <Col span={24}>
                      <Typography.Text type="secondary">
                        {t('vuln.edit.readonlyNote')}
                      </Typography.Text>
                    </Col>
                  </Row>
                </Card>
                <Card title={t('vuln.edit.content')} size="small">
                  <Form.Item name="description" label={t('vuln.form.description')}>
                    <TextArea rows={3} />
                  </Form.Item>
                  <Form.Item name="detail" label="漏洞详情">
                    <TextArea rows={8} />
                  </Form.Item>
                  <Form.Item name="impact" label="影响分析">
                    <TextArea rows={4} />
                  </Form.Item>
                  <Form.Item name="poc_code" label="POC代码">
                    <TextArea rows={4} />
                  </Form.Item>
                </Card>
              </Form>
            </Space>
          ) : (
            <div>{t('vuln.edit.notFound')}</div>
          )}
        </Spin>
      </Card>
    );
  }

  // ==================== 详情页 ====================
  if (vulnerabilityId) {
    return (
      <VulnerabilityDetail
        vulnerabilityId={vulnerabilityId}
        onChanged={fetchData}
        onClose={() => navigate('/vulnerabilities')}
      />
    );
  }

  // ==================== 列表页 ====================
  return (
    <Card
      title={t('vuln.list.title')}
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
          {t('vuln.list.submit')}
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          placeholder={t('vuln.list.searchPlaceholder')}
          prefix={<SearchOutlined />}
          style={{ width: 280 }}
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          onPressEnter={() => setParams((prev) => ({ ...prev, search: searchText.trim(), page: 1 }))}
          allowClear
        />
        <Select
          allowClear
          placeholder={t('vuln.list.filterWorkflow')}
          style={{ width: 160 }}
          value={params.workflow_status}
          options={WORKFLOW_STATUS_OPTIONS.map((item) => ({ value: item.value, label: t(item.labelKey) }))}
          onChange={(value) => setParams((prev) => ({ ...prev, workflow_status: value, page: 1 }))}
        />
        <Select
          allowClear
          placeholder={t('vuln.list.filterSeverity')}
          style={{ width: 140 }}
          value={params.severity}
          options={SEVERITY_OPTIONS.map((item) => ({ value: item.value, label: t(item.labelKey) }))}
          onChange={(value) => setParams((prev) => ({ ...prev, severity: value, page: 1 }))}
        />
        <Button type="primary" onClick={() => setParams((prev) => ({ ...prev, search: searchText.trim(), page: 1 }))}>
          {t('vuln.list.query')}
        </Button>
        <Button
          onClick={() => {
            setSearchText('');
            setParams({ page: 1, page_size: params.page_size, search: '' });
          }}
        >
          {t('vuln.list.reset')}
        </Button>
      </Space>

      <Table
        columns={columns}
        dataSource={data}
        rowKey="id"
        loading={loading}
        scroll={{ x: 1200 }}
        pagination={{
          current: params.page,
          pageSize: params.page_size,
          total,
          showSizeChanger: true,
          showTotal: (count) => t('vuln.list.total', { count }),
          onChange: (page, pageSize) => setParams((prev) => ({ ...prev, page, page_size: pageSize })),
        }}
      />

      <Drawer
        title={t('vuln.drawer.title')}
        open={Boolean(drawerId)}
        width="min(1080px, 96vw)"
        onClose={() => setDrawerId(null)}
        destroyOnClose
      >
        {drawerId ? (
          <VulnerabilityDetail
            vulnerabilityId={drawerId}
            embedded
            onChanged={fetchData}
            onClose={() => setDrawerId(null)}
          />
        ) : null}
      </Drawer>

      <Modal
        title={t('vuln.create.title')}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        confirmLoading={createSubmitting}
        destroyOnClose
        width={820}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label={t('vuln.form.title')} rules={[{ required: true, message: t('vuln.form.titleRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="asset_id" label={t('vuln.create.asset')} rules={[{ required: true, message: t('vuln.create.assetRequired') }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder={t('vuln.create.assetPlaceholder')}
              options={assets.map((asset) => ({ value: asset.id, label: asset.name || asset.code }))}
              onChange={(assetId: string) => {
                form.setFieldValue('discovered_version_id', undefined);
                loadAssetVersions(assetId).then(setCreateAssetVersions);
              }}
            />
          </Form.Item>
          {!selectedAssetWritable ? (
            <Alert
              type="warning"
              showIcon
              style={{ marginTop: -8, marginBottom: 12 }}
              message={VULN_CREATE_FOREIGN_ASSET_HINT}
            />
          ) : null}
          <Form.Item
            name="discovered_version_id"
            label={t('vuln.form.discoveredVersion')}
            rules={[{ required: true, message: t('vuln.create.versionRequired') }]}
          >
            <Select
              placeholder={t('vuln.create.versionPlaceholder')}
              options={createAssetVersions.map((item) => ({
                value: item.id,
                label: `${item.name || `v${item.versionNo}`}${item.isLatest ? t('vuln.form.versionLatest') : ''}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="affected_versions" label={t('vuln.form.affectedVersions')}>
            <Input placeholder={t('vuln.form.affectedPlaceholder')} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="type" label={t('vuln.form.type')} rules={[{ required: true, message: t('vuln.form.typeRequired') }]}>
                <Select options={typeOptions} placeholder={t('vuln.form.typePlaceholder')} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="severity" label={t('vuln.form.severity')} rules={[{ required: true, message: t('vuln.form.severityRequired') }]}>
                <Select options={SEVERITY_OPTIONS.map((item) => ({ value: item.value, label: t(item.labelKey) }))} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="cvss_score" label={t('vuln.form.cvss')}>
                <InputNumber min={0} max={10} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="assigned_to" label={t('vuln.form.assignee')} extra={userDirectoryLimitHit ? USER_DIRECTORY_LIMIT_HINT : undefined}>
                <Select
                  allowClear
                  showSearch
                  onSearch={handleUserSearch}
                  filterOption={false}
                  optionFilterProp="label"
                  placeholder={t('vuln.form.assigneePlaceholder')}
                  options={users.map((user) => ({ value: user.id, label: user.fullName || user.username }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label={t('vuln.form.summary')} rules={[{ required: true, message: t('vuln.form.summaryRequired') }]}>
            <TextArea rows={2} />
          </Form.Item>
          <Form.Item name="detail" label={t('vuln.form.detailMarkdown')} rules={[{ required: true, message: t('vuln.form.detailRequired') }]}>
            <TextArea rows={6} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Vulnerabilities;
