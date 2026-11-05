import React, { useEffect, useMemo, useState } from 'react';
import { Layout, Menu, Dropdown, Avatar, Badge } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { EyeOutlined, BankOutlined, ProjectOutlined, UserOutlined, LogoutOutlined, DashboardOutlined, SafetyCertificateOutlined, AppstoreOutlined, TeamOutlined, SettingOutlined, FileTextOutlined, ToolOutlined, BellOutlined, ReadOutlined, CheckSquareOutlined, AuditOutlined, FileSearchOutlined, CloudUploadOutlined, BgColorsOutlined, ControlOutlined, LinkOutlined } from '@ant-design/icons';
import { useUserStore } from '@/store/userStore';
import { getCurrentUser, logout } from '@/api/auth';
import { getUnreadNotificationCount } from '@/api/notification';
import { getMyAccessProfile, isManagementProfile } from '@/api/permission';
import { getPendingRegistrationCount, REGISTRATION_REVIEW_CHANGED_EVENT } from '@/api/registrationReview';
import { useT } from '@/i18n';
import ImpersonationBanner from './ImpersonationBanner';
import ImpersonationPicker from './ImpersonationPicker';
import { isImpersonating } from '@/utils/impersonation';
import LoginThemePreferenceModal from '@/components/preferences/LoginThemePreferenceModal';
import LangSwitcher from '@/i18n/LangSwitcher';
import AppBrand from './AppBrand';
import { collectMenuKeys, collectOpenKeys, selectMenuKey } from './menuSelection';

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
  /** t91：当前账号是否 ADMIN（决定「以其他用户视角查看」入口是否可见） */
  const [isAdmin, setIsAdmin] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  /** t97：偏好设置弹窗（登录页主题） */
  const [prefsOpen, setPrefsOpen] = useState(false);
  /** t114：待审批注册申请数量（侧边栏角标）；null = 未知/加载失败 → 不显示角标 */
  const [pendingRegistrations, setPendingRegistrations] = useState<number | null>(null);
  /** 模拟态：后端 token 内已带 imp claim，这里读本地标记决定横幅与入口隐藏 */
  const impersonating = isImpersonating();
  const { t } = useT();

  // t114：待审批注册申请数量（静默请求；失败保持 null → 不显示角标，而不是显示 0 误导）
  useEffect(() => {
    if (managerEligible === false) {
      setPendingRegistrations(null);
      return;
    }
    let cancelled = false;
    const refresh = () => {
      getPendingRegistrationCount().then((count) => {
        if (!cancelled) setPendingRegistrations(count);
      });
    };
    refresh();
    const handler = () => refresh();
    window.addEventListener(REGISTRATION_REVIEW_CHANGED_EVENT, handler);
    return () => {
      cancelled = true;
      window.removeEventListener(REGISTRATION_REVIEW_CHANGED_EVENT, handler);
    };
  }, [managerEligible]);

  useEffect(() => {
    let cancelled = false;
    getMyAccessProfile().then((profile) => {
      if (cancelled) return;
      // 画像请求失败时 roles/permissions 均为空；此时保持 null（未知）而不是判定为非管理层
      const known = profile.roles.length > 0 || profile.permissions.length > 0;
      setManagerEligible(known ? isManagementProfile(profile) : null);
      setIsAdmin(profile.roles.includes('admin'));
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
          /**
           * t110：`GET /auth/me` **不再**同步登录页主题的本机标记。
           *
           * v2 语义：本机标记只由用户在偏好设置里主动选择 / 从账号同步 / 跟随默认驱动；
           * 登录成功与 `/auth/me` 一律不写盘（跨设备不自动生效是设计选择，
           * 需要时由用户自己点「从账号同步到本机」）。因此这里刻意不做任何处理。
           */
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
                { key: '/customers', icon: <BankOutlined />, label: t('menu.customers') },
                { key: '/projects', icon: <ProjectOutlined />, label: t('menu.projects') },
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
        // 系统设置（t110）：仅管理员（后端 /admin/settings/** 也只放行 ADMIN，页面内再兜一次 403）
        ...(isAdmin ? [{ key: '/settings', icon: <ControlOutlined />, label: t('menu.settings') }] : []),
        // 注册审批（t114）：仅管理层（admin / project_leader，与后端 hasAnyRole 一致）+ 待审批角标
        ...(managerEligible === false
          ? []
          : [
              {
                key: '/admin/registration-requests',
                icon: <AuditOutlined />,
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }} data-testid="menu-registration-review">
                    {t('registrationReview.menu')}
                    {pendingRegistrations !== null && pendingRegistrations > 0 ? (
                      <Badge count={pendingRegistrations} size="small" overflowCount={99} data-testid="menu-registration-badge" />
                    ) : null}
                  </span>
                ),
              },
            ]),
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
      // t91：模拟视角入口仅 ADMIN 可见；模拟态下隐藏（后端也禁止链式模拟）
      ...(isAdmin && !impersonating
        ? [
            {
              key: 'impersonate',
              icon: <EyeOutlined />,
              label: t('impersonation.menu'),
              onClick: () => setPickerOpen(true),
            },
          ]
        : []),
      // t97：偏好设置（登录页主题）——所有登录用户都可以改自己的偏好
      {
        key: 'preferences',
        icon: <BgColorsOutlined />,
        label: t('preferences.menu'),
        onClick: () => setPrefsOpen(true),
      },
      // t106：社交账号绑定（SSO），登录用户都可管理自己的绑定
      {
        key: 'sso-bindings',
        icon: <LinkOutlined />,
        label: t('ssoBinding.menu'),
        onClick: () => navigate('/account/sso-bindings'),
      },
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: t('header.logout'),
        onClick: handleLogout,
      },
    ],
  };
  /**
   * 菜单 key 从 menuItems 树里收集（t89）：
   * 原实现是手写数组，新增「客户管理 / 项目管理 / 审批中心」时漏同步，
   * 导致这三个路由匹配不到、selectedKeys 退回 /dashboard（点了不变蓝）。
   */
  const menuKeys = useMemo(() => collectMenuKeys(menuItems as any), [menuItems]);
  const selectedMenuKey = useMemo(
    () => selectMenuKey(location.pathname, menuKeys),
    [location.pathname, menuKeys],
  );
  /** 分组恒展开（type: 'group'），这里为将来的 SubMenu 预留父级展开逻辑，当前返回空数组 */
  const openKeys = useMemo(() => collectOpenKeys(menuItems as any, selectedMenuKey), [menuItems, selectedMenuKey]);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible collapsed={collapsed} onCollapse={(value) => setCollapsed(value)}>
        <AppBrand collapsed={collapsed} />
        <Menu
          theme="dark"
          selectedKeys={[selectedMenuKey]}
          {...(openKeys.length > 0 ? { openKeys } : {})}
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
          <ImpersonationBanner />
          <Outlet />
        </Content>
      </Layout>
      <ImpersonationPicker open={pickerOpen} onClose={() => setPickerOpen(false)} />
      {/* t97：偏好设置弹窗按需挂载（每次打开都重新读服务端偏好，避免复用旧状态） */}
      {prefsOpen ? <LoginThemePreferenceModal onClose={() => setPrefsOpen(false)} /> : null}
    </Layout>
  );
};

export default BasicLayout;
