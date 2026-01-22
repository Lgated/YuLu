# RAG 向量检索增强生成 - 完整实现方案

## 一、RAG 基础知识

### 1.1 什么是 RAG？

**RAG（Retrieval-Augmented Generation）**：检索增强生成

**核心思想**：
- 不是让 LLM 从训练数据中回忆知识
- 而是从外部知识库中**检索**相关信息
- 将检索到的信息作为**上下文**输入给 LLM
- LLM 基于这些上下文生成回答

**工作流程**：
```
用户问题
    ↓
向量化（Embedding）
    ↓
向量数据库检索（相似度搜索）
    ↓
Rerank（可选，重新排序）
    ↓
构建 Prompt（问题 + 检索到的知识）
    ↓
LLM 生成回答
```

### 1.2 RAG 的优势

1. **知识更新灵活**：不需要重新训练模型，只需更新知识库
2. **减少幻觉**：基于真实知识库回答，减少编造信息
3. **可追溯性**：可以知道答案来源，便于验证
4. **多租户隔离**：不同租户可以有不同的知识库

### 1.3 RAG 的典型应用场景

- **客服知识库**：FAQ、产品文档、政策说明
- **企业内部知识管理**：技术文档、流程规范
- **法律咨询**：法条检索、案例参考
- **医疗咨询**：医学知识库、诊疗指南

---

## 二、Embedding 模型选型

### 2.1 什么是 Embedding？

**Embedding（嵌入向量）**：将文本转换为固定长度的数值向量

**作用**：
- 语义相似的文本，向量距离更近
- 可以用向量距离（余弦相似度、欧氏距离）衡量文本相似度

**示例**： 
```
"退货流程" → [0.1, 0.3, 0.5, ..., 0.2]  (768维向量)
"退款步骤" → [0.12, 0.28, 0.52, ..., 0.18]  (768维向量)
两个向量很接近，说明语义相似
```

### 2.2 Embedding 模型选型对比

#### 2.2.1 中文 Embedding 模型推荐

| 模型                          | 维度   | 优势            | 适用场景    | 接入方式          |
| --------------------------- | ---- | ------------- | ------- | ------------- |
| **text-embedding-v2**（通义千问） | 1536 | 中文效果好，与通义千问配套 | 中文客服知识库 | DashScope API |
| **bge-large-zh-v1.5**       | 1024 | 开源，中文SOTA     | 自部署，成本低 | HuggingFace   |
| **m3e-base**                | 768  | 轻量级，速度快       | 小规模知识库  | HuggingFace   |
| **text2vec-chinese**        | 768  | 中文优化，开源       | 中文场景    | HuggingFace   |

#### 2.2.2 选型建议

**方案A：使用通义千问 Embedding（推荐）**

**原因**：
- ✅ 与现有 LLM（通义千问）配套，生态一致
- ✅ 中文效果好，专门针对中文优化
- ✅ API 调用，无需部署模型
- ✅ 多租户场景下，可以按租户隔离

**接入方式**：
```java
// DashScope Embedding API
POST https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding
Headers: {
    "Authorization": "Bearer {api-key}",
    "Content-Type": "application/json"
}
Body: {
    "model": "text-embedding-v2",
    "input": {
        "texts": ["退货流程", "退款步骤"]
    }
}
```

**方案B：使用开源模型（备选）**

**原因**：
- ✅ 成本低，无需 API 调用费用
- ✅ 数据隐私，不发送到外部服务
- ❌ 需要部署模型，资源消耗大
- ❌ 需要 GPU 支持（推荐）

**推荐模型**：`bge-large-zh-v1.5` 或 `m3e-base`

### 2.3 向量维度选择

**维度越高，表达能力越强，但存储和计算成本也越高**

| 维度 | 存储成本 | 检索速度 | 表达能力 | 推荐场景 |
|------|----------|----------|----------|----------|
| 384 | 低 | 快 | 一般 | 小规模知识库（< 1万条） |
| 768 | 中 | 中 | 好 | 中等规模（1-10万条） |
| 1024 | 中高 | 中 | 很好 | 大规模（10-100万条） |
| 1536 | 高 | 慢 | 优秀 | 超大规模（> 100万条） |

**建议**：
- **小租户**：768 维（m3e-base）
- **大租户**：1536 维（text-embedding-v2）

---

## 三、Chunk 切分策略

### 3.1 为什么需要切分？

**问题**：知识库文档可能很长（几千字），直接向量化会丢失细节

**解决**：将长文档切分成多个**Chunk（块）**，每个 Chunk 单独向量化

**好处**：
- 检索更精确：可以定位到具体段落
- 上下文更聚焦：LLM 看到的上下文更相关
- 存储更灵活：可以按 Chunk 更新，不需要重新处理整个文档

