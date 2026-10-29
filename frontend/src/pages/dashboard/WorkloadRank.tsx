import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Empty, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { getWorkloadRanking, listTasksForDashboard } from '@/api/dashboard';
import { getApiErrorStatus } from '@/utils/errorMessage';
import { describeApiFailure } from './apiFailure';
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

/** 排行接口失败 → 分级提示文案（403 / 404 / 5xx 各不相同，避免都写成“暂无数据”） */
const buildRankNotice = (status: number | undefined, fallbackUsed: boolean): RankNotice => {
  const failure = describeApiFailure(status);
  const suffix = fallbackUsed ? '已切换为本地聚合口径。' : '本地聚合同样没有任务数据。';
  const scopeHint =
    failure.kind === 'forbidden'
      ? '后端对跨用户排行做了数据边界：管理员 / 项目负责人 / 小组长看全量，其他角色只返回自己一行。'
      : '';
  return {
    status,
    title: `排行接口${failure.label}`,
    detail: `${scopeHint}${failure.hint}${suffix}`,
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
        title: '排行接口返回空数据',
        detail:
          local.length > 0
            ? '服务端排行没有数据，已切换为本地聚合口径（按 /tasks 最近任务）。'
            : '服务端排行与本地聚合都没有数据，分配任务后再来看。',
        level: 'info',
      });
    } catch (error) {
      const status = getApiErrorStatus(error);
      const local = buildLocalRows(await listTasksForDashboard(TASK_SCAN_SIZE).catch(() => []));
      setRows(local);
      setSource(local.length > 0 ? 'local' : null);
      setNotice(buildRankNotice(status, local.length > 0));
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
        title: '排名',
        key: 'rank',
        width: 70,
        render: (_: unknown, __: RankRow, index: number) => {
          const colors = ['#f5222d', '#fa8c16', '#faad14'];
          return index < 3 ? <Tag color={colors[index]}>{index + 1}</Tag> : <Text type="secondary">{index + 1}</Text>;
        },
      },
      {
        title: '负责人',
        dataIndex: 'assignee',
        key: 'assignee',
        render: (value: string) => <Text strong>{value || '未分配'}</Text>,
      },
      {
        title: '任务数',
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
          title: '预估工时',
          dataIndex: 'estimatedHours',
          key: 'estimatedHours',
          width: 110,
          render: (value: number) => <span>{value}</span>,
        },
        {
          title: '实际工时',
          dataIndex: 'actualHours',
          key: 'actualHours',
          width: 110,
          render: (value: number, record: RankRow) => (
            <Tooltip title={`实际 ${value} / 预估 ${record.estimatedHours}`}>
              <span>{value}</span>
            </Tooltip>
          ),
        },
      ];
    }

    // 本地兜底口径才有状态明细（服务端排行只提供任务数与工时）
    return [
      ...base,
      { title: '进行中', dataIndex: 'inProgress', key: 'inProgress', width: 90 },
      { title: '待处理', dataIndex: 'pending', key: 'pending', width: 90 },
      { title: '已完成', dataIndex: 'completed', key: 'completed', width: 90 },
      {
        title: '实际/预估工时',
        key: 'hours',
        width: 140,
        render: (_: unknown, record: RankRow) => (
          <Tooltip title={`实际 ${record.actualHours} / 预估 ${record.estimatedHours}`}>
            <span>
              {record.actualHours} / {record.estimatedHours}
            </span>
          </Tooltip>
        ),
      },
    ];
  }, [source]);

  return (
    <div>
      <Space style={{ marginBottom: 8, justifyContent: 'flex-end', width: '100%' }}>
        {source === 'local' ? (
          <Tag color="orange">本兜底口径</Tag>
        ) : source === 'server' ? (
          <Tag color="green">服务端口径</Tag>
        ) : null}
        <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={load}>
          刷新
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
              重试
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
                  ? '排行数据为空（原因见上方提示）'
                  : `近 ${RANK_DAYS} 天内没有带负责人的任务，分配任务后这里会出现排行榜`
              }
            />
          ),
        }}
      />
      <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
        {source === 'local'
          ? `当前为本兜底口径：排行接口不可用或为空，改按 /tasks 最近 ${TASK_SCAN_SIZE} 条任务本地聚合（可见范围随任务数据权限）。`
          : `数据来源：GET /dashboard/workload/ranking（近 ${RANK_DAYS} 天，按任务数倒序，取前 ${RANK_LIMIT}）。可见范围：管理员 / 项目负责人 / 小组长=全量排行，其他角色=仅自己一行。`}
      </Text>
    </div>
  );
};

export default WorkloadRank;
