# RAG 实现方案 - 完整步骤与架构设计

## 一、当前状态分析

### 1.1 已完成的基础设施

✅ **向量化服务**：
- `EmbeddingService` 接口
- `DashScopeEmbeddingService` 实现（调用 text-embedding-v2 API）
- 支持单个和批量文本向量化
- 输出维度：1536

✅ **向量存储服务**：
- `QdrantVectorStore` 实现
- 支持创建 Collection、插入向量、相似度搜索
- 已适配 Qdrant Java Client 1.10.0

✅ **LLM 服务**：
- `LangChain4jQwenClient` 实现
- 支持多轮对话、情绪识别、意图识别
- 已实现结构化输出（JSON 格式）

✅ **对话服务**：
- `ChatService` 和 `ChatServiceImpl`
- 支持会话管理、上下文管理（Redis）
- 支持情绪检测和工单自动创建

### 1.2 缺失的 RAG 组件

❌ **文档管理服务**：
- 文档上传、解析、切分
- 文档元数据管理

❌ **知识库服务**：
- 知识库文档的向量化存储
- 文档索引管理

❌ **检索服务**：
- 基于用户问题的向量检索
- 检索结果排序和过滤

❌ **RAG 集成**：
- 检索结果注入 LLM Prompt
- 上下文构建策略

---

## 二、RAG 架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        RAG 完整流程                              │
└─────────────────────────────────────────────────────────────────┘

【知识库构建阶段】
文档上传 → 文档解析 → 文本切分(Chunking) → 向量化(Embedding) → 存储到 Qdrant
                                                                    ↓
                                                            知识库就绪

【对话检索阶段】
用户问题 → 向量化(Embedding) → 向量检索(Qdrant) → 检索结果排序 → 构建 Prompt → LLM 生成回答
                                                                      ↑
                                                              注入知识库上下文
```

### 2.2 核心组件设计

#### 组件1：文档管理服务（DocumentService）

**职责**：
- 文档上传（支持多种格式：TXT、PDF、DOCX、MD 等）
- 文档解析（提取纯文本）
- 文档元数据管理（标题、来源、上传时间、租户ID等）

**接口设计**：
```java
public interface DocumentService {
    // 上传文档
    Long uploadDocument(Long tenantId, String title, String content, String source);
    
    // 解析文档（提取文本）
    String parseDocument(byte[] fileBytes, String fileType);
    
    // 获取文档列表
    List<Document> listDocuments(Long tenantId);
    
    // 删除文档
    void deleteDocument(Long documentId);
}
```

#### 组件2：文档切分服务（ChunkService）

**职责**：
- 将长文档切分为多个 Chunk
- 支持多种切分策略（固定长度、按段落、按句子等）
- 处理重叠（Overlap）以保持上下文连贯性

**接口设计**：
```java
public interface ChunkService {
    // 切分文档
    List<DocumentChunk> chunkDocument(String content, ChunkStrategy strategy);
    
    // 切分策略枚举
    enum ChunkStrategy {
        FIXED_SIZE,      // 固定大小（如 500 tokens）
        BY_PARAGRAPH,    // 按段落切分
        BY_SENTENCE,     // 按句子切分
        SMART            // 智能切分（结合段落和大小）
    }
}
```

**切分参数**：
- `chunkSize`: 每个 Chunk 的最大字符数（建议 500-1000）
- `overlapSize`: 重叠字符数（建议 50-200）
- `minChunkSize`: 最小 Chunk 大小（避免过小的片段）

#### 组件3：知识库服务（KnowledgeBaseService）

**职责**：
- 管理知识库文档的向量化存储
- 文档索引的创建、更新、删除
- 多租户隔离（每个租户一个 Collection）

**接口设计**：
```java
public interface KnowledgeBaseService {
    // 索引文档（上传后自动索引）
    void indexDocument(Long documentId, Long tenantId);
    
    // 批量索引文档
    void batchIndexDocuments(List<Long> documentIds, Long tenantId);
    
    // 删除文档索引
    void deleteDocumentIndex(Long documentId, Long tenantId);
    
    // 更新文档索引（文档内容变更后）
    void updateDocumentIndex(Long documentId, Long tenantId);
    
