# minScore 流程图详解

## 一、完整数据流

```
┌─────────────────────────────────────────────────────────────────┐
│ 用户问题："那满三年后是多少天？"                                  │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ [1] EmbeddingService.embed(question)                            │
│                                                                 │
│ 输入：文本字符串                                                 │
│ 输出：List<Float> (1536维向量)                                  │
│ 例如：[0.123, -0.456, 0.789, ..., 0.234]                       │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ [2] QdrantVectorStore.search(collection, queryVector, topK, filter)│
│                                                                 │
│ Qdrant 内部处理：                                               │
│ 1. 遍历集合中所有向量点                                          │
│ 2. 计算每个向量与 queryVector 的 Cosine 相似度                │
│ 3. 按相似度降序排序                                             │
│ 4. 返回 topK=8 个最高分的结果                                   │
│                                                                 │
│ 返回：List<ScoredPoint>                                         │
│ [                                                               │
│   ScoredPoint(score=0.82, payload={...}),                      │
│   ScoredPoint(score=0.75, payload={...}),                      │
│   ScoredPoint(score=0.68, payload={...}),                      │
│   ScoredPoint(score=0.55, payload={...}),  ← "满三年后年假增至20天"│
│   ScoredPoint(score=0.42, payload={...}),                      │
│   ScoredPoint(score=0.35, payload={...}),                      │
│   ScoredPoint(score=0.28, payload={...}),                      │
│   ScoredPoint(score=0.15, payload={...})                       │
│ ]                                                               │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ [3] KnowledgeSearchServiceImpl.search() 处理                    │
│                                                                 │
│ points.stream()                                                 │
│   .sorted(按 score 降序)  ← 确保最高分在前                      │
│   .filter(p -> p.getScore() >= minScore)  ← minScore 在这里！  │
│   .map(toDTO)                                                   │
│   .collect()                                                    │
│                                                                 │
│ 假设 minScore = 0.35：                                          │
│                                                                 │
│ 过滤前（8条）：                                                 │
│   score=0.82 ✅ (>= 0.35) → 保留                               │
│   score=0.75 ✅ (>= 0.35) → 保留                               │
│   score=0.68 ✅ (>= 0.35) → 保留                               │
│   score=0.55 ✅ (>= 0.35) → 保留                               │
│   score=0.42 ✅ (>= 0.35) → 保留                               │
│   score=0.35 ✅ (>= 0.35) → 保留（边界值）                     │
│   score=0.28 ❌ (< 0.35)  → 过滤                               │
│   score=0.15 ❌ (< 0.35)  → 过滤                               │
│                                                                 │
│ 过滤后（6条）：                                                 │
│   RetrievalResultDTO(score=0.82, ...),                         │
│   RetrievalResultDTO(score=0.75, ...),                         │
│   RetrievalResultDTO(score=0.68, ...),                          │
│   RetrievalResultDTO(score=0.55, ...),  ← 包含正确答案           │
│   RetrievalResultDTO(score=0.42, ...),                         │
│   RetrievalResultDTO(score=0.35, ...)                          │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ [4] KnowledgeChatServiceImpl.buildRagAugment()                 │
│                                                                 │
│ 接收：List<RetrievalResultDTO> (已过滤)                         │
│ 处理：                                                           │
│   1. buildContext(hits) → 拼装上下文字符串                      │
│   2. buildRagAugmentedUserMessageForChat() → 构造 RAG Prompt    │
│ 返回：RagAugmentResult { augmentedUserMessage, refs }          │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ [5] LLM 生成回答                                                 │
│                                                                 │
│ 输入：对话历史 + RAG 增强后的用户消息                            │
│ 输出：AI 回答（基于知识库内容）                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、minScore 在不同位置的影响

### 场景1：minScore = 0.55（过高）

```
Qdrant 返回（8条）：
  score=0.82 ✅
  score=0.75 ✅
  score=0.68 ✅
  score=0.55 ✅ (边界值，刚好通过)
  score=0.42 ❌ (被过滤)
  score=0.35 ❌ (被过滤)
  score=0.28 ❌ (被过滤)
  score=0.15 ❌ (被过滤)

