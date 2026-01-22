# 上下文构造逻辑详解与 Redis 交互机制

## 一、问题分析

### 1.1 从日志发现的问题

根据您提供的日志，发现了**AI回答被重复存储两次**的问题：

**第一次对话（"好的我想知道退货的流程"）**：
```
历史上下文数量: 3
  [0] role=user, content=你是谁
  [1] role=assistant, content=我是您的客服助手，旨在为您提供帮助和解答您的问题。
  [2] role=assistant, content=我是您的客服助手，旨在为您提供帮助和解答您的问题。  ❌ 重复！
```

**第二次对话（"邮费谁出"）**：
```
历史上下文数量: 6
  [0] role=user, content=你是谁
  [1] role=assistant, content=我是您的客服助手，旨在为您提供帮助和解答您的问题。
  [2] role=assistant, content=我是您的客服助手，旨在为您提供帮助和解答您的问题。  ❌ 重复！
  [3] role=user, content=好的我想知道退货的流程
  [4] role=assistant, content=您好，退货流程通常包括以下步骤...  ❌ 重复！
  [5] role=assistant, content=您好，退货流程通常包括以下步骤...  ❌ 重复！
```

### 1.2 代码问题定位

**文件**：`src/main/java/com/ityfz/yulu/chat/service/impl/ChatServiceImpl.java`

**问题代码**（第 200-262 行）：
```java
// 5. 调用 AI
String aiReply = llmClient.chat(messages, question);

// ❌ 第 202-203 行：第一次存储用户问题和AI回答
appendContext(sessionId, "user", question);
appendContext(sessionId, "assistant", aiReply);

// ... 中间省略 ...

// 8. 把 AI 回答写入 MySQL
ChatMessage aiMsg = new ChatMessage();
// ... 设置 aiMsg ...
chatMessageMapper.insert(aiMsg);

// ❌ 第 262 行：第二次存储AI回答
appendContext(sessionId, "assistant", aiReply);
```

**问题根源**：
- AI回答在第 203 行被存储了一次
- 又在第 262 行被存储了一次
- 导致每次AI回答都被重复存储

---

## 二、AI回答是否应该算进上下文？

### 2.1 标准AI对话逻辑

**答案：是的，AI回答必须算进上下文！**

这是所有AI对话系统（ChatGPT、Claude、通义千问等）的标准做法。

### 2.2 为什么需要AI回答？

**原因1：保持对话连贯性**

```
用户：你是谁
AI：我是您的客服助手

用户：好的我想知道退货的流程  ← 用户说"好的"，这是对上一个AI回答的回应
AI：您好，退货流程通常包括以下步骤...
```

如果上下文不包含AI回答，AI就不知道用户说"好的"是在回应什么。

**原因2：支持多轮对话**

```
用户：我想退货
AI：请问是什么原因要退货呢？
用户：尺寸不合适  ← 这是对AI问题的回答
AI：好的，我来帮您处理退货申请...
```

如果上下文不包含AI回答，AI就不知道用户说"尺寸不合适"是在回答什么问题。

**原因3：上下文理解**

```
用户：这个多少钱？
AI：这个商品价格是100元
用户：那运费呢？  ← "那"指的是"这个商品"
AI：运费是10元
```

如果上下文不包含AI回答，AI就不知道"那"指的是什么。

### 2.3 标准上下文格式

所有主流AI系统都使用**交替对话格式**：

```
System: 你是客服助手
User: 你是谁
Assistant: 我是您的客服助手
User: 好的我想知道退货的流程
Assistant: 您好，退货流程通常包括以下步骤...
User: 邮费谁出
Assistant: 一般情况下，如果是商品质量问题...
```

**格式规则**：
- System消息（可选）：设定AI的角色和行为
- User消息：用户的问题
- Assistant消息：AI的回答
- **必须交替出现**：User → Assistant → User → Assistant → ...

---

## 三、Redis 交互机制详解

### 3.1 Redis List 数据结构

**Redis List** 是一个双向链表，支持从头部或尾部插入/删除元素。

**关键操作**：
- `LPUSH` / `leftPush`：从**左侧（头部）**插入，新元素在 index 0
- `RPUSH` / `rightPush`：从**右侧（尾部）**插入，新元素在最后
- `LRANGE` / `range`：读取指定范围，`range(key, 0, 9)` 表示读取 index 0-9
- `LTRIM` / `trim`：裁剪列表，只保留指定范围

