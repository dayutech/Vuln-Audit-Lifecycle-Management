import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Tabs, Card, Button, Descriptions, Tag, Spin, Result, Form, Input, Select, Row, Col, Space, Upload, Table, message, Modal, Tooltip, Steps } from 'antd';
import { AppstoreOutlined, BuildOutlined, FileZipOutlined, BugOutlined, SafetyCertificateOutlined, UploadOutlined, PlusOutlined, MinusCircleOutlined, UserOutlined, LockOutlined, TagOutlined, PlusCircleOutlined, CodeOutlined, SettingOutlined, NodeIndexOutlined, LinkOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { 
  getAssetDetail, 
  getAssetVersions,
  createAssetVersion,
  setDefaultAssetVersion,
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
  createEnvironment,
  deleteEnvironment
} from '@/api/asset';
import { getVulnerabilityList, createVulnerability, updateVulnerability, deleteVulnerability } from '@/api/vulnerability';
import { getTaskList, createTask, updateTask, deleteTask } from '@/api/task';
import type { AssetVO, AssetFileVO, AssetDeliverableVO, EnvironmentVO, AssetVersionVO } from '@/types/asset';

const { TabPane } = Tabs;
const { Option } = Select;
const { TextArea } = Input;

const renderListFormItem = (
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
                        <Input placeholder="如：生产环境 / 内网访问" prefix={<TagOutlined style={{ color: '#bfbfbf' }} />} />
                      </Form.Item>
                    </div>
                    <div style={{ flex: '0 0 50px', display: 'flex', justifyContent: 'center', paddingTop: 4 }}>
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

const AssetDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [asset, setAsset] = useState<AssetVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [assetVersions, setAssetVersions] = useState<AssetVersionVO[]>([]);
  const [selectedVersionId, setSelectedVersionId] = useState<string>();
  const [versionLoading, setVersionLoading] = useState(false);
  const [createVersionVisible, setCreateVersionVisible] = useState(false);
  const [creatingVersion, setCreatingVersion] = useState(false);
  const [setDefaultLoading, setSetDefaultLoading] = useState(false);
  const [versionForm] = Form.useForm();

  // Metadata states
  const [metadataForm] = Form.useForm();
  const [metadataExists, setMetadataExists] = useState(false);
  const [metadataLoading, setMetadataLoading] = useState(false);
  const [metadataSubmitting, setMetadataSubmitting] = useState(false);

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

  // Vulnerability states
  const [vulnerabilities, setVulnerabilities] = useState<any[]>([]);
  const [vulnLoading, setVulnLoading] = useState(false);
  const [vulnModalVisible, setVulnModalVisible] = useState(false);
  const [vulnSubmitting, setVulnSubmitting] = useState(false);
  const [vulnForm] = Form.useForm();
  const [vulnMode, setVulnMode] = useState<'create' | 'edit'>('create');
  const [currentVulnId, setCurrentVulnId] = useState<string>('');

  // Task states
  const [tasks, setTasks] = useState<any[]>([]);
  const [taskLoading, setTaskLoading] = useState(false);
  const [taskModalVisible, setTaskModalVisible] = useState(false);
  const [taskSubmitting, setTaskSubmitting] = useState(false);
  const [taskForm] = Form.useForm();
  const [taskMode, setTaskMode] = useState<'create' | 'edit'>('create');
  const [currentTaskId, setCurrentTaskId] = useState<string>('');

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
              usageNote: '管理员登录',
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
      }
    } catch (error) {
      console.error(error);
    } finally {
      setMetadataLoading(false);
    }
  };

  const loadFiles = async (assetId: string, assetVersionId?: string) => {
    try {
      setFileLoading(true);
      const res: any = await getAssetFiles(assetId, assetVersionId);
      const data = res?.data ? res.data : res;
      setFiles(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
    } finally {
      setFileLoading(false);
    }
  };

  const loadDeliverables = async (assetId: string, assetVersionId?: string) => {
    try {
      setDeliverableLoading(true);
      const res: any = await getAssetDeliverables(assetId, assetVersionId);
      const data = res?.data ? res.data : res;
      setDeliverables(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
    } finally {
      setDeliverableLoading(false);
    }
  };

  const loadEnvironments = async (assetId: string, assetVersionId?: string) => {
    try {
      setEnvLoading(true);
      const res: any = await getEnvironmentList({ page: 1, page_size: 100, assetId, assetVersionId });
      const data = res?.data ? res.data : res;
      setEnvironments(Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
    } finally {
      setEnvLoading(false);
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

  const loadTasks = async (assetId: string) => {
    try {
      setTaskLoading(true);
      const res: any = await getTaskList({ page: 1, page_size: 100, reference_id: assetId });
      const data = res?.data ? res.data : res;
      setTasks(Array.isArray(data?.items) ? data.items : Array.isArray(data?.content) ? data.content : Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
    } finally {
      setTaskLoading(false);
    }
  };

  const handleCreateVuln = () => {
    setVulnMode('create');
    vulnForm.resetFields();
    setVulnModalVisible(true);
  };

  const handleEditVuln = (record: any) => {
    setVulnMode('edit');
    setCurrentVulnId(record.id);
    vulnForm.setFieldsValue({
      title: record.title,
      type: record.type,
      severity: record.severity,
      description: record.description,
      remediation: record.remediation,
    });
    setVulnModalVisible(true);
  };

  const handleDeleteVuln = async (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '您确定要删除此漏洞吗？',
      onOk: async () => {
        try {
          await deleteVulnerability(id);
          message.success('删除成功');
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
        await createVulnerability({ ...values, asset_id: asset?.id });
        message.success('漏洞创建成功');
      } else {
        await updateVulnerability(currentVulnId, values);
        message.success('漏洞更新成功');
      }
      setVulnModalVisible(false);
      if (asset?.id) loadVulnerabilities(asset.id);
    } catch (error) {
      console.error(error);
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
      type: record.type,
      priority: record.priority,
      description: record.description,
    });
    setTaskModalVisible(true);
  };

  const handleDeleteTask = async (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '您确定要删除此任务吗？',
      onOk: async () => {
        try {
          await deleteTask(id);
          message.success('删除成功');
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
        await createTask({ ...values, reference_id: asset?.id });
        message.success('任务创建成功');
      } else {
        await updateTask(currentTaskId, values);
        message.success('任务更新成功');
      }
      setTaskModalVisible(false);
      if (asset?.id) loadTasks(asset.id);
    } catch (error) {
      console.error(error);
    } finally {
      setTaskSubmitting(false);
    }
  };

  useEffect(() => {
    const fetchAsset = async () => {
      try {
        setLoading(true);
        const res: any = await getAssetDetail(id as string);
        const found = res?.data ? res.data : res;
        if (found && found.id) {
          const normalizedAsset = normalizeAsset(found);
          setAsset(normalizedAsset);
          loadVersions(found.id);
          loadVulnerabilities(found.id);
          loadTasks(found.id);
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
    if (!asset?.id) return;
    loadFiles(asset.id, selectedVersionId);
    loadDeliverables(asset.id, selectedVersionId);
    loadEnvironments(asset.id, selectedVersionId);
  }, [asset?.id, selectedVersionId]);

  useEffect(() => {
    if (!asset?.id) return;
    loadMetadata(asset.id, stepToModule[overviewStep], selectedVersionId);
  }, [asset?.id, selectedVersionId, overviewStep]);

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

      await updateAssetMetadata(asset.id, currentModule, payload, selectedVersionId);
      message.success('扩展信息更新成功');
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
      }, values.setDefault);
      message.success('资产版本创建成功');
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
      message.warning('请先选择版本');
      return;
    }
    const selected = assetVersions.find((item) => item.id === selectedVersionId);
    if (selected?.isDefault) {
      message.info('当前已是默认版本');
      return;
    }
    try {
      setSetDefaultLoading(true);
      await setDefaultAssetVersion(asset.id, selectedVersionId);
      message.success('默认版本切换成功');
      await loadVersions(asset.id);
    } catch (error) {
      console.error(error);
    } finally {
      setSetDefaultLoading(false);
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
        message.error('请先选择文件');
        return;
      }
      const selectedFile = uploadList[0]?.originFileObj ?? uploadList[0];
      if (!(selectedFile instanceof Blob)) {
        message.error('请选择有效文件后再上传');
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
      if (values.fileType) formData.append('fileType', values.fileType);
      if (values.description) formData.append('description', values.description);
      if (target === 'asset_file') {
        await uploadAssetFile(asset.id, formData, selectedVersionId);
        await loadFiles(asset.id, selectedVersionId);
        message.success('文件上传成功');
      } else {
        await uploadAssetDeliverable(asset.id, formData, selectedVersionId);
        await loadDeliverables(asset.id, selectedVersionId);
        message.success('交付物上传成功');
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
        asset_version_id: selectedVersionId,
        template_id: values.template_id || '',
        config: values.config || '{}',
      });
      message.success('环境创建成功');
      setEnvModalVisible(false);
      envForm.resetFields();
      loadEnvironments(asset.id, selectedVersionId);
    } catch (error) {
      console.error(error);
    } finally {
      setEnvSubmitting(false);
    }
  };

  const handleDeleteEnv = (envId: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该环境吗？此操作不可恢复。',
      onOk: async () => {
        try {
          await deleteEnvironment(envId);
          message.success('环境已删除');
          if (asset) loadEnvironments(asset.id, selectedVersionId);
        } catch (error) {
          console.error(error);
        }
      }
    });
  };

  const handleDeleteDeliverable = (deliverableId: string) => {
    if (!asset) return;
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该交付物吗？删除后将归档。',
      onOk: async () => {
        try {
          await deleteAssetDeliverable(deliverableId);
          message.success('交付物已删除');
          await loadDeliverables(asset.id, selectedVersionId);
        } catch (error) {
          console.error(error);
          message.error('交付物删除失败');
        }
      },
    });
  };

  const handleDeleteAssetFile = (fileId: string) => {
    if (!asset) return;
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该资产文件吗？删除后将归档。',
      onOk: async () => {
        try {
          await deleteAssetFile(fileId);
          message.success('资产文件已删除');
          await loadFiles(asset.id, selectedVersionId);
        } catch (error) {
          console.error(error);
          message.error('资产文件删除失败');
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
      message.error('资产文件下载失败');
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
      message.error('交付物下载失败');
    }
  };

  const fileColumns = [
    { title: '文件名', dataIndex: 'name', key: 'name' },
    { title: '分类', dataIndex: 'category', key: 'category' },
    { title: '类型', dataIndex: 'fileType', key: 'fileType' },
    {
      title: '大小',
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
    { title: '版本', dataIndex: 'version', key: 'version' },
    { title: '上传时间', dataIndex: 'createdAt', key: 'createdAt', render: (val: string) => val ? new Date(val).toLocaleString() : '-' },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: AssetFileVO) => (
        <Space>
          <a onClick={() => handleDownloadAssetFile(record)}>下载</a>
          <a onClick={() => handleDeleteAssetFile(record.id)} style={{ color: 'red' }}>删除</a>
        </Space>
      ),
    },
  ];

  const deliverableColumns = [
    { title: '文件名', dataIndex: 'name', key: 'name' },
    { title: '交付物类型', dataIndex: 'deliverableType', key: 'deliverableType' },
    { title: '类型', dataIndex: 'fileType', key: 'fileType' },
    {
      title: '大小',
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
    { title: '上传时间', dataIndex: 'createdAt', key: 'createdAt', render: (val: string) => val ? new Date(val).toLocaleString() : '-' },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: AssetDeliverableVO) => (
        <Space>
          <a onClick={() => handleDownloadDeliverable(record)}>下载</a>
          <a onClick={() => handleDeleteDeliverable(record.id)} style={{ color: 'red' }}>删除</a>
        </Space>
      ),
    },
  ];

  const envColumns = [
    { title: '环境名称', dataIndex: 'name', key: 'name' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (status: string) => <Tag color={status === 'ACTIVE' ? 'success' : 'default'}>{status}</Tag> },
    { title: '模板ID', dataIndex: 'templateId', key: 'templateId' },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', render: (text: string) => text ? new Date(text).toLocaleString() : '-' },
    { 
      title: '操作', 
      key: 'action',
      render: (_: any, record: EnvironmentVO) => (
        <Space>
          <a onClick={() => handleDeleteEnv(record.id)}>删除</a>
        </Space>
      )
    }
  ];

  const vulnColumns = [
    { title: '漏洞名称', dataIndex: 'title', key: 'title', render: (text: string, record: any) => <a onClick={() => navigate(`/vulnerabilities/${record.id}`)}>{text}</a> },
    { title: '严重程度', dataIndex: 'severity', key: 'severity', render: (severity: string) => {
        const colors: Record<string, string> = { CRITICAL: 'darkred', HIGH: 'red', MEDIUM: 'orange', LOW: 'blue', INFO: 'default' };
        return <Tag color={colors[severity] || 'default'}>{severity}</Tag>;
    } },
    { title: '状态', dataIndex: 'status', key: 'status' },
    { title: '发现时间', dataIndex: 'createdAt', key: 'createdAt', render: (text: string) => text ? new Date(text).toLocaleString() : '-' },
    { 
      title: '操作', 
      key: 'action',
      render: (_: any, record: any) => (
        <Space>
          <a onClick={() => handleEditVuln(record)}>编辑</a>
          <a onClick={() => handleDeleteVuln(record.id)} style={{ color: 'red' }}>删除</a>
        </Space>
      )
    }
  ];

  const taskColumns = [
    { title: '任务名称', dataIndex: 'title', key: 'title', render: (text: string, record: any) => <a onClick={() => navigate(`/tasks/${record.id}`)}>{text}</a> },
    { title: '任务类型', dataIndex: 'type', key: 'type' },
    { title: '优先级', dataIndex: 'priority', key: 'priority' },
    { title: '状态', dataIndex: 'status', key: 'status' },
    { title: '负责人', dataIndex: ['assignee', 'username'], key: 'assignee', render: (text: string) => text || '-' },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', render: (text: string) => text ? new Date(text).toLocaleString() : '-' },
    { 
      title: '操作', 
      key: 'action',
      render: (_: any, record: any) => (
        <Space>
          <a onClick={() => handleEditTask(record)}>编辑</a>
          <a onClick={() => handleDeleteTask(record.id)} style={{ color: 'red' }}>删除</a>
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
        subTitle="抱歉，您访问的资产不存在或已被删除。"
        extra={<Button type="primary" onClick={() => navigate('/assets')}>返回资产列表</Button>}
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
          </div>
        }
        extra={
          <Space>
            <Select
              style={{ width: 220 }}
              value={selectedVersionId}
              loading={versionLoading}
              placeholder="选择资产版本"
              onChange={(value) => setSelectedVersionId(value)}
              options={assetVersions.map((item) => ({
                label: `${item.name || `v${item.versionNo}`}${item.isDefault ? ' - 默认' : ''}`,
                value: item.id,
              }))}
            />
            <Button onClick={() => setCreateVersionVisible(true)}>新建版本</Button>
            <Button loading={setDefaultLoading} onClick={handleSetDefaultVersion}>设为默认</Button>
            <Button onClick={() => navigate('/assets')}>返回列表</Button>
          </Space>
        }
        style={{ marginBottom: 24 }}
      >
        <Descriptions column={3}>
          <Descriptions.Item label="目标类型">{asset?.target_type}</Descriptions.Item>
          <Descriptions.Item label="资产来源">{asset?.target_source}</Descriptions.Item>
          <Descriptions.Item label="所属团队">{asset?.team_name || asset?.team?.name || '-'}</Descriptions.Item>
          <Descriptions.Item label="责任人">{asset?.owner_name || asset?.owner?.username || '-'}</Descriptions.Item>
          <Descriptions.Item label="描述">{asset?.description}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{asset?.created_at ? new Date(asset.created_at).toLocaleString() : '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Modal
        title="新建资产版本"
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
          }}
        >
          <Form.Item name="name" label="版本名称">
            <Input placeholder="留空将自动使用 v{版本号}" />
          </Form.Item>
          <Form.Item name="description" label="版本描述">
            <TextArea rows={3} placeholder="输入本次版本说明" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select>
              <Option value="active">active</Option>
              <Option value="inactive">inactive</Option>
            </Select>
          </Form.Item>
          <Form.Item name="setDefault" label="创建后设为默认">
            <Select>
              <Option value={false}>否</Option>
              <Option value={true}>是</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <Card style={{ borderRadius: 8 }} bodyStyle={{ padding: '0 24px 24px' }}>
        <Tabs defaultActiveKey="overview" size="large">
          <TabPane tab={<span><FileZipOutlined />资产文件</span>} key="asset-files">
            <div style={{ padding: '24px 0' }}>
              <div style={{ marginBottom: 24, padding: 16, background: '#fafafa', borderRadius: 8 }}>
                <Form form={assetFileUploadForm} layout="vertical">
                  <Row gutter={16}>
                    <Col span={6}>
                      <Form.Item name="category" label="文件分类" rules={[{ required: true, message: '请选择文件分类' }]}>
                        <Select placeholder="选择分类">
                          <Option value="系统镜像">系统镜像</Option>
                          <Option value="磁盘文件">磁盘文件</Option>
                          <Option value="安装包">安装包</Option>
                          <Option value="提取程序">提取程序</Option>
                          <Option value="配置文件">配置文件</Option>
                          <Option value="其他">其他</Option>
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={6}>
                      <Form.Item name="fileType" label="文件类型">
                        <Input placeholder="iso/vmdk/zip/exe" />
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item name="description" label="描述">
                        <Input placeholder="输入安装包或配置文件说明" />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={24}>
                      <Form.Item label="文件上传">
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
                            <Button icon={<UploadOutlined />}>选择文件</Button>
                          </Upload>
                          <Button type="primary" loading={fileUploading} onClick={() => handleUploadAssetFile('asset_file')} disabled={assetFileUploadList.length === 0}>
                            开始上传
                          </Button>
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
          <TabPane tab={<span><AppstoreOutlined />产品概览</span>} key="overview">
            <div style={{ padding: '24px 0', maxWidth: 1000 }}>
              <Spin spinning={metadataLoading}>
                <Steps 
                  current={overviewStep} 
                  onChange={setOverviewStep} 
                  style={{ marginBottom: 40 }}
                  items={[
                    { title: '基础与存储' },
                    { title: '网络与接口' },
                    { title: '运行配置' },
                    { title: '认证与权限' },
                    { title: '附加信息' },
                  ]}
                />
                
                <Form form={metadataForm} layout="vertical">
                  <div style={{ display: overviewStep === 0 ? 'block' : 'none' }}>
                    <Card bordered={false} className="overview-step-card">
                      <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 24, paddingBottom: 8, borderBottom: '1px solid #f0f0f0', color: '#1f1f1f' }}>基础与存储信息</div>
                      <Row gutter={24}>
                        <Col span={8}><Form.Item name="runtimePlatform" label="运行平台 (操作系统)"><Input placeholder="如: Windows Server / Linux / Unix" /></Form.Item></Col>
                        <Col span={8}><Form.Item name="deploymentMethod" label="部署方式"><Input placeholder="如: Docker / 物理机 / K8s" /></Form.Item></Col>
                        <Col span={8}><Form.Item name="systemArchitecture" label="系统架构"><Input placeholder="x86 / ARM" /></Form.Item></Col>
                        
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
                                              <Input placeholder="如：Java / Go" />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 20%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'version']} style={{ marginBottom: 0 }}>
                                              <Input placeholder="如：jdk1.8 / 1.21" />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 20%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'framework']} style={{ marginBottom: 0 }}>
                                              <Input placeholder="如：Spring Boot" />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 30%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'note']} style={{ marginBottom: 0 }}>
                                              <Input placeholder="如：核心后端服务" />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 10%', display: 'flex', justifyContent: 'center', paddingTop: 4 }}>
                                            <Space size={8}>
                                              {fields.length > 1 && (
                                                <Tooltip title="移除">
                                                  <MinusCircleOutlined 
                                                    style={{ color: '#ff4d4f', fontSize: 16, cursor: 'pointer' }} 
                                                    onClick={() => remove(name)} 
                                                  />
                                                </Tooltip>
                                              )}
                                              {index === fields.length - 1 && (
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
                                      {fields.length === 0 && (
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
                    </Card>
                  </div>

                  <div style={{ display: overviewStep === 1 ? 'block' : 'none' }}>
                    <Card bordered={false} className="overview-step-card">
                      <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 24, paddingBottom: 8, borderBottom: '1px solid #f0f0f0', color: '#1f1f1f' }}>网络与接口</div>
                      <Row gutter={24}>
                        <Col span={24}>{renderListFormItem('servicePortsList', '服务端口', '如：8080', <NodeIndexOutlined style={{ color: '#bfbfbf' }} />, {
                          name: 'accessType',
                          label: '访问权限',
                          options: [
                            { label: '全部开放', value: 'all' },
                            { label: '仅限本地', value: 'local' },
                            { label: '仅限内网', value: 'internal' },
                            { label: '指定IP', value: 'specific_ip' },
                          ],
                          dependentField: {
                            name: 'allowedIps',
                            label: '允许的IP',
                            placeholder: '输入允许访问的IP或IP段，多个用逗号分隔（如：192.168.1.1, 10.0.0.0/24）',
                            showWhenValue: 'specific_ip'
                          }
                        }, true)}</Col>
                        <Col span={24}>
                          <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
                            <span style={{ fontSize: 16, fontWeight: 600, color: '#1f1f1f' }}>API端点分组管理</span>
                            <Tooltip title="基于授权类型分组管理API端点信息">
                              <InfoCircleOutlined style={{ color: '#8c8c8c' }} />
                            </Tooltip>
                          </div>
                          {renderListFormItem('apiEndpointsList', 'API端点', '如：/api/v1/users', <LinkOutlined style={{ color: '#bfbfbf' }} />, {
                            name: 'authType',
                            label: '授权类型',
                            options: [
                              { label: '需要认证', value: 'auth_required' },
                              { label: '预认证', value: 'public' },
                            ]
                          }, true)}
                        </Col>
                      </Row>
                    </Card>
                  </div>

                  <div style={{ display: overviewStep === 2 ? 'block' : 'none' }}>
                    <Card bordered={false} className="overview-step-card">
                      <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 24, paddingBottom: 8, borderBottom: '1px solid #f0f0f0', color: '#1f1f1f' }}>运行配置</div>
                      <Row gutter={24}>
                        <Col span={12}><Form.Item name="installPath" label="安装路径"><Input placeholder="/opt/app" /></Form.Item></Col>
                        <Col span={12}><Form.Item name="startupCommand" label="启动命令"><Input placeholder="如: ./start.sh" /></Form.Item></Col>

                        <Col span={8}><Form.Item name="databaseType" label="数据库类型"><Input placeholder="MySQL / PostgreSQL / Redis" /></Form.Item></Col>
                        <Col span={16}><Form.Item name="databaseConnection" label="数据库连接信息"><Input placeholder="如: jdbc:mysql://localhost:3306/db" /></Form.Item></Col>

                        <Col span={24}>{renderListFormItem('startupScriptPathList', '启动脚本路径', '如：/opt/app/start.sh', <CodeOutlined style={{ color: '#bfbfbf' }} />)}</Col>
                        <Col span={24}>{renderListFormItem('configFilePathList', '配置文件路径', '如：/etc/app/config.yaml', <SettingOutlined style={{ color: '#bfbfbf' }} />)}</Col>
                      </Row>
                    </Card>
                  </div>

                  <div style={{ display: overviewStep === 3 ? 'block' : 'none' }}>
                    <Card bordered={false} className="overview-step-card">
                      <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 24, paddingBottom: 8, borderBottom: '1px solid #f0f0f0', color: '#1f1f1f' }}>认证与权限</div>
                      <Row gutter={24}>
                        <Col span={24}>
                          <Form.Item label="账号密码组">
                            <div style={{ border: '1px solid #e8e8e8', borderRadius: 8, overflow: 'hidden' }}>
                              <div style={{ background: '#fafafa', padding: '12px 16px', borderBottom: '1px solid #e8e8e8', display: 'flex', fontWeight: 500, color: '#595959' }}>
                                <div style={{ flex: '0 0 28%', paddingRight: 8 }}>用户名</div>
                                <div style={{ flex: '0 0 28%', paddingRight: 8 }}>密码</div>
                                <div style={{ flex: '0 0 34%', paddingRight: 8 }}>账号用途备注</div>
                                <div style={{ flex: '0 0 10%', textAlign: 'center' }}>操作</div>
                              </div>
                              <div style={{ padding: '16px 16px 0' }}>
                                <Form.List name="accountCredentials">
                                  {(fields, { add, remove }) => (
                                    <>
                                      {fields.map(({ key, name, ...restField }, index) => (
                                        <div key={key} style={{ display: 'flex', marginBottom: 16, alignItems: 'flex-start' }}>
                                          <div style={{ flex: '0 0 28%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'username']} style={{ marginBottom: 0 }}>
                                              <Input placeholder="输入用户名" prefix={<UserOutlined style={{ color: '#bfbfbf' }} />} />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 28%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'password']} style={{ marginBottom: 0 }}>
                                              <Input.Password placeholder="留空不修改" prefix={<LockOutlined style={{ color: '#bfbfbf' }} />} />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 34%', paddingRight: 8 }}>
                                            <Form.Item {...restField} name={[name, 'usageNote']} style={{ marginBottom: 0 }}>
                                              <Input placeholder="如：SSH / Web管理" prefix={<TagOutlined style={{ color: '#bfbfbf' }} />} />
                                            </Form.Item>
                                          </div>
                                          <div style={{ flex: '0 0 10%', display: 'flex', justifyContent: 'center', paddingTop: 4 }}>
                                            <Space size={8}>
                                              {fields.length > 1 && (
                                                <Tooltip title="移除">
                                                  <MinusCircleOutlined 
                                                    style={{ color: '#ff4d4f', fontSize: 16, cursor: 'pointer' }} 
                                                    onClick={() => remove(name)} 
                                                  />
                                                </Tooltip>
                                              )}
                                              {index === fields.length - 1 && (
                                                <Tooltip title="添加账号">
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
                                          <Button type="dashed" onClick={() => add({ usageNote: '', username: '', password: '' })} block icon={<PlusOutlined />}>
                                            添加账号
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
                    </Card>
                  </div>

                  <div style={{ display: overviewStep === 4 ? 'block' : 'none' }}>
                    <Card bordered={false} className="overview-step-card">
                      <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 24, paddingBottom: 8, borderBottom: '1px solid #f0f0f0', color: '#1f1f1f' }}>附加信息</div>
                      <Row gutter={24}>
                        <Col span={24}>
                          <Form.Item name="specialNotes" label="特殊说明"><TextArea rows={3} /></Form.Item>
                        </Col>
                      </Row>
                    </Card>
                  </div>

                  <div style={{ marginTop: 40, paddingTop: 24, borderTop: '1px solid #f0f0f0', display: 'flex', gap: 8, alignItems: 'center' }}>
                    <Button disabled={overviewStep === 0} onClick={() => setOverviewStep(overviewStep - 1)}>
                      上一步
                    </Button>
                    <Button disabled={overviewStep === 4} onClick={() => setOverviewStep(overviewStep + 1)}>
                      下一步
                    </Button>
                    <Button type="primary" loading={metadataSubmitting} onClick={handleSubmitMetadata} style={{ marginLeft: 8 }}>
                      {metadataExists ? '保存修改' : '创建扩展信息'}
                    </Button>
                  </div>
                </Form>
              </Spin>
            </div>
          </TabPane>
          <TabPane tab={<span><BuildOutlined />环境与部署</span>} key="environments">
            <div style={{ padding: '24px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <div>
                  <h3>环境管理</h3>
                  <p style={{ color: '#8c8c8c' }}>可以在此处对接云平台或K8s，管理资产的多套部署环境（开发、测试、生产）。</p>
                </div>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => setEnvModalVisible(true)}>添加环境</Button>
              </div>
              <Table
                columns={envColumns}
                dataSource={environments}
                rowKey="id"
                loading={envLoading}
                pagination={false}
              />
            </div>
          </TabPane>
          <TabPane tab={<span><SafetyCertificateOutlined />审计任务</span>} key="tasks">
            <div style={{ padding: '24px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <div>
                  <h3>审计记录</h3>
                  <p style={{ color: '#8c8c8c' }}>展示针对此产品的历史审计任务、安全测试报告等。</p>
                </div>
                <Space>
                  <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateTask}>新建任务</Button>
                  <Button onClick={() => navigate('/tasks')}>查看全部</Button>
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
          <TabPane tab={<span><BugOutlined />漏洞档案</span>} key="vulnerabilities">
            <div style={{ padding: '24px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <div>
                  <h3>关联漏洞</h3>
                  <p style={{ color: '#8c8c8c' }}>展示只针对此产品的安全漏洞。</p>
                </div>
                <Space>
                  <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateVuln}>新建漏洞</Button>
                  <Button onClick={() => navigate('/vulnerabilities')}>查看全部</Button>
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
          <TabPane tab={<span><FileZipOutlined />交付物管理</span>} key="deliverables">
            <div style={{ padding: '24px 0' }}>
              <div style={{ marginBottom: 24, padding: 16, background: '#fafafa', borderRadius: 8 }}>
                <Form form={deliverableUploadForm} layout="vertical">
                  <Row gutter={16}>
                    <Col span={6}>
                      <Form.Item name="category" label="交付物类型" rules={[{ required: true, message: '请选择交付物类型' }]}>
                        <Select placeholder="选择交付物类型">
                          <Option value="漏洞利用代码">漏洞利用代码</Option>
                          <Option value="POC脚本">POC脚本</Option>
                          <Option value="EXP脚本">EXP脚本</Option>
                          <Option value="复现步骤文档">复现步骤文档</Option>
                          <Option value="利用说明文档">利用说明文档</Option>
                          <Option value="其他交付物">其他交付物</Option>
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={6}>
                      <Form.Item name="fileType" label="文件类型">
                        <Input placeholder="py/js/md/txt/zip" />
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item name="description" label="说明">
                        <Input placeholder="输入交付物作用、适用版本与使用方式" />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={24}>
                      <Form.Item label="交付物上传">
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
                            <Button icon={<UploadOutlined />}>选择文件</Button>
                          </Upload>
                          <Button type="primary" loading={deliverableUploading} onClick={() => handleUploadAssetFile('deliverable')} disabled={deliverableUploadList.length === 0}>
                            提交交付物
                          </Button>
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
        title="添加部署环境"
        open={envModalVisible}
        onOk={handleCreateEnv}
        onCancel={() => {
          setEnvModalVisible(false);
          envForm.resetFields();
        }}
        confirmLoading={envSubmitting}
      >
        <Form form={envForm} layout="vertical">
          <Form.Item name="name" label="环境名称" rules={[{ required: true, message: '请输入环境名称' }]}>
            <Input placeholder="如: 开发环境、生产环境、测试环境" />
          </Form.Item>
          <Form.Item name="template_id" label="环境模板">
            <Input placeholder="可选，如: k8s-deployment-template" />
          </Form.Item>
          <Form.Item name="config" label="环境配置 (JSON)">
            <TextArea rows={4} placeholder='{"replicas": 3, "memory": "2Gi"}' />
          </Form.Item>
        </Form>
      </Modal>

      {/* 漏洞表单弹窗 */}
      <Modal
        title={vulnMode === 'create' ? '新建漏洞' : '编辑漏洞'}
        open={vulnModalVisible}
        onCancel={() => setVulnModalVisible(false)}
        onOk={handleSubmitVuln}
        confirmLoading={vulnSubmitting}
      >
        <Form form={vulnForm} layout="vertical">
          <Form.Item name="title" label="漏洞名称" rules={[{ required: true, message: '请输入漏洞名称' }]}>
            <Input placeholder="请输入漏洞名称" />
          </Form.Item>
          <Form.Item name="type" label="漏洞类型" rules={[{ required: true, message: '请选择漏洞类型' }]}>
            <Select placeholder="请选择漏洞类型">
              <Option value="SQL_INJECTION">SQL注入</Option>
              <Option value="XSS">跨站脚本 (XSS)</Option>
              <Option value="CSRF">跨站请求伪造 (CSRF)</Option>
              <Option value="RCE">远程代码执行</Option>
              <Option value="OTHER">其他</Option>
            </Select>
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
          <Form.Item name="remediation" label="修复建议">
            <Input.TextArea rows={3} placeholder="请输入修复建议" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 任务表单弹窗 */}
      <Modal
        title={taskMode === 'create' ? '新建任务' : '编辑任务'}
        open={taskModalVisible}
        onCancel={() => setTaskModalVisible(false)}
        onOk={handleSubmitTask}
        confirmLoading={taskSubmitting}
      >
        <Form form={taskForm} layout="vertical">
          <Form.Item name="title" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="请输入任务名称" />
          </Form.Item>
          <Form.Item name="type" label="任务类型" rules={[{ required: true, message: '请选择任务类型' }]}>
            <Select placeholder="请选择任务类型">
              <Option value="AUDIT">安全审计</Option>
              <Option value="TEST">渗透测试</Option>
              <Option value="REMEDIATION">漏洞修复</Option>
              <Option value="REVIEW">代码审查</Option>
              <Option value="OTHER">其他</Option>
            </Select>
          </Form.Item>
          <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请选择优先级' }]}>
            <Select placeholder="请选择优先级">
              <Option value="P0">P0 - 紧急</Option>
              <Option value="P1">P1 - 高</Option>
              <Option value="P2">P2 - 中</Option>
              <Option value="P3">P3 - 低</Option>
            </Select>
          </Form.Item>
          <Form.Item name="description" label="任务描述">
            <Input.TextArea rows={4} placeholder="请输入任务描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AssetDetail;
