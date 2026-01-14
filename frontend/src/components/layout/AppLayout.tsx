import { Layout, Menu, Dropdown, Avatar, Badge } from 'antd';
import {
  MessageOutlined,
  ProfileOutlined,
  BellOutlined,
  LogoutOutlined
} from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { clearToken } from '../../utils/storage';

const { Header, Sider, Content } = Layout;

interface AppLayoutProps {
  children: ReactNode;
}

export function AppLayout({ children }: AppLayoutProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  const selectedKey = location.pathname.startsWith('/tickets')
    ? '/tickets'
    : location.pathname.startsWith('/notify')
    ? '/notify'
    : '/chat';

  const onLogout = () => {
    clearToken();
    navigate('/login');
  };

  const userMenu = (
    <Menu
      items={[
        {
          key: 'logout',
          icon: <LogoutOutlined />,
          label: '退出登录',
          onClick: onLogout
        }
      ]}
    />
  );

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider
        theme="dark"
        width={220}
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        breakpoint="lg"
        collapsedWidth={64}
      >
        <div className="yulu-logo">YuLu 客服中台</div>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={[selectedKey]}
          items={[
            {
              key: '/chat',
              icon: <MessageOutlined />,
              label: <Link to="/chat">AI 对话</Link>
            },
            {
              key: '/tickets',
              icon: <ProfileOutlined />,
              label: <Link to="/tickets">工单中心</Link>
            },
            {
              key: '/notify',
              icon: <BellOutlined />,
              label: <Link to="/notify">通知中心</Link>
            }
          ]}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <div style={{ fontSize: 18, fontWeight: 500 }}>智链客服中台 · 单体版</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <Badge dot>
              <BellOutlined style={{ fontSize: 18, cursor: 'pointer' }} />
            </Badge>
            <Dropdown overlay={userMenu} trigger={['click']}>
              <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar size="small">U</Avatar>
                <span>当前用户</span>
              </div>
            </Dropdown>
          </div>
        </Header>
        <Content style={{ padding: 16, overflow: 'hidden' }}>{children}</Content>
      </Layout>
    </Layout>
  );
}


