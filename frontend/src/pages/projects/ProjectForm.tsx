import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Col, DatePicker, Form, Input, Row, Select, Space, Switch, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useT } from '@/i18n';
import PageHeader from '@/components/layout/PageHeader';
import PageFormShell, { FormGroup } from '@/components/layout/PageFormShell';
import CustomerSelectEmpty from '@/components/customer/CustomerSelectEmpty';
import { AppLoading } from '@/components/Feedback';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { useBackNavigation } from '@/hooks/useBackNavigation';
import { useCustomerOptions } from '@/hooks/useCustomerOptions';
import { canCreateProject, canManageProject } from '@/utils/accessRoles';
import { getApiErrorMessage } from '@/utils/errorMessage';
import { createProject, extractProject, getProjectDetail, updateProject } from '@/api/project';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT, mergeUserDirectory } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import { PROJECT_TYPE_ORDER, projectTypeKey, type ProjectRequest, type ProjectVO } from '@/types/project';
import { describeProjectFailure } from './projectFailure';

const { RangePicker } = DatePicker;

interface ProjectFormValues {
  code?: string;
  name: string;
  customerId: string;
  managerId?: string;
  type?: string;
  isPhased?: boolean;
  contractNo?: string;
  description?: string;
  period?: [Dayjs, Dayjs];
}

/**
 * 项目新建 / 编辑**独立页面**（t142，替代原来的 `ProjectFormModal` 弹框）。
 *
 * - `/projects/new`（支持 `?customerId=` 预选客户，客户详情的「新建项目」按钮走这里）
 * - `/projects/:id/edit`：`GET /projects/{id}` 回填
 * - 页头 `PageHeader`（← 返回）+ 分组卡片 + 吸底操作条（取消=返回上一页 / 保存）
 * - 客户下拉复用 t139 的 `useCustomerOptions` + `CustomerSelectEmpty`（含空态与 403 指引）
 * - 权限：新建要求 `canCreateProject`；编辑要求 `canManageProject`（与列表页/详情页一致）
 */
