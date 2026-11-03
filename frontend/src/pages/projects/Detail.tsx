import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useNavigate, useParams } from 'react-router-dom';
import { useT } from '@/i18n';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { useUserStore } from '@/store/userStore';
import { canManageProject, resolveCurrentUserId } from '@/utils/accessRoles';
import {
  changeProjectStatus,
  createProjectPhase,
  deleteProject,
  deleteProjectPhase,
  extractPhase,
  extractPhaseList,
  extractProject,
  extractProjectAssets,
  extractProjectDetail,
  getProjectAssets,
  getProjectDetail,
  getProjectPhases,
  updateProjectPhase,
} from '@/api/project';
import {
  PHASE_STATUS_COLORS,
  PHASE_STATUS_ORDER,
  PROJECT_STATUS_COLORS,
  isKnownPhaseStatus,
  isKnownProjectType,
  isProjectClosed,
  nextProjectStatuses,
  phaseStatusFallback,
  phaseStatusKey,
  projectStatusKey,
  projectTypeKey,
  transitionLabelKey,
  type PhaseVO,
  type ProjectAssetLinkVO,
  type ProjectDetailVO,
  type ProjectVO,
} from '@/types/project';
import { AppEmpty, AppLoading } from '@/components/Feedback';
import ProjectFormModal from './ProjectFormModal';
import { describeProjectFailure } from './projectFailure';

const { RangePicker } = DatePicker;

interface PhaseFormValues {
  phaseNo?: number | null;
  name: string;
  status?: string;
  period?: [Dayjs, Dayjs] | null;
}

/**
 * 项目详情（t72）：基本信息 + 状态机 + 期次管理 + 期次时间线 + 资产清单。
 *
 * 资产清单调用 `GET /projects/{id}/assets`（后端 t69）；接口未上线时按 404 降级为提示，不阻塞页面。
 */
