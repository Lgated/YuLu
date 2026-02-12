# RAG 改进执行计划

## 快速概览

| 优先级  | 改进项            | 预计时间 | 预期收益          |
| ---- | -------------- | ---- | ------------- |
| 🔴 高 | 动态 minScore 调整 | 2天   | 检索准确率 +20%    |
| 🔴 高 | 上下文去重优化        | 1天   | Token 成本 -30% |
| 🔴 高 | 检索结果缓存         | 3天   | 响应时间 -30%     |
| 🔴 高 | 检索质量评估         | 2天   | 问题发现 +50%     |
| 🟡 中 | 混合检索           | 1周   | 检索准确率 +30%    |
| 🟡 中 | 重排序            | 1周   | 结果相关性 +25%    |
| 🟡 中 | 流式输出           | 3天   | 用户体验 +40%     |
| 🟡 中 | 用户反馈机制         | 2天   | 持续优化能力        |
| 🟢 低 | 知识图谱           | 1个月  | 支持复杂推理        |
| 🟢 低 | 多模态支持          | 2周   | 支持图片/表格       |

---

## Phase 1: 基础优化（1-2 个月）

### 1.1 动态 minScore 调整 ⭐⭐⭐

**当前问题**：
```java
// 硬编码
double minScore = 0.35;
```

**改进方案**：
```java
private double calculateMinScore(String question, long knowledgeBaseSize) {
    double baseScore = 0.35;
    
    // 根据问题长度调整
    int len = question.length();
    if (len < 10) baseScore = 0.3;      // 简短问题
    else if (len > 30) baseScore = 0.4;  // 长问题
    
    // 根据知识库大小调整
    if (knowledgeBaseSize < 100) baseScore -= 0.05;
    else if (knowledgeBaseSize > 10000) baseScore += 0.05;
    
    return Math.max(0.2, Math.min(0.6, baseScore));
}
```

**实施步骤**：
1. 在 `KnowledgeChatServiceImpl` 中添加 `calculateMinScore` 方法
2. 在 `buildRagAugment` 中调用该方法
3. 添加配置项支持手动调整
4. 添加日志记录实际使用的 minScore

**验收标准**：
- 不同长度的问题使用不同的 minScore
- 检索准确率提升 20%

---

### 1.2 上下文去重优化 ⭐⭐⭐

**当前问题**：
```java
// 简单拼接，可能重复
for (RetrievalResultDTO h : hits) {
    sb.append(formatHit(idx++, h));
}
```

**改进方案**：
```java
private String buildContext(List<RetrievalResultDTO> hits, int maxChars) {
    // 1. 按文档分组
    Map<Long, List<RetrievalResultDTO>> byDocument = hits.stream()
        .collect(Collectors.groupingBy(RetrievalResultDTO::getDocumentId));
    
    // 2. 每个文档最多取 2 个最高分的 chunk
    List<RetrievalResultDTO> deduplicated = new ArrayList<>();
    for (List<RetrievalResultDTO> chunks : byDocument.values()) {
        chunks.stream()
            .sorted(Comparator.comparingDouble(RetrievalResultDTO::getScore).reversed())
            .limit(2)
            .forEach(deduplicated::add);
    }
    
    // 3. 按得分重新排序
    deduplicated.sort(Comparator.comparingDouble(RetrievalResultDTO::getScore).reversed());
    
    // 4. 拼装上下文（原有逻辑）
    StringBuilder sb = new StringBuilder();
    int remain = maxChars;
    for (RetrievalResultDTO h : deduplicated) {
        String snippet = formatHit(deduplicated.indexOf(h) + 1, h);
        if (snippet.length() > remain) break;
        sb.append(snippet).append("\n\n");
        remain -= snippet.length();
    }
    return sb.toString();
}
```

**实施步骤**：
1. 修改 `buildContext` 方法
2. 添加单元测试验证去重效果
3. 对比优化前后的 Token 使用量

**验收标准**：
- 同一文档的多个 chunk 最多出现 2 次
- Token 使用量减少 30%

---

### 1.3 检索结果缓存 ⭐⭐⭐

**当前问题**：相同问题重复检索，增加成本和延迟

**改进方案**：
```java
@Cacheable(value = "rag-search", key = "#tenantId + ':' + #question.hashCode()")
public List<RetrievalResultDTO> search(Long tenantId, String question, int topK, double minScore) {
    // 检索逻辑
}
```

**实施步骤**：
1. 添加 Spring Cache 依赖（如果还没有）
2. 配置 Redis 作为缓存存储
3. 在 `KnowledgeSearchServiceImpl.search` 方法上添加 `@Cacheable`
4. 设置缓存过期时间（建议 1 小时）
5. 添加缓存命中率监控

**验收标准**：
- 相同问题的第二次请求命中缓存
- 响应时间减少 30%
- API 调用成本降低 40%

---

### 1.4 检索质量评估 ⭐⭐

**当前问题**：无法判断检索结果是否相关

