# AI 对话上下文问题诊断与修复方案

## 一、问题现象

### 1.1 用户反馈

- **问题**：AI 一直返回相同的答案："我是您的客服助手,可以为您提供帮助和解答问题。"
- **表现**：无论用户问什么问题，AI 都回答一样的内容
- **需求**：
  1. 检查上下文构造逻辑是否有问题
  2. 在控制台打印 AI 看到的上下文，便于调试
  3. 找出为什么 AI 不回答问题

### 1.2 问题分析

通过代码审查，发现了**4个关键问题**：

---

## 二、问题根源分析

### 2.1 问题一：上下文顺序错误（严重）

**位置**：`LangChain4jQwenClient.java` 第 70-73 行

**当前代码**：
```java
if (context != null) {
    List<Message> sortedContext = new ArrayList<>(context);
    Collections.reverse(sortedContext);  // ✅ 创建了反转后的列表
    for (Message m : context) {          // ❌ 但遍历的还是原始 context！
        // ...
    }
}
```

**问题**：
- 代码创建了 `sortedContext` 并反转了顺序
- 但实际遍历时用的是原始的 `context`，没有使用 `sortedContext`
- 导致上下文顺序错误，AI 看到的对话顺序是反的

**影响**：
- AI 无法正确理解对话的先后顺序
- 可能导致 AI 忽略用户的最新问题

---

### 2.2 问题二：当前问题被传递两次（严重）

**位置**：`ChatServiceImpl.java` 第 183 行和第 200 行

**当前代码流程**：
```java
// 第 183 行：先把用户问题存入 Redis
appendContext(sessionId, "user", question);

// 第 188 行：从 Redis 读取上下文（此时已包含当前问题）
List<Map<String, String>> context = listContextFromRedis(sessionId);
List<Message> messages = context.stream()
    .map(m -> new Message(m.get("role"), m.get("content")))
    .collect(Collectors.toList());

// 第 200 行：调用 LLM，又传了一次 question
String aiReply = llmClient.chat(messages, question);
```

**问题**：
- 用户问题先被 `appendContext` 存入 Redis
- 然后从 Redis 读取上下文时，已经包含了当前问题
- 调用 `llmClient.chat(messages, question)` 时，又把 `question` 作为参数传了一次
- 导致当前问题在上下文中出现了**两次**

**影响**：
- 上下文混乱，AI 可能无法正确理解用户意图
- 浪费 token，增加成本

---

### 2.3 问题三：Redis 存储顺序与使用顺序不匹配

**位置**：`ChatServiceImpl.java` 第 333 行和第 152 行

**当前代码**：
```java
// appendContext: 使用 leftPush，最新消息在 index 0
stringRedisTemplate.opsForList().leftPush(key, json);

// listContextFromRedis: 读取前 10 条（index 0-9），即最新的 10 条
List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, CONTEXT_LIMIT - 1);
```

**问题**：
- Redis 中：最新消息在 index 0（leftPush）
- 读取时：取的是 index 0-9，即最新的 10 条
- 但顺序是：**最新 → 更旧**（倒序）
- LLM 需要的是：**最旧 → 最新**（正序）

**影响**：
- 即使修复了问题一，上下文顺序仍然是反的
- AI 看到的对话顺序错误

---

### 2.4 问题四：缺少上下文调试日志

**位置**：`LangChain4jQwenClient.java` 和 `ChatServiceImpl.java`

**问题**：
- 代码中没有打印 AI 实际看到的上下文
- 无法调试和验证上下文是否正确

**影响**：
- 无法快速定位问题
- 调试困难

---

## 三、修复方案

### 3.1 修复一：使用正确的上下文列表

**文件**：`src/main/java/com/ityfz/yulu/common/ai/impl/LangChain4jQwenClient.java`

**位置**：第 70-84 行

**修改前**：
```java
if (context != null) {
    List<Message> sortedContext = new ArrayList<>(context);
    Collections.reverse(sortedContext);
    for (Message m : context) {  // ❌ 错误：应该用 sortedContext
        // ...
    }
}
```

