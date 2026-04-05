import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Tag, Modal, Form, Select, Input, DatePicker, InputNumber, message, Descriptions, Tabs, Timeline } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import {
  getTaskList,
  createTask,
  updateTaskStatus,
  updateTask,
  getTaskDetail,
  assignTask,
  getTaskComments,
  createTaskComment,
  getTaskAttachments,
  createTaskAttachment,
  getTaskAuditLogs,
  getTaskRelatedVulnerabilities,
  addTaskCollaborator,
  removeTaskCollaborator,
  submitTaskReview,
  approveTaskReview,
  rejectTaskReview,
} from '@/api/task';
import type { TaskVO, CreateTaskRequest, UpdateTaskStatusRequest, TaskCommentVO, TaskAttachmentVO, AuditLogVO } from '@/types/task';
import { getAssetList, getAssetVersions } from '@/api/asset';
import { getUserList } from '@/api/user';
import { getDocumentList } from '@/api/document';
import type { AssetVO, AssetVersionVO } from '@/types/asset';
import type { UserVO } from '@/types/user';
import dayjs from 'dayjs';
import { useNavigate, useParams } from 'react-router-dom';
import { useUserStore } from '@/store/userStore';

const { Option } = Select;

const statusColors: Record<string, string> = {
  pending: 'default',
  in_progress: 'processing',
  review_pending: 'warning',
  assigned: 'blue',
  completed: 'success',
  cancelled: 'error'
};

const statusLabels: Record<string, string> = {
  pending: '待处理',
  in_progress: '进行中',
  review_pending: '待审核',
  assigned: '已分配',
  completed: '已完成',
  cancelled: '已取消'
};

const executionTypeLabels: Record<string, string> = {
  analysis: '代码分析',
  verify: '验证复现',
  retest: '复测回归',
  fix_review: '修复复核',
  meeting: '沟通评审',
  other: '其他',
};

const getPayload = (res: any) => (res as any)?.data ?? res;

