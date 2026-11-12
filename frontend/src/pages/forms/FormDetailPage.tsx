import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Descriptions, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import {
  addInstanceField,
  assignFormSubmission,
  FORM_FIELD_TYPES,
  getFormSubmission,
  setFormConfirmer,
  type FormSubmissionVO,
  type FormValueVO,
} from '@/api/forms';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import { getMyAccessProfile } from '@/api/permission';
import { resolveCurrentUserId } from '@/utils/accessRoles';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { formatDateTime } from '@/utils/datetime';
import { useT } from '@/i18n';

/**
 * 表单详情（t182，发起人侧）。
 *
 * 已实现：指派处理人、加实例字段、设确认人。
 * **TODO(5B)**：填写 / 提交（被指派人）、确认 / 退回（确认人）、覆盖值——本块只渲染禁用占位。
 */
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ASSIGNED: 'blue',
  SUBMITTED: 'gold',
  CONFIRMED: 'green',
  RETURNED: 'red',
};

const SUBJECT_PATH: Record<string, (id: string) => string> = {
  CUSTOMER: (id) => `/customers/${id}`,
  PROJECT: (id) => `/projects/${id}`,
  ASSET: (id) => `/assets/${id}`,
};

const FormDetailPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  /** 从「生成表单」跳过来时带的快照统计（t182：跳详情后仍要显示提示） */
  const snapshotNotice = (location.state ?? null) as {
    snapshotFilled: number;
    snapshotNull: number;
    skippedSources?: string[];
  } | null;

  const [data, setData] = useState<FormSubmissionVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [users, setUsers] = useState<Array<{ value: string; label: string }>>([]);
  const [assignOpen, setAssignOpen] = useState(false);
  const [confirmerOpen, setConfirmerOpen] = useState(false);
  const [fieldOpen, setFieldOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [assignForm] = Form.useForm<{ assigneeId: string; note?: string }>();
  const [confirmerForm] = Form.useForm<{ confirmerId: string }>();
  const [fieldForm] = Form.useForm<{ label: string; fieldType: string; options?: string }>();
  const [currentUserId, setCurrentUserId] = useState<string | undefined>();

  const load = useCallback(async () => {
    if (!id) return;
    try {
      setLoading(true);
      setData(await getFormSubmission(id));
    } catch (error) {
      message.error(getApiErrorMessage(error, t('form.detail.loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [id, t]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [list, profile] = await Promise.all([
          getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT }).catch(() => []),
          getMyAccessProfile().catch(() => null),
        ]);
        if (cancelled) return;
        setUsers((Array.isArray(list) ? list : []).map((row: any) => ({ value: row.id, label: row.fullName || row.username })));
        setCurrentUserId(resolveCurrentUserId(profile as any));
      } catch {
        // 下拉失败不阻断详情
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const isInitiator = useMemo(() => {
    if (!data) return false;
    const owner = data.createdBy ?? null;
    return Boolean(owner && currentUserId && String(owner) === String(currentUserId));
  }, [data, currentUserId]);

  const doAssign = async () => {
    if (!id) return;
    try {
      const values = await assignForm.validateFields();
      setSaving(true);
      await assignFormSubmission(id, { assigneeId: values.assigneeId, note: values.note });
      message.success(t('form.detail.assigned'));
      setAssignOpen(false);
      await load();
    } catch (error: any) {
      if (Array.isArray(error?.errorFields)) return;
      message.error(getApiErrorMessage(error, t('form.detail.assignFailed')));
    } finally {
      setSaving(false);
    }
  };

  const doSetConfirmer = async () => {
    if (!id) return;
    try {
      const values = await confirmerForm.validateFields();
      setSaving(true);
      await setFormConfirmer(id, values.confirmerId);
      message.success(t('form.detail.confirmerSet'));
      setConfirmerOpen(false);
      await load();
    } catch (error: any) {
      if (Array.isArray(error?.errorFields)) return;
      message.error(getApiErrorMessage(error, t('form.detail.confirmerFailed')));
    } finally {
      setSaving(false);
    }
  };

  const doAddField = async () => {
    if (!id) return;
    try {
      const values = await fieldForm.validateFields();
      setSaving(true);
      await addInstanceField(id, {
        label: values.label,
        fieldType: values.fieldType,
        options: values.fieldType === 'SELECT' ? values.options ?? '' : undefined,
      });
      message.success(t('form.field.saved'));
      setFieldOpen(false);
      await load();
    } catch (error: any) {
      if (Array.isArray(error?.errorFields)) return;
      const status = getApiErrorStatus(error);
      message.error(
        status === 409 ? getApiErrorMessage(error, t('form.field.saveFailed')) : getApiErrorMessage(error, t('form.field.saveFailed')),
      );
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { title: t('form.field.label'), dataIndex: 'label', key: 'label', width: 200 },
    {
      title: t('form.field.type'),
      dataIndex: 'fieldType',
      key: 'fieldType',
      width: 110,
      render: (value: string, row: FormValueVO) => (
        <Space size={4}>
          <span>{t(`form.type.${value}` as never)}</span>
          {row.instanceField ? <Tag color="purple">{t('form.field.instanceBadge')}</Tag> : null}
        </Space>
      ),
    },
    {
      title: (
        <Tooltip title={t('form.field.snapshotHint')}>
          <span>{t('form.field.snapshot')}</span>
        </Tooltip>
      ),
      dataIndex: 'sourceValue',
      key: 'sourceValue',
      width: 180,
      render: (value: string | null) => <span style={{ color: '#8c8c8c' }}>{value ?? '-'}</span>,
    },
    { title: t('form.field.filled'), dataIndex: 'filledValue', key: 'filledValue', width: 160, render: (v: string | null) => v ?? '-' },
    {
      title: t('form.field.override'),
      dataIndex: 'overrideValue',
      key: 'overrideValue',
      width: 160,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('form.field.effective'),
      key: 'effectiveValue',
      width: 180,
      render: (_: unknown, row: FormValueVO) => (
        <strong data-testid={`form-effective-${row.fieldId}`}>{row.effectiveValue ?? '-'}</strong>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title={data?.definitionName ?? t('form.detail.title')}
        subtitle={data?.title ?? data?.subjectName ?? undefined}
        fallbackTo="/forms/mine"
        onBack={() => navigate('/forms/mine')}
        extra={
          <Space wrap>
            <Button onClick={() => setAssignOpen(true)} disabled={!isInitiator} data-testid="form-assign">
              {t('form.detail.assign')}
            </Button>
            <Button onClick={() => setConfirmerOpen(true)} disabled={!isInitiator} data-testid="form-set-confirmer">
              {t('form.detail.setConfirmer')}
            </Button>
            <Button
              icon={<PlusOutlined />}
              onClick={() => {
                fieldForm.resetFields();
                fieldForm.setFieldsValue({ fieldType: 'TEXT' });
                setFieldOpen(true);
              }}
              disabled={!isInitiator}
              data-testid="form-instance-field-add"
            >
              {t('form.detail.addField')}
            </Button>
            {/* TODO(5B)：填写 / 提交 由被指派人在 5B 实现 */}
            <Tooltip title={t('form.detail.comingSoon')}>
              <Button disabled data-testid="form-fill-placeholder">
                {t('form.detail.fill')}
              </Button>
            </Tooltip>
            {/* TODO(5B)：确认 / 退回 由确认人在 5B 实现 */}
            <Tooltip title={t('form.detail.comingSoon')}>
              <Button disabled data-testid="form-confirm-placeholder">
                {t('form.detail.confirm')}
              </Button>
            </Tooltip>
          </Space>
        }
      />
      <div data-testid="form-detail">
        {snapshotNotice ? (
          <Alert
            type="success"
            showIcon
            style={{ marginBottom: 12 }}
            message={t('form.new.snapshotNotice', {
              filled: snapshotNotice.snapshotFilled,
              empty: snapshotNotice.snapshotNull,
            })}
            description={
              snapshotNotice.skippedSources && snapshotNotice.skippedSources.length > 0
                ? t('form.new.skippedSources', { list: snapshotNotice.skippedSources.join(', ') })
                : undefined
            }
            data-testid="form-snapshot-notice"
          />
        ) : null}
        {data?.note ? <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={`${t('form.detail.note')}: ${data.note}`} /> : null}
        <Card loading={loading}>
          <Descriptions size="small" column={3} style={{ marginBottom: 12 }}>
            <Descriptions.Item label={t('form.subject')}>
              {data?.subjectId ? (
                <a
                  onClick={() => navigate((SUBJECT_PATH[data.subjectType] ?? (() => '/dashboard'))(String(data.subjectId)))}
                  data-testid="form-subject-link"
                >
                  {data.subjectName ?? data.subjectId}
                </a>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('form.mine.colStatus')}>
              <Tag color={STATUS_COLOR[data?.status ?? ''] ?? 'default'} data-testid="form-status">
                {t(`form.status.${data?.status ?? 'DRAFT'}` as never)}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('form.mine.colProgress')}>
              <span data-testid="form-progress">
                {t('form.detail.progress', { filled: data?.filledCount ?? 0, total: data?.fieldCount ?? 0 })}
              </span>
            </Descriptions.Item>
            <Descriptions.Item label={t('form.detail.createdBy')}>{data?.createdByName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('form.detail.assignedTo')}>{data?.assignedToName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('form.detail.confirmedBy')}>
              {data?.confirmedBy ? users.find((user) => user.value === data.confirmedBy)?.label ?? data.confirmedBy : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('form.mine.colCreatedAt')}>
              {data?.createdAt ? formatDateTime(data.createdAt) : '-'}
            </Descriptions.Item>
          </Descriptions>

          <Table
            rowKey={(row) => row.fieldId ?? row.id}
            size="small"
            dataSource={data?.fields ?? []}
            columns={columns}
            pagination={false}
            scroll={{ x: 1100 }}
            data-testid="form-field-values"
          />
        </Card>
      </div>

      <Modal
        open={assignOpen}
        title={t('form.detail.assignTitle')}
        onCancel={() => setAssignOpen(false)}
        onOk={() => void doAssign()}
        confirmLoading={saving}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        destroyOnHidden
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item name="assigneeId" label={t('form.detail.assignee')} rules={[{ required: true, message: t('form.detail.assigneeRequired') }]}>
            <Select showSearch optionFilterProp="label" options={users} data-testid="form-assign-select" />
          </Form.Item>
          <Form.Item name="note" label={t('form.detail.assignNote')}>
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={confirmerOpen}
        title={t('form.detail.confirmerTitle')}
        onCancel={() => setConfirmerOpen(false)}
        onOk={() => void doSetConfirmer()}
        confirmLoading={saving}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        destroyOnHidden
      >
        <Form form={confirmerForm} layout="vertical">
          <Form.Item name="confirmerId" label={t('form.detail.confirmer')} rules={[{ required: true, message: t('form.detail.confirmerRequired') }]}>
            <Select showSearch optionFilterProp="label" options={users} data-testid="form-confirmer-select" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={fieldOpen}
        title={t('form.detail.addFieldTitle')}
        onCancel={() => setFieldOpen(false)}
        onOk={() => void doAddField()}
        confirmLoading={saving}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        destroyOnHidden
      >
        <Form form={fieldForm} layout="vertical">
          <Form.Item name="label" label={t('form.field.label')} rules={[{ required: true, message: t('form.field.label') }]}>
            <Input maxLength={200} data-testid="form-instance-field-label" />
          </Form.Item>
          <Form.Item name="fieldType" label={t('form.field.type')} rules={[{ required: true }]}>
            <Select
              options={FORM_FIELD_TYPES.filter((value) => value !== 'FILE').map((value) => ({
                value,
                label: t(`form.type.${value}` as never),
              }))}
              data-testid="form-instance-field-type"
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.fieldType !== next.fieldType}>
            {({ getFieldValue }) =>
              getFieldValue('fieldType') === 'SELECT' ? (
                <Form.Item
                  name="options"
                  label={t('form.field.options')}
                  rules={[{ required: true, message: t('form.field.optionsRequired') }]}
                >
                  <Input.TextArea rows={3} data-testid="form-instance-field-options" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default FormDetailPage;


