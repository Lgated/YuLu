import { useState, useEffect } from 'react';
import { Card, Form, Switch, Button, Space, message, Radio, Typography, Divider, Modal } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, PauseCircleOutlined } from '@ant-design/icons';
import { agentApi } from '../../api/agent';
import { getRole } from '../../utils/storage';
import { useHeartbeat } from '../../hooks/useHeartbeat';

const { Title, Text } = Typography;

type OnlineStatus = 'ONLINE' | 'OFFLINE' | 'AWAY';

export default function AgentProfilePage() {
  // 启动心跳定时器（客服保持在线状态）
  // 注意：只有在 ONLINE 状态时才发送心跳
  const [currentStatus, setCurrentStatus] = useState<OnlineStatus>('OFFLINE');
  useHeartbeat({ enabled: currentStatus === 'ONLINE', interval: 30000 }); // 30秒一次
  
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  // 初始化：首次挂载时设为 OFFLINE（默认离线），用户需手动上线
  useEffect(() => {
    // 从 localStorage 读取上次状态（如果有的话）
    const savedStatus = localStorage.getItem('agent_online_status') as OnlineStatus | null;
    const initialStatus = savedStatus || 'OFFLINE';
    setCurrentStatus(initialStatus);
    form.setFieldsValue({ status: initialStatus });
  }, []); // 空依赖数组 - 只在组件首次挂载时执行一次

  // 更新在线状态（带确认对话框）
  const handleStatusChange = (targetStatus: OnlineStatus) => {
    if (targetStatus === currentStatus) {
      return; // 如果已经是目标状态，不需要操作
    }

    const statusText = getStatusText(targetStatus);
    Modal.confirm({
      title: '确认修改状态',
      content: `确定要将状态修改为"${statusText}"吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        setLoading(true);
        try {
          await agentApi.updateOnlineStatus(targetStatus);
          setCurrentStatus(targetStatus);
          // 保存状态到 localStorage，避免切换 tab 后状态丢失
          localStorage.setItem('agent_online_status', targetStatus);
          form.setFieldsValue({ status: targetStatus });
          message.success(`状态已更新为：${statusText}`);
        } catch (e: any) {
          message.error(e?.response?.data?.message || '状态更新失败');
        } finally {
          setLoading(false);
        }
      }
    });
  };

  // 获取状态文本
  const getStatusText = (status: OnlineStatus) => {
    const statusMap: Record<OnlineStatus, string> = {
      ONLINE: '在线',
      OFFLINE: '离线',
      AWAY: '离开'
    };
    return statusMap[status];
  };

  // 获取状态图标
  const getStatusIcon = (status: OnlineStatus) => {
    switch (status) {
      case 'ONLINE':
        return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
      case 'AWAY':
        return <PauseCircleOutlined style={{ color: '#faad14' }} />;
      case 'OFFLINE':
        return <CloseCircleOutlined style={{ color: '#ff4d4f' }} />;
    }
  };

  // 检查是否是客服角色
  const role = getRole();
  const isAgent = role === 'AGENT';

  if (!isAgent) {
    return (
      <Card>
        <Typography.Text type="danger">此页面仅限客服使用</Typography.Text>
      </Card>
    );
  }

  return (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      <Card>
        <Title level={4}>个人设置</Title>
        <Divider />

        {/* 在线状态管理 */}
        <Card
          title="在线状态管理"
          style={{ marginBottom: 24 }}
        >
          <Form form={form} layout="vertical">
            <Form.Item
              label="当前状态"
              name="status"
            >
              <Radio.Group
                value={currentStatus}
                disabled={true} // 只读，仅用于展示
              >
                <Space direction="vertical" size="middle">
                  <Radio value="ONLINE">
                    <Space>
                      {getStatusIcon('ONLINE')}
                      <Text>在线</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        （可以接收新会话和工单）
                      </Text>
                    </Space>
                  </Radio>
                  <Radio value="AWAY">
                    <Space>
                      {getStatusIcon('AWAY')}
                      <Text>离开</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        （暂时无法接收新会话）
                      </Text>
                    </Space>
                  </Radio>
                  <Radio value="OFFLINE">
                    <Space>
                      {getStatusIcon('OFFLINE')}
                      <Text>离线</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        （无法接收新会话和工单）
                      </Text>
                    </Space>
                  </Radio>
                </Space>
              </Radio.Group>
            </Form.Item>

            <Form.Item>
              <Space>
                <Button
                  type="primary"
                  onClick={() => handleStatusChange('ONLINE')}
                  loading={loading && currentStatus === 'ONLINE'}
                  disabled={currentStatus === 'ONLINE'}
                >
                  上线
                </Button>
                <Button
                  onClick={() => handleStatusChange('AWAY')}
                  loading={loading && currentStatus === 'AWAY'}
                  disabled={currentStatus === 'AWAY'}
                >
                  离开
                </Button>
                <Button
                  danger
                  onClick={() => handleStatusChange('OFFLINE')}
                  loading={loading && currentStatus === 'OFFLINE'}
                  disabled={currentStatus === 'OFFLINE'}
                >
                  下线
                </Button>
              </Space>
            </Form.Item>
          </Form>

          <Divider />

          <div style={{ marginTop: 16 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              💡 提示：设置为"在线"后，系统会自动发送心跳保持在线状态。
              如果长时间无操作，系统会自动将状态设置为"离线"。
            </Text>
          </div>
        </Card>

        {/* 其他设置（预留） */}
        <Card title="其他设置">
          <Text type="secondary">更多设置功能开发中...</Text>
        </Card>
      </Card>
    </div>
  );
}

