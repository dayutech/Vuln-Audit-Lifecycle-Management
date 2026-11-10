import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { TableColumnsType } from 'antd';
import { DeleteOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useT } from '@/i18n';
import { formatDateTime } from '@/utils/datetime';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import {
  extractUploadApprovalPage,
  listMyUploadsRemote,
  withdrawMyUpload,
  MY_UPLOAD_STATUSES,
  type UploadApprovalItem,
} from '@/api/upload';

/**
 * 「我提交的上传」（t161 建立 → t167 改为**纯服务端**）。
 *
 * 数据源：`GET /uploads/my?status=&keyword=&page=&size=`（后端按 requested_by 过滤）
 * —— 本地 localStorage 登记已彻底删除，换设备/清缓存后历史依旧完整。
 *
 * 能力：状态筛选（全部/审核中/已通过/已拒绝/已撤回/已过期）、关键字、分页、
 *      撤回自己的 PENDING（二次确认；409 说明状态已变 → 提示并刷新）、
 *      APPROVED 行「打开」跳到目标详情（targetId）。
 */
export interface MyUploadsModalProps {
  open: boolean;
  onClose: () => void;
  /** 撤回/刷新后通知外层（例如重新拉取 PENDING 提示） */
  onChanged?: () => void;
}

const PAGE_SIZE = 10;

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
  EXPIRED: 'default',
};

