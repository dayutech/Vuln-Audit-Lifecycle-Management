import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Input, Tag, Modal, Form, Select, message } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { getDocumentList, createDocument, updateDocument, deleteDocument, publishDocument, unpublishDocument } from '@/api/document';
import type { DocumentVO, CreateDocumentRequest, UpdateDocumentRequest } from '@/types/document';
import ReactQuill from 'react-quill';
import 'react-quill/dist/quill.snow.css';

const { Option } = Select;

const statusColors: Record<string, string> = {
  draft: 'default',
  published: 'success',
  archived: 'warning'
};

const statusLabels: Record<string, string> = {
  draft: '草稿',
  published: '已发布',
  archived: '已归档'
};

const Documents: React.FC = () => {
  const [data, setData] = useState<DocumentVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [currentDoc, setCurrentDoc] = useState<DocumentVO | null>(null);
  const [form] = Form.useForm();
  const [editorContent, setEditorContent] = useState('');

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getDocumentList(params);
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
    setModalMode('create');
    form.resetFields();
    setEditorContent('');
    setCurrentDoc(null);
    setIsModalVisible(true);
  };

  const handleEdit = (record: DocumentVO) => {
    setModalMode('edit');
    setCurrentDoc(record);
    form.setFieldsValue({
      title: record.title,
      category: record.category,
      summary: record.summary,
      changelog: '', // for new version
    });
    setEditorContent(record.content || '');
    setIsModalVisible(true);
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该文档吗？此操作不可恢复。',
      onOk: async () => {
        try {
          await deleteDocument(id);
          message.success('删除成功');
          fetchData();
        } catch (error) {
          // Error handled by interceptor
        }
      }
    });
  };

  const handlePublishToggle = async (record: DocumentVO) => {
    try {
      if (record.status === 'draft') {
        await publishDocument(record.id);
        message.success('发布成功');
      } else {
        await unpublishDocument(record.id);
        message.success('取消发布成功');
      }
      fetchData();
    } catch (error) {
      // Error handled by interceptor
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      const payload = { ...values, content: editorContent };
      
      if (modalMode === 'create') {
        await createDocument(payload as CreateDocumentRequest);
        message.success('创建成功');
      } else {
        if (currentDoc) {
          await updateDocument(currentDoc.id, payload as UpdateDocumentRequest);
          message.success('更新成功，已生成新版本');
        }
      }
      setIsModalVisible(false);
      fetchData();
    } catch (error) {
      // Form validation error or API error
    }
  };

  const columns = [
    { title: '文档标题', dataIndex: 'title', key: 'title' },
    { title: '分类', dataIndex: 'category', key: 'category' },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status',
      render: (text: string) => <Tag color={statusColors[text] || 'default'}>{statusLabels[text] || text}</Tag>
    },
    { title: '版本', dataIndex: 'version', key: 'version', render: (v: number) => `v${v}` },
    { title: '更新时间', dataIndex: 'updated_at', key: 'updated_at', render: (text: string) => new Date(text).toLocaleString() },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: DocumentVO) => (
        <Space size="middle">
          <a onClick={() => handleEdit(record)}>编辑</a>
          <a onClick={() => handlePublishToggle(record)}>
            {record.status === 'draft' ? '发布' : '取消发布'}
          </a>
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
    <Card title="文档管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建文档</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder="搜索文档标题" 
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
        title={modalMode === 'create' ? '新建文档' : '编辑文档'}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
        width={1000}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="文档标题" rules={[{ required: true, message: '请输入文档标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="category" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
            <Select disabled={modalMode === 'edit'}>
              <Option value="guide">指南/规范</Option>
              <Option value="report">审计报告</Option>
              <Option value="policy">安全策略</Option>
              <Option value="other">其他</Option>
            </Select>
          </Form.Item>
          <Form.Item name="summary" label="摘要">
            <Input.TextArea rows={2} />
          </Form.Item>
          {modalMode === 'edit' && (
            <Form.Item name="changelog" label="版本更新说明" rules={[{ required: true, message: '请输入本次修改的更新说明' }]}>
              <Input.TextArea rows={2} placeholder="简要描述本次修改了哪些内容" />
            </Form.Item>
          )}
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

export default Documents;