import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Descriptions, Input, Space, Spin, Tag, Typography, message } from 'antd';
import { EditOutlined, SendOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { addKnowledgeComment, getKnowledgeDetail } from '@/api/knowledge';
import { renderRichContent } from '@/pages/knowledge/richContent';
import { hasLegacyAttachment, stripAttachment } from '@/utils/attachments';
import { canWriteKnowledgeTags } from '@/utils/accessRoles';
import AttachmentPanel from '@/components/attachments/AttachmentPanel';
import { getMyAccessProfile } from '@/api/permission';
import { getApiErrorMessage } from '@/utils/errorMessage';
import { formatDateTime } from '@/utils/datetime';
import { useT, type MessageKey } from '@/i18n';
import type { KnowledgeArticleVO } from '@/types/knowledge';

const CATEGORY_KEYS: Record<string, MessageKey> = {
  vulnerability: 'knowledge.category.vulnerability',
  methodology: 'knowledge.category.methodology',
  experience: 'knowledge.category.experience',
  tool: 'knowledge.category.tool',
};

/**
 * 知识库详情（t159，需求 13）：**整页**，替换原来的右侧 Drawer。
 * 附件（若有）在这里可直接下载；编辑入口跳到 `/knowledge/:id/edit`。
 */
const KnowledgeDetailPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [article, setArticle] = useState<KnowledgeArticleVO | null>(null);
  const [loading, setLoading] = useState(false);
  /** t159：评论从列表页的弹框移到详情页内联（列表页已无编辑/详情弹框） */
  const [comment, setComment] = useState('');
  const [commentSubmitting, setCommentSubmitting] = useState(false);
  /** t174：附件上传/删除沿用「知识库写权限」判定（作者或管理角色），未放宽 */
  const [accessProfile, setAccessProfile] = useState<any>(null);
  const [accessLoaded, setAccessLoaded] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    (async () => {
      try {
        setLoading(true);
        const res: any = await getKnowledgeDetail(id);
        if (!cancelled) setArticle(res?.data ? res.data : res);
      } catch (error) {
        message.error(getApiErrorMessage(error, t('knowledge.loadFailed')));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id, t]);

  const submitComment = async () => {
    if (!id || !comment.trim()) return;
    try {
      setCommentSubmitting(true);
      await addKnowledgeComment(id, comment.trim());
      message.success(t('knowledge.commentSubmitted'));
      setComment('');
    } catch (error) {
      message.error(getApiErrorMessage(error, t('knowledge.commentFailed')));
    } finally {
      setCommentSubmitting(false);
    }
  };

  // t174：正文与附件分区；旧格式（t159 base64）只做提示，不转换
  const content = useMemo(() => stripAttachment(article?.content), [article?.content]);
  useEffect(() => {
    getMyAccessProfile()
      .then((res: any) => setAccessProfile(res?.data ?? res))
      .catch(() => setAccessProfile(null))
      .finally(() => setAccessLoaded(true));
  }, []);

  const attachmentWritable = accessLoaded && canWriteKnowledgeTags(accessProfile, article?.authorId);

  const legacyAttachment = useMemo(() => hasLegacyAttachment(article?.content), [article?.content]);
  const tags = (article?.tags ?? '').split(',').map((tag) => tag.trim()).filter(Boolean);
  const published = (article?.status ?? '').toLowerCase() === 'published';

  return (
    <>
      <PageHeader
        title={article?.title ?? t('knowledge.detailTitle')}
        subtitle={article ? formatDateTime(article.updatedAt ?? article.createdAt) : undefined}
        fallbackTo="/knowledge"
        extra={
          <Button
            type="primary"
            icon={<EditOutlined />}
            onClick={() => navigate(`/knowledge/${id}/edit`)}
            data-testid="knowledge-detail-edit"
          >
            {t('common.edit')}
          </Button>
        }
      />
      <div data-testid="knowledge-detail-page">
        <Spin spinning={loading}>
          <Card>
            <Descriptions size="small" column={3} style={{ marginBottom: 16 }}>
              <Descriptions.Item label={t('knowledge.form.category')}>
                {article?.category ? (
                  <Tag color="geekblue">
                    {CATEGORY_KEYS[article.category] ? t(CATEGORY_KEYS[article.category]) : article.category}
                  </Tag>
                ) : (
                  '-'
                )}
              </Descriptions.Item>
              <Descriptions.Item label={t('knowledge.colStatus')}>
                <Tag color={published ? 'green' : 'gold'}>
                  {published ? t('knowledge.status.published') : t('knowledge.status.draft')}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('knowledge.colViews')}>{article?.viewCount ?? 0}</Descriptions.Item>
            </Descriptions>
            {tags.length > 0 ? (
              <Space wrap style={{ marginBottom: 12 }}>
                {tags.map((tag) => (
                  <Tag key={tag} color="cyan">
                    {tag}
                  </Tag>
                ))}
              </Space>
            ) : null}
            {article?.summary ? (
              <Typography.Paragraph type="secondary">{article.summary}</Typography.Paragraph>
            ) : null}
            {legacyAttachment ? (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 12 }}
                message={t('docAttachment.legacyNotice')}
                data-testid="knowledge-legacy-attachment"
              />
            ) : null}
            <div
              className="knowledge-content"
              data-testid="knowledge-detail-content"
              dangerouslySetInnerHTML={{ __html: renderRichContent(content, t('knowledge.emptyContent')) }}
            />

            {/* t174：附件区（真实端点，正文与附件分区） */}
            <Card size="small" style={{ marginTop: 16 }}>
              <AttachmentPanel ownerType="knowledge" ownerId={String(id ?? '')} canWrite={attachmentWritable} />
            </Card>

            {/* t159：评论从列表页的弹框移到详情页内联（列表页已无编辑/详情弹框） */}
            <div style={{ marginTop: 24 }} data-testid="knowledge-comment-box">
              <Typography.Title level={5}>{t('knowledge.actionComment')}</Typography.Title>
              <Input.TextArea
                rows={3}
                maxLength={1000}
                showCount
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                data-testid="knowledge-comment-input"
              />
              <Button
                type="primary"
                icon={<SendOutlined />}
                style={{ marginTop: 8 }}
                loading={commentSubmitting}
                disabled={!comment.trim()}
                onClick={submitComment}
                data-testid="knowledge-comment-submit"
              >
                {t('knowledge.submitComment')}
              </Button>
            </div>
          </Card>
        </Spin>
      </div>
    </>
  );
};

export default KnowledgeDetailPage;






