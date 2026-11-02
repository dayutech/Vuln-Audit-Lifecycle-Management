import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Empty, Select, Skeleton, Space, Tag, Typography } from 'antd';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { getCustomerDistribution, getProjectDistribution } from '@/api/dashboard';
import { extractCustomerPage, getCustomerOptions } from '@/api/customer';
import { getApiErrorStatus } from '@/utils/errorMessage';
import { describeApiFailure } from './apiFailure';
import { statsScopeTagColor, type StatsScopeTone } from './statsScope';
import { useT } from '@/i18n';
import type { CustomerDistributionItem, ProjectDistributionItem } from '@/types/dashboard';

const { Text } = Typography;

/** 卡片口径标注：复用 t30/t24 的 resolveStatsScope 结果（不新造一套口径文案） */
export interface DistCardScopeProps {
  /** 已按语言渲染好的口径文案（全量 / 当前可见范围 / 当前可见范围（本团队）） */
  scopeLabel: string;
  scopeTone: StatsScopeTone;
}

const TOP_N = 10;

const barHeight = (rows: number): number => Math.max(220, Math.min(rows, TOP_N) * 32 + 60);

/** 失败分级（403 / 404 / 5xx / 网络各不相同），文案走 i18n 的 api.failure.* 词条 */
const useDistFailure = () => {
  const { t } = useT();
  return useCallback(
    (error: unknown) => {
      const failure = describeApiFailure(getApiErrorStatus(error));
      return {
        level: failure.level,
        message: t(failure.labelKey, failure.vars),
        description: t(failure.hintKey),
      };
    },
    [t]
  );
};

/* ==================== 按客户分布 ==================== */

export const CustomerDistributionCard: React.FC<DistCardScopeProps> = ({ scopeLabel, scopeTone }) => {
  const { t } = useT();
  const buildFailure = useDistFailure();
  const [items, setItems] = useState<CustomerDistributionItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<{ level: 'warning' | 'error' | 'info'; message: string; description: string } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await getCustomerDistribution({ topN: TOP_N });
      setItems(list);
      setFailure(null);
    } catch (error) {
      setItems([]);
      setFailure(buildFailure(error));
    } finally {
      setLoading(false);
    }
  }, [buildFailure]);

  useEffect(() => {
    load();
  }, [load]);

  const chartData = useMemo(
    () =>
      items.map((item) => ({
        name: item.customerName || item.customerCode || item.customerId,
        assets: Number(item.assetCount) || 0,
        projects: Number(item.projectCount) || 0,
      })),
    [items]
  );

  const scopeTag = <Tag color={statsScopeTagColor(scopeTone)}>{scopeLabel}</Tag>;

  if (failure) {
    return <Alert type={failure.level} showIcon message={failure.message} description={failure.description} />;
  }
  if (loading) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }
  if (chartData.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('dashboard.customerDist.empty')} />;
  }

  return (
    <div>
      <Space size={6} wrap style={{ marginBottom: 8 }}>
        {scopeTag}
        <Text type="secondary" style={{ fontSize: 12 }}>
          {t('dashboard.customerDist.scopeHint')}
        </Text>
      </Space>
      <ResponsiveContainer width="100%" height={barHeight(chartData.length)}>
        <BarChart data={chartData} layout="vertical" margin={{ top: 8, right: 24, left: 8, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis type="number" allowDecimals={false} tickFormatter={(value) => String(value)} />
          <YAxis type="category" dataKey="name" width={140} />
          <ChartTooltip
            labelFormatter={(label) => t('dashboard.customerDist.tooltipLabel', { name: String(label) })}
          />
          <Legend />
          <Bar dataKey="assets" name={t('dashboard.customerDist.assetSeries')} fill="#1890ff" />
          <Bar dataKey="projects" name={t('dashboard.customerDist.projectSeries')} fill="#722ed1" />
        </BarChart>
      </ResponsiveContainer>
      <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
        {t('dashboard.customerDist.footnote', { topN: TOP_N })}
      </Text>
    </div>
  );
};

/* ==================== 按项目分布 ==================== */

export const ProjectDistributionCard: React.FC<DistCardScopeProps> = ({ scopeLabel, scopeTone }) => {
  const { t } = useT();
  const buildFailure = useDistFailure();
  const [items, setItems] = useState<ProjectDistributionItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<{ level: 'warning' | 'error' | 'info'; message: string; description: string } | null>(null);
  const [customerId, setCustomerId] = useState<string | undefined>(undefined);
  const [customerOptions, setCustomerOptions] = useState<{ label: string; value: string }[]>([]);

  // 客户过滤下拉：失败静默（不抢卡片主体提示），只留「全部客户」
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const page = extractCustomerPage(await getCustomerOptions(undefined, 200));
        if (cancelled) return;
        setCustomerOptions(
          page.items.map((item) => ({
            label: item.name || item.code || item.id,
            value: item.id,
          }))
        );
      } catch {
        if (!cancelled) setCustomerOptions([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await getProjectDistribution({ customerId, topN: TOP_N });
      setItems(list);
      setFailure(null);
    } catch (error) {
      setItems([]);
      setFailure(buildFailure(error));
    } finally {
      setLoading(false);
    }
  }, [customerId, buildFailure]);

  useEffect(() => {
    load();
  }, [load]);

  const chartData = useMemo(
    () =>
      items.map((item) => ({
        name: item.projectName || item.projectCode || item.projectId,
        assets: Number(item.assetCount) || 0,
      })),
    [items]
  );

  const filter = (
    <Select
      size="small"
      style={{ minWidth: 160 }}
      allowClear
      value={customerId}
      onChange={(value) => setCustomerId(value || undefined)}
      placeholder={t('dashboard.projectDist.filterAll')}
      options={customerOptions}
      notFoundContent={t('dashboard.projectDist.filterEmpty')}
      aria-label={t('dashboard.projectDist.filterLabel')}
    />
  );

  if (failure) {
    return (
      <div>
        <Space size={8} wrap style={{ marginBottom: 8 }}>
          <Tag color={statsScopeTagColor(scopeTone)}>{scopeLabel}</Tag>
          {filter}
        </Space>
        <Alert type={failure.level} showIcon message={failure.message} description={failure.description} />
      </div>
    );
  }

  return (
    <div>
      <Space size={8} wrap style={{ marginBottom: 8 }}>
        <Tag color={statsScopeTagColor(scopeTone)}>{scopeLabel}</Tag>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {t('dashboard.projectDist.filterLabel')}
        </Text>
        {filter}
      </Space>
      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : chartData.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('dashboard.projectDist.empty')} />
      ) : (
        <ResponsiveContainer width="100%" height={barHeight(chartData.length)}>
          <BarChart data={chartData} layout="vertical" margin={{ top: 8, right: 24, left: 8, bottom: 8 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis type="number" allowDecimals={false} tickFormatter={(value) => String(value)} />
            <YAxis type="category" dataKey="name" width={160} />
            <ChartTooltip
              labelFormatter={(label) => t('dashboard.projectDist.tooltipLabel', { name: String(label) })}
            />
            <Legend />
            <Bar dataKey="assets" name={t('dashboard.projectDist.assetSeries')} fill="#13c2c2" />
          </BarChart>
        </ResponsiveContainer>
      )}
      <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
        {t('dashboard.projectDist.footnote', { topN: TOP_N })}
      </Text>
    </div>
  );
};
