import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, PushpinFilled, ReloadOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import {
  createAnnouncement,
  deleteAnnouncement,
  getActiveAnnouncements,
  getAnnouncementList,
  getServerNow,
  updateAnnouncement,
} from '@/api/notification';
import {
  ANNOUNCEMENT_FORBIDDEN_MESSAGE,
  ANNOUNCEMENT_READONLY_HINT,
  canManageAnnouncements,
  getMyAccessProfile,
} from '@/api/permission';
import type { AnnouncementPage, AnnouncementSaveRequest, AnnouncementVO } from '@/types/notification';
import { ANNOUNCEMENT_PRIORITY_META } from '@/types/notification';
import { formatDateTime } from '@/utils/datetime';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

const PAGE_SIZE = 10;
const DATETIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss';

type AnnouncementStatus = 'active' | 'scheduled' | 'expired';

interface AnnouncementFormValues {
  title: string;
  content: string;
  priority: string;
  publishedAt: Dayjs;
  expiresAt?: Dayjs | null;
}

const priorityMeta = (priority?: string | null) =>
  ANNOUNCEMENT_PRIORITY_META[(priority ?? 'normal').toLowerCase()] ?? ANNOUNCEMENT_PRIORITY_META.normal;

const resolveStatus = (item: AnnouncementVO, now: Dayjs): AnnouncementStatus => {
  const published = item.publishedAt ? dayjs(item.publishedAt) : null;
  if (published && published.isAfter(now)) return 'scheduled';
  if (item.expiresAt && dayjs(item.expiresAt).isBefore(now)) return 'expired';
  return 'active';
};

const STATUS_META: Record<AnnouncementStatus, { color: string; label: string }> = {
  active: { color: 'success', label: '生效中' },
  scheduled: { color: 'processing', label: '未生效（待发布时间）' },
  expired: { color: 'default', label: '已过期' },
};

