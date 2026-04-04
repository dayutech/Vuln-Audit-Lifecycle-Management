import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Tag, Modal, Form, Select, Input, DatePicker, message, Descriptions } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { getTaskList, createTask, updateTaskStatus, getTaskDetail, assignTask } from '@/api/task';
import type { TaskVO, CreateTaskRequest, UpdateTaskStatusRequest } from '@/types/task';
import { getAssetList, getAssetVersions } from '@/api/asset';
import { getUserList } from '@/api/user';
import type { AssetVO, AssetVersionVO } from '@/types/asset';
import type { UserVO } from '@/types/user';
import dayjs from 'dayjs';
import { useNavigate, useParams } from 'react-router-dom';
import { useUserStore } from '@/store/userStore';

const { Option } = Select;

const statusColors: Record<string, string> = {
  pending: 'default',
  in_progress: 'processing',
  completed: 'success',
  cancelled: 'error'
};

const statusLabels: Record<string, string> = {
  pending: '待处理',
  in_progress: '进行中',
  completed: '已完成',
  cancelled: '已取消'
};

const Tasks: React.FC = () => {
  const navigate = useNavigate();
  const { id: taskId } = useParams();
  const { user } = useUserStore();
  const [data, setData] = useState<TaskVO[]>([]);
  const [detailTask, setDetailTask] = useState<TaskVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState<{ page: number; page_size: number; search: string; status?: string }>({ page: 1, page_size: 10, search: '' });
  const [users, setUsers] = useState<UserVO[]>([]);
  const [assets, setAssets] = useState<AssetVO[]>([]);
  const [assetVersions, setAssetVersions] = useState<AssetVersionVO[]>([]);

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [statusModalVisible, setStatusModalVisible] = useState(false);
  const [assignModalVisible, setAssignModalVisible] = useState(false);
  const [currentTask, setCurrentTask] = useState<TaskVO | null>(null);
  const [form] = Form.useForm();
  const [statusForm] = Form.useForm();
  const [assignForm] = Form.useForm();

  const canAssignTask = ['admin', 'project_leader', 'team_leader', 'ROLE_ADMIN', 'ROLE_LEADER'].includes(user?.role || '');

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getTaskList(params);
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

  const fetchTaskDetail = async (id: string) => {
    setLoading(true);
    try {
      const res = await getTaskDetail(id);
      const payload = (res as any)?.data || res;
      setDetailTask(payload);
    } catch (error) {
      setDetailTask(null);
    } finally {
      setLoading(false);
    }
  };

  const fetchUsers = async () => {
    try {
      const res = await getUserList({ page: 1, page_size: 200 });
      const payload = (res as any)?.data || res;
      const items = payload.content || payload.items || [];
      setUsers(Array.isArray(items) ? items : []);
    } catch (error) {
      setUsers([]);
    }
  };

  const fetchAssets = async () => {
    try {
      const res = await getAssetList({ page: 1, page_size: 200 });
      const payload = (res as any)?.data || res;
      const items = payload.content || payload.items || [];
      setAssets(Array.isArray(items) ? items : []);
    } catch (error) {
      setAssets([]);
    }
  };

  const fetchAssetVersions = async (assetId?: string) => {
    if (!assetId) {
      setAssetVersions([]);
      form.setFieldsValue({ asset_version_id: undefined });
      return;
    }
    try {
      const res = await getAssetVersions(assetId);
      const payload = (res as any)?.data?.data ?? (res as any)?.data ?? res;
      const source = Array.isArray(payload) ? payload : payload?.items || [];
      const list = source.map((item: any) => ({
        id: String(item?.id ?? ''),
        assetId: item?.assetId ?? item?.asset_id ?? '',
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
    if (taskId) {
      fetchTaskDetail(taskId);
      return;
    }
    fetchData();
  }, [params.page, params.page_size, params.search, params.status, taskId]);

  useEffect(() => {
    fetchUsers();
    fetchAssets();
  }, []);

  const handleCreate = () => {
    form.resetFields();
    setAssetVersions([]);
    setIsModalVisible(true);
  };

  const handleStatusUpdate = (record: TaskVO) => {
    setCurrentTask(record);
    statusForm.setFieldsValue({ status: record.status });
    setStatusModalVisible(true);
  };

  const handleAssign = (record: TaskVO) => {
    setCurrentTask(record);
    assignForm.resetFields();
    setAssignModalVisible(true);
  };

  const handleCreateSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        asset_id: values.asset_id,
        asset_version_id: values.asset_version_id,
        reference_id: values.asset_id,
        assignee_id: values.assignee_id,
        description: values.description,
        due_date: values.due_date ? values.due_date.toISOString() : undefined
      };
      
      await createTask(payload as CreateTaskRequest);
      message.success('任务创建成功');
      setIsModalVisible(false);
      if (taskId) {
        fetchTaskDetail(taskId);
      } else {
        fetchData();
      }
    } catch (error) {
      // Handled
    }
  };

  const handleStatusSubmit = async () => {
    try {
      const values = await statusForm.validateFields();
      if (currentTask) {
        await updateTaskStatus(currentTask.id, values as UpdateTaskStatusRequest);
        message.success('状态更新成功');
        setStatusModalVisible(false);
        if (taskId) {
          fetchTaskDetail(taskId);
        } else {
          fetchData();
        }
      }
    } catch (error) {
      // Handled
    }
  };

  const handleAssignSubmit = async () => {
    try {
      const values = await assignForm.validateFields();
      if (!currentTask) return;
      await assignTask(currentTask.id, values);
      message.success('任务分配成功');
      setAssignModalVisible(false);
      if (taskId) {
        fetchTaskDetail(taskId);
      } else {
        fetchData();
      }
    } catch (error) {
      // Handled
    }
  };

  const columns = [
    {
      title: '任务标题',
      dataIndex: 'title',
      key: 'title',
      render: (text: string, record: TaskVO) => <a onClick={() => navigate(`/tasks/${record.id}`)}>{text}</a>
    },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status',
      render: (text: string) => <Tag color={statusColors[text] || 'default'}>{statusLabels[text] || text}</Tag>
    },
    {
      title: '创建者',
      key: 'creator',
      render: (_: any, record: TaskVO) => record.creator?.username || '-'
    },
    { 
      title: '责任人', 
      key: 'assignee',
      render: (_: any, record: TaskVO) => record.assignee?.username || '-'
    },
    { 
      title: '截止日期', 
      dataIndex: 'due_date', 
      key: 'due_date',
      render: (text: string) => text ? dayjs(text).format('YYYY-MM-DD HH:mm') : '-'
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: TaskVO) => (
        <Space size="middle">
          <a onClick={() => navigate(`/tasks/${record.id}`)}>详情</a>
          <a onClick={() => handleStatusUpdate(record)}>更新状态</a>
          {canAssignTask && <a onClick={() => handleAssign(record)}>分配</a>}
        </Space>
      ),
    },
  ];

  if (taskId) {
    return (
      <Card
        title="任务详情"
        extra={<Button onClick={() => navigate('/tasks')}>返回任务列表</Button>}
        loading={loading}
      >
        {detailTask ? (
          <>
            <Descriptions column={2} style={{ marginBottom: 24 }}>
              <Descriptions.Item label="任务名称">{detailTask.title || '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColors[detailTask.status] || 'default'}>{statusLabels[detailTask.status] || detailTask.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="创建者">{detailTask.creator?.username || '-'}</Descriptions.Item>
              <Descriptions.Item label="负责人">{detailTask.assignee?.username || '-'}</Descriptions.Item>
              <Descriptions.Item label="资产ID">{(detailTask as any).asset_id || (detailTask as any).assetId || '-'}</Descriptions.Item>
              <Descriptions.Item label="资产版本ID">{(detailTask as any).asset_version_id || (detailTask as any).assetVersionId || '-'}</Descriptions.Item>
              <Descriptions.Item label="截止日期">{detailTask.due_date ? dayjs(detailTask.due_date).format('YYYY-MM-DD HH:mm') : '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{((detailTask as any).created_at || (detailTask as any).createdAt) ? dayjs((detailTask as any).created_at || (detailTask as any).createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{((detailTask as any).updated_at || (detailTask as any).updatedAt) ? dayjs((detailTask as any).updated_at || (detailTask as any).updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
              <Descriptions.Item label="任务描述" span={2}>{(detailTask as any).description || '-'}</Descriptions.Item>
            </Descriptions>
            <Space>
              <Button onClick={() => handleStatusUpdate(detailTask)}>更新状态</Button>
              {canAssignTask && <Button onClick={() => handleAssign(detailTask)}>分配任务</Button>}
            </Space>
          </>
        ) : (
          <div>任务不存在或无权限查看</div>
        )}
      </Card>
    );
  }

  return (
    <Card title="任务管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>创建任务</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Space>
        <Input 
          placeholder="搜索任务标题" 
          prefix={<SearchOutlined />} 
          style={{ width: 300 }} 
          onPressEnter={(e) => setParams({ ...params, search: e.currentTarget.value, page: 1 })}
        />
          <Select
            allowClear
            style={{ width: 180 }}
            placeholder="按状态筛选"
            value={params.status}
            onChange={(value) => setParams({ ...params, status: value, page: 1 })}
          >
            <Option value="pending">待处理</Option>
            <Option value="in_progress">进行中</Option>
            <Option value="completed">已完成</Option>
            <Option value="cancelled">已取消</Option>
          </Select>
        </Space>
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
        title="创建任务"
        open={isModalVisible}
        onOk={handleCreateSubmit}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="asset_id" label="关联资产" rules={[{ required: true, message: '请选择资产' }]}>
            <Select
              placeholder="请选择资产"
              showSearch
              optionFilterProp="children"
              onChange={(value) => fetchAssetVersions(value)}
            >
              {assets.map((item) => (
                <Option key={item.id} value={item.id}>{item.name} ({item.code})</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="asset_version_id" label="关联资产版本" rules={[{ required: true, message: '请选择资产版本' }]}>
            <Select placeholder="请选择资产版本">
              {assetVersions.map((item) => (
                <Option key={item.id} value={item.id}>
                  {item.name || `v${item.versionNo}`}{item.isLatest ? ' - 最新' : ''}{item.isDefault ? ' - 默认' : ''}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="assignee_id" label="责任人">
            <Select placeholder="不选择则默认为创建者" allowClear showSearch optionFilterProp="children">
              {users.map((item) => (
                <Option key={item.id} value={item.id}>{item.username}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="description" label="任务描述">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="due_date" label="截止日期">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="更新任务状态"
        open={statusModalVisible}
        onOk={handleStatusSubmit}
        onCancel={() => setStatusModalVisible(false)}
        destroyOnClose
      >
        <Form form={statusForm} layout="vertical">
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select>
              <Option value="pending">待处理</Option>
              <Option value="in_progress">进行中</Option>
              <Option value="completed">已完成</Option>
              <Option value="cancelled">已取消</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="分配任务"
        open={assignModalVisible}
        onOk={handleAssignSubmit}
        onCancel={() => setAssignModalVisible(false)}
        destroyOnClose
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item name="assignee_id" label="指派给" rules={[{ required: true, message: '请选择指派用户' }]}>
            <Select placeholder="请选择用户" showSearch optionFilterProp="children">
              {users.map((item) => (
                <Option key={item.id} value={item.id}>{item.username}</Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Tasks;
