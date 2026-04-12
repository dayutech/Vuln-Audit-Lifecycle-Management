import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Table, Button, Space, Card, Input, Tag, Modal, Form, Select, message, Tabs } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { getDocumentList, createDocument, updateDocument, deleteDocument, publishDocument, unpublishDocument } from '@/api/document';
import type { DocumentVO, CreateDocumentRequest, UpdateDocumentRequest } from '@/types/document';
import { formatDateTime } from '@/utils/datetime';
import { useLocation } from 'react-router-dom';

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

const containsHtmlTag = (content: string) => /<\/?[a-z][\s\S]*>/i.test(content);
const escapeHtml = (content: string) =>
  content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const renderInlineMarkdown = (text: string) =>
  escapeHtml(text)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');

const renderMarkdownToHtml = (markdown: string) => {
  const codeBlocks: string[] = [];
  const placeholderMarkdown = markdown.replace(/```([\s\S]*?)```/g, (_, block: string) => {
    const index = codeBlocks.push(block) - 1;
    return `@@CODE_BLOCK_${index}@@`;
  });
  const lines = placeholderMarkdown.replace(/\r\n/g, '\n').split('\n');
  const htmlParts: string[] = [];
  let listItems: string[] = [];
  const flushList = () => {
    if (listItems.length > 0) {
      htmlParts.push(`<ul>${listItems.map((item) => `<li>${item}</li>`).join('')}</ul>`);
      listItems = [];
    }
  };

  lines.forEach((raw) => {
    const line = raw.trimEnd();
    if (line.startsWith('- ')) {
      listItems.push(renderInlineMarkdown(line.slice(2).trim()));
      return;
    }
    flushList();
    if (!line.trim()) {
      htmlParts.push('<div><br/></div>');
      return;
    }
    if (line.startsWith('### ')) {
      htmlParts.push(`<h3>${renderInlineMarkdown(line.slice(4).trim())}</h3>`);
      return;
    }
    if (line.startsWith('## ')) {
      htmlParts.push(`<h2>${renderInlineMarkdown(line.slice(3).trim())}</h2>`);
      return;
    }
    if (line.startsWith('# ')) {
      htmlParts.push(`<h1>${renderInlineMarkdown(line.slice(2).trim())}</h1>`);
      return;
    }
    htmlParts.push(`<p>${renderInlineMarkdown(line.trim())}</p>`);
  });
  flushList();

  let html = htmlParts.join('');
  codeBlocks.forEach((block, index) => {
    const codeHtml = `<pre style="padding:12px;background:#f6f8fa;border-radius:6px;overflow:auto;"><code>${escapeHtml(block.trim())}</code></pre>`;
    html = html.replace(`@@CODE_BLOCK_${index}@@`, codeHtml);
  });
  return html;
};

const renderRichContent = (content: string, emptyText: string) => {
  const normalized = (content || '').trim();
  if (!normalized) {
    return `<p>${emptyText}</p>`;
  }
  if (containsHtmlTag(normalized)) {
    return normalized;
  }
  return renderMarkdownToHtml(normalized);
};

const Documents: React.FC = () => {
  const location = useLocation();
  const tableWrapRef = useRef<HTMLDivElement>(null);
  const [data, setData] = useState<DocumentVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });
  const [searchText, setSearchText] = useState('');

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

  useEffect(() => {
    const search = new URLSearchParams(location.search).get('search') || '';
    setSearchText(search);
    setParams((prev) => (prev.search === search ? prev : { ...prev, search, page: 1 }));
  }, [location.search]);

  const normalizedSearch = useMemo(() => params.search.trim().toLowerCase(), [params.search]);

  const isMatchedRecord = (record: DocumentVO) => {
    if (!normalizedSearch) {
      return false;
    }
    const fields = [
      record.id,
      record.title,
      record.category,
      record.summary,
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
      if (!editorContent.trim()) {
        message.warning('请输入正文内容');
        return;
      }
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
    {
      title: '更新时间',
      dataIndex: 'updated_at',
      key: 'updated_at',
      render: (_: unknown, record: DocumentVO & { updatedAt?: string; updated_at?: string }) =>
        formatDateTime(record.updated_at ?? record.updatedAt)
    },
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

  return (
    <Card title="文档管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建文档</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder="搜索文档标题" 
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
            <Tabs
              defaultActiveKey="edit"
              items={[
                {
                  key: 'edit',
                  label: '编写',
                  children: (
                    <Input.TextArea
                      rows={14}
                      value={editorContent}
                      onChange={(e) => setEditorContent(e.target.value)}
                      placeholder="使用 Markdown 编写文档内容"
                    />
                  ),
                },
                {
                  key: 'preview',
                  label: '预览',
                  children: (
                    <div
                      style={{ minHeight: 320, border: '1px solid #f0f0f0', borderRadius: 6, padding: 12, background: '#fafafa' }}
                      dangerouslySetInnerHTML={{ __html: renderRichContent(editorContent, '暂无内容') }}
                    />
                  ),
                },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Documents;
