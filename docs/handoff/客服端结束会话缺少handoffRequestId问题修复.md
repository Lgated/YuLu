# 客服端结束会话缺少 handoffRequestId 问题修复

## 问题现象

客服端点击"结束会话"按钮时，提示：**"无法结束会话：缺少转人工请求ID"**

## 问题原因

### 根本原因

在 `AgentWorkbenchPage.tsx` 中，当客服从 URL 或 sessionStorage 恢复会话时，创建的占位符会话对象**没有包含 `handoffRequestId`** 字段。

### 详细分析

#### 正常流程（有 handoffRequestId）

1. 客服在"待处理请求"列表中点击"接受"
2. 调用 `handleAccept()` 方法
3. 创建 `AgentSession` 对象，包含 `handoffRequestId`
4. 点击"结束会话"按钮，能正常调用 API

#### 异常流程（缺少 handoffRequestId）

1. 客服刷新页面或从 URL 直接进入会话
2. 触发 `useEffect` 恢复会话
3. 创建占位符 `AgentSession` 对象，**没有 `handoffRequestId`**
4. 点击"结束会话"按钮，提示缺少 ID

### 代码位置

**问题代码（修复前）：**

```typescript
// frontend/src/pages/agent/AgentWorkbenchPage.tsx

useEffect(() => {
  const targetSessionId = ...;
  
  if (targetSessionId && !activeSessions.has(targetSessionId)) {
    // 创建占位符，缺少 handoffRequestId
    const placeholderSession: AgentSession = {
      sessionId: targetSessionId,
      userId: 0,
      userName: `会话 #${targetSessionId}`,
      messages: [],
      unread: 0,
      // ❌ 缺少 handoffRequestId
    };
    setActiveSessions(prev => new Map(prev).set(targetSessionId, placeholderSession));
  }
}, [searchParams, activeSessions]);
```

## 解决方案

### 方案概述

1. **后端**：添加根据 sessionId 查询转人工请求的 API
2. **前端**：在恢复会话时，从后端获取 `handoffRequestId`
3. **优化**：添加调试信息和错误提示

### 修复步骤

#### 步骤1：添加后端 API

**文件：`src/main/java/com/ityfz/yulu/handoff/controller/AgentHandoffController.java`**

添加新的接口方法：

```java
@GetMapping("/by-session/{sessionId}")
@Operation(summary = "根据会话ID获取转人工请求", description = "获取指定会话的转人工请求信息")
public ApiResponse<HandoffRequestItemDTO> getBySessionId(@PathVariable Long sessionId) {
    Long tenantId = SecurityUtil.currentTenantId();
    Long agentId = SecurityUtil.currentUserId();
    
    // 查询该会话的转人工请求
    HandoffRequest request = handoffRequestMapper.selectOne(Wrappers.<HandoffRequest>lambdaQuery()
        .eq(HandoffRequest::getTenantId, tenantId)
        .eq(HandoffRequest::getSessionId, sessionId)
        .eq(HandoffRequest::getAgentId, agentId)
        .in(HandoffRequest::getStatus, 
            HandoffStatus.ACCEPTED.getCode(), 
            HandoffStatus.IN_PROGRESS.getCode())
        .orderByDesc(HandoffRequest::getCreateTime)
        .last("LIMIT 1"));
    
    if (request == null) {
        throw new BizException(ErrorCodes.NOT_FOUND, "未找到该会话的转人工请求");
    }
    
    // 构建返回对象
    HandoffRequestItemDTO dto = HandoffRequestItemDTO.builder()
        .handoffRequestId(request.getId())
        .sessionId(request.getSessionId())
        .userId(request.getUserId())
        .userName("客户#" + request.getUserId())
        .ticketId(request.getTicketId())
        .priority(request.getPriority())
        .reason(request.getReason())
        .createdAt(request.getCreateTime())
        .build();
    
    return ApiResponse.success("查询成功", dto);
}
```

**添加必要的 import：**

```java
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.handoff.entity.HandoffRequest;
import com.ityfz.yulu.handoff.enums.HandoffStatus;
import com.ityfz.yulu.handoff.mapper.HandoffRequestMapper;
```

**添加依赖注入：**

```java
public class AgentHandoffController {
    private final HandoffService handoffService;
    private final HandoffRequestMapper handoffRequestMapper; // 新增
}
```

#### 步骤2：添加前端 API 方法

**文件：`frontend/src/api/handoff.ts`**

```typescript
/**
 * 根据会话ID获取转人工请求信息
 */
