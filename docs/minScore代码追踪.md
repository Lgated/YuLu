# minScore 代码追踪

按代码执行顺序，追踪 minScore 在整个 RAG 链路中的流转。

---

## 一、调用入口

### 1.1 客服对话入口

**文件**：`ChatServiceImpl.java`  
**方法**：`chatWithAi()`  
**行号**：第171行

```java
@Override
public ChatAskResponse chatWithAi(Long sessionId, Long userId, Long tenantId, String question) {
    // ... 会话创建、用户消息落库、获取对话上下文 ...
    
    // 4. RAG 增强：检索知识库，拼装「参考资料 + 用户问题」作为本轮发给 LLM 的 user 消息
    RagAugmentResult rag = knowledgeChatService.buildRagAugment(tenantId, question);
    //                                                                      ↑
    //                                                              传入原始问题
    // ...
}
```

---

## 二、RAG 增强入口

### 2.1 buildRagAugment 方法

**文件**：`KnowledgeChatServiceImpl.java`  
**方法**：`buildRagAugment()`  
**行号**：第96行

```java
@Override
public RagAugmentResult buildRagAugment(Long tenantId, String question) {
    // 第105-107行：设置检索参数
    int topK = 8;
    double minScore = 0.35;  // ← minScore 在这里设置默认值
    
    // 第111行：调用检索服务，传入 minScore
    List<RetrievalResultDTO> hits = searchService.search(tenantId, question.trim(), topK, minScore);
    //                                                                              ↑        ↑
    //                                                                          topK=8  minScore=0.35
    // ...
}
```

**关键点**：
- `minScore = 0.35` 是**硬编码的默认值**
- 这个值会传递给 `KnowledgeSearchService.search()`

---

## 三、检索服务层

### 3.1 search 方法签名

**文件**：`KnowledgeSearchServiceImpl.java`  
**方法**：`search()`  
**行号**：第31行

```java
@Override
public List<RetrievalResultDTO> search(Long tenantId, String query, int topK, double minScore) {
    //                                                                              ↑
    //                                                                      接收 minScore 参数
    // 第32行：注释说明 minScore 的作用
    // minScore：相似度门槛，低于该值直接丢弃
```

### 3.2 向量化

**文件**：`KnowledgeSearchServiceImpl.java`  
**行号**：第34行

```java
// 1、将用户问题向量化
List<Float> qv = embeddingService.embed(query);
// 调用 DashScopeEmbeddingService.embed()
// 返回：List<Float> (1536维向量)
```

**调用链**：
```
KnowledgeSearchServiceImpl.search()
  → EmbeddingService.embed()
    → DashScopeEmbeddingService.embed()
      → DashScope API
        → 返回向量
```

### 3.3 Qdrant 搜索

**文件**：`KnowledgeSearchServiceImpl.java`  
**行号**：第43行

```java
// Qdrant 搜索（tenant 过滤 + payload 返回）
Points.Filter filter = buildTenantFilter(tenantId);
List<Points.ScoredPoint> points = qdrantVectorStore.search(COLLECTION, qv, topK, filter);
//                                                                              ↑
//                                                                      注意：这里不传 minScore
//                                                                      Qdrant 只返回 topK 个结果
```

**Qdrant 内部处理**（`QdrantVectorStore.search()`，第155行）：
```java
public List<Points.ScoredPoint> search(String collectionName, List<Float> queryVector, int topK, Points.Filter filter) {
    // 1. 构建搜索请求
    Points.SearchPoints.Builder searchBuilder = Points.SearchPoints.newBuilder()
            .setCollectionName(collectionName)
            .addAllVector(queryVector)  // 查询向量
            .setLimit(topK)             // 最多返回 topK 条
            .setWithPayload(...);       // 返回 payload
    
    // 2. 执行搜索
    List<Points.ScoredPoint> results = client.searchAsync(searchBuilder.build()).get();
    //                                                                        ↑
    //                                                          Qdrant 计算 Cosine 相似度
    //                                                          返回按 score 降序的 topK 条结果
    //                                                          注意：Qdrant 不应用 minScore
    
    return results;
}
```

**关键点**：
- Qdrant **不接收 minScore 参数**
- Qdrant 只负责：
  1. 计算 Cosine 相似度
  2. 按相似度降序排序
  3. 返回 topK 个结果
- **不负责过滤**，所有 topK 个结果都返回（即使分数很低）

### 3.4 minScore 过滤（核心位置）

**文件**：`KnowledgeSearchServiceImpl.java`  
**行号**：第46-53行

```java
// 二次排序 + 阈值过滤 + DTO 化
return points.stream()
    // 按分数倒序，高的在前
    .sorted(Comparator.comparingDouble(Points.ScoredPoint::getScore).reversed())
    //                                                                  ↑
    //                                                          确保最高分在前（虽然 Qdrant 已经排序）
    
    // 滤掉低分，防止"无关段落"混进来
    .filter(p -> p.getScore() >= minScore)  // ← minScore 在这里生效！
    //        ↑
    //   每个 ScoredPoint 的 score 字段
    //   与 minScore 比较，只保留 score >= minScore 的
    
    // 把 ScoredPoint 转成业务对象
    .map(this::toDTO)
    .collect(Collectors.toList());
```

