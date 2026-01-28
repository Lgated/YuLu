# React 前端设计与实现指南（YuLu 智链客服中台）

> 目标：基于你**现有后端接口**，从零设计并实现一个符合“客服中台”主题的 React 前端。  
> 要求：风格偏中后台（Dashboard）、多租户客服场景，支持登录、AI 对话、工单管理、通知中心等。

---

## 一、技术栈与总体结构

### 1.1 技术栈推荐

- **构建工具**：Vite + React + TypeScript
- **UI 组件库**：Ant Design（中后台生态成熟，适合客服中台主题）
- **路由**：React Router
- **状态管理**：React Query（数据获取） + 简单的 Context/Redux 只管理登录态
- **HTTP 客户端**：Axios（统一设置 BaseURL、拦截器、错误处理）

### 1.2 前端项目结构推荐

```bash
frontend/
  ├── src/
  │   ├── api/                 # 封装所有后端接口
  │   │   ├── auth.ts
  │   │   ├── chat.ts
  │   │   ├── ticket.ts
  │   │   └── notify.ts
  │   ├── components/          # 可复用组件
  │   │   ├── Layout/
  │   │   │   ├── AppLayout.tsx
  │   │   │   └── SideMenu.tsx
  │   │   ├── Chat/
  │   │   │   ├── ChatWindow.tsx
  │   │   │   ├── SessionList.tsx
  │   │   │   └── MessageInput.tsx
  │   │   └── Ticket/
  │   │       ├── TicketTable.tsx
  │   │       └── TicketDetailModal.tsx
  │   ├── pages/               # 路由页面
  │   │   ├── Login.tsx
  │   │   ├── ChatPage.tsx
  │   │   ├── TicketListPage.tsx
  │   │   ├── TicketDetailPage.tsx (可选，或用 Modal)
  │   │   └── NotifyCenterPage.tsx
  │   ├── hooks/
  │   │   └── useAuth.ts
  │   ├── router/
  │   │   └── index.tsx
  │   ├── utils/
  │   │   ├── axios.ts         # axios 实例 & 拦截器
  │   │   └── storage.ts       # token 本地存取
  │   ├── styles/
  │   │   └── theme.less       # 主题色、布局
  │   ├── main.tsx
  │   └── App.tsx
  └── index.html
```

---

## 二、接口梳理 & 前端能力映射

### 2.1 鉴权 / 多租户

后端接口（来自 `AuthController`）：

- `POST /api/auth/registerTenant`：租户 + 管理员注册
- `POST /api/auth/registerUser`：租户内新增用户
- `POST /api/auth/login`：登录，返回 `LoginResponse`（包含 JWT）

JWT 内包含：`userId、tenantId、role、username`，前端只需：

- 登录成功后，把 `token` 存到 `localStorage/sessionStorage`
- 后续请求，在 Header 中统一带上：`Authorization: Bearer ${token}`

### 2.2 Chat 模块

接口（来自 `ChatController`）：

- `POST /api/chat/ask`
  - Request：`{ sessionId?: number, question: string }`
  - Response：`ChatMessage`（包含 id, tenantId, sessionId, senderType, content, emotion, createTime）
- `GET /api/chat/messages/{sessionId}`：获取会话历史
- `GET /api/chat/sessions/all`：当前租户所有会话（仅 ADMIN）
- `GET /api/chat/sessions/user/{userId}`：某个用户的所有会话（仅 ADMIN）

### 2.3 Ticket 模块

接口（来自 `TicketController`）：

- `GET /api/ticket/list?status=&page=&size=`：分页工单列表
- `POST /api/ticket/assign`：分配工单（ADMIN）
- `POST /api/ticket/transition`：状态流转
- `POST /api/ticket/comment`：添加备注
- `GET /api/ticket/comment/list?ticketId=`：备注列表
- `GET /api/ticket/stats`：统计数据（按状态/优先级）

### 2.4 通知模块

接口（来自 `NotifyController`）：

- `GET /api/notify/list`：分页通知列表（支持只查未读）
- `POST /api/notify/read`：标记已读（可全量）

---

## 三、主题风格设计（客服中台）

### 3.1 视觉风格

- **整体风格**：深浅结合的中后台风格
  - 主色：蓝色（如 `#1677ff`，AntD 默认主色）或偏青色（客服科技感）
  - 背景：浅灰 `#f5f5f5`
- **布局**：
  - 左侧：深色侧边栏（LOGO + 菜单）
  - 顶部：白色顶部栏（租户名、当前用户、通知图标）
  - 内容：卡片式模块（Chat / Ticket / Notify）

### 3.2 布局结构

`AppLayout` 负责：

- 左侧菜单：`Chat 对话 / 工单 / 通知 / 管理`
- 顶部：
  - 左：系统名称「YuLu 智链客服中台」
  - 中：当前租户名（可从登陆返回或 JWT 中提取展示）
  - 右：通知铃铛 + 用户头像/用户名 + 退出

---

## 四、关键页面设计与接口对接

### 4.1 登录页 `Login.tsx`

**功能**：

- 输入：`tenantCode`、`username`、`password`
- 调用：`POST /api/auth/login`
- 成功：
  - 保存 `token` 到 `localStorage`
  - 跳转到 `/chat`

**UI**：

- 居中卡片，左侧简短描述「多租户智能客服中台」，右侧表单
- 使用 AntD 的 `Form / Input / Button / Alert`

**核心逻辑伪代码**：

```ts
const onFinish = async (values) => {
  const res = await authApi.login(values);
  storage.setToken(res.data.token);
  navigate('/chat');
};
```

### 4.2 Chat 对话页 `ChatPage.tsx`

**布局**：

- 左侧：会话列表（`SessionList`）
  - 显示「默认会话」、「历史会话标题」
