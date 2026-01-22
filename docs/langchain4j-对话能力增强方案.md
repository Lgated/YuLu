## 1. 概览：你现在已经有的能力

先把你现有后端的对话链路梳理清楚，后面的升级方案全部**基于当前实现**来增强，而不是推翻重来：

- **统一大模型接口：`LLMClient`**
  - `String chat(List<Message> context, String question)`：
    - `Message` 内部就是 `role + content`，你在 Redis 中维护最近 N 条上下文，再转成 `List<Message>` 传入。
  - `String detectIntent(String text)`：意图识别（Mock/规则）。
  - `String detectEmotion(String text)`：情绪识别（Mock/规则）。

- **具体实现：**
  - `MockLLMClient`：关键字规则，用来本地快速联调整条业务链路。
  - `QianWenClient`：直接走 DashScope HTTP（标准 / 兼容模式），复用通义千问原生 API。
  - `LangChain4jQwenClient`（当前 Chat 正在使用）：
    - 使用 `OpenAiChatModel` + `System/User/AiMessage`，走通义千问的 OpenAI 兼容接口。
    - 内部已经把 `List<Message>` 转成 LangChain4j 消息列表，再 `model.chat(messages).aiMessage().text()` 返回。
    - `detectEmotion` 目前是简单**关键字规则**，还没用上 LangChain4j。

- **业务侧：`ChatServiceImpl.chatWithAi(...)`**
  1. 设置 `TenantContextHolder`，保证多租户链路正确。
  2. 没有会话则 `createSessionIfNotExists(...)` 创建会话。
  3. 把用户提问落库（`chat_message`）+ 写入 Redis 上下文 `appendContext(sessionId, "user", question)`。
  4. 从 Redis 读取最近 N 条上下文（`CONTEXT_LIMIT=10`），并且用 `trimContextByLength` 做总字符上限裁剪（小租户/大租户不同限额）。
  5. 把 Redis 里的 `List<Map<role,content>>` 转成 `List<Message>`，调用 `llmClient.chat(messages, question)` 拿到 AI 文本回复。
  6. 调用 `llmClient.detectEmotion(question)` 得到情绪标签，触发 MQ 事件 `NegativeEmotionEvent` / 创建工单。
  7. 把 AI 回复落库 + 写入 Redis 上下文。

> 也就是说：**调用抽象、上下文策略、多租户、负向情绪自动工单**你都已经有了，接下来要做的是：
> - 让 LangChain4j 做得更“聪明”（结构化输出、情绪/意图模型化）；  
> - 在现有链路中平滑插入 RAG 与摘要能力。

---

## 2. 本文升级目标（在不改接口的前提下增强能力）

1. **增强 `LangChain4jQwenClient.chat`：**
   - 仍然对外返回 `String`（保持 `LLMClient.chat` 不变）；
   - 内部用 LangChain4j 让模型返回 JSON 结构（answer + emotion + intent），便于后续扩展。

2. **把情绪 / 意图识别也做成模型能力：**
   - `detectEmotion` / `detectIntent` 从规则 -> LangChain4j Prompt 调用；
   - 保留规则作为兜底，保证稳定性。

3. **在现有 Redis 上下文策略上，增加“摘要 + 窗口”记忆：**
   - 每个会话增加一个 summary 槽位；
   - 超长会话时用 LangChain4j 总结旧对话，压缩为摘要文本 + 最近几轮原始对话。

4. **引入多租户 RAG 知识库：**
   - 新增知识库表，按租户管理 FAQ / 文档；
   - 在 `chatWithAi` 里先检索，再把结果通过 SystemMessage 注入 `LLMClient.chat` 的 context；
   - 第一阶段先用 SQL/全文检索跑通，第二阶段再接入向量检索。

下面依次展开，每一节都围绕“你现在已经有的代码”来讲怎么**在原基础上增强**。

---

## 3. 阶段一：让 LangChain4jQwenClient 支持结构化输出（不改接口）

### 3.1 内部定义一个结构化结果 `ChatResult`

> 只在 `LangChain4jQwenClient` 内部使用，对外接口还是 `String chat(...)`。

```java
// 仅在 LangChain4jQwenClient 内部使用
static class ChatResult {
    private String answer;   // 最终回复文本
    private String emotion;  // 情绪: HAPPY / ANGRY / SAD / NEUTRAL / NORMAL
    private String intent;   // 意图: REFUND / INVOICE / COMPLAIN / GENERAL ...

    // getter / setter 省略
}
```

