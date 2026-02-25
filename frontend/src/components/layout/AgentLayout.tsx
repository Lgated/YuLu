import {
  ProfileOutlined,
  BellOutlined,
  MessageOutlined,
  BookOutlined,
  UserOutlined,
  DesktopOutlined
} from '@ant-design/icons';
import type { ReactNode } from 'react';
import { useEffect } from 'react';
import { PortalLayout } from './PortalLayout';
import { WebSocketClient } from '../../utils/websocket';

const NOTIFY_BADGE_KEY = 'agent_notify_unread_count';

let agentWsClient: WebSocketClient | null = null;
let agentWsRefCount = 0;

export function AgentLayout({ children }: { children: ReactNode }) {
  useEffect(() => {
    agentWsRefCount += 1;
    if (!agentWsClient) {
      agentWsClient = new WebSocketClient('/ws/agent');
    }
    const client = agentWsClient;
    const handler = () => {
      const current = Number(localStorage.getItem(NOTIFY_BADGE_KEY) || '0');
      const next = current + 1;
      localStorage.setItem(NOTIFY_BADGE_KEY, String(next));
      window.dispatchEvent(new CustomEvent('notify:badge', { detail: { count: next } }));
      window.dispatchEvent(new Event('notify:update'));
    };

    client.on('ADMIN_NOTIFICATION', handler);
    return () => {
      client.off('ADMIN_NOTIFICATION', handler);
      agentWsRefCount -= 1;
      if (agentWsRefCount <= 0) {
        agentWsClient?.disconnect();
        agentWsClient = null;
        agentWsRefCount = 0;
      }
    };
  }, []);

  return (
    <PortalLayout
      logoText="YuLu 客服工作台"
      headerTitle="YuLu客服端"
      menuItems={[
        {
          key: '/agent/tickets',
          icon: <ProfileOutlined />,
          label: '我的工单'
        },
        {
          key: '/agent/sessions',
          icon: <MessageOutlined />,
          label: '待接入会话'
        },
        {
          key: '/agent/workbench',
          icon: <DesktopOutlined />,
          label: '我的工作台'
        },
        {
          key: '/agent/knowledge',
          icon: <BookOutlined />,
          label: '知识库查询'
        },
        {
          key: '/agent/notify',
          icon: <BellOutlined />,
          label: '通知中心'
        },
        {
          key: '/agent/profile',
          icon: <UserOutlined />,
          label: '个人设置'
        }
      ]}
    >
      {children}
    </PortalLayout>
  );
}
