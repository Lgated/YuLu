import { Layout, Menu, Dropdown, Avatar, Badge } from 'antd';
import { BellOutlined, LogoutOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useMemo, useState } from 'react';
import { clearToken, getUsername, getRole } from '../../utils/storage';

const { Header, Sider, Content } = Layout;

export type PortalMenuItem = {
  key: string;
  icon?: ReactNode;
  label: ReactNode;
  match?: (pathname: string) => boolean;
};

interface PortalLayoutProps {
  children: ReactNode;
  logoText: string;
  headerTitle: string;
  menuItems: PortalMenuItem[];
}

export function PortalLayout({ children, logoText, headerTitle, menuItems }: PortalLayoutProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  const selectedKey = useMemo(() => {
    const p = location.pathname;
    const matched = menuItems.find((it) => (it.match ? it.match(p) : p.startsWith(it.key)));
    return matched?.key || menuItems[0]?.key;
  }, [location.pathname, menuItems]);

  const onLogout = async () => {
    // 如果是客服，登出前设置为离线状态
    const role = getRole();
    if (role === 'AGENT') {
      try {
        const { agentApi } = await import('../../api/agent');
        await agentApi.updateOnlineStatus('OFFLINE');
      } catch (error) {
        // 登出时状态更新失败不影响登出流程
        console.warn('登出时更新状态失败:', error);
      }
    }
    clearToken();
    navigate('/login');
  };

  const userMenuItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: onLogout
    }
  ];

  const username = getUsername() || '当前用户';

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
        <div className="yulu-logo">{logoText}</div>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={selectedKey ? [selectedKey] : []}
          items={menuItems.map((it) => ({
            key: it.key,
            icon: it.icon,
            label: typeof it.label === 'string' ? <Link to={it.key}>{it.label}</Link> : it.label
          }))}
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
          <div style={{ fontSize: 18, fontWeight: 500 }}>{headerTitle}</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <Badge dot>
              <BellOutlined style={{ fontSize: 18, cursor: 'pointer' }} />
            </Badge>
            <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
              <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar size="small">{username?.[0]?.toUpperCase() || 'U'}</Avatar>
                <span>{username}</span>
              </div>
            </Dropdown>
          </div>
        </Header>
        <Content style={{ padding: 16, overflow: 'hidden' }}>{children}</Content>
      </Layout>
    </Layout>
  );
}


