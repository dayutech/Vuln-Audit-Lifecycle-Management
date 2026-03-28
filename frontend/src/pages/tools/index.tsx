import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Card, Input, Modal, Form, Select, Upload, message, Tag } from 'antd';
import { SearchOutlined, UploadOutlined, DownloadOutlined } from '@ant-design/icons';
import { getToolList, uploadTool, downloadTool } from '@/api/tool';
import type { ToolVO } from '@/types/tool';

const { Option } = Select;
const { TextArea } = Input;

const Tools: React.FC = () => {
  const [data, setData] = useState<ToolVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });

  // Modal states
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [fileList, setFileList] = useState<any[]>([]);
  const [form] = Form.useForm();
  const [uploading, setUploading] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getToolList(params);
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

  useEffect(() => {
    fetchData();
  }, [params.page, params.page_size, params.search]);

  const handleUploadClick = () => {
    form.resetFields();
    setFileList([]);
    setIsModalVisible(true);
  };

  const handleDownload = async (record: ToolVO) => {
    try {
      const blob = await downloadTool(record.id);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', record.name);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      message.error('下载失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      if (fileList.length === 0) {
        message.error('请选择要上传的文件');
        return;
      }

      setUploading(true);
      const formData = new FormData();
      formData.append('file', fileList[0].originFileObj);
      formData.append('name', values.name);
      formData.append('category', values.category);
      if (values.description) formData.append('description', values.description);
      if (values.requirements) formData.append('requirements', values.requirements);

      await uploadTool(formData);
      message.success('上传成功');
      setIsModalVisible(false);
      fetchData();
    } catch (error) {
      // Form validation error or API error
    } finally {
      setUploading(false);
    }
  };

  const uploadProps = {
    onRemove: (file: any) => {
      setFileList((prev) => prev.filter((item) => item.uid !== file.uid));
    },
    beforeUpload: (file: any) => {
      setFileList([file]);
      return false; // Prevent automatic upload
    },
    fileList,
  };

  const columns = [
    { title: '工具名称', dataIndex: 'name', key: 'name' },
    { 
      title: '分类', 
      dataIndex: 'category', 
      key: 'category',
      render: (text: string) => <Tag color="blue">{text}</Tag>
    },
    { title: '描述', dataIndex: 'description', key: 'description' },
    { title: '上传时间', dataIndex: 'created_at', key: 'created_at', render: (text: string) => new Date(text).toLocaleString() },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: ToolVO) => (
        <Space size="middle">
          <a onClick={() => handleDownload(record)}><DownloadOutlined /> 下载</a>
          <a>详情</a>
        </Space>
      ),
    },
  ];

  return (
    <Card title="工具资源库" extra={<Button type="primary" icon={<UploadOutlined />} onClick={handleUploadClick}>上传工具</Button>}>
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder="搜索工具名称/描述" 
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
        title="上传工具"
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        confirmLoading={uploading}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item label="工具文件" required>
            <Upload {...uploadProps} maxCount={1}>
              <Button icon={<UploadOutlined />}>选择文件</Button>
            </Upload>
          </Form.Item>
          <Form.Item name="name" label="工具名称" rules={[{ required: true, message: '请输入工具名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="category" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
            <Select>
              <Option value="scanner">扫描器</Option>
              <Option value="exploit">利用工具</Option>
              <Option value="script">脚本</Option>
              <Option value="other">其他</Option>
            </Select>
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={3} />
          </Form.Item>
          <Form.Item name="requirements" label="运行环境要求">
            <TextArea rows={2} placeholder="例如: Python 3.8+, Java 11" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Tools;