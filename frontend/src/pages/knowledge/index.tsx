import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Input, Tag, Modal, Form, Select, message } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { getKnowledgeList, createKnowledge, deleteKnowledge } from '@/api/knowledge';
import type { KnowledgeArticleVO, CreateKnowledgeRequest } from '@/types/knowledge';
import ReactQuill from 'react-quill';
import 'react-quill/dist/quill.snow.css';

const { Option } = Select;

const Knowledge: React.FC = () => {
  const [data, setData] = useState<KnowledgeArticleVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [editorContent, setEditorContent] = useState('');

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getKnowledgeList(params);
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
    setEditorContent('');
    setIsModalVisible(true);
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该文章吗？此操作不可恢复。',
      onOk: async () => {
        try {
          await deleteKnowledge(id);
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
      const payload = { 
        ...values, 
        content: editorContent,
        tags: values.tags || []
      };
      
      await createKnowledge(payload as CreateKnowledgeRequest);
      message.success('创建成功');
      setIsModalVisible(false);
      fetchData();
    } catch (error) {
      // Form validation error or API error
    }
  };

  const columns = [
    { title: '标题', dataIndex: 'title', key: 'title' },
    { title: '分类', dataIndex: 'category', key: 'category' },
    { 
      title: '标签', 
      key: 'tags',
      render: (_: any, record: KnowledgeArticleVO) => (
        <>
          {record.tags?.map(t => <Tag key={t.id} color="cyan">{t.tag}</Tag>)}
        </>
      )
    },
    { 
      title: '作者', 
      key: 'author',
      render: (_: any, record: KnowledgeArticleVO) => record.author?.full_name || record.author?.username || '-'
    },
    { title: '浏览量', dataIndex: 'view_count', key: 'view_count' },
    { title: '创建时间', dataIndex: 'created_at', key: 'created_at', render: (text: string) => new Date(text).toLocaleString() },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: KnowledgeArticleVO) => (
        <Space size="middle">
          <a>查看详情</a>
          <a style={{ color: 'red' }} onClick={() => handleDelete(record.id)}>删除</a>
        </Space>
      ),
    },
  ];

  const modules = {
    toolbar: [
      [{ 'header': [1, 2, 3, 4, 5, 6, false] }],
      ['bold', 'italic', 'underline', 'strike', 'blockquote'],
      [{'list': 'ordered'}, {'list': 'bullet'}, {'indent': '-1'}, {'indent': '+1'}],
      ['link', 'image', 'code-block'],
      ['clean']
    ],
  };

  return (
    <Card title="知识库" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>发布文章</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder="搜索文章标题/内容" 
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
        title="发布文章"
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
        width={1000}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="文章标题" rules={[{ required: true, message: '请输入文章标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="category" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
            <Select>
              <Option value="vulnerability">漏洞分析</Option>
              <Option value="methodology">方法论</Option>
              <Option value="experience">实战经验</Option>
              <Option value="tool">工具使用</Option>
            </Select>
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Select mode="tags" placeholder="输入标签后按回车" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="正文内容" required>
            <div style={{ height: 400, marginBottom: 50 }}>
              <ReactQuill 
                theme="snow" 
                value={editorContent} 
                onChange={setEditorContent} 
                modules={modules}
                style={{ height: '100%' }}
              />
            </div>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Knowledge;