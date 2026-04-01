import React, { useEffect, useMemo, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Input,
  Tag,
  Card,
  Modal,
  Form,
  Select,
  message,
  Tabs,
  Upload,
  Row,
  Col,
} from 'antd';
import { PlusOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons';
import {
  getAssetList,
  createAsset,
  updateAsset,
  deleteAsset,
  getAssetMetadata,
  createAssetMetadata,
  updateAssetMetadata,
  getAssetFiles,
  uploadAssetFile,
} from '@/api/asset';
import type {
  AssetVO,
  CreateAssetRequest,
  UpdateAssetRequest,
  AssetMetadataVO,
  UpsertAssetMetadataRequest,
  AssetFileVO,
} from '@/types/asset';

const { Option } = Select;
const { TextArea } = Input;

const Assets: React.FC = () => {
  const [data, setData] = useState<AssetVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [currentAsset, setCurrentAsset] = useState<AssetVO | null>(null);
  const [form] = Form.useForm();
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailAsset, setDetailAsset] = useState<AssetVO | null>(null);
  const [metadataForm] = Form.useForm();
  const [uploadForm] = Form.useForm();
  const [metadataExists, setMetadataExists] = useState(false);
  const [metadataLoading, setMetadataLoading] = useState(false);
  const [fileLoading, setFileLoading] = useState(false);
  const [metadataSubmitting, setMetadataSubmitting] = useState(false);
  const [fileUploading, setFileUploading] = useState(false);
  const [files, setFiles] = useState<AssetFileVO[]>([]);
  const [uploadFileList, setUploadFileList] = useState<any[]>([]);
  const [activeTab, setActiveTab] = useState('metadata');

  const unwrapPayload = (res: any) => (res?.data ? res.data : res);

  const metadataInitialValues = useMemo(
    () => ({
      systemArchitecture: '',
      installPath: '',
      startupScriptPath: '',
      configFilePath: '',
      startupCommand: '',
      programmingLanguage: '',
      framework: '',
      databaseType: '',
      databaseConnection: '',
      adminUsername: '',
      adminPasswordEncrypted: '',
      servicePorts: '',
      apiEndpoints: '',
      specialNotes: '',
      changeReason: '',
    }),
    []
  );

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getAssetList(params);
      const payload = unwrapPayload(res);
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

  useEffect(() => {
    fetchData();
  }, [params.page, params.page_size, params.search]);

  const handleCreate = () => {
    setModalMode('create');
    form.resetFields();
    setCurrentAsset(null);
    setIsModalVisible(true);
  };

  const handleEdit = (record: AssetVO) => {
    setModalMode('edit');
    setCurrentAsset(record);
    form.setFieldsValue({
      name: record.name,
      code: record.code,
      target_type: record.target_type,
      target_source: record.target_source,
      description: record.description,
      priority: record.priority,
      status: record.status,
    });
    setIsModalVisible(true);
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该资产吗？此操作不可恢复。',
      onOk: async () => {
        try {
          await deleteAsset(id);
          message.success('删除成功');
          fetchData();
        } catch (error) {
          // Error handled by interceptor
        }
      }
    });
  };

  const openDetail = async (record: AssetVO) => {
    setDetailAsset(record);
    setDetailVisible(true);
    setActiveTab('metadata');
    metadataForm.setFieldsValue(metadataInitialValues);
    uploadForm.resetFields();
    setUploadFileList([]);
    await Promise.all([loadMetadata(record.id), loadFiles(record.id)]);
  };

  const loadMetadata = async (assetId: string) => {
    setMetadataLoading(true);
    try {
      const res = await getAssetMetadata(assetId);
      const payload = unwrapPayload(res);
      if (payload?.success === false) {
        setMetadataExists(false);
        metadataForm.setFieldsValue(metadataInitialValues);
        return;
      }
      const metadata: AssetMetadataVO = payload?.data || payload;
      setMetadataExists(Boolean(metadata?.id));
      metadataForm.setFieldsValue({
        systemArchitecture: metadata.systemArchitecture || '',
        installPath: metadata.installPath || '',
        startupScriptPath: metadata.startupScriptPath || '',
        configFilePath: metadata.configFilePath || '',
        startupCommand: metadata.startupCommand || '',
        programmingLanguage: metadata.programmingLanguage || '',
        framework: metadata.framework || '',
        databaseType: metadata.databaseType || '',
        databaseConnection: metadata.databaseConnection || '',
        adminUsername: metadata.adminUsername || '',
        adminPasswordEncrypted: '',
        servicePorts: metadata.servicePorts || '',
        apiEndpoints: metadata.apiEndpoints || '',
        specialNotes: metadata.specialNotes || '',
        changeReason: '',
      });
    } catch (error) {
      setMetadataExists(false);
      metadataForm.setFieldsValue(metadataInitialValues);
    } finally {
      setMetadataLoading(false);
    }
  };

  const loadFiles = async (assetId: string) => {
    setFileLoading(true);
    try {
      const res = await getAssetFiles(assetId);
      const payload = unwrapPayload(res);
      const list = payload?.data || payload || [];
      setFiles(Array.isArray(list) ? list : []);
    } catch (error) {
      setFiles([]);
    } finally {
      setFileLoading(false);
    }
  };

  const handleSubmitMetadata = async () => {
    if (!detailAsset) return;
    try {
      const values = await metadataForm.validateFields();
      setMetadataSubmitting(true);
      const requestBody: UpsertAssetMetadataRequest = {
        systemArchitecture: values.systemArchitecture || undefined,
        installPath: values.installPath || undefined,
        startupScriptPath: values.startupScriptPath || undefined,
        configFilePath: values.configFilePath || undefined,
        startupCommand: values.startupCommand || undefined,
        programmingLanguage: values.programmingLanguage || undefined,
        framework: values.framework || undefined,
        databaseType: values.databaseType || undefined,
        databaseConnection: values.databaseConnection || undefined,
        adminUsername: values.adminUsername || undefined,
        adminPasswordEncrypted: values.adminPasswordEncrypted || undefined,
        servicePorts: values.servicePorts || undefined,
        apiEndpoints: values.apiEndpoints || undefined,
        specialNotes: values.specialNotes || undefined,
      };
      if (metadataExists) {
        await updateAssetMetadata(detailAsset.id, requestBody, values.changeReason || undefined);
        message.success('资产扩展信息已更新');
      } else {
        await createAssetMetadata(detailAsset.id, requestBody);
        message.success('资产扩展信息已创建');
      }
      await loadMetadata(detailAsset.id);
    } catch (error) {
      return;
    } finally {
      setMetadataSubmitting(false);
    }
  };

  const handleUploadAssetFile = async () => {
    if (!detailAsset) return;
    try {
      const values = await uploadForm.validateFields();
      if (uploadFileList.length === 0) {
        message.error('请选择文件');
        return;
      }
      setFileUploading(true);
      const formData = new FormData();
      formData.append('file', uploadFileList[0].originFileObj);
      formData.append('category', values.category);
      if (values.fileType) formData.append('fileType', values.fileType);
      if (values.description) formData.append('description', values.description);
      await uploadAssetFile(detailAsset.id, formData);
      message.success('文件上传成功');
      uploadForm.resetFields();
      setUploadFileList([]);
      await loadFiles(detailAsset.id);
    } finally {
      setFileUploading(false);
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      if (modalMode === 'create') {
        await createAsset(values as CreateAssetRequest);
        message.success('创建成功');
      } else {
        if (currentAsset) {
          await updateAsset(currentAsset.id, values as UpdateAssetRequest);
          message.success('更新成功');
        }
      }
      setIsModalVisible(false);
      fetchData();
    } catch (error) {
      // Form validation error or API error
    }
  };

  const columns = [
    { title: '资产名称', dataIndex: 'name', key: 'name' },
    { title: '资产编码', dataIndex: 'code', key: 'code' },
    { title: '类型', dataIndex: 'target_type', key: 'target_type' },
    { title: '来源', dataIndex: 'target_source', key: 'target_source' },
    { 
      title: '优先级', 
      dataIndex: 'priority', 
      key: 'priority',
      render: (text: string) => {
        const colors: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue', P3: 'green' };
        return <Tag color={colors[text] || 'default'}>{text}</Tag>;
      }
    },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status',
      render: (text: string) => {
        const colors: Record<string, string> = { active: 'success', inactive: 'default', archived: 'warning' };
        const labels: Record<string, string> = { active: '活跃', inactive: '未活跃', archived: '已归档' };
        return <Tag color={colors[text] || 'default'}>{labels[text] || text}</Tag>;
      }
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: AssetVO) => (
        <Space size="middle">
          <a onClick={() => openDetail(record)}>扩展信息</a>
          <a onClick={() => handleEdit(record)}>编辑</a>
          <a style={{ color: 'red' }} onClick={() => handleDelete(record.id)}>删除</a>
        </Space>
      ),
    },
  ];

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
    { title: '校验值', dataIndex: 'checksum', key: 'checksum', ellipsis: true },
  ];

  return (
    <Card title="资产管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建资产</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder="搜索资产名称/编码" 
          prefix={<SearchOutlined />} 
          style={{ width: 300 }} 
          onPressEnter={(e) => setParams({ ...params, search: e.currentTarget.value, page: 1 })}
        />
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
        title={modalMode === 'create' ? '新建资产' : '编辑资产'}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="资产名称" rules={[{ required: true, message: '请输入资产名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="code" label="资产编码" rules={[{ required: true, message: '请输入资产编码' }]}>
            <Input disabled={modalMode === 'edit'} />
          </Form.Item>
          <Form.Item name="target_type" label="资产类型" rules={[{ required: true, message: '请选择资产类型' }]}>
            <Select disabled={modalMode === 'edit'}>
              <Option value="web">Web应用</Option>
              <Option value="mobile">移动应用</Option>
              <Option value="host">主机系统</Option>
              <Option value="network">网络设备</Option>
            </Select>
          </Form.Item>
          <Form.Item name="target_source" label="资产来源" rules={[{ required: true, message: '请选择资产来源' }]}>
            <Select disabled={modalMode === 'edit'}>
              <Option value="internal">内部自研</Option>
              <Option value="external">外部采购</Option>
              <Option value="customer">客户授权</Option>
            </Select>
          </Form.Item>
          <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请选择优先级' }]}>
            <Select>
              <Option value="P0">P0 - 核心/关键</Option>
              <Option value="P1">P1 - 重要</Option>
              <Option value="P2">P2 - 一般</Option>
              <Option value="P3">P3 - 边缘</Option>
            </Select>
          </Form.Item>
          {modalMode === 'edit' && (
            <Form.Item name="status" label="状态">
              <Select>
                <Option value="active">活跃</Option>
                <Option value="inactive">未活跃</Option>
                <Option value="archived">已归档</Option>
              </Select>
            </Form.Item>
          )}
          <Form.Item name="description" label="描述">
            <TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={detailAsset ? `资产扩展信息：${detailAsset.name}` : '资产扩展信息'}
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={null}
        width={1100}
        destroyOnClose
      >
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'metadata',
              label: '环境与产品信息',
              children: (
                <Form form={metadataForm} layout="vertical">
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item name="systemArchitecture" label="系统架构">
                        <Input placeholder="x86_64 / arm64" />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="installPath" label="安装路径">
                        <Input placeholder="/opt/app" />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item name="startupScriptPath" label="启动脚本路径">
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="configFilePath" label="配置文件路径">
                        <Input />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item name="startupCommand" label="启动命令">
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="programmingLanguage" label="编程语言">
                        <Input placeholder="Java / Go / Python" />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item name="framework" label="框架">
                        <Input placeholder="Spring Boot / Gin / Django" />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="databaseType" label="数据库类型">
                        <Input placeholder="MySQL / PostgreSQL / Redis" />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Form.Item name="databaseConnection" label="数据库连接信息">
                    <Input />
                  </Form.Item>
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item name="adminUsername" label="管理员用户名">
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="adminPasswordEncrypted" label="管理员密码">
                        <Input.Password placeholder={metadataExists ? '留空表示不修改' : ''} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Form.Item name="servicePorts" label="服务端口">
                    <Input placeholder="8080,3306,6379" />
                  </Form.Item>
                  <Form.Item name="apiEndpoints" label="API端点">
                    <TextArea rows={2} placeholder="/api/v1/users,/api/v1/products" />
                  </Form.Item>
                  <Form.Item name="specialNotes" label="特殊说明">
                    <TextArea rows={3} />
                  </Form.Item>
                  <Form.Item name="changeReason" label="修改理由">
                    <Input placeholder="更新原因（更新时建议填写）" />
                  </Form.Item>
                  <Space>
                    <Button type="primary" loading={metadataSubmitting || metadataLoading} onClick={handleSubmitMetadata}>
                      {metadataExists ? '更新扩展信息' : '创建扩展信息'}
                    </Button>
                  </Space>
                </Form>
              ),
            },
            {
              key: 'files',
              label: '安装包与镜像',
              children: (
                <>
                  <Form form={uploadForm} layout="vertical">
                    <Row gutter={16}>
                      <Col span={8}>
                        <Form.Item name="category" label="文件分类" rules={[{ required: true, message: '请选择文件分类' }]}>
                          <Select>
                            <Option value="系统镜像">系统镜像</Option>
                            <Option value="磁盘文件">磁盘文件</Option>
                            <Option value="安装包">安装包</Option>
                            <Option value="提取程序">提取程序</Option>
                            <Option value="配置文件">配置文件</Option>
                            <Option value="其他">其他</Option>
                          </Select>
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item name="fileType" label="文件类型">
                          <Input placeholder="iso/vmdk/zip/exe" />
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item name="description" label="描述">
                          <Input />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Form.Item label="文件">
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
                    <Button type="primary" loading={fileUploading} onClick={handleUploadAssetFile}>
                      上传文件
                    </Button>
                  </Form>
                  <Table
                    style={{ marginTop: 16 }}
                    columns={fileColumns}
                    dataSource={files}
                    rowKey="id"
                    loading={fileLoading}
                    pagination={false}
                  />
                </>
              ),
            },
          ]}
        />
      </Modal>
    </Card>
  );
};

export default Assets;
