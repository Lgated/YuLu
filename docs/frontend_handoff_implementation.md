# 前端转人工功能实现指南 (WebSocket + UI)

> **目标**：在你现有的前端项目基础上，集成 WebSocket，并实现完整的客户转人工申请、排队、与客服实时对话的 UI 与业务逻辑。

---

## 📋 目录

1. [项目准备](#1-项目准备)
   - [创建 WebSocket 工具类](#11-创建-websocket-工具类)
   - [创建 Handoff API 文件](#12-创建-handoff-api-文件)
   - [更新 API 类型定义](#13-更新-api-类型定义)
2. [UI 组件实现](#2-ui-组件实现)
   - [转人工弹窗 (HandoffModal)](#21-转人工弹窗-handoffmodal)
   - [转人工状态栏 (HandoffStatus)](#22-转人工状态栏-handoffstatus)
3. [核心页面改造 (ChatPage.tsx)](#3-核心页面改造-chatpagetsx)
   - [引入依赖与状态管理](#31-引入依赖与状态管理)
   - [集成 WebSocket 与事件处理](#32-集成-websocket-与事件处理)
   - [UI 渲染与交互逻辑](#33-ui-渲染与交互逻辑)
4. [客服端实现 (AgentHandoffPanel)](#4-客服端实现-agenthandoffpanel)

---

## 1. 项目准备

首先，我们需要创建一些基础的工具和类型文件。

### 1.1 创建 WebSocket 工具类

这个类将负责管理 WebSocket 连接、心跳、重连和消息收发。

**文件路径**: `frontend/src/utils/websocket.ts`

```typescript
import { getToken } from './storage';

// 定义 WebSocket 消息格式
export interface WebSocketMessage {
  type: string;
  payload: any;
  timestamp?: string;
  requestId?: string;
}

/**
 * 可重连、带心跳的 WebSocket 客户端
 */
export class WebSocketClient {
  private ws: WebSocket | null = null;
  private url: string;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private messageHandlers: Map<string, ((payload: any) => void)[]> = new Map();

  constructor(url: string, private getSessionId?: () => number | null) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.url = `${protocol}//${window.location.host}/api${url}`;
  }

  public connect() {
    if (this.ws) {
      this.ws.close();
    }

    const token = getToken();
    if (!token) {
      console.warn('[WebSocket] No token found, connection aborted.');
      return;
    }

    let fullUrl = `${this.url}?token=${token}`;
    if (this.getSessionId) {
        const sessionId = this.getSessionId();
        if (sessionId) {
            fullUrl += `&sessionId=${sessionId}`;
        }
    }

    this.ws = new WebSocket(fullUrl);

    this.ws.onopen = () => {
      console.log('[WebSocket] Connection established.');
      this.clearReconnectTimer();
      this.startHeartbeat();
    };

    this.ws.onmessage = (event) => {
      try {
        const message: WebSocketMessage = JSON.parse(event.data);
        this.handleMessage(message);
      } catch (error) {
        console.error('[WebSocket] Failed to parse message:', event.data);
      }
    };

    this.ws.onerror = (error) => {
      console.error('[WebSocket] Connection error:', error);
    };

    this.ws.onclose = (event) => {
      console.log(`[WebSocket] Connection closed: ${event.code}`);
      this.stopHeartbeat();
      if (event.code !== 1000) { // 1000 is normal closure
        this.scheduleReconnect();
      }
    };
  }

  private handleMessage(message: WebSocketMessage) {
    const handlers = this.messageHandlers.get(message.type);
    if (handlers) {
      handlers.forEach(handler => handler(message.payload));
    }
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      this.send('PING', {});
    }, 30000); // 30-second heartbeat
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      console.log('[WebSocket] Reconnecting...');
      this.connect();
    }, 5000); // Reconnect after 5 seconds
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  public send(type: string, payload: any) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const message: WebSocketMessage = {
        type,
        payload,
        timestamp: new Date().toISOString()
      };
      this.ws.send(JSON.stringify(message));
    } else {
      console.warn('[WebSocket] Connection not open. Message not sent:', { type, payload });
    }
  }

  public on(type: string, handler: (payload: any) => void) {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, []);
    }
    this.messageHandlers.get(type)!.push(handler);
  }

  public off(type: string, handler: (payload: any) => void) {
    const handlers = this.messageHandlers.get(type);
    if (handlers) {
      const index = handlers.indexOf(handler);
      if (index > -1) {
        handlers.splice(index, 1);
      }
    }
  }

  public disconnect() {
    this.clearReconnectTimer();
    this.stopHeartbeat();
    if (this.ws) {
      this.ws.onclose = null; // Prevent reconnection on manual disconnect
      this.ws.close(1000, 'Manual disconnect');
      this.ws = null;
    }
    console.log('[WebSocket] Disconnected manually.');
  }
}
```

### 1.2 创建 Handoff API 文件

将所有与转人工相关的 HTTP 请求集中管理。

**文件路径**: `frontend/src/api/handoff.ts`

```typescript
import http from './axios';
import type { ApiResponse, HandoffTransferRequest, HandoffTransferResponse, HandoffStatusResponse } from './types';

/**
 * 转人工（Handoff）相关 API
 */
export const handoffApi = {
  /**
   * 客户申请转人工
   */
  requestTransfer(payload: HandoffTransferRequest) {
    return http.post<ApiResponse<HandoffTransferResponse>>('/customer/handoff/transfer', payload);
  },

  /**
   * 客户查询转人工状态
   */
  getStatus(handoffRequestId: number) {
    return http.get<ApiResponse<HandoffStatusResponse>>(`/customer/handoff/status/${handoffRequestId}`);
  },

  /**
   * 客户取消转人工
   */
  cancel(handoffRequestId: number) {
    return http.post<ApiResponse<void>>(`/customer/handoff/cancel/${handoffRequestId}`);
  },

  // ... (客服端接口，暂时保留)
};
```

### 1.3 更新 API 类型定义

在 `types.ts` 文件中添加转人工流程所需的 TypeScript 接口。

**文件路径**: `frontend/src/api/types.ts` (在文件末尾追加)

```typescript
// ... (保留你已有的所有类型)

/**
 * 客户申请转人工的请求体
 */
export interface HandoffTransferRequest {
  sessionId: number;
  reason?: string;
}

/**
 * 申请转人工的响应体
 */
export interface HandoffTransferResponse {
  handoffRequestId: number;
  ticketId: number;
  queuePosition?: number;
  estimatedWaitTime?: number;
  fallback: boolean;
  fallbackMessage?: string;
}

/**
 * 查询转人工状态的响应体
 */
export interface HandoffStatusResponse {
  handoffRequestId: number;
  status: 'PENDING' | 'ASSIGNED' | 'ACCEPTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED' | 'CANCELLED' | 'FALLBACK_TICKET';
  queuePosition?: number;
  estimatedWaitTime?: number;
  assignedAgentId?: number;
  assignedAgentName?: string;
}
```

---

## 2. UI 组件实现

创建两个可复用的 React 组件来处理转人工的 UI。

### 2.1 转人工弹窗 (HandoffModal)

用于让客户输入转人工原因并提交申请。

**文件路径**: `frontend/src/components/HandoffModal.tsx` (新建文件)

```tsx
import { Modal, Form, Input, Button, message } from 'antd';
import { useState } from 'react';
import { handoffApi } from '../api/handoff';
import type { HandoffTransferResponse } from '../api/types';

interface HandoffModalProps {
  open: boolean;
  sessionId: number | null;
  onClose: () => void;
  onSuccess: (response: HandoffTransferResponse) => void;
}

export default function HandoffModal({ open, sessionId, onClose, onSuccess }: HandoffModalProps) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleFinish = async (values: { reason: string }) => {
    if (!sessionId) {
      message.error('会话 ID 无效');
      return;
    }
    setLoading(true);
    try {
      const res = await handoffApi.requestTransfer({ sessionId, reason: values.reason });
      if (res.success || res.code === '200') {
        onSuccess(res.data);
        form.resetFields();
      } else {
        message.error(res.message || '申请转人工失败');
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '申请转人工失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="申请转人工服务"
      open={open}
      onCancel={onClose}
      footer={null}
      destroyOnClose
    >
      <p style={{ marginBottom: 16, color: '#666' }}>
        您即将连接人工客服。如果需要，请简要描述您遇到的问题，以便我们更快地为您服务。
      </p>
      <Form form={form} onFinish={handleFinish} layout="vertical">
        <Form.Item name="reason" label="问题描述 (可选)">
          <Input.TextArea rows={4} placeholder="例如：AI 的回答不准确，我需要更详细的解释。" />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block>
            确认转接
          </Button>
        </Form.Item>
      </Form>
    </Modal>
  );
}
```

### 2.2 转人工状态栏 (HandoffStatus)

用于在聊天页面顶部显示排队信息或连接状态。

**文件路径**: `frontend/src/components/HandoffStatus.tsx` (新建文件)

```tsx
import { Alert, Button, Space, Spin } from 'antd';
import type { HandoffStatusResponse } from '../api/types';

interface HandoffStatusProps {
  statusInfo: HandoffStatusResponse;
  onCancel: () => void;
}

export default function HandoffStatus({ statusInfo, onCancel }: HandoffStatusProps) {
  const { status, queuePosition, estimatedWaitTime, assignedAgentName } = statusInfo;

  let message: React.ReactNode;
  let description: React.ReactNode | undefined;
  let showCancel = false;

  switch (status) {
    case 'PENDING':
    case 'ASSIGNED':
      message = '正在为您连接人工客服...';
      description = (
        <Space>
          <Spin size="small" />
          {queuePosition && queuePosition > 0
            ? `您当前排在第 ${queuePosition} 位，预计等待 ${estimatedWaitTime || '-'} 秒。`
            : '正在分配客服，请稍候。'}
        </Space>
      );
      showCancel = true;
      break;
    case 'ACCEPTED':
    case 'IN_PROGRESS':
      message = `客服 ${assignedAgentName || ''} 正在为您服务`;
      break;
    case 'COMPLETED':
      message = '本次人工服务已结束。';
      break;
    case 'CANCELLED':
      message = '您已取消转人工请求。';
      break;
    default:
      return null;
  }

  return (
    <Alert
      message={message}
      description={description}
      type="info"
      showIcon
      style={{ marginBottom: 16 }}
      action={
        showCancel ? (
          <Button size="small" type="text" danger onClick={onCancel}>
            取消
          </Button>
        ) : null
      }
    />
  );
}
```

---

## 3. 核心页面改造 (`ChatPage.tsx`)

这是最核心的部分，我们将为 `ChatPage.tsx` 增加转人工的完整逻辑。

### 3.1 引入依赖与状态管理

在 `ChatPage.tsx` 顶部引入我们刚刚创建的组件和 API，并添加新的 state 来管理转人工流程。

```tsx
// ... (其他 import)
import { WebSocketClient } from '../utils/websocket';
import HandoffModal from '../components/HandoffModal';
import HandoffStatus from '../components/HandoffStatus';
import { handoffApi } from '../api/handoff';
import type { HandoffStatusResponse, HandoffTransferResponse, ChatMessage } from '../api/types';
import { Modal } from 'antd'; // 确保 antd 的 Modal 已引入

// ...

export default function ChatPage() {
  // ... (保留你已有的 state)

  // 新增转人工相关的 state
  const [handoffModalOpen, setHandoffModalOpen] = useState(false);
  const [handoffStatus, setHandoffStatus] = useState<HandoffStatusResponse | null>(null);
  const wsClientRef = useRef<WebSocketClient | null>(null);

  // ...
}
```

### 3.2 集成 WebSocket 与事件处理

我们需要在会话加载或切换后初始化 WebSocket，并监听来自服务器的事件。

```tsx
// 在 ChatPage 组件内部

// 初始化 WebSocket 连接
const setupWebSocket = (sessionId: number) => {
  if (wsClientRef.current) {
    wsClientRef.current.disconnect();
  }
  
  const client = new WebSocketClient('/ws/customer', () => sessionId);
  wsClientRef.current = client;

  // 监听新消息
  client.on('TEXT', (payload) => {
    if (payload.sessionId === currentSessionId) {
      // 将客服消息添加到消息列表
      setMessages(prev => [...prev, {
        id: payload.messageId,
        senderType: 'AGENT',
        content: payload.content,
        createTime: new Date().toISOString(),
      } as ChatMessage]);
    }
  });

  // 监听排队状态更新
  client.on('QUEUE_UPDATE', (payload) => {
    if (handoffStatus && payload.handoffRequestId === handoffStatus.handoffRequestId) {
      setHandoffStatus(prev => prev ? { ...prev, ...payload } : null);
    }
  });

  // 监听客服接受
  client.on('HANDOFF_ACCEPTED', (payload) => {
    if (handoffStatus && payload.handoffRequestId === handoffStatus.handoffRequestId) {
      message.success(`客服 ${payload.assignedAgentName || ''} 已接入`);
      setHandoffStatus(prev => prev ? { ...prev, status: 'ACCEPTED', ...payload } : null);
      insertSystemMessage(`客服 ${payload.assignedAgentName || ''} 已加入对话。`);
    }
  });
  
  // 监听对话结束
  client.on('HANDOFF_COMPLETED', (payload) => {
      if (currentSessionId === payload.sessionId) {
          setHandoffStatus(null); // 清理状态
          insertSystemMessage('本次人工服务已结束。');
      }
  });

  client.connect();
};

// 在 useEffect 中调用
useEffect(() => {
  if (currentSessionId) {
    loadMessages(currentSessionId);
    // 当会话 ID 确定后，设置 WebSocket
    setupWebSocket(currentSessionId);
  }

  return () => {
    // 组件卸载时断开连接
    wsClientRef.current?.disconnect();
  };
}, [currentSessionId]);

// 插入系统消息的辅助函数
const insertSystemMessage = (content: string) => {
    setMessages(prev => [...prev, {
        id: Date.now(), // 临时 ID
        senderType: 'SYSTEM', // 你可能需要一个 SYSTEM 类型来展示不同的样式
        content,
        createTime: new Date().toISOString(),
    } as any]); // 使用 any 绕过类型检查，因为 ChatMessage 可能没有 SYSTEM 类型
};
```

### 3.3 UI 渲染与交互逻辑

最后，我们将转人工的按钮、弹窗和状态栏集成到 `ChatPage` 的 JSX 中。

```tsx
// 在 ChatPage 组件的 return 语句中

// 1. 添加一个“转人工”按钮 (可以放在输入框旁边)
<Button onClick={() => setHandoffModalOpen(true)} disabled={!currentSessionId || !!handoffStatus}>
  转人工服务
</Button>

// 2. 在聊天消息列表的顶部，渲染转人工状态栏
<div className="chat-messages" ref={messagesEndRef}>
  {handoffStatus && (
    <HandoffStatus 
      statusInfo={handoffStatus} 
      onCancel={async () => {
        if (handoffStatus.handoffRequestId) {
          try {
            await handoffApi.cancel(handoffStatus.handoffRequestId);
            message.success('已取消转人工请求');
            setHandoffStatus(null);
          } catch (e) {
            message.error('取消失败');
          }
        }
      }}
    />
  )}
  {messages.map((msg) => (
    // ... 你的消息渲染逻辑
  ))}
</div>

// 3. 在组件的根部渲染转人工弹窗
<HandoffModal
  open={handoffModalOpen}
  sessionId={currentSessionId}
  onClose={() => setHandoffModalOpen(false)}
  onSuccess={(response) => {
    setHandoffModalOpen(false);
    if (response.fallback) {
      // 处理无客服在线的兜底情况
      Modal.success({
        title: '已为您创建工单',
        content: response.fallbackMessage || `当前无客服在线，已为您创建工单 #${response.ticketId}。`,
      });
    } else {
      // 进入正常排队
      message.info('已为您转接人工客服，请稍候...');
      setHandoffStatus({
        handoffRequestId: response.handoffRequestId,
        status: 'PENDING',
        queuePosition: response.queuePosition,
        estimatedWaitTime: response.estimatedWaitTime,
      });
    }
  }}
/>

// 4. 修改消息发送逻辑
const handleSend = async () => {
  if (!inputValue.trim() || !currentSessionId) return;

  // 将用户消息立即添加到UI
  const userMessage: ChatMessage = {
    id: Date.now(), // 临时ID
    sessionId: currentSessionId,
    senderType: 'USER',
    content: inputValue,
    createTime: new Date().toISOString(),
  } as any;
  setMessages(prev => [...prev, userMessage]);
  const currentInput = inputValue;
  setInputValue('');
  setLoading(true);

  try {
    if (handoffStatus && (handoffStatus.status === 'ACCEPTED' || handoffStatus.status === 'IN_PROGRESS')) {
      // 如果是人工模式，通过 WebSocket 发送
      wsClientRef.current?.send('TEXT', { sessionId: currentSessionId, content: currentInput });
    } else {
      // 否则，走原来的 AI 聊天逻辑
      const res = await chatApi.ask({ sessionId: currentSessionId, question: currentInput });
      if (res.success || res.code === '200') {
        setMessages(prev => [...prev, res.data.aiMessage]);
      }
    }
  } catch (e: any) {
    message.error(e?.response?.data?.message || '消息发送失败');
    // 发送失败时，可以考虑将刚才乐观添加的用户消息标记为失败
  } finally {
    setLoading(false);
  }
};
```

---

## 4. 客服端实现 (AgentHandoffPanel)

对于客服端，你需要一个面板来接收和处理转人工请求。

**文件路径**: `frontend/src/components/AgentHandoffPanel.tsx` (新建文件)

```tsx
import { useEffect, useRef, useState } from 'react';
import { Card, List, Button, message, notification, Tag } from 'antd';
import { WebSocketClient } from '../utils/websocket';
import { handoffApi } from '../api/handoff';

interface HandoffRequest {
  handoffRequestId: number;
  userName: string;
  reason: string;
  priority: string;
}

export default function AgentHandoffPanel() {
  const [requests, setRequests] = useState<HandoffRequest[]>([]);
  const wsClientRef = useRef<WebSocketClient | null>(null);

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

  const handleAccept = async (handoffRequestId: number) => {
    try {
      await handoffApi.accept(handoffRequestId);
      message.success('已接受请求，请在会话列表中开始对话。');
      setRequests(prev => prev.filter(r => r.handoffRequestId !== handoffRequestId));
      // 你可以在这里触发会话列表的刷新
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
              <Button type="primary" size="small" onClick={() => handleAccept(item.handoffRequestId)}>
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
```

> **集成**: 你可以将 `AgentHandoffPanel` 组件放置在客服工作台的布局中，例如 `AgentLayout.tsx` 或 `AgentDashboardPage.tsx`。
