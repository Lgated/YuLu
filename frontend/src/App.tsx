import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Login from './pages/Login';
import Register from './pages/Register';
import ChatPage from './pages/ChatPage';
import TicketListPage from './pages/TicketListPage';
import NotifyCenterPage from './pages/NotifyCenterPage';
import { getRole, getToken } from './utils/storage';
import { AdminLayout } from './components/layout/AdminLayout';
import { CustomerLayout } from './components/layout/CustomerLayout';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import AdminSessionsPage from './pages/admin/AdminSessionsPage';
import CustomerFaqPage from './pages/customer/CustomerFaqPage';

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
  // USER -> C端；ADMIN/AGENT -> B端
  return <Navigate to={role === 'USER' ? '/customer/chat' : '/admin/dashboard'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
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

      {/* B端（租户/客服） */}
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
        path="/admin/notify"
        element={
          <RequireAuth>
            <AdminLayout>
              <NotifyCenterPage />
            </AdminLayout>
          </RequireAuth>
        }
      />

      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  );
}



