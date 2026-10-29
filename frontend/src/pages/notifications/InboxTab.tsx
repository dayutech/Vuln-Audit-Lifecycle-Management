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
import { notificationTypeLabel } from '@/types/notification';
import { formatDateTime } from '@/utils/datetime';

const { Text, Paragraph } = Typography;

const PAGE_SIZE = 10;
const UNREAD_BG = '#e6f4ff';
const UNREAD_EVENT = 'notifications:unread-changed';

type ReadFilter = 'all' | 'unread' | 'read';

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

const renderChannel = (channel?: string | null) => {
  const value = (channel ?? '').toLowerCase();
  if (!value) return <Tag>未记录</Tag>;
  const tags: React.ReactNode[] = [];
  if (value.includes('in_app')) tags.push(<Tag key="in_app" color="blue">站内信</Tag>);
  if (value.includes('email')) tags.push(<Tag key="email" color="purple">邮件</Tag>);
  if (value.includes('webhook')) tags.push(<Tag key="webhook" color="cyan">Webhook</Tag>);
  if (value.includes('sms')) tags.push(<Tag key="sms" color="gold">短信</Tag>);
  return tags.length > 0 ? <Space size={4}>{tags}</Space> : <Tag>{channel}</Tag>;
};

const SEND_STATUS_META: Record<string, { color: string; label: string }> = {
  sent: { color: 'success', label: '已送达' },
  partial: { color: 'warning', label: '部分失败' },
  failed: { color: 'error', label: '发送失败' },
  pending: { color: 'default', label: '待投递' },
};

const renderSendStatus = (status?: NotificationSendStatus | null, errorMessage?: string | null) => {
  if (!status) {
    return (
      <Tooltip title="当前部署未返回投递状态字段（旧版本后端），站内信仍可正常阅读">
        <Tag>未知</Tag>
      </Tooltip>
    );
  }
  const meta = SEND_STATUS_META[status];
  const tag = <Tag color={meta?.color ?? 'default'}>{meta?.label ?? status}</Tag>;
  if (!errorMessage) return tag;
  return (
    <Tooltip title={errorMessage}>
      <span>{tag}</span>
    </Tooltip>
  );
};