const AnnouncementsTab: React.FC = () => {
  const [form] = Form.useForm<AnnouncementFormValues>();
  const [activeList, setActiveList] = useState<AnnouncementVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageData, setPageData] = useState<AnnouncementPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [activeLoading, setActiveLoading] = useState(false);
  const [serverNow, setServerNow] = useState<Dayjs>(dayjs());
  const [canManage, setCanManage] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AnnouncementVO | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [schemaMissingPublishedAt, setSchemaMissingPublishedAt] = useState(false);

  const loadServerClock = useCallback(async () => {
    const now = await getServerNow();
    setServerNow(dayjs(now));
    return dayjs(now);
  }, []);

  const loadActive = useCallback(async () => {
    setActiveLoading(true);
    try {
      setActiveList(await getActiveAnnouncements());
    } catch {
      setActiveList([]);
    } finally {
      setActiveLoading(false);
    }
  }, []);

  const loadPage = useCallback(async (targetPage: number) => {
    setLoading(true);
    try {
      const data = await getAnnouncementList({ page: targetPage, page_size: PAGE_SIZE });
      setPageData(data);
      setSchemaMissingPublishedAt(
        data.content.length > 0 && data.content.some((item) => !item.publishedAt)
      );
    } catch {
      setPageData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    (async () => {
      const profile = await getMyAccessProfile();
      setCanManage(canManageAnnouncements(profile));
    })();
  }, []);

  const refresh = useCallback(async () => {
    await loadServerClock();
    await Promise.all([loadActive(), loadPage(page)]);
  }, [loadServerClock, loadActive, loadPage, page]);

  useEffect(() => {
    refresh();
    // page 变化时只重载分页数据，避免重复请求
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    loadServerClock();
  }, [loadServerClock]);

  const openCreate = async () => {
    setEditing(null);
    const now = await loadServerClock();
    form.resetFields();
    form.setFieldsValue({
      title: '',
      content: '',
      priority: 'normal',
      publishedAt: now,
      expiresAt: null,
    });
    setModalOpen(true);
  };

  const openEdit = async (item: AnnouncementVO) => {
    setEditing(item);
    await loadServerClock();
    form.resetFields();
    form.setFieldsValue({
      title: item.title,
      content: item.content,
      priority: item.priority || 'normal',
      publishedAt: item.publishedAt ? dayjs(item.publishedAt) : dayjs(),
      expiresAt: item.expiresAt ? dayjs(item.expiresAt) : null,
    });
    setModalOpen(true);
  };

  /**
   * 公告请求体。
   *
   * publishedAt 一律显式提交（服务端时钟对齐后的时间）：t40 前的后端未补默认值，
   * 缺字段会直接 500；t40 之后虽会兜底补 now，但显式提交才能保证「生效判定」与展示时间一致。
   */
  const buildPayload = (values: AnnouncementFormValues): AnnouncementSaveRequest => ({
    title: values.title.trim(),
    content: values.content,
    priority: values.priority || 'normal',
    publishedAt: (values.publishedAt ?? serverNow).format(DATETIME_FORMAT),
    expiresAt: values.expiresAt ? values.expiresAt.format(DATETIME_FORMAT) : null,
  });

  /** 公告写操作的失败提示：401/403/400/5xx 分别可读，403 用角色口径文案 */
  const describeWriteFailure = (error: unknown, fallback: string): string => {
    const status = getApiErrorStatus(error);
    if (status === 401) return '登录已过期，请重新登录后再试';
    if (status === 403) return ANNOUNCEMENT_FORBIDDEN_MESSAGE;
    return getApiErrorMessage(error, fallback);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload = buildPayload(values);
      if (editing) {
        await updateAnnouncement(editing.id, payload);
        message.success('公告已更新');
      } else {
        await createAnnouncement(payload);
        message.success('公告已发布');
      }
      setModalOpen(false);
      setEditing(null);
      await Promise.all([loadActive(), loadPage(editing ? page : 1)]);
      if (!editing) setPage(1);
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(describeWriteFailure(error, '公告保存失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (item: AnnouncementVO) => {
    try {
      await deleteAnnouncement(item.id);
      message.success('公告已删除');
      await Promise.all([loadActive(), loadPage(page)]);
    } catch (error: any) {
      message.error(describeWriteFailure(error, '公告删除失败'));
    }
  };

  const columns = useMemo(
    () => [
      {
        title: '标题',
        dataIndex: 'title',
        key: 'title',
        render: (title: string) => <Text strong>{title || '(无标题)'}</Text>,
      },
      {
        title: '优先级',
        dataIndex: 'priority',
        key: 'priority',
        width: 100,
        render: (priority: string) => {
          const meta = priorityMeta(priority);
          return <Tag color={meta.color}>{meta.label}</Tag>;
        },
      },
      {
        title: '状态',
        key: 'status',
        width: 160,
        render: (_: unknown, record: AnnouncementVO) => {
          const meta = STATUS_META[resolveStatus(record, serverNow)];
          return <Tag color={meta.color}>{meta.label}</Tag>;
        },
      },
      {
        title: '发布时间',
        dataIndex: 'publishedAt',
        key: 'publishedAt',
        width: 180,
        render: (value: string) => formatDateTime(value),
      },
      {
        title: '过期时间',
        dataIndex: 'expiresAt',
        key: 'expiresAt',
        width: 180,
        render: (value?: string | null) => (value ? formatDateTime(value) : '长期有效'),
      },
      ...(canManage
        ? [
            {
              title: '操作',
              key: 'action',
              width: 150,
              render: (_: unknown, record: AnnouncementVO) => (
                <Space size={4}>
                  <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
                    编辑
                  </Button>
                  <Popconfirm
                    title="删除公告"
                    description="删除后该公告不再展示，确定继续？"
                    okText="删除"
                    cancelText="取消"
                    onConfirm={() => handleDelete(record)}
                  >
                    <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]
        : []),
    ],
    // openEdit/handleDelete 依赖的 state 已在闭包中稳定使用，这里只跟随 canManage/serverNow/page 变化
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [canManage, serverNow, page]
  );

  return (
    <div>
      <Card
        size="small"
        title={
          <Space>
            <PushpinFilled style={{ color: '#1677ff' }} />
            生效中的公告
            <Tag color="blue">{activeList.length}</Tag>
          </Space>
        }
        extra={
          <Space>
            <Button size="small" icon={<ReloadOutlined />} onClick={refresh} loading={activeLoading || loading}>
              刷新
            </Button>
            {canManage ? (
              <Button size="small" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                发布公告
              </Button>
            ) : null}
          </Space>
        }
        style={{ marginBottom: 16 }}
      >
        <Spin spinning={activeLoading}>
          {activeList.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="当前没有生效中的公告；发布时间晚于服务器时间或已过期的公告不会在此展示"
            />
          ) : (
            <List
              dataSource={activeList}
              renderItem={(item) => {
                const meta = priorityMeta(item.priority);
                return (
                  <List.Item key={item.id} style={{ paddingLeft: 0, paddingRight: 0 }}>
                    <List.Item.Meta
                      title={
                        <Space wrap>
                          <Tag color={meta.color}>{meta.label}</Tag>
                          <Text strong>{item.title}</Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            发布于 {formatDateTime(item.publishedAt)}
                            {item.expiresAt ? ` · 至 ${formatDateTime(item.expiresAt)}` : ' · 长期有效'}
                          </Text>
                        </Space>
                      }
                      description={
                        <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }} ellipsis={{ rows: 3, expandable: true, symbol: '展开' }}>
                          {item.content}
                        </Paragraph>
                      }
                    />
                  </List.Item>
                );
              }}
            />
          )}
        </Spin>
      </Card>

      <Card
        size="small"
        title="全部公告"
        extra={
          !canManage ? <Text type="secondary">{ANNOUNCEMENT_READONLY_HINT}</Text> : undefined
        }
      >
        {schemaMissingPublishedAt ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="部分历史公告缺少发布时间字段，可能由旧版本后端写入，展示时按未生效处理"
          />
        ) : null}
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={pageData?.content ?? []}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={canManage ? '还没有公告，点击右上角「发布公告」开始' : '暂无公告'}
              />
            ),
          }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: pageData?.totalElements ?? 0,
            showSizeChanger: false,
            onChange: (next) => setPage(next),
          }}
        />
      </Card>

      <Modal
        open={modalOpen}
        title={editing ? '编辑公告' : '发布公告'}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        onOk={handleSubmit}
        confirmLoading={submitting}
        okText={editing ? '保存' : '发布'}
        destroyOnHidden
        width={640}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请填写公告标题' }, { max: 200, message: '标题不超过 200 字' }]}>
            <Input placeholder="例如：平台例行维护通知" maxLength={200} showCount />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请选择优先级' }]}>
                <Select
                  options={[
                    { value: 'normal', label: '普通' },
                    { value: 'important', label: '重要' },
                    { value: 'urgent', label: '紧急' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="publishedAt"
                label="发布时间"
                rules={[{ required: true, message: '发布时间必填（后端 publishedAt 为 NOT NULL）' }]}
                extra="生效判定使用服务端时间，默认已对齐服务器时钟"
              >
                <DatePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm:ss" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="expiresAt" label="过期时间" extra="留空表示长期有效">
            <DatePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm:ss" />
          </Form.Item>
          <Form.Item name="content" label="公告内容" rules={[{ required: true, message: '请填写公告内容' }]}>
            <TextArea rows={6} placeholder="支持多行文本，发布后在通知中心公告区置顶展示" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AnnouncementsTab;
