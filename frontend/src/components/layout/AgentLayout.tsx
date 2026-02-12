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
import { notification } from 'antd';
import { PortalLayout } from './PortalLayout';
import { WebSocketClient } from '../../utils/websocket';

let agentWsClient: WebSocketClient | null = null;
let agentWsRefCount = 0;

export function AgentLayout({ children }: { children: ReactNode }) {
  useEffect(() => {
    agentWsRefCount += 1;
    if (!agentWsClient) {
      agentWsClient = new WebSocketClient('/ws/agent');
    }
    const client = agentWsClient;
    const handler = (payload: any) => {
      notification.open({
        message: payload?.title || '新通知',
        description: payload?.content || '',
        placement: 'topRight',
        duration: 3
      });
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
