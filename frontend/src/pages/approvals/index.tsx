import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Badge, Button, Card, Descriptions, Empty, Input, Modal, Segmented, Space, Table, Tabs, Tag, Tooltip, message } from 'antd';
import { Link } from 'react-router-dom';
import {
  getAccessApprovalHistory,
  getAccessApprovalHistoryCount,
  getMyAccessApprovalRequests,
  reviewAssetAccessRequest,
} from '@/api/asset';
import type { ApprovalHistoryAs, ApprovalHistoryItemVO } from '@/api/asset';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import type { AssetAccessVO } from '@/types/asset';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useT } from '@/i18n';
import type { MessageKey } from '@/i18n/messages/zh-CN';

/**
 * 审批中心（t153 引入三分类；t157 起「已通过 / 已拒绝」改用**后端审批历史端点**）。
 *
 * 数据来源：
 * - 待审批：`GET /assets/my/access-approval-requests`（既有接口），角标取 `totalElements`；
 * - 已通过 / 已拒绝：`GET /assets/my/access-approval-history?as=&status=&page=&size=`（t156），
 *   角标取 `GET .../access-approval-history/count`；
 * - **不再有任何 localStorage 兜底**（t153 的 `owl_approval_decisions_v1` 已删除，并清理旧键一次），
 *   所以换浏览器/清缓存后历史仍在。
 *
 * 视角：`as=reviewer`（我审批过的，默认） / `as=requester`（我申请且已被处理的）。
 */
type ApprovalTab = 'pending' | 'approved' | 'rejected';

/** t153 遗留的本地留存键，t157 起不再使用（只清理一次） */
const LEGACY_DECISION_STORE_KEY = 'owl_approval_decisions_v1';

const shortId = (value?: string | null) => (value ? `${value.slice(0, 8)}…` : '-');
const formatTime = (value?: string | null) => (value ? new Date(value).toLocaleString() : '-');

const statusMeta = (status?: string): { color: string; key: MessageKey } => {
  switch (status) {
    case 'approved':
      return { color: 'green', key: 'approvals.status.approved' };
    case 'rejected':
      return { color: 'red', key: 'approvals.status.rejected' };
    default:
      return { color: 'gold', key: 'approvals.status.pending' };
  }
};

/** 历史行（后端 ApprovalHistoryItem）→ 表格统一行模型 */
interface HistoryRow {
  id: string;
  assetId: string;
  assetLabel: string;
  assetDeleted: boolean;
  applicantName: string;
  status: string;
  requestedAt?: string | null;
  reviewedAt?: string | null;
  reviewerName: string;
  reviewNote?: string | null;
  scopeText: string;
  kind: 'history';
}

/** 待审批行（AssetAccessVO）→ 表格统一行模型 */
interface PendingRow {
  id: string;
  assetId: string;
  assetLabel: string;
  assetDeleted: boolean;
  applicantName: string;
  status: string;
  requestedAt?: string | null;
  reviewedAt?: string | null;
  reviewerName: string;
  reviewNote?: string | null;
  scopeText: string;
  kind: 'pending';
}

type Row = PendingRow | HistoryRow;

