import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Empty, Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { TableColumnsType } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { useT } from '@/i18n';
import {
  addAssetProjectLink,
  getAssetProjectLinks,
  extractAssetProjectLinks,
  removeAssetProjectLink,
} from '@/api/asset';
import type { AssetProjectLinkVO } from '@/types/asset';
import { getCustomerOptions, extractCustomerPage } from '@/api/customer';
import { getProjectDetail, getProjectList, extractPhaseList } from '@/api/project';
import type { CustomerVO } from '@/types/customer';
import type { PhaseVO, ProjectVO } from '@/types/project';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';

const LINK_TYPES = ['TARGET', 'SHARED', 'REFERENCE'] as const;

interface AssetProjectLinksPanelProps {
  assetId: string;
  /** 资产写权（负责人或管理层）；false 时隐藏挂靠/解绑入口（t73 复用 canWriteAsset） */
  canWrite?: boolean;
}

/**
 * 资产详情「所属项目」页签（t73）。
 *
 * 对接后端 t69：GET/POST /assets/{id}/project-links、DELETE /assets/{id}/project-links/{linkId}。
 * - 重复挂靠 409、最后一条解绑 409、挂靠不存在 404 分别给出可读文案
 * - 跨客户挂靠允许：客户 → 项目 → 期次三级联动，项目列表按客户过滤
 */
