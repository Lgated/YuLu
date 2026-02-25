import { MessageOutlined, ProfileOutlined, BellOutlined, DashboardOutlined, BookOutlined, UserOutlined } from '@ant-design/icons';
import type { ReactNode } from 'react';
import { useEffect } from 'react';
import { PortalLayout } from './PortalLayout';
import { WebSocketClient } from '../../utils/websocket';

const ADMIN_NOTIFY_BADGE_KEY = 'admin_notify_unread_count';
let adminWsClient: WebSocketClient | null = null;
let adminWsRefCount = 0;

export function AdminLayout({ children }: { children: ReactNode }) {
  useEffect(() => {
    adminWsRefCount += 1;
    if (!adminWsClient) {
      adminWsClient = new WebSocketClient('/ws/agent');
    }
    const client = adminWsClient;
    const handler = () => {
      const current = Number(localStorage.getItem(ADMIN_NOTIFY_BADGE_KEY) || '0');
      const next = current + 1;
      localStorage.setItem(ADMIN_NOTIFY_BADGE_KEY, String(next));
      window.dispatchEvent(new CustomEvent('notify:badge', { detail: { count: next } }));
      window.dispatchEvent(new Event('notify:update'));
    };

    client.on('ADMIN_NOTIFICATION', handler);
    return () => {
      client.off('ADMIN_NOTIFICATION', handler);
      adminWsRefCount -= 1;
      if (adminWsRefCount <= 0) {
        adminWsClient?.disconnect();
        adminWsClient = null;
        adminWsRefCount = 0;
      }
    };
  }, []);

  return (
    <PortalLayout
      logoText="YuLu 租户工作台"
      headerTitle="YuLu 租户端"
      menuItems={[
        {
          key: '/admin/dashboard',
          icon: <DashboardOutlined />,
          label: '数据看板'
        },
        {
          key: '/admin/tickets',
          icon: <ProfileOutlined />,
          label: '工单系统'
        },
        {
          key: '/admin/sessions',
          icon: <MessageOutlined />,
          label: '会话管理'
        },
        {
          key: '/admin/knowledge',
          icon: <BookOutlined />,
          label: '知识库'
        },
        {
          key: '/admin/faq',
          icon: <BookOutlined />,
          label: 'FAQ管理'
        },
        {
          key: '/admin/handoff',
          icon: <ProfileOutlined />,
          label: '转人工管理'
        },
        {
          key: '/admin/notify',
          icon: <BellOutlined />,
          label: '通知中心'
        },
        {
          key: '/admin/users',
          icon: <UserOutlined />,
          label: '账号管理'
        }
      ]}
    >
      {children}
    </PortalLayout>
  );
}
