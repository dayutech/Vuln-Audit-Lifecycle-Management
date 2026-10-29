import type { DashboardCardDef, DashboardCardLayout, DashboardConfigPayload } from '@/types/dashboard';

/** 大盘可用卡片；顺序即默认布局顺序 */
export const DASHBOARD_CARDS: DashboardCardDef[] = [
  { id: 'kpi-assets', title: '资产概览', defaultSpan: 6, description: '资产总数与状态分布' },
  { id: 'kpi-vulnerabilities', title: '漏洞概览', defaultSpan: 6, description: '漏洞总数与严重程度' },
  { id: 'kpi-workload', title: '我的任务', defaultSpan: 6, description: '当前登录人的任务状态分布' },
  { id: 'kpi-claims', title: '我的资产认领', defaultSpan: 6, description: '资产认领审批状态' },
  { id: 'chart-trends', title: '趋势折线', defaultSpan: 24, description: '资产/漏洞/任务按天新增趋势' },
  { id: 'chart-asset-type', title: '资产分布', defaultSpan: 12, description: '资产类型与优先级分布' },
  { id: 'chart-vuln-severity', title: '漏洞分布', defaultSpan: 12, description: '漏洞严重程度与工作流状态' },
  { id: 'rank-workload', title: '工作量排行', defaultSpan: 24, description: '按任务负责人聚合的排行' },
];

export const cardTitle = (id: string): string => DASHBOARD_CARDS.find((card) => card.id === id)?.title ?? id;

export const defaultLayout = (): DashboardCardLayout[] =>
  DASHBOARD_CARDS.map((card) => ({ id: card.id, span: card.defaultSpan, visible: true }));

const ALLOWED_SPANS = [6, 8, 12, 16, 24];

const normalizeSpan = (value: unknown, fallback: number): number => {
  const span = Number(value);
  if (!Number.isFinite(span)) return fallback;
  const clamped = Math.min(Math.max(Math.round(span), 1), 24);
  if (ALLOWED_SPANS.includes(clamped)) return clamped;
  // 取最接近的合法栅格宽度，避免出现 5/7 这类无效 col
  return ALLOWED_SPANS.reduce((best, current) =>
    Math.abs(current - clamped) < Math.abs(best - clamped) ? current : best
  , ALLOWED_SPANS[0]);
};

const safeParse = (raw?: string | null): unknown => {
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
};

/**
 * 解析后端 dashboard_configs。
 *
 * - layout 列：数组，元素可以是 {id, span} 或直接 id 字符串；
 * - cards 列：启用卡片 id 数组，或 [{id, visible}] 形式。
 * 任一段非法/缺失都回落到默认布局（保证大盘永远可渲染）。
 */
export const decodeLayout = (layoutJson?: string | null, cardsJson?: string | null): DashboardCardLayout[] => {
  const fallback = defaultLayout();
  const rawLayout = safeParse(layoutJson);
  const rawCards = safeParse(cardsJson);

  if (!Array.isArray(rawLayout) || rawLayout.length === 0) {
    return fallback;
  }

  const known = new Map(DASHBOARD_CARDS.map((card) => [card.id, card]));
  const visibility = new Map<string, boolean>();
  if (Array.isArray(rawCards)) {
    rawCards.forEach((item) => {
      if (typeof item === 'string') {
        visibility.set(item, true);
      } else if (item && typeof item === 'object') {
        const id = String((item as any).id ?? '');
        if (!id) return;
        const visible = (item as any).visible;
        visibility.set(id, visible === undefined ? true : Boolean(visible));
      }
    });
  }

  const parsed: DashboardCardLayout[] = [];
  rawLayout.forEach((item) => {
    const id = typeof item === 'string' ? item : String((item as any)?.id ?? '');
    if (!id || !known.has(id) || parsed.some((row) => row.id === id)) return;
    const span = normalizeSpan(typeof item === 'object' ? (item as any)?.span : undefined, known.get(id)!.defaultSpan);
    const explicitVisible = typeof item === 'object' ? (item as any)?.visible : undefined;
    const visible =
      explicitVisible !== undefined
        ? Boolean(explicitVisible)
        : visibility.size > 0
          ? Boolean(visibility.get(id))
          : true;
    parsed.push({ id, span, visible });
  });

  if (parsed.length === 0) return fallback;
  // 补齐配置里没有的新卡片，保证后续迭代新增的卡片默认可见
  DASHBOARD_CARDS.forEach((card) => {
    if (!parsed.some((row) => row.id === card.id)) {
      parsed.push({ id: card.id, span: card.defaultSpan, visible: true });
    }
  });
  return parsed;
};

/** 编码成后端 POST /dashboard/config 的请求体（layout/cards 均为 JSON 字符串，列 NOT NULL） */
export const encodeLayout = (layout: DashboardCardLayout[]): DashboardConfigPayload => ({
  layout: JSON.stringify(layout.map(({ id, span }) => ({ id, span }))),
  cards: JSON.stringify(layout.filter((row) => row.visible).map((row) => row.id)),
});