const MyUploadsModal: React.FC<MyUploadsModalProps> = ({ open, onClose, onChanged }) => {
  const { t } = useT();
  const navigate = useNavigate();
  const [status, setStatus] = useState<string>('all');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<UploadApprovalItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [withdrawingId, setWithdrawingId] = useState<string | null>(null);
  const retriedRef = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await listMyUploadsRemote({ status, keyword, page: page - 1, size: PAGE_SIZE });
      const parsed = extractUploadApprovalPage(res);
      setItems(parsed.items);
      setTotal(parsed.total);
      retriedRef.current = false;
    } catch (error) {
      const code = getApiErrorStatus(error);
      // 错误分级：401 交给拦截器跳登录；403 提示；5xx 重试一次；其余展示后端消息
      if (code === 403) {
        setItems([]);
        setTotal(0);
        setLoadError(t('upload.forbidden'));
      } else if (!code || code >= 500) {
        if (!retriedRef.current) {
          retriedRef.current = true;
          setLoading(false);
          window.setTimeout(() => {
            void load();
          }, 600);
          return;
        }
        setLoadError(getApiErrorMessage(error, t('upload.myUploadsLoadFailed')));
      } else {
        setLoadError(getApiErrorMessage(error, t('upload.myUploadsLoadFailed')));
      }
    } finally {
      setLoading(false);
    }
  }, [status, keyword, page, t]);

  useEffect(() => {
    if (!open) return undefined;
    const timer = window.setTimeout(() => {
      void load();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [open, load]);

  /** 打开时重置到第一页（筛选条件保留上一次，便于对照） */
  useEffect(() => {
    if (!open) return;
    const timer = window.setTimeout(() => {
      setPage(1);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [open, status]);

  const handleWithdraw = (record: UploadApprovalItem) => {
    Modal.confirm({
      title: t('upload.withdrawTitle'),
      content: t('upload.withdrawContent', { name: record.displayName || record.fileName || '-' }),
      okText: t('upload.withdraw'),
      okButtonProps: { danger: true, 'data-testid': 'my-upload-withdraw-confirm' } as never,
      cancelText: t('common.cancel'),
      onOk: async () => {
        setWithdrawingId(record.id);
        try {
          await withdrawMyUpload(record.id);
          message.success(t('upload.withdrawSuccess'));
          await load();
          onChanged?.();
        } catch (error) {
          const code = getApiErrorStatus(error);
          if (code === 409) {
            // 状态已变（已被批准/拒绝/撤回）：提示后刷新，让用户看到最新状态
            message.warning(t('upload.withdrawConflict'));
            await load();
            onChanged?.();
          } else if (code === 403) {
            message.error(t('upload.forbidden'));
          } else {
            message.error(getApiErrorMessage(error, t('upload.withdrawFailed')));
          }
        } finally {
          setWithdrawingId(null);
        }
      },
    });
  };

  const openTarget = (record: UploadApprovalItem) => {
    if (!record.targetId) return;
    const type = String(record.targetType ?? 'TOOL').toUpperCase();
    const path =
      type === 'TOOL'
        ? `/tools?open=${record.targetId}`
        : type === 'KNOWLEDGE'
          ? `/knowledge/${record.targetId}`
          : type === 'DOCUMENT'
            ? `/documents/${record.targetId}`
            : `/tools?open=${record.targetId}`;
    onClose();
    navigate(path);
  };

  const formatSize = (size?: number | null) => {
    if (!size || size <= 0) return '-';
    if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
    if (size >= 1024) return `${(size / 1024).toFixed(0)} KB`;
    return `${size} B`;
  };

  const statusTag = (value?: string | null) => {
    const normalized = String(value ?? '').toUpperCase();
    if (!normalized) return '-';
    return <Tag color={STATUS_COLORS[normalized] ?? 'default'}>{t(`upload.status${normalized}` as never)}</Tag>;
  };

  const columns: TableColumnsType<UploadApprovalItem> = [
    {
      title: t('upload.colFileName'),
      key: 'fileName',
      render: (_: unknown, record) => record.fileName || '-',
    },
    {
      title: t('upload.colDisplayName'),
      key: 'displayName',
      render: (_: unknown, record) => record.displayName || '-',
    },
    {
      title: t('upload.colFileSize'),
      key: 'fileSize',
      width: 100,
      render: (_: unknown, record) => formatSize(record.fileSize),
    },
    {
      title: t('upload.colStatus'),
      key: 'status',
      width: 110,
      render: (_: unknown, record) => (
        <span data-testid={`my-upload-status-${record.id}`}>{statusTag(record.status)}</span>
      ),
    },
    {
      title: t('upload.colRequestedAt'),
      key: 'requestedAt',
      width: 165,
      render: (_: unknown, record) => formatDateTime(record.requestedAt, '-'),
    },
    {
      title: t('upload.colReviewer'),
      key: 'reviewer',
      width: 150,
      render: (_: unknown, record) => (
        <span>
          {record.reviewer?.username ?? '-'}
          {record.reviewedAt ? (
            <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
              {formatDateTime(record.reviewedAt, '')}
            </Typography.Text>
          ) : null}
        </span>
      ),
    },
    {
      title: t('upload.colReviewNote'),
      key: 'reviewNote',
      render: (_: unknown, record) =>
        record.reviewNote ? (
          <Typography.Text type={String(record.status).toUpperCase() === 'REJECTED' ? 'danger' : undefined}>
            {record.reviewNote}
          </Typography.Text>
        ) : (
          '-'
        ),
    },
    {
      title: t('upload.colPurgeAfter'),
      key: 'purgeAfter',
      width: 165,
      render: (_: unknown, record) =>
        String(record.status).toUpperCase() === 'PENDING' ? formatDateTime(record.purgeAfter, '-') : '-',
    },
    {
      title: t('project.colAction'),
      key: 'action',
      width: 170,
      render: (_: unknown, record) => {
        const normalized = String(record.status ?? '').toUpperCase();
        return (
          <Space size="small">
            {normalized === 'APPROVED' && record.targetId ? (
              <Button
                size="small"
                type="link"
                onClick={() => openTarget(record)}
                data-testid={`my-upload-open-${record.id}`}
              >
                {t('upload.open')}
              </Button>
            ) : null}
            {normalized === 'PENDING' ? (
              <Button
                size="small"
                danger
                type="link"
                icon={<DeleteOutlined />}
                loading={withdrawingId === record.id}
                onClick={() => handleWithdraw(record)}
                data-testid={`my-upload-withdraw-${record.id}`}
              >
                {t('upload.withdraw')}
              </Button>
            ) : null}
            {normalized !== 'PENDING' && normalized !== 'APPROVED' ? '-' : null}
          </Space>
        );
      },
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
      width={1180}
      destroyOnHidden
      data-testid="my-uploads-modal"
    >
      <Alert type="info" showIcon style={{ marginBottom: 12 }} message={t('upload.myUploadsHint')} />
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          style={{ width: 160 }}
          value={status}
          onChange={(value) => {
            setStatus(value);
            setPage(1);
          }}
          options={MY_UPLOAD_STATUSES.map((value) => ({
            value,
            label: value === 'all' ? t('upload.statusAll') : t(`upload.status${value}` as never),
          }))}
          data-testid="my-uploads-status-filter"
        />
        <Input
          allowClear
          style={{ width: 240 }}
          prefix={<SearchOutlined />}
          placeholder={t('upload.myUploadsKeyword')}
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          onPressEnter={() => {
            setPage(1);
            void load();
          }}
          data-testid="my-uploads-keyword"
        />
        <Button
          onClick={() => {
            setPage(1);
            void load();
          }}
          data-testid="my-uploads-search"
        >
          {t('common.search')}
        </Button>
      </Space>
      {loadError ? <Alert type="error" showIcon style={{ marginBottom: 12 }} message={loadError} /> : null}
      <Table<UploadApprovalItem>
        size="small"
        rowKey="id"
        loading={loading}
        dataSource={items}
        columns={columns}
        scroll={{ x: 'max-content' }}
        data-testid="my-uploads-table"
        locale={{ emptyText: t('upload.myUploadsEmpty') }}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          onChange: (nextPage) => setPage(nextPage),
          showTotal: (count) => t('upload.total', { count }),
        }}
      />
    </Modal>
  );
};

export default MyUploadsModal;


