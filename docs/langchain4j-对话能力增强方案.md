## 1. 先对齐一下你当前的实现

你现在后端已经有了比较完整的一套 LLM 抽象和 LangChain4j 接入：

- **统一接口 `LLMClient`**（`com.ityfz.yulu.common.ai.LLMClient`）  
  - `String chat(List<Message> context, String question)`：带上下文对话；  
  - `String detectIntent(String text)`：意图识别（目前实现较简单 / mock）；  
  - `String detectEmotion(String text)`：情绪识别（当前是规则或 mock）。

- **多个实现**：  
  - `MockLLMClient`：关键字规则，用于本地快速联调。  
  - `QianWenClient`：直接调用 DashScope HTTP 接口（标准 / 兼容模式都有）。  
  - `LangChain4jQwenClient`：基于 `OpenAiChatModel` 的 LangChain4j 实现，目前只实现了 `chat`，`detectEmotion` 仍然是规则实现。

- **业务使用点：`ChatServiceImpl.chatWithAi(...)`**  
  1. 根据 `tenantId` 设置 `TenantContextHolder`。  
  2. 确保 `ChatSession` 存在（`createSessionIfNotExists`）。  
  3. 把本轮用户问题写入 MySQL（`chat_message`）+ Redis 上下文（`appendContext(sessionId, "user", question)`）。  
  4. 从 Redis 中取出最近 N 条上下文（有**条数 + 字符长度**双重裁剪）。  
  5. 把 Redis `Map(role, content)` 转成 `List<Message>` 传给 `llmClient.chat(context, question)`。  
  6. 用 `llmClient.detectEmotion(question)` 做情绪识别，触发 MQ 事件 / 工单。  
  7. 把 AI 回复写回 MySQL + Redis。

可以看到：**架构上你已经做得很好**：

- LLM 抽象已经存在；  
- LangChain4j 已经对接到具体实现里；  
- Redis 上下文策略也已经考虑了“最近 N 条 + 字符长度分层（小租户/大租户）”；  
- 负面情绪会通过 MQ 事件串联到工单系统。

下面的方案就不再重复这些，而是站在你现有实现之上，做“**升级版 LangChain4j 对话能力**”。
## 2. 改造目标（基于现有 LLMClient）

1. **强化 LangChain4jQwenClient**：  
   - 仍然通过 `LLMClient.chat(...)` 统一出口；  
   - 但在内部用 LangChain4j 实现更丰富的能力（例如 JSON 结构输出、函数调用）。

2. **把情绪识别改成“模型能力”**：  
   - 逐步淘汰规则版 `detectEmotion`；  
   - 用 LangChain4j 调用小 Prompt（或工具）做情绪分类，并与 MQ/工单链路对齐。

3. **完善上下文策略**：  
   - Redis 里已经有“最近 N 条 + 字符长度裁剪”，在此基础上：  
   - 可以调整结构为“Summary + 最近对话窗口”，给长会话更好表现。

4. **引入 RAG 知识库**：  
   - 新增按租户维护的知识表和向量存储；  
   - 在 `chatWithAi` 里先做检索，把结果注入到 `LLMClient.chat` 的 context 里。  

整个过程中：**`ChatServiceImpl.chatWithAi` 的接口形态尽量保持不变**，以减少对前端与其他后端调用的影响。

---

## 4. 步骤二：接入 LangChain4j 的基础模型

### 4.1 引入依赖（写在文档中，实际按需添加）

`pom.xml` 里（示例）：

```xml
<dependencies>
    <!-- LangChain4j 核心 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>0.34.0</version>
    </dependency>

    <!-- OpenAI 兼容模型（包括通义千问兼容模式） -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>0.34.0</version>
    </dependency>
</dependencies>
```

> 版本号以你实际时间点为准，可以查一下最新稳定版。

### 4.2 配置 `ChatLanguageModel` Bean

假设你用的是 **通义千问 OpenAI 兼容接口**：

```java
@Configuration
public class Lc4jConfig {

    @Bean
    public ChatLanguageModel qwenChatModel(@Value("${ai.qianwen.base-url}") String baseUrl,
                                           @Value("${ai.qianwen.api-key}") String apiKey,
                                           @Value("${ai.qianwen.model}") String model) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)  // 兼容地址，如 https://dashscope.aliyuncs.com/compatible/v1
                .apiKey(apiKey)
                .modelName(model)  // qwen-turbo 等
                .temperature(0.2)
                .build();
    }
}
```

### 4.3 新增 `LangChain4jQwenClient` 实现 `AiChatClient`

