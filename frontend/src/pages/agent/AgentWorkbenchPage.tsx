import { useEffect, useRef, useState } from 'react';
import { Card, List, Button, message, notification, Layout, Menu, Tag } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { WebSocketClient } from '../../utils/websocket';
import { handoffApi } from '../../api/handoff';
import AgentChatWindow from '../../components/AgentChatWindow';
import type { ChatMessage } from '../../api/types';

const { Sider, Content } = Layout;

// Define the data structure for an agent's session
interface AgentSession {
  sessionId: number;
  userId: number;
  userName: string;
  messages: ChatMessage[];
  unread: number;
  handoffRequestId?: number; // 添加转人工请求ID
}

export default function AgentWorkbenchPage() {
  const [searchParams] = useSearchParams();
  const [pendingRequests, setPendingRequests] = useState<any[]>([]);
  const [activeSessions, setActiveSessions] = useState<Map<number, AgentSession>>(new Map());
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const wsClientRef = useRef<WebSocketClient | null>(null);

  useEffect(() => {
    // 1. Load historical pending requests
    const loadPending = async () => {
      try {
        const res = await handoffApi.getPendingRequests();
        setPendingRequests(res.data || []);
      } catch (e) {
        console.error('Failed to load pending requests', e);
      }
    };
    loadPending();

    // 2. Establish WebSocket connection
    const client = new WebSocketClient('/ws/agent');
    wsClientRef.current = client;

    // 3. Listen for new handoff requests
    client.on('HANDOFF_REQUEST', (payload) => {
      notification.info({
        message: '新的转人工请求',
        description: `来自客户 ${payload.userName || '未知'} 的请求，请及时处理。`,
        duration: 0,
      });
      setPendingRequests(prev => [payload, ...prev]);
    });

    // 4. Listen for messages from customers
    client.on('TEXT', (payload) => {
      const { sessionId, content, messageId } = payload;
      setActiveSessions(prev => {
        const newSessions = new Map(prev);
        const session = newSessions.get(sessionId);
        if (session) {
          const newMessage: ChatMessage = {
            id: messageId || Date.now(),
            senderType: 'USER',
            content,
            createTime: new Date().toISOString(),
          } as any;
          session.messages.push(newMessage);
          // Mark as unread if not the current session
          if (sessionId !== currentSessionId) {
            session.unread = (session.unread || 0) + 1;
          }
        }
        return newSessions;
      });
    });

    // 5. Listen for session completion (from user or system)
    client.on('HANDOFF_COMPLETED', (payload) => {
      const { sessionId, endedBy, message: msg } = payload;
      notification.info({
        message: '会话已结束',
        description: msg || `会话 #${sessionId} 已结束（${endedBy === 'USER' ? '用户' : '系统'}）`,
      });
      
      // Remove from active sessions
      setActiveSessions(prev => {
        const newSessions = new Map(prev);
        newSessions.delete(sessionId);
        return newSessions;
      });

      // Clear current session if it's the one being ended
      if (sessionId === currentSessionId) {
        setCurrentSessionId(null);
      }
    });

    client.connect();

    return () => {
      client.disconnect();
    };
  }, []);

  // 5. Restore session from URL query or sessionStorage on mount
  useEffect(() => {
    const sessionIdFromUrl = searchParams.get('sessionId');
    const sessionIdFromStorage = sessionStorage.getItem('agent_selected_handoff_session_id');
    const targetSessionId = sessionIdFromUrl ? Number(sessionIdFromUrl) : (sessionIdFromStorage ? Number(sessionIdFromStorage) : null);

    if (targetSessionId && !activeSessions.has(targetSessionId)) {
      // 从后端获取会话的转人工请求信息
      const loadSessionInfo = async () => {
        try {
          const res = await handoffApi.getBySessionId(targetSessionId);
          const handoffInfo = res.data;
          
          // 创建完整的会话对象
          const restoredSession: AgentSession = {
            sessionId: targetSessionId,
            userId: handoffInfo.userId,
            userName: handoffInfo.userName || `客户 #${handoffInfo.userId}`,
            messages: [],
            unread: 0,
            handoffRequestId: handoffInfo.handoffRequestId, // 关键：保存 handoffRequestId
          };
          
          setActiveSessions(prev => new Map(prev).set(targetSessionId, restoredSession));
          setCurrentSessionId(targetSessionId);
        } catch (error: any) {
          console.error('Failed to load session info:', error);
          message.error('加载会话信息失败');
          
          // 如果加载失败，创建一个占位符（但没有 handoffRequestId）
          const placeholderSession: AgentSession = {
            sessionId: targetSessionId,
            userId: 0,
            userName: `会话 #${targetSessionId}`,
            messages: [],
            unread: 0,
          };
          setActiveSessions(prev => new Map(prev).set(targetSessionId, placeholderSession));
          setCurrentSessionId(targetSessionId);
        }
      };
      
      loadSessionInfo();
    } else if (targetSessionId && activeSessions.has(targetSessionId)) {
      setCurrentSessionId(targetSessionId);
    }
  }, [searchParams]);

  // Agent accepts a request
  const handleAccept = async (request: any) => {
    try {
      const res = await handoffApi.accept(request.handoffRequestId);
      message.success('已接受请求，开始对话。');
      
      // Remove from pending list
      setPendingRequests(prev => prev.filter(r => r.handoffRequestId !== request.handoffRequestId));

      // Create a new active session
      const newSession: AgentSession = {
        sessionId: res.data.sessionId,
        userId: res.data.userId,
        userName: request.userName || `客户 #${res.data.userId}`,
        messages: [], // You can preload historical messages here
        unread: 0,
        handoffRequestId: request.handoffRequestId, // 保存转人工请求ID
      };

      setActiveSessions(prev => new Map(prev).set(newSession.sessionId, newSession));
      setCurrentSessionId(newSession.sessionId);

    } catch (e: any) { 
      message.error(e?.response?.data?.message || '接受失败');
    }
  };

  // Agent ends a session
  const handleEndSession = (sessionId: number) => {
    // Remove from active sessions
    setActiveSessions(prev => {
      const newSessions = new Map(prev);
      newSessions.delete(sessionId);
      return newSessions;
    });

    // Clear current session if it's the one being ended
    if (currentSessionId === sessionId) {
      setCurrentSessionId(null);
    }

    message.success('会话已结束');
  };

  // Agent sends a message
  const handleSendMessage = (sessionId: number, content: string) => {
    if (!wsClientRef.current) return;

    // **CRITICAL FIX**: Construct payload with sessionId
    const payload = { sessionId, content };
    wsClientRef.current.send('TEXT', payload);

    // Optimistically update UI
    const newMessage: ChatMessage = {
      id: Date.now(),
      senderType: 'AGENT',
      content,
      createTime: new Date().toISOString(),
    } as any;

    setActiveSessions(prev => {
      const newSessions = new Map(prev);
      const session = newSessions.get(sessionId);
      if (session) {
        session.messages.push(newMessage);
      }
      return newSessions;
    });
  };

  const currentChat = currentSessionId ? activeSessions.get(currentSessionId) : null;

  return (
    <Layout style={{ height: 'calc(100vh - 96px)' }}>
      <Sider width={300} theme="light" style={{ borderRight: '1px solid #f0f0f0', overflowY: 'auto' }}>
        <Card title="待处理请求" size="small">
          <List
            dataSource={pendingRequests}
            renderItem={(item) => (
              <List.Item actions={[<Button type="primary" size="small" onClick={() => handleAccept(item)}>接受</Button>]}>
                <List.Item.Meta
                  title={`客户: ${item.userName || '未知'}`}
                  description={`原因: ${item.reason || '无'}`}
                />
              </List.Item>
            )}
          />
        </Card>
        <Card title="进行中会话" size="small" style={{ marginTop: 16 }}>
          <Menu
            mode="inline"
            selectedKeys={currentSessionId ? [String(currentSessionId)] : []}
            onClick={({ key }) => {
              const sessionId = Number(key);
              setCurrentSessionId(sessionId);
              // Clear unread count on selection
              setActiveSessions(prev => {
                  const newSessions = new Map(prev);
                  const session = newSessions.get(sessionId);
                  if (session) session.unread = 0;
                  return newSessions;
              });
            }}
          >
            {Array.from(activeSessions.values()).map(session => (
              <Menu.Item key={session.sessionId}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>{session.userName}</span>
                  {session.unread > 0 && <Tag color="red">{session.unread}</Tag>}
                </div>
              </Menu.Item>
            ))}
          </Menu>
        </Card>
      </Sider>
      <Content style={{ background: '#fff' }}>
        {currentChat ? (
          <AgentChatWindow
            session={currentChat}
            onSendMessage={(content) => handleSendMessage(currentChat.sessionId, content)}
            onEndSession={handleEndSession}
          />
        ) : (
          <div style={{ textAlign: 'center', padding: '40px' }}>请从左侧选择一个会话开始处理。</div>
        )}
      </Content>
    </Layout>
  );
}

