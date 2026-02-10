# 前端客服工作台实现指南 (WebSocket 对接修复)

> **目标**：修复客服端在接受转人工后无法与客户正常通信的问题。本文提供一个全新的、功能完整的客服工作台页面 (`AgentWorkbenchPage`)，它整合了待处理列表和实时聊天窗口，并确保了 WebSocket 消息格式的正确性。

---

## 📋 目录

1. [问题回顾](#1-问题回顾)
2. [核心组件设计](#2-核心组件设计)
   - `AgentWorkbenchPage.tsx`: 主工作台，管理会话
   - `AgentChatWindow.tsx`: 独立的聊天窗口组件
3. [详细代码实现](#3-详细代码实现)
   - [客服工作台页面代码](#31-客服工作台页面代码-agentworkbenchpagetsx)
   - [客服聊天窗口组件代码](#32-客服聊天窗口组件代码-agentchatwindowtsx)
4. [集成与路由](#4-集成与路由)
5. [后端健壮性优化 (建议)](#5-后端健壮性优化-建议)

---

## 1. 问题回顾

当前问题的核心是：客服端在通过 WebSocket 发送消息时，没有在消息体 `payload` 中包含 `sessionId`，导致后端无法处理该消息，从而中断了通信流程。

本方案通过创建一个新的、结构化的客服工作台页面来彻底解决此问题，确保每个聊天窗口都与其 `sessionId` 严格绑定。

---

## 2. 核心组件设计

我们将创建两个新的组件来构成客服的工作界面。

### `AgentWorkbenchPage.tsx`

-   **职责**: 作为客服的主要工作区。
-   **左侧**: 显示“待处理的转人工请求”列表和“正在进行中”的会话列表。
-   **右侧**: 显示当前选中的会话的聊天窗口 (`AgentChatWindow`)。
-   **状态管理**: 维护一个当前所有活跃会话（包括它们的 `sessionId`、消息列表等）的映射。
-   **WebSocket 管理**: 建立一个全局的客服端 WebSocket 连接，并根据收到的消息将其分发到对应的会话中。

### `AgentChatWindow.tsx`

-   **职责**: 渲染一个独立的对话界面。
-   **接收 props**: 接收 `sessionId`、历史消息、客服信息等。
-   **发送消息**: 当用户在此窗口输入并发送消息时，调用一个由父组件 (`AgentWorkbenchPage`) 传入的回调函数，该函数会通过 WebSocket 发送带有正确 `sessionId` 的消息。

---

## 3. 详细代码实现

### 3.1 客服工作台页面代码 (`AgentWorkbenchPage.tsx`)

这是最核心的文件，它管理着所有的状态和逻辑。

**文件路径**: `frontend/src/pages/agent/AgentWorkbenchPage.tsx` (新建文件)

```tsx
import { useEffect, useRef, useState } from 'react';
import { Card, List, Button, message, notification, Layout, Menu, Tag } from 'antd';
import { WebSocketClient } from '../../utils/websocket';
import { handoffApi } from '../../api/handoff';
import AgentChatWindow from '../../components/AgentChatWindow'; // 我们将创建这个组件
import type { ChatMessage } from '../../api/types';

const { Sider, Content } = Layout;

// 定义客服端会话的数据结构
interface AgentSession {
  sessionId: number;
  userId: number;
  userName: string;
  messages: ChatMessage[];
  unread: number;
}

export default function AgentWorkbenchPage() {
  const [pendingRequests, setPendingRequests] = useState<any[]>([]);
  const [activeSessions, setActiveSessions] = useState<Map<number, AgentSession>>(new Map());
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const wsClientRef = useRef<WebSocketClient | null>(null);

  useEffect(() => {
    // 1. 加载历史待处理请求
    const loadPending = async () => {
      try {
        const res = await handoffApi.getPendingRequests();
        setPendingRequests(res.data || []);
      } catch (e) {
        console.error('Failed to load pending requests', e);
      }
    };
    loadPending();

    // 2. 建立 WebSocket 连接
    const client = new WebSocketClient('/ws/agent');
    wsClientRef.current = client;

    // 3. 监听新的转人工请求
    client.on('HANDOFF_REQUEST', (payload) => {
      notification.info({
        message: '新的转人工请求',
        description: `来自客户 ${payload.userName || '未知'} 的请求，请及时处理。`,
        duration: 0,
      });
      setPendingRequests(prev => [payload, ...prev]);
    });

    // 4. 监听来自客户的消息
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
          // 如果当前不在这个会话，标记为未读
          if (sessionId !== currentSessionId) {
            session.unread = (session.unread || 0) + 1;
          }
        }
        return newSessions;
      });
    });

    client.connect();

    return () => {
      client.disconnect();
    };
  }, []);

  // 客服接受请求
  const handleAccept = async (request: any) => {
    try {
      const res = await handoffApi.accept(request.handoffRequestId);
      message.success('已接受请求，开始对话。');
      
      // 从待处理列表中移除
      setPendingRequests(prev => prev.filter(r => r.handoffRequestId !== request.handoffRequestId));

      // 创建一个新的活跃会话
      const newSession: AgentSession = {
        sessionId: res.data.sessionId,
        userId: res.data.userId,
        userName: request.userName || `客户 #${res.data.userId}`,
        messages: [], // 可以在这里预加载历史消息
        unread: 0,
      };

      setActiveSessions(prev => new Map(prev).set(newSession.sessionId, newSession));
      setCurrentSessionId(newSession.sessionId);

    } catch (e: any) { 
      message.error(e?.response?.data?.message || '接受失败');
    }
  };

  // 客服发送消息
  const handleSendMessage = (sessionId: number, content: string) => {
    if (!wsClientRef.current) return;

    // 构造带有 sessionId 的 payload
    const payload = { sessionId, content };
    wsClientRef.current.send('TEXT', payload);

    // 乐观更新 UI
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
              // 清除未读标记
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
          />
        ) : (
          <div style={{ textAlign: 'center', padding: '40px' }}>请从左侧选择一个会话开始处理。</div>
        )}
      </Content>
    </Layout>
  );
}
```

### 3.2 客服聊天窗口组件代码 (`AgentChatWindow.tsx`)

这是一个纯 UI 组件，负责渲染对话界面。

**文件路径**: `frontend/src/components/AgentChatWindow.tsx` (新建文件)

```tsx
import { Input, Button, List } from 'antd';
import { useState, useEffect, useRef } from 'react';
import type { ChatMessage } from '../api/types';

const { TextArea } = Input;

interface AgentChatWindowProps {
  session: {
    sessionId: number;
    userName: string;
    messages: ChatMessage[];
  };
  onSendMessage: (content: string) => void;
}

export default function AgentChatWindow({ session, onSendMessage }: AgentChatWindowProps) {
  const [inputValue, setInputValue] = useState('');
  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [session.messages]);

  const handleSend = () => {
    if (!inputValue.trim()) return;
    onSendMessage(inputValue);
    setInputValue('');
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 16 }}>
      <h3 style={{ marginTop: 0, borderBottom: '1px solid #f0f0f0', paddingBottom: 16 }}>
        正在与 {session.userName} 对话 (会话ID: {session.sessionId})
      </h3>
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
```

---

## 4. 集成与路由

1.  **创建客服工作台路由**
    在你的 `App.tsx` 或其他路由配置文件中，为客服角色添加一个指向新页面的路由。

    ```tsx
    // 在 App.tsx 或你的路由配置中
    import AgentWorkbenchPage from './pages/agent/AgentWorkbenchPage';

    // ...
    <Route path="/agent/workbench" element={<AgentWorkbenchPage />} />
    // ...
    ```

2.  **修改客服布局**
    你可能需要修改 `AgentLayout.tsx`，在侧边栏菜单中添加一个“我的工作台”或“实时会话”的链接，指向 `/agent/workbench`。

---

## 5. 后端健壮性优化 (建议)

为了防止因为前端意外没有发送 `sessionId` 而导致整个 WebSocket 连接断开，建议你优化一下后端的 `WebSocketMessageService.handleAgentMessage` 方法。

**文件路径**: `src/main/java/com/ityfz/yulu/handoff/websocket/service/WebSocketMessageService.java`

```java
// ...
public void handleAgentMessage(Long tenantId, Long agentId, WebSocketMessage wsMessage) {
    Map<String, Object> payload = wsMessage.getPayload();
    
    // 【修改点】健壮性检查
    Object sessionIdObj = payload.get("sessionId");
    if (sessionIdObj == null) {
        // 不再抛出异常，而是记录警告并返回，或者发送一个错误消息给客服
        log.warn("[WebSocket] 客服 {} 发送的消息缺少 sessionId", agentId);
        // 可以选择通过 WebSocket 发送一个错误提示给客服
        // agentHandler.sendToAgent(tenantId, agentId, createErrorMessage("消息发送失败：缺少会话ID"));
        return; 
    }
    
    Long sessionId = Long.valueOf(sessionIdObj.toString());

    // ... (后续逻辑保持不变)
}
```

这个小修改可以极大地提升系统的稳定性，避免因为前端的一个小错误导致整个客服的实时通信中断。
