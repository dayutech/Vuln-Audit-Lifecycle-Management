import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Input, Space, Table, Tag, message } from 'antd';
import { ReloadOutlined, SearchOutlined, UndoOutlined } from '@ant-design/icons';
import {
  isDeletedRow,
  listSoftDeletable,
  restoreSoftDeleted,
  type SoftDeleteKind,
  type SoftDeletedItem,
} from '@/api/softDelete';
import { getApiErrorStatus, getApiErrorMessage } from '@/utils/errorMessage';
import { useT } from '@/i18n';

/**
 * 「显示已删除」列表面板（t143，配合后端 t140 软删除）。
 *
 * 为什么单独一个面板而不是在普通表格上加一列：
 * 普通列表端点（`/customers`、`/projects`、`/assets`）**本来就不返回软删行**，
 * 含已删的数据只在 `GET /admin/{kind}?includeDeleted=true` 这个 ADMIN 端点上，
 * 且字段只有 `{id, code, name, deletedAt, deletedBy}`。硬塞进原表格会让状态/负责人等列全空。
 *
 * 因此交互是：列表页工具栏一个「显示已删除」开关，打开后表格切到这个面板 ——
 * 已删行**置灰**并带「已删除」Tag，操作列给「恢复」（`POST /admin/{kind}/{id}/restore`，204 幂等）。
 */
export interface SoftDeletedTableProps {
  kind: SoftDeleteKind;
  /** 恢复成功后通知父页面刷新普通列表 */
  onRestored?: () => void;
  /** 与列表页共享的关键字（打开开关时把当前搜索词带过来） */
  keyword?: string;
}

const SoftDeletedTable: React.FC<SoftDeletedTableProps> = ({ kind, onRestored, keyword }) => {
  const { t } = useT();
  const [items, setItems] = useState<SoftDeletedItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [search, setSearch] = useState(keyword ?? '');
  const [keywordApplied, setKeywordApplied] = useState(keyword ?? '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [restoring, setRestoring] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { items: rows, total: count } = await listSoftDeletable(kind, {
        keyword: keywordApplied || undefined,
        includeDeleted: true,
        page,
        page_size: pageSize,
      });
      // 该端点同时返回未删行：这里只保留已删行（面板语义就是"已删除"）
      const deleted = rows.filter((row) => isDeletedRow(row));
      setItems(deleted);
      setTotal(deleted.length === rows.length ? count : deleted.length);
    } catch (err) {
      setItems([]);
      setTotal(0);
      const status = getApiErrorStatus(err);
      setError(status === 403 ? t('softDelete.forbidden') : getApiErrorMessage(err, t('common.operationFailed')));
    } finally {
      setLoading(false);
    }
  }, [kind, keywordApplied, page, pageSize, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const restore = async (row: SoftDeletedItem) => {
    setRestoring(row.id);
    try {
      await restoreSoftDeleted(kind, row.id);
      message.success(t('softDelete.restoreSuccess', { name: row.name || row.code || row.id }));
      await load();
      onRestored?.();
    } catch (err) {
      const status = getApiErrorStatus(err);
      if (status === 404) message.error(t('softDelete.notFound'));
      else if (status === 403) message.error(t('softDelete.forbidden'));
      else message.error(getApiErrorMessage(err, t('common.operationFailed')));
    } finally {
      setRestoring(null);
    }
  };

  return (
    <div data-testid={`soft-deleted-panel-${kind}`}>
      <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={t('softDelete.panelHint')} />
      {error ? <Alert type="error" showIcon style={{ marginBottom: 12 }} message={error} /> : null}
      <Space wrap style={{ marginBottom: 12 }}>
        <Input
          allowClear
          prefix={<SearchOutlined />}
          style={{ width: 260 }}
          value={search}
          placeholder={t('softDelete.searchPlaceholder')}
          onChange={(event) => setSearch(event.target.value)}
          onPressEnter={() => {
            setKeywordApplied(search.trim());
            setPage(1);
          }}
          data-testid={`soft-deleted-search-${kind}`}
        />
        <Button
          icon={<ReloadOutlined />}
          loading={loading}
          onClick={() => void load()}
          data-testid={`soft-deleted-refresh-${kind}`}
        >
          {t('common.refresh')}
        </Button>
      </Space>
      {/* antd Table 不转发 data-testid：外层 div 承载，配合 .ant-table-row 给 qa 断言用 */}
      <div data-testid={`soft-deleted-table-${kind}-wrap`}>
      <Table<SoftDeletedItem>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={items}
        locale={{ emptyText: t('softDelete.empty') }}
        rowClassName={() => 'soft-deleted-row'}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (nextPage, nextSize) => {
            setPage(nextPage);
            setPageSize(nextSize);
          },
        }}
        columns={[
          { title: t('softDelete.colCode'), dataIndex: 'code', key: 'code', width: 180, render: (v?: string) => v || '-' },
          { title: t('softDelete.colName'), dataIndex: 'name', key: 'name', render: (v?: string) => v || '-' },
          {
            title: t('softDelete.colDeletedAt'),
            dataIndex: 'deletedAt',
            key: 'deletedAt',
            width: 180,
            render: (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-'),
          },
          {
            title: t('softDelete.colStatus'),
            key: 'status',
            width: 110,
            render: () => (
              // antd Tag 不转发 data-testid，用 span 承载（qa 断言用）
              <span data-testid="soft-deleted-tag">
                <Tag color="default">{t('softDelete.deletedTag')}</Tag>
              </span>
            ),
          },
          {
            title: t('softDelete.colAction'),
            key: 'action',
            width: 140,
            render: (_: unknown, row: SoftDeletedItem) => (
              <Button
                type="link"
                size="small"
                icon={<UndoOutlined />}
                loading={restoring === row.id}
                onClick={() => void restore(row)}
                data-testid={`soft-deleted-restore-${row.id}`}
              >
                {t('softDelete.restore')}
              </Button>
            ),
          },
        ]}
      />
      </div>
      <style>{`.soft-deleted-row > td { color: rgba(0,0,0,0.45); background: #fafafa; }`}</style>
    </div>
  );
};

export default SoftDeletedTable;


