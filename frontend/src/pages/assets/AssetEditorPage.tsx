import { useEffect, useState } from 'react';
import { Alert, Form, Input, Select, message } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { FormGroup, PageFormShell } from '@/components/layout/PageFormShell';
import { createAsset, getAssetDetail, getAssetList, getAssetNextCode, updateAsset } from '@/api/asset';
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


/**
 * t193：枚举取值必须与后端一致，且**展示本地化文案**而不是英文标识符。
 *
 * 此前写的是 `critical / high / medium / low` 与 `active / inactive / archived`，
 * 而后端只接受 `P0–P3` 与 `active / under_test / suspended / archived`：
 * 默认值 `medium` 直接导致创建时 400「无效的优先级值」，下拉里也全是英文标识符。
 */
const TARGET_TYPES = ['web', 'api', 'mobile', 'host', 'network', 'other'] as const;
const TARGET_SOURCES = ['customer', 'internal', 'public', 'other'] as const;
const PRIORITIES = ['P0', 'P1', 'P2', 'P3'] as const;
const STATUSES = ['active', 'under_test', 'suspended', 'archived'] as const;
/** 状态值 → 本地化标签（under_test 的词条键是 underTest） */
const STATUS_LABEL_KEYS = {
  active: 'asset.status.active',
  under_test: 'asset.status.underTest',
  suspended: 'asset.status.suspended',
  archived: 'asset.status.archived',
} as const;

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
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm<any>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [customers, setCustomers] = useState<CustomerOption[]>([]);
  const [projects, setProjects] = useState<ProjectOption[]>([]);
  const [teams, setTeams] = useState<Array<{ id: string; name: string }>>([]);
  const [customerId, setCustomerId] = useState<string | undefined>();

  const assetId = window.location.pathname.match(/^\/assets\/([^/]+)\/edit$/)?.[1];
  const isEdit = Boolean(assetId);

  /**
   * t193：支持从项目侧「新建资产」带参进入 ——
   * `/assets/new?customerId=..&projectId=..&returnTo=/projects/{id}`：
   * 客户与项目预选并**锁定**（该资产必须挂到来源项目），保存后回到 returnTo。
   */
  const presetCustomerId = (searchParams.get('customerId') ?? '').trim() || undefined;
  const presetProjectId = (searchParams.get('projectId') ?? '').trim() || undefined;
  const returnTo = (searchParams.get('returnTo') ?? '').trim() || undefined;
  const presetLocked = !isEdit && Boolean(presetProjectId);

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
        /**
         * t192 顺带修复：`GET /teams?page=&size=` 返回的是分页对象
         * `{ content, totalElements, ... }`，此前直接 `setTeams(res.data)` 把**对象**塞进
         * `teams` 状态，渲染时 `teams.map(...)` 抛 `d.map is not a function`，
         * 整个 `/assets/new`（含编辑页）被错误边界吃掉，只能看到 "Unexpected Application Error!"。
         */
        const tPayload: any = (teamRes as any)?.data ?? teamRes;
        const teamRows: any[] = Array.isArray(tPayload)
          ? tPayload
          : (Array.isArray(tPayload?.content) ? tPayload.content : []);
        setTeams(teamRows.map((row: any) => ({ id: row.id, name: row.name })));
      } catch {
        // 选项失败不阻断表单
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (assetId) return;
    /**
     * t192：新建页打开即向后端要一个按规则生成的资产编码并只读预填，
     * 用户不允许手动设置；预览失败时留空，提交后由服务端兜底生成。
     */
    // t193：从项目侧进入时预选客户 + 项目（并锁定），首屏即可直接填资产信息保存
    if (presetCustomerId) {
      setCustomerId(presetCustomerId);
      form.setFieldsValue({ customer_id: presetCustomerId, project_id: presetProjectId });
    }
    let cancelled = false;
    getAssetNextCode()
      .then((code) => {
        if (!cancelled && code) form.setFieldsValue({ code });
      })
      .catch(() => {
        /* 静默：预览失败不阻塞表单 */
      });
    return () => {
      cancelled = true;
    };
  }, [assetId, form, presetCustomerId, presetProjectId]);

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
          code: (target as any).code,
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
        code: values.code || undefined,
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
      // t193：从项目侧进入时回到项目详情，否则回资产详情
      navigate(returnTo ?? (row?.id ? `/assets/${row.id}` : '/assets'));
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
          <Form form={form} layout="vertical" disabled={loading} initialValues={{ priority: 'P2', status: 'active', target_type: 'web', target_source: 'customer' }}>
            <FormGroup title={t('asset.form.basicGroup')}>
              {/* t192：资产编码由后端按规则生成并预填，用户不可编辑 */}
              <Form.Item name="code" label={t('asset.list.colCode')}>
                <Input placeholder={t('asset.form.codePlaceholder')} readOnly data-testid="asset-form-code" />
              </Form.Item>
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
                      disabled={presetLocked}
                      data-testid="asset-form-customer-select"
                    />
                  </Form.Item>
                  <Form.Item
                    name="project_id"
                    label={t('asset.projectLink.projectLabel')}
                    rules={[{ required: true, message: t('asset.projectLink.projectPlaceholder') }]}
                    extra={presetLocked ? t('asset.form.projectLocked') : t('asset.projectLink.createRequired')}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder={t('asset.projectLink.projectPlaceholder')}
                      disabled={!customerId || presetLocked}
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
                <Select
                  options={TARGET_TYPES.map((value) => ({ value, label: t(`asset.targetType.${value}`) }))}
                  data-testid="asset-form-target-type"
                />
              </Form.Item>
              <Form.Item name="target_source" label={t('asset.form.targetSource')} rules={[{ required: true, message: t('asset.form.targetSourceRequired') }]}>
                <Select
                  options={TARGET_SOURCES.map((value) => ({ value, label: t(`asset.targetSource.${value}`) }))}
                  data-testid="asset-form-target-source"
                />
              </Form.Item>
              <Form.Item name="priority" label={t('asset.list.colPriority')} rules={[{ required: true, message: t('asset.form.priorityRequired') }]}>
                <Select
                  options={PRIORITIES.map((value) => ({ value, label: t(`asset.priority.${value}`) }))}
                  data-testid="asset-form-priority"
                />
              </Form.Item>
              <Form.Item name="status" label={t('asset.list.colStatus')} rules={[{ required: true, message: t('asset.form.statusRequired') }]}>
                <Select
                  options={STATUSES.map((value) => ({ value, label: t(STATUS_LABEL_KEYS[value]) }))}
                  data-testid="asset-form-status"
                />
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

