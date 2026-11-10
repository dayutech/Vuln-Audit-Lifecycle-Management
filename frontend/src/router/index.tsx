import React from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import BasicLayout from '@/layouts/BasicLayout';
import Login from '@/pages/Login';
import Dashboard from '@/pages/Dashboard';
import Customers from '@/pages/customers/index';
import CustomerDetail from '@/pages/customers/Detail';
import CustomerForm from '@/pages/customers/CustomerForm';
import Assets from '@/pages/assets/index';
import AssetEditorPage from '@/pages/assets/AssetEditorPage';
import AssetDetail from '@/pages/assets/Detail';
import Vulnerabilities from '@/pages/vulnerabilities/index';
import VulnerabilityEditorPage from '@/pages/vulnerabilities/VulnerabilityEditorPage';
import VulnerabilityDetailPage from '@/pages/vulnerabilities/VulnerabilityDetailPage';
import Teams from '@/pages/teams/index';
import Roles from '@/pages/roles/index';
import Users from '@/pages/users/index';
import Documents from '@/pages/documents/index';
import DocumentEditorPage from '@/pages/documents/DocumentEditorPage';
import DocumentDetailPage from '@/pages/documents/DocumentDetailPage';
import DocumentUploadPage from '@/pages/documents/DocumentUploadPage';
import Tools from '@/pages/tools/index';
import Notifications from '@/pages/notifications/index';
import Knowledge from '@/pages/knowledge/index';
import KnowledgeEditorPage from '@/pages/knowledge/KnowledgeEditorPage';
import KnowledgeDetailPage from '@/pages/knowledge/KnowledgeDetailPage';
import KnowledgeUploadPage from '@/pages/knowledge/KnowledgeUploadPage';
import Tasks from '@/pages/tasks/index';
import Approvals from '@/pages/approvals/index';
import ApprovalScopes from '@/pages/approval-scopes/index';
import AuditLogs from '@/pages/audit-logs/index';
import { ErrorPage } from '@/components/Feedback';
import ImportPage from '@/pages/import/index';
import Projects from '@/pages/projects/index';
// 别名：页面组件与路由路径同名，避免与 /projects 列表页的 import 冲突
import ProjectFormPage from '@/pages/projects/ProjectForm';
import ProjectDetail from '@/pages/projects/Detail';
import Settings from '@/pages/settings/index';
import UploadApprovals from '@/pages/admin/UploadApprovals';
import Register from '@/pages/Register';
import ForgotPassword from '@/pages/ForgotPassword';
import SsoCallback from '@/pages/SsoCallback';
import Unlock from '@/pages/Unlock';
import SsoBindings from '@/pages/account/SsoBindings';
import RegistrationRequests from '@/pages/registration-requests/index';
import TagsAdmin from '@/pages/tags/index';
import AccountSecurity from '@/pages/account/Security';

const PrivateRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = localStorage.getItem('access_token');
  return token ? <>{children}</> : <Navigate to="/login" />;
};

