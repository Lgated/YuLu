import { useState } from 'react';
import { Card, Form, Input, Button, Typography, message, Select } from 'antd';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import { authApi } from '../api/auth';
import { setRole, setToken, setUsername } from '../utils/storage';

const { Title, Paragraph, Link } = Typography;

// 固定的租户选项
const TENANT_OPTIONS = [
  { label: 'EDU_001', value: 'EDU_001' },
  { label: 'EDU_002', value: 'EDU_002' },
  { label: 'EDU_003', value: 'EDU_003' }
];

export default function Register() {
  const navigate = useNavigate();
  const [isAdmin, setIsAdmin] = useState(false); // 是否为管理员注册模式

  const onFinish = async (values: any) => {
    try {
      let res;
      
      if (isAdmin) {
        // B端注册：调用租户注册接口
        res = await authApi.adminRegisterTenant({
          tenantCode: values.tenantCode,
          tenantName: values.tenantName,
          adminUsername: values.adminUsername,
          adminPassword: values.adminPassword,
          tenantIdentifier: values.tenantIdentifier || values.tenantCode // 如果没有提供，默认等于tenantCode
        });
        // 注册成功后跳转到登录页面
        if (res.success || res.code === '200') {
          message.success(res.message || '注册成功，请登录');
          navigate('/login'); // 跳转到登录页面
        }
      } else {
        // C端注册：调用客户注册接口
        res = await authApi.customerRegister({
          tenantIdentifier: values.tenantIdentifier,
          username: values.username,
          password: values.password,
          nickName: values.nickName,
          email: values.email,
          phone: values.phone
        });
        // 注册成功后自动登录，跳转到C端页面
        if (res.success || res.code === '200') {
          if (res.data?.token) {
            setToken(res.data.token);
          }
          setRole(res.data?.role || 'USER');
          if (res.data?.username) setUsername(res.data.username);
          message.success(res.message || '注册成功');
          navigate('/customer/chat'); // 跳转到客户端首页
        }
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '注册失败');
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
          {isAdmin ? '管理后台注册' : '客户服务注册'}
        </Paragraph>
        
        <Form layout="vertical" onFinish={onFinish}>
          {isAdmin ? (
            // B端注册表单：租户注册
            <>
              <Form.Item
                label="租户编码"
                name="tenantCode"
                rules={[{ required: true, message: '请选择租户编码' }]}
              >
                <Select placeholder="请选择租户编码" options={TENANT_OPTIONS} />
              </Form.Item>
              <Form.Item
                label="租户名称"
                name="tenantName"
                rules={[{ required: true, message: '请输入租户名称' }]}
              >
                <Input placeholder="例如 教育机构A" />
              </Form.Item>
              <Form.Item
                label="租户标识码（可选）"
                name="tenantIdentifier"
                tooltip="如果不填写，默认等于租户编码。C端用户使用此标识登录"
              >
                <Select 
                  placeholder="请选择租户标识码（留空则等于租户编码）" 
                  options={TENANT_OPTIONS}
                  allowClear
                />
              </Form.Item>
              <Form.Item
                label="管理员用户名"
                name="adminUsername"
                rules={[{ required: true, message: '请输入管理员用户名' }]}
              >
                <Input />
              </Form.Item>
              <Form.Item
                label="管理员密码"
                name="adminPassword"
                rules={[{ required: true, message: '请输入管理员密码' }]}
              >
                <Input.Password />
              </Form.Item>
            </>
          ) : (
            // C端注册表单：客户注册（需要租户标识、用户名和密码）
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
              <Form.Item
                label="昵称"
                name="nickName"
              >
                <Input placeholder="可选" />
              </Form.Item>
              <Form.Item
                label="邮箱"
                name="email"
              >
                <Input placeholder="可选" />
              </Form.Item>
              <Form.Item
                label="手机号"
                name="phone"
              >
                <Input placeholder="可选" />
              </Form.Item>
            </>
          )}
          
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              注册
            </Button>
          </Form.Item>
          
          {/* 切换提示 */}
          <Form.Item style={{ marginBottom: 0, textAlign: 'center' }}>
            <Link
              type="secondary"
              onClick={() => setIsAdmin(!isAdmin)}
              style={{ fontSize: 12 }}
            >
              {isAdmin ? '← 返回客户注册' : '我是管理员 →'}
            </Link>
          </Form.Item>
          
          {/* 登录链接 */}
          <Form.Item style={{ marginBottom: 0, textAlign: 'center' }}>
            <RouterLink to="/login" style={{ fontSize: 12 }}>
              已有账号？去登录
            </RouterLink>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}

