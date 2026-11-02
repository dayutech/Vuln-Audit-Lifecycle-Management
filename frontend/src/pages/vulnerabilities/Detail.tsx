import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Steps,
  Table,
  Tabs,
  Tag,
  Timeline,
  Tooltip,
  Typography,
  Upload,
  message,
} from 'antd';
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  UploadOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  addVulnerabilityTag,
  assignVulnerability,
  deleteVulnerabilityAttachment,
  deleteVulnerabilityTag,
  deliverVulnerability,
  downloadVulnerabilityAttachment,
  getVulnerabilityAttachments,
  getVulnerabilityDetail,
  getVulnerabilityHistory,
  getVulnerabilityTags,
  updateCustomerStatus,
  updateDisclosureStatus,
  updateExploitStage,
  updateVendorFixStatus,
  updateWorkflowStatus,
  uploadVulnerabilityAttachment,
} from '@/api/vulnerability';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import { getApiErrorMessage, getApiErrorStatus, getBlobErrorMessage } from '@/utils/errorMessage';
import { saveBlobAsFile } from '@/utils/download';
import type {
  VulnerabilityAttachmentVO,
  VulnerabilityHistoryVO,
  VulnerabilityTagVO,
  VulnerabilityVO,
} from '@/types/vulnerability';
import {
  EXPLOIT_STAGE_OPTIONS,
  HISTORY_STATUS_TYPES,
  WORKFLOW_STATUS_OPTIONS,
  customerColor,
  customerKey,
  disclosureColor,
  disclosureKey,
  exploitStageColor,
  exploitStageKey,
  fieldTestColor,
  fieldTestKey,
  formatFileSize,
  nextWorkflowStatuses,
  severityColor,
  severityKey,
  workflowColor,
  workflowIndex,
  workflowKey,
} from './constants';
import { useT } from '@/i18n';
import type { MessageKey } from '@/i18n/messages/zh-CN';

const { TextArea } = Input;

type ActionKind = 'workflow' | 'customer' | 'disclosure' | 'exploit' | 'vendor' | 'deliver' | 'assign';

interface ActionState {
  kind: ActionKind;
  /** workflow / customer / disclosure 的目标状态 */
  target?: string;
}

interface VulnerabilityDetailProps {
  vulnerabilityId: string;
  /** 抽屉内渲染时去掉外层 Card 的边框 */
  embedded?: boolean;
  /** 数据变更（状态流转 / 标签 / 附件）后通知列表刷新 */
  onChanged?: () => void;
  onClose?: () => void;
}

const getPayload = <T,>(res: unknown): T => ((res as any)?.data ?? res) as T;

const pickText = (source: any, camel: string, snake: string): string | undefined => {
  const value = source?.[camel] ?? source?.[snake];
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  return String(value);
};

const formatDateTime = (value?: string | null) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');

const renderBlock = (content?: string) => (
  <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.8, minHeight: 24 }}>{content?.trim() || '-'}</div>
);

const ACTION_TITLE_KEYS: Record<ActionKind, MessageKey> = {
  workflow: 'vuln.detail.action.workflow',
  customer: 'vuln.detail.action.customer',
  disclosure: 'vuln.detail.action.disclosure',
  exploit: 'vuln.detail.action.exploit',
  vendor: 'vuln.detail.action.vendor',
  deliver: 'vuln.detail.action.deliver',
  assign: 'vuln.detail.action.assign',
};

