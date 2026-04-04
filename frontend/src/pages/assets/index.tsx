import React, { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Input,
  Tag,
  Card,
  Modal,
  Form,
  Select,
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
} from '@/api/asset';
import type {
  AssetVO,
  CreateAssetRequest,
  UpdateAssetRequest,
} from '@/types/asset';
import { getTeamList } from '@/api/team';
import type { TeamVO } from '@/types/team';

const { TextArea } = Input;

const Assets: React.FC = () => {
  const navigate = useNavigate();
  const [data, setData] = useState<AssetVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [currentAsset, setCurrentAsset] = useState<AssetVO | null>(null);
  const [teams, setTeams] = useState<TeamVO[]>([]);
  const [targetTypeOptions, setTargetTypeOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [targetSourceOptions, setTargetSourceOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [priorityOptions, setPriorityOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [statusOptions, setStatusOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [form] = Form.useForm();

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
  });

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getAssetList(params);
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
      setTargetTypeOptions(Array.isArray(payload?.targetTypes) ? payload.targetTypes : []);
      setTargetSourceOptions(Array.isArray(payload?.targetSources) ? payload.targetSources : []);
      setPriorityOptions(Array.isArray(payload?.priorities) ? payload.priorities : []);
      setStatusOptions(Array.isArray(payload?.statuses) ? payload.statuses : []);
    } catch (error) {
      setTargetTypeOptions([]);
      setTargetSourceOptions([]);
      setPriorityOptions([]);
      setStatusOptions([]);
    }
  };

  useEffect(() => {
    fetchData();
  }, [params.page, params.page_size, params.search]);

  useEffect(() => {
    fetchTeams();
    fetchAssetOptions();
  }, []);

  const handleCreate = () => {
    setModalMode('create');
    form.resetFields();
    form.setFieldsValue({
      priority: priorityOptions[0]?.value,
      status: statusOptions[0]?.value,
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
      title: '确认删除',
      content: '确定要删除该资产吗？此操作不可恢复。',
      onOk: async () => {
        try {
          await deleteAsset(id);
          message.success('删除成功');
          fetchData();
        } catch (error) {
          // Error handled by interceptor
        }
      }
    });
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      if (modalMode === 'create') {
        await createAsset(values as CreateAssetRequest);
        message.success('创建成功');
      } else {
        if (currentAsset) {
          await updateAsset(currentAsset.id, values as UpdateAssetRequest);
          message.success('更新成功');
        }
      }
      setIsModalVisible(false);
      fetchData();
    } catch (error) {
      // Form validation error or API error
    }
  };

  const columns = [
    { 
      title: '资产名称', 
      dataIndex: 'name', 
      key: 'name',
      render: (text: string, record: AssetVO) => (
        <a onClick={() => navigate(`/assets/${record.id}`)} style={{ fontWeight: 500 }}>{text}</a>
      )
    },
    { title: '资产编码', dataIndex: 'code', key: 'code' },
    { 
      title: '所属团队', 
      dataIndex: 'team_name', 
      key: 'team_name',
      render: (text: string) => text || '-'
    },
    { 
      title: '负责人', 
      dataIndex: 'owner_name', 
      key: 'owner_name',
      render: (text: string) => text || '-'
    },
    { title: '类型', dataIndex: 'target_type', key: 'target_type' },
    { title: '来源', dataIndex: 'target_source', key: 'target_source' },
    { 
      title: '优先级', 
      dataIndex: 'priority', 
      key: 'priority',
      render: (text: string) => {
        const colors: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue', P3: 'green' };
        return <Tag color={colors[text] || 'default'}>{text}</Tag>;
      }
    },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status',
      render: (text: string) => {
        const colors: Record<string, string> = {
          active: 'success',
          under_test: 'processing',
          suspended: 'warning',
          archived: 'default',
        };
        const labels: Record<string, string> = {
          active: '活跃',
          under_test: '测试中',
          suspended: '暂停',
          archived: '已归档',
        };
        return <Tag color={colors[text] || 'default'}>{labels[text] || text}</Tag>;
      }
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: AssetVO) => (
        <Space size="middle">
          <a onClick={() => navigate(`/assets/${record.id}`)}>进入工作台</a>
          <a onClick={() => handleEdit(record)}>编辑</a>
          <a style={{ color: 'red' }} onClick={() => handleDelete(record.id)}>删除</a>
        </Space>
      ),
    },
  ];

  return (
    <Card title="资产管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建资产</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder="搜索资产名称/编码" 
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
        title={modalMode === 'create' ? '新建资产' : '编辑资产'}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="资产名称" rules={[{ required: true, message: '请输入资产名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="team_id" label="所属团队" rules={[{ required: true, message: '请选择所属团队' }]}>
            <Select placeholder="请选择所属团队" showSearch optionFilterProp="label" options={teams.map((team) => ({ label: team.name, value: team.id }))} />
          </Form.Item>
          <Form.Item name="target_type" label="资产类型" rules={[{ required: true, message: '请选择资产类型' }]}>
            <Select
              placeholder="请选择资产类型"
              options={targetTypeOptions}
              disabled={targetTypeOptions.length === 0}
            />
          </Form.Item>
          <Form.Item name="target_source" label="资产来源" rules={[{ required: true, message: '请选择资产来源' }]}>
            <Select
              placeholder="请选择资产来源"
              options={targetSourceOptions}
              disabled={targetSourceOptions.length === 0}
            />
          </Form.Item>
          <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请选择优先级' }]}>
            <Select placeholder="请选择优先级" options={priorityOptions} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
            <Select placeholder="请选择状态" options={statusOptions} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Assets;
