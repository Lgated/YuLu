# 前端客服工作台集成指南

> **目标**: 解决客服发送消息时后端报 “会话ID不能为空” 的问题。本文档将指导你如何正确地将新的 `AgentWorkbenchPage` 集成到你的前端应用中，以确保 WebSocket 消息包含了必要的 `sessionId`。

---

## 1. 问题根源

此问题的根本原因在于，当前客服聊天界面在发送 WebSocket 消息时，没有将 `sessionId` 包含在消息的 `payload` 中。我们之前创建的 `AgentWorkbenchPage.tsx` 和 `AgentChatWindow.tsx` 已经修复了这个问题，但应用目前可能仍在使用旧的、不正确的页面组件。

## 2. 集成步骤

请按照以下步骤操作，确保你的应用加载的是新的工作台页面。

### 步骤 1: 检查文件是否存在

首先，请确认以下两个文件已存在于你的项目中，并且文件内容与我之前提供的一致：

-   `frontend/src/pages/agent/AgentWorkbenchPage.tsx`
-   `frontend/src/components/AgentChatWindow.tsx`

### 步骤 2: 修改应用主路由 (`App.tsx`)

我们需要告诉应用，当客服登录后，应该使用哪个页面作为其主界面。我们将修改 `App.tsx`，将客服相关的路由指向新的 `AgentWorkbenchPage`。

**文件路径**: `frontend/src/App.tsx`

**请找到类似如下的代码块**:

```tsx
// ... (imports)
import AgentLayout from './components/layout/AgentLayout';
import AgentSessionsPage from './pages/agent/AgentSessionsPage'; // <-- 旧的页面
import AgentTicketPage from './pages/agent/AgentTicketPage';
// ... (其他 imports)

function App() {
  // ...
  return (
    <Routes>
      {/* ... (其他路由) */}

      {/* 客服路由组 */}
      <Route path="/agent" element={<AgentLayout />}>
        <Route index element={<Navigate to="sessions" />} />
        <Route path="sessions" element={<AgentSessionsPage />} /> {/* <-- 这里是旧的页面组件 */}
        <Route path="tickets" element={<AgentTicketPage />} />
        {/* ... (其他客服路由) */}
      </Route>
    </Routes>
  );
}
```

**修改为**: 

1.  **导入**新的 `AgentWorkbenchPage`。
2.  将 `sessions` 路径的 `element` **替换**为 `AgentWorkbenchPage`。
3.  将默认跳转路径也**修改**为 `workbench`。

```tsx
// ... (imports)
import AgentLayout from './components/layout/AgentLayout';
// import AgentSessionsPage from './pages/agent/AgentSessionsPage'; // <-- 注释或删除旧的 import
import AgentWorkbenchPage from './pages/agent/AgentWorkbenchPage'; // <-- 1. 导入新页面
import AgentTicketPage from './pages/agent/AgentTicketPage';
// ... (其他 imports)

function App() {
  // ...
  return (
    <Routes>
      {/* ... (其他路由) */}

      {/* 客服路由组 */}
      <Route path="/agent" element={<AgentLayout />}>
        <Route index element={<Navigate to="workbench" />} /> {/* <-- 2. 修改默认跳转 */}
        <Route path="workbench" element={<AgentWorkbenchPage />} /> {/* <-- 3. 使用新页面组件 */}
        {/* <Route path="sessions" element={<AgentSessionsPage />} /> */}{/* <-- 4. 注释或删除旧的路由 */}
        <Route path="tickets" element={<AgentTicketPage />} />
        {/* ... (其他客服路由) */}
      </Route>
    </Routes>
  );
}
```

### 步骤 3: 修改客服布局菜单 (`AgentLayout.tsx`)

现在，我们需要更新客服工作台的侧边栏菜单，使其链接到新的工作台页面。

**文件路径**: `frontend/src/components/layout/AgentLayout.tsx`

**请找到定义菜单项 (items) 的代码**，它可能看起来像这样：

```tsx
// ...
const menuItems = [
  {
    key: 'sessions',
    icon: <CommentOutlined />,
    label: <Link to="/agent/sessions">我的会话</Link>,
  },
  {
    key: 'tickets',
    icon: <FileTextOutlined />,
    label: <Link to="/agent/tickets">我的工单</Link>,
  },
  // ...
];
// ...
```

**修改为**: 

将 “我的会话” 的 `key` 和 `to` 链接都指向 `workbench`。

```tsx
// ...
const menuItems = [
  {
    key: 'workbench', // <-- 修改 key
    icon: <CommentOutlined />,
    label: <Link to="/agent/workbench">我的工作台</Link>, // <-- 修改链接和标签
  },
  {
    key: 'tickets',
    icon: <FileTextOutlined />,
    label: <Link to="/agent/tickets">我的工单</Link>,
  },
  // ...
];
// ...
```

---

## 3. 验证

完成以上修改后，请重新启动你的前端应用 (`npm run dev`)。

1.  以**客服身份**登录。
2.  应用应该会自动跳转到 `/agent/workbench`，并显示新的工作台界面（左侧是“待处理请求”和“进行中会话”，右侧是提示信息）。
3.  此时，再重复一遍转人工的流程：
    -   客户申请转人工。
    -   客服在工作台的“待处理请求”列表中点击“接受”。
    -   右侧应该会加载出聊天窗口。
    -   **现在，当客服在此窗口发送消息时，后端的 `sessionId` 错误应该已经消失，客户能够正常收到消息。**

如果仍然存在问题，请检查浏览器的开发者工具控制台是否有新的报错信息。







