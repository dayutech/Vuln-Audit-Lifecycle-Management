import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Modal, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import type { TableColumnsType } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useT } from '@/i18n';
import { extractUploadApproval, getMyPendingUpload, type UploadApprovalItem } from '@/api/upload';
import { forgetMyUpload, listMyUploads, type MyUploadRecord } from '@/utils/myUploads';
import { formatDateTime } from '@/utils/datetime';

/**
 * 「我提交的上传」面板（t161）。
 *
 * 数据来源：本地登记的 requestId（`utils/myUploads`，因后端暂无「我的上传」列表端点）
 * → 逐条 `GET /uploads/pending/{id}` 回查状态；已 REJECTED / EXPIRED 会显示原因与时间。
 *
 * 展示字段：文件名 / 大小 / 类型 / 状态 / 提交时间 / 有效期 / 拒绝原因。
 */
export interface MyUploadsModalProps {
  open: boolean;
  onClose: () => void;
}

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  EXPIRED: 'default',
};

interface Row {
  record: MyUploadRecord;
  item: UploadApprovalItem | null;
  loadError?: string;
}

const MyUploadsModal: React.FC<MyUploadsModalProps> = ({ open, onClose }) => {
  const { t } = useT();
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    const records = listMyUploads();
    if (records.length === 0) {
      setRows([]);
      return;
    }
    setLoading(true);
    try {
      const settled = await Promise.all(
        records.map(async (record): Promise<Row> => {
          try {
            const item = extractUploadApproval(await getMyPendingUpload(record.requestId));
            if (!item) return { record, item: null, loadError: t('upload.statusUnavailable') };
            return { record, item };
          } catch {
            return { record, item: null, loadError: t('upload.statusUnavailable') };
          }
        }),
      );
      setRows(settled);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!open) return undefined;
    const timer = window.setTimeout(() => {
      void load();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [open, load]);

  const formatSize = (size?: number | null) => {
    if (!size || size <= 0) return '-';
    if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
    if (size >= 1024) return `${(size / 1024).toFixed(0)} KB`;
    return `${size} B`;
  };

  const columns: TableColumnsType<Row> = [
    {
      title: t('upload.colFileName'),
      key: 'fileName',
      render: (_: unknown, row) => row.item?.fileName ?? row.record.fileName ?? '-',
    },
    {
      title: t('upload.colFileSize'),
      key: 'fileSize',
      width: 110,
      render: (_: unknown, row) => formatSize(row.item?.fileSize ?? row.record.fileSize),
    },
    {
      title: t('upload.colTargetType'),
      key: 'targetType',
      width: 130,
      render: (_: unknown, row) => row.item?.targetType ?? row.record.targetType ?? '-',
    },
    {
      title: t('upload.colStatus'),
      key: 'status',
      width: 110,
      render: (_: unknown, row) => {
        const status = String(row.item?.status ?? 'PENDING').toUpperCase();
        return <Tag color={STATUS_COLORS[status] ?? 'default'}>{t(`upload.status${status}` as never)}</Tag>;
      },
    },
    {
      title: t('upload.colRequestedAt'),
      key: 'requestedAt',
      width: 170,
      render: (_: unknown, row) => formatDateTime(row.item?.requestedAt ?? row.record.requestedAt, '-'),
    },
    {
      title: t('upload.colPurgeAfter'),
      key: 'purgeAfter',
      width: 170,
      render: (_: unknown, row) => formatDateTime(row.item?.purgeAfter ?? row.record.purgeAfter, '-'),
    },
    {
      title: t('upload.colReviewNote'),
      key: 'reviewNote',
      render: (_: unknown, row) =>
        row.loadError ? (
          <Typography.Text type="secondary">{row.loadError}</Typography.Text>
        ) : row.item?.reviewNote ? (
          <Typography.Text>{row.item.reviewNote}</Typography.Text>
        ) : (
          '-'
        ),
    },
    {
      title: t('project.colAction'),
      key: 'action',
      width: 90,
      render: (_: unknown, row) => (
        <Tooltip title={t('upload.forgetHint')}>
          <a
            onClick={() => {
              forgetMyUpload(row.record.requestId);
              void load();
              message.success(t('upload.forgotten'));
            }}
            data-testid={`my-upload-forget-${row.record.requestId}`}
          >
            {t('upload.forget')}
          </a>
        </Tooltip>
      ),
    },
  ];

  return (
    <Modal
      open={open}
      title={t('upload.myUploadsTitle')}
      onCancel={onClose}
      footer={
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()} data-testid="my-uploads-refresh">
            {t('common.refresh')}
          </Button>
          <Button type="primary" onClick={onClose}>
            {t('common.close')}
          </Button>
        </Space>
      }
      width={980}
      destroyOnHidden
      data-testid="my-uploads-modal"
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message={t('upload.myUploadsHint')}
        data-testid="my-uploads-hint"
      />
      <Table<Row>
        size="small"
        rowKey={(row) => row.record.requestId}
        loading={loading}
        dataSource={rows}
        columns={columns}
        scroll={{ x: 'max-content' }}
        data-testid="my-uploads-table"
        locale={{ emptyText: t('upload.myUploadsEmpty') }}
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
      />
    </Modal>
  );
};

export default MyUploadsModal;

