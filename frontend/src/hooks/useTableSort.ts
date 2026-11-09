import { useCallback, useMemo, useState } from 'react';
import { compareFileRows, type FileRowLike, type FileSortKey, type SortOrder } from '@/utils/fileFilters';

/**
 * 表格排序 hook（t146）。
 *
 * 选型：**单列 + 升/降/取消三态循环**（点一次升序、再点降序、第三次恢复后端默认顺序）。
 * - 不做多列优先级：文件/交付物列表的排序诉求是"看最新 / 看最大"，antd 的多列排序要 Shift 点击，
 *   对这类轻量场景是多余操作成本；单列状态也更一目了然。
 * - 排序交给 antd 的列 `sorter`（数据已在浏览器里，量级是"单个资产的文件数"），
 *   这里只**托管状态**：`sortOrder` 受控 → 列头箭头（升/降）天然可见，
 *   点击后由 `onTableChange` 回写，避免"自己排一遍 antd 又排一遍"。
 */
export interface TableSorterPayload {
  columnKey?: string;
  order?: 'ascend' | 'descend' | null;
}

export interface UseTableSortResult {
  sortKey: FileSortKey | null;
  sortOrder: SortOrder;
  /** 传给 `<Table onChange>`：把 antd 的排序状态回写到 hook */
  onTableChange: (sorterPayload: TableSorterPayload | undefined) => void;
  /** 生成某一列的 sorter 配置（含受控 sortOrder）；比较器用 any 以适配 antd CompareFn<T> */
  sorterFor: (key: FileSortKey, versionLabel?: (id: string) => string) => {
    sorter: (a: any, b: any) => number;
    sortOrder: SortOrder;
  };
  reset: () => void;
}

const SORT_KEYS: FileSortKey[] = ['name', 'version', 'category', 'fileSize', 'createdAt', 'uploader'];

export const useTableSort = (): UseTableSortResult => {
  const [sortKey, setSortKey] = useState<FileSortKey | null>(null);
  const [sortOrder, setSortOrder] = useState<SortOrder>(null);

  const onTableChange = useCallback((sorterPayload: TableSorterPayload | undefined) => {
    const key = sorterPayload?.columnKey as FileSortKey | undefined;
    const order = sorterPayload?.order ?? null;
    if (!key || !SORT_KEYS.includes(key) || !order) {
      // 取消排序（第三次点击）或点了不可排序列 → 恢复后端默认顺序
      setSortKey(null);
      setSortOrder(null);
      return;
    }
    setSortKey(key);
    setSortOrder(order);
  }, []);

  const sorterFor = useCallback(
    <T extends FileRowLike>(key: FileSortKey, versionLabel?: (id: string) => string) => ({
      sorter: (a: T, b: T) => compareFileRows(key, a, b, versionLabel),
      sortOrder: sortKey === key ? sortOrder : null,
    }),
    [sortKey, sortOrder],
  );

  const reset = useCallback(() => {
    setSortKey(null);
    setSortOrder(null);
  }, []);

  return useMemo(
    () => ({ sortKey, sortOrder, onTableChange, sorterFor, reset }),
    [sortKey, sortOrder, onTableChange, sorterFor, reset],
  );
};

export default useTableSort;