### 3.2 Chunk 切分策略

#### 3.2.1 固定长度切分（简单）

**方法**：按固定字符数切分，如每 500 字符一个 Chunk

**优点**：
- ✅ 实现简单
- ✅ 切分速度快

**缺点**：
- ❌ 可能切断句子
- ❌ 语义不完整

**示例**：
```
原文（1000字符）：
"退货流程包括以下步骤：1. 登录账户...2. 选择商品...3. 填写原因..."

切分（每500字符）：
Chunk 1: "退货流程包括以下步骤：1. 登录账户...2. 选择商品..."  (500字符)
Chunk 2: "...3. 填写原因..."  (500字符)  ❌ 语义不完整
```

#### 3.2.2 重叠切分（推荐）

**方法**：固定长度 + 重叠区域

**优点**：
- ✅ 保持语义完整性
- ✅ 避免边界信息丢失

**参数**：
- `chunkSize`：每个 Chunk 的长度（如 500 字符）
- `chunkOverlap`：重叠区域长度（如 100 字符）

**示例**：
```
原文（1000字符）：
"退货流程包括以下步骤：1. 登录账户...2. 选择商品...3. 填写原因..."

切分（chunkSize=500, chunkOverlap=100）：
Chunk 1: "退货流程包括以下步骤：1. 登录账户...2. 选择商品..."  (500字符)
Chunk 2: "...2. 选择商品...3. 填写原因..."  (500字符，前100字符与Chunk1重叠)
```

#### 3.2.3 语义切分（最佳）

**方法**：按句子、段落、章节等语义边界切分

**优点**：
- ✅ 语义最完整
- ✅ 检索最精确

**实现**：
1. 先按段落切分
2. 如果段落太长，再按句子切分
3. 如果句子太长，再按固定长度切分

**示例**：
```
原文：
"## 退货流程

退货流程包括以下步骤：

1. 登录账户，进入订单详情页面
2. 选择需要退货的商品并填写退货原因
3. 提交申请后，客服会在1-3个工作日内审核
4. 审核通过后，您将收到退货指引
5. 按照指引寄回商品
6. 我们收到退货并确认无误后，将为您办理退款

## 退款说明

退款将在3-5个工作日内到账..."

切分：
Chunk 1: "## 退货流程\n\n退货流程包括以下步骤：\n\n1. 登录账户...\n6. 我们收到退货..."  (整个"退货流程"章节)
Chunk 2: "## 退款说明\n\n退款将在3-5个工作日内到账..."  (整个"退款说明"章节)
```

### 3.3 切分参数建议

| 文档类型 | chunkSize | chunkOverlap | 切分策略 |
|---------|-----------|--------------|----------|
| **FAQ** | 200-300 | 50 | 按问题-答案对切分 |
| **产品文档** | 500-800 | 100-150 | 按段落切分 |
| **政策文档** | 800-1000 | 150-200 | 按章节切分 |
| **技术文档** | 1000-1500 | 200-300 | 按章节/代码块切分 |

**推荐配置**：
```java
// 默认配置
private static final int DEFAULT_CHUNK_SIZE = 500;
private static final int DEFAULT_CHUNK_OVERLAP = 100;

// 按租户配置
private int resolveChunkSize(Long tenantId) {
    // 可以根据租户类型返回不同的 chunkSize
    return DEFAULT_CHUNK_SIZE;
}
```

---

## 四、向量数据库选型

### 4.1 什么是向量数据库？

**向量数据库**：专门用于存储和检索高维向量的数据库

**核心能力**：
- **向量存储**：高效存储数百万甚至数千万个向量
- **相似度搜索**：快速找到与查询向量最相似的向量
- **混合检索**：支持向量检索 + 元数据过滤

### 4.2 向量数据库对比

| 数据库              | 类型  | 优势               | 缺点          | 推荐场景            |
| ---------------- | --- | ---------------- | ----------- | --------------- |
| **Milvus**       | 开源  | 功能强大，性能好，社区活跃    | 部署复杂，资源消耗大  | 大规模生产环境         |
| **Qdrant**       | 开源  | 轻量级，Rust实现，性能好   | 功能相对简单      | 中小规模，快速部署       |
| **Chroma**       | 开源  | 简单易用，Python生态    | 性能一般，不适合大规模 | 原型开发，小规模        |
| **Weaviate**     | 开源  | 功能丰富，支持多模态       | 学习曲线陡       | 复杂场景            |
| **PGVector**     | 插件  | 基于PostgreSQL，易集成 | 性能一般        | 已有PostgreSQL的项目 |
| **Redis Vector** | 插件  | 基于Redis，易集成      | 功能有限        | 已有Redis的项目，小规模  |
| **DashVector**   | 云服务 | 阿里云，与通义千问配套      | 需要云服务，有成本   | 使用通义千问的项目       |
|                  |     |                  |             |                 |

