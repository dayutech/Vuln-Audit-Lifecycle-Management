import React, { useEffect, useMemo, useState } from 'react';
import { Layout, Menu, Dropdown, Avatar, Badge } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { UserOutlined, LogoutOutlined, DashboardOutlined, SafetyCertificateOutlined, AppstoreOutlined, TeamOutlined, SettingOutlined, FileTextOutlined, ToolOutlined, BellOutlined, ReadOutlined, CheckSquareOutlined, AuditOutlined, FileSearchOutlined, CloudUploadOutlined } from '@ant-design/icons';
import { useUserStore } from '@/store/userStore';
import { getCurrentUser, logout } from '@/api/auth';
import { getUnreadNotificationCount } from '@/api/notification';
import { getMyAccessProfile, isManagementProfile } from '@/api/permission';

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
      label: '核心业务',
      key: 'core',
      type: 'group' as const,
      children: [
        { key: '/dashboard', icon: <DashboardOutlined />, label: '安全态势大盘' },
        { key: '/assets', icon: <AppstoreOutlined />, label: '产品与资产中心' },
        { key: '/asset-groups', icon: <AppstoreOutlined />, label: '资产分组' },
        { key: '/vulnerabilities', icon: <SafetyCertificateOutlined />, label: '全局漏洞视图' },
        { key: '/tasks', icon: <CheckSquareOutlined />, label: '安全审计任务' },
        { key: '/approvals', icon: <AuditOutlined />, label: '审批中心' },
      ],
    },
    {
      label: '知识与资源',
      key: 'knowledge',
      type: 'group' as const,
      children: [
        { key: '/knowledge', icon: <ReadOutlined />, label: '安全知识库' },
        { key: '/documents', icon: <FileTextOutlined />, label: '规范与文档' },
        { key: '/tools', icon: <ToolOutlined />, label: '工具与组件库' },
      ],
    },
    {
      label: '系统与管理',
      key: 'system',
      type: 'group' as const,
      children: [
        { key: '/teams', icon: <TeamOutlined />, label: '团队架构' },
        { key: '/approval-scopes', icon: <AuditOutlined />, label: '审批范围' },
        { key: '/users', icon: <UserOutlined />, label: '账号管理' },
        { key: '/roles', icon: <SettingOutlined />, label: '角色与权限' },
        // 审计日志仅管理层可见（后端 @PreAuthorize 限 admin / project_leader）
        ...(managerEligible === false
          ? []
          : [{ key: '/audit-logs', icon: <FileSearchOutlined />, label: '审计日志' }]),
        // 数据导入：后端 /import/assets、/import/vulnerabilities 仅要求登录，所有角色可用
        { key: '/import', icon: <CloudUploadOutlined />, label: '数据导入' },
        { key: '/notifications', icon: <BellOutlined />, label: (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            系统消息
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
        label: '退出登录',
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
        <Header style={{ padding: '0 24px', background: '#fff', display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
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