### 3.2 当前项目的存储逻辑

#### 3.2.1 appendContext 方法

**位置**：`ChatServiceImpl.java` 第 327-343 行

**代码**：
```java
private void appendContext(Long sessionId, String role, String content) {
    String key = "chat:context:" + sessionId;  // Redis key
    Map<String, String> entry = new HashMap<>();
    entry.put("role", role);      // "user" 或 "assistant"
    entry.put("content", content);
    String json = JSONUtil.toJsonStr(entry);  // 转成JSON字符串

    // leftPush：从左侧插入，新消息在 index 0
    stringRedisTemplate.opsForList().leftPush(key, json);
    
    // trim：只保留前 10 条（index 0-9），丢弃更旧的消息
    stringRedisTemplate.opsForList().trim(key, 0, CONTEXT_LIMIT - 1);
    
    // 按字符长度进一步裁剪
    trimContextByLength(sessionId, tenantId);
}
```

**存储顺序**：**最新消息在 index 0（倒序）**

#### 3.2.2 存储过程图解

**场景**：用户和AI进行了3轮对话

**第1轮对话**：
```
用户：你是谁
AI：我是您的客服助手
```

**Redis 存储过程**：

```
步骤1：用户问题存入
leftPush("chat:context:4", {"role":"user","content":"你是谁"})

Redis List (chat:context:4):
┌─────────────────────────────────┐
│ index 0: {"role":"user","content":"你是谁"} │  ← 最新
└─────────────────────────────────┘

步骤2：AI回答存入
leftPush("chat:context:4", {"role":"assistant","content":"我是您的客服助手"})

Redis List (chat:context:4):
┌─────────────────────────────────────────────────────────────┐
│ index 0: {"role":"assistant","content":"我是您的客服助手"} │  ← 最新
│ index 1: {"role":"user","content":"你是谁"}                │  ← 更旧
└─────────────────────────────────────────────────────────────┘
```

**第2轮对话**：
```
用户：好的我想知道退货的流程
AI：您好，退货流程通常包括以下步骤...
```

**Redis 存储过程**：

```
步骤1：用户问题存入
leftPush("chat:context:4", {"role":"user","content":"好的我想知道退货的流程"})

Redis List (chat:context:4):
┌─────────────────────────────────────────────────────────────┐
│ index 0: {"role":"user","content":"好的我想知道退货的流程"} │  ← 最新
│ index 1: {"role":"assistant","content":"我是您的客服助手"}   │
│ index 2: {"role":"user","content":"你是谁"}                │  ← 最旧
└─────────────────────────────────────────────────────────────┘

步骤2：AI回答存入
leftPush("chat:context:4", {"role":"assistant","content":"您好，退货流程..."})

Redis List (chat:context:4):
┌─────────────────────────────────────────────────────────────┐
│ index 0: {"role":"assistant","content":"您好，退货流程..."} │  ← 最新
│ index 1: {"role":"user","content":"好的我想知道退货的流程"} │
│ index 2: {"role":"assistant","content":"我是您的客服助手"}   │
│ index 3: {"role":"user","content":"你是谁"}                │  ← 最旧
└─────────────────────────────────────────────────────────────┘
```

#### 3.2.3 读取上下文过程

**位置**：`ChatServiceImpl.java` 第 149-162 行

**代码**：
```java
public List<Map<String, String>> listContextFromRedis(Long sessionId) {
    String key = "chat:context:" + sessionId;
    // range(0, 9)：读取 index 0-9，即最新的 10 条
    List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, CONTEXT_LIMIT - 1);
    
    if (CollectionUtils.isEmpty(jsonList)) {
        return Collections.emptyList();
    }
    
    // 转成 Map 列表
    return jsonList.stream()
        .map(json -> JSONUtil.toBean(json, new TypeReference<Map<String, String>>() {}, true))
        .collect(Collectors.toList());
}
```

**读取结果**：**顺序是倒序（最新 → 最旧）**

**示例**：读取上面的 Redis List

