import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Tabs, Card, Button, Descriptions, Tag, Spin, Result, Form, Input, Select, Row, Col, Space, Upload, Table, message, Modal, Tooltip, Steps, Alert, Typography } from 'antd';
import { AppstoreOutlined, BuildOutlined, FileZipOutlined, BugOutlined, SafetyCertificateOutlined, UploadOutlined, PlusOutlined, MinusCircleOutlined, UserOutlined, LockOutlined, TagOutlined, PlusCircleOutlined, CodeOutlined, SettingOutlined, NodeIndexOutlined, LinkOutlined, InfoCircleOutlined, PlayCircleOutlined, StopOutlined, CloudUploadOutlined, ReloadOutlined } from '@ant-design/icons';
import { 
  getAssetDetail, 
  getAssetVersions,
  createAssetVersion,
  setDefaultAssetVersion,
  setLatestAssetVersion,
  getAssetMetadata, 
  updateAssetMetadata, 
  getAssetFiles, 
  uploadAssetFile,
  deleteAssetFile,
  downloadAssetFile,
  getAssetDeliverables,
  uploadAssetDeliverable,
  deleteAssetDeliverable,
  downloadAssetDeliverable,
  getEnvironmentList,
  getEnvironmentDetail,
  createEnvironment,
  deleteEnvironment,
  createEnvironmentSnapshot,
  getEnvironmentSnapshots,
  rollbackEnvironmentSnapshot,
  deployEnvironment,
  startEnvironment,
  stopEnvironment,
  createAssetAccessRequest,
  getMyAccessRequests,
  createAssetClaim,
  getMyClaims,
} from '@/api/asset';
import { getVulnerabilityList, createVulnerability, updateVulnerability, deleteVulnerability, getVulnerabilityOptions } from '@/api/vulnerability';
import { isAssetSubscribed, subscribeAsset, unsubscribeAsset } from '@/api/notification';
import { describeVulnCreateFailure, describeVulnUpdateFailure, isFormValidationError } from '@/pages/vulnerabilities/createError';
import { toVulnerabilityCreatePayload, toVulnerabilityUpdatePayload } from '@/pages/vulnerabilities/createPayload';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import {
  canWriteEnvironment,
  getMyAccessProfile,
  ENVIRONMENT_FORBIDDEN_MESSAGE,
  ENVIRONMENT_READONLY_HINT,
  type MyAccessProfile,
} from '@/api/permission';
import { getTaskList, createTask, updateTask, updateTaskStatus, deleteTask } from '@/api/task';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT, USER_DIRECTORY_LIMIT_HINT, mergeUserDirectory } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import type { AssetVO, AssetFileVO, AssetDeliverableVO, EnvironmentVO, AssetVersionVO, AssetAccessVO, AssetClaimVO } from '@/types/asset';

import AssetTagsPanel from './AssetTagsPanel';
import { useUserStore } from '@/store/userStore';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { canWriteAsset, resolveCurrentUserId, ASSET_WRITE_FORBIDDEN_TIP } from '@/utils/accessRoles';
import { useT } from '@/i18n';
import type { MessageKey } from '@/i18n/messages/zh-CN';

const { TabPane } = Tabs;
const { Option } = Select;
const { TextArea } = Input;

const renderListFormItem = (
  t: (key: MessageKey, vars?: Record<string, string | number>) => string,
  name: string, 
  label: string, 
  placeholder: string, 
  icon: React.ReactNode,
  extraField?: { name: string; label: string; options: { label: string; value: string }[], dependentField?: { name: string, label: string, placeholder: string, showWhenValue: string } },
  hideLabel?: boolean
) => (
  <Form.Item label={hideLabel ? null : label}>
    <div style={{ border: '1px solid #e8e8e8', borderRadius: 8, overflow: 'hidden' }}>
      <div style={{ background: '#fafafa', padding: '12px 16px', borderBottom: '1px solid #e8e8e8', display: 'flex', fontWeight: 500, color: '#595959' }}>
        <div style={{ flex: '1', paddingRight: 8 }}>{t('asset.detail.contentSuffix', { label })}</div>
        {extraField && <div style={{ flex: '0 0 140px', paddingRight: 8 }}>{extraField.label}</div>}
        <div style={{ flex: '1', paddingRight: 8 }}>{t('asset.detail.purposeNote')}</div>
        <div style={{ flex: '0 0 50px', textAlign: 'center' }}>{t('asset.detail.colAction')}</div>
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
                        <Input placeholder={placeholder} prefix={icon} />
                      </Form.Item>
                    </div>
                    {extraField && (
                      <div style={{ flex: '0 0 140px', paddingRight: 8 }}>
                        <Form.Item {...restField} name={[fieldName, extraField.name]} style={{ marginBottom: 0 }}>
                          <Select options={extraField.options} />
                        </Form.Item>
                      </div>
                    )}
                    <div style={{ flex: '1', paddingRight: 8 }}>
                      <Form.Item {...restField} name={[fieldName, 'note']} style={{ marginBottom: 0 }}>
                        <Input placeholder={t('asset.detail.notePlaceholder')} prefix={<TagOutlined style={{ color: '#bfbfbf' }} />} />
                      </Form.Item>
                    </div>
                    <div style={{ flex: '0 0 50px', display: 'flex', justifyContent: 'center', paddingTop: 4 }}>
                      <Space size={8}>
                        {fields.length > 1 && (
                          <Tooltip title={t('asset.detail.remove')}>
                            <MinusCircleOutlined 
                              style={{ color: '#ff4d4f', fontSize: 16, cursor: 'pointer' }} 
                              onClick={() => remove(fieldName)} 
                            />
                          </Tooltip>
                        )}
                        {index === fields.length - 1 && (
                          <Tooltip title={t('asset.detail.add')}>
                            <PlusCircleOutlined 
                              style={{ color: '#1890ff', fontSize: 16, cursor: 'pointer' }} 
                              onClick={() => add({ value: '', note: '', ...(extraField ? { [extraField.name]: extraField.options[0].value } : {}) })} 
                            />
                          </Tooltip>
                        )}
                      </Space>
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
                                <Input placeholder={extraField.dependentField.placeholder} prefix={<InfoCircleOutlined style={{ color: '#bfbfbf' }}/>} />
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
              {fields.length === 0 && (
                <div style={{ marginBottom: 12 }}>
                  <Button type="dashed" onClick={() => add({ value: '', note: '', ...(extraField ? { [extraField.name]: extraField.options[0].value } : {}) })} block icon={<PlusOutlined />}>{t('asset.detail.add')}</Button>
                </div>
              )}
            </>
          )}
        </Form.List>
      </div>
    </div>
  </Form.Item>
);

