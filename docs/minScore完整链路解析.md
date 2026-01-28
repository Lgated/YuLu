# minScore 完整链路解析

深入解析 `minScore`（最小相似度阈值）在整个 RAG 检索链路中的作用、计算方式和调优策略。

---

## 一、整体链路图

```
用户问题："那满三年后是多少天？"
    ↓
[1] EmbeddingService.embed(question)
    → 将文本转换为向量（1536维 Float 数组）
    → 例如：[0.123, -0.456, 0.789, ...] (1536个浮点数)
    ↓
[2] QdrantVectorStore.search(collection, queryVector, topK, filter)
    → 在 Qdrant 中执行向量相似度搜索
    → 使用 Cosine 相似度算法计算
    → 返回 topK 个结果（按相似度降序）
    ↓
[3] Qdrant 返回 List<ScoredPoint>
    → 每个 ScoredPoint 包含：
       - score: 相似度分数（0.0 ~ 1.0，Cosine 相似度）
       - payload: 元数据（documentId, chunkId, title, chunkText 等）
    ↓
[4] KnowledgeSearchServiceImpl.search()
    → 对 Qdrant 返回的结果进行二次处理：
       a) 按 score 降序排序（确保最高分在前）
       b) 应用 minScore 过滤：filter(p -> p.getScore() >= minScore)
       c) 转换为 RetrievalResultDTO
    ↓
[5] KnowledgeChatServiceImpl.buildRagAugment()
    → 接收过滤后的结果
    → 构建上下文字符串
    → 拼装 RAG Prompt
    ↓
[6] LLM 生成回答
```

---

## 二、minScore 的作用位置

### 2.1 代码位置

**位置1：KnowledgeSearchServiceImpl.search()**（第50行）
```java
return points.stream()
    .sorted(Comparator.comparingDouble(Points.ScoredPoint::getScore).reversed())
    .filter(p -> p.getScore() >= minScore)  // ← minScore 在这里生效
    .map(this::toDTO)
    .collect(Collectors.toList());
```

**位置2：KnowledgeChatServiceImpl.buildRagAugment()**（第107行）
```java
double minScore = 0.35;  // ← 在这里设置默认值
List<RetrievalResultDTO> hits = searchService.search(tenantId, question.trim(), topK, minScore);
```

### 2.2 作用时机

```
Qdrant 返回结果（可能包含低分结果）
    ↓
二次排序（按 score 降序）
    ↓
minScore 过滤 ← 在这里过滤掉低分结果
    ↓
转换为 DTO
    ↓
返回给 buildRagAugment
```

**关键点**：
- Qdrant 本身**不应用 minScore**，它只返回 topK 个最相似的结果
- minScore 是在**应用层**（KnowledgeSearchServiceImpl）进行过滤
- 过滤发生在**排序之后、DTO 转换之前**

---

## 三、相似度分数（Score）的计算原理

### 3.1 Cosine 相似度

Qdrant 使用 **Cosine 相似度**（余弦相似度）计算向量之间的相似度。

**公式**：
```
cosine_similarity(A, B) = (A · B) / (||A|| × ||B||)
```

其中：
- `A · B`：两个向量的点积（内积）
- `||A||`：向量 A 的模长（L2 范数）
- `||B||`：向量 B 的模长

**结果范围**：`-1.0 ~ 1.0`，但在文本向量场景中通常为 `0.0 ~ 1.0`（因为 embedding 向量通常都是非负或归一化的）

### 3.2 实际计算过程

**步骤1：问题向量化**
```java
// 用户问题："那满三年后是多少天？"
List<Float> queryVector = embeddingService.embed("那满三年后是多少天？");
// 结果：[0.123, -0.456, 0.789, ..., 0.234] (1536维)
```

**步骤2：知识库中的向量**
```java
// 知识库 chunk："员工入职满一年后可享受带薪年假15天，满三年后年假增至20天。"
// 这个 chunk 在索引时已经被向量化并存入 Qdrant
// 存储的向量：[0.145, -0.432, 0.801, ..., 0.198] (1536维)
```

**步骤3：Qdrant 计算相似度**
```java
// Qdrant 内部计算：
score = cosine_similarity(queryVector, chunkVector)
// 例如：score = 0.42
```

