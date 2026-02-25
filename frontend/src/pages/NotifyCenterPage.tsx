import { useEffect, useState } from 'react';
import { Button, Card, Form, Input, List, Tabs, Tag, message } from 'antd';
import { notifyApi } from '../api/notify';
import { adminHandoffApi } from '../api/adminHandoff';
import type { NotifyMessage } from '../api/types';
import { getRole } from '../utils/storage';
import { useLocation } from 'react-router-dom';

export default function NotifyCenterPage() {
  const [unreadList, setUnreadList] = useState<NotifyMessage[]>([]);
  const [readList, setReadList] = useState<NotifyMessage[]>([]);
  const [sending, setSending] = useState(false);
  const [sendForm] = Form.useForm();
  const role = getRole();
  const location = useLocation();

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    const handler = () => load();
    window.addEventListener('notify:update', handler);
    return () => window.removeEventListener('notify:update', handler);
  }, []);

  useEffect(() => {
    if (role === 'AGENT' && location.pathname.startsWith('/agent/notify')) {
      localStorage.removeItem('agent_notify_unread_count');
      window.dispatchEvent(new Event('notify:clear'));
    }
    if (role === 'ADMIN' && location.pathname.startsWith('/admin/notify')) {
      localStorage.removeItem('admin_notify_unread_count');
      window.dispatchEvent(new Event('notify:clear'));
    }
  }, [role, location.pathname]);

  const load = async () => {
    try {
      const res = await notifyApi.list({ page: 1, size: 50, onlyUnread: false });
      if (res.success || res.code === '200') {
        const records = res.data?.records || [];
        setUnreadList(records.filter((item) => item.readFlag === 0));
        setReadList(records.filter((item) => item.readFlag !== 0));
      }
    } catch (e: any) {
      console.error('加载通知失败', e);
    }
  };

  const handleSend = async () => {
    try {
      const values = await sendForm.validateFields();
      setSending(true);
      const res = await adminHandoffApi.broadcastNotification(values.title, values.content);
      if (res.success || res.code === '200') {
        message.success('通知已发送');
        sendForm.resetFields();
        load();
      }
    } catch (e: any) {
      if (!e?.errorFields) {
        message.error(e?.response?.data?.message || '发送通知失败');
      }
    } finally {
      setSending(false);
    }
  };

  const markAllRead = async () => {
    try {
      const res = await notifyApi.markAllRead();
      if (res.success || res.code === '200') {
        const moved = unreadList.map((n) => ({ ...n, readFlag: 1 }));
        setUnreadList([]);
        setReadList((prev) => [...moved, ...prev]);
        message.success('已全部标记为已读');
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '全部标记已读失败');
    }
  };

  const markAsRead = async (item: NotifyMessage) => {
    if (item.readFlag !== 0) return;
    try {
      const res = await notifyApi.markRead([item.id]);
      if (res.success || res.code === '200') {
        setUnreadList((prev) => prev.filter((n) => n.id !== item.id));
        setReadList((prev) => [{ ...item, readFlag: 1 }, ...prev]);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '标记已读失败');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {role === 'ADMIN' && (
        <Card title="发布通知">
          <Form form={sendForm} layout="vertical">
            <Form.Item
              label="标题"
              name="title"
              rules={[{ required: true, message: '请输入标题' }]}
            >
              <Input placeholder="请输入标题" />
            </Form.Item>
            <Form.Item
              label="内容"
              name="content"
              rules={[{ required: true, message: '请输入内容' }]}
            >
              <Input.TextArea rows={4} placeholder="请输入内容" />
            </Form.Item>
            <Button type="primary" onClick={handleSend} loading={sending}>
              发送
            </Button>
          </Form>
        </Card>
      )}
      <Card title="通知中心">
        <Tabs
          items={[
            {
              key: 'unread',
              label: `未读 (${unreadList.length})`,
              children: (
                <div>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
                    <Button size="small" onClick={markAllRead} disabled={unreadList.length === 0}>
                      全部已读
                    </Button>
                  </div>
                  <List
                    dataSource={unreadList}
                    renderItem={(item) => (
                      <List.Item onClick={() => markAsRead(item)} style={{ cursor: 'pointer' }}>
                        <List.Item.Meta
                          title={
                            <>
                              {item.title}{' '}
                              <Tag color="blue" style={{ marginLeft: 8 }}>
                                未读
                              </Tag>
                            </>
                          }
                          description={item.content}
                        />
                        <div style={{ fontSize: 12, color: '#999' }}>{item.createTime}</div>
                      </List.Item>
                    )}
                  />
                </div>
              )
            },
            {
              key: 'read',
              label: `已读 (${readList.length})`,
              children: (
                <List
                  dataSource={readList}
                  renderItem={(item) => (
                    <List.Item>
                      <List.Item.Meta title={item.title} description={item.content} />
                      <div style={{ fontSize: 12, color: '#999' }}>{item.createTime}</div>
                    </List.Item>
                  )}
                />
              )
            }
          ]}
        />
      </Card>
    </div>
  );
}
