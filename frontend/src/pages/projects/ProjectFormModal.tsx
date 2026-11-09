import React, { useEffect, useMemo, useState } from 'react';
import { DatePicker, Form, Input, Modal, Select, Switch, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useT } from '@/i18n';
import CustomerSelectEmpty from '@/components/customer/CustomerSelectEmpty';
import { useCustomerOptions } from '@/hooks/useCustomerOptions';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT, mergeUserDirectory } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import { createProject, updateProject, extractProject } from '@/api/project';
import {
  PROJECT_TYPE_ORDER,
  projectTypeKey,
  type ProjectRequest,
  type ProjectVO,
} from '@/types/project';
import { describeProjectFailure } from './projectFailure';

const { RangePicker } = DatePicker;

export interface ProjectFormModalProps {
  open: boolean;
  /** 传入则为编辑模式 */
  project?: ProjectVO | null;
  /** 新建时的默认客户（从客户详情跳转过来） */
  defaultCustomerId?: string;
  onCancel: () => void;
  onSaved: (project: ProjectVO | null, mode: 'create' | 'edit') => void;
}

interface ProjectFormValues {
  code: string;
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
 * 项目新建 / 编辑表单（t72）。
 *
 * 客户下拉走 `api/customer.ts` 的 `getCustomerOptions`（silent，避免与全局提示重复），
 * 项目经理下拉走 `/users/directory`（工程师也可读，不触发 PII 403）。
 */
const ProjectFormModal: React.FC<ProjectFormModalProps> = ({
  open,
  project,
  defaultCustomerId,
  onCancel,
  onSaved,
}) => {
  const { t } = useT();
  const [form] = Form.useForm<ProjectFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  // t139：客户下拉带状态加载（历史缺陷：请求失败被 catch 吞成空下拉，没有任何提示）
  const customerOptionsState = useCustomerOptions(open);
  const customers = customerOptionsState.items;
  const isEdit = Boolean(project?.id);

  useEffect(() => {
    if (!open) return;
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
  }, [open]);

  useEffect(() => {
    if (!open) return;
    if (project) {
      form.setFieldsValue({
        code: project.code,
        name: project.name,
        customerId: project.customerId,
        managerId: project.managerId ?? undefined,
        type: project.type ?? PROJECT_TYPE_ORDER[0],
        isPhased: Boolean(project.isPhased),
        contractNo: project.contractNo ?? undefined,
        description: project.description ?? undefined,
        period:
          project.startDate && project.endDate
            ? [dayjs(project.startDate), dayjs(project.endDate)]
            : undefined,
      });
      return;
    }
    form.resetFields();
    form.setFieldsValue({
      type: PROJECT_TYPE_ORDER[0],
      isPhased: false,
      customerId: defaultCustomerId,
    });
  }, [open, project, defaultCustomerId, form]);

  const customerOptions = useMemo(
    () =>
      customers.map((customer) => ({
        value: customer.id,
        label: `${customer.name}${customer.code ? `（${customer.code}）` : ''}`,
      })),
    [customers],
  );

  const managerOptions = useMemo(
    () => users.map((user) => ({ value: user.id, label: user.fullName || user.username })),
    [users],
  );

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      const payload: ProjectRequest = {
        code: values.code.trim(),
        name: values.name.trim(),
        customerId: values.customerId,
        type: values.type,
        managerId: values.managerId ?? null,
        isPhased: Boolean(values.isPhased),
        contractNo: values.contractNo?.trim() || null,
        description: values.description?.trim() || null,
        startDate: values.period?.[0]?.format('YYYY-MM-DD') ?? null,
        endDate: values.period?.[1]?.format('YYYY-MM-DD') ?? null,
      };
      setSubmitting(true);
      const res = isEdit && project?.id
        ? await updateProject(project.id, payload)
        : await createProject(payload);
      const saved = extractProject(res);
      message.success(isEdit ? t('project.updated') : t('project.created'));
      onSaved(saved, isEdit ? 'edit' : 'create');
    } catch (error: any) {
      if (error?.errorFields) {
        // 表单校验失败：交给 Form 自己提示
        return;
      }
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

  return (
    <Modal
      title={isEdit ? t('project.form.editTitle') : t('project.form.createTitle')}
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={submitting}
      destroyOnHidden
      width={720}
      maskClosable={false}
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="code"
          label={t('project.form.code')}
          rules={[{ required: true, message: t('project.form.codeRequired') }]}
        >
          <Input maxLength={50} disabled={isEdit} placeholder="PRJ-2026-001" />
        </Form.Item>
        <Form.Item
          name="name"
          label={t('project.form.name')}
          rules={[{ required: true, message: t('project.form.nameRequired') }]}
        >
          <Input maxLength={200} />
        </Form.Item>
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
            data-testid="project-form-customer-select"
            // t139：请求失败/无客户时给出可执行指引，而不是一片空白
            notFoundContent={
              <CustomerSelectEmpty
                compact
                status={customerOptionsState.status}
                detail={customerOptionsState.detail}
                onRetry={() => void customerOptionsState.reload()}
              />
            }
          />
        </Form.Item>
        <Form.Item name="managerId" label={t('project.form.manager')}>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder={t('project.form.managerPlaceholder')}
            options={managerOptions}
          />
        </Form.Item>
        <Form.Item
          name="type"
          label={t('project.form.type')}
          rules={[{ required: true, message: t('project.form.typeRequired') }]}
        >
          <Select
            options={PROJECT_TYPE_ORDER.map((type) => ({ value: type, label: t(projectTypeKey(type)) }))}
          />
        </Form.Item>
        <Form.Item name="period" label={t('project.form.period')}>
          <RangePicker
            style={{ width: '100%' }}
            placeholder={[t('project.form.period'), t('project.form.period')].slice(0, 2) as [string, string]}
          />
        </Form.Item>
        <Form.Item name="isPhased" label={t('project.form.isPhased')} valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="contractNo" label={t('project.form.contractNo')}>
          <Input maxLength={100} />
        </Form.Item>
        <Form.Item name="description" label={t('project.form.description')}>
          <Input.TextArea
            rows={3}
            maxLength={1000}
            placeholder={t('project.form.descriptionPlaceholder')}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ProjectFormModal;