const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />,
  },
  // t105：登录页的自助入口（公开；与登录页共用主题外壳与 formSkin）
  {
    path: '/register',
    element: <Register />,
  },
  {
    path: '/forgot-password',
    element: <ForgotPassword />,
  },
  // t106：第三方登录回调（公开；provider 由发起时写入 sessionStorage，回调地址对四家统一）
  {
    path: '/auth/sso/callback',
    element: <SsoCallback />,
  },
  // t127：账户锁定后的 OTP 解锁（公开，未登录也能访问）
  {
    path: '/unlock',
    element: <Unlock />,
  },
  {
    path: '/',
    element: (
      <PrivateRoute>
        <BasicLayout />
      </PrivateRoute>
    ),
    children: [
      {
        path: '/',
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: '/dashboard',
        element: <Dashboard />,
      },
      {
        path: '/customers',
        element: <Customers />,
      },
      // t142：客户新建/详情/编辑统一为独立页面（原 Drawer 全部下线）
      {
        path: '/customers/new',
        element: <CustomerForm />,
      },
      {
        path: '/customers/:id',
        element: <CustomerDetail />,
      },
      {
        path: '/customers/:id/edit',
        element: <CustomerForm />,
      },
      {
        path: '/projects',
        element: <Projects />,
      },
      // t142：项目新建/编辑改为独立页面（原 ProjectFormModal 下线），详情页保留
      {
        path: '/projects/new',
        element: <ProjectFormPage />,
      },
      {
        path: '/projects/:id',
        element: <ProjectDetail />,
      },
      {
        path: '/projects/:id/edit',
        element: <ProjectFormPage />,
      },
      {
        path: '/assets',
        element: <Assets />,
      },
      // t168：资产新建 / 编辑统一为整页（原弹框已移除）
      { path: '/assets/new', element: <AssetEditorPage /> },
      { path: '/assets/:id/edit', element: <AssetEditorPage /> },
      {
        path: '/assets/:id',
        element: <AssetDetail />,
      },
      {
        // t143：资产分组已并入标签体系（V30 迁移）；旧书签重定向到资产中心
        path: '/asset-groups',
        element: <Navigate to="/assets" replace />,
      },
      {
        path: '/vulnerabilities',
        element: <Vulnerabilities />,
      },
      // t168：漏洞详情与编辑统一为整页（原 Drawer / 弹框已移除）
      { path: '/vulnerabilities/new', element: <VulnerabilityEditorPage /> },
      {
        path: '/vulnerabilities/:id',
        element: <VulnerabilityDetailPage />,
      },
      {
        path: '/vulnerabilities/:id/edit',
        element: <VulnerabilityEditorPage />,
      },
      {
        path: '/tasks',
        element: <Tasks />,
      },
      {
        path: '/tasks/:id',
        element: <Tasks />,
      },
      {
        path: '/approvals',
        element: <Approvals />,
      },
      {
        path: '/documents',
        element: <Documents />,
      },
      // t159（需求 14）：规范文档的整页新建 / 编辑 / 详情 / 上传
      { path: '/documents/new', element: <DocumentEditorPage /> },
      { path: '/documents/upload', element: <DocumentUploadPage /> },
      { path: '/documents/:id/edit', element: <DocumentEditorPage /> },
      { path: '/documents/:id', element: <DocumentDetailPage /> },
      {
        path: '/tools',
        element: <Tools />,
      },
      {
        path: '/knowledge',
        element: <Knowledge />,
      },
      // t159（需求 13/15）：知识库的整页新建 / 编辑 / 详情 / 上传（原弹框已移除）
      { path: '/knowledge/new', element: <KnowledgeEditorPage /> },
      { path: '/knowledge/upload', element: <KnowledgeUploadPage /> },
      { path: '/knowledge/:id/edit', element: <KnowledgeEditorPage /> },
      { path: '/knowledge/:id', element: <KnowledgeDetailPage /> },
      {
        path: '/teams',
        element: <Teams />,
      },
      {
        path: '/approval-scopes',
        element: <ApprovalScopes />,
      },
      {
        path: '/roles',
        element: <Roles />,
      },
      {
        path: '/users',
        element: <Users />,
      },
      {
        path: '/notifications',
        element: <Notifications />,
      },
      {
        path: '/audit-logs',
        element: <AuditLogs />,
      },
      {
        path: '/import',
        element: <ImportPage />,
      },
      {
        // t110：系统设置（登录页默认主题；仅管理员，页面内再做一次画像判定）
        path: '/settings',
        element: <Settings />,
      },
      {
        // t137：账号安全（两步验证）——所有登录用户可用；强制 2FA 引导也指向这里
        path: '/account/security',
        element: <AccountSecurity />,
      },
      {
        // t114：注册审批（仅 ADMIN / PROJECT_LEADER；页面内再判一次，非授权角色显示 403）
        path: '/admin/registration-requests',
        element: <RegistrationRequests />,
      },
      {
        // t143：标签定义管理（仅 ADMIN；页面内再判一次）
        path: '/admin/tags',
        element: <TagsAdmin />,
      },
      {
        // t161：大文件上传审批（仅 ADMIN；页面内再判一次，非管理员显示 403 提示）
        path: '/admin/upload-approvals',
        element: <UploadApprovals />,
      },
      {
        // t106：账号设置 - 社交账号绑定（登录用户都可访问，只操作自己）
        path: '/account/sso-bindings',
        element: <SsoBindings />,
      },
      // 403 / 404：i18n 化通用组件；未匹配路径落到 404，避免空白页
      {
        path: '/403',
        element: <ErrorPage status="403" />,
      },
      {
        path: '/404',
        element: <ErrorPage status="404" />,
      },
      {
        path: '*',
        element: <ErrorPage status="404" />,
      },
    ],
  },
]);

export default router;