```
Redis List (chat:context:4):
┌─────────────────────────────────────────────────────────────┐
│ index 0: {"role":"assistant","content":"您好，退货流程..."} │
│ index 1: {"role":"user","content":"好的我想知道退货的流程"} │
│ index 2: {"role":"assistant","content":"我是您的客服助手"}   │
│ index 3: {"role":"user","content":"你是谁"}                │
└─────────────────────────────────────────────────────────────┘

读取结果（List<Map<String, String>>）：
[
  {"role":"assistant","content":"您好，退货流程..."},  ← 最新
  {"role":"user","content":"好的我想知道退货的流程"},
  {"role":"assistant","content":"我是您的客服助手"},
  {"role":"user","content":"你是谁"}                   ← 最旧
]
```

#### 3.2.4 反转队列（在 LangChain4jQwenClient 中）

**位置**：`LangChain4jQwenClient.java` 第 70-72 行

**代码**：
```java
if (context != null) {
    List<Message> sortedContext = new ArrayList<>(context);
    Collections.reverse(sortedContext);  // 反转：最旧 → 最新
    for (Message m : sortedContext) {    // ✅ 应该用 sortedContext
        // ...
    }
}
```

**反转过程**：

```
读取结果（倒序）：
[
  {"role":"assistant","content":"您好，退货流程..."},  ← 最新
  {"role":"user","content":"好的我想知道退货的流程"},
  {"role":"assistant","content":"我是您的客服助手"},
  {"role":"user","content":"你是谁"}                   ← 最旧
]

反转后（正序）：
[
  {"role":"user","content":"你是谁"},                   ← 最旧
  {"role":"assistant","content":"我是您的客服助手"},
  {"role":"user","content":"好的我想知道退货的流程"},
  {"role":"assistant","content":"您好，退货流程..."}    ← 最新
]
```

**为什么需要反转？**

LLM 需要按**时间顺序**理解对话：
- 最旧的消息在前（历史）
- 最新的消息在后（当前）

如果顺序反了，AI 会先看到最新的消息，无法理解对话的演进过程。

---

## 四、完整对话流程示例

### 4.1 场景：用户进行3轮对话

**对话内容**：
1. 用户：你是谁
2. AI：我是您的客服助手
3. 用户：好的我想知道退货的流程
4. AI：您好，退货流程通常包括以下步骤...
5. 用户：邮费谁出
6. AI：一般情况下，如果是商品质量问题...

### 4.2 完整流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                        第1轮对话                                  │
└─────────────────────────────────────────────────────────────────┘

用户发送："你是谁"
    ↓
[ChatServiceImpl.chatWithAi]
    ↓
1. 存入MySQL：INSERT chat_message (USER, "你是谁")
    ↓
2. 存入Redis：leftPush("chat:context:4", {"role":"user","content":"你是谁"})
    ↓
Redis List:
┌─────────────────────────────────┐
│ index 0: {"role":"user","content":"你是谁"} │
└─────────────────────────────────┘
    ↓
3. 读取上下文：range(0, 9) → [{"role":"user","content":"你是谁"}]
    ↓
4. 调用LLM：chat([], "你是谁")
    ↓
5. AI返回："我是您的客服助手"
    ↓
6. 存入MySQL：INSERT chat_message (AI, "我是您的客服助手")
    ↓
7. 存入Redis：leftPush("chat:context:4", {"role":"assistant","content":"我是您的客服助手"})
    ↓
Redis List:
┌─────────────────────────────────────────────────────────────┐
│ index 0: {"role":"assistant","content":"我是您的客服助手"} │
│ index 1: {"role":"user","content":"你是谁"}                │
└─────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────┐
│                        第2轮对话                                  │
└─────────────────────────────────────────────────────────────────┘

用户发送："好的我想知道退货的流程"
    ↓
[ChatServiceImpl.chatWithAi]
    ↓
1. 存入MySQL：INSERT chat_message (USER, "好的我想知道退货的流程")
    ↓
2. 存入Redis：leftPush("chat:context:4", {"role":"user","content":"好的我想知道退货的流程"})
    ↓
Redis List:
┌─────────────────────────────────────────────────────────────┐
│ index 0: {"role":"user","content":"好的我想知道退货的流程"} │
│ index 1: {"role":"assistant","content":"我是您的客服助手"}   │
│ index 2: {"role":"user","content":"你是谁"}                │
└─────────────────────────────────────────────────────────────┘
    ↓