### 3.2 调整 System Prompt：让模型输出 JSON

在 `LangChain4jQwenClient.chat` 里，你现在有：

```java
messages.add(SystemMessage.from("你是一个专业的客服助手，回答要简洁、准确。"));
...
return model.chat(messages).aiMessage().text();
```

可以调整为（伪代码，思路级）：

```java
messages.add(SystemMessage.from(
    "你是一个专业的客服助手，所有输出必须是 JSON：" +
    "{ \"answer\": \"...\", \"emotion\": \"HAPPY|ANGRY|SAD|NEUTRAL\", \"intent\": \"REFUND|INVOICE|COMPLAIN|GENERAL\" }。" +
    "只输出 JSON，不要输出多余文字。"
));

ChatResponse<AiMessage> resp = model.chat(messages);
String jsonText = resp.aiMessage().text();

ChatResult result = parseJson(jsonText);
// 对 LLMClient.chat 的调用方来说，仍然只是一个 string
return result.getAnswer();
```

JSON 解析方法示例（可用 Jackson / Hutool）：

```java
private ChatResult parseJson(String jsonText) {
    ChatResult r = new ChatResult();
    try {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(jsonText);
        r.setAnswer(node.path("answer").asText(jsonText)); // 解析不到就用原文
        r.setEmotion(node.path("emotion").asText("NORMAL").toUpperCase());
        r.setIntent(node.path("intent").asText("GENERAL").toUpperCase());
    } catch (Exception e) {
        log.warn("[LLM] 解析 JSON 失败，原始返回：{}", jsonText, e);
        r.setAnswer(jsonText);
        r.setEmotion("NORMAL");
        r.setIntent("GENERAL");
    }
    return r;
}
```

> 这一步做完后，**上层 `ChatServiceImpl.chatWithAi` 完全不用改**，但 `LangChain4jQwenClient` 已经拿到了情绪+意图等结构化信息，为下一阶段做准备。

### 3.3 可选：缓存最近一次结果，便于以后复用

如果你希望在 `detectEmotion` / `detectIntent` 里复用 `chat` 的结构化结果，可以在 `LangChain4jQwenClient` 里加一个字段：

```java
private final ThreadLocal<ChatResult> lastResult = new ThreadLocal<>();

public String chat(List<Message> context, String question) {
    ChatResult result = doChatWithJson(context, question);
    lastResult.set(result);
    return result.getAnswer();
}

private Optional<ChatResult> getLastResult() {
    return Optional.ofNullable(lastResult.get());
}
```

短期可以先不用，在后面的情绪识别阶段再决定是否启用。

---

## 4. 阶段二：把情绪 / 意图识别改造为 LangChain4j 能力

目前：

- `ChatServiceImpl.chatWithAi` 中：

```java
String aiReply = llmClient.chat(messages, question);
String emotion = llmClient.detectEmotion(question);
```

- `LangChain4jQwenClient.detectEmotion` 是简单关键字规则；
- `MockLLMClient.detectEmotion` 也是规则。

目标：

1. 在 **LangChain4jQwenClient.detectEmotion** 中用模型做情绪分类；  
2. 其它实现（Mock/QianWenClient）仍然可以使用规则或空实现，保证灵活切换。

### 4.1 简单版：独立小 Prompt 做情绪分类

在 `LangChain4jQwenClient` 里直接实现：

```java
@Override
public String detectEmotion(String text) {
    if (text == null || text.isEmpty()) {
        return "NORMAL";
    }

    try {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(SystemMessage.from(
                "你是一个情绪分析助手，请根据用户这句话判断情绪。" +
                "只返回 JSON：{ \"emotion\": \"HAPPY|ANGRY|SAD|NEUTRAL\" }，不要输出其他任何文字。"
        ));
        msgs.add(UserMessage.from(text));

        String json = model.chat(msgs).aiMessage().text();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);
        String emotion = node.path("emotion").asText("NEUTRAL").toUpperCase();
        return emotion;
    } catch (Exception e) {
        log.warn("[LLM] 情绪识别失败，回退到规则实现。text={}", text, e);
        // 回退为你原来那套关键字规则，确保不会影响主流程
        return fallbackRuleEmotion(text);
    }
}
```

你原来的规则逻辑可以抽成 `fallbackRuleEmotion(text)` 保留。

### 4.2 进阶版：和 `chat` 共享一次调用结果

如果你觉得“每轮对话多调一次 detectEmotion 的模型”开销有点高，可以做一步优化：

