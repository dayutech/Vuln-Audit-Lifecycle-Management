import React from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import BasicLayout from '@/layouts/BasicLayout';
import Login from '@/pages/Login';
import Dashboard from '@/pages/Dashboard';
import Customers from '@/pages/customers/index';
import Assets from '@/pages/assets/index';
import AssetDetail from '@/pages/assets/Detail';
import Vulnerabilities from '@/pages/vulnerabilities/index';
import Teams from '@/pages/teams/index';
import Roles from '@/pages/roles/index';
import Users from '@/pages/users/index';
import Documents from '@/pages/documents/index';
import Tools from '@/pages/tools/index';
import Notifications from '@/pages/notifications/index';
import Knowledge from '@/pages/knowledge/index';
import Tasks from '@/pages/tasks/index';
import Approvals from '@/pages/approvals/index';
import AssetGroups from '@/pages/asset-groups/index';
import ApprovalScopes from '@/pages/approval-scopes/index';
import AuditLogs from '@/pages/audit-logs/index';
import { ErrorPage } from '@/components/Feedback';
import ImportPage from '@/pages/import/index';
import Projects from '@/pages/projects/index';
import ProjectDetail from '@/pages/projects/Detail';

const PrivateRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = localStorage.getItem('access_token');
  return token ? <>{children}</> : <Navigate to="/login" />;
};

const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />,
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
      {
        path: '/projects',
        element: <Projects />,
      },
      {
        path: '/projects/:id',
        element: <ProjectDetail />,
      },
      {
        path: '/assets',
        element: <Assets />,
      },
      {
        path: '/assets/:id',
        element: <AssetDetail />,
      },
      {
        path: '/asset-groups',
        element: <AssetGroups />,
      },
      {
        path: '/vulnerabilities',
        element: <Vulnerabilities />,
      },
      {
        path: '/vulnerabilities/:id',
        element: <Vulnerabilities />,
      },
      {
        path: '/vulnerabilities/:id/edit',
        element: <Vulnerabilities />,
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
      {
        path: '/tools',
        element: <Tools />,
      },
      {
        path: '/knowledge',
        element: <Knowledge />,
      },
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