const ProjectDetail: React.FC = () => {
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { profile, loaded: profileLoaded } = useAccessProfile();
  const storeUser = useUserStore((state) => state.user);
  const currentUserId = resolveCurrentUserId(profile, storeUser?.id);

  const [detail, setDetail] = useState<ProjectDetailVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [assets, setAssets] = useState<ProjectAssetLinkVO[]>([]);
  const [assetsLoading, setAssetsLoading] = useState(false);
  const [assetsUnavailable, setAssetsUnavailable] = useState(false);
  const [phaseFilter, setPhaseFilter] = useState<string>('ALL');
  const [statusChanging, setStatusChanging] = useState(false);
  const [formOpen, setFormOpen] = useState(false);

  const [phaseModalOpen, setPhaseModalOpen] = useState(false);
  const [editingPhase, setEditingPhase] = useState<PhaseVO | null>(null);
  const [phaseSubmitting, setPhaseSubmitting] = useState(false);
  const [phaseForm] = Form.useForm<PhaseFormValues>();

  const project: ProjectVO | null = detail?.project ?? null;
  const phases = detail?.phases ?? [];
  const writable = profileLoaded && canManageProject(profile, project?.managerId, currentUserId);
  const closed = isProjectClosed(project);
  const isPhased = Boolean(project?.isPhased);

  const loadDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const res = await getProjectDetail(id);
      const payload = extractProjectDetail(res);
      if (payload?.project) {
        setDetail({ ...payload, phases: payload.phases?.length ? payload.phases : extractPhaseList(res) });
        setNotFound(false);
      } else {
        setDetail(null);
        setNotFound(true);
      }
    } catch {
      setDetail(null);
      setNotFound(true);
    } finally {
      setLoading(false);
    }
  }, [id]);

  const loadPhases = useCallback(async () => {
    if (!id) return;
    try {
      const res = await getProjectPhases(id);
      const list = extractPhaseList(res);
      setDetail((prev) => (prev ? { ...prev, phases: list } : prev));
    } catch {
      // 403/404 由详情本身兜底
    }
  }, [id]);

  const loadAssets = useCallback(async () => {
    if (!id) return;
    setAssetsLoading(true);
    try {
      const res = await getProjectAssets(id, { page_size: 200 });
      setAssets(extractProjectAssets(res));
      setAssetsUnavailable(false);
    } catch (error: any) {
      const status = error?.response?.status;
      setAssets([]);
      // 404 = 挂靠接口尚未上线（后端 t69）或项目不存在；403 = 无读权限
      setAssetsUnavailable(status === 404 || status === 403 || status === undefined);
    } finally {
      setAssetsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadDetail();
    loadAssets();
  }, [loadDetail, loadAssets]);

  const phaseAssetCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    assets.forEach((link) => {
      const key = link.phaseId ?? 'UNPHASED';
      counts[key] = (counts[key] ?? 0) + 1;
    });
    return counts;
  }, [assets]);

  const filteredAssets = useMemo(() => {
    if (phaseFilter === 'ALL') return assets;
    if (phaseFilter === 'UNPHASED') return assets.filter((link) => !link.phaseId);
    return assets.filter((link) => link.phaseId === phaseFilter);
  }, [assets, phaseFilter]);

  const handleStatusChange = (target: string) => {
    if (!project) return;
    const from = String(project.status ?? 'DRAFT').toUpperCase();
    Modal.confirm({
      title: t('project.confirm.statusTitle'),
      content: t('project.confirm.statusContent', {
        name: project.name,
        from: t(projectStatusKey(from)),
        to: t(projectStatusKey(target)),
      }),
      okText: t(transitionLabelKey(from, target)),
      cancelText: t('common.cancel'),
      onOk: async () => {
        setStatusChanging(true);
        try {
          const res = await changeProjectStatus(project.id, target);
          const updated = extractProject(res);
          if (updated) {
            setDetail((prev) => (prev ? { ...prev, project: { ...prev.project, ...updated } } : prev));
          } else {
            await loadDetail();
          }
          message.success(t('project.statusChanged'));
        } catch (error) {
          message.error(
            describeProjectFailure(error, {
              forbidden: t('project.writeForbidden'),
              notFound: t('project.detail.notFound'),
              blockedTemplate: t('project.deleteBlocked'),
              blockedFallback: t('project.deleteBlockedHint'),
              invalidTransition: t('project.statusInvalid'),
              fallback: t('common.operationFailed'),
            }),
          );
        } finally {
          setStatusChanging(false);
        }
      },
    });
  };

  const handleDeleteProject = () => {
    if (!project) return;
    Modal.confirm({
      title: t('project.deleteTitle'),
      content: t('project.deleteContent'),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await deleteProject(project.id);
          message.success(t('project.deleted'));
          navigate('/projects');
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
        }
      },
    });
  };

  const openCreatePhase = () => {
    setEditingPhase(null);
    phaseForm.resetFields();
    phaseForm.setFieldsValue({ status: PHASE_STATUS_ORDER[0] });
    setPhaseModalOpen(true);
  };

  const openEditPhase = (phase: PhaseVO) => {
    setEditingPhase(phase);
    phaseForm.setFieldsValue({
      phaseNo: phase.phaseNo ?? undefined,
      name: phase.name ?? '',
      status: phaseStatusFallback(phase.status),
      period:
        phase.startDate && phase.endDate
          ? [dayjs(phase.startDate), dayjs(phase.endDate)]
          : undefined,
    });
    setPhaseModalOpen(true);
  };

  const handlePhaseSubmit = async () => {
    if (!project) return;
    try {
      const values = await phaseForm.validateFields();
      setPhaseSubmitting(true);
      const payload = {
        phaseNo: values.phaseNo ?? null,
        name: values.name.trim(),
        status: values.status,
        startDate: values.period?.[0]?.format('YYYY-MM-DD') ?? null,
        endDate: values.period?.[1]?.format('YYYY-MM-DD') ?? null,
      };
      if (editingPhase?.id) {
        const res = await updateProjectPhase(editingPhase.id, payload);
        const updated = extractPhase(res);
        if (updated) {
          setDetail((prev) =>
            prev
              ? {
                  ...prev,
                  phases: (prev.phases ?? []).map((phase) => (phase.id === updated.id ? { ...phase, ...updated } : phase)),
                }
              : prev,
          );
        } else {
          await loadPhases();
        }
        message.success(t('phase.updated'));
      } else {
        await createProjectPhase(project.id, payload);
        message.success(t('phase.created'));
        await loadPhases();
      }
      setPhaseModalOpen(false);
      setEditingPhase(null);
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(
        describeProjectFailure(
          error,
          {
            forbidden: t('phase.writeForbidden'),
            notFound: t('project.detail.notFound'),
            blockedTemplate: t('phase.deleteBlocked'),
            blockedFallback: t('phase.deleteBlockedHint'),
            invalidTransition: t('common.operationFailed'),
            fallback: t('common.operationFailed'),
          },
          (template, count) => template.replace('{count}', String(count)),
        ),
      );
    } finally {
      setPhaseSubmitting(false);
    }
  };

  const handleDeletePhase = (phase: PhaseVO) => {
    Modal.confirm({
      title: t('phase.deleteTitle'),
      content: t('phase.deleteContent'),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await deleteProjectPhase(phase.id);
          message.success(t('phase.deleted'));
          await loadPhases();
        } catch (error) {
          message.error(
            describeProjectFailure(
              error,
              {
                forbidden: t('phase.writeForbidden'),
                notFound: t('project.detail.notFound'),
                blockedTemplate: t('phase.deleteBlocked'),
                blockedFallback: t('phase.deleteBlockedHint'),
                invalidTransition: t('common.operationFailed'),
                fallback: t('common.operationFailed'),
              },
              (template, count) => template.replace('{count}', String(count)),
            ),
          );
        }
      },
    });
  };

  const phaseColumns: TableColumnsType<PhaseVO> = [
    { title: t('phase.colNo'), key: 'phaseNo', width: 90, render: (_: unknown, record) => record.phaseNo ?? '-' },
    { title: t('phase.colName'), key: 'name', dataIndex: 'name', render: (value: string) => value || '-' },
    {
      title: t('phase.colPeriod'),
      key: 'period',
      width: 210,
      render: (_: unknown, record) =>
        record.startDate || record.endDate ? `${record.startDate ?? '-'} ~ ${record.endDate ?? '-'}` : '-',
    },
    {
      title: t('phase.colStatus'),
      key: 'status',
      width: 120,
      render: (_: unknown, record) => {
        const status = phaseStatusFallback(record.status);
        return (
          <Tag color={PHASE_STATUS_COLORS[status] ?? 'default'}>
            {isKnownPhaseStatus(status) ? t(phaseStatusKey(status)) : status}
          </Tag>
        );
      },
    },
    {
      title: t('phase.colAssetCount'),
      key: 'assetCount',
      width: 100,
      render: (_: unknown, record) => phaseAssetCounts[record.id] ?? (assetsUnavailable ? '-' : 0),
    },
    ...(writable && !closed
      ? [
          {
            title: t('phase.colAction'),
            key: 'action',
            width: 150,
            render: (_: unknown, record: PhaseVO) => (
              <Space size="small">
                <a onClick={() => openEditPhase(record)}>{t('common.edit')}</a>
                <Button type="link" size="small" danger style={{ padding: 0 }} onClick={() => handleDeletePhase(record)}>
                  {t('phase.delete')}
                </Button>
              </Space>
            ),
          } as TableColumnsType<PhaseVO>[number],
        ]
      : []),
  ];

  const assetColumns: TableColumnsType<ProjectAssetLinkVO> = [
    {
      title: t('project.detail.colAssetCode'),
      key: 'code',
      width: 170,
      render: (_: unknown, record) => record.assetCode ?? record.asset?.code ?? '-',
    },
    {
      title: t('project.detail.colAssetName'),
      key: 'name',
      render: (_: unknown, record) => record.assetName ?? record.asset?.name ?? '-',
    },
    {
      title: t('project.detail.colAssetType'),
      key: 'targetType',
      width: 130,
      render: (_: unknown, record) => record.targetType ?? record.asset?.targetType ?? record.asset?.target_type ?? '-',
    },
    {
      title: t('project.detail.colAssetStatus'),
      key: 'status',
      width: 120,
      render: (_: unknown, record) => record.assetStatus ?? record.status ?? record.asset?.status ?? '-',
    },
    {
      title: t('project.detail.colLinkType'),
      key: 'linkType',
      width: 150,
      render: (_: unknown, record) => {
        if (record.linkType) return <Tag>{record.linkType}</Tag>;
        if (record.phaseId) return <Tag color="blue">{record.phaseName ?? record.phaseNo ?? t('project.detail.unphased')}</Tag>;
        return <Tag color="default">{t('project.detail.unphased')}</Tag>;
      },
    },
    {
      title: t('project.colAction'),
      key: 'action',
      width: 120,
      render: (_: unknown, record) => {
        const assetId = record.assetId ?? record.asset?.id;
        return assetId ? <a onClick={() => navigate(`/assets/${assetId}`)}>{t('project.detail.viewAsset')}</a> : '-';
      },
    },
  ];

  const phaseFilterOptions = [
    { value: 'ALL', label: t('project.detail.allPhases') },
    ...phases.map((phase) => ({
      value: phase.id,
      label: `${phase.phaseNo ?? '-'}. ${phase.name ?? ''}`,
    })),
    { value: 'UNPHASED', label: t('project.detail.unphased') },
  ];

  if (loading) {
    return (
      <Card>
        <AppLoading tip={t('common.loading')} />
      </Card>
    );
  }

  if (notFound || !project) {
    return (
      <Card>
        <Alert
          type="warning"
          showIcon
          message={t('project.detail.notFound')}
          action={
            <Space>
              <Button size="small" onClick={loadDetail}>
                {t('project.detail.reload')}
              </Button>
              <Button size="small" type="link" onClick={() => navigate('/projects')}>
                {t('project.detail.back')}
              </Button>
            </Space>
          }
        />
      </Card>
    );
  }

  const transitions = writable && !closed ? nextProjectStatuses(project.status) : [];

  return (
    <Spin spinning={loading}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card
          title={
            <Space wrap>
              <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/projects')} />
              <Typography.Text strong style={{ fontSize: 16 }}>
                {project.name}
              </Typography.Text>
              <Tag color={PROJECT_STATUS_COLORS[String(project.status ?? 'DRAFT').toUpperCase()] ?? 'default'}>
                {t(projectStatusKey(project.status))}
              </Tag>
              {project.isPhased ? <Tag color="blue">{t('project.multiphaseYes')}</Tag> : null}
            </Space>
          }
          extra={
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={() => { loadDetail(); loadAssets(); }}>
                {t('common.refresh')}
              </Button>
              {writable ? (
                <Button onClick={() => setFormOpen(true)} disabled={closed}>
                  {t('project.detail.edit')}
                </Button>
              ) : null}
              {transitions.map((target) => (
                <Button
                  key={target}
                  type="primary"
                  loading={statusChanging}
                  onClick={() => handleStatusChange(target)}
                >
                  {t(transitionLabelKey(project.status, target))}
                </Button>
              ))}
              {writable ? (
                <Button danger disabled={closed} onClick={handleDeleteProject}>
                  {t('project.delete')}
                </Button>
              ) : null}
            </Space>
          }
        >
          {!writable ? (
            <Alert type="info" showIcon style={{ marginBottom: 16 }} message={t('project.readonlyHint')} />
          ) : null}
          {closed ? (
            <Alert type="warning" showIcon style={{ marginBottom: 16 }} message={t('project.closedHint')} />
          ) : null}
          <Descriptions
            size="small"
            column={{ xs: 1, sm: 2, md: 3 }}
            items={[
              { key: 'code', label: t('project.colCode'), children: project.code },
              { key: 'customer', label: t('project.detail.customer'), children: project.customerName ?? detail?.customer?.name ?? '-' },
              { key: 'manager', label: t('project.detail.manager'), children: project.managerName ?? '-' },
              {
                key: 'type',
                label: t('project.colType'),
                children: project.type
                  ? isKnownProjectType(project.type)
                    ? t(projectTypeKey(project.type))
                    : project.type
                  : '-',
              },
              {
                key: 'period',
                label: t('project.detail.period'),
                children:
                  project.startDate || project.endDate
                    ? `${project.startDate ?? '-'} ~ ${project.endDate ?? '-'}`
                    : '-',
              },
              { key: 'isPhased', label: t('project.detail.isPhased'), children: project.isPhased ? t('project.multiphaseYes') : t('project.multiphaseNo') },
              { key: 'contractNo', label: t('project.detail.contractNo'), children: project.contractNo || '-' },
              { key: 'assetCount', label: t('project.colAssetCount'), children: detail?.assetCount ?? assets.length },
              { key: 'description', label: t('project.detail.description'), children: project.description || '-' },
            ]}
          />
        </Card>

        {isPhased ? (
          <Row gutter={16}>
            <Col xs={24} lg={15}>
              <Card
                size="small"
                title={t('project.detail.phaseSection')}
                extra={
                  writable && !closed ? (
                    <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCreatePhase}>
                      {t('phase.new')}
                    </Button>
                  ) : null
                }
              >
                <Table<PhaseVO>
                  size="small"
                  rowKey="id"
                  columns={phaseColumns}
                  dataSource={phases}
                  pagination={false}
                  locale={{ emptyText: <AppEmpty description={t('phase.empty')} /> }}
                />
              </Card>
            </Col>
            <Col xs={24} lg={9}>
              <Card size="small" title={t('project.detail.phaseTimeline')}>
                {phases.length === 0 ? (
                  <AppEmpty description={t('phase.empty')} />
                ) : (
                  <Timeline
                    items={phases.map((phase) => ({
                      color: phaseStatusFallback(phase.status) === 'DONE' ? 'green' : 'blue',
                      children: (
                        <Space direction="vertical" size={0}>
                          <Typography.Text strong>
                            {`${phase.phaseNo ?? '-'}. ${phase.name ?? ''}`}
                          </Typography.Text>
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {`${phase.startDate ?? '-'} ~ ${phase.endDate ?? '-'} · ${t(phaseStatusKey(phasePhaseStatus(phase)))}`}
                          </Typography.Text>
                        </Space>
                      ),
                    }))}
                  />
                )}
              </Card>
            </Col>
          </Row>
        ) : (
          <Alert type="info" showIcon message={t('phase.notPhasedHint')} />
        )}

        <Card
          size="small"
          title={t('project.detail.assetSection')}
          extra={
            <Space>
              <Select
                size="small"
                style={{ width: 220 }}
                value={phaseFilter}
                options={phaseFilterOptions}
                onChange={setPhaseFilter}
              />
              <Typography.Text type="secondary">
                {t('project.detail.assetTotal', { count: filteredAssets.length })}
              </Typography.Text>
            </Space>
          }
        >
          {assetsUnavailable ? (
            <Alert type="info" showIcon message={t('project.detail.assetsUnavailable')} />
          ) : (
            <Table<ProjectAssetLinkVO>
              size="small"
              rowKey={(record) => record.linkId ?? record.assetId ?? `${record.asset?.id ?? ''}`}
              loading={assetsLoading}
              columns={assetColumns}
              dataSource={filteredAssets}
              pagination={{ pageSize: 10, hideOnSinglePage: true }}
              locale={{ emptyText: <AppEmpty description={t('project.detail.emptyAssets')} /> }}
            />
          )}
        </Card>

        <ProjectFormModal
          open={formOpen}
          project={project}
          onCancel={() => setFormOpen(false)}
          onSaved={async () => {
            setFormOpen(false);
            await loadDetail();
          }}
        />

        <Modal
          title={editingPhase ? t('phase.editTitle') : t('phase.createTitle')}
          open={phaseModalOpen}
          onOk={handlePhaseSubmit}
          confirmLoading={phaseSubmitting}
          onCancel={() => {
            setPhaseModalOpen(false);
            setEditingPhase(null);
          }}
          okText={t('common.confirm')}
          cancelText={t('common.cancel')}
          destroyOnHidden
          maskClosable={false}
        >
          <Form form={phaseForm} layout="vertical" preserve={false}>
            <Form.Item name="phaseNo" label={t('phase.no')}>
              <InputNumber min={1} max={999} style={{ width: '100%' }} placeholder={t('phase.noPlaceholder')} />
            </Form.Item>
            <Form.Item
              name="name"
              label={t('phase.name')}
              rules={[{ required: true, message: t('phase.nameRequired') }]}
            >
              <Input maxLength={200} />
            </Form.Item>
            <Form.Item name="status" label={t('phase.status')}>
              <Select
                options={PHASE_STATUS_ORDER.map((status) => ({ value: status, label: t(phaseStatusKey(status)) }))}
              />
            </Form.Item>
            <Form.Item name="period" label={t('phase.period')}>
              <RangePicker style={{ width: '100%' }} />
            </Form.Item>
          </Form>
        </Modal>
      </Space>
    </Spin>
  );
};

/** 期次状态归一（后端允许任意大写字符串，未知值回落 PLANNED 的展示 key） */
const phasePhaseStatus = (phase: PhaseVO): string => phaseStatusFallback(phase.status);

export default ProjectDetail;
