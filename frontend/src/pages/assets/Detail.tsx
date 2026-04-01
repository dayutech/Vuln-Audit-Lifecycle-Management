import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Tabs, Card, Button, Descriptions, Tag, Spin, Result, Form, Input, Select, Row, Col, Space, Upload, Table, message, Modal } from 'antd';
import { AppstoreOutlined, BuildOutlined, FileZipOutlined, BugOutlined, SafetyCertificateOutlined, UploadOutlined, PlusOutlined } from '@ant-design/icons';
import { 
  getAssetList, 
  getAssetMetadata, 
  createAssetMetadata, 
  updateAssetMetadata, 
  getAssetFiles, 
  uploadAssetFile,
  getEnvironmentList,
  createEnvironment,
  deleteEnvironment
} from '@/api/asset';
import { getVulnerabilityList } from '@/api/vulnerability';
import { getTaskList } from '@/api/task';
import type { AssetVO, AssetFileVO, UpsertAssetMetadataRequest, EnvironmentVO } from '@/types/asset';

const { TabPane } = Tabs;
const { Option } = Select;
const { TextArea } = Input;

const AssetDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [asset, setAsset] = useState<AssetVO | null>(null);
  const [loading, setLoading] = useState(true);

  // Metadata states
  const [metadataForm] = Form.useForm();
  const [metadataExists, setMetadataExists] = useState(false);
  const [metadataLoading, setMetadataLoading] = useState(false);
  const [metadataSubmitting, setMetadataSubmitting] = useState(false);

  // File states
  const [uploadForm] = Form.useForm();
  const [files, setFiles] = useState<AssetFileVO[]>([]);
  const [fileLoading, setFileLoading] = useState(false);
  const [fileUploading, setFileUploading] = useState(false);
  const [uploadFileList, setUploadFileList] = useState<any[]>([]);

  // Environment states
  const [environments, setEnvironments] = useState<EnvironmentVO[]>([]);
  const [envLoading, setEnvLoading] = useState(false);
  const [envModalVisible, setEnvModalVisible] = useState(false);
  const [envForm] = Form.useForm();
  const [envSubmitting, setEnvSubmitting] = useState(false);

  // Vulnerability states
  const [vulnerabilities, setVulnerabilities] = useState<any[]>([]);
  const [vulnLoading, setVulnLoading] = useState(false);

  // Task states
  const [tasks, setTasks] = useState<any[]>([]);
  const [taskLoading, setTaskLoading] = useState(false);

  const metadataInitialValues = useMemo(() => ({
    systemArchitecture: '', installPath: '', startupScriptPath: '', configFilePath: '',
    startupCommand: '', programmingLanguage: '', framework: '', databaseType: '',
    databaseConnection: '', adminUsername: '', adminPasswordEncrypted: '',
    servicePorts: '', apiEndpoints: '', specialNotes: '', changeReason: '',
  }), []);

  const loadMetadata = async (assetId: string) => {
    try {
      setMetadataLoading(true);
      const res: any = await getAssetMetadata(assetId);
      const data = res?.data ? res.data : res;
      if (data && data.id) {
        setMetadataExists(true);
        metadataForm.setFieldsValue(data);
      } else {
        setMetadataExists(false);
        metadataForm.setFieldsValue(metadataInitialValues);
      }
    } catch (error) {
      setMetadataExists(false);
      metadataForm.setFieldsValue(metadataInitialValues);
    } finally {
      setMetadataLoading(false);
    }
  };

  const loadFiles = async (assetId: string) => {
    try {
      setFileLoading(true);
      const res: any = await getAssetFiles(assetId);
      const data = res?.data ? res.data : res;
      setFiles(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
    } finally {
      setFileLoading(false);
    }
  };

  const loadEnvironments = async (assetId: string) => {
    try {
      setEnvLoading(true);
      const res: any = await getEnvironmentList({ page: 1, page_size: 100, assetId });
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
      setVulnerabilities(Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : []);
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
      setTasks(Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
    } finally {
      setTaskLoading(false);
    }
  };

  useEffect(() => {
    const fetchAsset = async () => {
      try {
        setLoading(true);
        const res = await getAssetList({ search: id });
        const payload = res?.data ? res.data : res;
        const items = payload.content || payload.items || [];
        const found = items.find((item: AssetVO) => item.id === id);
        if (found) {
          setAsset(found);
          loadMetadata(found.id);
          loadFiles(found.id);
          loadEnvironments(found.id);
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

  const handleSubmitMetadata = async () => {
    if (!asset) return;
    try {
      const values = await metadataForm.validateFields();
      setMetadataSubmitting(true);
      if (metadataExists) {
        await updateAssetMetadata(asset.id, values as UpsertAssetMetadataRequest);
        message.success('扩展信息更新成功');
      } else {
        await createAssetMetadata(asset.id, values as UpsertAssetMetadataRequest);
        message.success('扩展信息创建成功');
        setMetadataExists(true);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setMetadataSubmitting(false);
    }
  };

  const handleUploadAssetFile = async () => {
    if (!asset) return;
    try {
      const values = await uploadForm.validateFields();
      if (uploadFileList.length === 0) {
        message.error('请先选择文件');
        return;
      }
      setFileUploading(true);
      const formData = new FormData();
      formData.append('file', uploadFileList[0].originFileObj);
      formData.append('category', values.category);
      if (values.fileType) formData.append('fileType', values.fileType);
      if (values.description) formData.append('description', values.description);
      await uploadAssetFile(asset.id, formData);
      message.success('文件上传成功');
      uploadForm.resetFields();
      setUploadFileList([]);
      await loadFiles(asset.id);
    } finally {
      setFileUploading(false);
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
      message.success('环境创建成功');
      setEnvModalVisible(false);
      envForm.resetFields();
      loadEnvironments(asset.id);
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
          if (asset) loadEnvironments(asset.id);
        } catch (error) {
          console.error(error);
        }
      }
    });
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
  ];

  const taskColumns = [
    { title: '任务名称', dataIndex: 'title', key: 'title', render: (text: string, record: any) => <a onClick={() => navigate(`/tasks/${record.id}`)}>{text}</a> },
    { title: '任务类型', dataIndex: 'type', key: 'type' },
    { title: '优先级', dataIndex: 'priority', key: 'priority' },
    { title: '状态', dataIndex: 'status', key: 'status' },
    { title: '负责人', dataIndex: ['assignee', 'username'], key: 'assignee', render: (text: string) => text || '-' },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', render: (text: string) => text ? new Date(text).toLocaleString() : '-' },
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
        extra={<Button onClick={() => navigate('/assets')}>返回列表</Button>}
        style={{ marginBottom: 24 }}
      >
        <Descriptions column={3}>
          <Descriptions.Item label="资产类型">{asset.target_type}</Descriptions.Item>
          <Descriptions.Item label="来源">{asset.target_source}</Descriptions.Item>
          <Descriptions.Item label="优先级">{asset.priority}</Descriptions.Item>
          <Descriptions.Item label="负责人">{asset.owner?.username || '-'}</Descriptions.Item>
          <Descriptions.Item label="所属团队">{asset.team?.name || '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{new Date(asset.created_at).toLocaleString()}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card style={{ borderRadius: 8 }} bodyStyle={{ padding: '0 24px 24px' }}>
        <Tabs defaultActiveKey="overview" size="large">
          <TabPane tab={<span><AppstoreOutlined />产品概览</span>} key="overview">
            <div style={{ padding: '24px 0', maxWidth: 900 }}>
              <Spin spinning={metadataLoading}>
                <Form form={metadataForm} layout="vertical">
                  <Row gutter={16}>
                    <Col span={12}><Form.Item name="systemArchitecture" label="系统架构"><Input placeholder="x86 / ARM" /></Form.Item></Col>
                    <Col span={12}><Form.Item name="installPath" label="安装路径"><Input placeholder="/opt/app" /></Form.Item></Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={12}><Form.Item name="startupScriptPath" label="启动脚本路径"><Input /></Form.Item></Col>
                    <Col span={12}><Form.Item name="configFilePath" label="配置文件路径"><Input /></Form.Item></Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={12}><Form.Item name="startupCommand" label="启动命令"><Input /></Form.Item></Col>
                    <Col span={12}><Form.Item name="programmingLanguage" label="编程语言"><Input placeholder="Java / Go / Python" /></Form.Item></Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={12}><Form.Item name="framework" label="框架"><Input placeholder="Spring Boot / Gin / Django" /></Form.Item></Col>
                    <Col span={12}><Form.Item name="databaseType" label="数据库类型"><Input placeholder="MySQL / PostgreSQL / Redis" /></Form.Item></Col>
                  </Row>
                  <Form.Item name="databaseConnection" label="数据库连接信息"><Input /></Form.Item>
                  <Row gutter={16}>
                    <Col span={12}><Form.Item name="adminUsername" label="管理员用户名"><Input /></Form.Item></Col>
                    <Col span={12}><Form.Item name="adminPasswordEncrypted" label="管理员密码"><Input.Password placeholder={metadataExists ? '留空表示不修改' : ''} /></Form.Item></Col>
                  </Row>
                  <Form.Item name="servicePorts" label="服务端口"><Input placeholder="8080,3306,6379" /></Form.Item>
                  <Form.Item name="apiEndpoints" label="API端点"><TextArea rows={2} placeholder="/api/v1/users" /></Form.Item>
                  <Form.Item name="specialNotes" label="特殊说明"><TextArea rows={3} /></Form.Item>
                  <Space>
                    <Button type="primary" loading={metadataSubmitting} onClick={handleSubmitMetadata}>
                      {metadataExists ? '保存修改' : '创建扩展信息'}
                    </Button>
                  </Space>
                </Form>
              </Spin>
            </div>
          </TabPane>
          <TabPane tab={<span><FileZipOutlined />交付物管理</span>} key="artifacts">
            <div style={{ padding: '24px 0' }}>
              <div style={{ marginBottom: 24, padding: 16, background: '#fafafa', borderRadius: 8 }}>
                <Form form={uploadForm} layout="vertical">
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
                        <Input placeholder="输入版本或更新说明" />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={24}>
                      <Form.Item label="文件上传">
                        <Upload
                          beforeUpload={(file) => {
                            setUploadFileList([file]);
                            return false;
                          }}
                          onRemove={() => setUploadFileList([])}
                          fileList={uploadFileList}
                          maxCount={1}
                        >
                          <Button icon={<UploadOutlined />}>选择文件</Button>
                        </Upload>
                      </Form.Item>
                    </Col>
                  </Row>
                  <Button type="primary" loading={fileUploading} onClick={handleUploadAssetFile}>
                    开始上传
                  </Button>
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
          <TabPane tab={<span><BugOutlined />漏洞档案</span>} key="vulnerabilities">
            <div style={{ padding: '24px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <div>
                  <h3>关联漏洞</h3>
                  <p style={{ color: '#8c8c8c' }}>展示只针对此产品的安全漏洞。</p>
                </div>
                <Button onClick={() => navigate('/vulnerabilities')}>查看全部</Button>
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
          <TabPane tab={<span><SafetyCertificateOutlined />审计任务</span>} key="tasks">
            <div style={{ padding: '24px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <div>
                  <h3>审计记录</h3>
                  <p style={{ color: '#8c8c8c' }}>展示针对此产品的历史审计任务、安全测试报告等。</p>
                </div>
                <Button onClick={() => navigate('/tasks')}>查看全部</Button>
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
    </div>
  );
};

export default AssetDetail;
