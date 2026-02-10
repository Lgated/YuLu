# Redis 数据结构错误修复方案

## 问题现象

1. 用户发起转人工请求后一直排队
2. 客服端收不到转人工通知
3. Redis 中 `agent:status:1:3` 的值是一个时间戳字符串（`1770625360645`），而不是 Hash 结构

## 问题原因

`agent:status:1:3` 被错误地设置为了一个**字符串类型**，而不是**Hash 类型**。

### 正确的数据结构

应该是 Hash 类型：
```redis
HGETALL agent:status:1:3

# 应该返回：
status: ONLINE
last_active_time: 2026-02-09T16:30:00
heartbeat_time: 1770625360645
current_sessions: 0
max_sessions: 5
```

### 错误的数据结构

当前是 String 类型：
```redis
GET agent:status:1:3

# 返回：
"1770625360645"
```

## 解决方案

### 方法1：使用新的调试接口（推荐）

我已经添加了新的调试接口，可以一键修复。

#### 步骤1：检查 Redis 数据结构

```
GET http://localhost:8080/api/admin/debug/check-agent-redis/3
Headers:
  Authorization: Bearer <管理员token>
```

**响应示例：**
```json
{
  "code": 200,
  "message": "检查完成",
  "data": {
    "statusKeyExists": true,
    "statusKeyType": "STRING",  // ❌ 错误！应该是 HASH
    "statusData": "1770625360645",
    "warning": "数据类型错误！应该是 HASH，实际是 STRING",
    "sessionsKeyExists": false,
    "inOnlineSet": false
  }
}
```

#### 步骤2：强制重置客服状态

```
POST http://localhost:8080/api/admin/debug/force-reset-agent/3
Headers:
  Authorization: Bearer <管理员token>
```

**响应：**
```json
{
  "code": 200,
  "message": "客服状态已强制重置，请客服重新登录或刷新页面"
}
```

#### 步骤3：客服设置在线状态

客服需要调用设置在线接口：

```
PUT http://localhost:8080/api/admin/user/online-status?status=ONLINE
Headers:
  Authorization: Bearer <客服token>
```

或者客服直接刷新页面，前端应该会自动调用这个接口。

#### 步骤4：验证修复

再次检查 Redis 数据结构：

```
GET http://localhost:8080/api/admin/debug/check-agent-redis/3
```

**预期响应：**
```json
{
  "code": 200,
  "message": "检查完成",
  "data": {
    "statusKeyExists": true,
    "statusKeyType": "HASH",  // ✅ 正确！
    "statusData": {
      "status": "ONLINE",
      "last_active_time": "2026-02-09T16:30:00",
      "heartbeat_time": 1770625360645,
      "current_sessions": 0,
      "max_sessions": 5
    },
    "sessionsKeyExists": true,
    "sessionsData": 0,
    "inOnlineSet": true,
    "onlineScore": 1770625360645
  }
}
```

### 方法2：使用 Redis 客户端手动修复

#### 步骤1：删除错误的 key

在 Redis 客户端中执行：

```redis
# 删除客服状态
DEL agent:status:1:3

# 删除会话计数
DEL agent:sessions:1:3

# 从在线集合中移除
ZREM agent:online:1 "3"
```

#### 步骤2：客服重新登录

客服刷新页面或重新登录，系统会自动创建正确的 Hash 结构。

#### 步骤3：验证

```redis
# 检查数据类型
TYPE agent:status:1:3
# 应该返回：hash

# 查看所有字段
HGETALL agent:status:1:3
# 应该返回 Hash 结构
```

## 前端修复（确保正确调用设置在线接口）

### 检查前端登录逻辑

前端在客服登录成功后，应该调用设置在线接口：

```typescript
// 登录成功后
const loginResponse = await authApi.login(credentials);

// 如果是客服或管理员，设置在线状态
if (loginResponse.data.role === 'AGENT' || loginResponse.data.role === 'ADMIN') {
  try {
    await axios.put('/api/admin/user/online-status?status=ONLINE');
  } catch (error) {
    console.error('Failed to set online status:', error);
  }
}
```

### 检查前端心跳逻辑

前端应该定期发送心跳：

```typescript
// 每30秒发送一次心跳
useEffect(() => {
  const heartbeatInterval = setInterval(async () => {
    try {
      await axios.post('/api/admin/user/heartbeat');
    } catch (error) {
      console.error('Heartbeat failed:', error);
    }
  }, 30000); // 30秒

  return () => clearInterval(heartbeatInterval);
}, []);
```

