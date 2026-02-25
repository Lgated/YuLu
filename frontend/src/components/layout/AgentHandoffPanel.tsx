import { useEffect, useRef, useState } from 'react';
import { Card, List, Button, message, notification, Tag } from 'antd';
import { WebSocketClient } from '../../utils/websocket';
import { handoffApi } from '../../api/handoff';

interface HandoffRequest {
  handoffRequestId: number;
  sessionId?: number;
  userName: string;
  reason: string;
  priority: string;
}

import { useNavigate } from 'react-router-dom';

const SELECTED_SESSION_KEY = 'agent_selected_handoff_session_id';

export default function AgentHandoffPanel() {
  const [requests, setRequests] = useState<HandoffRequest[]>([]);
  const wsClientRef = useRef<WebSocketClient | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    // 加载历史待处理请求
    const loadPending = async () => {
      try {
        const res = await handoffApi.getPendingRequests();
        setRequests(res.data || []);
      } catch (e) {
        console.error('Failed to load pending requests', e);
      }
    };
    loadPending();

    // 建立 WebSocket 连接
    const client = new WebSocketClient('/ws/agent');
    wsClientRef.current = client;

    client.on('HANDOFF_REQUEST', (payload) => {
      notification.info({
        message: '新的转人工请求',
        description: `来自客户 ${payload.userName} 的请求，请及时处理。`,
        duration: 0,
      });
      setRequests(prev => [payload, ...prev]);
    });

    client.connect();

    return () => {
      client.disconnect();
    };
  }, []);

  const handleAccept = async (handoffRequestId: number, sessionId?: number) => {
    try {
      const res = await handoffApi.accept(handoffRequestId);
      message.success('已接受请求，正在打开会话窗口。');
      
      // 持久化已接受的 session id（用于刷新后恢复）
      const acceptedSessionId = sessionId || res?.data?.sessionId;
      if (acceptedSessionId) {
        sessionStorage.setItem(SELECTED_SESSION_KEY, String(acceptedSessionId));
      }
      
      setRequests(prev => prev.filter(r => r.handoffRequestId !== handoffRequestId));
      
      // 跳转到客服工作台并带上 sessionId 和 handoffRequestId
      if (acceptedSessionId) {
        navigate(`/agent/workbench?sessionId=${acceptedSessionId}&handoffRequestId=${handoffRequestId}`);
      }
    } catch (e: any) { 
      message.error(e?.response?.data?.message || '接受失败');
    }
  };
  
  const handleReject = async (handoffRequestId: number) => {
      try {
          await handoffApi.reject(handoffRequestId, '客服正忙');
          message.warning('已拒绝该请求');
          setRequests(prev => prev.filter(r => r.handoffRequestId !== handoffRequestId));
      } catch (e: any) {
          message.error(e?.response?.data?.message || '拒绝操作失败');
      }
  }

  return (
    <Card title="待处理的转人工请求">
      <List
        dataSource={requests}
        renderItem={(item) => (
          <List.Item
            actions={[
              <Button type="primary" size="small" onClick={() => handleAccept(item.handoffRequestId, item.sessionId)}>
                接受
              </Button>,
              <Button danger size="small" onClick={() => handleReject(item.handoffRequestId)}>
                拒绝
              </Button>
            ]}
          >
            <List.Item.Meta
              title={<span>客户: {item.userName} <Tag color="red">{item.priority}</Tag></span>}
              description={`原因: ${item.reason || '未提供'}`}
            />
          </List.Item>
        )}
      />
    </Card>
  );
}