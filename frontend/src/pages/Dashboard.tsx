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
import { useT, type MessageKey } from '@/i18n';
import { canExportReports, resolveStatsScope, statsScopeTagColor } from './dashboard/statsScope';
import type {
  AssetStats,
  DashboardCardLayout,
  ExportType,
  VulnerabilityStats,
  WorkloadStats,
} from '@/types/dashboard';
import TrendChart from './dashboard/TrendChart';
import { CustomerDistributionCard, ProjectDistributionCard } from './dashboard/CustomerProjectDistribution';
import WorkloadRank from './dashboard/WorkloadRank';
import LayoutSettings from './dashboard/LayoutSettings';
import { decodeLayout, defaultLayout, encodeLayout } from './dashboard/layoutConfig';

const { Title, Text } = Typography;

const TYPE_COLORS = ['#1890ff', '#13c2c2', '#722ed1', '#eb2f96', '#fa8c16', '#52c41a'];
const SEVERITY_META: Record<string, { labelKey: MessageKey; color: string }> = {
  critical: { labelKey: 'dashboard.severity.critical', color: '#f5222d' },
  high: { labelKey: 'dashboard.severity.high', color: '#fa8c16' },
  medium: { labelKey: 'dashboard.severity.medium', color: '#1890ff' },
  low: { labelKey: 'dashboard.severity.low', color: '#52c41a' },
  info: { labelKey: 'dashboard.severity.info', color: '#8c8c8c' },
};
const PRIORITY_META: Record<string, { label: string; color: string }> = {
  P0: { label: 'P0', color: '#f5222d' },
  P1: { label: 'P1', color: '#fa8c16' },
  P2: { label: 'P2', color: '#1890ff' },
  P3: { label: 'P3', color: '#8c8c8c' },
};
const ASSET_STATUS_KEYS: Record<string, MessageKey> = {
  active: 'dashboard.assetStatus.active',
  under_test: 'dashboard.assetStatus.underTest',
  suspended: 'dashboard.assetStatus.suspended',
  archived: 'dashboard.assetStatus.archived',
};
const WORKFLOW_KEYS: Record<string, MessageKey> = {
  new: 'dashboard.workflow.new',
  discovered: 'dashboard.workflow.discovered',
  in_progress: 'dashboard.workflow.inProgress',
  resolved: 'dashboard.workflow.resolved',
  fixed: 'dashboard.workflow.fixed',
  verified: 'dashboard.workflow.verified',
  reopened: 'dashboard.workflow.reopened',
  closed: 'dashboard.workflow.closed',
  wontfix: 'dashboard.workflow.wontfix',
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

const EXPORT_OPTIONS: { key: ExportType; labelKey: 'dashboard.exportAssets' | 'dashboard.exportVulnerabilities' | 'dashboard.exportWorkload' }[] = [
  { key: 'assets', labelKey: 'dashboard.exportAssets' },
  { key: 'vulnerabilities', labelKey: 'dashboard.exportVulnerabilities' },
  { key: 'workload', labelKey: 'dashboard.exportWorkload' },
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
  const { t } = useT();

  const [layout, setLayout] = useState<DashboardCardLayout[]>(defaultLayout());
  const [draftLayout, setDraftLayout] = useState<DashboardCardLayout[]>(defaultLayout());
  const [layoutModalOpen, setLayoutModalOpen] = useState(false);
  const [layoutSaving, setLayoutSaving] = useState(false);
  const [configLoaded, setConfigLoaded] = useState(false);

  const [trendDays, setTrendDays] = useState(30);
  const [exportingType, setExportingType] = useState<ExportType | null>(null);

  const scope = useMemo(() => resolveStatsScope(profile), [profile]);
  /** 口径文案走 i18n（resolveStatsScope 里的中文标签仅作后端/日志口径参考） */
  const scopeLabel = useMemo(() => {
    if (scope.tone === 'full') return t('dashboard.scope.full');
    if (profile?.roles?.includes('team_leader')) return t('dashboard.scope.scopedTeam');
    return t('dashboard.scope.scoped');
  }, [scope.tone, profile, t]);

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
      setRefreshError(getApiErrorMessage(assetsResult.reason, t('dashboard.statsAssetsFailed')));
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
          message: t('dashboard.vulnForbiddenInline'),
        });
      } else {
        const failure = describeApiFailure(status);
        setVulnStatsError({
          forbidden: false,
          message: `${t(failure.labelKey, failure.vars)}：${t(failure.hintKey)}`,
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
      message.success(t('dashboard.layoutSaved'));
    } catch (error: any) {
      if (!error?.response) message.error(t('dashboard.layoutSaveFailed'));
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
      message.success(t('dashboard.layoutResetDone'));
    } catch (error: any) {
      if (!error?.response) message.error(t('dashboard.layoutResetFailed'));
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
  const vulnBlockedMessage = t('dashboard.vulnForbiddenDesc', { role: scope.roleLabel });

  const handleExport = async (type: ExportType) => {
    setExportingType(type);
    try {
      const blob = await exportDashboardReport(type, 'csv');
      if (!(blob instanceof Blob)) {
        message.error(t('dashboard.exportNotBlob'));
        return;
      }
      // 防御：少数代理会把错误 JSON 以 200 返回，按 content-type 分流而不是直接落盘
      if (blob.type.includes('json')) {
        const text = await blob.text();
        let detail = t('dashboard.exportFailed');
        try {
          detail = (JSON.parse(text) as { message?: string })?.message ?? detail;
        } catch {
          detail = text.slice(0, 160) || detail;
        }
        message.error(detail);
        return;
      }
      if (blob.size === 0) {
        message.error(t('dashboard.exportEmpty'));
        return;
      }
      saveBlobAsFile(blob, `${type}.csv`);
      message.success(t('dashboard.exportSuccess', { type, size: (blob.size / 1024).toFixed(1) }));
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 403) {
        message.error(t('dashboard.exportForbidden'));
        return;
      }
      // 错误体是 Blob，全局拦截器读不到 message，这里解析后端原始提示
      message.error(await getBlobErrorMessage(error, t('dashboard.exportFailed')));
    } finally {
      setExportingType(null);
    }
  };

  const renderAssetTypeChart = () => {
    const typeData = toChartData(assetStats?.by_type);
    const priorityData = toChartData(assetStats?.by_priority, {
      P0: t('dashboard.priority.P0'),
      P1: t('dashboard.priority.P1'),
      P2: t('dashboard.priority.P2'),
      P3: t('dashboard.priority.P3'),
    });
    if (typeData.length === 0 && priorityData.length === 0) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('dashboard.emptyAssets')} />;
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
              <Bar dataKey="value" name={t('dashboard.seriesAssets')}>
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
          message={t('dashboard.vulnForbiddenTitle')}
          description={vulnBlockedMessage}
        />
      );
    }
    if (vulnUnavailable && vulnStatsError) {
      return <Alert type="warning" showIcon message={t('dashboard.vulnUnavailableTitle')} description={vulnStatsError.message} />;
    }
    if (loading) return <Skeleton active paragraph={{ rows: 6 }} />;
    const severityData = toChartData(
      normalizeEnumMap(vulnStats?.by_severity),
      Object.fromEntries(Object.entries(SEVERITY_META).map(([key, meta]) => [key, t(meta.labelKey)]))
    );
    const workflowData = toChartData(
      normalizeEnumMap(vulnStats?.by_workflow_status),
      Object.fromEntries(Object.entries(WORKFLOW_KEYS).map(([key, labelKey]) => [key, t(labelKey)]))
    );
    if (severityData.length === 0 && workflowData.length === 0) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={t('dashboard.emptyVulns')}
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
              <Bar dataKey="value" name={t('dashboard.seriesVulns')}>
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
              <Bar dataKey="value" name={t('dashboard.seriesVulns')} fill="#2f54eb" />
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
                  {t('dashboard.cardAssets')}
                  <Tag color={statsScopeTagColor(scope.tone)}>{scopeLabel}</Tag>
                </Space>
              }
              value={assetStats?.total ?? 0}
              prefix={<AppstoreOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
            <Space size={4} wrap style={{ marginTop: 8 }}>
              {Object.entries(assetStats?.by_status ?? {}).map(([key, value]) => (
                <Tag key={key} color={key === 'active' ? 'green' : 'default'}>
                  {t(ASSET_STATUS_KEYS[key] ?? 'dashboard.assetStatus.active')} {value}
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
                  {t('dashboard.cardVulnerabilities')}
                  <Tag color={statsScopeTagColor(scope.tone)}>{scopeLabel}</Tag>
                </Space>
              }
              value={vulnAccessBlocked || vulnUnavailable ? '-' : vulnStats?.total ?? 0}
              prefix={<SafetyCertificateOutlined />}
              valueStyle={{ color: '#cf1322' }}
            />
            {vulnAccessBlocked ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {t('dashboard.managerOnly', { role: scope.roleLabel })}
              </Text>
            ) : vulnUnavailable ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {t('dashboard.statsLoadFailed')}
              </Text>
            ) : (
              <Space size={4} wrap style={{ marginTop: 8 }}>
                {Object.entries(normalizeEnumMap(vulnStats?.by_severity) ?? {}).map(([key, value]) => (
                  <Tag key={key} color={SEVERITY_META[key]?.color ?? 'default'}>
                    {SEVERITY_META[key] ? t(SEVERITY_META[key].labelKey) : key} {value}
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
              title={t('dashboard.cardMyTasks')}
              value={
                Object.values(workload?.tasks ?? {}).reduce((sum, value) => sum + (Number(value) || 0), 0)
              }
              prefix={<CheckSquareOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
            <Space size={4} wrap style={{ marginTop: 8 }}>
              <Tag color="orange">{t('dashboard.taskPending', { count: workload?.tasks?.pending ?? 0 })}</Tag>
              <Tag color="blue">{t('dashboard.taskInProgress', { count: workload?.tasks?.in_progress ?? 0 })}</Tag>
              <Tag color="green">{t('dashboard.taskCompleted', { count: workload?.tasks?.completed ?? 0 })}</Tag>
            </Space>
          </Card>
        );
      case 'kpi-claims':
        return (
          <Card loading={loading} variant="borderless">
            <Statistic
              title={t('dashboard.cardMyClaims')}
              value={Object.values(workload?.claims ?? {}).reduce((sum, value) => sum + (Number(value) || 0), 0)}
              prefix={<TrophyOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
            <Space size={4} wrap style={{ marginTop: 8 }}>
              <Tag color="orange">{t('dashboard.claimPending', { count: workload?.claims?.pending ?? 0 })}</Tag>
              <Tag color="blue">{t('dashboard.claimApproved', { count: workload?.claims?.approved ?? 0 })}</Tag>
              <Tag color="green">{t('dashboard.claimCompleted', { count: workload?.claims?.completed ?? 0 })}</Tag>
            </Space>
          </Card>
        );
      case 'chart-trends':
        return (
          <Card title={t('dashboard.cardTrends')} variant="borderless">
            <TrendChart days={trendDays} onDaysChange={setTrendDays} />
          </Card>
        );
      case 'chart-asset-type':
        return (
          <Card
            title={
              <Space size={6}>
                {t('dashboard.cardAssetDistribution')}
                <Tag color={statsScopeTagColor(scope.tone)}>{scopeLabel}</Tag>
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
                {t('dashboard.cardVulnDistribution')}
                <Tag color={statsScopeTagColor(scope.tone)}>{scopeLabel}</Tag>
              </Space>
            }
            variant="borderless"
          >
            {renderVulnChart()}
          </Card>
        );
      case 'chart-customer-dist':
        return (
          <Card
            title={
              <Space size={6}>
                {t('dashboard.card.customerDistribution')}
                <Tag color={statsScopeTagColor(scope.tone)}>{scopeLabel}</Tag>
              </Space>
            }
            variant="borderless"
          >
            <CustomerDistributionCard scopeLabel={scopeLabel} scopeTone={scope.tone} />
          </Card>
        );
      case 'chart-project-dist':
        return (
          <Card
            title={
              <Space size={6}>
                {t('dashboard.card.projectDistribution')}
                <Tag color={statsScopeTagColor(scope.tone)}>{scopeLabel}</Tag>
              </Space>
            }
            variant="borderless"
          >
            <ProjectDistributionCard scopeLabel={scopeLabel} scopeTone={scope.tone} />
          </Card>
        );
      case 'rank-workload':
        return (
          <Card title={t('dashboard.cardWorkloadRank')} variant="borderless">
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
          {t('dashboard.title')}
        </Title>
        <Space wrap>
          {canExport ? (
            <Dropdown
              menu={{
                items: EXPORT_OPTIONS.map((option) => ({
                  key: option.key,
                  label: t(option.labelKey),
                  onClick: () => handleExport(option.key),
                })),
              }}
            >
              <Button icon={<DownloadOutlined />} loading={!!exportingType}>
                {t('dashboard.export')}
              </Button>
            </Dropdown>
          ) : null}
          <Button icon={<SettingOutlined />} onClick={handleOpenLayout}>
            {t('dashboard.layout')}
          </Button>
          <Button icon={<ReloadOutlined />} onClick={loadStats} loading={loading}>
            {t('dashboard.refresh')}
          </Button>
        </Space>
      </div>

      {refreshError ? <Alert type="warning" showIcon style={{ marginTop: 16 }} message={refreshError} /> : null}
      {!configLoaded ? <Alert type="info" showIcon style={{ marginTop: 16 }} message={t('dashboard.layoutLoading')} /> : null}

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
          <Empty description={t('dashboard.allCardsHidden')}>
            <Button type="primary" onClick={handleOpenLayout}>
              {t('dashboard.goSettings')}
            </Button>
          </Empty>
        </Card>
      ) : null}

      <Text type="secondary" style={{ display: 'block', marginTop: 16, fontSize: 12 }}>
        {t('dashboard.footerSources')}；{t('dashboard.footerScope', { scope: scopeLabel })}
        {scope.tone === 'full' ? t('dashboard.footerScopeFull') : t('dashboard.footerScopeScoped')}
        ；{t('dashboard.footerLayout')}
        {canExport ? t('dashboard.footerExportAllowed') : t('dashboard.footerExportDenied')}
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