3. 读取上下文：range(0, 9) → [
      {"role":"user","content":"好的我想知道退货的流程"},      ← 最新（倒序）
      {"role":"assistant","content":"我是您的客服助手"},
      {"role":"user","content":"你是谁"}                       ← 最旧（倒序）
    ]
    ↓
4. 转换为Message列表：[
      Message("user", "好的我想知道退货的流程"),
      Message("assistant", "我是您的客服助手"),
      Message("user", "你是谁")
    ]
    ↓
5. 传入LangChain4jQwenClient.chat()
    ↓
6. 反转列表：[
      Message("user", "你是谁"),                              ← 最旧（正序）
      Message("assistant", "我是您的客服助手"),
      Message("user", "好的我想知道退货的流程")                ← 最新（正序）
    ]
    ↓
7. 构建LangChain4j消息列表：
   [
     SystemMessage("你是客服助手..."),
     UserMessage("你是谁"),
     AiMessage("我是您的客服助手"),
     UserMessage("好的我想知道退货的流程"),
     UserMessage("好的我想知道退货的流程")  ← 当前问题（单独传入）
   ]
    ↓
8. 调用模型：model.chat(messages)
    ↓
9. AI返回："您好，退货流程通常包括以下步骤..."
    ↓
10. 存入MySQL：INSERT chat_message (AI, "您好，退货流程...")
    ↓
11. 存入Redis：leftPush("chat:context:4", {"role":"assistant","content":"您好，退货流程..."})
    ↓
Redis List:
┌─────────────────────────────────────────────────────────────┐
│ index 0: {"role":"assistant","content":"您好，退货流程..."} │
│ index 1: {"role":"user","content":"好的我想知道退货的流程"} │
│ index 2: {"role":"assistant","content":"我是您的客服助手"}   │
│ index 3: {"role":"user","content":"你是谁"}                │
└─────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────┐
│                        第3轮对话                                  │
└─────────────────────────────────────────────────────────────────┘

用户发送："邮费谁出"
    ↓
[ChatServiceImpl.chatWithAi]
    ↓
...（流程相同）
    ↓
最终Redis List:
┌─────────────────────────────────────────────────────────────┐
│ index 0: {"role":"assistant","content":"一般情况下..."}     │
│ index 1: {"role":"user","content":"邮费谁出"}                │
│ index 2: {"role":"assistant","content":"您好，退货流程..."} │
│ index 3: {"role":"user","content":"好的我想知道退货的流程"} │
│ index 4: {"role":"assistant","content":"我是您的客服助手"}   │
│ index 5: {"role":"user","content":"你是谁"}                │
└─────────────────────────────────────────────────────────────┘
```

---

## 五、问题修复方案

### 5.1 问题：AI回答被重复存储

**原因**：
- 第 203 行：`appendContext(sessionId, "assistant", aiReply);`
- 第 262 行：`appendContext(sessionId, "assistant", aiReply);`

**修复方案**：删除第 262 行的重复存储

**修改前**：
```java
// 5. 调用 AI
String aiReply = llmClient.chat(messages, question);
appendContext(sessionId, "user", question);
appendContext(sessionId, "assistant", aiReply);  // ✅ 保留这里

// ... 中间省略 ...

// 8. 把 AI 回答写入 MySQL
ChatMessage aiMsg = new ChatMessage();
// ... 设置 aiMsg ...
chatMessageMapper.insert(aiMsg);

// 9. 更新 Redis 上下文
appendContext(sessionId, "assistant", aiReply);  // ❌ 删除这行，重复了
```

**修改后**：
```java
// 5. 调用 AI
String aiReply = llmClient.chat(messages, question);

// 6. 存入Redis上下文（用户问题和AI回答）
appendContext(sessionId, "user", question);
appendContext(sessionId, "assistant", aiReply);

// ... 中间省略 ...

// 8. 把 AI 回答写入 MySQL
ChatMessage aiMsg = new ChatMessage();
// ... 设置 aiMsg ...
chatMessageMapper.insert(aiMsg);

// ❌ 删除这行：appendContext(sessionId, "assistant", aiReply);
// 因为已经在第6步存储过了
```

### 5.2 问题：用户问题可能被重复存储

**检查**：查看代码第 183 行是否被注释

如果第 183 行没有被注释，用户问题也会被重复存储。

**修复方案**：确保用户问题只存储一次

**推荐方案**：在调用LLM之前不存储用户问题，在调用LLM之后一起存储

```java
// 3. 先把用户提问写入 MySQL（但不存入Redis）
ChatMessage userMsg = new ChatMessage();
// ... 设置 userMsg ...
chatMessageMapper.insert(userMsg);
// ❌ 不要在这里 appendContext

