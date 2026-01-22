import { useEffect, useRef, useState } from 'react';
import { Card, List, Input, Button, message as antdMessage, Tag, Popconfirm, Space } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { chatApi } from '../api/chat';
import type { ChatMessage, ChatSession, ChatAskResponse, RagRef } from '../api/types';
import { getCurrentSessionId, setCurrentSessionId as saveSessionId } from '../utils/storage';

const { TextArea } = Input;

export default function ChatPage() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState('');
  const [loading, setLoading] = useState(false);
  const [creatingSession, setCreatingSession] = useState(false);
  const chatWindowRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    // 每次进入页面都先加载会话列表，再根据 localStorage 的 sessionId 恢复历史
    loadSessions(true);
  }, []);

  // 消息变化时自动滚动到底部
  useEffect(() => {
    const el = chatWindowRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages]);

  const loadSessions = async (autoSelect: boolean) => {
    try {
      const res = await chatApi.sessions();
      if (res.success || res.code === '200') {
        const list = res.data || [];
        setSessions(list);

        if (!autoSelect) return;

        const savedId = getCurrentSessionId();
        // 优先恢复上次会话；若不存在则选第一个
        const targetId =
          savedId && list.some((s) => s.id === savedId)
            ? savedId
            : list.length > 0
            ? list[0].id
            : null;

        if (targetId) {
          setCurrentSessionId(targetId);
          saveSessionId(targetId);
          await loadMessages(targetId);
        } else {
          // 没有任何会话：清空消息
          setCurrentSessionId(null);
          setMessages([]);
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

  // 创建新会话
  const handleCreateSession = async () => {
    setCreatingSession(true);
    try {
      const res = await chatApi.createSession();
      if (res.success || res.code === '200') {
        antdMessage.success('会话创建成功');
        await loadSessions(false);
        // 切换到新会话
        if (res.data) {
          setCurrentSessionId(res.data.id);
          saveSessionId(res.data.id);
          setMessages([]);
        }
      }
    } catch (e: any) {
      antdMessage.error(e?.response?.data?.message || '创建会话失败');
    } finally {
      setCreatingSession(false);
    }
  };

  // 删除会话
  const handleDeleteSession = async (sessionId: number, e?: React.MouseEvent) => {
    e?.stopPropagation();
    try {
      const res = await chatApi.deleteSession(sessionId);
      if (res.success || res.code === '200') {
        antdMessage.success('会话删除成功');
        // 如果删除的是当前会话，切换到其他会话
        if (sessionId === currentSessionId) {
          const remaining = sessions.filter((s) => s.id !== sessionId);
          if (remaining.length > 0) {
            setCurrentSessionId(remaining[0].id);
            saveSessionId(remaining[0].id);
            await loadMessages(remaining[0].id);
          } else {
            setCurrentSessionId(null);
            setMessages([]);
          }
        }
        await loadSessions(false);
      }
    } catch (e: any) {
      antdMessage.error(e?.response?.data?.message || '删除会话失败');
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
        const data: ChatAskResponse = res.data;
        const aiMsg = data.aiMessage;
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
        // 将用户消息和AI消息都添加到消息列表，并保存引用信息
        setMessages((prev) => [
          ...prev,
          { ...userMsg, refs: undefined }, // 用户消息没有引用
          { ...aiMsg, refs: data.refs } // AI消息包含引用
        ]);
        setQuestion('');
        await loadSessions(false); // 刷新会话列表
      }
    } catch (e: any) {
      antdMessage.error(e?.response?.data?.message || '发送失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        gap: 16,
        height: 'calc(100vh - 96px)',
        overflow: 'hidden'
      }}
    >
      <Card
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>会话列表</span>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              size="small"
              onClick={handleCreateSession}
              loading={creatingSession}
            >
              新建
            </Button>
          </div>
        }
        style={{ width: 300, overflowY: 'auto', height: '100%' }}
      >
        <List
          dataSource={sessions}
          renderItem={(item) => (
            <List.Item
              style={{
                cursor: 'pointer',
                background: item.id === currentSessionId ? '#e6f4ff' : undefined,
                padding: '8px 12px',
                borderRadius: 4
              }}
              onClick={() => {
                setCurrentSessionId(item.id);
                saveSessionId(item.id);
                loadMessages(item.id);
              }}
              actions={[
                <Popconfirm
                  title="确定删除此会话吗？"
                  onConfirm={(e) => handleDeleteSession(item.id, e)}
                  onClick={(e) => e.stopPropagation()}
                  key="delete"
                >
                  <Button
                    type="text"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    onClick={(e) => e.stopPropagation()}
                  />
                </Popconfirm>
              ]}
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
        <div className="chat-window" ref={chatWindowRef} style={{ flex: 1, overflowY: 'auto', marginBottom: 16 }}>
          <List
            dataSource={messages}
            renderItem={(msg) => {
              const isUser = msg.senderType === 'USER';
              const refs: RagRef[] = msg.refs || [];
              
              return (
                <div className={`chat-message ${isUser ? 'user' : 'ai'}`} style={{ marginBottom: 16 }}>
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
                    
                    {/* 显示引用文档 */}
                    {!isUser && refs && refs.length > 0 && (
                      <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid #f0f0f0' }}>
                        <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>
                          参考文档：
                        </div>
                        <Space size={[8, 8]} wrap>
                          {refs.map((ref, index) => (
                            <Tag key={index} color="blue" style={{ margin: 0 }}>
                              {ref.title}
                            </Tag>
                          ))}
                        </Space>
                      </div>
                    )}
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
  );
}


