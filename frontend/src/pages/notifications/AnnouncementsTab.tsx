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
  canManageAnnouncements,
  getMyAccessProfile,
} from '@/api/permission';
import type { AnnouncementPage, AnnouncementSaveRequest, AnnouncementVO } from '@/types/notification';
import { ANNOUNCEMENT_PRIORITY_KEYS } from '@/types/notification';
import { formatDateTime } from '@/utils/datetime';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useT, type MessageKey } from '@/i18n';

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
  ANNOUNCEMENT_PRIORITY_KEYS[(priority ?? 'normal').toLowerCase()] ?? ANNOUNCEMENT_PRIORITY_KEYS.normal;

const resolveStatus = (item: AnnouncementVO, now: Dayjs): AnnouncementStatus => {
  const published = item.publishedAt ? dayjs(item.publishedAt) : null;
  if (published && published.isAfter(now)) return 'scheduled';
  if (item.expiresAt && dayjs(item.expiresAt).isBefore(now)) return 'expired';
  return 'active';
};

const STATUS_META: Record<AnnouncementStatus, { color: string; labelKey: MessageKey }> = {
  active: { color: 'success', labelKey: 'notifications.ann.statusActive' },
  scheduled: { color: 'processing', labelKey: 'notifications.ann.statusScheduled' },
  expired: { color: 'default', labelKey: 'notifications.ann.statusExpired' },
};

