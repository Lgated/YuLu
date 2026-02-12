import { useEffect, useState } from 'react';
import { Button, Card, Form, Input, List, Tag, message } from 'antd';
import { notifyApi } from '../api/notify';
import { adminHandoffApi } from '../api/adminHandoff';
import type { NotifyMessage } from '../api/types';
import { getRole } from '../utils/storage';

export default function NotifyCenterPage() {
  const [data, setData] = useState<NotifyMessage[]>([]);
  const [sending, setSending] = useState(false);
  const [sendForm] = Form.useForm();
  const role = getRole();

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    const handler = () => load();
    window.addEventListener('notify:update', handler);
    return () => window.removeEventListener('notify:update', handler);
  }, []);

  const load = async () => {
    try {
      const res = await notifyApi.list({ page: 1, size: 50 });
      if (res.success || res.code === '200') {
        setData(res.data?.records || []);
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
        <List
          dataSource={data}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <>
                    {item.title}{' '}
                    {item.readFlag === 0 && (
                      <Tag color="blue" style={{ marginLeft: 8 }}>
                        未读
                      </Tag>
                    )}
                  </>
                }
                description={item.content}
              />
              <div style={{ fontSize: 12, color: '#999' }}>{item.createTime}</div>
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
}

