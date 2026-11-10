import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Input, Select, Space, Table, Tag, message } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { getVulnerabilityList } from '@/api/vulnerability';
import { getAssetList } from '@/api/asset';
import type { AssetVO } from '@/types/asset';
import type { VulnerabilityVO } from '@/types/vulnerability';
import { useT } from '@/i18n';
import type { MessageKey } from '@/i18n/messages/zh-CN';
import {
  SEVERITY_OPTIONS,
  WORKFLOW_STATUS_OPTIONS,
  customerColor,
  customerKey,
  disclosureColor,
  disclosureKey,
  severityColor,
  severityKey,
  workflowColor,
  workflowKey,
} from './constants';

/**
 * 漏洞列表页（t171 整页重写）。
 *
 * ## 为什么重写
 * 旧文件除「详情 Drawer + 创建 Modal」外，还内嵌了整套旧编辑页实现（`handleEditPageSubmit`、
 * `editPageForm`、`UNEXPECTED_CHANGE_FIELDS`、`loadAssetVersions` + 回填 effect，约 200 行），
 * 全部挂在 `vulnerabilityId`/`isEditPage` 分支上。t168 已把详情与编辑拆成独立整页
 * （`/vulnerabilities/:id`、`/vulnerabilities/new`、`/vulnerabilities/:id/edit`），
 * 本页只负责**列表**，因此删掉所有分支与弹框/抽屉，入口统一改成路由跳转。
 *
 * ## 保留的能力
 * - `getVulnerabilityList` + `params`（分页/排序/筛选）与 URL `search` 参数同步；
 * - 筛选：关键字、工作流状态、严重度（资产列展示走资产名映射）；
 * - 表格列（编号/标题/资产/类型/严重度/CVSS/工作流/客户/披露/操作）；
 * - 行操作：详情/编辑跳整页；**状态流转仍由详情页的 PATCH 端点负责**（本页不提交状态）。
 *
 * ## 兼容
 * `/vulnerabilities?open=<id>` → `/vulnerabilities/<id>`；`?new=1` → `/vulnerabilities/new`，
 * 均先 `message.info` 再 `replace`（沿用 t142 的模式，避免历史书签失效）。
 */

const getPayload = (res: any) => (res as any)?.data ?? res;

const readText = (source: any, camel: string, snake: string): string | undefined => {
  const value = source?.[camel] ?? source?.[snake];
  if (value === null || value === undefined || value === '') return undefined;
  return String(value);
};

interface ListParams {
  page: number;
  page_size: number;
  search: string;
  severity?: string;
  workflow_status?: string;
}

