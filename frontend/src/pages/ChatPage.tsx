import { useEffect, useRef, useState } from 'react';
import { Card, List, Input, Button, message, Tag, Popconfirm, Space, Modal } from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined, UserSwitchOutlined } from '@ant-design/icons';
import { chatApi } from '../api/chat';
import type { ChatMessage, ChatSession } from '../api/types';
import { getCurrentSessionId, setCurrentSessionId as saveSessionId } from '../utils/storage';
import { WebSocketClient } from '../utils/websocket';
import HandoffModal from '../components/HandoffModal';
import HandoffStatus from '../components/HandoffStatus';
import HandoffRatingModal from '../components/HandoffRatingModal';
import { handoffApi } from '../api/handoff';
import type { HandoffStatusResponse } from '../api/types';

const { TextArea } = Input;

export default function ChatPage() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [creatingSession, setCreatingSession] = useState(false);
  const [titleModalOpen, setTitleModalOpen] = useState(false);
  const [newSessionTitle, setNewSessionTitle] = useState('');
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [renameSession, setRenameSession] = useState<ChatSession | null>(null);
  const [renameTitle, setRenameTitle] = useState('');
  const chatWindowRef = useRef<HTMLDivElement | null>(null);

  const [handoffModalOpen, setHandoffModalOpen] = useState(false);
  const [handoffStatus, setHandoffStatus] = useState<HandoffStatusResponse | null>(null);
  const [handoffPanelOpen, setHandoffPanelOpen] = useState(false);
  const [ratingModalOpen, setRatingModalOpen] = useState(false);
  const [pendingRatingHandoffId, setPendingRatingHandoffId] = useState<number | undefined>(undefined);
  const wsClientRef = useRef<WebSocketClient | null>(null);

  const insertSystemMessage = (content: string) => {
    setMessages((prev) => [
      ...prev,
      {
        id: Date.now(),
        senderType: 'SYSTEM',
        content,
        createTime: new Date().toISOString()
      } as any
    ]);
  };

  const checkPendingRating = async (sessionId: number) => {
    try {
      const res = await handoffApi.getPendingRating(sessionId);
      const pending = res?.data;
      if (pending?.needRating && pending?.handoffRequestId) {
        setPendingRatingHandoffId(Number(pending.handoffRequestId));
        setRatingModalOpen(true);
      }
    } catch {
      // 评价查询失败不影响主流程
    }
  };

  const setupWebSocket = (sessionId: number) => {
    if (wsClientRef.current) {
      wsClientRef.current.disconnect();
    }

    const client = new WebSocketClient('/ws/customer', () => sessionId);
    wsClientRef.current = client;

    client.on('TEXT', (payload) => {
      if (payload.sessionId === currentSessionId) {
        setMessages((prev) => [
          ...prev,
          {
            id: payload.messageId || Date.now(),
            senderType: 'AGENT',
            content: payload.content,
            createTime: new Date().toISOString()
          } as ChatMessage
        ]);
      }
    });

    client.on('QUEUE_UPDATE', (payload) => {
      if (handoffStatus && payload.handoffRequestId === handoffStatus.handoffRequestId) {
        setHandoffStatus((prev) => (prev ? { ...prev, ...payload } : null));
      }
    });

    client.on('HANDOFF_ACCEPTED', (payload) => {
      const payloadHandoffId = payload.handoffRequestId ? Number(payload.handoffRequestId) : null;
      const payloadSessionId = payload.sessionId ? Number(payload.sessionId) : null;

      const sessionMatches = payloadSessionId && currentSessionId && payloadSessionId === Number(currentSessionId);
      const handoffMatches = handoffStatus && payloadHandoffId && payloadHandoffId === Number(handoffStatus.handoffRequestId);

      if (sessionMatches || handoffMatches) {
        const agentName = payload.assignedAgentName || `客服#${payload.agentId || ''}`;
        message.success(`客服 ${agentName} 已接入`);
        setHandoffStatus((prev) => {
          if (prev) {
            return { ...prev, status: 'ACCEPTED', assignedAgentName: agentName } as HandoffStatusResponse;
          }
          return {
            handoffRequestId: payloadHandoffId || undefined,
            status: 'ACCEPTED',
            assignedAgentName: agentName
          } as HandoffStatusResponse;
        });
        insertSystemMessage(`客服 ${agentName} 已加入对话。`);
      }
    });

    client.on('HANDOFF_COMPLETED', (payload) => {
      if (currentSessionId === payload.sessionId) {
        setHandoffStatus(null);
        if (payload.endedBy === 'USER') {
          insertSystemMessage('您已结束与客服的对话，已切换回AI助手。');
        } else {
          insertSystemMessage('客服已结束本次人工会话，已切换回AI助手。');
        }
        checkPendingRating(Number(payload.sessionId));
      }
    });

    client.connect();
  };

  useEffect(() => {
    loadSessions(true);
  }, []);

  useEffect(() => {
    if (currentSessionId) {
      loadMessages(currentSessionId);
      setupWebSocket(currentSessionId);
      checkPendingRating(currentSessionId);
    }

    return () => {
      wsClientRef.current?.disconnect();
    };
  }, [currentSessionId]);

  useEffect(() => {
    chatWindowRef.current?.scrollTo({ top: chatWindowRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  const loadSessions = async (autoSelect: boolean) => {
    try {
      const res = await chatApi.sessions();
      if (res.success || res.code === '200') {
        const list = res.data || [];
        setSessions(list);
        if (autoSelect) {
          const savedId = getCurrentSessionId();
          const targetId = list.some((s) => s.id === savedId) ? savedId : list[0]?.id || null;
          if (targetId) {
            setCurrentSessionId(targetId);
            saveSessionId(targetId);
          }
        }
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载会话失败');
    }
  };

  const loadMessages = async (sessionId: number) => {
    try {
      const res = await chatApi.messages(sessionId);
      if (res.success || res.code === '200') {
        setMessages(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载消息失败');
    }
  };

  const handleSend = async () => {
    if (!inputValue.trim() || !currentSessionId) return;

    const userMessage: ChatMessage = {
      id: Date.now(),
      sessionId: currentSessionId,
      senderType: 'USER',
      content: inputValue,
      createTime: new Date().toISOString()
    } as any;
    setMessages((prev) => [...prev, userMessage]);
    const currentInput = inputValue;
    setInputValue('');

    if (handoffStatus && (handoffStatus.status === 'ACCEPTED' || handoffStatus.status === 'IN_PROGRESS')) {
      wsClientRef.current?.send('TEXT', { sessionId: currentSessionId, content: currentInput });
    } else {
      setLoading(true);
      try {
        const res = await chatApi.ask({ sessionId: currentSessionId, question: currentInput });
        if (res.success || res.code === '200') {
          setMessages((prev) => [...prev, res.data.aiMessage]);
        }
      } catch (e: any) {
        message.error(e?.response?.data?.message || '消息发送失败');
      } finally {
        setLoading(false);
      }
    }
  };

  const doCreateSession = async (title?: string) => {
    setCreatingSession(true);
    try {
      const res = await chatApi.createSession(title);
      if (res.success || res.code === '200') {
        message.success('会话创建成功');
        await loadSessions(false);
        if (res.data) {
          setCurrentSessionId(res.data.id);
          saveSessionId(res.data.id);
          setMessages([]);
        }
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '创建会话失败');
    } finally {
      setCreatingSession(false);
    }
  };

  const handleCreateSession = () => {
    setNewSessionTitle('');
    setTitleModalOpen(true);
  };

  const confirmCreateSession = async () => {
    const title = newSessionTitle.trim();
    setTitleModalOpen(false);
    await doCreateSession(title ? title : undefined);
  };

  const handleDeleteSession = async (sessionId: number, e?: React.MouseEvent) => {
    e?.stopPropagation();
    try {
      const res = await chatApi.deleteSession(sessionId);
      if (res.success || res.code === '200') {
        message.success('会话删除成功');
        if (sessionId === currentSessionId) {
          const remaining = sessions.filter((s) => s.id !== sessionId);
          const nextSession = remaining.length > 0 ? remaining[0] : null;
          setCurrentSessionId(nextSession?.id || null);
          if (nextSession) saveSessionId(nextSession.id);
        }
        await loadSessions(false);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '删除会话失败');
    }
  };

  const openRenameModal = (session: ChatSession, e?: React.MouseEvent) => {
    e?.stopPropagation();
    setRenameSession(session);
    setRenameTitle(session.sessionTitle || '');
    setRenameModalOpen(true);
  };

  const confirmRename = async () => {
    if (!renameSession) return;
    const title = renameTitle.trim();
    if (!title) {
      message.warning('会话名称不能为空');
      return;
    }
    try {
      const res = await chatApi.editSession(renameSession.id, title);
      if (res.success || res.code === '200') {
        const updated = res.data;
        message.success('会话名称已更新');
        setSessions((prev) => prev.map((s) => (s.id === updated.id ? { ...s, sessionTitle: updated.sessionTitle } : s)));
        setRenameModalOpen(false);
        setRenameSession(null);
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '更新会话名称失败');
    }
  };

  return (
    <div style={{ display: 'flex', gap: 16, height: 'calc(100vh - 96px)', overflow: 'hidden' }}>
      <Card
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>会话列表</span>
            <Button type="primary" icon={<PlusOutlined />} size="small" onClick={handleCreateSession} loading={creatingSession}>
              新建
            </Button>
          </div>
        }
        style={{ width: 300, overflowY: 'auto', height: '100%' }}
      >
        <Modal title="新建会话" open={titleModalOpen} okText="确定" cancelText="取消" onCancel={() => setTitleModalOpen(false)} onOk={confirmCreateSession} confirmLoading={creatingSession} destroyOnClose>
          <Input placeholder="例如：物流进度咨询" value={newSessionTitle} onChange={(e) => setNewSessionTitle(e.target.value)} maxLength={30} allowClear onPressEnter={confirmCreateSession} />
        </Modal>
        <Modal title="重命名会话" open={renameModalOpen} okText="确定" cancelText="取消" onCancel={() => { setRenameModalOpen(false); setRenameSession(null); }} onOk={confirmRename} destroyOnClose>
          <Input placeholder="请输入新的会话名称" value={renameTitle} onChange={(e) => setRenameTitle(e.target.value)} maxLength={30} allowClear onPressEnter={confirmRename} />
        </Modal>
        <List
          dataSource={sessions}
          renderItem={(item) => (
            <List.Item
              style={{ cursor: 'pointer', background: item.id === currentSessionId ? '#e6f4ff' : undefined, padding: '8px 12px', borderRadius: 4 }}
              onClick={() => {
                setCurrentSessionId(item.id);
                saveSessionId(item.id);
              }}
              actions={[
                <Button key="edit" type="text" size="small" icon={<EditOutlined />} onClick={(e) => openRenameModal(item, e)} />,
                <Popconfirm title="确定删除此会话吗？" onConfirm={(e) => handleDeleteSession(item.id, e)} onClick={(e) => e?.stopPropagation()} key="delete">
                  <Button type="text" danger size="small" icon={<DeleteOutlined />} onClick={(e) => e.stopPropagation()} />
                </Popconfirm>
              ]}
            >
              {item.sessionTitle || `会话 #${item.id}`}
            </List.Item>
          )}
        />
      </Card>

      <Card
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
            <span>{currentSessionId ? sessions.find((s) => s.id === currentSessionId)?.sessionTitle || `会话 #${currentSessionId}` : '智能对话'}</span>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button
                type={handoffStatus ? 'default' : 'primary'}
                icon={<UserSwitchOutlined />}
                size="small"
                onClick={() => {
                  if (!currentSessionId) {
                    message.warning('请先在左侧选择一个会话');
                    return;
                  }
                  if (handoffStatus) {
                    setHandoffPanelOpen(true);
                  } else {
                    setHandoffModalOpen(true);
                  }
                }}
              >
                {handoffStatus ? (handoffStatus.queuePosition ? `排队第 ${handoffStatus.queuePosition} 位` : '查看状态') : '转人工'}
              </Button>

              {handoffStatus && (handoffStatus.status === 'ACCEPTED' || handoffStatus.status === 'IN_PROGRESS') && (
                <Button
                  danger
                  size="small"
                  onClick={() => {
                    if (!handoffStatus.handoffRequestId) {
                      message.error('对话信息缺失');
                      return;
                    }
                    Modal.confirm({
                      title: '确认结束对话？',
                      content: '结束后将切换回AI助手，且无法继续与客服对话。',
                      okText: '确认结束',
                      cancelText: '取消',
                      okButtonProps: { danger: true },
                      onOk: async () => {
                        try {
                          await handoffApi.endByUser(handoffStatus.handoffRequestId as number);
                          message.success('对话已结束');
                          setHandoffStatus(null);
                          if (currentSessionId) {
                            checkPendingRating(currentSessionId);
                          }
                        } catch (e: any) {
                          message.error(e?.response?.data?.message || '结束对话失败');
                        }
                      }
                    });
                  }}
                >
                  结束对话
                </Button>
              )}
            </div>
          </div>
        }
        style={{ flex: 1, height: '100%' }}
        bodyStyle={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 16, paddingTop: 8 }}
      >
        <div ref={chatWindowRef} style={{ flex: 1, overflowY: 'auto', padding: '0 8px' }}>
          {handoffStatus && (
            <div style={{ marginBottom: 16, position: 'sticky', top: 0, zIndex: 10 }}>
              <HandoffStatus
                statusInfo={handoffStatus}
                onCancel={async () => {
                  if (handoffStatus.handoffRequestId) {
                    try {
                      await handoffApi.cancel(handoffStatus.handoffRequestId);
                      message.success('已取消转人工请求');
                      setHandoffStatus(null);
                      insertSystemMessage('已取消排队，切换回AI对话。');
                    } catch {
                      message.error('取消失败');
                    }
                  }
                }}
              />
            </div>
          )}
          <List
            dataSource={messages}
            renderItem={(msg) => {
              const isUser = msg.senderType === 'USER';
              const senderName = msg.senderType === 'AGENT' ? '客服' : msg.senderType === 'AI' ? 'AI' : '我';
              const bubbleClass = isUser ? 'user' : 'ai';

              if (msg.senderType === 'SYSTEM') {
                return (
                  <div style={{ textAlign: 'center', color: '#999', fontSize: 12, margin: '8px 0' }}>
                    {msg.content}
                  </div>
                );
              }

              return (
                <div className={`chat-message ${bubbleClass}`} style={{ marginBottom: 16 }}>
                  <div className={`chat-bubble ${bubbleClass}`}>
                    <div style={{ marginBottom: 4, fontSize: 12, opacity: 0.7 }}>{senderName}</div>
                    <div>{msg.content}</div>
                    {msg.refs && msg.refs.length > 0 && (
                      <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid #f0f0f0' }}>
                        <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>参考文档：</div>
                        <Space size={[8, 8]} wrap>
                          {msg.refs.map((ref, index) => (
                            <Tag key={index} color="blue">
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
            placeholder={handoffStatus && (handoffStatus.status === 'ACCEPTED' || handoffStatus.status === 'IN_PROGRESS') ? '正在与客服对话...' : '请输入要咨询的问题...'}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            disabled={loading}
          />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button
                onClick={() => {
                  if (!currentSessionId) {
                    message.warning('请先选择会话');
                    return;
                  }
                  if (handoffStatus) {
                    setHandoffPanelOpen(true);
                  } else {
                    setHandoffModalOpen(true);
                  }
                }}
                disabled={!currentSessionId}
                type={handoffStatus ? 'default' : 'primary'}
              >
                {handoffStatus
                  ? handoffStatus.status === 'ACCEPTED' || handoffStatus.status === 'IN_PROGRESS'
                    ? '客服中...'
                    : '排队中...'
                  : '转人工服务'}
              </Button>

              {handoffStatus && handoffStatus.status !== 'ACCEPTED' && handoffStatus.status !== 'IN_PROGRESS' && (
                <Button
                  danger
                  onClick={async () => {
                    if (handoffStatus.handoffRequestId) {
                      try {
                        await handoffApi.cancel(handoffStatus.handoffRequestId);
                        message.success('已取消转人工请求');
                        setHandoffStatus(null);
                        insertSystemMessage('已取消排队，切换回AI对话。');
                      } catch {
                        message.error('取消失败');
                      }
                    }
                  }}
                >
                  取消排队
                </Button>
              )}
            </div>
            <Button type="primary" onClick={handleSend} loading={loading}>
              发送
            </Button>
          </div>
        </div>
      </Card>

      <HandoffModal
        open={handoffModalOpen}
        sessionId={currentSessionId}
        onClose={() => setHandoffModalOpen(false)}
        onSuccess={(response) => {
          setHandoffModalOpen(false);
          if (response.fallback) {
            Modal.success({
              title: '已为您创建工单',
              content: response.fallbackMessage || `当前无客服在线，已为您创建工单 #${response.ticketId}。`
            });
            insertSystemMessage(response.fallbackMessage || '当前无客服在线，已为您创建工单。');
          } else {
            message.info('已为您转接人工客服，请稍候...');
            setHandoffStatus({
              handoffRequestId: response.handoffRequestId,
              status: 'PENDING',
              queuePosition: response.queuePosition,
              estimatedWaitTime: response.estimatedWaitTime
            });
            insertSystemMessage('正在为您转接人工客服...');
          }
        }}
      />

      <Modal title="转人工状态" open={handoffPanelOpen} footer={null} onCancel={() => setHandoffPanelOpen(false)} destroyOnClose>
        {handoffStatus ? (
          <HandoffStatus
            statusInfo={handoffStatus}
            onCancel={async () => {
              if (handoffStatus.handoffRequestId) {
                try {
                  await handoffApi.cancel(handoffStatus.handoffRequestId);
                  message.success('已取消转人工请求');
                  setHandoffStatus(null);
                  setHandoffPanelOpen(false);
                  insertSystemMessage('已取消排队，切换回AI对话。');
                } catch {
                  message.error('取消失败');
                }
              }
            }}
          />
        ) : (
          <div>当前没有转人工请求。</div>
        )}
      </Modal>

      <HandoffRatingModal
        open={ratingModalOpen}
        handoffRequestId={pendingRatingHandoffId}
        onClose={() => setRatingModalOpen(false)}
        onSuccess={() => {
          setRatingModalOpen(false);
          setPendingRatingHandoffId(undefined);
        }}
      />
    </div>
  );
}

