import { Layout, Menu, Dropdown, Avatar, Badge } from 'antd';
import { BellOutlined, LogoutOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';
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
  const [notifyCount, setNotifyCount] = useState(0);
  const role = getRole();
  const badgeKey = role === 'ADMIN' ? 'admin_notify_unread_count' : 'agent_notify_unread_count';

  useEffect(() => {
    if (role !== 'AGENT' && role !== 'ADMIN') return;
    const initial = Number(localStorage.getItem(badgeKey) || '0');
    setNotifyCount(Number.isNaN(initial) ? 0 : initial);
    const onBadge = (e: Event) => {
      const detail = (e as CustomEvent).detail as { count?: number } | undefined;
      if (detail?.count !== undefined) {
        setNotifyCount(detail.count);
      } else {
        const current = Number(localStorage.getItem(badgeKey) || '0');
        setNotifyCount(Number.isNaN(current) ? 0 : current);
      }
    };
    const onClear = () => setNotifyCount(0);
    window.addEventListener('notify:badge', onBadge);
    window.addEventListener('notify:clear', onClear);
    return () => {
      window.removeEventListener('notify:badge', onBadge);
      window.removeEventListener('notify:clear', onClear);
    };
  }, [role, badgeKey]);

  const selectedKey = useMemo(() => {
    const p = location.pathname;
    // 排序菜单项：更长的 key 优先匹配
    const sortedItems = [...menuItems].sort((a, b) => b.key.length - a.key.length);
    const matched = sortedItems.find((it) => {
      if (it.match) return it.match(p);
      return p === it.key || p.startsWith(it.key + '/') || p.startsWith(it.key + '?');
    });
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
  const handleBellClick = () => {
    if (role === 'AGENT') {
      localStorage.removeItem('agent_notify_unread_count');
      window.dispatchEvent(new Event('notify:clear'));
      navigate('/agent/notify');
      return;
    }
    if (role === 'ADMIN') {
      localStorage.removeItem('admin_notify_unread_count');
      window.dispatchEvent(new Event('notify:clear'));
      navigate('/admin/notify');
    }
  };

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
      <Layout style={{ minHeight: 0 }}>
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
            <Badge count={role === 'AGENT' || role === 'ADMIN' ? notifyCount : 0} size="small" overflowCount={99}>
              <BellOutlined style={{ fontSize: 18, cursor: 'pointer' }} onClick={handleBellClick} />
            </Badge>
            <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
              <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar size="small">{username?.[0]?.toUpperCase() || 'U'}</Avatar>
                <span>{username}</span>
              </div>
            </Dropdown>
          </div>
        </Header>
        <Content style={{ padding: 16, overflow: 'auto' }}>{children}</Content>
      </Layout>
    </Layout>
  );
}
