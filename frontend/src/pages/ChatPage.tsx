import { useEffect, useRef, useState } from 'react';
import { Card, List, Input, Button, message as antdMessage, Tag } from 'antd';
import { chatApi } from '../api/chat';
import type { ChatMessage, ChatSession } from '../api/types';
import { AppLayout } from '../components/layout/AppLayout';
import { getCurrentSessionId, setCurrentSessionId as saveSessionId } from '../utils/storage';

const { TextArea } = Input;

export default function ChatPage() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState('');
  const [loading, setLoading] = useState(false);
  const chatWindowRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const savedId = getCurrentSessionId();
    if (savedId) {
      setCurrentSessionId(savedId);
      loadMessages(savedId);
    } else {
      loadSessions();
    }
  }, []);

  // 消息变化时自动滚动到底部
  useEffect(() => {
    const el = chatWindowRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages]);

  const loadSessions = async () => {
    try {
      const res = await chatApi.allSessions();
      if (res.success || res.code === '200') {
        setSessions(res.data || []);
        if (res.data && res.data.length > 0) {
          const first = res.data[0];
          setCurrentSessionId(first.id);
          saveSessionId(first.id);
          loadMessages(first.id);
        }
      }
    } catch (e: any) {
      antdMessage.error(e?.response?.data?.message || '加载会话失败');
    }
  };

  const loadMessages = async (sessionId: number) => {
    try {
      const res = await chatApi.messages(sessionId);
      if (res.success || res.code === '200') {
        setMessages(res.data || []);
        // 确保会话列表中至少包含当前会话（避免从其他 Tab 返回时列表为空）
        setSessions((prev) => {
          if (prev.find((s) => s.id === sessionId)) {
            return prev;
          }
          return [
            {
              id: sessionId,
              tenantId: 0,
              userId: 0,
              sessionTitle: `默认会话`,
              status: 1,
              createTime: '',
              updateTime: ''
            },
            ...prev
          ];
        });
      }
    } catch (e: any) {
      antdMessage.error(e?.response?.data?.message || '加载消息失败');
    }
  };

  const handleSend = async () => {
    if (!question.trim()) return;
    setLoading(true);
    try {
      const payload = { sessionId: currentSessionId ?? undefined, question };
      const res = await chatApi.ask(payload);
      // 后端 ApiResponse：success=true 且 code='200' 表示成功
      if (res.success || res.code === '200') {
        const aiMsg = res.data;
        const userMsg: ChatMessage = {
          id: Date.now(),
          tenantId: aiMsg.tenantId,
          sessionId: aiMsg.sessionId,
          senderType: 'USER',
          content: question,
          emotion: 'NORMAL',
          createTime: new Date().toISOString()
        };
        setCurrentSessionId(aiMsg.sessionId);
        saveSessionId(aiMsg.sessionId);
        setMessages((prev) => [...prev, userMsg, aiMsg]);
        setQuestion('');
        if (!sessions.find((s) => s.id === aiMsg.sessionId)) {
          // 新会话简单追加
          setSessions((prev) => [
            { id: aiMsg.sessionId, tenantId: aiMsg.tenantId, userId: 0, sessionTitle: '默认会话', status: 1, createTime: '', updateTime: '' },
            ...prev
          ]);
        }
      }
    } catch (e: any) {
      antdMessage.error(e?.response?.data?.message || '发送失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AppLayout>
      <div
        style={{
          display: 'flex',
          gap: 16,
          height: 'calc(100vh - 96px)',
          overflow: 'hidden'
        }}
      >
        <Card title="会话列表" style={{ width: 260, overflowY: 'auto', height: '100%' }}>
          <List
            dataSource={sessions}
            renderItem={(item) => (
              <List.Item
                style={{
                  cursor: 'pointer',
                  background: item.id === currentSessionId ? '#e6f4ff' : undefined
                }}
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
        <Card
          title="智能对话"
          style={{ flex: 1, height: '100%' }}
          bodyStyle={{
            display: 'flex',
            flexDirection: 'column',
            height: '100%',
            padding: 16,
            paddingTop: 8
          }}
        >
          <div className="chat-window" ref={chatWindowRef}>
            <List
              dataSource={messages}
              renderItem={(msg) => {
                const isUser = msg.senderType === 'USER';
                return (
                  <div className={`chat-message ${isUser ? 'user' : 'ai'}`}>
                    <div className={`chat-bubble ${isUser ? 'user' : 'ai'}`}>
                      <div style={{ marginBottom: 4, fontSize: 12, opacity: 0.7 }}>
                        {isUser ? '用户' : 'AI'}
                        {!isUser && msg.emotion && msg.emotion !== 'NORMAL' && (
                          <Tag color="red" style={{ marginLeft: 8 }}>
                            {msg.emotion}
                          </Tag>
                        )}
                      </div>
                      <div>{msg.content}</div>
                    </div>
                  </div>
                );
              }}
            />
          </div>
          <div style={{ marginTop: 8 }}>
            <TextArea
              rows={3}
              placeholder="请输入要咨询的问题..."
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onPressEnter={(e) => {
                if (!e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
            />
            <div style={{ textAlign: 'right', marginTop: 8 }}>
              <Button type="primary" onClick={handleSend} loading={loading}>
                发送
              </Button>
            </div>
          </div>
        </Card>
      </div>
    </AppLayout>
  );
}


