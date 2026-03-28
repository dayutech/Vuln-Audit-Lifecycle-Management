import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Tag, Modal, Form, Select, Input, DatePicker, message } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { getTaskList, createTask, updateTaskStatus } from '@/api/task';
import type { TaskVO, CreateTaskRequest, UpdateTaskStatusRequest } from '@/types/task';
import dayjs from 'dayjs';

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

const priorityColors: Record<string, string> = {
  high: 'red',
  medium: 'orange',
  low: 'blue'
};

const priorityLabels: Record<string, string> = {
  high: '高',
  medium: '中',
  low: '低'
};

const Tasks: React.FC = () => {
  const [data, setData] = useState<TaskVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [statusModalVisible, setStatusModalVisible] = useState(false);
  const [currentTask, setCurrentTask] = useState<TaskVO | null>(null);
  const [form] = Form.useForm();
  const [statusForm] = Form.useForm();

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

  useEffect(() => {
    fetchData();
  }, [params.page, params.page_size, params.search]);

  const handleCreate = () => {
    form.resetFields();
    setIsModalVisible(true);
  };

  const handleStatusUpdate = (record: TaskVO) => {
    setCurrentTask(record);
    statusForm.setFieldsValue({ status: record.status });
    setStatusModalVisible(true);
  };

  const handleCreateSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        due_date: values.due_date ? values.due_date.toISOString() : undefined
      };
      
      await createTask(payload as CreateTaskRequest);
      message.success('任务创建成功');
      setIsModalVisible(false);
      fetchData();
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
        fetchData();
      }
    } catch (error) {
      // Handled
    }
  };

  const columns = [
    { title: '任务标题', dataIndex: 'title', key: 'title' },
    { 
      title: '优先级', 
      dataIndex: 'priority', 
      key: 'priority',
      render: (text: string) => <Tag color={priorityColors[text] || 'default'}>{priorityLabels[text] || text}</Tag>
    },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status',
      render: (text: string) => <Tag color={statusColors[text] || 'default'}>{statusLabels[text] || text}</Tag>
    },
    { 
      title: '指派给', 
      key: 'assignee',
      render: (_: any, record: TaskVO) => record.assignee?.full_name || record.assignee?.username || '-'
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
          <a>详情</a>
          <a onClick={() => handleStatusUpdate(record)}>更新状态</a>
        </Space>
      ),
    },
  ];

  return (
    <Card title="任务管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>创建任务</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder="搜索任务标题" 
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
        title="创建任务"
        open={isModalVisible}
        onOk={handleCreateSubmit}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="任务标题" rules={[{ required: true, message: '请输入任务标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="任务类型" rules={[{ required: true, message: '请选择任务类型' }]}>
            <Select>
              <Option value="vulnerability">漏洞处理</Option>
              <Option value="audit">安全审计</Option>
              <Option value="other">其他</Option>
            </Select>
          </Form.Item>
          <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请选择优先级' }]}>
            <Select>
              <Option value="high">高</Option>
              <Option value="medium">中</Option>
              <Option value="low">低</Option>
            </Select>
          </Form.Item>
          <Form.Item name="assignee_id" label="指派给 (用户ID)">
            <Input placeholder="输入用户UUID" />
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
    </Card>
  );
};

export default Tasks;