const ProjectForm: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const isEdit = Boolean(id);
  const { goBack, canGoBack } = useBackNavigation('/projects');
  const { profile, loaded: profileLoaded } = useAccessProfile();

  const [form] = Form.useForm<ProjectFormValues>();
  const [loading, setLoading] = useState(isEdit);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [project, setProject] = useState<ProjectVO | null>(null);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);

  const customerOptionsState = useCustomerOptions(Boolean(profileLoaded));

  const allowed = profileLoaded
    ? isEdit
      ? canManageProject(profile, project?.managerId ?? null)
      : canCreateProject(profile)
    : false;

  useEffect(() => {
    let cancelled = false;
    getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT })
      .then((list) => {
        if (!cancelled) setUsers((prev) => mergeUserDirectory(prev, list));
      })
      .catch(() => {
        if (!cancelled) setUsers([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const loadProject = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setLoadError(null);
    try {
      const parsed = extractProject(await getProjectDetail(id));
      if (!parsed) {
        setLoadError(t('projectPage.notFound'));
        return;
      }
      setProject(parsed);
      form.setFieldsValue({
        code: parsed.code,
        name: parsed.name,
        customerId: parsed.customerId,
        managerId: parsed.managerId ?? undefined,
        type: parsed.type ?? PROJECT_TYPE_ORDER[0],
        isPhased: Boolean(parsed.isPhased),
        contractNo: parsed.contractNo ?? undefined,
        description: parsed.description ?? undefined,
        period:
          parsed.startDate && parsed.endDate ? [dayjs(parsed.startDate), dayjs(parsed.endDate)] : undefined,
      });
    } catch (error) {
      setLoadError(getApiErrorMessage(error, t('projectPage.loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [id, form, t]);

  useEffect(() => {
    if (isEdit) {
      void loadProject();
      return;
    }
    const presetCustomerId = searchParams.get('customerId') ?? undefined;
    form.setFieldsValue({ type: PROJECT_TYPE_ORDER[0], isPhased: false, customerId: presetCustomerId });
  }, [isEdit, loadProject, form, searchParams]);

  const customerOptions = useMemo(
    () =>
      customerOptionsState.items.map((customer) => ({
        value: customer.id,
        label: `${customer.name}${customer.code ? `（${customer.code}）` : ''}`,
      })),
    [customerOptionsState.items],
  );

  const managerOptions = useMemo(
    () => users.map((item) => ({ value: item.id, label: item.fullName || item.username })),
    [users],
  );

  const handleSubmit = async () => {
    let values: ProjectFormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    try {
      const payload: ProjectRequest = {
        code: values.code?.trim() || null,
        name: values.name.trim(),
        customerId: values.customerId,
        type: values.type,
        managerId: values.managerId ?? null,
        isPhased: Boolean(values.isPhased),
        contractNo: values.contractNo?.trim() || null,
        description: values.description?.trim() || null,
        startDate: values.period?.[0]?.format('YYYY-MM-DD') ?? null,
        endDate: values.period?.[1]?.format('YYYY-MM-DD') ?? null,
      } as ProjectRequest;
      if (isEdit && id) {
        await updateProject(id, payload);
        message.success(t('project.updated'));
        // 保存后回详情：有站内历史就 -1（历史栈保持 [列表, 详情, 编辑]），否则 replace 到详情
        if (canGoBack) navigate(-1);
        else navigate(`/projects/${id}`, { replace: true });
      } else {
        const saved = extractProject(await createProject(payload));
        message.success(t('project.created'));
        if (saved?.id) navigate(`/projects/${saved.id}`, { replace: true });
        else navigate('/projects', { replace: true });
      }
    } catch (error) {
      message.error(
        describeProjectFailure(
          error,
          {
            forbidden: t('project.writeForbidden'),
            notFound: t('project.detail.notFound'),
            blockedTemplate: t('project.deleteBlocked'),
            blockedFallback: t('project.deleteBlockedHint'),
            invalidTransition: t('project.statusInvalid'),
            fallback: t('common.operationFailed'),
          },
          (template, count) => template.replace('{count}', String(count)),
        ),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const title = isEdit ? t('projectPage.editTitle') : t('projectPage.newTitle');
  const subtitle = project ? t('projectPage.subtitleTemplate', { code: project.code }) : undefined;

  return (
    <>
      <PageHeader
        title={title}
        subtitle={subtitle}
        fallbackTo="/projects"
        onBack={goBack}
        extra={
          <Space>
            <Button onClick={goBack} data-testid="project-form-back-list">
              {t('projectPage.backToList')}
            </Button>
          </Space>
        }
        testId="project-form-header"
      />

      {!profileLoaded ? (
        <AppLoading tip={t('common.loading')} />
      ) : !allowed ? (
        <Alert
          type="info"
          showIcon
          message={isEdit ? t('project.forbidden') : t('project.createForbidden')}
          data-testid="project-form-readonly"
        />
      ) : loading ? (
        <AppLoading tip={t('common.loading')} />
      ) : loadError ? (
        <Alert
          type="error"
          showIcon
          message={loadError}
          action={
            <Button size="small" onClick={() => void loadProject()}>
              {t('common.retry')}
            </Button>
          }
        />
      ) : (
        <Form form={form} layout="vertical" data-testid="project-form">
          <PageFormShell
            submitting={submitting}
            onSubmit={() => void handleSubmit()}
            onCancel={goBack}
            submitText={isEdit ? t('projectPage.saveSubmit') : t('projectPage.createSubmit')}
            hint={isEdit ? t('projectPage.codeImmutable') : t('projectPage.formHint')}
            testId="project-form-shell"
          >
            <FormGroup title={t('projectPage.sectionBasic')}>
              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item name="code" label={t('project.form.code')} extra={t('project.form.codeHint')}>
                    <Input placeholder={t('project.form.codePlaceholder')} maxLength={50} data-testid="project-form-code" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={16}>
                  <Form.Item
                    name="name"
                    label={t('project.form.name')}
                    rules={[{ required: true, message: t('project.form.nameRequired') }]}
                  >
                    <Input maxLength={200} data-testid="project-form-name" />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="customerId"
                    label={t('project.form.customer')}
                    rules={[{ required: true, message: t('project.form.customerRequired') }]}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder={t('project.form.customerPlaceholder')}
                      options={customerOptions}
                      loading={customerOptionsState.status === 'loading'}
                      notFoundContent={
                        <CustomerSelectEmpty
                          compact
                          status={customerOptionsState.status}
                          detail={customerOptionsState.detail}
                          onRetry={() => void customerOptionsState.reload()}
                        />
                      }
                      data-testid="project-form-customer-select"
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="managerId" label={t('project.form.manager')}>
                    <Select
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      placeholder={t('project.form.managerPlaceholder')}
                      options={managerOptions}
                      data-testid="project-form-manager-select"
                    />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item name="type" label={t('project.form.type')}>
                    <Select
                      options={PROJECT_TYPE_ORDER.map((item) => ({ value: item, label: t(projectTypeKey(item)) }))}
                      data-testid="project-form-type-select"
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="isPhased" label={t('project.form.isPhased')} valuePropName="checked">
                    <Switch data-testid="project-form-phased" />
                  </Form.Item>
                </Col>
              </Row>
            </FormGroup>

            <FormGroup title={t('projectPage.sectionSchedule')}>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item name="period" label={t('project.form.period')}>
                    <RangePicker style={{ width: '100%' }} data-testid="project-form-period" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="contractNo" label={t('project.form.contractNo')}>
                    <Input maxLength={100} data-testid="project-form-contract" />
                  </Form.Item>
                </Col>
              </Row>
            </FormGroup>

            <FormGroup title={t('projectPage.sectionMore')}>
              <Form.Item name="description" label={t('project.form.description')}>
                <Input.TextArea rows={3} maxLength={1000} data-testid="project-form-description" />
              </Form.Item>
            </FormGroup>
          </PageFormShell>
        </Form>
      )}
    </>
  );
};

export default ProjectForm;