1. 在第 3 节的 `ChatResult` 和 `lastResult` 上扩展：  
   - `chat(...)` 调用 LangChain4j 时就顺带把 `emotion/intent` 算出来；  
   - `detectEmotion` / `detectIntent` 先看 `lastResult`，如果存在就直接用里面的字段。

2. 伪代码：

```java
@Override
public String detectEmotion(String text) {
    return getLastResult()
        .map(ChatResult::getEmotion)
        .orElseGet(() -> fallbackRuleEmotion(text));
}
```

> 这种做法的好处是：对业务侧还是“先调 chat，再调 detectEmotion”，但底层只真正调用了一次大模型。

---

## 5. 阶段三：基于 Redis 的“摘要 + 窗口”记忆

你当前的上下文策略已经很不错：

- Redis List：`chat:context:{sessionId}`，leftPush，最新在前；
- 条数 + 字符长度双重控制：
  - `CONTEXT_LIMIT` 限制消息条数；
  - `trimContextByLength` 根据租户大小动态裁剪总字符。

在此基础上，可以再加一层“**摘要（summary）**”，让长会话信息更稳定：

### 5.1 新增 summary key

为每个会话增加一个 Redis Key：

- `chat:summary:{sessionId}`：存放对当前会话的**整体摘要文本**。

### 5.2 触发摘要的时机

在 `appendContext` 或 `chatWithAi` 结束时，可以根据以下条件触发摘要：

- 当前 Redis 总字符数接近上限，比如超过 70%–80%；  
- 或会话轮次超过某个阈值（如 20 轮）。

### 5.3 用 LangChain4j 生成摘要

可以在 `ChatServiceImpl` 新增一个私有方法：

```java
private void generateAndSaveSummaryIfNeeded(Long sessionId, Long tenantId) {
    // 1. 读取当前 context（listContextFromRedis）
    List<Map<String, String>> ctx = listContextFromRedis(sessionId);
    if (ctx.size() < 10) {
        return; // 太短不需要摘要
    }

    // 2. 构造一个简单的文本：role + content 拼在一起
    StringBuilder sb = new StringBuilder();
    for (Map<String, String> m : ctx) {
        sb.append(m.get("role")).append(": ").append(m.get("content")).append("\n");
    }

    // 3. 调用一个专门的 summarizer（可以通过 LLMClient 或单独 LangChain4j）
    String summary = llmClient.chat(
        Collections.emptyList(),
        "下面是用户和客服的一段对话，请用不超过 200 字总结当前会话的关键信息（用户是谁、在问什么、已给出哪些答案）：\n\n" +
        sb.toString()
    );

    // 4. 写入 Redis: chat:summary:{sessionId}
    stringRedisTemplate.opsForValue().set("chat:summary:" + sessionId, summary);

    // 5. 可选：清理一部分旧 context，只保留最近几条
}
```

> 可以先简单实现为“直接用 `llmClient.chat` 生成摘要”，后面如果你想更专业再单独封装一个 `SummaryLLMClient` 或 Tools。

### 5.4 在 LangChain4jQwenClient.chat 中使用摘要

当你在 `ChatServiceImpl.chatWithAi` 里准备 `List<Message>` 时，可以多读一个 summary：

```java
String summary = stringRedisTemplate.opsForValue().get("chat:summary:" + sessionId);
if (summary != null && !summary.isEmpty()) {
    messages.add(0, new Message("system",
        "这是本次会话目前为止的摘要，请在回答问题时参考这些信息：" + summary));
}
```

然后再追加 Redis 里的最近 N 条原始对话。  
这样你就得到了“摘要 + 窗口”的混合记忆，而不需要在 LangChain4j 那一层做复杂的 ChatMemory。

---

## 6. 阶段四：结合多租户的 RAG 知识库

### 6.1 业务定位

你的系统是多租户客服中台，每个租户都会有自己的：

- 产品 FAQ；
- 业务规则（退款、发票、售后流程）；
- 合同条款等长文档。

RAG 的目标就是：**在回答问题前先检索“这个租户的知识”，让回答更贴合业务**。

### 6.2 最小可行版本：不引入向量库，先用 SQL 检索

先不要急着上向量数据库，建议：

1. 建一张简单的知识表，例如：

```sql
CREATE TABLE tenant_knowledge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  title VARCHAR(255),
  content TEXT NOT NULL,
  create_time DATETIME,
  update_time DATETIME
);
```

2. `TenantKnowledgeService.findRelevant(tenantId, question, limit)`：
   - 最简单：按 `tenant_id` 过滤后，对 `title` / `content` 做 `LIKE` 检索；  
   - 稍进阶：使用 MySQL 全文索引。