### 4.3 选型建议

#### 方案A：DashVector（推荐，与通义千问配套）

**原因**：
- ✅ 与通义千问 Embedding 配套，生态一致
- ✅ 云服务，无需部署
- ✅ 多租户隔离方便（按 Collection 隔离）
- ✅ 与现有技术栈（阿里云）一致

**接入方式**：
```java
// DashVector Java SDK
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>dashvector-sdk-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 方案B：Qdrant（备选，自部署）

**原因**：
- ✅ 轻量级，部署简单
- ✅ 性能好，Rust 实现
- ✅ 开源免费
- ❌ 需要自己部署和维护

**接入方式**：
```java
// Qdrant Java Client
<dependency>
    <groupId>io.qdrant</groupId>
    <artifactId>qdrant-java</artifactId>
    <version>1.7.0</version>
</dependency>
```

#### 方案C：Redis Vector（备选，已有Redis）

**原因**：
- ✅ 项目已有 Redis，无需额外部署
- ✅ 集成简单
- ❌ 功能有限，不适合大规模

**接入方式**：
```java
// Redis Vector（Redis 7.0+）
// 使用 Redis Stack，支持向量搜索
```

### 4.4 推荐方案：DashVector

**理由**：
1. 与通义千问 Embedding 配套
2. 云服务，无需部署
3. 多租户隔离方便
4. 与现有技术栈一致

**成本**：
- 免费额度：1000 个 Collection，每个 Collection 最多 100 万条向量
- 超出后按量计费（相对便宜）

---

## 五、索引检索

### 5.1 向量检索原理

**流程**：
```
用户问题："退货流程是什么？"
    ↓
向量化：question → [0.1, 0.3, 0.5, ...]  (1536维)
    ↓
向量数据库检索：找到最相似的 K 个向量（如 top-5）
    ↓
返回对应的 Chunk 文本
```

### 5.2 相似度计算

**余弦相似度（Cosine Similarity）**：最常用

**公式**：
```
similarity = (A · B) / (||A|| × ||B||)
```

**范围**：[-1, 1]
- 1：完全相同
- 0：无关
- -1：完全相反

**示例**：
```
问题向量：[0.1, 0.3, 0.5]
Chunk1向量：[0.12, 0.28, 0.52]  → 相似度：0.98  ✅ 很相似
Chunk2向量：[0.9, 0.1, 0.2]    → 相似度：0.45  ❌ 不太相似
```

### 5.3 检索策略

#### 5.3.1 Top-K 检索

**方法**：返回相似度最高的 K 个 Chunk

**参数**：
- `K`：返回的 Chunk 数量（如 5）

**优点**：
- ✅ 简单直接
- ✅ 速度快

**缺点**：
- ❌ 可能包含不相关的结果

**示例**：
```java
// 检索 top-5
List<Chunk> chunks = vectorStore.search(questionVector, 5);
// 返回相似度最高的 5 个 Chunk
```

#### 5.3.2 阈值检索

**方法**：只返回相似度超过阈值的 Chunk

**参数**：
- `threshold`：相似度阈值（如 0.7）

**优点**：
- ✅ 过滤低质量结果
- ✅ 提高准确性

**缺点**：
- ❌ 可能返回结果太少

**示例**：
```java
// 只返回相似度 > 0.7 的 Chunk
List<Chunk> chunks = vectorStore.search(questionVector, 5, 0.7);
```

#### 5.3.3 混合检索（推荐）

**方法**：Top-K + 阈值 + 元数据过滤

**参数**：
- `K`：最多返回 K 个
- `threshold`：相似度阈值
- `metadataFilter`：元数据过滤（如租户ID、文档类型）

**优点**：
- ✅ 兼顾数量和质量
- ✅ 支持多租户隔离

**示例**：
```java
// 检索条件
SearchRequest request = SearchRequest.builder()
    .vector(questionVector)
    .topK(5)                    // 最多返回5个
    .minScore(0.7)              // 相似度阈值
    .filter("tenant_id = 1")    // 元数据过滤（租户隔离）
    .build();

List<Chunk> chunks = vectorStore.search(request);
```

### 5.4 检索参数建议

| 场景 | Top-K | 阈值 | 说明 |
|------|-------|------|------|
| **简单问答** | 3-5 | 0.7 | 问题明确，答案简短 |
| **复杂问答** | 5-10 | 0.6 | 需要多段上下文 |
| **文档检索** | 10-20 | 0.5 | 需要更多上下文 |

**推荐配置**：
```java
// 默认配置
private static final int DEFAULT_TOP_K = 5;
private static final double DEFAULT_THRESHOLD = 0.7;

