# 前端API路径修改说明

## ✅ 已完成的修改

### 1. API路径更新

| 功能模块 | 旧路径 | 新路径 | 说明 |
|---------|--------|--------|------|
| **C端聊天** | `/api/chat/*` | `/api/customer/chat/*` | C端用户使用 |
| **B端工单** | `/api/ticket/*` | `/api/admin/ticket/*` | B端管理员/客服使用 |
| **B端通知** | `/api/notify/*` | `/api/admin/notify/*` | B端管理员/客服使用 |
| **B端登录** | `/api/auth/login` | `/api/admin/auth/login` | B端登录（需要tenantCode） |
| **C端登录** | - | `/api/customer/auth/login` | C端登录（不需要tenantCode） |

### 2. 修改的文件

#### `frontend/src/api/auth.ts`
- ✅ 添加 `adminLogin()` - B端登录
- ✅ 添加 `customerLogin()` - C端登录
- ✅ 保留 `login()` 作为兼容方法（调用adminLogin）

#### `frontend/src/api/chat.ts`
- ✅ 更新为C端路径：`/customer/chat/*`
- ✅ 添加 `transferToAgent()` - 转人工服务
- ✅ 新增 `sessionApi` - B端会话管理API（`/admin/session/*`）

#### `frontend/src/api/ticket.ts`
- ✅ 更新为B端路径：`/admin/ticket/*`
- ✅ 添加更多工单管理方法：`assign()`, `transition()`, `addComment()`, `stats()`

#### `frontend/src/api/notify.ts`
- ✅ 更新为B端路径：`/admin/notify/*`
- ✅ 添加 `markRead()` - 标记通知为已读

#### `frontend/src/api/types.ts`
- ✅ 更新 `ApiResponse` 类型，添加 `success` 字段

#### `frontend/src/pages/ChatPage.tsx`
- ✅ 移除 `allSessions()` 调用（C端不应该看到所有会话）
- ✅ 保持使用C端聊天API

#### `frontend/src/pages/NotifyCenterPage.tsx`
- ✅ 修复响应判断逻辑：使用 `res.success || res.code === '200'`

---

## ⚠️ 注意事项

### 1. NotifyController 后端重构

**当前状态**：`NotifyController` 还在旧路径 `/api/notify`

**需要操作**：
- 后端需要创建 `AdminNotifyController`，路径改为 `/api/admin/notify`
- 或者暂时保持前端路径为 `/api/notify`（如果后端还没重构）

**建议**：如果后端还没重构NotifyController，可以：
1. 暂时保持前端路径为 `/api/notify`
2. 或者后端先重构NotifyController到 `/api/admin/notify`

### 2. 会话列表功能

**当前状态**：`ChatPage` 中移除了 `allSessions()` 调用

**原因**：C端用户不应该看到所有会话，只应该看到自己的会话

**需要操作**：
- 后端需要提供C端接口：`/api/customer/chat/sessions` - 获取当前用户的会话列表
- 或者前端根据用户角色选择调用：
  - C端：`/api/customer/chat/sessions`（自己的会话）
  - B端：`sessionApi.listAllSessions()`（所有会话）

### 3. 用户角色判断

**当前状态**：前端还没有根据用户角色选择不同的API

**需要操作**：
- 前端需要从Token中解析用户角色
- 根据角色选择调用C端或B端API
- 或者后端在登录响应中返回角色信息，前端保存到localStorage

---

## 🔄 后续优化建议

### 1. 创建API路由工具

```typescript
// frontend/src/utils/apiRouter.ts
export const getApiPrefix = (role: string) => {
  if (role === 'USER') {
    return '/customer';
  } else if (role === 'ADMIN' || role === 'AGENT') {
    return '/admin';
  }
  return '';
};
```

### 2. 根据角色动态选择API

```typescript
// 在组件中根据用户角色选择API
const userRole = getUserRole(); // 从localStorage或context获取
const chatApi = userRole === 'USER' ? customerChatApi : adminSessionApi;
```

### 3. 统一错误处理

确保所有API调用都使用统一的响应判断：
```typescript
if (res.success || res.code === '200') {
  // 成功处理
}
```

---

## 📝 测试清单

- [ ] C端登录功能正常（使用 `/api/customer/auth/login`）
- [ ] B端登录功能正常（使用 `/api/admin/auth/login`）
- [ ] C端聊天功能正常（使用 `/api/customer/chat/*`）
- [ ] B端工单列表正常（使用 `/api/admin/ticket/*`）
- [ ] B端通知列表正常（使用 `/api/admin/notify/*`）
- [ ] 权限控制正常（C端无法访问B端接口，返回403）

---

**文档版本**：v1.0  
**创建时间**：2026-01-14




