// 4. 从 Redis 中取出当前会话最近的上下文（不包含当前问题）
List<Map<String, String>> context = listContextFromRedis(sessionId);
List<Message> messages = context.stream()
    .map(m -> new Message(m.get("role"), m.get("content")))
    .collect(Collectors.toList());

// 5. 调用 AI（question 作为当前问题传入，不在 context 中）
String aiReply = llmClient.chat(messages, question);

// 6. 在AI回答后，一起存入Redis（用户问题和AI回答）
appendContext(sessionId, "user", question);      // ✅ 现在存入
appendContext(sessionId, "assistant", aiReply); // ✅ 现在存入
```

---

## 六、Redis 操作详解

### 6.1 leftPush 操作

**作用**：从列表左侧（头部）插入元素

**语法**：`leftPush(key, value)`

**效果**：
- 新元素插入到 index 0
- 原有元素向右移动（index +1）

**示例**：
```
初始列表：[A, B, C]
leftPush("key", "X")

结果列表：[X, A, B, C]
         ↑
       index 0
```

### 6.2 range 操作

**作用**：读取列表指定范围

**语法**：`range(key, start, end)`

**说明**：
- `start`：起始索引（包含）
- `end`：结束索引（包含）
- 支持负数：`-1` 表示最后一个元素

**示例**：
```
列表：[X, A, B, C]
     ↑  ↑  ↑  ↑
     0  1  2  3

range("key", 0, 2)  → [X, A, B]
range("key", 0, -1) → [X, A, B, C]  （全部）
range("key", 0, 9)  → [X, A, B, C]  （最多10条）
```

### 6.3 trim 操作

**作用**：裁剪列表，只保留指定范围

**语法**：`trim(key, start, end)`

**说明**：
- 只保留 `start` 到 `end` 之间的元素
- 其他元素被删除

**示例**：
```
初始列表：[X, A, B, C, D, E]
         ↑  ↑  ↑  ↑  ↑  ↑
         0  1  2  3  4  5

trim("key", 0, 2)

结果列表：[X, A, B]
         ↑  ↑  ↑
         0  1  2
         （C, D, E 被删除）
```

### 6.4 完整操作流程示例

**场景**：存储3条消息，只保留最新2条

```
步骤1：leftPush("key", "消息1")
列表：[消息1]
     index 0

步骤2：leftPush("key", "消息2")
列表：[消息2, 消息1]
     index 0  index 1

步骤3：leftPush("key", "消息3")
列表：[消息3, 消息2, 消息1]
     index 0  index 1  index 2

步骤4：trim("key", 0, 1)  （只保留最新2条）
列表：[消息3, 消息2]
     index 0  index 1
     （消息1 被删除）

步骤5：range("key", 0, 1)  （读取最新2条）
结果：[消息3, 消息2]
```

---

## 七、总结

### 7.1 关键点总结

1. **AI回答必须算进上下文**：这是所有AI对话系统的标准做法
2. **上下文格式**：必须是交替的 User → Assistant → User → Assistant
3. **Redis存储顺序**：使用 `leftPush`，最新消息在 index 0（倒序）
4. **读取后需要反转**：LLM需要正序（最旧 → 最新）
5. **避免重复存储**：确保每条消息只存储一次

### 7.2 修复清单

- [ ] 删除第 262 行的重复 `appendContext(sessionId, "assistant", aiReply)`
- [ ] 检查第 183 行，确保用户问题不重复存储
- [ ] 验证 `LangChain4jQwenClient` 中使用了 `sortedContext` 而不是 `context`
- [ ] 添加调试日志，验证上下文顺序正确

### 7.3 验证方法

修复后，查看日志应该看到：

```
历史上下文数量: 4
  [0] role=user, content=你是谁
  [1] role=assistant, content=我是您的客服助手
  [2] role=user, content=好的我想知道退货的流程
  [3] role=assistant, content=您好，退货流程通常包括以下步骤...
  （没有重复！）
```

---

**文档完成时间**：2025-01-16  
**适用版本**：ChatServiceImpl v1.0, LangChain4jQwenClient v1.0












