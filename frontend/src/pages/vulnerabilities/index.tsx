import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Table, Button, Space, Input, Tag, Card, Modal, Form, Select, InputNumber, message } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { getVulnerabilityList, createVulnerability, getVulnerabilityOptions } from '@/api/vulnerability';
import type { VulnerabilityVO, CreateVulnerabilityRequest } from '@/types/vulnerability';
import { useLocation } from 'react-router-dom';

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

const Vulnerabilities: React.FC = () => {
  const location = useLocation();
  const tableWrapRef = useRef<HTMLDivElement>(null);
  const [data, setData] = useState<VulnerabilityVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });
  const [searchText, setSearchText] = useState('');

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [typeOptions, setTypeOptions] = useState<Array<{ value: string; label: string }>>([]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getVulnerabilityList(params);
      const payload = res.data || (res as any);
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

  useEffect(() => {
    fetchData();
  }, [params.page, params.page_size, params.search]);

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
        const res: any = await getVulnerabilityOptions();
        const payload = res?.data ? res.data : res;
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

  const columns = [
    { title: '漏洞名称', dataIndex: 'title', key: 'title' },
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
      render: (_: any) => (
        <Space size="middle">
          <a>详情</a>
          <a>状态流转</a>
        </Space>
      ),
    },
  ];

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