**修改后**：
```java
if (context != null) {
    // 创建副本并反转，使最旧的消息在前
    List<Message> sortedContext = new ArrayList<>(context);
    Collections.reverse(sortedContext);
    
    // ✅ 使用反转后的列表
    for (Message m : sortedContext) {
        if (m == null) continue;
        String role = m.getRole();
        String content = m.getContent();
        if (content == null) content = "";
        if ("assistant".equalsIgnoreCase(role) || "ai".equalsIgnoreCase(role)) {
            messages.add(AiMessage.from(content));
        } else {
            messages.add(UserMessage.from(content));
        }
    }
}
```

---

### 3.2 修复二：避免当前问题重复传递

**文件**：`src/main/java/com/ityfz/yulu/chat/service/impl/ChatServiceImpl.java`

**位置**：第 183-200 行

**方案 A：先读取上下文，再存入当前问题（推荐）**

**修改前**：
```java
// 3. 先把用户提问写入 MySQL & Redis
ChatMessage userMsg = new ChatMessage();
// ... 设置 userMsg
chatMessageMapper.insert(userMsg);
appendContext(sessionId, "user", question);  // ❌ 先存入

// 4. 从 Redis 中取出当前会话最近的上下文
List<Map<String, String>> context = listContextFromRedis(sessionId);  // ❌ 已包含当前问题
List<Message> messages = context.stream()
    .map(m -> new Message(m.get("role"), m.get("content")))
    .collect(Collectors.toList());

// 5. 调用 AI
String aiReply = llmClient.chat(messages, question);  // ❌ 又传了一次
```

**修改后**：
```java
// 3. 先把用户提问写入 MySQL（但不存入 Redis）
ChatMessage userMsg = new ChatMessage();
// ... 设置 userMsg
chatMessageMapper.insert(userMsg);
// ❌ 不要在这里 appendContext，避免重复

// 4. 从 Redis 中取出当前会话最近的上下文（不包含当前问题）
List<Map<String, String>> context = listContextFromRedis(sessionId);
List<Message> messages = context.stream()
    .map(m -> new Message(m.get("role"), m.get("content")))
    .collect(Collectors.toList());

// 5. 调用 AI（question 作为当前问题传入，不在 context 中）
String aiReply = llmClient.chat(messages, question);

// 6. 在 AI 回答后，再把用户问题和 AI 回答存入 Redis
appendContext(sessionId, "user", question);  // ✅ 现在存入
// ... AI 回答后
appendContext(sessionId, "assistant", aiReply);
```

**方案 B：从 context 中排除当前问题（备选）**

如果必须保持先存入 Redis 的逻辑，可以这样修改：

```java
// 3. 先把用户提问写入 MySQL & Redis
ChatMessage userMsg = new ChatMessage();
// ... 设置 userMsg
chatMessageMapper.insert(userMsg);
appendContext(sessionId, "user", question);

// 4. 从 Redis 中取出当前会话最近的上下文
List<Map<String, String>> context = listContextFromRedis(sessionId);
// ✅ 排除第一条（当前问题），因为后面会单独传
if (!context.isEmpty() && "user".equals(context.get(0).get("role"))) {
    context = context.subList(1, context.size());
}
List<Message> messages = context.stream()
    .map(m -> new Message(m.get("role"), m.get("content")))
    .collect(Collectors.toList());

// 5. 调用 AI
String aiReply = llmClient.chat(messages, question);
```

**推荐使用方案 A**，逻辑更清晰。

---

### 3.3 修复三：反转 Redis 读取顺序

**文件**：`src/main/java/com/ityfz/yulu/chat/service/impl/ChatServiceImpl.java`

**位置**：第 149-162 行

**修改前**：
```java
public List<Map<String, String>> listContextFromRedis(Long sessionId) {
    String key = buildContextKey(sessionId);
    // 读取列表前10条（index 0-9），即最新的 10 条，顺序是：最新 → 更旧
    List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, CONTEXT_LIMIT - 1);
    if (CollectionUtils.isEmpty(jsonList)) {
        return Collections.emptyList();
    }
    // 直接返回，顺序是反的
    return jsonList.stream()
        .map(json -> JSONUtil.toBean(json, new TypeReference<Map<String, String>>() {}, true))
        .collect(Collectors.toList());
}
```

