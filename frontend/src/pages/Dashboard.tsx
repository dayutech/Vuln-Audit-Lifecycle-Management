import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Dropdown,
  Empty,
  Row,
  Skeleton,
  Space,
  Statistic,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  AppstoreOutlined,
  DownloadOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  CheckSquareOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  deleteDashboardConfig,
  exportDashboardReport,
  getAssetStats,
  getDashboardConfig,
  getVulnerabilityStats,
  getWorkloadStats,
  saveDashboardConfig,
} from '@/api/dashboard';
import { getMyAccessProfile, type MyAccessProfile } from '@/api/permission';
import { getApiErrorMessage, getApiErrorStatus, getBlobErrorMessage } from '@/utils/errorMessage';
import { saveBlobAsFile } from '@/utils/download';
import { describeApiFailure } from './dashboard/apiFailure';
import { canExportReports, resolveStatsScope, statsScopeTagColor } from './dashboard/statsScope';
import type {
  AssetStats,
  DashboardCardLayout,
  ExportType,
  VulnerabilityStats,
  WorkloadStats,
} from '@/types/dashboard';
import TrendChart from './dashboard/TrendChart';
import WorkloadRank from './dashboard/WorkloadRank';
import LayoutSettings from './dashboard/LayoutSettings';
import { decodeLayout, defaultLayout, encodeLayout } from './dashboard/layoutConfig';

const { Title, Text } = Typography;

const TYPE_COLORS = ['#1890ff', '#13c2c2', '#722ed1', '#eb2f96', '#fa8c16', '#52c41a'];
const SEVERITY_META: Record<string, { label: string; color: string }> = {
  critical: { label: '严重', color: '#f5222d' },
  high: { label: '高危', color: '#fa8c16' },
  medium: { label: '中危', color: '#1890ff' },
  low: { label: '低危', color: '#52c41a' },
};
const PRIORITY_META: Record<string, { label: string; color: string }> = {
  P0: { label: 'P0', color: '#f5222d' },
  P1: { label: 'P1', color: '#fa8c16' },
  P2: { label: 'P2', color: '#1890ff' },
  P3: { label: 'P3', color: '#8c8c8c' },
};
const ASSET_STATUS_LABEL: Record<string, string> = {
  active: '在用',
  under_test: '测试中',
  suspended: '暂停',
  archived: '归档',
};
const WORKFLOW_LABEL: Record<string, string> = {
  new: '新建',
  discovered: '已发现',
  in_progress: '处理中',
  resolved: '已解决',
  fixed: '已修复',
  verified: '已验证',
  reopened: '重新打开',
  closed: '已关闭',
  wontfix: '不修复',
};

/** 严重程度/工作流状态的小写归一（t29：库中大写、响应小写；兼容旧版本可能返回的大写键） */
const normalizeEnumMap = (map: Record<string, number> | undefined): Record<string, number> | undefined => {
  if (!map) return undefined;
  const normalized: Record<string, number> = {};
  Object.entries(map).forEach(([key, value]) => {
    const lower = key.toLowerCase();
    normalized[lower] = (normalized[lower] ?? 0) + (Number(value) || 0);
  });
  return normalized;
};

const EXPORT_OPTIONS: { key: ExportType; label: string }[] = [
  { key: 'assets', label: '导出资产清单 CSV' },
  { key: 'vulnerabilities', label: '导出漏洞清单 CSV' },
  { key: 'workload', label: '导出工作量 CSV' },
];

/** 把后端返回的 count map 转成图表数据 */
const toChartData = (map: Record<string, number> | undefined, labels?: Record<string, string>) =>
  Object.entries(map ?? {}).map(([key, value]) => ({
    name: labels?.[key] ?? key,
    value: Number(value) || 0,
    rawKey: key,
  }));