    // 获取知识库统计信息
    KnowledgeBaseStats getStats(Long tenantId);
}
```

#### 组件4：检索服务（RetrievalService）

**职责**：
- 基于用户问题进行向量检索
- 检索结果排序和过滤
- 支持元数据过滤（如按文档来源、时间范围等）

**接口设计**：
```java
public interface RetrievalService {
    // 检索相关文档
    List<RetrievalResult> retrieve(String query, Long tenantId, int topK);
    
    // 带过滤条件的检索
    List<RetrievalResult> retrieveWithFilter(String query, Long tenantId, 
                                              RetrievalFilter filter, int topK);
    
    // 检索结果
    class RetrievalResult {
        private String chunkId;        // Chunk ID
        private String content;         // Chunk 内容
        private Double score;           // 相似度分数
        private Map<String, Object> metadata;  // 元数据（文档ID、标题等）
    }
}
```

#### 组件5：RAG 服务（RAGService）

**职责**：
- 整合检索和生成流程
- 构建增强的 Prompt
- 管理 RAG 配置（如 Top-K、温度等）

**接口设计**：
```java
public interface RAGService {
    // RAG 对话（检索 + 生成）
    String ragChat(String question, Long tenantId, List<Message> history);
    
    // 构建增强 Prompt
    String buildEnhancedPrompt(String question, List<RetrievalResult> results);
}
```

---

## 三、实现步骤（分阶段）

### 阶段1：基础服务实现（优先级：高）

#### 步骤1.1：实现 ChunkService（文档切分）

**实现思路**：
1. 使用固定大小切分策略（最简单）
2. 实现重叠机制（保持上下文连贯）
3. 处理边界情况（文档太短、特殊字符等）

**关键代码结构**：
```java
@Service
public class ChunkServiceImpl implements ChunkService {
    @Value("${rag.chunk.size:500}")
    private int chunkSize;
    
    @Value("${rag.chunk.overlap:50}")
    private int overlapSize;
    
    @Override
    public List<DocumentChunk> chunkDocument(String content, ChunkStrategy strategy) {
        // 1. 预处理：清理文本、统一换行符
        // 2. 根据策略切分
        // 3. 添加重叠
        // 4. 生成 Chunk ID 和元数据
    }
}
```

**配置项**（application.yml）：
```yaml
rag:
  chunk:
    size: 500        # Chunk 大小（字符数）
    overlap: 50      # 重叠大小
    min-size: 50     # 最小 Chunk 大小
```

#### 步骤1.2：实现 DocumentService（文档管理）

**实现思路**：
1. 文档实体设计（ID、租户ID、标题、内容、来源、状态等）
2. 文档解析（先支持 TXT，后续扩展 PDF、DOCX）
3. 文档存储（MySQL）

**数据库表设计**：
```sql
CREATE TABLE `knowledge_document` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `content` TEXT NOT NULL,
  `source` VARCHAR(255),  -- 来源（如：用户上传、FAQ、产品手册等）
  `status` TINYINT DEFAULT 1,  -- 1:已索引 0:未索引
  `create_time` DATETIME,
  `update_time` DATETIME,
  INDEX `idx_tenant_status` (`tenant_id`, `status`)
);
```

**关键代码结构**：
```java
@Service
public class DocumentServiceImpl implements DocumentService {
    @Autowired
    private DocumentMapper documentMapper;
    
    @Autowired
    private ChunkService chunkService;
    
    @Override
    public Long uploadDocument(Long tenantId, String title, String content, String source) {
        // 1. 保存文档到 MySQL
        // 2. 返回文档 ID
    }
    
