import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Result,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { DownloadOutlined, ReloadOutlined, SearchOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { exportAuditLogs, getAuditLogDetail, getAuditLogList } from '@/api/audit';
import { getMyAccessProfile, isManagementProfile, type MyAccessProfile } from '@/api/permission';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { AuditLogQuery, AuditLogVO } from '@/types/audit';
import { AUDIT_ACTION_HINT_KEY, AUDIT_RESOURCE_TYPE_KEYS } from '@/types/audit';
import { useT } from '@/i18n';
import { formatDateTime } from '@/utils/datetime';
import { getApiErrorStatus, getBlobErrorMessage } from '@/utils/errorMessage';
import { saveBlobAsFile } from '@/utils/download';
import { useNavigate } from 'react-router-dom';

const { Text, Paragraph } = Typography;
const { RangePicker } = DatePicker;

const PAGE_SIZE = 20;

interface FilterFormValues {
  range?: [Dayjs | null, Dayjs | null] | null;
  action?: string;
  resourceType?: string;
  keyword?: string;
}

/** 表单值 → 请求参数（时间转 ISO-8601，后端用 LocalDateTime 解析） */
const toQuery = (values: FilterFormValues): Omit<AuditLogQuery, 'page' | 'page_size'> => {
  const [start, end] = values.range ?? [null, null];
  return {
    start_time: start ? start.format('YYYY-MM-DDTHH:mm:ss') : undefined,
    end_time: end ? end.format('YYYY-MM-DDTHH:mm:ss') : undefined,
    action: values.action?.trim() || undefined,
    resource_type: values.resourceType || undefined,
    keyword: values.keyword?.trim() || undefined,
  };
};

const shortId = (value?: string | null) => (value ? `${value.slice(0, 8)}…` : '-');

const prettyDetails = (value?: string | null) => {
  if (!value) return '-';
  try {
    const parsed = JSON.parse(value);
    return JSON.stringify(parsed, null, 2);
  } catch {
    return value;
  }
};

/**
 * 审计日志页面（/audit-logs）。
 *
 * 仅管理层（admin / project_leader）可用：后端类级 @PreAuthorize 会返回 403，
 * 页面捕获 403 后展示无权限提示（而不是白屏），并在提示里给出当前账号角色。
 */
const AuditLogs: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm<FilterFormValues>();
  const [rows, setRows] = useState<AuditLogVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const [profile, setProfile] = useState<MyAccessProfile | null>(null);
  const [applied, setApplied] = useState<Omit<AuditLogQuery, 'page' | 'page_size'>>({});
  const [userNames, setUserNames] = useState<Record<string, string>>({});
  const [observedTypes, setObservedTypes] = useState<string[]>([]);
  const [detail, setDetail] = useState<AuditLogVO | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadUserNameMap = useCallback(async () => {
    try {
      // 用户目录（仅 id/username/fullName）：工程师也可读，避免 GET /users 的 403
      const list = await getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT });
      const map: Record<string, string> = {};
      list.forEach((user) => {
        if (!user?.id) return;
        map[String(user.id)] = user.fullName || user.username || String(user.id);
      });
      setUserNames(map);
    } catch {
      // 拿不到用户目录时退回展示用户 ID，不影响审计日志本身
      setUserNames({});
    }
  }, []);

  const load = useCallback(
    async (targetPage: number, query: Omit<AuditLogQuery, 'page' | 'page_size'>) => {
      setLoading(true);
      try {
        const data = await getAuditLogList({ ...query, page: targetPage, page_size: PAGE_SIZE });
        setRows(data.content);
        setTotal(data.totalElements);
        setForbidden(false);
        setObservedTypes((prev) => {
          const next = new Set(prev);
          data.content.forEach((item) => item.resourceType && next.add(item.resourceType));
          return Array.from(next).slice(0, 50);
        });
      } catch (error) {
        const status = getApiErrorStatus(error);
        if (status === 403) {
          setForbidden(true);
          setRows([]);
          setTotal(0);
        } else {
          message.error(await getBlobErrorMessage(error, t('audit.loadFailed')));
        }
      } finally {
        setLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    getMyAccessProfile().then(setProfile);
    loadUserNameMap();
    load(1, {});
  }, [load, loadUserNameMap]);

  const handleSearch = async () => {
    const values = await form.validateFields();
    const query = toQuery(values);
    setApplied(query);
    setPage(1);
    load(1, query);
  };

  const handleReset = () => {
    form.resetFields();
    setApplied({});
    setPage(1);
    load(1, {});
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const blob = await exportAuditLogs(applied);
      if (!(blob instanceof Blob)) {
        message.error(t('audit.exportNotBlob'));
        return;
      }
      // 防御：少数代理会把错误 JSON 以 200 返回，按 content-type 分流而不是直接落盘
      if (blob.type.includes('json')) {
        const text = await blob.text();
        let detail = t('audit.exportFailed');
        try {
          detail = (JSON.parse(text) as { message?: string })?.message ?? detail;
        } catch {
          detail = text.slice(0, 160) || detail;
        }
        const hint = /参数格式错误/.test(detail) ? t('audit.exportNotDeployed') : '';
        message.error(`${detail}${hint}`);
        return;
      }
      if (blob.size === 0) {
        message.error(t('audit.exportEmpty'));
        return;
      }
      saveBlobAsFile(blob, 'audit-logs.csv');
      message.success(t('audit.exportSuccess', { size: (blob.size / 1024).toFixed(1) }));
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 403) {
        message.error(t('audit.exportForbidden'));
        return;
      }
      // 错误体是 Blob，全局拦截器读不到 message，这里解析后端原始提示
      const detail = await getBlobErrorMessage(error, t('audit.exportFailed'));
      const hint = /参数格式错误|未部署/.test(detail) ? t('audit.exportNotDeployed') : '';
      message.error(`${detail}${hint}`);
    } finally {
      setExporting(false);
    }
  };

  const openDetail = async (record: AuditLogVO) => {
    setDetail(record);
    setDetailLoading(true);
    try {
      const full = await getAuditLogDetail(record.id);
      if (full) setDetail(full);
    } catch {
      // 详情接口失败时沿用列表行数据
    } finally {
      setDetailLoading(false);
    }
  };

  const { t } = useT();

  /** 导出受后端 @PreAuthorize 限制（admin / project_leader）；未知画像时先放行，由 403 兜底 */
  const canExport = profile ? isManagementProfile(profile) : true;

  const resourceTypeOptions = useMemo(() => {
    const merged = new Map(AUDIT_RESOURCE_TYPE_KEYS.map((item) => [item.value, t(item.key)]));
    observedTypes.forEach((type) => {
      if (!merged.has(type)) merged.set(type, type);
    });
    return Array.from(merged.entries()).map(([value, label]) => ({ value, label }));
  }, [observedTypes, t]);

  const columns: ColumnsType<AuditLogVO> = useMemo(
    () => [
      {
        title: t('audit.col.time'),
        dataIndex: 'createdAt',
        key: 'createdAt',
        width: 180,
        render: (value?: string) => <Text style={{ whiteSpace: 'nowrap' }}>{formatDateTime(value)}</Text>,
      },
      {
        title: t('audit.col.action'),
        dataIndex: 'action',
        key: 'action',
        ellipsis: { showTitle: false },
        render: (value?: string) => (
          <Tooltip title={value} placement="topLeft">
            <span style={{ fontFamily: 'monospace' }}>{value || '-'}</span>
          </Tooltip>
        ),
      },
      {
        title: t('audit.col.resourceType'),
        dataIndex: 'resourceType',
        key: 'resourceType',
        width: 150,
        render: (value?: string) => (value ? <Tag color="geekblue">{value}</Tag> : '-'),
      },
      {
        title: t('audit.col.resourceId'),
        dataIndex: 'resourceId',
        key: 'resourceId',
        width: 120,
        render: (value?: string | null) =>
          value ? (
            <Tooltip title={value}>
              <Text type="secondary" style={{ fontFamily: 'monospace' }}>
                {shortId(value)}
              </Text>
            </Tooltip>
          ) : (
            '-'
          ),
      },
      {
        title: t('audit.col.user'),
        dataIndex: 'userId',
        key: 'userId',
        width: 130,
        render: (value?: string) => {
          const name = value ? userNames[value] : undefined;
          return name ? (
            <Space size={4}>
              <Text>{name}</Text>
              <Tooltip title={value}>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {shortId(value)}
                </Text>
              </Tooltip>
            </Space>
          ) : (
            <Tooltip title={value}>{shortId(value)}</Tooltip>
          );
        },
      },
      {
        title: t('audit.col.ip'),
        dataIndex: 'ipAddress',
        key: 'ipAddress',
        width: 130,
        render: (value?: string | null) => value || '-',
      },
      {
        title: t('audit.col.details'),
        dataIndex: 'details',
        key: 'details',
        ellipsis: { showTitle: false },
        render: (value: string | null | undefined, record) => (
          <Tooltip title={value || t('audit.noDetails')}>
            <a onClick={() => openDetail(record)}>{value || t('audit.view')}</a>
          </Tooltip>
        ),
      },
    ],
    // openDetail 只依赖 setState，userNames 变化时需要重算“用户”列
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [userNames]
  );

  if (forbidden) {
    return (
      <Result
        status="403"
        icon={<SafetyCertificateOutlined style={{ color: '#faad14' }} />}
        title={t('audit.forbiddenTitle')}
        subTitle={
          profile?.roles?.length
            ? t('audit.forbiddenWithRoles', { roles: profile.roles.join(' / ') })
            : t('audit.forbiddenNoRoles')
        }
        extra={
          <Space>
            <Button type="primary" onClick={() => navigate('/dashboard')}>
              {t('audit.backHome')}
            </Button>
            <Button onClick={() => load(1, applied)}>{t('audit.retry')}</Button>
          </Space>
        }
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          {t('audit.title')}
        </Typography.Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => load(page, applied)} loading={loading}>
            {t('audit.refresh')}
          </Button>
          <Tooltip title={canExport ? '' : t('audit.exportDisabledTip')}>
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={handleExport}
              loading={exporting}
              disabled={!canExport}
            >
              {t('audit.export')}
            </Button>
          </Tooltip>
        </Space>
      </div>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={t('audit.filterHintTitle')}
        description={
          <span>
            {t('audit.filterHintBody', { actionHint: t(AUDIT_ACTION_HINT_KEY) })}
          </span>
        }
      />

      <Card size="small" style={{ marginBottom: 16 }}>
        <Form form={form} layout="vertical" onFinish={handleSearch}>
          <Row gutter={12}>
            <Col xs={24} md={10} lg={9}>
              <Form.Item name="range" label={t('audit.filter.range')}>
                <RangePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm:ss" allowEmpty={[true, true]} />
              </Form.Item>
            </Col>
            <Col xs={24} md={7} lg={5}>
              <Form.Item name="resourceType" label={t('audit.filter.resourceType')}>
                <Select
                  allowClear
                  showSearch
                  placeholder={t('audit.filter.resourceTypePlaceholder')}
                  options={resourceTypeOptions}
                  optionFilterProp="label"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={7} lg={4}>
              <Form.Item name="action" label={t('audit.filter.action')}>
                <Input allowClear placeholder={t('audit.filter.actionPlaceholder')} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} lg={6}>
              <Form.Item name="keyword" label={t('audit.filter.keyword')}>
                <Input allowClear placeholder={t('audit.filter.keywordPlaceholder')} />
              </Form.Item>
            </Col>
          </Row>
          <Space>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
              {t('audit.filter.search')}
            </Button>
            <Button onClick={handleReset}>{t('audit.filter.reset')}</Button>
            <Text type="secondary">{t('audit.total', { count: total })}</Text>
          </Space>
        </Form>
      </Card>

      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        locale={{
          emptyText: (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <span>
                  {t('audit.emptyTitle')}
                  <br />
                  {t('audit.emptyRetention')}
                </span>
              }
            />
          ),
        }}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          showTotal: (count) => t('audit.total', { count }),
          onChange: (next) => {
            setPage(next);
            load(next, applied);
          },
        }}
      />

      <Drawer
        open={!!detail}
        onClose={() => setDetail(null)}
        width={620}
        title={t('audit.detailTitle')}
        loading={detailLoading}
      >
        {detail ? (
          <div>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label={t('audit.col.time')}>{formatDateTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label={t('audit.col.action')}>
                <Text code>{detail.action || '-'}</Text>
              </Descriptions.Item>
              <Descriptions.Item label={t('audit.col.resourceType')}>{detail.resourceType || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('audit.col.resourceId')}>{detail.resourceId || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('audit.col.user')}>
                {userNames[detail.userId] ? `${userNames[detail.userId]}（${detail.userId}）` : detail.userId || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('audit.col.ip')}>{detail.ipAddress || '-'}</Descriptions.Item>
              <Descriptions.Item label="User-Agent">{detail.userAgent || '-'}</Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{ marginTop: 16 }}>
              {t('audit.detail.details')}
            </Typography.Title>
            <Paragraph>
              <pre
                style={{
                  margin: 0,
                  padding: 12,
                  background: '#fafafa',
                  borderRadius: 6,
                  maxHeight: 320,
                  overflow: 'auto',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                }}
              >
                {prettyDetails(detail.details)}
              </pre>
            </Paragraph>
          </div>
        ) : null}
      </Drawer>
    </div>
  );
};

export default AuditLogs;
