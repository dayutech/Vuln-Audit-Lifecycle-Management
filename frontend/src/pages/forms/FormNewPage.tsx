import { useCallback, useEffect, useState } from 'react';
import { Alert, Form, Input, Select, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { FormGroup, PageFormShell } from '@/components/layout/PageFormShell';
import {
  createFormSubmission,
  listEnabledDefinitions,
  resetEnabledDefinitionsCache,
  type FormDefinitionVO,
  type FormSubjectType,
} from '@/api/forms';
import { getCustomerList } from '@/api/customer';
import { getProjectList } from '@/api/project';
import { getAssetList } from '@/api/asset';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useT } from '@/i18n';

/**
 * 生成表单（t182）。
 *
 * 流程：模板 → 主体类型 → 具体主体（客户/项目/资产，带 403/空态引导）→ 可选标题/指派 →
 * `POST /forms/submissions` → 跳详情并提示 `snapshotFilled/snapshotNull`。
 */
const SUBJECT_TYPES: FormSubjectType[] = ['CUSTOMER', 'PROJECT', 'ASSET'];

interface Option {
  value: string;
  label: string;
}

const FormNewPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [form] = Form.useForm<{
    definitionId: string;
    subjectType: FormSubjectType;
    subjectId: string;
    title?: string;
    assignedTo?: string;
  }>();
  const [definitions, setDefinitions] = useState<FormDefinitionVO[]>([]);
  const [users, setUsers] = useState<Option[]>([]);
  const [subjectType, setSubjectType] = useState<FormSubjectType>('CUSTOMER');
  const [subjectOptions, setSubjectOptions] = useState<Option[]>([]);
  const [subjectLoading, setSubjectLoading] = useState(false);
  const [subjectEmptyHint, setSubjectEmptyHint] = useState<string | null>(null);
  const [loadingDefinitions, setLoadingDefinitions] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState<{ filled: number; empty: number; skipped: string[] } | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        setLoadingDefinitions(true);
        resetEnabledDefinitionsCache();
        const list = await listEnabledDefinitions();
        if (!cancelled) setDefinitions(list);
      } catch (error) {
        message.error(getApiErrorMessage(error, t('form.def.loadFailed')));
      } finally {
        if (!cancelled) setLoadingDefinitions(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [t]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const list = await getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT });
        if (!cancelled) {
          setUsers((Array.isArray(list) ? list : []).map((row: any) => ({ value: row.id, label: row.fullName || row.username })));
        }
      } catch {
        if (!cancelled) setUsers([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const loadSubjects = useCallback(
    async (type: FormSubjectType) => {
      setSubjectLoading(true);
      setSubjectEmptyHint(null);
      setSubjectOptions([]);
      form.setFieldValue('subjectId', undefined);
      try {
        if (type === 'CUSTOMER') {
          const res: any = await getCustomerList({ page: 1, page_size: 100 } as any);
          const payload = res?.data ?? res;
          const rows = payload?.content ?? payload?.items ?? (Array.isArray(payload) ? payload : []);
          setSubjectOptions(rows.map((row: any) => ({ value: row.id, label: row.code ? `${row.name} (${row.code})` : row.name })));
          if (rows.length === 0) setSubjectEmptyHint(t('form.subjectEmpty', { type: t('form.subject.CUSTOMER') }));
        } else if (type === 'PROJECT') {
          const res: any = await getProjectList({ page: 1, page_size: 100 } as any);
          const payload = res?.data ?? res;
          const rows = payload?.content ?? payload?.items ?? (Array.isArray(payload) ? payload : []);
          setSubjectOptions(rows.map((row: any) => ({ value: row.id, label: row.code ? `${row.name} (${row.code})` : row.name })));
          if (rows.length === 0) setSubjectEmptyHint(t('form.subjectEmpty', { type: t('form.subject.PROJECT') }));
        } else {
          const res: any = await getAssetList({ page: 1, page_size: 100 } as any);
          const payload = res?.data ?? res;
          const rows = payload?.content ?? payload?.items ?? (Array.isArray(payload) ? payload : []);
          setSubjectOptions(rows.map((row: any) => ({ value: row.id, label: row.code ? `${row.name} (${row.code})` : row.name })));
          if (rows.length === 0) setSubjectEmptyHint(t('form.subjectEmpty', { type: t('form.subject.ASSET') }));
        }
      } catch (error) {
        const status = getApiErrorStatus(error);
        setSubjectOptions([]);
        setSubjectEmptyHint(
          status === 403
            ? t('form.subjectEmpty', { type: t(`form.subject.${type}` as never) })
            : getApiErrorMessage(error, t('form.subjectEmpty', { type: t(`form.subject.${type}` as never) })),
        );
      } finally {
        setSubjectLoading(false);
      }
    },
    [form, t],
  );

  useEffect(() => {
    void loadSubjects(subjectType);
  }, [subjectType, loadSubjects]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      setCreated(null);
      const result = await createFormSubmission({
        definitionId: values.definitionId,
        subjectType: values.subjectType,
        subjectId: values.subjectId,
        title: values.title?.trim() || undefined,
        assignedTo: values.assignedTo || undefined,
      });
      message.success(t('form.new.created'));
      setCreated({
        filled: result.snapshotFilled ?? 0,
        empty: result.snapshotNull ?? 0,
        skipped: result.skippedSources ?? [],
      });
      // t182：把快照结果交给详情页（导航后仍能看到「已取到 N 个 / M 个为空」）
      navigate(`/forms/${result.id}`, {
        state: {
          snapshotFilled: result.snapshotFilled ?? 0,
          snapshotNull: result.snapshotNull ?? 0,
          skippedSources: result.skippedSources ?? [],
        },
      });
    } catch (error: any) {
      if (Array.isArray(error?.errorFields)) return;
      if (getApiErrorStatus(error) === 403) {
        message.error(getApiErrorMessage(error, t('form.new.failed')));
        return;
      }
      message.error(getApiErrorMessage(error, t('form.new.failed')));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader title={t('form.new.title')} subtitle={t('form.new.subtitle')} fallbackTo="/forms/mine" />
      <div data-testid="form-new">
        <PageFormShell
          submitting={submitting}
          onSubmit={submit}
          onCancel={() => navigate('/forms/mine')}
          submitText={t('form.new.submit')}
          hint={t('form.new.subtitle')}
          testId="form-new-form"
        >
          <Form form={form} layout="vertical" initialValues={{ subjectType: 'CUSTOMER' }}>
            <FormGroup title={t('form.new.definition')}>
              <Form.Item
                name="definitionId"
                label={t('form.new.definition')}
                rules={[{ required: true, message: t('form.new.definitionRequired') }]}
              >
                <Select
                  loading={loadingDefinitions}
                  showSearch
                  optionFilterProp="label"
                  placeholder={definitions.length === 0 ? t('form.new.noDefinition') : t('form.new.definition')}
                  options={definitions.map((item) => ({ value: item.id, label: `${item.name} (${item.code})` }))}
                  data-testid="form-new-definition"
                />
              </Form.Item>
            </FormGroup>

            <FormGroup title={t('form.subject')}>
              <Form.Item name="subjectType" label={t('form.subjectType')} rules={[{ required: true }]}>
                <Select
                  onChange={(value: FormSubjectType) => setSubjectType(value)}
                  options={SUBJECT_TYPES.map((value) => ({ value, label: t(`form.subject.${value}` as never) }))}
                  data-testid="form-subject-type"
                />
              </Form.Item>
              <Form.Item name="subjectId" label={t('form.subject')} rules={[{ required: true, message: t('form.subjectRequired') }]}>
                <Select
                  loading={subjectLoading}
                  showSearch
                  optionFilterProp="label"
                  placeholder={t('form.subject')}
                  options={subjectOptions}
                  notFoundContent={subjectEmptyHint ?? undefined}
                  data-testid="form-subject-select"
                />
              </Form.Item>
            </FormGroup>

            <FormGroup title={t('form.new.titleField')}>
              <Form.Item name="title" label={t('form.new.titleField')}>
                <Input maxLength={200} data-testid="form-new-title-field" />
              </Form.Item>
              <Form.Item name="assignedTo" label={t('form.new.assignee')}>
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={users}
                  data-testid="form-new-assignee"
                />
              </Form.Item>
            </FormGroup>

            {created ? (
              <Alert
                type="success"
                showIcon
                message={t('form.new.snapshotNotice', { filled: created.filled, empty: created.empty })}
                description={
                  created.skipped.length > 0 ? t('form.new.skippedSources', { list: created.skipped.join(', ') }) : undefined
                }
                data-testid="form-snapshot-notice"
              />
            ) : null}
          </Form>
        </PageFormShell>
      </div>
    </>
  );
};

export default FormNewPage;
