# 客服对话 + RAG 集成方案

在**既有客服对话**（多轮上下文、Session、Redis、LangChain4jQwenClient）上，**叠加知识库 RAG**：每轮提问时先检索知识库，将「参考资料 + 用户问题」作为本轮 user 消息发给 LLM，同时保留对话历史。前端继续走 `POST /api/customer/chat/ask`，返回 `aiMessage` + `refs`。

---

## 一、目标与约束

- **客服效果**：多轮对话、会话列表、历史消息、情绪识别、负向情绪工单等逻辑保持不变。
- **RAG 效果**：每轮提问时检索知识库，把检索到的资料注入到**本轮**发给 LLM 的 user 消息中；无检索时照常按客服回复。
- **统一 LLM**：全程使用 **LangChain4jQwenClient**，不单独再接 QianWenClient。
- **上下文**：  
  - **对话上下文**：Redis 存的最近多轮 user/assistant，以及可选的 session 摘要。  
  - **RAG 上下文**：本轮的检索结果，仅拼进**本轮** user 消息，不写入 Redis。

---

## 二、整体链路

```
用户提问
  → POST /api/customer/chat/ask { sessionId?, question }
  → CustomerChatController.ask
  → ChatService.chatWithAi(sessionId, userId, tenantId, question)
      1. 会话创建 / 校验（沿用原逻辑）
      2. 用户消息落库（原始 question）
      3. 从 Redis 拉取对话上下文 → List<Message>
      4. （可选）摘要注入 messages
      5. RAG 增强：KnowledgeChatService.buildRagAugment(tenantId, question)
           → 检索 → 拼装「参考资料 + 用户问题」→ RagAugmentResult
      6. LLM：llmClient.chat(messages, rag.augmentedUserMessage)
      7. Redis 追加 user(原始 question)、assistant(aiReply)
      8. 情绪识别、负向情绪工单、AI 消息落库（沿用原逻辑）
      9. 返回 ChatAskResponse { aiMessage, refs }
  → 前端收到 data.aiMessage、data.refs
```

- **发给 LLM 的**：`messages` = 对话历史（含可选摘要），`question` = 本轮 **RAG 增强后的 user 消息**（有检索时是「参考资料 + 用户问题」，否则是原始 `question`）。
- **写入 Redis / DB 的**：始终是**原始** user 问句与 AI 回复，不存长 RAG 块。

---

## 三、涉及文件与改动摘要

### 1. 新增

| 文件 | 说明 |
|------|------|
| `knowledge/dto/RagAugmentResult.java` | `augmentedUserMessage`、`refs`，供 `buildRagAugment` 返回 |
| `chat/dto/ChatAskResponse.java` | `aiMessage`、`refs`，供 ask 接口返回 |

### 2. 修改

| 文件 | 改动 |
|------|------|
| `KnowledgeChatService` | 新增 `RagAugmentResult buildRagAugment(Long tenantId, String question)` |
| `KnowledgeChatServiceImpl` | 实现 `buildRagAugment`：检索 → `buildContext` → 拼装客服用 RAG 提示 → 返回 `RagAugmentResult` |
| `ChatService` | `chatWithAi` 返回类型改为 `ChatAskResponse` |
| `ChatServiceImpl` | 在调用 LLM 前执行 `buildRagAugment`，用 `augmentedUserMessage` 作为本轮 user；返回 `ChatAskResponse` |
| `CustomerChatController` | ask 返回 `ApiResponse<ChatAskResponse>` |
| `LangChain4jQwenClient` | System 提示中增加：当用户消息含【参考资料】时，优先结合资料作答并保持客服语气 |

---

## 四、关键实现说明

### 1. `KnowledgeChatService.buildRagAugment`

- 入参：`tenantId`、`question`。
- 逻辑：  
  - 调用 `KnowledgeSearchService.search(tenantId, question, topK=8, minScore=0.55)`。  
  - 若有结果：`buildContext`（长度上限等沿用现有逻辑）→ 拼装「参考资料 + 用户问题」的**客服向**提示 → `augmentedUserMessage` = 该拼接结果，`refs` = 检索结果转换的 `RagRefDTO` 列表。  
  - 若无结果：`augmentedUserMessage` = 原始 `question`，`refs` 为空。
