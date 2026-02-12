import React from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Login from './pages/Login';
import Register from './pages/Register';
import ChatPage from './pages/ChatPage';
import TicketListPage from './pages/admin/TicketListPage';
import NotifyCenterPage from './pages/NotifyCenterPage';
import { getRole, getToken } from './utils/storage';
import { AdminLayout } from './components/layout/AdminLayout';
import { CustomerLayout } from './components/layout/CustomerLayout';
import { AgentLayout } from './components/layout/AgentLayout';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import AdminSessionsPage from './pages/admin/AdminSessionsPage';
import CustomerFaqPage from './pages/customer/CustomerFaqPage';
import KnowledgePage from './pages/admin/KnowledgePage';
import UserManagementPage from './pages/admin/UserManagementPage';
import AgentTicketPage from './pages/agent/AgentTicketPage';
import AdminHandoffPage from './pages/admin/AdminHandoffPage';

import AgentProfilePage from './pages/agent/AgentProfilePage';
import AgentKnowledgePage from './pages/agent/AgentKnowledgePage';
import AgentWorkspacePage from './pages/agent/AgentWorkspacePage';
import AgentSessionsPage from './pages/agent/AgentSessionsPage';
// 1. 导入新页面 AgentWorkbenchPage
import AgentWorkbenchPage from './pages/agent/AgentWorkbenchPage';

function RequireAuth({ children }: { children: JSX.Element }) {
  const location = useLocation();
  const token = getToken();
  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return children;
}

function HomeRedirect() {
  const token = getToken();
  const role = getRole();
  if (!token) return <Navigate to="/login" replace />;

  // 根据角色跳转
  if (role === 'USER') {
    return <Navigate to="/customer/chat" replace />;
  } else if (role === 'ADMIN') {
    return <Navigate to="/admin/dashboard" replace />;
  } else if (role === 'AGENT') {
    // 2. 修改默认跳转路径为 workbench
    return <Navigate to="/agent/workbench" replace />;
  }

  return <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      
      {/* 注意：你原本有一个单独的 /agent 路由，如果使用了 AgentLayout 包含 Outlet 的嵌套路由结构，这行可能有用；
          但在你下方的代码模式中（Layout 包裹 Page），这行可能是多余的，但我先保留它以免破坏原有逻辑 */}
      <Route path="/agent" element={<AgentLayout />} />

      {/* C端（客户） */}
      <Route
        path="/customer/chat"
        element={
          <RequireAuth>
            <CustomerLayout>
              <ChatPage />
            </CustomerLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/customer/faq"
        element={
          <RequireAuth>
            <CustomerLayout>
              <CustomerFaqPage />
            </CustomerLayout>
          </RequireAuth>
        }
      />

      {/* B端（租户/管理员） */}
      <Route
        path="/admin/dashboard"
        element={
          <RequireAuth>
            <AdminLayout>
              <AdminDashboardPage />
            </AdminLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/admin/tickets"
        element={
          <RequireAuth>
            <AdminLayout>
              <TicketListPage />
            </AdminLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/admin/sessions"
        element={
          <RequireAuth>
            <AdminLayout>
              <AdminSessionsPage />
            </AdminLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/admin/knowledge"
        element={
          <RequireAuth>
            <AdminLayout>
              <KnowledgePage />
            </AdminLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/admin/handoff"
        element={
          <RequireAuth>
            <AdminLayout>
              <AdminHandoffPage />
            </AdminLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/admin/notify"
        element={
          <RequireAuth>
            <AdminLayout>
              <NotifyCenterPage />
            </AdminLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/admin/users"
        element={
          <RequireAuth>
            <AdminLayout>
              <UserManagementPage />
            </AdminLayout>
          </RequireAuth>
        }
      />

       {/* B端（客服） */}
      <Route
        path="/agent/tickets"
        element={
          <RequireAuth>
            <AgentLayout>
              <AgentTicketPage />
            </AgentLayout>
          </RequireAuth>
        }
      />

      <Route
        path="/agent/sessions"
        element={
          <RequireAuth>
            <AgentLayout>
              <AgentSessionsPage />
            </AgentLayout>
          </RequireAuth>
        }
      />
      
      {/* 客服工作台 */}
      <Route
        path="/agent/workbench"
        element={
          <RequireAuth>
            <AgentLayout>
              <AgentWorkbenchPage />
            </AgentLayout>
          </RequireAuth>
        }
      />

      <Route
        path="/agent/profile"
        element={
          <RequireAuth>
            <AgentLayout>
              <AgentProfilePage />
            </AgentLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/agent/knowledge"
        element={
          <RequireAuth>
            <AgentLayout>
              <AgentKnowledgePage />
            </AgentLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/agent/notify"
        element={
          <RequireAuth>
            <AgentLayout>
              <NotifyCenterPage />
            </AgentLayout>
          </RequireAuth>
        }
      />
      
      {/* 如果 Workspace 和 Workbench 是同一个功能，可以考虑删除下面这个路由。
          暂时保留以防是两个不同的页面 */}
      <Route
        path="/agent/workspace"
        element={
          <RequireAuth>
            <AgentLayout>
              <React.Suspense fallback={<div>加载中...</div>}>
                <AgentWorkspacePage />
              </React.Suspense>
            </AgentLayout>
          </RequireAuth>
        }
      />
      
      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  );
}
