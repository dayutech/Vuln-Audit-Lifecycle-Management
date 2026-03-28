import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Modal, Form, Input, Select, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { getRoleList, createRole, updateRole, deleteRole, getPermissionList } from '@/api/role';
import type { RoleVO, PermissionVO, CreateRoleRequest, UpdateRoleRequest } from '@/types/role';

const { TextArea } = Input;
const { Option } = Select;

const Roles: React.FC = () => {
  const [data, setData] = useState<RoleVO[]>([]);
  const [permissions, setPermissions] = useState<PermissionVO[]>([]);
  const [loading, setLoading] = useState(false);

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [currentRole, setCurrentRole] = useState<RoleVO | null>(null);
  const [form] = Form.useForm();

  const fetchData = async () => {
    setLoading(true);
    try {
      const [roleRes, permRes] = await Promise.all([
        getRoleList({ include_permissions: true }),
        getPermissionList({ page_size: 1000 })
      ]);
      const rolePayload = roleRes.data || (roleRes as any);
      const permPayload = permRes.data || (permRes as any);
      const roleItems = Array.isArray(rolePayload) ? rolePayload : (rolePayload.content || rolePayload.items || []);
      const permissionItems = Array.isArray(permPayload) ? permPayload : (permPayload.content || permPayload.items || []);
      setData(roleItems);
      setPermissions(permissionItems);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleCreate = () => {
    setModalMode('create');
    form.resetFields();
    setCurrentRole(null);
    setIsModalVisible(true);
  };

  const handleEdit = (record: RoleVO) => {
    setModalMode('edit');
    setCurrentRole(record);
    form.setFieldsValue({
      name: record.name,
      code: record.code,
      description: record.description,
      permission_ids: record.permissions?.map(p => p.id) || [],
    });
    setIsModalVisible(true);
  };

  const handleDelete = (record: RoleVO) => {
    if (record.is_system) {
      message.warning('系统内置角色不可删除');
      return;
    }
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除角色 [${record.name}] 吗？此操作不可恢复。`,
      onOk: async () => {
        try {
          await deleteRole(record.id);
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
        await createRole(values as CreateRoleRequest);
        message.success('创建成功');
      } else {
        if (currentRole) {
          await updateRole(currentRole.id, values as UpdateRoleRequest);
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
    { title: '角色名称', dataIndex: 'name', key: 'name' },
    { title: '角色编码', dataIndex: 'code', key: 'code' },
    { title: '描述', dataIndex: 'description', key: 'description' },
    { 
      title: '类型', 
      dataIndex: 'is_system', 
      key: 'is_system',
      render: (is_system: boolean) => (
        <Tag color={is_system ? 'red' : 'green'}>{is_system ? '系统内置' : '自定义'}</Tag>
      )
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: RoleVO) => (
        <Space size="middle">
          <a onClick={() => handleEdit(record)}>编辑权限</a>
          {!record.is_system && (
            <a style={{ color: 'red' }} onClick={() => handleDelete(record)}>删除</a>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card title="角色与权限管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>创建角色</Button>}>
      <Table 
        columns={columns} 
        dataSource={data} 
        rowKey="id"
        loading={loading}
        pagination={false}
      />

      <Modal
        title={modalMode === 'create' ? '创建角色' : '编辑角色权限'}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="code" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
            <Input disabled={modalMode === 'edit'} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={2} />
          </Form.Item>
          <Form.Item name="permission_ids" label="分配权限">
            <Select mode="multiple" placeholder="请选择权限" allowClear>
              {permissions.map(p => (
                <Option key={p.id} value={p.id}>{p.name} ({p.code})</Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Roles;
