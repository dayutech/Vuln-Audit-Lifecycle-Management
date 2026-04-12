import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Input, Tag, Modal, Form, Select, message, Tabs } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { getKnowledgeList, createKnowledge, deleteKnowledge } from '@/api/knowledge';
import type { KnowledgeArticleVO, CreateKnowledgeRequest } from '@/types/knowledge';
import { formatDateTime } from '@/utils/datetime';

const { Option } = Select;

type KnowledgeTagItem = { id: string; tag: string };

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

const normalizeKnowledgeTags = (value: unknown): KnowledgeTagItem[] => {
  if (Array.isArray(value)) {
    return value
      .map((item, index) => {
        if (typeof item === 'string') {
          const text = item.trim();
          if (!text) return null;
          return { id: `${text}-${index}`, tag: text };
        }
        if (item && typeof item === 'object') {
          const candidate = item as Record<string, unknown>;
          const tagText = typeof candidate.tag === 'string'
            ? candidate.tag.trim()
            : typeof candidate.name === 'string'
              ? candidate.name.trim()
              : '';
          if (!tagText) return null;
          const tagId = typeof candidate.id === 'string' && candidate.id
            ? candidate.id
            : `${tagText}-${index}`;
          return { id: tagId, tag: tagText };
        }
        return null;
      })
      .filter((item): item is KnowledgeTagItem => item !== null);
  }

  if (typeof value === 'string') {
    const text = value.trim();
    if (!text) return [];
    return text
      .split(',')
      .map(s => s.trim())
      .filter(Boolean)
      .map((tag, index) => ({ id: `${tag}-${index}`, tag }));
  }

  if (value && typeof value === 'object') {
    const candidate = value as Record<string, unknown>;
    if (Array.isArray(candidate.items)) {
      return normalizeKnowledgeTags(candidate.items);
    }
  }

  return [];
};

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
      const normalizedItems = items.map((item: KnowledgeArticleVO) => ({
        ...item,
        tags: normalizeKnowledgeTags(item.tags),
      }));
      const totalCount = payload.totalElements || payload.total || 0;
      setData(normalizedItems);
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
      if (!editorContent.trim()) {
        message.warning('请输入正文内容');
        return;
      }
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
          {normalizeKnowledgeTags(record.tags).map(t => <Tag key={t.id} color="cyan">{t.tag}</Tag>)}
        </>
      )
    },
    { 
      title: '作者', 
      key: 'author',
      render: (_: any, record: KnowledgeArticleVO) => record.author?.full_name || record.author?.username || '-'
    },
    { title: '浏览量', dataIndex: 'view_count', key: 'view_count' },
    {
      title: '创建时间',
      dataIndex: 'created_at',
      key: 'created_at',
      render: (_: unknown, record: KnowledgeArticleVO & { createdAt?: string; created_at?: string }) =>
        formatDateTime(record.created_at ?? record.createdAt)
    },
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
                      placeholder="使用 Markdown 编写文章内容"
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

export default Knowledge;
