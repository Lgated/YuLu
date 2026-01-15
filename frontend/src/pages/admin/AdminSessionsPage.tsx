import { useEffect, useState } from 'react';
import { Card, List, message as antdMessage } from 'antd';
import type { ChatMessage, ChatSession } from '../../api/types';
import { sessionApi } from '../../api/chat';

export default function AdminSessionsPage() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    setLoadingSessions(true);
    try {
      const res = await sessionApi.listAllSessions();
      if (res.success || res.code === '200') {
        const list = res.data || [];
        setSessions(list);
        if (list.length > 0) {
          const firstId = list[0].id;
          setCurrentSessionId(firstId);
          await loadMessages(firstId);
        } else {
          setCurrentSessionId(null);
          setMessages([]);
        }
      }
    } catch (e: any) {
      antdMessage.error(e?.response?.data?.message || '加载会话失败');
    } finally {
      setLoadingSessions(false);
    }
  };

  const loadMessages = async (sessionId: number) => {
    setLoadingMessages(true);
    try {
      const res = await sessionApi.listMessages(sessionId);
      if (res.success || res.code === '200') {
        setMessages(res.data || []);
      }
    } catch (e: any) {
      antdMessage.error(e?.response?.data?.message || '加载消息失败');
    } finally {
      setLoadingMessages(false);
    }
  };

  return (
    <div style={{ display: 'flex', gap: 16, height: 'calc(100vh - 96px)', overflow: 'hidden' }}>
      {/* 左侧：会话列表，可滚动 */}
      <Card
        title="会话列表"
        style={{ width: 320, height: '100%', display: 'flex', flexDirection: 'column' }}
        bodyStyle={{ padding: 0, flex: 1, overflowY: 'auto' }}
        loading={loadingSessions}
      >
        <List
          dataSource={sessions}
          renderItem={(item) => (
            <List.Item
              style={{ cursor: 'pointer', background: item.id === currentSessionId ? '#e6f4ff' : undefined }}
              onClick={() => {
                setCurrentSessionId(item.id);
                loadMessages(item.id);
              }}
            >
              {item.sessionTitle || `会话 #${item.id}`}
            </List.Item>
          )}
        />
      </Card>
      {/* 右侧：会话消息，可滚动 */}
      <Card
        title="会话消息"
        style={{ flex: 1, height: '100%', display: 'flex', flexDirection: 'column' }}
        bodyStyle={{ padding: 16, flex: 1, overflowY: 'auto' }}
        loading={loadingMessages}
      >
        <List
          dataSource={messages}
          renderItem={(msg) => (
            <List.Item>
              <List.Item.Meta
                title={`${msg.senderType}  ·  ${msg.createTime || ''}`}
                description={msg.content}
              />
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
}
