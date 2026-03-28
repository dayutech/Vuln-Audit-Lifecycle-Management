import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Modal, Form, Input, Select, message, Tag } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { getUserList, createUser, updateUser, deleteUser } from '@/api/user';
import { getRoleList } from '@/api/role';
import type { UserVO, CreateUserRequest, UpdateUserRequest } from '@/types/user';
import type { RoleVO } from '@/types/role';
import { formatDateTime } from '@/utils/datetime';

const { Option } = Select;

const Users: React.FC = () => {
  const [data, setData] = useState<UserVO[]>([]);
  const [roles, setRoles] = useState<RoleVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [currentUser, setCurrentUser] = useState<UserVO | null>(null);
  const [form] = Form.useForm();

  const fetchRoles = async () => {
    try {
      const res = await getRoleList();
      const payload = res.data || (res as any);
      const items = Array.isArray(payload) ? payload : (payload.content || payload.items || []);
      setRoles(items);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getUserList(params);
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
    fetchRoles();
  }, []);

  useEffect(() => {
    fetchData();
  }, [params.page, params.page_size, params.search]);

  const handleCreate = () => {
    setModalMode('create');
    form.resetFields();
    setCurrentUser(null);
    setIsModalVisible(true);
  };

  const handleEdit = (record: UserVO) => {
    setModalMode('edit');
    setCurrentUser(record);
    form.setFieldsValue({
      full_name: record.full_name,
      email: record.email,
      phone: record.phone,
      role: record.role,
    });
    setIsModalVisible(true);
  };

  const handleDelete = (record: UserVO) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除用户 [${record.username}] 吗？此操作不可恢复。`,
      onOk: async () => {
        try {
          await deleteUser(record.id);
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
        await createUser(values as CreateUserRequest);
        message.success('创建成功');
      } else {
        if (currentUser) {
          await updateUser(currentUser.id, values as UpdateUserRequest);
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
    { title: '用户名', dataIndex: 'username', key: 'username' },
    { title: '姓名', dataIndex: 'full_name', key: 'full_name' },
    { title: '邮箱', dataIndex: 'email', key: 'email' },
    { 
      title: '主角色', 
      dataIndex: 'role', 
      key: 'role',
      render: (text: string) => {
        const roleObj = roles.find(r => r.code === text);
        return <Tag color="blue">{roleObj ? roleObj.name : text}</Tag>;
      }
    },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status',
      render: (text: string) => (
        <Tag color={text === 'active' ? 'success' : 'default'}>
          {text === 'active' ? '活跃' : '未激活'}
        </Tag>
      )
    },
    {
      title: '创建时间',
      dataIndex: 'created_at',
      key: 'created_at',
      render: (_: unknown, record: UserVO & { createdAt?: string; created_at?: string }) =>
        formatDateTime(record.created_at ?? record.createdAt)
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: UserVO) => (
        <Space size="middle">
          <a onClick={() => handleEdit(record)}>编辑</a>
          <a style={{ color: 'red' }} onClick={() => handleDelete(record)}>删除</a>
        </Space>
      ),
    },
  ];

  return (
    <Card title="用户管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>创建用户</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder="搜索用户名/姓名/邮箱" 
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
        title={modalMode === 'create' ? '创建用户' : '编辑用户'}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          {modalMode === 'create' && (
            <>
              <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
                <Input />
              </Form.Item>
              <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
                <Input.Password />
              </Form.Item>
            </>
          )}
          <Form.Item name="full_name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ required: true, type: 'email', message: '请输入正确的邮箱格式' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input />
          </Form.Item>
          <Form.Item name="role" label="主角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select placeholder="请选择主角色">
              {roles.map(r => (
                <Option key={r.code} value={r.code}>{r.name}</Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Users;