- 右侧（上）：对话窗口（`ChatWindow`）
  - 消息按时间线排列，左侧用户，右侧 AI
  - 显示 `emotion` 标签（例如负向情绪用红色小标记）
- 右侧（下）：输入框（`MessageInput`）
  - 多行输入 + 发送按钮

**数据交互**：

- 初始化：
  - 如果没有选定 `sessionId`，可以先不加载记录
- 选择会话：
  - 调 `GET /api/chat/messages/{sessionId}` 更新消息列表
- 发送消息：
  - 调 `POST /api/chat/ask`
  - 后端支持 `sessionId` 为空自动创建会话 → 前端收到返回的 `ChatMessage` 中带有 `sessionId`
  - 把用户消息和 AI 回复都 append 到当前窗口（用户消息可以在发请求前本地先显示）

**接口封装示例（`api/chat.ts`）**：

```ts
export const chatApi = {
  ask(payload: { sessionId?: number; question: string }) {
    return axios.post<ApiResponse<ChatMessage>>('/api/chat/ask', payload);
  },
  messages(sessionId: number) {
    return axios.get<ApiResponse<ChatMessage[]>>(`/api/chat/messages/${sessionId}`);
  },
  allSessions() {
    return axios.get<ApiResponse<ChatSession[]>>('/api/chat/sessions/all');
  },
};
```

### 4.3 工单列表页 `TicketListPage.tsx`

**布局**：

- 顶部：筛选项
  - 状态：下拉（PENDING / PROCESSING / DONE / CLOSED）
  - 关键字搜索（可选，后端暂未提供可前端过滤）
- 中部：工单列表（`TicketTable`）
  - 列：ID、标题、优先级、状态、创建时间、处理人、操作
- 右侧/弹窗：工单详情 + 备注列表

**数据交互**：

- 加载列表：`GET /api/ticket/list?status=&page=&size=`
- 查看详情：
  - 调用 `GET /api/ticket/comment/list?ticketId=`
- 添加备注：
  - `POST /api/ticket/comment`
- 状态流转：
  - `POST /api/ticket/transition`
- 分配工单（仅管理员）：
  - `POST /api/ticket/assign`

### 4.4 通知中心页 `NotifyCenterPage.tsx`

**布局**：

- 卡片列表：每条通知显示
  - 标题、内容、创建时间、是否已读
  - 未读用蓝点/高亮背景标记
- 顶部按钮：
  - 「仅查看未读」开关
  - 「全部标记已读」

**接口**：

- `GET /api/notify/list`（带分页参数）
- `POST /api/notify/read`

**Header 小红点**：

- 在全局 Layout 顶部放一个铃铛图标
- 初始加载时请求 `onlyUnread=true` 的数量（可以前端统计）
- 点进通知中心后，可清除红点

---

## 五、Axios 封装与登录态管理

### 5.1 Axios 实例

`src/utils/axios.ts`：

```ts
import axios from 'axios';
import { getToken, clearToken } from './storage';

const instance = axios.create({
  baseURL: 'http://localhost:8080', // 可放配置文件
  timeout: 15000,
});

instance.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

instance.interceptors.response.use(
  (res) => res.data,
  (error) => {
    if (error.response?.status === 401) {
      clearToken();
      // 重定向到登录页
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default instance;
```

### 5.2 登录态存储

`src/utils/storage.ts`：

```ts
const TOKEN_KEY = 'yulu_token';

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const setToken = (token: string) => localStorage.setItem(TOKEN_KEY, token);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);
```

---

## 六、路由与权限控制

### 6.1 基本路由配置

`/login`：登录页（不需要登录态）  
`/chat`：聊天页（需要登录）  
`/tickets`：工单列表页  
`/notify`：通知中心  
`/admin/...`：后续租户/用户管理页面

### 6.2 前端路由守卫思路

- 使用一个 `RequireAuth` 组件包装需要登录的路由：
  - 检查 `getToken()` 是否存在
  - 不存在则重定向 `/login`
- 对于需要管理员权限的页面（如工单分配、查看所有会话）：
  - 登陆后解析 JWT 或从登录返回中拿 `role`
  - 没有 ADMIN 权限则隐藏相关菜单/按钮

---

## 七、如何一步步落地（建议执行顺序）

1. **创建前端项目**：使用 Vite + React + TS + AntD 初始化工程。
2. **实现登录页**：先打通 `/api/auth/login`，拿到 token，并能正常跳转。
3. **搭建基础布局 `AppLayout`**：侧边栏 + 顶部栏 + 内容区。
4. **实现 Chat 页面**：对接 `/api/chat/ask` 和 `/api/chat/messages/{sessionId}`，先不管会话列表的复杂功能，至少能「输入一句话 → 后端返回答复 → 展示」。
5. **实现工单列表基础版**：能分页展示工单，点击行展开详情弹窗 + 备注列表。
6. **实现通知中心**：展示通知列表 + 标记已读逻辑。
7. **优化主题与细节**：根据实际需求微调颜色、图标、交互。

---

## 八、你可以让我继续帮你做什么

如果你愿意，我可以在 **不直接改你仓库代码** 的前提下，继续在 `docs/` 下为你生成：

- `frontend-API-typing.md`：基于你 Java DTO 的 TypeScript 类型定义（前后端强类型对齐）
- `frontend-chat-page-sample.md`：一个完整的 `ChatPage.tsx` 示例（含 React Query/AntD 代码）
- `frontend-auth-flow.md`：登录态 + 权限控制的详细流程图与示例代码

你只需要告诉我：  
> “先帮我出 **Chat 页完整示例** / 先出 **类型定义** / 先出 **权限流程**”  

我就会在对应的 md 里给你可以直接对照搬进前端项目的代码和说明。



