getBySessionId(sessionId: number) {
  return http.get<ApiResponse<any>>(`/agent/handoff/by-session/${sessionId}`);
}
```

#### 步骤3：修改前端恢复会话逻辑

**文件：`frontend/src/pages/agent/AgentWorkbenchPage.tsx`**

```typescript
// 5. Restore session from URL query or sessionStorage on mount
useEffect(() => {
  const sessionIdFromUrl = searchParams.get('sessionId');
  const sessionIdFromStorage = sessionStorage.getItem('agent_selected_handoff_session_id');
  const targetSessionId = sessionIdFromUrl ? Number(sessionIdFromUrl) : (sessionIdFromStorage ? Number(sessionIdFromStorage) : null);

  if (targetSessionId && !activeSessions.has(targetSessionId)) {
    // 从后端获取会话的转人工请求信息
    const loadSessionInfo = async () => {
      try {
        const res = await handoffApi.getBySessionId(targetSessionId);
        const handoffInfo = res.data;
        
        // 创建完整的会话对象
        const restoredSession: AgentSession = {
          sessionId: targetSessionId,
          userId: handoffInfo.userId,
          userName: handoffInfo.userName || `客户 #${handoffInfo.userId}`,
          messages: [],
          unread: 0,
          handoffRequestId: handoffInfo.handoffRequestId, // ✅ 关键：保存 handoffRequestId
        };
        
        setActiveSessions(prev => new Map(prev).set(targetSessionId, restoredSession));
        setCurrentSessionId(targetSessionId);
      } catch (error: any) {
        console.error('Failed to load session info:', error);
        message.error('加载会话信息失败');
        
        // 如果加载失败，创建一个占位符（但没有 handoffRequestId）
        const placeholderSession: AgentSession = {
          sessionId: targetSessionId,
          userId: 0,
          userName: `会话 #${targetSessionId}`,
          messages: [],
          unread: 0,
        };
        setActiveSessions(prev => new Map(prev).set(targetSessionId, placeholderSession));
        setCurrentSessionId(targetSessionId);
      }
    };
    
    loadSessionInfo();
  } else if (targetSessionId && activeSessions.has(targetSessionId)) {
    setCurrentSessionId(targetSessionId);
  }
}, [searchParams]); // ✅ 移除 activeSessions 依赖，避免无限循环
```

#### 步骤4：优化错误提示和UI

**文件：`frontend/src/components/AgentChatWindow.tsx`**

```typescript
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
  <div>
    <h3>
      正在与 {session.userName} 对话 (会话ID: {session.sessionId})
      {/* 开发环境显示调试信息 */}
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
);
```

## 测试步骤

### 测试1：正常接受流程

1. 客服登录
2. 用户发起转人工请求
3. 客服在"待处理请求"中点击"接受"
4. 点击"结束会话"按钮
5. **预期**：能正常结束会话

### 测试2：刷新页面后结束会话

1. 客服接受转人工请求
2. 刷新浏览器页面（F5）
3. 会话自动恢复
4. 点击"结束会话"按钮
5. **预期**：能正常结束会话（之前会报错）

### 测试3：从 URL 直接进入会话

1. 客服接受转人工请求
2. 复制浏览器 URL（包含 sessionId 参数）
3. 打开新标签页，粘贴 URL
4. 点击"结束会话"按钮
5. **预期**：能正常结束会话

### 测试4：开发环境调试信息

1. 在开发环境（`npm run dev`）
2. 查看会话标题
3. **预期**：能看到 `[转人工ID: xxx]` 的调试信息

## 验证方法

### 方法1：查看浏览器控制台

打开浏览器开发者工具（F12），查看 Console：

```javascript
// 应该能看到会话对象包含 handoffRequestId
{
  sessionId: 30,
  userId: 2,
  userName: "客户 #2",
  messages: [],
  unread: 0,
  handoffRequestId: 28  // ✅ 应该有这个字段
}
```

### 方法2：查看网络请求

在 Network 标签中，查看 `/api/agent/handoff/by-session/30` 请求：

**请求：**
```
GET /api/agent/handoff/by-session/30
Authorization: Bearer <token>
```

**响应：**
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "handoffRequestId": 28,
    "sessionId": 30,
    "userId": 2,
    "userName": "客户#2",
    "ticketId": 32,
    "priority": "MEDIUM",
    "reason": null,
    "createdAt": "2026-02-09T13:34:49"
  }
}
```