const Tasks: React.FC = () => {
  const navigate = useNavigate();
  const { id: taskId } = useParams();
  const { user } = useUserStore();
  const [data, setData] = useState<TaskVO[]>([]);
  const [detailTask, setDetailTask] = useState<TaskVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState<{ page: number; page_size: number; search: string; status?: string }>({ page: 1, page_size: 10, search: '' });
  const [users, setUsers] = useState<UserVO[]>([]);
  const [assets, setAssets] = useState<AssetVO[]>([]);
  const [assetVersions, setAssetVersions] = useState<AssetVersionVO[]>([]);
  const [taskComments, setTaskComments] = useState<TaskCommentVO[]>([]);
  const [taskAttachments, setTaskAttachments] = useState<TaskAttachmentVO[]>([]);
  const [taskAuditLogs, setTaskAuditLogs] = useState<AuditLogVO[]>([]);
  const [relatedVulnerabilities, setRelatedVulnerabilities] = useState<any[]>([]);
  const [relatedDocuments, setRelatedDocuments] = useState<any[]>([]);
  const [commentSubmitting, setCommentSubmitting] = useState(false);
  const [attachmentSubmitting, setAttachmentSubmitting] = useState(false);
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [decisionSubmitting, setDecisionSubmitting] = useState(false);
  const [collaboratorSubmitting, setCollaboratorSubmitting] = useState(false);
  const [commentForm] = Form.useForm();
  const [attachmentForm] = Form.useForm();
  const [conclusionForm] = Form.useForm();
  const [reviewDecisionForm] = Form.useForm();
  const [collaboratorForm] = Form.useForm();

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [statusModalVisible, setStatusModalVisible] = useState(false);
  const [assignModalVisible, setAssignModalVisible] = useState(false);
  const [collaboratorModalVisible, setCollaboratorModalVisible] = useState(false);
  const [currentTask, setCurrentTask] = useState<TaskVO | null>(null);
  const [form] = Form.useForm();
  const [statusForm] = Form.useForm();
  const [assignForm] = Form.useForm();

  const canAssignTask = ['admin', 'project_leader', 'team_leader', 'ROLE_ADMIN', 'ROLE_LEADER'].includes(user?.role || '');
  const canReviewTask = ['admin', 'project_leader', 'team_leader', 'ROLE_ADMIN', 'ROLE_LEADER'].includes(user?.role || '');

  const getEstimatedHours = (task?: TaskVO | null): number => Number(task?.estimated_hours ?? task?.estimatedHours ?? 0);
  const getActualHours = (task?: TaskVO | null): number => Number(task?.actual_hours ?? task?.actualHours ?? 0);
  const getBurnRate = (task?: TaskVO | null): string => {
    const estimated = getEstimatedHours(task);
    if (!estimated) {
      return '-';
    }
    return `${((getActualHours(task) / estimated) * 100).toFixed(1)}%`;
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getTaskList(params);
      const payload = res.data || (res as any);
      const items = payload.content || payload.items || [];
      const totalCount = payload.totalElements || payload.total || 0;
      setData(items);
      setTotal(totalCount);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const fetchTaskDetail = async (id: string) => {
    setLoading(true);
    try {
      const res = await getTaskDetail(id);
      const payload = getPayload(res);
      setDetailTask(payload);
      conclusionForm.setFieldsValue({
        audit_conclusion: payload?.audit_conclusion || payload?.auditConclusion || '',
      });
      reviewDecisionForm.setFieldsValue({ signature: user?.full_name || user?.username || '', comment: '' });
      const assetId = payload?.asset_id || payload?.assetId;
      const [commentsRes, attachmentsRes, auditLogsRes, vulnerabilitiesRes, documentsRes] = await Promise.all([
        getTaskComments(id),
        getTaskAttachments(id),
        getTaskAuditLogs(id),
        assetId ? getTaskRelatedVulnerabilities(assetId) : Promise.resolve([]),
        assetId ? getDocumentList({ page: 1, page_size: 50, asset_id: assetId }) : Promise.resolve([]),
      ]);
      const commentsPayload = getPayload(commentsRes);
      setTaskComments(Array.isArray(commentsPayload) ? commentsPayload : []);
      const attachmentsPayload = getPayload(attachmentsRes);
      setTaskAttachments(Array.isArray(attachmentsPayload) ? attachmentsPayload : []);
      attachmentForm.resetFields();
      const auditLogsPayload = getPayload(auditLogsRes);
      setTaskAuditLogs(auditLogsPayload?.content || auditLogsPayload?.items || []);
      const vulnerabilitiesPayload = getPayload(vulnerabilitiesRes);
      setRelatedVulnerabilities(vulnerabilitiesPayload?.content || vulnerabilitiesPayload?.items || []);
      const documentsPayload = getPayload(documentsRes);
      setRelatedDocuments(documentsPayload?.content || documentsPayload?.items || []);
    } catch (error) {
      setDetailTask(null);
      setTaskComments([]);
      setTaskAttachments([]);
      setTaskAuditLogs([]);
      setRelatedVulnerabilities([]);
      setRelatedDocuments([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchUsers = async () => {
    try {
      const res = await getUserList({ page: 1, page_size: 200 });
      const payload = (res as any)?.data || res;
      const items = payload.content || payload.items || [];
      setUsers(Array.isArray(items) ? items : []);
    } catch (error) {
      setUsers([]);
    }
  };

  const fetchAssets = async () => {
    try {
      const res = await getAssetList({ page: 1, page_size: 200 });
      const payload = (res as any)?.data || res;
      const items = payload.content || payload.items || [];
      setAssets(Array.isArray(items) ? items : []);
    } catch (error) {
      setAssets([]);
    }
  };

  const fetchAssetVersions = async (assetId?: string) => {
    if (!assetId) {
      setAssetVersions([]);
      form.setFieldsValue({ asset_version_id: undefined });
      return;
    }
    try {
      const res = await getAssetVersions(assetId);
      const payload = (res as any)?.data?.data ?? (res as any)?.data ?? res;
      const source = Array.isArray(payload) ? payload : payload?.items || [];
      const list = source.map((item: any) => ({
        id: String(item?.id ?? ''),
        assetId: item?.assetId ?? item?.asset_id ?? '',
        versionNo: Number(item?.versionNo ?? item?.version_no ?? 0),
        name: item?.name ?? '',
        description: item?.description,
        isDefault: Boolean(item?.isDefault ?? item?.is_default),
        isLatest: Boolean(item?.isLatest ?? item?.is_latest),
        status: item?.status ?? 'active',
      }));
      setAssetVersions(list);
    } catch (error) {
      setAssetVersions([]);
    }
  };

  useEffect(() => {
    if (taskId) {
      fetchTaskDetail(taskId);
      return;
    }
    fetchData();
  }, [params.page, params.page_size, params.search, params.status, taskId]);

  useEffect(() => {
    fetchUsers();
    fetchAssets();
  }, []);

  const handleCreate = () => {
    form.resetFields();
    setAssetVersions([]);
    setIsModalVisible(true);
  };

  const handleStatusUpdate = (record: TaskVO) => {
    setCurrentTask(record);
    statusForm.setFieldsValue({ status: record.status });
    setStatusModalVisible(true);
  };

  const handleAssign = (record: TaskVO) => {
    setCurrentTask(record);
    assignForm.resetFields();
    setAssignModalVisible(true);
  };

  const handleCreateSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        title: values.title,
        asset_id: values.asset_id,
        asset_version_id: values.asset_version_id,
        reference_id: values.asset_id,
        assignee_id: values.assignee_id,
        description: values.description,
        due_date: values.due_date ? values.due_date.toISOString() : undefined,
        estimated_hours: values.estimated_hours,
      };
      
      const createdTaskRes = await createTask(payload as CreateTaskRequest);
      const createdTask = getPayload(createdTaskRes);
      const collaboratorIds: string[] = Array.isArray(values.collaborator_ids) ? values.collaborator_ids : [];
      if (createdTask?.id && collaboratorIds.length > 0) {
        await Promise.all(collaboratorIds.map((id) => addTaskCollaborator(createdTask.id, id)));
      }
      message.success('任务创建成功');
      setIsModalVisible(false);
      if (taskId) {
        fetchTaskDetail(taskId);
      } else {
        fetchData();
      }
    } catch (error) {
      // Handled
    }
  };

  const handleStatusSubmit = async () => {
    try {
      const values = await statusForm.validateFields();
      if (currentTask) {
        await updateTaskStatus(currentTask.id, values as UpdateTaskStatusRequest);
        message.success('状态更新成功');
        setStatusModalVisible(false);
        if (taskId) {
          fetchTaskDetail(taskId);
        } else {
          fetchData();
        }
      }
    } catch (error) {
      // Handled
    }
  };

  const handleAssignSubmit = async () => {
    try {
      const values = await assignForm.validateFields();
      if (!currentTask) return;
      await assignTask(currentTask.id, values);
      message.success('任务分配成功');
      setAssignModalVisible(false);
      if (taskId) {
        fetchTaskDetail(taskId);
      } else {
        fetchData();
      }
    } catch (error) {
      // Handled
    }
  };

  const handleCommentSubmit = async () => {
    if (!taskId) {
      return;
    }
    try {
      const values = await commentForm.validateFields();
      setCommentSubmitting(true);
      await createTaskComment(taskId, values.content, values.spent_hours, values.work_type, values.evidence_path);
      message.success('执行记录添加成功');
      commentForm.resetFields();
      const commentsRes = await getTaskComments(taskId);
      const commentsPayload = getPayload(commentsRes);
      setTaskComments(Array.isArray(commentsPayload) ? commentsPayload : []);
    } finally {
      setCommentSubmitting(false);
    }
  };

  const handleCollaboratorManage = () => {
    if (!detailTask) {
      return;
    }
    collaboratorForm.setFieldsValue({
      collaborator_ids: (detailTask.collaborators || []).map((item) => item.id),
    });
    setCollaboratorModalVisible(true);
  };

  const handleCollaboratorSubmit = async () => {
    if (!taskId || !detailTask) {
      return;
    }
    try {
      const values = await collaboratorForm.validateFields();
      const selectedIds: string[] = Array.isArray(values.collaborator_ids) ? values.collaborator_ids : [];
      const currentIds = (detailTask.collaborators || []).map((item) => item.id);
      const toAdd = selectedIds.filter((id) => !currentIds.includes(id));
      const toRemove = currentIds.filter((id) => !selectedIds.includes(id));
      setCollaboratorSubmitting(true);
      await Promise.all([
        ...toAdd.map((id) => addTaskCollaborator(taskId, id)),
        ...toRemove.map((id) => removeTaskCollaborator(taskId, id)),
      ]);
      message.success('协作者更新成功');
      setCollaboratorModalVisible(false);
      fetchTaskDetail(taskId);
    } finally {
      setCollaboratorSubmitting(false);
    }
  };

  const handleSubmitForReview = async () => {
    if (!taskId || !detailTask) {
      return;
    }
    try {
      const values = await conclusionForm.validateFields();
      setReviewSubmitting(true);
      await updateTask(taskId, {
        audit_conclusion: values.audit_conclusion,
      });
      await submitTaskReview(taskId);
      message.success('已提交审核');
      fetchTaskDetail(taskId);
    } finally {
      setReviewSubmitting(false);
    }
  };

  const handleAttachmentSubmit = async () => {
    if (!taskId) {
      return;
    }
    try {
      const values = await attachmentForm.validateFields();
      setAttachmentSubmitting(true);
      await createTaskAttachment(taskId, values.fileName, values.filePath);
      message.success('任务附件添加成功');
      attachmentForm.resetFields();
      const attachmentsRes = await getTaskAttachments(taskId);
      const attachmentsPayload = getPayload(attachmentsRes);
      setTaskAttachments(Array.isArray(attachmentsPayload) ? attachmentsPayload : []);
      const auditLogsRes = await getTaskAuditLogs(taskId);
      const auditLogsPayload = getPayload(auditLogsRes);
      setTaskAuditLogs(auditLogsPayload?.content || auditLogsPayload?.items || []);
    } finally {
      setAttachmentSubmitting(false);
    }
  };

  const handleApprove = async () => {
    if (!taskId) {
      return;
    }
    try {
      const values = await reviewDecisionForm.validateFields();
      setDecisionSubmitting(true);
      await approveTaskReview(taskId, values.signature, values.comment);
      message.success('审核通过');
      fetchTaskDetail(taskId);
    } finally {
      setDecisionSubmitting(false);
    }
  };

  const handleReject = async () => {
    if (!taskId) {
      return;
    }
    try {
      const values = await reviewDecisionForm.validateFields();
      setDecisionSubmitting(true);
      await rejectTaskReview(taskId, values.signature, values.comment);
      message.success('已驳回，任务回到进行中');
      fetchTaskDetail(taskId);
    } finally {
      setDecisionSubmitting(false);
    }
  };

  const columns = [
    {
      title: '任务标题',
      dataIndex: 'title',
      key: 'title',
      render: (text: string, record: TaskVO) => <a onClick={() => navigate(`/tasks/${record.id}`)}>{text}</a>
    },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status',
      render: (text: string) => <Tag color={statusColors[text] || 'default'}>{statusLabels[text] || text}</Tag>
    },
    {
      title: '创建者',
      key: 'creator',
      render: (_: any, record: TaskVO) => record.creator?.username || '-'
    },
    { 
      title: '责任人', 
      key: 'assignee',
      render: (_: any, record: TaskVO) => record.assignee?.username || '-'
    },
    { 
      title: '截止日期', 
      dataIndex: 'due_date', 
      key: 'due_date',
      render: (text: string) => text ? dayjs(text).format('YYYY-MM-DD HH:mm') : '-'
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: TaskVO) => {
        const canUpdateRecordStatus = (canAssignTask || user?.id === record.assignee?.id) && record.status !== 'review_pending';
        return (
          <Space size="middle">
            <a onClick={() => navigate(`/tasks/${record.id}`)}>详情</a>
            {canUpdateRecordStatus && <a onClick={() => handleStatusUpdate(record)}>更新状态</a>}
            {canAssignTask && <a onClick={() => handleAssign(record)}>分配</a>}
          </Space>
        );
      },
    },
  ];

  if (taskId) {
    const currentUserId = user?.id;
    const assigneeId = detailTask?.assignee?.id;
    const taskStatus = detailTask?.status || '';
    const canSubmitReview = !!detailTask && currentUserId === assigneeId && ['pending', 'in_progress', 'assigned'].includes(taskStatus);
    const canApproveOrReject = !!detailTask && canReviewTask && taskStatus === 'review_pending';
    const canUpdateStatus = !!detailTask && (canAssignTask || currentUserId === assigneeId) && taskStatus !== 'review_pending';
    const collaboratorIds = (detailTask?.collaborators || []).map((item) => item.id);
    const canRecordExecution = !!detailTask && (canAssignTask || currentUserId === assigneeId || collaboratorIds.includes(currentUserId || ''));
    return (
      <Card
        title="审计任务工作台"
        extra={<Button onClick={() => navigate('/tasks')}>返回任务列表</Button>}
        loading={loading}
      >
        {detailTask ? (
          <>
            <Descriptions column={3} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="任务名称">{detailTask.title || '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColors[detailTask.status] || 'default'}>{statusLabels[detailTask.status] || detailTask.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="截止日期">{detailTask.due_date ? dayjs(detailTask.due_date).format('YYYY-MM-DD HH:mm') : '-'}</Descriptions.Item>
              <Descriptions.Item label="创建者">{detailTask.creator?.username || '-'}</Descriptions.Item>
              <Descriptions.Item label="负责人">{detailTask.assignee?.username || '-'}</Descriptions.Item>
              <Descriptions.Item label="协作者">{(detailTask.collaborators || []).map((item) => item.username).join('、') || '-'}</Descriptions.Item>
              <Descriptions.Item label="预估工时">{getEstimatedHours(detailTask) || '-'}</Descriptions.Item>
              <Descriptions.Item label="实际工时">{getActualHours(detailTask)}</Descriptions.Item>
              <Descriptions.Item label="燃尽比例">{getBurnRate(detailTask)}</Descriptions.Item>
              <Descriptions.Item label="资产ID">{(detailTask as any).asset_id || (detailTask as any).assetId || '-'}</Descriptions.Item>
              <Descriptions.Item label="资产版本ID">{(detailTask as any).asset_version_id || (detailTask as any).assetVersionId || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{((detailTask as any).created_at || (detailTask as any).createdAt) ? dayjs((detailTask as any).created_at || (detailTask as any).createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{((detailTask as any).updated_at || (detailTask as any).updatedAt) ? dayjs((detailTask as any).updated_at || (detailTask as any).updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
              <Descriptions.Item label="任务描述" span={3}>{(detailTask as any).description || '-'}</Descriptions.Item>
            </Descriptions>
            <Space>
              {canUpdateStatus && <Button onClick={() => handleStatusUpdate(detailTask)}>更新状态</Button>}
              {canAssignTask && <Button onClick={() => handleAssign(detailTask)}>分配任务</Button>}
              {canAssignTask && <Button onClick={handleCollaboratorManage}>协作者管理</Button>}
            </Space>
            <Tabs
              style={{ marginTop: 20 }}
              items={[
                {
                  key: 'review',
                  label: '审计结论与审批',
                  children: (
                    <>
                      <Form form={conclusionForm} layout="vertical" style={{ marginBottom: 16 }}>
                        <Form.Item name="audit_conclusion" label="审计结论" rules={[{ required: true, message: '请填写审计结论' }]}>
                          <Input.TextArea rows={5} placeholder="填写审计结论、风险摘要与建议措施" />
                        </Form.Item>
                        {canSubmitReview && (
                          <Button type="primary" loading={reviewSubmitting} onClick={handleSubmitForReview}>
                            提交审核
                          </Button>
                        )}
                      </Form>
                      <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
                        <Descriptions.Item label="审核人">{(detailTask as any).reviewed_by || '-'}</Descriptions.Item>
                        <Descriptions.Item label="审核签字">{(detailTask as any).review_signature || '-'}</Descriptions.Item>
                        <Descriptions.Item label="审核时间">{(detailTask as any).reviewed_at ? dayjs((detailTask as any).reviewed_at).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
                        <Descriptions.Item label="审核备注" span={2}>{(detailTask as any).approval_comment || '-'}</Descriptions.Item>
                      </Descriptions>
                      {canApproveOrReject && (
                        <Form form={reviewDecisionForm} layout="vertical">
                          <Form.Item name="signature" label="签字确认" rules={[{ required: true, message: '请填写签字' }]}>
                            <Input placeholder="请输入签字，如：项目组长-张三" />
                          </Form.Item>
                          <Form.Item name="comment" label="审核备注">
                            <Input.TextArea rows={3} placeholder="可选：填写审核意见" />
                          </Form.Item>
                          <Space>
                            <Button type="primary" loading={decisionSubmitting} onClick={handleApprove}>
                              审核通过
                            </Button>
                            <Button danger loading={decisionSubmitting} onClick={handleReject}>
                              驳回整改
                            </Button>
                          </Space>
                        </Form>
                      )}
                    </>
                  ),
                },
                {
                  key: 'execution',
                  label: `执行记录 (${taskComments.length})`,
                  children: (
                    <>
                      <Form form={commentForm} layout="vertical" style={{ marginBottom: 16 }}>
                        <Form.Item name="content" label="新增执行记录" rules={[{ required: true, message: '请输入执行记录内容' }]}>
                          <Input.TextArea rows={3} placeholder="例如：已完成资产配置核查，发现xx风险点" />
                        </Form.Item>
                        <Form.Item name="spent_hours" label="本次实际工时">
                          <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="如：1.5" />
                        </Form.Item>
                        <Form.Item name="work_type" label="执行类型">
                          <Select allowClear placeholder="选择执行类型">
                            <Option value="analysis">代码分析</Option>
                            <Option value="verify">验证复现</Option>
                            <Option value="retest">复测回归</Option>
                            <Option value="fix_review">修复复核</Option>
                            <Option value="meeting">沟通评审</Option>
                            <Option value="other">其他</Option>
                          </Select>
                        </Form.Item>
                        <Form.Item name="evidence_path" label="证据路径">
                          <Input placeholder="例如：/evidence/task-001/login-module-review.md" />
                        </Form.Item>
                        <Button type="primary" loading={commentSubmitting} onClick={handleCommentSubmit} disabled={!canRecordExecution}>提交执行记录</Button>
                      </Form>
                      <Timeline
                        items={taskComments.map((item) => ({
                          children: (
                            <div>
                              <div>{item.content}</div>
                              <div style={{ color: '#8c8c8c', marginTop: 4 }}>
                                {(item.createdAt || item.created_at) ? dayjs(item.createdAt || item.created_at).format('YYYY-MM-DD HH:mm:ss') : '-'}
                                {typeof (item.spentHours ?? item.spent_hours) === 'number' ? ` · 工时 ${item.spentHours ?? item.spent_hours}h` : ''}
                                {item.workType || item.work_type ? ` · 类型 ${executionTypeLabels[(item.workType || item.work_type) as string] || item.workType || item.work_type}` : ''}
                                {item.evidencePath || item.evidence_path ? ` · 证据 ${item.evidencePath || item.evidence_path}` : ''}
                              </div>
                            </div>
                          ),
                        }))}
                      />
                    </>
                  ),
                },
                {
                  key: 'vulnerabilities',
                  label: `关联漏洞 (${relatedVulnerabilities.length})`,
                  children: (
                    <Table
                      rowKey={(record) => record.id || record.vulnerabilityId}
                      size="small"
                      pagination={false}
                      dataSource={relatedVulnerabilities}
                      columns={[
                        {
                          title: '漏洞编号',
                          dataIndex: 'vulnerabilityId',
                          key: 'vulnerabilityId',
                          render: (value: string, record: any) => {
                            const keyword = value || record?.id || '';
                            return keyword ? (
                              <a onClick={() => navigate(`/vulnerabilities?search=${encodeURIComponent(keyword)}`)}>{value || record?.id}</a>
                            ) : '-';
                          },
                        },
                        {
                          title: '漏洞标题',
                          dataIndex: 'title',
                          key: 'title',
                          render: (value: string, record: any) => {
                            const keyword = value || record?.vulnerabilityId || record?.id || '';
                            return keyword ? (
                              <a onClick={() => navigate(`/vulnerabilities?search=${encodeURIComponent(keyword)}`)}>{value || '-'}</a>
                            ) : '-';
                          },
                        },
                        { title: '类型', dataIndex: 'type', key: 'type' },
                        { title: '严重级别', dataIndex: 'severity', key: 'severity' },
                        { title: '状态', dataIndex: 'status', key: 'status' },
                      ]}
                    />
                  ),
                },
                {
                  key: 'documents',
                  label: `文档证据 (${relatedDocuments.length})`,
                  children: (
                    <Table
                      rowKey="id"
                      size="small"
                      pagination={false}
                      dataSource={relatedDocuments}
                      columns={[
                        {
                          title: '文档标题',
                          dataIndex: 'title',
                          key: 'title',
                          render: (value: string, record: any) => {
                            const keyword = value || record?.documentNo || '';
                            return keyword ? (
                              <a onClick={() => navigate(`/documents?search=${encodeURIComponent(keyword)}`)}>{value || '-'}</a>
                            ) : '-';
                          },
                        },
                        { title: '文档编号', dataIndex: 'documentNo', key: 'documentNo' },
                        { title: '分类', dataIndex: 'category', key: 'category' },
                        { title: '状态', dataIndex: 'status', key: 'status' },
                      ]}
                    />
                  ),
                },
                {
                  key: 'attachments',
                  label: `任务附件 (${taskAttachments.length})`,
                  children: (
                    <>
                      <Form form={attachmentForm} layout="vertical" style={{ marginBottom: 16 }}>
                        <Space style={{ width: '100%' }} align="start">
                          <Form.Item
                            name="fileName"
                            label="文件名"
                            rules={[{ required: true, message: '请输入文件名' }]}
                            style={{ width: 260 }}
                          >
                            <Input placeholder="例如：核查截图-防火墙策略.png" />
                          </Form.Item>
                          <Form.Item
                            name="filePath"
                            label="文件路径"
                            rules={[{ required: true, message: '请输入文件路径' }]}
                            style={{ flex: 1 }}
                          >
                            <Input placeholder="例如：/evidence/task-001/fw-policy.png 或对象存储URL" />
                          </Form.Item>
                          <Form.Item label=" ">
                            <Button type="primary" loading={attachmentSubmitting} onClick={handleAttachmentSubmit}>添加附件</Button>
                          </Form.Item>
                        </Space>
                      </Form>
                      <Table
                        rowKey="id"
                        size="small"
                        pagination={false}
                        dataSource={taskAttachments}
                        columns={[
                          { title: '文件名', dataIndex: 'fileName', key: 'fileName' },
                          { title: '文件路径', dataIndex: 'filePath', key: 'filePath' },
                          {
                            title: '上传时间',
                            dataIndex: 'createdAt',
                            key: 'createdAt',
                            render: (value: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
                          },
                        ]}
                      />
                    </>
                  ),
                },
                {
                  key: 'auditLogs',
                  label: `操作留痕 (${taskAuditLogs.length})`,
                  children: (
                    <Table
                      rowKey="id"
                      size="small"
                      pagination={false}
                      dataSource={taskAuditLogs}
                      columns={[
                        { title: '动作', dataIndex: 'action', key: 'action' },
                        { title: '类型', dataIndex: 'resourceType', key: 'resourceType' },
                        { title: '详情', dataIndex: 'details', key: 'details' },
                        {
                          title: '时间',
                          dataIndex: 'createdAt',
                          key: 'createdAt',
                          render: (value: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
                        },
                      ]}
                    />
                  ),
                },
              ]}
            />
          </>
        ) : (
          <div>任务不存在或无权限查看</div>
        )}
      </Card>
    );
  }

  return (
    <Card title="任务管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>创建任务</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Space>
        <Input 
          placeholder="搜索任务标题" 
          prefix={<SearchOutlined />} 
          style={{ width: 300 }} 
          onPressEnter={(e) => setParams({ ...params, search: e.currentTarget.value, page: 1 })}
        />
          <Select
            allowClear
            style={{ width: 180 }}
            placeholder="按状态筛选"
            value={params.status}
            onChange={(value) => setParams({ ...params, status: value, page: 1 })}
          >
            <Option value="pending">待处理</Option>
            <Option value="in_progress">进行中</Option>
            <Option value="review_pending">待审核</Option>
            <Option value="completed">已完成</Option>
            <Option value="cancelled">已取消</Option>
          </Select>
        </Space>
      </div>
      <Table 
        columns={columns} 
        dataSource={data} 
        rowKey="id"
        loading={loading}
        pagination={{
          current: params.page,
          pageSize: params.page_size,
          total,
          onChange: (page, pageSize) => setParams({ ...params, page, page_size: pageSize })
        }}
      />

      <Modal
        title="创建任务"
        open={isModalVisible}
        onOk={handleCreateSubmit}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="任务标题">
            <Input placeholder="不填将自动生成任务名" />
          </Form.Item>
          <Form.Item name="asset_id" label="关联资产" rules={[{ required: true, message: '请选择资产' }]}>
            <Select
              placeholder="请选择资产"
              showSearch
              optionFilterProp="children"
              onChange={(value) => fetchAssetVersions(value)}
            >
              {assets.map((item) => (
                <Option key={item.id} value={item.id}>{item.name} ({item.code})</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="asset_version_id" label="关联资产版本" rules={[{ required: true, message: '请选择资产版本' }]}>
            <Select placeholder="请选择资产版本">
              {assetVersions.map((item) => (
                <Option key={item.id} value={item.id}>
                  {item.name || `v${item.versionNo}`}{item.isLatest ? ' - 最新' : ''}{item.isDefault ? ' - 默认' : ''}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="assignee_id" label="责任人">
            <Select placeholder="不选择则默认为创建者" allowClear showSearch optionFilterProp="children">
              {users.map((item) => (
                <Option key={item.id} value={item.id}>{item.username}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="description" label="任务描述">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="estimated_hours" label="预估工时">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="collaborator_ids" label="协作者">
            <Select mode="multiple" allowClear showSearch optionFilterProp="children" placeholder="可选：协作者可记录执行项">
              {users.map((item) => (
                <Option key={item.id} value={item.id}>{item.username}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="due_date" label="截止日期">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="更新任务状态"
        open={statusModalVisible}
        onOk={handleStatusSubmit}
        onCancel={() => setStatusModalVisible(false)}
        destroyOnClose
      >
        <Form form={statusForm} layout="vertical">
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select>
              <Option value="pending">待处理</Option>
              <Option value="in_progress">进行中</Option>
              <Option value="completed">已完成</Option>
              <Option value="cancelled">已取消</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="协作者管理"
        open={collaboratorModalVisible}
        onOk={handleCollaboratorSubmit}
        confirmLoading={collaboratorSubmitting}
        onCancel={() => setCollaboratorModalVisible(false)}
        destroyOnClose
      >
        <Form form={collaboratorForm} layout="vertical">
          <Form.Item name="collaborator_ids" label="协作者">
            <Select mode="multiple" allowClear showSearch optionFilterProp="children" placeholder="选择协作者">
              {users
                .filter((item) => item.id !== detailTask?.assignee?.id)
                .map((item) => (
                  <Option key={item.id} value={item.id}>{item.username}</Option>
                ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="分配任务"
        open={assignModalVisible}
        onOk={handleAssignSubmit}
        onCancel={() => setAssignModalVisible(false)}
        destroyOnClose
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item name="assignee_id" label="指派给" rules={[{ required: true, message: '请选择指派用户' }]}>
            <Select placeholder="请选择用户" showSearch optionFilterProp="children">
              {users.map((item) => (
                <Option key={item.id} value={item.id}>{item.username}</Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Tasks;