过滤后：4条
问题：如果正确答案的 score 是 0.54，会被过滤掉！
```

### 场景2：minScore = 0.35（当前设置）

```
Qdrant 返回（8条）：
  score=0.82 ✅
  score=0.75 ✅
  score=0.68 ✅
  score=0.55 ✅
  score=0.42 ✅
  score=0.35 ✅ (边界值)
  score=0.28 ❌ (被过滤)
  score=0.15 ❌ (被过滤)

过滤后：6条
优点：更多相关结果能通过
```

### 场景3：minScore = 0.3（较低）

```
Qdrant 返回（8条）：
  score=0.82 ✅
  score=0.75 ✅
  score=0.68 ✅
  score=0.55 ✅
  score=0.42 ✅
  score=0.35 ✅
  score=0.28 ✅ (也被保留)
  score=0.15 ❌ (被过滤)

过滤后：7条
风险：可能混入一些不相关的内容
```

---

## 三、Cosine 相似度计算示例

### 示例1：高度相关

```
问题向量：[0.5, 0.3, 0.8, ...]
知识库向量：[0.48, 0.32, 0.79, ...]  ← 非常相似

Cosine 相似度 = 0.92  ← 高分，高度相关
```

### 示例2：中等相关

```
问题向量：[0.5, 0.3, 0.8, ...]
知识库向量：[0.4, 0.2, 0.6, ...]  ← 部分相似

Cosine 相似度 = 0.55  ← 中等分，相关但不够精确
```

### 示例3：低相关

```
问题向量：[0.5, 0.3, 0.8, ...]
知识库向量：[0.1, 0.9, 0.2, ...]  ← 差异较大

Cosine 相似度 = 0.28  ← 低分，可能不相关
```

---

## 四、调优决策树

```
开始
  ↓
问题是否检索到结果？
  ├─ 是 → refs 是否为空？
  │        ├─ 是 → minScore 过高，降低 0.05
  │        └─ 否 → 检查 score 分布
  │                 ├─ 最低分接近 minScore → 正常
  │                 └─ 最低分远高于 minScore → 可适当提高
  │
  └─ 否 → 检查日志
           ├─ Qdrant 返回了结果但被过滤 → minScore 过高，降低
           └─ Qdrant 没返回结果 → 问题表述问题，不是 minScore 问题
```

---

## 五、关键代码位置速查

| 文件 | 行号 | 代码 | 说明 |
|------|------|------|------|
| `KnowledgeChatServiceImpl.java` | 107 | `double minScore = 0.35;` | 设置默认值 |
| `KnowledgeChatServiceImpl.java` | 111 | `searchService.search(..., minScore)` | 传入 minScore |
| `KnowledgeSearchServiceImpl.java` | 31 | `search(..., double minScore)` | 接收 minScore 参数 |
| `KnowledgeSearchServiceImpl.java` | 50 | `.filter(p -> p.getScore() >= minScore)` | **minScore 过滤位置** |
| `QdrantVectorStore.java` | 102 | `.setDistance(Collections.Distance.Cosine)` | 相似度算法配置 |
| `QdrantVectorStore.java` | 155 | `search(..., topK, filter)` | Qdrant 搜索（不涉及 minScore） |

---

## 六、调试检查清单

- [ ] 查看日志中 `[RAG] 检索结果数量`
- [ ] 确认 `refs` 是否为空
- [ ] 如果为空，检查 Qdrant 是否返回了结果
- [ ] 查看返回结果的 score 分布
- [ ] 尝试降低 minScore 0.05，看是否能检索到
- [ ] 直接调用检索接口测试：`GET /api/admin/knowledge/search?q=...&minScore=0.3`

---

通过这个流程图，可以清晰地看到 minScore 在整个链路中的位置和作用。






















