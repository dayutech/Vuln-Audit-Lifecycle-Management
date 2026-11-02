import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  List,
  Popconfirm,
  Radio,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  CheckOutlined,
  DeleteOutlined,
  ExportOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import {
  deleteNotification,
  getNotificationList,
  getNotificationRecipients,
  getUnreadNotificationIds,
  markAllNotificationRead,
  markNotificationRead,
} from '@/api/notification';
import type {
  NotificationListItem,
  NotificationRecipientVO,
  NotificationSendStatus,
  NotificationVO,
} from '@/types/notification';
import { notificationTypeLabelKey } from '@/types/notification';
import { formatDateTime } from '@/utils/datetime';
import { useT, type MessageKey } from '@/i18n';
import { getApiErrorStatus } from '@/utils/errorMessage';
import { AppEmpty } from '@/components/Feedback';

const { Text, Paragraph } = Typography;

const PAGE_SIZE = 10;
const UNREAD_BG = '#e6f4ff';
const UNREAD_EVENT = 'notifications:unread-changed';

type ReadFilter = 'all' | 'unread' | 'read';
type Translate = (key: MessageKey, vars?: Record<string, string | number>) => string;

const FILTER_TO_PARAM: Record<ReadFilter, boolean | undefined> = {
  all: undefined,
  unread: false,
  read: true,
};

const dispatchUnreadCount = (count: number) => {
  window.dispatchEvent(new CustomEvent(UNREAD_EVENT, { detail: count }));
};

/**
 * 通知 → 关联资源路由。
 * 后端 relatedType 目前未写入（多为 null），因此同时用 type 关键字兜底匹配。
 */
const resolveRelatedRoute = (item: NotificationVO): string | null => {
  const haystack = `${item.relatedType ?? ''} ${item.type ?? ''}`.toLowerCase();
  const id = item.relatedId ?? null;
  if (haystack.includes('vuln')) return id ? `/vulnerabilities/${id}` : '/vulnerabilities';
  if (haystack.includes('task')) return id ? `/tasks/${id}` : '/tasks';
  if (haystack.includes('asset')) return id ? `/assets/${id}` : '/assets';
  if (haystack.includes('approval') || haystack.includes('access')) return '/approvals';
  return null;
};

/** 渠道标签（枚举展示统一走 t()；未知渠道回显原值，不吞信息） */
const renderChannel = (channel: string | null | undefined, t: Translate) => {
  const value = (channel ?? '').toLowerCase();
  if (!value) return <Tag>{t('notifications.channel.notRecorded')}</Tag>;
  const tags: React.ReactNode[] = [];
  if (value.includes('in_app')) tags.push(<Tag key="in_app" color="blue">{t('notifications.channel.inApp')}</Tag>);
  if (value.includes('email')) tags.push(<Tag key="email" color="purple">{t('notifications.channel.email')}</Tag>);
  if (value.includes('webhook')) tags.push(<Tag key="webhook" color="cyan">{t('notifications.channel.webhook')}</Tag>);
  if (value.includes('sms')) tags.push(<Tag key="sms" color="gold">{t('notifications.channel.sms')}</Tag>);
  return tags.length > 0 ? <Space size={4}>{tags}</Space> : <Tag>{channel}</Tag>;
};

/** 发送状态 → 文案 key（sent 且仅站内时用「仅站内成功 / In-app only」自解释） */
const sendStatusKey = (status: string, channel?: string | null): MessageKey => {
  if (status === 'sent') {
    const value = (channel ?? '').toLowerCase();
    const onlyInApp = value === 'in_app' || (value.includes('in_app') && !value.includes('email') && !value.includes('webhook') && !value.includes('sms'));
    return onlyInApp ? 'notifications.status.inAppOnly' : 'notifications.status.sent';
  }
  if (status === 'partial') return 'notifications.status.partial';
  if (status === 'failed') return 'notifications.status.failed';
  if (status === 'pending') return 'notifications.status.pending';
  return 'notifications.status.unknown';
};

const SEND_STATUS_COLOR: Record<string, string> = {
  sent: 'success',
  partial: 'warning',
  failed: 'error',
  pending: 'default',
};

