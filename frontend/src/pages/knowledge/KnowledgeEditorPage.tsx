import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Select, Space, Tabs, message } from 'antd';
import { CloudUploadOutlined, SaveOutlined, SendOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { PageFormShell, FormGroup } from '@/components/layout/PageFormShell';
import { createKnowledge, getKnowledgeDetail, updateKnowledge } from '@/api/knowledge';
import { renderRichContent } from '@/pages/knowledge/richContent';
import { downloadRecordFile, parseAttachment, stripAttachment } from '@/utils/attachments';
import { getApiErrorMessage } from '@/utils/errorMessage';
import { useT, type MessageKey } from '@/i18n';
import type { KnowledgeArticleVO } from '@/types/knowledge';

/**
 * 知识库整页编辑 / 发布（t159，需求 13）。
 *
 * - `/knowledge/new`：新建；`/knowledge/:id/edit`：编辑；
 * - 顶部 `PageHeader`（返回 + 标题 + 「保存草稿 / 发布」），正文用 Tab 在「编写 / 预览」间切换；
 * - **不是弹框**：原 `/knowledge` 列表页的发布 `Modal` 已删除，一切编辑都在这里；
 * - 有未保存改动时返回会二次确认。
 */
const { Option } = Select;

const CATEGORY_KEYS: Record<string, MessageKey> = {
  vulnerability: 'knowledge.category.vulnerability',
  methodology: 'knowledge.category.methodology',
  experience: 'knowledge.category.experience',
  tool: 'knowledge.category.tool',
};

interface FormValues {
  title: string;
  category: string;
  summary?: string;
  tags?: string[];
}

const KnowledgeEditorPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);

  const [form] = Form.useForm<FormValues>();
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState<'draft' | 'published' | null>(null);
  const [snapshot, setSnapshot] = useState('');
  /** t159：脏标记（Form.onValuesChange + 正文 onChange 都会置位），用于离开二次确认 */
  const [dirtyFlag, setDirtyFlag] = useState(false);

  const currentSnapshot = useCallback(
    () => JSON.stringify({ values: form.getFieldsValue(true), content }),
    [form, content],
  );

  useEffect(() => {
    if (!isEdit || !id) {
      setSnapshot(JSON.stringify({ values: {}, content: '' }));
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        setLoading(true);
        const res: any = await getKnowledgeDetail(id);
        const data: KnowledgeArticleVO = res?.data ? res.data : res;
        if (cancelled || !data) return;
        form.setFieldsValue({
          title: data.title,
          category: data.category,
          summary: data.summary ?? undefined,
          tags: typeof data.tags === 'string' ? data.tags.split(',').filter(Boolean) : undefined,
        });
        const text = stripAttachment(data.content);
        setContent(text);
        setSnapshot(JSON.stringify({ values: form.getFieldsValue(true), content: text }));
      } catch (error) {
        message.error(getApiErrorMessage(error, t('knowledge.loadFailed')));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id, isEdit, form, t]);

  const dirty = useMemo(() => dirtyFlag || currentSnapshot() !== snapshot, [dirtyFlag, currentSnapshot, snapshot]);

  const confirmLeave = useCallback(
    (leave: () => void) => {
      if (!dirty) {
        leave();
        return;
      }
      Modal.confirm({
        title: t('knowledge.discardTitle'),
        content: t('knowledge.discardContent'),
        okText: t('knowledge.discardOk'),
        cancelText: t('common.cancel'),
        okButtonProps: { danger: true },
        onOk: leave,
      });
    },
    [dirty, t],
  );

  const save = async (status: 'draft' | 'published') => {
    try {
      const values = await form.validateFields();
      setSubmitting(status);
      const payload = {
        title: values.title.trim(),
        category: values.category,
        summary: values.summary?.trim(),
        tags: values.tags ?? [],
        content,
        status,
      };
      if (isEdit && id) {
        await updateKnowledge(id, payload);
        message.success(t('knowledge.saved'));
        setSnapshot(JSON.stringify({ values: form.getFieldsValue(true), content }));
        if (status === 'published') navigate(`/knowledge/${id}`);
        return;
      }
      const res: any = await createKnowledge(payload);
      const created = res?.data ? res.data : res;
      message.success(status === 'published' ? t('knowledge.published') : t('knowledge.saved'));
      if (created?.id) navigate(`/knowledge/${created.id}`);
      else navigate('/knowledge');
    } catch (error: any) {
      if (Array.isArray(error?.errorFields)) return; // 表单校验失败，交给 antd 提示
      message.error(getApiErrorMessage(error, t('knowledge.saveFailed')));
    } finally {
      setSubmitting(null);
    }
  };

  const attachment = useMemo(() => parseAttachment(content), [content]);

  return (
    <>
      <PageHeader
        title={isEdit ? t('knowledge.editTitle') : t('knowledge.newTitle')}
        subtitle={t('knowledge.pageSubtitle')}
        fallbackTo="/knowledge"
        onBack={() => confirmLeave(() => navigate('/knowledge'))}
        extra={
          <>
            <Button
              icon={<SaveOutlined />}
              loading={submitting === 'draft'}
              onClick={() => save('draft')}
              data-testid="knowledge-save-draft"
            >
              {t('knowledge.saveDraft')}
            </Button>
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={submitting === 'published'}
              onClick={() => save('published')}
              data-testid="knowledge-publish"
            >
              {t('knowledge.publish')}
            </Button>
          </>
        }
      />

      <div data-testid="knowledge-edit-page">
        <PageFormShell
          submitting={Boolean(submitting)}
          onSubmit={() => save('published')}
          onCancel={() => confirmLeave(() => navigate('/knowledge'))}
          submitText={t('knowledge.publish')}
          hint={
            <Space size={8}>
              <CloudUploadOutlined />
              <Button
                type="link"
                size="small"
                style={{ padding: 0 }}
                onClick={() => confirmLeave(() => navigate(`/knowledge/upload${isEdit && id ? `?from=${id}` : ''}`))}
                data-testid="knowledge-upload-open"
              >
                {t('knowledge.upload.open')}
              </Button>
            </Space>
          }
          testId="knowledge-edit-form"
        >
          <Form form={form} layout="vertical" disabled={loading} onValuesChange={() => setDirtyFlag(true)}>
            <FormGroup title={t('knowledge.form.title')}>
              <Form.Item
                name="title"
                label={t('knowledge.form.title')}
                rules={[{ required: true, message: t('knowledge.form.titleRequired') }]}
              >
                <Input maxLength={500} placeholder={t('knowledge.form.titlePlaceholder')} />
              </Form.Item>
              <Form.Item
                name="category"
                label={t('knowledge.form.category')}
                rules={[{ required: true, message: t('knowledge.form.categoryRequired') }]}
              >
                <Select placeholder={t('knowledge.form.categoryPlaceholder')}>
                  {Object.entries(CATEGORY_KEYS).map(([value, labelKey]) => (
                    <Option key={value} value={value}>
                      {t(labelKey)}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
              <Form.Item name="summary" label={t('knowledge.form.summary')}>
                <Input.TextArea rows={2} maxLength={500} showCount />
              </Form.Item>
              <Form.Item name="tags" label={t('knowledge.form.tags')}>
                <Select mode="tags" placeholder={t('knowledge.form.tagsPlaceholder')} />
              </Form.Item>
            </FormGroup>

            <FormGroup title={t('knowledge.form.content')}>
              {attachment ? (
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message={t('knowledge.upload.attachmentAttached', { name: attachment.name })}
                  action={
                    <Button size="small" onClick={() => downloadRecordFile(content, 'knowledge')} data-testid="knowledge-attachment-download">
                      {t('knowledge.upload.download')}
                    </Button>
                  }
                />
              ) : null}
              <Tabs
                defaultActiveKey="edit"
                items={[
                  {
                    key: 'edit',
                    label: t('knowledge.tabWrite'),
                    children: (
                      <Input.TextArea
                        rows={18}
                        value={content}
                        onChange={(event) => {
                          setContent(event.target.value);
                          setDirtyFlag(true);
                        }}
                        placeholder={t('knowledge.editorPlaceholder')}
                        data-testid="knowledge-content-input"
                      />
                    ),
                  },
                  {
                    key: 'preview',
                    label: t('knowledge.tabPreview'),
                    children: (
                      <div
                        style={{ minHeight: 360, border: '1px solid #f0f0f0', borderRadius: 6, padding: 12, background: '#fafafa' }}
                        data-testid="knowledge-preview"
                        dangerouslySetInnerHTML={{ __html: renderRichContent(content, t('knowledge.emptyContent')) }}
                      />
                    ),
                  },
                ]}
              />
            </FormGroup>
          </Form>
        </PageFormShell>
      </div>
    </>
  );
};

export { CATEGORY_KEYS };
export default KnowledgeEditorPage;