### 方法3：查看后端日志

后端应该有查询日志：

```
SELECT * FROM handoff_request 
WHERE tenant_id = 1 
  AND session_id = 30 
  AND agent_id = 3 
  AND status IN ('ACCEPTED', 'IN_PROGRESS')
ORDER BY create_time DESC 
LIMIT 1
```

## 常见问题

### Q1: 刷新页面后还是提示缺少 ID？

**A**: 检查以下几点：

1. **后端接口是否正常？**
   - 使用 Postman 测试：`GET /api/agent/handoff/by-session/30`
   - 检查返回数据是否包含 `handoffRequestId`

2. **前端是否正确调用？**
   - 打开浏览器控制台，查看 Network 标签
   - 确认有 `/api/agent/handoff/by-session/30` 请求
   - 查看响应数据

3. **会话状态是否正确？**
   - 查询数据库：
     ```sql
     SELECT * FROM handoff_request WHERE session_id = 30;
     ```
   - 确认 `status` 是 `ACCEPTED` 或 `IN_PROGRESS`
   - 确认 `agent_id` 是当前客服的 ID

### Q2: 提示"未找到该会话的转人工请求"？

**A**: 可能的原因：

1. **会话已结束**
   - 检查 `handoff_request` 表的 `status` 字段
   - 如果是 `COMPLETED`，说明会话已结束

2. **客服不匹配**
   - 检查 `handoff_request` 表的 `agent_id` 字段
   - 确认是否是当前登录的客服

3. **租户不匹配**
   - 检查 `handoff_request` 表的 `tenant_id` 字段
   - 确认是否是当前租户

### Q3: 开发环境看不到调试信息？

**A**: 确认以下几点：

1. 使用 `npm run dev` 启动（不是 `npm run build`）
2. `process.env.NODE_ENV` 应该是 `'development'`
3. 刷新页面，清除缓存

## 总结

### 修复内容

1. ✅ 添加后端 API：`GET /api/agent/handoff/by-session/{sessionId}`
2. ✅ 添加前端 API 方法：`handoffApi.getBySessionId()`
3. ✅ 修改前端恢复会话逻辑，从后端获取 `handoffRequestId`
4. ✅ 优化错误提示和 UI，添加调试信息

### 影响范围

- **后端**：新增 1 个 API 接口
- **前端**：修改 2 个文件（AgentWorkbenchPage.tsx, AgentChatWindow.tsx）
- **数据库**：无变更

### 测试覆盖

- ✅ 正常接受流程
- ✅ 刷新页面后结束会话
- ✅ 从 URL 直接进入会话
- ✅ 开发环境调试信息

完成以上修复后，客服端应该能够正常结束会话，无论是直接接受还是刷新页面后恢复的会话。
