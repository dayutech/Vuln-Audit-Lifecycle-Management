import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Modal, Form, Input, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { getTeamList, createTeam, deleteTeam } from '@/api/team';
import type { TeamVO, CreateTeamRequest } from '@/types/team';

const { TextArea } = Input;

const Teams: React.FC = () => {
  const [data, setData] = useState<TeamVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [form] = Form.useForm();

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getTeamList();
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
  }, []);

  const handleCreate = () => {
    form.resetFields();
    setIsModalVisible(true);
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该团队吗？此操作不可恢复。',
      onOk: async () => {
        try {
          await deleteTeam(id);
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
      await createTeam(values as CreateTeamRequest);
      message.success('创建成功');
      setIsModalVisible(false);
      fetchData();
    } catch (error) {
      // Form validation error or API error
    }
  };

  const columns = [
    { title: '团队名称', dataIndex: 'name', key: 'name' },
    { title: '描述', dataIndex: 'description', key: 'description' },
    { 
      title: '负责人', 
      key: 'leader',
      render: (_: any, record: TeamVO) => record.leader?.full_name || record.leader?.username || '-'
    },
    { title: '成员数量', dataIndex: 'member_count', key: 'member_count' },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: TeamVO) => (
        <Space size="middle">
          <a>成员管理</a>
          <a style={{ color: 'red' }} onClick={() => handleDelete(record.id)}>删除</a>
        </Space>
      ),
    },
  ];

  return (
    <Card title="团队管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>创建团队</Button>}>
      <Table 
        columns={columns} 
        dataSource={data} 
        rowKey="id"
        loading={loading}
        pagination={{
          total,
          showSizeChanger: false
        }}
      />

      <Modal
        title="创建团队"
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="团队名称" rules={[{ required: true, message: '请输入团队名称' }]}>
            <Input />
          </Form.Item>
          {/* Note: In a real app, leader_id should use a Select component to choose from users */}
          <Form.Item name="leader_id" label="负责人ID">
            <Input placeholder="请输入负责人UUID (可选)" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Teams;