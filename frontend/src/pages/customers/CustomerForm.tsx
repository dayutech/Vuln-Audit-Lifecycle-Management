import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, AutoComplete, Button, Col, Form, Input, Row, Select, Space, message } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { useT } from '@/i18n';
import PageHeader from '@/components/layout/PageHeader';
import PageFormShell, { FormGroup } from '@/components/layout/PageFormShell';
import { AppLoading } from '@/components/Feedback';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { useBackNavigation } from '@/hooks/useBackNavigation';
import { isManagementProfile } from '@/utils/accessRoles';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { createCustomer, extractCustomerDetail, getCustomerDetail, updateCustomer } from '@/api/customer';
import { extractIndustryOptions, searchIndustryTypes } from '@/api/industry';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT, mergeUserDirectory } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import type { CustomerCreateRequest, CustomerVO } from '@/types/customer';

const { TextArea } = Input;
const LEVEL_OPTIONS = ['A', 'B', 'C'];

interface CustomerFormValues {
  code?: string;
  name: string;
  industry?: string;
  level?: string;
  ownerId?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  description?: string;
}

/**
 * 客户新建 / 编辑**独立页面**（t142，替代原来的右侧 Drawer）。
 *
 * - `/customers/new`：全页表单，提交后跳到新客户详情页；
 * - `/customers/:id/edit`：先 `GET /customers/{id}` 回填，提交后回详情页；
 * - 页头统一 `PageHeader`（← 返回），底部吸底操作条（取消=返回上一页 / 保存）；
 * - 行业字段用 `AutoComplete` + `GET /industry-types?keyword=`（t129 检索能力），
 *   仍允许自由输入，不会把存量自由文本行业锁死；
 * - 负责人走 `/users/directory`（PII 收敛，支持关键字检索）；
 * - 权限：与后端 `hasAnyRole('ADMIN','PROJECT_LEADER')` 对齐（`isManagementProfile`），
 *   无写权限时直接显示只读提示并禁用保存。
 */
