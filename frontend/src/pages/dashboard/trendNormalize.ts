import dayjs from 'dayjs';
import type { TrendPoint } from '@/types/dashboard';

/**
 * 趋势响应归一。
 *
 * t20 契约：/dashboard/trends/{assets|vulnerabilities|workload} 返回
 * {days,startDate,endDate,data:[{date,count}]}，点数恒等于 days、无数据天补 0。
 * 这里仍兼容其他可能的字段名（label/day/value/total），以便灰度期新旧后端并存。
 */

export const toNumber = (value: unknown): number => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
};

export const pickDate = (item: any): string | null => {
  const raw = item?.date ?? item?.day ?? item?.label ?? item?.x ?? item?.timestamp ?? item?.time ?? item?.bucket;
  if (raw === null || raw === undefined || raw === '') return null;
  const parsed = dayjs(typeof raw === 'number' ? raw : String(raw));
  return parsed.isValid() ? parsed.format('YYYY-MM-DD') : String(raw);
};

export const pickValue = (item: any): number =>
  toNumber(item?.count ?? item?.value ?? item?.total ?? item?.y ?? item?.amount ?? 0);

export const sumPoint = (point: TrendPoint): number => point.assets + point.vulnerabilities + point.workload;

/** 生成固定 days 个点的空序列（日期从旧到新） */
export const buildSeries = (
  days: number,
  buckets: Record<string, Partial<Record<'assets' | 'vulnerabilities' | 'workload', number>>>
): TrendPoint[] => {
  const points: TrendPoint[] = [];
  for (let i = days - 1; i >= 0; i -= 1) {
    const day = dayjs().startOf('day').subtract(i, 'day');
    const key = day.format('YYYY-MM-DD');
    const bucket = buckets[key] ?? {};
    points.push({
      date: key,
      label: day.format('MM-DD'),
      assets: bucket.assets ?? 0,
      vulnerabilities: bucket.vulnerabilities ?? 0,
      workload: bucket.workload ?? 0,
    });
  }
  return points;
};

export type TrendPayloads = Partial<Record<'assets' | 'vulnerabilities' | 'workload', unknown[]>>;

/** 把三类趋势响应合并到同一组日期点上 */
export const mergeTrendResponses = (days: number, payloads: TrendPayloads): TrendPoint[] => {
  const buckets: Record<string, Partial<Record<'assets' | 'vulnerabilities' | 'workload', number>>> = {};
  const consume = (kind: 'assets' | 'vulnerabilities' | 'workload', list?: unknown[]) => {
    if (!Array.isArray(list)) return;
    list.forEach((raw) => {
      const date = pickDate(raw);
      if (!date) return;
      const bucket = buckets[date] ?? {};
      bucket[kind] = (bucket[kind] ?? 0) + pickValue(raw);
      buckets[date] = bucket;
    });
  };
  consume('assets', payloads.assets);
  consume('vulnerabilities', payloads.vulnerabilities);
  consume('workload', payloads.workload);
  return buildSeries(days, buckets);
};