const renderSendStatus = (
  status: NotificationSendStatus | null | undefined,
  errorMessage: string | null | undefined,
  channel: string | null | undefined,
  t: Translate
) => {
  if (!status) {
    return (
      <Tooltip title={t('notifications.status.unknownTip')}>
        <Tag>{t('notifications.status.unknown')}</Tag>
      </Tooltip>
    );
  }
  const label = t(sendStatusKey(status, channel));
  const tag = <Tag color={SEND_STATUS_COLOR[status] ?? 'default'}>{label}</Tag>;
  if (!errorMessage) return tag;
  return (
    <Tooltip title={errorMessage}>
      <span>{tag}</span>
    </Tooltip>
  );
};

const InboxTab: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useT();
  const [items, setItems] = useState<NotificationListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [filter, setFilter] = useState<ReadFilter>('all');
  const [unreadCount, setUnreadCount] = useState(0);
  const [actionId, setActionId] = useState<string | null>(null);
  const [markingAll, setMarkingAll] = useState(false);

  const [detail, setDetail] = useState<NotificationListItem | null>(null);
  const [recipients, setRecipients] = useState<NotificationRecipientVO[]>([]);
  const [recipientsLoading, setRecipientsLoading] = useState(false);
  /** 投递记录仅「接收人本人或管理层」可见（t50 canReadNotificationRecipients）：403 时直接不渲染该区块 */
  const [recipientsDenied, setRecipientsDenied] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [pageData, unreadIds] = await Promise.all([
        getNotificationList({ page, page_size: PAGE_SIZE, is_read: FILTER_TO_PARAM[filter] }),
        getUnreadNotificationIds(),
      ]);
      const unreadSet = new Set(unreadIds);
      setItems(
        pageData.content.map((item) => ({
          ...item,
          isRead: !unreadSet.has(String(item.id)),
        }))
      );
      setTotal(pageData.totalElements);
      setUnreadCount(unreadIds.length);
      dispatchUnreadCount(unreadIds.length);
    } catch (error: any) {
      if (!error?.response) message.error(t('notifications.inbox.loadFailed'));
      setItems([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, filter, t]);

  useEffect(() => {
    load();
  }, [load]);

  const handleMarkRead = async (item: NotificationListItem) => {
    setActionId(item.id);
    try {
      await markNotificationRead(item.id);
      message.success(t('notifications.inbox.markedRead'));
      await load();
      if (detail?.id === item.id) setDetail({ ...item, isRead: true });
    } catch (error: any) {
      if (!error?.response) message.error(t('notifications.inbox.markReadFailed'));
    } finally {
      setActionId(null);
    }
  };

  const handleMarkAllRead = async () => {
    setMarkingAll(true);
    try {
      await markAllNotificationRead();
      message.success(t('notifications.inbox.markedAllRead'));
      if (page === 1) {
        await load();
      } else {
        setPage(1);
      }
    } catch (error: any) {
      if (!error?.response) message.error(t('notifications.inbox.markAllFailed'));
    } finally {
      setMarkingAll(false);
    }
  };

  const handleDelete = async (item: NotificationListItem) => {
    setActionId(item.id);
    try {
      await deleteNotification(item.id);
      message.success(t('notifications.inbox.deleted'));
      if (detail?.id === item.id) setDetail(null);
      await load();
    } catch (error: any) {
      if (!error?.response) message.error(t('notifications.inbox.deleteFailed'));
    } finally {
      setActionId(null);
    }
  };

  const openDetail = async (item: NotificationListItem) => {
    setDetail(item);
    setRecipients([]);
    setRecipientsDenied(false);
    setRecipientsLoading(true);
    try {
      setRecipients(await getNotificationRecipients(item.id));
    } catch (error) {
      setRecipients([]);
      // t50：投递记录仅「接收人本人或管理层」可读；403 时不渲染该区块，而不是显示空表
      if (getApiErrorStatus(error) === 403) setRecipientsDenied(true);
    } finally {
      setRecipientsLoading(false);
    }
  };

  const handleJump = (item: NotificationVO) => {
    const route = resolveRelatedRoute(item);
    if (!route) {
      message.info(t('notifications.inbox.noRelated'));
      return;
    }
    navigate(route);
  };

  const detailRoute = useMemo(() => (detail ? resolveRelatedRoute(detail) : null), [detail]);

  return (
    <div>
      <Alert
        type="info"
        showIcon
        closable
        style={{ marginBottom: 16 }}
        message={t('notifications.inbox.externalClosed')}
      />
      <Card
        size="small"
        style={{ marginBottom: 16 }}
        styles={{ body: { display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center', justifyContent: 'space-between' } }}
      >
        <Space size={12} wrap>
          <Radio.Group
            value={filter}
            optionType="button"
            buttonStyle="solid"
            onChange={(event) => {
              setFilter(event.target.value as ReadFilter);
              setPage(1);
            }}
            options={[
              { label: t('notifications.inbox.filterAll'), value: 'all' },
              { label: t('notifications.inbox.filterUnread', { count: unreadCount }), value: 'unread' },
              { label: t('notifications.inbox.filterRead'), value: 'read' },
            ]}
          />
          <Text type="secondary">{t('notifications.inbox.total', { count: total })}</Text>
        </Space>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => load()} loading={loading}>
            {t('notifications.refresh')}
          </Button>
          <Button
            type="primary"
            icon={<CheckOutlined />}
            onClick={handleMarkAllRead}
            loading={markingAll}
            disabled={unreadCount === 0}
          >
            {t('notifications.markAllRead')}
          </Button>
        </Space>
      </Card>

      <Spin spinning={loading}>
        {items.length === 0 && !loading ? (
          <AppEmpty
            description={filter === 'unread' ? t('notifications.emptyUnread') : t('notifications.emptyInbox')}
          >
            <Button icon={<ReloadOutlined />} onClick={() => load()}>
              {t('common.refresh')}
            </Button>
          </AppEmpty>
        ) : (
          <List
            itemLayout="vertical"
            dataSource={items}
            pagination={{
              current: page,
              pageSize: PAGE_SIZE,
              total,
              showSizeChanger: false,
              onChange: (next) => setPage(next),
            }}
            renderItem={(item) => {
              const route = resolveRelatedRoute(item);
              return (
                <List.Item
                  key={item.id}
                  style={{
                    padding: '12px 16px',
                    borderRadius: 8,
                    background: item.isRead ? undefined : UNREAD_BG,
                    border: '1px solid #f0f0f0',
                    marginBottom: 8,
                  }}
                  actions={[
                    <Button key="detail" type="link" size="small" onClick={() => openDetail(item)}>
                      {t('notifications.inbox.viewDetail')}
                    </Button>,
                    route ? (
                      <Button key="jump" type="link" size="small" icon={<ExportOutlined />} onClick={() => handleJump(item)}>
                        {t('notifications.inbox.relatedResource')}
                      </Button>
                    ) : null,
                    !item.isRead ? (
                      <Button
                        key="read"
                        type="link"
                        size="small"
                        icon={<CheckOutlined />}
                        loading={actionId === item.id}
                        onClick={() => handleMarkRead(item)}
                      >
                        {t('notifications.inbox.markReadShort')}
                      </Button>
                    ) : null,
                    <Popconfirm
                      key="delete"
                      title={t('notifications.inbox.deleteConfirmTitle')}
                      onConfirm={() => handleDelete(item)}
                      okText={t('notifications.inbox.delete')}
                      cancelText={t('notifications.inbox.cancel')}
                    >
                      <Button type="link" size="small" danger icon={<DeleteOutlined />} loading={actionId === item.id}>
                        {t('notifications.inbox.delete')}
                      </Button>
                    </Popconfirm>,
                  ].filter(Boolean)}
                >
                  <List.Item.Meta
                    title={
                      <Space size={8} wrap>
                        {!item.isRead && <Badge status="processing" />}
                        {route ? (
                          <Tooltip title={t('notifications.inbox.jumpTip')}>
                            <Text
                              strong={!item.isRead}
                              style={{ fontSize: 15, color: '#1677ff', cursor: 'pointer' }}
                              onClick={() => handleJump(item)}
                            >
                              {item.title || t('notifications.inbox.noTitle')}
                            </Text>
                          </Tooltip>
                        ) : (
                          <Text strong={!item.isRead} style={{ fontSize: 15 }}>
                            {item.title || t('notifications.inbox.noTitle')}
                          </Text>
                        )}
                        <Tag color={item.isRead ? 'default' : 'blue'}>
                          {item.isRead ? t('notifications.inbox.read') : t('notifications.inbox.unread')}
                        </Tag>
                        <Tag>{t(notificationTypeLabelKey(item.type))}</Tag>
                        {item.priority && item.priority !== 'normal' ? <Tag color="orange">{item.priority}</Tag> : null}
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {formatDateTime(item.createdAt, '-')}
                        </Text>
                      </Space>
                    }
                    description={
                      <Paragraph
                        ellipsis={{ rows: 2, expandable: false }}
                        style={{ marginBottom: 0, color: 'rgba(0,0,0,0.65)' }}
                      >
                        {item.content || t('notifications.inbox.noBody')}
                      </Paragraph>
                    }
                  />
                </List.Item>
              );
            }}
          />
        )}
      </Spin>

      <Drawer
        width={620}
        open={!!detail}
        onClose={() => setDetail(null)}
        title={
          detail
            ? t('notifications.inbox.detailTitleWithType', { type: t(notificationTypeLabelKey(detail.type)) })
            : t('notifications.inbox.detailTitle')
        }
        extra={
          detail ? (
            <Space>
              {!detail.isRead && (
                <Button size="small" icon={<CheckOutlined />} onClick={() => handleMarkRead(detail)} loading={actionId === detail.id}>
                  {t('notifications.inbox.markReadShort')}
                </Button>
              )}
              {detailRoute && (
                <Button size="small" icon={<ExportOutlined />} onClick={() => handleJump(detail)}>
                  {t('notifications.inbox.gotoRelated')}
                </Button>
              )}
            </Space>
          ) : null
        }
      >
        {detail ? (
          <div>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label={t('notifications.inbox.field.title')}>
                {detail.title || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('notifications.inbox.field.type')}>
                {t(notificationTypeLabelKey(detail.type))}
              </Descriptions.Item>
              <Descriptions.Item label={t('notifications.inbox.field.status')}>
                <Tag color={detail.isRead ? 'default' : 'blue'}>
                  {detail.isRead ? t('notifications.inbox.read') : t('notifications.inbox.unread')}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('notifications.inbox.field.time')}>
                {formatDateTime(detail.createdAt, '-')}
              </Descriptions.Item>
              <Descriptions.Item label={t('notifications.inbox.field.related')}>
                {detail.relatedId
                  ? t('notifications.inbox.field.relatedValue', {
                      type: detail.relatedType || 'related',
                      id: detail.relatedId,
                    })
                  : t('notifications.inbox.field.none')}
              </Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{ marginTop: 16 }}>
              {t('notifications.inbox.body')}
            </Typography.Title>
            <Card size="small" style={{ whiteSpace: 'pre-wrap' }}>
              {detail.content || t('notifications.inbox.noBody')}
            </Card>

            {!recipientsDenied && (<>
            <Typography.Title level={5} style={{ marginTop: 20 }}>
              <Space>
                <SendOutlined />
                {t('notifications.inbox.delivery')}
              </Space>
            </Typography.Title>
            <Spin spinning={recipientsLoading}>
              {recipients.length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={recipientsLoading ? t('notifications.inbox.deliveryLoading') : t('notifications.inbox.noRecipients')}
                />
              ) : (
                <List
                  size="small"
                  dataSource={recipients}
                  renderItem={(record) => (
                    <List.Item>
                      <Space direction="vertical" size={4} style={{ width: '100%' }}>
                        <Space size={8} wrap>
                          {renderChannel(record.channel, t)}
                          {renderSendStatus(record.sendStatus, record.errorMessage, record.channel, t)}
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {t('notifications.inbox.recipient', { userId: record.userId })}
                          </Text>
                          {record.recipientType ? <Tag>{record.recipientType}</Tag> : null}
                        </Space>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {t('notifications.inbox.deliveredAt', {
                            time: formatDateTime(record.sentAt, t('notifications.inbox.notDelivered')),
                          })}
                          {' · '}
                          {t('notifications.inbox.readStatus')}
                          {record.isRead
                            ? t('notifications.inbox.readAt', { time: formatDateTime(record.readAt, '') })
                            : t('notifications.inbox.unread')}
                        </Text>
                        {record.errorMessage ? (
                          <Text type="danger" style={{ fontSize: 12 }}>
                            {t('notifications.inbox.failReason', { reason: record.errorMessage })}
                          </Text>
                        ) : null}
                      </Space>
                    </List.Item>
                  )}
                />
              )}
            </Spin>
            </>)}
          </div>
        ) : null}
      </Drawer>
    </div>
  );
};

export default InboxTab;