**步骤4：返回 topK 结果**
```java
// Qdrant 返回 topK=8 个最相似的结果，按 score 降序：
[
  ScoredPoint(score=0.82, chunk="年假每年15天..."),      // 最相关
  ScoredPoint(score=0.75, chunk="工作时间是..."),
  ScoredPoint(score=0.68, chunk="请假流程..."),
  ScoredPoint(score=0.55, chunk="满三年后年假增至20天..."),  // 相关但分数较低
  ScoredPoint(score=0.42, chunk="其他内容..."),          // 可能不相关
  ScoredPoint(score=0.35, chunk="无关内容..."),          // 不相关
  ScoredPoint(score=0.28, chunk="完全无关..."),          // 完全不相关
  ScoredPoint(score=0.15, chunk="噪音数据...")           // 噪音
]
```

**步骤5：应用 minScore 过滤**
```java
// 如果 minScore = 0.35
filter(p -> p.getScore() >= 0.35)
// 结果：保留 score >= 0.35 的项
// 过滤后：
[
  ScoredPoint(score=0.82, ...),  // ✅ 保留
  ScoredPoint(score=0.75, ...),  // ✅ 保留
  ScoredPoint(score=0.68, ...),  // ✅ 保留
  ScoredPoint(score=0.55, ...),  // ✅ 保留
  ScoredPoint(score=0.42, ...),  // ✅ 保留
  ScoredPoint(score=0.35, ...),  // ✅ 保留（边界值）
  // ScoredPoint(score=0.28, ...)  // ❌ 被过滤
  // ScoredPoint(score=0.15, ...)  // ❌ 被过滤
]
```

---

## 四、为什么需要 minScore？

### 4.1 问题场景

**没有 minScore 的情况**：
```
用户问题："那满三年后是多少天？"
Qdrant 返回 topK=8：
  - score=0.42: "满三年后年假增至20天"  ← 正确答案，但分数不高
  - score=0.35: "其他福利政策..."
  - score=0.28: "完全无关的内容..."     ← 噪音
  - score=0.15: "噪音数据..."          ← 噪音
```

如果不过滤，这些低分结果会被：
1. 拼接到 RAG Prompt 中
2. 传给 LLM
3. LLM 可能被噪音干扰，回答不准确

### 4.2 minScore 的作用

**作用1：过滤噪音**
- 低分结果通常是**不相关**的内容
- 过滤后只保留**真正相关**的知识库片段

**作用2：提高回答质量**
- 减少 LLM 的干扰信息
- 让 LLM 专注于高质量的相关内容

**作用3：控制上下文长度**
- 过滤低分结果后，上下文更短
- 节省 Token，降低 LLM 调用成本

---

## 五、minScore 的调优策略

### 5.1 当前设置

```java
// KnowledgeChatServiceImpl.buildRagAugment()
double minScore = 0.35;  // 默认值
```

### 5.2 不同 minScore 值的影响

| minScore | 效果 | 适用场景 | 风险 |
|----------|------|----------|------|
| **0.2** | 非常宽松，几乎不过滤 | 知识库内容少，需要尽可能多的结果 | 可能混入大量噪音 |
| **0.3** | 较宽松 | 简短问题、代词问题 | 可能混入少量噪音 |
| **0.35** | **当前设置**，平衡 | 大多数场景 | 平衡准确率和召回率 |
| **0.4** | 较严格 | 问题表述清晰、关键词明确 | 可能漏掉相关但分数稍低的结果 |
| **0.5** | 严格 | 问题非常明确，需要高精度 | 可能漏掉相关结果 |
| **0.55** | 非常严格 | 只接受高度相关的结果 | 容易漏掉相关结果（如"那满三年后是多少天？"） |
| **0.6+** | 极严格 | 特殊场景，需要极高精度 | 可能过滤掉所有结果 |

### 5.3 调优建议

#### 场景1：简短问题或代词问题
```java
// 问题："那满三年后是多少天？"（9字，含代词）
// 建议：minScore = 0.3 ~ 0.35
double minScore = 0.35;
```

#### 场景2：问题表述清晰
```java
// 问题："星云科技的工作时间是什么？"（明确、关键词多）
// 建议：minScore = 0.4 ~ 0.45
double minScore = 0.4;
```