```java
@Component
public class LangChain4jQwenClient implements AiChatClient {

    private final ChatLanguageModel chatModel;

    public LangChain4jQwenClient(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ChatMessage chat(Long tenantId,
                            Long userId,
                            Long sessionId,
                            String question,
                            List<ChatMessage> contextMessages) {
        List<dev.langchain4j.data.message.ChatMessage> lcMessages = new ArrayList<>();

        // 1. 固定一个 system 提示词
        lcMessages.add(SystemMessage.from("你是一个多租户智能客服助手，请用简洁、友好的语气回答用户问题。"));

        // 2. 把历史消息映射为 LangChain4j 消息
        for (ChatMessage msg : contextMessages) {
            if ("USER".equals(msg.getSenderType())) {
                lcMessages.add(UserMessage.from(msg.getContent()));
            } else {
                lcMessages.add(AiMessage.from(msg.getContent()));
            }
        }

        // 3. 当前用户问题
        lcMessages.add(UserMessage.from(question));

        // 4. 调用模型
        dev.langchain4j.data.message.AiMessage aiMessage =
                chatModel.generate(lcMessages).content().aiMessage();

        // 5. 转换回你项目的 ChatMessage（只填必要字段）
        ChatMessage aiReply = new ChatMessage();
        aiReply.setTenantId(tenantId);
        aiReply.setSessionId(sessionId);
        aiReply.setSenderType("AI");
        aiReply.setContent(aiMessage.text());
        // emotion 字段后面由情绪识别能力填充

        return aiReply;
    }
}
```

> 这一步的关键是：**LangChain4j 只负责“生成一条 AI 回复”**，其它（会话管理、持久化）仍由 `ChatService` 负责。

---

## 5. 步骤三：上下文（会话记忆）策略设计

你现在的上下文是：
- 所有消息都保存在 `chat_message` 表；
- 每次问答前，`ChatService` 自己从 DB 读“最近 N 条”。

这本身没问题，但随着对话变长，有几个要点需要策略：

1. **只取最近 N 条**（例如 10～20），控制 token 数。  
2. **优先保留用户消息 + 重要 AI 回复**，不需要所有闲聊。  
3. 长会话时，可以定期做“**对话总结**”，替换掉早期细节。  

### 5.1 简单版：最近 N 条消息（推荐先实现）

在 `ChatService.loadRecentMessages(sessionId)` 中：

```java
public List<ChatMessage> loadRecentMessages(Long sessionId) {
    if (sessionId == null) {
        return Collections.emptyList();
    }
    // 按 create_time 倒序取最近 20 条，再反转成时间正序
    List<ChatMessage> list = chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, sessionId)
                    .orderByDesc(ChatMessage::getCreateTime)
                    .last("LIMIT 20")
    );
    Collections.reverse(list);
    return list;
}
```

先用这个简单策略跑起来，后续再加“总结能力”。

### 5.2 进阶：LangChain4j 的 ChatMemory（可选）

LangChain4j 自带内存组件，例如 `MessageWindowChatMemory`：

```java
ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

AiMessage ai = chatModel.generate(userMessage("你好"), memory);
```

问题是它默认是**内存级别**，不带 sessionId、多租户、持久化。  
你可以用它来封装**“窗口长度控制”**，但真实数据仍来源于 DB。

一种折衷是：

1. DB 里取出最近 N 条历史。  
2. 放进 `MessageWindowChatMemory`，让它自动裁剪。  
3. 再把 memory 中的消息传入 `chatModel.generate`。  

不过在现阶段，你完全可以只用“最近 N 条消息 + DB”，先完成可用版本。

---

## 6. 步骤四：把情绪识别做成模型能力

你现在的 `ChatMessage` 里有 `emotion` 字段（NORMAL / 其他）。  
目前大概率是：
- 要么没用；
- 要么在业务层用规则写死。

### 6.1 方案选择

两种路径：

1. **Prompt 工程**：在生成答复时，请模型顺带给出情绪标签（JSON 格式）。  
2. **Tool / 函数调用**：单独给出一个“情绪分析工具”，模型在需要时调用（LangChain4j 的 Tools 机制）。  

对你现在的需求，**方案 1 更简单**，易于落地。

### 6.2 Prompt 中携带结构化情绪要求

在 `LangChain4jQwenClient.chat(...)` 中，不直接生成纯文本，而是要求模型返回 JSON：

```java
SystemMessage system = SystemMessage.from(
        "你是一个客服 AI 助手。回答用户问题时，请返回 JSON：" +
        "{ \"answer\": \"...\", \"emotion\": \"HAPPY|ANGRY|SAD|NEUTRAL\" }。" +
        "只返回 JSON，不要包含多余文字。"
);
```

调用后解析：

```java
AiMessage rawAi = chatModel.generate(lcMessages).content().aiMessage();
String json = rawAi.text();

ObjectMapper mapper = new ObjectMapper();
JsonNode node = mapper.readTree(json);
String answer = node.path("answer").asText();
String emotion = node.path("emotion").asText("NEUTRAL");

ChatMessage aiReply = new ChatMessage();
aiReply.setContent(answer);
aiReply.setEmotion(emotion.toUpperCase());
```

这样：
- 前端展示时可以根据 `emotion` 加 Tag（你已经有基础能力）。  
- 后续 B 端统计“情绪分布”也很方便。

> 小技巧：如果担心模型不守规矩，可以在解析失败时 fallback 为 `NORMAL`，并记录日志。

---

## 7. 步骤五：引入 RAG 知识库

### 7.1 业务目标

按你的“多租户智能客服中台”设想：
- 不同租户有自己的 FAQ / 文档；
- C 端提问时，AI 先从该租户的知识库检索，再结合问题回答。