## 完整的测试流程

### 测试1：检查当前状态

```bash
# 1. 检查 Redis 数据结构
curl -X GET "http://localhost:8080/api/admin/debug/check-agent-redis/3" \
  -H "Authorization: Bearer <token>"

# 2. 如果 statusKeyType 不是 HASH，执行修复
```

### 测试2：强制重置

```bash
# 1. 强制重置客服状态
curl -X POST "http://localhost:8080/api/admin/debug/force-reset-agent/3" \
  -H "Authorization: Bearer <token>"

# 2. 客服设置在线
curl -X PUT "http://localhost:8080/api/admin/user/online-status?status=ONLINE" \
  -H "Authorization: Bearer <客服token>"

# 3. 再次检查
curl -X GET "http://localhost:8080/api/admin/debug/check-agent-redis/3" \
  -H "Authorization: Bearer <token>"
```

### 测试3：转人工流程

```bash
# 1. 用户发起转人工请求
# 2. 观察后端日志
# 3. 客服端应该收到通知
# 4. 客服接受请求
# 5. 正常通信
```

## 预防措施

### 1. 添加数据类型检查

在 `AgentStatusServiceImpl` 中添加检查：

```java
@Override
public Map<String, Object> getAgentStatus(Long tenantId, Long userId) {
    String statusKey = buildStatusKey(tenantId, userId);
    
    // 检查数据类型
    DataType type = redisTemplate.type(statusKey);
    if (type != DataType.HASH && type != DataType.NONE) {
        log.warn("[AgentStatus] 数据类型错误：key={}, type={}, 正在删除...", statusKey, type);
        redisTemplate.delete(statusKey);
        return Map.of("status", "OFFLINE", "current_sessions", 0);
    }
    
    Map<Object, Object> hash = redisTemplate.opsForHash().entries(statusKey);
    // ... 其余代码
}
```

### 2. 添加定时任务检查

创建一个定时任务，定期检查并修复错误的数据结构：

```java
@Scheduled(fixedRate = 300000) // 每5分钟
public void checkAndFixRedisDataStructure() {
    // 获取所有客服状态 key
    Set<String> keys = redisTemplate.keys("agent:status:*");
    
    for (String key : keys) {
        DataType type = redisTemplate.type(key);
        if (type != DataType.HASH && type != DataType.NONE) {
            log.warn("[AgentStatus] 发现错误的数据类型：key={}, type={}, 正在删除...", key, type);
            redisTemplate.delete(key);
        }
    }
}
```

### 3. 前端添加错误处理

前端在调用心跳接口失败时，尝试重新设置在线状态：

```typescript
const sendHeartbeat = async () => {
  try {
    await axios.post('/api/admin/user/heartbeat');
  } catch (error) {
    console.error('Heartbeat failed, trying to set online status...');
    try {
      await axios.put('/api/admin/user/online-status?status=ONLINE');
    } catch (e) {
      console.error('Failed to set online status:', e);
    }
  }
};
```

## 常见问题

### Q1: 为什么会出现数据类型错误？

**A**: 可能的原因：
1. 之前有其他代码错误地使用了 `redisTemplate.opsForValue().set()` 设置了这个 key
2. 手动在 Redis 中设置了错误的值
3. 代码升级前后数据结构不一致

### Q2: 修复后还是不行？

**A**: 检查以下几点：
1. 确认后端服务已重启
2. 确认客服已刷新页面
3. 确认 Redis 数据结构正确（使用检查接口）
4. 查看后端日志，确认没有其他错误

### Q3: 如何防止问题再次发生？

**A**: 
1. 添加数据类型检查（见预防措施）
2. 添加定时任务自动修复
3. 前端添加错误处理和重试机制
4. 定期检查 Redis 数据结构

## 总结

### 问题根源
Redis 中 `agent:status:1:3` 的数据类型错误（String 而不是 Hash）

### 解决步骤
1. ✅ 使用调试接口检查数据结构
2. ✅ 强制重置客服状态
3. ✅ 客服重新设置在线
4. ✅ 验证修复成功

### 预防措施
1. ✅ 添加数据类型检查
2. ✅ 添加定时任务自动修复
3. ✅ 前端添加错误处理

完成以上步骤后，转人工功能应该能够正常工作。