// 按问题复杂度动态调整
private int resolveTopK(String question) {
    // 简单问题：3个
    // 复杂问题：5-10个
    return question.length() > 50 ? 10 : 5;
}
```

---

## 六、Rerank 详解

### 6.1 什么是 Rerank？

**Rerank（重排序）**：对检索到的结果进行二次排序，提高准确性

**问题**：
- 向量检索基于**语义相似度**，但语义相似 ≠ 真正相关
- 例如："退货流程" 和 "退款流程" 语义相似，但可能不是用户想要的

**解决**：
- 使用**专门的 Rerank 模型**对结果重新排序
- Rerank 模型考虑**问题和文档的完整语义关系**

### 6.2 Rerank 工作流程

```
向量检索（Top-10）
    ↓
[Chunk1, Chunk2, Chunk3, ..., Chunk10]  (按向量相似度排序)
    ↓
Rerank 模型重新评分
    ↓
[Chunk3, Chunk1, Chunk5, ..., Chunk2]  (按 Rerank 分数重新排序)
    ↓
取 Top-3 作为最终结果
```

### 6.3 Rerank 模型选型

| 模型 | 类型 | 优势 | 适用场景 |
|------|------|------|----------|
| **bge-reranker-large** | 开源 | 中文效果好，开源免费 | 自部署 |
| **bge-reranker-base** | 开源 | 轻量级，速度快 | 小规模 |
| **通义千问 Rerank** | 云服务 | 与通义千问配套 | 使用通义千问的项目 |

### 6.4 Rerank 使用场景

#### 场景1：必须使用 Rerank

- **高精度要求**：客服场景，答案必须准确
- **复杂问题**：需要多段上下文，需要精确匹配
- **多租户场景**：不同租户的知识库可能有重叠

#### 场景2：可以不用 Rerank

- **简单问答**：问题明确，向量检索已经足够
- **成本敏感**：Rerank 会增加延迟和成本
- **小规模知识库**：知识库小，检索结果少

### 6.5 Rerank 实现示例

```java
// 1. 向量检索（Top-10）
List<Chunk> candidates = vectorStore.search(questionVector, 10);

// 2. Rerank 重新排序
List<Chunk> reranked = reranker.rerank(
    question,                    // 用户问题
    candidates,                 // 候选 Chunk 列表
    3                           // 最终返回 Top-3
);

// 3. 使用 Rerank 后的结果
String context = reranked.stream()
    .map(Chunk::getContent)
    .collect(Collectors.joining("\n\n"));
```

### 6.6 Rerank 参数建议

| 场景 | 检索 Top-K | Rerank Top-K | 说明 |
|------|-----------|--------------|------|
| **简单问答** | 5 | 3 | 检索5个，Rerank后取3个 |
| **复杂问答** | 10 | 5 | 检索10个，Rerank后取5个 |
| **文档检索** | 20 | 10 | 检索20个，Rerank后取10个 |

**推荐配置**：
```java
// 检索阶段：多检索一些（给 Rerank 更多候选）
private static final int RETRIEVE_TOP_K = 10;

// Rerank 阶段：最终返回较少（提高精度）
private static final int RERANK_TOP_K = 5;
```

---

## 七、Prompt 构造方式

### 7.1 RAG Prompt 结构

**标准结构**：
```
System: 角色设定
Context: 检索到的知识（来自向量数据库）
Question: 用户问题
Instruction: 回答要求
```

### 7.2 Prompt 模板设计

#### 模板1：简单问答（推荐）

```java
String prompt = String.format(
    "你是一个专业的客服助手，请根据以下知识库内容回答用户问题。\n\n" +
    "【知识库内容】\n%s\n\n" +
    "【用户问题】\n%s\n\n" +
    "【回答要求】\n" +
    "1. 只基于知识库内容回答，不要编造信息\n" +
    "2. 如果知识库中没有相关信息，请说"我不知道"\n" +
    "3. 回答要简洁、准确、友好\n" +
    "4. 如果涉及步骤，请按顺序列出",
    context,  // 检索到的知识
    question  // 用户问题
);
```

#### 模板2：带来源引用

```java
String prompt = String.format(
    "你是一个专业的客服助手，请根据以下知识库内容回答用户问题。\n\n" +
    "【知识库内容】\n%s\n\n" +
    "【用户问题】\n%s\n\n" +
    "【回答要求】\n" +
    "1. 只基于知识库内容回答\n" +
    "2. 在回答末尾标注信息来源（如：来源：退货政策文档）\n" +
    "3. 如果知识库中没有相关信息，请说"我不知道"",
    formatContextWithSource(chunks),  // 带来源的知识
    question
);

