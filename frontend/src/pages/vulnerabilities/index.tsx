import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Table, Button, Space, Input, Tag, Card, Modal, Form, Select, InputNumber, message, Descriptions, Spin, Row, Col, DatePicker } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { getVulnerabilityList, createVulnerability, getVulnerabilityOptions, getVulnerabilityDetail, updateVulnerability } from '@/api/vulnerability';
import type { VulnerabilityVO, CreateVulnerabilityRequest } from '@/types/vulnerability';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { getAssetVersions } from '@/api/asset';
import type { AssetVersionVO } from '@/types/asset';
import { getUserList } from '@/api/user';
import type { UserVO } from '@/types/user';

const { Option } = Select;
const { TextArea } = Input;

const severityColors: Record<string, string> = {
  CRITICAL: 'red',
  HIGH: 'orange',
  MEDIUM: 'blue',
  LOW: 'green',
  INFO: 'default',
  critical: 'red',
  high: 'orange',
  medium: 'blue',
  low: 'green'
};

const severityLabels: Record<string, string> = {
  CRITICAL: '严重',
  HIGH: '高危',
  MEDIUM: '中危',
  LOW: '低危',
  INFO: '信息',
  critical: '严重',
  high: '高危',
  medium: '中危',
  low: '低危'
};

const workflowStatusOptions = [
  { value: 'discovered', label: '已发现' },
  { value: 'verified', label: '已验证' },
  { value: 'analyzing', label: '分析中' },
  { value: 'analyzed', label: '已分析' },
  { value: 'exploit_development', label: '利用开发中' },
  { value: 'field_testing', label: '出场测试中' },
  { value: 'delivered', label: '已交付' },
  { value: 'closed', label: '已关闭' },
];

const customerStatusOptions = [
  { value: 'pending', label: '待确认' },
  { value: 'confirmed', label: '已确认' },
  { value: 'rejected', label: '已拒绝' },
];

const disclosureStatusOptions = [
  { value: 'undisclosed', label: '未披露' },
  { value: 'disclosed', label: '已披露' },
  { value: 'has_cve', label: '已分配CVE' },
];

const exploitStageOptions = [
  { value: '探测', label: '探测' },
  { value: '突破', label: '突破' },
  { value: '控守', label: '控守' },
  { value: '已完成', label: '已完成' },
];

const fieldTestStatusOptions = [
  { value: '待测试', label: '待测试' },
  { value: '测试中', label: '测试中' },
  { value: '测试完成', label: '测试完成' },
  { value: '测试失败', label: '测试失败' },
];

const booleanOptions = [
  { value: true, label: '是' },
  { value: false, label: '否' },
];

const getPayload = (res: any) => (res as any)?.data ?? res;
const normalizeText = (value?: string | null) => {
  const nextValue = value?.trim();
  return nextValue ? nextValue : undefined;
};

