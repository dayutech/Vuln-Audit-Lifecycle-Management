import React, { useCallback, useEffect, useState } from 'react';
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
  InboxOutlined,
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
  uploadToolVersion,
} from '@/api/tool';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { canManageTools, canDeleteTool, TOOL_TAG_FORBIDDEN_MESSAGE, TOOL_TAG_READONLY_HINT } from '@/utils/accessRoles';
import type { ToolVO, ToolVersionVO } from '@/types/tool';
import { formatDateTime } from '@/utils/datetime';
import { saveBlobAsFile } from '@/utils/download';
import MyUploadsModal from '@/components/upload/MyUploadsModal';
import { extractSubmitUpload, submitPendingUpload } from '@/api/upload';
import { extractUploadApprovalPage, listMyUploadsRemote } from '@/api/upload';
import { getApiErrorMessage, getApiErrorStatus, getBlobErrorMessage } from '@/utils/errorMessage';
import { useT, type MessageKey } from '@/i18n';

const { Text } = Typography;
const { Option } = Select;
const { TextArea } = Input;

/** 分类 → i18n 词条（t88：分类展示随语言切换） */
const CATEGORY_KEYS: Record<string, MessageKey> = {
  scanner: 'tools.category.scanner',
  exploit: 'tools.category.exploit',
  script: 'tools.category.script',
  other: 'tools.category.other',
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
  const { t } = useT();
  const [data, setData] = useState<ToolVO[]>([]);
  // t51：PUT /tools/{id} 与上传版本仅 admin/PL/TL；DELETE 仅 admin/PL
  const { profile: toolProfile, loaded: toolProfileLoaded } = useAccessProfile();
  const allowManageTools = toolProfileLoaded && canManageTools(toolProfile);
  const allowDeleteTool = toolProfileLoaded && canDeleteTool(toolProfile);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });

  // 上传工具
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createForm] = Form.useForm();
  const [packageFile, setPackageFile] = useState<File | null>(null);
  /** t161：我的上传（待审批状态）面板 + PENDING 提示 */
  const [myUploadsOpen, setMyUploadsOpen] = useState(false);
  /** t164：改为数组 —— 连续多次 PENDING 上传各自保留一条，互不覆盖（t163 只看到 1 次的问题） */
  const [pendingNotices, setPendingNotices] = useState<Array<{ requestId: string; fileName: string; purgeAfter?: string | null }>>([]);
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

  /**
   * t167：清理历史版本的本地登记键（只删不写）——「我的上传」已改走
   * GET /uploads/my，localStorage 登记整体退役。
   */
  useEffect(() => {
    ['upload.pending.registry.v1', 'owl_my_uploads'].forEach((key) => {
      try {
        window.localStorage.removeItem(key);
      } catch {
        /* 隐私模式下忽略 */
      }
    });
  }, []);

  /**
   * t167：页面挂载/上传后，用**服务端**的 PENDING 请求恢复页面内提示。
   *
   * 这样「清缓存 / 换设备 / 刷新页面」后提示依旧在（t164 靠本地登记，刷新即丢）。
   */
  const restorePendingNotices = useCallback(async () => {
    try {
      const res = await listMyUploadsRemote({ status: 'PENDING', page: 0, size: 20 });
      const parsed = extractUploadApprovalPage(res);
      const restored = parsed.items.map((item) => ({
        requestId: item.id,
        fileName: item.displayName || item.fileName || '-',
        purgeAfter: item.purgeAfter ?? null,
      }));
      setPendingNotices(restored);
    } catch {
      // 提示恢复失败不影响主流程（列表与上传照常）
    }
  }, []);

  useEffect(() => {
    void restorePendingNotices();
  }, [restorePendingNotices]);

  // 版本列表的「创建人」列：后端只返回 createdBy（UUID），这里拉一次用户目录做映射
  useEffect(() => {
    let cancelled = false;
    getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT })
      .then((list) => {
        if (cancelled) return;
        const map: Record<string, string> = {};
        list.forEach((user) => {
          if (user?.id) {
            map[user.id] = user.fullName || user.username || user.id;
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
    /** t164：上传失败时才保留弹窗与已选文件；成功路径必须走完公共收尾（清 fileList + 刷新列表） */
    let failedOnUpload = false;
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
        // t161：大文件统一走 /uploads/pending —— 超门限时后端只写临时区、业务表没有记录，
        // 因此 PENDING 分支**绝不**把工具塞进列表，只提示「等待审核」。
        try {
          const res = await submitPendingUpload({
            file: packageFile,
            fileName: packageFile.name,
            targetType: 'TOOL',
            name: values.name,
            description: values.description,
          });
          const result = extractSubmitUpload(res);
          if (result.status === 'PENDING' && result.requestId) {
            const record = {
              requestId: result.requestId,
              fileName: result.fileName || packageFile.name,
              purgeAfter: result.purgeAfter ?? null,
            };
            // t167：不再写任何本地登记 —— 提示来自**服务端返回**，列表由 `GET /uploads/my` 兜底
            setPendingNotices((prev) => [record, ...prev.filter((item) => item.requestId !== record.requestId)]);
            message.info(t('upload.pendingNoticeTitle'));
            void restorePendingNotices();
          } else {
            message.success(t('feedback.tool.createdWithPackage'));
          }
        } catch (error) {
          // 错误分级：413 超上限 / 400 非法类型 / 403 无权限 / 其它可重试（**不清空表单**）
          const status = getApiErrorStatus(error);
          if (status === 413) message.error(t('upload.tooLarge'));
          else if (status === 400) message.error(t('upload.invalidType'));
          else if (status === 403) message.error(t('upload.forbidden'));
          else message.error(t('upload.uploadFailed'));
          failedOnUpload = true;
        }
      } else {
        message.success(t('feedback.tool.createdWithoutPackage'));
      }

      if (failedOnUpload) {
        // 只有「上传失败」才保留弹窗 + 已选文件，让用户直接重试
        return;
      }
      // 公共收尾：清掉 antd fileList（避免与下一次上传串台）+ 关闭弹窗 + 刷新列表
      setPackageFile(null);
      setIsCreateOpen(false);
      await Promise.resolve(fetchData());
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
      message.success(t('feedback.tool.downloaded', { name: savedName }));
      fetchData();
    } catch (error: any) {
      // 下载走 responseType=blob，错误体同样是 Blob，需要先读文本再取后端 message
      const backendMessage = await getBlobErrorMessage(error, t('tools.downloadFailed'));
      if (error?.response?.status === 404) {
        Modal.info({
          title: t('tools.noPackageTitle'),
          content: (
            <div>
              <p>{t('tools.backendSays', { message: backendMessage })}</p>
              {record.repositoryUrl && (
                <p>
                  {t('tools.repoUrlLabel')}
                  <Typography.Paragraph copyable={{ text: record.repositoryUrl }} code>
                    {record.repositoryUrl}
                  </Typography.Paragraph>
                </p>
              )}
              {record.installCommand && (
                <p>
                  {t('tools.installCommandLabel')}
                  <Typography.Paragraph copyable={{ text: record.installCommand }} code>
                    {record.installCommand}
                  </Typography.Paragraph>
                </p>
              )}
              {!record.repositoryUrl && !record.installCommand && <p>{t('tools.noPackageHint')}</p>}
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
      title: t('tools.deleteTitle'),
      content: t('tools.deleteConfirm', { name: record.name }),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteTool(record.id);
          message.success(t('feedback.tool.deleted'));
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
          message.warning(t('feedback.tool.versionConflictRetry'));
          setIsRetryingVersion(true);
          await new Promise((resolve) => setTimeout(resolve, 600));
          uploaded = await uploadToolVersion(versionTool.id, payload);
          setIsRetryingVersion(false);
        } else {
          throw error;
        }
      }

      const created = uploaded?.data ?? uploaded;
      const label = created?.version || payload.version || t('feedback.tool.versionFallbackLabel');
      const no = created?.versionNo ? t('feedback.tool.versionNoSuffix', { no: created.versionNo }) : '';
      message.success(
        created?.hasFile === false
          ? t('feedback.tool.versionMetaOnly', { version: label, no })
          : t('feedback.tool.versionUploaded', { version: label, no }),
      );
      versionForm.resetFields();
      setVersionFile(null);
      await refreshVersions();
    } catch (error) {
      setVersionError(describeVersionError(error, t('tools.versionUploadFailed'), t));
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
      message.success(t('feedback.tool.downloaded', { name: savedName }));
    } catch (error) {
      const status = getApiErrorStatus(error);
      const backendMessage = await getBlobErrorMessage(error, t('tools.versionDownloadFailed'));
      if (status === 404) {
        setVersionError(
          t('tools.version404Hint', { message: backendMessage }),
        );
      } else if (status === 403) {
        setVersionError(t('tools.version403Hint', { message: backendMessage }));
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
    // t55：POST /tools/{id}/tags 仅 admin/PL/TL；前端先拦一次，避免无谓的 403 请求
    if (!allowManageTools) {
      message.error(TOOL_TAG_FORBIDDEN_MESSAGE);
      return;
    }
    const tag = newTag.trim();
    if (!tag) {
      message.warning(t('feedback.tool.tagRequired'));
      return;
    }
    if (tags.includes(tag)) {
      message.info(t('feedback.tool.tagExists'));
      return;
    }
    try {
      await addToolTag(tagTool.id, tag);
      setTags((prev) => [...prev, tag]);
      setNewTag('');
      message.success(t('feedback.tool.tagAdded', { tag }));
      fetchData();
    } catch (error) {
      // 后端 @PreAuthorize 是唯一权威：403 用角色文案，其余按服务端消息
      message.error(getApiErrorMessage(error, TOOL_TAG_FORBIDDEN_MESSAGE));
    }
  };

  const handleRemoveTag = async (tag: string) => {
    if (!tagTool) return;
    // t55：DELETE /tools/{id}/tags?tag=xxx（query 参数，不是 /{tagId}）仅 admin/PL/TL
    if (!allowManageTools) {
      message.error(TOOL_TAG_FORBIDDEN_MESSAGE);
      return;
    }
    try {
      await removeToolTag(tagTool.id, tag);
      setTags((prev) => prev.filter((item) => item !== tag));
      message.success(t('feedback.tool.tagRemoved', { tag }));
      fetchData();
    } catch (error) {
      message.error(getApiErrorMessage(error, TOOL_TAG_FORBIDDEN_MESSAGE));
    }
  };

  const columns = [
    {
      title: t('tools.colName'),
      key: 'name',
      render: (_: unknown, record: ToolVO) => (
        <Space size={4}>
          <span>{record.name}</span>
          {record.isOfficial && <Tag color="blue">{t('tools.officialTag')}</Tag>}
        </Space>
      ),
    },
    {
      title: t('tools.colCategory'),
      dataIndex: 'category',
      key: 'category',
      render: (category: string) => <Tag color="geekblue">{CATEGORY_KEYS[category] ? t(CATEGORY_KEYS[category]) : category}</Tag>,
    },
    {
      title: t('tools.colTags'),
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
          <Text type="secondary">{t('tools.unset')}</Text>
        );
      },
    },
    {
      title: t('tools.colPackage'),
      key: 'fileName',
      render: (_: unknown, record: ToolVO) =>
        record.fileName ? (
          <TooltipWrapper text={`${record.fileName}（${formatFileSize(record.fileSize)}）`}>
            <Tag color="green">{t('tools.uploadedTag')}</Tag>
          </TooltipWrapper>
        ) : (
          <Tag>{t('tools.externalOnlyTag')}</Tag>
        ),
    },
    {
      title: t('tools.colDownloads'),
      key: 'downloadCount',
      render: (_: unknown, record: ToolVO) => record.downloadCount ?? record.download_count ?? 0,
    },
    {
      title: t('tools.colUploadedAt'),
      key: 'createdAt',
      render: (_: unknown, record: ToolVO) => formatDateTime(record.createdAt ?? record.created_at),
    },
    {
      title: t('tools.colAction'),
      key: 'action',
      render: (_: unknown, record: ToolVO) => (
        <Space size="middle" wrap>
          <a onClick={() => handleDownload(record)}>
            <CloudDownloadOutlined /> {t('tools.actionDownload')}
          </a>
          <a onClick={() => openVersions(record)}>
            <HistoryOutlined /> {t('tools.actionVersions')}
          </a>
          <a onClick={() => openTags(record)}>
            <TagsOutlined /> {t('tools.actionTags')}
          </a>
          {allowDeleteTool && <a style={{ color: 'red' }} onClick={() => handleDelete(record)}>{t('tools.actionDelete')}</a>}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={t('tools.title')}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={fetchData}>{t('common.refresh')}</Button>
          {/* t161：我提交的上传（大文件待审批状态） */}
          <Button icon={<InboxOutlined />} onClick={() => setMyUploadsOpen(true)} data-testid="upload-pending-status-open">
            {t('upload.myUploadsOpen')}
          </Button>
          <Button type="primary" icon={<UploadOutlined />} disabled={!allowManageTools} onClick={openCreate}>{t('tools.upload')}</Button>
        </Space>
      }
      data-testid="tools-page"
    >
      {/* t161/t164：超门限上传的待审提示（PENDING 的记录不会出现在下面的业务列表里）
          用数组渲染 —— 连续多次 PENDING 上传各自一条，直到用户主动关闭或刷新页面 */}
      <div data-testid="upload-pending-notice-list">
        {pendingNotices.map((notice) => (
          <Alert
            key={notice.requestId}
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            data-testid="upload-pending-notice"
            message={t('upload.pendingNoticeTitle')}
            description={
              <span data-testid="upload-pending-notice-file">
                {notice.fileName}
                {' · '}
                {notice.purgeAfter
                  ? t('upload.pendingNoticeBody', { purgeAfter: formatDateTime(notice.purgeAfter, '-') })
                  : t('upload.pendingNoticeBodyNoDate')}
              </span>
            }
            action={
              <Space direction="vertical" size={4}>
                <Button size="small" onClick={() => setMyUploadsOpen(true)} data-testid="upload-pending-notice-open">
                  {t('upload.viewStatus')}
                </Button>
                <Button
                  size="small"
                  type="text"
                  onClick={() =>
                    setPendingNotices((prev) => prev.filter((item) => item.requestId !== notice.requestId))
                  }
                  data-testid={`upload-pending-notice-close-${notice.requestId}`}
                >
                  {t('common.close')}
                </Button>
              </Space>
            }
            closable
            onClose={() =>
              setPendingNotices((prev) => prev.filter((item) => item.requestId !== notice.requestId))
            }
          />
        ))}
      </div>
      <div style={{ marginBottom: 16 }}>
        <Input
          allowClear
          placeholder={t('tools.searchPlaceholder')}
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

      {/* t161：我的上传（大文件待审批状态） */}
      <MyUploadsModal
        open={myUploadsOpen}
        onClose={() => setMyUploadsOpen(false)}
        onChanged={() => void restorePendingNotices()}
      />

      {/* 新建工具 */}
      <Modal
        title={t('tools.createTitle')}
        open={isCreateOpen}
        onOk={handleCreate}
        onCancel={() => setIsCreateOpen(false)}
        confirmLoading={creating}
        okText={t('tools.createOk')}
        width={640}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={t('tools.createHint')}
        />
        <Form form={createForm} layout="vertical">
          <Form.Item name="name" label={t('tools.form.name')} rules={[{ required: true, message: t('tools.form.nameRequired') }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="category" label={t('tools.form.category')} rules={[{ required: true, message: t('tools.form.categoryRequired') }]}>
            <Select placeholder={t('tools.form.categoryPlaceholder')}>
              {Object.entries(CATEGORY_KEYS).map(([value, labelKey]) => (
                <Option key={value} value={value}>{t(labelKey)}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="description" label={t('tools.form.description')}>
            <TextArea rows={3} />
          </Form.Item>
          <Form.Item name="repositoryUrl" label={t('tools.form.repositoryUrl')}>
            <Input placeholder="https://…" />
          </Form.Item>
          <Form.Item name="installCommand" label={t('tools.form.installCommand')}>
            <TextArea rows={2} placeholder={t('tools.form.installPlaceholder')} />
          </Form.Item>
          <Form.Item label={t('tools.form.packageFile')}>
            <Upload
              maxCount={1}
              beforeUpload={(file) => {
                setPackageFile(file);
                return false;
              }}
              onRemove={() => setPackageFile(null)}
              fileList={packageFile ? [{ uid: packageFile.name, name: packageFile.name } as any] : []}
            >
              <Button icon={<UploadOutlined />}>{t('tools.chooseFile')}</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>

      {/* 版本 */}
      <Drawer
        title={<Space><HistoryOutlined />{t('tools.versionsDrawerTitle', { name: versionTool?.name ?? '' })}</Space>}
        open={!!versionTool}
        onClose={() => setVersionTool(null)}
        width={860}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message={t('tools.versionsHint')}
        />

        {versionError && (
          <Alert
            type="error"
            showIcon
            closable
            style={{ marginBottom: 12 }}
            message={t('tools.versionErrorTitle')}
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
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('tools.noVersions')} /> }}
          columns={[
            {
              title: t('tools.colVersionNo'),
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
              title: t('tools.colFileName'),
              key: 'fileName',
              render: (_: unknown, record: ToolVersionVO) =>
                record.fileName ? <Text ellipsis={{ tooltip: record.fileName }}>{record.fileName}</Text> : <Text type="secondary">-</Text>,
            },
            {
              title: t('tools.colFileSize'),
              key: 'fileSize',
              width: 90,
              render: (_: unknown, record: ToolVersionVO) => formatFileSize(record.fileSize),
            },
            {
              title: t('tools.colHasFile'),
              key: 'hasFile',
              width: 110,
              render: (_: unknown, record: ToolVersionVO) =>
                record.hasFile === false ? <Tag>{t('tools.metaOnlyTag')}</Tag> : <Tag color="green">{t('tools.hasFileTag')}</Tag>,
            },
            {
              title: t('tools.colCreatedBy'),
              key: 'createdBy',
              width: 120,
              render: (_: unknown, record: ToolVersionVO) => displayUser(record.createdBy),
            },
            {
              title: t('tools.colCreatedAt'),
              key: 'createdAt',
              width: 160,
              render: (_: unknown, record: ToolVersionVO) => formatDateTime(record.createdAt),
            },
            {
              title: t('tools.colAction'),
              key: 'action',
              width: 90,
              render: (_: unknown, record: ToolVersionVO) =>
                record.hasFile === false ? (
                  <Tooltip title={t('tools.noFileTooltip')}>
                    <Button type="link" size="small" disabled icon={<DownloadOutlined />}>
                      {t('tools.actionDownload')}
                    </Button>
                  </Tooltip>
                ) : (
                  <Button
                    type="link"
                    size="small"
                    icon={<DownloadOutlined />}
                    onClick={() => handleDownloadVersion(record)}
                  >
                    {t('tools.actionDownload')}
                  </Button>
                ),
            },
          ]}
          expandable={{
            expandedRowRender: (record) => (
              <div>
<Text type="secondary">{t('tools.changelogLabel')}</Text>
                {record.changelog ? <span>{record.changelog}</span> : <Text type="secondary">-</Text>}
              </div>
            ),
            rowExpandable: (record) => Boolean(record.changelog),
          }}
        />

        {allowManageTools ? (
        <Card size="small" title={t('tools.uploadVersion')} style={{ marginTop: 16 }}>
          <Form form={versionForm} layout="vertical">
            <Form.Item
              name="version"
              label={t('tools.form.versionNo')}
              extra={t('tools.form.versionNoExtra')}
            >
              <Input placeholder={t('tools.form.versionPlaceholder')} />
            </Form.Item>
            <Form.Item name="changelog" label={t('tools.form.changelog')}>
              <TextArea rows={2} maxLength={500} showCount />
            </Form.Item>
            <Form.Item label={t('tools.form.versionFile')}>
              <Upload
                maxCount={1}
                beforeUpload={(file) => {
                  setVersionFile(file);
                  return false;
                }}
                onRemove={() => setVersionFile(null)}
                fileList={versionFile ? [{ uid: versionFile.name, name: versionFile.name } as any] : []}
              >
                <Button icon={<UploadOutlined />}>{t('tools.chooseFile')}</Button>
              </Upload>
            </Form.Item>
            <Button type="primary" icon={<PlusOutlined />} loading={versionSubmitting} onClick={handleUploadVersion}>
              {isRetryingVersion ? t('tools.retryingVersion') : t('tools.uploadVersion')}
            </Button>
          </Form>
        </Card>
        ) : (
          <Alert
            type="info"
            showIcon
            style={{ marginTop: 16 }}
            message={t('tools.readonlyHint')}
          />
        )}
      </Drawer>

      {/* 标签 */}
      <Drawer
        title={<Space><TagsOutlined />{t('tools.tagsDrawerTitle', { name: tagTool?.name ?? '' })}</Space>}
        open={!!tagTool}
        onClose={() => setTagTool(null)}
        width={420}
        destroyOnClose
      >
        {allowManageTools ? (
          <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
            <Input
              placeholder={t('tools.tagPlaceholder')}
              value={newTag}
              onChange={(event) => setNewTag(event.target.value)}
              onPressEnter={handleAddTag}
            />
            <Button type="primary" onClick={handleAddTag}>{t('tools.addTag')}</Button>
          </Space.Compact>
        ) : (
          <Alert type="info" showIcon style={{ marginBottom: 16 }} message={TOOL_TAG_READONLY_HINT} />
        )}

        {tagLoading ? (
          <Text type="secondary">{t('tools.loading')}</Text>
        ) : tags.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('tools.noTags')} />
        ) : (
          <Space size={[8, 8]} wrap>
            {tags.map((tag) => (
              <Tag key={tag} color="cyan" closable={allowManageTools} onClose={() => handleRemoveTag(tag)}>
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
const describeVersionError = (error: unknown, fallback: string, t: (key: MessageKey, vars?: Record<string, string | number>) => string): string => {
  const status = getApiErrorStatus(error);
  const backendMessage = getApiErrorMessage(error, fallback);
  switch (status) {
    case 400:
      return t('tools.err400', { message: backendMessage });
    case 403:
      return t('tools.err403', { message: backendMessage });
    case 404:
      return t('tools.err404', { message: backendMessage });
    case 409:
      return t('tools.err409', { message: backendMessage });
    default:
      return status ? t('tools.errOther', { message: backendMessage, status }) : backendMessage;
  }
};

export default Tools;








