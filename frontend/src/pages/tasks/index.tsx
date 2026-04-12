import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Tag, Modal, Form, Select, Input, DatePicker, InputNumber, message, Descriptions, Tabs, Progress, Steps, Upload, Row, Col, Tooltip, Spin } from 'antd';
import { PlusOutlined, SearchOutlined, UploadOutlined, PlusCircleOutlined, MinusCircleOutlined, UserOutlined, LockOutlined, TagOutlined, CodeOutlined, SettingOutlined, NodeIndexOutlined, LinkOutlined, InfoCircleOutlined } from '@ant-design/icons';
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
  getTaskRelatedVulnerabilities,
  addTaskCollaborator,
  removeTaskCollaborator,
  submitTaskReview,
  approveTaskReview,
  rejectTaskReview,
} from '@/api/task';
import { createVulnerability, getVulnerabilityOptions } from '@/api/vulnerability';
import type { TaskVO, CreateTaskRequest, UpdateTaskStatusRequest, TaskCommentVO, TaskAttachmentVO } from '@/types/task';
import {
  getAssetList,
  getAssetVersions,
  getAssetDetail as getAssetDetailById,
  updateAsset as updateAssetInfo,
  getAssetMetadata,
  updateAssetMetadata,
  getAssetDeliverables,
  uploadAssetDeliverable,
} from '@/api/asset';
import { getUserList } from '@/api/user';
import { getDocumentList, getDocumentDetail, createDocument, updateDocument } from '@/api/document';
import type { AssetVO, AssetVersionVO, AssetDeliverableVO } from '@/types/asset';
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
  env_collect: '获取环境',
  env_setup: '搭建环境',
  code_audit: '代码审计',
};

const executionStageProgress: Record<string, number> = {
  env_collect: 33,
  env_setup: 66,
  code_audit: 100,
};

const auditProgressStageOrder = ['env_collect', 'env_setup', 'code_audit'];
const stageDocCategory = '审计阶段记录';

const getPayload = (res: any) => (res as any)?.data ?? res;
const containsHtmlTag = (content: string) => /<\/?[a-z][\s\S]*>/i.test(content);
const escapeHtml = (content: string) =>
  content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const renderInlineMarkdown = (text: string) =>
  escapeHtml(text)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');

const renderMarkdownToHtml = (markdown: string) => {
  const codeBlocks: string[] = [];
  const placeholderMarkdown = markdown.replace(/```([\s\S]*?)```/g, (_, block: string) => {
    const index = codeBlocks.push(block) - 1;
    return `@@CODE_BLOCK_${index}@@`;
  });
  const lines = placeholderMarkdown.replace(/\r\n/g, '\n').split('\n');
  const htmlParts: string[] = [];
  let listItems: string[] = [];
  const flushList = () => {
    if (listItems.length > 0) {
      htmlParts.push(`<ul>${listItems.map((item) => `<li>${item}</li>`).join('')}</ul>`);
      listItems = [];
    }
  };

  lines.forEach((raw) => {
    const line = raw.trimEnd();
    if (line.startsWith('- ')) {
      listItems.push(renderInlineMarkdown(line.slice(2).trim()));
      return;
    }
    flushList();
    if (!line.trim()) {
      htmlParts.push('<div><br/></div>');
      return;
    }
    if (line.startsWith('### ')) {
      htmlParts.push(`<h3>${renderInlineMarkdown(line.slice(4).trim())}</h3>`);
      return;
    }
    if (line.startsWith('## ')) {
      htmlParts.push(`<h2>${renderInlineMarkdown(line.slice(3).trim())}</h2>`);
      return;
    }
    if (line.startsWith('# ')) {
      htmlParts.push(`<h1>${renderInlineMarkdown(line.slice(2).trim())}</h1>`);
      return;
    }
    htmlParts.push(`<p>${renderInlineMarkdown(line.trim())}</p>`);
  });
  flushList();

  let html = htmlParts.join('');
  codeBlocks.forEach((block, index) => {
    const codeHtml = `<pre style="padding:12px;background:#f6f8fa;border-radius:6px;overflow:auto;"><code>${escapeHtml(block.trim())}</code></pre>`;
    html = html.replace(`@@CODE_BLOCK_${index}@@`, codeHtml);
  });
  return html;
};

const renderRichContent = (content: string, emptyText: string) => {
  const normalized = (content || '').trim();
  if (!normalized) {
    return `<p>${emptyText}</p>`;
  }
  if (containsHtmlTag(normalized)) {
    return normalized;
  }
  return renderMarkdownToHtml(normalized);
};

