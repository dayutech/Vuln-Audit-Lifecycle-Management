import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Input,
  List,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CloudUploadOutlined,
  FireOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import {
  addKnowledgeTag,
  deleteKnowledge,
  extractKnowledgeList,
  extractStringList,
  getKnowledgeList,
  getKnowledgeTags,
  getPopularKnowledge,
  removeKnowledgeTag,
  searchKnowledge,
} from '@/api/knowledge';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { KnowledgeArticleVO } from '@/types/knowledge';
import { formatDateTime } from '@/utils/datetime';
import { useUserStore } from '@/store/userStore';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { getApiErrorMessage } from '@/utils/errorMessage';
import {
  canDeleteKnowledge,
  canWriteKnowledgeTags,
  resolveCurrentUserId,
  KNOWLEDGE_TAG_FORBIDDEN_MESSAGE,
  KNOWLEDGE_TAG_READONLY_HINT,
} from '@/utils/accessRoles';
import { useT, type MessageKey } from '@/i18n';

const { Text } = Typography;

const CATEGORY_KEYS: Record<string, MessageKey> = {
  vulnerability: 'knowledge.category.vulnerability',
  methodology: 'knowledge.category.methodology',
  experience: 'knowledge.category.experience',
  tool: 'knowledge.category.tool',
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
  const { t } = useT();
  const navigate = useNavigate();
  const currentUser = useUserStore((state) => state.user);
  // t50：DELETE /knowledge/{id} 仅作者本人或管理层
  const { profile: accessProfile, loaded: accessLoaded } = useAccessProfile();
  const effectiveUserId = resolveCurrentUserId(accessProfile, currentUser?.id);

  const [data, setData] = useState<KnowledgeArticleVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  /** t159：状态筛选（当前页过滤；后端 /knowledge 暂不支持 status 参数） */
  const [statusOnly, setStatusOnly] = useState('');
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });

  // 搜索态
  const [searchKeyword, setSearchKeyword] = useState<string | null>(null);
  const [searching, setSearching] = useState(false);

  // 热门
  const [popular, setPopular] = useState<KnowledgeArticleVO[]>([]);

  // 新建

  // 详情

  // 评论

  // 标签
  const [tagArticle, setTagArticle] = useState<KnowledgeArticleVO | null>(null);
  /** t61：文章标签写权（作者本人或管理层）——决定标签抽屉是否渲染增删控件 */
  const tagWritable = accessLoaded && canWriteKnowledgeTags(accessProfile, tagArticle?.authorId);
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
        message.info(t('feedback.knowledge.searchEmpty', { text }));
      } else {
        message.success(t('feedback.knowledge.searchHit', { text, count: results.length }));
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

  // t159（需求 13）：新建/发布改为整页编辑，不再弹框
  const handleCreate = () => {
    navigate('/knowledge/new');
  };

  const handleDelete = (record: KnowledgeArticleVO) => {
    Modal.confirm({
      title: t('knowledge.deleteTitle'),
      content: t('knowledge.deleteConfirm', { title: record.title }),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteKnowledge(record.id);
          message.success(t('feedback.knowledge.deleted'));
          fetchData();
          fetchPopular();
        } catch {
          // 404 由拦截器提示
        }
      },
    });
  };


  // ==================== 详情 ====================

  // t159（需求 13）：详情改为整页（/knowledge/:id），不再用 Drawer
  const openDetail = (record: KnowledgeArticleVO) => {
    navigate(`/knowledge/${record.id}`);
  };

  // ==================== 评论 ====================



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
    // t61：文章标签仅作者本人或管理层可写
    if (!tagWritable) {
      message.error(KNOWLEDGE_TAG_FORBIDDEN_MESSAGE);
      return;
    }
    const tag = newTag.trim();
    if (!tag) {
      message.warning(t('feedback.knowledge.tagRequired'));
      return;
    }
    if (tags.includes(tag)) {
      message.info(t('feedback.knowledge.tagExists'));
      return;
    }
    try {
      await addKnowledgeTag(tagArticle.id, tag);
      setTags((prev) => [...prev, tag]);
      setNewTag('');
      message.success(t('feedback.knowledge.tagAdded', { tag }));
      fetchData();
    } catch (error) {
      message.error(getApiErrorMessage(error, KNOWLEDGE_TAG_FORBIDDEN_MESSAGE));
    }
  };

  const handleRemoveTag = async (tag: string) => {
    if (!tagArticle) return;
    if (!tagWritable) {
      message.error(KNOWLEDGE_TAG_FORBIDDEN_MESSAGE);
      return;
    }
    try {
      await removeKnowledgeTag(tagArticle.id, tag);
      setTags((prev) => prev.filter((item) => item !== tag));
      message.success(t('feedback.knowledge.tagRemoved', { tag }));
      fetchData();
    } catch (error) {
      message.error(getApiErrorMessage(error, KNOWLEDGE_TAG_FORBIDDEN_MESSAGE));
    }
  };

  const columns = useMemo(
    () => [
      {
        title: t('knowledge.colTitle'),
        dataIndex: 'title',
        key: 'title',
        render: (title: string, record: KnowledgeArticleVO) => (
          <a onClick={() => openDetail(record)}>{title}</a>
        ),
      },
      {
        title: t('knowledge.colCategory'),
        dataIndex: 'category',
        key: 'category',
        render: (category: string) => <Tag color="geekblue">{CATEGORY_KEYS[category] ? t(CATEGORY_KEYS[category]) : category}</Tag>,
      },
      {
        // t159（需求 4）：草稿/已发布状态清晰区分
        title: t('knowledge.colStatus'),
        key: 'status',
        width: 110,
        render: (_: unknown, record: KnowledgeArticleVO) => {
          const published = (record.status ?? '').toLowerCase() === 'published';
          return (
            <Tag color={published ? 'green' : 'gold'} data-testid={`knowledge-status-${record.id}`}>
              {published ? t('knowledge.status.published') : t('knowledge.status.draft')}
            </Tag>
          );
        },
      },
      {
        title: t('knowledge.colTagsLabel'),
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
            <Text type="secondary">{t('knowledge.unsetTags')}</Text>
          );
        },
      },
      {
        title: t('knowledge.colAuthor'),
        key: 'author',
        render: (_: unknown, record: KnowledgeArticleVO) => authorName(record),
      },
      {
        title: t('knowledge.colViews'),
        key: 'viewCount',
        render: (_: unknown, record: KnowledgeArticleVO) => record.viewCount ?? record.view_count ?? 0,
      },
      {
        title: t('knowledge.colCreated'),
        key: 'createdAt',
        render: (_: unknown, record: KnowledgeArticleVO) =>
          formatDateTime(record.createdAt ?? record.created_at),
      },
      {
        title: t('knowledge.colAction'),
        key: 'action',
        render: (_: unknown, record: KnowledgeArticleVO) => (
          <Space size="middle" wrap>
            <a onClick={() => openDetail(record)}>{t('knowledge.actionDetail')}</a>
            {/* t159：评论入口移到详情页（列表页不再弹框） */}
            <a onClick={() => openTags(record)}>
              <TagsOutlined /> {t('knowledge.actionTags')}
            </a>
            {accessLoaded && canDeleteKnowledge(accessProfile, record.authorId, effectiveUserId) && (
              <a style={{ color: 'red' }} onClick={() => handleDelete(record)}>{t('knowledge.actionDelete')}</a>
            )}
          </Space>
        ),
      },
    ],
    [data, authorNames],
  );

  return (
    <Card
      title={t('knowledge.title')}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => { fetchData(); fetchPopular(); }}>{t('common.refresh')}</Button>
          <Button icon={<CloudUploadOutlined />} onClick={() => navigate('/knowledge/upload')} data-testid="knowledge-upload-open">
            {t('knowledge.upload.open')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate} data-testid="knowledge-new">
            {t('knowledge.publish')}
          </Button>
        </Space>
      }
    >
      <Row gutter={16}>
        <Col xs={24} lg={17}>
          <Space style={{ marginBottom: 16 }} wrap>
            <Input
              allowClear
              placeholder={t('knowledge.searchPlaceholder')}
              prefix={<SearchOutlined />}
              style={{ width: 340 }}
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              onPressEnter={handleSearch}
            />
            <Button type="primary" loading={searching} onClick={handleSearch}>{t('knowledge.searchButton')}</Button>
            <Button
              onClick={() => setParams({ ...params, search: keyword, page: 1 })}
              disabled={searchKeyword !== null}
            >
              {t('knowledge.advancedFilter')}
            </Button>
            {/* t159（需求 4）：状态筛选（后端列表暂无 status 参数，先按当前页过滤并说明） */}
            <Select
              allowClear
              placeholder={t('knowledge.filterStatus')}
              style={{ width: 150 }}
              value={statusOnly || undefined}
              onChange={(value) => setStatusOnly(value ?? '')}
              data-testid="knowledge-filter-status"
              options={[
                { value: 'draft', label: t('knowledge.status.draft') },
                { value: 'published', label: t('knowledge.status.published') },
              ]}
            />
          </Space>

          {searchKeyword !== null && (
            <Alert
              type="success"
              showIcon
              style={{ marginBottom: 12 }}
              message={t('knowledge.searchBanner', { keyword: searchKeyword ?? '', count: total })}
              action={<a onClick={exitSearch}>{t('knowledge.exitSearch')}</a>}
            />
          )}

          <Table
            columns={columns}
            dataSource={statusOnly ? data.filter((item) => (item.status ?? '').toLowerCase() === statusOnly) : data}
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
          <Card size="small" title={<Space><FireOutlined />{t('knowledge.hotArticles')}</Space>}>
            {popular.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('knowledge.emptyData')} />
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
                            {t('knowledge.metaLine', { views: item.viewCount ?? item.view_count ?? 0, author: authorName(item) })}
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

      {/* 文章详情 */}

      {/* 评论 */}

      {/* 标签 */}
      <Drawer
        title={<Space><TagsOutlined />{t('knowledge.tagsDrawerTitle', { title: tagArticle?.title ?? '' })}</Space>}
        open={!!tagArticle}
        onClose={() => setTagArticle(null)}
        width={420}
        destroyOnClose
      >
        {tagWritable ? (
        <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
          <Input
            placeholder={t('knowledge.tagPlaceholder')}
            value={newTag}
            onChange={(event) => setNewTag(event.target.value)}
            onPressEnter={handleAddTag}
          />
          <Button type="primary" onClick={handleAddTag}>{t('knowledge.addTag')}</Button>
        </Space.Compact>
        ) : (
          <Alert type="info" showIcon style={{ marginBottom: 16 }} message={KNOWLEDGE_TAG_READONLY_HINT} />
        )}

        {tagLoading ? (
          <Text type="secondary">{t('knowledge.loading')}</Text>
        ) : tags.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('knowledge.noTags')} />
        ) : (
          <Space size={[8, 8]} wrap>
            {tags.map((tag) => (
              <Tag key={tag} color="cyan" closable={tagWritable} onClose={() => handleRemoveTag(tag)}>
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










