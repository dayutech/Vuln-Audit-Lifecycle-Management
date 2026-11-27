import React, { useEffect, useMemo, useState } from 'react';
import { Layout, Menu, Dropdown, Avatar, Badge, Button, Drawer, Tooltip } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { MenuOutlined, EyeOutlined, BankOutlined, ProjectOutlined, UserOutlined, LogoutOutlined, DashboardOutlined, SafetyCertificateOutlined, AppstoreOutlined, TeamOutlined, FileTextOutlined, ToolOutlined, BellOutlined, ReadOutlined, CheckSquareOutlined, AuditOutlined, BgColorsOutlined, ControlOutlined, LinkOutlined , FormOutlined } from '@ant-design/icons';
import { useUserStore } from '@/store/userStore';
import { getCurrentUser, logout } from '@/api/auth';
import { getUnreadNotificationCount } from '@/api/notification';
import { getMyAccessProfile, isManagementProfile } from '@/api/permission';
import { getPendingRegistrationCount, REGISTRATION_REVIEW_CHANGED_EVENT } from '@/api/registrationReview';
import { endLoggingOut, markLoggingOut } from '@/utils/session';
import { useT } from '@/i18n';
import ImpersonationBanner from './ImpersonationBanner';
import ImpersonationPicker from './ImpersonationPicker';
import { isImpersonating } from '@/utils/impersonation';
import LoginThemePreferenceModal from '@/components/preferences/LoginThemePreferenceModal';
import TwoFactorRequiredBanner from '@/components/twoFactor/TwoFactorRequiredBanner';
import LangSwitcher from '@/i18n/LangSwitcher';
import AppBrand from './AppBrand';
import { collectMenuKeys, collectOpenKeys, selectMenuKey } from './menuSelection';
import './layout.css';

const { Header, Sider, Content } = Layout;

const BasicLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, setUser, clearAuth } = useUserStore();
  const [collapsed, setCollapsed] = useState(false);
  /** t144：窄屏（antd Sider breakpoint=lg）标记，决定是否渲染汉堡按钮 / 抽屉导航 */
  const [isMobile, setIsMobile] = useState(false);
  /** t144：窄屏抽屉导航开关 */
  const [navOpen, setNavOpen] = useState(false);
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

  /**
   * 退出登录（t122，对应意见 18）。
   *
   * 顺序很重要：**先**标记「正在登出」（401 拦截器据此静默），再请求 /auth/logout，
   * 最后清 token + 跳 /login。这样登出瞬间的在飞请求（通知未读数、仪表盘轮询等）
   * 即使拿到 401 也不会弹「未认证，请先登录」，也不会重复跳转。
   */
  const handleLogout = async () => {
    markLoggingOut();
    try {
      await logout();
    } catch {
      /* 登出接口失败不影响本地登出：token 照清、照跳登录页 */
    } finally {
      clearAuth();
      navigate('/login', { replace: true });
      // 跳转完成即收尾；残留的在飞请求在 401 分支里已被静默
      window.setTimeout(endLoggingOut, 600);
    }
  };

  /**
   * 侧边栏菜单（t191：系统与管理重组）。
   *
   * 结构约定见 `docs/design/menu-restructure.md`：
   * - 三个固定分组（核心业务 / 知识与资源 / 系统与管理）仍是 `type: 'group'`；
   * - 「系统与管理」内部改为 4 个可折叠子菜单 + 1 个独立项（系统消息），
   *   以后新增功能按「归位规则」进子菜单，不再平铺；
   * - 子菜单的子项不带图标：父级一个图标已足够区分，同时消除同类功能的重复图标。
   */
  type NavLeaf = { key: string; label: React.ReactNode };
  type NavSubMenu = { key: string; icon: React.ReactNode; label: React.ReactNode; children: NavLeaf[] };
  type NavTop = NavSubMenu | { key: string; icon: React.ReactNode; label: React.ReactNode };

  /** 可见子项为空时整个子菜单不渲染（角色裁剪后不留空壳） */
  const buildSubMenu = (key: string, icon: React.ReactNode, label: string, children: NavLeaf[]): NavSubMenu | null =>
    children.length > 0 ? { key, icon, label, children } : null;

  /** 注册审批入口（管理层可见；带待审批数量角标） */
  const registrationReviewItem: NavLeaf = {
    key: '/admin/registration-requests',
    label: (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }} data-testid="menu-registration-review">
        {t('registrationReview.menu')}
        {pendingRegistrations !== null && pendingRegistrations > 0 ? (
          <Badge count={pendingRegistrations} size="small" overflowCount={99} data-testid="menu-registration-badge" />
        ) : null}
      </span>
    ),
  };

  const orgChildren: NavLeaf[] = [
    { key: '/teams', label: t('menu.teams') },
    // 账号管理 / 角色与权限 / 注册审批：普通角色进入只会 403（后端 class-level 为 hasAnyRole ADMIN/PROJECT_LEADER）
    ...(managerEligible === false ? [] : [{ key: '/users', label: t('menu.users') }]),
    ...(managerEligible === false ? [] : [{ key: '/roles', label: t('menu.roles') }]),
    ...(managerEligible === false ? [] : [registrationReviewItem]),
  ];

  const governanceChildren: NavLeaf[] = [
    // t194：审批中心（资产访问审批）与上传审批合并为一页，统一挂在「审批与合规」下
    { key: '/approvals', label: t('menu.approvals') },
    { key: '/approval-scopes', label: t('menu.approvalScopes') },
    ...(managerEligible === false ? [] : [{ key: '/audit-logs', label: t('menu.auditLogs') }]),
  ];

  const formChildren: NavLeaf[] = [
    // t191：这 3 项自「知识与资源」移入，与模板管理同属表单生命周期
    { key: '/forms/assigned', label: t('menu.formsAssigned') },
    { key: '/forms/mine', label: t('menu.formsMine') },
    { key: '/forms/new', label: t('menu.formsNew') },
    ...(isAdmin ? [{ key: '/admin/form-definitions', label: t('menu.formDefinitions') }] : []),
  ];

  const platformChildren: NavLeaf[] = [
    ...(isAdmin ? [{ key: '/settings', label: t('menu.settings') }] : []),
    ...(isAdmin ? [{ key: '/admin/tags', label: t('menu.tags') }] : []),
    { key: '/import', label: t('menu.import') },
  ];

  const rawSystemChildren: Array<NavTop | null> = [
    buildSubMenu('sys.org', <TeamOutlined />, t('menu.groupOrg'), orgChildren),
    buildSubMenu('sys.governance', <AuditOutlined />, t('menu.groupGovernance'), governanceChildren),
    buildSubMenu('sys.forms', <FormOutlined />, t('menu.groupForms'), formChildren),
    buildSubMenu('sys.platform', <ControlOutlined />, t('menu.groupPlatform'), platformChildren),
    {
      key: '/notifications',
      icon: <BellOutlined />,
      label: (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          {t('menu.notifications')}
          {unreadCount > 0 && <Badge count={unreadCount} size="small" overflowCount={99} />}
        </span>
      ),
    },
  ];
  const systemChildren: NavTop[] = rawSystemChildren.filter((item): item is NavTop => item !== null);
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
                // t153（用户需求 11）：安全审计任务排在全局漏洞视图**之前**
                { key: '/tasks', icon: <CheckSquareOutlined />, label: t('menu.tasks') },
                { key: '/vulnerabilities', icon: <SafetyCertificateOutlined />, label: t('menu.vulnerabilities') },
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
      children: systemChildren,
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
      // t137：账号安全（两步验证）——所有登录用户自助；强制 2FA 引导也指向这里
      {
        key: 'account-security',
        icon: <SafetyCertificateOutlined />,
        label: t('twoFactor.menu'),
        onClick: () => navigate('/account/security'),
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
  /**
   * t191：`/forms/{id}` 是表单详情，不属于「生成表单 / 我发起的 / 待我处理」任一项。
   * 此前它会匹配失败并退回 `/dashboard`（侧栏高亮跑到「安全态势大盘」）；
   * 现在改为「表单中心保持展开、不选中任何叶子」，语义更诚实。
   */
  const isFormDetailPath = useMemo(() => {
    const path = location.pathname.split('?')[0].split('#')[0];
    return /^\/forms\/[^/]+$/.test(path) && !['/forms/new', '/forms/mine', '/forms/assigned'].includes(path);
  }, [location.pathname]);
  const selectedMenuKey = useMemo(
    () => (isFormDetailPath ? '' : selectMenuKey(location.pathname, menuKeys)),
    [location.pathname, menuKeys, isFormDetailPath],
  );
  /**
   * 子菜单展开态（t191）。
   *
   * - 进入某个子菜单下的页面时，把该子菜单**并入**已展开集合（不关闭用户手动展开的其它子菜单）；
   * - effect 依赖用「签名串」而不是数组：menuItems 每次渲染都是新数组，
   *   若直接依赖数组会导致 setState → 重渲染 → 再触发的死循环。
   */
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const routeOpenKeys = useMemo(
    () => collectOpenKeys(menuItems as any, selectedMenuKey),
    [menuItems, selectedMenuKey],
  );
  const routeOpenKeysSignature = isFormDetailPath
    ? 'sys.forms'
    : routeOpenKeys.join('|');
  useEffect(() => {
    if (!routeOpenKeysSignature) return;
    const next = routeOpenKeysSignature.split('|');
    setOpenKeys((prev) => {
      const merged = Array.from(new Set([...prev, ...next]));
      return merged.length === prev.length ? prev : merged;
    });
  }, [routeOpenKeysSignature]);
  const handleOpenChange = (keys: string[]) => setOpenKeys(keys);

  return (
    <Layout className={`basic-layout${isMobile ? ' basic-layout--mobile' : ''}`}>
      {/* t144：窄屏（<lg）自动收起为 0 宽（内容区不再被 200px 侧栏挤压），导航改由顶栏汉堡 + 抽屉承载 */}
      <Sider
        collapsible
        breakpoint="lg"
        collapsedWidth={0}
        collapsed={isMobile ? true : collapsed}
        onBreakpoint={(broken) => {
          setIsMobile(broken);
          if (!broken) setNavOpen(false);
        }}
        onCollapse={(value, type) => {
          // responsive 触发的收起/展开由 onBreakpoint 统一管理，避免与宽屏折叠记忆互相覆盖
          if (type === 'responsive') {
            setIsMobile(value);
            return;
          }
          setCollapsed(value);
        }}
        trigger={null}
        width={200}
      >
        <AppBrand collapsed={isMobile ? false : collapsed} />
        <Menu
          theme="dark"
          selectedKeys={selectedMenuKey ? [selectedMenuKey] : []}
          openKeys={openKeys}
          onOpenChange={handleOpenChange}
          mode="inline"
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header className="basic-layout__header">
          <div className="basic-layout__header-left">
            {isMobile ? (
              <Button
                type="text"
                className="basic-layout__hamburger"
                icon={<MenuOutlined />}
                aria-label={t('layout.openMenu')}
                onClick={() => setNavOpen(true)}
                data-testid="nav-hamburger"
              />
            ) : null}
          </div>
          <div className="basic-layout__header-right">
            {/*
              t191：顶栏铃铛 + 侧栏「系统消息」是同一份 unreadCount 的两个入口（联动）：
              任一处标记已读都会通过 notifications:unread-changed 事件同时刷新两边角标。
            */}
            <Tooltip title={t('menu.notifications')}>
              <span
                className="basic-layout__bell"
                data-testid="header-notifications-badge"
                data-count={unreadCount}
              >
                <Badge count={unreadCount} size="small" overflowCount={99}>
                  <Button
                    type="text"
                    aria-label={t('menu.notifications')}
                    data-testid="header-notifications"
                    icon={<BellOutlined style={{ fontSize: 17 }} />}
                    onClick={() => navigate('/notifications')}
                  />
                </Badge>
              </span>
            </Tooltip>
            <LangSwitcher />
            <Dropdown menu={userMenu} placement="bottomRight">
              <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar icon={<UserOutlined />} src={user?.avatar} />
                <span>{user?.username || '-'}</span>
              </div>
            </Dropdown>
          </div>
        </Header>
        <Content className="basic-layout__content">
          {/* t137：强制 2FA 时不可关闭的引导横幅（监听 403 TWO_FACTOR_SETUP_REQUIRED 事件） */}
          <TwoFactorRequiredBanner />
          <ImpersonationBanner />
          <Outlet />
        </Content>
      </Layout>

      {/* t144：窄屏抽屉导航 —— 复用同一份 menuItems 与 t89 的 selectedMenuKey，不另起匹配逻辑 */}
      <Drawer
        placement="left"
        width={248}
        open={navOpen}
        onClose={() => setNavOpen(false)}
        closable={false}
        title={null}
        rootClassName="basic-layout__nav-drawer"
        styles={{ body: { padding: 0, background: '#001529' } }}
        data-testid="nav-drawer"
      >
        <div className="basic-layout__nav-brand">
          <AppBrand collapsed={false} />
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedMenuKey ? [selectedMenuKey] : []}
          openKeys={openKeys}
          onOpenChange={handleOpenChange}
          items={menuItems}
          onClick={({ key }) => {
            navigate(key);
            setNavOpen(false);
          }}
          data-testid="nav-drawer-menu"
        />
      </Drawer>

      <ImpersonationPicker open={pickerOpen} onClose={() => setPickerOpen(false)} />
      {/* t97：偏好设置弹窗按需挂载（每次打开都重新读服务端偏好，避免复用旧状态） */}
      {prefsOpen ? <LoginThemePreferenceModal onClose={() => setPrefsOpen(false)} /> : null}
    </Layout>
  );
};

export default BasicLayout;