    @Override
    public String parseDocument(byte[] fileBytes, String fileType) {
        // 根据文件类型解析（TXT/PDF/DOCX）
        // 返回纯文本
    }
}
```

#### 步骤1.3：实现 KnowledgeBaseService（知识库索引）

**实现思路**：
1. 文档切分后，批量向量化
2. 存储到 Qdrant（每个租户一个 Collection）
3. 维护文档和 Chunk 的映射关系

**数据库表设计**（Chunk 元数据）：
```sql
CREATE TABLE `knowledge_chunk` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `chunk_index` INT NOT NULL,  -- 在文档中的序号
  `content` TEXT NOT NULL,
  `qdrant_point_id` BIGINT,    -- Qdrant 中的 Point ID
  `create_time` DATETIME,
  INDEX `idx_document` (`document_id`),
  INDEX `idx_tenant` (`tenant_id`)
);
```

**关键代码结构**：
```java
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private QdrantVectorStore vectorStore;
    
    @Autowired
    private ChunkService chunkService;
    
    @Override
    public void indexDocument(Long documentId, Long tenantId) {
        // 1. 从数据库获取文档内容
        // 2. 切分文档
        // 3. 批量向量化
        // 4. 存储到 Qdrant（带元数据：documentId, chunkIndex等）
        // 5. 保存 Chunk 元数据到 MySQL
        // 6. 更新文档状态为"已索引"
    }
}
```

**Collection 命名规则**：
- 格式：`knowledge_base_tenant_{tenantId}`
- 示例：`knowledge_base_tenant_1`

### 阶段2：检索服务实现（优先级：高）

#### 步骤2.1：实现 RetrievalService（向量检索）

**实现思路**：
1. 用户问题向量化
2. 在 Qdrant 中搜索 Top-K 相似文档
3. 根据相似度分数排序
4. 返回检索结果（包含内容和元数据）

**关键代码结构**：
```java
@Service
public class RetrievalServiceImpl implements RetrievalService {
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private QdrantVectorStore vectorStore;
    
    @Value("${rag.retrieval.top-k:5}")
    private int defaultTopK;
    
    @Override
    public List<RetrievalResult> retrieve(String query, Long tenantId, int topK) {
        // 1. 问题向量化
        List<Float> queryVector = embeddingService.embed(query);
        
        // 2. 构建 Collection 名称
        String collectionName = "knowledge_base_tenant_" + tenantId;
        
        // 3. 在 Qdrant 中搜索
        List<Points.ScoredPoint> results = vectorStore.search(
            collectionName, queryVector, topK, null
        );
        
        // 4. 转换为 RetrievalResult
        // 5. 从 Payload 中提取元数据（documentId, chunkIndex等）
        // 6. 可选：从 MySQL 获取完整 Chunk 内容
    }
}
```

**配置项**：
```yaml
rag:
  retrieval:
    top-k: 5              # 默认返回 Top-5
    min-score: 0.7       # 最小相似度阈值（可选）
```

### 阶段3：RAG 集成（优先级：高）

#### 步骤3.1：实现 RAGService（检索增强生成）

**实现思路**：
1. 用户问题 → 向量检索 → 获取相关知识
2. 构建增强 Prompt（问题 + 知识库上下文）
3. 调用 LLM 生成回答

**Prompt 模板设计**：
```
你是一个专业的客服助手。请基于以下知识库内容回答用户问题。

【知识库内容】
{检索到的文档片段1}
{检索到的文档片段2}
...

【用户问题】
{用户问题}

【要求】
1. 优先使用知识库内容回答
2. 如果知识库中没有相关信息，可以基于常识回答
3. 回答要准确、简洁、友好
```

**关键代码结构**：
```java
@Service
public class RAGServiceImpl implements RAGService {
    @Autowired
    private RetrievalService retrievalService;
    
    @Autowired
    private LLMClient llmClient;
    
    @Value("${rag.prompt.template}")
    private String promptTemplate;
    
    @Override
    public String ragChat(String question, Long tenantId, List<Message> history) {
        // 1. 检索相关知识
        List<RetrievalResult> results = retrievalService.retrieve(question, tenantId, 5);
        
        // 2. 构建知识库上下文
        String knowledgeContext = buildKnowledgeContext(results);
        
        // 3. 构建增强 Prompt
        String enhancedPrompt = buildEnhancedPrompt(question, knowledgeContext);
        
        // 4. 调用 LLM（将 enhancedPrompt 作为 System Message 或第一个 User Message）
        // 5. 返回回答
    }
    