const CustomerForm: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const { goBack, canGoBack } = useBackNavigation('/customers');
  const { profile, loaded: profileLoaded } = useAccessProfile();
  const canWrite = profileLoaded && isManagementProfile(profile);

  const [form] = Form.useForm<CustomerFormValues>();
  const [loading, setLoading] = useState(isEdit);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [customer, setCustomer] = useState<CustomerVO | null>(null);

  const [owners, setOwners] = useState<UserDirectoryEntry[]>([]);
  const [ownerLimitHit, setOwnerLimitHit] = useState(false);
  const ownerTimer = useRef<number | undefined>(undefined);

  const [industryOptions, setIndustryOptions] = useState<{ value: string; label: string }[]>([]);
  const industryTimer = useRef<number | undefined>(undefined);

  /** 负责人下拉（/users/directory） */
  const loadOwners = useCallback(async (keyword?: string) => {
    try {
      const list = await getUserDirectory({ keyword, limit: USER_DIRECTORY_DEFAULT_LIMIT });
      setOwners((prev) => mergeUserDirectory(prev, list));
      setOwnerLimitHit(list.length >= USER_DIRECTORY_DEFAULT_LIMIT);
    } catch {
      if (!keyword) setOwners([]);
    }
  }, []);

  useEffect(() => {
    void loadOwners();
  }, [loadOwners]);

  const handleOwnerSearch = (keyword: string) => {
    if (ownerTimer.current) window.clearTimeout(ownerTimer.current);
    ownerTimer.current = window.setTimeout(() => {
      void loadOwners(keyword);
    }, 300);
  };

  /** 行业检索（t129 `GET /industry-types?keyword=`） */
  const handleIndustrySearch = (keyword: string) => {
    if (industryTimer.current) window.clearTimeout(industryTimer.current);
    industryTimer.current = window.setTimeout(async () => {
      try {
        const list = extractIndustryOptions(await searchIndustryTypes(keyword));
        setIndustryOptions(list.map((item) => ({ value: item.name, label: `${item.name}（${item.code}）` })));
      } catch {
        /* 检索失败不阻塞自由输入 */
      }
    }, 300);
  };

  const loadCustomer = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setLoadError(null);
    try {
      const parsed = extractCustomerDetail(await getCustomerDetail(id));
      if (!parsed) {
        setLoadError(t('customerPage.notFound'));
        return;
      }
      setCustomer(parsed);
      form.setFieldsValue({
        code: parsed.code,
        name: parsed.name,
        industry: parsed.industry ?? undefined,
        level: parsed.level ?? undefined,
        ownerId: parsed.ownerId ?? undefined,
        contactName: parsed.contactName ?? undefined,
        contactPhone: parsed.contactPhone ?? undefined,
        contactEmail: parsed.contactEmail ?? undefined,
        description: parsed.description ?? undefined,
      });
    } catch (error) {
      const status = getApiErrorStatus(error);
      setLoadError(
        status === 404
          ? t('customerPage.notFound')
          : status === 403
            ? t('customer.forbidden')
            : getApiErrorMessage(error, t('customerPage.loadFailed')),
      );
    } finally {
      setLoading(false);
    }
  }, [id, form, t]);

  useEffect(() => {
    if (!isEdit) {
      form.setFieldsValue({ level: 'B' });
      return;
    }
    void loadCustomer();
  }, [isEdit, loadCustomer, form]);

  const handleSubmit = async () => {
    let values: CustomerFormValues;
    try {
      values = await form.validateFields();
    } catch {
      // 字段级错误由 Form 自己提示
      return;
    }
    setSubmitting(true);
    try {
      const payload: CustomerCreateRequest = {
        code: (values.code ?? '').trim(),
        name: values.name.trim(),
        industry: values.industry?.trim() || undefined,
        level: values.level || undefined,
        ownerId: values.ownerId || undefined,
        contactName: values.contactName?.trim() || undefined,
        contactPhone: values.contactPhone?.trim() || undefined,
        contactEmail: values.contactEmail?.trim() || undefined,
        description: values.description?.trim() || undefined,
      } as CustomerCreateRequest;
      if (isEdit && id) {
        await updateCustomer(id, payload);
        message.success(t('customerPage.updateSuccess'));
        // 保存后回到详情：有站内历史就 -1（历史栈保持 [列表, 详情, 编辑]，再按返回即回列表），
        // 新标签页直接打开编辑页时用 replace 跳到详情
        if (canGoBack) navigate(-1);
        else navigate(`/customers/${id}`, { replace: true });
      } else {
        const created = await createCustomer(payload);
        message.success(t('customerPage.createSuccess'));
        const newId = extractCustomerDetail(created)?.id;
        if (newId) navigate(`/customers/${newId}`, { replace: true });
        else navigate('/customers', { replace: true });
      }
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 409) {
        message.error(t('customer.codeConflict'));
      } else if (status === 403) {
        message.error(t('customer.forbidden'));
      } else {
        message.error(getApiErrorMessage(error, t('common.operationFailed')));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const title = isEdit ? t('customerPage.editTitle') : t('customerPage.newTitle');
  const subtitle = customer ? t('customerPage.subtitleTemplate', { code: customer.code }) : undefined;

  return (
    <>
      <PageHeader
        title={title}
        subtitle={subtitle}
        fallbackTo="/customers"
        onBack={goBack}
        extra={
          <Space>
            <Button onClick={goBack} data-testid="customer-form-back-list">
              {t('customerPage.backToList')}
            </Button>
          </Space>
        }
        testId="customer-form-header"
      />

      {!profileLoaded ? (
        <AppLoading tip={t('common.loading')} />
      ) : !canWrite ? (
        <Alert type="info" showIcon message={t('customerPage.readonlyHint')} data-testid="customer-form-readonly" />
      ) : loading ? (
        <AppLoading tip={t('common.loading')} />
      ) : loadError ? (
        <Alert
          type="error"
          showIcon
          message={loadError}
          action={
            <Button size="small" onClick={() => void loadCustomer()}>
              {t('common.retry')}
            </Button>
          }
        />
      ) : (
        <Form form={form} layout="vertical" disabled={!canWrite} data-testid="customer-form">
          <PageFormShell
            submitting={submitting}
            onSubmit={() => void handleSubmit()}
            onCancel={goBack}
            submitText={isEdit ? t('customerPage.saveSubmit') : t('customerPage.createSubmit')}
            submitDisabled={!canWrite}
            hint={isEdit ? t('customerPage.codeImmutable') : t('customerPage.codeAutoHint')}
            testId="customer-form-shell"
          >
            <FormGroup title={t('customerPage.sectionBasic')}>
              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item
                    name="code"
                    label={t('customer.fieldCode')}
                    rules={isEdit ? [] : [{ required: true, message: t('customer.requiredCode') }]}
                  >
                    <Input placeholder={t('customer.placeholderCode')} maxLength={50} disabled={isEdit} data-testid="customer-form-code" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={16}>
                  <Form.Item
                    name="name"
                    label={t('customer.fieldName')}
                    rules={[{ required: true, message: t('customer.requiredName') }]}
                  >
                    <Input maxLength={200} data-testid="customer-form-name" />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={16}>
                <Col xs={24} md={16}>
                  <Form.Item
                    name="industry"
                    label={t('customer.fieldIndustry')}
                    extra={t('customerPage.industrySearchHint')}
                  >
                    <AutoComplete
                      options={industryOptions}
                      onSearch={handleIndustrySearch}
                      onFocus={() => handleIndustrySearch('')}
                      placeholder={t('customerPage.industrySearchPlaceholder')}
                      allowClear
                      data-testid="customer-form-industry"
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="level" label={t('customer.fieldLevel')}>
                    <Select allowClear options={LEVEL_OPTIONS.map((item) => ({ value: item, label: item }))} data-testid="customer-form-level" />
                  </Form.Item>
                </Col>
              </Row>
            </FormGroup>

            <FormGroup title={t('customerPage.sectionContact')}>
              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item name="contactName" label={t('customer.fieldContactName')}>
                    <Input maxLength={100} data-testid="customer-form-contact-name" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="contactPhone" label={t('customer.fieldContactPhone')}>
                    <Input maxLength={50} data-testid="customer-form-contact-phone" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="contactEmail" label={t('customer.fieldContactEmail')}>
                    <Input maxLength={200} data-testid="customer-form-contact-email" />
                  </Form.Item>
                </Col>
              </Row>
            </FormGroup>

            <FormGroup title={t('customerPage.sectionOwner')}>
              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item
                    name="ownerId"
                    label={t('customer.fieldOwner')}
                    extra={ownerLimitHit ? t('customer.ownerLimitHint') : undefined}
                  >
                    <Select
                      allowClear
                      showSearch
                      filterOption={false}
                      onSearch={handleOwnerSearch}
                      placeholder={t('customer.placeholderOwner')}
                      options={owners.map((item) => ({
                        value: item.id,
                        label: item.fullName ? `${item.fullName}（${item.username}）` : item.username,
                      }))}
                      data-testid="customer-form-owner"
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={16}>
                  <Form.Item name="description" label={t('customer.fieldDescription')}>
                    <TextArea rows={3} maxLength={1000} data-testid="customer-form-description" />
                  </Form.Item>
                </Col>
              </Row>
            </FormGroup>
          </PageFormShell>
        </Form>
      )}
    </>
  );
};

export default CustomerForm;