### 7.2 最小可行方案（MVP）

**先不急着上向量数据库**，可以：
1. 使用 LangChain4j 内置的 **JDBC 文档存储 + Embedding 模型**；
2. 把每个租户的知识条目（question / answer / 文档段落）做成记录，存到一张表：`tenant_knowledge`；
3. 用 LangChain4j 的 `EmbeddingStoreIngestor` 把文本转 embedding 存到表里；
4. 每次聊天时：
   - 用当前问题做 embedding；
   - 在对应租户的向量集合里做最近邻检索；
   - 把检索到的文本作为 `SystemMessage` 或 `UserMessage` 的一部分，喂给模型。

### 7.3 表结构示例

```sql
CREATE TABLE tenant_knowledge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  title VARCHAR(255),
  content TEXT NOT NULL,
  embedding VARBINARY(4096), -- 具体长度视 embedding 模型而定
  create_time DATETIME,
  update_time DATETIME
);
```

> 若使用 LangChain4j 官方的 JDBC EmbeddingStore，可以直接复用其建表语句，这里只是示意。

### 7.4 代码轮廓：构建 RAG Chain

1. 配置 Embedding 模型：

```java
@Bean
public EmbeddingModel embeddingModel(@Value("${ai.qianwen.api-key}") String apiKey) {
    return OpenAiEmbeddingModel.builder()
            .baseUrl("...") // 兼容地址
            .apiKey(apiKey)
            .modelName("text-embedding-xxx")
            .build();
}
```

2. 配置按租户分片的 EmbeddingStore（可以在代码里带上 tenantId 作为前缀 / 过滤条件）。

3. 在 `ChatService.chatWithAi(...)` 中：

```java
// 1. 用 embeddingModel 为 question 生成向量
Embedding questionEmbedding = embeddingModel.embed(question).content();

// 2. 在当前租户的知识库里检索 topK 段落
List<TextSegment> relevant = embeddingStore.findRelevant(questionEmbedding, 5);

// 3. 把这些段落拼成一个 context 文本
String ragContext = relevant.stream()
        .map(TextSegment::text)
        .collect(Collectors.joining("\n\n"));

// 4. 在 System / UserMessage 中加入：
lcMessages.add(SystemMessage.from(
    "下面是与你所在租户相关的知识库内容，请在回答问题时优先参考这些信息：\n\n" + ragContext
));
```

> 初期可以只支持“FAQ 短文档”，等跑通后再引入文档上传、切片等流程。

### 7.5 多租户隔离注意点

1. 所有知识库记录都必须带 `tenant_id`。  
2. EmbeddingStore 层的查询要以 `tenant_id` 为过滤条件。  
3. 若后面上了独立向量库（如 Milvus / PGVector），每个租户可以用：
   - 独立 collection；或  
   - 共享 collection + `tenant_id` 过滤。

---

## 8. 步骤六：渐进式上线与验证路径

建议按照下面的顺序来边学边写：

1. **第 1 阶段：LangChain4j 基础替换**
   - 抽象 `AiChatClient`。
   - 接入 `LangChain4jQwenClient`，保持上下文策略不变（仍然“最近 N 条 DB 消息”）。
   - 回归测试：C 端聊天、B 端会话查看是否正常，情绪先固定为 NORMAL。

2. **第 2 阶段：情绪识别能力**
   - 在 LangChain4j 调用中加入 JSON 输出约定。  
   - 解析 `emotion`，填充到 `ChatMessage.emotion`。  
   - 前端 C/B 端把情绪用 Tag 高亮（你已经有一部分逻辑）。

3. **第 3 阶段：RAG MVP**
   - 建好 `tenant_knowledge` 表。  
   - 写一段单独的“知识导入脚本”（可以是一个 Spring Boot CommandLineRunner），把种子 FAQ 存进去并生成 embedding。  
   - 在 `chatWithAi` 中串联检索 + 上下文注入。  
   - 验证：在知识库中存在答案的问题，AI 回答应明显更贴近业务。  

4. **第 4 阶段：后台管理与可视化**
   - B 端增加“知识库管理”菜单（列表 / 新增 / 编辑）。  
   - 每次增删改知识条目时，自动生成 / 更新 embedding。  
   - 会话管理页增加按情绪筛选、统计图表（利用 `emotion` 字段）。

---

## 9. 你可以如何边学边写

1. **先从接口开始**：把 `AiChatClient` 抽出来，`ChatService` 只依赖接口。  
2. **在 docs 里起一个 “LangChain4j 实战笔记”**，照着本方案，每完成一步就记一节，方便回顾。  
3. **逐步替换**：
   - 先写 `LangChain4jQwenClient`，在配置里把它注入为 `AiChatClient` 的默认实现；
   - 保持 `QianWenClient` 不删，必要时可以快速切回；
   - RAG 与情绪识别都可以在 LangChain4j 客户端内部逐步增强，不影响控制层。

如果你愿意，下一步我可以按照这个文档里的顺序，先带你把 **`AiChatClient` + LangChain4jQwenClient + 最近 N 条上下文** 这一小步真正落到代码里，再一起迭代 RAG 与情绪识别。


