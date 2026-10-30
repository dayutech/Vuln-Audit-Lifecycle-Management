import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Divider,
  Drawer,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CommentOutlined,
  FireOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import {
  addKnowledgeComment,
  addKnowledgeTag,
  createKnowledge,
  deleteKnowledge,
  extractKnowledgeList,
  extractStringList,
  getKnowledgeDetail,
  getKnowledgeList,
  getKnowledgeTags,
  getPopularKnowledge,
  removeKnowledgeTag,
  searchKnowledge,
} from '@/api/knowledge';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { CreateKnowledgeRequest, KnowledgeArticleVO } from '@/types/knowledge';
import { formatDateTime } from '@/utils/datetime';
import { useUserStore } from '@/store/userStore';

const { Option } = Select;
const { Text, Paragraph } = Typography;

const categoryLabels: Record<string, string> = {
  vulnerability: '漏洞分析',
  methodology: '方法论',
  experience: '实战经验',
  tool: '工具使用',
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

const splitTags = (tags?: string): string[] =>
  (tags ?? '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);

/**
 * 安全知识库（/knowledge）
 *
 * 对接后端：
 * - 列表/详情/新建/删除：GET|POST|PUT|DELETE /knowledge
 * - 搜索：GET /knowledge/search?keyword=（返回列表，不分页）
 * - 热门：GET /knowledge/popular?limit=（按浏览量倒序）
 * - 评论：POST /knowledge/{id}/comments?content=（后端暂无评论列表查询接口，提交后给出明确提示）
 * - 标签：GET|POST|DELETE /knowledge/{id}/tags?tag=***
 */
const Knowledge: React.FC = () => {
  const currentUser = useUserStore((state) => state.user);

  const [data, setData] = useState<KnowledgeArticleVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });

  // 搜索态
  const [searchKeyword, setSearchKeyword] = useState<string | null>(null);
  const [searching, setSearching] = useState(false);

  // 热门
  const [popular, setPopular] = useState<KnowledgeArticleVO[]>([]);

  // 新建
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [editorContent, setEditorContent] = useState('');
  const [creating, setCreating] = useState(false);

  // 详情
  const [detail, setDetail] = useState<KnowledgeArticleVO | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // 评论
  const [commentArticle, setCommentArticle] = useState<KnowledgeArticleVO | null>(null);
  const [commentContent, setCommentContent] = useState('');
  const [commentSubmitting, setCommentSubmitting] = useState(false);

  // 标签
  const [tagArticle, setTagArticle] = useState<KnowledgeArticleVO | null>(null);
  const [tags, setTags] = useState<string[]>([]);
  const [tagLoading, setTagLoading] = useState(false);
  const [newTag, setNewTag] = useState('');

  // 作者名映射（后端文章只返回 authorId，没有嵌套 author 对象）
  const [authorNames, setAuthorNames] = useState<Record<string, string>>({});

  const fetchAuthors = async () => {
    try {
      // 作者名只取自用户目录（id/username/fullName）；工程师可读，不会 403
      const list = await getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT });
      const map: Record<string, string> = {};
      list.forEach((user) => {
        if (user?.id) {
          map[user.id] = user.fullName || user.username || user.id;
        }
      });
      setAuthorNames(map);
    } catch {
      setAuthorNames({});
    }
  };

  const fetchPopular = async () => {
    try {
      const res: any = await getPopularKnowledge(5);
      setPopular(extractKnowledgeList(res));
    } catch {
      setPopular([]);
    }
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const res: any = await getKnowledgeList(params);
      const payload = res?.data ?? res ?? {};
      setData(payload.content || payload.items || []);
      setTotal(payload.totalElements || payload.total || 0);
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAuthors();
    fetchPopular();
  }, []);

  useEffect(() => {
    if (searchKeyword === null) {
      fetchData();
    }
  }, [params.page, params.page_size, params.search, searchKeyword]);

  const authorName = (article: KnowledgeArticleVO) => {
    const id = article.authorId;
    if (!id) return '-';
    return authorNames[id] ?? `${id.slice(0, 8)}…`;
  };

  // ==================== 搜索 ====================

  const handleSearch = async () => {
    const text = keyword.trim();
    if (!text) {
      setSearchKeyword(null);
      return;
    }
    setSearching(true);
    try {
      const res: any = await searchKnowledge(text);
      const results = extractKnowledgeList(res);
      setData(results);
      setTotal(results.length);
      setSearchKeyword(text);
      if (results.length === 0) {
        message.info(`没有匹配「${text}」的文章`);
      } else {
        message.success(`搜索「${text}」命中 ${results.length} 篇`);
      }
    } catch {
      // 拦截器提示
    } finally {
      setSearching(false);
    }
  };

  const exitSearch = () => {
    setSearchKeyword(null);
    setKeyword('');
    setParams({ ...params, search: '', page: 1 });
    fetchData();
  };

  // ==================== 新建 / 删除 ====================

  const handleCreate = () => {
    form.resetFields();
    setEditorContent('');
    setIsModalVisible(true);
  };

  const handleDelete = (record: KnowledgeArticleVO) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除文章 [${record.title}] 吗？此操作不可恢复。`,
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteKnowledge(record.id);
          message.success('删除成功');
          fetchData();
          fetchPopular();
        } catch {
          // 404 由拦截器提示
        }
      },
    });
  };

  const handleModalOk = async () => {
    let values: any;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    if (!editorContent.trim()) {
      message.warning('请输入正文内容');
      return;
    }
    if (!currentUser?.id) {
      message.error('未获取到当前登录用户信息，请刷新页面后重试');
      return;
    }
    setCreating(true);
    try {
      await createKnowledge({
        title: values.title,
        category: values.category,
        summary: values.summary,
        content: editorContent,
        tags: values.tags || [],
        status: 'published',
      } as CreateKnowledgeRequest);
      message.success('发布成功');
      setIsModalVisible(false);
      fetchData();
      fetchPopular();
    } catch {
      // 400 由拦截器提示
    } finally {
      setCreating(false);
    }
  };

  // ==================== 详情 ====================

  const openDetail = async (record: KnowledgeArticleVO) => {
    setDetail(record);
    setDetailLoading(true);
    try {
      const res: any = await getKnowledgeDetail(record.id);
      const payload = res?.data ?? res;
      if (payload && payload.id) {
        setDetail(payload);
      }
    } catch {
      // 详情接口会累加浏览量，失败时仍展示列表里的内容
    } finally {
      setDetailLoading(false);
    }
  };

  // ==================== 评论 ====================

  const openComment = (record: KnowledgeArticleVO) => {
    setCommentArticle(record);
    setCommentContent('');
  };

  const handleSubmitComment = async () => {
    if (!commentArticle) return;
    const content = commentContent.trim();
    if (!content) {
      message.warning('请输入评论内容');
      return;
    }
    setCommentSubmitting(true);
    try {
      await addKnowledgeComment(commentArticle.id, content);
      message.success('评论已提交');
      setCommentArticle(null);
      setCommentContent('');
    } catch {
      // 404 由拦截器提示
    } finally {
      setCommentSubmitting(false);
    }
  };

  // ==================== 标签 ====================

  const openTags = async (record: KnowledgeArticleVO) => {
    setTagArticle(record);
    setNewTag('');
    setTags(splitTags(record.tags));
    setTagLoading(true);
    try {
      const res: any = await getKnowledgeTags(record.id);
      setTags(extractStringList(res));
    } catch {
      // 退回实体 tags 字段
    } finally {
      setTagLoading(false);
    }
  };

  const handleAddTag = async () => {
    if (!tagArticle) return;
    const tag = newTag.trim();
    if (!tag) {
      message.warning('请输入标签内容');
      return;
    }
    if (tags.includes(tag)) {
      message.info('该标签已存在');
      return;
    }
    try {
      await addKnowledgeTag(tagArticle.id, tag);
      setTags((prev) => [...prev, tag]);
      setNewTag('');
      message.success(`已添加标签「${tag}」`);
      fetchData();
    } catch {
      // 拦截器提示
    }
  };

  const handleRemoveTag = async (tag: string) => {
    if (!tagArticle) return;
    try {
      await removeKnowledgeTag(tagArticle.id, tag);
      setTags((prev) => prev.filter((item) => item !== tag));
      message.success(`已移除标签「${tag}」`);
      fetchData();
    } catch {
      // 拦截器提示
    }
  };

  const columns = useMemo(
    () => [
      {
        title: '标题',
        dataIndex: 'title',
        key: 'title',
        render: (title: string, record: KnowledgeArticleVO) => (
          <a onClick={() => openDetail(record)}>{title}</a>
        ),
      },
      {
        title: '分类',
        dataIndex: 'category',
        key: 'category',
        render: (category: string) => <Tag color="geekblue">{categoryLabels[category] ?? category}</Tag>,
      },
      {
        title: '标签',
        key: 'tags',
        render: (_: unknown, record: KnowledgeArticleVO) => {
          const list = splitTags(record.tags);
          return list.length > 0 ? (
            <Space size={[0, 4]} wrap>
              {list.map((tag) => (
                <Tag key={tag} color="cyan">{tag}</Tag>
              ))}
            </Space>
          ) : (
            <Text type="secondary">未设置</Text>
          );
        },
      },
      {
        title: '作者',
        key: 'author',
        render: (_: unknown, record: KnowledgeArticleVO) => authorName(record),
      },
      {
        title: '浏览量',
        key: 'viewCount',
        render: (_: unknown, record: KnowledgeArticleVO) => record.viewCount ?? record.view_count ?? 0,
      },
      {
        title: '创建时间',
        key: 'createdAt',
        render: (_: unknown, record: KnowledgeArticleVO) =>
          formatDateTime(record.createdAt ?? record.created_at),
      },
      {
        title: '操作',
        key: 'action',
        render: (_: unknown, record: KnowledgeArticleVO) => (
          <Space size="middle" wrap>
            <a onClick={() => openDetail(record)}>详情</a>
            <a onClick={() => openComment(record)}>
              <CommentOutlined /> 评论
            </a>
            <a onClick={() => openTags(record)}>
              <TagsOutlined /> 标签
            </a>
            <a style={{ color: 'red' }} onClick={() => handleDelete(record)}>删除</a>
          </Space>
        ),
      },
    ],
    [data, authorNames],
  );

  return (
    <Card
      title="安全知识库"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => { fetchData(); fetchPopular(); }}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>发布文章</Button>
        </Space>
      }
    >
      <Row gutter={16}>
        <Col xs={24} lg={17}>
          <Space style={{ marginBottom: 16 }} wrap>
            <Input
              allowClear
              placeholder="搜索标题/正文关键词，回车搜索"
              prefix={<SearchOutlined />}
              style={{ width: 340 }}
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              onPressEnter={handleSearch}
            />
            <Button type="primary" loading={searching} onClick={handleSearch}>搜索</Button>
            <Button
              onClick={() => setParams({ ...params, search: keyword, page: 1 })}
              disabled={searchKeyword !== null}
            >
              高级筛选
            </Button>
          </Space>

          {searchKeyword !== null && (
            <Alert
              type="success"
              showIcon
              style={{ marginBottom: 12 }}
              message={`搜索「${searchKeyword}」命中 ${total} 篇（GET /knowledge/search）`}
              action={<a onClick={exitSearch}>退出搜索</a>}
            />
          )}

          <Table
            columns={columns}
            dataSource={data}
            rowKey="id"
            loading={loading}
            pagination={
              searchKeyword !== null
                ? false
                : {
                    current: params.page,
                    pageSize: params.page_size,
                    total,
                    showSizeChanger: true,
                    onChange: (page, pageSize) => setParams({ ...params, page, page_size: pageSize }),
                  }
            }
          />
        </Col>

        <Col xs={24} lg={7}>
          <Card size="small" title={<Space><FireOutlined />热门文章</Space>}>
            {popular.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
            ) : (
              <List
                size="small"
                dataSource={popular}
                renderItem={(item, index) => (
                  <List.Item>
                    <Space align="start">
                      <Tag color={index < 3 ? 'red' : 'default'}>{index + 1}</Tag>
                      <div>
                        <a onClick={() => openDetail(item)}>{item.title}</a>
                        <div>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            浏览 {item.viewCount ?? item.view_count ?? 0} · {authorName(item)}
                          </Text>
                        </div>
                      </div>
                    </Space>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* 发布文章 */}
      <Modal
        title="发布文章"
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        confirmLoading={creating}
        okText="发布"
        destroyOnClose
        width={1000}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="文章标题" rules={[{ required: true, message: '请输入文章标题' }]}>
            <Input maxLength={500} />
          </Form.Item>
          <Form.Item name="category" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
            <Select>
              {Object.entries(categoryLabels).map(([value, label]) => (
                <Option key={value} value={value}>{label}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="summary" label="摘要（可选）">
            <Input maxLength={500} />
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
                      onChange={(event) => setEditorContent(event.target.value)}
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

      {/* 文章详情 */}
      <Drawer
        title={detail?.title ?? '文章详情'}
        open={!!detail}
        onClose={() => setDetail(null)}
        width={720}
        destroyOnClose
      >
        {detail && (
          <>
            <Space wrap style={{ marginBottom: 8 }}>
              <Tag color="geekblue">{categoryLabels[detail.category] ?? detail.category}</Tag>
              {splitTags(detail.tags).map((tag) => (
                <Tag key={tag} color="cyan">{tag}</Tag>
              ))}
              <Text type="secondary">
                作者 {authorName(detail)} · 浏览 {detail.viewCount ?? detail.view_count ?? 0} ·{' '}
                {formatDateTime(detail.createdAt ?? detail.created_at)}
              </Text>
            </Space>
            <Divider />
            <div
              style={{ lineHeight: 1.8 }}
              dangerouslySetInnerHTML={{ __html: renderRichContent(detail.content, '暂无内容') }}
            />
            <Divider />
            <Space>
              <Button icon={<CommentOutlined />} onClick={() => openComment(detail)}>发表评论</Button>
              <Button icon={<TagsOutlined />} onClick={() => openTags(detail)}>管理标签</Button>
            </Space>
            {detailLoading && <Paragraph type="secondary">加载最新内容…</Paragraph>}
          </>
        )}
      </Drawer>

      {/* 评论 */}
      <Modal
        title={`发表评论 · ${commentArticle?.title ?? ''}`}
        open={!!commentArticle}
        onOk={handleSubmitComment}
        onCancel={() => setCommentArticle(null)}
        okText="提交评论"
        confirmLoading={commentSubmitting}
        destroyOnClose
      >
        <Input.TextArea
          rows={5}
          maxLength={1000}
          showCount
          value={commentContent}
          onChange={(event) => setCommentContent(event.target.value)}
          placeholder="写下你的补充、复现要点或修复建议"
        />
        <Alert
          type="info"
          showIcon
          style={{ marginTop: 12 }}
          message="评论提交后写入后端（POST /knowledge/{id}/comments）。当前后端未提供评论列表查询接口，因此页面不回显历史评论。"
        />
      </Modal>

      {/* 标签 */}
      <Drawer
        title={<Space><TagsOutlined />标签管理 · {tagArticle?.title}</Space>}
        open={!!tagArticle}
        onClose={() => setTagArticle(null)}
        width={420}
        destroyOnClose
      >
        <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
          <Input
            placeholder="输入标签，如 反序列化"
            value={newTag}
            onChange={(event) => setNewTag(event.target.value)}
            onPressEnter={handleAddTag}
          />
          <Button type="primary" onClick={handleAddTag}>添加</Button>
        </Space.Compact>

        {tagLoading ? (
          <Text type="secondary">加载中…</Text>
        ) : tags.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该文章暂无标签" />
        ) : (
          <Space size={[8, 8]} wrap>
            {tags.map((tag) => (
              <Tag key={tag} color="cyan" closable onClose={() => handleRemoveTag(tag)}>
                {tag}
              </Tag>
            ))}
          </Space>
        )}
      </Drawer>
    </Card>
  );
};

export default Knowledge;
