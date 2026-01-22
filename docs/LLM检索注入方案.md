# LLM 检索注入方案（结合当前项目）

## 1. 现状与目标
- 已有能力：文档切分 + 向量化（DashScope text-embedding-v2）+ Qdrant 向量存储（集合 `yulu_chunks`）+ 向量检索（`KnowledgeSearchService.search` 支持租户过滤、得分阈值）。
- 目标：将检索结果安全、高效地注入 LLM Prompt，支持基于检索的对话 / 问答，降低幻觉。

## 2. 总体流程
1) 接收请求：`tenantId` + `userQuestion`（及可选参数 topK、minScore、maxContextTokens）。
2) 向量检索：调用 `KnowledgeSearchService.search(tenantId, question, topK, minScore)`，返回 `RetrievalResultDTO` 列表。
3) 结果过滤与重排：
   - 按得分排序（已有）并再次截断至 topK。
   - 可选：基于置信度/关键词的二次过滤，或按文档聚合后保留每文档前 N 个 chunk。
4) 上下文构造：
   - 归一化字段：title/source/fileType/chunkIndex/chunkText。
   - 聚合同文档相邻的 chunk（`chunk_index` 连续）以减少分片碎片。
   - 长度控制：按字符或 token 粗略限制（如 1200~2000 字符，或估算 token ≈ 字符数 * 0.5）。
   - 去重与裁剪：对内容做去重（按文档+chunk_index），超长时尾部截断并标记 `...`。
5) Prompt 模板注入：
   - System 层：角色、回答风格、不得捏造、引用来源。
   - User 层：原始问题 + 构造好的上下文。
6) 调用 LLM（LangChain4j 或已有 Chat 接口），返回答案。
7) 可选：将检索结果与响应一起记录，便于追踪。

## 3. 关键代码示例
以下示例展示“检索 + 上下文拼装 + Prompt 构造”，供新建服务类参考（命名示例：`KnowledgeChatService`）。

```java
@Service
@RequiredArgsConstructor
public class KnowledgeChatService {
    private static final int DEFAULT_TOP_K = 8;
    private static final double DEFAULT_MIN_SCORE = 0.55;
    private static final int MAX_CONTEXT_CHARS = 2000; // 粗略控制上下文长度

    private final KnowledgeSearchService searchService;
    private final ChatLanguageModel chatModel; // LangChain4j 组件，按你项目实际注入

    public String answer(Long tenantId, String question) {
        // 1) 检索
        List<RetrievalResultDTO> hits = searchService.search(
                tenantId, question, DEFAULT_TOP_K, DEFAULT_MIN_SCORE);

        if (hits == null || hits.isEmpty()) {
            return "未在知识库找到相关内容，请提供更多信息。";
        }

        // 2) 组装上下文
        String context = buildContext(hits, MAX_CONTEXT_CHARS);

        // 3) 构造 Prompt
        String prompt = """
                你是企业内部知识库助手，请基于下方检索到的资料回答。
                - 只根据资料作答，不要编造。
                - 如资料不足，直说“未找到相关信息”。
                - 保留关键信息，回答简洁。

                【用户问题】
                %s

                【检索到的资料】
                %s
                """.formatted(question, context);

        // 4) 调用 LLM
        return chatModel.generate(prompt);
    }

    // 将检索结果转为可读上下文，按长度限制截断
    private String buildContext(List<RetrievalResultDTO> hits, int maxChars) {
        StringBuilder sb = new StringBuilder();
        int remain = maxChars;
        int idx = 1;
        for (RetrievalResultDTO h : hits) {
            String snippet = formatHit(idx++, h);
            if (snippet.length() > remain) {
                break; // 超过长度预算就停止
            }
            sb.append(snippet).append("\n");
            remain -= snippet.length();
        }
        return sb.toString();
    }

    private String formatHit(int order, RetrievalResultDTO h) {
        // 对 chunk 做防护性截断
        String chunk = h.getChunkText();
        int limit = 400;
        if (chunk != null && chunk.length() > limit) {
            chunk = chunk.substring(0, limit) + "...";
        }
        return "[片段#" + order + "] " +
                "docId=" + h.getDocumentId() +
                ", chunkIdx=" + h.getChunkIndex() +
                ", title=" + safe(h.getTitle()) +
                ", source=" + safe(h.getSource()) + "\n" +
                chunk;
    }

    private String safe(String v) { return v == null ? "" : v; }
}
```