**改进方案**：
```java
private boolean isHighQualityRetrieval(List<RetrievalResultDTO> hits, String question) {
    if (hits == null || hits.isEmpty()) return false;
    
    double maxScore = hits.get(0).getScore();
    double avgTop3 = hits.stream()
        .limit(3)
        .mapToDouble(RetrievalResultDTO::getScore)
        .average()
        .orElse(0.0);
    
    int count = hits.size();
    
    // 质量标准：
    // 1. 最高分 >= 0.6
    // 2. 前 3 个平均分 >= 0.5
    // 3. 结果数量 2-8 个
    return maxScore >= 0.6 && avgTop3 >= 0.5 && count >= 2 && count <= 8;
}

// 在 buildRagAugment 中使用
if (!isHighQualityRetrieval(hits, question)) {
    log.warn("[RAG] 检索质量较低: question={}, maxScore={}, count={}", 
        question, 
        hits.isEmpty() ? 0 : hits.get(0).getScore(),
        hits.size());
    
    // 可选：尝试降低 minScore 重新检索
    if (hits.isEmpty() && minScore > 0.2) {
        double lowerScore = minScore - 0.1;
        hits = searchService.search(tenantId, question, topK, lowerScore);
        log.info("[RAG] 降低 minScore 重新检索: {} -> {}", minScore, lowerScore);
    }
}
```

**实施步骤**：
1. 添加 `isHighQualityRetrieval` 方法
2. 在 `buildRagAugment` 中调用
3. 添加质量评估日志
4. 可选：实现自动降级重试

**验收标准**：
- 能够识别低质量检索
- 日志中记录质量评估结果
- 可选：自动降级重试机制

---

## Phase 2: 高级检索（3-4 个月）

### 2.1 混合检索（Hybrid Search） ⭐⭐

**目标**：结合向量检索和关键词检索

**实施步骤**：
1. 实现关键词检索（基于 Elasticsearch 或 MySQL 全文索引）
2. 实现 RRF（Reciprocal Rank Fusion）融合算法
3. 在 `KnowledgeSearchService` 中添加 `hybridSearch` 方法
4. 在 `buildRagAugment` 中可选择使用混合检索

**预期收益**：检索准确率提升 30%

---

### 2.2 重排序（Re-ranking） ⭐⭐

**目标**：使用 Cross-Encoder 模型重新排序检索结果

**实施步骤**：
1. 集成 Cross-Encoder 模型（或调用 API）
2. 实现重排序方法
3. 在检索后、拼装上下文前调用重排序
4. 对比重排序前后的效果

**预期收益**：结果相关性提升 25%

---

## Phase 3: 用户体验（5-6 个月）

### 3.1 流式输出 ⭐⭐

**目标**：支持流式返回 LLM 回答

**实施步骤**：
1. 修改 `LLMClient` 接口，添加 `chatStream` 方法
2. 在 `LangChain4jQwenClient` 中实现流式调用
3. 在 `CustomerChatController` 中添加流式接口
4. 前端对接流式接口

**预期收益**：用户体验提升 40%

---

### 3.2 用户反馈机制 ⭐

**目标**：收集用户反馈，持续优化

**实施步骤**：
1. 创建 `RagFeedback` 实体和表
2. 添加反馈接口 `POST /api/customer/chat/feedback`
3. 前端添加"有帮助/无帮助"按钮
4. 分析反馈数据，优化检索策略

**预期收益**：能够持续优化系统

---

## 技术债务清理

### 立即处理

1. **批量更新 Chunk**（`ChunkIndexServiceImpl` 第96行 TODO）
   ```java
   // 当前：循环更新
   for (Chunk c : chunks) {
       chunkMapper.updateById(c);
   }
   
   // 改进：批量更新
   chunkMapper.updateBatchById(chunks);
   ```

2. **配置化常量**
   ```java
   // 当前：硬编码
   private static final int MAX_CONTEXT_CHARS = 4000;
   
   // 改进：配置化
   @Value("${rag.context.max-chars:4000}")
   private int maxContextChars;
   ```

3. **删除 Qdrant 向量**（`DocumentServiceImpl` 第200行 TODO）
   ```java
   // 实现文档删除时同步删除 Qdrant 中的向量
   public void deleteDocument(Long id, Long tenantId) {
       // 1. 删除 Qdrant 中的向量点
       qdrantVectorStore.deleteByFilter(COLLECTION, 
           buildTenantFilter(tenantId),
           buildDocumentFilter(id));
       
       // 2. 删除数据库记录
       documentMapper.deleteById(id);
   }
   ```

---

## 监控指标

建议建立以下监控：

1. **检索指标**
   - 检索命中率（refs 不为空的比例）
   - 平均检索分数
   - 检索结果数量分布

2. **性能指标**
   - 端到端响应时间（P50、P95、P99）
   - 各阶段耗时（向量化、检索、LLM 调用）
   - 缓存命中率

3. **成本指标**
   - Embedding API 调用次数和成本
   - LLM API 调用次数和成本
   - Token 使用量

4. **质量指标**
   - 用户反馈评分
   - 回答准确率（人工评估）
   - 检索质量评估通过率

---

## 总结

### 立即行动（本周）

1. ✅ 实现动态 minScore 调整
2. ✅ 实现上下文去重优化
3. ✅ 添加检索质量评估

### 短期目标（1-2 个月）

- 完成 Phase 1 的所有改进项
- 检索准确率提升 20%
- 响应时间减少 30%
- 成本降低 40%

### 中期目标（3-6 个月）

- 完成 Phase 2 和 Phase 3 的改进项
- 支持混合检索和重排序
- 支持流式输出
- 建立用户反馈机制

### 长期目标（7-12 个月）

- 知识图谱集成
- 多模态支持
- 智能问答能力

通过持续优化，逐步提升 RAG 系统的性能和用户体验！






















