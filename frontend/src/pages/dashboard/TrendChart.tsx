import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Empty, Segmented, Skeleton, Space, Typography } from 'antd';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import dayjs from 'dayjs';
import { getTrend, listAssetsForDashboard, listTasksForDashboard, listVulnerabilitiesForDashboard } from '@/api/dashboard';
import { getApiErrorStatus } from '@/utils/errorMessage';
import { describeApiFailure } from './apiFailure';
import { useT, type MessageKey } from '@/i18n';
import { buildSeries, mergeTrendResponses, sumPoint } from './trendNormalize';
import type { TrendPoint } from '@/types/dashboard';

const { Text } = Typography;

const DAY_OPTIONS = [
  { labelKey: 'dashboard.trend.range7' as MessageKey, value: 7 },
  { labelKey: 'dashboard.trend.range30' as MessageKey, value: 30 },
  { labelKey: 'dashboard.trend.range90' as MessageKey, value: 90 },
];

type TrendKind = 'assets' | 'vulnerabilities' | 'workload';

const TREND_KINDS: TrendKind[] = ['assets', 'vulnerabilities', 'workload'];

/** 后端 /trends/* 全为 0 时的兜底：按列表 createdAt 本地按天聚合 */
const buildFallbackSeries = async (days: number): Promise<TrendPoint[]> => {
  const start = dayjs().startOf('day').subtract(days - 1, 'day');
  const [assets, vulnerabilities, tasks] = await Promise.all([
    listAssetsForDashboard(200).catch(() => []),
    listVulnerabilitiesForDashboard(200).catch(() => []),
    listTasksForDashboard(200).catch(() => []),
  ]);

  const buckets: Record<string, Partial<Record<TrendKind, number>>> = {};
  const consume = (kind: TrendKind, list: any[]) => {
    list.forEach((item) => {
      const raw = item?.createdAt ?? item?.created_at;
      const parsed = dayjs(raw);
      if (!parsed.isValid() || parsed.isBefore(start)) return;
      const key = parsed.format('YYYY-MM-DD');
      const bucket = buckets[key] ?? {};
      bucket[kind] = (bucket[kind] ?? 0) + 1;
      buckets[key] = bucket;
    });
  };
  consume('assets', assets);
  consume('vulnerabilities', vulnerabilities);
  consume('workload', tasks);
  return buildSeries(days, buckets);
};

interface TrendChartProps {
  days: number;
  onDaysChange: (days: number) => void;
}

/**
 * 趋势折线图。
 *
 * 主路径：/dashboard/trends/{assets,vulnerabilities,workload}（t20 起返回真实归日数据
 * {days,startDate,endDate,data:[{date,count}]}，点数恒等于 days）。
 *
 * 兜底收紧规则：
 * - 服务端任一条曲线有非零数据 → 直接用服务端数据，且**不**显示任何兜底口径文案；
 * - 服务端全为 0 时才尝试本地聚合；本地有数据 → 展示本地数据并标注「本兜底口径」；
 * - 本地也为空 → 只展示空态；仅当三个接口都失败时才额外给出分级错误提示（403/404/5xx）。
 */
const TrendChart: React.FC<TrendChartProps> = ({ days, onDaysChange }) => {
  const { t } = useT();
  const [series, setSeries] = useState<TrendPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [usingFallback, setUsingFallback] = useState(false);
  const [failureNotice, setFailureNotice] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setFailureNotice(null);
      try {
        const results = await Promise.all(
          TREND_KINDS.map((kind) =>
            getTrend(kind, days)
              .then((response) => ({ kind, data: response?.data, error: null as unknown }))
              .catch((error: unknown) => ({ kind, data: undefined, error }))
          )
        );

        const failed = results.filter((item) => item.error !== null);
        const payloads: Partial<Record<TrendKind, unknown[]>> = {};
        results.forEach((item) => {
          if (Array.isArray(item.data)) payloads[item.kind] = item.data;
        });

        const merged = mergeTrendResponses(days, payloads);
        const hasRemote = merged.some((point) => sumPoint(point) > 0);

        // 服务端有真实数据 → 一律以服务端为准，不显示兜底口径
        if (hasRemote) {
          if (!cancelled) {
            setSeries(merged);
            setUsingFallback(false);
            setFailureNotice(null);
          }
          return;
        }

        const fallback = await buildFallbackSeries(days);
        const hasLocal = fallback.some((point) => sumPoint(point) > 0);
        if (!cancelled) {
          setSeries(fallback);
          setUsingFallback(hasLocal);
          // 仅当三个接口全部失败时才提示错误级别；部分失败但 200 空集合按“无活动数据”处理
          if (failed.length === TREND_KINDS.length) {
            const failure = describeApiFailure(getApiErrorStatus(failed[0].error));
            setFailureNotice(
              t('dashboard.trend.failureNotice', {
                label: t(failure.labelKey, failure.vars),
                hint: t(failure.hintKey),
                suffix: hasLocal ? t('dashboard.trend.fallbackSuffix') : '',
              })
            );
          } else {
            setFailureNotice(null);
          }
        }
      } catch {
        if (!cancelled) {
          setSeries(buildSeries(days, {}));
          setUsingFallback(false);
          setFailureNotice(t('dashboard.trend.loadFailed'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [days]);

  const totals = useMemo(
    () =>
      series.reduce(
        (acc, point) => ({
          assets: acc.assets + point.assets,
          vulnerabilities: acc.vulnerabilities + point.vulnerabilities,
          workload: acc.workload + point.workload,
        }),
        { assets: 0, vulnerabilities: 0, workload: 0 }
      ),
    [series]
  );

  const hasData = totals.assets + totals.vulnerabilities + totals.workload > 0;

  return (
    <div>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }} wrap>
        <Segmented options={DAY_OPTIONS.map((item) => ({ label: t(item.labelKey), value: item.value }))} value={days} onChange={(value) => onDaysChange(Number(value))} />
        <Space size={16} wrap>
          <Text type="secondary">{t('dashboard.trend.assetsAdded', { count: totals.assets })}</Text>
          <Text type="secondary">{t('dashboard.trend.vulnsAdded', { count: totals.vulnerabilities })}</Text>
          <Text type="secondary">{t('dashboard.trend.tasksAdded', { count: totals.workload })}</Text>
        </Space>
      </Space>

      {failureNotice ? <Alert type="warning" showIcon message={failureNotice} style={{ marginBottom: 12 }} /> : null}

      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : hasData ? (
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={series} margin={{ top: 10, right: 24, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="label" minTickGap={16} />
            <YAxis allowDecimals={false} />
            <Tooltip labelFormatter={(label) => t('dashboard.trend.dateLabel', { date: String(label) })} />
            <Legend />
            <Line type="monotone" dataKey="assets" name={t('dashboard.trend.seriesAssets')} stroke="#1890ff" strokeWidth={2} dot={false} />
            <Line type="monotone" dataKey="vulnerabilities" name={t('dashboard.trend.seriesVulnerabilities')} stroke="#f5222d" strokeWidth={2} dot={false} />
            <Line type="monotone" dataKey="workload" name={t('dashboard.trend.seriesTasks')} stroke="#52c41a" strokeWidth={2} dot={false} />
          </LineChart>
        </ResponsiveContainer>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={t('dashboard.trend.empty', { days })}
        />
      )}

      {usingFallback ? (
        <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
          {t('dashboard.trend.fallbackNote')}
        </Text>
      ) : null}
    </div>
  );
};

export default TrendChart;
