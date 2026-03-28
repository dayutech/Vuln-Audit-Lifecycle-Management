import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Input, Tag, Card, Modal, Form, Select, InputNumber, message } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { getVulnerabilityList, createVulnerability } from '@/api/vulnerability';
import type { VulnerabilityVO, CreateVulnerabilityRequest } from '@/types/vulnerability';

const { Option } = Select;
const { TextArea } = Input;

const severityColors: Record<string, string> = {
  critical: 'red',
  high: 'orange',
  medium: 'blue',
  low: 'green'
};

const severityLabels: Record<string, string> = {
  critical: '严重',
  high: '高危',
  medium: '中危',
  low: '低危'
};

const Vulnerabilities: React.FC = () => {
  const [data, setData] = useState<VulnerabilityVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [form] = Form.useForm();

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
          onPressEnter={(e) => setParams({ ...params, search: e.currentTarget.value, page: 1 })}
        />
      </div>
      <Table 
        columns={columns} 
        dataSource={data} 
        rowKey="id"
        loading={loading}
        pagination={{
          current: params.page,
          pageSize: params.page_size,
          total,
          onChange: (page, pageSize) => setParams({ ...params, page, page_size: pageSize })
        }}
      />

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
          <Form.Item name="type" label="漏洞类型" rules={[{ required: true, message: '请输入漏洞类型' }]}>
            <Input placeholder="例如: SQL注入, XSS" />
          </Form.Item>
          <Form.Item name="severity" label="严重程度" rules={[{ required: true, message: '请选择严重程度' }]}>
            <Select>
              <Option value="critical">严重</Option>
              <Option value="high">高危</Option>
              <Option value="medium">中危</Option>
              <Option value="low">低危</Option>
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