// 格式化上下文（带来源）
private String formatContextWithSource(List<Chunk> chunks) {
    return chunks.stream()
        .map(chunk -> String.format("[来源：%s]\n%s", chunk.getSource(), chunk.getContent()))
        .collect(Collectors.joining("\n\n---\n\n"));
}
```

#### 模板3：多轮对话增强

```java
String prompt = String.format(
    "你是一个专业的客服助手，请根据以下知识库内容和对话历史回答用户问题。\n\n" +
    "【对话历史】\n%s\n\n" +
    "【知识库内容】\n%s\n\n" +
    "【用户问题】\n%s\n\n" +
    "【回答要求】\n" +
    "1. 结合对话历史和知识库内容回答\n" +
    "2. 如果用户的问题与对话历史相关，请参考对话历史\n" +
    "3. 如果知识库中有相关信息，优先使用知识库内容",
    formatHistory(history),  // 对话历史
    context,                  // 检索到的知识
    question
);
```

### 7.3 Prompt 优化技巧

#### 技巧1：限制上下文长度

```java
// 限制上下文总长度（避免超出 Token 限制）
private String truncateContext(String context, int maxLength) {
    if (context.length() <= maxLength) {
        return context;
    }
    // 优先保留前面的内容（通常更相关）
    return context.substring(0, maxLength - 3) + "...";
}
```

#### 技巧2：按相关性排序

```java
// 按相似度分数排序，优先使用最相关的
List<Chunk> sortedChunks = chunks.stream()
    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
    .collect(Collectors.toList());
```

#### 技巧3：去重

```java
// 去除重复的 Chunk（可能来自同一文档的不同部分）
Set<String> seen = new HashSet<>();
List<Chunk> uniqueChunks = chunks.stream()
    .filter(chunk -> seen.add(chunk.getContent()))
    .collect(Collectors.toList());
```

---

## 八、完整实现步骤

### 8.1 阶段一：基础准备（1-2天）

#### 步骤1：添加依赖

**pom.xml**：
```xml
<!-- DashVector SDK -->
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>dashvector-sdk-java</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- LangChain4j Embedding（可选，如果使用 LangChain4j 的 Embedding） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

#### 步骤2：配置 DashVector

**application.yml**：
```yaml
# DashVector 配置
dashvector:
  api-key: ${DASHVECTOR_API_KEY}  # 从环境变量读取
  endpoint: https://dashvector.cn-hangzhou.aliyuncs.com
```

**DashVectorProperties.java**：
```java
@ConfigurationProperties(prefix = "dashvector")
@Data
public class DashVectorProperties {
    private String apiKey;
    private String endpoint;
}
```

#### 步骤3：创建实体类

**KnowledgeChunk.java**：
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeChunk {
    private String id;              // Chunk ID
    private String content;         // Chunk 内容
    private String source;          // 来源（文档名、URL等）
    private Long tenantId;          // 租户ID（多租户隔离）
    private String docType;         // 文档类型（FAQ、产品文档等）
    private Map<String, String> metadata;  // 其他元数据
    private List<Float> vector;     // 向量（可选，通常存储在向量数据库）
}
```

### 8.2 阶段二：Embedding 服务（2-3天）

#### 步骤1：创建 EmbeddingService

**EmbeddingService.java**：
```java
public interface EmbeddingService {
    /**
     * 将文本转换为向量
     * @param text 文本
     * @return 向量（List<Float>）
     */
    List<Float> embed(String text);
    
    /**
     * 批量向量化
     * @param texts 文本列表
     * @return 向量列表
     */
    List<List<Float>> embedBatch(List<String> texts);
    
    /**
     * 获取向量维度
     * @return 维度
     */
    int getDimension();
}
```

#### 步骤2：实现 DashScope Embedding

**DashScopeEmbeddingService.java**：
```java
@Service
public class DashScopeEmbeddingService implements EmbeddingService {
    
    private final QianWenProperties qianWenProperties;
    private final RestTemplate restTemplate;
    private static final int DIMENSION = 1536;  // text-embedding-v2 的维度
    
    @Override
    public List<Float> embed(String text) {
        // 调用 DashScope Embedding API
        String url = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
        
        Map<String, Object> request = new HashMap<>();
        request.put("model", "text-embedding-v2");
        request.put("input", Map.of("texts", List.of(text)));
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + qianWenProperties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        
        // 解析响应
        Map<String, Object> body = response.getBody();
        List<Map<String, Object>> outputs = (List<Map<String, Object>>) body.get("output");
        List<Double> embedding = (List<Double>) outputs.get(0).get("embedding");
        
        // 转换为 List<Float>
        return embedding.stream()
            .map(Double::floatValue)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        // 批量调用（DashScope 支持批量，最多 25 条）
        // 实现类似，但需要分批处理
        // ...
    }
    