    private String buildKnowledgeContext(List<RetrievalResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            sb.append("【知识片段").append(i + 1).append("】\n");
            sb.append(r.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
```

#### 步骤3.2：集成到 ChatService

**修改点**：
在 `ChatServiceImpl.chatWithAi()` 方法中，在调用 LLM 之前先进行检索。

**集成方案**：
```java
// 在 ChatServiceImpl 中注入 RAGService
@Autowired
private RAGService ragService;

// 在 chatWithAi() 方法中
@Override
public ChatMessage chatWithAi(Long sessionId, Long userId, Long tenantId, String question) {
    // ... 现有代码 ...
    
    // 5. RAG 检索增强（新增）
    String aiReply;
    if (shouldUseRAG(tenantId)) {  // 判断是否启用 RAG
        aiReply = ragService.ragChat(question, tenantId, messages);
    } else {
        // 原有逻辑：直接调用 LLM
        aiReply = llmClient.chat(messages, question);
    }
    
    // ... 后续代码 ...
}
```

**配置项**（控制是否启用 RAG）：
```yaml
rag:
  enabled: true           # 是否启用 RAG
  tenant-whitelist: []    # 租户白名单（空则全部启用）
```

### 阶段4：优化和扩展（优先级：中）

#### 步骤4.1：实现 Rerank（重排序）

**目的**：提高检索精度，使用更强大的模型对 Top-K 结果重新排序。

**实现思路**：
1. 先用向量检索获取 Top-20
2. 使用 Cross-Encoder 模型对结果重排序
3. 返回 Top-5

**可选方案**：
- 使用 LangChain4j 的 Rerank 模型
- 或使用 DashScope 的 Rerank API（如果有）

#### 步骤4.2：实现混合检索（Hybrid Search）

**目的**：结合关键词检索和向量检索，提高召回率。

**实现思路**：
1. 关键词检索（BM25 或 Elasticsearch）
2. 向量检索
3. 结果融合（RRF - Reciprocal Rank Fusion）

#### 步骤4.3：实现文档更新机制

**目的**：当文档内容变更时，自动更新索引。

**实现思路**：
1. 监听文档更新事件
2. 删除旧索引
3. 重新索引

---

## 四、数据库设计

### 4.1 知识库文档表

```sql
CREATE TABLE `knowledge_document` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `title` VARCHAR(255) NOT NULL COMMENT '文档标题',
  `content` TEXT NOT NULL COMMENT '文档内容',
  `source` VARCHAR(255) COMMENT '来源（用户上传/FAQ/产品手册等）',
  `file_type` VARCHAR(50) COMMENT '文件类型（txt/pdf/docx等）',
  `file_size` BIGINT COMMENT '文件大小（字节）',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0-未索引 1-已索引 2-索引失败',
  `indexed_at` DATETIME COMMENT '索引时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_tenant_status` (`tenant_id`, `status`)
) COMMENT='知识库文档表';
```

### 4.2 文档切分表

```sql
CREATE TABLE `knowledge_chunk` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL COMMENT '文档ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `chunk_index` INT NOT NULL COMMENT '在文档中的序号（从0开始）',
  `content` TEXT NOT NULL COMMENT 'Chunk 内容',
  `content_length` INT COMMENT '内容长度（字符数）',
  `qdrant_point_id` BIGINT COMMENT 'Qdrant 中的 Point ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_document` (`document_id`),
  INDEX `idx_tenant` (`tenant_id`),
  INDEX `idx_qdrant_point` (`qdrant_point_id`)
) COMMENT='文档切分表';
```

### 4.3 检索日志表（可选，用于分析）

```sql
CREATE TABLE `rag_retrieval_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `session_id` BIGINT COMMENT '会话ID',
  `query` TEXT NOT NULL COMMENT '用户问题',
  `retrieved_count` INT COMMENT '检索到的文档数',
  `used_count` INT COMMENT '实际使用的文档数',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_tenant_time` (`tenant_id`, `create_time`)
) COMMENT='RAG检索日志表';
```

---

## 五、配置项设计

### 5.1 application.yml 配置

```yaml
# RAG 配置
rag:
  # 是否启用 RAG
  enabled: true
  
  # 文档切分配置
  chunk:
    size: 500          # Chunk 大小（字符数）
    overlap: 50        # 重叠大小
    min-size: 50       # 最小 Chunk 大小
    strategy: FIXED_SIZE  # 切分策略：FIXED_SIZE/BY_PARAGRAPH/BY_SENTENCE/SMART
  
  # 检索配置
  retrieval:
    top-k: 5           # 默认返回 Top-K
    min-score: 0.7     # 最小相似度阈值（0-1）
    enable-rerank: false  # 是否启用 Rerank（暂不支持）
  
  # Prompt 配置
  prompt:
    template: |
      你是一个专业的客服助手。请基于以下知识库内容回答用户问题。
      
      【知识库内容】
      {knowledge_context}
      
      【用户问题】
      {question}
      
      【要求】
      1. 优先使用知识库内容回答
      2. 如果知识库中没有相关信息，可以基于常识回答
      3. 回答要准确、简洁、友好
  
  # 租户配置
  tenant:
    whitelist: []      # 租户白名单（空则全部启用）
    blacklist: []      # 租户黑名单
