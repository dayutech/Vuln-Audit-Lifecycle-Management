import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Select, Space, Table, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import FormExportButton from '@/components/forms/FormExportButton';
import { listAssignedFormSubmissions, type FormSubmissionSummaryVO } from '@/api/forms';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { formatDateTime } from '@/utils/datetime';
import { useT } from '@/i18n';

/**
 * 待我处理（t183）：`GET /forms/submissions/assigned?status=&page=&size=`
 *
 * 后端语义：`assignedTo = 我`（含退回后待重填的 RETURNED），并带 `note`（退回原因）。
 * 空态区分「暂无待办」。
 */
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ASSIGNED: 'blue',
  SUBMITTED: 'gold',
  CONFIRMED: 'green',
  RETURNED: 'red',
};

const STATUS_OPTIONS = ['ASSIGNED', 'RETURNED', 'SUBMITTED', 'CONFIRMED'] as const;

const FormAssignedPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [rows, setRows] = useState<FormSubmissionSummaryVO[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [params, setParams] = useState<{ page: number; size: number; status?: string }>({ page: 0, size: 10 });

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const page = await listAssignedFormSubmissions(params);
      setRows(page.content);
      setTotal(page.totalElements);
    } catch (error) {
      const code = getApiErrorStatus(error);
      setRows([]);
      setTotal(0);
      setLoadError(code === 403 ? t('form.assigned.forbidden') : getApiErrorMessage(error, t('form.assigned.loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [params, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns = [
    {
      title: t('form.mine.colDefinition'),
      dataIndex: 'definitionName',
      key: 'definitionName',
      render: (value: string, row: FormSubmissionSummaryVO) => (
        <a onClick={() => navigate(`/forms/${row.id}`)} data-testid={`form-assigned-open-${row.id}`}>
          {value}
        </a>
      ),
    },
    {
      title: t('form.mine.colSubject'),
      key: 'subject',
      render: (_: unknown, row: FormSubmissionSummaryVO) => (
        <Space size={6}>
          <Tag>{t(`form.subject.${row.subjectType}` as never)}</Tag>
          <span>{row.subjectName ?? row.subjectId}</span>
        </Space>
      ),
    },
    {
      title: t('form.mine.colStatus'),
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (value: string) => (
        <Tag color={STATUS_COLOR[value] ?? 'default'} data-testid={`form-assigned-status-${value}`}>
          {t(`form.status.${value}` as never)}
        </Tag>
      ),
    },
    {
      title: t('form.mine.colProgress'),
      key: 'progress',
      width: 110,
      render: (_: unknown, row: FormSubmissionSummaryVO) => `${row.filledCount}/${row.fieldCount}`,
    },
    {
      title: t('form.assigned.colReturnReason'),
      key: 'note',
      render: (_: unknown, row: FormSubmissionSummaryVO) =>
        String(row.status).toUpperCase() === 'RETURNED' && row.note ? (
          <span style={{ color: '#cf1322' }} data-testid={`form-assigned-note-${row.id}`}>
            {row.note}
          </span>
        ) : (
          '-'
        ),
    },
    {
      title: t('form.mine.colCreatedAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (value: string | null) => (value ? formatDateTime(value) : '-'),
    },
  ];

  return (
    <>
      <PageHeader
        title={t('form.assigned.title')}
        subtitle={t('form.assigned.subtitle')}
        fallbackTo="/dashboard"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => void load()} data-testid="form-assigned-refresh" />
            <FormExportButton
              params={{ status: params.status }}
              fallbackName="assigned-forms"
              testIdPrefix="form-export"
            />
          </Space>
        }
      />
      <Card data-testid="form-assigned-list">
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            allowClear
            style={{ width: 180 }}
            placeholder={t('form.mine.filterStatus')}
            value={params.status}
            onChange={(value) =>
              setParams((prev) => ({ ...prev, status: value, page: 0 }))
            }
            options={STATUS_OPTIONS.map((value) => ({ value, label: t(`form.status.${value}` as never) }))}
            data-testid="form-assigned-filter-status"
          />
        </Space>
        {loadError ? (
          <div style={{ color: '#cf1322', marginBottom: 12 }} data-testid="form-assigned-error">
            {loadError}
          </div>
        ) : null}
        <Table
          rowKey="id"
          loading={loading}
          dataSource={rows}
          columns={columns}
          scroll={{ x: 1000 }}
          locale={{ emptyText: t('form.assigned.empty') }}
          pagination={{
            current: params.page + 1,
            pageSize: params.size,
            total,
            showSizeChanger: false,
            onChange: (page) => setParams((prev) => ({ ...prev, page: page - 1 })),
          }}
        />
      </Card>
    </>
  );
};

export default FormAssignedPage;

