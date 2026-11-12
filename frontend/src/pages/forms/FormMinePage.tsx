import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Select, Space, Table, Tag, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { FORM_STATUSES, listMyFormSubmissions, type FormSubmissionSummaryVO } from '@/api/forms';
import FormExportButton from '@/components/forms/FormExportButton';
import { getApiErrorMessage } from '@/utils/errorMessage';
import { formatDateTime } from '@/utils/datetime';
import { useT } from '@/i18n';

/**
 * 我发起的表单（t182）。
 *
 * 契约：`GET /forms/submissions/mine?status=&page=&size=` —— **只返回自己发起的**，后端已按 createdAt desc。
 */
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ASSIGNED: 'blue',
  SUBMITTED: 'gold',
  CONFIRMED: 'green',
  RETURNED: 'red',
};

const FormMinePage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [rows, setRows] = useState<FormSubmissionSummaryVO[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<string | undefined>();
  const [params, setParams] = useState<{ page: number; size: number; status?: string }>({ page: 0, size: 10 });

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const page = await listMyFormSubmissions(params);
      setRows(page.content);
      setTotal(page.totalElements);
    } catch (error) {
      message.error(getApiErrorMessage(error, t('form.mine.loadFailed')));
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
        <a onClick={() => navigate(`/forms/${row.id}`)} data-testid={`form-mine-open-${row.id}`}>
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
      render: (value: string) => <Tag color={STATUS_COLOR[value] ?? 'default'}>{t(`form.status.${value}` as never)}</Tag>,
    },
    {
      title: t('form.mine.colProgress'),
      key: 'progress',
      width: 120,
      render: (_: unknown, row: FormSubmissionSummaryVO) => `${row.filledCount}/${row.fieldCount}`,
    },
    {
      title: t('form.mine.colAssignee'),
      key: 'assignedTo',
      width: 160,
      render: (_: unknown, row: FormSubmissionSummaryVO) => row.assignedToName ?? '-',
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
        title={t('form.mine.title')}
        subtitle={t('form.new.subtitle')}
        fallbackTo="/dashboard"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => void load()} data-testid="form-mine-refresh" />
            <FormExportButton params={{ status: params.status }} fallbackName="my-forms" testIdPrefix="form-export" />
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => navigate('/forms/new')}
              data-testid="form-mine-new"
            >
              {t('form.new.title')}
            </Button>
          </Space>
        }
      />
      <Card data-testid="form-mine-list">
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            allowClear
            style={{ width: 180 }}
            placeholder={t('form.mine.filterStatus')}
            value={status}
            onChange={(value) => {
              setStatus(value);
              setParams((prev) => ({ ...prev, status: value, page: 0 }));
            }}
            options={FORM_STATUSES.map((value) => ({ value, label: t(`form.status.${value}` as never) }))}
            data-testid="form-mine-filter-status"
          />
        </Space>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={rows}
          columns={columns}
          scroll={{ x: 1000 }}
          locale={{ emptyText: t('form.mine.empty') }}
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

export default FormMinePage;

