import { MessageOutlined, QuestionCircleOutlined, LogoutOutlined } from '@ant-design/icons';
import type { ReactNode } from 'react';
import { Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { PortalLayout } from './PortalLayout';
import { clearToken } from '../../utils/storage';

export function CustomerLayout({ children }: { children: ReactNode }) {
  const navigate = useNavigate();

  return (
    <PortalLayout
      logoText="YuLu 客户助手"
      headerTitle="在线客服"
      menuItems={[
        {
          key: '/customer/chat',
          icon: <MessageOutlined />,
          label: '智能对话'
        },
        {
          key: '/customer/faq',
          icon: <QuestionCircleOutlined />,
          label: '常见问题'
        }
      ]}
    >
      {children}
    </PortalLayout>
  );
}



































