import React, { useMemo, useState } from 'react';
import { Alert, Form, Input, Modal, Progress, Select, Space, Typography, Upload } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { formatBytes } from '@/utils/fileFilters';
import { useT } from '@/i18n';

/**
 * 文件上传弹框（t146，资产文件与交付物共用）。
 *
 * 用户反馈"不要把版本/分类/描述输入框直接摆在页面上" → 这些字段全部收进弹框：
 * 点「上传文件」→ 弹框里选文件 + 填版本/分类/描述 → 提交。
 *
 * 关键行为：
 * - **字段校验**：版本、分类必填，文件必选（提交按钮禁用 + 行内提示）；
 * - **进度**：`onProgress` 由 axios 的 `onUploadProgress` 上报（浏览器给出 total 时才有百分比，
 *   否则进度条走"进行中"态并停在 0，完成后关闭）；
 * - **失败保留已填内容**：提交失败只把错误显示在弹框内（400/413/5xx 分级），
 *   不清空表单、不关闭弹框，用户只需改一处再提交；
 * - 成功后由父组件关闭弹框并刷新列表（父组件负责 `message.success` 与数据刷新）。
 */
export type FileUploadKind = 'asset-file' | 'deliverable';

export interface FileUploadValues {
  /** 版本 id */
  versionId: string;
  /** 资产文件 = category；交付物 = deliverableType */
  category: string;
  description?: string;
  /** 可选备注（后端无独立字段，随 description 一起提交时由父组件拼接） */
  remark?: string;
}

export interface FileUploadModalProps {
  open: boolean;
  kind: FileUploadKind;
  categories: Array<{ value: string; label: string }>;
  versions: Array<{ id: string; label: string }>;
  defaultVersionId?: string;
  submitting: boolean;
  /** 0–100；undefined 表示"进行中但无百分比" */
  progress?: number;
  /** 弹框内错误文案（父组件按状态码分级后传入） */
  errorText?: string | null;
  onCancel: () => void;
  onSubmit: (values: FileUploadValues, file: File) => void;
}

const FileUploadModal: React.FC<FileUploadModalProps> = ({
  open,
  kind,
  categories,
  versions,
  defaultVersionId,
  submitting,
  progress,
  errorText,
  onCancel,
  onSubmit,
}) => {
  const { t } = useT();
  const [form] = Form.useForm<FileUploadValues>();
  const [fileList, setFileList] = useState<any[]>([]);
  const [fileError, setFileError] = useState<string | null>(null);

  /**
   * 默认值走 `initialValues`（而不是"打开时 useEffect resetFields"）：
   * 父组件按 `uploadKind ? <FileUploadModal/> : null` 挂载，每次打开都是新实例，
   * 天然是干净状态；同时避免"在 effect 里同步 setState"的级联渲染告警。
   */
  const initialValues: FileUploadValues = {
    versionId: defaultVersionId ?? '',
    category: categories[0]?.value ?? '',
    description: '',
    remark: '',
  };

  const selectedFile: File | null = useMemo(() => {
    const raw = fileList[0]?.originFileObj ?? fileList[0];
    return raw instanceof Blob ? (raw as File) : null;
  }, [fileList]);

  const submit = async () => {
    if (!selectedFile) {
      setFileError(t('assetFile.upload.noFile'));
      return;
    }
    try {
      const values = await form.validateFields();
      onSubmit(values, selectedFile);
    } catch {
      /* 表单校验失败：antd 已在字段下提示 */
    }
  };

  const titleKey = kind === 'asset-file' ? 'assetFile.upload.title' : 'deliverable.upload.title';

  return (
    <Modal
      open={open}
      title={t(titleKey as 'assetFile.upload.title')}
      onCancel={submitting ? undefined : onCancel}
      onOk={submit}
      confirmLoading={submitting}
      okText={t('assetFile.upload.submit')}
      cancelText={t('common.cancel')}
      okButtonProps={{ disabled: !selectedFile, 'data-testid': 'file-upload-submit' }}
      destroyOnHidden
      maskClosable={!submitting}
    >
      <div data-testid="file-upload-modal" data-kind={kind}>
        {errorText ? (
          <Alert type="error" showIcon message={errorText} style={{ marginBottom: 12 }} data-testid="file-upload-error" />
        ) : null}

        <Form form={form} layout="vertical" initialValues={initialValues}>
          <Form.Item label={t('assetFile.upload.fileLabel')} required>
            <Upload.Dragger
              multiple={false}
              maxCount={1}
              fileList={fileList}
              beforeUpload={() => false}
              onChange={(info) => {
                setFileList(info.fileList.slice(-1));
                setFileError(null);
              }}
              onRemove={() => {
                setFileList([]);
                return true;
              }}
              data-testid="file-upload-dropzone"
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">{t('assetFile.upload.dragHint')}</p>
              <p className="ant-upload-hint">{t('assetFile.upload.dragSubHint')}</p>
            </Upload.Dragger>
            {fileError ? (
              <div style={{ color: '#ef4444', fontSize: 12, marginTop: 6 }} data-testid="file-upload-no-file">
                {fileError}
              </div>
            ) : null}
            {selectedFile ? (
              <div style={{ fontSize: 12, color: '#4b5563', marginTop: 6 }} data-testid="file-upload-selected">
                <Typography.Text code>{selectedFile.name}</Typography.Text> · {formatBytes(selectedFile.size)}
              </div>
            ) : null}
          </Form.Item>

          <Form.Item
            name="versionId"
            label={t('assetFile.upload.version')}
            rules={[{ required: true, message: t('assetFile.upload.versionRequired') }]}
          >
            <Select
              options={versions.map((item) => ({ value: item.id, label: item.label }))}
              placeholder={t('assetFile.upload.versionPlaceholder')}
              data-testid="file-upload-version"
            />
          </Form.Item>

          <Form.Item
            name="category"
            label={kind === 'asset-file' ? t('assetFile.upload.category') : t('deliverable.upload.type')}
            rules={[{ required: true, message: t('assetFile.upload.categoryRequired') }]}
          >
            <Select options={categories} data-testid="file-upload-category" />
          </Form.Item>

          <Form.Item name="description" label={t('assetFile.upload.description')}>
            <Input.TextArea rows={3} maxLength={500} showCount data-testid="file-upload-description" />
          </Form.Item>

          <Form.Item
            name="remark"
            label={t('assetFile.upload.remark')}
            extra={t('assetFile.upload.remarkHint')}
          >
            <Input maxLength={200} data-testid="file-upload-remark" />
          </Form.Item>
        </Form>

        {submitting ? (
          <Progress
            percent={progress ?? 0}
            status="active"
            size="small"
            format={() => (progress === undefined ? t('assetFile.upload.uploading') : `${progress}%`)}
            data-testid="file-upload-progress"
            data-percent={progress ?? -1}
          />
        ) : null}

        <Space direction="vertical" size={0} style={{ fontSize: 12, color: '#9ca3af' }}>
          <span>{t('assetFile.upload.acceptedHint')}</span>
        </Space>
      </div>
    </Modal>
  );
};

export default FileUploadModal;



