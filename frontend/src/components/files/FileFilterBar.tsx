import React from 'react';
import { Button, DatePicker, Input, Select, Space } from 'antd';
import dayjs from 'dayjs';
import { FilterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { hasActiveFilters, type FileFilters } from '@/utils/fileFilters';
import { useT } from '@/i18n';

/**
 * 文件/交付物列表的筛选条（t146，资产文件与交付物共用）。
 *
 * - **分类**：选项来自「当前已加载数据的 distinct 值」（`assetFile.filter.categoryFromData`），
 *   不依赖后端枚举 —— 交付物类型与文件分类各是各的集合，硬编码会漂移；
 * - **版本**：这里筛的是**文件行自己的版本列**（`assetVersionId`），
 *   与 t143 删掉的"顶部版本作用域下拉"不是一回事（那个是给列表接口传 `assetVersionId` 参数、
 *   对"全版本视图"毫无作用；本控件是纯前端过滤已加载的行，见 README/报告说明）；
 * - **上传人**：后端 `AssetFile`/`AssetDeliverable` 目前**没有上传人字段**，
 *   所以下拉只会在数据里真的带 `uploaderName/createdBy` 时才有选项，否则显示提示（见 `uploaderUnavailable`）；
 * - 全部条件 **AND** 组合，纯前端生效（后端列表接口无筛选/分页参数），一键「重置筛选」。
 */
export interface FileFilterBarProps {
  filters: FileFilters;
  onChange: (next: FileFilters) => void;
  categories: string[];
  versions: Array<{ id: string; label: string }>;
  /** t149：上传人下拉（value=user id，label=展示名），来自已加载行的 distinct uploadedBy/uploaderName */
  uploaderOptions: Array<{ value: string; label: string }>;
  /** testid 前缀：asset 文件用 `file-filter`，交付物用 `deliverable-filter` */
  testIdPrefix?: string;
  /** 额外提示：当前数据集里没有上传人字段 */
  uploaderUnavailable?: boolean;
}

const FileFilterBar: React.FC<FileFilterBarProps> = ({
  filters,
  onChange,
  categories,
  versions,
  uploaderOptions,
  testIdPrefix = 'file-filter',
  uploaderUnavailable = false,
}) => {
  const { t } = useT();
  const patch = (next: Partial<FileFilters>) => onChange({ ...filters, ...next });
  const active = hasActiveFilters(filters);

  return (
    <Space wrap size={[8, 8]} data-testid={`${testIdPrefix}-bar`} data-active={active ? 'true' : 'false'}>
      <Select
        allowClear
        style={{ width: 168 }}
        placeholder={t('assetFile.filter.categoryPlaceholder')}
        value={filters.category || undefined}
        options={categories.map((item) => ({ value: item, label: item }))}
        onChange={(value) => patch({ category: value ?? '' })}
        data-testid={`${testIdPrefix}-category`}
      />
      <Select
        allowClear
        style={{ width: 190 }}
        placeholder={t('assetFile.filter.versionPlaceholder')}
        value={filters.versionId || undefined}
        options={versions.map((item) => ({ value: item.id, label: item.label }))}
        onChange={(value) => patch({ versionId: value ?? '' })}
        data-testid={`${testIdPrefix}-version`}
      />
      <Input
        allowClear
        prefix={<SearchOutlined />}
        style={{ width: 220 }}
        placeholder={t('assetFile.filter.keywordPlaceholder')}
        value={filters.keyword ?? ''}
        onChange={(event) => patch({ keyword: event.target.value })}
        data-testid={`${testIdPrefix}-keyword`}
      />
      <Select
        allowClear
        showSearch
        optionFilterProp="label"
        style={{ width: 170 }}
        disabled={uploaderUnavailable || uploaderOptions.length === 0}
        placeholder={
          uploaderUnavailable ? t('assetFile.filter.uploaderUnavailable') : t('assetFile.filter.uploaderPlaceholder')
        }
        value={filters.uploaderId || undefined}
        options={uploaderOptions}
        onChange={(value) => patch({ uploaderId: value ?? '' })}
        data-testid={`${testIdPrefix}-uploader`}
        data-unavailable={uploaderUnavailable ? 'true' : 'false'}
      />
      <DatePicker.RangePicker
        allowEmpty={[true, true]}
        style={{ width: 260 }}
        value={[filters.dateFrom ? dayjs(filters.dateFrom) : null, filters.dateTo ? dayjs(filters.dateTo) : null]}
        onChange={(range) => {
          const format = (date: any) => (date ? date.format('YYYY-MM-DD') : '');
          patch({ dateFrom: format(range?.[0]), dateTo: format(range?.[1]) });
        }}
        data-testid={`${testIdPrefix}-date-range`}
      />
      <Button
        icon={<ReloadOutlined />}
        disabled={!active}
        onClick={() => onChange({})}
        data-testid={`${testIdPrefix}-reset`}
      >
        {t('assetFile.filter.reset')}
      </Button>
      <span style={{ fontSize: 12, color: '#8c8c8c' }} data-testid={`${testIdPrefix}-hint`}>
        <FilterOutlined /> {t('assetFile.filter.frontendHint')}
      </span>
    </Space>
  );
};

export default FileFilterBar;



