import { useState, useEffect } from 'react';
import { Card } from 'antd';
import { MessageOutlined } from '@ant-design/icons';
import { useHeartbeat } from '../../hooks/useHeartbeat';
import AgentHandoffPanel from '../../components/layout/AgentHandoffPanel';

/**
 * 待接入会话页面（客服）
 * 已集成：`AgentHandoffPanel`，用于实时展示分配到当前客服的转人工请求
 */
export default function AgentSessionsPage() {
  // 启动心跳定时器（客服保持在线状态）
  useHeartbeat({ enabled: true, interval: 30000 }); // 30秒一次
  
  const [loading, setLoading] = useState(false);

  return (
    <Card title="待接入会话">
      <AgentHandoffPanel />
    </Card>
  );
}