const Vulnerabilities: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { id: vulnerabilityId } = useParams();
  const isEditPage = Boolean(vulnerabilityId && location.pathname.endsWith('/edit'));
  const tableWrapRef = useRef<HTMLDivElement>(null);
  const [data, setData] = useState<VulnerabilityVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<VulnerabilityVO | null>(null);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });
  const [searchText, setSearchText] = useState('');

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [editPageForm] = Form.useForm();
  const [typeOptions, setTypeOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [assetVersions, setAssetVersions] = useState<AssetVersionVO[]>([]);
  const [users, setUsers] = useState<UserVO[]>([]);
  const [editPageSubmitting, setEditPageSubmitting] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getVulnerabilityList(params);
      const payload = getPayload(res);
      const items = payload.content || payload.items || [];
      const totalCount = payload.totalElements || payload.total || 0;
      setData(items);
      setTotal(totalCount);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const fetchDetail = async (id: string) => {
    setDetailLoading(true);
    try {
      const res = await getVulnerabilityDetail(id);
      const payload = getPayload(res);
      setDetail(payload || null);
    } catch (error) {
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    if (vulnerabilityId) {
      fetchDetail(vulnerabilityId);
      return;
    }
    fetchData();
  }, [params.page, params.page_size, params.search, vulnerabilityId]);

  useEffect(() => {
    const search = new URLSearchParams(location.search).get('search') || '';
    setSearchText(search);
    setParams((prev) => (prev.search === search ? prev : { ...prev, search, page: 1 }));
  }, [location.search]);

  const normalizedSearch = useMemo(() => params.search.trim().toLowerCase(), [params.search]);

  const isMatchedRecord = (record: VulnerabilityVO) => {
    if (!normalizedSearch) {
      return false;
    }
    const fields = [
      record.id,
      record.vulnerability_no,
      record.title,
      record.cve_id,
      record.type,
    ]
      .filter(Boolean)
      .map((item) => String(item).toLowerCase());
    return fields.some((value) => value.includes(normalizedSearch));
  };

  useEffect(() => {
    if (!normalizedSearch || data.length === 0) {
      return;
    }
    const firstMatched = data.find((item) => isMatchedRecord(item));
    if (!firstMatched || !tableWrapRef.current) {
      return;
    }
    const row = tableWrapRef.current.querySelector(`tr[data-row-key="${firstMatched.id}"]`) as HTMLTableRowElement | null;
    row?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [data, normalizedSearch]);

  useEffect(() => {
    const fetchOptions = async () => {
      try {
        const res = await getVulnerabilityOptions();
        const payload = getPayload(res);
        const types = Array.isArray(payload?.types) ? payload.types : [];
        setTypeOptions(types.map((item: any) => ({
          value: String(item?.value ?? item?.label ?? ''),
          label: String(item?.label ?? item?.value ?? ''),
        })).filter((item: any) => !!item.value));
      } catch (error) {
        setTypeOptions([]);
      }
    };
    fetchOptions();
  }, []);

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        const res = await getUserList({ page: 1, page_size: 200 });
        const payload = getPayload(res);
        setUsers(payload?.content || payload?.items || []);
      } catch (error) {
        setUsers([]);
      }
    };
    fetchUsers();
  }, []);

  const handleCreate = () => {
    form.resetFields();
    setIsModalVisible(true);
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      await createVulnerability(values as CreateVulnerabilityRequest);
      message.success('创建成功');
      setIsModalVisible(false);
      fetchData();
    } catch (error) {
      // Form validation error or API error
    }
  };

  const fetchAssetVersionOptions = async (assetId?: string) => {
    if (!assetId) {
      setAssetVersions([]);
      return;
    }
    try {
      const res = await getAssetVersions(assetId);
      const payload = getPayload(res);
      const source = Array.isArray(payload) ? payload : payload?.items || [];
      const list = source.map((item: any) => ({
        id: String(item?.id ?? ''),
        assetId: String(item?.assetId ?? item?.asset_id ?? assetId),
        versionNo: Number(item?.versionNo ?? item?.version_no ?? 0),
        name: item?.name ?? '',
        description: item?.description,
        isDefault: Boolean(item?.isDefault ?? item?.is_default),
        isLatest: Boolean(item?.isLatest ?? item?.is_latest),
        status: item?.status ?? 'active',
      }));
      setAssetVersions(list);
    } catch (error) {
      setAssetVersions([]);
    }
  };

  useEffect(() => {
    if (!detail || !isEditPage) {
      return;
    }
    fetchAssetVersionOptions(detail.assetId || detail.asset_id);
    editPageForm.setFieldsValue({
      title: detail.title,
      cve_id: detail.cveId || detail.cve_id,
      discovered_version_id: detail.discoveredVersionId || detail.discovered_version_id,
      affected_versions: detail.affectedVersions || detail.affected_versions,
      type: detail.type,
      severity: detail.severity,
      cvss_score: detail.cvssScore ?? detail.cvss_score,
      workflow_status: detail.workflowStatus || detail.workflow_status,
      customer_status: detail.customerStatus || detail.customer_status,
      customer_reject_reason: detail.customer_reject_reason,
      disclosure_status: detail.disclosureStatus || detail.disclosure_status,
      disclosure_date: detail.disclosureDate || detail.disclosure_date ? dayjs(detail.disclosureDate || detail.disclosure_date) : null,
      disclosure_source: detail.disclosureSource || detail.disclosure_source,
      exploit_stage: detail.exploitStage || detail.exploit_stage,
      exploit_code: detail.exploitCode || detail.exploit_code,
      field_test_status: detail.fieldTestStatus || detail.field_test_status,
      field_test_result: detail.fieldTestResult || detail.field_test_result,
      delivery_note: detail.deliveryNote || detail.delivery_note,
      vendor_fixed: detail.vendorFixed ?? detail.vendor_fixed ?? false,
      vendor_fix_version: detail.vendorFixVersion || detail.vendor_fix_version,
      description: detail.description,
      detail: detail.detail,
      impact: detail.impact,
      poc_code: detail.pocCode || detail.poc_code,
      assigned_to: detail.assignedTo || detail.assigned_to?.id,
    });
  }, [detail, isEditPage, editPageForm]);

  const handleEditPageSubmit = async () => {
    if (!detail || !vulnerabilityId) {
      return;
    }
    try {
      const values = await editPageForm.validateFields();
      setEditPageSubmitting(true);
      await updateVulnerability(vulnerabilityId, {
        title: values.title,
        cve_id: normalizeText(values.cve_id),
        discovered_version_id: values.discovered_version_id,
        affected_versions: normalizeText(values.affected_versions),
        type: values.type,
        severity: values.severity,
        cvss_score: values.cvss_score,
        workflow_status: values.workflow_status,
        customer_status: values.customer_status,
        customer_reject_reason: values.customer_status === 'rejected' ? normalizeText(values.customer_reject_reason) : undefined,
        disclosure_status: values.disclosure_status,
        disclosure_date: values.disclosure_date ? values.disclosure_date.format('YYYY-MM-DDTHH:mm:ss') : undefined,
        disclosure_source: normalizeText(values.disclosure_source),
        exploit_stage: values.exploit_stage,
        exploit_code: normalizeText(values.exploit_code),
        field_test_status: values.field_test_status,
        field_test_result: normalizeText(values.field_test_result),
        delivery_note: normalizeText(values.delivery_note),
        vendor_fixed: values.vendor_fixed,
        vendor_fix_version: values.vendor_fixed ? normalizeText(values.vendor_fix_version) : undefined,
        description: values.description,
        detail: values.detail,
        impact: normalizeText(values.impact),
        poc_code: normalizeText(values.poc_code),
        assigned_to: values.assigned_to || undefined,
      });
      message.success('漏洞更新成功');
      navigate(`/vulnerabilities/${vulnerabilityId}`);
    } finally {
      setEditPageSubmitting(false);
    }
  };

  const renderContentBlock = (content?: string) => (
    <div
      style={{
        whiteSpace: 'pre-wrap',
        lineHeight: 1.8,
        minHeight: 24,
      }}
    >
      {content?.trim() || '-'}
    </div>
  );

  const columns = [
    {
      title: '漏洞名称',
      dataIndex: 'title',
      key: 'title',
      render: (value: string, record: VulnerabilityVO) => (
        <a onClick={() => navigate(`/vulnerabilities/${record.id}`)}>{value || '-'}</a>
      ),
    },
    { 
      title: '所属资产', 
      key: 'asset',
      render: (_: any, record: VulnerabilityVO) => record.asset?.name || '-'
    },
    { title: '类型', dataIndex: 'type', key: 'type' },
    { 
      title: '严重程度', 
      dataIndex: 'severity', 
      key: 'severity',
      render: (text: string) => <Tag color={severityColors[text] || 'default'}>{severityLabels[text] || text}</Tag>
    },
    { title: 'CVSS评分', dataIndex: 'cvss_score', key: 'cvss_score' },
    { 
      title: '工作流状态', 
      dataIndex: 'workflow_status', 
      key: 'workflow_status',
      render: (text: string) => <Tag color="processing">{text}</Tag>
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: VulnerabilityVO) => (
        <Space size="middle">
          <a onClick={() => navigate(`/vulnerabilities/${record.id}`)}>详情</a>
          <a onClick={() => navigate(`/vulnerabilities/${record.id}/edit`)}>编辑</a>
        </Space>
      ),
    },
  ];

  if (vulnerabilityId) {
    const vulnerabilityNo = detail?.vulnerabilityNo || detail?.vulnerability_no || '-';
    const assetName = detail?.asset?.name || detail?.assetId || detail?.asset_id || '-';
    const workflowStatus = detail?.workflowStatus || detail?.workflow_status || '-';
    const customerStatus = detail?.customerStatus || detail?.customer_status || '-';
    const disclosureStatus = detail?.disclosureStatus || detail?.disclosure_status || '-';
    const discoveredVersionId = detail?.discoveredVersionId || detail?.discovered_version_id || '-';
    const affectedVersions = detail?.affectedVersions || detail?.affected_versions || '-';
    const cvssScore = detail?.cvssScore ?? detail?.cvss_score ?? '-';
    const createdAt = detail?.createdAt || detail?.created_at;
    const updatedAt = detail?.updatedAt || detail?.updated_at;
    const vendorFixed = detail?.vendorFixed ?? detail?.vendor_fixed;
    const vendorFixVersion = detail?.vendorFixVersion || detail?.vendor_fix_version || '-';
    const disclosureSource = detail?.disclosureSource || detail?.disclosure_source || '-';
    const disclosureDate = detail?.disclosureDate || detail?.disclosure_date;
    const exploitStage = detail?.exploitStage || detail?.exploit_stage || '-';
    const fieldTestStatus = detail?.fieldTestStatus || detail?.field_test_status || '-';
    const fieldTestResult = detail?.fieldTestResult || detail?.field_test_result;
    const deliveryNote = detail?.deliveryNote || detail?.delivery_note;
    const description = detail?.description;
    const detailContent = detail?.detail;
    const impact = detail?.impact;
    const pocCode = detail?.pocCode || detail?.poc_code;
    const assigneeName = detail?.assigned_to?.full_name || detail?.assigned_to?.username || '-';

    if (isEditPage) {
      return (
        <Card
          title="编辑漏洞"
          extra={(
            <Space>
              <Button onClick={() => navigate(`/vulnerabilities/${vulnerabilityId}`)}>返回详情</Button>
              <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/vulnerabilities')}>返回漏洞列表</Button>
              <Button type="primary" loading={editPageSubmitting} onClick={handleEditPageSubmit}>保存修改</Button>
            </Space>
          )}
        >
          <Spin spinning={detailLoading}>
            {detail ? (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Descriptions title={detail.title || '未命名漏洞'} column={3} bordered>
                  <Descriptions.Item label="漏洞编号">{vulnerabilityNo}</Descriptions.Item>
                  <Descriptions.Item label="所属资产">{assetName}</Descriptions.Item>
                  <Descriptions.Item label="创建时间">{createdAt ? dayjs(createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
                  <Descriptions.Item label="更新时间">{updatedAt ? dayjs(updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
                  <Descriptions.Item label="发现者">{detail.discoverer?.full_name || detail.discoverer?.username || '-'}</Descriptions.Item>
                  <Descriptions.Item label="当前处理人">{assigneeName}</Descriptions.Item>
                </Descriptions>
                <Form form={editPageForm} layout="vertical">
                  <Card title="基础信息" size="small">
                    <Row gutter={16}>
                      <Col span={12}>
                        <Form.Item name="title" label="漏洞名称" rules={[{ required: true, message: '请输入漏洞名称' }]}>
                          <Input />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="cve_id" label="CVE编号">
                          <Input placeholder="例如：CVE-2026-0001" />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="type" label="漏洞类型" rules={[{ required: true, message: '请选择漏洞类型' }]}>
                          <Select options={typeOptions} placeholder="请选择漏洞类型" />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="severity" label="严重程度" rules={[{ required: true, message: '请选择严重程度' }]}>
                          <Select>
                            <Option value="CRITICAL">严重</Option>
                            <Option value="HIGH">高危</Option>
                            <Option value="MEDIUM">中危</Option>
                            <Option value="LOW">低危</Option>
                            <Option value="INFO">信息</Option>
                          </Select>
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="cvss_score" label="CVSS评分">
                          <InputNumber min={0} max={10} step={0.1} style={{ width: '100%' }} />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="assigned_to" label="指派处理人">
                          <Select
                            allowClear
                            placeholder="请选择处理人"
                            options={users.map((user) => ({
                              value: user.id,
                              label: user.full_name || user.username,
                            }))}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="discovered_version_id" label="发现版本" rules={[{ required: true, message: '请选择发现版本' }]}>
                          <Select placeholder="请选择发现时所属资产版本">
                            {assetVersions.map((item) => (
                              <Option key={item.id} value={item.id}>
                                {item.name || `v${item.versionNo}`}{item.isLatest ? ' - 最新' : ''}{item.isDefault ? ' - 默认' : ''}
                              </Option>
                            ))}
                          </Select>
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="affected_versions" label="受影响版本">
                          <Input placeholder="例如: v1.0, v1.1, v2.x" />
                        </Form.Item>
                      </Col>
                    </Row>
                  </Card>
                  <Card title="状态信息" size="small">
                    <Row gutter={16}>
                      <Col span={8}>
                        <Form.Item name="workflow_status" label="工作流状态" rules={[{ required: true, message: '请选择工作流状态' }]}>
                          <Select options={workflowStatusOptions} />
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item name="customer_status" label="客户确认状态" rules={[{ required: true, message: '请选择客户确认状态' }]}>
                          <Select options={customerStatusOptions} />
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item name="disclosure_status" label="公开披露状态" rules={[{ required: true, message: '请选择公开披露状态' }]}>
                          <Select options={disclosureStatusOptions} />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="customer_reject_reason" label="客户拒绝原因">
                          <Input placeholder="客户拒绝时填写" />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="disclosure_source" label="披露来源">
                          <Input placeholder="例如：官方公告 / CNVD / 供应商通告" />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="disclosure_date" label="披露日期">
                          <DatePicker showTime style={{ width: '100%' }} />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="vendor_fixed" label="厂商是否修复" rules={[{ required: true, message: '请选择是否已修复' }]}>
                          <Select options={booleanOptions} />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="vendor_fix_version" label="修复版本">
                          <Input placeholder="例如：v1.0.3" />
                        </Form.Item>
                      </Col>
                    </Row>
                  </Card>
                  <Card title="利用与测试" size="small">
                    <Row gutter={16}>
                      <Col span={8}>
                        <Form.Item name="exploit_stage" label="利用阶段">
                          <Select allowClear options={exploitStageOptions} />
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item name="field_test_status" label="出场测试状态">
                          <Select allowClear options={fieldTestStatusOptions} />
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item name="delivery_note" label="交付备注">
                          <Input />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="exploit_code" label="利用代码">
                          <TextArea rows={4} />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="field_test_result" label="出场测试结果">
                          <TextArea rows={4} />
                        </Form.Item>
                      </Col>
                    </Row>
                  </Card>
                  <Card title="漏洞内容" size="small">
                    <Form.Item name="description" label="漏洞描述">
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
              <div>漏洞不存在或无权限查看</div>
            )}
          </Spin>
        </Card>
      );
    }

    return (
      <Card
        title="漏洞详情"
        extra={(
          <Space>
            <Button type="primary" onClick={() => navigate(`/vulnerabilities/${vulnerabilityId}/edit`)}>编辑漏洞</Button>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/vulnerabilities')}>返回漏洞列表</Button>
          </Space>
        )}
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Descriptions
                title={detail.title || '未命名漏洞'}
                column={3}
                bordered
              >
                <Descriptions.Item label="漏洞编号">{vulnerabilityNo}</Descriptions.Item>
                <Descriptions.Item label="漏洞类型">{detail.type || '-'}</Descriptions.Item>
                <Descriptions.Item label="严重程度">
                  <Tag color={severityColors[detail.severity] || 'default'}>{severityLabels[detail.severity] || detail.severity || '-'}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="所属资产">{assetName}</Descriptions.Item>
                <Descriptions.Item label="发现版本">{discoveredVersionId}</Descriptions.Item>
                <Descriptions.Item label="受影响版本">{affectedVersions}</Descriptions.Item>
                <Descriptions.Item label="CVE编号">{detail.cveId || detail.cve_id || '-'}</Descriptions.Item>
                <Descriptions.Item label="CVSS评分">{cvssScore}</Descriptions.Item>
                <Descriptions.Item label="发现者">{detail.discoverer?.full_name || detail.discoverer?.username || '-'}</Descriptions.Item>
                <Descriptions.Item label="工作流状态"><Tag color="processing">{workflowStatus}</Tag></Descriptions.Item>
                <Descriptions.Item label="客户确认状态"><Tag color="gold">{customerStatus}</Tag></Descriptions.Item>
                <Descriptions.Item label="公开披露状态"><Tag color="cyan">{disclosureStatus}</Tag></Descriptions.Item>
                <Descriptions.Item label="利用阶段">{exploitStage}</Descriptions.Item>
                <Descriptions.Item label="出场测试状态">{fieldTestStatus}</Descriptions.Item>
                <Descriptions.Item label="厂商修复">
                  <Tag color={vendorFixed ? 'success' : 'default'}>{vendorFixed ? '已修复' : '未修复'}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="修复版本">{vendorFixVersion}</Descriptions.Item>
                <Descriptions.Item label="披露来源">{disclosureSource}</Descriptions.Item>
                <Descriptions.Item label="披露日期">{disclosureDate ? dayjs(disclosureDate).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
                <Descriptions.Item label="创建时间">{createdAt ? dayjs(createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
                <Descriptions.Item label="更新时间">{updatedAt ? dayjs(updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
              </Descriptions>
              <Card title="漏洞描述" size="small">
                {renderContentBlock(description)}
              </Card>
              <Card title="漏洞详情" size="small">
                {renderContentBlock(detailContent)}
              </Card>
              <Card title="影响分析" size="small">
                {renderContentBlock(impact)}
              </Card>
              <Card title="POC / 测试结果" size="small">
                {renderContentBlock(pocCode || fieldTestResult || deliveryNote)}
              </Card>
            </Space>
          ) : (
            <div>漏洞不存在或无权限查看</div>
          )}
        </Spin>
      </Card>
    );
  }

  return (
    <Card title="漏洞管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>提交漏洞</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder="搜索漏洞名称/CVE" 
          prefix={<SearchOutlined />} 
          style={{ width: 300 }} 
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          onPressEnter={() => setParams((prev) => ({ ...prev, search: searchText.trim(), page: 1 }))}
        />
      </div>
      <div ref={tableWrapRef}>
        <Table 
          columns={columns} 
          dataSource={data} 
          rowKey="id"
          loading={loading}
          onRow={(record) => (
            isMatchedRecord(record)
              ? { style: { backgroundColor: '#fffbe6' } }
              : {}
          )}
          pagination={{
            current: params.page,
            pageSize: params.page_size,
            total,
            onChange: (page, pageSize) => setParams({ ...params, page, page_size: pageSize })
          }}
        />
      </div>

      <Modal
        title="提交漏洞"
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
        width={800}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="漏洞名称" rules={[{ required: true, message: '请输入漏洞名称' }]}>
            <Input />
          </Form.Item>
          {/* Note: In a real app, this would be a remote select for assets */}
          <Form.Item name="asset_id" label="资产ID" rules={[{ required: true, message: '请输入资产ID' }]}>
            <Input placeholder="输入UUID" />
          </Form.Item>
          <Form.Item name="discovered_version_id" label="发现版本ID" rules={[{ required: true, message: '请输入发现版本ID' }]}>
            <Input placeholder="输入资产版本UUID" />
          </Form.Item>
          <Form.Item name="affected_versions" label="受影响版本">
            <Input placeholder="例如: v1.0, v1.1, v2.x" />
          </Form.Item>
          <Form.Item name="type" label="漏洞类型" rules={[{ required: true, message: '请选择漏洞类型' }]}>
            <Select options={typeOptions} placeholder="请选择漏洞类型" />
          </Form.Item>
          <Form.Item name="severity" label="严重程度" rules={[{ required: true, message: '请选择严重程度' }]}>
            <Select>
              <Option value="CRITICAL">严重</Option>
              <Option value="HIGH">高危</Option>
              <Option value="MEDIUM">中危</Option>
              <Option value="LOW">低危</Option>
              <Option value="INFO">信息</Option>
            </Select>
          </Form.Item>
          <Form.Item name="cvss_score" label="CVSS评分">
            <InputNumber min={0} max={10} step={0.1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="简要描述">
            <TextArea rows={2} />
          </Form.Item>
          <Form.Item name="detail" label="漏洞详情 (Markdown)">
            <TextArea rows={6} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Vulnerabilities;