    @Override
    public int getDimension() {
        return DIMENSION;
    }
}
```

### 8.3 阶段三：Chunk 切分服务（1-2天）

#### 步骤1：创建 ChunkService

**ChunkService.java**：
```java
public interface ChunkService {
    /**
     * 将文档切分成 Chunk
     * @param content 文档内容
     * @param metadata 元数据（来源、租户ID等）
     * @return Chunk 列表
     */
    List<KnowledgeChunk> chunk(String content, Map<String, String> metadata);
}
```

#### 步骤2：实现重叠切分

**OverlapChunkService.java**：
```java
@Service
public class OverlapChunkService implements ChunkService {
    
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;
    
    @Override
    public List<KnowledgeChunk> chunk(String content, Map<String, String> metadata) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        
        int chunkSize = DEFAULT_CHUNK_SIZE;
        int overlap = DEFAULT_CHUNK_OVERLAP;
        
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            String chunkContent = content.substring(start, end);
            
            // 创建 Chunk
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setId(UUID.randomUUID().toString());
            chunk.setContent(chunkContent);
            chunk.setSource(metadata.get("source"));
            chunk.setTenantId(Long.parseLong(metadata.get("tenantId")));
            chunk.setDocType(metadata.get("docType"));
            chunk.setMetadata(metadata);
            
            chunks.add(chunk);
            
            // 移动到下一个 Chunk（考虑重叠）
            start = end - overlap;
        }
        
        return chunks;
    }
}
```

### 8.4 阶段四：向量数据库服务（3-4天）

#### 步骤1：创建 VectorStore 接口

**VectorStore.java**：
```java
public interface VectorStore {
    /**
     * 插入向量
     * @param chunks Chunk 列表（包含向量）
     */
    void upsert(List<KnowledgeChunk> chunks);
    
    /**
     * 检索相似 Chunk
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @param tenantId 租户ID（过滤）
     * @return Chunk 列表（按相似度排序）
     */
    List<KnowledgeChunk> search(List<Float> queryVector, int topK, Long tenantId);
    
    /**
     * 删除 Chunk
     * @param chunkIds Chunk ID 列表
     */
    void delete(List<String> chunkIds);
}
```

#### 步骤2：实现 DashVector Store

**DashVectorStore.java**：
```java
@Service
public class DashVectorStore implements VectorStore {
    
    private final DashVectorClient client;
    private final EmbeddingService embeddingService;
    
    @Override
    public void upsert(List<KnowledgeChunk> chunks) {
        // 1. 为每个 Chunk 生成向量（如果还没有）
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getVector() == null) {
                List<Float> vector = embeddingService.embed(chunk.getContent());
                chunk.setVector(vector);
            }
        }
        
        // 2. 构建 DashVector 的 Document 对象
        List<Document> documents = chunks.stream()
            .map(chunk -> {
                Map<String, String> fields = new HashMap<>();
                fields.put("content", chunk.getContent());
                fields.put("source", chunk.getSource());
                fields.put("tenant_id", String.valueOf(chunk.getTenantId()));
                fields.put("doc_type", chunk.getDocType());
                
                return Document.builder()
                    .id(chunk.getId())
                    .vector(chunk.getVector().stream().map(Float::doubleValue).collect(Collectors.toList()))
                    .fields(fields)
                    .build();
            })
            .collect(Collectors.toList());
        
        // 3. 插入到 DashVector
        String collectionName = buildCollectionName(chunks.get(0).getTenantId());
        Collection collection = client.getCollection(collectionName);
        collection.upsert(documents);
    }
    
    @Override
    public List<KnowledgeChunk> search(List<Float> queryVector, int topK, Long tenantId) {
        // 1. 构建查询
        String collectionName = buildCollectionName(tenantId);
        Collection collection = client.getCollection(collectionName);
        
        // 2. 构建过滤条件（租户隔离）
        String filter = String.format("tenant_id = '%d'", tenantId);
        
        // 3. 检索
        QueryRequest request = QueryRequest.builder()
            .vector(queryVector.stream().map(Float::doubleValue).collect(Collectors.toList()))
            .topK(topK)
            .filter(filter)
            .build();
        
        QueryResponse response = collection.query(request);
        
        // 4. 转换为 KnowledgeChunk
        return response.getDocuments().stream()
            .map(doc -> {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setId(doc.getId());
                chunk.setContent(doc.getFields().get("content"));
                chunk.setSource(doc.getFields().get("source"));
                chunk.setTenantId(Long.parseLong(doc.getFields().get("tenant_id")));
                chunk.setDocType(doc.getFields().get("doc_type"));
                chunk.setVector(doc.getVector().stream().map(Double::floatValue).collect(Collectors.toList()));
                return chunk;
            })
            .collect(Collectors.toList());
    }
    
    private String buildCollectionName(Long tenantId) {
        return "knowledge_base_" + tenantId;  // 按租户隔离
    }
}
```

### 8.5 阶段五：Rerank 服务（可选，2-3天）

#### 步骤1：创建 RerankService

**RerankService.java**：
```java
public interface RerankService {
    /**
     * 对检索结果重新排序
     * @param question 用户问题
     * @param candidates 候选 Chunk 列表
     * @param topK 最终返回数量
     * @return 重新排序后的 Chunk 列表
     */
    List<KnowledgeChunk> rerank(String question, List<KnowledgeChunk> candidates, int topK);
}
```

#### 步骤2：实现 BGE Rerank（或使用通义千问 Rerank）

**BGERerankService.java**：
```java
@Service
public class BGERerankService implements RerankService {
    
