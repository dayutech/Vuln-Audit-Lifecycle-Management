import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Select, Space, Tabs, message } from 'antd';
import { CloudUploadOutlined, SaveOutlined, SendOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { FormGroup, PageFormShell } from '@/components/layout/PageFormShell';
import { createDocument, getDocumentDetail, publishDocument, updateDocument } from '@/api/document';
import { renderRichContent } from '@/pages/knowledge/richContent';
import { downloadRecordFile, parseAttachment, stripAttachment } from '@/utils/attachments';
import { getApiErrorMessage } from '@/utils/errorMessage';
import { useT } from '@/i18n';
import type { DocumentVO } from '@/types/document';

/**
 * 规范与文档整页编辑（t159，需求 14）。
 *
 * - `/documents/new`：新建；`/documents/:id/edit`：编辑（保存会生成新版本，保留 `changelog` 字段）；
 * - 原 `/documents` 列表页的编辑 `Modal` 已删除；版本历史仍在详情页用弹框查看（任务允许）；
 * - 顶部 `PageHeader`：返回 / 保存草稿 / 发布；未保存离开二次确认。
 */
const { Option } = Select;

interface FormValues {
  title: string;
  category: string;
  summary?: string;
  changelog?: string;
}

const DocumentEditorPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);

  const [form] = Form.useForm<FormValues>();
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState<'draft' | 'published' | null>(null);
  const [snapshot, setSnapshot] = useState('');
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
        const res: any = await getDocumentDetail(id);
        const data: DocumentVO = res?.data ? res.data : res;
        if (cancelled || !data) return;
        form.setFieldsValue({
          title: data.title,
          category: data.category,
          summary: data.summary ?? undefined,
          changelog: '',
        });
        const text = stripAttachment(data.content);
        setContent(text);
        setSnapshot(JSON.stringify({ values: form.getFieldsValue(true), content: text }));
      } catch (error) {
        message.error(getApiErrorMessage(error, t('documents.loadFailed')));
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
        title: t('documents.discardTitle'),
        content: t('documents.discardContent'),
        okText: t('documents.discardOk'),
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
      if (!content.trim()) {
        message.warning(t('feedback.document.bodyRequired'));
        return;
      }
      setSubmitting(status);
      const payload = {
        title: values.title.trim(),
        category: values.category,
        summary: values.summary?.trim(),
        content,
        ...(isEdit ? { changelog: values.changelog?.trim() || t('documents.form.changelogDefault') } : {}),
      };
      if (isEdit && id) {
        await updateDocument(id, payload);
        if (status === 'published') await publishDocument(id);
        message.success(status === 'published' ? t('feedback.document.published') : t('feedback.document.updatedNewVersion'));
        setSnapshot(JSON.stringify({ values: form.getFieldsValue(true), content }));
        navigate(`/documents/${id}`);
        return;
      }
      const res: any = await createDocument(payload);
      const created = res?.data ? res.data : res;
      if (status === 'published' && created?.id) await publishDocument(created.id);
      message.success(status === 'published' ? t('feedback.document.published') : t('feedback.document.created'));
      navigate(created?.id ? `/documents/${created.id}` : '/documents');
    } catch (error: any) {
      if (Array.isArray(error?.errorFields)) return;
      message.error(getApiErrorMessage(error, t('documents.saveFailed')));
    } finally {
      setSubmitting(null);
    }
  };

  const attachment = useMemo(() => parseAttachment(content), [content]);

  return (
    <>
      <PageHeader
        title={isEdit ? t('documents.editTitle') : t('documents.newTitle')}
        subtitle={t('documents.pageSubtitle')}
        fallbackTo="/documents"
        onBack={() => confirmLeave(() => navigate('/documents'))}
        extra={
          <>
            <Button
              icon={<SaveOutlined />}
              loading={submitting === 'draft'}
              onClick={() => save('draft')}
              data-testid="document-save-draft"
            >
              {t('documents.saveDraft')}
            </Button>
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={submitting === 'published'}
              onClick={() => save('published')}
              data-testid="document-publish"
            >
              {t('documents.publish')}
            </Button>
          </>
        }
      />

      <div data-testid="document-edit-page">
        <PageFormShell
          submitting={Boolean(submitting)}
          onSubmit={() => save('published')}
          onCancel={() => confirmLeave(() => navigate('/documents'))}
          submitText={t('documents.publish')}
          hint={
            <Space size={8}>
              <CloudUploadOutlined />
              <Button
                type="link"
                size="small"
                style={{ padding: 0 }}
                onClick={() => confirmLeave(() => navigate('/documents/upload'))}
                data-testid="document-upload-open"
              >
                {t('documents.upload.open')}
              </Button>
            </Space>
          }
          testId="document-edit-form"
        >
          <Form form={form} layout="vertical" disabled={loading} onValuesChange={() => setDirtyFlag(true)}>
            <FormGroup title={t('documents.form.title')}>
              <Form.Item
                name="title"
                label={t('documents.form.title')}
                rules={[{ required: true, message: t('documents.form.titleRequired') }]}
              >
                <Input maxLength={500} />
              </Form.Item>
              <Form.Item
                name="category"
                label={t('documents.form.category')}
                rules={[{ required: true, message: t('documents.form.categoryRequired') }]}
              >
                <Select disabled={isEdit}>
                  <Option value="guide">{t('documents.form.categoryGuide')}</Option>
                  <Option value="report">{t('documents.form.categoryReport')}</Option>
                  <Option value="policy">{t('documents.form.categoryPolicy')}</Option>
                  <Option value="other">{t('documents.form.categoryOther')}</Option>
                </Select>
              </Form.Item>
              <Form.Item name="summary" label={t('documents.form.summary')}>
                <Input.TextArea rows={2} maxLength={200} showCount />
              </Form.Item>
              {isEdit ? (
                <Form.Item
                  name="changelog"
                  label={t('documents.form.changelog')}
                  rules={[{ required: true, message: t('documents.form.changelogRequired') }]}
                >
                  <Input.TextArea rows={2} placeholder={t('documents.form.changelogPlaceholder')} />
                </Form.Item>
              ) : null}
            </FormGroup>

            <FormGroup title={t('documents.form.content')}>
              {attachment ? (
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message={t('documents.upload.attachmentAttached', { name: attachment.name })}
                  action={
                    <Button size="small" onClick={() => downloadRecordFile(content, 'document')} data-testid="document-attachment-download">
                      {t('documents.upload.download')}
                    </Button>
                  }
                />
              ) : null}
              <Tabs
                defaultActiveKey="edit"
                items={[
                  {
                    key: 'edit',
                    label: t('documents.tabWrite'),
                    children: (
                      <Input.TextArea
                        rows={18}
                        value={content}
                        onChange={(event) => {
                          setContent(event.target.value);
                          setDirtyFlag(true);
                        }}
                        placeholder={t('documents.editorPlaceholder')}
                        data-testid="document-content-input"
                      />
                    ),
                  },
                  {
                    key: 'preview',
                    label: t('documents.tabPreview'),
                    children: (
                      <div
                        style={{ minHeight: 360, border: '1px solid #f0f0f0', borderRadius: 6, padding: 12, background: '#fafafa' }}
                        data-testid="document-preview"
                        dangerouslySetInnerHTML={{ __html: renderRichContent(content, t('documents.emptyContent')) }}
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

export default DocumentEditorPage;