const InboxTab: React.FC = () => {
  const navigate = useNavigate();
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
      if (!error?.response) message.error('通知列表加载失败');
      setItems([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, filter]);

  useEffect(() => {
    load();
  }, [load]);

  const handleMarkRead = async (item: NotificationListItem) => {
    setActionId(item.id);
    try {
      await markNotificationRead(item.id);
      message.success('已标记为已读');
      await load();
      if (detail?.id === item.id) setDetail({ ...item, isRead: true });
    } catch (error: any) {
      if (!error?.response) message.error('标记已读失败');
    } finally {
      setActionId(null);
    }
  };

  const handleMarkAllRead = async () => {
    setMarkingAll(true);
    try {
      await markAllNotificationRead();
      message.success('全部消息已标记为已读');
      if (page === 1) {
        await load();
      } else {
        setPage(1);
      }
    } catch (error: any) {
      if (!error?.response) message.error('批量标记失败');
    } finally {
      setMarkingAll(false);
    }
  };

  const handleDelete = async (item: NotificationListItem) => {
    setActionId(item.id);
    try {
      await deleteNotification(item.id);
      message.success('通知已删除');
      if (detail?.id === item.id) setDetail(null);
      await load();
    } catch (error: any) {
      if (!error?.response) message.error('删除失败');
    } finally {
      setActionId(null);
    }
  };

  const openDetail = async (item: NotificationListItem) => {
    setDetail(item);
    setRecipients([]);
    setRecipientsLoading(true);
    try {
      setRecipients(await getNotificationRecipients(item.id));
    } catch {
      setRecipients([]);
    } finally {
      setRecipientsLoading(false);
    }
  };

  const handleJump = (item: NotificationVO) => {
    const route = resolveRelatedRoute(item);
    if (!route) {
      message.info('该消息没有可跳转的关联资源');
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
        message="外部通道默认关闭：邮件/Webhook 未启用时通知仅落站内信，投递结果不会报错，可在消息详情里查看发送状态与失败原因。"
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
              { label: '全部消息', value: 'all' },
              { label: `未读 ${unreadCount}`, value: 'unread' },
              { label: '已读', value: 'read' },
            ]}
          />
          <Text type="secondary">共 {total} 条</Text>
        </Space>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => load()} loading={loading}>
            刷新
          </Button>
          <Button
            type="primary"
            icon={<CheckOutlined />}
            onClick={handleMarkAllRead}
            loading={markingAll}
            disabled={unreadCount === 0}
          >
            全部标为已读
          </Button>
        </Space>
      </Card>

      <Spin spinning={loading}>
        {items.length === 0 && !loading ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              filter === 'unread'
                ? '没有未读消息，说明动态都已处理完毕'
                : '暂无消息：任务分配、漏洞流转、审批与公告提醒会集中出现在这里'
            }
          >
            <Button icon={<ReloadOutlined />} onClick={() => load()}>
              刷新试试
            </Button>
          </Empty>
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
                      查看详情
                    </Button>,
                    route ? (
                      <Button key="jump" type="link" size="small" icon={<ExportOutlined />} onClick={() => handleJump(item)}>
                        关联资源
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
                        标为已读
                      </Button>
                    ) : null,
                    <Popconfirm key="delete" title="删除该通知？" onConfirm={() => handleDelete(item)} okText="删除" cancelText="取消">
                      <Button type="link" size="small" danger icon={<DeleteOutlined />} loading={actionId === item.id}>
                        删除
                      </Button>
                    </Popconfirm>,
                  ].filter(Boolean)}
                >
                  <List.Item.Meta
                    title={
                      <Space size={8} wrap>
                        {!item.isRead && <Badge status="processing" />}
                        {route ? (
                          <Tooltip title="点击跳转关联资源">
                            <Text
                              strong={!item.isRead}
                              style={{ fontSize: 15, color: '#1677ff', cursor: 'pointer' }}
                              onClick={() => handleJump(item)}
                            >
                              {item.title || '(无标题)'}
                            </Text>
                          </Tooltip>
                        ) : (
                          <Text strong={!item.isRead} style={{ fontSize: 15 }}>
                            {item.title || '(无标题)'}
                          </Text>
                        )}
                        <Tag color={item.isRead ? 'default' : 'blue'}>{item.isRead ? '已读' : '未读'}</Tag>
                        <Tag>{notificationTypeLabel(item.type)}</Tag>
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
                        {item.content || '(无正文)'}
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
        title={detail ? `消息详情 - ${notificationTypeLabel(detail.type)}` : '消息详情'}
        extra={
          detail ? (
            <Space>
              {!detail.isRead && (
                <Button size="small" icon={<CheckOutlined />} onClick={() => handleMarkRead(detail)} loading={actionId === detail.id}>
                  标为已读
                </Button>
              )}
              {detailRoute && (
                <Button size="small" icon={<ExportOutlined />} onClick={() => handleJump(detail)}>
                  前往关联资源
                </Button>
              )}
            </Space>
          ) : null
        }
      >
        {detail ? (
          <div>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="标题">{detail.title || '-'}</Descriptions.Item>
              <Descriptions.Item label="类型">{notificationTypeLabel(detail.type)}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={detail.isRead ? 'default' : 'blue'}>{detail.isRead ? '已读' : '未读'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="时间">{formatDateTime(detail.createdAt, '-')}</Descriptions.Item>
              <Descriptions.Item label="关联资源">
                {detail.relatedId ? `${detail.relatedType || 'related'}：${detail.relatedId}` : '无'}
              </Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{ marginTop: 16 }}>
              正文
            </Typography.Title>
            <Card size="small" style={{ whiteSpace: 'pre-wrap' }}>
              {detail.content || '(无正文)'}
            </Card>

            <Typography.Title level={5} style={{ marginTop: 20 }}>
              <Space>
                <SendOutlined />
                投递记录
              </Space>
            </Typography.Title>
            <Spin spinning={recipientsLoading}>
              {recipients.length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={recipientsLoading ? '加载中' : '没有投递记录，说明该消息尚未分发或被清理'}
                />
              ) : (
                <List
                  size="small"
                  dataSource={recipients}
                  renderItem={(record) => (
                    <List.Item>
                      <Space direction="vertical" size={4} style={{ width: '100%' }}>
                        <Space size={8} wrap>
                          {renderChannel(record.channel)}
                          {renderSendStatus(record.sendStatus, record.errorMessage)}
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            接收人 {record.userId}
                          </Text>
                          {record.recipientType ? <Tag>{record.recipientType}</Tag> : null}
                        </Space>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          投递时间：{formatDateTime(record.sentAt, '未投递')} · 阅读状态：
                          {record.isRead ? `已读 ${formatDateTime(record.readAt, '')}` : '未读'}
                        </Text>
                        {record.errorMessage ? (
                          <Text type="danger" style={{ fontSize: 12 }}>
                            失败原因：{record.errorMessage}
                          </Text>
                        ) : null}
                      </Space>
                    </List.Item>
                  )}
                />
              )}
            </Spin>
          </div>
        ) : null}
      </Drawer>
    </div>
  );
};

export default InboxTab;