const Vulnerabilities: React.FC = () => {
  const { t } = useT();
  const location = useLocation();
  const navigate = useNavigate();

  const labelOf = (key: MessageKey | undefined, fallback?: string | null) => (key ? t(key) : fallback || '-');

  const [data, setData] = useState<VulnerabilityVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [assets, setAssets] = useState<AssetVO[]>([]);
  const [params, setParams] = useState<ListParams>({ page: 1, page_size: 10, search: '' });
  const [searchText, setSearchText] = useState('');

  const assetNameMap = useMemo(() => {
    const map = new Map<string, string>();
    assets.forEach((asset) => map.set(asset.id, asset.name || asset.code || asset.id));
    return map;
  }, [assets]);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getVulnerabilityList(params as any);
      const payload = getPayload(res);
      setData(payload?.content || payload?.items || []);
      setTotal(payload?.totalElements || payload?.total || 0);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  // 资产名映射（资产列展示用；一次拉取，不做 per-row 请求）
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res: any = await getAssetList({ page: 1, page_size: 200 } as any);
        const payload = getPayload(res);
        if (!cancelled) setAssets(payload?.content || payload?.items || []);
      } catch {
        if (!cancelled) setAssets([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // URL ?search= → 列表筛选（保持 shareable URL）
  useEffect(() => {
    const search = new URLSearchParams(location.search).get('search') || '';
    setSearchText(search);
    setParams((prev) => (prev.search === search ? prev : { ...prev, search, page: 1 }));
  }, [location.search]);

  // 旧入口兼容：?open=<id> → 详情整页；?new=1 → 新建整页
  useEffect(() => {
    const query = new URLSearchParams(location.search);
    const openId = query.get('open');
    if (openId) {
      message.info(t('vuln.list.redirectOpen'));
      navigate(`/vulnerabilities/${openId}`, { replace: true });
      return;
    }
    if (query.get('new') === '1') {
      message.info(t('vuln.list.redirectNew'));
      navigate('/vulnerabilities/new', { replace: true });
    }
  }, [location.search, navigate, t]);

  const columns = [
    {
      title: t('vuln.list.colNo'),
      key: 'vulnerabilityNo',
      width: 180,
      render: (_: unknown, record: VulnerabilityVO) => readText(record, 'vulnerabilityNo', 'vulnerability_no') ?? '-',
    },
    {
      title: t('vuln.list.colTitle'),
      dataIndex: 'title',
      key: 'title',
      render: (value: string, record: VulnerabilityVO) => (
        <a onClick={() => navigate(`/vulnerabilities/${record.id}`)} data-testid={`vuln-open-${record.id}`}>
          {value || '-'}
        </a>
      ),
    },
    {
      title: t('vuln.list.colAsset'),
      key: 'asset',
      render: (_: unknown, record: VulnerabilityVO) => {
        const assetName = (record as any).asset?.name;
        const assetId = readText(record, 'assetId', 'asset_id');
        return assetName || (assetId ? assetNameMap.get(assetId) ?? assetId : '-');
      },
    },
    { title: t('vuln.list.colType'), dataIndex: 'type', key: 'type' },
    {
      title: t('vuln.list.colSeverity'),
      dataIndex: 'severity',
      key: 'severity',
      render: (text: string) => <Tag color={severityColor(text)}>{labelOf(severityKey(text), text)}</Tag>,
    },
    {
      title: t('vuln.list.colCvss'),
      key: 'cvssScore',
      width: 80,
      render: (_: unknown, record: VulnerabilityVO) =>
        (record as any).cvssScore ?? (record as any).cvss_score ?? '-',
    },
    {
      title: t('vuln.list.colWorkflow'),
      key: 'workflowStatus',
      width: 120,
      render: (_: unknown, record: VulnerabilityVO) => {
        const status = readText(record, 'workflowStatus', 'workflow_status');
        return <Tag color={workflowColor(status)}>{labelOf(workflowKey(status), status)}</Tag>;
      },
    },
    {
      title: t('vuln.list.colCustomer'),
      key: 'customerStatus',
      width: 100,
      render: (_: unknown, record: VulnerabilityVO) => {
        const status = readText(record, 'customerStatus', 'customer_status');
        return <Tag color={customerColor(status)}>{labelOf(customerKey(status), status)}</Tag>;
      },
    },
    {
      title: t('vuln.list.colDisclosure'),
      key: 'disclosureStatus',
      width: 110,
      render: (_: unknown, record: VulnerabilityVO) => {
        const status = readText(record, 'disclosureStatus', 'disclosure_status');
        return <Tag color={disclosureColor(status)}>{labelOf(disclosureKey(status), status)}</Tag>;
      },
    },
    {
      title: t('vuln.list.colAction'),
      key: 'action',
      width: 180,
      render: (_: unknown, record: VulnerabilityVO) => (
        <Space size="middle">
          {/* t171：详情/编辑统一跳整页（原 Drawer / 内嵌编辑页已移除） */}
          <a onClick={() => navigate(`/vulnerabilities/${record.id}`)} data-testid={`vuln-detail-${record.id}`}>
            {t('vuln.list.detail')}
          </a>
          <a onClick={() => navigate(`/vulnerabilities/${record.id}/edit`)} data-testid={`vuln-edit-${record.id}`}>
            {t('vuln.list.edit')}
          </a>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={t('vuln.list.title')}
      extra={
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => navigate('/vulnerabilities/new')}
          data-testid="vuln-list-new"
        >
          {t('vuln.list.submit')}
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          placeholder={t('vuln.list.searchPlaceholder')}
          prefix={<SearchOutlined />}
          style={{ width: 280 }}
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          onPressEnter={() => setParams((prev) => ({ ...prev, search: searchText.trim(), page: 1 }))}
          allowClear
          data-testid="vuln-list-search"
        />
        <Select
          allowClear
          placeholder={t('vuln.list.filterWorkflow')}
          style={{ width: 160 }}
          value={params.workflow_status}
          options={WORKFLOW_STATUS_OPTIONS.map((item) => ({ value: item.value, label: t(item.labelKey) }))}
          onChange={(value) => setParams((prev) => ({ ...prev, workflow_status: value, page: 1 }))}
          data-testid="vuln-list-filter-workflow"
        />
        <Select
          allowClear
          placeholder={t('vuln.list.filterSeverity')}
          style={{ width: 140 }}
          value={params.severity}
          options={SEVERITY_OPTIONS.map((item) => ({ value: item.value, label: t(item.labelKey) }))}
          onChange={(value) => setParams((prev) => ({ ...prev, severity: value, page: 1 }))}
          data-testid="vuln-list-filter-severity"
        />
        <Button
          type="primary"
          onClick={() => setParams((prev) => ({ ...prev, search: searchText.trim(), page: 1 }))}
          data-testid="vuln-list-query"
        >
          {t('vuln.list.query')}
        </Button>
        <Button
          onClick={() => {
            setSearchText('');
            setParams({ page: 1, page_size: params.page_size, search: '' });
          }}
          data-testid="vuln-list-reset"
        >
          {t('vuln.list.reset')}
        </Button>
      </Space>

      <Table
        columns={columns}
        dataSource={data}
        rowKey="id"
        loading={loading}
        scroll={{ x: 1200 }}
        data-testid="vuln-table"
        pagination={{
          current: params.page,
          pageSize: params.page_size,
          total,
          showSizeChanger: true,
          showTotal: (count) => t('vuln.list.total', { count }),
          onChange: (page, pageSize) => setParams((prev) => ({ ...prev, page, page_size: pageSize })),
        }}
      />
    </Card>
  );
};

export default Vulnerabilities;
