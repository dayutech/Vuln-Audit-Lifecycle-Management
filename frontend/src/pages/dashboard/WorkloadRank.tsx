import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Empty, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { getWorkloadRanking, listTasksForDashboard } from '@/api/dashboard';
import { getApiErrorStatus } from '@/utils/errorMessage';
import { useT } from '@/i18n';
import { describeApiFailure } from './apiFailure';
import type { MessageKey } from '@/i18n';
import { buildLocalRows, mapServerRows, type RankRow } from './rankMapping';

const { Text } = Typography;

/** 排行窗口与条数：与后端默认值保持一致（limit=10、days=30） */
const RANK_LIMIT = 10;
const RANK_DAYS = 30;
/** 本地兜底时扫描的任务条数 */
const TASK_SCAN_SIZE = 200;

type RankSource = 'server' | 'local';

interface RankNotice {
  status?: number;
  title: string;
  detail: string;
  level: 'warning' | 'error' | 'info';
}

/** 排行接口失败 → 分级提示文案（403 / 404 / 5xx 各不相同，避免都写成“暂无数据”）；t59 起文案走 i18n */
const buildRankNotice = (
  status: number | undefined,
  fallbackUsed: boolean,
  t: (key: MessageKey, vars?: Record<string, string | number>) => string
): RankNotice => {
  const failure = describeApiFailure(status);
  const suffix = fallbackUsed ? t('dashboard.rank.noticeFallbackSuffix') : t('dashboard.rank.noticeNothingLocal');
  const scopeHint = failure.kind === 'forbidden' ? t('dashboard.rank.noticeScopeHint') : '';
  return {
    status,
    title: t('dashboard.rank.noticeTitle', { label: t(failure.labelKey, failure.vars) }),
    detail: `${scopeHint}${t(failure.hintKey)}${suffix}`,
    level: failure.level,
  };
};

/**
 * 工作量排行。
 *
 * 主路径：GET /dashboard/workload/ranking?limit=10&days=30（t20 服务端实现，含数据边界）。
 * 仅在接口非 2xx 或返回空数组时，回退到按 /tasks 的本地聚合，并在表尾明确标注
 * 「当前为本兜底口径」，避免把兜底数据当成正式口径展示。
 */
const WorkloadRank: React.FC = () => {
  const { t } = useT();
  const [rows, setRows] = useState<RankRow[]>([]);
  const [source, setSource] = useState<RankSource | null>(null);
  const [notice, setNotice] = useState<RankNotice | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const items = await getWorkloadRanking({ limit: RANK_LIMIT, days: RANK_DAYS });
      if (items.length > 0) {
        setRows(mapServerRows(items));
        setSource('server');
        setNotice(null);
        return;
      }
      // 接口 2xx 但空数组 → 兜底（并保留“为什么走兜底”的说明）
      const local = buildLocalRows(await listTasksForDashboard(TASK_SCAN_SIZE).catch(() => []));
      setRows(local);
      setSource(local.length > 0 ? 'local' : null);
      setNotice({
        status: 200,
        title: t('dashboard.rank.noticeEmptyTitle'),
        detail:
          local.length > 0
            ? t('dashboard.rank.noticeEmptyFallback')
            : t('dashboard.rank.noticeEmptyNone'),
        level: 'info',
      });
    } catch (error) {
      const status = getApiErrorStatus(error);
      const local = buildLocalRows(await listTasksForDashboard(TASK_SCAN_SIZE).catch(() => []));
      setRows(local);
      setSource(local.length > 0 ? 'local' : null);
      setNotice(buildRankNotice(status, local.length > 0, t));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const columns = useMemo(() => {
    const base = [
      {
        title: t('dashboard.rank.colRank'),
        key: 'rank',
        width: 70,
        render: (_: unknown, __: RankRow, index: number) => {
          const colors = ['#f5222d', '#fa8c16', '#faad14'];
          return index < 3 ? <Tag color={colors[index]}>{index + 1}</Tag> : <Text type="secondary">{index + 1}</Text>;
        },
      },
      {
        title: t('dashboard.rank.colAssignee'),
        dataIndex: 'assignee',
        key: 'assignee',
        render: (value: string) => <Text strong>{value || t('dashboard.rank.unassigned')}</Text>,
      },
      {
        title: t('dashboard.rank.colTasks'),
        dataIndex: 'total',
        key: 'total',
        sorter: (a: RankRow, b: RankRow) => a.total - b.total,
        defaultSortOrder: 'descend' as const,
      },
    ];

    if (source === 'server') {
      // 服务端口径：只提供任务数与工时
      return [
        ...base,
        {
          title: t('dashboard.rank.colEstimated'),
          dataIndex: 'estimatedHours',
          key: 'estimatedHours',
          width: 110,
          render: (value: number) => <span>{value}</span>,
        },
        {
          title: t('dashboard.rank.colActual'),
          dataIndex: 'actualHours',
          key: 'actualHours',
          width: 110,
          render: (value: number, record: RankRow) => (
            <Tooltip title={t('dashboard.rank.hoursTip', { actual: value, estimated: record.estimatedHours })}>
              <span>{value}</span>
            </Tooltip>
          ),
        },
      ];
    }

    // 本地兜底口径才有状态明细（服务端排行只提供任务数与工时）
    return [
      ...base,
      { title: t('dashboard.rank.colInProgress'), dataIndex: 'inProgress', key: 'inProgress', width: 90 },
      { title: t('dashboard.rank.colPending'), dataIndex: 'pending', key: 'pending', width: 90 },
      { title: t('dashboard.rank.colCompleted'), dataIndex: 'completed', key: 'completed', width: 90 },
      {
        title: t('dashboard.rank.colHours'),
        key: 'hours',
        width: 140,
        render: (_: unknown, record: RankRow) => (
          <Tooltip title={t('dashboard.rank.hoursTip', { actual: record.actualHours, estimated: record.estimatedHours })}>
            <span>
              {record.actualHours} / {record.estimatedHours}
            </span>
          </Tooltip>
        ),
      },
    ];
  }, [source, t]);

  return (
    <div>
      <Space style={{ marginBottom: 8, justifyContent: 'flex-end', width: '100%' }}>
        {source === 'local' ? (
          <Tag color="orange">{t('dashboard.rank.localTag')}</Tag>
        ) : source === 'server' ? (
          <Tag color="green">{t('dashboard.rank.serverTag')}</Tag>
        ) : null}
        <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={load}>
          {t('common.refresh')}
        </Button>
      </Space>

      {notice ? (
        <Alert
          type={notice.level}
          showIcon
          style={{ marginBottom: 8 }}
          message={notice.title}
          description={notice.detail}
          action={
            <Button size="small" onClick={load} loading={loading}>
              {t('dashboard.rank.retry')}
            </Button>
          }
        />
      ) : null}

      <Table
        rowKey="key"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        scroll={{ y: 320 }}
        locale={{
          emptyText: (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                notice
                  ? t('dashboard.rank.emptyWithNotice')
                  : t('dashboard.rank.empty', { days: RANK_DAYS })
              }
            />
          ),
        }}
      />
      <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
        {source === 'local'
          ? t('dashboard.rank.footLocal', { scan: TASK_SCAN_SIZE })
          : t('dashboard.rank.footServer', { days: RANK_DAYS, limit: RANK_LIMIT })}
      </Text>
    </div>
  );
};

export default WorkloadRank;
