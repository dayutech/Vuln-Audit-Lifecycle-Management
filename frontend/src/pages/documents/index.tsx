import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Drawer, Empty, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { CloudUploadOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import {
  getDocumentList,
  deleteDocument,
  publishDocument,
  unpublishDocument,
  getDocumentTags,
  addDocumentTag,
  removeDocumentTag,
  extractDocumentTags,
} from '@/api/document';
import type { DocumentVO, DocumentTagVO } from '@/types/document';
import { formatDateTime } from '@/utils/datetime';
import { useLocation } from 'react-router-dom';
import { useT, type MessageKey } from '@/i18n';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { canWriteDocumentTags, DOCUMENT_TAG_FORBIDDEN_MESSAGE, DOCUMENT_TAG_READONLY_HINT } from '@/utils/accessRoles';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';


const statusColors: Record<string, string> = {
  draft: 'default',
  published: 'success',
  archived: 'warning'
};

const STATUS_LABEL_KEYS: Record<string, MessageKey> = {
  draft: 'documents.status.draft',
  published: 'documents.status.published',
  archived: 'documents.status.archived'
};



const Documents: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const location = useLocation();
  const tableWrapRef = useRef<HTMLDivElement>(null);
  const [data, setData] = useState<DocumentVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState<{ page: number; page_size: number; search: string; status?: string }>({ page: 1, page_size: 10, search: '' });
  const [searchText, setSearchText] = useState('');
  /** t159：状态筛选（草稿/已发布） */
  const [statusFilter, setStatusFilter] = useState('');

  // Modal states

  // ===== 文档标签（t66）：后端 t61 起仅作者本人或管理层可写 =====
  const { profile: accessProfile, loaded: accessLoaded } = useAccessProfile();
  const [tagDoc, setTagDoc] = useState<DocumentVO | null>(null);
  const [docTags, setDocTags] = useState<DocumentTagVO[]>([]);
  const [tagLoading, setTagLoading] = useState(false);
  const [tagSubmitting, setTagSubmitting] = useState(false);
  const [newTag, setNewTag] = useState('');
  const [tagError, setTagError] = useState<string | null>(null);
  const docAuthorId = tagDoc?.author_id ?? (tagDoc as any)?.authorId ?? (tagDoc as any)?.createdBy;
  const tagWritable = accessLoaded && canWriteDocumentTags(accessProfile, docAuthorId);

  const loadDocTags = async (documentId: string) => {
    setTagLoading(true);
    setTagError(null);
    try {
      setDocTags(extractDocumentTags(await getDocumentTags(documentId)));
    } catch (error) {
      setDocTags([]);
      // 404：文档已不存在（t61 起不存在的资源返回 404，避免写入孤儿标签）
      setTagError(
        getApiErrorStatus(error) === 404
          ? t('documents.tags.notFound')
          : getApiErrorMessage(error, t('documents.tags.loadFailed')),
      );
    } finally {
      setTagLoading(false);
    }
  };

  const openTags = (record: DocumentVO) => {
    setTagDoc(record);
    setDocTags([]);
    setNewTag('');
    void loadDocTags(record.id);
  };

  const handleAddDocTag = async () => {
    if (!tagDoc) return;
    if (!tagWritable) {
      message.error(DOCUMENT_TAG_FORBIDDEN_MESSAGE);
      return;
    }
    const tag = newTag.trim();
    if (!tag) {
      message.warning(t('documents.tags.needInput'));
      return;
    }
    if (docTags.some((item) => item.tag === tag)) {
      message.info(t('documents.tags.duplicated'));
      return;
    }
    setTagSubmitting(true);
    try {
      await addDocumentTag(tagDoc.id, tag);
      setNewTag('');
      message.success(t('documents.tags.addSuccess', { tag }));
      await loadDocTags(tagDoc.id);
    } catch (error) {
      message.error(
        getApiErrorStatus(error) === 404
          ? t('documents.tags.notFound')
          : getApiErrorMessage(error, DOCUMENT_TAG_FORBIDDEN_MESSAGE),
      );
    } finally {
      setTagSubmitting(false);
    }
  };

  const handleRemoveDocTag = async (tag: DocumentTagVO) => {
    if (!tagDoc) return;
    if (!tagWritable) {
      message.error(DOCUMENT_TAG_FORBIDDEN_MESSAGE);
      return;
    }
    try {
      await removeDocumentTag(tagDoc.id, tag.id);
      message.success(t('documents.tags.removeSuccess', { tag: tag.tag }));
      await loadDocTags(tagDoc.id);
    } catch (error) {
      message.error(
        getApiErrorStatus(error) === 404
          ? t('documents.tags.notFound')
          : getApiErrorMessage(error, DOCUMENT_TAG_FORBIDDEN_MESSAGE),
      );
    }
  };

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
    // t174：状态是服务端筛选参数，必须进依赖
  }, [params.page, params.page_size, params.search, params.status]);

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

  // t159（需求 14）：新建 / 编辑改为整页，不再弹框
  const handleCreate = () => {
    navigate('/documents/new');
  };

  const handleEdit = (record: DocumentVO) => {
    navigate(`/documents/${record.id}/edit`);
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: t('documents.deleteTitle'),
      content: t('documents.deleteConfirm'),
      onOk: async () => {
        try {
          await deleteDocument(id);
          message.success(t('feedback.document.deleted'));
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
        message.success(t('feedback.document.published'));
      } else {
        await unpublishDocument(record.id);
        message.success(t('feedback.document.unpublished'));
      }
      fetchData();
    } catch (error) {
      // Error handled by interceptor
    }
  };


  const columns = [
    { title: t('documents.colTitle'), dataIndex: 'title', key: 'title' },
    { title: t('documents.colCategory'), dataIndex: 'category', key: 'category' },
    { 
      title: t('documents.colStatus'), 
      dataIndex: 'status', 
      key: 'status',
      render: (text: string) => <Tag color={statusColors[text] || 'default'}>{STATUS_LABEL_KEYS[text] ? t(STATUS_LABEL_KEYS[text]) : text || text}</Tag>
    },
    { title: t('documents.colVersionLabel'), dataIndex: 'version', key: 'version', render: (v: number) => `v${v}` },
    {
      title: t('documents.colUpdated'),
      dataIndex: 'updated_at',
      key: 'updated_at',
      render: (_: unknown, record: DocumentVO & { updatedAt?: string; updated_at?: string }) =>
        formatDateTime(record.updated_at ?? record.updatedAt)
    },
    {
      title: t('documents.colAction'),
      key: 'action',
      render: (_: any, record: DocumentVO) => (
        <Space size="middle">
          <a onClick={() => navigate(`/documents/${record.id}`)}>{t('documents.actionDetail')}</a>
          <a onClick={() => handleEdit(record)}>{t('documents.actionEdit')}</a>
          <a onClick={() => openTags(record)}>{t('documents.tags.action')}</a>
          <a onClick={() => handlePublishToggle(record)}>
            {record.status === 'draft' ? t('documents.actionPublish') : t('documents.actionUnpublish')}
          </a>
          <a style={{ color: 'red' }} onClick={() => handleDelete(record.id)}>{t('documents.actionDelete')}</a>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={t('documents.title')}
      extra={
        <Space>
          <Button icon={<CloudUploadOutlined />} onClick={() => navigate('/documents/upload')} data-testid="document-upload-open">
            {t('documents.upload.open')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate} data-testid="document-create">
            {t('documents.create')}
          </Button>
        </Space>
      }
    >
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder={t('documents.searchPlaceholder')} 
          prefix={<SearchOutlined />} 
          style={{ width: 300 }} 
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          onPressEnter={() => setParams((prev) => ({ ...prev, search: searchText.trim(), page: 1 }))}
        />
        {/* t174：状态筛选改为**服务端**（t162 支持 `status=DRAFT|PUBLISHED`，数据库过滤、跨页正确） */}
        <Select
          allowClear
          placeholder={t('documents.filterStatus')}
          style={{ width: 160, marginLeft: 12 }}
          value={statusFilter || undefined}
          onChange={(value) => {
            const next = (value ?? '').toUpperCase();
            setStatusFilter(next);
            // 透传 status 并重置到第 1 页，避免停在不存在的页码
            setParams((prev) => ({ ...prev, status: next || undefined, page: 1 }));
          }}
          data-testid="document-filter-status"
          options={[
            { value: 'DRAFT', label: t('documents.status.draft') },
            { value: 'PUBLISHED', label: t('documents.status.published') },
          ]}
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

      {/* 文档标签（t66） */}
      <Drawer
        title={t('documents.tags.title', { title: tagDoc?.title ?? '' })}
        open={!!tagDoc}
        onClose={() => setTagDoc(null)}
        width={420}
        destroyOnClose
      >
        {tagError ? <Alert type="warning" showIcon style={{ marginBottom: 16 }} message={tagError} /> : null}

        {tagWritable ? (
          <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
            <Input
              placeholder={t('documents.tags.addPlaceholder')}
              value={newTag}
              maxLength={50}
              onChange={(event) => setNewTag(event.target.value)}
              onPressEnter={handleAddDocTag}
            />
            <Button type="primary" loading={tagSubmitting} onClick={handleAddDocTag}>
              {t('documents.tags.add')}
            </Button>
          </Space.Compact>
        ) : (
          <Alert type="info" showIcon style={{ marginBottom: 16 }} message={DOCUMENT_TAG_READONLY_HINT} />
        )}

        {tagLoading ? (
          <Typography.Text type="secondary">{t('documents.tags.loading')}</Typography.Text>
        ) : docTags.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('documents.tags.empty')} />
        ) : (
          <Space size={[8, 8]} wrap>
            {docTags.map((item) => (
              <Tag key={item.id} color="cyan" closable={tagWritable} onClose={() => handleRemoveDocTag(item)}>
                {item.tag}
              </Tag>
            ))}
          </Space>
        )}
      </Drawer>
    </Card>
  );
};

export default Documents;