const AnnouncementsTab: React.FC = () => {
  const { t } = useT();
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
    if (status === 401) return t('notifications.ann.authExpired');
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
        message.success(t('notifications.ann.updated'));
      } else {
        await createAnnouncement(payload);
        message.success(t('notifications.ann.published'));
      }
      setModalOpen(false);
      setEditing(null);
      await Promise.all([loadActive(), loadPage(editing ? page : 1)]);
      if (!editing) setPage(1);
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(describeWriteFailure(error, t('notifications.ann.saveFailed')));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (item: AnnouncementVO) => {
    try {
      await deleteAnnouncement(item.id);
      message.success(t('notifications.ann.deleted'));
      await Promise.all([loadActive(), loadPage(page)]);
    } catch (error: any) {
      message.error(describeWriteFailure(error, t('notifications.ann.deleteFailed')));
    }
  };

  const columns = useMemo(
    () => [
      {
        title: t('notifications.ann.colTitle'),
        dataIndex: 'title',
        key: 'title',
        render: (title: string) => <Text strong>{title || t('notifications.ann.noTitle')}</Text>,
      },
      {
        title: t('notifications.ann.colPriority'),
        dataIndex: 'priority',
        key: 'priority',
        width: 100,
        render: (priority: string) => {
          const meta = priorityMeta(priority);
          return <Tag color={meta.color}>{t(meta.labelKey)}</Tag>;
        },
      },
      {
        title: t('notifications.ann.colStatus'),
        key: 'status',
        width: 160,
        render: (_: unknown, record: AnnouncementVO) => {
          const meta = STATUS_META[resolveStatus(record, serverNow)];
          return <Tag color={meta.color}>{t(meta.labelKey)}</Tag>;
        },
      },
      {
        title: t('notifications.ann.colPublishedAt'),
        dataIndex: 'publishedAt',
        key: 'publishedAt',
        width: 180,
        render: (value: string) => formatDateTime(value),
      },
      {
        title: t('notifications.ann.colExpiresAt'),
        dataIndex: 'expiresAt',
        key: 'expiresAt',
        width: 180,
        render: (value?: string | null) => (value ? formatDateTime(value) : t('notifications.ann.longTerm')),
      },
      ...(canManage
        ? [
            {
              title: t('notifications.ann.colActions'),
              key: 'action',
              width: 150,
              render: (_: unknown, record: AnnouncementVO) => (
                <Space size={4}>
                  <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
                    {t('notifications.ann.edit')}
                  </Button>
                  <Popconfirm
                    title={t('notifications.ann.deleteTitle')}
                    description={t('notifications.ann.deleteDesc')}
                    okText={t('notifications.ann.delete')}
                    cancelText={t('notifications.ann.cancel')}
                    onConfirm={() => handleDelete(record)}
                  >
                    <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                      {t('notifications.ann.delete')}
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]
        : []),
    ],
    // openEdit/handleDelete 依赖的 state 已在闭包中稳定使用，这里只跟随 canManage/serverNow/page/t 变化
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [canManage, serverNow, page, t]
  );

  return (
    <div>
      <Card
        size="small"
        title={
          <Space>
            <PushpinFilled style={{ color: '#1677ff' }} />
            {t('notifications.announcements.title')}
            <Tag color="blue">{activeList.length}</Tag>
          </Space>
        }
        extra={
          <Space>
            <Button size="small" icon={<ReloadOutlined />} onClick={refresh} loading={activeLoading || loading}>
              {t('common.refresh')}
            </Button>
            {canManage ? (
              <Button size="small" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                {t('notifications.announcements.publish')}
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
              description={t('notifications.ann.emptyActive')}
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
                          <Tag color={meta.color}>{t(meta.labelKey)}</Tag>
                          <Text strong>{item.title}</Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {t('notifications.ann.publishedAtLabel', { time: formatDateTime(item.publishedAt) })}
                            {item.expiresAt
                              ? t('notifications.ann.until', { time: formatDateTime(item.expiresAt) })
                              : ` · ${t('notifications.ann.longTerm')}`}
                          </Text>
                        </Space>
                      }
                      description={
                        <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }} ellipsis={{ rows: 3, expandable: true, symbol: t('notifications.ann.expand') }}>
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
        title={t('notifications.announcements.all')}
        extra={
          !canManage ? <Text type="secondary">{t('notifications.announcements.readonly')}</Text> : undefined
        }
      >
        {schemaMissingPublishedAt ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message={t('notifications.ann.schemaMissing')}
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
                description={canManage ? t('notifications.ann.emptyManage') : t('notifications.ann.emptyNone')}
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
        title={editing ? t('notifications.ann.modalEdit') : t('notifications.ann.modalCreate')}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        onOk={handleSubmit}
        confirmLoading={submitting}
        okText={editing ? t('notifications.ann.okSave') : t('notifications.ann.okPublish')}
        destroyOnHidden
        width={640}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="title" label={t('notifications.ann.form.title')} rules={[{ required: true, message: t('notifications.ann.form.titleRequired') }, { max: 200, message: t('notifications.ann.form.titleMax') }]}>
            <Input placeholder={t('notifications.ann.form.titlePlaceholder')} maxLength={200} showCount />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="priority" label={t('notifications.ann.form.priority')} rules={[{ required: true, message: t('notifications.ann.form.priorityRequired') }]}>
                <Select
                  options={[
                    { value: 'normal', label: t('notifications.ann.form.priorityNormal') },
                    { value: 'important', label: t('notifications.ann.form.priorityImportant') },
                    { value: 'urgent', label: t('notifications.ann.form.priorityUrgent') },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="publishedAt"
                label={t('notifications.ann.form.publishedAt')}
                rules={[{ required: true, message: t('notifications.ann.form.publishedAtRequired') }]}
                extra={t('notifications.ann.form.publishedAtExtra')}
              >
                <DatePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm:ss" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="expiresAt" label={t('notifications.ann.form.expiresAt')} extra={t('notifications.ann.form.expiresAtExtra')}>
            <DatePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm:ss" />
          </Form.Item>
          <Form.Item name="content" label={t('notifications.ann.form.content')} rules={[{ required: true, message: t('notifications.ann.form.contentRequired') }]}>
            <TextArea rows={6} placeholder={t('notifications.ann.form.contentPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AnnouncementsTab;