#### 场景3：知识库内容较少
```java
// 知识库只有少量文档，需要尽可能召回
// 建议：minScore = 0.3
double minScore = 0.3;
```

#### 场景4：知识库内容很多
```java
// 知识库内容丰富，需要高精度
// 建议：minScore = 0.4 ~ 0.5
double minScore = 0.45;
```

### 5.4 动态调整策略（可选实现）

```java
private double calculateMinScore(String question) {
    int len = question.length();
    
    // 根据问题长度调整
    if (len < 10) {
        return 0.3;  // 简短问题，降低阈值
    } else if (len < 20) {
        return 0.35; // 中等长度
    } else {
        return 0.4;  // 长问题，提高阈值
    }
    
    // 或者根据问题类型调整
    // if (question.contains("那") || question.contains("这")) {
    //     return 0.3;  // 代词问题，降低阈值
    // }
}
```

---

## 六、完整代码链路追踪

### 6.1 调用链

```
ChatServiceImpl.chatWithAi()
    ↓
KnowledgeChatService.buildRagAugment(tenantId, question)
    ↓
KnowledgeSearchService.search(tenantId, question, topK=8, minScore=0.35)
    ↓
[1] EmbeddingService.embed(question)
    → DashScopeEmbeddingService.embed()
    → 调用 DashScope API
    → 返回 List<Float> (1536维向量)
    ↓
[2] QdrantVectorStore.search(collection, queryVector, topK=8, filter)
    → 构建 SearchPoints 请求
    → client.searchAsync()
    → Qdrant 计算 Cosine 相似度
    → 返回 List<ScoredPoint> (按 score 降序，最多 topK 条)
    ↓
[3] KnowledgeSearchServiceImpl.search() 处理
    → points.stream()
    → .sorted(按 score 降序)
    → .filter(p -> p.getScore() >= minScore)  ← minScore 过滤
    → .map(toDTO)
    → 返回 List<RetrievalResultDTO>
    ↓
[4] KnowledgeChatServiceImpl.buildRagAugment() 继续
    → buildContext(hits)  // 拼装上下文
    → buildRagAugmentedUserMessageForChat()  // 构造 Prompt
    → 返回 RagAugmentResult
```

### 6.2 关键代码位置

**文件1：KnowledgeSearchServiceImpl.java**
```java
// 第31行：search 方法接收 minScore 参数
public List<RetrievalResultDTO> search(Long tenantId, String query, int topK, double minScore)

// 第43行：调用 Qdrant 搜索（不传 minScore，Qdrant 只返回 topK）
List<Points.ScoredPoint> points = qdrantVectorStore.search(COLLECTION, qv, topK, filter);

// 第46-53行：应用 minScore 过滤
return points.stream()
    .sorted(Comparator.comparingDouble(Points.ScoredPoint::getScore).reversed())
    .filter(p -> p.getScore() >= minScore)  // ← minScore 在这里
    .map(this::toDTO)
    .collect(Collectors.toList());
```

**文件2：QdrantVectorStore.java**
```java
// 第155行：Qdrant 搜索方法（不涉及 minScore）
public List<Points.ScoredPoint> search(String collectionName, List<Float> queryVector, int topK, Points.Filter filter)

// Qdrant 内部：
// 1. 计算所有向量的 Cosine 相似度
// 2. 按相似度降序排序
// 3. 返回 topK 个结果（不应用 minScore）
```

**文件3：KnowledgeChatServiceImpl.java**
```java
// 第107行：设置 minScore 默认值
double minScore = 0.35;

// 第111行：调用 search，传入 minScore
List<RetrievalResultDTO> hits = searchService.search(tenantId, question.trim(), topK, minScore);
```

---

## 七、实际案例：为什么"那满三年后是多少天？"检索不到？

### 7.1 问题分析

**原始问题**：`"那满三年后是多少天？"`（9字）

**向量化后**：
- 问题向量：`[0.123, -0.456, 0.789, ...]`（1536维）
- 知识库向量：`[0.145, -0.432, 0.801, ...]`（1536维）

**相似度计算**：
```
cosine_similarity(问题向量, 知识库向量) = 0.42
```

**Qdrant 返回**（topK=8）：
```
[
  ScoredPoint(score=0.82, "年假每年15天..."),
  ScoredPoint(score=0.75, "工作时间..."),
  ScoredPoint(score=0.55, "满三年后年假增至20天..."),  ← 正确答案
  ScoredPoint(score=0.42, "其他内容..."),
  ...
]
```

