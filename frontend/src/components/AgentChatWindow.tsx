import { Input, Button, List, message as antdMessage, Popconfirm } from 'antd';
import { useState, useEffect, useRef } from 'react';
import type { ChatMessage } from '../api/types';
import { handoffApi } from '../api/handoff';

const { TextArea } = Input;

interface AgentChatWindowProps {
  session: {
    sessionId: number;
    userName: string;
    messages: ChatMessage[];
    handoffRequestId?: number; // 添加转人工请求ID
  };
  onSendMessage: (content: string) => void;
  onEndSession?: (sessionId: number) => void; // 添加结束会话回调
}

export default function AgentChatWindow({ session, onSendMessage, onEndSession }: AgentChatWindowProps) {
  const [inputValue, setInputValue] = useState('');
  const [ending, setEnding] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [session.messages]);

  const handleSend = () => {
    if (!inputValue.trim()) return;
    onSendMessage(inputValue);
    setInputValue('');
  };

  const handleEndSession = async () => {
    if (!session.handoffRequestId) {
      antdMessage.error('无法结束会话：缺少转人工请求ID，请刷新页面后重试');
      console.error('Missing handoffRequestId for session:', session);
      return;
    }

    try {
      setEnding(true);
      await handoffApi.complete(session.handoffRequestId);
      antdMessage.success('会话已结束');
      
      // 调用父组件的回调
      if (onEndSession) {
        onEndSession(session.sessionId);
      }
    } catch (error: any) {
      antdMessage.error(error?.response?.data?.message || '结束会话失败');
    } finally {
      setEnding(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 16 }}>
      <div style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center',
        borderBottom: '1px solid #f0f0f0', 
        paddingBottom: 16,
        marginBottom: 16
      }}>
        <h3 style={{ margin: 0 }}>
          正在与 {session.userName} 对话 (会话ID: {session.sessionId})
          {/* 调试信息：显示 handoffRequestId */}
          {process.env.NODE_ENV === 'development' && (
            <span style={{ fontSize: '12px', color: '#999', marginLeft: '8px' }}>
              [转人工ID: {session.handoffRequestId || '未设置'}]
            </span>
          )}
        </h3>
        <Popconfirm
          title="确定要结束此会话吗？"
          description="结束后将无法继续对话，会话将切换回AI模式。"
          onConfirm={handleEndSession}
          okText="确定"
          cancelText="取消"
          disabled={!session.handoffRequestId}
        >
          <Button 
            danger 
            loading={ending}
            disabled={!session.handoffRequestId}
            title={!session.handoffRequestId ? '缺少转人工请求ID，请刷新页面' : ''}
          >
            结束会话
          </Button>
        </Popconfirm>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', marginBottom: 16 }}>
        <List
          dataSource={session.messages}
          renderItem={(msg) => {
            const isAgent = msg.senderType === 'AGENT';
            return (
              <List.Item style={{ borderBottom: 'none', display: 'flex', justifyContent: isAgent ? 'flex-end' : 'flex-start' }}>
                <div style={{
                  background: isAgent ? '#e6f7ff' : '#f5f5f5',
                  padding: '8px 12px',
                  borderRadius: '12px',
                  maxWidth: '70%',
                }}>
                  {msg.content}
                </div>
              </List.Item>
            );
          }}
        />
        <div ref={messagesEndRef} />
      </div>
      <div>
        <TextArea
          rows={3}
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onPressEnter={(e) => { if (!e.shiftKey) { e.preventDefault(); handleSend(); } }}
        />
        <Button type="primary" onClick={handleSend} style={{ marginTop: 8, float: 'right' }}>
          发送
        </Button>
      </div>
    </div>
  );
}






