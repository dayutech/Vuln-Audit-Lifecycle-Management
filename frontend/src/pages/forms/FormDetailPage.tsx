import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import {
  addInstanceField,
  assignFormSubmission,
  confirmForm,
  FORM_FIELD_TYPES,
  getFormSubmission,
  isInlineEditableFieldType,
  putFormOverrides,
  putFormValues,
  returnForm,
  setFormConfirmer,
  submitForm,
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
 * 表单详情（t182 发起人侧 → t183 补齐 5B 全部角色交互）。
 *
 * 角色 → 可做之事（后端仍是权威，前端只做位置与置灰）：
 * - 被指派人：填写值可编辑（`ASSIGNED` / `RETURNED` 时），保存（不校验必填）/ 提交（必填由后端校验）；
 * - 发起人 / 确认人 / ADMIN：覆盖值可编辑（**仅 SUBMITTED 之后**；只影响导出，不改业务数据）；
 * - 确认人：确认 / 退回（退回原因必填）。
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

/** 可填写状态：被指派人还能改 filledValue */
const FILLABLE_STATUSES = new Set(['ASSIGNED', 'RETURNED', 'DRAFT']);
/** 可覆盖状态：SUBMITTED 之后（含 CONFIRMED / RETURNED 之后的历史数据） */
const OVERRIDABLE_STATUSES = new Set(['SUBMITTED', 'CONFIRMED', 'RETURNED']);

const parseOptions = (raw?: string | null): string[] =>
  (raw ?? '')
    .split(/[\n,，]/)
    .map((item) => item.trim())
    .filter(Boolean);

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
  const [returnOpen, setReturnOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [returnNote, setReturnNote] = useState('');
  const [missingRequired, setMissingRequired] = useState<string[]>([]);
  const [valuesDraft, setValuesDraft] = useState<Record<string, string>>({});
  const [overrideDraft, setOverrideDraft] = useState<Record<string, string>>({});
  const [assignForm] = Form.useForm<{ assigneeId: string; note?: string }>();
  const [confirmerForm] = Form.useForm<{ confirmerId: string }>();
  const [fieldForm] = Form.useForm<{ label: string; fieldType: string; options?: string }>();
  const [currentUserId, setCurrentUserId] = useState<string | undefined>();
  const [isAdmin, setIsAdmin] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      setLoading(true);
      const payload = await getFormSubmission(id);
      setData(payload);
      // 回填草稿：填写值 / 覆盖值都从服务端当前值起步，避免"保存把别的字段清空"
      const nextValues: Record<string, string> = {};
      const nextOverrides: Record<string, string> = {};
      (payload.fields ?? []).forEach((row) => {
        const key = row.fieldId ?? row.id;
        nextValues[key] = row.filledValue ?? '';
        nextOverrides[key] = row.overrideValue ?? '';
      });
      setValuesDraft(nextValues);
      setOverrideDraft(nextOverrides);
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
        const roles = (profile as any)?.roles ?? [];
        setIsAdmin(Array.isArray(roles) && roles.some((role: string) => String(role).toLowerCase() === 'admin'));
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

  const isAssignee = useMemo(() => {
    if (!data) return false;
    const assignee = data.assignedTo ?? null;
    return Boolean(assignee && currentUserId && String(assignee) === String(currentUserId));
  }, [data, currentUserId]);

  const isConfirmer = useMemo(() => {
    if (!data) return false;
    const confirmer = (data as any).confirmerId ?? data.confirmedBy ?? null;
    return Boolean(confirmer && currentUserId && String(confirmer) === String(currentUserId));
  }, [data, currentUserId]);

  const status = String(data?.status ?? 'DRAFT').toUpperCase();
  const canFill = (isAssignee || isAdmin) && FILLABLE_STATUSES.has(status);
  const canOverride = (isInitiator || isConfirmer || isAdmin) && OVERRIDABLE_STATUSES.has(status);
  const canConfirm = (isConfirmer || isAdmin) && status === 'SUBMITTED';

  const fields = data?.fields ?? [];

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
      message.error(getApiErrorMessage(error, t('form.field.saveFailed')));
    } finally {
      setSaving(false);
    }
  };

  /* ---------------- 5B：填写 / 提交 ---------------- */
  const collectFilled = () =>
    fields
      .filter((row) => !(row as any).instanceField || isInlineEditableFieldType(row.fieldType))
      .map((row) => {
        const key = row.fieldId ?? row.id;
        return { fieldId: key, filledValue: valuesDraft[key] ?? '' };
      });

  const doSaveValues = async () => {
    if (!id) return;
    setSaving(true);
    try {
      await putFormValues(id, collectFilled());
      message.success(t('form.fill.saved'));
      setMissingRequired([]);
      await load();
    } catch (error) {
      message.error(getApiErrorMessage(error, t('form.fill.saveFailed')));
    } finally {
      setSaving(false);
    }
  };

  const doSubmit = async () => {
    if (!id) return;
    setSubmitting(true);
    try {
      // 先落一次填写值，保证"改了没保存就提交"也不会丢
      await putFormValues(id, collectFilled());
      await submitForm(id);
      message.success(t('form.fill.submitted'));
      setMissingRequired([]);
      await load();
    } catch (error) {
      const status403 = getApiErrorStatus(error);
      if (status403 === 400) {
        // 后端 400：data 里带缺失字段 label 列表（字段名不定，做多种兜底）
        const payload = (error as any)?.response?.data;
        const missing =
          payload?.data?.missingFields ?? payload?.data?.missing ?? payload?.missingFields ?? payload?.missing ?? [];
        const labels = Array.isArray(missing)
          ? missing.map((item: any) => (typeof item === 'string' ? item : item?.label ?? item?.fieldLabel ?? '')).filter(Boolean)
          : [];
        setMissingRequired(labels.length > 0 ? labels : [getApiErrorMessage(error, t('form.fill.missingFallback'))]);
        message.error(t('form.fill.missingRequired'));
      } else if (status403 === 409) {
        message.error(getApiErrorMessage(error, t('form.fill.alreadySubmitted')));
      } else {
        message.error(getApiErrorMessage(error, t('form.fill.submitFailed')));
      }
    } finally {
      setSubmitting(false);
    }
  };

  /* ---------------- 5B：覆盖值 ---------------- */
  const doSaveOverrides = async () => {
    if (!id) return;
    setSaving(true);
    try {
      await putFormOverrides(
        id,
        fields.map((row) => {
          const key = row.fieldId ?? row.id;
          const raw = overrideDraft[key] ?? '';
          return { fieldId: key, overrideValue: raw === '' ? null : raw };
        }),
      );
      message.success(t('form.override.saved'));
      await load();
    } catch (error) {
      const code = getApiErrorStatus(error);
      message.error(
        code === 409 ? t('form.override.notYet') : getApiErrorMessage(error, t('form.override.saveFailed')),
      );
    } finally {
      setSaving(false);
    }
  };

  const doClearOverride = async (row: FormValueVO) => {
    if (!id) return;
    const key = row.fieldId ?? row.id;
    setOverrideDraft((prev) => ({ ...prev, [key]: '' }));
    setSaving(true);
    try {
      await putFormOverrides(id, [{ fieldId: key, overrideValue: null }]);
      message.success(t('form.override.cleared'));
      await load();
    } catch (error) {
      message.error(getApiErrorMessage(error, t('form.override.clearFailed')));
    } finally {
      setSaving(false);
    }
  };

  /* ---------------- 5B：确认 / 退回 ---------------- */
  const doConfirm = () => {
    if (!id) return;
    Modal.confirm({
      title: t('form.confirm.title'),
      content: t('form.confirm.content'),
      okText: t('form.confirm.ok'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await confirmForm(id);
          message.success(t('form.confirm.success'));
          await load();
        } catch (error) {
          message.error(getApiErrorMessage(error, t('form.confirm.failed')));
        }
      },
    });
  };

  const doReturn = async () => {
    if (!id) return;
    const note = returnNote.trim();
    if (!note) {
      message.warning(t('form.return.noteRequired'));
      return;
    }
    if (note.length > 2000) {
      message.warning(t('form.return.noteTooLong'));
      return;
    }
    setSaving(true);
    try {
      await returnForm(id, note);
      message.success(t('form.return.success'));
      setReturnOpen(false);
      setReturnNote('');
      await load();
    } catch (error) {
      message.error(getApiErrorMessage(error, t('form.return.failed')));
    } finally {
      setSaving(false);
    }
  };

  /* ---------------- 字段编辑器（按类型渲染） ---------------- */
  const renderFilledEditor = (row: FormValueVO) => {
    const key = row.fieldId ?? row.id;
    const disabled = !canFill;
    const value = valuesDraft[key] ?? '';
    const onChange = (next: string) => setValuesDraft((prev) => ({ ...prev, [key]: next }));
    switch (row.fieldType) {
      case 'TEXTAREA':
        return (
          <Input.TextArea
            rows={2}
            disabled={disabled}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            data-testid={`form-value-input-${key}`}
          />
        );
      case 'NUMBER':
        return (
          <InputNumber
            style={{ width: '100%' }}
            disabled={disabled}
            value={value === '' ? null : Number(value)}
            onChange={(next) => onChange(next === null || next === undefined ? '' : String(next))}
            data-testid={`form-value-input-${key}`}
          />
        );
      case 'DATE':
        return (
          <Input
            placeholder="YYYY-MM-DD"
            disabled={disabled}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            data-testid={`form-value-input-${key}`}
          />
        );
      case 'SELECT': {
        const options = parseOptions(row.options).map((item) => ({ value: item, label: item }));
        return (
          <Select
            style={{ width: '100%' }}
            allowClear
            disabled={disabled}
            value={value || undefined}
            options={options}
            onChange={(next) => onChange(next ?? '')}
            data-testid={`form-value-input-${key}`}
          />
        );
      }
      case 'CHECKBOX':
        return (
          <Checkbox
            disabled={disabled}
            checked={value === 'true'}
            onChange={(event) => onChange(event.target.checked ? 'true' : 'false')}
            data-testid={`form-value-input-${key}`}
          />
        );
      case 'FILE':
        return (
          <Tooltip title={t('form.fill.fileHint')}>
            <span style={{ color: '#8c8c8c' }} data-testid={`form-value-file-hint-${key}`}>
              {t('form.fill.filePlaceholder')}
            </span>
          </Tooltip>
        );
      default:
        return (
          <Input
            disabled={disabled}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            data-testid={`form-value-input-${key}`}
          />
        );
    }
  };

  const renderOverrideEditor = (row: FormValueVO) => {
    const key = row.fieldId ?? row.id;
    const disabled = !canOverride;
    return (
      <Space size={4} style={{ width: '100%' }}>
        <Input
          style={{ width: 130 }}
          disabled={disabled}
          value={overrideDraft[key] ?? ''}
          onChange={(event) => setOverrideDraft((prev) => ({ ...prev, [key]: event.target.value }))}
          data-testid={`form-override-input-${key}`}
        />
        <Button
          size="small"
          type="link"
          disabled={disabled}
          onClick={() => void doClearOverride(row)}
          data-testid={`form-override-clear-${key}`}
        >
          {t('form.override.clear')}
        </Button>
      </Space>
    );
  };

  const columns = [
    { title: t('form.field.label'), dataIndex: 'label', key: 'label', width: 180 },
    {
      title: t('form.field.type'),
      dataIndex: 'fieldType',
      key: 'fieldType',
      width: 130,
      render: (value: string, row: FormValueVO) => (
        <Space size={4}>
          <span>{t(`form.type.${value}` as never)}</span>
          {row.required ? <Tag color="red">{t('form.field.required')}</Tag> : null}
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
      width: 150,
      render: (value: string | null) => <span style={{ color: '#8c8c8c' }}>{value ?? '-'}</span>,
    },
    {
      title: t('form.field.filled'),
      key: 'filledValue',
      width: 220,
      render: (_: unknown, row: FormValueVO) => renderFilledEditor(row),
    },
    {
      title: (
        <Tooltip title={t('form.override.hint')}>
          <span>{t('form.field.override')}</span>
        </Tooltip>
      ),
      key: 'overrideValue',
      width: 250,
      render: (_: unknown, row: FormValueVO) => renderOverrideEditor(row),
    },
    {
      title: t('form.field.effective'),
      key: 'effectiveValue',
      width: 160,
      render: (_: unknown, row: FormValueVO) => (
        <strong data-testid={`form-effective-${row.fieldId ?? row.id}`}>{row.effectiveValue ?? '-'}</strong>
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
            {/* 5B：被指派人保存（草稿不校验必填） */}
            <Button onClick={() => void doSaveValues()} loading={saving} disabled={!canFill} data-testid="form-save-values">
              {t('form.fill.save')}
            </Button>
            <Button
              type="primary"
              onClick={() => void doSubmit()}
              loading={submitting}
              disabled={!canFill}
              data-testid="form-submit"
            >
              {t('form.fill.submit')}
            </Button>
            {/* 5B：发起人 / 确认人 / ADMIN 覆盖值 */}
            <Tooltip title={canOverride ? t('form.override.hint') : t('form.override.notYet')}>
              <Button onClick={() => void doSaveOverrides()} loading={saving} disabled={!canOverride} data-testid="form-save-overrides">
                {t('form.override.save')}
              </Button>
            </Tooltip>
            {/* 5B：确认人确认 / 退回 */}
            <Button type="primary" onClick={() => doConfirm()} disabled={!canConfirm} data-testid="form-confirm">
              {t('form.detail.confirm')}
            </Button>
            <Button danger disabled={!canConfirm} onClick={() => setReturnOpen(true)} data-testid="form-return">
              {t('form.return.title')}
            </Button>
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
        {/* 退回原因：状态 RETURNED 必须显眼展示，被指派人据此重填 */}
        {status === 'RETURNED' ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message={t('form.return.banner', { note: data?.note ?? '-' })}
            data-testid="form-return-reason"
          />
        ) : data?.note ? (
          <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={`${t('form.detail.note')}: ${data.note}`} data-testid="form-assign-note" />
        ) : null}
        {/* 覆盖值口径必须明示 */}
        {canOverride ? (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message={t('form.override.banner')}
            data-testid="form-override-banner"
          />
        ) : null}
        {missingRequired.length > 0 ? (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 12 }}
            message={t('form.fill.missingRequired')}
            description={missingRequired.join('、')}
            data-testid="form-missing-required"
          />
        ) : null}
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
              <Tag color={STATUS_COLOR[status] ?? 'default'} data-testid="form-status">
                {t(`form.status.${status}` as never)}
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
            dataSource={fields}
            columns={columns}
            pagination={false}
            scroll={{ x: 1300 }}
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
        open={returnOpen}
        title={t('form.return.title')}
        onCancel={() => setReturnOpen(false)}
        onOk={() => void doReturn()}
        confirmLoading={saving}
        okText={t('form.return.title')}
        okButtonProps={{ danger: true }}
        cancelText={t('common.cancel')}
        destroyOnHidden
        data-testid="form-return-modal"
      >
        <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={t('form.return.hint')} />
        <Input.TextArea
          rows={3}
          maxLength={2000}
          showCount
          value={returnNote}
          onChange={(event) => setReturnNote(event.target.value)}
          placeholder={t('form.return.notePlaceholder')}
          data-testid="form-return-note"
        />
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

