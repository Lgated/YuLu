import { useState } from 'react';
import { Card, Form, Input, Button, Typography, message, Select } from 'antd';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import { authApi } from '../api/auth';
import { setRole, setToken, setUsername, setUserId } from '../utils/storage';
import { agentApi } from '../api/agent';

const { Title, Paragraph, Link } = Typography;

// 固定的租户选项
const TENANT_OPTIONS = [
  { label: 'EDU_001', value: 'EDU_001' },
  { label: 'EDU_002', value: 'EDU_002' },
  { label: 'EDU_003', value: 'EDU_003' }
];

export default function Login() {
  const navigate = useNavigate();
  const [isAdmin, setIsAdmin] = useState(false); // 是否为管理员登录模式

  const onFinish = async (values: any) => {
    try {
      let res;
      
      if (isAdmin) {
        // B端登录：调用管理员登录接口
        res = await authApi.adminLogin({
          tenantCode: values.tenantCode,
          username: values.username,
          password: values.password
        });
        // 登录成功后跳转到B端页面
        if (res.success || res.code === '200') {
          if (res.data?.token) {
            setToken(res.data.token);
          }
          if (res.data?.role) setRole(res.data.role);
          if (res.data?.username) setUsername(res.data.username);
          if (res.data?.userId) setUserId(res.data.userId);
          
          // 如果是客服，登录后自动设置为在线状态
          const role = res.data?.role;
          if (role === 'AGENT') {
            try {
              await agentApi.updateOnlineStatus('ONLINE');
            } catch (error) {
              // 状态更新失败不影响登录流程
              console.warn('登录后设置在线状态失败:', error);
            }
          }
          
          message.success(res.message || '登录成功');
          
          // 根据角色跳转到不同页面
          if (role === 'ADMIN') {
            navigate('/admin/dashboard');
          } else if (role === 'AGENT') {
            navigate('/agent/tickets');
          } else {
            navigate('/admin/dashboard'); // 默认跳转
          }
        } else {
          // 如果返回了错误响应但没有抛出异常
          message.error(res.message || '登录失败，请检查账号密码');
        }
      } else {
        // C端登录：调用客户登录接口
        res = await authApi.customerLogin({
          tenantIdentifier: values.tenantIdentifier,
          username: values.username,
          password: values.password
        });
        // 登录成功后跳转到C端页面
        if (res.success || res.code === '200') {
          if (res.data?.token) {
            setToken(res.data.token);
          }
          // C端固定为USER（后端若返回role也会覆盖）
          setRole(res.data?.role || 'USER');
          if (res.data?.username) setUsername(res.data.username);
          if (res.data?.userId) setUserId(res.data.userId);
          message.success(res.message || '登录成功');
          navigate('/customer/chat'); // 客户端首页
        } else {
          // 如果返回了错误响应但没有抛出异常
          message.error(res.message || '登录失败，请检查账号密码');
        }
      }
    } catch (e: any) {
      // 处理各种错误情况
      let errorMessage = '登录失败';
      if (e?.response?.data) {
        // 后端返回的错误信息
        errorMessage = e.response.data.message || e.response.data.error || errorMessage;
      } else if (e?.message) {
        // 网络错误或其他错误
        errorMessage = e.message;
      }
      message.error(errorMessage);
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
          {isAdmin ? '管理后台登录' : '客户服务登录'}
        </Paragraph>
        
        <Form layout="vertical" onFinish={onFinish}>
          {isAdmin ? (
            // B端登录表单：需要租户编码
            <>
              <Form.Item
                label="租户编码"
                name="tenantCode"
                rules={[{ required: true, message: '请选择租户编码' }]}
              >
                <Select placeholder="请选择租户编码" options={TENANT_OPTIONS} />
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
            </>
          ) : (
            // C端登录表单：需要租户标识、用户名和密码
            <>
              <Form.Item
                label="租户标识"
                name="tenantIdentifier"
                rules={[{ required: true, message: '请选择租户标识' }]}
              >
                <Select placeholder="请选择租户标识" options={TENANT_OPTIONS} />
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
            </>
          )}
          
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              登录
            </Button>
          </Form.Item>
          
          {/* 切换提示 */}
          <Form.Item style={{ marginBottom: 0, textAlign: 'center' }}>
            <Link
              type="secondary"
              onClick={() => setIsAdmin(!isAdmin)}
              style={{ fontSize: 12 }}
            >
              {isAdmin ? '← 返回客户登录' : '我是管理员 →'}
            </Link>
          </Form.Item>
          
          {/* 注册链接 */}
          <Form.Item style={{ marginBottom: 0, textAlign: 'center' }}>
            <RouterLink to="/register" style={{ fontSize: 12 }}>
              没有账号？去注册
            </RouterLink>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}


