/**
 * 文件/交付物列表的筛选与排序纯函数（t146）。
 *
 * 为什么放在工具层而不是组件里：
 * - 后端 `GET /assets/{id}/files`、`/deliverables` 是**无分页、无筛选、无排序参数**的
 *   `List<...>` 接口（见 AssetFileController：只有 `assetVersionId` / `allVersions`），
 *   所以筛选与排序只能在**已加载的整份数据集**上做；把它写成纯函数便于单测与复用；
 * - 组件只负责收集条件，判定逻辑全部收敛在这里（资产文件 / 交付物两个 tab 共用）。
 */

export interface FileRowLike {
  id: string;
  name?: string;
  category?: string;
  deliverableType?: string;
  fileSize?: number | null;
  createdAt?: string;
  assetVersionId?: string;
  asset_version_id?: string;
  /** t148：后端已补 uploadedBy（id）+ uploaderName（展示名）；旧别名保留兼容 */
  uploaderName?: string;
  uploader_name?: string;
  createdBy?: string;
  created_by?: string;
  uploadedBy?: string;
  uploaded_by?: string;
}

export interface FileFilters {
  category?: string;
  /** 版本 id（'' 表示全部） */
  versionId?: string;
  keyword?: string;
  /** t149：上传人 id（服务端 `uploaderId` 参数；本地降级时按行 `uploadedBy` 比较） */
  uploaderId?: string;
  /** 上传人**显示名**（仅本地降级路径使用） */
  uploader?: string;
  /** [开始, 结束] 的 ISO 日（含端点；空表示不限） */
  dateFrom?: string;
  dateTo?: string;
}

export const EMPTY_FILE_FILTERS: FileFilters = {};

/** 行 → 版本 id（兼容 camel / snake） */
export const versionIdOf = (row: FileRowLike): string => String(row.assetVersionId ?? row.asset_version_id ?? '');

/** 行 → 上传人显示名（后端暂未提供，返回空串） */
/** 行 → 上传人 id（t148 uploadedBy；本地降级筛选用） */
export const uploaderIdOf = (row: FileRowLike): string =>
  String(row.uploadedBy ?? row.uploaded_by ?? row.createdBy ?? row.created_by ?? '');

export const uploaderOf = (row: FileRowLike): string =>
  String(row.uploaderName ?? row.uploader_name ?? row.createdBy ?? row.created_by ?? row.uploadedBy ?? row.uploaded_by ?? '');

/** 行 → 分类（资产文件用 category，交付物用 deliverableType） */
export const categoryOf = (row: FileRowLike): string => String(row.category ?? row.deliverableType ?? '');

/** 去重取值（用于下拉选项：分类 / 上传人） */
export const distinctValues = (values: Array<string | undefined | null>): string[] =>
  Array.from(new Set(values.map((value) => String(value ?? '').trim()).filter(Boolean))).sort((a, b) => a.localeCompare(b));

/** 日期是否落在 [from, to] 内（按本地日；端点都是 `YYYY-MM-DD`） */
const withinDateRange = (createdAt: string | undefined, from?: string, to?: string): boolean => {
  if (!from && !to) return true;
  if (!createdAt) return false;
  const time = new Date(createdAt).getTime();
  if (Number.isNaN(time)) return false;
  if (from) {
    const start = new Date(`${from}T00:00:00`).getTime();
    if (time < start) return false;
  }
  if (to) {
    const end = new Date(`${to}T23:59:59.999`).getTime();
    if (time > end) return false;
  }
  return true;
};

/** 是否设置了任一筛选条件（用于显示"已启用筛选"与重置按钮的禁用态） */
export const hasActiveFilters = (filters: FileFilters): boolean =>
  Boolean(
    (filters.category ?? '').trim() ||
      (filters.versionId ?? '').trim() ||
      (filters.keyword ?? '').trim() ||
      (filters.uploader ?? '').trim() ||
      (filters.uploaderId ?? '').trim() ||
      filters.dateFrom ||
      filters.dateTo,
  );

/** 应用筛选（全部条件为 AND） */
export const applyFileFilters = <T extends FileRowLike>(rows: T[], filters: FileFilters): T[] => {
  const keyword = (filters.keyword ?? '').trim().toLowerCase();
  const category = (filters.category ?? '').trim();
  const versionId = (filters.versionId ?? '').trim();
  const uploader = (filters.uploader ?? '').trim();
  const uploaderId = (filters.uploaderId ?? '').trim();
  return rows.filter((row) => {
    if (category && categoryOf(row) !== category) return false;
    if (versionId && versionIdOf(row) !== versionId) return false;
    if (uploader && uploaderOf(row) !== uploader) return false;
    if (uploaderId && uploaderIdOf(row) !== uploaderId) return false;
    if (keyword && !String(row.name ?? '').toLowerCase().includes(keyword)) return false;
    if (!withinDateRange(row.createdAt, filters.dateFrom, filters.dateTo)) return false;
    return true;
  });
};

export type SortOrder = 'ascend' | 'descend' | null;

/** 可排序字段：文件名 / 版本 / 分类 / 大小 / 上传时间 / 上传人 */
export type FileSortKey = 'name' | 'version' | 'category' | 'fileSize' | 'createdAt' | 'uploader';

const compareText = (a: string, b: string): number => a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });

/**
 * 单列排序比较器（选型：单列 + 升/降切换）。
 *
 * 为什么不做多列优先级：antd Table 的多列排序需要 `sorter: {multiple: n}` + Shift 点击，
 * 对"文件列表按大小/时间排一下"这种轻量诉求来说是多余的操作成本；单列足够且状态一目了然。
 */
export const compareFileRows = <T extends FileRowLike>(
  key: FileSortKey,
  a: T,
  b: T,
  versionLabel: (id: string) => string = (id) => id,
): number => {
  switch (key) {
    case 'fileSize':
      return (a.fileSize ?? 0) - (b.fileSize ?? 0);
    case 'createdAt':
      return new Date(a.createdAt ?? 0).getTime() - new Date(b.createdAt ?? 0).getTime();
    case 'version':
      return compareText(versionLabel(versionIdOf(a)), versionLabel(versionIdOf(b)));
    case 'category':
      return compareText(categoryOf(a), categoryOf(b));
    case 'uploader':
      return compareText(uploaderOf(a), uploaderOf(b));
    case 'name':
    default:
      return compareText(String(a.name ?? ''), String(b.name ?? ''));
  }
};

/** 应用排序（order 为 null 时原样返回，保持后端给的顺序：创建时间倒序） */
export const sortFileRows = <T extends FileRowLike>(
  rows: T[],
  key: FileSortKey | null,
  order: SortOrder,
  versionLabel?: (id: string) => string,
): T[] => {
  if (!key || !order) return rows;
  const factor = order === 'ascend' ? 1 : -1;
  return [...rows].sort((a, b) => factor * compareFileRows(key, a, b, versionLabel));
};

/** 人类可读文件大小（表格列与上传弹框共用） */
export const formatBytes = (size?: number | null): string => {
  if (!size || size <= 0) return '-';
  if (size >= 1024 * 1024 * 1024) return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(2)} MB`;
  if (size >= 1024) return `${(size / 1024).toFixed(2)} KB`;
  return `${size} B`;
};



