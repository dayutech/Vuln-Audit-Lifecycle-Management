import React, { useEffect, useMemo, useState } from 'react';
import { Layout, Menu, Dropdown, Avatar } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { UserOutlined, LogoutOutlined, DashboardOutlined, SafetyCertificateOutlined, AppstoreOutlined, TeamOutlined, SettingOutlined, FileTextOutlined, ToolOutlined, BellOutlined, ReadOutlined, CheckSquareOutlined } from '@ant-design/icons';
import { useUserStore } from '@/store/userStore';
import { getCurrentUser, logout } from '@/api/auth';

const { Header, Sider, Content } = Layout;

const BasicLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, setUser, clearAuth } = useUserStore();
  const [collapsed, setCollapsed] = useState(false);

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
        { key: '/vulnerabilities', icon: <SafetyCertificateOutlined />, label: '全局漏洞视图' },
        { key: '/tasks', icon: <CheckSquareOutlined />, label: '安全审计任务' },
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
        { key: '/users', icon: <UserOutlined />, label: '账号管理' },
        { key: '/roles', icon: <SettingOutlined />, label: '角色与权限' },
        { key: '/notifications', icon: <BellOutlined />, label: '系统消息' },
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
      '/vulnerabilities',
      '/tasks',
      '/knowledge',
      '/documents',
      '/tools',
      '/teams',
      '/users',
      '/roles',
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
              <span>{user?.full_name || user?.username}</span>
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