    // 使用 BGE Rerank 模型（需要部署或使用 API）
    // 这里简化实现，实际需要调用模型 API
    
    @Override
    public List<KnowledgeChunk> rerank(String question, List<KnowledgeChunk> candidates, int topK) {
        // 1. 对每个候选计算 Rerank 分数
        List<ScoredChunk> scored = candidates.stream()
            .map(chunk -> {
                double score = calculateRerankScore(question, chunk.getContent());
                return new ScoredChunk(chunk, score);
            })
            .collect(Collectors.toList());
        
        // 2. 按分数排序
        scored.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        // 3. 返回 Top-K
        return scored.stream()
            .limit(topK)
            .map(ScoredChunk::getChunk)
            .collect(Collectors.toList());
    }
    
    private double calculateRerankScore(String question, String content) {
        // 调用 Rerank 模型 API
        // 返回分数（0-1之间）
        // ...
    }
}
```

### 8.6 阶段六：RAG 服务整合（2-3天）

#### 步骤1：创建 RAGService

**RAGService.java**：
```java
public interface RAGService {
    /**
     * RAG 检索增强生成
     * @param question 用户问题
     * @param tenantId 租户ID
     * @param topK 检索数量
     * @return 检索到的上下文
     */
    String retrieve(String question, Long tenantId, int topK);
}
```

#### 步骤2：实现 RAGService

**RAGServiceImpl.java**：
```java
@Service
public class RAGServiceImpl implements RAGService {
    
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final RerankService rerankService;  // 可选
    
    @Override
    public String retrieve(String question, Long tenantId, int topK) {
        // 1. 问题向量化
        List<Float> questionVector = embeddingService.embed(question);
        
        // 2. 向量检索（检索更多，给 Rerank 更多候选）
        int retrieveK = topK * 2;  // 检索 topK * 2 个
        List<KnowledgeChunk> chunks = vectorStore.search(questionVector, retrieveK, tenantId);
        
        // 3. Rerank（可选）
        if (rerankService != null && chunks.size() > topK) {
            chunks = rerankService.rerank(question, chunks, topK);
        } else {
            // 如果没有 Rerank，直接取前 topK 个
            chunks = chunks.stream().limit(topK).collect(Collectors.toList());
        }
        
        // 4. 构建上下文
        return formatContext(chunks);
    }
    
    private String formatContext(List<KnowledgeChunk> chunks) {
        return chunks.stream()
            .map(chunk -> String.format("[来源：%s]\n%s", chunk.getSource(), chunk.getContent()))
            .collect(Collectors.joining("\n\n---\n\n"));
    }
}
```

#### 步骤3：集成到 ChatService

**ChatServiceImpl.java**（修改）：
```java
@Service
public class ChatServiceImpl implements ChatService {
    
    private final RAGService ragService;  // 新增
    
    @Override
    public ChatMessage chatWithAi(Long sessionId, Long userId, Long tenantId, String question) {
        // ... 原有代码 ...
        
        // 5. RAG 检索（新增）
        String context = ragService.retrieve(question, tenantId, 5);
        
        // 6. 构建 Prompt（修改）
        String prompt = buildRAGPrompt(context, question, messages);
        
        // 7. 调用 AI（修改，传入新的 prompt）
        String aiReply = llmClient.chat(messages, prompt);
        
        // ... 后续代码 ...
    }
    
