import React, { useMemo } from 'react';
import { BellOutlined, NotificationOutlined, SettingOutlined } from '@ant-design/icons';
import { Card, Tabs } from 'antd';
import { useSearchParams } from 'react-router-dom';
import InboxTab from './InboxTab';
import AnnouncementsTab from './AnnouncementsTab';
import SettingsTab from './SettingsTab';

type TabKey = 'inbox' | 'announcements' | 'settings';

const VALID_TABS: TabKey[] = ['inbox', 'announcements', 'settings'];

const isTabKey = (value: string | null): value is TabKey =>
  !!value && (VALID_TABS as string[]).includes(value);

/**
 * 通知中心（/notifications）
 *
 * 三个页签共用一条路由，通过 ?tab=inbox|announcements|settings 做深链：
 * - 站内信：未读高亮、单条/全部已读、删除、跳转关联资源、投递记录
 * - 公告区：生效中公告置顶 + 全量分页；管理员可发布/编辑/删除
 * - 设置：按通知类型配置 启用/邮件/短信/Webhook
 */
const Notifications: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const activeKey: TabKey = isTabKey(tabParam) ? tabParam : 'inbox';

  const items = useMemo(
    () => [
      {
        key: 'inbox',
        label: (
          <span>
            <BellOutlined /> 站内信
          </span>
        ),
        children: <InboxTab />,
      },
      {
        key: 'announcements',
        label: (
          <span>
            <NotificationOutlined /> 公告区
          </span>
        ),
        children: <AnnouncementsTab />,
      },
      {
        key: 'settings',
        label: (
          <span>
            <SettingOutlined /> 通知设置
          </span>
        ),
        children: <SettingsTab />,
      },
    ],
    []
  );

  return (
    <Card title="通知中心">
      <Tabs
        activeKey={activeKey}
        items={items}
        onChange={(key) => {
          const next = new URLSearchParams(searchParams);
          if (key === 'inbox') {
            next.delete('tab');
          } else {
            next.set('tab', key);
          }
          setSearchParams(next, { replace: true });
        }}
      />
    </Card>
  );
};

export default Notifications;
