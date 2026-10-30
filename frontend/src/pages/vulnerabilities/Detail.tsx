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
  customerLabel,
  disclosureColor,
  disclosureLabel,
  exploitStageColor,
  exploitStageLabel,
  fieldTestColor,
  fieldTestLabel,
  formatFileSize,
  nextWorkflowStatuses,
  severityColor,
  severityLabel,
  workflowColor,
  workflowIndex,
  workflowLabel,
} from './constants';

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

const ACTION_TITLES: Record<ActionKind, string> = {
  workflow: '工作流状态流转',
  customer: '客户确认状态',
  disclosure: '公开披露状态',
  exploit: '更新利用阶段',
  vendor: '更新厂商修复状态',
  deliver: '交付漏洞',
  assign: '分配处理人',
};

const VulnerabilityDetail: React.FC<VulnerabilityDetailProps> = ({
  vulnerabilityId,
  embedded = false,
  onChanged,
  onClose,
}) => {
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
      message.success(`${ACTION_TITLES[action.kind]}成功`);
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
      message.warning('请输入标签名称');
      return;
    }
    setTagSubmitting(true);
    try {
      await addVulnerabilityTag(vulnerabilityId, value);
      message.success('标签已添加');
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
      message.success('标签已移除');
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
      message.success('附件上传成功');
      await loadAttachments();
      onChanged?.();
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 413) {
        message.error('附件上传失败：文件超出服务端允许的大小');
      } else if (status === 403) {
        message.error('附件上传失败：没有该漏洞的写权限');
      } else {
        message.error(getApiErrorMessage(error, '附件上传失败，请稍后重试'));
      }
    } finally {
      setUploading(false);
    }
  };

  const handleDeleteAttachment = (record: VulnerabilityAttachmentVO) => {
    Modal.confirm({
      title: '确认删除附件',
      content: `确定删除「${pickText(record, 'fileName', 'file_name') ?? '该附件'}」吗？删除后不可恢复。`,
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteVulnerabilityAttachment(vulnerabilityId, record.id);
          message.success('附件已删除');
          await loadAttachments();
          onChanged?.();
        } catch (error) {
          const status = getApiErrorStatus(error);
          if (status === 404) {
            message.error('附件删除失败：附件不存在或已被删除');
          } else if (status === 403) {
            message.error('附件删除失败：没有该漏洞的写权限');
          } else {
            message.error(getApiErrorMessage(error, '附件删除失败，请稍后重试'));
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
        message.error('附件下载失败：服务端返回了空文件');
        return;
      }
      saveBlobAsFile(download.blob, download.fileName || fallbackName);
      message.success(`附件已开始下载：${download.fileName || fallbackName}`);
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 404) {
        // 后端会区分「附件不属于该漏洞」与「磁盘文件缺失」，直接展示其 message
        message.error(await getBlobErrorMessage(error, '附件不存在或已被移除'));
      } else if (status === 403) {
        message.error('没有下载该附件的权限：需要对附件所属资产的访问权限');
      } else if (status === 401) {
        message.error('登录状态已失效，请重新登录后再下载');
      } else {
        message.error(await getBlobErrorMessage(error, '附件下载失败，请稍后重试'));
      }
    }
  };

  const attachmentColumns = [
    {
      title: '文件名',
      key: 'fileName',
      render: (_: unknown, record: VulnerabilityAttachmentVO) => pickText(record, 'fileName', 'file_name') ?? '-',
    },
    {
      title: '类型',
      key: 'fileType',
      width: 160,
      render: (_: unknown, record: VulnerabilityAttachmentVO) => pickText(record, 'fileType', 'file_type') ?? '-',
    },
    {
      title: '大小',
      key: 'fileSize',
      width: 110,
      render: (_: unknown, record: VulnerabilityAttachmentVO) => {
        const size = (record as any).fileSize ?? (record as any).file_size;
        return formatFileSize(typeof size === 'number' ? size : undefined);
      },
    },
    {
      title: '上传人',
      key: 'uploadedBy',
      width: 140,
      render: (_: unknown, record: VulnerabilityAttachmentVO) =>
        userNameOf((record as any).uploadedBy ?? (record as any).uploaded_by),
    },
    {
      title: '上传时间',
      key: 'createdAt',
      width: 180,
      render: (_: unknown, record: VulnerabilityAttachmentVO) =>
        formatDateTime((record as any).createdAt ?? (record as any).created_at),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_: unknown, record: VulnerabilityAttachmentVO) => (
        <Space size="small">
          <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => handleDownloadAttachment(record)}>
            下载
          </Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDeleteAttachment(record)}>
            删除
          </Button>
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
            <Tag color={meta.color}>{meta.label}</Tag>
            <Typography.Text>
              {fromLabel} <Typography.Text type="secondary">→</Typography.Text> <strong>{toLabel}</strong>
            </Typography.Text>
            <Typography.Text type="secondary">{formatDateTime(createdAt)}</Typography.Text>
            <Typography.Text type="secondary">操作人：{userNameOf(changedBy)}</Typography.Text>
          </Space>
          {item.comment ? (
            <div style={{ marginTop: 4 }}>
              <Typography.Text type="secondary">说明：{item.comment}</Typography.Text>
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
              message={`即将把工作流状态从「${workflowLabel(currentWorkflow)}」流转到「${workflowLabel(action.target)}」`}
            />
            <Form.Item name="comment" label="变更说明">
              <TextArea rows={3} placeholder="例如：复现环境已搭建，漏洞可稳定触发" />
            </Form.Item>
          </>
        );
      case 'customer':
        return (
          <>
            <Form.Item label="目标状态">
              <Tag color={customerColor(action.target)}>{customerLabel(action.target)}</Tag>
            </Form.Item>
            {action.target === 'rejected' ? (
              <Form.Item
                name="rejectReason"
                label="拒绝原因"
                rules={[{ required: true, message: '请填写客户拒绝原因' }]}
              >
                <TextArea rows={3} placeholder="客户拒绝的说明，会写入状态历史" />
              </Form.Item>
            ) : null}
          </>
        );
      case 'disclosure':
        return (
          <>
            <Form.Item label="目标状态">
              <Tag color={disclosureColor(action.target)}>{disclosureLabel(action.target)}</Tag>
            </Form.Item>
            <Form.Item name="disclosureDate" label="披露日期">
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="disclosureSource" label="披露来源">
              <Input placeholder="例如：官方公告 / CNVD / 供应商通告" />
            </Form.Item>
          </>
        );
      case 'exploit':
        return (
          <>
            <Form.Item name="exploitStage" label="利用阶段" rules={[{ required: true, message: '请选择利用阶段' }]}>
              <Select options={EXPLOIT_STAGE_OPTIONS.map((item) => ({ value: item.value, label: item.label }))} />
            </Form.Item>
            <Form.Item name="exploitCode" label="利用代码">
              <TextArea rows={4} placeholder="可留空，仅更新阶段" />
            </Form.Item>
          </>
        );
      case 'vendor':
        return (
          <>
            <Form.Item name="vendorFixed" label="厂商是否修复" rules={[{ required: true, message: '请选择是否已修复' }]}>
              <Select
                options={[
                  { value: true, label: '是' },
                  { value: false, label: '否' },
                ]}
              />
            </Form.Item>
            <Form.Item name="vendorFixVersion" label="修复版本">
              <Input placeholder="例如：v1.0.3" />
            </Form.Item>
          </>
        );
      case 'deliver':
        return (
          <Form.Item name="deliveryNote" label="交付备注">
            <TextArea rows={3} placeholder="交付范围、客户接收人等说明" />
          </Form.Item>
        );
      case 'assign':
        return (
          <Form.Item name="assignedTo" label="处理人" rules={[{ required: true, message: '请选择处理人' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="请选择处理人"
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
        <Empty description={notFound ? '漏洞不存在或无权限查看' : '暂无数据'} />
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Button icon={<ReloadOutlined />} onClick={reloadAll}>
            重新加载
          </Button>
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
      title: item.label,
      status: index < currentStepIndex ? ('finish' as const) : index === currentStepIndex ? ('process' as const) : ('wait' as const),
    })),
  };

  const tabs = [
    {
      key: 'overview',
      label: '概览',
      children: (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card size="small" title="工作流状态机">
            <Steps size="small" labelPlacement="vertical" current={workflowSteps.current} items={workflowSteps.items} />
          </Card>
          <Descriptions
            column={{ xs: 1, sm: 2, md: 3 }}
            bordered
            size="small"
            items={[
              { key: 'no', label: '漏洞编号', children: pickText(detail, 'vulnerabilityNo', 'vulnerability_no') ?? '-' },
              { key: 'type', label: '漏洞类型', children: (detail as any).type ?? '-' },
              {
                key: 'severity',
                label: '严重程度',
                children: <Tag color={severityColor(severity)}>{severityLabel(severity)}</Tag>,
              },
              { key: 'asset', label: '所属资产', children: assetName },
              {
                key: 'version',
                label: '发现版本',
                children: pickText(detail, 'discoveredVersionId', 'discovered_version_id') ?? '-',
              },
              {
                key: 'affected',
                label: '受影响版本',
                children: pickText(detail, 'affectedVersions', 'affected_versions') ?? '-',
              },
              { key: 'cve', label: 'CVE编号', children: pickText(detail, 'cveId', 'cve_id') ?? '-' },
              {
                key: 'cvss',
                label: 'CVSS评分',
                children: (detail as any).cvssScore ?? (detail as any).cvss_score ?? '-',
              },
              {
                key: 'discoverer',
                label: '发现者',
                children:
                  (detail as any).discoverer?.full_name ||
                  (detail as any).discoverer?.username ||
                  userNameOf(pickText(detail, 'discovererId', 'discoverer_id') ?? (detail as any).discoverer_id),
              },
              { key: 'assignee', label: '当前处理人', children: userNameOf(currentAssigneeId) },
              { key: 'exploitStage', label: '利用阶段', children: exploitStageLabel(currentExploitStage) },
              {
                key: 'fieldTest',
                label: '出场测试状态',
                children: (
                  <Tag color={fieldTestColor((detail as any).fieldTestStatus ?? (detail as any).field_test_status)}>
                    {fieldTestLabel((detail as any).fieldTestStatus ?? (detail as any).field_test_status)}
                  </Tag>
                ),
              },
              {
                key: 'vendorFixed',
                label: '厂商修复',
                children: (
                  <Tag color={currentVendorFixed ? 'success' : 'default'}>{currentVendorFixed ? '已修复' : '未修复'}</Tag>
                ),
              },
              { key: 'vendorVersion', label: '修复版本', children: vendorFixVersion ?? '-' },
              { key: 'disclosureSource', label: '披露来源', children: disclosureSource ?? '-' },
              { key: 'disclosureDate', label: '披露日期', children: formatDateTime(disclosureDate) },
              {
                key: 'createdAt',
                label: '创建时间',
                children: formatDateTime(pickText(detail, 'createdAt', 'created_at')),
              },
              {
                key: 'updatedAt',
                label: '更新时间',
                children: formatDateTime(pickText(detail, 'updatedAt', 'updated_at')),
              },
            ]}
          />
          <Card size="small" title="漏洞描述">
            {renderBlock(detail.description)}
          </Card>
          <Card size="small" title="漏洞详情">
            {renderBlock(detail.detail)}
          </Card>
          <Card size="small" title="影响分析">
            {renderBlock(pickText(detail, 'impact', 'impact'))}
          </Card>
          <Card size="small" title="POC / 测试结果 / 交付备注">
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
      label: `附件 (${attachments.length})`,
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
              <Button icon={<UploadOutlined />} loading={uploading}>
                上传附件
              </Button>
            </Upload>
            <Button icon={<ReloadOutlined />} onClick={loadAttachments} loading={attachmentLoading}>
              刷新
            </Button>
          </Space>
          <Table
            rowKey="id"
            size="small"
            loading={attachmentLoading}
            columns={attachmentColumns}
            dataSource={attachments}
            pagination={false}
            locale={{ emptyText: '暂无附件' }}
          />
        </Space>
      ),
    },
    {
      key: 'tags',
      label: `标签 (${tags.length})`,
      children: (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space.Compact style={{ maxWidth: 420 }}>
            <Input
              placeholder="输入标签名称后回车添加"
              value={tagInput}
              maxLength={50}
              onChange={(event) => setTagInput(event.target.value)}
              onPressEnter={handleAddTag}
            />
            <Button type="primary" icon={<PlusOutlined />} loading={tagSubmitting} onClick={handleAddTag}>
              添加标签
            </Button>
          </Space.Compact>
          {tags.length === 0 ? (
            <Empty description="暂无标签" />
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
      label: `状态历史 (${history.length})`,
      children: (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadHistory} loading={historyLoading}>
              刷新历史
            </Button>
          </Space>
          <Spin spinning={historyLoading}>
            {timelineItems.length === 0 ? <Empty description="暂无状态变更记录" /> : <Timeline items={timelineItems} />}
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
              title="工作流状态"
              extra={<Tag color={workflowColor(currentWorkflow)}>{workflowLabel(currentWorkflow)}</Tag>}
            >
              <Progress
                percent={Math.round(((currentStepIndex + 1) / WORKFLOW_STATUS_OPTIONS.length) * 100)}
                size="small"
                format={() => `${currentStepIndex + 1}/${WORKFLOW_STATUS_OPTIONS.length}`}
              />
              <div style={{ marginTop: 12 }}>
                <Typography.Text type="secondary">合法流转</Typography.Text>
                <div style={{ marginTop: 8 }}>
                  {nextStatuses.length === 0 ? (
                    <Typography.Text type="secondary">当前状态没有可流转的下一步</Typography.Text>
                  ) : (
                    <Space wrap>
                      {nextStatuses.map((status) => (
                        <Button key={status} type="primary" size="small" onClick={() => openAction('workflow', status)}>
                          流转到 {workflowLabel(status)}
                        </Button>
                      ))}
                    </Space>
                  )}
                </div>
              </div>
              <div style={{ marginTop: 12 }}>
                <Tooltip title="调用 POST /vulnerabilities/{id}/deliver，记录 workflow 历史">
                  <Button
                    size="small"
                    icon={<SendOutlined />}
                    disabled={currentWorkflow === 'delivered' || currentWorkflow === 'closed'}
                    onClick={() => openAction('deliver')}
                  >
                    交付客户
                  </Button>
                </Tooltip>
              </div>
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card
              size="small"
              title="客户确认状态"
              extra={<Tag color={customerColor(currentCustomer)}>{customerLabel(currentCustomer)}</Tag>}
            >
              <Space wrap>
                <Button
                  size="small"
                  type="primary"
                  disabled={currentCustomer === 'confirmed'}
                  onClick={() => openAction('customer', 'confirmed')}
                >
                  客户已确认
                </Button>
                <Button
                  size="small"
                  danger
                  disabled={currentCustomer === 'rejected'}
                  onClick={() => openAction('customer', 'rejected')}
                >
                  客户拒绝
                </Button>
                <Button
                  size="small"
                  disabled={currentCustomer === 'pending' || !currentCustomer}
                  onClick={() => openAction('customer', 'pending')}
                >
                  重置为待确认
                </Button>
              </Space>
              {customerRejectReason ? (
                <Alert style={{ marginTop: 12 }} type="warning" showIcon message={`拒绝原因：${customerRejectReason}`} />
              ) : null}
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card
              size="small"
              title="公开披露状态"
              extra={<Tag color={disclosureColor(currentDisclosure)}>{disclosureLabel(currentDisclosure)}</Tag>}
            >
              <Space wrap>
                <Button
                  size="small"
                  type="primary"
                  disabled={currentDisclosure === 'disclosed'}
                  onClick={() => openAction('disclosure', 'disclosed')}
                >
                  标记已披露
                </Button>
                <Button
                  size="small"
                  disabled={currentDisclosure === 'has_cve'}
                  onClick={() => openAction('disclosure', 'has_cve')}
                >
                  标记已分配CVE
                </Button>
                <Button
                  size="small"
                  disabled={currentDisclosure === 'undisclosed'}
                  onClick={() => openAction('disclosure', 'undisclosed')}
                >
                  撤销披露
                </Button>
              </Space>
              <div style={{ marginTop: 12 }}>
                <Typography.Text type="secondary">
                  披露来源：{disclosureSource ?? '-'} ｜ 披露日期：{formatDateTime(disclosureDate)}
                </Typography.Text>
              </div>
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={8}>
            <Card
              size="small"
              title="利用阶段"
              extra={<Tag color={exploitStageColor(currentExploitStage)}>{exploitStageLabel(currentExploitStage)}</Tag>}
            >
              <Button size="small" onClick={() => openAction('exploit')}>
                更新利用阶段
              </Button>
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card
              size="small"
              title="厂商修复"
              extra={<Tag color={currentVendorFixed ? 'success' : 'default'}>{currentVendorFixed ? '已修复' : '未修复'}</Tag>}
            >
              <Space wrap>
                <Typography.Text type="secondary">修复版本：{vendorFixVersion ?? '-'}</Typography.Text>
                <Button size="small" onClick={() => openAction('vendor')}>
                  更新修复状态
                </Button>
              </Space>
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card size="small" title="处理人" extra={<Tag>{userNameOf(currentAssigneeId)}</Tag>}>
              <Button size="small" icon={<UserSwitchOutlined />} onClick={() => openAction('assign')}>
                分配处理人
              </Button>
            </Card>
          </Col>
        </Row>

        <Card
          size="small"
          title="最近状态变更"
          extra={
            <Button type="link" size="small" onClick={() => setActiveTab('history')}>
              查看全部历史
            </Button>
          }
        >
          {recentHistory.length === 0 ? (
            <Typography.Text type="secondary">暂无状态变更记录</Typography.Text>
          ) : (
            <Timeline items={timelineItems.slice(0, 3)} />
          )}
        </Card>

        <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabs} />
      </Space>

      <Modal
        title={action ? `${ACTION_TITLES[action.kind]}` : ''}
        open={Boolean(action)}
        onCancel={() => setAction(null)}
        onOk={handleActionSubmit}
        confirmLoading={actionSubmitting}
        destroyOnClose
        okText="确认"
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
        {(detail as any).title || '未命名漏洞'}
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
            <Button icon={<ReloadOutlined />} onClick={reloadAll}>
              刷新
            </Button>
            <Button type="primary" icon={<EditOutlined />} href={`/vulnerabilities/${vulnerabilityId}/edit`}>
              编辑漏洞
            </Button>
            {onClose ? (
              <Button icon={<ArrowLeftOutlined />} onClick={onClose}>
                返回列表
              </Button>
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
