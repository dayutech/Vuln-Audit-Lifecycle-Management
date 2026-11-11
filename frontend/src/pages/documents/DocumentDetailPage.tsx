import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Descriptions, Modal, Spin, Table, Tag, Typography, message } from 'antd';
import { EditOutlined, HistoryOutlined, SendOutlined, StopOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { getDocumentDetail, getDocumentVersions, publishDocument, unpublishDocument } from '@/api/document';
import { renderRichContent } from '@/pages/knowledge/richContent';
import { hasLegacyAttachment, stripAttachment } from '@/utils/attachments';
import AttachmentPanel from '@/components/attachments/AttachmentPanel';
import { canWriteDocumentTags } from '@/utils/accessRoles';
import { getMyAccessProfile } from '@/api/permission';
import { getApiErrorMessage } from '@/utils/errorMessage';
import { formatDateTime } from '@/utils/datetime';
import { useT } from '@/i18n';
import type { DocumentVO } from '@/types/document';

/**
 * 规范与文档详情（t159，需求 14）：**整页**，替换原来的 Drawer。
 *
 * 版本历史仍用弹框查看（任务允许：只有**编辑**必须整页）。
 */
const DocumentDetailPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [doc, setDoc] = useState<DocumentVO | null>(null);
  const [loading, setLoading] = useState(false);
  /** t174：附件上传/删除沿用「文档写权限」判定（作者或管理角色），未放宽 */
  const [accessProfile, setAccessProfile] = useState<any>(null);
  const [accessLoaded, setAccessLoaded] = useState(false);
  const [versionsOpen, setVersionsOpen] = useState(false);
  const [versions, setVersions] = useState<any[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    (async () => {
      try {
        setLoading(true);
        const res: any = await getDocumentDetail(id);
        if (!cancelled) setDoc(res?.data ? res.data : res);
      } catch (error) {
        message.error(getApiErrorMessage(error, t('documents.loadFailed')));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id, t]);

  const openVersions = async () => {
    if (!id) return;
    setVersionsOpen(true);
    try {
      setVersionsLoading(true);
      const res: any = await getDocumentVersions(id);
      const payload = res?.data ?? res;
      setVersions(Array.isArray(payload) ? payload : Array.isArray(payload?.content) ? payload.content : []);
    } catch {
      setVersions([]);
    } finally {
      setVersionsLoading(false);
    }
  };

  const togglePublish = async () => {
    if (!doc) return;
    try {
      if ((doc.status ?? '').toLowerCase() === 'draft') {
        await publishDocument(doc.id);
        message.success(t('feedback.document.published'));
      } else {
        await unpublishDocument(doc.id);
        message.success(t('feedback.document.unpublished'));
      }
      const res: any = await getDocumentDetail(doc.id);
      setDoc(res?.data ? res.data : res);
    } catch (error) {
      message.error(getApiErrorMessage(error, t('documents.saveFailed')));
    }
  };

  // t174：正文与附件分区；旧格式（t159 base64）只提示，不转换
  const content = useMemo(() => stripAttachment(doc?.content), [doc?.content]);
  useEffect(() => {
    getMyAccessProfile()
      .then((res: any) => setAccessProfile(res?.data ?? res))
      .catch(() => setAccessProfile(null))
      .finally(() => setAccessLoaded(true));
  }, []);

  const attachmentWritable = accessLoaded && canWriteDocumentTags(accessProfile, (doc as any)?.authorId ?? (doc as any)?.author_id);

  const legacyAttachment = useMemo(() => hasLegacyAttachment(doc?.content), [doc?.content]);
  const published = (doc?.status ?? '').toLowerCase() !== 'draft';

  return (
    <>
      <PageHeader
        title={doc?.title ?? t('documents.detailTitle')}
        subtitle={doc ? `${t('documents.colVersionLabel')} v${doc.version ?? 1} · ${formatDateTime(doc.updated_at)}` : undefined}
        fallbackTo="/documents"
        extra={
          <>
            <Button icon={<HistoryOutlined />} onClick={openVersions} data-testid="document-versions-open">
              {t('documents.versions')}
            </Button>
            <Button icon={published ? <StopOutlined /> : <SendOutlined />} onClick={togglePublish} data-testid="document-publish-toggle">
              {published ? t('documents.unpublish') : t('documents.publish')}
            </Button>
            <Button
              type="primary"
              icon={<EditOutlined />}
              onClick={() => navigate(`/documents/${id}/edit`)}
              data-testid="document-detail-edit"
            >
              {t('common.edit')}
            </Button>
          </>
        }
      />
      <div data-testid="document-detail-page">
        <Spin spinning={loading}>
          <Card>
            <Descriptions size="small" column={3} style={{ marginBottom: 16 }}>
              <Descriptions.Item label={t('documents.colCategory')}>{doc?.category ?? '-'}</Descriptions.Item>
              <Descriptions.Item label={t('documents.colStatus')}>
                <Tag color={published ? 'green' : 'gold'}>{published ? t('documents.statusPublished') : t('documents.statusDraft')}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('documents.colVersionLabel')}>v{doc?.version ?? 1}</Descriptions.Item>
            </Descriptions>
            {doc?.summary ? <Typography.Paragraph type="secondary">{doc.summary}</Typography.Paragraph> : null}
            {legacyAttachment ? (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 12 }}
                message={t('docAttachment.legacyNotice')}
                data-testid="document-legacy-attachment"
              />
            ) : null}
            <div
              data-testid="document-detail-content"
              dangerouslySetInnerHTML={{ __html: renderRichContent(content, t('documents.emptyContent')) }}
            />
            {/* t174：附件区（真实端点，正文与附件分区） */}
            <Card size="small" style={{ marginTop: 16 }}>
              <AttachmentPanel ownerType="documents" ownerId={String(id ?? '')} canWrite={attachmentWritable} />
            </Card>
          </Card>
        </Spin>
      </div>

      <Modal
        open={versionsOpen}
        title={t('documents.versions')}
        onCancel={() => setVersionsOpen(false)}
        footer={null}
        width={720}
      >
        <Table
          size="small"
          rowKey={(row: any) => String(row?.id ?? row?.version)}
          loading={versionsLoading}
          dataSource={versions}
          pagination={false}
          data-testid="document-versions-table"
          columns={[
            { title: t('documents.colVersionLabel'), key: 'version', render: (_: unknown, row: any) => `v${row?.version ?? '-'}` },
            { title: t('documents.form.changelog'), key: 'changelog', render: (_: unknown, row: any) => row?.changelog || '-' },
            {
              title: t('documents.colUpdatedAt'),
              key: 'createdAt',
              render: (_: unknown, row: any) => formatDateTime(row?.createdAt ?? row?.updatedAt),
            },
          ]}
          locale={{ emptyText: t('documents.versionsEmpty') }}
        />
      </Modal>
    </>
  );
};

export default DocumentDetailPage;





