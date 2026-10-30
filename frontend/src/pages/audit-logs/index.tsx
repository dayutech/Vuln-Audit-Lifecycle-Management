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
import { AUDIT_ACTION_HINT, AUDIT_RESOURCE_TYPE_OPTIONS } from '@/types/audit';
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
          message.error(await getBlobErrorMessage(error, '审计日志加载失败，请稍后重试'));
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
        const hint = /参数格式错误/.test(detail) ? '（后端可能尚未部署 /audit-logs/export）' : '';
        message.error(`${detail}${hint}`);
        return;
      }
      if (blob.size === 0) {
        message.error('导出文件为空，请检查服务端导出实现');
        return;
      }
      saveBlobAsFile(blob, 'audit-logs.csv');
      message.success(`已导出 audit-logs.csv（${(blob.size / 1024).toFixed(1)} KB）`);
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 403) {
        message.error('当前账号无导出权限（仅 admin / 项目负责人可导出）');
        return;
      }
      // 错误体是 Blob，全局拦截器读不到 message，这里解析后端原始提示
      const detail = await getBlobErrorMessage(error, '导出失败');
      const hint = /参数格式错误|未部署/.test(detail) ? '（后端可能尚未部署 /audit-logs/export）' : '';
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

  /** 导出受后端 @PreAuthorize 限制（admin / project_leader）；未知画像时先放行，由 403 兜底 */
  const canExport = profile ? isManagementProfile(profile) : true;

  const resourceTypeOptions = useMemo(() => {
    const merged = new Map(AUDIT_RESOURCE_TYPE_OPTIONS.map((item) => [item.value, item.label]));
    observedTypes.forEach((type) => {
      if (!merged.has(type)) merged.set(type, type);
    });
    return Array.from(merged.entries()).map(([value, label]) => ({ value, label }));
  }, [observedTypes]);

  const columns: ColumnsType<AuditLogVO> = useMemo(
    () => [
      {
        title: '时间',
        dataIndex: 'createdAt',
        key: 'createdAt',
        width: 180,
        render: (value?: string) => <Text style={{ whiteSpace: 'nowrap' }}>{formatDateTime(value)}</Text>,
      },
      {
        title: '操作',
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
        title: '资源类型',
        dataIndex: 'resourceType',
        key: 'resourceType',
        width: 150,
        render: (value?: string) => (value ? <Tag color="geekblue">{value}</Tag> : '-'),
      },
      {
        title: '资源ID',
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
        title: '用户',
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
        title: 'IP',
        dataIndex: 'ipAddress',
        key: 'ipAddress',
        width: 130,
        render: (value?: string | null) => value || '-',
      },
      {
        title: '详情',
        dataIndex: 'details',
        key: 'details',
        ellipsis: { showTitle: false },
        render: (value: string | null | undefined, record) => (
          <Tooltip title={value || '无详情'}>
            <a onClick={() => openDetail(record)}>{value || '查看'}</a>
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
        title="无权限访问审计日志"
        subTitle={
          profile?.roles?.length
            ? `当前账号角色：${profile.roles.join('、')}；审计日志仅管理员（admin）与项目负责人（project_leader）可查看。`
            : '审计日志仅管理员（admin）与项目负责人（project_leader）可查看，如需访问请联系管理员授权。'
        }
        extra={
          <Space>
            <Button type="primary" onClick={() => navigate('/dashboard')}>
              返回大盘
            </Button>
            <Button onClick={() => load(1, applied)}>重试</Button>
          </Space>
        }
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          审计日志
        </Typography.Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => load(page, applied)} loading={loading}>
            刷新
          </Button>
          <Tooltip title={canExport ? '' : '仅管理员 / 项目负责人可导出 CSV'}>
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={handleExport}
              loading={exporting}
              disabled={!canExport}
            >
              导出 CSV
            </Button>
          </Tooltip>
        </Space>
      </div>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="筛选说明"
        description={
          <span>
            时间范围为闭区间（ISO-8601）；关键词对「操作 / 资源类型 / 详情」做模糊匹配。{AUDIT_ACTION_HINT}
          </span>
        }
      />

      <Card size="small" style={{ marginBottom: 16 }}>
        <Form form={form} layout="vertical" onFinish={handleSearch}>
          <Row gutter={12}>
            <Col xs={24} md={10} lg={9}>
              <Form.Item name="range" label="时间范围">
                <RangePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm:ss" allowEmpty={[true, true]} />
              </Form.Item>
            </Col>
            <Col xs={24} md={7} lg={5}>
              <Form.Item name="resourceType" label="资源类型">
                <Select
                  allowClear
                  showSearch
                  placeholder="全部资源类型"
                  options={resourceTypeOptions}
                  optionFilterProp="label"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={7} lg={4}>
              <Form.Item name="action" label="操作（等值）">
                <Input allowClear placeholder="如 DELETE TeamController.deleteTeam" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} lg={6}>
              <Form.Item name="keyword" label="关键词">
                <Input allowClear placeholder="匹配操作/资源类型/详情" />
              </Form.Item>
            </Col>
          </Row>
          <Space>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
            <Text type="secondary">共 {total} 条</Text>
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
                  当前筛选条件下没有审计记录；清空条件或放宽时间范围后再试。
                  <br />
                  注：超过保留期（默认 180 天）的日志由后端每日 03:30 自动清理，属于预期行为，不代表数据丢失。
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
          showTotal: (count) => `共 ${count} 条`,
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
        title="审计日志详情"
        loading={detailLoading}
      >
        {detail ? (
          <div>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="时间">{formatDateTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="操作">
                <Text code>{detail.action || '-'}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="资源类型">{detail.resourceType || '-'}</Descriptions.Item>
              <Descriptions.Item label="资源ID">{detail.resourceId || '-'}</Descriptions.Item>
              <Descriptions.Item label="用户">
                {userNames[detail.userId] ? `${userNames[detail.userId]}（${detail.userId}）` : detail.userId || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="IP 地址">{detail.ipAddress || '-'}</Descriptions.Item>
              <Descriptions.Item label="User-Agent">{detail.userAgent || '-'}</Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{ marginTop: 16 }}>
              详情
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