- 返回值：`RagAugmentResult`。**不调 LLM**，仅做检索与拼装。

### 2. 客服向 RAG 提示（`buildRagAugmentedUserMessageForChat`）

```text
以下是本轮知识库检索到的参考资料。若与用户问题相关，请优先结合这些内容作答，并保持客服语气；若无关或资料不足，则按常规客服方式回复。

【参考资料】
{ buildContext 的结果 }

【用户问题】
{ question }
```

- 有检索时，将整段作为**本轮**发给 LLM 的 user 消息。  
- 无检索时，不包含【参考资料】，即直接使用原始 `question`。

### 3. `ChatServiceImpl.chatWithAi` 中的 RAG 接入点

```java
// 4. RAG 增强
RagAugmentResult rag = knowledgeChatService.buildRagAugment(tenantId, question);
String questionToSend = rag.getAugmentedUserMessage();

// 5. 调用 AI（对话历史 + 本轮增强后的 user 消息）
String aiReply = llmClient.chat(messages, questionToSend);
appendContext(sessionId, "user", question);   // 仍存原始 question
// ... 情绪、落库、append assistant ...

return ChatAskResponse.builder()
    .aiMessage(aiMsg)
    .refs(rag.getRefs() != null ? rag.getRefs() : Collections.emptyList())
    .build();
```

- `messages`：来自 Redis 的对话历史（及可选摘要），不变。  
- `questionToSend`：RAG 增强后的本轮 user 内容；`appendContext` 仍使用原始 `question`，保证后续轮次上下文是正常对话。

### 4. LangChain4jQwenClient 的 System 提示补充

在原有「客服助手 + JSON 输出」说明后，增加一句：

```text
当用户消息中包含【参考资料】时，请优先结合这些资料作答，并保持客服语气；否则按常规客服方式回复。
```

- 有【参考资料】时：偏向知识库内容 + 客服口吻。  
- 无【参考资料】时：与原先纯客服行为一致。

### 5. 前端对接

- 请求：仍为 `POST /api/customer/chat/ask`，body `{ sessionId?, question }`。  
- 响应：`data` 为 `ChatAskResponse`，例如：

```json
{
  "success": true,
  "code": "200",
  "message": "OK",
  "data": {
    "aiMessage": { /* 原 ChatMessage 结构 */ },
    "refs": [ /* RagRefDTO[]，本轮引用，可能为空 */ ]
  }
}
```

- 展示：`data.aiMessage` 照旧渲染为 AI 回复；`data.refs` 可做「本回答依据」等展示（标题、来源、得分等）。

---

## 五、配置与依赖

- **检索 / 向量 / 知识库**：沿用现有 `KnowledgeSearchService`、`EmbeddingService`、`QdrantVectorStore`，无需新增配置。  
- **LLM**：沿用 `LangChain4jQwenClient` 及 `QianWenProperties`。  
- **会话与 Redis**：沿用现有 `chat:context:{sessionId}`、`chat:summary:{sessionId}` 等 key 及 `CONTEXT_LIMIT`、按租户的 context 长度限制。

---

## 六、可选扩展

- **topK / minScore**：当前在 `buildRagAugment` 内写死；可改为从配置、租户或请求参数读取。  
- **refs 使用**：例如按 `documentId`、`chunkId` 跳转原文、高亮来源等。  
- **流式输出**：若后续 LLM 改为流式，仅需在返回 `answer` 的链路上做流式改造，RAG 增强与 `refs` 逻辑可不变。

---

## 七、小结

- **对话上下文**：继续用 Redis + 可选摘要，多轮逻辑不变。  
- **RAG 上下文**：仅在本轮、通过 `buildRagAugment` 生成「参考资料 + 用户问题」，作为本轮 user 消息传入 LLM。  
- **存储**：Redis/DB 只存原始问答，不存 RAG 长文本。  
- **接口**：仍是 `ask`，返回 `aiMessage` + `refs`，前端稍作适配即可同时获得客服对话与 RAG 引用。