const VulnerabilityDetail: React.FC<VulnerabilityDetailProps> = ({
  vulnerabilityId,
  embedded = false,
  onChanged,
  onClose,
}) => {
  const { t } = useT();
  /** 枚举展示统一走 t()：常量只保留 labelKey，未知值回落原始英文/中文值 */
  const labelOf = useCallback(
    (key: MessageKey | undefined, fallback?: string | null) => (key ? t(key) : fallback || '-'),
    [t],
  );
  const workflowLabel = useCallback((v?: string | null) => labelOf(workflowKey(v), v), [labelOf]);
  const customerLabel = useCallback((v?: string | null) => labelOf(customerKey(v), v), [labelOf]);
  const disclosureLabel = useCallback((v?: string | null) => labelOf(disclosureKey(v), v), [labelOf]);
  const exploitStageLabel = useCallback((v?: string | null) => labelOf(exploitStageKey(v), v), [labelOf]);
  const fieldTestLabel = useCallback((v?: string | null) => labelOf(fieldTestKey(v), v), [labelOf]);
  const severityLabel = useCallback((v?: string | null) => labelOf(severityKey(v), v), [labelOf]);
  const [detail, setDetail] = useState<VulnerabilityVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [notFound, setNotFound] = useState(false);

  const [history, setHistory] = useState<VulnerabilityHistoryVO[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  const [tags, setTags] = useState<VulnerabilityTagVO[]>([]);
  const [tagInput, setTagInput] = useState('');
  const [tagSubmitting, setTagSubmitting] = useState(false);

  const [attachments, setAttachments] = useState<VulnerabilityAttachmentVO[]>([]);
  const [attachmentLoading, setAttachmentLoading] = useState(false);
  const [uploading, setUploading] = useState(false);

  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);

  const [activeTab, setActiveTab] = useState('overview');
  const [action, setAction] = useState<ActionState | null>(null);
  const [actionSubmitting, setActionSubmitting] = useState(false);
  const [actionForm] = Form.useForm();

  const loadDetail = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getVulnerabilityDetail(vulnerabilityId);
      setDetail(getPayload<VulnerabilityVO>(res) ?? null);
      setNotFound(false);
    } catch (error) {
      setDetail(null);
      setNotFound(true);
    } finally {
      setLoading(false);
    }
  }, [vulnerabilityId]);

  const loadHistory = useCallback(async () => {
    setHistoryLoading(true);
    try {
      const res = await getVulnerabilityHistory(vulnerabilityId);
      const payload = getPayload<VulnerabilityHistoryVO[]>(res);
      setHistory(Array.isArray(payload) ? payload : []);
    } catch (error) {
      setHistory([]);
    } finally {
      setHistoryLoading(false);
    }
  }, [vulnerabilityId]);

  const loadTags = useCallback(async () => {
    try {
      const res = await getVulnerabilityTags(vulnerabilityId);
      const payload = getPayload<VulnerabilityTagVO[]>(res);
      setTags(Array.isArray(payload) ? payload : []);
    } catch (error) {
      setTags([]);
    }
  }, [vulnerabilityId]);

  const loadAttachments = useCallback(async () => {
    setAttachmentLoading(true);
    try {
      const res = await getVulnerabilityAttachments(vulnerabilityId);
      const payload = getPayload<VulnerabilityAttachmentVO[]>(res);
      setAttachments(Array.isArray(payload) ? payload : []);
    } catch (error) {
      setAttachments([]);
    } finally {
      setAttachmentLoading(false);
    }
  }, [vulnerabilityId]);

  const reloadAll = useCallback(async () => {
    await Promise.all([loadDetail(), loadHistory(), loadTags(), loadAttachments()]);
  }, [loadDetail, loadHistory, loadTags, loadAttachments]);

  useEffect(() => {
    setDetail(null);
    setNotFound(false);
    setActiveTab('overview');
    reloadAll();
  }, [reloadAll]);

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        // 人员名映射走用户目录（工程师可读）
        const list = await getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT });
        setUsers(list);
      } catch (error) {
        setUsers([]);
      }
    };
    fetchUsers();
  }, []);

  const userNames = useMemo(() => {
    const map = new Map<string, string>();
    users.forEach((user) => {
      map.set(user.id, user.fullName || user.username || user.id);
    });
    return map;
  }, [users]);

  const userNameOf = useCallback(
    (id?: string | null) => {
      if (!id) {
        return '-';
      }
      return userNames.get(id) ?? `${id.slice(0, 8)}…`;
    },
    [userNames],
  );

  const currentWorkflow = pickText(detail, 'workflowStatus', 'workflow_status');
  const currentCustomer = pickText(detail, 'customerStatus', 'customer_status');
  const currentDisclosure = pickText(detail, 'disclosureStatus', 'disclosure_status');
  const currentExploitStage = pickText(detail, 'exploitStage', 'exploit_stage');
  const currentVendorFixed = (detail as any)?.vendorFixed ?? (detail as any)?.vendor_fixed ?? false;
  const currentAssigneeId = pickText(detail, 'assignedTo', 'assigned_to');

  const nextStatuses = nextWorkflowStatuses(currentWorkflow);
  const currentStepIndex = Math.max(workflowIndex(currentWorkflow), 0);

  const openAction = (kind: ActionKind, target?: string) => {
    actionForm.resetFields();
    if (kind === 'exploit') {
      actionForm.setFieldsValue({ exploitStage: currentExploitStage ?? '探测' });
    }
    if (kind === 'vendor') {
      actionForm.setFieldsValue({
        vendorFixed: currentVendorFixed,
        vendorFixVersion: pickText(detail, 'vendorFixVersion', 'vendor_fix_version'),
      });
    }
    if (kind === 'assign') {
      actionForm.setFieldsValue({ assignedTo: currentAssigneeId });
    }
    if (kind === 'disclosure') {
      actionForm.setFieldsValue({
        disclosureSource: pickText(detail, 'disclosureSource', 'disclosure_source'),
      });
    }
    setAction({ kind, target });
  };

  const handleActionSubmit = async () => {
    if (!action) {
      return;
    }
    try {
      const values = await actionForm.validateFields();
      setActionSubmitting(true);
      switch (action.kind) {
        case 'workflow':
          await updateWorkflowStatus(vulnerabilityId, action.target as string, values.comment?.trim() || undefined);
          break;
        case 'customer':
          await updateCustomerStatus(
            vulnerabilityId,
            action.target as string,
            action.target === 'rejected' ? values.rejectReason?.trim() || undefined : undefined,
          );
          break;
        case 'disclosure':
          await updateDisclosureStatus(
            vulnerabilityId,
            action.target as string,
            values.disclosureDate ? values.disclosureDate.format('YYYY-MM-DDTHH:mm:ss') : undefined,
            values.disclosureSource?.trim() || undefined,
          );
          break;
        case 'exploit':
          await updateExploitStage(vulnerabilityId, values.exploitStage, values.exploitCode?.trim() || undefined);
          break;
        case 'vendor':
          await updateVendorFixStatus(
            vulnerabilityId,
            Boolean(values.vendorFixed),
            values.vendorFixed ? values.vendorFixVersion?.trim() || undefined : undefined,
          );
          break;
        case 'deliver':
          await deliverVulnerability(vulnerabilityId, values.deliveryNote?.trim() || undefined);
          break;
        case 'assign':
          await assignVulnerability(vulnerabilityId, values.assignedTo);
          break;
        default:
          break;
      }
      message.success(t('vuln.detail.action.success', { action: t(ACTION_TITLE_KEYS[action.kind]) }));
      setAction(null);
      await Promise.all([loadDetail(), loadHistory()]);
      onChanged?.();
    } catch (error) {
      // 表单校验失败或接口报错（错误提示由 request 拦截器统一弹出）
    } finally {
      setActionSubmitting(false);
    }
  };

  const handleAddTag = async () => {
    const value = tagInput.trim();
    if (!value) {
      message.warning(t('vuln.detail.tag.inputRequired'));
      return;
    }
    setTagSubmitting(true);
    try {
      await addVulnerabilityTag(vulnerabilityId, value);
      message.success(t('vuln.detail.tag.addSuccess'));
      setTagInput('');
      await loadTags();
      onChanged?.();
    } catch (error) {
      // 拦截器已提示（如「标签已存在」）
    } finally {
      setTagSubmitting(false);
    }
  };

  const handleRemoveTag = async (record: VulnerabilityTagVO) => {
    try {
      await deleteVulnerabilityTag(vulnerabilityId, record.id);
      message.success(t('vuln.detail.tag.removeSuccess'));
      await loadTags();
      onChanged?.();
    } catch (error) {
      // 拦截器已提示
    }
  };

  const handleUploadAttachment = async (file: File) => {
    setUploading(true);
    try {
      await uploadVulnerabilityAttachment(vulnerabilityId, file);
      message.success(t('vuln.detail.att.uploadSuccess'));
      await loadAttachments();
      onChanged?.();
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 413) {
        message.error(t('vuln.detail.att.uploadTooLarge'));
      } else if (status === 403) {
        message.error(t('vuln.detail.att.uploadForbidden'));
      } else {
        message.error(getApiErrorMessage(error, t('vuln.detail.att.uploadFailed')));
      }
    } finally {
      setUploading(false);
    }
  };

  const handleDeleteAttachment = (record: VulnerabilityAttachmentVO) => {
    Modal.confirm({
      title: t('vuln.detail.att.deleteTitle'),
      content: t('vuln.detail.att.deleteContent', {
        name: pickText(record, 'fileName', 'file_name') ?? t('vuln.detail.att.unnamed'),
      }),
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteVulnerabilityAttachment(vulnerabilityId, record.id);
          message.success(t('vuln.detail.att.deleteSuccess'));
          await loadAttachments();
          onChanged?.();
        } catch (error) {
          const status = getApiErrorStatus(error);
          if (status === 404) {
            message.error(t('vuln.detail.att.deleteNotFound'));
          } else if (status === 403) {
            message.error(t('vuln.detail.att.deleteForbidden'));
          } else {
            message.error(getApiErrorMessage(error, t('vuln.detail.att.deleteFailed')));
          }
          // 404/403 时刷新列表，避免界面停留在过期数据
          await loadAttachments();
        }
      },
    });
  };

  /**
   * 附件真实下载：GET /vulnerabilities/{id}/attachments/{attachmentId}/download。
   * 文件名优先取 Content-Disposition 的 filename*（中文），解析失败回落记录里的文件名。
   */
  const handleDownloadAttachment = async (record: VulnerabilityAttachmentVO) => {
    const fallbackName = pickText(record, 'fileName', 'file_name') ?? 'attachment.bin';
    try {
      const download = await downloadVulnerabilityAttachment(vulnerabilityId, record.id);
      if (!download.blob || download.blob.size === 0) {
        message.error(t('vuln.detail.att.downloadEmpty'));
        return;
      }
      saveBlobAsFile(download.blob, download.fileName || fallbackName);
      message.success(t('vuln.detail.att.downloadStarted', { name: download.fileName || fallbackName }));
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 404) {
        // 后端会区分「附件不属于该漏洞」与「磁盘文件缺失」，直接展示其 message
        message.error(await getBlobErrorMessage(error, t('vuln.detail.att.downloadNotFound')));
      } else if (status === 403) {
        message.error(t('vuln.detail.att.downloadForbidden'));
      } else if (status === 401) {
        message.error(t('vuln.detail.att.downloadUnauthorized'));
      } else {
        message.error(await getBlobErrorMessage(error, t('vuln.detail.att.downloadFailed')));
      }
    }
  };

  const attachmentColumns = [
    {
      title: t('vuln.detail.att.colName'),
      key: 'fileName',
      render: (_: unknown, record: VulnerabilityAttachmentVO) => pickText(record, 'fileName', 'file_name') ?? '-',
    },
    {
      title: t('vuln.detail.att.colType'),
      key: 'fileType',
      width: 160,
      render: (_: unknown, record: VulnerabilityAttachmentVO) => pickText(record, 'fileType', 'file_type') ?? '-',
    },
    {
      title: t('vuln.detail.att.colSize'),
      key: 'fileSize',
      width: 110,
      render: (_: unknown, record: VulnerabilityAttachmentVO) => {
        const size = (record as any).fileSize ?? (record as any).file_size;
        return formatFileSize(typeof size === 'number' ? size : undefined);
      },
    },
    {
      title: t('vuln.detail.att.colUploader'),
      key: 'uploadedBy',
      width: 140,
      render: (_: unknown, record: VulnerabilityAttachmentVO) =>
        userNameOf((record as any).uploadedBy ?? (record as any).uploaded_by),
    },
    {
      title: t('vuln.detail.att.colUploadedAt'),
      key: 'createdAt',
      width: 180,
      render: (_: unknown, record: VulnerabilityAttachmentVO) =>
        formatDateTime((record as any).createdAt ?? (record as any).created_at),
    },
    {
      title: t('vuln.detail.att.colAction'),
      key: 'action',
      width: 150,
      render: (_: unknown, record: VulnerabilityAttachmentVO) => (
        <Space size="small">
          <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => handleDownloadAttachment(record)}>{t('vuln.detail.att.download')}</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDeleteAttachment(record)}>{t('vuln.detail.att.delete')}</Button>
        </Space>
      ),
    },
  ];

  const timelineItems = history.map((item) => {
    const statusType = (item as any).statusType ?? (item as any).status_type ?? 'workflow';
    const meta = HISTORY_STATUS_TYPES[statusType] ?? { label: statusType, color: 'gray' };
    const fromStatus = (item as any).fromStatus ?? (item as any).from_status;
    const toStatus = (item as any).toStatus ?? (item as any).to_status;
    const changedBy = (item as any).changedBy ?? (item as any).changed_by;
    const createdAt = (item as any).createdAt ?? (item as any).created_at;

    let fromLabel = fromStatus || '-';
    let toLabel = toStatus || '-';
    if (statusType === 'workflow') {
      fromLabel = fromStatus ? workflowLabel(fromStatus) : '—';
      toLabel = workflowLabel(toStatus);
    } else if (statusType === 'customer') {
      fromLabel = fromStatus ? customerLabel(fromStatus) : '—';
      toLabel = customerLabel(toStatus);
    } else if (statusType === 'disclosure') {
      fromLabel = fromStatus ? disclosureLabel(fromStatus) : '—';
      toLabel = disclosureLabel(toStatus);
    }

    return {
      color: meta.color,
      children: (
        <div>
          <Space size={8} wrap>
            <Tag color={meta.color}>{t(meta.labelKey)}</Tag>
            <Typography.Text>
              {fromLabel} <Typography.Text type="secondary">→</Typography.Text> <strong>{toLabel}</strong>
            </Typography.Text>
            <Typography.Text type="secondary">{formatDateTime(createdAt)}</Typography.Text>
            <Typography.Text type="secondary">{t('vuln.detail.timelineOperator', { name: userNameOf(changedBy) })}</Typography.Text>
          </Space>
          {item.comment ? (
            <div style={{ marginTop: 4 }}>
              <Typography.Text type="secondary">{t('vuln.detail.timelineComment', { comment: item.comment })}</Typography.Text>
            </div>
          ) : null}
        </div>
      ),
    };
  });

  const recentHistory = history.slice(0, 3);

  const renderActionFields = () => {
    if (!action) {
      return null;
    }
    switch (action.kind) {
      case 'workflow':
        return (
          <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={t('vuln.detail.transitionHint', { from: workflowLabel(currentWorkflow), to: workflowLabel(action.target) })}
            />
            <Form.Item name="comment" label={t('vuln.detail.field.comment')}>
              <TextArea rows={3} placeholder={t('vuln.detail.field.commentPlaceholder')} />
            </Form.Item>
          </>
        );
      case 'customer':
        return (
          <>
            <Form.Item label={t('vuln.detail.field.targetStatus')}>
              <Tag color={customerColor(action.target)}>{customerLabel(action.target)}</Tag>
            </Form.Item>
            {action.target === 'rejected' ? (
              <Form.Item
                name="rejectReason"
                label={t('vuln.detail.field.rejectReason')}
                rules={[{ required: true, message: t('vuln.detail.field.rejectReasonRequired') }]}
              >
                <TextArea rows={3} placeholder={t('vuln.detail.field.rejectReasonPlaceholder')} />
              </Form.Item>
            ) : null}
          </>
        );
      case 'disclosure':
        return (
          <>
            <Form.Item label={t('vuln.detail.field.targetStatus')}>
              <Tag color={disclosureColor(action.target)}>{disclosureLabel(action.target)}</Tag>
            </Form.Item>
            <Form.Item name="disclosureDate" label={t('vuln.detail.field.disclosureDate')}>
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="disclosureSource" label={t('vuln.detail.field.disclosureSource')}>
              <Input placeholder={t('vuln.detail.field.disclosureSourcePlaceholder')} />
            </Form.Item>
          </>
        );
      case 'exploit':
        return (
          <>
            <Form.Item name="exploitStage" label={t('vuln.detail.field.stage')} rules={[{ required: true, message: t('vuln.detail.field.stageRequired') }]}>
              <Select options={EXPLOIT_STAGE_OPTIONS.map((item) => ({ value: item.value, label: t(item.labelKey) }))} />
            </Form.Item>
            <Form.Item name="exploitCode" label={t('vuln.detail.field.exploitCode')}>
              <TextArea rows={4} placeholder={t('vuln.detail.field.exploitCodePlaceholder')} />
            </Form.Item>
          </>
        );
      case 'vendor':
        return (
          <>
            <Form.Item name="vendorFixed" label={t('vuln.detail.field.vendorFixed')} rules={[{ required: true, message: t('vuln.detail.field.vendorFixedRequired') }]}>
              <Select
                options={[
                  { value: true, label: t('vuln.detail.field.yes') },
                  { value: false, label: t('vuln.detail.field.no') },
                ]}
              />
            </Form.Item>
            <Form.Item name="vendorFixVersion" label={t('vuln.detail.field.fixVersion')}>
              <Input placeholder={t('vuln.detail.field.fixVersionPlaceholder')} />
            </Form.Item>
          </>
        );
      case 'deliver':
        return (
          <Form.Item name="deliveryNote" label={t('vuln.detail.field.deliveryNote')}>
            <TextArea rows={3} placeholder={t('vuln.detail.field.deliveryNotePlaceholder')} />
          </Form.Item>
        );
      case 'assign':
        return (
          <Form.Item name="assignedTo" label={t('vuln.detail.field.assignee')} rules={[{ required: true, message: t('vuln.detail.field.assigneeRequired') }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder={t('vuln.detail.field.assigneeRequired')}
              options={users.map((user) => ({ value: user.id, label: user.fullName || user.username }))}
            />
          </Form.Item>
        );
      default:
        return null;
    }
  };

  if (loading && !detail) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin spinning />
      </div>
    );
  }

  if (!detail) {
    return (
      <Card>
        <Empty description={notFound ? t('vuln.detail.notFound') : t('common.empty')} />
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Button icon={<ReloadOutlined />} onClick={reloadAll}>{t('vuln.detail.reload')}</Button>
        </div>
      </Card>
    );
  }

  const severity = (detail as any).severity;
  const vendorFixVersion = pickText(detail, 'vendorFixVersion', 'vendor_fix_version');
  const disclosureSource = pickText(detail, 'disclosureSource', 'disclosure_source');
  const disclosureDate = pickText(detail, 'disclosureDate', 'disclosure_date');
  const customerRejectReason = pickText(detail, 'customerRejectReason', 'customer_reject_reason');
  const assetName =
    (detail as any).asset?.name ??
    pickText(detail, 'assetId', 'asset_id') ??
    '-';

  const workflowSteps = {
    current: currentStepIndex,
    items: WORKFLOW_STATUS_OPTIONS.map((item, index) => ({
      title: t(item.labelKey),
      status: index < currentStepIndex ? ('finish' as const) : index === currentStepIndex ? ('process' as const) : ('wait' as const),
    })),
  };

  const tabs = [
    {
      key: 'overview',
      label: t('vuln.detail.overview'),
      children: (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card size="small" title={t('vuln.detail.workflowMachine')}>
            <Steps size="small" labelPlacement="vertical" current={workflowSteps.current} items={workflowSteps.items} />
          </Card>
          <Descriptions
            column={{ xs: 1, sm: 2, md: 3 }}
            bordered
            size="small"
            items={[
              { key: 'no', label: t('vuln.list.colNo'), children: pickText(detail, 'vulnerabilityNo', 'vulnerability_no') ?? '-' },
              { key: 'type', label: t('vuln.field.type'), children: (detail as any).type ?? '-' },
              {
                key: 'severity',
                label: t('vuln.field.severity'),
                children: <Tag color={severityColor(severity)}>{severityLabel(severity)}</Tag>,
              },
              { key: 'asset', label: t('vuln.field.asset'), children: assetName },
              {
                key: 'version',
                label: t('vuln.field.discoveredVersion'),
                children: pickText(detail, 'discoveredVersionId', 'discovered_version_id') ?? '-',
              },
              {
                key: 'affected',
                label: t('vuln.field.affectedVersions'),
                children: pickText(detail, 'affectedVersions', 'affected_versions') ?? '-',
              },
              { key: 'cve', label: t('vuln.field.cveId'), children: pickText(detail, 'cveId', 'cve_id') ?? '-' },
              {
                key: 'cvss',
                label: t('vuln.detail.cvss'),
                children: (detail as any).cvssScore ?? (detail as any).cvss_score ?? '-',
              },
              {
                key: 'discoverer',
                label: t('vuln.detail.colDiscoverer'),
                children:
                  (detail as any).discoverer?.full_name ||
                  (detail as any).discoverer?.username ||
                  userNameOf(pickText(detail, 'discovererId', 'discoverer_id') ?? (detail as any).discoverer_id),
              },
              { key: 'assignee', label: t('vuln.detail.assigneeCard'), children: userNameOf(currentAssigneeId) },
              { key: 'exploitStage', label: t('vuln.detail.field.stage'), children: exploitStageLabel(currentExploitStage) },
              {
                key: 'fieldTest',
                label: t('vuln.edit.fieldTestStatus'),
                children: (
                  <Tag color={fieldTestColor((detail as any).fieldTestStatus ?? (detail as any).field_test_status)}>
                    {fieldTestLabel((detail as any).fieldTestStatus ?? (detail as any).field_test_status)}
                  </Tag>
                ),
              },
              {
                key: 'vendorFixed',
                label: t('vuln.detail.vendorCard'),
                children: (
                  <Tag color={currentVendorFixed ? 'success' : 'default'}>{currentVendorFixed ? t('vuln.detail.vendorFixedYes') : t('vuln.detail.vendorFixedNo')}</Tag>
                ),
              },
              { key: 'vendorVersion', label: t('vuln.detail.field.fixVersion'), children: vendorFixVersion ?? '-' },
              { key: 'disclosureSource', label: t('vuln.detail.field.disclosureSource'), children: disclosureSource ?? '-' },
              { key: 'disclosureDate', label: t('vuln.detail.field.disclosureDate'), children: formatDateTime(disclosureDate) },
              {
                key: 'createdAt',
                label: t('vuln.detail.colCreatedAt'),
                children: formatDateTime(pickText(detail, 'createdAt', 'created_at')),
              },
              {
                key: 'updatedAt',
                label: t('vuln.detail.colUpdatedAt'),
                children: formatDateTime(pickText(detail, 'updatedAt', 'updated_at')),
              },
            ]}
          />
          <Card size="small" title={t('vuln.detail.sectionDescription')}>
            {renderBlock(detail.description)}
          </Card>
          <Card size="small" title={t('vuln.detail.sectionDetail')}>
            {renderBlock(detail.detail)}
          </Card>
          <Card size="small" title={t('vuln.detail.sectionImpact')}>
            {renderBlock(pickText(detail, 'impact', 'impact'))}
          </Card>
          <Card size="small" title={t('vuln.detail.sectionPoc')}>
            {renderBlock(
              [
                pickText(detail, 'pocCode', 'poc_code'),
                pickText(detail, 'fieldTestResult', 'field_test_result'),
                pickText(detail, 'deliveryNote', 'delivery_note'),
              ]
                .filter(Boolean)
                .join('\n\n'),
            )}
          </Card>
        </Space>
      ),
    },
    {
      key: 'attachments',
      label: t('vuln.detail.tabAttachments', { count: attachments.length }),
      children: (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space wrap>
            <Upload
              showUploadList={false}
              beforeUpload={(file) => {
                handleUploadAttachment(file as unknown as File);
                return false;
              }}
            >
              <Button icon={<UploadOutlined />} loading={uploading}>{t('vuln.detail.uploadAttachment')}</Button>
            </Upload>
            <Button icon={<ReloadOutlined />} onClick={loadAttachments} loading={attachmentLoading}>{t('vuln.detail.refresh')}</Button>
          </Space>
          <Table
            rowKey="id"
            size="small"
            loading={attachmentLoading}
            columns={attachmentColumns}
            dataSource={attachments}
            pagination={false}
            locale={{ emptyText: t('vuln.detail.emptyAttachments') }}
          />
        </Space>
      ),
    },
    {
      key: 'tags',
      label: t('vuln.detail.tabTags', { count: tags.length }),
      children: (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space.Compact style={{ maxWidth: 420 }}>
            <Input
              placeholder={t('vuln.detail.tagPlaceholder')}
              value={tagInput}
              maxLength={50}
              onChange={(event) => setTagInput(event.target.value)}
              onPressEnter={handleAddTag}
            />
            <Button type="primary" icon={<PlusOutlined />} loading={tagSubmitting} onClick={handleAddTag}>{t('vuln.detail.addTag')}</Button>
          </Space.Compact>
          {tags.length === 0 ? (
            <Empty description={t('vuln.detail.emptyTags')} />
          ) : (
            <Space wrap size={[8, 8]}>
              {tags.map((tag) => (
                <Tag
                  key={tag.id}
                  color="blue"
                  closable
                  onClose={(event) => {
                    event.preventDefault();
                    handleRemoveTag(tag);
                  }}
                >
                  {tag.tag}
                </Tag>
              ))}
            </Space>
          )}
        </Space>
      ),
    },
    {
      key: 'history',
      label: t('vuln.detail.tabHistory', { count: history.length }),
      children: (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadHistory} loading={historyLoading}>{t('vuln.detail.refreshHistory')}</Button>
          </Space>
          <Spin spinning={historyLoading}>
            {timelineItems.length === 0 ? <Empty description={t('vuln.detail.emptyHistory')} /> : <Timeline items={timelineItems} />}
          </Spin>
        </Space>
      ),
    },
  ];

  const content = (
    <>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={8}>
            <Card
              size="small"
              title={t('vuln.detail.workflowCard')}
              extra={<Tag color={workflowColor(currentWorkflow)}>{workflowLabel(currentWorkflow)}</Tag>}
            >
              <Progress
                percent={Math.round(((currentStepIndex + 1) / WORKFLOW_STATUS_OPTIONS.length) * 100)}
                size="small"
                format={() => `${currentStepIndex + 1}/${WORKFLOW_STATUS_OPTIONS.length}`}
              />
              <div style={{ marginTop: 12 }}>
                <Typography.Text type="secondary">{t('vuln.detail.legalTransition')}</Typography.Text>
                <div style={{ marginTop: 8 }}>
                  {nextStatuses.length === 0 ? (
                    <Typography.Text type="secondary">{t('vuln.detail.noNextStatus')}</Typography.Text>
                  ) : (
                    <Space wrap>
                      {nextStatuses.map((status) => (
                        <Button key={status} type="primary" size="small" onClick={() => openAction('workflow', status)}>
                          {t('vuln.detail.transitionTo', { status: workflowLabel(status) })}
                        </Button>
                      ))}
                    </Space>
                  )}
                </div>
              </div>
              <div style={{ marginTop: 12 }}>
                <Tooltip title={t('vuln.detail.deliverTooltip')}>
                  <Button
                    size="small"
                    icon={<SendOutlined />}
                    disabled={currentWorkflow === 'delivered' || currentWorkflow === 'closed'}
                    onClick={() => openAction('deliver')}
                  >{t('vuln.detail.deliver')}</Button>
                </Tooltip>
              </div>
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card
              size="small"
              title={t('vuln.detail.customerCard')}
              extra={<Tag color={customerColor(currentCustomer)}>{customerLabel(currentCustomer)}</Tag>}
            >
              <Space wrap>
                <Button
                  size="small"
                  type="primary"
                  disabled={currentCustomer === 'confirmed'}
                  onClick={() => openAction('customer', 'confirmed')}
                >{t('vuln.detail.customerConfirmed')}</Button>
                <Button
                  size="small"
                  danger
                  disabled={currentCustomer === 'rejected'}
                  onClick={() => openAction('customer', 'rejected')}
                >{t('vuln.detail.customerRejected')}</Button>
                <Button
                  size="small"
                  disabled={currentCustomer === 'pending' || !currentCustomer}
                  onClick={() => openAction('customer', 'pending')}
                >{t('vuln.detail.customerReset')}</Button>
              </Space>
              {customerRejectReason ? (
                <Alert style={{ marginTop: 12 }} type="warning" showIcon message={t('vuln.detail.rejectReasonAlert', { reason: customerRejectReason ?? '' })} />
              ) : null}
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card
              size="small"
              title={t('vuln.detail.disclosureCard')}
              extra={<Tag color={disclosureColor(currentDisclosure)}>{disclosureLabel(currentDisclosure)}</Tag>}
            >
              <Space wrap>
                <Button
                  size="small"
                  type="primary"
                  disabled={currentDisclosure === 'disclosed'}
                  onClick={() => openAction('disclosure', 'disclosed')}
                >{t('vuln.detail.markDisclosed')}</Button>
                <Button
                  size="small"
                  disabled={currentDisclosure === 'has_cve'}
                  onClick={() => openAction('disclosure', 'has_cve')}
                >{t('vuln.detail.markHasCve')}</Button>
                <Button
                  size="small"
                  disabled={currentDisclosure === 'undisclosed'}
                  onClick={() => openAction('disclosure', 'undisclosed')}
                >{t('vuln.detail.revokeDisclosure')}</Button>
              </Space>
              <div style={{ marginTop: 12 }}>
                <Typography.Text type="secondary">
                  {t('vuln.detail.disclosureMeta', { source: disclosureSource ?? '-', date: formatDateTime(disclosureDate) })}
                </Typography.Text>
              </div>
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={8}>
            <Card
              size="small"
              title={t('vuln.detail.field.stage')}
              extra={<Tag color={exploitStageColor(currentExploitStage)}>{exploitStageLabel(currentExploitStage)}</Tag>}
            >
              <Button size="small" onClick={() => openAction('exploit')}>{t('vuln.detail.updateStage')}</Button>
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card
              size="small"
              title={t('vuln.detail.vendorCard')}
              extra={<Tag color={currentVendorFixed ? 'success' : 'default'}>{currentVendorFixed ? t('vuln.detail.vendorFixedYes') : t('vuln.detail.vendorFixedNo')}</Tag>}
            >
              <Space wrap>
                <Typography.Text type="secondary">{t('vuln.detail.fixVersion', { version: vendorFixVersion ?? '-' })}</Typography.Text>
                <Button size="small" onClick={() => openAction('vendor')}>{t('vuln.detail.updateVendor')}</Button>
              </Space>
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card size="small" title={t('vuln.detail.field.assignee')} extra={<Tag>{userNameOf(currentAssigneeId)}</Tag>}>
              <Button size="small" icon={<UserSwitchOutlined />} onClick={() => openAction('assign')}>{t('vuln.detail.assign')}</Button>
            </Card>
          </Col>
        </Row>

        <Card
          size="small"
          title={t('vuln.detail.recentChanges')}
          extra={
            <Button type="link" size="small" onClick={() => setActiveTab('history')}>{t('vuln.detail.viewAllHistory')}</Button>
          }
        >
          {recentHistory.length === 0 ? (
            <Typography.Text type="secondary">{t('vuln.detail.emptyHistory')}</Typography.Text>
          ) : (
            <Timeline items={timelineItems.slice(0, 3)} />
          )}
        </Card>

        <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabs} />
      </Space>

      <Modal
        title={action ? t(ACTION_TITLE_KEYS[action.kind]) : ''}
        open={Boolean(action)}
        onCancel={() => setAction(null)}
        onOk={handleActionSubmit}
        confirmLoading={actionSubmitting}
        destroyOnClose
        okText={t('common.confirm')}
      >
        <Form form={actionForm} layout="vertical" preserve={false}>
          {renderActionFields()}
        </Form>
      </Modal>
    </>
  );

  const title = (
    <Space wrap>
      <Typography.Text strong style={{ fontSize: 16 }}>
        {(detail as any).title || t('vuln.edit.unnamed')}
      </Typography.Text>
      <Tag color={severityColor(severity)}>{severityLabel(severity)}</Tag>
      <Tag color={workflowColor(currentWorkflow)}>{workflowLabel(currentWorkflow)}</Tag>
    </Space>
  );

  if (embedded) {
    return (
      <Spin spinning={loading}>
        <div style={{ marginBottom: 12 }}>{title}</div>
        {content}
      </Spin>
    );
  }

  return (
    <Spin spinning={loading}>
      <Card
        title={title}
        extra={
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={reloadAll}>{t('vuln.detail.refresh')}</Button>
            <Button type="primary" icon={<EditOutlined />} href={`/vulnerabilities/${vulnerabilityId}/edit`}>{t('vuln.detail.editVuln')}</Button>
            {onClose ? (
              <Button icon={<ArrowLeftOutlined />} onClick={onClose}>{t('vuln.detail.backList')}</Button>
            ) : null}
          </Space>
        }
      >
        {content}
      </Card>
    </Spin>
  );
};

export default VulnerabilityDetail;