**详细说明**：

1. **输入**：`points` 是 Qdrant 返回的 `List<ScoredPoint>`，包含 topK 个结果，每个都有 `score` 字段

2. **排序**：虽然 Qdrant 已经按 score 降序返回，但这里再次排序确保顺序正确

3. **过滤**：`.filter(p -> p.getScore() >= minScore)` 是**关键步骤**
   - 遍历每个 `ScoredPoint`
   - 检查 `p.getScore() >= minScore`
   - 只保留满足条件的项

4. **转换**：`.map(this::toDTO)` 将 `ScoredPoint` 转换为 `RetrievalResultDTO`

5. **返回**：过滤后的 `List<RetrievalResultDTO>`

---

## 四、ScoredPoint 结构

### 4.1 Qdrant 返回的数据结构

**类型**：`io.qdrant.client.grpc.Points.ScoredPoint`

**包含字段**：
- `score`：`float` 类型，相似度分数（0.0 ~ 1.0）
- `payload`：`Map<String, JsonWithInt.Value>`，元数据（documentId、chunkId、title、chunkText 等）
- `id`：点的 ID（通常是 chunkId）

### 4.2 toDTO 转换

**文件**：`KnowledgeSearchServiceImpl.java`  
**方法**：`toDTO()`  
**行号**：第57行

```java
private RetrievalResultDTO toDTO(Points.ScoredPoint p) {
    Map<String, JsonWithInt.Value> payload = p.getPayloadMap();
    
    return RetrievalResultDTO.builder()
            .documentId(getLong(payload, "document_id"))
            .chunkId(getLong(payload, "chunk_id"))
            .chunkIndex(getInt(payload, "chunk_index"))
            .title(getString(payload, "title"))
            .source(getString(payload, "source"))
            .fileType(getString(payload, "file_type"))
            .chunkText(getString(payload, "chunk"))
            .score((double) p.getScore())  // ← 将 score 也保存到 DTO 中
            .build();
}
```

**关键点**：
- `p.getScore()` 获取 Qdrant 计算的相似度分数
- 转换为 `Double` 类型保存到 `RetrievalResultDTO.score`
- 这个 score 会最终出现在 `refs` 中，供前端展示

---

## 五、完整执行示例

### 示例：问题"那满三年后是多少天？"

**步骤1**：`ChatServiceImpl.chatWithAi()` 调用
```java
RagAugmentResult rag = knowledgeChatService.buildRagAugment(tenantId, "那满三年后是多少天？");
```

**步骤2**：`KnowledgeChatServiceImpl.buildRagAugment()` 设置参数
```java
int topK = 8;
double minScore = 0.35;  // 设置 minScore
```

**步骤3**：调用 `KnowledgeSearchServiceImpl.search()`
```java
List<RetrievalResultDTO> hits = searchService.search(tenantId, "那满三年后是多少天？", 8, 0.35);
```

**步骤4**：向量化
```java
List<Float> qv = embeddingService.embed("那满三年后是多少天？");
// 返回：[0.123, -0.456, 0.789, ..., 0.234] (1536维)
```

**步骤5**：Qdrant 搜索
```java
List<Points.ScoredPoint> points = qdrantVectorStore.search(COLLECTION, qv, 8, filter);
// Qdrant 返回（示例）：
// [
//   ScoredPoint(score=0.82, payload={title:"员工手册", chunkText:"年假每年15天..."}),
//   ScoredPoint(score=0.75, payload={title:"员工手册", chunkText:"工作时间..."}),
//   ScoredPoint(score=0.68, payload={title:"员工手册", chunkText:"请假流程..."}),
//   ScoredPoint(score=0.55, payload={title:"员工手册", chunkText:"满三年后年假增至20天..."}),
//   ScoredPoint(score=0.42, payload={title:"员工手册", chunkText:"其他福利..."}),
//   ScoredPoint(score=0.35, payload={title:"员工手册", chunkText:"..."}),
//   ScoredPoint(score=0.28, payload={title:"员工手册", chunkText:"..."}),
//   ScoredPoint(score=0.15, payload={title:"员工手册", chunkText:"..."})
// ]
```

