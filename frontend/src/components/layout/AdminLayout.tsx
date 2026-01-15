import { MessageOutlined, ProfileOutlined, BellOutlined, DashboardOutlined } from '@ant-design/icons';
import type { ReactNode } from 'react';
import { PortalLayout } from './PortalLayout';

export function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <PortalLayout
      logoText="YuLu 租户工作台"
      headerTitle="智链客服中台 · 租户端"
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
          key: '/admin/notify',
          icon: <BellOutlined />,
          label: '通知中心'
        }
      ]}
    >
      {children}
    </PortalLayout>
  );
}


