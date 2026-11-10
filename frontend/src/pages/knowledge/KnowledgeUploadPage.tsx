import { useState } from 'react';
import { Alert, Form, Input, Select, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { PageFormShell, FormGroup } from '@/components/layout/PageFormShell';
import { createKnowledge } from '@/api/knowledge';
import {
  ATTACHMENT_ACCEPT,
  BINARY_ATTACHMENT_MAX_BYTES,
  TEXT_ATTACHMENT_MAX_BYTES,
  buildAttachmentBlock,
  extensionOf,
  isAllowedAttachment,
  readTextFile,
  titleFromFileName,
} from '@/utils/attachments';
import { getApiErrorMessage } from '@/utils/errorMessage';
import { useT } from '@/i18n';

/**
 * 知识库手动上传（t159，需求 15）：**整页**表单，不是弹框。
 *
 * ## 实现口径（不新增后端依赖）
 * 后端 `KnowledgeArticle` 没有附件字段/附件表，也没有「上传即建记录」的端点。这里：
 * 1. `.md`/`.txt` → 文件文本作为正文；
 * 2. 其他类型（及文本原件）→ 以 base64 附件块随正文保存（下载时前端还原成 Blob），
 *    受 `content` 为 TEXT(64 KB) 限制，**二进制附件上限 40 KB**、文本上限 60 KB，超限直接拦下并提示。
 * 详见 `utils/attachments.ts` 顶部说明；后端补附件端点后只需替换该模块的两个函数。
 */
const { Option } = Select;
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
    const isText = ['md', 'txt'].includes(extensionOf(picked.name));
    const limit = isText ? TEXT_ATTACHMENT_MAX_BYTES : BINARY_ATTACHMENT_MAX_BYTES;
    if (picked.size > limit) {
      setError(t('knowledge.upload.tooLarge', { name: picked.name, limit: Math.round(limit / 1024) }));
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
      const isText = ['md', 'txt'].includes(extensionOf(file.name));
      const text = isText ? await readTextFile(file) : '';
      const block = await buildAttachmentBlock(file);
      const content = `${isText ? text : `# ${values.title}\n\n`}${block}`;
      const res: any = await createKnowledge({
        title: values.title.trim(),
        category: values.category,
        summary: values.summary?.trim(),
        tags: values.tags ?? [],
        content,
        status: 'draft',
      });
      const created = res?.data ? res.data : res;
      message.success(t('knowledge.upload.created'));
      navigate(created?.id ? `/knowledge/${created.id}` : '/knowledge');
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
          <Form form={form} layout="vertical">
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
                <Input maxLength={500} placeholder={t('knowledge.upload.titleFromFile')} />
              </Form.Item>
              <Form.Item
                name="category"
                label={t('knowledge.form.category')}
                rules={[{ required: true, message: t('knowledge.form.categoryRequired') }]}
              >
                <Select placeholder={t('knowledge.form.categoryPlaceholder')}>
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