**修改后**：
```java
public List<Map<String, String>> listContextFromRedis(Long sessionId) {
    String key = buildContextKey(sessionId);
    // 读取列表前10条（index 0-9），即最新的 10 条
    List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, CONTEXT_LIMIT - 1);
    if (CollectionUtils.isEmpty(jsonList)) {
        return Collections.emptyList();
    }
    // ✅ 反转顺序，使最旧的消息在前（LLM 需要正序）
    Collections.reverse(jsonList);
    return jsonList.stream()
        .map(json -> JSONUtil.toBean(json, new TypeReference<Map<String, String>>() {}, true))
        .collect(Collectors.toList());
}
```

**注意**：如果使用修复二的方案 A，这个修复可以不做，因为 `LangChain4jQwenClient` 中已经做了反转。但为了代码清晰，建议在 `listContextFromRedis` 中反转，这样返回的就是正序。

---

### 3.4 修复四：添加上下文调试日志

**文件**：`src/main/java/com/ityfz/yulu/common/ai/impl/LangChain4jQwenClient.java`

**位置**：`chat` 方法中，在调用模型之前

**添加日志**：
```java
@Override
public String chat(List<Message> context, String question) {
    List<ChatMessage> messages = new ArrayList<>();

    // 1）System 提示词
    messages.add(SystemMessage.from(
            "你是一个专业的客服助手，所有输出必须是 JSON：" +
                    "{ \"answer\": \"...\", \"emotion\": \"HAPPY|ANGRY|SAD|NEUTRAL|NORMAL\", " +
                    "\"intent\": \"REFUND|INVOICE|COMPLAIN|GENERAL\" }。" +
                    "answer 是最终给用户看的自然语言回答；" +
                    "emotion 是情绪标签；intent 是用户意图标签。" +
                    "不要输出任何说明文字，不要输出 JSON 以外的内容。"
    ));

    // 2）把历史上下文从 List<Message> 转成 LangChain4j 的 ChatMessage
    if (context != null) {
        List<Message> sortedContext = new ArrayList<>(context);
        Collections.reverse(sortedContext);
        
        // ✅ 添加调试日志：打印上下文
        log.info("========== [AI上下文调试] ==========");
        log.info("历史上下文数量: {}", sortedContext.size());
        for (int i = 0; i < sortedContext.size(); i++) {
            Message m = sortedContext.get(i);
            log.info("  [{}] role={}, content={}", i, m.getRole(), 
                    m.getContent() != null && m.getContent().length() > 50 
                        ? m.getContent().substring(0, 50) + "..." 
                        : m.getContent());
        }
        log.info("当前问题: {}", question);
        log.info("====================================");
        
        for (Message m : sortedContext) {
            if (m == null) continue;
            String role = m.getRole();
            String content = m.getContent();
            if (content == null) content = "";
            if ("assistant".equalsIgnoreCase(role) || "ai".equalsIgnoreCase(role)) {
                messages.add(AiMessage.from(content));
            } else {
                messages.add(UserMessage.from(content));
            }
        }
    }

    // 3）再追加本轮用户问题
    messages.add(UserMessage.from(question));
    
    // ✅ 添加调试日志：打印最终发送给模型的消息
    log.info("========== [AI消息列表] ==========");
    log.info("发送给模型的消息总数: {}", messages.size());
    for (int i = 0; i < messages.size(); i++) {
        ChatMessage msg = messages.get(i);
        String content = msg.text();
        log.info("  [{}] type={}, content={}", i, msg.getClass().getSimpleName(),
                content != null && content.length() > 100 
                    ? content.substring(0, 100) + "..." 
                    : content);
    }
    log.info("===================================");

    // 4）调用模型
    ChatResponse response = model.chat(messages);
    String rawText = response.aiMessage().text();
    
    // ✅ 添加调试日志：打印模型原始响应
    log.info("========== [AI原始响应] ==========");
    log.info("原始响应: {}", rawText);
    log.info("===================================");

    // 5）解析 JSON 得到结构化结果
    ChatResult result = parseChatResult(rawText);
    lastResult.set(result);
    
    // ✅ 添加调试日志：打印解析后的结果
    log.info("========== [AI解析结果] ==========");
    log.info("answer: {}", result.getAnswer());
    log.info("emotion: {}", result.getEmotion());
    log.info("intent: {}", result.getIntent());
    log.info("===================================");

    // 6）对外仍然只返回 answer 文本
    return result.getAnswer();
}
```

