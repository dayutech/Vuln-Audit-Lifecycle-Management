import { useState } from 'react';
import { Alert, Form, Input, Select, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { FormGroup, PageFormShell } from '@/components/layout/PageFormShell';
import { createDocument } from '@/api/document';
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
 * 规范文档手动上传（t159，需求 15）：**整页**表单。
 *
 * 实现口径与知识库上传一致（见 `utils/attachments.ts`）：后端 `Document` 无附件字段，
 * 文本类直接作正文，其余类型以 base64 附件块随正文保存（≤40 KB），下载时前端还原。
 */
const { Option } = Select;
const { Dragger } = Upload;

const DocumentUploadPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [form] = Form.useForm<{ title: string; category: string; summary?: string }>();
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pickFile = (picked: File) => {
    setError(null);
    if (!isAllowedAttachment(picked.name)) {
      setError(t('documents.upload.typeNotAllowed', { ext: extensionOf(picked.name) || '?' }));
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
        setError(t('documents.upload.needFile'));
        return;
      }
      const values = await form.validateFields();
      setSubmitting(true);
      // t174：先建草稿记录，再把文件作为**真实附件**上传（不再 base64 塞正文）
      const res: any = await createDocument({
        title: values.title.trim(),
        category: values.category,
        summary: values.summary?.trim(),
        content: t('docAttachment.draftPlaceholder', { name: file.name }),
      });
      const created = res?.data ? res.data : res;
      if (!created?.id) {
        message.warning(t('documents.upload.createdNoAttach'));
        navigate('/documents');
        return;
      }
      try {
        await uploadAttachment('documents', created.id, file);
      } catch (uploadError) {
        message.warning(t('documents.upload.partialFailed'));
        navigate(`/documents/${created.id}`);
        return;
      }
      message.success(t('documents.upload.created'));
      navigate(`/documents/${created.id}`);
    } catch (submitError: any) {
      if (Array.isArray(submitError?.errorFields)) return;
      message.error(getApiErrorMessage(submitError, t('documents.upload.failed')));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader
        title={t('documents.upload.title')}
        subtitle={t('documents.upload.subtitle')}
        fallbackTo="/documents"
        onBack={() => navigate('/documents')}
      />
      <div data-testid="document-upload-page">
        <PageFormShell
          submitting={submitting}
          onSubmit={submit}
          onCancel={() => navigate('/documents')}
          submitText={t('documents.upload.submit')}
          hint={t('documents.upload.hint')}
          testId="document-upload-form"
        >
          <Form form={form} layout="vertical" initialValues={{ category: 'vulnerability' }}>
            <FormGroup title={t('documents.upload.file')}>
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
                data-testid="document-upload-dropzone"
              >
                <p className="ant-upload-drag-icon">
                  <InboxOutlined />
                </p>
                <p className="ant-upload-text" data-testid="doc-upload-file-input">
                  {t('documents.upload.draggerText')}
                </p>
                <p className="ant-upload-hint">{t('documents.upload.acceptHint', { accept: ATTACHMENT_ACCEPT })}</p>
              </Dragger>
              {error ? (
                <Alert type="error" showIcon style={{ marginTop: 12 }} message={error} data-testid="document-upload-error" />
              ) : null}
            </FormGroup>

            <FormGroup title={t('documents.upload.recordInfo')}>
              <Form.Item
                name="title"
                label={t('documents.form.title')}
                rules={[{ required: true, message: t('documents.form.titleRequired') }]}
              >
                <Input maxLength={500} placeholder={t('documents.upload.titleFromFile')} data-testid="documents-upload-title" />
              </Form.Item>
              <Form.Item
                name="category"
                label={t('documents.form.category')}
                rules={[{ required: true, message: t('documents.form.categoryRequired') }]}
              >
                <Select placeholder={t('documents.form.categoryPlaceholder')} data-testid="documents-upload-category">
                  <Option value="guide">{t('documents.form.categoryGuide')}</Option>
                  <Option value="report">{t('documents.form.categoryReport')}</Option>
                  <Option value="policy">{t('documents.form.categoryPolicy')}</Option>
                  <Option value="other">{t('documents.form.categoryOther')}</Option>
                </Select>
              </Form.Item>
              <Form.Item name="summary" label={t('documents.form.summary')}>
                <Input.TextArea rows={2} maxLength={200} showCount />
              </Form.Item>
            </FormGroup>
          </Form>
        </PageFormShell>
      </div>
    </>
  );
};

export default DocumentUploadPage;