const Dashboard: React.FC = () => {
  const [assetStats, setAssetStats] = useState<AssetStats | null>(null);
  const [vulnStats, setVulnStats] = useState<VulnerabilityStats | null>(null);
  const [workload, setWorkload] = useState<WorkloadStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  /** 漏洞统计失败态：403 单独渲染「仅管理层可见」，其余按 5xx/网络分级提示 */
  const [vulnStatsError, setVulnStatsError] = useState<{ forbidden: boolean; message: string } | null>(null);
  const [profile, setProfile] = useState<MyAccessProfile | null>(null);

  const [layout, setLayout] = useState<DashboardCardLayout[]>(defaultLayout());
  const [draftLayout, setDraftLayout] = useState<DashboardCardLayout[]>(defaultLayout());
  const [layoutModalOpen, setLayoutModalOpen] = useState(false);
  const [layoutSaving, setLayoutSaving] = useState(false);
  const [configLoaded, setConfigLoaded] = useState(false);

  const [trendDays, setTrendDays] = useState(30);
  const [exportingType, setExportingType] = useState<ExportType | null>(null);

  const scope = useMemo(() => resolveStatsScope(profile), [profile]);

  const loadStats = useCallback(async () => {
    setLoading(true);
    setRefreshError(null);
    setVulnStatsError(null);

    // 资产/工作量统计对所有角色可用；漏洞统计 t29 起限管理层，403 属预期（不弹错、不当成数据缺失）
    const [assetsResult, workloadResult, vulnResult] = await Promise.allSettled([
      getAssetStats(),
      getWorkloadStats(),
      getVulnerabilityStats(),
    ]);

    if (assetsResult.status === 'fulfilled') {
      setAssetStats(assetsResult.value);
    } else {
      setAssetStats(null);
      setRefreshError(getApiErrorMessage(assetsResult.reason, '资产统计加载失败'));
    }

    if (workloadResult.status === 'fulfilled') {
      setWorkload(workloadResult.value);
    } else {
      setWorkload(null);
    }

    if (vulnResult.status === 'fulfilled') {
      setVulnStats(vulnResult.value);
      setVulnStatsError(null);
    } else {
      const status = getApiErrorStatus(vulnResult.reason);
      setVulnStats(null);
      if (status === 403) {
        setVulnStatsError({
          forbidden: true,
          message: '漏洞统计仅管理层（管理员 / 项目负责人 / 小组长）可见，当前账号无统计准入权限。',
        });
      } else {
        const failure = describeApiFailure(status);
        setVulnStatsError({
          forbidden: false,
          message: `漏洞统计${failure.label}：${failure.hint}`,
        });
      }
    }

    setLoading(false);
  }, []);

  const loadConfig = useCallback(async () => {
    try {
      const config = await getDashboardConfig();
      // 未保存过配置时后端返回 data 为 null（ApiResponse @JsonInclude NON_NULL 会省略 data 字段）
      setLayout(decodeLayout(config?.layout, config?.cards));
    } catch {
      setLayout(defaultLayout());
    } finally {
      setConfigLoaded(true);
    }
  }, []);

  useEffect(() => {
    getMyAccessProfile().then(setProfile);
    loadStats();
    loadConfig();
  }, [loadStats, loadConfig]);

  const handleOpenLayout = () => {
    setDraftLayout(layout.map((row) => ({ ...row })));
    setLayoutModalOpen(true);
  };

  const handleSaveLayout = async () => {
    setLayoutSaving(true);
    try {
      await saveDashboardConfig(encodeLayout(draftLayout));
      setLayout(draftLayout.map((row) => ({ ...row })));
      setLayoutModalOpen(false);
      message.success('大盘布局已保存');
    } catch (error: any) {
      if (!error?.response) message.error('布局保存失败，已保留当前布局');
    } finally {
      setLayoutSaving(false);
    }
  };

  const handleResetLayout = async () => {
    setLayoutSaving(true);
    try {
      await deleteDashboardConfig();
      const next = defaultLayout();
      setLayout(next);
      setDraftLayout(next);
      setLayoutModalOpen(false);
      message.success('已恢复默认布局');
    } catch (error: any) {
      if (!error?.response) message.error('恢复默认布局失败');
    } finally {
      setLayoutSaving(false);
    }
  };

  /** 导出报表与 /dashboard/export 的准入一致（admin / project_leader）；团队长及以下不展示入口 */
  const canExport = useMemo(() => canExportReports(profile), [profile]);

  /**
   * 漏洞统计可见性：
   * - 后端 t29 对工程师返回 403（vulnStatsError.forbidden）；
   * - 若画像已明确为非管理类角色，则即使旧后端返回 200 也按「仅管理层可见」展示，
   *   避免把全局数值当成「当前可见范围」误导用户。
   */
  const vulnAccessBlocked = Boolean(vulnStatsError?.forbidden) || (profile ? !scope.canViewVulnStats : false);
  const vulnUnavailable = !vulnAccessBlocked && Boolean(vulnStatsError);
  const vulnBlockedMessage = `漏洞统计仅管理层（管理员 / 项目负责人 / 小组长）可见，当前角色：${scope.roleLabel}。`;

  const handleExport = async (type: ExportType) => {
    setExportingType(type);
    try {
      const blob = await exportDashboardReport(type, 'csv');
      if (!(blob instanceof Blob)) {
        message.error('导出失败：响应不是文件流');
        return;
      }
      // 防御：少数代理会把错误 JSON 以 200 返回，按 content-type 分流而不是直接落盘
      if (blob.type.includes('json')) {
        const text = await blob.text();
        let detail = '导出失败';
        try {
          detail = (JSON.parse(text) as { message?: string })?.message ?? detail;
        } catch {
          detail = text.slice(0, 160) || detail;
        }
        message.error(detail);
        return;
      }
      if (blob.size === 0) {
        message.error('导出文件为空，请检查服务端导出实现');
        return;
      }
      saveBlobAsFile(blob, `${type}.csv`);
      message.success(`已导出 ${type}.csv（${(blob.size / 1024).toFixed(1)} KB）`);
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 403) {
        message.error('当前账号无导出权限（仅管理员 / 项目负责人可导出报表）');
        return;
      }
      // 错误体是 Blob，全局拦截器读不到 message，这里解析后端原始提示
      message.error(await getBlobErrorMessage(error, '导出失败'));
    } finally {
      setExportingType(null);
    }
  };

  const renderAssetTypeChart = () => {
    const typeData = toChartData(assetStats?.by_type);
    const priorityData = toChartData(assetStats?.by_priority, { P0: 'P0 紧急', P1: 'P1 高', P2: 'P2 中', P3: 'P3 低' });
    if (typeData.length === 0 && priorityData.length === 0) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无资产数据" />;
    }
    return (
      <Row gutter={8}>
        <Col span={12}>
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie data={typeData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={80} label>
                {typeData.map((entry, index) => (
                  <Cell key={`type-${entry.rawKey}`} fill={TYPE_COLORS[index % TYPE_COLORS.length]} />
                ))}
              </Pie>
              <ChartTooltip />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </Col>
        <Col span={12}>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={priorityData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis allowDecimals={false} />
              <ChartTooltip />
              <Bar dataKey="value" name="资产数">
                {priorityData.map((entry) => (
                  <Cell key={`priority-${entry.rawKey}`} fill={PRIORITY_META[entry.rawKey]?.color ?? '#1890ff'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </Col>
      </Row>
    );
  };

  const renderVulnChart = () => {
    // 403（t29 工程师）/ 角色预判 / 5xx 都单独提示，绝不渲染成「暂无漏洞数据」
    if (vulnAccessBlocked) {
      return (
        <Alert
          type="info"
          showIcon
          message="漏洞统计仅管理层可见"
          description={vulnBlockedMessage}
        />
      );
    }
    if (vulnUnavailable && vulnStatsError) {
      return <Alert type="warning" showIcon message="漏洞统计暂不可用" description={vulnStatsError.message} />;
    }
    if (loading) return <Skeleton active paragraph={{ rows: 6 }} />;
    const severityData = toChartData(
      normalizeEnumMap(vulnStats?.by_severity),
      Object.fromEntries(Object.entries(SEVERITY_META).map(([key, meta]) => [key, meta.label]))
    );
    const workflowData = toChartData(normalizeEnumMap(vulnStats?.by_workflow_status), WORKFLOW_LABEL);
    if (severityData.length === 0 && workflowData.length === 0) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="当前可见范围内还没有漏洞记录，提交漏洞后这里会显示分布"
        />
      );
    }
    return (
      <Row gutter={8}>
        <Col span={12}>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={severityData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis allowDecimals={false} />
              <ChartTooltip />
              <Bar dataKey="value" name="漏洞数">
                {severityData.map((entry) => (
                  <Cell key={`sev-${entry.rawKey}`} fill={SEVERITY_META[entry.rawKey]?.color ?? '#8c8c8c'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </Col>
        <Col span={12}>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={workflowData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis allowDecimals={false} />
              <ChartTooltip />
              <Bar dataKey="value" name="漏洞数" fill="#2f54eb" />
            </BarChart>
          </ResponsiveContainer>
        </Col>
      </Row>
    );
  };

  const renderCard = (id: string) => {
    switch (id) {
      case 'kpi-assets':
        return (
          <Card loading={loading} variant="borderless">
            <Statistic
              title={
                <Space size={6}>
                  资产总数
                  <Tag color={statsScopeTagColor(scope.tone)}>{scope.label}</Tag>
                </Space>
              }
              value={assetStats?.total ?? 0}
              prefix={<AppstoreOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
            <Space size={4} wrap style={{ marginTop: 8 }}>
              {Object.entries(assetStats?.by_status ?? {}).map(([key, value]) => (
                <Tag key={key} color={key === 'active' ? 'green' : 'default'}>
                  {ASSET_STATUS_LABEL[key] ?? key} {value}
                </Tag>
              ))}
            </Space>
          </Card>
        );
      case 'kpi-vulnerabilities':
        return (
          <Card loading={loading} variant="borderless">
            <Statistic
              title={
                <Space size={6}>
                  漏洞总数
                  <Tag color={statsScopeTagColor(scope.tone)}>{scope.label}</Tag>
                </Space>
              }
              value={vulnAccessBlocked || vulnUnavailable ? '-' : vulnStats?.total ?? 0}
              prefix={<SafetyCertificateOutlined />}
              valueStyle={{ color: '#cf1322' }}
            />
            {vulnAccessBlocked ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                仅管理层可见（当前角色：{scope.roleLabel}）
              </Text>
            ) : vulnUnavailable ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                统计加载失败，可稍后刷新
              </Text>
            ) : (
              <Space size={4} wrap style={{ marginTop: 8 }}>
                {Object.entries(normalizeEnumMap(vulnStats?.by_severity) ?? {}).map(([key, value]) => (
                  <Tag key={key} color={SEVERITY_META[key]?.color ?? 'default'}>
                    {SEVERITY_META[key]?.label ?? key} {value}
                  </Tag>
                ))}
              </Space>
            )}
          </Card>
        );
      case 'kpi-workload':
        return (
          <Card loading={loading} variant="borderless">
            <Statistic
              title="我的任务"
              value={
                Object.values(workload?.tasks ?? {}).reduce((sum, value) => sum + (Number(value) || 0), 0)
              }
              prefix={<CheckSquareOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
            <Space size={4} wrap style={{ marginTop: 8 }}>
              <Tag color="orange">待处理 {workload?.tasks?.pending ?? 0}</Tag>
              <Tag color="blue">进行中 {workload?.tasks?.in_progress ?? 0}</Tag>
              <Tag color="green">已完成 {workload?.tasks?.completed ?? 0}</Tag>
            </Space>
          </Card>
        );
      case 'kpi-claims':
        return (
          <Card loading={loading} variant="borderless">
            <Statistic
              title="我的资产认领"
              value={Object.values(workload?.claims ?? {}).reduce((sum, value) => sum + (Number(value) || 0), 0)}
              prefix={<TrophyOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
            <Space size={4} wrap style={{ marginTop: 8 }}>
              <Tag color="orange">待审批 {workload?.claims?.pending ?? 0}</Tag>
              <Tag color="blue">已通过 {workload?.claims?.approved ?? 0}</Tag>
              <Tag color="green">已完成 {workload?.claims?.completed ?? 0}</Tag>
            </Space>
          </Card>
        );
      case 'chart-trends':
        return (
          <Card title="趋势折线" variant="borderless">
            <TrendChart days={trendDays} onDaysChange={setTrendDays} />
          </Card>
        );
      case 'chart-asset-type':
        return (
          <Card
            title={
              <Space size={6}>
                资产分布（类型 / 优先级）
                <Tag color={statsScopeTagColor(scope.tone)}>{scope.label}</Tag>
              </Space>
            }
            variant="borderless"
          >
            {loading ? <Skeleton active paragraph={{ rows: 6 }} /> : renderAssetTypeChart()}
          </Card>
        );
      case 'chart-vuln-severity':
        return (
          <Card
            title={
              <Space size={6}>
                漏洞分布（严重程度 / 工作流状态）
                <Tag color={statsScopeTagColor(scope.tone)}>{scope.label}</Tag>
              </Space>
            }
            variant="borderless"
          >
            {renderVulnChart()}
          </Card>
        );
      case 'rank-workload':
        return (
          <Card title="工作量排行" variant="borderless">
            <WorkloadRank />
          </Card>
        );
      default:
        return null;
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <Title level={2} style={{ margin: 0 }}>
          安全态势大盘
        </Title>
        <Space wrap>
          {canExport ? (
            <Dropdown
              menu={{
                items: EXPORT_OPTIONS.map((option) => ({
                  key: option.key,
                  label: option.label,
                  onClick: () => handleExport(option.key),
                })),
              }}
            >
              <Button icon={<DownloadOutlined />} loading={!!exportingType}>
                导出报表
              </Button>
            </Dropdown>
          ) : null}
          <Button icon={<SettingOutlined />} onClick={handleOpenLayout}>
            布局设置
          </Button>
          <Button icon={<ReloadOutlined />} onClick={loadStats} loading={loading}>
            刷新
          </Button>
        </Space>
      </div>

      {refreshError ? <Alert type="warning" showIcon style={{ marginTop: 16 }} message={refreshError} /> : null}
      {!configLoaded ? <Alert type="info" showIcon style={{ marginTop: 16 }} message="正在加载个性化布局…" /> : null}

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {layout
          .filter((row) => row.visible)
          .map((row) => (
            <Col key={row.id} xs={24} md={row.span >= 24 ? 24 : row.span >= 12 ? 12 : 8} lg={row.span}>
              {renderCard(row.id)}
            </Col>
          ))}
      </Row>

      {layout.every((row) => !row.visible) ? (
        <Card variant="borderless" style={{ marginTop: 16 }}>
          <Empty description="所有卡片都已隐藏，可在「布局设置」中重新勾选">
            <Button type="primary" onClick={handleOpenLayout}>
              去设置
            </Button>
          </Empty>
        </Card>
      ) : null}

      <Text type="secondary" style={{ display: 'block', marginTop: 16, fontSize: 12 }}>
        数据来源：/dashboard/assets/stats、/dashboard/vulnerabilities/stats、/dashboard/workload/stats、/dashboard/trends/*；
        统计口径：{scope.label}
        {scope.tone === 'full'
          ? '（管理员 / 项目负责人可见全部资产与漏洞）'
          : '（仅统计你可访问的资产及其漏洞；漏洞统计仅管理层可用）'}
        ；布局保存在 /dashboard/config（未保存时使用默认布局）
        {canExport ? '；导出报表仅管理员 / 项目负责人可用。' : '；当前角色不展示导出入口（仅管理员 / 项目负责人可导出）。'}
      </Text>

      <LayoutSettings
        open={layoutModalOpen}
        layout={draftLayout}
        saving={layoutSaving}
        onCancel={() => setLayoutModalOpen(false)}
        onChange={setDraftLayout}
        onSave={handleSaveLayout}
        onReset={handleResetLayout}
      />
    </div>
  );
};

export default Dashboard;
