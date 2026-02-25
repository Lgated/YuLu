import { useEffect, useRef, useState } from 'react';
import { Card, List, Input, Button, message, Modal } from 'antd';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { sessionApi } from '../../api/chat';
import { handoffApi } from '../../api/handoff';
import { WebSocketClient } from '../../utils/websocket';

const { TextArea } = Input;

export default function AgentWorkspacePage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const sessionIdParam = searchParams.get('sessionId');
  const sessionId = sessionIdParam ? Number(sessionIdParam) : null;
  const handoffRequestIdParam = searchParams.get('handoffRequestId');
  const handoffRequestId = handoffRequestIdParam ? Number(handoffRequestIdParam) : null;

  const [messages, setMessages] = useState<any[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [inputDisabled, setInputDisabled] = useState(false);
  const wsRef = useRef<WebSocketClient | null>(null);

  useEffect(() => {
    if (!sessionId) return;

    const loadMessages = async () => {
      try {
        const res = await sessionApi.listMessages(sessionId);
        if (res.success || res.code === '200') {
          setMessages(res.data || []);
        }
      } catch (e) {
        console.error('Failed to load messages', e);
      }
    };
    loadMessages();

    const client = new WebSocketClient('/ws/agent');
    wsRef.current = client;

    client.on('TEXT', (payload) => {
      if (payload.sessionId === sessionId) {
        setMessages(prev => [...prev, {
          id: payload.messageId || Date.now(),
          senderType: payload.senderType || 'USER',
          content: payload.content,
          createTime: new Date().toISOString()
        }]);
      }
    });

    // 监听对话完成事件
    client.on('HANDOFF_COMPLETED', (payload) => {
      if (payload.sessionId === sessionId) {
        setInputDisabled(true);
        message.info('对话已结束，3秒后返回待接入列表...');
        setTimeout(() => {
          navigate('/agent/sessions');
        }, 3000);
      }
    });

    client.connect();

    return () => {
      client.disconnect();
    };
  }, [sessionId]);

  const handleSend = () => {
    if (inputDisabled) {
      message.warning('会话已结束或已释放，无法发送消息');
      return;
    }
    if (!sessionId) {
      message.warning('无效会话');
      return;
    }
    if (!inputValue.trim()) return;

    setLoading(true);
    try {
      wsRef.current?.send('TEXT', { sessionId, content: inputValue });
      setMessages(prev => [...prev, { id: Date.now(), senderType: 'AGENT', content: inputValue, createTime: new Date().toISOString() }]);
      setInputValue('');
    } catch (e) {
      message.error('发送失败');
    } finally {
      setLoading(false);
    }
  };

  // 结束对话
  const handleCompleteConversation = () => {
    if (!handoffRequestId) {
      message.error('缺少转人工信息');
      return;
    }

    Modal.confirm({
      title: '确认结束对话？',
      content: '结束后用户将切换回 AI 助手，且无法继续与你对话。',
      okText: '确认结束',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setCompleting(true);
        try {
          await handoffApi.complete(handoffRequestId);
          message.success('对话已结束，用户已切换回 AI 助手');
          // 返回待接入会话列表
          setTimeout(() => {
            navigate('/agent/sessions');
          }, 1000);
        } catch (e: any) {
          message.error(e?.response?.data?.message || '结束对话失败');
        } finally {
          setCompleting(false);
        }
      },
    });
  };

  return (
    <Card
      title={`会话 #${sessionId} (客服)`}
      extra={
        handoffRequestId && (
          <Button
            danger
            onClick={handleCompleteConversation}
            loading={completing}
          >
            结束对话
          </Button>
        )
      }
      style={{ height: '100%' }}
    >
      <div style={{ height: 500, overflowY: 'auto', padding: 8 }}>
        <List
          dataSource={messages}
          renderItem={(msg: any) => (
            <div style={{ marginBottom: 12 }}>
              <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>{msg.senderType}</div>
              <div>{msg.content}</div>
            </div>
          )}
        />
      </div>

      <div style={{ marginTop: 12 }}>
        <TextArea rows={3} value={inputValue} onChange={(e) => setInputValue(e.target.value)} disabled={inputDisabled} />
        <div style={{ marginTop: 8, textAlign: 'right' }}>
          <Button type="primary" onClick={handleSend} loading={loading}>发送</Button>
        </div>
      </div>
    </Card>
  );
}
