import { Card, Form, Input, Button, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { setToken } from '../utils/storage';

const { Title, Paragraph } = Typography;

export default function Login() {
  const navigate = useNavigate();

  const onFinish = async (values: any) => {
    try {
      const res = await authApi.login(values);
      // 后端 ApiResponse：success=true 且 code='200' 表示成功
      if (res.success || res.code === '200') {
        if (res.data?.token) {
          setToken(res.data.token);
        }
        message.success(res.message || '登录成功');
        navigate('/chat');
      } else {
        message.error(res.message || '登录失败');
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '登录失败');
    }
  };

  return (
    <div
      style={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg,#1f3b73,#1677ff)'
      }}
    >
      <Card style={{ width: 420 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 8 }}>
          YuLu 智链客服中台
        </Title>
        <Paragraph style={{ textAlign: 'center', marginBottom: 24 }}>
          解决你想解决的问题
        </Paragraph>
        <Form layout="vertical" onFinish={onFinish} initialValues={{ tenantCode: 'EDU_001' }}>
          <Form.Item
            label="租户编码"
            name="tenantCode"
            rules={[{ required: true, message: '请输入租户编码' }]}
          >
            <Input placeholder="例如 TENANT_001" />
          </Form.Item>
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}


