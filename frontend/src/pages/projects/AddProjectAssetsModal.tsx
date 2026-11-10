import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Collapse, Divider, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { TableColumnsType } from 'antd';
import { DownloadOutlined, InboxOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons';
import { useT } from '@/i18n';
import { validateCsvFile } from '@/api/import';
import {
  bulkAddProjectAssets,
  extractAvailableAssetPage,
  extractBulkAddResult,
  getProjectAvailableAssets,
  PROJECT_ASSET_LINK_TYPES,
  type AvailableAssetItem,
  type BulkAddAssetsResult,
  type ProjectAssetLinkType,
} from '@/api/project';
import { linkTypeKey } from './projectAssetMeta';
import type { PhaseVO } from '@/types/project';

/**
 * 「添加资产」弹窗（t155，接入 t154 批量接口）。
 *
 * 数据源只有 `GET /projects/{id}/available-assets`（未挂靠本项目 ∩ 调用者可见，含 linkCount）。
 * 提交一律走 `POST /projects/{id}/assets` —— **不逐条调用** `POST /assets/{id}/project-links`
 * （README §68.3 硬规则：逐条失败会「删一半」，且拿不到 skipped/failed 汇总）。
 *
 * CSV 批量导入：本仓库后端没有「项目资产导入」专用端点，因此**纯前端**实现——
 * 解析 CSV 取出资产编号 → 在 available-assets 里按编号精确匹配出 id → 用同一个批量添加接口提交。
 * 匹配不到的编号作为「失败行」展示（原因：不存在或不可见），不新增任何后端依赖。
 */
export interface AddProjectAssetsModalProps {
  open: boolean;
  projectId: string;
  /** 项目是否分期（决定是否显示期次下拉） */
  isPhased: boolean;
  phases: PhaseVO[];
  onClose: () => void;
  /** 提交成功（added>0）后通知父级刷新资产清单 */
  onChanged: () => void;
}

const PAGE_SIZE = 10;
/** CSV 解析后最多回填的候选页数（每页 100）：500 个候选上限，避免无界翻页 */
const IMPORT_LOOKUP_PAGES = 5;
const IMPORT_LOOKUP_PAGE_SIZE = 100;

const AddProjectAssetsModal: React.FC<AddProjectAssetsModalProps> = ({
  open,
  projectId,
  isPhased,
  phases,
  onClose,
  onChanged,
}) => {
  const { t } = useT();

  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<AvailableAssetItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  /** 跨页保留已选（Map 存完整对象，便于提交后展示编号） */
  const [selected, setSelected] = useState<Record<string, AvailableAssetItem>>({});
  const [phaseId, setPhaseId] = useState<string | undefined>(undefined);
  const [linkType, setLinkType] = useState<ProjectAssetLinkType>('TARGET');

  const [submitting, setSubmitting] = useState(false);
  const [summary, setSummary] = useState<BulkAddAssetsResult | null>(null);
  /** CSV 导入中未匹配到的编号（单独展示，便于用户核对） */
  const [importMisses, setImportMisses] = useState<string[]>([]);
  const [importing, setImporting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    setLoadError(null);
    try {
      const res = await getProjectAvailableAssets(projectId, { keyword: keyword.trim() || undefined, page: page - 1, size: PAGE_SIZE });
      const parsed = extractAvailableAssetPage(res);
      setItems(parsed.items);
      setTotal(parsed.total);
    } catch (error: any) {
      setItems([]);
      setTotal(0);
      const status = error?.response?.status;
      setLoadError(
        status === 404
          ? t('projectAsset.projectNotFound')
          : status === 403
            ? t('projectAsset.forbidden')
            : t('projectAsset.loadFailed'),
      );
    } finally {
      setLoading(false);
    }
  }, [projectId, keyword, page, t]);

  useEffect(() => {
    if (!open) return;
    void load();
  }, [open, load]);

  /** 打开时重置状态（关闭时保留已选会导致下次误提交） */
  useEffect(() => {
    if (open) return;
    setKeyword('');
    setPage(1);
    setSelected({});
    setPhaseId(undefined);
    setLinkType('TARGET');
    setSummary(null);
    setImportMisses([]);
  }, [open]);

  const selectedIds = useMemo(() => Object.keys(selected), [selected]);

  const submit = async (assetIds: string[], misses: string[] = []) => {
    if (assetIds.length === 0) {
      message.warning(t('projectAsset.selectRequired'));
      return;
    }
    setSubmitting(true);
    try {
      const res = await bulkAddProjectAssets(projectId, {
        assetIds,
        phaseId: isPhased ? phaseId ?? null : null,
        linkType,
      });
      const result = extractBulkAddResult(res);
      setSummary(result);
      if (result.added > 0) {
        onChanged();
        await load();
        setSelected({});
      }
      // 只有「完全没有需要用户注意的结果」才自动关闭：
      // - added = 0（全部被跳过）→ 保留，否则用户看不到「为什么一个都没加进去」
      // - failed > 0 → 保留（展示失败明细）
      // - CSV 里有未匹配编号 → 保留（展示未匹配清单）
      if (result.added > 0 && result.failed.length === 0 && misses.length === 0 && importMisses.length === 0) {
        message.success(t('projectAsset.addedToast', { count: result.added }));
        onClose();
      }
      // 否则保留弹窗让用户看明细
    } catch (error: any) {
      const status = error?.response?.status;
      message.error(
        status === 403
          ? t('projectAsset.forbidden')
          : status === 404
            ? t('projectAsset.projectNotFound')
            : t('projectAsset.submitFailed'),
      );
    } finally {
      setSubmitting(false);
    }
  };

  /** ---------------- CSV 导入（纯前端：解析编号 → 匹配 id → 同一个批量接口） ---------------- */
  const parseCsvCodes = (text: string): string[] => {
    const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    if (lines.length === 0) return [];
    const splitRow = (row: string) =>
      row
        .split(',')
        .map((cell) => cell.replace(/^"|"$/g, '').trim())
        .filter((cell) => cell.length > 0);
    const header = splitRow(lines[0]).map((cell) => cell.toLowerCase());
    const codeIndex = header.findIndex((cell) => ['code', 'assetcode', 'asset_code', '资产编号', '编号'].includes(cell));
    const from = codeIndex >= 0 ? 1 : 0;
    const idx = codeIndex >= 0 ? codeIndex : 0;
    const codes: string[] = [];
    for (let i = from; i < lines.length; i += 1) {
      const row = splitRow(lines[i]);
      const code = row[idx];
      if (code) codes.push(code);
    }
    return Array.from(new Set(codes));
  };

  const lookupIdsByCodes = async (codes: string[]): Promise<{ ids: string[]; misses: string[] }> => {
    if (codes.length === 0) return { ids: [], misses: [] };
    const wanted = new Set(codes);
    const found = new Map<string, string>();
    for (let p = 0; p < IMPORT_LOOKUP_PAGES && found.size < wanted.size; p += 1) {
      const res = await getProjectAvailableAssets(projectId, { page: p, size: IMPORT_LOOKUP_PAGE_SIZE });
      const parsed = extractAvailableAssetPage(res);
      parsed.items.forEach((item) => {
        if (wanted.has(item.code)) found.set(item.code, item.id);
      });
      if (parsed.items.length < IMPORT_LOOKUP_PAGE_SIZE) break;
    }
    const ids: string[] = [];
    const misses: string[] = [];
    codes.forEach((code) => {
      const id = found.get(code);
      if (id) ids.push(id);
      else misses.push(code);
    });
    return { ids, misses };
  };

  const handleCsvFile = async (file: File) => {
    const invalid = validateCsvFile(file);
    if (invalid) {
      message.error(t('projectAsset.importInvalidFile'));
      return;
    }
    setImporting(true);
    try {
      const text = await file.text();
      const codes = parseCsvCodes(text);
      if (codes.length === 0) {
        message.warning(t('projectAsset.importEmpty'));
        return;
      }
      const { ids, misses } = await lookupIdsByCodes(codes);
      setImportMisses(misses);
      if (ids.length === 0) {
        message.warning(t('projectAsset.importAllMissing', { count: codes.length }));
        return;
      }
      await submit(ids, misses);
    } catch {
      message.error(t('projectAsset.importFailed'));
    } finally {
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const downloadTemplate = () => {
    const content = 'code\nASSET-0001\nASSET-0002\n';
    const blob = new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'project-assets-template.csv';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  };

  const columns: TableColumnsType<AvailableAssetItem> = [
    { title: t('projectAsset.colCode'), dataIndex: 'code', key: 'code', width: 170 },
    { title: t('projectAsset.colName'), dataIndex: 'name', key: 'name' },
    { title: t('projectAsset.colType'), dataIndex: 'targetType', key: 'targetType', width: 120, render: (v?: string) => v || '-' },
    {
      title: t('projectAsset.colStatus'),
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (v?: string) => (v ? <Tag>{v}</Tag> : '-'),
    },
    {
      title: t('projectAsset.colLinkCount'),
      dataIndex: 'linkCount',
      key: 'linkCount',
      width: 130,
      render: (value: number) => (
        <Typography.Text type={value === 0 ? 'warning' : undefined}>{value}</Typography.Text>
      ),
    },
  ];

  return (
    <Modal
      open={open}
      title={t('projectAsset.addTitle')}
      onCancel={() => {
        if (submitting) return;
        onClose();
      }}
      width={860}
      maskClosable={false}
      destroyOnHidden
      footer={
        <Space>
          <Button onClick={onClose} disabled={submitting}>
            {t('common.cancel')}
          </Button>
          <Button
            type="primary"
            loading={submitting}
            onClick={() => void submit(selectedIds)}
            data-testid="project-asset-add-submit"
          >
            {t('projectAsset.addSubmit', { count: selectedIds.length })}
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Space wrap>
          <Input
            allowClear
            prefix={<SearchOutlined />}
            style={{ width: 260 }}
            placeholder={t('projectAsset.searchPlaceholder')}
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(1);
            }}
            data-testid="project-asset-add-search"
          />
          {isPhased ? (
            <Select
              allowClear
              style={{ width: 220 }}
              placeholder={t('projectAsset.phasePlaceholder')}
              value={phaseId}
              onChange={setPhaseId}
              options={phases.map((phase) => ({
                value: phase.id,
                label: `${phase.phaseNo ?? '-'}. ${phase.name ?? ''}`,
              }))}
              data-testid="project-asset-add-phase"
            />
          ) : null}
          <Select
            style={{ width: 190 }}
            value={linkType}
            onChange={(value) => setLinkType(value as ProjectAssetLinkType)}
            options={PROJECT_ASSET_LINK_TYPES.map((type) => ({ value: type, label: t(linkTypeKey(type)) }))}
            data-testid="project-asset-add-linktype"
          />
        </Space>

        <Space>
          <Button icon={<UploadOutlined />} onClick={() => fileInputRef.current?.click()} loading={importing} data-testid="project-asset-import">
            {t('projectAsset.importCsv')}
          </Button>
          <Button type="link" size="small" icon={<DownloadOutlined />} onClick={downloadTemplate} data-testid="project-asset-import-template">
            {t('projectAsset.downloadTemplate')}
          </Button>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t('projectAsset.importHint')}
          </Typography.Text>
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv"
            style={{ display: 'none' }}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) void handleCsvFile(file);
            }}
          />
        </Space>

        {summary ? (
          <Alert
            type={summary.failed.length > 0 ? 'warning' : 'success'}
            showIcon
            data-testid="project-asset-add-summary"
            message={
              <Space size={16} wrap>
                <span>{t('projectAsset.summaryAdded', { count: summary.added })}</span>
                <span>{t('projectAsset.summarySkipped', { count: summary.skipped })}</span>
                <span>{t('projectAsset.summaryFailed', { count: summary.failed.length })}</span>
              </Space>
            }
            description={
              summary.failed.length > 0 || importMisses.length > 0 ? (
                <Collapse
                  size="small"
                  ghost
                  items={[
                    ...(summary.failed.length > 0
                      ? [
                          {
                            key: 'failed',
                            label: t('projectAsset.failedDetails', { count: summary.failed.length }),
                            children: (
                              <Table
                                size="small"
                                rowKey={(row) => `${row.assetId ?? ''}-${row.code ?? ''}`}
                                pagination={false}
                                dataSource={summary.failed}
                                columns={[
                                  { title: t('projectAsset.colCode'), dataIndex: 'code', key: 'code', width: 180 },
                                  { title: t('projectAsset.colReason'), dataIndex: 'reason', key: 'reason' },
                                ]}
                              />
                            ),
                          },
                        ]
                      : []),
                    ...(importMisses.length > 0
                      ? [
                          {
                            key: 'misses',
                            label: t('projectAsset.importMisses', { count: importMisses.length }),
                            children: (
                              <Space wrap size={[4, 4]}>
                                {importMisses.map((code) => (
                                  <Tag key={code}>{code}</Tag>
                                ))}
                              </Space>
                            ),
                          },
                        ]
                      : []),
                  ]}
                />
              ) : undefined
            }
          />
        ) : null}

        {loadError ? <Alert type="error" showIcon message={loadError} /> : null}

        <div data-testid="project-available-assets-wrap">
        <Table<AvailableAssetItem>
          size="small"
          rowKey="id"
          loading={loading}
          dataSource={items}
          columns={columns}
          scroll={{ x: 'max-content' }}
          data-testid="project-available-assets-table"
          locale={{ emptyText: t('projectAsset.empty') }}
          rowSelection={{
            selectedRowKeys: selectedIds,
            preserveSelectedRowKeys: true,
            onChange: (_keys, rows) => {
              const next: Record<string, AvailableAssetItem> = {};
              // 保留跨页已选：本页被取消的键要移除，本页新选的加入
              const pageIds = new Set(items.map((item) => item.id));
              Object.entries(selected).forEach(([id, item]) => {
                if (!pageIds.has(id)) next[id] = item;
              });
              rows.forEach((row) => {
                next[row.id] = row;
              });
              setSelected(next);
            },
          }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            onChange: (nextPage) => setPage(nextPage),
            showTotal: (count) => t('projectAsset.total', { count }),
          }}
        />
        </div>

        <Divider style={{ margin: '4px 0' }} />
        <Space size={12} wrap>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            <InboxOutlined /> {t('projectAsset.selectedCount', { count: selectedIds.length })}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t('projectAsset.batchHint')}
          </Typography.Text>
        </Space>
      </Space>
    </Modal>
  );
};

export default AddProjectAssetsModal;