const Approvals: React.FC = () => {
  const { t } = useT();

  const [activeTab, setActiveTab] = useState<ApprovalTab>('pending');
  const [as, setAs] = useState<ApprovalHistoryAs>('reviewer');

  const [pending, setPending] = useState<AssetAccessVO[]>([]);
  const [pendingTotal, setPendingTotal] = useState(0);
  const [pendingPage, setPendingPage] = useState(1);
  const [pendingPageSize, setPendingPageSize] = useState(10);
  const [pendingLoading, setPendingLoading] = useState(false);

  const [history, setHistory] = useState<ApprovalHistoryItemVO[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPage, setHistoryPage] = useState(1);
  const [historyPageSize, setHistoryPageSize] = useState(10);
  const [historyLoading, setHistoryLoading] = useState(false);

  const [counts, setCounts] = useState<{ approved: number; rejected: number }>({ approved: 0, rejected: 0 });
  const [reviewingId, setReviewingId] = useState<string | null>(null);
  const [detail, setDetail] = useState<HistoryRow | null>(null);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  const retriedRef = useRef<Set<string>>(new Set());

  const userMap = useMemo(() => new Map(users.map((user) => [user.id, user])), [users]);

  const userName = useCallback(
    (id?: string | null, fallback?: string | null) => {
      if (fallback) return fallback;
      const hit = id ? userMap.get(id) : undefined;
      return hit ? hit.fullName || hit.username : shortId(id);
    },
    [userMap],
  );

  /** 旧版本地留存键清理（t157）：只做一次，避免残留脏数据被误读 */
  useEffect(() => {
    try {
      window.localStorage.removeItem(LEGACY_DECISION_STORE_KEY);
    } catch {
      // 隐私模式：无需处理
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT })
      .then((list) => {
        if (!cancelled) setUsers(list);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  /**
   * 统一错误分级（t157）：401 由 axios 拦截器跳登录；403 提示无权限；5xx 允许**重试一次**；其余提示后端消息。
   * `key` 用于「同一次失败只重试一次」，避免无限循环。
   */
  const handleLoadError = useCallback(
    (error: unknown, key: string, retry: () => void) => {
      const status = getApiErrorStatus(error);
      if (status === 401) return; // 拦截器已跳登录
      if (status === 403) {
        message.error(t('approvals.forbidden'));
        return;
      }
      if (status && status >= 500 && !retriedRef.current.has(key)) {
        retriedRef.current.add(key);
        message.warning(t('approvals.retrying'));
        retry();
        return;
      }
      message.error(getApiErrorMessage(error, t('approvals.loadFailed')));
    },
    [t],
  );

  const loadPending = useCallback(async () => {
    try {
      setPendingLoading(true);
      const res: any = await getMyAccessApprovalRequests({ page: pendingPage, page_size: pendingPageSize });
      const payload = res?.data ?? res;
      const rows: AssetAccessVO[] = Array.isArray(payload?.content) ? payload.content : [];
      setPending(rows);
      setPendingTotal(Number(payload?.totalElements ?? rows.length) || 0);
    } catch (error) {
      handleLoadError(error, `pending:${pendingPage}:${pendingPageSize}`, () => {
        void loadPending();
      });
    } finally {
      setPendingLoading(false);
    }
  }, [pendingPage, pendingPageSize, handleLoadError]);

  const loadCounts = useCallback(async () => {
    try {
      const res: any = await getAccessApprovalHistoryCount(as);
      const payload = res?.data ?? res;
      setCounts({
        approved: Number(payload?.approved ?? 0) || 0,
        rejected: Number(payload?.rejected ?? 0) || 0,
      });
    } catch (error) {
      handleLoadError(error, `count:${as}`, () => {
        void loadCounts();
      });
    }
  }, [as, handleLoadError]);

  const loadHistory = useCallback(
    async (status: 'approved' | 'rejected') => {
      try {
        setHistoryLoading(true);
        const res: any = await getAccessApprovalHistory({
          as,
          status,
          page: historyPage,
          page_size: historyPageSize,
        });
        const payload = res?.data ?? res;
        const rows: ApprovalHistoryItemVO[] = Array.isArray(payload?.content) ? payload.content : [];
        setHistory(rows);
        setHistoryTotal(Number(payload?.totalElements ?? rows.length) || 0);
      } catch (error) {
        handleLoadError(error, `history:${status}:${as}:${historyPage}:${historyPageSize}`, () => {
          void loadHistory(status);
        });
      } finally {
        setHistoryLoading(false);
      }
    },
    [as, historyPage, historyPageSize, handleLoadError],
  );

  useEffect(() => {
    void loadPending();
  }, [loadPending]);

  useEffect(() => {
    void loadCounts();
  }, [loadCounts]);

  useEffect(() => {
    if (activeTab === 'pending') return;
    void loadHistory(activeTab);
  }, [activeTab, loadHistory]);

  const scopeTextOf = (scope?: ApprovalHistoryItemVO['scope'] | AssetAccessVO | null) => {
    if (!scope) return '-';
    const parts: string[] = [];
    if (scope.canViewDocuments) parts.push(t('approvals.scopeDocuments'));
    if (scope.canViewEnvironments) parts.push(t('approvals.scopeEnvironments'));
    if (scope.canViewFiles) parts.push(t('approvals.scopeFiles'));
    return parts.length ? parts.join(' / ') : t('approvals.scopeBasic');
  };

  const pendingRows: PendingRow[] = useMemo(
    () =>
      pending.map((row) => ({
        kind: 'pending',
        id: row.id,
        assetId: row.assetId,
        assetLabel: (row as any).assetName || shortId(row.assetId),
        assetDeleted: false,
        applicantName: userName(row.userId, (row as any).userName),
        status: row.status || 'pending',
        requestedAt: row.createdAt,
        reviewedAt: row.reviewedAt,
        reviewerName: userName(row.reviewedBy),
        reviewNote: row.reviewNote || row.requestNote || null,
        scopeText: scopeTextOf(row),
      })),
    [pending, userName, t],
  );

  const historyRows: HistoryRow[] = useMemo(
    () =>
      history.map((row) => ({
        kind: 'history',
        id: row.id,
        assetId: row.assetId,
        assetLabel: row.assetName || row.assetCode || shortId(row.assetId),
        assetDeleted: Boolean(row.assetDeleted),
        applicantName: row.applicant?.username || shortId(row.applicant?.id),
        status: row.status,
        requestedAt: row.requestedAt,
        reviewedAt: row.reviewedAt,
        reviewerName: row.reviewer?.username || shortId(row.reviewer?.id),
        reviewNote: row.reviewNote ?? null,
        scopeText: scopeTextOf(row.scope),
      })),
    [history, t],
  );

  const refreshAll = useCallback(() => {
    void loadPending();
    void loadCounts();
    if (activeTab !== 'pending') void loadHistory(activeTab);
  }, [activeTab, loadPending, loadCounts, loadHistory]);

  const handleReview = (record: AssetAccessVO, approved: boolean) => {
    if (record.status && record.status !== 'pending') {
      message.warning(t('approvals.alreadyProcessed'));
      return;
    }
    let reviewNote = '';
    Modal.confirm({
      title: approved ? t('approvals.approveTitle') : t('approvals.rejectTitle'),
      content: (
        <Input.TextArea
          rows={3}
          placeholder={t('approvals.reviewNotePlaceholder')}
          data-testid="approval-review-note"
          onChange={(event) => {
            reviewNote = event.target.value;
          }}
        />
      ),
      okText: approved ? t('approvals.approve') : t('approvals.reject'),
      cancelText: t('common.cancel'),
      okButtonProps: approved ? {} : { danger: true },
      onOk: async () => {
        try {
          setReviewingId(record.id);
          await reviewAssetAccessRequest(record.id, approved, reviewNote || undefined);
          message.success(approved ? t('feedback.approval.approved') : t('feedback.approval.rejected'));
          // t157：不再本地留存；重新拉待审批 + 角标（历史由后端提供）
          await loadPending();
          await loadCounts();
          if (activeTab !== 'pending') await loadHistory(activeTab);
        } catch (error: any) {
          const status = getApiErrorStatus(error);
          if (status === 409) {
            message.warning(t('approvals.alreadyProcessed'));
            await loadPending();
            return;
          }
          message.error(getApiErrorMessage(error, t('feedback.approval.failed')));
        } finally {
          setReviewingId(null);
        }
      },
    });
  };

  const targetCell = (row: Row) => (
    <Space size={4} wrap>
      {row.assetDeleted ? (
        // 资产已软删：历史仍展示，但**不产生坏链**（不可点，给出提示）
        <Tooltip title={t('approvals.assetDeletedHint')}>
          <span style={{ color: '#8c8c8c' }} data-testid={`approval-target-${row.id}`}>
            {row.assetLabel}
          </span>
        </Tooltip>
      ) : (
        <Link to={`/assets/${row.assetId}`} data-testid={`approval-target-${row.id}`}>
          {row.assetLabel}
        </Link>
      )}
      {row.assetDeleted ? (
        <Tag color="default" data-testid="approval-asset-deleted-tag">
          {t('approvals.assetDeleted')}
        </Tag>
      ) : null}
    </Space>
  );

  const columns = [
    {
      title: t('approvals.colType'),
      key: 'type',
      width: 120,
      render: () => <Tag color="blue">{t('approvals.typeAccessRequest')}</Tag>,
    },
    { title: t('approvals.colTarget'), key: 'target', render: (_: unknown, row: Row) => targetCell(row) },
    {
      title: t('approvals.colScope'),
      key: 'scope',
      width: 150,
      render: (_: unknown, row: Row) => <Tag color="geekblue">{row.scopeText}</Tag>,
    },
    { title: t('approvals.colApplicant'), key: 'applicant', render: (_: unknown, row: Row) => row.applicantName },
    {
      title: t('approvals.colSubmittedAt'),
      key: 'requestedAt',
      render: (_: unknown, row: Row) => formatTime(row.requestedAt),
    },
    {
      title: t('approvals.colStatus'),
      key: 'status',
      width: 110,
      render: (_: unknown, row: Row) => {
        const meta = statusMeta(row.status);
        return (
          <Tag color={meta.color} data-testid={`approval-status-${row.id}`}>
            {t(meta.key)}
          </Tag>
        );
      },
    },
    { title: t('approvals.colReviewer'), key: 'reviewer', render: (_: unknown, row: Row) => row.reviewerName },
    {
      title: t('approvals.colReviewedAt'),
      key: 'reviewedAt',
      render: (_: unknown, row: Row) => formatTime(row.reviewedAt),
    },
    { title: t('approvals.colRemark'), key: 'remark', render: (_: unknown, row: Row) => row.reviewNote || '-' },
    {
      title: t('approvals.colAction'),
      key: 'action',
      width: 190,
      render: (_: unknown, row: Row) => {
        if (row.kind === 'history' || (row.status && row.status !== 'pending')) {
          return (
            <Space size="middle">
              <Button
                type="link"
                size="small"
                onClick={() => setDetail(row as HistoryRow)}
                data-testid={`approval-detail-${row.id}`}
              >
                {t('approvals.viewDetail')}
              </Button>
              {/* 已审批项不允许重复审批：按钮保留但禁用 */}
              <Tooltip title={t('approvals.alreadyProcessed')}>
                <Button type="link" size="small" disabled data-testid={`approval-approve-${row.id}`}>
                  {t('approvals.approve')}
                </Button>
              </Tooltip>
            </Space>
          );
        }
        return (
          <Space size="middle">
            <Button
              type="link"
              size="small"
              loading={reviewingId === row.id}
              onClick={() => handleReview(pending.find((item) => item.id === row.id) as AssetAccessVO, true)}
              data-testid={`approval-approve-${row.id}`}
            >
              {t('approvals.approve')}
            </Button>
            <Button
              type="link"
              size="small"
              danger
              onClick={() => handleReview(pending.find((item) => item.id === row.id) as AssetAccessVO, false)}
              data-testid={`approval-reject-${row.id}`}
            >
              {t('approvals.reject')}
            </Button>
          </Space>
        );
      },
    },
  ];

  const tabLabel = (key: ApprovalTab, labelKey: MessageKey, count: number) => (
    <span data-testid={`approval-tab-${key}`}>
      {t(labelKey)}{' '}
      <Badge
        count={count}
        size="small"
        showZero={false}
        overflowCount={99}
        style={{ marginInlineStart: 4 }}
        // t157：历史角标有独立 testid，便于区分「待审批 vs 历史计数」
        data-testid={key === 'pending' ? 'approval-badge-pending' : `approval-history-badge-${key}`}
        data-count={count}
      />
    </span>
  );

  const emptyText = activeTab === 'pending' ? t('approvals.emptyPending') : t('approvals.emptyRecords');

  const isHistoryTab = activeTab !== 'pending';

  return (
    <Card
      title={t('approvals.title')}
      extra={
        <Button onClick={refreshAll} data-testid="approval-refresh">
          {t('common.refresh')}
        </Button>
      }
    >
      <Tabs
        activeKey={activeTab}
        onChange={(key) => {
          setActiveTab(key as ApprovalTab);
          setHistoryPage(1);
        }}
        items={[
          { key: 'pending', label: tabLabel('pending', 'approvals.tab.pending', pendingTotal) },
          { key: 'approved', label: tabLabel('approved', 'approvals.tab.approved', counts.approved) },
          { key: 'rejected', label: tabLabel('rejected', 'approvals.tab.rejected', counts.rejected) },
        ]}
      />

      {isHistoryTab ? (
        <div style={{ marginBottom: 12 }}>
          <Segmented
            value={as}
            onChange={(value) => {
              setAs(value as ApprovalHistoryAs);
              setHistoryPage(1);
            }}
            options={[
              { label: t('approvals.as.reviewer'), value: 'reviewer' },
              { label: t('approvals.as.requester'), value: 'requester' },
            ]}
            data-testid="approval-as-switch"
          />
        </div>
      ) : null}

      {isHistoryTab ? (
        <Table<HistoryRow>
          columns={columns as any}
          dataSource={historyRows}
          rowKey="id"
          loading={historyLoading}
          data-testid="approval-history-table"
          pagination={{
            current: historyPage,
            pageSize: historyPageSize,
            total: historyTotal,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            onChange: (nextPage, nextSize) => {
              setHistoryPage(nextPage);
              setHistoryPageSize(nextSize);
            },
          }}
          locale={{ emptyText: <Empty description={emptyText} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
        />
      ) : (
        <Table<PendingRow>
          columns={columns as any}
          dataSource={pendingRows}
          rowKey="id"
          loading={pendingLoading}
          data-testid="approval-table-pending"
          pagination={{
            current: pendingPage,
            pageSize: pendingPageSize,
            total: pendingTotal,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            onChange: (nextPage, nextSize) => {
              setPendingPage(nextPage);
              setPendingPageSize(nextSize);
            },
          }}
          locale={{ emptyText: <Empty description={emptyText} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
        />
      )}

      <Modal
        open={Boolean(detail)}
        title={t('approvals.detailTitle')}
        onCancel={() => setDetail(null)}
        footer={
          <Button onClick={() => setDetail(null)} data-testid="approval-detail-close">
            {t('common.close')}
          </Button>
        }
      >
        {detail ? (
          <Descriptions column={1} size="small" bordered data-testid="approval-detail-body">
            <Descriptions.Item label={t('approvals.colType')}>{t('approvals.typeAccessRequest')}</Descriptions.Item>
            <Descriptions.Item label={t('approvals.colTarget')}>
              {detail.assetDeleted ? (
                <Space size={4}>
                  <span>{detail.assetLabel}</span>
                  <Tag color="default" data-testid="approval-asset-deleted-tag">
                    {t('approvals.assetDeleted')}
                  </Tag>
                </Space>
              ) : (
                <Link to={`/assets/${detail.assetId}`}>{detail.assetLabel}</Link>
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('approvals.colApplicant')}>{detail.applicantName}</Descriptions.Item>
            <Descriptions.Item label={t('approvals.colSubmittedAt')}>{formatTime(detail.requestedAt)}</Descriptions.Item>
            <Descriptions.Item label={t('approvals.colStatus')}>
              <Tag color={statusMeta(detail.status).color}>{t(statusMeta(detail.status).key)}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('approvals.colReviewer')}>{detail.reviewerName}</Descriptions.Item>
            <Descriptions.Item label={t('approvals.colReviewedAt')}>{formatTime(detail.reviewedAt)}</Descriptions.Item>
            <Descriptions.Item label={t('approvals.colRemark')}>{detail.reviewNote || '-'}</Descriptions.Item>
          </Descriptions>
        ) : null}
      </Modal>
    </Card>
  );
};

export default Approvals;