**步骤6**：应用 minScore 过滤
```java
points.stream()
    .sorted(Comparator.comparingDouble(Points.ScoredPoint::getScore).reversed())
    // 排序后顺序不变（Qdrant 已经排序）
    
    .filter(p -> p.getScore() >= 0.35)  // ← minScore = 0.35
    // 过滤过程：
    //   score=0.82 >= 0.35 ✅ 保留
    //   score=0.75 >= 0.35 ✅ 保留
    //   score=0.68 >= 0.35 ✅ 保留
    //   score=0.55 >= 0.35 ✅ 保留 ← "满三年后年假增至20天"
    //   score=0.42 >= 0.35 ✅ 保留
    //   score=0.35 >= 0.35 ✅ 保留（边界值）
    //   score=0.28 >= 0.35 ❌ 过滤
    //   score=0.15 >= 0.35 ❌ 过滤
    
    .map(this::toDTO)
    // 转换为 RetrievalResultDTO，包含 score 字段
    
    .collect(Collectors.toList());
// 返回：6条结果（过滤掉2条低分结果）
```

**步骤7**：返回给 `buildRagAugment()`
```java
// hits 包含6条结果，其中包含"满三年后年假增至20天"
// buildContext(hits) 拼装上下文
// buildRagAugmentedUserMessageForChat() 构造 RAG Prompt
// 返回 RagAugmentResult { augmentedUserMessage, refs }
```

---

## 六、关键理解点

### 6.1 minScore 不是 Qdrant 的参数

**错误理解**：
```java
// ❌ 错误：以为 minScore 是传给 Qdrant 的
qdrantVectorStore.search(collection, vector, topK, minScore);  // 没有这个参数
```

**正确理解**：
```java
// ✅ 正确：Qdrant 只接收 topK，不接收 minScore
List<Points.ScoredPoint> points = qdrantVectorStore.search(collection, vector, topK, filter);
// 然后在应用层过滤
points.stream().filter(p -> p.getScore() >= minScore);
```

### 6.2 minScore 的过滤时机

**时机**：在**排序之后、DTO 转换之前**

```
Qdrant 返回结果
    ↓
排序（确保最高分在前）
    ↓
minScore 过滤 ← 在这里
    ↓
DTO 转换
    ↓
返回
```

### 6.3 score 的范围

- **Cosine 相似度**：理论上 `-1.0 ~ 1.0`
- **实际范围**：在文本向量场景中通常是 `0.0 ~ 1.0`
- **高分**：`>= 0.7` 通常表示高度相关
- **中分**：`0.4 ~ 0.7` 表示中等相关
- **低分**：`< 0.4` 可能不相关

### 6.4 为什么需要二次排序？

虽然 Qdrant 已经按 score 降序返回，但代码中仍然有 `.sorted()`：

```java
.sorted(Comparator.comparingDouble(Points.ScoredPoint::getScore).reversed())
```

**原因**：
1. **防御性编程**：确保顺序正确，即使 Qdrant 行为改变也不受影响
2. **代码清晰**：明确表达"按分数降序"的意图
3. **性能影响**：对已排序的列表再次排序，性能影响很小（topK 通常只有 8 条）

---

## 七、调试技巧

### 7.1 查看实际分数

在 `KnowledgeSearchServiceImpl.search()` 中添加日志：

```java
List<Points.ScoredPoint> points = qdrantVectorStore.search(COLLECTION, qv, topK, filter);

// 添加日志：查看所有结果的分数（过滤前）
log.info("[Search] Qdrant 返回结果（过滤前）: question={}, minScore={}, count={}", 
        query, minScore, points.size());
for (Points.ScoredPoint p : points) {
    log.info("  - score={}, willFilter={}", p.getScore(), p.getScore() < minScore);
}

// 应用过滤
List<RetrievalResultDTO> results = points.stream()
    .sorted(...)
    .filter(p -> p.getScore() >= minScore)
    .map(this::toDTO)
    .collect(Collectors.toList());

// 添加日志：查看过滤后的结果
log.info("[Search] 过滤后结果数量: {}", results.size());
```

### 7.2 测试不同 minScore 值

```java
// 临时测试代码
double[] testScores = {0.2, 0.3, 0.35, 0.4, 0.5};
for (double score : testScores) {
    List<RetrievalResultDTO> results = searchService.search(tenantId, question, topK, score);
    log.info("minScore={}, 结果数量={}", score, results.size());
    if (!results.isEmpty()) {
        log.info("  最低分: {}, 最高分: {}", 
            results.get(results.size()-1).getScore(),
            results.get(0).getScore());
    }
}
```

---

## 八、总结

### minScore 的关键点

1. **设置位置**：`KnowledgeChatServiceImpl.buildRagAugment()` 第107行
2. **传递路径**：`buildRagAugment()` → `searchService.search()` → `KnowledgeSearchServiceImpl.search()`
3. **生效位置**：`KnowledgeSearchServiceImpl.search()` 第50行，`.filter(p -> p.getScore() >= minScore)`
4. **作用时机**：在 Qdrant 返回结果后，DTO 转换前
5. **作用范围**：过滤掉相似度低于阈值的知识库片段

### 调优建议

- **默认值 0.35**：适合大多数场景
- **简短问题**：降低到 0.3
- **明确问题**：提高到 0.4
- **根据实际效果调整**：观察 `refs` 是否为空、回答是否准确

通过代码追踪，可以清楚地看到 minScore 在整个链路中的流转和作用位置。

