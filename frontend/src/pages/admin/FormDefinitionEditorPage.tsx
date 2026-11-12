import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { DeleteOutlined, DownOutlined, PlusOutlined, UpOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { FormGroup, PageFormShell } from '@/components/layout/PageFormShell';
import {
  FORM_FIELD_SOURCES,
  FORM_FIELD_TYPES,
  addFormField,
  createFormDefinition,
  deleteFormField,
  getFormDefinition,
  reorderFormFields,
  resetEnabledDefinitionsCache,
  updateFormDefinition,
  updateFormField,
  type FormDefinitionVO,
  type FormFieldVO,
} from '@/api/forms';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useT } from '@/i18n';

/**
 * 表单模板编辑页（t182）：整页表单 + 同页字段编辑器。
 *
 * - 新建：`/admin/form-definitions/new`；编辑：`/:id/edit`
 * - `code` 创建后不可改（后端 403），因此编辑态置灰并提示
 * - 字段顺序用「上移/下移」按钮调整，调 `PUT .../fields/reorder`（必须覆盖全部字段）
 * - 已有表单数据的字段后端会 409：类型/删除在 UI 上给出 Tooltip 说明（不做乐观拦截）
 */
const FormDefinitionEditorPage: React.FC = () => {
  const { t, lang } = useT();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);

  const [form] = Form.useForm<{ code: string; name: string; description?: string; enabled: boolean }>();
  const [fieldForm] = Form.useForm<{
    label: string;
    fieldType: string;
    required: boolean;
    sourceType?: string | null;
    options?: string;
  }>();
  const [definition, setDefinition] = useState<FormDefinitionVO | null>(null);
  const [fields, setFields] = useState<FormFieldVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [fieldModalOpen, setFieldModalOpen] = useState(false);
  const [editingField, setEditingField] = useState<FormFieldVO | null>(null);
  const [fieldSaving, setFieldSaving] = useState(false);
  const [reordering, setReordering] = useState(false);

  const sourceOptions = useMemo(
    () =>
      FORM_FIELD_SOURCES.map((source) => ({
        value: source.value,
        label: `${(lang ?? 'zh').startsWith('en') ? source.en : source.zh}（${source.value}）`,
      })),
    [lang],
  );

  const load = useCallback(async () => {
    if (!id) return;
    try {
      setLoading(true);
      const detail = await getFormDefinition(id);
      setDefinition(detail);
      setFields([...(detail.fields ?? [])].sort((a, b) => a.sortOrder - b.sortOrder));
      form.setFieldsValue({
        code: detail.code,
        name: detail.name,
        description: detail.description ?? undefined,
        enabled: detail.enabled,
      });
    } catch (error) {
      message.error(getApiErrorMessage(error, t('form.def.loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [id, form, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (isEdit && id) {
        await updateFormDefinition(id, { name: values.name, description: values.description, enabled: values.enabled });
        message.success(t('form.def.saved'));
        resetEnabledDefinitionsCache();
        await load();
      } else {
        const created = await createFormDefinition(values);
        message.success(t('form.def.created'));
        resetEnabledDefinitionsCache();
        navigate(`/admin/form-definitions/${created.id}/edit`);
      }
    } catch (error: any) {
      if (Array.isArray(error?.errorFields)) return;
      if (getApiErrorStatus(error) === 403) {
        message.error(t('form.def.forbidden'));
        return;
      }
      message.error(getApiErrorMessage(error, t('form.def.saveFailed')));
    } finally {
      setSubmitting(false);
    }
  };

  // ---------- 字段增删改 ----------
  const openAddField = () => {
    setEditingField(null);
    fieldForm.resetFields();
    fieldForm.setFieldsValue({ fieldType: 'TEXT', required: false, sourceType: null, options: '' });
    setFieldModalOpen(true);
  };

  const openEditField = (row: FormFieldVO) => {
    setEditingField(row);
    fieldForm.setFieldsValue({
      label: row.label,
      fieldType: row.fieldType,
      required: row.required,
      sourceType: row.sourceType ?? null,
      options: row.options ?? '',
    });
    setFieldModalOpen(true);
  };

  const saveField = async () => {
    if (!id) return;
    try {
      const values = await fieldForm.validateFields();
      setFieldSaving(true);
      const payload = {
        label: values.label,
        fieldType: values.fieldType,
        required: values.required,
        sourceType: values.sourceType || null,
        options: values.fieldType === 'SELECT' ? values.options ?? '' : null,
        sortOrder: editingField ? editingField.sortOrder : fields.length,
      };
      if (editingField) {
        await updateFormField(id, editingField.id, payload);
      } else {
        await addFormField(id, payload);
      }
      message.success(t('form.field.saved'));
      setFieldModalOpen(false);
      resetEnabledDefinitionsCache();
      await load();
    } catch (error: any) {
      if (Array.isArray(error?.errorFields)) return;
      if (getApiErrorStatus(error) === 409) {
        message.error(getApiErrorMessage(error, t('form.field.saveFailed')));
        return;
      }
      message.error(getApiErrorMessage(error, t('form.field.saveFailed')));
    } finally {
      setFieldSaving(false);
    }
  };

  const removeField = (row: FormFieldVO) => {
    if (!id) return;
    Modal.confirm({
      title: t('form.field.deleteTitle'),
      content: t('form.field.deleteConfirm').replace('{label}', row.label),
      okText: t('common.delete'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteFormField(id, row.id);
          message.success(t('form.field.deleted'));
          await load();
        } catch (error) {
          message.error(getApiErrorMessage(error, t('form.field.deleteFailed')));
        }
      },
    });
  };

  /** 上移/下移：重排后一次性提交全部 fieldIds（后端要求覆盖全量） */
  const move = async (index: number, delta: number) => {
    if (!id) return;
    const next = [...fields];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    const [item] = next.splice(index, 1);
    next.splice(target, 0, item);
    setFields(next);
    try {
      setReordering(true);
      const updated = await reorderFormFields(id, next.map((row) => row.id));
      setFields([...(updated.fields ?? next)].sort((a, b) => a.sortOrder - b.sortOrder));
    } catch (error) {
      message.error(getApiErrorMessage(error, t('form.field.reorderFailed')));
      await load();
    } finally {
      setReordering(false);
    }
  };

  const fieldColumns = [
    { title: '#', key: 'sort', width: 60, render: (_: unknown, __: FormFieldVO, index: number) => index + 1 },
    {
      title: t('form.field.label'),
      dataIndex: 'label',
      key: 'label',
      render: (value: string, row: FormFieldVO) => (
        <Space size={6}>
          <a onClick={() => openEditField(row)} data-testid={`form-field-edit-${row.id}`}>
            {value}
          </a>
          {row.required ? <Tag color="red">{t('form.field.required')}</Tag> : null}
        </Space>
      ),
    },
    { title: t('form.field.key'), dataIndex: 'fieldKey', key: 'fieldKey', width: 180 },
    {
      title: t('form.field.type'),
      dataIndex: 'fieldType',
      key: 'fieldType',
      width: 120,
      render: (value: string) => t(`form.type.${value}` as never),
    },
    {
      title: t('form.field.source'),
      key: 'source',
      width: 200,
      render: (_: unknown, row: FormFieldVO) =>
        row.sourceType ? <Tag color="blue">{row.sourceLabel ?? row.sourceType}</Tag> : '-',
    },
    {
      title: t('form.field.actions'),
      key: 'actions',
      width: 220,
      fixed: 'right' as const,
      render: (_: unknown, row: FormFieldVO, index: number) => (
        <Space size="small">
          <Button
            size="small"
            icon={<UpOutlined />}
            disabled={index === 0 || reordering}
            onClick={() => void move(index, -1)}
            data-testid={`form-field-reorder-up-${row.id}`}
            title={t('form.field.moveUp')}
          />
          <Button
            size="small"
            icon={<DownOutlined />}
            disabled={index === fields.length - 1 || reordering}
            onClick={() => void move(index, 1)}
            data-testid={`form-field-reorder-down-${row.id}`}
            title={t('form.field.moveDown')}
          />
          <Tooltip title={t('form.field.deleteLocked')}>
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => removeField(row)}
              data-testid={`form-field-delete-${row.id}`}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title={isEdit ? t('form.def.editTitle') : t('form.def.newTitle')}
        subtitle={isEdit ? definition?.code : t('form.def.subtitle')}
        fallbackTo="/admin/form-definitions"
        onBack={() => navigate('/admin/form-definitions')}
      />
      <div data-testid="form-def-editor">
        <PageFormShell
          submitting={submitting}
          onSubmit={submit}
          onCancel={() => navigate('/admin/form-definitions')}
          submitText={t('common.save')}
          hint={isEdit ? t('form.def.codeImmutable') : undefined}
          testId="form-def-form"
        >
          <Form form={form} layout="vertical" disabled={loading} initialValues={{ enabled: true }}>
            <FormGroup title={t('form.def.basicGroup')}>
              <Form.Item
                name="code"
                label={t('form.def.colCode')}
                rules={[{ required: true, message: t('form.def.colCode') }]}
                extra={isEdit ? t('form.def.codeImmutable') : undefined}
              >
                <Input maxLength={50} disabled={isEdit} data-testid="form-def-code" />
              </Form.Item>
              <Form.Item
                name="name"
                label={t('form.def.colName')}
                rules={[{ required: true, message: t('form.def.colName') }]}
              >
                <Input maxLength={200} data-testid="form-def-name" />
              </Form.Item>
              <Form.Item name="description" label={t('form.def.basicGroup')}>
                <Input.TextArea rows={2} maxLength={1000} />
              </Form.Item>
              <Form.Item name="enabled" label={t('form.def.colEnabled')} valuePropName="checked">
                <Switch data-testid="form-def-enabled" />
              </Form.Item>
            </FormGroup>
          </Form>
        </PageFormShell>

        {isEdit ? (
          <Card
            style={{ marginTop: 16 }}
            title={t('form.def.fieldsGroup')}
            extra={
              <Button icon={<PlusOutlined />} onClick={openAddField} data-testid="form-field-add">
                {t('form.field.add')}
              </Button>
            }
          >
            <Table
              rowKey="id"
              size="small"
              loading={loading}
              dataSource={fields}
              columns={fieldColumns}
              pagination={false}
              scroll={{ x: 1000 }}
              locale={{ emptyText: t('form.field.empty') }}
              data-testid="form-field-table"
            />
          </Card>
        ) : null}
      </div>

      <Modal
        open={fieldModalOpen}
        title={editingField ? t('form.field.editTitle') : t('form.field.addTitle')}
        onCancel={() => setFieldModalOpen(false)}
        onOk={() => void saveField()}
        confirmLoading={fieldSaving}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        destroyOnHidden
      >
        <Form form={fieldForm} layout="vertical">
          <Form.Item
            name="label"
            label={t('form.field.label')}
            rules={[{ required: true, message: t('form.field.label') }]}
          >
            <Input maxLength={200} data-testid="form-field-label" />
          </Form.Item>
          <Form.Item
            name="fieldType"
            label={t('form.field.type')}
            rules={[{ required: true, message: t('form.field.type') }]}
          >
            <Select
              data-testid="form-field-type"
              options={FORM_FIELD_TYPES.map((value) => ({ value, label: t(`form.type.${value}` as never) }))}
            />
          </Form.Item>
          <Form.Item name="required" label={t('form.field.required')} valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="sourceType" label={t('form.field.source')} extra={t('form.field.sourceHint')}>
            <Select allowClear options={sourceOptions} data-testid="form-field-source" placeholder="-" />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, next) => prev.fieldType !== next.fieldType}
          >
            {({ getFieldValue }) =>
              getFieldValue('fieldType') === 'SELECT' ? (
                <Form.Item
                  name="options"
                  label={t('form.field.options')}
                  rules={[{ required: true, message: t('form.field.optionsRequired') }]}
                >
                  <Input.TextArea rows={3} data-testid="form-field-options" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default FormDefinitionEditorPage;


