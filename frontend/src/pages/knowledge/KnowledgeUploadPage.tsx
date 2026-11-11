import { useState } from 'react';
import { Alert, Form, Input, Select, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { PageFormShell, FormGroup } from '@/components/layout/PageFormShell';
import { createKnowledge } from '@/api/knowledge';
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_MAX_BYTES,
  extensionOf,
  isAllowedAttachment,
  titleFromFileName,
} from '@/utils/attachments';
import { uploadAttachment } from '@/api/attachment';
import { getApiErrorMessage } from '@/utils/errorMessage';
import { useT } from '@/i18n';

/**
 * 知识库手动上传（t159 需求 15；t174 改为真实附件端点）。
 *
 * ## 现在的实现（t174）
 * 1. 先用表单字段创建**草稿记录**（标题/分类/摘要/标签）；
 * 2. 再用 POST /api/v1/knowledge/{id}/attachments 把文件作为**真实附件**上传（≤50 MB，超限 413）；
 * 3. 跳转详情页，附件在独立分区里可下载/删除。
 *
 * t159 的 base64 塞正文方案已删除（受 content TEXT(64KB) 限制，实际传不了 PDF/PPT/压缩包）。
 */const { Option } = Select;
const { Dragger } = Upload;

const KnowledgeUploadPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [form] = Form.useForm<{ title: string; category: string; summary?: string; tags?: string[] }>();
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pickFile = (picked: File) => {
    setError(null);
    if (!isAllowedAttachment(picked.name)) {
      setError(t('knowledge.upload.typeNotAllowed', { ext: extensionOf(picked.name) || '?' }));
      setFile(null);
      return;
    }
    if (picked.size > ATTACHMENT_MAX_BYTES) {
      setError(t('docAttachment.tooLarge', { name: picked.name, limit: 50 }));
      setFile(null);
      return;
    }
    setFile(picked);
    form.setFieldsValue({ title: titleFromFileName(picked.name) });
  };

  const submit = async () => {
    try {
      if (!file) {
        setError(t('knowledge.upload.needFile'));
        return;
      }
      const values = await form.validateFields();
      setSubmitting(true);
      // t174：先建草稿记录，再把文件作为**真实附件**上传（不再 base64 塞正文）
      const res: any = await createKnowledge({
        title: values.title.trim(),
        category: values.category,
        summary: values.summary?.trim(),
        tags: values.tags ?? [],
        content: t('docAttachment.draftPlaceholder', { name: file.name }),
        status: 'draft',
      });
      const created = res?.data ? res.data : res;
      if (!created?.id) {
        message.warning(t('knowledge.upload.createdNoAttach'));
        navigate('/knowledge');
        return;
      }
      try {
        await uploadAttachment('knowledge', created.id, file);
      } catch (uploadError) {
        message.warning(t('knowledge.upload.partialFailed'));
        navigate(`/knowledge/${created.id}`);
        return;
      }
      message.success(t('knowledge.upload.created'));
      navigate(`/knowledge/${created.id}`);
    } catch (submitError: any) {
      if (Array.isArray(submitError?.errorFields)) return;
      message.error(getApiErrorMessage(submitError, t('knowledge.upload.failed')));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader
        title={t('knowledge.upload.title')}
        subtitle={t('knowledge.upload.subtitle')}
        fallbackTo="/knowledge"
        onBack={() => navigate('/knowledge')}
      />
      <div data-testid="knowledge-upload-page">
        <PageFormShell
          submitting={submitting}
          onSubmit={submit}
          onCancel={() => navigate('/knowledge')}
          submitText={t('knowledge.upload.submit')}
          hint={t('knowledge.upload.hint')}
          testId="knowledge-upload-form"
        >
          <Form form={form} layout="vertical" initialValues={{ category: 'vulnerability' }}>
            <FormGroup title={t('knowledge.upload.file')}>
              <Dragger
                accept={ATTACHMENT_ACCEPT}
                multiple={false}
                maxCount={1}
                beforeUpload={(picked) => {
                  pickFile(picked as File);
                  return false;
                }}
                onRemove={() => setFile(null)}
                fileList={file ? ([{ uid: 'picked', name: file.name, size: file.size }] as any) : []}
                data-testid="knowledge-upload-dropzone"
              >
                <p className="ant-upload-drag-icon">
                  <InboxOutlined />
                </p>
                <p className="ant-upload-text" data-testid="doc-upload-file-input">
                  {t('knowledge.upload.draggerText')}
                </p>
                <p className="ant-upload-hint">{t('knowledge.upload.acceptHint', { accept: ATTACHMENT_ACCEPT })}</p>
              </Dragger>
              {error ? (
                <Alert type="error" showIcon style={{ marginTop: 12 }} message={error} data-testid="knowledge-upload-error" />
              ) : null}
            </FormGroup>

            <FormGroup title={t('knowledge.upload.recordInfo')}>
              <Form.Item
                name="title"
                label={t('knowledge.form.title')}
                rules={[{ required: true, message: t('knowledge.form.titleRequired') }]}
              >
                <Input maxLength={500} placeholder={t('knowledge.upload.titleFromFile')} data-testid="knowledge-upload-title" />
              </Form.Item>
              <Form.Item
                name="category"
                label={t('knowledge.form.category')}
                rules={[{ required: true, message: t('knowledge.form.categoryRequired') }]}
              >
                <Select placeholder={t('knowledge.form.categoryPlaceholder')} data-testid="knowledge-upload-category">
                  <Option value="vulnerability">{t('knowledge.category.vulnerability')}</Option>
                  <Option value="methodology">{t('knowledge.category.methodology')}</Option>
                  <Option value="experience">{t('knowledge.category.experience')}</Option>
                  <Option value="tool">{t('knowledge.category.tool')}</Option>
                </Select>
              </Form.Item>
              <Form.Item name="summary" label={t('knowledge.form.summary')}>
                <Input.TextArea rows={2} maxLength={500} showCount />
              </Form.Item>
              <Form.Item name="tags" label={t('knowledge.form.tags')}>
                <Select mode="tags" placeholder={t('knowledge.form.tagsPlaceholder')} />
              </Form.Item>
            </FormGroup>
          </Form>
        </PageFormShell>
      </div>
    </>
  );
};

export default KnowledgeUploadPage;





