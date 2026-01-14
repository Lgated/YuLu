import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Login from './pages/Login';
import ChatPage from './pages/ChatPage';
import TicketListPage from './pages/TicketListPage';
import NotifyCenterPage from './pages/NotifyCenterPage';
import { getToken } from './utils/storage';

function RequireAuth({ children }: { children: JSX.Element }) {
  const location = useLocation();
  const token = getToken();
  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/chat"
        element={
          <RequireAuth>
            <ChatPage />
          </RequireAuth>
        }
      />
      <Route
        path="/tickets"
        element={
          <RequireAuth>
            <TicketListPage />
          </RequireAuth>
        }
      />
      <Route
        path="/notify"
        element={
          <RequireAuth>
            <NotifyCenterPage />
          </RequireAuth>
        }
      />
      <Route path="*" element={<Navigate to="/chat" replace />} />
    </Routes>
  );
}