---

## 四、完整修复步骤

### 步骤 1：修复 LangChain4jQwenClient 的上下文遍历

1. 打开文件：`src/main/java/com/ityfz/yulu/common/ai/impl/LangChain4jQwenClient.java`
2. 定位到第 70-84 行
3. 将 `for (Message m : context)` 改为 `for (Message m : sortedContext)`
4. 添加上下文调试日志（见修复四）

### 步骤 2：修复 ChatServiceImpl 的问题重复传递

1. 打开文件：`src/main/java/com/ityfz/yulu/chat/service/impl/ChatServiceImpl.java`
2. 定位到 `chatWithAi` 方法（第 165 行）
3. 调整代码顺序：
   - 先读取 Redis 上下文（不包含当前问题）
   - 调用 LLM（传入 context 和 question）
   - 再把用户问题和 AI 回答存入 Redis
4. 或者使用方案 B：在读取上下文后排除第一条

### 步骤 3：修复 Redis 读取顺序（可选）

1. 打开文件：`src/main/java/com/ityfz/yulu/chat/service/impl/ChatServiceImpl.java`
2. 定位到 `listContextFromRedis` 方法（第 149 行）
3. 在返回前添加 `Collections.reverse(jsonList);`

### 步骤 4：验证修复

1. 重启应用
2. 发送几条测试消息
3. 查看控制台日志，确认：
   - 上下文顺序正确（最旧 → 最新）
   - 当前问题不重复
   - AI 能正确回答问题

---

## 五、问题总结

### 5.1 根本原因

1. **上下文顺序错误**：创建了反转列表但没使用
2. **问题重复传递**：当前问题在 context 和参数中都出现了
3. **Redis 顺序问题**：存储是倒序，读取时没有反转
4. **缺少调试日志**：无法验证上下文是否正确

### 5.2 修复优先级

1. **高优先级**：修复一（使用正确的上下文列表）
2. **高优先级**：修复二（避免问题重复传递）
3. **中优先级**：修复三（反转 Redis 读取顺序）
4. **低优先级**：修复四（添加调试日志，但建议立即添加）

### 5.3 预期效果

修复后：
- ✅ 上下文顺序正确（最旧 → 最新）
- ✅ 当前问题不重复
- ✅ AI 能正确理解用户问题并回答
- ✅ 控制台可以查看 AI 看到的完整上下文

---

## 六、调试建议

### 6.1 查看日志

修复后，在控制台会看到类似这样的日志：

```
========== [AI上下文调试] ==========
历史上下文数量: 4
  [0] role=user, content=我买的裤子到了没?
  [1] role=assistant, content=我是您的客服助手,可以为您提供帮助和解答问题。
  [2] role=user, content=你是不是傻
  [3] role=assistant, content=我是您的客服助手,可以为您提供帮助和解答问题。
当前问题: 我现在的问题是什么
====================================

========== [AI消息列表] ==========
发送给模型的消息总数: 6
  [0] type=SystemMessage, content=你是一个专业的客服助手，所有输出必须是 JSON：...
  [1] type=UserMessage, content=我买的裤子到了没?
  [2] type=AiMessage, content=我是您的客服助手,可以为您提供帮助和解答问题。
  ...
===================================

========== [AI原始响应] ==========
原始响应: {"answer":"您当前询问的是关于您购买的裤子是否已经送达的问题...","emotion":"NEUTRAL","intent":"GENERAL"}
===================================

========== [AI解析结果] ==========
answer: 您当前询问的是关于您购买的裤子是否已经送达的问题...
emotion: NEUTRAL
intent: GENERAL
===================================
```

### 6.2 验证要点

1. **上下文数量**：应该包含历史对话，不包含当前问题
2. **上下文顺序**：最旧的消息在前，最新的在后
3. **消息类型**：SystemMessage、UserMessage、AiMessage 交替出现
4. **当前问题**：只在最后出现一次
5. **AI 响应**：应该根据上下文和当前问题生成不同的回答

---

**文档完成时间**：2025-01-14  
**适用版本**：LangChain4jQwenClient v1.0, ChatServiceImpl v1.0

