> 说明：
> - `ChatLanguageModel` 为 LangChain4j 的接口，若你项目用其他 LLM 客户端，替换调用即可。
> - `DEFAULT_MIN_SCORE` 需结合你的向量模型得分分布调整。
> - 如果需要流式输出，可使用 LangChain4j 的 streaming 接口。

## 4. Prompt 模板建议
- System 层（示例）  
  ```
  你是企业知识库助手，只依据提供的资料回答，禁止编造。
  若资料不足，请直接说明“未找到相关信息”。
  返回用中文，简洁、分点。
  ```
- User 层：拼接用户问题 + 整理后的上下文片段。
- 可选：加入“引用来源编号”要求，例如“请在答案结尾标注使用的片段编号”。

## 5. 上下文构造策略细节
- **去重与聚合**：同文档、相邻 `chunk_index` 的片段可尝试合并；若合并后过长再截断。
- **长度预算**：为防止超出 LLM 上下文，可设 `maxContextChars` 或粗略 token 预算，提前截断。
- **排序与覆盖**：可按得分排序；若同一文档出现多次，可限制“每文档最多 N 个片段”。
- **安全兜底**：当得分全部低于阈值时，直接返回“未找到相关信息”，避免幻觉。

## 6. 集成步骤清单
1) 新增服务类（如 `KnowledgeChatService`），实现“检索 -> 上下文 -> Prompt -> LLM”管道。
2) 在 Controller 暴露对话/问答接口：`POST /api/knowledge/chat`（参数：tenantId, question, topK?, minScore?）。
3) 依赖注入：`KnowledgeSearchService` + LLM 客户端（LangChain4j ChatLanguageModel）。
4) 配置默认参数：topK、minScore、上下文长度、LLM 模型名、温度等。
5) 日志与追踪：记录检索结果与最终回答，便于审核与调优。
6) （可选）流式输出：使用 LangChain4j streaming，在前端按 SSE/WS 返回。

## 7. 后续可优化
- **Rerank**：引入交叉编码器或 BM25 + 向量混排，提升相关性。
- **多模态**：若后续有图片/表格，可在 payload 中增加类型标识并定制处理。
- **召回策略**：在向量搜索失败时，回退关键词搜索或 FAQ 命中。
- **指标**：埋点记录命中率、用户反馈，定期调整 minScore/topK。

---
本方案基于当前代码结构（`ChunkIndexServiceImpl`、`KnowledgeSearchServiceImpl`、Qdrant 向量库）设计，直接添加服务类与 Prompt 组装逻辑即可落地。

## 8. 落地指南（更细节）

### 8.1 Bean 注入与配置
- 需要的 Bean：
  - `KnowledgeSearchService`：现有。
  - `ChatLanguageModel`（LangChain4j）：根据你使用的 LLM（OpenAI / DashScope / 阿里灵积）配置，示例：
    ```java
    @Configuration
    public class LlmConfig {
        @Bean
        public ChatLanguageModel chatModel(
                @Value("${llm.api-key}") String apiKey,
                @Value("${llm.base-url}") String baseUrl,
                @Value("${llm.model-name}") String model) {
            return OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)   // 可选
                    .modelName(model)
                    .temperature(0.2)
                    .build();
        }
    }
    ```
  - 若使用 DashScope/Lingji，替换为对应的 LangChain4j 适配器配置。

### 8.2 Chat 服务类放置与注入
- 新建 `com.ityfz.yulu.knowledge.service.impl.KnowledgeChatService`（或 `common.ai` 下皆可），`@Service` 标注。
- 通过构造器注入：
  ```java
  @Service
  @RequiredArgsConstructor
  public class KnowledgeChatService {
      private final KnowledgeSearchService searchService;
      private final ChatLanguageModel chatModel;
      ...
  }
  ```

### 8.3 Controller 示例
```java
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeChatController {

    private final KnowledgeChatService chatService;

    @PostMapping("/chat")
    public String chat(@RequestParam Long tenantId,
                       @RequestParam String question,
                       @RequestParam(required = false, defaultValue = "8") Integer topK,
                       @RequestParam(required = false, defaultValue = "0.55") Double minScore) {
        return chatService.answer(tenantId, question, topK, minScore);
    }
}
```