const AssetProjectLinksPanel: React.FC<AssetProjectLinksPanelProps> = ({ assetId, canWrite = false }) => {
  const { t } = useT();
  const navigate = useNavigate();
  const [links, setLinks] = useState<AssetProjectLinkVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<{
    customer_id?: string;
    project_id: string;
    phase_id?: string;
    link_type?: string;
    remark?: string;
  }>();

  const [customers, setCustomers] = useState<CustomerVO[]>([]);
  const [projects, setProjects] = useState<ProjectVO[]>([]);
  const [phases, setPhases] = useState<PhaseVO[]>([]);
  const [projectLoading, setProjectLoading] = useState(false);
  const [phaseLoading, setPhaseLoading] = useState(false);

  const loadLinks = useCallback(async () => {
    if (!assetId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await getAssetProjectLinks(assetId);
      setLinks(extractAssetProjectLinks(res));
    } catch (err) {
      setLinks([]);
      const status = getApiErrorStatus(err);
      setError(
        status === 403
          ? t('asset.projectLink.forbidden')
          : status === 404
            ? t('asset.projectLink.notFound')
            : getApiErrorMessage(err, t('common.operationFailed')),
      );
    } finally {
      setLoading(false);
    }
  }, [assetId, t]);

  useEffect(() => {
    loadLinks();
  }, [loadLinks]);

  const linkTypeLabel = (value?: string | null) => {
    const normalized = String(value ?? 'TARGET').toUpperCase();
    if (normalized === 'SHARED') return t('asset.projectLink.linkTypeSHARED');
    if (normalized === 'REFERENCE') return t('asset.projectLink.linkTypeREFERENCE');
    return t('asset.projectLink.linkTypeTARGET');
  };

  const openModal = async () => {
    setModalOpen(true);
    form.resetFields();
    form.setFieldsValue({ link_type: 'TARGET' });
    setProjects([]);
    setPhases([]);
    if (customers.length === 0) {
      try {
        const res = await getCustomerOptions(undefined, 200);
        setCustomers(extractCustomerPage(res).items);
      } catch {
        setCustomers([]);
      }
    }
  };

  const loadProjects = async (customerId?: string) => {
    if (!customerId) {
      setProjects([]);
      return;
    }
    setProjectLoading(true);
    try {
      const res = await getProjectList({ page: 1, page_size: 200, customerId });
      const payload = res?.data ?? res;
      const items = payload?.content || payload?.items || [];
      setProjects(Array.isArray(items) ? items : []);
    } catch {
      setProjects([]);
    } finally {
      setProjectLoading(false);
    }
  };

  const loadPhases = async (projectId?: string, isPhased?: boolean) => {
    if (!projectId || !isPhased) {
      setPhases([]);
      return;
    }
    setPhaseLoading(true);
    try {
      const res = await getProjectDetail(projectId);
      const payload = (res as any)?.data ?? res;
      setPhases(Array.isArray(payload?.phases) ? payload.phases : extractPhaseList(res));
    } catch {
      setPhases([]);
    } finally {
      setPhaseLoading(false);
    }
  };

  const handleUnlink = (record: AssetProjectLinkVO) => {
    Modal.confirm({
      title: t('asset.projectLink.unlinkTitle'),
      content: t('asset.projectLink.unlinkContent', { name: record.projectName ?? record.projectCode ?? '-' }),
      okText: t('asset.projectLink.unlink'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await removeAssetProjectLink(assetId, record.linkId);
          message.success(t('asset.projectLink.removed'));
          await loadLinks();
        } catch (err) {
          const status = getApiErrorStatus(err);
          if (status === 409) {
            // 最后一条挂靠 / 其他业务冲突：后端 message 已说明原因，优先展示并给出引导
            message.error(
              getApiErrorMessage(err, t('asset.projectLink.lastOne')) || t('asset.projectLink.lastOne'),
            );
            return;
          }
          if (status === 404) {
            message.error(t('asset.projectLink.linkNotFound'));
            return;
          }
          if (status === 403) {
            message.error(t('asset.projectLink.forbidden'));
            return;
          }
          message.error(getApiErrorMessage(err, t('common.operationFailed')));
        }
      },
    });
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await addAssetProjectLink(assetId, {
        project_id: values.project_id,
        phase_id: values.phase_id || null,
        link_type: values.link_type || 'TARGET',
        remark: values.remark || null,
      });
      message.success(t('asset.projectLink.created'));
      setModalOpen(false);
      await loadLinks();
    } catch (err: any) {
      if (err?.errorFields) return;
      const status = getApiErrorStatus(err);
      if (status === 409) {
        message.error(getApiErrorMessage(err, t('asset.projectLink.duplicate')));
        return;
      }
      if (status === 400) {
        message.error(getApiErrorMessage(err, t('asset.projectLink.invalidPhase')));
        return;
      }
      if (status === 404) {
        message.error(t('asset.projectLink.notFound'));
        return;
      }
      if (status === 403) {
        message.error(t('asset.projectLink.forbidden'));
        return;
      }
      message.error(getApiErrorMessage(err, t('common.operationFailed')));
    } finally {
      setSubmitting(false);
    }
  };

  const columns: TableColumnsType<AssetProjectLinkVO> = [
    {
      title: t('asset.projectLink.colProject'),
      key: 'project',
      render: (_: unknown, record) => (
        <a onClick={() => navigate(`/projects/${record.projectId}`)}>
          {record.projectName ?? record.projectCode ?? record.projectId}
        </a>
      ),
    },
    {
      title: t('asset.projectLink.colCustomer'),
      key: 'customer',
      width: 180,
      render: (_: unknown, record) => record.customerName ?? record.customerId ?? '-',
    },
    {
      title: t('asset.projectLink.colPhase'),
      key: 'phase',
      width: 150,
      render: (_: unknown, record) => record.phaseName || t('asset.projectLink.unphased'),
    },
    {
      title: t('asset.projectLink.colLinkType'),
      key: 'linkType',
      width: 120,
      render: (_: unknown, record) => <Tag>{linkTypeLabel(record.linkType)}</Tag>,
    },
    {
      title: t('asset.projectLink.colRemark'),
      key: 'remark',
      ellipsis: true,
      render: (_: unknown, record) => record.remark || '-',
    },
    {
      title: t('asset.projectLink.colLinkedAt'),
      key: 'linkedAt',
      width: 180,
      render: (_: unknown, record) => (record.linkedAt ? dayjs(record.linkedAt).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    ...(canWrite
      ? [
          {
            title: t('asset.projectLink.colAction'),
            key: 'action',
            width: 150,
            render: (_: unknown, record: AssetProjectLinkVO) => (
              <Space size="small">
                <a onClick={() => navigate(`/projects/${record.projectId}`)}>{t('asset.projectLink.viewProject')}</a>
                <Button type="link" size="small" danger style={{ padding: 0 }} onClick={() => handleUnlink(record)}>
                  {t('asset.projectLink.unlink')}
                </Button>
              </Space>
            ),
          } as TableColumnsType<AssetProjectLinkVO>[number],
        ]
      : []),
  ];

  const addableProjects = useMemo(
    () => projects.filter((project) => !links.some((link) => link.projectId === project.id && !link.phaseId)),
    [projects, links],
  );

  return (
    <div style={{ padding: '24px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div>
          <h3 style={{ margin: 0 }}>{t('asset.projectLink.title')}</h3>
          <p style={{ marginTop: 8, marginBottom: 0, color: '#8c8c8c' }}>{t('asset.projectLink.desc')}</p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadLinks} loading={loading}>
            {t('common.refresh')}
          </Button>
          {canWrite ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={openModal}>
              {t('asset.projectLink.add')}
            </Button>
          ) : null}
        </Space>
      </div>

      {!canWrite ? (
        <Alert type="info" showIcon style={{ marginBottom: 16 }} message={t('asset.projectLink.readonly')} />
      ) : null}
      {error ? <Alert type="error" showIcon style={{ marginBottom: 16 }} message={error} /> : null}

      {links.length === 0 && !loading ? (
        <Empty description={t('asset.projectLink.empty')} />
      ) : (
        <Table<AssetProjectLinkVO>
          size="small"
          rowKey="linkId"
          loading={loading}
          pagination={false}
          dataSource={links}
          columns={columns}
          scroll={{ x: 1100 }}
        />
      )}

      <Modal
        title={t('asset.projectLink.addTitle')}
        open={modalOpen}
        onOk={handleSubmit}
        confirmLoading={submitting}
        onCancel={() => setModalOpen(false)}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        destroyOnHidden
        maskClosable={false}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="customer_id" label={t('asset.projectLink.customerLabel')}>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder={t('asset.projectLink.customerPlaceholder')}
              options={customers.map((customer) => ({ value: customer.id, label: customer.name }))}
              onChange={(value) => {
                form.setFieldValue('project_id', undefined);
                form.setFieldValue('phase_id', undefined);
                setPhases([]);
                loadProjects(value);
              }}
            />
          </Form.Item>
          <Form.Item
            name="project_id"
            label={t('asset.projectLink.projectLabel')}
            rules={[{ required: true, message: t('asset.projectLink.projectRequired') }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              loading={projectLoading}
              placeholder={t('asset.projectLink.projectPlaceholder')}
              options={addableProjects.map((project) => ({ value: project.id, label: `${project.name}（${project.code}）` }))}
              onChange={(value) => {
                form.setFieldValue('phase_id', undefined);
                const project = projects.find((item) => item.id === value);
                loadPhases(value, Boolean(project?.isPhased));
              }}
            />
          </Form.Item>
          <Form.Item name="phase_id" label={t('asset.projectLink.phaseLabel')}>
            <Select
              allowClear
              loading={phaseLoading}
              disabled={phases.length === 0}
              placeholder={t('asset.projectLink.phasePlaceholder')}
              options={phases.map((phase) => ({ value: phase.id, label: `${phase.phaseNo ?? '-'}. ${phase.name ?? ''}` }))}
            />
          </Form.Item>
          <Form.Item name="link_type" label={t('asset.projectLink.linkTypeLabel')}>
            <Select options={LINK_TYPES.map((type) => ({ value: type, label: linkTypeLabel(type) }))} />
          </Form.Item>
          <Form.Item name="remark" label={t('asset.projectLink.remarkLabel')}>
            <Input.TextArea rows={2} maxLength={500} placeholder={t('asset.projectLink.remarkPlaceholder')} />
          </Form.Item>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t('asset.projectLink.desc')}
          </Typography.Text>
        </Form>
      </Modal>
    </div>
  );
};

export default AssetProjectLinksPanel;
