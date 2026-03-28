import React, { useEffect, useState } from 'react';
import { Card, List, Typography, Badge, Button, Tabs, message, Space } from 'antd';
import { CheckOutlined } from '@ant-design/icons';
import { getNotificationList, markNotificationRead, markAllNotificationRead } from '@/api/notification';
import type { NotificationVO } from '@/types/notification';
import { formatDateTime } from '@/utils/datetime';

const { Text } = Typography;
const { TabPane } = Tabs;

const Notifications: React.FC = () => {
  const [data, setData] = useState<NotificationVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, is_read: undefined as boolean | undefined });

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getNotificationList(params);
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
  }, [params.page, params.page_size, params.is_read]);

  const handleMarkRead = async (id: string) => {
    try {
      await markNotificationRead(id);
      message.success('已标记为已读');
      fetchData();
    } catch (error) {
      // Error handled
    }
  };

  const handleMarkAllRead = async () => {
    try {
      const unreadIds = data.filter(item => !item.is_read).map(item => item.id);
      if (unreadIds.length === 0) return;
      await markAllNotificationRead(unreadIds);
      message.success('全部标记为已读');
      fetchData();
    } catch (error) {
      // Error handled
    }
  };

  const onTabChange = (key: string) => {
    if (key === 'all') {
      setParams({ ...params, page: 1, is_read: undefined });
    } else if (key === 'unread') {
      setParams({ ...params, page: 1, is_read: false });
    } else if (key === 'read') {
      setParams({ ...params, page: 1, is_read: true });
    }
  };

  return (
    <Card 
      title="消息中心" 
      extra={<Button icon={<CheckOutlined />} onClick={handleMarkAllRead}>全部标为已读</Button>}
    >
      <Tabs defaultActiveKey="all" onChange={onTabChange}>
        <TabPane tab="全部消息" key="all" />
        <TabPane tab="未读消息" key="unread" />
        <TabPane tab="已读消息" key="read" />
      </Tabs>
      
      <List
        loading={loading}
        itemLayout="horizontal"
        dataSource={data}
        pagination={{
          current: params.page,
          pageSize: params.page_size,
          total: total,
          onChange: (page, pageSize) => setParams({ ...params, page, page_size: pageSize })
        }}
        renderItem={item => (
          <List.Item
            actions={[
              !item.is_read && <a onClick={() => handleMarkRead(item.id)}>标为已读</a>
            ]}
          >
            <List.Item.Meta
              title={
                <Space>
                  {!item.is_read && <Badge status="processing" />}
                  <Text strong={!item.is_read}>{item.notification?.title}</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {formatDateTime(item.notification?.created_at ?? item.notification?.createdAt, '')}
                  </Text>
                </Space>
              }
              description={item.notification?.content}
            />
          </List.Item>
        )}
      />
    </Card>
  );
};

export default Notifications;
