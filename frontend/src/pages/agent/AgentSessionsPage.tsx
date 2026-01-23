import { useState, useEffect } from 'react';
import { Card, Table, Tag, Button, Space, message, Empty } from 'antd';
import { MessageOutlined } from '@ant-design/icons';
import { useHeartbeat } from '../../hooks/useHeartbeat';

/**
 * 待接入会话页面（客服）
 * TODO: 等待后端实现转人工记录查询接口
 */
export default function AgentSessionsPage() {
  // 启动心跳定时器（客服保持在线状态）
  useHeartbeat({ enabled: true, interval: 30000 }); // 30秒一次
  
  const [loading, setLoading] = useState(false);

  return (
    <Card title="待接入会话">
      <Empty
        description="待接入会话功能开发中，等待后端接口实现"
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    </Card>
  );
}