    private String buildRAGPrompt(String context, String question, List<Message> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的客服助手，请根据以下知识库内容和对话历史回答用户问题。\n\n");
        
        if (!history.isEmpty()) {
            sb.append("【对话历史】\n");
            for (Message msg : history) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }
        
        sb.append("【知识库内容】\n").append(context).append("\n\n");
        sb.append("【用户问题】\n").append(question).append("\n\n");
        sb.append("【回答要求】\n");
        sb.append("1. 优先使用知识库内容回答\n");
        sb.append("2. 如果知识库中没有相关信息，请说"我不知道"\n");
        sb.append("3. 回答要简洁、准确、友好\n");
        
        return sb.toString();
    }
}
```

### 8.7 阶段七：知识库管理（2-3天）

#### 步骤1：创建知识库管理接口

**KnowledgeBaseController.java**：
```java
@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeBaseController {
    
    private final KnowledgeBaseService knowledgeBaseService;
    
    /**
     * 上传文档
     */
    @PostMapping("/upload")
    public ApiResponse<String> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") String docType) {
        Long tenantId = TenantContextHolder.getTenantId();
        knowledgeBaseService.uploadDocument(file, docType, tenantId);
        return ApiResponse.success("上传成功");
    }
    
    /**
     * 删除文档
     */
    @DeleteMapping("/{docId}")
    public ApiResponse<String> deleteDocument(@PathVariable String docId) {
        Long tenantId = TenantContextHolder.getTenantId();
        knowledgeBaseService.deleteDocument(docId, tenantId);
        return ApiResponse.success("删除成功");
    }
}
```

#### 步骤2：实现知识库服务

**KnowledgeBaseService.java**：
```java
@Service
public class KnowledgeBaseService {
    
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    
    /**
     * 上传文档并构建向量索引
     */
    public void uploadDocument(MultipartFile file, String docType, Long tenantId) {
        // 1. 读取文档内容
        String content = readFileContent(file);
        
        // 2. 切分成 Chunk
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", file.getOriginalFilename());
        metadata.put("tenantId", String.valueOf(tenantId));
        metadata.put("docType", docType);
        
        List<KnowledgeChunk> chunks = chunkService.chunk(content, metadata);
        
        // 3. 向量化并存储
        for (KnowledgeChunk chunk : chunks) {
            List<Float> vector = embeddingService.embed(chunk.getContent());
            chunk.setVector(vector);
        }
        
        vectorStore.upsert(chunks);
    }
    
    /**
     * 删除文档
     */
    public void deleteDocument(String docId, Long tenantId) {
        // 从向量数据库中删除对应的 Chunk
        // ...
    }
}
```

---

## 九、实施时间表

| 阶段      | 任务            | 预计时间 | 优先级 |
| ------- | ------------- | ---- | --- |
| **阶段一** | 基础准备（依赖、配置）   | 1-2天 | 高   |
| **阶段二** | Embedding 服务  | 2-3天 | 高   |
| **阶段三** | Chunk 切分服务    | 1-2天 | 高   |
| **阶段四** | 向量数据库服务       | 3-4天 | 高   |
| **阶段五** | Rerank 服务（可选） | 2-3天 | 中   |
| **阶段六** | RAG 服务整合      | 2-3天 | 高   |
| **阶段七** | 知识库管理         | 2-3天 | 中   |

**总计**：13-20天（不含 Rerank 为 11-17天）

---

## 十、关键技术点总结

### 10.1 选型总结

| 组件            | 推荐方案                    | 原因                  |
| ------------- | ----------------------- | ------------------- |
| **Embedding** | text-embedding-v2（通义千问） | 与现有 LLM 配套，中文效果好    |
| **向量数据库**     | DashVector              | 与通义千问配套，云服务，多租户隔离方便 |
| **Chunk 切分**  | 重叠切分                    | 平衡语义完整性和检索精度        |
| **Rerank**    | 可选                      | 高精度场景使用，简单场景可不用     |

### 10.2 多租户隔离策略

1. **按租户创建 Collection**：`knowledge_base_{tenantId}`
2. **检索时过滤**：`filter("tenant_id = {tenantId}")`
3. **元数据存储**：每个 Chunk 存储 `tenantId`

### 10.3 性能优化建议

1. **批量向量化**：一次处理多条文本，减少 API 调用
2. **异步处理**：文档上传后异步构建向量索引
3. **缓存**：缓存常用问题的检索结果
4. **分页检索**：大规模知识库使用分页检索

---

## 十一、常见问题 FAQ

### Q1：向量数据库一定要用 DashVector 吗？

**A**：不一定。如果成本敏感，可以使用 Qdrant（自部署）或 Redis Vector（已有 Redis）。

### Q2：Rerank 一定要用吗？

**A**：不一定。简单问答场景可以不用，复杂场景建议使用。

### Q3：Chunk 切分多大合适？

**A**：一般 500-800 字符，根据文档类型调整。FAQ 可以小一些（200-300），技术文档可以大一些（1000-1500）。

### Q4：如何评估 RAG 效果？

**A**：
- **检索准确率**：检索到的 Chunk 是否相关
- **回答准确率**：AI 回答是否正确
- **用户满意度**：用户对回答的满意度

### Q5：如何处理知识库更新？

**A**：
- **增量更新**：新文档直接插入，旧文档标记删除
- **全量重建**：定期全量重建索引（成本高）

---

**文档完成时间**：2025-01-16  
**适用版本**：YuLu v1.0  
**技术栈**：Spring Boot 2.7.18, LangChain4j 1.3.0, DashVector, 通义千问












