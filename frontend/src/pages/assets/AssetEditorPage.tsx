import { useEffect, useState } from 'react';
import { Alert, Form, Input, Select, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { FormGroup, PageFormShell } from '@/components/layout/PageFormShell';
import { createAsset, getAssetDetail, getAssetList, updateAsset } from '@/api/asset';
import { getCustomerList } from '@/api/customer';
import { getProjectList } from '@/api/project';
import { getTeamList } from '@/api/team';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useT } from '@/i18n';
import type { AssetVO } from '@/types/asset';

/**
 * 资产新建 / 编辑整页表单（t168，需求 9 第 4 条：详情与编辑统一为独立页面）。
 *
 * - `/assets/new`：新建（必须挂客户 + 项目，与后端 t69 一致）；
 * - `/assets/:id/edit`：编辑基本信息（名称/类型/来源/优先级/状态/所属团队/描述）。
 * 统一 `PageHeader`（返回 + 标题 + 副标题）+ `PageFormShell`（吸底操作条），不再是弹框。
 */
const { Option } = Select;

const TARGET_TYPES = ['web', 'api', 'mobile', 'host', 'network', 'other'];
const TARGET_SOURCES = ['customer', 'internal', 'public', 'other'];
const PRIORITIES = ['critical', 'high', 'medium', 'low'];
const STATUSES = ['active', 'inactive', 'archived'];

interface CustomerOption {
  id: string;
  name: string;
}
interface ProjectOption {
  id: string;
  name: string;
  code?: string;
}

const AssetEditorPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [form] = Form.useForm<any>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [customers, setCustomers] = useState<CustomerOption[]>([]);
  const [projects, setProjects] = useState<ProjectOption[]>([]);
  const [teams, setTeams] = useState<Array<{ id: string; name: string }>>([]);
  const [customerId, setCustomerId] = useState<string | undefined>();

  const assetId = window.location.pathname.match(/^\/assets\/([^/]+)\/edit$/)?.[1];
  const isEdit = Boolean(assetId);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [customerRes, teamRes] = await Promise.all([
          getCustomerList({ page: 1, page_size: 100 } as any).catch(() => null),
          getTeamList().catch(() => null),
        ]);
        if (cancelled) return;
        const cPayload: any = (customerRes as any)?.data ?? customerRes;
        setCustomers((Array.isArray(cPayload?.content) ? cPayload.content : []).map((row: any) => ({ id: row.id, name: row.name })));
        setTeams(Array.isArray(teamRes) ? (teamRes as any[]) : ((teamRes as any)?.data ?? []));
      } catch {
        // 选项失败不阻断表单
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // 编辑态：回填
  useEffect(() => {
    if (!assetId) return;
    let cancelled = false;
    (async () => {
      try {
        setLoading(true);
        const rows = await getAssetList({ page: 1, page_size: 100 } as any).catch(() => null as any);
        const payload: any = (rows as any)?.data ?? rows;
        let target: AssetVO | undefined = (Array.isArray(payload?.content) ? payload.content : []).find(
          (row: AssetVO) => row.id === assetId,
        );
        if (!target) {
          // 列表未命中时退回详情接口
          const detail: any = await getAssetDetail(assetId);
          target = (detail?.data ?? detail) as AssetVO;
        }
        if (cancelled || !target) return;
        form.setFieldsValue({
          name: target.name,
          target_type: (target as any).targetType ?? target.target_type,
          target_source: (target as any).targetSource ?? target.target_source,
          priority: target.priority,
          status: target.status,
          team_id: (target as any).teamId ?? target.team_id,
          description: target.description,
        });
      } catch (error) {
        message.error(getApiErrorMessage(error, t('asset.form.loadFailed')));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [assetId, form, t]);

  useEffect(() => {
    if (!customerId) {
      setProjects([]);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const res: any = await getProjectList({ page: 1, page_size: 100, customerId } as any);
        const payload: any = (res as any)?.data ?? res;
        if (!cancelled) {
          setProjects(
            (Array.isArray(payload?.content) ? payload.content : []).map((row: any) => ({
              id: row.id,
              name: row.name,
              code: row.code,
            })),
          );
        }
      } catch {
        if (!cancelled) setProjects([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [customerId]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (isEdit && assetId) {
        await updateAsset(assetId, {
          name: values.name,
          target_type: values.target_type,
          target_source: values.target_source,
          priority: values.priority,
          status: values.status,
          team_id: values.team_id,
          description: values.description,
        } as any);
        message.success(t('asset.form.saved'));
        navigate(`/assets/${assetId}`);
        return;
      }
      const created: any = await createAsset({
        name: values.name,
        target_type: values.target_type,
        target_source: values.target_source,
        priority: values.priority,
        status: values.status,
        team_id: values.team_id,
        description: values.description,
        project_id: values.project_id,
      } as any);
      const row = (created as any)?.data ?? created;
      message.success(t('asset.form.created'));
      navigate(row?.id ? `/assets/${row.id}` : '/assets');
    } catch (error: any) {
      if (Array.isArray(error?.errorFields)) return;
      const status = getApiErrorStatus(error);
      if (status === 403) {
        message.error(t('asset.form.forbidden'));
        return;
      }
      message.error(getApiErrorMessage(error, t('asset.form.saveFailed')));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader
        title={isEdit ? t('asset.form.editTitle') : t('asset.form.createTitle')}
        subtitle={isEdit ? assetId : t('asset.form.createSubtitle')}
        fallbackTo={isEdit && assetId ? `/assets/${assetId}` : '/assets'}
        onBack={() => navigate(isEdit && assetId ? `/assets/${assetId}` : '/assets')}
      />
      <div data-testid="asset-edit-page">
        <PageFormShell
          submitting={submitting}
          onSubmit={submit}
          onCancel={() => navigate(isEdit && assetId ? `/assets/${assetId}` : '/assets')}
          submitText={isEdit ? t('common.save') : t('asset.list.new')}
          hint={isEdit ? t('asset.form.editHint') : t('asset.projectLink.createRequired')}
          testId="asset-page-form"
        >
          <Form form={form} layout="vertical" disabled={loading} initialValues={{ priority: 'medium', status: 'active', target_type: 'web', target_source: 'customer' }}>
            <FormGroup title={t('asset.form.basicGroup')}>
              <Form.Item name="name" label={t('asset.list.colName')} rules={[{ required: true, message: t('asset.form.nameRequired') }]}>
                <Input maxLength={200} data-testid="asset-form-name" />
              </Form.Item>
              {!isEdit ? (
                <>
                  <Form.Item
                    name="customer_id"
                    label={t('asset.projectLink.customerLabel')}
                    rules={[{ required: true, message: t('asset.projectLink.customerPlaceholder') }]}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder={t('asset.projectLink.customerPlaceholder')}
                      onChange={(value) => setCustomerId(value)}
                      options={customers.map((c) => ({ value: c.id, label: c.name }))}
                      data-testid="asset-form-customer-select"
                    />
                  </Form.Item>
                  <Form.Item
                    name="project_id"
                    label={t('asset.projectLink.projectLabel')}
                    rules={[{ required: true, message: t('asset.projectLink.projectPlaceholder') }]}
                    extra={t('asset.projectLink.createRequired')}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder={t('asset.projectLink.projectPlaceholder')}
                      disabled={!customerId}
                      options={projects.map((p) => ({ value: p.id, label: p.code ? `${p.name} (${p.code})` : p.name }))}
                      data-testid="asset-form-project-select"
                    />
                  </Form.Item>
                </>
              ) : null}
              <Form.Item name="team_id" label={t('asset.list.colTeam')} rules={[{ required: true, message: t('asset.form.teamRequired') }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder={t('asset.form.teamRequired')}
                  options={teams.map((team) => ({ value: team.id, label: team.name }))}
                />
              </Form.Item>
            </FormGroup>

            <FormGroup title={t('asset.form.attributeGroup')}>
              <Form.Item name="target_type" label={t('asset.form.targetType')} rules={[{ required: true, message: t('asset.form.targetTypeRequired') }]}>
                <Select>
                  {TARGET_TYPES.map((value) => (
                    <Option key={value} value={value}>
                      {value}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
              <Form.Item name="target_source" label={t('asset.form.targetSource')} rules={[{ required: true, message: t('asset.form.targetSourceRequired') }]}>
                <Select>
                  {TARGET_SOURCES.map((value) => (
                    <Option key={value} value={value}>
                      {value}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
              <Form.Item name="priority" label={t('asset.list.colPriority')} rules={[{ required: true, message: t('asset.form.priorityRequired') }]}>
                <Select>
                  {PRIORITIES.map((value) => (
                    <Option key={value} value={value}>
                      {value}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
              <Form.Item name="status" label={t('asset.list.colStatus')} rules={[{ required: true, message: t('asset.form.statusRequired') }]}>
                <Select>
                  {STATUSES.map((value) => (
                    <Option key={value} value={value}>
                      {value}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
              <Form.Item name="description" label={t('asset.form.description')}>
                <Input.TextArea rows={4} maxLength={2000} showCount />
              </Form.Item>
            </FormGroup>

            {!isEdit ? (
              <Alert type="info" showIcon message={t('asset.form.createNote')} style={{ marginBottom: 16 }} />
            ) : null}
          </Form>
        </PageFormShell>
      </div>
    </>
  );
};

export default AssetEditorPage;