### 8.4 Chat 服务完整示例（含参数透传）
```java
@Service
@RequiredArgsConstructor
public class KnowledgeChatService {
    private static final int DEFAULT_MAX_CONTEXT_CHARS = 2000;

    private final KnowledgeSearchService searchService;
    private final ChatLanguageModel chatModel;

    public String answer(Long tenantId, String question, Integer topK, Double minScore) {
        int k = (topK == null || topK <= 0) ? 8 : topK;
        double score = (minScore == null) ? 0.55 : minScore;

        List<RetrievalResultDTO> hits = searchService.search(tenantId, question, k, score);
        if (hits == null || hits.isEmpty()) {
            return "未在知识库找到相关内容，请提供更多信息。";
        }

        String context = buildContext(hits, DEFAULT_MAX_CONTEXT_CHARS);
        String prompt = buildPrompt(question, context);
        return chatModel.generate(prompt);
    }

    private String buildPrompt(String question, String context) {
        return """
                你是企业内部知识库助手，只依据下方资料回答。
                约束：
                - 不要编造。如果资料不足，请回答“未找到相关信息”。
                - 回答使用中文，简洁分点。
                - 若引用内容，尽量提及片段编号。

                【用户问题】
                %s

                【检索到的资料】
                %s
                """.formatted(question, context);
    }

    private String buildContext(List<RetrievalResultDTO> hits, int maxChars) {
        // 可先按 docId 分组、按 chunkIndex 聚合相邻片段，再做长度裁剪
        StringBuilder sb = new StringBuilder();
        int remain = maxChars;
        int order = 1;
        for (RetrievalResultDTO h : hits) {
            String snippet = formatHit(order++, h);
            if (snippet.length() > remain) break;
            sb.append(snippet).append("\n");
            remain -= snippet.length();
        }
        return sb.toString();
    }

    private String formatHit(int order, RetrievalResultDTO h) {
        String chunk = h.getChunkText();
        int limit = 400; // 防止单段过长
        if (chunk != null && chunk.length() > limit) {
            chunk = chunk.substring(0, limit) + "...";
        }
        return "[片段#" + order + "] " +
                "docId=" + h.getDocumentId() +
                ", chunkIdx=" + h.getChunkIndex() +
                ", title=" + safe(h.getTitle()) +
                ", source=" + safe(h.getSource()) + "\n" +
                chunk;
    }

    private String safe(String v) { return v == null ? "" : v; }
}
```

### 8.5 Prompt 细化模板（可直接落地）
- **System 侧（可作为前置指令）**：
  ```
  你是企业知识库助手，只使用提供的资料回答。
  资料不足时直接说“未找到相关信息”，不要编造。
  回答使用中文，简洁分点，可附片段编号。
  ```
- **User 侧**：
  ```
  【用户问题】
  %s
  【检索资料】
  %s
  ```
- **可选扩展**：
  - 要求输出引用：“请在回答结尾标注使用的片段编号，如 [#1][#3]”。
  - 控制风格/格式：“每个要点前加 ‘-’；限制 150 字内。”。

### 8.6 上下文构造高级策略
- **分组聚合**：同一 `documentId` 下，按 `chunk_index` 连续段合并，再截断。
- **长度预算**：按字符粗略估算 tokens（字符数 * 0.5），超出即停止添加片段。
- **置信度过滤**：在 `minScore` 之上再做“低于 X 不入场”，避免低相关度噪声。
- **每文档限额**：同一文档最多取 N 个片段，防止单文档垄断上下文。
- **去重**：基于 `documentId + chunkIndex` 做去重，防止重复。

### 8.7 流式与非流式
- 非流式：示例代码中的 `chatModel.generate(prompt)`。
- 流式：若需要 SSE/WS，可用 LangChain4j streaming 接口，将 token 增量推送前端。

### 8.8 常见故障排查
- 检索为空：检查 `minScore` 是否过高，或向量维度/集合名匹配。
- 上下文过长：调整 `MAX_CONTEXT_CHARS` 或分文档限额。
- 回答幻觉：加强 System 约束，空结果时直接返回“不足”。


