import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Empty, List, Modal, Progress, Space, Tag, Typography, Upload, message } from 'antd';
import { DeleteOutlined, DownloadOutlined, InboxOutlined, UploadOutlined } from '@ant-design/icons';
import {
  deleteAttachment,
  downloadAttachment,
  listAttachments,
  uploadAttachment,
  type AttachmentOwnerType,
  type AttachmentVO,
} from '@/api/attachment';
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_MAX_BYTES,
  formatFileSize,
  isAllowedAttachment,
} from '@/utils/attachments';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useT } from '@/i18n';

/**
 * 知识库 / 文档附件区（t174）。
 *
 * - 展示后端真实附件（文件名/大小/上传人/时间），支持**多文件逐个上传**（每个文件独立进度）、
 *   下载（文件名取列表值，避免中文落盘成乱码）、删除（二次确认）；
 * - 使用 t162 的 `/api/v1/{knowledge|documents}/{id}/attachments` 四组端点，
 *   **不再**把文件 base64 塞进正文；
 * - 413（超过 50MB）给友好提示。
 */
interface AttachmentPanelProps {
  ownerType: AttachmentOwnerType;
  ownerId: string;
  /** 只读（无写权限时隐藏上传/删除；下载仍可用） */
  canWrite?: boolean;
}

interface PendingUpload {
  uid: string;
  name: string;
  size: number;
  percent: number;
  status: 'uploading' | 'error';
}

const AttachmentPanel: React.FC<AttachmentPanelProps> = ({ ownerType, ownerId, canWrite = false }) => {
  const { t } = useT();
  const [items, setItems] = useState<AttachmentVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [pending, setPending] = useState<PendingUpload[]>([]);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const seq = useRef(0);

  const load = useCallback(async () => {
    if (!ownerId) return;
    try {
      setLoading(true);
      setItems(await listAttachments(ownerType, ownerId));
    } catch (error) {
      message.error(getApiErrorMessage(error, t('docAttachment.loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [ownerType, ownerId, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const uploadOne = useCallback(
    async (file: File) => {
      if (!isAllowedAttachment(file.name)) {
        message.error(t('docAttachment.typeNotAllowed', { ext: file.name.split('.').pop() ?? '?' }));
        return;
      }
      if (file.size > ATTACHMENT_MAX_BYTES) {
        message.error(t('docAttachment.tooLarge', { name: file.name, limit: 50 }));
        return;
      }
      seq.current += 1;
      const uid = `u-${seq.current}`;
      setPending((prev) => [...prev, { uid, name: file.name, size: file.size, percent: 0, status: 'uploading' }]);
      try {
        await uploadAttachment(ownerType, ownerId, file, (percent) => {
          setPending((prev) => prev.map((row) => (row.uid === uid ? { ...row, percent } : row)));
        });
        setPending((prev) => prev.filter((row) => row.uid !== uid));
        message.success(t('docAttachment.uploaded', { name: file.name }));
        await load();
      } catch (error) {
        const status = getApiErrorStatus(error);
        setPending((prev) => prev.filter((row) => row.uid !== uid));
        if (status === 413) {
          message.error(t('docAttachment.tooLarge413'));
          return;
        }
        message.error(getApiErrorMessage(error, t('docAttachment.uploadFailed')));
      }
    },
    [ownerType, ownerId, load, t],
  );

  const handleDownload = async (row: AttachmentVO) => {
    try {
      setDownloadingId(row.id);
      await downloadAttachment(ownerType, ownerId, row.id, row.fileName);
    } catch (error) {
      message.error(getApiErrorMessage(error, t('docAttachment.downloadFailed')));
    } finally {
      setDownloadingId(null);
    }
  };

  const handleDelete = (row: AttachmentVO) => {
    Modal.confirm({
      title: t('docAttachment.deleteTitle'),
      content: t('docAttachment.deleteConfirm', { name: row.fileName }),
      okText: t('common.delete'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          setDeletingId(row.id);
          await deleteAttachment(ownerType, ownerId, row.id);
          message.success(t('docAttachment.deleted'));
          await load();
        } catch (error) {
          message.error(getApiErrorMessage(error, t('docAttachment.deleteFailed')));
        } finally {
          setDeletingId(null);
        }
      },
    });
  };

  const totalText = useMemo(() => t('docAttachment.count', { count: items.length }), [items.length, t]);

  return (
    <div data-testid="doc-attachment-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Typography.Title level={5} style={{ margin: 0 }}>
          {t('docAttachment.title')}
        </Typography.Title>
        <Space size={8}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {totalText}
          </Typography.Text>
          {canWrite ? (
            <Upload
              accept={ATTACHMENT_ACCEPT}
              multiple
              showUploadList={false}
              beforeUpload={(file) => {
                void uploadOne(file as File);
                return false;
              }}
            >
              <Button icon={<UploadOutlined />} data-testid="doc-attachment-upload">
                {t('docAttachment.upload')}
              </Button>
            </Upload>
          ) : null}
        </Space>
      </div>

      {pending.length > 0 ? (
        <div style={{ marginBottom: 8 }}>
          {pending.map((row) => (
            <div key={row.uid} style={{ marginBottom: 4 }}>
              <Space size={8}>
                <InboxOutlined />
                <span>{row.name}</span>
                <span style={{ color: '#8c8c8c' }}>{formatFileSize(row.size)}</span>
              </Space>
              <Progress percent={row.percent} size="small" status="active" />
            </div>
          ))}
        </div>
      ) : null}

      <List
        size="small"
        bordered
        loading={loading}
        dataSource={items}
        data-testid="doc-attachment-list"
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('docAttachment.empty')} /> }}
        renderItem={(row) => (
          <List.Item
            key={row.id}
            actions={[
              <Button
                key="download"
                type="link"
                size="small"
                icon={<DownloadOutlined />}
                loading={downloadingId === row.id}
                onClick={() => void handleDownload(row)}
                data-testid={`doc-attachment-download-${row.id}`}
              >
                {t('docAttachment.download')}
              </Button>,
              ...(canWrite
                ? [
                    <Button
                      key="delete"
                      type="link"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      loading={deletingId === row.id}
                      onClick={() => handleDelete(row)}
                      data-testid={`doc-attachment-delete-${row.id}`}
                    >
                      {t('common.delete')}
                    </Button>,
                  ]
                : []),
            ]}
          >
            <List.Item.Meta
              title={<span data-testid={`doc-attachment-name-${row.id}`}>{row.fileName}</span>}
              description={
                <Space size={8} wrap>
                  <Tag>{formatFileSize(row.fileSize)}</Tag>
                  {row.uploaderName ? <span>{row.uploaderName}</span> : null}
                  {row.uploadedAt ? <span>{new Date(row.uploadedAt).toLocaleString()}</span> : null}
                </Space>
              }
            />
          </List.Item>
        )}
      />

      <Alert
        type="info"
        showIcon
        style={{ marginTop: 8 }}
        message={t('docAttachment.limitHint', { accept: ATTACHMENT_ACCEPT, limit: 50 })}
      />
    </div>
  );
};

export default AttachmentPanel;
