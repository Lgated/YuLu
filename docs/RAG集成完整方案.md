# 知识库 RAG 集成完整方案

基于你已有的 **Chunk 切分、索引、检索** 能力，本方案补全 **RAG 对话** 全链路，包括 Controller、Service、DTO 及调用方式。

---

## 一、整体链路

```
用户提问
  → POST /api/admin/knowledge/chat { "question": "..." }
  → KnowledgeRagController.chat
  → KnowledgeChatService.ragChat(tenantId, request)
  → 1. KnowledgeSearchService.search(tenantId, question, topK, minScore)  // 向量检索
  → 2. buildContext(hits)  // 拼装检索结果为上下文
  → 3. buildRagUserMessage(question, context)  // 构造 RAG 用户消息
  → 4. LLMClient.chat(messages, "")  // 调用 QianWen，此处用 qianWenClient
  → 5. 返回 RagChatResponse { answer, refs }
```

- **租户**：从 JWT 解析 `tenantId`（`SecurityUtil.currentTenantId()`），检索与回答均按租户隔离。
- **LLM**：RAG 使用 `qianWenClient`，与客服场景的 `langChain4jQwenClient` 分离，避免 JSON 约束影响 RAG 回答。

---

## 二、已新增 / 修改的文件

### 1. DTO

| 文件 | 说明 |
|------|------|
| `knowledge/dto/RagChatRequest.java` | 请求体：`question`（必填）、`topK`、`minScore` |
| `knowledge/dto/RagRefDTO.java` | 单条引用：`documentId`、`chunkId`、`title`、`source`、`score` 等 |
| `knowledge/dto/RagChatResponse.java` | 响应：`answer`、`refs` |

### 2. Service

| 文件 | 说明 |
|------|------|
| `KnowledgeChatService` | 接口改为 `RagChatResponse ragChat(Long tenantId, RagChatRequest request)` |
| `KnowledgeChatServiceImpl` | 实现检索 → 上下文 → Prompt → 调用 `qianWenClient`，返回 `RagChatResponse` |

### 3. Controller

| 文件 | 说明 |
|------|------|
| `KnowledgeRagController` | `POST /api/admin/knowledge/chat`，校验登录后调用 `KnowledgeChatService.ragChat` |

---

## 三、接口说明

### `POST /api/admin/knowledge/chat`

- **鉴权**：需在 Header 中带 `Authorization: Bearer <JWT>`，与现有 `/api/admin/*` 一致。
- **请求体**：

```json
{
  "question": "用户问题（必填）",
  "topK": 8,
  "minScore": 0.55
}
```

- **响应**：

```json
{
  "success": true,
  "code": "200",
  "message": "OK",
  "data": {
    "answer": "模型基于知识库生成的回答",
    "refs": [
      {
        "documentId": 1,
        "chunkId": 10,
        "chunkIndex": 0,
        "title": "文档标题",
        "source": "用户上传",
        "fileType": "txt",
        "score": 0.82
      }
    ]
  }
}
```

- **无检索结果**：`answer` 为固定提示（如「未在知识库找到与当前问题相关的内容...」），`refs` 为空数组。
- **问题为空**：校验失败，返回 `VALIDATION_ERROR`。

---

## 四、核心逻辑摘要

### 1. 检索与上下文

- 使用现有 `KnowledgeSearchService.search(tenantId, query, topK, minScore)`。
- 对检索结果做长度控制（如总上下文约 4000 字、单条 chunk 约 500 字），避免超出模型限制。

### 2. RAG 用户消息（发给 LLM 的 Prompt）

单条 `user` 消息，内容形如：

```
你是企业内部知识库助手。请严格基于下方【检索到的资料】回答用户的【问题】。
要求：
1. 仅根据资料内容作答，不要编造资料中不存在的信息。
2. 若资料不足以回答问题，请明确说明“根据现有资料无法回答”。
3. 回答简洁、有条理，可适当分点。

【检索到的资料】
[片段#1] 文档《xxx》...
[片段#2] ...

【用户问题】
用户输入的问题
```

### 3. LLM 调用

- `KnowledgeChatServiceImpl` 注入 `@Qualifier("qianWenClient") LLMClient`。
- 构造 `messages = [ new Message("user", userMessage) ]`，再 `llmClient.chat(messages, "")`。
- QianWen 客户端仅使用 `messages`，不追加 `question`，因此 RAG 把完整说明 + 资料 + 问题都放在一条 `user` 消息里。

---

## 五、依赖与配置

- **Spring**：`KnowledgeRagController`、`KnowledgeChatServiceImpl` 受组件扫描管理，无需额外配置。
- **QianWen**：复用现有 `QianWenProperties`（`baseUrl`、`apiKey`、`model`），RAG 与客服共用同一套配置。
- **检索 / 向量**：复用现有 `KnowledgeSearchService`、`EmbeddingService`、`QdrantVectorStore`。

---

## 六、调用示例

### cURL

```bash
curl -X POST 'http://localhost:8080/api/admin/knowledge/chat' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer YOUR_JWT_TOKEN' \
  -d '{"question": "请问退换货政策是什么？", "topK": 6, "minScore": 0.5}'
```

### Postman

1. 方法：`POST`
2. URL：`http://localhost:8080/api/admin/knowledge/chat`
3. Headers：`Content-Type: application/json`，`Authorization: Bearer <token>`
4. Body → raw → JSON：

```json
{
  "question": "请问退换货政策是什么？"
}
```

---

## 七、可扩展点

1. **多轮 RAG**：在 `RagChatRequest` 中增加 `history: List<Message>`，在 `KnowledgeChatServiceImpl` 中把 `history` 与当前 RAG 用户消息一起传入 `llmClient.chat`（需保证 QianWen 传参格式支持多轮）。
2. **流式输出**：若 QianWen 支持 SSE，可新增流式接口，逐步返回 `answer`。
3. **引用高亮**：前端根据 `refs` 中的 `documentId`、`chunkId` 等定位原文，做高亮或来源展示。
4. **参数可调**：`topK`、`minScore`、上下文长度等可从配置或管理后台读取，按租户或场景区分。

---

## 八、故障排查

| 现象 | 可能原因 | 建议 |
|------|----------|------|
| 401 / 未登录 | 未带 JWT 或 token 无效 | 检查 `Authorization` 头，先调 `/api/admin/auth/login` 取 token |
| `TENANT_REQUIRED` | JWT 中无 `tenantId` 或未解析 | 确认登录接口写入的 claims 含 `tenantId` |
| 回答与知识库无关 | 检索结果不准或 `minScore` 过低 | 调高 `minScore`，或检查 embedding / 索引质量 |
| 回答明显乱编 | 未严格按资料作答 | 调整 RAG 提示词，强调“仅根据资料”“不得编造” |
| LLM 报错 | 通义千问 API 限流、Key 无效、网络异常 | 查看 `KnowledgeChatServiceImpl` 及 QianWen 客户端日志 |

按上述链路与接口调用即可完成从 **检索 → 上下文 → LLM → 返回** 的完整 RAG 集成。

