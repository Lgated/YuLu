import { 
  ProfileOutlined, 
  BellOutlined, 
  MessageOutlined,
  BookOutlined,
  UserOutlined,
  DesktopOutlined
} from '@ant-design/icons';
import type { ReactNode } from 'react';
import { PortalLayout } from './PortalLayout';

export function AgentLayout({ children }: { children: ReactNode }) {
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
          key: '/agent/workspace',
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