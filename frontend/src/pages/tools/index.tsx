import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
  message,
} from 'antd';
import {
  CloudDownloadOutlined,
  DownloadOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  TagsOutlined,
  UploadOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import {
  addToolTag,
  createTool,
  deleteTool,
  downloadToolFile,
  downloadToolVersion,
  extractList,
  extractStringList,
  getToolList,
  getToolTags,
  getToolVersions,
  removeToolTag,
  uploadToolPackage,
  uploadToolVersion,
} from '@/api/tool';
import { getUserList } from '@/api/user';
import type { ToolVO, ToolVersionVO } from '@/types/tool';
import { formatDateTime } from '@/utils/datetime';
import { saveBlobAsFile } from '@/utils/download';
import { getApiErrorMessage, getApiErrorStatus, getBlobErrorMessage } from '@/utils/errorMessage';

const { Text } = Typography;
const { Option } = Select;
const { TextArea } = Input;

const categoryLabels: Record<string, string> = {
  scanner: '扫描器',
  exploit: '利用工具',
  script: '脚本',
  other: '其他',
};

const formatFileSize = (size?: number): string => {
  if (!size || size <= 0) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
};

const splitTags = (tags?: string): string[] =>
  (tags ?? '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);

/**
 * 工具资源库（/tools）
 *
 * 对接后端：
 * - 列表/创建/删除：GET|POST|DELETE /tools（后端接收 **JSON** Tool 实体）
 * - 工具包：POST /tools/{id}/file 上传，GET /tools/{id}/download 下载真实文件流（并累加下载数）
 * - 版本：GET /tools/{id}/versions、POST /tools/{id}/versions（multipart，version/changelog/file）
 * - 标签：GET|POST|DELETE /tools/{id}/tags?tag=***
 *
 * 注意：未上传工具包的工具下载会返回 404「该工具未上传工具包」，页面会给出说明而不是静默失败。
 */
const Tools: React.FC = () => {
  const [data, setData] = useState<ToolVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });

  // 上传工具
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createForm] = Form.useForm();
  const [packageFile, setPackageFile] = useState<File | null>(null);
  const [creating, setCreating] = useState(false);

  // 版本
  const [versionTool, setVersionTool] = useState<ToolVO | null>(null);
  const [versions, setVersions] = useState<ToolVersionVO[]>([]);
  const [versionLoading, setVersionLoading] = useState(false);
  const [versionForm] = Form.useForm();
  const [versionFile, setVersionFile] = useState<File | null>(null);
  const [versionSubmitting, setVersionSubmitting] = useState(false);
  /** 409 自动重试中的状态（按钮文案提示） */
  const [isRetryingVersion, setIsRetryingVersion] = useState(false);
  /** 版本上传/下载失败的行内提示（按 404/403/409 区分文案） */
  const [versionError, setVersionError] = useState<string | null>(null);

  // 用户 ID → 姓名（版本列表「创建人」列）
  const [userNames, setUserNames] = useState<Record<string, string>>({});

  // 标签
  const [tagTool, setTagTool] = useState<ToolVO | null>(null);
  const [tags, setTags] = useState<string[]>([]);
  const [tagLoading, setTagLoading] = useState(false);
  const [newTag, setNewTag] = useState('');

  const fetchData = async () => {
    setLoading(true);
    try {
      const res: any = await getToolList(params);
      const payload = res?.data ?? res ?? {};
      setData(payload.content || payload.items || []);
      setTotal(payload.totalElements || payload.total || 0);
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [params.page, params.page_size, params.search]);

  // 版本列表的「创建人」列：后端只返回 createdBy（UUID），这里一次性拉用户映射
  useEffect(() => {
    let cancelled = false;
    getUserList({ page: 1, page_size: 200 })
      .then((res: any) => {
        if (cancelled) return;
        const payload = res?.data ?? res ?? {};
        const items = payload.content || payload.items || [];
        const map: Record<string, string> = {};
        items.forEach((user: any) => {
          if (user?.id) {
            map[user.id] = user.fullName || user.full_name || user.username || user.id;
          }
        });
        setUserNames(map);
      })
      .catch(() => {
        if (!cancelled) setUserNames({});
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const displayUser = (userId?: string): string => {
    if (!userId) return '-';
    return userNames[userId] ?? `${userId.slice(0, 8)}…`;
  };

  // ==================== 创建工具 ====================

  const openCreate = () => {
    createForm.resetFields();
    setPackageFile(null);
    setIsCreateOpen(true);
  };

  const handleCreate = async () => {
    let values: any;
    try {
      values = await createForm.validateFields();
    } catch {
      return;
    }
    setCreating(true);
    try {
      const created: any = await createTool({
        name: values.name,
        category: values.category,
        description: values.description,
        repositoryUrl: values.repositoryUrl,
        installCommand: values.installCommand,
        usageExample: values.usageExample,
      });
      const toolId = created?.data?.id ?? created?.id;
      if (packageFile && toolId) {
        try {
          await uploadToolPackage(toolId, packageFile, packageFile.name);
          message.success('工具已创建，工具包上传成功');
        } catch {
          message.warning('工具已创建，但工具包上传失败，可在详情中重新上传');
        }
      } else {
        message.success('工具已创建（未上传工具包，可直接登记仓库地址）');
      }
      setIsCreateOpen(false);
      fetchData();
    } catch {
      // 400/409 由拦截器提示
    } finally {
      setCreating(false);
    }
  };

  // ==================== 下载 ====================

  const handleDownload = async (record: ToolVO) => {
    try {
      const blob = await downloadToolFile(record.id);
      const savedName = saveBlobAsFile(blob as Blob, record.fileName || `${record.name}.bin`);
      message.success(`已下载 ${savedName}`);
      fetchData();
    } catch (error: any) {
      // 下载走 responseType=blob，错误体同样是 Blob，需要先读文本再取后端 message
      const backendMessage = await getBlobErrorMessage(error, '工具包下载失败');
      if (error?.response?.status === 404) {
        Modal.info({
          title: '该工具暂无可下载的工具包',
          content: (
            <div>
              <p>后端返回：{backendMessage}</p>
              {record.repositoryUrl && (
                <p>
                  仓库地址：
                  <Typography.Paragraph copyable={{ text: record.repositoryUrl }} code>
                    {record.repositoryUrl}
                  </Typography.Paragraph>
                </p>
              )}
              {record.installCommand && (
                <p>
                  安装命令：
                  <Typography.Paragraph copyable={{ text: record.installCommand }} code>
                    {record.installCommand}
                  </Typography.Paragraph>
                </p>
              )}
              {!record.repositoryUrl && !record.installCommand && <p>请先为该工具上传工具包或补充仓库地址。</p>}
            </div>
          ),
        });
      } else {
        message.error(backendMessage);
      }
    }
  };

  const handleDelete = (record: ToolVO) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除工具 [${record.name}] 吗？`,
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteTool(record.id);
          message.success('删除成功');
          fetchData();
        } catch {
          // 404 由拦截器提示
        }
      },
    });
  };

  // ==================== 版本 ====================

  const openVersions = async (record: ToolVO) => {
    setVersionTool(record);
    versionForm.resetFields();
    setVersionFile(null);
    setVersions([]);
    setVersionError(null);
    setVersionLoading(true);
    try {
      const res: any = await getToolVersions(record.id);
      setVersions(extractList<ToolVersionVO>(res));
    } catch {
      setVersions([]);
    } finally {
      setVersionLoading(false);
    }
  };

  const handleUploadVersion = async () => {
    if (!versionTool) return;
    let values: any;
    try {
      values = await versionForm.validateFields();
    } catch {
      return;
    }
    setVersionSubmitting(true);
    setVersionError(null);

    const payload = {
      // 留空则由后端按现有最大 versionNo +1 自动生成 vN
      version: values.version?.trim() ? values.version.trim() : undefined,
      changelog: values.changelog?.trim() ? values.changelog.trim() : undefined,
      file: versionFile ?? undefined,
      fileName: versionFile?.name,
    };

    const refreshVersions = async () => {
      const res: any = await getToolVersions(versionTool.id);
      setVersions(extractList<ToolVersionVO>(res));
    };

    try {
      let uploaded: any;
      try {
        uploaded = await uploadToolVersion(versionTool.id, payload);
      } catch (error) {
        // 409 = (tool_id, version_no) 唯一约束冲突（并发上传同一版本号）
        if (getApiErrorStatus(error) === 409) {
          message.warning('版本号冲突（409）：可能有人同时在传该版本，正在自动重试一次…');
          setIsRetryingVersion(true);
          await new Promise((resolve) => setTimeout(resolve, 600));
          uploaded = await uploadToolVersion(versionTool.id, payload);
          setIsRetryingVersion(false);
        } else {
          throw error;
        }
      }

      const created = uploaded?.data ?? uploaded;
      const label = created?.version || payload.version || '新版本';
      const no = created?.versionNo ? `（#${created.versionNo}）` : '';
      message.success(
        created?.hasFile === false
          ? `已创建仅元数据版本 ${label}${no}（未上传文件）`
          : `版本 ${label}${no} 上传成功`,
      );
      versionForm.resetFields();
      setVersionFile(null);
      await refreshVersions();
    } catch (error) {
      setVersionError(describeVersionError(error, '上传版本失败'));
    } finally {
      setIsRetryingVersion(false);
      setVersionSubmitting(false);
    }
  };

  const handleDownloadVersion = async (version: ToolVersionVO) => {
    if (!versionTool) return;
    setVersionError(null);
    try {
      const { blob, fileName } = await downloadToolVersion(versionTool.id, version.id);
      const savedName = saveBlobAsFile(
        blob,
        fileName || version.fileName || `tool-${version.version || version.id}.bin`,
      );
      message.success(`已下载 ${savedName}`);
    } catch (error) {
      const status = getApiErrorStatus(error);
      const backendMessage = await getBlobErrorMessage(error, '版本文件下载失败');
      if (status === 404) {
        setVersionError(
          `${backendMessage}（404：版本不存在或不属于该工具 / 该版本仅有元数据 / 文件已被移除；可刷新版本列表后重试）`,
        );
      } else if (status === 403) {
        setVersionError(`${backendMessage}（403：没有下载该版本文件的权限）`);
      } else {
        setVersionError(backendMessage);
      }
    }
  };

  // ==================== 标签 ====================

  const openTags = async (record: ToolVO) => {
    setTagTool(record);
    setNewTag('');
    setTags(splitTags(record.tags));
    setTagLoading(true);
    try {
      const res: any = await getToolTags(record.id);
      setTags(extractStringList(res));
    } catch {
      // 标签接口异常时退回实体里的 tags 字段
    } finally {
      setTagLoading(false);
    }
  };

  const handleAddTag = async () => {
    if (!tagTool) return;
    const tag = newTag.trim();
    if (!tag) {
      message.warning('请输入标签内容');
      return;
    }
    if (tags.includes(tag)) {
      message.info('该标签已存在');
      return;
    }
    try {
      await addToolTag(tagTool.id, tag);
      setTags((prev) => [...prev, tag]);
      setNewTag('');
      message.success(`已添加标签「${tag}」`);
      fetchData();
    } catch {
      // 拦截器提示
    }
  };

  const handleRemoveTag = async (tag: string) => {
    if (!tagTool) return;
    try {
      await removeToolTag(tagTool.id, tag);
      setTags((prev) => prev.filter((item) => item !== tag));
      message.success(`已移除标签「${tag}」`);
      fetchData();
    } catch {
      // 拦截器提示
    }
  };

  const columns = [
    {
      title: '工具名称',
      key: 'name',
      render: (_: unknown, record: ToolVO) => (
        <Space size={4}>
          <span>{record.name}</span>
          {record.isOfficial && <Tag color="blue">官方</Tag>}
        </Space>
      ),
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      render: (category: string) => <Tag color="geekblue">{categoryLabels[category] ?? category}</Tag>,
    },
    {
      title: '标签',
      key: 'tags',
      render: (_: unknown, record: ToolVO) => {
        const list = splitTags(record.tags);
        return list.length > 0 ? (
          <Space size={[0, 4]} wrap>
            {list.map((tag) => (
              <Tag key={tag} color="cyan">{tag}</Tag>
            ))}
          </Space>
        ) : (
          <Text type="secondary">未设置</Text>
        );
      },
    },
    {
      title: '工具包',
      key: 'fileName',
      render: (_: unknown, record: ToolVO) =>
        record.fileName ? (
          <TooltipWrapper text={`${record.fileName}（${formatFileSize(record.fileSize)}）`}>
            <Tag color="green">已上传</Tag>
          </TooltipWrapper>
        ) : (
          <Tag>仅外链</Tag>
        ),
    },
    {
      title: '下载量',
      key: 'downloadCount',
      render: (_: unknown, record: ToolVO) => record.downloadCount ?? record.download_count ?? 0,
    },
    {
      title: '上传时间',
      key: 'createdAt',
      render: (_: unknown, record: ToolVO) => formatDateTime(record.createdAt ?? record.created_at),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: ToolVO) => (
        <Space size="middle" wrap>
          <a onClick={() => handleDownload(record)}>
            <CloudDownloadOutlined /> 下载
          </a>
          <a onClick={() => openVersions(record)}>
            <HistoryOutlined /> 版本
          </a>
          <a onClick={() => openTags(record)}>
            <TagsOutlined /> 标签
          </a>
          <a style={{ color: 'red' }} onClick={() => handleDelete(record)}>删除</a>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="工具与组件库"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={fetchData}>刷新</Button>
          <Button type="primary" icon={<UploadOutlined />} onClick={openCreate}>上传工具</Button>
        </Space>
      }
    >
      <div style={{ marginBottom: 16 }}>
        <Input
          allowClear
          placeholder="搜索工具名称/描述，回车检索"
          prefix={<SearchOutlined />}
          style={{ width: 320 }}
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          onPressEnter={() => setParams({ ...params, search: keyword, page: 1 })}
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
          showSizeChanger: true,
          onChange: (page, pageSize) => setParams({ ...params, page, page_size: pageSize }),
        }}
      />

      {/* 新建工具 */}
      <Modal
        title="上传工具"
        open={isCreateOpen}
        onOk={handleCreate}
        onCancel={() => setIsCreateOpen(false)}
        confirmLoading={creating}
        okText="创建"
        width={640}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="工具元数据先以 JSON 创建，随后自动把所选文件上传到 /tools/{id}/file；也可只登记仓库地址。"
        />
        <Form form={createForm} layout="vertical">
          <Form.Item name="name" label="工具名称" rules={[{ required: true, message: '请输入工具名称' }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="category" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
            <Select placeholder="请选择分类">
              {Object.entries(categoryLabels).map(([value, label]) => (
                <Option key={value} value={value}>{label}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={3} />
          </Form.Item>
          <Form.Item name="repositoryUrl" label="仓库地址">
            <Input placeholder="https://…" />
          </Form.Item>
          <Form.Item name="installCommand" label="安装命令">
            <TextArea rows={2} placeholder="如 pip install -r requirements.txt" />
          </Form.Item>
          <Form.Item label="工具包文件（可选，≤100MB）">
            <Upload
              maxCount={1}
              beforeUpload={(file) => {
                setPackageFile(file);
                return false;
              }}
              onRemove={() => setPackageFile(null)}
              fileList={packageFile ? [{ uid: packageFile.name, name: packageFile.name } as any] : []}
            >
              <Button icon={<UploadOutlined />}>选择文件</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>

      {/* 版本 */}
      <Drawer
        title={<Space><HistoryOutlined />版本管理 · {versionTool?.name}</Space>}
        open={!!versionTool}
        onClose={() => setVersionTool(null)}
        width={860}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="版本号可留空由后端自动生成 v1/v2/…；不带文件提交会创建「仅元数据」版本（hasFile=false，不可下载）。"
        />

        {versionError && (
          <Alert
            type="error"
            showIcon
            closable
            style={{ marginBottom: 12 }}
            message="版本操作失败"
            description={versionError}
            onClose={() => setVersionError(null)}
          />
        )}

        <Table<ToolVersionVO>
          size="small"
          rowKey="id"
          loading={versionLoading}
          dataSource={versions}
          pagination={false}
          scroll={{ x: 760 }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无版本记录" /> }}
          columns={[
            {
              title: '版本号',
              key: 'version',
              width: 130,
              render: (_: unknown, record: ToolVersionVO) => (
                <Space size={4}>
                  <Text strong>{record.version || '-'}</Text>
                  {typeof record.versionNo === 'number' && <Tag color="blue">#{record.versionNo}</Tag>}
                </Space>
              ),
            },
            {
              title: '文件名',
              key: 'fileName',
              render: (_: unknown, record: ToolVersionVO) =>
                record.fileName ? <Text ellipsis={{ tooltip: record.fileName }}>{record.fileName}</Text> : <Text type="secondary">-</Text>,
            },
            {
              title: '大小',
              key: 'fileSize',
              width: 90,
              render: (_: unknown, record: ToolVersionVO) => formatFileSize(record.fileSize),
            },
            {
              title: '是否有文件',
              key: 'hasFile',
              width: 110,
              render: (_: unknown, record: ToolVersionVO) =>
                record.hasFile === false ? <Tag>仅元数据</Tag> : <Tag color="green">有文件</Tag>,
            },
            {
              title: '创建人',
              key: 'createdBy',
              width: 120,
              render: (_: unknown, record: ToolVersionVO) => displayUser(record.createdBy),
            },
            {
              title: '创建时间',
              key: 'createdAt',
              width: 160,
              render: (_: unknown, record: ToolVersionVO) => formatDateTime(record.createdAt),
            },
            {
              title: '操作',
              key: 'action',
              width: 90,
              render: (_: unknown, record: ToolVersionVO) =>
                record.hasFile === false ? (
                  <Tooltip title="该版本未上传文件">
                    <Button type="link" size="small" disabled icon={<DownloadOutlined />}>
                      下载
                    </Button>
                  </Tooltip>
                ) : (
                  <Button
                    type="link"
                    size="small"
                    icon={<DownloadOutlined />}
                    onClick={() => handleDownloadVersion(record)}
                  >
                    下载
                  </Button>
                ),
            },
          ]}
          expandable={{
            expandedRowRender: (record) => (
              <div>
                <Text type="secondary">变更说明：</Text>
                {record.changelog ? <span>{record.changelog}</span> : <Text type="secondary">-</Text>}
              </div>
            ),
            rowExpandable: (record) => Boolean(record.changelog),
          }}
        />

        <Card size="small" title="上传新版本" style={{ marginTop: 16 }}>
          <Form form={versionForm} layout="vertical">
            <Form.Item
              name="version"
              label="版本号"
              extra="留空则由后端自动生成 vN（v1、v2…）"
            >
              <Input placeholder="如 1.2.0（可留空）" />
            </Form.Item>
            <Form.Item name="changelog" label="变更说明">
              <TextArea rows={2} maxLength={500} showCount />
            </Form.Item>
            <Form.Item label="版本文件（可选）">
              <Upload
                maxCount={1}
                beforeUpload={(file) => {
                  setVersionFile(file);
                  return false;
                }}
                onRemove={() => setVersionFile(null)}
                fileList={versionFile ? [{ uid: versionFile.name, name: versionFile.name } as any] : []}
              >
                <Button icon={<UploadOutlined />}>选择文件</Button>
              </Upload>
            </Form.Item>
            <Button type="primary" icon={<PlusOutlined />} loading={versionSubmitting} onClick={handleUploadVersion}>
              {isRetryingVersion ? '版本号冲突，自动重试中…' : '上传版本'}
            </Button>
          </Form>
        </Card>
      </Drawer>

      {/* 标签 */}
      <Drawer
        title={<Space><TagsOutlined />标签管理 · {tagTool?.name}</Space>}
        open={!!tagTool}
        onClose={() => setTagTool(null)}
        width={420}
        destroyOnClose
      >
        <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
          <Input
            placeholder="输入标签，如 信息收集"
            value={newTag}
            onChange={(event) => setNewTag(event.target.value)}
            onPressEnter={handleAddTag}
          />
          <Button type="primary" onClick={handleAddTag}>添加</Button>
        </Space.Compact>

        {tagLoading ? (
          <Text type="secondary">加载中…</Text>
        ) : tags.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该工具暂无标签" />
        ) : (
          <Space size={[8, 8]} wrap>
            {tags.map((tag) => (
              <Tag key={tag} color="cyan" closable onClose={() => handleRemoveTag(tag)}>
                {tag}
              </Tag>
            ))}
          </Space>
        )}
      </Drawer>
    </Card>
  );
};

/** 简单包装：内容 + tooltip */
const TooltipWrapper: React.FC<{ text: string; children: React.ReactNode }> = ({ text, children }) => (
  <span title={text}>{children}</span>
);

/**
 * 版本上传失败的行内文案：按状态码区分（403 / 404 / 409 / 400），
 * 不再统一成「操作失败」；后端 message 原样保留在前面。
 */
const describeVersionError = (error: unknown, fallback: string): string => {
  const status = getApiErrorStatus(error);
  const backendMessage = getApiErrorMessage(error, fallback);
  switch (status) {
    case 400:
      return `${backendMessage}（400：请求参数不合法，请检查版本号/文件后重试）`;
    case 403:
      return `${backendMessage}（403：没有为该工具上传版本的权限）`;
    case 404:
      return `${backendMessage}（404：工具不存在，可能已被删除，请刷新列表后重试）`;
    case 409:
      return `${backendMessage}（409：版本号冲突，自动重试后仍失败，请更换版本号或留空由后端自动生成）`;
    default:
      return status ? `${backendMessage}（HTTP ${status}）` : backendMessage;
  }
};

export default Tools;