const renderListFormItem = (
  name: string,
  label: string,
  placeholder: string,
  icon: React.ReactNode,
  extraField?: { name: string; label: string; options: { label: string; value: string }[]; dependentField?: { name: string; label: string; placeholder: string; showWhenValue: string } },
  hideLabel?: boolean,
  disabled?: boolean,
) => (
  <Form.Item label={hideLabel ? null : label}>
    <div style={{ border: '1px solid #e8e8e8', borderRadius: 8, overflow: 'hidden' }}>
      <div style={{ background: '#fafafa', padding: '12px 16px', borderBottom: '1px solid #e8e8e8', display: 'flex', fontWeight: 500, color: '#595959' }}>
        <div style={{ flex: '1', paddingRight: 8 }}>{label}内容</div>
        {extraField && <div style={{ flex: '0 0 140px', paddingRight: 8 }}>{extraField.label}</div>}
        <div style={{ flex: '1', paddingRight: 8 }}>用途备注</div>
        <div style={{ flex: '0 0 50px', textAlign: 'center' }}>操作</div>
      </div>
      <div style={{ padding: '12px 16px 0' }}>
        <Form.List name={name}>
          {(fields, { add, remove }) => (
            <>
              {fields.map(({ key, name: fieldName, ...restField }, index) => (
                <div key={key} style={{ marginBottom: 12 }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start' }}>
                    <div style={{ flex: '1', paddingRight: 8 }}>
                      <Form.Item {...restField} name={[fieldName, 'value']} style={{ marginBottom: 0 }}>
                        <Input placeholder={placeholder} prefix={icon} disabled={disabled} />
                      </Form.Item>
                    </div>
                    {extraField && (
                      <div style={{ flex: '0 0 140px', paddingRight: 8 }}>
                        <Form.Item {...restField} name={[fieldName, extraField.name]} style={{ marginBottom: 0 }}>
                          <Select options={extraField.options} disabled={disabled} />
                        </Form.Item>
                      </div>
                    )}
                    <div style={{ flex: '1', paddingRight: 8 }}>
                      <Form.Item {...restField} name={[fieldName, 'note']} style={{ marginBottom: 0 }}>
                        <Input placeholder="如：生产环境 / 内网访问" prefix={<TagOutlined style={{ color: '#bfbfbf' }} />} disabled={disabled} />
                      </Form.Item>
                    </div>
                    <div style={{ flex: '0 0 50px', display: 'flex', justifyContent: 'center', paddingTop: 4 }}>
                      {!disabled && (
                        <Space size={8}>
                          {fields.length > 1 && (
                            <Tooltip title="移除">
                              <MinusCircleOutlined
                                style={{ color: '#ff4d4f', fontSize: 16, cursor: 'pointer' }}
                                onClick={() => remove(fieldName)}
                              />
                            </Tooltip>
                          )}
                          {index === fields.length - 1 && (
                            <Tooltip title="添加">
                              <PlusCircleOutlined
                                style={{ color: '#1890ff', fontSize: 16, cursor: 'pointer' }}
                                onClick={() => add({ value: '', note: '', ...(extraField ? { [extraField.name]: extraField.options[0].value } : {}) })}
                              />
                            </Tooltip>
                          )}
                        </Space>
                      )}
                    </div>
                  </div>
                  {extraField?.dependentField && (
                    <Form.Item
                      noStyle
                      shouldUpdate={(prevValues, currentValues) => {
                        const prevList = prevValues[name] || [];
                        const currentList = currentValues[name] || [];
                        return prevList[fieldName]?.[extraField.name] !== currentList[fieldName]?.[extraField.name];
                      }}
                    >
                      {({ getFieldValue }) => {
                        const list = getFieldValue(name) || [];
                        const currentVal = list[fieldName]?.[extraField.name];
                        if (extraField.dependentField && currentVal === extraField.dependentField.showWhenValue) {
                          return (
                            <div style={{ marginTop: 8, paddingLeft: 12, borderLeft: '2px solid #1890ff' }}>
                              <Form.Item {...restField} name={[fieldName, extraField.dependentField.name]} style={{ marginBottom: 0 }}>
                                <Input placeholder={extraField.dependentField.placeholder} prefix={<InfoCircleOutlined style={{ color: '#bfbfbf' }} />} disabled={disabled} />
                              </Form.Item>
                            </div>
                          );
                        }
                        return null;
                      }}
                    </Form.Item>
                  )}
                </div>
              ))}
              {fields.length === 0 && !disabled && (
                <div style={{ marginBottom: 12 }}>
                  <Button type="dashed" onClick={() => add({ value: '', note: '', ...(extraField ? { [extraField.name]: extraField.options[0].value } : {}) })} block icon={<PlusOutlined />}>
                    添加
                  </Button>
                </div>
              )}
            </>
          )}
        </Form.List>
      </div>
    </div>
  </Form.Item>
);

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
  const [relatedVulnerabilities, setRelatedVulnerabilities] = useState<any[]>([]);
  const [relatedDocuments, setRelatedDocuments] = useState<any[]>([]);
  const [assetDetail, setAssetDetail] = useState<AssetVO | null>(null);
  const [assetMetadataVersionId, setAssetMetadataVersionId] = useState<string>();
  const [assetDeliverables, setAssetDeliverables] = useState<AssetDeliverableVO[]>([]);
  const [vulnTypeOptions, setVulnTypeOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [relatedVulnModalVisible, setRelatedVulnModalVisible] = useState(false);
  const [relatedVulnSubmitting, setRelatedVulnSubmitting] = useState(false);
  const [attachmentSubmitting, setAttachmentSubmitting] = useState(false);
  const [assetInfoSubmitting, setAssetInfoSubmitting] = useState(false);
  const [overviewSubmitting, setOverviewSubmitting] = useState(false);
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [overviewStep, setOverviewStep] = useState(0);
  const [overviewExists, setOverviewExists] = useState(false);
  const [baseOverviewInheritedFromVersionId, setBaseOverviewInheritedFromVersionId] = useState<string>();
  const [deliverableSubmitting, setDeliverableSubmitting] = useState(false);
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [decisionSubmitting, setDecisionSubmitting] = useState(false);
  const [collaboratorSubmitting, setCollaboratorSubmitting] = useState(false);
  const [manualStageSubmitting, setManualStageSubmitting] = useState(false);
  const [deliverableUploadList, setDeliverableUploadList] = useState<any[]>([]);
  const [manualProgressStage, setManualProgressStage] = useState<string>();
  const [viewStageKey, setViewStageKey] = useState<string>(auditProgressStageOrder[0]);
  const [taskInfoExpanded, setTaskInfoExpanded] = useState(false);
  const [stageNoteMap, setStageNoteMap] = useState<Record<string, string>>({
    env_collect: '',
    env_setup: '',
    code_audit: '',
  });
  const [stageDocIdMap, setStageDocIdMap] = useState<Record<string, string | undefined>>({
    env_collect: undefined,
    env_setup: undefined,
    code_audit: undefined,
  });
  const [stageDocSavingMap, setStageDocSavingMap] = useState<Record<string, boolean>>({
    env_collect: false,
    env_setup: false,
    code_audit: false,
  });
  const [stageDocEditingMap, setStageDocEditingMap] = useState<Record<string, boolean>>({
    env_collect: false,
    env_setup: false,
    code_audit: false,
  });
  const [documentPreviewVisible, setDocumentPreviewVisible] = useState(false);
  const [documentPreviewLoading, setDocumentPreviewLoading] = useState(false);
  const [documentPreviewData, setDocumentPreviewData] = useState<any>(null);
  const [attachmentForm] = Form.useForm();
  const [assetInfoForm] = Form.useForm();
  const [overviewForm] = Form.useForm();
  const [deliverableForm] = Form.useForm();
  const [conclusionForm] = Form.useForm();
  const [reviewDecisionForm] = Form.useForm();
  const [collaboratorForm] = Form.useForm();
  const [relatedVulnForm] = Form.useForm();

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

  const stepToModule = ['base', 'network', 'env', 'account', 'extra'] as const;
  type MetadataModule = typeof stepToModule[number];
  const moduleFieldNames: Record<MetadataModule, (string | string[])[]> = {
    base: ['runtimePlatform', 'deploymentMethod', 'systemArchitecture', 'programmingLanguagesList'],
    network: ['servicePortsList', 'apiEndpointsList'],
    env: ['installPath', 'startupCommand', 'databaseType', 'databaseConnection', 'startupScriptPathList', 'configFilePathList'],
    account: ['accountCredentials'],
    extra: ['specialNotes'],
  };

  const versionLabelById = (versionId?: string) => {
    if (!versionId) return '-';
    const found = assetVersions.find((item) => item.id === versionId);
    if (!found) return versionId;
    return `${found.name || `v${found.versionNo}`}${found.isDefault ? ' - 默认' : ''}${found.isLatest ? ' - 最新' : ''}`;
  };

  const getStageDocMarker = (taskId: string, stageKey: string) => `[任务:${taskId}][阶段:${stageKey}]`;
  const getStageDocTitle = (task: TaskVO, stageKey: string) => `[审计阶段记录]${getStageDocMarker(task.id, stageKey)} ${task.title || '未命名任务'}`;
  const getStageDocSummary = (task: TaskVO, stageKey: string) =>
    `${getStageDocMarker(task.id, stageKey)} 任务「${task.title || '-'}」的${executionTypeLabels[stageKey] || stageKey}阶段过程记录`;
  const stageDocTaskFieldMap: Record<string, { snake: keyof TaskVO; camel: keyof TaskVO }> = {
    env_collect: { snake: 'env_collect_doc_id', camel: 'envCollectDocId' },
    env_setup: { snake: 'env_setup_doc_id', camel: 'envSetupDocId' },
    code_audit: { snake: 'code_audit_doc_id', camel: 'codeAuditDocId' },
  };
  const getBoundStageDocId = (task: TaskVO, stageKey: string): string | undefined => {
    const field = stageDocTaskFieldMap[stageKey];
    if (!field) {
      return undefined;
    }
    const bound = task[field.snake] || task[field.camel];
    return typeof bound === 'string' ? bound : undefined;
  };
  const buildStageDocBindingPayload = (stageKey: string, docId: string): Partial<CreateTaskRequest> => {
    if (stageKey === 'env_collect') {
      return { env_collect_doc_id: docId };
    }
    if (stageKey === 'env_setup') {
      return { env_setup_doc_id: docId };
    }
    return { code_audit_doc_id: docId };
  };

  const pickStageDocument = (docs: any[], marker: string) => {
    const candidates = docs.filter((doc) => {
      const title = String(doc?.title || '');
      const summary = String(doc?.summary || '');
      return title.includes(marker) || summary.includes(marker);
    });
    if (candidates.length === 0) {
      return undefined;
    }
    return candidates.sort((a, b) => {
      const aTime = dayjs(a?.updatedAt || a?.updated_at || a?.createdAt || a?.created_at || 0).valueOf();
      const bTime = dayjs(b?.updatedAt || b?.updated_at || b?.createdAt || b?.created_at || 0).valueOf();
      return bTime - aTime;
    })[0];
  };

  const sortDocumentsByUpdatedAt = (docs: any[]) => [...docs].sort((a, b) => {
    const aTime = dayjs(a?.updatedAt || a?.updated_at || a?.createdAt || a?.created_at || 0).valueOf();
    const bTime = dayjs(b?.updatedAt || b?.updated_at || b?.createdAt || b?.created_at || 0).valueOf();
    return bTime - aTime;
  });

  const loadTaskRelatedDocuments = async (task: TaskVO, initialDocs?: any[]) => {
    const currentDocs = Array.isArray(initialDocs) ? initialDocs : [];
    const boundDocIds = auditProgressStageOrder
      .map((stageKey) => getBoundStageDocId(task, stageKey))
      .filter((id): id is string => !!id);
    const missingDocIds = boundDocIds.filter((id) => !currentDocs.some((doc) => doc?.id === id));
    if (missingDocIds.length === 0) {
      return sortDocumentsByUpdatedAt(currentDocs);
    }
    const missingDocs = await Promise.all(
      missingDocIds.map((id) => getDocumentDetail(id).then((res) => getPayload(res)).catch(() => null)),
    );
    return sortDocumentsByUpdatedAt([
      ...currentDocs,
      ...missingDocs.filter((doc) => !!doc),
    ]);
  };

  const hydrateStageDocuments = async (task: TaskVO, docs: any[]) => {
    const nextDocIds: Record<string, string | undefined> = {
      env_collect: undefined,
      env_setup: undefined,
      code_audit: undefined,
    };
    const nextNotes: Record<string, string> = {
      env_collect: '',
      env_setup: '',
      code_audit: '',
    };
    const fetches: Promise<void>[] = [];
    const assetId = (task as any)?.asset_id || (task as any)?.assetId;
    auditProgressStageOrder.forEach((stageKey) => {
      const marker = getStageDocMarker(task.id, stageKey);
      fetches.push((async () => {
        const boundDocId = getBoundStageDocId(task, stageKey);
        let matched = boundDocId ? { id: boundDocId } : pickStageDocument(docs, marker);
        if (!matched?.id) {
          const searchRes = await getDocumentList({ page: 1, page_size: 100, task_id: task.id, search: marker });
          const searchPayload = getPayload(searchRes);
          const searchList = searchPayload?.content || searchPayload?.items || [];
          matched = pickStageDocument(Array.isArray(searchList) ? searchList : [], marker);
        }
        if (!matched?.id && assetId) {
          const searchRes = await getDocumentList({ page: 1, page_size: 100, asset_id: assetId, search: marker });
          const searchPayload = getPayload(searchRes);
          const searchList = searchPayload?.content || searchPayload?.items || [];
          matched = pickStageDocument(Array.isArray(searchList) ? searchList : [], marker);
        }
        if (!matched?.id) {
          return;
        }
        nextDocIds[stageKey] = matched.id;
        const detailRes = await getDocumentDetail(matched.id);
        const detail = getPayload(detailRes) as any;
        nextNotes[stageKey] = detail?.content || '';
      })().catch(() => {
        nextNotes[stageKey] = '';
      }));
    });
    if (fetches.length > 0) {
      await Promise.all(fetches);
    }
    setStageDocIdMap(nextDocIds);
    setStageNoteMap(nextNotes);
  };

  const parseJsonList = (str: string | undefined | null, defaultTypeField?: { key: string; val: string }) => {
    if (!str) return [{ value: '', note: '', ...(defaultTypeField ? { [defaultTypeField.key]: defaultTypeField.val } : {}) }];
    try {
      const parsed = JSON.parse(str);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed.map((item: any) => ({
          value: item?.value || '',
          note: item?.note || '',
          ...(defaultTypeField ? { [defaultTypeField.key]: item?.[defaultTypeField.key] || defaultTypeField.val } : {}),
        }));
      }
    } catch (error) {
      const arr = str.split(/[,;\n]+/).map((s) => s.trim()).filter((s) => !!s);
      if (arr.length > 0) {
        return arr.map((v) => ({
          value: v,
          note: '',
          ...(defaultTypeField ? { [defaultTypeField.key]: defaultTypeField.val } : {}),
        }));
      }
    }
    return [{ value: '', note: '', ...(defaultTypeField ? { [defaultTypeField.key]: defaultTypeField.val } : {}) }];
  };

  const loadAssetMetadata = async (assetId: string, module: MetadataModule, assetVersionId?: string) => {
    try {
      setOverviewLoading(true);
      const metadataRes = await getAssetMetadata(assetId, module, assetVersionId);
      const data = getPayload(metadataRes) as any;
      if (data && data.id) {
        let accountCredentials: Array<{ usageNote?: string; username?: string; password?: string }> = [];
        if (module === 'account') {
          try {
            const source = data.accountCredentialsJson ?? data.account_credentials_json ?? data.accountCredentials ?? data.account_credentials;
            const parsed = Array.isArray(source) ? source : (source ? JSON.parse(source) : []);
            if (Array.isArray(parsed)) {
              accountCredentials = parsed.map((item: any) => ({
                usageNote: item?.usageNote || '',
                username: item?.username || '',
                password: item?.password ?? item?.pwd ?? item?.pass ?? item?.secret ?? '',
              }));
            }
          } catch (error) {
            accountCredentials = [];
          }
          const adminUsername = data.adminUsername ?? data.admin_username;
          if (accountCredentials.length === 0 && adminUsername) {
            accountCredentials = [{
              usageNote: '管理员登录',
              username: adminUsername,
              password: data.adminPassword ?? data.admin_password ?? '',
            }];
          }
          if (accountCredentials.length === 0) {
            accountCredentials = [{ usageNote: '', username: '', password: '' }];
          }
        }

        let programmingLanguagesList: Array<{ language: string; version: string; framework: string; note: string }> = [];
        if (module === 'base') {
          try {
            const parsedLang = data.programmingLanguagesJson ? JSON.parse(data.programmingLanguagesJson) : [];
            if (Array.isArray(parsedLang) && parsedLang.length > 0) {
              programmingLanguagesList = parsedLang.map((item: any) => ({
                language: item?.language || '',
                version: item?.version || '',
                framework: item?.framework || '',
                note: item?.note || '',
              }));
            }
          } catch (error) {
            programmingLanguagesList = [];
          }
          if (programmingLanguagesList.length === 0) {
            programmingLanguagesList = [{ language: '', version: '', framework: '', note: '' }];
          }
          setBaseOverviewInheritedFromVersionId(data.copiedFromAssetVersionId ?? data.copied_from_asset_version_id ?? undefined);
        }

        overviewForm.setFieldsValue({
          ...data,
          ...(module === 'base' ? { programmingLanguagesList } : {}),
          ...(module === 'network' ? {
            servicePortsList: parseJsonList(data.servicePorts, { key: 'accessType', val: 'all' }),
            apiEndpointsList: parseJsonList(data.apiEndpoints, { key: 'authType', val: 'auth_required' }),
          } : {}),
          ...(module === 'env' ? {
            startupScriptPathList: parseJsonList(data.startupScriptPath),
            configFilePathList: parseJsonList(data.configFilePath),
          } : {}),
          ...(module === 'account' ? { accountCredentials } : {}),
        });
        setOverviewExists(true);
      } else {
        if (module === 'base') {
          setBaseOverviewInheritedFromVersionId(undefined);
        }
        setOverviewExists(false);
      }
    } finally {
      setOverviewLoading(false);
    }
  };

  const fetchAssetDeliverables = async (assetId: string, assetVersionId?: string) => {
    const deliverablesRes = await getAssetDeliverables(assetId, assetVersionId);
    const deliverablesPayload = getPayload(deliverablesRes);
    setAssetDeliverables(Array.isArray(deliverablesPayload) ? deliverablesPayload : []);
  };

  const fetchTaskAssetContext = async (assetId?: string, assetVersionId?: string) => {
    if (!assetId) {
      setAssetDetail(null);
      setAssetMetadataVersionId(undefined);
      setAssetDeliverables([]);
      setOverviewStep(0);
      setBaseOverviewInheritedFromVersionId(undefined);
      setOverviewExists(false);
      assetInfoForm.resetFields();
      overviewForm.resetFields();
      deliverableForm.resetFields();
      setDeliverableUploadList([]);
      return;
    }
    const assetRes = await getAssetDetailById(assetId);
    const assetPayload = getPayload(assetRes) as any;
    const normalizedAsset: AssetVO = {
      ...assetPayload,
      team_id: assetPayload?.team_id ?? assetPayload?.teamId,
      team_name: assetPayload?.team_name ?? assetPayload?.teamName,
      owner_id: assetPayload?.owner_id ?? assetPayload?.ownerId,
      owner_name: assetPayload?.owner_name ?? assetPayload?.ownerName,
      target_type: assetPayload?.target_type ?? assetPayload?.targetType ?? '',
      target_source: assetPayload?.target_source ?? assetPayload?.targetSource ?? '',
      created_at: assetPayload?.created_at ?? assetPayload?.createdAt ?? '',
      updated_at: assetPayload?.updated_at ?? assetPayload?.updatedAt,
    };
    setAssetDetail(normalizedAsset);
    assetInfoForm.setFieldsValue({
      name: normalizedAsset.name,
      code: normalizedAsset.code,
      target_type: normalizedAsset.target_type,
      target_source: normalizedAsset.target_source,
      description: normalizedAsset.description,
      priority: normalizedAsset.priority,
      status: normalizedAsset.status,
    });
    setAssetMetadataVersionId(assetVersionId);
    await fetchAssetDeliverables(assetId, assetVersionId);
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
      setTaskInfoExpanded(false);
      setDetailTask(payload);
      conclusionForm.setFieldsValue({
        audit_conclusion: payload?.audit_conclusion || payload?.auditConclusion || '',
      });
      reviewDecisionForm.setFieldsValue({ signature: user?.full_name || user?.username || '', comment: '' });
      const assetId = payload?.asset_id || payload?.assetId;
      const assetVersionId = payload?.asset_version_id || payload?.assetVersionId;
      const [commentsRes, attachmentsRes, vulnerabilitiesRes, documentsRes] = await Promise.all([
        getTaskComments(id),
        getTaskAttachments(id),
        assetId ? getTaskRelatedVulnerabilities(assetId) : Promise.resolve([]),
        getDocumentList({ page: 1, page_size: 100, task_id: id }),
      ]);
      const commentsPayload = getPayload(commentsRes);
      const comments = Array.isArray(commentsPayload) ? commentsPayload : [];
      setTaskComments(comments);
      const sortedComments = [...comments].sort((a, b) => {
        const aTime = dayjs(a.createdAt || a.created_at || 0).valueOf();
        const bTime = dayjs(b.createdAt || b.created_at || 0).valueOf();
        return bTime - aTime;
      });
      const initialStage = (sortedComments[0]?.workType || sortedComments[0]?.work_type || '') as string;
      const normalizedStage = auditProgressStageOrder.includes(initialStage) ? initialStage : auditProgressStageOrder[0];
      setManualProgressStage(normalizedStage);
      setViewStageKey(normalizedStage);
      setStageDocEditingMap({
        env_collect: false,
        env_setup: false,
        code_audit: false,
      });
      const attachmentsPayload = getPayload(attachmentsRes);
      setTaskAttachments(Array.isArray(attachmentsPayload) ? attachmentsPayload : []);
      attachmentForm.resetFields();
      const vulnerabilitiesPayload = getPayload(vulnerabilitiesRes);
      setRelatedVulnerabilities(vulnerabilitiesPayload?.content || vulnerabilitiesPayload?.items || []);
      const documentsPayload = getPayload(documentsRes);
      const initialDocumentList = documentsPayload?.content || documentsPayload?.items || [];
      const documentList = await loadTaskRelatedDocuments(payload as TaskVO, Array.isArray(initialDocumentList) ? initialDocumentList : []);
      setRelatedDocuments(documentList);
      await hydrateStageDocuments(payload as TaskVO, Array.isArray(documentList) ? documentList : []);
      fetchAssetVersions(assetId);
      await fetchTaskAssetContext(assetId, assetVersionId);
      fetchVulnerabilityTypeOptions();
    } catch (error) {
      setDetailTask(null);
      setTaskComments([]);
      setTaskAttachments([]);
      setRelatedVulnerabilities([]);
      setRelatedDocuments([]);
      setManualProgressStage(auditProgressStageOrder[0]);
      setViewStageKey(auditProgressStageOrder[0]);
      setStageNoteMap({
        env_collect: '',
        env_setup: '',
        code_audit: '',
      });
      setStageDocIdMap({
        env_collect: undefined,
        env_setup: undefined,
        code_audit: undefined,
      });
      setStageDocEditingMap({
        env_collect: false,
        env_setup: false,
        code_audit: false,
      });
      setAssetDetail(null);
      setAssetMetadataVersionId(undefined);
      setAssetDeliverables([]);
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

  const fetchVulnerabilityTypeOptions = async () => {
    try {
      const res = await getVulnerabilityOptions();
      const payload = (res as any)?.data ?? res;
      const types = Array.isArray(payload?.types) ? payload.types : [];
      setVulnTypeOptions(types.map((item: any) => ({
        value: String(item?.value ?? item?.label ?? ''),
        label: String(item?.label ?? item?.value ?? ''),
      })).filter((item: any) => !!item.value));
    } catch (error) {
      setVulnTypeOptions([]);
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

  useEffect(() => {
    if (!assetDetail) {
      return;
    }
    loadAssetMetadata(assetDetail.id, stepToModule[overviewStep], assetMetadataVersionId);
  }, [assetDetail?.id, overviewStep, assetMetadataVersionId]);

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
      message.success('附件添加成功');
      attachmentForm.resetFields();
      const attachmentsRes = await getTaskAttachments(taskId);
      const attachmentsPayload = getPayload(attachmentsRes);
      setTaskAttachments(Array.isArray(attachmentsPayload) ? attachmentsPayload : []);
    } finally {
      setAttachmentSubmitting(false);
    }
  };

  const handleOpenDocumentPreview = async (documentId: string) => {
    setDocumentPreviewVisible(true);
    setDocumentPreviewLoading(true);
    try {
      const res = await getDocumentDetail(documentId);
      const payload = getPayload(res);
      setDocumentPreviewData(payload);
    } finally {
      setDocumentPreviewLoading(false);
    }
  };

  const handleManualProgressSwitch = async () => {
    if (!taskId) {
      return;
    }
    const currentStage = manualProgressStage && auditProgressStageOrder.includes(manualProgressStage)
      ? manualProgressStage
      : auditProgressStageOrder[0];
    const currentIndex = auditProgressStageOrder.indexOf(currentStage);
    if (currentIndex >= auditProgressStageOrder.length - 1) {
      message.info('当前已是最后阶段');
      return;
    }
    const nextStage = auditProgressStageOrder[currentIndex + 1];
    try {
      setManualStageSubmitting(true);
      await createTaskComment(taskId, `审计阶段切换至：${executionTypeLabels[nextStage]}`, undefined, nextStage);
      message.success('审计进度已更新');
      setManualProgressStage(nextStage);
      setViewStageKey(nextStage);
      const commentsRes = await getTaskComments(taskId);
      const commentsPayload = getPayload(commentsRes);
      setTaskComments(Array.isArray(commentsPayload) ? commentsPayload : []);
    } finally {
      setManualStageSubmitting(false);
    }
  };

  const handleSaveStageNote = async (stageKey: string) => {
    if (!detailTask) {
      return;
    }
    const assetId = (detailTask as any)?.asset_id || (detailTask as any)?.assetId;
    if (!assetId) {
      message.warning('当前任务未关联资产，无法保存阶段文档');
      return;
    }
    const nextSavingMap = { ...stageDocSavingMap, [stageKey]: true };
    setStageDocSavingMap(nextSavingMap);
    try {
      const title = getStageDocTitle(detailTask, stageKey);
      const content = stageNoteMap[stageKey] || '';
      const marker = getStageDocMarker(detailTask.id, stageKey);
      let existingDocId = stageDocIdMap[stageKey] || getBoundStageDocId(detailTask, stageKey);
      if (!existingDocId) {
        const taskSearchRes = await getDocumentList({ page: 1, page_size: 100, task_id: detailTask.id, search: marker });
        const taskSearchPayload = getPayload(taskSearchRes);
        const taskSearchList = taskSearchPayload?.content || taskSearchPayload?.items || [];
        let matched = pickStageDocument(Array.isArray(taskSearchList) ? taskSearchList : [], marker);
        if (!matched?.id) {
          const searchRes = await getDocumentList({ page: 1, page_size: 100, asset_id: assetId, search: marker });
          const searchPayload = getPayload(searchRes);
          const searchList = searchPayload?.content || searchPayload?.items || [];
          matched = pickStageDocument(Array.isArray(searchList) ? searchList : [], marker);
        }
        if (matched?.id) {
          existingDocId = matched.id;
          setStageDocIdMap((prev) => ({ ...prev, [stageKey]: matched.id }));
        }
      }
      if (existingDocId) {
        await updateDocument(existingDocId, {
          title,
          content,
          changelog: `更新${executionTypeLabels[stageKey] || stageKey}阶段记录`,
        });
      } else {
        const created = await createDocument({
          title,
          category: stageDocCategory,
          summary: getStageDocSummary(detailTask, stageKey),
          content,
          asset_id: assetId,
          task_id: detailTask.id,
        });
        const createdPayload = getPayload(created) as any;
        if (createdPayload?.id) {
          setStageDocIdMap((prev) => ({ ...prev, [stageKey]: createdPayload.id }));
          existingDocId = createdPayload.id;
        }
      }
      if (existingDocId && getBoundStageDocId(detailTask, stageKey) !== existingDocId) {
        const updatedTask = await updateTask(detailTask.id, buildStageDocBindingPayload(stageKey, existingDocId));
        const updatedTaskPayload = getPayload(updatedTask) as TaskVO;
        setDetailTask(updatedTaskPayload);
        const relatedTaskDocuments = await loadTaskRelatedDocuments(updatedTaskPayload);
        setRelatedDocuments(relatedTaskDocuments);
      } else {
        const relatedTaskDocuments = await loadTaskRelatedDocuments(detailTask);
        setRelatedDocuments(relatedTaskDocuments);
      }
      setStageDocEditingMap((prev) => ({ ...prev, [stageKey]: false }));
      message.success('阶段文档已保存，可在文档与附件中查看');
    } finally {
      setStageDocSavingMap((prev) => ({ ...prev, [stageKey]: false }));
    }
  };

  const handleAssetInfoSubmit = async () => {
    if (!assetDetail) {
      return;
    }
    try {
      const values = await assetInfoForm.validateFields();
      setAssetInfoSubmitting(true);
      await updateAssetInfo(assetDetail.id, {
        name: values.name,
        target_type: values.target_type,
        target_source: values.target_source,
        description: values.description,
        priority: values.priority,
        status: values.status,
      });
      message.success('资产基本信息更新成功');
      const assetRes = await getAssetDetailById(assetDetail.id);
      const assetPayload = getPayload(assetRes) as any;
      const normalizedAsset: AssetVO = {
        ...assetPayload,
        team_id: assetPayload?.team_id ?? assetPayload?.teamId,
        team_name: assetPayload?.team_name ?? assetPayload?.teamName,
        owner_id: assetPayload?.owner_id ?? assetPayload?.ownerId,
        owner_name: assetPayload?.owner_name ?? assetPayload?.ownerName,
        target_type: assetPayload?.target_type ?? assetPayload?.targetType ?? '',
        target_source: assetPayload?.target_source ?? assetPayload?.targetSource ?? '',
        created_at: assetPayload?.created_at ?? assetPayload?.createdAt ?? '',
        updated_at: assetPayload?.updated_at ?? assetPayload?.updatedAt,
      };
      setAssetDetail(normalizedAsset);
      assetInfoForm.setFieldsValue({
        name: normalizedAsset.name,
        code: normalizedAsset.code,
        target_type: normalizedAsset.target_type,
        target_source: normalizedAsset.target_source,
        description: normalizedAsset.description,
        priority: normalizedAsset.priority,
        status: normalizedAsset.status,
      });
    } finally {
      setAssetInfoSubmitting(false);
    }
  };

  const handleOverviewSubmit = async () => {
    if (!assetDetail) {
      return;
    }
    try {
      const module = stepToModule[overviewStep];
      const values = await overviewForm.validateFields(moduleFieldNames[module] as any);
      setOverviewSubmitting(true);
      if (module === 'base') {
        const programmingLanguages = Array.isArray(values.programmingLanguagesList)
          ? values.programmingLanguagesList
            .map((item: any) => ({
              language: item?.language?.trim() || '',
              version: item?.version?.trim() || '',
              framework: item?.framework?.trim() || '',
              note: item?.note?.trim() || '',
            }))
            .filter((item: any) => item.language || item.version || item.framework || item.note)
          : [];
        await updateAssetMetadata(assetDetail.id, 'base', {
          runtimePlatform: values.runtimePlatform?.trim() || '',
          deploymentMethod: values.deploymentMethod?.trim() || '',
          systemArchitecture: values.systemArchitecture?.trim() || '',
          programmingLanguagesJson: JSON.stringify(programmingLanguages),
        }, assetMetadataVersionId);
      } else if (module === 'network') {
        const stringifyList = (
          list: Array<{ value?: string; note?: string; [key: string]: any }> | undefined,
          typeField?: { key: string; val: string },
        ) => JSON.stringify(
          (Array.isArray(list) ? list : [])
            .map((item) => ({
              value: item?.value?.trim() || '',
              note: item?.note?.trim() || '',
              ...(typeField ? { [typeField.key]: (item?.[typeField.key] || typeField.val).trim?.() || item?.[typeField.key] || typeField.val } : {}),
            }))
            .filter((item) => item.value || item.note),
        );
        await updateAssetMetadata(assetDetail.id, 'network', {
          servicePorts: stringifyList(values.servicePortsList, { key: 'accessType', val: 'all' }),
          apiEndpoints: stringifyList(values.apiEndpointsList, { key: 'authType', val: 'auth_required' }),
        }, assetMetadataVersionId);
      } else if (module === 'env') {
        const stringifyList = (list: Array<{ value?: string; note?: string }> | undefined) => JSON.stringify(
          (Array.isArray(list) ? list : [])
            .map((item) => ({
              value: item?.value?.trim() || '',
              note: item?.note?.trim() || '',
            }))
            .filter((item) => item.value || item.note),
        );
        await updateAssetMetadata(assetDetail.id, 'env', {
          installPath: values.installPath?.trim() || '',
          startupCommand: values.startupCommand?.trim() || '',
          databaseType: values.databaseType?.trim() || '',
          databaseConnection: values.databaseConnection?.trim() || '',
          startupScriptPath: stringifyList(values.startupScriptPathList),
          configFilePath: stringifyList(values.configFilePathList),
        }, assetMetadataVersionId);
      } else if (module === 'account') {
        const normalizedAccounts = Array.isArray(values.accountCredentials)
          ? values.accountCredentials
            .map((item: any) => ({
              usageNote: item?.usageNote?.trim() || '',
              username: item?.username?.trim() || '',
              password: item?.password || '',
            }))
            .filter((item: any) => item.usageNote || item.username || item.password)
          : [];
        await updateAssetMetadata(assetDetail.id, 'account', {
          accountCredentialsJson: JSON.stringify(normalizedAccounts),
        }, assetMetadataVersionId);
      } else {
        await updateAssetMetadata(assetDetail.id, 'extra', {
          specialNotes: values.specialNotes?.trim() || '',
        }, assetMetadataVersionId);
      }
      setOverviewExists(true);
      message.success('产品概览更新成功');
      await loadAssetMetadata(assetDetail.id, module, assetMetadataVersionId);
    } finally {
      setOverviewSubmitting(false);
    }
  };

  const handleAssetVersionChange = async (value?: string) => {
    if (!assetDetail) {
      return;
    }
    setAssetMetadataVersionId(value);
    await fetchAssetDeliverables(assetDetail.id, value);
  };

  const handleDeliverableSubmit = async () => {
    if (!assetDetail) {
      return;
    }
    if (deliverableUploadList.length === 0) {
      message.warning('请先选择交付物文件');
      return;
    }
    try {
      const values = await deliverableForm.validateFields();
      const currentFile = deliverableUploadList[0]?.originFileObj;
      if (!currentFile) {
        message.warning('文件读取失败，请重新选择');
        return;
      }
      setDeliverableSubmitting(true);
      const formData = new FormData();
      formData.append('file', currentFile);
      formData.append('deliverableType', values.deliverable_type);
      if (values.description) {
        formData.append('description', values.description);
      }
      await uploadAssetDeliverable(assetDetail.id, formData, assetMetadataVersionId);
      message.success('交付物添加成功');
      deliverableForm.resetFields();
      setDeliverableUploadList([]);
      await fetchAssetDeliverables(assetDetail.id, assetMetadataVersionId);
    } finally {
      setDeliverableSubmitting(false);
    }
  };

  const handleCreateRelatedVulnerability = () => {
    const assetId = (detailTask as any)?.asset_id || (detailTask as any)?.assetId;
    if (!assetId) {
      message.warning('当前任务未关联资产，无法创建漏洞');
      return;
    }
    relatedVulnForm.resetFields();
    relatedVulnForm.setFieldsValue({
      discovered_version_id: (detailTask as any)?.asset_version_id || (detailTask as any)?.assetVersionId,
    });
    setRelatedVulnModalVisible(true);
  };

  const handleSubmitRelatedVulnerability = async () => {
    if (!taskId || !detailTask) {
      return;
    }
    const assetId = (detailTask as any)?.asset_id || (detailTask as any)?.assetId;
    if (!assetId) {
      message.warning('当前任务未关联资产，无法创建漏洞');
      return;
    }
    try {
      const values = await relatedVulnForm.validateFields();
      setRelatedVulnSubmitting(true);
      await createVulnerability({
        ...values,
        asset_id: assetId,
        affected_versions: values.affected_versions?.trim() ? values.affected_versions.trim() : undefined,
      });
      message.success('漏洞创建成功');
      setRelatedVulnModalVisible(false);
      relatedVulnForm.resetFields();
      const vulnerabilitiesRes = await getTaskRelatedVulnerabilities(assetId);
      const vulnerabilitiesPayload = getPayload(vulnerabilitiesRes);
      setRelatedVulnerabilities(vulnerabilitiesPayload?.content || vulnerabilitiesPayload?.items || []);
    } finally {
      setRelatedVulnSubmitting(false);
    }
  };

  const taskActionModals = (
    <>
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
    </>
  );

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
    const creatorId = detailTask?.creator?.id;
    const taskStatus = detailTask?.status || '';
    const canSubmitReview = !!detailTask && currentUserId === assigneeId && ['pending', 'in_progress', 'assigned'].includes(taskStatus);
    const canApproveOrReject = !!detailTask && canReviewTask && taskStatus === 'review_pending';
    const canUpdateStatus = !!detailTask && (canAssignTask || currentUserId === assigneeId) && taskStatus !== 'review_pending';
    const canManageTaskMembers = !!detailTask && (canAssignTask || currentUserId === creatorId || currentUserId === assigneeId);
    const collaboratorIds = (detailTask?.collaborators || []).map((item) => item.id);
    const canRecordExecution = !!detailTask && (canAssignTask || currentUserId === assigneeId || collaboratorIds.includes(currentUserId || ''));
    const canCreateRelatedVulnerability = canRecordExecution;
    const sortedProgressComments = [...taskComments].sort((a, b) => {
      const aTime = dayjs(a.createdAt || a.created_at || 0).valueOf();
      const bTime = dayjs(b.createdAt || b.created_at || 0).valueOf();
      return bTime - aTime;
    });
    const latestProgressStageKeyFromComment = (sortedProgressComments[0]?.workType || sortedProgressComments[0]?.work_type || '') as string;
    const latestProgressStageKey = auditProgressStageOrder.includes(latestProgressStageKeyFromComment)
      ? latestProgressStageKeyFromComment
      : (manualProgressStage || auditProgressStageOrder[0]);
    const progressCurrentStageIndex = auditProgressStageOrder.indexOf(latestProgressStageKey);
    const progressCurrentStageLabel = executionTypeLabels[latestProgressStageKey] || latestProgressStageKey || '未开始';
    const progressPercent = executionStageProgress[latestProgressStageKey] ?? 0;
    const normalizedViewStageKey = auditProgressStageOrder.includes(viewStageKey) ? viewStageKey : latestProgressStageKey;
    const viewStageIndex = auditProgressStageOrder.indexOf(normalizedViewStageKey);
    const canMoveToNextStage = canRecordExecution && progressCurrentStageIndex >= 0 && progressCurrentStageIndex < auditProgressStageOrder.length - 1;
    const mergedEvidence = [
      ...relatedDocuments.map((item) => ({
        rowId: `document-${item.id}`,
        documentId: item.id,
        sourceType: '文档证据',
        name: item.title || '-',
        reference: item.documentNo || '-',
        category: item.category || '-',
        status: item.status || '-',
        createdAt: item.createdAt || item.created_at,
        navigateKeyword: item.title || item.documentNo || '',
      })),
      ...taskAttachments.map((item) => ({
        rowId: `attachment-${item.id}`,
        sourceType: '任务附件',
        name: item.fileName || '-',
        reference: item.filePath || '-',
        category: '任务附件',
        status: '已上传',
        createdAt: item.createdAt || (item as any).created_at,
        navigateKeyword: '',
      })),
    ];
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
              <Descriptions.Item label="负责人">{detailTask.assignee?.username || '-'}</Descriptions.Item>
              <Descriptions.Item label="协作者">{(detailTask.collaborators || []).map((item) => item.username).join('、') || '-'}</Descriptions.Item>
              {taskInfoExpanded && (
                <>
                  <Descriptions.Item label="创建者">{detailTask.creator?.username || '-'}</Descriptions.Item>
                  <Descriptions.Item label="预估工时">{getEstimatedHours(detailTask) || '-'}</Descriptions.Item>
                  <Descriptions.Item label="实际工时">{getActualHours(detailTask)}</Descriptions.Item>
                  <Descriptions.Item label="燃尽比例">{getBurnRate(detailTask)}</Descriptions.Item>
                  <Descriptions.Item label="资产名称">{assetDetail?.name || '-'}</Descriptions.Item>
                  <Descriptions.Item label="资产版本">{versionLabelById((detailTask as any).asset_version_id || (detailTask as any).assetVersionId)}</Descriptions.Item>
                  <Descriptions.Item label="创建时间">{((detailTask as any).created_at || (detailTask as any).createdAt) ? dayjs((detailTask as any).created_at || (detailTask as any).createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
                  <Descriptions.Item label="更新时间">{((detailTask as any).updated_at || (detailTask as any).updatedAt) ? dayjs((detailTask as any).updated_at || (detailTask as any).updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
                  <Descriptions.Item label="任务描述" span={3}>{(detailTask as any).description || '-'}</Descriptions.Item>
                </>
              )}
            </Descriptions>
            <Space>
              {canUpdateStatus && <Button onClick={() => handleStatusUpdate(detailTask)}>更新状态</Button>}
              <Button onClick={() => handleAssign(detailTask)} disabled={!canManageTaskMembers}>分配任务</Button>
              <Button onClick={handleCollaboratorManage} disabled={!canManageTaskMembers}>协作者管理</Button>
              <Button type="link" onClick={() => setTaskInfoExpanded((prev) => !prev)}>
                {taskInfoExpanded ? '收起详情' : '查看更多'}
              </Button>
            </Space>
            <Tabs
              style={{ marginTop: 20 }}
              items={[
                {
                  key: 'asset_profile',
                  label: '资产信息',
                  children: (
                    <>
                      {assetDetail ? (
                        <Tabs
                          type="card"
                          items={[
                            {
                              key: 'asset_basic',
                              label: '资产基本信息',
                              children: (
                                <Card bordered={false} className="overview-step-card">
                                  <Card bordered={false} className="overview-step-card">
                                    <Form form={assetInfoForm} layout="vertical">
                                      <Row gutter={24}>
                                        <Col span={6}>
                                          <Form.Item name="name" label="资产名称" rules={[{ required: true, message: '请输入资产名称' }]}>
                                            <Input placeholder="请输入资产名称" disabled={!canRecordExecution} />
                                          </Form.Item>
                                        </Col>
                                        <Col span={6}>
                                          <Form.Item name="code" label="资产编码">
                                            <Input disabled />
                                          </Form.Item>
                                        </Col>
                                        <Col span={6}>
                                          <Form.Item name="target_type" label="目标类型" rules={[{ required: true, message: '请输入目标类型' }]}>
                                            <Input placeholder="如：应用系统" disabled={!canRecordExecution} />
                                          </Form.Item>
                                        </Col>
                                        <Col span={6}>
                                          <Form.Item name="target_source" label="资产来源" rules={[{ required: true, message: '请输入资产来源' }]}>
                                            <Input placeholder="如：自研" disabled={!canRecordExecution} />
                                          </Form.Item>
                                        </Col>
                                        <Col span={12}>
                                          <Form.Item name="description" label="描述">
                                            <Input.TextArea rows={3} placeholder="请输入资产描述" disabled={!canRecordExecution} />
                                          </Form.Item>
                                        </Col>
                                        <Col span={6}>
                                          <Form.Item name="priority" label="优先级">
                                            <Select disabled={!canRecordExecution}>
                                              <Option value="P0">P0</Option>
                                              <Option value="P1">P1</Option>
                                              <Option value="P2">P2</Option>
                                              <Option value="P3">P3</Option>
                                            </Select>
                                          </Form.Item>
                                        </Col>
                                        <Col span={6}>
                                          <Form.Item name="status" label="状态">
                                            <Select disabled={!canRecordExecution}>
                                              <Option value="active">active</Option>
                                              <Option value="under_test">under_test</Option>
                                              <Option value="suspended">suspended</Option>
                                              <Option value="archived">archived</Option>
                                            </Select>
                                          </Form.Item>
                                        </Col>
                                      </Row>
                                      <Button type="primary" loading={assetInfoSubmitting} onClick={handleAssetInfoSubmit} disabled={!canRecordExecution}>
                                        保存资产信息
                                      </Button>
                                    </Form>
                                  </Card>
                                </Card>
                              ),
                            },
                            {
                              key: 'asset_overview',
                              label: '产品概览',
                              children: (
                                <Card bordered={false} className="overview-step-card">
                                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                                    <h3 style={{ margin: 0 }}>产品概览</h3>
                                    <Select
                                      style={{ width: 260 }}
                                      value={assetMetadataVersionId}
                                      placeholder="选择概览卡片版本"
                                      onChange={(value) => handleAssetVersionChange(value)}
                                      options={assetVersions.map((item) => ({
                                        label: `${item.name || `v${item.versionNo}`}${item.isDefault ? ' - 默认' : ''}${item.isLatest ? ' - 最新' : ''}`,
                                        value: item.id,
                                      }))}
                                    />
                                  </div>
                                  <Spin spinning={overviewLoading}>
                                    {overviewStep === 0 && (
                                      <div style={{ marginBottom: 16 }}>
                                        <Tag color={overviewExists ? 'blue' : 'default'}>
                                          {overviewExists ? '当前版本已存在概览信息' : '当前版本暂无概览信息，保存后创建'}
                                        </Tag>
                                        {baseOverviewInheritedFromVersionId && (
                                          <Tag color="purple">
                                            继承自版本：{versionLabelById(baseOverviewInheritedFromVersionId)}
                                          </Tag>
                                        )}
                                      </div>
                                    )}
                                    <Steps
                                      current={overviewStep}
                                      onChange={setOverviewStep}
                                      style={{ marginBottom: 28 }}
                                      items={[
                                        { title: '基础与存储' },
                                        { title: '网络与接口' },
                                        { title: '运行配置' },
                                        { title: '认证与权限' },
                                        { title: '附加信息' },
                                      ]}
                                    />
                                    <Form form={overviewForm} layout="vertical">
                                      <Card bordered={false} className="overview-step-card">
                                        {overviewStep === 0 && (
                                          <Row gutter={24}>
                                            <Col span={8}><Form.Item name="runtimePlatform" label="运行平台 (操作系统)"><Input placeholder="如: Windows Server / Linux / Unix" disabled={!canRecordExecution} /></Form.Item></Col>
                                            <Col span={8}><Form.Item name="deploymentMethod" label="部署方式"><Input placeholder="如: Docker / 物理机 / K8s" disabled={!canRecordExecution} /></Form.Item></Col>
                                            <Col span={8}><Form.Item name="systemArchitecture" label="系统架构"><Input placeholder="x86 / ARM" disabled={!canRecordExecution} /></Form.Item></Col>
                                            <Col span={24}>
                                              <Form.Item label="编程语言与框架">
                                                <div style={{ border: '1px solid #e8e8e8', borderRadius: 8, overflow: 'hidden' }}>
                                                  <div style={{ background: '#fafafa', padding: '12px 16px', borderBottom: '1px solid #e8e8e8', display: 'flex', fontWeight: 500, color: '#595959' }}>
                                                    <div style={{ flex: '0 0 20%', paddingRight: 8 }}>编程语言</div>
                                                    <div style={{ flex: '0 0 20%', paddingRight: 8 }}>版本</div>
                                                    <div style={{ flex: '0 0 20%', paddingRight: 8 }}>框架</div>
                                                    <div style={{ flex: '0 0 30%', paddingRight: 8 }}>说明（使用该语言的服务）</div>
                                                    <div style={{ flex: '0 0 10%', textAlign: 'center' }}>操作</div>
                                                  </div>
                                                  <div style={{ padding: '16px 16px 0' }}>
                                                    <Form.List name="programmingLanguagesList">
                                                      {(fields, { add, remove }) => (
                                                        <>
                                                          {fields.map(({ key, name, ...restField }, index) => (
                                                            <div key={key} style={{ display: 'flex', marginBottom: 16, alignItems: 'flex-start' }}>
                                                              <div style={{ flex: '0 0 20%', paddingRight: 8 }}>
                                                                <Form.Item {...restField} name={[name, 'language']} style={{ marginBottom: 0 }}>
                                                                  <Input placeholder="如：Java / Go" disabled={!canRecordExecution} />
                                                                </Form.Item>
                                                              </div>
                                                              <div style={{ flex: '0 0 20%', paddingRight: 8 }}>
                                                                <Form.Item {...restField} name={[name, 'version']} style={{ marginBottom: 0 }}>
                                                                  <Input placeholder="如：jdk1.8 / 1.21" disabled={!canRecordExecution} />
                                                                </Form.Item>
                                                              </div>
                                                              <div style={{ flex: '0 0 20%', paddingRight: 8 }}>
                                                                <Form.Item {...restField} name={[name, 'framework']} style={{ marginBottom: 0 }}>
                                                                  <Input placeholder="如：Spring Boot" disabled={!canRecordExecution} />
                                                                </Form.Item>
                                                              </div>
                                                              <div style={{ flex: '0 0 30%', paddingRight: 8 }}>
                                                                <Form.Item {...restField} name={[name, 'note']} style={{ marginBottom: 0 }}>
                                                                  <Input placeholder="如：核心后端服务" disabled={!canRecordExecution} />
                                                                </Form.Item>
                                                              </div>
                                                              <div style={{ flex: '0 0 10%', display: 'flex', justifyContent: 'center', paddingTop: 4 }}>
                                                                <Space size={8}>
                                                                  {canRecordExecution && fields.length > 1 && (
                                                                    <Tooltip title="移除">
                                                                      <MinusCircleOutlined
                                                                        style={{ color: '#ff4d4f', fontSize: 16, cursor: 'pointer' }}
                                                                        onClick={() => remove(name)}
                                                                      />
                                                                    </Tooltip>
                                                                  )}
                                                                  {canRecordExecution && index === fields.length - 1 && (
                                                                    <Tooltip title="添加">
                                                                      <PlusCircleOutlined
                                                                        style={{ color: '#1890ff', fontSize: 16, cursor: 'pointer' }}
                                                                        onClick={() => add({ language: '', version: '', framework: '', note: '' })}
                                                                      />
                                                                    </Tooltip>
                                                                  )}
                                                                </Space>
                                                              </div>
                                                            </div>
                                                          ))}
                                                          {fields.length === 0 && canRecordExecution && (
                                                            <div style={{ marginBottom: 16 }}>
                                                              <Button type="dashed" onClick={() => add({ language: '', version: '', framework: '', note: '' })} block icon={<PlusOutlined />}>
                                                                添加编程语言
                                                              </Button>
                                                            </div>
                                                          )}
                                                        </>
                                                      )}
                                                    </Form.List>
                                                  </div>
                                                </div>
                                              </Form.Item>
                                            </Col>
                                          </Row>
                                        )}
                                        {overviewStep === 1 && (
                                          <Row gutter={24}>
                                            <Col span={24}>{renderListFormItem('servicePortsList', '服务端口', '如：8080', <NodeIndexOutlined style={{ color: '#bfbfbf' }} />, {
                                              name: 'accessType',
                                              label: '访问范围',
                                              options: [
                                                { label: '全部可达', value: 'all' },
                                                { label: '仅内网', value: 'intranet_only' },
                                                { label: '仅白名单', value: 'whitelist_only' },
                                              ],
                                            }, false, !canRecordExecution)}</Col>
                                            <Col span={24}>{renderListFormItem('apiEndpointsList', 'API端点', '如：/api/v1/users', <LinkOutlined style={{ color: '#bfbfbf' }} />, {
                                              name: 'authType',
                                              label: '鉴权',
                                              options: [
                                                { label: '需鉴权', value: 'auth_required' },
                                                { label: '免鉴权', value: 'no_auth' },
                                                { label: '条件免鉴权', value: 'conditional' },
                                              ],
                                              dependentField: {
                                                name: 'conditionNote',
                                                label: '条件说明',
                                                placeholder: '如：仅内网来源IP免鉴权',
                                                showWhenValue: 'conditional',
                                              },
                                            }, false, !canRecordExecution)}</Col>
                                          </Row>
                                        )}
                                        {overviewStep === 2 && (
                                          <Row gutter={24}>
                                            <Col span={12}><Form.Item name="installPath" label="应用安装路径"><Input placeholder="如：/opt/app" disabled={!canRecordExecution} /></Form.Item></Col>
                                            <Col span={12}><Form.Item name="startupCommand" label="启动命令"><Input placeholder="如：java -jar app.jar" disabled={!canRecordExecution} /></Form.Item></Col>
                                            <Col span={12}><Form.Item name="databaseType" label="数据库类型"><Input placeholder="如：MySQL / PostgreSQL" disabled={!canRecordExecution} /></Form.Item></Col>
                                            <Col span={12}><Form.Item name="databaseConnection" label="数据库连接信息"><Input placeholder="如：jdbc:mysql://db:3306/app" disabled={!canRecordExecution} /></Form.Item></Col>
                                            <Col span={24}>{renderListFormItem('startupScriptPathList', '启动脚本路径', '如：/opt/app/start.sh', <CodeOutlined style={{ color: '#bfbfbf' }} />, undefined, false, !canRecordExecution)}</Col>
                                            <Col span={24}>{renderListFormItem('configFilePathList', '配置文件路径', '如：/etc/app/config.yaml', <SettingOutlined style={{ color: '#bfbfbf' }} />, undefined, false, !canRecordExecution)}</Col>
                                          </Row>
                                        )}
                                        {overviewStep === 3 && (
                                          <Row gutter={24}>
                                            <Col span={24}>
                                              <Form.Item label="账号凭据">
                                                <div style={{ border: '1px solid #e8e8e8', borderRadius: 8, overflow: 'hidden' }}>
                                                  <div style={{ background: '#fafafa', padding: '12px 16px', borderBottom: '1px solid #e8e8e8', display: 'flex', fontWeight: 500, color: '#595959' }}>
                                                    <div style={{ flex: '0 0 28%', paddingRight: 8 }}>用途备注</div>
                                                    <div style={{ flex: '0 0 28%', paddingRight: 8 }}>账号</div>
                                                    <div style={{ flex: '0 0 28%', paddingRight: 8 }}>密码</div>
                                                    <div style={{ flex: '0 0 16%', textAlign: 'center' }}>操作</div>
                                                  </div>
                                                  <div style={{ padding: '16px 16px 0' }}>
                                                    <Form.List name="accountCredentials">
                                                      {(fields, { add, remove }) => (
                                                        <>
                                                          {fields.map(({ key, name, ...restField }, index) => (
                                                            <div key={key} style={{ display: 'flex', marginBottom: 16, alignItems: 'flex-start' }}>
                                                              <div style={{ flex: '0 0 28%', paddingRight: 8 }}>
                                                                <Form.Item {...restField} name={[name, 'usageNote']} style={{ marginBottom: 0 }}>
                                                                  <Input placeholder="如：管理员后台登录" prefix={<TagOutlined style={{ color: '#bfbfbf' }} />} disabled={!canRecordExecution} />
                                                                </Form.Item>
                                                              </div>
                                                              <div style={{ flex: '0 0 28%', paddingRight: 8 }}>
                                                                <Form.Item {...restField} name={[name, 'username']} style={{ marginBottom: 0 }}>
                                                                  <Input placeholder="账号" prefix={<UserOutlined style={{ color: '#bfbfbf' }} />} disabled={!canRecordExecution} />
                                                                </Form.Item>
                                                              </div>
                                                              <div style={{ flex: '0 0 28%', paddingRight: 8 }}>
                                                                <Form.Item {...restField} name={[name, 'password']} style={{ marginBottom: 0 }}>
                                                                  <Input.Password placeholder="密码（可选）" prefix={<LockOutlined style={{ color: '#bfbfbf' }} />} disabled={!canRecordExecution} />
                                                                </Form.Item>
                                                              </div>
                                                              <div style={{ flex: '0 0 16%', display: 'flex', justifyContent: 'center', paddingTop: 4 }}>
                                                                <Space size={8}>
                                                                  {canRecordExecution && fields.length > 1 && (
                                                                    <Tooltip title="移除">
                                                                      <MinusCircleOutlined style={{ color: '#ff4d4f', fontSize: 16, cursor: 'pointer' }} onClick={() => remove(name)} />
                                                                    </Tooltip>
                                                                  )}
                                                                  {canRecordExecution && index === fields.length - 1 && (
                                                                    <Tooltip title="添加">
                                                                      <PlusCircleOutlined style={{ color: '#1890ff', fontSize: 16, cursor: 'pointer' }} onClick={() => add({ usageNote: '', username: '', password: '' })} />
                                                                    </Tooltip>
                                                                  )}
                                                                </Space>
                                                              </div>
                                                            </div>
                                                          ))}
                                                          {fields.length === 0 && canRecordExecution && (
                                                            <div style={{ marginBottom: 16 }}>
                                                              <Button type="dashed" onClick={() => add({ usageNote: '', username: '', password: '' })} block icon={<PlusOutlined />}>
                                                                添加账号信息
                                                              </Button>
                                                            </div>
                                                          )}
                                                        </>
                                                      )}
                                                    </Form.List>
                                                  </div>
                                                </div>
                                              </Form.Item>
                                            </Col>
                                          </Row>
                                        )}
                                        {overviewStep === 4 && (
                                          <Row gutter={24}>
                                            <Col span={24}><Form.Item name="specialNotes" label="特殊说明"><Input.TextArea rows={6} placeholder="记录补充信息，如依赖外部服务、审计边界、临时说明等" disabled={!canRecordExecution} /></Form.Item></Col>
                                          </Row>
                                        )}
                                      </Card>
                                      <Button type="primary" loading={overviewSubmitting} onClick={handleOverviewSubmit} disabled={!canRecordExecution}>
                                        保存当前分栏
                                      </Button>
                                    </Form>
                                  </Spin>
                                </Card>
                              ),
                            },
                            {
                              key: 'asset_deliverable',
                              label: '交付物',
                              children: (
                                <Card bordered={false} className="overview-step-card">
                                  <div style={{ marginBottom: 16 }}>
                                    <h3 style={{ margin: 0 }}>交付物</h3>
                                    <p style={{ marginTop: 8, marginBottom: 0, color: '#8c8c8c' }}>在任务中直接新增并查看资产版本下的交付物。</p>
                                  </div>
                                  <Form form={deliverableForm} layout="vertical" style={{ marginBottom: 12 }}>
                                    <Space style={{ width: '100%' }} align="start">
                                      <Form.Item
                                        name="deliverable_type"
                                        label="交付物类型"
                                        rules={[{ required: true, message: '请选择交付物类型' }]}
                                        style={{ width: 220 }}
                                      >
                                        <Select placeholder="请选择类型" disabled={!canRecordExecution}>
                                          <Option value="漏洞POC">漏洞POC</Option>
                                          <Option value="漏洞EXP">漏洞EXP</Option>
                                          <Option value="修复补丁">修复补丁</Option>
                                          <Option value="说明文档">说明文档</Option>
                                          <Option value="其他交付物">其他交付物</Option>
                                        </Select>
                                      </Form.Item>
                                      <Form.Item name="description" label="说明" style={{ flex: 1 }}>
                                        <Input placeholder="填写交付物用途与说明" disabled={!canRecordExecution} />
                                      </Form.Item>
                                      <Form.Item label="上传文件">
                                        <Upload
                                          beforeUpload={(file) => {
                                            setDeliverableUploadList([file]);
                                            return false;
                                          }}
                                          onRemove={() => setDeliverableUploadList([])}
                                          fileList={deliverableUploadList}
                                          maxCount={1}
                                        >
                                          <Button icon={<UploadOutlined />} disabled={!canRecordExecution}>选择文件</Button>
                                        </Upload>
                                      </Form.Item>
                                      <Form.Item label=" ">
                                        <Button type="primary" loading={deliverableSubmitting} onClick={handleDeliverableSubmit} disabled={!canRecordExecution}>
                                          增加交付物
                                        </Button>
                                      </Form.Item>
                                    </Space>
                                  </Form>
                                  <Table
                                    rowKey="id"
                                    size="small"
                                    pagination={false}
                                    dataSource={assetDeliverables}
                                    columns={[
                                      { title: '名称', dataIndex: 'name', key: 'name' },
                                      { title: '类型', dataIndex: 'deliverableType', key: 'deliverableType' },
                                      { title: '说明', dataIndex: 'description', key: 'description', render: (value: string) => value || '-' },
                                      { title: '文件大小', dataIndex: 'fileSize', key: 'fileSize', render: (value: number) => value ? `${Math.ceil(value / 1024)} KB` : '-' },
                                      { title: '时间', dataIndex: 'createdAt', key: 'createdAt', render: (value: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-' },
                                    ]}
                                  />
                                </Card>
                              ),
                            },
                          ]}
                        />
                      ) : (
                        <Card bordered={false} className="overview-step-card">
                          <div style={{ color: '#8c8c8c' }}>当前任务未关联资产，无法维护资产信息</div>
                        </Card>
                      )}
                    </>
                  ),
                },
                {
                  key: 'execution',
                  label: `审计进度 (${taskComments.length})`,
                  children: (
                    <>
                      <Card bordered={false} className="overview-step-card" style={{ marginBottom: 16 }}>
                        <Steps
                          current={viewStageIndex >= 0 ? viewStageIndex : 0}
                          onChange={(nextStepIndex) => {
                            if (nextStepIndex <= progressCurrentStageIndex) {
                              setViewStageKey(auditProgressStageOrder[nextStepIndex]);
                            }
                          }}
                          items={auditProgressStageOrder.map((stageKey, index) => ({
                            title: executionTypeLabels[stageKey],
                            disabled: index > progressCurrentStageIndex,
                          }))}
                        />
                      </Card>
                      <Card bordered={false} className="overview-step-card">
                        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <div style={{ fontWeight: 500 }}>当前阶段：{progressCurrentStageLabel}</div>
                          <Space>
                            <Button type="primary" onClick={handleManualProgressSwitch} loading={manualStageSubmitting} disabled={!canMoveToNextStage}>
                              进入下一阶段
                            </Button>
                          </Space>
                        </div>
                        <Progress percent={progressPercent} />
                        <div style={{ marginTop: 8, color: '#8c8c8c' }}>
                          通过“进入下一阶段”推进流程，已完成阶段可点击名称查看该阶段文档
                        </div>
                        <div style={{ marginTop: 16 }}>
                          <Card
                            size="small"
                            title={`${executionTypeLabels[normalizedViewStageKey]}阶段记录 (Markdown)`}
                            extra={
                              <Space>
                                <Button
                                  size="small"
                                  onClick={() => setStageDocEditingMap((prev) => ({ ...prev, [normalizedViewStageKey]: !prev[normalizedViewStageKey] }))}
                                  disabled={!canRecordExecution}
                                >
                                  {stageDocEditingMap[normalizedViewStageKey] ? '取消编辑' : '编辑'}
                                </Button>
                                {stageDocEditingMap[normalizedViewStageKey] && (
                                  <Button
                                    type="primary"
                                    size="small"
                                    onClick={() => handleSaveStageNote(normalizedViewStageKey)}
                                    loading={stageDocSavingMap[normalizedViewStageKey]}
                                    disabled={!canRecordExecution}
                                  >
                                    保存到文档与附件
                                  </Button>
                                )}
                              </Space>
                            }
                          >
                            {stageDocEditingMap[normalizedViewStageKey] ? (
                              <Input.TextArea
                                rows={12}
                                value={stageNoteMap[normalizedViewStageKey] || ''}
                                onChange={(e) => setStageNoteMap((prev) => ({ ...prev, [normalizedViewStageKey]: e.target.value }))}
                                placeholder={`记录${executionTypeLabels[normalizedViewStageKey]}阶段过程（Markdown）`}
                                disabled={!canRecordExecution}
                              />
                            ) : (
                              <div
                                style={{ minHeight: 220, border: '1px solid #f0f0f0', borderRadius: 6, padding: 12, background: '#fafafa' }}
                                dangerouslySetInnerHTML={{ __html: renderRichContent(stageNoteMap[normalizedViewStageKey] || '', '暂无内容，点击“编辑”开始编写。') }}
                              />
                            )}
                          </Card>
                          <div style={{ marginTop: 8, color: '#8c8c8c' }}>
                            当前查看：{executionTypeLabels[normalizedViewStageKey]}（阶段 {viewStageIndex + 1}/{auditProgressStageOrder.length}）
                          </div>
                        </div>
                      </Card>
                    </>
                  ),
                },
                {
                  key: 'vulnerabilities',
                  label: `关联漏洞 (${relatedVulnerabilities.length})`,
                  children: (
                    <>
                      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
                        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateRelatedVulnerability} disabled={!canCreateRelatedVulnerability}>
                          新建漏洞
                        </Button>
                      </div>
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
                              const vulnerabilityId = record?.id || record?.vulnerabilityId || '';
                              return vulnerabilityId ? (
                                <a onClick={() => navigate(`/vulnerabilities/${vulnerabilityId}`)}>{value || vulnerabilityId}</a>
                              ) : '-';
                            },
                          },
                          {
                            title: '漏洞标题',
                            dataIndex: 'title',
                            key: 'title',
                            render: (value: string, record: any) => {
                              const vulnerabilityId = record?.id || record?.vulnerabilityId || '';
                              return vulnerabilityId ? (
                                <a onClick={() => navigate(`/vulnerabilities/${vulnerabilityId}`)}>{value || '-'}</a>
                              ) : '-';
                            },
                          },
                          { title: '类型', dataIndex: 'type', key: 'type' },
                          { title: '严重级别', dataIndex: 'severity', key: 'severity' },
                          { title: '状态', dataIndex: 'status', key: 'status' },
                        ]}
                      />
                    </>
                  ),
                },
                {
                  key: 'evidence',
                  label: `文档与附件 (${mergedEvidence.length})`,
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
                        rowKey="rowId"
                        size="small"
                        pagination={false}
                        dataSource={mergedEvidence}
                        columns={[
                          { title: '来源', dataIndex: 'sourceType', key: 'sourceType' },
                          {
                            title: '名称',
                            dataIndex: 'name',
                            key: 'name',
                            render: (value: string, record: any) => {
                              if (record.sourceType !== '文档证据' || !record.navigateKeyword) {
                                return value || '-';
                              }
                              return (
                                <a onClick={() => navigate(`/documents?search=${encodeURIComponent(record.navigateKeyword)}`)}>
                                  {value || '-'}
                                </a>
                              );
                            },
                          },
                          { title: '编号/路径', dataIndex: 'reference', key: 'reference' },
                          { title: '分类', dataIndex: 'category', key: 'category' },
                          { title: '状态', dataIndex: 'status', key: 'status' },
                          {
                            title: '时间',
                            dataIndex: 'createdAt',
                            key: 'createdAt',
                            render: (value: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
                          },
                          {
                            title: '操作',
                            key: 'action',
                            render: (_: any, record: any) => {
                              if (record.sourceType === '文档证据' && record.documentId) {
                                return (
                                  <Space>
                                    <a onClick={() => handleOpenDocumentPreview(record.documentId)}>查看</a>
                                    <a onClick={() => navigate(`/documents?search=${encodeURIComponent(record.navigateKeyword || record.name || '')}`)}>定位到规范与文档</a>
                                  </Space>
                                );
                              }
                              return '-';
                            },
                          },
                        ]}
                      />
                    </>
                  ),
                },
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
              ]}
            />
            <Modal
              title="新建漏洞"
              open={relatedVulnModalVisible}
              onCancel={() => setRelatedVulnModalVisible(false)}
              onOk={handleSubmitRelatedVulnerability}
              confirmLoading={relatedVulnSubmitting}
            >
              <Form form={relatedVulnForm} layout="vertical">
                <Form.Item name="title" label="漏洞名称" rules={[{ required: true, message: '请输入漏洞名称' }]}>
                  <Input placeholder="请输入漏洞名称" />
                </Form.Item>
                <Form.Item name="type" label="漏洞类型" rules={[{ required: true, message: '请选择漏洞类型' }]}>
                  <Select placeholder="请选择漏洞类型" options={vulnTypeOptions} />
                </Form.Item>
                <Form.Item name="discovered_version_id" label="发现版本" rules={[{ required: true, message: '请选择发现版本' }]}>
                  <Select placeholder="请选择发现时所属资产版本">
                    {assetVersions.map((item) => (
                      <Option key={item.id} value={item.id}>
                        {item.name || `v${item.versionNo}`}{item.isLatest ? ' - 最新' : ''}{item.isDefault ? ' - 默认' : ''}
                      </Option>
                    ))}
                  </Select>
                </Form.Item>
                <Form.Item name="affected_versions" label="受影响版本">
                  <Input.TextArea rows={2} placeholder="可自由填写，例如：v1.0, v1.1, v2.x" />
                </Form.Item>
                <Form.Item name="severity" label="严重程度" rules={[{ required: true, message: '请选择严重程度' }]}>
                  <Select placeholder="请选择严重程度">
                    <Option value="CRITICAL">严重</Option>
                    <Option value="HIGH">高危</Option>
                    <Option value="MEDIUM">中危</Option>
                    <Option value="LOW">低危</Option>
                    <Option value="INFO">信息</Option>
                  </Select>
                </Form.Item>
                <Form.Item name="description" label="漏洞描述" rules={[{ required: true, message: '请输入漏洞描述' }]}>
                  <Input.TextArea rows={4} placeholder="请输入漏洞描述" />
                </Form.Item>
              </Form>
            </Modal>
            <Modal
              title={documentPreviewData?.title || '文档预览'}
              open={documentPreviewVisible}
              onCancel={() => {
                setDocumentPreviewVisible(false);
                setDocumentPreviewData(null);
              }}
              footer={null}
              width={980}
            >
              <Spin spinning={documentPreviewLoading}>
                <Descriptions column={2} size="small" bordered style={{ marginBottom: 12 }}>
                  <Descriptions.Item label="分类">{documentPreviewData?.category || '-'}</Descriptions.Item>
                  <Descriptions.Item label="状态">{documentPreviewData?.status || '-'}</Descriptions.Item>
                  <Descriptions.Item label="更新时间" span={2}>
                    {documentPreviewData?.updated_at ? dayjs(documentPreviewData.updated_at).format('YYYY-MM-DD HH:mm:ss') : '-'}
                  </Descriptions.Item>
                </Descriptions>
                <div
                  style={{ minHeight: 320, border: '1px solid #f0f0f0', borderRadius: 6, padding: 12, background: '#fafafa' }}
                  dangerouslySetInnerHTML={{ __html: renderRichContent(documentPreviewData?.content || '', '文档暂无内容') }}
                />
              </Spin>
            </Modal>
            {taskActionModals}
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
            <Select mode="multiple" allowClear showSearch optionFilterProp="children" placeholder="可选：协作者可记录审计进度">
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
      {taskActionModals}
    </Card>
  );
};

export default Tasks;