```

---

## 六、实现顺序建议

### 第一周：基础服务

1. ✅ **Day 1-2**：实现 `ChunkService`
   - 文档切分逻辑
   - 重叠机制
   - 单元测试

2. ✅ **Day 3-4**：实现 `DocumentService`
   - 文档实体和 Mapper
   - 文档上传接口
   - 文档解析（先支持 TXT）

3. ✅ **Day 5**：实现 `KnowledgeBaseService`
   - 文档索引逻辑
   - Qdrant 存储
   - Chunk 元数据管理

### 第二周：检索和集成

4. ✅ **Day 1-2**：实现 `RetrievalService`
   - 向量检索
   - 结果转换
   - 元数据提取

5. ✅ **Day 3-4**：实现 `RAGService`
   - Prompt 构建
   - LLM 集成
   - 测试验证

6. ✅ **Day 5**：集成到 `ChatService`
   - 修改 `chatWithAi()` 方法
   - 配置开关
   - 端到端测试

### 第三周：优化和扩展

7. ✅ **Day 1-3**：前端接口
   - 文档上传 API
   - 知识库管理 API
   - 文档列表 API

8. ✅ **Day 4-5**：性能优化
   - 批量索引优化
   - 检索性能优化
   - 缓存机制

---

## 七、关键设计决策

### 7.1 多租户隔离策略

**方案**：每个租户一个 Qdrant Collection

**优点**：
- ✅ 完全隔离，安全性高
- ✅ 易于管理（删除租户时直接删除 Collection）
- ✅ 性能好（无需过滤）

**缺点**：
- ❌ Collection 数量多（但 Qdrant 支持）

**实现**：
```java
String collectionName = "knowledge_base_tenant_" + tenantId;
```

### 7.2 Chunk 大小选择

**推荐值**：
- **Chunk Size**: 500-1000 字符
- **Overlap**: 50-200 字符

**考虑因素**：
- 太小：上下文不完整
- 太大：检索精度下降，Token 消耗大

### 7.3 Top-K 选择

**推荐值**：
- **初始检索**: Top-20（保证召回率）
- **最终返回**: Top-5（保证精度，控制 Token）

**考虑因素**：
- 太少：可能遗漏相关信息
- 太多：Token 消耗大，可能引入噪声

### 7.4 Prompt 设计原则

1. **明确角色**：定义 AI 的身份（客服助手）
2. **明确任务**：基于知识库回答
3. **明确格式**：结构化输出（如果需要）
4. **明确要求**：准确、简洁、友好

---

## 八、测试策略

### 8.1 单元测试

- `ChunkService`: 测试各种切分策略
- `RetrievalService`: 测试检索逻辑
- `RAGService`: Mock LLM，测试 Prompt 构建

### 8.2 集成测试

- 端到端流程：文档上传 → 索引 → 检索 → 生成
- 多租户隔离测试
- 性能测试（批量索引、并发检索）

### 8.3 评估指标

- **检索精度**：Top-K 中相关文档的比例
- **回答质量**：人工评估或自动评估
- **响应时间**：检索 + 生成的总时间

---

## 九、注意事项

### 9.1 错误处理

- 文档解析失败：记录日志，不阻塞流程
- 向量化失败：重试机制
- Qdrant 连接失败：降级到无 RAG 模式

### 9.2 性能优化

- **批量向量化**：使用 `embedBatch()` 而不是循环调用
- **异步索引**：文档上传后异步索引，不阻塞用户
- **缓存机制**：常见问题的检索结果可以缓存

### 9.3 监控和日志

- 记录检索日志（问题、检索结果、使用情况）
- 监控索引状态（成功/失败数量）
- 监控 Qdrant 性能（查询延迟、存储大小）

---

## 十、后续扩展方向

1. **多模态支持**：图片、表格的向量化
2. **增量更新**：文档变更时只更新相关 Chunk
3. **智能路由**：根据问题类型选择不同的知识库
4. **用户反馈**：收集用户对回答的反馈，优化检索策略
5. **A/B 测试**：对比不同 Prompt 模板的效果

---

**文档完成时间**：2025-01-16  
**适用版本**：YuLu v1.0  
**技术栈**：Spring Boot 2.7.18, DashScope API, Qdrant 1.10.0, LangChain4j

















