import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { TableColumnsType } from 'antd';
import { CheckOutlined, CloseOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useT } from '@/i18n';
import PageHeader from '@/components/layout/PageHeader';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { formatDateTime } from '@/utils/datetime';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import {
  approveUpload,
  extractUploadApprovalPage,
  listUploadApprovals,
  rejectUpload,
  type UploadApprovalItem,
} from '@/api/upload';

/**
 * 管理员：大文件上传审批页（t161，`/admin/upload-approvals`）。
 *
 * - 仅 ADMIN 可用（后端 `@PreAuthorize("hasRole('ADMIN')")`；前端再做一次入口/页面级拦截）；
 * - 列表字段：文件名 / 大小 / 类型 / 申请人 / 提交时间 / 剩余有效期（purgeAfter）；
 * - 通过 → 临时文件移入正式存储并**此时才创建业务记录**；拒绝 → 必须填原因（二次确认），临时文件被删；
 * - 操作后行内更新（重新拉当前页），空态「暂无待审批的上传」。
 */
const PAGE_SIZE = 20;

const STATUS_OPTIONS = ['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'] as const;

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  EXPIRED: 'default',
};

const UploadApprovals: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { profile, loaded: profileLoaded } = useAccessProfile();
  const isAdmin = profileLoaded && (profile?.roles ?? []).some((role) => String(role).toLowerCase() === 'admin');

  const [status, setStatus] = useState<string>('PENDING');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<UploadApprovalItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);

  const [rejectTarget, setRejectTarget] = useState<UploadApprovalItem | null>(null);
  const [rejectNote, setRejectNote] = useState('');
  const [rejectSubmitting, setRejectSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await listUploadApprovals({ status, page: page - 1, size: PAGE_SIZE });
      const parsed = extractUploadApprovalPage(res);
      setItems(parsed.items);
      setTotal(parsed.total);
    } catch (error) {
      setItems([]);
      setTotal(0);
      const code = getApiErrorStatus(error);
      setLoadError(
        code === 403
          ? t('upload.forbidden')
          : code === 404
            ? t('upload.approvalsUnavailable')
            : getApiErrorMessage(error, t('upload.loadFailed')),
      );
    } finally {
      setLoading(false);
    }
  }, [status, page, t]);

  useEffect(() => {
    if (!profileLoaded || !isAdmin) return undefined;
    const timer = window.setTimeout(() => {
      void load();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [profileLoaded, isAdmin, load]);

  const handleApprove = (record: UploadApprovalItem) => {
    Modal.confirm({
      title: t('upload.approveTitle'),
      content: t('upload.approveContent', { name: record.fileName ?? '-' }),
      okText: t('upload.approve'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        setActingId(record.id);
        try {
          await approveUpload(record.id);
          message.success(t('upload.approveSuccess'));
          await load();
        } catch (error) {
          const code = getApiErrorStatus(error);
          message.error(
            code === 409
              ? t('upload.alreadyHandled')
              : code === 403
                ? t('upload.forbidden')
                : getApiErrorMessage(error, t('upload.approveFailed')),
          );
        } finally {
          setActingId(null);
        }
      },
    });
  };

  const submitReject = async () => {
    if (!rejectTarget) return;
    const note = rejectNote.trim();
    if (!note) {
      message.warning(t('upload.rejectNoteRequired'));
      return;
    }
    setRejectSubmitting(true);
    try {
      await rejectUpload(rejectTarget.id, note);
      message.success(t('upload.rejectSuccess'));
      setRejectTarget(null);
      setRejectNote('');
      await load();
    } catch (error) {
      const code = getApiErrorStatus(error);
      message.error(
        code === 409
          ? t('upload.alreadyHandled')
          : code === 403
            ? t('upload.forbidden')
            : getApiErrorMessage(error, t('upload.rejectFailed')),
      );
    } finally {
      setRejectSubmitting(false);
    }
  };

  const formatSize = (size?: number | null) => {
    if (!size || size <= 0) return '-';
    if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
    if (size >= 1024) return `${(size / 1024).toFixed(0)} KB`;
    return `${size} B`;
  };

  const columns: TableColumnsType<UploadApprovalItem> = [
    { title: t('upload.colFileName'), dataIndex: 'fileName', key: 'fileName', render: (v?: string) => v || '-' },
    {
      title: t('upload.colFileSize'),
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 110,
      render: (value?: number) => formatSize(value),
    },
    {
      title: t('upload.colTargetType'),
      dataIndex: 'targetType',
      key: 'targetType',
      width: 120,
      render: (v?: string) => v || '-',
    },
    {
      title: t('upload.colRequester'),
      key: 'requester',
      width: 140,
      render: (_: unknown, record) => record.requester?.username ?? '-',
    },
    {
      title: t('upload.colRequestedAt'),
      dataIndex: 'requestedAt',
      key: 'requestedAt',
      width: 170,
      render: (v?: string) => formatDateTime(v, '-'),
    },
    {
      title: t('upload.colPurgeAfter'),
      dataIndex: 'purgeAfter',
      key: 'purgeAfter',
      width: 170,
      render: (v?: string) => formatDateTime(v, '-'),
    },
    {
      title: t('upload.colStatus'),
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value?: string) => {
        const normalized = String(value ?? '').toUpperCase();
        return <Tag color={STATUS_COLORS[normalized] ?? 'default'}>{normalized ? t(`upload.status${normalized}` as never) : '-'}</Tag>;
      },
    },
    {
      title: t('project.colAction'),
      key: 'action',
      width: 190,
      render: (_: unknown, record) => {
        const pending = String(record.status ?? '').toUpperCase() === 'PENDING';
        if (!pending) {
          return <Typography.Text type="secondary">{record.reviewNote || '-'}</Typography.Text>;
        }
        return (
          <Space size="small">
            <Button
              size="small"
              type="primary"
              icon={<CheckOutlined />}
              loading={actingId === record.id}
              onClick={() => handleApprove(record)}
              data-testid={`upload-approve-${record.id}`}
            >
              {t('upload.approve')}
            </Button>
            <Button
              size="small"
              danger
              icon={<CloseOutlined />}
              disabled={actingId === record.id}
              onClick={() => {
                setRejectTarget(record);
                setRejectNote('');
              }}
              data-testid={`upload-reject-${record.id}`}
            >
              {t('upload.reject')}
            </Button>
          </Space>
        );
      },
    },
  ];

  if (profileLoaded && !isAdmin) {
    return (
      <div className="page" data-testid="admin-upload-approvals-page">
        <PageHeader
          title={t('upload.approvalsTitle')}
          fallbackTo="/dashboard"
          testId="upload-approvals-header"
        />
        <Alert
          type="error"
          showIcon
          message={t('upload.forbidden')}
          description={t('upload.forbiddenHint')}
          action={
            <Button size="small" onClick={() => navigate('/tools')}>
              {t('upload.backToTools')}
            </Button>
          }
          data-testid="upload-approvals-forbidden"
        />
      </div>
    );
  }

  return (
    <div className="page" data-testid="admin-upload-approvals-page">
      <PageHeader
        title={t('upload.approvalsTitle')}
        subtitle={t('upload.approvalsSubtitle')}
        fallbackTo="/dashboard"
        testId="upload-approvals-header"
        extra={
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()} data-testid="upload-approvals-refresh">
            {t('common.refresh')}
          </Button>
        }
      />
      <Card
        className="page-group"
        size="small"
        title={t('upload.approvalsListTitle')}
        extra={
          <Space>
            <Select
              style={{ width: 160 }}
              value={status}
              onChange={(value) => {
                setStatus(value);
                setPage(1);
              }}
              options={STATUS_OPTIONS.map((value) => ({ value, label: t(`upload.status${value}` as never) }))}
              data-testid="upload-approvals-status-filter"
            />
            <Typography.Text type="secondary">{t('upload.total', { count: total })}</Typography.Text>
          </Space>
        }
      >
        {loadError ? <Alert type="error" showIcon style={{ marginBottom: 12 }} message={loadError} /> : null}
        <Table<UploadApprovalItem>
          size="small"
          rowKey="id"
          loading={loading}
          dataSource={items}
          columns={columns}
          scroll={{ x: 'max-content' }}
          data-testid="admin-upload-approvals-table"
          locale={{ emptyText: t('upload.approvalsEmpty') }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            onChange: (nextPage) => setPage(nextPage),
            showTotal: (count) => t('upload.total', { count }),
          }}
        />
      </Card>

      <Modal
        open={Boolean(rejectTarget)}
        title={t('upload.rejectTitle')}
        onCancel={() => {
          if (rejectSubmitting) return;
          setRejectTarget(null);
          setRejectNote('');
        }}
        onOk={() => void submitReject()}
        okText={t('upload.reject')}
        okButtonProps={{ danger: true }}
        cancelText={t('common.cancel')}
        confirmLoading={rejectSubmitting}
        destroyOnHidden
        data-testid="upload-reject-modal"
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={t('upload.rejectHint', { name: rejectTarget?.fileName ?? '-' })}
        />
        <Form layout="vertical">
          <Form.Item label={t('upload.rejectNote')} required>
            <Input.TextArea
              rows={3}
              maxLength={500}
              showCount
              value={rejectNote}
              onChange={(event) => setRejectNote(event.target.value)}
              placeholder={t('upload.rejectNotePlaceholder')}
              data-testid="upload-reject-note"
            />
          </Form.Item>
        </Form>
      </Modal>

      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        <UploadOutlined /> {t('upload.approvalsFooterHint')}
      </Typography.Text>
    </div>
  );
};

export default UploadApprovals;

