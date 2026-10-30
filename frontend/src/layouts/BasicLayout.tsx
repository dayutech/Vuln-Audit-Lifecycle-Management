import React, { useEffect, useMemo, useState } from 'react';
import { Layout, Menu, Dropdown, Avatar, Badge } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { UserOutlined, LogoutOutlined, DashboardOutlined, SafetyCertificateOutlined, AppstoreOutlined, TeamOutlined, SettingOutlined, FileTextOutlined, ToolOutlined, BellOutlined, ReadOutlined, CheckSquareOutlined, AuditOutlined, FileSearchOutlined, CloudUploadOutlined } from '@ant-design/icons';
import { useUserStore } from '@/store/userStore';
import { getCurrentUser, logout } from '@/api/auth';
import { getUnreadNotificationCount } from '@/api/notification';
import { getMyAccessProfile, isManagementProfile } from '@/api/permission';
import { useT } from '@/i18n';
import LangSwitcher from '@/i18n/LangSwitcher';

const { Header, Sider, Content } = Layout;

const BasicLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, setUser, clearAuth } = useUserStore();
  const [collapsed, setCollapsed] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  /**
   * 是否管理层（admin / project_leader）。
   * null 表示画像尚未返回或获取失败——此时仍展示入口，由页面自身的 403 兜底，
   * 避免「接口抖动导致管理员看不到入口」这种更差的结果。
   */
  const [managerEligible, setManagerEligible] = useState<boolean | null>(null);
  const { t } = useT();

  useEffect(() => {
    let cancelled = false;
    getMyAccessProfile().then((profile) => {
      if (cancelled) return;
      // 画像请求失败时 roles/permissions 均为空；此时保持 null（未知）而不是判定为非管理层
      const known = profile.roles.length > 0 || profile.permissions.length > 0;
      setManagerEligible(known ? isManagementProfile(profile) : null);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // 侧边栏「系统消息」未读角标：进入布局时拉一次，通知中心标记已读后通过自定义事件刷新
  useEffect(() => {
    let cancelled = false;
    const refresh = async () => {
      try {
        const count = await getUnreadNotificationCount();
        if (!cancelled) setUnreadCount(count);
      } catch {
        // 未登录/接口不可用时静默降级，不打扰主流程
      }
    };
    refresh();
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<unknown>).detail;
      if (typeof detail === 'number') {
        setUnreadCount(detail);
        return;
      }
      refresh();
    };
    window.addEventListener('notifications:unread-changed', handler);
    return () => {
      cancelled = true;
      window.removeEventListener('notifications:unread-changed', handler);
    };
  }, []);

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const res = await getCurrentUser();
        // Backend returns ApiResponse: { success: true, data: { ... } }
        if (res && res.data) {
          setUser(res.data);
        }
      } catch (error) {
        clearAuth();
        navigate('/login');
      }
    };
    if (!user) {
      fetchUser();
    }
  }, [user, setUser, clearAuth, navigate]);

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
      clearAuth();
      navigate('/login');
    }
  };

  const menuItems = [
    {
      label: t('menu.groupCore'),
      key: 'core',
      type: 'group' as const,
      children: [
                { key: '/dashboard', icon: <DashboardOutlined />, label: t('menu.dashboard') },
                { key: '/assets', icon: <AppstoreOutlined />, label: t('menu.assets') },
                { key: '/asset-groups', icon: <AppstoreOutlined />, label: t('menu.assetGroups') },
                { key: '/vulnerabilities', icon: <SafetyCertificateOutlined />, label: t('menu.vulnerabilities') },
                { key: '/tasks', icon: <CheckSquareOutlined />, label: t('menu.tasks') },
                { key: '/approvals', icon: <AuditOutlined />, label: t('menu.approvals') },
      ],
    },
    {
      label: t('menu.groupKnowledge'),
      key: 'knowledge',
      type: 'group' as const,
      children: [
                { key: '/knowledge', icon: <ReadOutlined />, label: t('menu.knowledge') },
                { key: '/documents', icon: <FileTextOutlined />, label: t('menu.documents') },
                { key: '/tools', icon: <ToolOutlined />, label: t('menu.tools') },
      ],
    },
    {
      label: t('menu.groupSystem'),
      key: 'system',
      type: 'group' as const,
      children: [
                { key: '/teams', icon: <TeamOutlined />, label: t('menu.teams') },
                { key: '/approval-scopes', icon: <AuditOutlined />, label: t('menu.approvalScopes') },
        // 账号管理仅管理层可见（t45：GET /users 收紧为 admin / project_leader）
        ...(managerEligible === false
          ? []
          : [{ key: '/users', icon: <UserOutlined />, label: t('menu.users') }]),
                { key: '/roles', icon: <SettingOutlined />, label: t('menu.roles') },
        // 审计日志仅管理层可见（后端 @PreAuthorize 限 admin / project_leader）
        ...(managerEligible === false
          ? []
          : [{ key: '/audit-logs', icon: <FileSearchOutlined />, label: t('menu.auditLogs') }]),
        // 数据导入：后端 /import/assets、/import/vulnerabilities 仅要求登录，所有角色可用
                { key: '/import', icon: <CloudUploadOutlined />, label: t('menu.import') },
        { key: '/notifications', icon: <BellOutlined />, label: (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            {t('menu.notifications')}
            {unreadCount > 0 && <Badge count={unreadCount} size="small" overflowCount={99} />}
          </span>
        ) },
      ],
    },
  ];

  const userMenu = {
    items: [
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: t('header.logout'),
        onClick: handleLogout,
      },
    ],
  };
  const selectedMenuKey = useMemo(() => {
    const menuKeys = [
      '/dashboard',
      '/assets',
      '/asset-groups',
      '/vulnerabilities',
      '/tasks',
      '/knowledge',
      '/documents',
      '/tools',
      '/teams',
      '/approval-scopes',
      '/users',
      '/roles',
      '/audit-logs',
      '/import',
      '/notifications',
    ];
    const exactMatchedKey = menuKeys.find((key) => key === location.pathname);
    if (exactMatchedKey) {
      return exactMatchedKey;
    }
    const partialMatchedKey = menuKeys
      .sort((a, b) => b.length - a.length)
      .find((key) => location.pathname.startsWith(`${key}/`));
    return partialMatchedKey || '/dashboard';
  }, [location.pathname]);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible collapsed={collapsed} onCollapse={(value) => setCollapsed(value)}>
        <div style={{ height: 32, margin: 16, background: 'rgba(255, 255, 255, 0.2)', borderRadius: 6 }} />
        <Menu
          theme="dark"
          selectedKeys={[selectedMenuKey]}
          mode="inline"
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ padding: '0 24px', background: '#fff', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 12 }}>
          <LangSwitcher />
          <Dropdown menu={userMenu} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar icon={<UserOutlined />} src={user?.avatar} />
              <span>{user?.username || '-'}</span>
            </div>
          </Dropdown>
        </Header>
        <Content style={{ margin: '24px 16px', padding: 24, background: '#fff', minHeight: 280, borderRadius: 8 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default BasicLayout;