const AssetDetail: React.FC = () => {
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [asset, setAsset] = useState<AssetVO | null>(null);
  // 资产写入口（新建版本/设为默认/设为最新/标签删除）：管理层或资产负责人
  const { profile: accessProfile, loaded: accessProfileLoaded } = useAccessProfile();
  const currentUser = useUserStore((state) => state.user);
  const assetOwnerId = asset?.ownerId ?? asset?.owner_id;
  const currentUserId = resolveCurrentUserId(accessProfile, currentUser?.id);
  const canWriteCurrentAsset = accessProfileLoaded && canWriteAsset(accessProfile, assetOwnerId, currentUserId);
  const [loading, setLoading] = useState(true);
  const [assetVersions, setAssetVersions] = useState<AssetVersionVO[]>([]);
  const [selectedVersionId, setSelectedVersionId] = useState<string>();
  const [overviewVersionId, setOverviewVersionId] = useState<string>();
  const [fileVersionScope, setFileVersionScope] = useState<string>();
  const [deliverableVersionScope, setDeliverableVersionScope] = useState<string>();
  const [versionLoading, setVersionLoading] = useState(false);
  const [createVersionVisible, setCreateVersionVisible] = useState(false);
  const [creatingVersion, setCreatingVersion] = useState(false);
  const [setDefaultLoading, setSetDefaultLoading] = useState(false);
  const [setLatestLoading, setSetLatestLoading] = useState(false);
  const [versionForm] = Form.useForm();

  // ===== 访问申请 / 认领 =====
  const [accessForm] = Form.useForm();
  const [claimForm] = Form.useForm();
  const [accessModalVisible, setAccessModalVisible] = useState(false);
  const [claimModalVisible, setClaimModalVisible] = useState(false);
  const [accessSubmitting, setAccessSubmitting] = useState(false);
  const [claimSubmitting, setClaimSubmitting] = useState(false);
  const [myAccess, setMyAccess] = useState<AssetAccessVO | null>(null);
  const [myClaim, setMyClaim] = useState<AssetClaimVO | null>(null);

  // ===== 资产动态订阅（/notifications/subscriptions/{assetId}） =====
  const [subscribed, setSubscribed] = useState(false);
  const [subscribeLoading, setSubscribeLoading] = useState(false);

  // Metadata states
  const [metadataForm] = Form.useForm();
  const [metadataExists, setMetadataExists] = useState(false);
  const [metadataLoading, setMetadataLoading] = useState(false);
  const [metadataSubmitting, setMetadataSubmitting] = useState(false);
  const [baseOverviewInheritedFromVersionId, setBaseOverviewInheritedFromVersionId] = useState<string>();

  // File states
  const [assetFileUploadForm] = Form.useForm();
  const [deliverableUploadForm] = Form.useForm();
  const [files, setFiles] = useState<AssetFileVO[]>([]);
  const [deliverables, setDeliverables] = useState<AssetDeliverableVO[]>([]);
  const [fileLoading, setFileLoading] = useState(false);
  const [deliverableLoading, setDeliverableLoading] = useState(false);
  const [fileUploading, setFileUploading] = useState(false);
  const [deliverableUploading, setDeliverableUploading] = useState(false);
  const [assetFileUploadList, setAssetFileUploadList] = useState<any[]>([]);
  const [deliverableUploadList, setDeliverableUploadList] = useState<any[]>([]);

  // 卡片切换状态
  const [overviewStep, setOverviewStep] = useState(0);

  // Environment states
  const [environments, setEnvironments] = useState<EnvironmentVO[]>([]);
  const [envLoading, setEnvLoading] = useState(false);
  const [envModalVisible, setEnvModalVisible] = useState(false);
  const [envForm] = Form.useForm();
  const [envSubmitting, setEnvSubmitting] = useState(false);
  /** 部署器可用性（来自 GET /environments/{id} 的 provisioner/provisionerAvailable/provisionerMessage） */
  const [envProvisioner, setEnvProvisioner] = useState<{
    type?: string;
    available?: boolean;
    message?: string;
  }>({});
  /** 正在执行部署/启动/停止的环境："<envId>:<action>" */
  const [envActionLoading, setEnvActionLoading] = useState<string | null>(null);
  /** 正在等待状态收敛的环境（provisioning → running/failed） */
  const [envConvergingId, setEnvConvergingId] = useState<string | null>(null);

  // ===== 环境快照（t40：写端点限 ADMIN/PROJECT_LEADER/TEAM_LEADER，POST 缺 name 先 400）=====
  const [profile, setProfile] = useState<MyAccessProfile | null>(null);
  const [snapshotEnv, setSnapshotEnv] = useState<EnvironmentVO | null>(null);
  const [snapshotList, setSnapshotList] = useState<any[]>([]);
  const [snapshotLoading, setSnapshotLoading] = useState(false);
  const [snapshotName, setSnapshotName] = useState('');
  const [snapshotSubmitting, setSnapshotSubmitting] = useState(false);
  const [snapshotRollingId, setSnapshotRollingId] = useState<string | null>(null);

  // Vulnerability states
  const [vulnerabilities, setVulnerabilities] = useState<any[]>([]);
  const [vulnLoading, setVulnLoading] = useState(false);
  const [vulnModalVisible, setVulnModalVisible] = useState(false);
  const [vulnSubmitting, setVulnSubmitting] = useState(false);
  const [vulnForm] = Form.useForm();
  const [vulnMode, setVulnMode] = useState<'create' | 'edit'>('create');
  const [currentVulnId, setCurrentVulnId] = useState<string>('');
  const [vulnTypeOptions, setVulnTypeOptions] = useState<Array<{ value: string; label: string }>>([]);

  // Task states
  const [tasks, setTasks] = useState<any[]>([]);
  const [taskLoading, setTaskLoading] = useState(false);
  const [taskModalVisible, setTaskModalVisible] = useState(false);
  const [taskSubmitting, setTaskSubmitting] = useState(false);
  const [taskForm] = Form.useForm();
  const [taskMode, setTaskMode] = useState<'create' | 'edit'>('create');
  const [currentTaskId, setCurrentTaskId] = useState<string>('');
  const [taskUsers, setTaskUsers] = useState<UserDirectoryEntry[]>([]);
  /** 责任人下拉改成后端搜索（keyword 走 /users/directory） */
  const [userDirectoryLimitHit, setUserDirectoryLimitHit] = useState(false);
  const userSearchTimer = useRef<number | undefined>(undefined);
  const handleUserSearch = (keyword: string) => {
    if (userSearchTimer.current) window.clearTimeout(userSearchTimer.current);
    userSearchTimer.current = window.setTimeout(async () => {
      const list = await getUserDirectory({ keyword, limit: USER_DIRECTORY_DEFAULT_LIMIT });
      setTaskUsers((prev) => mergeUserDirectory(prev, list));
      setUserDirectoryLimitHit(list.length >= USER_DIRECTORY_DEFAULT_LIMIT);
    }, 300);
  };
  const [taskStatusUpdatingId, setTaskStatusUpdatingId] = useState<string | null>(null);

  const normalizeAsset = (item: AssetVO): AssetVO => ({
    ...item,
    team_id: item.team_id ?? item.teamId,
    team_name: item.team_name ?? item.teamName,
    owner_id: item.owner_id ?? item.ownerId,
    owner_name: item.owner_name ?? item.ownerName,
    target_type: item.target_type ?? item.targetType ?? '',
    target_source: item.target_source ?? item.targetSource ?? '',
    created_at: item.created_at ?? item.createdAt ?? '',
    updated_at: item.updated_at ?? item.updatedAt,
  });

  const normalizeAssetVersion = (item: any): AssetVersionVO => ({
    id: String(item?.id ?? ''),
    assetId: item?.assetId ?? item?.asset_id ?? '',
    versionNo: Number(item?.versionNo ?? item?.version_no ?? 0),
    name: item?.name ?? '',
    description: item?.description,
    isDefault: Boolean(item?.isDefault ?? item?.is_default),
    isLatest: Boolean(item?.isLatest ?? item?.is_latest),
    status: item?.status ?? 'active',
    createdBy: item?.createdBy ?? item?.created_by,
    createdAt: item?.createdAt ?? item?.created_at,
    updatedAt: item?.updatedAt ?? item?.updated_at,
  });

  const loadVersions = async (assetId: string) => {
    try {
      setVersionLoading(true);
      const res: any = await getAssetVersions(assetId);
      const payload = res?.data?.data ?? res?.data ?? res;
      const sourceList = Array.isArray(payload) ? payload : Array.isArray(payload?.items) ? payload.items : [];
      const versionList = sourceList.map(normalizeAssetVersion).filter((item: AssetVersionVO) => !!item.id);
      setAssetVersions(versionList);
      setSelectedVersionId((prev) => {
        if (prev && versionList.some((item: AssetVersionVO) => item.id === prev)) {
          return prev;
        }
        const defaultVersion = versionList.find((item: AssetVersionVO) => item.isDefault) || versionList[0];
        return defaultVersion?.id;
      });
    } catch (error) {
      setAssetVersions([]);
      setSelectedVersionId(undefined);
    } finally {
      setVersionLoading(false);
    }
  };

  const versionLabelById = (versionId?: string) => {
    if (!versionId) return '-';
    const found = assetVersions.find((item) => item.id === versionId);
    if (!found) return versionId;
    return `${found.name || `v${found.versionNo}`}${found.isDefault ? t('asset.detail.versionDefaultSuffix') : ''}${found.isLatest ? t('asset.detail.versionLatestSuffix') : ''}`;
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

  const loadMetadata = async (assetId: string, module: MetadataModule, assetVersionId?: string) => {
    try {
      setMetadataLoading(true);
      const res: any = await getAssetMetadata(assetId, module, assetVersionId);
      const data = res?.data ? res.data : res;
      if (data && data.id) {
        let accountCredentials: Array<{ usageNote?: string; username?: string; password?: string }> = [];
        if (module === 'account') {
          try {
            const parsed = data.accountCredentialsJson ? JSON.parse(data.accountCredentialsJson) : [];
            if (Array.isArray(parsed)) {
              accountCredentials = parsed.map((item: any) => ({
                usageNote: item?.usageNote || '',
                username: item?.username || '',
                password: '',
              }));
            }
          } catch (error) {
            accountCredentials = [];
          }
          if (accountCredentials.length === 0 && data.adminUsername) {
            accountCredentials = [{
              usageNote: t('asset.detail.adminLogin'),
              username: data.adminUsername,
              password: '',
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
        }

        const parseJsonList = (str: string | undefined | null, defaultTypeField?: { key: string, val: string }) => {
          if (!str) return [{ value: '', note: '', ...(defaultTypeField ? { [defaultTypeField.key]: defaultTypeField.val } : {}) }];
          try {
            const parsed = JSON.parse(str);
            if (Array.isArray(parsed) && parsed.length > 0) {
              return parsed.map((item: any) => ({
                value: item?.value || '',
                note: item?.note || '',
                ...(defaultTypeField ? { [defaultTypeField.key]: item?.[defaultTypeField.key] || defaultTypeField.val } : {})
              }));
            }
          } catch (e) {
            const arr = str.split(/[,;\n]+/).map(s => s.trim()).filter(s => !!s);
            if (arr.length > 0) {
              return arr.map(v => ({ 
                value: v, 
                note: '', 
                ...(defaultTypeField ? { [defaultTypeField.key]: defaultTypeField.val } : {}) 
              }));
            }
          }
          return [{ value: '', note: '', ...(defaultTypeField ? { [defaultTypeField.key]: defaultTypeField.val } : {}) }];
        };

        setMetadataExists(true);
        if (module === 'base') {
          setBaseOverviewInheritedFromVersionId(
            data.copiedFromAssetVersionId ?? data.copied_from_asset_version_id ?? undefined
          );
        }
        metadataForm.setFieldsValue({
          ...data,
          ...(module === 'account' ? { accountCredentials } : {}),
          ...(module === 'base' ? { programmingLanguagesList } : {}),
          ...(module === 'env' ? {
            startupScriptPathList: parseJsonList(data.startupScriptPath),
            configFilePathList: parseJsonList(data.configFilePath),
          } : {}),
          ...(module === 'network' ? {
            servicePortsList: parseJsonList(data.servicePorts, { key: 'accessType', val: 'all' }),
            apiEndpointsList: parseJsonList(data.apiEndpoints, { key: 'authType', val: 'auth_required' }),
          } : {}),
        });
      } else {
        setMetadataExists(false);
        if (module === 'base') {
          setBaseOverviewInheritedFromVersionId(undefined);
        }
      }
    } catch (error) {
      console.error(error);
      if (module === 'base') {
        setBaseOverviewInheritedFromVersionId(undefined);
      }
    } finally {
      setMetadataLoading(false);
    }
  };

  const loadBaseOverviewInheritance = async (assetId: string, assetVersionId?: string) => {
    try {
      const res: any = await getAssetMetadata(assetId, 'base', assetVersionId);
      const data = res?.data ? res.data : res;
      setBaseOverviewInheritedFromVersionId(
        data?.copiedFromAssetVersionId ?? data?.copied_from_asset_version_id ?? undefined
      );
    } catch (error) {
      setBaseOverviewInheritedFromVersionId(undefined);
    }
  };

  const loadFiles = async (assetId: string, assetVersionId?: string, allVersions?: boolean) => {
    try {
      setFileLoading(true);
      const res: any = await getAssetFiles(assetId, assetVersionId, allVersions);
      const data = res?.data ? res.data : res;
      setFiles(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
    } finally {
      setFileLoading(false);
    }
  };

  const loadDeliverables = async (assetId: string, assetVersionId?: string, allVersions?: boolean) => {
    try {
      setDeliverableLoading(true);
      const res: any = await getAssetDeliverables(assetId, assetVersionId, allVersions);
      const data = res?.data ? res.data : res;
      setDeliverables(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
    } finally {
      setDeliverableLoading(false);
    }
  };

  /**
   * 加载环境列表并补齐详情字段（failReason、provisioner 三件套）。
   *
   * list 接口不返回 provisioner 信息，这里对每个环境补一次 GET /environments/{id}，
   * 这样页签能直接展示部署器可用性、失败原因与真实状态。
   */
  const loadEnvironments = async (assetId: string, options?: { silentLoading?: boolean }) => {
    try {
      if (!options?.silentLoading) {
        setEnvLoading(true);
      }
      const res: any = await getEnvironmentList({ page: 1, page_size: 100, assetId });
      const data = res?.data ? res.data : res;
      // 后端 PageResponse 用 content；兼容历史 items 数组形态
      const list: EnvironmentVO[] = Array.isArray(data?.content)
        ? data.content
        : Array.isArray(data?.items)
          ? data.items
          : Array.isArray(data)
            ? data
            : [];
      setEnvironments(list);

      if (list.length === 0) {
        setEnvProvisioner({});
        return;
      }

      const details = await Promise.all(
        list.map(async (item) => {
          try {
            const detailRes: any = await getEnvironmentDetail(item.id);
            return (detailRes?.data ? detailRes.data : detailRes) as EnvironmentVO;
          } catch {
            return null;
          }
        }),
      );

      const detailById = new Map<string, EnvironmentVO>();
      details.forEach((detail) => {
        if (detail?.id) {
          detailById.set(detail.id, detail);
        }
      });

      // 用详情里的最新状态/failReason 覆盖列表项，保证列表与后端一致
      setEnvironments(
        list.map((item) => {
          const detail = detailById.get(item.id);
          return detail ? { ...item, ...detail } : item;
        }),
      );

      const firstDetail = details.find((detail): detail is EnvironmentVO => Boolean(detail?.provisioner));
      if (firstDetail) {
        setEnvProvisioner({
          type: firstDetail.provisioner,
          available: firstDetail.provisionerAvailable,
          message: firstDetail.provisionerMessage,
        });
      }
    } catch (error) {
      console.error(error);
    } finally {
      setEnvLoading(false);
    }
  };

  /**
   * 轮询单个环境，直到状态从 provisioning 收敛（running / failed / stopped）。
   * 后端部署是「先置 provisioning，再真实调用编排」，前端需要刷新才能看到最终态。
   */
  const pollEnvironmentConvergence = async (assetId: string, envId: string) => {
    setEnvConvergingId(envId);
    try {
      for (let attempt = 0; attempt < 15; attempt += 1) {
        await new Promise((resolve) => setTimeout(resolve, 1500));
        const res: any = await getEnvironmentDetail(envId).catch(() => null);
        const detail = (res?.data ? res.data : res) as EnvironmentVO | null;
        if (!detail) {
          continue;
        }
        setEnvironments((prev) =>
          prev.map((item) => (item.id === envId ? { ...item, ...detail } : item)),
        );
        if (detail.provisioner) {
          setEnvProvisioner({
            type: detail.provisioner,
            available: detail.provisionerAvailable,
            message: detail.provisionerMessage,
          });
        }
        if (detail.status !== 'provisioning') {
          break;
        }
      }
    } finally {
      setEnvConvergingId(null);
      await loadEnvironments(assetId, { silentLoading: true });
    }
  };

  /** 展示环境操作失败：后端 message + failReason（长文本截断可展开），绝不静默 */
  const showEnvActionError = (
    title: string,
    backendMessage: string,
    failReason?: string | null,
    statusCode?: number,
  ) => {
    Modal.error({
      title,
      width: 620,
      content: (
        <div>
          <p style={{ marginBottom: 8 }}>
            {statusCode ? <Tag color="orange">HTTP {statusCode}</Tag> : null}
            {backendMessage || t('asset.detail.envUnknownError')}
          </p>
          {failReason ? (
            <div>
              <div style={{ color: '#8c8c8c', marginBottom: 4 }}>{t('asset.detail.envFailReasonLabel')}</div>
              <Typography.Paragraph
                style={{ marginBottom: 0, color: '#cf1322' }}
                copyable={{ text: failReason }}
                ellipsis={{ rows: 3, expandable: true, symbol: t('asset.detail.expand') }}
              >
                {failReason}
              </Typography.Paragraph>
            </div>
          ) : (
            <div style={{ color: '#8c8c8c' }}>{t('asset.detail.envNoFailReason')}</div>
          )}
        </div>
      ),
    });
  };

  /** 部署 / 启动 / 停止环境 */
  const handleEnvironmentAction = async (
    record: EnvironmentVO,
    action: 'deploy' | 'start' | 'stop',
  ) => {
    if (!asset?.id) {
      return;
    }
    if (envProvisioner.available === false) {
      message.warning(envProvisioner.message || t('asset.detail.envOrchestrationDisabled'));
      return;
    }
    const actionKey: MessageKey = action === 'deploy' ? 'asset.detail.envDeploy' : action === 'start' ? 'asset.detail.envStart' : 'asset.detail.envStop';
    setEnvActionLoading(`${record.id}:${action}`);
    try {
      if (action === 'deploy') {
        await deployEnvironment(record.id);
      } else if (action === 'start') {
        await startEnvironment(record.id);
      } else {
        await stopEnvironment(record.id);
      }
      message.success(t('asset.detail.envActionSuccess', { action: t(actionKey) }));
      await pollEnvironmentConvergence(asset.id, record.id);
    } catch (error) {
      const statusCode = getApiErrorStatus(error);
      const backendMessage = getApiErrorMessage(error, t('asset.detail.envActionFailed', { action: t(actionKey) }));
      // 失败后后端已把 status 置 failed 并写 failReason，重新拉详情用于展示
      const detailRes: any = await getEnvironmentDetail(record.id).catch(() => null);
      const detail = (detailRes?.data ? detailRes.data : detailRes) as EnvironmentVO | null;
      if (detail) {
        setEnvironments((prev) =>
          prev.map((item) => (item.id === record.id ? { ...item, ...detail } : item)),
        );
      }
      showEnvActionError(
        t('asset.detail.envActionFailed', { action: t(actionKey) }),
        backendMessage,
        detail?.failReason ?? record.failReason,
        statusCode,
      );
      await loadEnvironments(asset.id, { silentLoading: true });
    } finally {
      setEnvActionLoading(null);
    }
  };

  const loadVulnerabilities = async (assetId: string) => {
    try {
      setVulnLoading(true);
      const res: any = await getVulnerabilityList({ page: 1, page_size: 100, asset_id: assetId });
      const data = res?.data ? res.data : res;
      setVulnerabilities(Array.isArray(data?.items) ? data.items : Array.isArray(data?.content) ? data.content : Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
    } finally {
      setVulnLoading(false);
    }
  };

  const loadVulnerabilityOptions = async () => {
    try {
      const res: any = await getVulnerabilityOptions();
      const data = res?.data ? res.data : res;
      const types = Array.isArray(data?.types) ? data.types : [];
      setVulnTypeOptions(types.map((item: any) => ({
        value: String(item?.value ?? item?.label ?? ''),
        label: String(item?.label ?? item?.value ?? ''),
      })).filter((item: any) => !!item.value));
    } catch (error) {
      console.error(error);
      setVulnTypeOptions([]);
    }
  };

  const loadTasks = async (assetId: string) => {
    try {
      setTaskLoading(true);
      const res: any = await getTaskList({ page: 1, page_size: 100, reference_id: assetId, asset_id: assetId });
      const data = res?.data ? res.data : res;
      setTasks(Array.isArray(data?.items) ? data.items : Array.isArray(data?.content) ? data.content : Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
    } finally {
      setTaskLoading(false);
    }
  };

  const loadTaskUsers = async () => {
    try {
      // 任务负责人/协作者下拉走用户目录（GET /users/directory），不再请求受限的 GET /users
      const list = await getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT });
      setTaskUsers(list);
    } catch (error) {
      console.error(error);
      setTaskUsers([]);
    }
  };

  const handleCreateVuln = () => {
    setVulnMode('create');
    vulnForm.resetFields();
    if (selectedVersionId) {
      vulnForm.setFieldsValue({ discovered_version_id: selectedVersionId });
    }
    setVulnModalVisible(true);
  };

  const handleEditVuln = (record: any) => {
    setVulnMode('edit');
    setCurrentVulnId(record.id);
    vulnForm.setFieldsValue({
      title: record.title,
      type: record.type,
      severity: record.severity,
      discovered_version_id: record.discoveredVersionId || record.discovered_version_id,
      affected_versions: record.affectedVersions || record.affected_versions,
      description: record.description,
    });
    setVulnModalVisible(true);
  };

  const handleDeleteVuln = async (id: string) => {
    Modal.confirm({
      title: t('common.deleteConfirmTitle'),
      content: t('asset.detail.deleteVulnContent'),
      onOk: async () => {
        try {
          await deleteVulnerability(id);
          message.success(t('asset.detail.deleteSuccess'));
          if (asset?.id) loadVulnerabilities(asset.id);
        } catch (error) {
          console.error(error);
        }
      }
    });
  };

  const handleSubmitVuln = async () => {
    try {
      const values = await vulnForm.validateFields();
      setVulnSubmitting(true);
      if (vulnMode === 'create') {
        await createVulnerability(
          toVulnerabilityCreatePayload(values, asset?.id || '') as unknown as Parameters<typeof createVulnerability>[0]
        );
        message.success(t('asset.detail.vulnCreateSuccess'));
      } else {
        // t35：更新走 camelCase + DTO 白名单字段（PUT 为部分更新语义，状态字段不提交）
        await updateVulnerability(currentVulnId, toVulnerabilityUpdatePayload(values));
        message.success(t('asset.detail.vulnUpdateSuccess'));
      }
      setVulnModalVisible(false);
      if (asset?.id) loadVulnerabilities(asset.id);
    } catch (error) {
      if (!isFormValidationError(error)) {
        message.error(
          vulnMode === 'create' ? describeVulnCreateFailure(error) : describeVulnUpdateFailure(error, t('asset.detail.vulnSaveFailed'))
        );
      }
    } finally {
      setVulnSubmitting(false);
    }
  };

  const handleCreateTask = () => {
    setTaskMode('create');
    taskForm.resetFields();
    setTaskModalVisible(true);
  };

  const handleEditTask = (record: any) => {
    setTaskMode('edit');
    setCurrentTaskId(record.id);
    taskForm.setFieldsValue({
      title: record.title,
      description: record.description,
    });
    setTaskModalVisible(true);
  };

  const handleDeleteTask = async (id: string) => {
    Modal.confirm({
      title: t('common.deleteConfirmTitle'),
      content: t('asset.detail.deleteTaskContent'),
      onOk: async () => {
        try {
          await deleteTask(id);
          message.success(t('asset.detail.deleteSuccess'));
          if (asset?.id) loadTasks(asset.id);
        } catch (error) {
          console.error(error);
        }
      }
    });
  };

  const handleSubmitTask = async () => {
    try {
      const values = await taskForm.validateFields();
      setTaskSubmitting(true);
      if (taskMode === 'create') {
        await createTask({ ...values, reference_id: asset?.id, asset_id: asset?.id, asset_version_id: selectedVersionId });
        message.success(t('asset.detail.taskCreateSuccess'));
      } else {
        await updateTask(currentTaskId, values);
        message.success(t('asset.detail.taskUpdateSuccess'));
      }
      setTaskModalVisible(false);
      if (asset?.id) loadTasks(asset.id);
    } catch (error) {
      console.error(error);
    } finally {
      setTaskSubmitting(false);
    }
  };

  const handleInlineTaskStatusChange = async (record: any, status: string) => {
    if (record?.status === status) return;
    try {
      setTaskStatusUpdatingId(record.id);
      await updateTaskStatus(record.id, { status });
      message.success(t('asset.detail.taskStatusUpdateSuccess'));
      if (asset?.id) loadTasks(asset.id);
    } catch (error) {
      console.error(error);
    } finally {
      setTaskStatusUpdatingId(null);
    }
  };

  /**
   * 拉取当前用户对该资产的访问申请与认领状态，
   * 用于在页面上展示「已授权 / 待审批」并控制按钮可用性。
   */
  const loadMyAccessAndClaim = async (assetId: string) => {
    try {
      const accessRes: any = await getMyAccessRequests({ page: 1, page_size: 100 });
      const accessList: AssetAccessVO[] = accessRes?.data?.content ?? accessRes?.content ?? [];
      setMyAccess(accessList.find((item) => item.assetId === assetId) ?? null);
    } catch (error) {
      console.error('加载访问申请失败', error);
    }
    try {
      const claimRes: any = await getMyClaims({ page: 1, page_size: 100 });
      const claimList: AssetClaimVO[] = claimRes?.data?.content ?? claimRes?.content ?? [];
      setMyClaim(claimList.find((item) => item.assetId === assetId) ?? null);
    } catch (error) {
      console.error('加载认领记录失败', error);
    }
  };

  /**
   * 拉取当前用户对该资产的订阅状态（GET /notifications/subscriptions 返回已订阅 assetId 列表），
   * 用于渲染「订阅动态 / 取消订阅」按钮。
   */
  const loadSubscription = async (assetId: string) => {
    try {
      setSubscribed(await isAssetSubscribed(assetId));
    } catch (error) {
      console.error('加载订阅状态失败', error);
      setSubscribed(false);
    }
  };

  const handleToggleSubscribe = async () => {
    if (!asset?.id) return;
    try {
      setSubscribeLoading(true);
      if (subscribed) {
        await unsubscribeAsset(asset.id);
        setSubscribed(false);
        message.success(t('asset.detail.unsubscribeSuccess'));
      } else {
        await subscribeAsset(asset.id);
        setSubscribed(true);
        message.success(t('asset.detail.subscribeSuccess'));
      }
    } catch (error: any) {
      if (!error?.response) message.error(t('asset.detail.subscribeFailed'));
    } finally {
      setSubscribeLoading(false);
    }
  };

  const handleSubmitAccessRequest = async () => {
    if (!asset?.id) return;
    try {
      const values = await accessForm.validateFields();
      setAccessSubmitting(true);
      const created = await createAssetAccessRequest(asset.id, {
        can_view_documents: values.canViewDocuments,
        can_view_environments: values.canViewEnvironments,
        can_view_files: values.canViewFiles,
        allowed_file_types: values.allowedFileTypes,
        request_note: values.requestNote,
      });
      message.success(t('asset.detail.accessSuccess'));
      setAccessModalVisible(false);
      accessForm.resetFields();
      const payload: any = (created as any)?.data ?? created;
      setMyAccess(payload ?? null);
      loadMyAccessAndClaim(asset.id);
    } catch (error: any) {
      if (error?.errorFields) return; // 表单校验失败，交由表单展示
      message.error(error?.response?.data?.message || t('asset.detail.accessFailed'));
    } finally {
      setAccessSubmitting(false);
    }
  };

  const handleSubmitClaim = async () => {
    if (!asset?.id) return;
    try {
      const values = await claimForm.validateFields();
      setClaimSubmitting(true);
      const created = await createAssetClaim(asset.id, values.note);
      message.success(t('asset.detail.claimSuccess'));
      setClaimModalVisible(false);
      claimForm.resetFields();
      const payload: any = (created as any)?.data ?? created;
      setMyClaim(payload ?? null);
      loadMyAccessAndClaim(asset.id);
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.response?.data?.message || t('asset.detail.claimFailed'));
    } finally {
      setClaimSubmitting(false);
    }
  };

  useEffect(() => {
    const fetchAsset = async () => {      try {
        setLoading(true);
        const res: any = await getAssetDetail(id as string);
        const found = res?.data ? res.data : res;
        if (found && found.id) {
          const normalizedAsset = normalizeAsset(found);
          setAsset(normalizedAsset);
          loadVersions(found.id);
          loadVulnerabilities(found.id);
          loadVulnerabilityOptions();
          loadTaskUsers();
        }
      } catch (error) {
        console.error('Failed to fetch asset detail', error);
      } finally {
        setLoading(false);
      }
    };
    if (id) fetchAsset();
  }, [id]);

  useEffect(() => {
    setOverviewVersionId(selectedVersionId);
    setFileVersionScope(selectedVersionId);
    setDeliverableVersionScope(selectedVersionId);
  }, [selectedVersionId]);

  useEffect(() => {
    if (!asset?.id) return;
    loadFiles(asset.id, fileVersionScope === '__all__' ? undefined : fileVersionScope, fileVersionScope === '__all__');
  }, [asset?.id, fileVersionScope]);

  useEffect(() => {
    if (!asset?.id) return;
    loadDeliverables(asset.id, deliverableVersionScope === '__all__' ? undefined : deliverableVersionScope, deliverableVersionScope === '__all__');
  }, [asset?.id, deliverableVersionScope]);

  useEffect(() => {
    if (!asset?.id) return;
    loadMyAccessAndClaim(asset.id);
  }, [asset?.id]);

  useEffect(() => {
    if (!asset?.id) return;
    loadSubscription(asset.id);
  }, [asset?.id]);

  useEffect(() => {
    if (!asset?.id) return;
    loadEnvironments(asset.id);
  }, [asset?.id]);

  useEffect(() => {
    if (!asset?.id) return;
    loadTasks(asset.id);
  }, [asset?.id]);

  useEffect(() => {
    if (!asset?.id) return;
    loadMetadata(asset.id, stepToModule[overviewStep], overviewVersionId);
  }, [asset?.id, overviewVersionId, overviewStep]);

  useEffect(() => {
    if (!asset?.id) return;
    loadBaseOverviewInheritance(asset.id, overviewVersionId);
  }, [asset?.id, overviewVersionId]);

  // 权限画像：用于环境快照等写入口的角色判定（未知时按可写处理，由后端 403 兜底）
  useEffect(() => {
    getMyAccessProfile().then(setProfile);
  }, []);

  const handleSubmitMetadata = async () => {
    if (!asset) return;
    try {
      const currentModule = stepToModule[overviewStep];
      const values = await metadataForm.validateFields(moduleFieldNames[currentModule]);
      setMetadataSubmitting(true);
      
      let payload: any = {};
      
      const stringifyList = (list: any[], defaultTypeField?: { key: string, val: string }) => {
        if (!Array.isArray(list)) return '[]';
        const filtered = list.filter(item => (item?.value?.trim() || item?.note?.trim()));
        return JSON.stringify(filtered.map(item => ({
          value: item?.value?.trim() || '',
          note: item?.note?.trim() || '',
          ...(defaultTypeField ? { [defaultTypeField.key]: item?.[defaultTypeField.key] || defaultTypeField.val } : {})
        })));
      };

      if (currentModule === 'base') {
        const normalizedLanguages = Array.isArray(values.programmingLanguagesList)
          ? values.programmingLanguagesList
              .map((item: any) => ({
                language: item?.language?.trim() || '',
                version: item?.version?.trim() || '',
                framework: item?.framework?.trim() || '',
                note: item?.note?.trim() || '',
              }))
              .filter((item: any) => item.language || item.version || item.framework || item.note)
          : [];
        payload = {
          systemArchitecture: values.systemArchitecture,
          runtimePlatform: values.runtimePlatform,
          deploymentMethod: values.deploymentMethod,
          programmingLanguagesJson: JSON.stringify(normalizedLanguages)
        };
      } else if (currentModule === 'network') {
        payload = {
          servicePorts: stringifyList(values.servicePortsList, { key: 'accessType', val: 'all' }),
          apiEndpoints: stringifyList(values.apiEndpointsList, { key: 'authType', val: 'auth_required' })
        };
      } else if (currentModule === 'env') {
        payload = {
          installPath: values.installPath,
          startupCommand: values.startupCommand,
          databaseType: values.databaseType,
          databaseConnection: values.databaseConnection,
          startupScriptPath: stringifyList(values.startupScriptPathList),
          configFilePath: stringifyList(values.configFilePathList)
        };
      } else if (currentModule === 'account') {
        const normalizedAccounts = Array.isArray(values.accountCredentials)
          ? values.accountCredentials
              .map((item: any) => ({
                usageNote: item?.usageNote?.trim() || '',
                username: item?.username?.trim() || '',
                password: item?.password || '',
              }))
              .filter((item: any) => item.usageNote || item.username || item.password)
          : [];
        payload = {
          accountCredentialsJson: JSON.stringify(normalizedAccounts)
        };
      } else if (currentModule === 'extra') {
        payload = {
          specialNotes: values.specialNotes,
          changeReason: values.changeReason
        };
      }

      await updateAssetMetadata(asset.id, currentModule, payload, overviewVersionId);
      message.success(t('asset.detail.metadataSaveSuccess'));
      setMetadataExists(true);
    } catch (error) {
      console.error(error);
    } finally {
      setMetadataSubmitting(false);
    }
  };

  const handleCreateVersion = async () => {
    if (!asset) return;
    try {
      const values = await versionForm.validateFields();
      setCreatingVersion(true);
      await createAssetVersion(asset.id, {
        name: values.name,
        description: values.description,
        status: values.status,
        inherit_base_overview: values.inheritBaseOverview,
        inherit_from_version_id: values.inheritFromVersionId,
      }, values.setDefault);
      message.success(t('asset.detail.versionCreateSuccess'));
      setCreateVersionVisible(false);
      versionForm.resetFields();
      await loadVersions(asset.id);
    } catch (error) {
      console.error(error);
    } finally {
      setCreatingVersion(false);
    }
  };

  const handleSetDefaultVersion = async () => {
    if (!asset || !selectedVersionId) {
      message.warning(t('asset.detail.versionSelectFirst'));
      return;
    }
    const selected = assetVersions.find((item) => item.id === selectedVersionId);
    if (selected?.isDefault) {
      message.info(t('asset.detail.versionAlreadyDefault'));
      return;
    }
    try {
      setSetDefaultLoading(true);
      await setDefaultAssetVersion(asset.id, selectedVersionId);
      message.success(t('asset.detail.versionDefaultSuccess'));
      await loadVersions(asset.id);
    } catch (error) {
      console.error(error);
    } finally {
      setSetDefaultLoading(false);
    }
  };

  const handleSetLatestVersion = async () => {
    if (!asset || !selectedVersionId) {
      message.warning(t('asset.detail.versionSelectFirst'));
      return;
    }
    const selected = assetVersions.find((item) => item.id === selectedVersionId);
    if (selected?.isLatest) {
      message.info(t('asset.detail.versionAlreadyLatest'));
      return;
    }
    try {
      setSetLatestLoading(true);
      await setLatestAssetVersion(asset.id, selectedVersionId);
      message.success(t('asset.detail.versionLatestSuccess'));
      await loadVersions(asset.id);
    } catch (error) {
      console.error(error);
    } finally {
      setSetLatestLoading(false);
    }
  };

  const handleUploadAssetFile = async (target: 'asset_file' | 'deliverable') => {
    if (!asset) return;
    try {
      const uploadForm = target === 'asset_file' ? assetFileUploadForm : deliverableUploadForm;
      const uploadList = target === 'asset_file' ? assetFileUploadList : deliverableUploadList;
      const setUploadList = target === 'asset_file' ? setAssetFileUploadList : setDeliverableUploadList;
      const values = await uploadForm.validateFields();
      if (uploadList.length === 0) {
        message.error(t('asset.detail.noFileSelected'));
        return;
      }
      const selectedFile = uploadList[0]?.originFileObj ?? uploadList[0];
      if (!(selectedFile instanceof Blob)) {
        message.error(t('asset.detail.invalidFile'));
        return;
      }
      if (target === 'asset_file') {
        setFileUploading(true);
      } else {
        setDeliverableUploading(true);
      }
      const formData = new FormData();
      formData.append('file', selectedFile, uploadList[0]?.name || 'upload.bin');
      if (target === 'asset_file') {
        formData.append('category', values.category);
      } else {
        formData.append('deliverableType', values.category);
      }
      if (values.description) formData.append('description', values.description);
      const bindVersionId = target === 'asset_file'
        ? (fileVersionScope === '__all__' ? selectedVersionId : fileVersionScope)
        : (deliverableVersionScope === '__all__' ? selectedVersionId : deliverableVersionScope);
      if (target === 'asset_file') {
        await uploadAssetFile(asset.id, formData, bindVersionId);
        await loadFiles(asset.id, fileVersionScope === '__all__' ? undefined : fileVersionScope, fileVersionScope === '__all__');
        message.success(t('asset.detail.uploadSuccess'));
      } else {
        await uploadAssetDeliverable(asset.id, formData, bindVersionId);
        await loadDeliverables(asset.id, deliverableVersionScope === '__all__' ? undefined : deliverableVersionScope, deliverableVersionScope === '__all__');
        message.success(t('asset.detail.deliverableUploadSuccess'));
      }
      uploadForm.resetFields();
      setUploadList([]);
    } finally {
      if (target === 'asset_file') {
        setFileUploading(false);
      } else {
        setDeliverableUploading(false);
      }
    }
  };

  const handleCreateEnv = async () => {
    if (!asset) return;
    try {
      const values = await envForm.validateFields();
      setEnvSubmitting(true);
      await createEnvironment({
        name: values.name,
        asset_id: asset.id,
        template_id: values.template_id || '',
        config: values.config || '{}',
      });
      message.success(t('asset.detail.envCreateSuccess'));
      setEnvModalVisible(false);
      envForm.resetFields();
      loadEnvironments(asset.id);
    } catch (error) {
      // t40 环境写端点限 ADMIN/PROJECT_LEADER/TEAM_LEADER：403 给角色口径，400 展示后端原文
      if (isFormValidationError(error)) return;
      message.error(describeEnvWriteFailure(error, t('asset.detail.envCreateFailed')));
    } finally {
      setEnvSubmitting(false);
    }
  };

  const handleDeleteEnv = (envId: string) => {
    Modal.confirm({
      title: t('common.deleteConfirmTitle'),
      content: t('asset.detail.envDeleteContent'),
      onOk: async () => {
        try {
          await deleteEnvironment(envId);
          message.success(t('asset.detail.envDeleteSuccess'));
          if (asset) loadEnvironments(asset.id);
        } catch (error) {
          const statusCode = getApiErrorStatus(error);
          if (statusCode === 404) {
            message.error(t('asset.detail.envDeleteNotFound'));
            if (asset) loadEnvironments(asset.id);
          } else if (statusCode === 403) {
            message.error(t('asset.detail.envDeleteForbidden'));
          } else {
            message.error(getApiErrorMessage(error, t('asset.detail.envDeleteFailed')));
          }
        }
      }
    });
  };

  /* ==================== 环境快照（t40 写端点限 ADMIN/PROJECT_LEADER/TEAM_LEADER） ==================== */

  /**
   * 环境写操作的失败提示：401/403/400/5xx 分级。
   * - 401：登录过期（拦截器通常已跳登录，这里兜底）
   * - 403：t40 收紧后的角色门槛，给出角色口径而不是泛化「失败」
   * - 400：参数问题（如快照缺 name / 名称非法），展示后端原文
   * 统一复用 utils/errorMessage，不重复实现状态码解析。
   */
  const describeEnvWriteFailure = (error: unknown, fallback: string): string => {
    const status = getApiErrorStatus(error);
    if (status === 401) return t('asset.detail.loginExpired');
    if (status === 403) return ENVIRONMENT_FORBIDDEN_MESSAGE;
    if (status === 400) {
      return getApiErrorMessage(error, t('asset.detail.snapshotNameInvalid'));
    }
    return getApiErrorMessage(error, fallback);
  };

  const canWriteEnv = profile ? canWriteEnvironment(profile) : true;

  const loadSnapshots = async (envId: string) => {
    setSnapshotLoading(true);
    try {
      const res: any = await getEnvironmentSnapshots(envId);
      const payload = res?.data ?? res;
      setSnapshotList(Array.isArray(payload) ? payload : Array.isArray(payload?.content) ? payload.content : []);
    } catch (error) {
      setSnapshotList([]);
      message.error(describeEnvWriteFailure(error, t('asset.detail.snapshotListFailed')));
    } finally {
      setSnapshotLoading(false);
    }
  };

  const openSnapshotPanel = async (env: EnvironmentVO) => {
    setSnapshotEnv(env);
    setSnapshotList([]);
    if (!snapshotName.trim()) {
      setSnapshotName(
      t('asset.detail.snapshotDefaultName', {
        name: env.name || t('asset.detail.snapshotEnvFallback'),
        time: new Date().toLocaleString(),
      }),
    );
    }
    await loadSnapshots(env.id);
  };

  /**
   * 创建快照。
   * 后端 `POST /environments/{id}/snapshots` 的 name 是 @RequestParam 且必填，
   * 缺参会先 400（参数绑定早于方法级守卫），因此在**发请求前**做本地非空校验。
   */
  const handleCreateSnapshot = async () => {
    if (!snapshotEnv) return;
    const name = snapshotName.trim();
    if (!name) {
      message.warning(t('asset.detail.snapshotMissingName'));
      return;
    }
    setSnapshotSubmitting(true);
    try {
      await createEnvironmentSnapshot(snapshotEnv.id, name);
      message.success(t('asset.detail.snapshotCreateSuccess'));
      await loadSnapshots(snapshotEnv.id);
    } catch (error) {
      message.error(describeEnvWriteFailure(error, t('asset.detail.snapshotCreateFailed')));
    } finally {
      setSnapshotSubmitting(false);
    }
  };

  const handleRollbackSnapshot = (snapshotId: string) => {
    if (!snapshotEnv) return;
    Modal.confirm({
      title: t('asset.detail.snapshotRollbackTitle'),
      content: t('asset.detail.snapshotRollbackContent'),
      okText: t('asset.detail.snapshotRollback'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        setSnapshotRollingId(snapshotId);
        try {
          await rollbackEnvironmentSnapshot(snapshotEnv.id, snapshotId);
          message.success(t('asset.detail.snapshotRollbackSuccess'));
          await loadSnapshots(snapshotEnv.id);
          if (asset) loadEnvironments(asset.id);
        } catch (error) {
          message.error(describeEnvWriteFailure(error, t('asset.detail.snapshotRollbackFailed')));
        } finally {
          setSnapshotRollingId(null);
        }
      },
    });
  };

  const handleDeleteDeliverable = (deliverableId: string) => {
    if (!asset) return;
    Modal.confirm({
      title: t('common.deleteConfirmTitle'),
      content: t('asset.detail.deliverableDeleteContent'),
      onOk: async () => {
        try {
          await deleteAssetDeliverable(deliverableId);
          message.success(t('asset.detail.deliverableDeleteSuccess'));
          await loadDeliverables(asset.id, deliverableVersionScope === '__all__' ? undefined : deliverableVersionScope, deliverableVersionScope === '__all__');
        } catch (error) {
          console.error(error);
          message.error(t('asset.detail.deliverableDeleteFailed'));
        }
      },
    });
  };

  const handleDeleteAssetFile = (fileId: string) => {
    if (!asset) return;
    Modal.confirm({
      title: t('common.deleteConfirmTitle'),
      content: t('asset.detail.fileDeleteContent'),
      onOk: async () => {
        try {
          await deleteAssetFile(fileId);
          message.success(t('asset.detail.fileDeleteSuccess'));
          await loadFiles(asset.id, fileVersionScope === '__all__' ? undefined : fileVersionScope, fileVersionScope === '__all__');
        } catch (error) {
          console.error(error);
          message.error(t('asset.detail.fileDeleteFailed'));
        }
      },
    });
  };

  const handleDownloadAssetFile = async (record: AssetFileVO) => {
    try {
      const blob = await downloadAssetFile(record.id);
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = record.name || 'asset-file.bin';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(objectUrl);
    } catch (error) {
      console.error(error);
      message.error(t('asset.detail.fileDownloadFailed'));
    }
  };

  const handleDownloadDeliverable = async (record: AssetDeliverableVO) => {
    try {
      const blob = await downloadAssetDeliverable(record.id);
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = record.name || 'deliverable.bin';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(objectUrl);
    } catch (error) {
      console.error(error);
      message.error(t('asset.detail.deliverableDownloadFailed'));
    }
  };

  const fileColumns = [
    { title: t('asset.detail.fileName'), dataIndex: 'name', key: 'name' },
    { title: t('asset.detail.fileCategoryCol'), dataIndex: 'category', key: 'category' },
    { title: t('asset.detail.colVersion'), key: 'assetVersionId', render: (_: any, record: AssetFileVO & { asset_version_id?: string }) => versionLabelById(record.assetVersionId || record.asset_version_id) },
    {
      title: t('asset.detail.colSize'),
      dataIndex: 'fileSize',
      key: 'fileSize',
      render: (size?: number) => {
        if (!size) return '-';
        if (size >= 1024 * 1024 * 1024) return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`;
        if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(2)} MB`;
        if (size >= 1024) return `${(size / 1024).toFixed(2)} KB`;
        return `${size} B`;
      },
    },
    { title: t('asset.detail.colUploadedAt'), dataIndex: 'createdAt', key: 'createdAt', render: (val: string) => val ? new Date(val).toLocaleString() : '-' },
    {
      title: t('asset.detail.colAction'),
      key: 'action',
      render: (_: any, record: AssetFileVO) => (
        <Space>
          <a onClick={() => handleDownloadAssetFile(record)}>{t('asset.detail.download')}</a>
          <a onClick={() => handleDeleteAssetFile(record.id)} style={{ color: 'red' }}>{t('common.delete')}</a>
        </Space>
      ),
    },
  ];

  const deliverableColumns = [
    { title: t('asset.detail.fileName'), dataIndex: 'name', key: 'name' },
    { title: t('asset.detail.deliverableTypeCol'), dataIndex: 'deliverableType', key: 'deliverableType' },
    { title: t('asset.detail.colVersion'), key: 'assetVersionId', render: (_: any, record: AssetDeliverableVO & { asset_version_id?: string }) => versionLabelById(record.assetVersionId || record.asset_version_id) },
    {
      title: t('asset.detail.colSize'),
      dataIndex: 'fileSize',
      key: 'fileSize',
      render: (size?: number) => {
        if (!size) return '-';
        if (size >= 1024 * 1024 * 1024) return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`;
        if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(2)} MB`;
        if (size >= 1024) return `${(size / 1024).toFixed(2)} KB`;
        return `${size} B`;
      },
    },
    { title: t('asset.detail.colUploadedAt'), dataIndex: 'createdAt', key: 'createdAt', render: (val: string) => val ? new Date(val).toLocaleString() : '-' },
    {
      title: t('asset.detail.colAction'),
      key: 'action',
      render: (_: any, record: AssetDeliverableVO) => (
        <Space>
          <a onClick={() => handleDownloadDeliverable(record)}>{t('asset.detail.download')}</a>
          <a onClick={() => handleDeleteDeliverable(record.id)} style={{ color: 'red' }}>{t('common.delete')}</a>
        </Space>
      ),
    },
  ];

  const envStatusMeta: Record<string, { color: string; label: string }> = {
    created: { color: 'default', label: t('asset.detail.envStatus.created') },
    provisioning: { color: 'processing', label: t('asset.detail.envStatus.provisioning') },
    deploying: { color: 'processing', label: t('asset.detail.envStatus.provisioning') },
    running: { color: 'success', label: t('asset.detail.envStatus.running') },
    stopped: { color: 'default', label: t('asset.detail.envStatus.stopped') },
    failed: { color: 'error', label: t('asset.detail.envStatus.failed') },
  };

  const renderEnvStatus = (status?: string) => {
    const meta = envStatusMeta[(status || '').toLowerCase()];
    return <Tag color={meta?.color ?? 'default'}>{meta?.label ?? (status || '-')}</Tag>;
  };

  /** 部署/启停按钮：不可用时置灰 + Tooltip 说明原因 */
  const renderEnvActionButton = (
    record: EnvironmentVO,
    action: 'deploy' | 'start' | 'stop',
    label: string,
    icon: React.ReactNode,
    disabledReason?: string,
  ) => {
    const loading = envActionLoading === `${record.id}:${action}`;
    const converging = envConvergingId === record.id;
    const button = (
      <Button
        type="link"
        size="small"
        icon={icon}
        loading={loading}
        disabled={Boolean(disabledReason) || converging}
        onClick={() => handleEnvironmentAction(record, action)}
      >
        {label}
      </Button>
    );
    if (!disabledReason) {
      return button;
    }
    return (
      <Tooltip title={disabledReason}>
        {/* 禁用态按钮不触发鼠标事件，外面包一层 span 才能显示 Tooltip */}
        <span style={{ display: 'inline-block' }}>{button}</span>
      </Tooltip>
    );
  };

  const envColumns = [
    { title: t('asset.detail.envColName'), dataIndex: 'name', key: 'name' },
    {
      title: t('asset.detail.colStatus'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string, record: EnvironmentVO) => (
        <Space size={4} wrap>
          {renderEnvStatus(status)}
          {envConvergingId === record.id ? <Tag color="processing">{t('asset.detail.envConverging')}</Tag> : null}
        </Space>
      ),
    },
    {
      title: t('asset.detail.envColFailReason'),
      dataIndex: 'failReason',
      key: 'failReason',
      render: (failReason?: string | null, record?: EnvironmentVO) => {
        if (!failReason) {
          return <span style={{ color: '#bfbfbf' }}>-</span>;
        }
        const isFailed = (record?.status || '').toLowerCase() === 'failed';
        return (
          <Typography.Paragraph
            style={{ marginBottom: 0, maxWidth: 320, color: isFailed ? '#cf1322' : undefined }}
            copyable={{ text: failReason }}
            ellipsis={{ rows: 2, expandable: true, symbol: t('asset.detail.expand') }}
          >
            {failReason}
          </Typography.Paragraph>
        );
      },
    },
    { title: t('asset.detail.envColTemplate'), dataIndex: 'templateId', key: 'templateId', ellipsis: true },
    {
      title: t('asset.detail.colCreatedAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text: string) => (text ? new Date(text).toLocaleString() : '-'),
    },
    {
      title: t('asset.detail.colAction'),
      key: 'action',
      width: 220,
      render: (_: any, record: EnvironmentVO) => {
        const unavailableReason =
          envProvisioner.available === false
            ? envProvisioner.message || t('asset.detail.envActionBlocked')
            : undefined;
        const status = (record.status || '').toLowerCase();
        const startDisabled =
          unavailableReason ?? (status === 'running' ? t('asset.detail.envAlreadyRunning') : status === 'provisioning' ? t('asset.detail.envProvisioningWait') : undefined);
        const stopDisabled =
          unavailableReason ?? (status === 'stopped' ? t('asset.detail.envAlreadyStopped') : status === 'provisioning' ? t('asset.detail.envProvisioningWait') : undefined);
        return (
          <Space size={0} wrap>
            {renderEnvActionButton(record, 'deploy', t('asset.detail.envDeploy'), <CloudUploadOutlined />, unavailableReason)}
            {renderEnvActionButton(record, 'start', t('asset.detail.envStart'), <PlayCircleOutlined />, startDisabled)}
            {renderEnvActionButton(record, 'stop', t('asset.detail.envStop'), <StopOutlined />, stopDisabled)}
            <a onClick={() => openSnapshotPanel(record)} style={{ marginLeft: 4 }}>{t('asset.detail.envSnapshotAction')}</a>
            <a onClick={() => handleDeleteEnv(record.id)} style={{ color: 'red', marginLeft: 4 }}>{t('common.delete')}</a>
          </Space>
        );
      },
    },
  ];

  const vulnColumns = [
    { title: t('asset.detail.colVulnNo'), dataIndex: 'vulnerabilityNo', key: 'vulnerabilityNo', render: (_: string, record: any) => record?.vulnerabilityNo || record?.vulnerability_no || '-' },
    { title: t('asset.detail.colVulnTitle'), dataIndex: 'title', key: 'title', render: (text: string, record: any) => <a onClick={() => navigate(`/vulnerabilities/${record.id}`)}>{text}</a> },
    { title: t('asset.detail.colDiscoveredVersion'), dataIndex: 'discoveredVersionId', key: 'discoveredVersionId', render: (_: string, record: any) => versionLabelById(record?.discoveredVersionId || record?.discovered_version_id) },
    { title: t('asset.detail.colAffectedVersions'), dataIndex: 'affectedVersions', key: 'affectedVersions', render: (_: string, record: any) => record?.affectedVersions || record?.affected_versions || '-' },
    { title: t('asset.detail.colSeverity'), dataIndex: 'severity', key: 'severity', render: (severity: string) => {
        const colors: Record<string, string> = { CRITICAL: 'darkred', HIGH: 'red', MEDIUM: 'orange', LOW: 'blue', INFO: 'default' };
        return <Tag color={colors[severity] || 'default'}>{severity}</Tag>;
    } },
    { title: t('asset.detail.colStatus'), dataIndex: 'status', key: 'status' },
    { title: t('asset.detail.colDiscoveredAt'), dataIndex: 'createdAt', key: 'createdAt', render: (text: string) => text ? new Date(text).toLocaleString() : '-' },
    { 
      title: t('asset.detail.colAction'), 
      key: 'action',
      render: (_: any, record: any) => (
        <Space>
          <a onClick={() => handleEditVuln(record)}>{t('common.edit')}</a>
          <a onClick={() => handleDeleteVuln(record.id)} style={{ color: 'red' }}>{t('common.delete')}</a>
        </Space>
      )
    }
  ];

  const taskColumns = [
    { title: t('asset.detail.colTaskName'), dataIndex: 'title', key: 'title', render: (text: string, record: any) => <a onClick={() => navigate(`/tasks/${record.id}`)}>{text}</a> },
    {
      title: t('asset.detail.colStatus'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string, record: any) => (
        <Select
          size="small"
          style={{ width: 130 }}
          value={status}
          loading={taskStatusUpdatingId === record.id}
          onChange={(value) => handleInlineTaskStatusChange(record, value)}
        >
          <Option value="pending">{t('asset.detail.taskStatus.pending')}</Option>
          <Option value="in_progress">{t('asset.detail.taskStatus.inProgress')}</Option>
          <Option value="completed">{t('asset.detail.taskStatus.completed')}</Option>
          <Option value="cancelled">{t('asset.detail.taskStatus.cancelled')}</Option>
        </Select>
      ),
    },
    { title: t('asset.detail.colCreator'), key: 'creator', render: (_: any, record: any) => record?.creator?.username || '-' },
    { title: t('asset.detail.colAssignee'), key: 'assignee', render: (_: any, record: any) => record?.assignee?.username || '-' },
    { title: t('asset.detail.colCreatedAt'), dataIndex: 'createdAt', key: 'createdAt', render: (text: string) => text ? new Date(text).toLocaleString() : '-' },
    { 
      title: t('asset.detail.colAction'), 
      key: 'action',
      render: (_: any, record: any) => (
        <Space>
          <a onClick={() => handleEditTask(record)}>{t('common.edit')}</a>
          <a onClick={() => handleDeleteTask(record.id)} style={{ color: 'red' }}>{t('common.delete')}</a>
        </Space>
      )
    }
  ];

  if (loading) {
    return <div style={{ textAlign: 'center', padding: '50px' }}><Spin size="large" /></div>;
  }

  if (!asset) {
    return (
      <Result
        status="404"
        title="404"
        subTitle={t('asset.detail.notFound')}
        extra={<Button type="primary" onClick={() => navigate('/assets')}>{t('asset.detail.backToList')}</Button>}
      />
    );
  }

  return (
    <div className="asset-detail-workspace">
      <Card
        title={
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <span style={{ fontSize: '20px', fontWeight: 'bold', marginRight: '16px' }}>{asset.name}</span>
            <Tag color="blue">{asset.code}</Tag>
            <Tag color={asset.status === 'ACTIVE' ? 'success' : 'default'}>{asset.status}</Tag>
            {myAccess?.status === 'approved' && <Tag color="green">{t('asset.detail.tagApproved')}</Tag>}
            {myAccess?.status === 'pending' && <Tag color="orange">{t('asset.detail.tagPending')}</Tag>}
            {myAccess?.status === 'rejected' && <Tag color="red">{t('asset.detail.tagRejected')}</Tag>}
            {myClaim && ['pending', 'approved'].includes(myClaim.status) && (
              <Tag color="geekblue">{t('asset.detail.tagClaimed', { status: myClaim.status })}</Tag>
            )}
            {subscribed && <Tag color="cyan">{t('asset.detail.tagSubscribed')}</Tag>}
          </div>
        }
        extra={
          <Space>
            <Button onClick={() => setAccessModalVisible(true)} disabled={myAccess?.status === 'pending' || myAccess?.status === 'approved'}>{t('asset.detail.requestAccess')}</Button>
            <Button onClick={() => setClaimModalVisible(true)} disabled={!!myClaim && ['pending', 'approved'].includes(myClaim.status)}>{t('asset.detail.claimModalTitle')}</Button>
            <Tooltip title={subscribed ? t('asset.detail.unsubscribeTip') : t('asset.detail.subscribeTip')}>
              <Button
                type={subscribed ? 'default' : 'primary'}
                loading={subscribeLoading}
                onClick={handleToggleSubscribe}
              >
                {subscribed ? t('asset.detail.unsubscribe') : t('asset.detail.subscribe')}
              </Button>
            </Tooltip>
            <Select
              style={{ width: 220 }}
              value={selectedVersionId}
              loading={versionLoading}
              placeholder={t('asset.detail.versionPlaceholder')}
              onChange={(value) => setSelectedVersionId(value)}
              options={assetVersions.map((item) => ({
                label: `${item.name || `v${item.versionNo}`}${item.isDefault ? t('asset.detail.versionDefaultSuffix') : ''}${item.isLatest ? t('asset.detail.versionLatestSuffix') : ''}`,
                value: item.id,
              }))}
            />
            <Tooltip title={canWriteCurrentAsset ? '' : ASSET_WRITE_FORBIDDEN_TIP}>
              <Button disabled={!canWriteCurrentAsset} onClick={() => setCreateVersionVisible(true)}>{t('asset.detail.newVersion')}</Button>
            </Tooltip>
            <Tooltip title={canWriteCurrentAsset ? '' : ASSET_WRITE_FORBIDDEN_TIP}>
              <Button disabled={!canWriteCurrentAsset} loading={setDefaultLoading} onClick={handleSetDefaultVersion}>{t('asset.detail.setDefault')}</Button>
            </Tooltip>
            <Tooltip title={canWriteCurrentAsset ? '' : ASSET_WRITE_FORBIDDEN_TIP}>
              <Button disabled={!canWriteCurrentAsset} loading={setLatestLoading} onClick={handleSetLatestVersion}>{t('asset.detail.setLatest')}</Button>
            </Tooltip>
            <Button onClick={() => navigate('/assets')}>{t('asset.detail.backList')}</Button>
          </Space>
        }
        style={{ marginBottom: 24 }}
      >
        <Descriptions column={3}>
          <Descriptions.Item label={t('asset.detail.metaTargetType')}>{asset?.target_type}</Descriptions.Item>
          <Descriptions.Item label={t('asset.detail.metaSource')}>{asset?.target_source}</Descriptions.Item>
          <Descriptions.Item label={t('asset.detail.metaTeam')}>{asset?.team_name || asset?.team?.name || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('asset.detail.colAssignee')}>{asset?.owner_name || asset?.owner?.username || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('asset.detail.metaDescription')}>{asset?.description}</Descriptions.Item>
          <Descriptions.Item label={t('asset.detail.colCreatedAt')}>{asset?.created_at ? new Date(asset.created_at).toLocaleString() : '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Modal
        title={t('asset.detail.versionModalTitle')}
        open={createVersionVisible}
        onOk={handleCreateVersion}
        onCancel={() => {
          setCreateVersionVisible(false);
          versionForm.resetFields();
        }}
        confirmLoading={creatingVersion}
      >
        <Form
          form={versionForm}
          layout="vertical"
          initialValues={{
            setDefault: false,
            status: 'active',
            inheritBaseOverview: false,
          }}
        >
          <Form.Item name="name" label={t('asset.detail.versionName')}>
            <Input placeholder={t('asset.detail.versionNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('asset.detail.versionDescription')}>
            <TextArea rows={3} placeholder={t('asset.detail.versionDescriptionPlaceholder')} />
          </Form.Item>
          <Form.Item name="status" label={t('asset.detail.colStatus')}>
            <Select>
              <Option value="active">active</Option>
              <Option value="inactive">inactive</Option>
            </Select>
          </Form.Item>
          <Form.Item name="setDefault" label={t('asset.detail.versionSetDefault')}>
            <Select>
              <Option value={false}>{t('asset.detail.no')}</Option>
              <Option value={true}>{t('asset.detail.yes')}</Option>
            </Select>
          </Form.Item>
          <Form.Item name="inheritBaseOverview" label={t('asset.detail.versionInherit')}>
            <Select>
              <Option value={false}>{t('asset.detail.no')}</Option>
              <Option value={true} disabled={assetVersions.length === 0}>{t('asset.detail.yes')}</Option>
            </Select>
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prevValues, currentValues) => prevValues.inheritBaseOverview !== currentValues.inheritBaseOverview}
          >
            {({ getFieldValue }) => {
              if (!getFieldValue('inheritBaseOverview')) {
                return null;
              }
              return (
                <Form.Item
                  name="inheritFromVersionId"
                  label={t('asset.detail.versionSource')}
                  rules={[{ required: true, message: t('asset.detail.versionSourceRequired') }]}
                >
                  <Select
                    placeholder={t('asset.detail.versionSourcePlaceholder')}
                    options={assetVersions.map((item) => ({
                      label: `${item.name || `v${item.versionNo}`}${item.isDefault ? t('asset.detail.versionDefaultSuffix') : ''}${item.isLatest ? t('asset.detail.versionLatestSuffix') : ''}`,
                      value: item.id,
                    }))}
                  />
                </Form.Item>
              );
            }}
          </Form.Item>
        </Form>
      </Modal>

      <Card style={{ borderRadius: 8 }} bodyStyle={{ padding: '0 24px 24px' }}>
        <Tabs defaultActiveKey="overview" size="large">
          <TabPane tab={<span><FileZipOutlined />{t('asset.detail.filesTitle')}</span>} key="asset-files">
            <div style={{ padding: '24px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <div>
                  <h3 style={{ marginBottom: 0 }}>{t('asset.detail.filesTitle')}</h3>
                </div>
                <Select
                  style={{ width: 260 }}
                  value={fileVersionScope}
                  placeholder={t('asset.detail.fileVersionScope')}
                  onChange={(value) => setFileVersionScope(value)}
                  options={[
                    { label: t('asset.detail.showAllVersions'), value: '__all__' },
                    ...assetVersions.map((item) => ({
                      label: `${item.name || `v${item.versionNo}`}${item.isDefault ? t('asset.detail.versionDefaultSuffix') : ''}${item.isLatest ? t('asset.detail.versionLatestSuffix') : ''}`,
                      value: item.id,
                    })),
                  ]}
                />
              </div>
              <div style={{ marginBottom: 24, padding: 16, background: '#fafafa', borderRadius: 8 }}>
                <Form form={assetFileUploadForm} layout="vertical">
                  <Row gutter={16}>
                    <Col span={6}>
                      <Form.Item name="category" label={t('asset.detail.fileCategory')} rules={[{ required: true, message: t('asset.detail.fileCategoryRequired') }]}>
                        <Select placeholder={t('asset.detail.fileCategoryPlaceholder')}>
                          <Option value={t('asset.detail.categorySystemImage')}>{t('asset.detail.categorySystemImage')}</Option>
                          <Option value={t('asset.detail.categoryDiskFile')}>{t('asset.detail.categoryDiskFile')}</Option>
                          <Option value={t('asset.detail.categoryInstaller')}>{t('asset.detail.categoryInstaller')}</Option>
                          <Option value={t('asset.detail.categoryExtractor')}>{t('asset.detail.categoryExtractor')}</Option>
                          <Option value={t('asset.detail.categoryConfig')}>{t('asset.detail.categoryConfig')}</Option>
                          <Option value={t('asset.detail.categoryOther')}>{t('asset.detail.categoryOther')}</Option>
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item name="description" label={t('asset.detail.metaDescription')}>
                        <Input placeholder={t('asset.detail.fileDescPlaceholder')} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={24}>
                      <Form.Item label={t('asset.detail.fileUpload')}>
                        <Space>
                          <Upload
                            beforeUpload={(file) => {
                              setAssetFileUploadList([file]);
                              return false;
                            }}
                            onRemove={() => setAssetFileUploadList([])}
                            fileList={assetFileUploadList}
                            maxCount={1}
                          >
                            <Button icon={<UploadOutlined />}>{t('asset.detail.chooseFile')}</Button>
                          </Upload>
                          <Button type="primary" loading={fileUploading} onClick={() => handleUploadAssetFile('asset_file')} disabled={assetFileUploadList.length === 0}>{t('asset.detail.startUpload')}</Button>
                        </Space>
                      </Form.Item>
                    </Col>
                  </Row>
                </Form>
              </div>
              <Table
                columns={fileColumns}
                dataSource={files}
                rowKey="id"
                loading={fileLoading}
                pagination={false}
              />
            </div>
          </TabPane>
          <TabPane tab={<span><AppstoreOutlined />{t('asset.detail.overviewTitle')}</span>} key="overview">
            <div style={{ padding: '24px 0', maxWidth: 1000 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <h3 style={{ margin: 0 }}>{t('asset.detail.overviewTitle')}</h3>
                <Select
                  style={{ width: 260 }}
                  value={overviewVersionId}
                  placeholder={t('asset.detail.overviewVersionScope')}
                  onChange={(value) => setOverviewVersionId(value)}
                  options={assetVersions.map((item) => ({
                    label: `${item.name || `v${item.versionNo}`}${item.isDefault ? t('asset.detail.versionDefaultSuffix') : ''}${item.isLatest ? t('asset.detail.versionLatestSuffix') : ''}`,
                    value: item.id,
                  }))}
                />
              </div>
              {baseOverviewInheritedFromVersionId && (
                <div style={{ marginBottom: 16, padding: '8px 12px', background: '#f6ffed', border: '1px solid #b7eb8f', borderRadius: 6, color: '#389e0d' }}>
                  {t('asset.detail.overviewInherited', { version: versionLabelById(baseOverviewInheritedFromVersionId) })}
                </div>
              )}
              <Spin spinning={metadataLoading}>
                <Steps 
                  current={overviewStep} 
                  onChange={setOverviewStep} 
                  style={{ marginBottom: 40 }}
                  items={[
                    { title: t('asset.detail.stepBase') },
                    { title: t('asset.detail.stepNetwork') },
                    { title: t('asset.detail.stepEnv') },
                    { title: t('asset.detail.stepAccount') },
                    { title: t('asset.detail.stepExtra') },
                  ]}
                />
                
                <Form form={metadataForm} layout="vertical">
                  <div style={{ display: overviewStep === 0 ? 'block' : 'none' }}>
                    <Card bordered={false} className="overview-step-card">
                      <Row gutter={24}>
                        <Col span={8}><Form.Item name="runtimePlatform" label={t('asset.detail.runtimePlatform')}><Input placeholder={t('asset.detail.runtimePlatformPlaceholder')} /></Form.Item></Col>
                        <Col span={8}><Form.Item name="deploymentMethod" label={t('asset.detail.deploymentMethod')}><Input placeholder={t('asset.detail.deploymentMethodPlaceholder')} /></Form.Item></Col>
                        <Col span={8}><Form.Item name="systemArchitecture" label={t('asset.detail.systemArchitecture')}><Input placeholder="x86 / ARM" /></Form.Item></Col>
                        
                        <Col span={24}>
                          <Form.Item label={t('asset.detail.programmingLanguages')}>
                            <div style={{ border: '1px solid #e8e8e8', borderRadius: 8, overflow: 'hidden' }}>
                              <div style={{ background: '#fafafa', padding: '12px 16px', borderBottom: '1px solid #e8e8e8', display: 'flex', fontWeight: 500, color: '#595959' }}>
                                <div style={{ flex: '0 0 20%', paddingRight: 8 }}>{t('asset.detail.langName')}</div>
                                <div style={{ flex: '0 0 20%', paddingRight: 8 }}>{t('asset.detail.colVersion')}</div>
                                <div style={{ flex: '0 0 20%', paddingRight: 8 }}>{t('asset.detail.langFramework')}</div>
                                <div style={{ flex: '0 0 30%', paddingRight: 8 }}>{t('asset.detail.langNote')}</div>
                                <div style={{ flex: '0 0 10%', textAlign: 'center' }}>{t('asset.detail.colAction')}</div>
                              </div>
                              <div style={{ padding: '16px 16px 0' }}>
                                <Form.List name="programmingLanguagesList">
                                  {(fields, { add, remove }) => (
                                    <>
                                      {fields.map(({ key, name, ...restField }, index) => (
                                        <div key={key} style={{ display: 'flex', marginBottom: 16, alignItems: 'flex-start' }}>
                                          <div style={{ flex: '0 0 20%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'language']} style={{ marginBottom: 0 }}>
                                              <Input placeholder={t('asset.detail.langPlaceholder')} />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 20%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'version']} style={{ marginBottom: 0 }}>
                                              <Input placeholder={t('asset.detail.langVersionPlaceholder')} />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 20%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'framework']} style={{ marginBottom: 0 }}>
                                              <Input placeholder={t('asset.detail.langFrameworkPlaceholder')} />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 30%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'note']} style={{ marginBottom: 0 }}>
                                              <Input placeholder={t('asset.detail.langNotePlaceholder')} />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 10%', display: 'flex', justifyContent: 'center', paddingTop: 4 }}>
                                            <Space size={8}>
                                              {fields.length > 1 && (
                                                <Tooltip title={t('asset.detail.remove')}>
                                                  <MinusCircleOutlined 
                                                    style={{ color: '#ff4d4f', fontSize: 16, cursor: 'pointer' }} 
                                                    onClick={() => remove(name)} 
                                                  />
                                                </Tooltip>
                                              )}
                                              {index === fields.length - 1 && (
                                                <Tooltip title={t('asset.detail.add')}>
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
                                      {fields.length === 0 && (
                                        <div style={{ marginBottom: 16 }}>
                                          <Button type="dashed" onClick={() => add({ language: '', version: '', framework: '', note: '' })} block icon={<PlusOutlined />}>{t('asset.detail.addLanguage')}</Button>
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
                    </Card>
                  </div>

                  <div style={{ display: overviewStep === 1 ? 'block' : 'none' }}>
                    <Card bordered={false} className="overview-step-card">
                      <Row gutter={24}>
                        <Col span={24}>{renderListFormItem(t, 'servicePortsList', t('asset.detail.servicePorts'), t('asset.detail.servicePortsPlaceholder'), <NodeIndexOutlined style={{ color: '#bfbfbf' }} />, {
                          name: 'accessType',
                          label: t('asset.detail.accessScope'),
                          options: [
                            { label: t('asset.detail.accessAll'), value: 'all' },
                            { label: t('asset.detail.accessLocal'), value: 'local' },
                            { label: t('asset.detail.accessInternal'), value: 'internal' },
                            { label: t('asset.detail.accessSpecificIp'), value: 'specific_ip' },
                          ],
                          dependentField: {
                            name: 'allowedIps',
                            label: t('asset.detail.allowedIp'),
                            placeholder: t('asset.detail.allowedIpPlaceholder'),
                            showWhenValue: 'specific_ip'
                          }
                        }, true)}</Col>
                        <Col span={24}>
                          <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
                            <span style={{ fontSize: 14, fontWeight: 500, color: '#595959' }}>{t('asset.detail.apiEndpoints')}</span>
                            <Tooltip title={t('asset.detail.apiEndpointsTip')}>
                              <InfoCircleOutlined style={{ color: '#8c8c8c' }} />
                            </Tooltip>
                          </div>
                          {renderListFormItem(t, 'apiEndpointsList', t('asset.detail.apiEndpoints'), t('asset.detail.apiEndpointsPlaceholder'), <LinkOutlined style={{ color: '#bfbfbf' }} />, {
                            name: 'authType',
                            label: t('asset.detail.authType'),
                            options: [
                              { label: t('asset.detail.authRequired'), value: 'auth_required' },
                              { label: t('asset.detail.authPublic'), value: 'public' },
                            ]
                          }, true)}
                        </Col>
                      </Row>
                    </Card>
                  </div>

                  <div style={{ display: overviewStep === 2 ? 'block' : 'none' }}>
                    <Card bordered={false} className="overview-step-card">
                      <Row gutter={24}>
                        <Col span={12}><Form.Item name="installPath" label={t('asset.detail.installPath')}><Input placeholder="/opt/app" /></Form.Item></Col>
                        <Col span={12}><Form.Item name="startupCommand" label={t('asset.detail.startupCommand')}><Input placeholder={t('asset.detail.startupCommandPlaceholder')} /></Form.Item></Col>

                        <Col span={8}><Form.Item name="databaseType" label={t('asset.detail.databaseType')}><Input placeholder="MySQL / PostgreSQL / Redis" /></Form.Item></Col>
                        <Col span={16}><Form.Item name="databaseConnection" label={t('asset.detail.databaseConnection')}><Input placeholder={t('asset.detail.databaseConnectionPlaceholder')} /></Form.Item></Col>

                        <Col span={24}>{renderListFormItem(t, 'startupScriptPathList', t('asset.detail.startupScript'), t('asset.detail.startupScriptPlaceholder'), <CodeOutlined style={{ color: '#bfbfbf' }} />)}</Col>
                        <Col span={24}>{renderListFormItem(t, 'configFilePathList', t('asset.detail.configFile'), t('asset.detail.configFilePlaceholder'), <SettingOutlined style={{ color: '#bfbfbf' }} />)}</Col>
                      </Row>
                    </Card>
                  </div>

                  <div style={{ display: overviewStep === 3 ? 'block' : 'none' }}>
                    <Card bordered={false} className="overview-step-card">
                      <Row gutter={24}>
                        <Col span={24}>
                          <Form.Item label={t('asset.detail.credentials')}>
                            <div style={{ border: '1px solid #e8e8e8', borderRadius: 8, overflow: 'hidden' }}>
                              <div style={{ background: '#fafafa', padding: '12px 16px', borderBottom: '1px solid #e8e8e8', display: 'flex', fontWeight: 500, color: '#595959' }}>
                                <div style={{ flex: '0 0 28%', paddingRight: 8 }}>{t('asset.detail.username')}</div>
                                <div style={{ flex: '0 0 28%', paddingRight: 8 }}>{t('asset.detail.password')}</div>
                                <div style={{ flex: '0 0 34%', paddingRight: 8 }}>{t('asset.detail.credentialNote')}</div>
                                <div style={{ flex: '0 0 10%', textAlign: 'center' }}>{t('asset.detail.colAction')}</div>
                              </div>
                              <div style={{ padding: '16px 16px 0' }}>
                                <Form.List name="accountCredentials">
                                  {(fields, { add, remove }) => (
                                    <>
                                      {fields.map(({ key, name, ...restField }, index) => (
                                        <div key={key} style={{ display: 'flex', marginBottom: 16, alignItems: 'flex-start' }}>
                                          <div style={{ flex: '0 0 28%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'username']} style={{ marginBottom: 0 }}>
                                              <Input placeholder={t('asset.detail.usernamePlaceholder')} prefix={<UserOutlined style={{ color: '#bfbfbf' }} />} />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 28%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'password']} style={{ marginBottom: 0 }}>
                                              <Input.Password placeholder={t('asset.detail.passwordPlaceholder')} prefix={<LockOutlined style={{ color: '#bfbfbf' }} />} />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 34%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'usageNote']} style={{ marginBottom: 0 }}>
                                              <Input placeholder={t('asset.detail.credentialNotePlaceholder')} prefix={<TagOutlined style={{ color: '#bfbfbf' }} />} />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 10%', display: 'flex', justifyContent: 'center', paddingTop: 4 }}>
                                            <Space size={8}>
                                              {fields.length > 1 && (
                                                <Tooltip title={t('asset.detail.remove')}>
                                                  <MinusCircleOutlined 
                                                    style={{ color: '#ff4d4f', fontSize: 16, cursor: 'pointer' }} 
                                                    onClick={() => remove(name)} 
                                                  />
                                                </Tooltip>
                                              )}
                                              {index === fields.length - 1 && (
                                                <Tooltip title={t('asset.detail.addAccount')}>
                                                  <PlusCircleOutlined 
                                                    style={{ color: '#1890ff', fontSize: 16, cursor: 'pointer' }} 
                                                    onClick={() => add({ usageNote: '', username: '', password: '' })} 
                                                  />
                                                </Tooltip>
                                              )}
                                            </Space>
                                          </div>
                                        </div>
                                      ))}
                                      {fields.length === 0 && (
                                        <div style={{ marginBottom: 16 }}>
                                          <Button type="dashed" onClick={() => add({ usageNote: '', username: '', password: '' })} block icon={<PlusOutlined />}>{t('asset.detail.addAccount')}</Button>
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
                    </Card>
                  </div>

                  <div style={{ display: overviewStep === 4 ? 'block' : 'none' }}>
                    <Card bordered={false} className="overview-step-card">
                      <Row gutter={24}>
                        <Col span={24}>
                          <Form.Item name="specialNotes" label={t('asset.detail.specialNotes')}><TextArea rows={3} /></Form.Item>
                        </Col>
                      </Row>
                    </Card>
                  </div>

                  <div style={{ marginTop: 40, paddingTop: 24, borderTop: '1px solid #f0f0f0', display: 'flex', gap: 8, alignItems: 'center' }}>
                    <Button disabled={overviewStep === 0} onClick={() => setOverviewStep(overviewStep - 1)}>{t('asset.detail.prevStep')}</Button>
                    <Button disabled={overviewStep === 4} onClick={() => setOverviewStep(overviewStep + 1)}>{t('asset.detail.nextStep')}</Button>
                    <Button type="primary" loading={metadataSubmitting} onClick={handleSubmitMetadata} style={{ marginLeft: 8 }}>
                      {metadataExists ? t('asset.detail.saveChanges') : t('asset.detail.createMetadata')}
                    </Button>
                  </div>
                </Form>
              </Spin>
            </div>
          </TabPane>
          <TabPane tab={<span><TagOutlined />{t('asset.detail.tabTagsPlaceholder')}</span>} key="asset-tags">
            {asset?.id ? <AssetTagsPanel assetId={asset.id} canWrite={canWriteCurrentAsset} /> : null}
          </TabPane>
          <TabPane tab={<span><BuildOutlined />{t('asset.detail.tabEnvironments')}</span>} key="environments">
            <div style={{ padding: '24px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <div>
                  <h3 style={{ margin: 0 }}>{t('asset.detail.envTitle')}</h3>
                  <p style={{ marginTop: 8, marginBottom: 0, color: '#8c8c8c' }}>{t('asset.detail.envDesc')}</p>
                </div>
                <Space>
                  <Button
                    icon={<ReloadOutlined />}
                    loading={envLoading}
                    onClick={() => asset?.id && loadEnvironments(asset.id)}
                  >{t('common.refresh')}</Button>
                  <Tooltip title={canWriteEnv ? '' : ENVIRONMENT_FORBIDDEN_MESSAGE}>
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      disabled={!canWriteEnv}
                      onClick={() => setEnvModalVisible(true)}
                    >{t('asset.detail.addEnv')}</Button>
                  </Tooltip>
                </Space>
              </div>
              <Alert
                style={{ marginBottom: 16 }}
                showIcon
                type={
                  envProvisioner.available === false
                    ? 'warning'
                    : envProvisioner.available === true
                      ? 'success'
                      : 'info'
                }
                message={
                  envProvisioner.available === undefined
                    ? t('asset.detail.envProvisionerUnknown')
                    : envProvisioner.available
                      ? t('asset.detail.envProvisionerAvailable', { type: envProvisioner.type ?? 'docker' })
                      : t('asset.detail.envProvisionerUnavailable', { type: envProvisioner.type ?? 'noop' })
                }
                description={
                  envProvisioner.available === false
                    ? (envProvisioner.message || t('asset.detail.envProvisionerUnavailableDesc'))
                    : (envProvisioner.message || t('asset.detail.envProvisionerAvailableDesc'))
                }
              />
              <Table
                columns={envColumns}
                dataSource={environments}
                rowKey="id"
                loading={envLoading}
                pagination={false}
                locale={{ emptyText: t('asset.detail.envEmpty') }}
              />
            </div>
          </TabPane>
          <TabPane tab={<span><SafetyCertificateOutlined />{t('asset.detail.tabTasks')}</span>} key="tasks">
            <div style={{ padding: '24px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <div>
                  <h3 style={{ margin: 0 }}>{t('asset.detail.tasksTitle')}</h3>
                  <p style={{ marginTop: 8, marginBottom: 0, color: '#8c8c8c' }}>{t('asset.detail.tasksDesc')}</p>
                </div>
                <Space>
                  <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateTask}>{t('asset.detail.newTask')}</Button>
                  <Button onClick={() => navigate('/tasks')}>{t('asset.detail.viewAll')}</Button>
                </Space>
              </div>
              <Table
                columns={taskColumns}
                dataSource={tasks}
                rowKey="id"
                loading={taskLoading}
                pagination={false}
              />
            </div>
          </TabPane>
          <TabPane tab={<span><BugOutlined />{t('asset.detail.tabVulnerabilities')}</span>} key="vulnerabilities">
            <div style={{ padding: '24px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <div>
                  <h3>{t('asset.detail.vulnTitle')}</h3>
                  <p style={{ color: '#8c8c8c' }}>{t('asset.detail.vulnDesc')}</p>
                </div>
                <Space>
                  <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateVuln}>{t('asset.detail.newVuln')}</Button>
                  <Button onClick={() => navigate('/vulnerabilities')}>{t('asset.detail.viewAll')}</Button>
                </Space>
              </div>
              <Table
                columns={vulnColumns}
                dataSource={vulnerabilities}
                rowKey="id"
                loading={vulnLoading}
                pagination={false}
              />
            </div>
          </TabPane>
          <TabPane tab={<span><FileZipOutlined />{t('asset.detail.tabDeliverables')}</span>} key="deliverables">
            <div style={{ padding: '24px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <div>
                  <h3 style={{ marginBottom: 0 }}>{t('asset.detail.deliverablesTitle')}</h3>
                </div>
                <Select
                  style={{ width: 260 }}
                  value={deliverableVersionScope}
                  placeholder={t('asset.detail.deliverableVersionScope')}
                  onChange={(value) => setDeliverableVersionScope(value)}
                  options={[
                    { label: t('asset.detail.showAllVersions'), value: '__all__' },
                    ...assetVersions.map((item) => ({
                      label: `${item.name || `v${item.versionNo}`}${item.isDefault ? t('asset.detail.versionDefaultSuffix') : ''}${item.isLatest ? t('asset.detail.versionLatestSuffix') : ''}`,
                      value: item.id,
                    })),
                  ]}
                />
              </div>
              <div style={{ marginBottom: 24, padding: 16, background: '#fafafa', borderRadius: 8 }}>
                <Form form={deliverableUploadForm} layout="vertical">
                  <Row gutter={16}>
                    <Col span={6}>
                      <Form.Item name="category" label={t('asset.detail.deliverableTypeCol')} rules={[{ required: true, message: t('asset.detail.deliverableTypeRequired') }]}>
                        <Select placeholder={t('asset.detail.deliverableTypePlaceholder')}>
                          <Option value={t('asset.detail.deliverableExploitCode')}>{t('asset.detail.deliverableExploitCode')}</Option>
                          <Option value={t('asset.detail.deliverablePoc')}>{t('asset.detail.deliverablePoc')}</Option>
                          <Option value={t('asset.detail.deliverableExp')}>{t('asset.detail.deliverableExp')}</Option>
                          <Option value={t('asset.detail.deliverableReproDoc')}>{t('asset.detail.deliverableReproDoc')}</Option>
                          <Option value={t('asset.detail.deliverableUsageDoc')}>{t('asset.detail.deliverableUsageDoc')}</Option>
                          <Option value={t('asset.detail.deliverableOther')}>{t('asset.detail.deliverableOther')}</Option>
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item name="description" label={t('asset.detail.deliverableDesc')}>
                        <Input placeholder={t('asset.detail.deliverableDescPlaceholder')} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={24}>
                      <Form.Item label={t('asset.detail.deliverableUpload')}>
                        <Space>
                          <Upload
                            beforeUpload={(file) => {
                              setDeliverableUploadList([file]);
                              return false;
                            }}
                            onRemove={() => setDeliverableUploadList([])}
                            fileList={deliverableUploadList}
                            maxCount={1}
                          >
                            <Button icon={<UploadOutlined />}>{t('asset.detail.chooseFile')}</Button>
                          </Upload>
                          <Button type="primary" loading={deliverableUploading} onClick={() => handleUploadAssetFile('deliverable')} disabled={deliverableUploadList.length === 0}>{t('asset.detail.deliverableSubmit')}</Button>
                        </Space>
                      </Form.Item>
                    </Col>
                  </Row>
                </Form>
              </div>
              <Table
                columns={deliverableColumns}
                dataSource={deliverables}
                rowKey="id"
                loading={deliverableLoading}
                pagination={false}
              />
            </div>
          </TabPane>
        </Tabs>
      </Card>

      <Modal
        title={t('asset.detail.envCreateTitle')}
        open={envModalVisible}
        onOk={handleCreateEnv}
        onCancel={() => {
          setEnvModalVisible(false);
          envForm.resetFields();
        }}
        confirmLoading={envSubmitting}
      >
        <Form form={envForm} layout="vertical">
          <Form.Item name="name" label={t('asset.detail.envColName')} rules={[{ required: true, message: t('asset.detail.envNameRequired') }]}>
            <Input placeholder={t('asset.detail.envNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="template_id" label={t('asset.detail.envTemplate')}>
            <Input placeholder={t('asset.detail.envTemplatePlaceholder')} />
          </Form.Item>
          <Form.Item name="config" label={t('asset.detail.envConfig')}>
            <TextArea rows={4} placeholder='{"replicas": 3, "memory": "2Gi"}' />
          </Form.Item>
        </Form>
      </Modal>

      {/* 环境快照：查看 + 创建（name 本地校验）+ 回滚 */}
      <Modal
        title={snapshotEnv ? t('asset.detail.snapshotTitleEnv', { name: snapshotEnv.name }) : t('asset.detail.snapshotTitle')}
        open={Boolean(snapshotEnv)}
        onCancel={() => {
          setSnapshotEnv(null);
          setSnapshotList([]);
        }}
        footer={null}
        width={720}
        destroyOnHidden
      >
        {canWriteEnv ? (
          <Space.Compact style={{ width: '100%', marginBottom: 12 }}>
            <Input
              value={snapshotName}
              onChange={(event) => setSnapshotName(event.target.value)}
              placeholder={t('asset.detail.snapshotNamePlaceholder')}
              maxLength={100}
              onPressEnter={handleCreateSnapshot}
            />
            <Button type="primary" loading={snapshotSubmitting} onClick={handleCreateSnapshot}>{t('asset.detail.snapshotCreate')}</Button>
          </Space.Compact>
        ) : (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message={ENVIRONMENT_READONLY_HINT}
            description={ENVIRONMENT_FORBIDDEN_MESSAGE}
          />
        )}
        <Spin spinning={snapshotLoading}>
          {snapshotList.length === 0 ? (
            <span style={{ color: '#8c8c8c' }}>
              {snapshotLoading ? t('common.loading') : t('asset.detail.snapshotEmpty')}
            </span>
          ) : (
            <Table
              rowKey={(record: any) => String(record?.id ?? record?.name)}
              size="small"
              pagination={false}
              dataSource={snapshotList}
              columns={[
                { title: t('asset.detail.snapshotColName'), dataIndex: 'name', key: 'name' },
                {
                  title: t('asset.detail.colCreatedAt'),
                  dataIndex: 'createdAt',
                  key: 'createdAt',
                  render: (value: string) => (value ? new Date(value).toLocaleString() : '-'),
                },
                {
                  title: t('asset.detail.colAction'),
                  key: 'action',
                  width: 100,
                  render: (_: any, record: any) =>
                    canWriteEnv ? (
                      <Button
                        type="link"
                        size="small"
                        loading={snapshotRollingId === record.id}
                        onClick={() => handleRollbackSnapshot(record.id)}
                      >{t('asset.detail.snapshotRollback')}</Button>
                    ) : (
                      <span style={{ color: '#bfbfbf' }}>-</span>
                    ),
                },
              ]}
            />
          )}
        </Spin>
      </Modal>

      {/* 漏洞表单弹窗 */}
      <Modal
        title={vulnMode === 'create' ? t('asset.detail.newVuln') : t('asset.detail.vulnModalEdit')}
        open={vulnModalVisible}
        onCancel={() => setVulnModalVisible(false)}
        onOk={handleSubmitVuln}
        confirmLoading={vulnSubmitting}
      >
        <Form form={vulnForm} layout="vertical">
          <Form.Item name="title" label={t('asset.detail.colVulnTitle')} rules={[{ required: true, message: t('vuln.form.titleRequired') }]}>
            <Input placeholder={t('vuln.form.titleRequired')} />
          </Form.Item>
          <Form.Item name="type" label={t('vuln.form.type')} rules={[{ required: true, message: t('vuln.form.typeRequired') }]}>
            <Select placeholder={t('vuln.form.typeRequired')} options={vulnTypeOptions} />
          </Form.Item>
          <Form.Item name="discovered_version_id" label={t('asset.detail.colDiscoveredVersion')} rules={[{ required: true, message: t('vuln.create.versionRequired') }]}>
            <Select placeholder={t('vuln.form.discoveredVersionPlaceholder')}>
              {assetVersions.map((item) => (
                <Option key={item.id} value={item.id}>
                  {item.name || `v${item.versionNo}`}{item.isLatest ? t('asset.detail.versionLatestSuffix') : ''}{item.isDefault ? t('asset.detail.versionDefaultSuffix') : ''}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="affected_versions" label={t('asset.detail.colAffectedVersions')}>
            <Input.TextArea rows={2} placeholder={t('asset.detail.vulnAffectedPlaceholder')} />
          </Form.Item>
          <Form.Item name="severity" label={t('asset.detail.colSeverity')} rules={[{ required: true, message: t('vuln.form.severityRequired') }]}>
            <Select placeholder={t('vuln.form.severityRequired')}>
              <Option value="CRITICAL">{t('vuln.severity.critical')}</Option>
              <Option value="HIGH">{t('vuln.severity.high')}</Option>
              <Option value="MEDIUM">{t('vuln.severity.medium')}</Option>
              <Option value="LOW">{t('vuln.severity.low')}</Option>
              <Option value="INFO">{t('vuln.severity.info')}</Option>
            </Select>
          </Form.Item>
          <Form.Item name="description" label={t('asset.detail.vulnDescLabel')} rules={[{ required: true, message: t('asset.detail.vulnDescRequired') }]}>
            <Input.TextArea rows={4} placeholder={t('asset.detail.vulnDescRequired')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 任务表单弹窗 */}
      <Modal
        title={taskMode === 'create' ? t('asset.detail.newTask') : t('asset.detail.taskModalEdit')}
        open={taskModalVisible}
        onCancel={() => setTaskModalVisible(false)}
        onOk={handleSubmitTask}
        confirmLoading={taskSubmitting}
      >
        <Form form={taskForm} layout="vertical">
          {taskMode === 'edit' && (
            <Form.Item name="title" label={t('asset.detail.colTaskName')} rules={[{ required: true, message: t('asset.detail.taskNameRequired') }]}>
              <Input placeholder={t('asset.detail.taskNameRequired')} />
            </Form.Item>
          )}
          {taskMode === 'create' && (
            <Form.Item name="assignee_id" label={t('asset.detail.colAssignee')} extra={userDirectoryLimitHit ? USER_DIRECTORY_LIMIT_HINT : undefined}>
              <Select placeholder={t('asset.detail.taskAssigneePlaceholder')} allowClear showSearch optionFilterProp="children" onSearch={handleUserSearch} filterOption={false}>
                {taskUsers.map((item) => (
                  <Option key={item.id} value={item.id}>{item.username}</Option>
                ))}
              </Select>
            </Form.Item>
          )}
          <Form.Item name="description" label={t('asset.detail.taskDesc')}>
            <Input.TextArea rows={4} placeholder={t('asset.detail.taskDescPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ===== 申请访问 ===== */}
      <Modal
        title={t('asset.detail.accessModalTitle')}
        open={accessModalVisible}
        onCancel={() => setAccessModalVisible(false)}
        onOk={handleSubmitAccessRequest}
        confirmLoading={accessSubmitting}
        okText={t('asset.detail.accessSubmit')}
      >
        <Form
          form={accessForm}
          layout="vertical"
          initialValues={{ canViewDocuments: false, canViewEnvironments: false, canViewFiles: true }}
        >
          <Form.Item name="canViewDocuments" label={t('asset.detail.accessDocs')}>
            <Select options={[{ value: true, label: t('asset.detail.accessNeed') }, { value: false, label: t('asset.detail.accessNotNeed') }]} />
          </Form.Item>
          <Form.Item name="canViewEnvironments" label={t('asset.detail.accessEnvs')}>
            <Select options={[{ value: true, label: t('asset.detail.accessNeed') }, { value: false, label: t('asset.detail.accessNotNeed') }]} />
          </Form.Item>
          <Form.Item name="canViewFiles" label={t('asset.detail.accessFiles')}>
            <Select options={[{ value: true, label: t('asset.detail.accessNeed') }, { value: false, label: t('asset.detail.accessNotNeed') }]} />
          </Form.Item>
          <Form.Item name="allowedFileTypes" label={t('asset.detail.accessFileTypes')}>
            <Input placeholder={t('asset.detail.accessFileTypesPlaceholder')} />
          </Form.Item>
          <Form.Item name="requestNote" label={t('asset.detail.accessNote')}>
            <TextArea rows={3} placeholder={t('asset.detail.accessNotePlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ===== 认领资产 ===== */}
      <Modal
        title={t('asset.detail.claimModalTitle')}
        open={claimModalVisible}
        onCancel={() => setClaimModalVisible(false)}
        onOk={handleSubmitClaim}
        confirmLoading={claimSubmitting}
        okText={t('asset.detail.claimSubmit')}
      >
        <Form form={claimForm} layout="vertical">
          <Form.Item name="note" label={t('asset.detail.claimNote')}>
            <TextArea rows={3} placeholder={t('asset.detail.claimNotePlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AssetDetail;