3. 在 `ChatServiceImpl.chatWithAi` 里，在调用 `llmClient.chat` 之前插入一段：

```java
List<TenantKnowledge> docs = knowledgeService.findRelevant(tenantId, question, 3);
if (!docs.isEmpty()) {
    String ragContext = docs.stream()
            .map(TenantKnowledge::getContent)
            .collect(Collectors.joining("\n\n"));

    messages.add(0, new Message("system",
        "下面是与你当前租户相关的一些知识，请在回答问题时优先参考这些信息：\n\n" + ragContext));
}

String aiReply = llmClient.chat(messages, question);
```

> 这一阶段**不需要任何额外基础设施**，但效果已经会明显比纯通用 LLM 更贴业务。

### 6.3 第二阶段：引入 LangChain4j Embedding + 向量检索

当你希望进一步提升命中率时，再接入向量检索：

1. 在 `pom.xml` 中引入 Embedding 相关依赖（根据实际版本选择）：

```xml
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-embeddings-open-ai</artifactId>
  <version>...</version>
</dependency>
```

2. 配置 `EmbeddingModel` Bean，类似你现在配置 `OpenAiChatModel` 那样，指向通义千问的 embedding 模型。
3. 选择一个 EmbeddingStore：
   - 可以先用内存 / 文件版做实验；  
   - 稍后接入 JDBC / PGVector / Milvus 等。
4. 在知识导入或编辑时：
   - 取出 `content`，调用 `embeddingModel.embed(content)` 得到向量；  
   - 存入 EmbeddingStore，同时记录 `tenant_id`、`knowledge_id` 等元数据。
5. 在 `chatWithAi` 里：
   - 对 `question` 做 embedding；  
   - 在“当前租户”的向量集合中检索 topK 片段；  
   - 将检索结果拼接为 `ragContext`，以 SystemMessage 形式注入。

> 这一步在代码结构上和前面的 SQL 版 RAG 几乎一样，只是检索方式从 LIKE → 向量最近邻。

---

## 7. 推荐的迭代顺序（结合你现在的实现）

1. **第 1 步：LangChain4jQwenClient.chat JSON 化（结构化输出）**
   - 在类内部增加 `ChatResult` + `parseJson`；  
   - SystemPrompt 要求模型输出 JSON；  
   - 对外仍然返回 `String answer`。

2. **第 2 步：LangChain4j 情绪识别**
   - 在 `detectEmotion` 中用小 Prompt 返回 `{ "emotion": ... }`；  
   - 失败时 fallback 到你现在的规则；  
   - 不改 `ChatServiceImpl.chatWithAi` 的调用方式。

3. **第 3 步：RAG MVP（无向量版）**
   - 建 `tenant_knowledge` 表；  
   - 写 `knowledgeService.findRelevant(tenantId, question, limit)`；  
   - 在 `chatWithAi` 里注入一条 SystemMessage 结合知识内容。

4. **第 4 步：上下文摘要**
   - Redis 增加 `chat:summary:{sessionId}`；  
   - 在上下文过长时用 LLM 生成摘要；  
   - 每轮对话前把摘要作为 SystemMessage 注入。

5. **第 5 步：向量 RAG & 更细粒度意图分类（可选高级阶段）**
   - 接入 EmbeddingModel + EmbeddingStore；  
   - `detectIntent` 也改为 LangChain4j JSON 输出，支持更多意图标签（ORDER_QUERY / AFTER_SALE / TECH_SUPPORT 等）；  
   - 在 B 端看板中逐步增加“按情绪、按意图、按知识命中率”的统计图。

---

## 8. 你可以如何边学边写

1. **先把第 1 步做完**：
   - 只修改 `LangChain4jQwenClient.chat`：SystemPrompt + JSON 解析；  
   - 写一个简单的单元测试 / CommandLineRunner 手动调一两次，看 JSON 格式是否稳定。

2. 确认 Chat 正常工作后，再动 `detectEmotion`，逐步过渡到 LangChain4j 版。  
3. RAG 和摘要都可以先用最小实现（SQL 检索 + 普通 chat 做 summary），等跑通后再考虑向量化和更复杂的 prompt 设计。

这样你每一步都是**在原有架构上小步升级**，不会把现在已经稳定跑通的链路搞乱，同时又能一步步把 LangChain4j 的能力（结构化输出、情绪/意图分类、RAG）叠加进去。