**应用 minScore=0.55 过滤**：
```
filter(p -> p.getScore() >= 0.55)
// 结果：只保留 score >= 0.55 的项
// "满三年后年假增至20天" 的 score=0.55，刚好在边界，可能被保留
// 但如果 Qdrant 返回的 score 是 0.54，就会被过滤掉
```

### 7.2 为什么分数不高？

1. **问题太短**：只有9个字，向量表示不够丰富
2. **包含代词"那"**：指代上一轮的"年假"，但向量检索只看当前问题
3. **关键词稀疏**："满三年后是多少天" 与 "满三年后年假增至20天" 的语义相似度可能不够高

### 7.3 解决方案

**方案1：降低 minScore**（已实施）
```java
double minScore = 0.35;  // 从 0.55 降低到 0.35
```

**方案2：增加 topK**
```java
int topK = 15;  // 从 8 增加到 15，让更多候选结果进入过滤阶段
```

**方案3：结合对话上下文**（需要修改接口）
```java
// 在多轮对话中，将上一轮的问题也加入检索
String enhancedQuery = history.getLastQuestion() + " " + currentQuestion;
```

---

## 八、调试技巧

### 8.1 查看实际分数

在 `KnowledgeSearchServiceImpl.search()` 中添加日志：

```java
List<RetrievalResultDTO> hits = searchService.search(tenantId, question.trim(), topK, minScore);

// 添加日志查看所有结果的分数
log.info("[RAG] 检索结果详情: question={}, minScore={}", question, minScore);
for (RetrievalResultDTO hit : hits) {
    log.info("  - score={}, title={}, chunk={}", 
        hit.getScore(), hit.getTitle(), 
        hit.getChunkText() != null && hit.getChunkText().length() > 50 
            ? hit.getChunkText().substring(0, 50) + "..." 
            : hit.getChunkText());
}
```

### 8.2 测试不同 minScore 值

```java
// 临时测试：尝试不同的 minScore
double[] testScores = {0.3, 0.35, 0.4, 0.45, 0.5};
for (double score : testScores) {
    List<RetrievalResultDTO> results = searchService.search(tenantId, question, topK, score);
    log.info("minScore={}, 结果数量={}", score, results.size());
}
```

### 8.3 直接测试检索接口

```bash
# 测试不同的 minScore
GET /api/admin/knowledge/search?q=满三年后年假&topK=10&minScore=0.3
GET /api/admin/knowledge/search?q=满三年后年假&topK=10&minScore=0.35
GET /api/admin/knowledge/search?q=满三年后年假&topK=10&minScore=0.4
```

对比不同 minScore 下的结果数量和内容。

---

## 九、最佳实践

### 9.1 初始设置
- **默认 minScore = 0.35**：平衡准确率和召回率
- **根据实际效果调整**：如果漏掉相关结果，降低；如果混入噪音，提高

### 9.2 监控指标
- **检索命中率**：`refs` 不为空的比例
- **回答准确率**：AI 回答是否基于知识库内容
- **用户满意度**：用户对回答的反馈

### 9.3 调优流程
1. **初始值**：设置 `minScore = 0.35`
2. **观察**：查看日志，统计检索结果数量
3. **调整**：根据实际效果微调（±0.05）
4. **验证**：用测试用例验证调整效果

---

## 十、总结

### minScore 的核心作用
1. **过滤低质量结果**：只保留相似度足够高的知识库片段
2. **提高回答质量**：减少 LLM 的干扰信息
3. **控制上下文长度**：节省 Token 和成本

### 关键理解点
- minScore **不是** Qdrant 的参数，而是**应用层的过滤阈值**
- Qdrant 只负责计算相似度并返回 topK，不负责过滤
- minScore 在**排序之后、DTO 转换之前**应用
- 分数范围通常是 **0.0 ~ 1.0**（Cosine 相似度）

### 调优建议
- **默认值 0.35** 适合大多数场景
- **简短问题**：降低到 0.3
- **明确问题**：提高到 0.4
- **根据实际效果动态调整**

通过理解 minScore 的完整链路，可以更好地调优 RAG 系统的检索效果。






