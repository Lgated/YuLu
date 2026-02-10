# 转人工+工单系统+WebSocket 完整业务方案

> **目标**：设计一个**转人工功能与工单系统深度融合**的业务流，使用 **WebSocket** 实现实时双向通信，支持智能分配、排队管理、状态同步、上下文传递等完整闭环。

---

## 📋 目录

1. [业务流程图](#业务流程图)
2. [核心创新点](#核心创新点)
3. [数据库设计](#数据库设计)
4. [WebSocket 架构设计](#websocket-架构设计)
5. [API 接口设计](#api-接口设计)
6. [核心业务逻辑](#核心业务逻辑)
7. [技术实现细节](#技术实现细节)
8. [前端实现要点](#前端实现要点)
9. [实施步骤](#实施步骤)

---

## 🎯 业务流程图

### 整体业务流

```
客户AI对话
    ↓
[点击"转人工"] → 填写转人工原因（可选）
    ↓
系统创建转人工请求（handoff_request）
    ↓
系统检查/创建工单（ticket）→ 关联会话ID
    ↓
转人工请求进入Redis排队队列
    ↓
智能分配器选择客服（基于在线状态、负载、技能、优先级）
    ↓
WebSocket推送通知给客服
    ↓
客服接受/拒绝
    ├─ 拒绝 → 重新分配或退回队列
    └─ 接受 → 建立WebSocket连接
         ↓
    客户 ↔ 客服实时对话（WebSocket双向通信）
         ↓
    对话过程中：
    - 客服可操作工单（开始处理/完成/关闭）
    - 工单状态实时同步
    - 消息持久化到chat_message
         ↓
    对话结束 → 工单状态同步更新
         ↓
    记录完整转人工和对话过程（审计）
```

### 状态流转图

```
转人工请求状态：
PENDING（排队中）
  ↓
ASSIGNED（已分配，等待客服接受）
  ↓
ACCEPTED（客服已接受，建立连接）
  ↓
IN_PROGRESS（对话进行中）
  ↓
COMPLETED（对话完成）
  ↓
CLOSED（已关闭）

工单状态联动：
PENDING（待处理）← 转人工时自动创建
  ↓
PROCESSING（处理中）← 客服接受转人工时
  ↓
DONE（已完成）← 客服标记完成
  ↓
CLOSED（已关闭）← 对话结束或客服关闭
```

---

## 💡 核心创新点

### 1. **转人工与工单自动关联**
- 转人工时**自动检查/创建工单**，工单与会话强绑定
- 工单标题自动生成：`"转人工-会话#${sessionId}"`
- 工单描述包含：转人工原因 + AI对话摘要

### 2. **智能分配算法**
- **多维度评分**：
  - 在线状态（ONLINE > AWAY > OFFLINE）
  - 当前负载（current_sessions / max_sessions）
  - 技能标签匹配度（skillTags）
  - 历史服务质量（平均响应时间、满意度）
  - 优先级匹配（URGENT优先分配给高级客服）
- **负载均衡**：优先分配给负载较低的客服
- **自动接入**：如果客服配置了 `autoAccept=1`，自动接受

### 3. **WebSocket 实时双向通信**
- **客户端**：接收客服消息、排队状态更新、工单状态变化
- **客服端**：接收转人工请求、客户消息、工单操作结果
- **消息类型**：
  - `TEXT`：普通文本消息
  - `IMAGE`：图片消息（未来扩展）
  - `SYSTEM`：系统通知（如"客服已接入"、"工单状态已更新"）
  - `TYPING`：正在输入提示
  - `READ`：已读回执（未来扩展）

### 4. **上下文传递**
- AI对话历史自动传递给客服（最近N条消息）
- 会话摘要（如果有）一并传递
- 客户情绪标签（emotion）传递给客服

### 5. **排队可视化**
- 客户实时看到：
  - 当前排队位置
  - 预计等待时间（基于历史数据计算）
  - 在线客服数量
- WebSocket推送排队状态更新

### 6. **工单与会话联动**
- 客服在对话中可直接操作工单（无需跳转）
- 工单状态变化实时同步到对话界面
- 对话结束自动更新工单状态

---

## 🗄️ 数据库设计

### 1. 转人工请求表（handoff_request）

```sql
-- ============================================
-- 转人工请求表
-- ============================================
DROP TABLE IF EXISTS `handoff_request`;
CREATE TABLE `handoff_request` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID',
  `session_id` BIGINT(20) NOT NULL COMMENT '会话ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '客户ID',
  `ticket_id` BIGINT(20) DEFAULT NULL COMMENT '关联工单ID（转人工时自动创建/关联）',
  `agent_id` BIGINT(20) DEFAULT NULL COMMENT '分配的客服ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-排队中，ASSIGNED-已分配，ACCEPTED-已接受，IN_PROGRESS-进行中，COMPLETED-已完成，CLOSED-已关闭，REJECTED-已拒绝',
  `priority` VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级：LOW-低，MEDIUM-中，HIGH-高，URGENT-紧急',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '转人工原因（客户填写）',
  `queue_position` INT(11) DEFAULT NULL COMMENT '排队位置',
  `assigned_at` DATETIME DEFAULT NULL COMMENT '分配时间',
  `accepted_at` DATETIME DEFAULT NULL COMMENT '客服接受时间',
  `started_at` DATETIME DEFAULT NULL COMMENT '对话开始时间',
  `completed_at` DATETIME DEFAULT NULL COMMENT '对话完成时间',
  `closed_at` DATETIME DEFAULT NULL COMMENT '关闭时间',
  `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '拒绝原因（如果客服拒绝）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_ticket_id` (`ticket_id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_status` (`status`),
  KEY `idx_tenant_status` (`tenant_id`, `status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='转人工请求表';
```

### 2. 转人工事件记录表（handoff_event，可选，用于审计）

```sql
-- ============================================
-- 转人工事件记录表（审计用）
-- ============================================
DROP TABLE IF EXISTS `handoff_event`;
CREATE TABLE `handoff_event` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT(20) NOT NULL COMMENT '租户ID',
  `handoff_request_id` BIGINT(20) NOT NULL COMMENT '转人工请求ID',
  `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型：CREATED-创建，ASSIGNED-分配，ACCEPTED-接受，REJECTED-拒绝，STARTED-开始，COMPLETED-完成，CLOSED-关闭',
  `event_data` TEXT COMMENT '事件数据（JSON格式，存储详细信息）',
  `operator_id` BIGINT(20) DEFAULT NULL COMMENT '操作人ID（客户或客服）',
  `operator_type` VARCHAR(20) DEFAULT NULL COMMENT '操作人类型：USER-客户，AGENT-客服，SYSTEM-系统',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_handoff_request_id` (`handoff_request_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='转人工事件记录表';
```

### 3. 扩展 chat_session 表（添加转人工相关字段）

```sql
-- 为 chat_session 表添加转人工相关字段
ALTER TABLE `chat_session` 
ADD COLUMN `handoff_request_id` BIGINT(20) DEFAULT NULL COMMENT '转人工请求ID' AFTER `status`,
ADD COLUMN `agent_id` BIGINT(20) DEFAULT NULL COMMENT '当前接入的客服ID' AFTER `handoff_request_id`,
ADD COLUMN `chat_mode` VARCHAR(20) NOT NULL DEFAULT 'AI' COMMENT '对话模式：AI-AI对话，AGENT-人工客服对话' AFTER `agent_id`,
ADD KEY `idx_handoff_request_id` (`handoff_request_id`),
ADD KEY `idx_agent_id` (`agent_id`),
ADD KEY `idx_chat_mode` (`chat_mode`);
```

**说明**：
- `chat_mode`：用于区分AI对话和人工对话
- `agent_id`：当前接入的客服ID（如果有）
- `handoff_request_id`：关联转人工请求

---

## 🔌 WebSocket 架构设计

### 1. WebSocket 连接管理

#### 连接路径设计

```
客户端连接：
ws://your-domain/api/ws/customer?token={JWT_TOKEN}

客服端连接：
ws://your-domain/api/ws/agent?token={JWT_TOKEN}
```

#### 连接标识

- **客户连接**：`customer:{tenantId}:{userId}:{sessionId}`
- **客服连接**：`agent:{tenantId}:{agentId}`

### 2. WebSocket 消息协议

#### 消息格式（JSON）

```json
{
  "type": "MESSAGE_TYPE",
  "payload": {},
  "timestamp": "2026-01-27T12:00:00",
  "requestId": "uuid" // 可选，用于请求-响应匹配
}
```

#### 消息类型（type）

**客户 ↔ 客服消息**：
- `TEXT`：文本消息
- `IMAGE`：图片消息（未来）
- `TYPING`：正在输入
- `READ`：已读回执（未来）

**系统通知**：
- `HANDOFF_REQUEST`：转人工请求（推送给客服）
- `HANDOFF_ACCEPTED`：客服已接受（推送给客户）
- `HANDOFF_REJECTED`：客服已拒绝（推送给客户）
- `QUEUE_UPDATE`：排队状态更新（推送给客户）
- `TICKET_STATUS_CHANGED`：工单状态变化（推送给双方）
- `AGENT_JOINED`：客服已接入（推送给客户）
- `AGENT_LEFT`：客服离开（推送给客户）

**控制消息**：
- `PING`：心跳
- `PONG`：心跳响应
- `ERROR`：错误消息

### 3. WebSocket 消息示例

#### 客户发送文本消息

```json
{
  "type": "TEXT",
  "payload": {
    "sessionId": 123,
    "content": "你好，我需要帮助"
  },
  "timestamp": "2026-01-27T12:00:00"
}
```

#### 客服发送文本消息

```json
{
  "type": "TEXT",
  "payload": {
    "sessionId": 123,
    "content": "您好，我是客服小王，有什么可以帮您？"
  },
  "timestamp": "2026-01-27T12:00:01"
}
```

#### 转人工请求通知（推送给客服）

```json
{
  "type": "HANDOFF_REQUEST",
  "payload": {
    "handoffRequestId": 456,
    "sessionId": 123,
    "userId": 789,
    "userName": "客户张三",
    "ticketId": 101,
    "ticketTitle": "转人工-会话#123",
    "priority": "HIGH",
    "reason": "AI无法解决我的问题",
    "queuePosition": 1,
    "estimatedWaitTime": 30
  },
  "timestamp": "2026-01-27T12:00:00"
}
```

#### 排队状态更新（推送给客户）

```json
{
  "type": "QUEUE_UPDATE",
  "payload": {
    "handoffRequestId": 456,
    "queuePosition": 2,
    "estimatedWaitTime": 60,
    "onlineAgentsCount": 5
  },
  "timestamp": "2026-01-27T12:00:05"
}
```

#### 工单状态变化（推送给双方）

```json
{
  "type": "TICKET_STATUS_CHANGED",
  "payload": {
    "ticketId": 101,
    "oldStatus": "PENDING",
    "newStatus": "PROCESSING",
    "operatorId": 999,
    "operatorType": "AGENT"
  },
  "timestamp": "2026-01-27T12:00:10"
}
```

---

## 📡 API 接口设计

### 1. 转人工相关接口

#### 1.1 申请转人工

```
POST /api/customer/chat/transfer
Content-Type: application/json

Request:
{
  "sessionId": 123,
  "reason": "AI无法解决我的问题" // 可选
}

Response:
{
  "success": true,
  "code": "200",
  "message": "转人工申请已提交，正在为您分配客服...",
  "data": {
    "handoffRequestId": 456,
    "ticketId": 101,
    "queuePosition": 3,
    "estimatedWaitTime": 90
  }
}
```

#### 1.2 查询转人工状态

```
GET /api/customer/chat/transfer/status?handoffRequestId=456

Response:
{
  "success": true,
  "code": "200",
  "data": {
    "handoffRequestId": 456,
    "status": "ASSIGNED",
    "queuePosition": 1,
    "estimatedWaitTime": 30,
    "assignedAgentId": 999,
    "assignedAgentName": "客服小王"
  }
}
```

#### 1.3 取消转人工

```
POST /api/customer/chat/transfer/cancel
Content-Type: application/json

Request:
{
  "handoffRequestId": 456
}

Response:
{
  "success": true,
  "code": "200",
  "message": "已取消转人工申请"
}
```

### 2. 客服端接口

#### 2.1 接受转人工请求

```
POST /api/agent/handoff/accept
Content-Type: application/json

Request:
{
  "handoffRequestId": 456
}

Response:
{
  "success": true,
  "code": "200",
  "message": "已接受转人工请求",
  "data": {
    "handoffRequestId": 456,
    "sessionId": 123,
    "userId": 789,
    "ticketId": 101
  }
}
```

#### 2.2 拒绝转人工请求

```
POST /api/agent/handoff/reject
Content-Type: application/json

Request:
{
  "handoffRequestId": 456,
  "reason": "当前忙碌，无法接入" // 可选
}

Response:
{
  "success": true,
  "code": "200",
  "message": "已拒绝转人工请求"
}
```

#### 2.3 获取待处理的转人工请求列表

```
GET /api/agent/handoff/pending

Response:
{
  "success": true,
  "code": "200",
  "data": [
    {
      "handoffRequestId": 456,
      "sessionId": 123,
      "userId": 789,
      "userName": "客户张三",
      "ticketId": 101,
      "ticketTitle": "转人工-会话#123",
      "priority": "HIGH",
      "reason": "AI无法解决我的问题",
      "queuePosition": 1,
      "createdAt": "2026-01-27T12:00:00"
    }
  ]
}
```

#### 2.4 结束转人工对话

```
POST /api/agent/handoff/complete
Content-Type: application/json

Request:
{
  "handoffRequestId": 456,
  "summary": "问题已解决，客户满意" // 可选，对话总结
}

Response:
{
  "success": true,
  "code": "200",
  "message": "对话已结束"
}
```

### 3. WebSocket 连接接口

#### 3.1 建立WebSocket连接

```
客户端：
ws://your-domain/api/ws/customer?token={JWT_TOKEN}&sessionId={sessionId}

客服端：
ws://your-domain/api/ws/agent?token={JWT_TOKEN}
```

---

## 🧠 核心业务逻辑

### 1. 转人工申请流程

```java
/**
 * 转人工申请核心逻辑
 */
public class HandoffService {
    
    @Transactional
    public HandoffRequestResponse transferToAgent(Long tenantId, Long userId, Long sessionId, String reason) {
        // 1. 检查会话是否存在且属于该用户
        ChatSession session = validateSession(tenantId, userId, sessionId);
        
        // 2. 检查是否已有转人工请求（避免重复申请）
        HandoffRequest existing = checkExistingRequest(sessionId);
        if (existing != null && !isCompleted(existing)) {
            throw new BizException("已有转人工请求，请勿重复申请");
        }
        
        // 3. 检查/创建工单
        Ticket ticket = findOrCreateTicket(tenantId, userId, sessionId, reason);
        
        // 4. 创建转人工请求
        HandoffRequest request = new HandoffRequest();
        request.setTenantId(tenantId);
        request.setSessionId(sessionId);
        request.setUserId(userId);
        request.setTicketId(ticket.getId());
        request.setStatus(HandoffStatus.PENDING);
        request.setReason(reason);
        request.setPriority(calculatePriority(session)); // 基于会话情绪、时长等计算优先级
        handoffRequestMapper.insert(request);
        
        // 5. 更新会话状态
        session.setChatMode("AGENT");
        session.setHandoffRequestId(request.getId());
        chatSessionMapper.updateById(session);
        
        // 6. 进入排队队列（Redis）
        int queuePosition = addToQueue(tenantId, request.getId());
        request.setQueuePosition(queuePosition);
        handoffRequestMapper.updateById(request);
        
        // 7. 记录事件
        recordEvent(request.getId(), HandoffEventType.CREATED, userId, "USER");
        
        // 8. 触发智能分配（异步）
        asyncAssignAgent(tenantId, request.getId());
        
        // 9. 返回结果
        return HandoffRequestResponse.builder()
            .handoffRequestId(request.getId())
            .ticketId(ticket.getId())
            .queuePosition(queuePosition)
            .estimatedWaitTime(calculateEstimatedWaitTime(tenantId, queuePosition))
            .build();
    }
}
```

### 2. 智能分配算法

```java
/**
 * 智能分配客服
 */
public class AgentAssigner {
    
    public Long assignAgent(Long tenantId, Long handoffRequestId) {
        HandoffRequest request = handoffRequestMapper.selectById(handoffRequestId);
        
        // 1. 获取在线客服列表
        List<Long> onlineAgentIds = agentStatusService.getOnlineAgents(tenantId);
        if (onlineAgentIds.isEmpty()) {
            // 没有在线客服，保持排队状态
            return null;
        }
        
        // 2. 获取客服详细信息（包括配置、历史数据）
        List<AgentCandidate> candidates = buildCandidates(tenantId, onlineAgentIds);
        
        // 3. 多维度评分
        for (AgentCandidate candidate : candidates) {
            double score = calculateScore(candidate, request);
            candidate.setScore(score);
        }
        
        // 4. 排序并选择最优客服
        candidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        AgentCandidate best = candidates.get(0);
        
        // 5. 检查是否可接入
        if (!agentStatusService.canAcceptSession(tenantId, best.getAgentId())) {
            // 负载已满，保持排队
            return null;
        }
        
        // 6. 分配
        request.setAgentId(best.getAgentId());
        request.setStatus(HandoffStatus.ASSIGNED);
        request.setAssignedAt(LocalDateTime.now());
        handoffRequestMapper.updateById(request);
        
        // 7. 记录事件
        recordEvent(handoffRequestId, HandoffEventType.ASSIGNED, best.getAgentId(), "SYSTEM");
        
        // 8. WebSocket推送通知给客服
        websocketService.sendToAgent(best.getAgentId(), buildHandoffRequestMessage(request));
        
        return best.getAgentId();
    }
    
    private double calculateScore(AgentCandidate candidate, HandoffRequest request) {
        double score = 0.0;
        
        // 在线状态权重：40%
        if ("ONLINE".equals(candidate.getStatus())) {
            score += 40;
        } else if ("AWAY".equals(candidate.getStatus())) {
            score += 20;
        }
        
        // 负载权重：30%（负载越低分数越高）
        double loadRatio = candidate.getCurrentSessions() / (double) candidate.getMaxSessions();
        score += 30 * (1 - loadRatio);
        
        // 技能匹配权重：20%
        if (matchesSkills(candidate, request)) {
            score += 20;
        }
        
        // 历史服务质量权重：10%（平均响应时间、满意度）
        score += 10 * candidate.getQualityScore();
        
        // 优先级加成（URGENT优先分配给高级客服）
        if ("URGENT".equals(request.getPriority()) && candidate.isSeniorAgent()) {
            score += 10;
        }
        
        return score;
    }
}
```

### 3. 客服接受转人工

```java
/**
 * 客服接受转人工请求
 */
@Transactional
public HandoffAcceptResponse acceptHandoff(Long tenantId, Long agentId, Long handoffRequestId) {
    // 1. 验证请求
    HandoffRequest request = validateHandoffRequest(tenantId, agentId, handoffRequestId);
    
    // 2. 检查是否可接入
    if (!agentStatusService.canAcceptSession(tenantId, agentId)) {
        throw new BizException("当前负载已满，无法接入");
    }
    
    // 3. 更新请求状态
    request.setStatus(HandoffStatus.ACCEPTED);
    request.setAcceptedAt(LocalDateTime.now());
    handoffRequestMapper.updateById(request);
    
    // 4. 更新会话
    ChatSession session = chatSessionMapper.selectById(request.getSessionId());
    session.setAgentId(agentId);
    session.setChatMode("AGENT");
    chatSessionMapper.updateById(session);
    
    // 5. 更新工单状态
    Ticket ticket = ticketMapper.selectById(request.getTicketId());
    ticket.setStatus("PROCESSING");
    ticket.setAssignee(agentId);
    ticketMapper.updateById(ticket);
    
    // 6. 增加客服会话数
    agentStatusService.incrementSessionCount(tenantId, agentId);
    
    // 7. 从排队队列移除
    removeFromQueue(tenantId, handoffRequestId);
    
    // 8. 记录事件
    recordEvent(handoffRequestId, HandoffEventType.ACCEPTED, agentId, "AGENT");
    
    // 9. WebSocket通知客户
    websocketService.sendToCustomer(request.getUserId(), request.getSessionId(), 
        buildHandoffAcceptedMessage(request, agentId));
    
    // 10. 发送AI对话历史给客服（上下文传递）
    List<ChatMessage> aiHistory = getAIMessageHistory(request.getSessionId());
    websocketService.sendToAgent(agentId, buildContextMessage(request.getSessionId(), aiHistory));
    
    return HandoffAcceptResponse.builder()
        .handoffRequestId(handoffRequestId)
        .sessionId(request.getSessionId())
        .userId(request.getUserId())
        .ticketId(request.getTicketId())
        .build();
}
```

### 4. WebSocket 消息处理

```java
/**
 * WebSocket消息处理
 */
@Component
public class WebSocketMessageHandler {
    
    /**
     * 处理客户发送的消息
     */
    public void handleCustomerMessage(WebSocketSession session, TextMessage message) {
        // 1. 解析消息
        WebSocketMessage wsMsg = parseMessage(message.getPayload());
        
        // 2. 验证会话和权限
        Long sessionId = wsMsg.getPayload().getSessionId();
        ChatSession chatSession = validateSession(sessionId);
        
        // 3. 保存消息到数据库
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setTenantId(chatSession.getTenantId());
        chatMessage.setSessionId(sessionId);
        chatMessage.setSenderType("USER");
        chatMessage.setContent(wsMsg.getPayload().getContent());
        chatMessageMapper.insert(chatMessage);
        
        // 4. 转发给客服（如果已接入）
        if (chatSession.getAgentId() != null) {
            websocketService.sendToAgent(chatSession.getAgentId(), wsMsg);
        }
    }
    
    /**
     * 处理客服发送的消息
     */
    public void handleAgentMessage(WebSocketSession session, TextMessage message) {
        // 1. 解析消息
        WebSocketMessage wsMsg = parseMessage(message.getPayload());
        
        // 2. 验证会话和权限
        Long sessionId = wsMsg.getPayload().getSessionId();
        ChatSession chatSession = validateSession(sessionId);
        
        // 3. 验证客服权限
        if (!chatSession.getAgentId().equals(getCurrentAgentId(session))) {
            throw new BizException("无权限操作此会话");
        }
        
        // 4. 保存消息到数据库
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setTenantId(chatSession.getTenantId());
        chatMessage.setSessionId(sessionId);
        chatMessage.setSenderType("AGENT");
        chatMessage.setContent(wsMsg.getPayload().getContent());
        chatMessageMapper.insert(chatMessage);
        
        // 5. 转发给客户
        websocketService.sendToCustomer(chatSession.getUserId(), sessionId, wsMsg);
    }
}
```

---

## 🛠️ 技术实现细节

### 1. WebSocket 配置（Spring Boot）

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(customerWebSocketHandler(), "/api/ws/customer")
            .setAllowedOrigins("*") // 生产环境应配置具体域名
            .withSockJS(); // 可选：支持SockJS降级
        
        registry.addHandler(agentWebSocketHandler(), "/api/ws/agent")
            .setAllowedOrigins("*")
            .withSockJS();
    }
    
    @Bean
    public WebSocketHandler customerWebSocketHandler() {
        return new CustomerWebSocketHandler();
    }
    
    @Bean
    public WebSocketHandler agentWebSocketHandler() {
        return new AgentWebSocketHandler();
    }
}
```

### 2. WebSocket Handler 实现

```java
@Component
public class CustomerWebSocketHandler extends TextWebSocketHandler {
    
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 1. 从URL参数获取token和sessionId
        String token = getTokenFromQuery(session.getUri().getQuery());
        Long sessionId = getSessionIdFromQuery(session.getUri().getQuery());
        
        // 2. 验证token
        Long userId = validateToken(token);
        
        // 3. 存储连接
        String connectionKey = "customer:" + userId + ":" + sessionId;
        sessions.put(connectionKey, session);
        
        log.info("[WebSocket] 客户连接建立: userId={}, sessionId={}", userId, sessionId);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 处理客户发送的消息
        messageHandler.handleCustomerMessage(session, message);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 清理连接
        removeSession(session);
        log.info("[WebSocket] 客户连接关闭: {}", session.getId());
    }
    
    public void sendToCustomer(Long userId, Long sessionId, WebSocketMessage message) {
        String connectionKey = "customer:" + userId + ":" + sessionId;
        WebSocketSession session = sessions.get(connectionKey);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(JSON.toJSONString(message)));
            } catch (Exception e) {
                log.error("[WebSocket] 发送消息失败", e);
            }
        }
    }
}
```

### 3. Redis 排队队列实现

```java
@Service
public class HandoffQueueService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String QUEUE_KEY_PREFIX = "handoff:queue:";
    
    /**
     * 加入排队队列
     */
    public int addToQueue(Long tenantId, Long handoffRequestId) {
        String queueKey = QUEUE_KEY_PREFIX + tenantId;
        Long position = redisTemplate.opsForList().rightPush(queueKey, handoffRequestId.toString());
        redisTemplate.expire(queueKey, 1, TimeUnit.HOURS); // 1小时过期
        return position != null ? position.intValue() : 0;
    }
    
    /**
     * 获取排队位置
     */
    public int getQueuePosition(Long tenantId, Long handoffRequestId) {
        String queueKey = QUEUE_KEY_PREFIX + tenantId;
        List<Object> queue = redisTemplate.opsForList().range(queueKey, 0, -1);
        if (queue == null) return 0;
        
        String requestIdStr = handoffRequestId.toString();
        for (int i = 0; i < queue.size(); i++) {
            if (requestIdStr.equals(queue.get(i).toString())) {
                return i + 1;
            }
        }
        return 0;
    }
    
    /**
     * 从队列移除
     */
    public void removeFromQueue(Long tenantId, Long handoffRequestId) {
        String queueKey = QUEUE_KEY_PREFIX + tenantId;
        redisTemplate.opsForList().remove(queueKey, 1, handoffRequestId.toString());
    }
    
    /**
     * 获取队列长度
     */
    public int getQueueLength(Long tenantId) {
        String queueKey = QUEUE_KEY_PREFIX + tenantId;
        Long length = redisTemplate.opsForList().size(queueKey);
        return length != null ? length.intValue() : 0;
    }
}
```

---

## 🎨 前端实现要点

### 1. WebSocket 客户端封装

```typescript
// frontend/src/utils/websocket.ts
export class WebSocketClient {
  private ws: WebSocket | null = null;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private messageHandlers: Map<string, Function[]> = new Map();

  connect(url: string, token: string, sessionId?: number) {
    const wsUrl = `${url}?token=${token}${sessionId ? `&sessionId=${sessionId}` : ''}`;
    this.ws = new WebSocket(wsUrl);

    this.ws.onopen = () => {
      console.log('[WebSocket] 连接已建立');
      this.startHeartbeat();
    };

    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      this.handleMessage(message);
    };

    this.ws.onerror = (error) => {
      console.error('[WebSocket] 连接错误', error);
    };

    this.ws.onclose = () => {
      console.log('[WebSocket] 连接已关闭');
      this.reconnect(url, token, sessionId);
    };
  }

  send(type: string, payload: any) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const message = {
        type,
        payload,
        timestamp: new Date().toISOString()
      };
      this.ws.send(JSON.stringify(message));
    }
  }

  on(type: string, handler: Function) {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, []);
    }
    this.messageHandlers.get(type)!.push(handler);
  }

  private handleMessage(message: any) {
    const handlers = this.messageHandlers.get(message.type) || [];
    handlers.forEach(handler => handler(message.payload));
  }

  private startHeartbeat() {
    setInterval(() => {
      this.send('PING', {});
    }, 30000); // 30秒心跳
  }

  private reconnect(url: string, token: string, sessionId?: number) {
    if (this.reconnectTimer) return;
    
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect(url, token, sessionId);
    }, 3000); // 3秒后重连
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }
}
```

### 2. 客户转人工界面

```typescript
// frontend/src/pages/customer/TransferToAgentModal.tsx
export default function TransferToAgentModal({ visible, onClose, sessionId }) {
  const [reason, setReason] = useState('');
  const [queuePosition, setQueuePosition] = useState(0);
  const [estimatedWaitTime, setEstimatedWaitTime] = useState(0);
  const wsClient = useRef<WebSocketClient | null>(null);

  const handleTransfer = async () => {
    try {
      const res = await chatApi.transferToAgent(sessionId, reason);
      if (res.success) {
        // 建立WebSocket连接
        wsClient.current = new WebSocketClient();
        wsClient.current.connect(WS_URL, getToken(), sessionId);
        
        // 监听排队状态更新
        wsClient.current.on('QUEUE_UPDATE', (payload) => {
          setQueuePosition(payload.queuePosition);
          setEstimatedWaitTime(payload.estimatedWaitTime);
        });
        
        // 监听客服接入
        wsClient.current.on('HANDOFF_ACCEPTED', (payload) => {
          message.success('客服已接入，开始对话');
          onClose();
        });
      }
    } catch (e) {
      message.error('转人工失败');
    }
  };

  return (
    <Modal visible={visible} onCancel={onClose} footer={null}>
      <Form onFinish={handleTransfer}>
        <Form.Item label="转人工原因（可选）">
          <Input.TextArea 
            value={reason} 
            onChange={(e) => setReason(e.target.value)}
            placeholder="请描述您遇到的问题..."
          />
        </Form.Item>
        
        {queuePosition > 0 && (
          <Alert
            message={`您当前排队位置：第 ${queuePosition} 位，预计等待时间：${estimatedWaitTime} 秒`}
            type="info"
          />
        )}
        
        <Form.Item>
          <Button type="primary" htmlType="submit">确认转人工</Button>
        </Form.Item>
      </Form>
    </Modal>
  );
}
```

### 3. 客服转人工通知界面

```typescript
// frontend/src/pages/agent/HandoffNotification.tsx
export default function HandoffNotification() {
  const [pendingRequests, setPendingRequests] = useState([]);
  const wsClient = useRef<WebSocketClient | null>(null);

  useEffect(() => {
    // 建立WebSocket连接
    wsClient.current = new WebSocketClient();
    wsClient.current.connect(WS_AGENT_URL, getToken());
    
    // 监听转人工请求
    wsClient.current.on('HANDOFF_REQUEST', (payload) => {
      setPendingRequests(prev => [...prev, payload]);
      notification.info({
        message: '新的转人工请求',
        description: `客户 ${payload.userName} 申请转人工，优先级：${payload.priority}`,
        duration: 0, // 不自动关闭
      });
    });

    return () => {
      wsClient.current?.disconnect();
    };
  }, []);

  const handleAccept = async (handoffRequestId: number) => {
    try {
      await agentApi.acceptHandoff(handoffRequestId);
      message.success('已接受转人工请求');
      setPendingRequests(prev => prev.filter(r => r.handoffRequestId !== handoffRequestId));
    } catch (e) {
      message.error('接受失败');
    }
  };

  return (
    <Card title="转人工请求">
      <List
        dataSource={pendingRequests}
        renderItem={(item) => (
          <List.Item
            actions={[
              <Button type="primary" onClick={() => handleAccept(item.handoffRequestId)}>
                接受
              </Button>,
              <Button danger onClick={() => handleReject(item.handoffRequestId)}>
                拒绝
              </Button>
            ]}
          >
            <List.Item.Meta
              title={`${item.userName} - ${item.ticketTitle}`}
              description={`优先级：${item.priority} | 原因：${item.reason}`}
            />
          </List.Item>
        )}
      />
    </Card>
  );
}
```

---

## 📝 实施步骤

### 阶段1：基础准备（1-2天）

1. **数据库表创建**
   - 执行 `handoff_request` 表SQL
   - 执行 `handoff_event` 表SQL（可选）
   - 扩展 `chat_session` 表

2. **实体类和Mapper**
   - 创建 `HandoffRequest` 实体
   - 创建 `HandoffRequestMapper`
   - 创建 `HandoffEvent` 实体（可选）

### 阶段2：WebSocket 基础设施（2-3天）

1. **WebSocket 配置**
   - 添加 Spring WebSocket 依赖
   - 配置 WebSocket Handler
   - 实现连接管理

2. **WebSocket 消息处理**
   - 实现客户/客服消息处理
   - 实现心跳机制
   - 实现断线重连

### 阶段3：转人工核心逻辑（3-4天）

1. **转人工申请**
   - 实现 `HandoffService.transferToAgent()`
   - 实现工单自动创建/关联
   - 实现排队队列（Redis）

2. **智能分配**
   - 实现 `AgentAssigner`
   - 实现多维度评分算法
   - 实现自动分配逻辑

3. **客服接受/拒绝**
   - 实现接受逻辑
   - 实现拒绝逻辑
   - 实现状态同步

### 阶段4：WebSocket 实时通信（2-3天）

1. **消息推送**
   - 实现客户消息推送
   - 实现客服消息推送
   - 实现系统通知推送

2. **状态同步**
   - 实现排队状态更新
   - 实现工单状态同步
   - 实现连接状态管理

### 阶段5：前端实现（3-4天）

1. **WebSocket 客户端**
   - 封装 WebSocket 客户端
   - 实现消息处理
   - 实现断线重连

2. **转人工界面**
   - 客户转人工弹窗
   - 排队状态显示
   - 客服通知界面

3. **对话界面增强**
   - 集成 WebSocket 消息
   - 显示客服/客户消息
   - 显示系统通知

### 阶段6：测试和优化（2-3天）

1. **功能测试**
   - 转人工流程测试
   - WebSocket 连接测试
   - 状态同步测试

2. **性能优化**
   - WebSocket 连接池优化
   - Redis 队列优化
   - 分配算法优化

3. **异常处理**
   - 断线重连处理
   - 消息丢失处理
   - 并发冲突处理

---

## ✅ 验收清单

- [ ] 客户可以申请转人工，填写原因（可选）
- [ ] 转人工时自动创建/关联工单
- [ ] 转人工请求进入排队队列
- [ ] 智能分配算法正确选择客服
- [ ] WebSocket 推送通知给客服
- [ ] 客服可以接受/拒绝转人工请求
- [ ] 客服接受后建立 WebSocket 连接
- [ ] 客户和客服可以实时双向对话
- [ ] 对话消息持久化到数据库
- [ ] 工单状态与会话状态联动
- [ ] 排队状态实时更新
- [ ] 对话结束自动更新工单状态
- [ ] 转人工事件完整记录（审计）

---

## 🎯 总结

本方案设计了一个**转人工功能与工单系统深度融合**的业务流，使用 **WebSocket** 实现实时双向通信。核心特点：

1. **自动化**：转人工时自动创建/关联工单，减少人工操作
2. **智能化**：多维度评分算法，智能分配客服
3. **实时性**：WebSocket 双向通信，消息实时推送
4. **可追溯**：完整记录转人工和对话过程，支持审计
5. **用户体验**：排队可视化、状态同步、上下文传递

该方案可以分阶段实施，每个阶段都有明确的交付物和验收标准，便于项目管理和风险控制。






