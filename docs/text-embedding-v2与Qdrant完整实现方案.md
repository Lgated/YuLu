# text-embedding-v2 与 Qdrant 完整实现方案

## 一、问题解决：Qdrant 依赖导入失败

### 1.1 问题分析

当前 `pom.xml` 中的依赖：
```xml
<dependency>
    <groupId>io.qdrant</groupId>
    <artifactId>qdrant-client</artifactId>
    <version>1.9.0</version>
</dependency>
```

**问题**：`qdrant-client` 这个 artifactId 可能不存在或版本不对。

### 1.2 正确的依赖配置

Qdrant 的 Java 客户端正确的依赖应该是：

```xml
<!-- Qdrant Java Client -->
<dependency>
    <groupId>io.qdrant</groupId>
    <artifactId>qdrant-java</artifactId>
    <version>1.7.0</version>
</dependency>
```

**或者使用最新版本**（如果可用）：
```xml
<dependency>
    <groupId>io.qdrant</groupId>
    <artifactId>qdrant-java</artifactId>
    <version>1.8.0</version>
</dependency>
```

### 1.3 修复步骤

1. **删除错误的依赖**：
   在 `pom.xml` 中删除或注释掉：
   ```xml
   <!-- 删除这个 -->
   <dependency>
       <groupId>io.qdrant</groupId>
       <artifactId>qdrant-client</artifactId>
       <version>1.9.0</version>
   </dependency>
   ```

2. **添加正确的依赖**：
   ```xml
   <!-- Qdrant Java Client -->
   <dependency>
       <groupId>io.qdrant</groupId>
       <artifactId>qdrant-java</artifactId>
       <version>1.7.0</version>
   </dependency>
   ```

3. **刷新 Maven**：
   - IntelliJ IDEA：右键 `pom.xml` → `Maven` → `Reload Project`
   - 或执行：`mvn clean install`

### 1.4 如果仍然无法解析

如果 `qdrant-java` 也无法解析，可以尝试：

**方案A：使用 HTTP 客户端直接调用 Qdrant REST API**
- 使用 `RestTemplate` 或 `WebClient` 直接调用 Qdrant 的 HTTP API
- 无需额外依赖，但需要自己封装

**方案B：检查 Maven 仓库**
- 确认 Maven Central 是否有该依赖
- 可能需要添加其他仓库

**方案C：使用其他向量数据库客户端**
- 如 Milvus Java Client
- 或使用 Redis Vector（如果已有 Redis）

---

## 二、完整实现方案

### 2.1 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    RAG 架构流程                           │
└─────────────────────────────────────────────────────────┘

用户问题
    ↓
[Embedding Service] (text-embedding-v2)
    ↓ 生成向量
[Qdrant Vector DB] (相似度检索)
    ↓ 返回 Top-K 文档
[Context Builder] (构建上下文)
    ↓ 拼接 Prompt
[LLM Service] (通义千问)
    ↓ 生成回答
返回给用户
```

### 2.2 目录结构

```
src/main/java/com/ityfz/yulu/
├── common/
│   ├── config/
│   │   ├── QianWenProperties.java          (已有)
│   │   └── QdrantProperties.java           (新增)
│   ├── ai/
│   │   ├── impl/
│   │   │   ├── LangChain4jQwenClient.java  (已有)
│   │   │   ├── DashScopeEmbeddingService.java  (新增)
│   │   │   └── QdrantVectorStore.java      (新增)
│   │   └── EmbeddingService.java           (新增接口)
│   └── rag/
│       ├── ChunkService.java               (新增)
│       ├── RAGService.java                 (新增)
│       └── dto/
│           ├── DocumentChunk.java          (新增)
│           └── SearchResult.java           (新增)
```

---

## 三、依赖配置

### 3.1 Maven 依赖（pom.xml）

```xml
<properties>
    <java.version>17</java.version>
    <mybatis.plus.version>3.5.5</mybatis.plus.version>
    <mysql.connector.version>8.0.33</mysql.connector.version>
    <hutool.version>5.8.26</hutool.version>
    <langchain4j.version>1.3.0</langchain4j.version>
    <!-- 新增：Qdrant 版本 -->
    <qdrant.version>1.7.0</qdrant.version>
</properties>

<dependencies>
    <!-- 现有依赖... -->
    
    <!-- LangChain4j -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>

    <!-- Qdrant Java Client -->
    <dependency>
        <groupId>io.qdrant</groupId>
        <artifactId>qdrant-java</artifactId>
        <version>${qdrant.version}</version>
    </dependency>

    <!-- HTTP Client (用于 DashScope Embedding API) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

### 3.2 配置文件（application.yml）

```yaml
spring:
  application:
    name: Yulu
  # ... 其他配置 ...

# AI 配置（通义千问）
ai:
  qianwen:
    api-key: ${AI_QIANWEN_API_KEY:your_api_key_here}
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: qwen-turbo
  
  # 新增：DashScope Embedding 配置
  embedding:
    api-key: ${AI_QIANWEN_API_KEY:your_api_key_here}  # 复用通义千问的 API Key
    base-url: https://dashscope.aliyuncs.com/api/v1
    model: text-embedding-v2
    dimension: 1536  # text-embedding-v2 输出维度

# 新增：Qdrant 配置
qdrant:
  host: localhost
  port: 6333
  grpc-port: 6334
  timeout: 5000  # 超时时间（毫秒）
  # 可选：API Key（如果 Qdrant 启用了认证）
  # api-key: your_qdrant_api_key
```

---

## 四、核心实现代码

### 4.1 Qdrant 配置类

**文件**：`src/main/java/com/ityfz/yulu/common/config/QdrantProperties.java`

```java
package com.ityfz.yulu.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qdrant 向量数据库配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {
    /**
     * Qdrant 服务地址
     */
    private String host = "localhost";
    
    /**
     * HTTP 端口（默认 6333）
     */
    private int port = 6333;
    
    /**
     * gRPC 端口（默认 6334）
     */
    private int grpcPort = 6334;
    
    /**
     * 连接超时时间（毫秒）
     */
    private int timeout = 5000;
    
    /**
     * API Key（可选，如果 Qdrant 启用了认证）
     */
    private String apiKey;
}
```

### 4.2 Embedding 服务接口

**文件**：`src/main/java/com/ityfz/yulu/common/ai/EmbeddingService.java`

```java
package com.ityfz.yulu.common.ai;

import java.util.List;

/**
 * Embedding 服务接口
 * 用于将文本转换为向量
 */
public interface EmbeddingService {
    /**
     * 将单个文本转换为向量
     * 
     * @param text 输入文本
     * @return 向量（Float 列表）
     */
    List<Float> embed(String text);
    
    /**
     * 批量将文本转换为向量
     * 
     * @param texts 输入文本列表
     * @return 向量列表
     */
    List<List<Float>> embedBatch(List<String> texts);
    
    /**
     * 获取向量维度
     * 
     * @return 向量维度
     */
    int getDimension();
}
```

### 4.3 DashScope Embedding 服务实现

**文件**：`src/main/java/com/ityfz/yulu/common/ai/impl/DashScopeEmbeddingService.java`

```java
package com.ityfz.yulu.common.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ityfz.yulu.common.ai.EmbeddingService;
import com.ityfz.yulu.common.config.QianWenProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope text-embedding-v2 实现
 */
@Slf4j
@Service
public class DashScopeEmbeddingService implements EmbeddingService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${ai.embedding.api-key:${ai.qianwen.api-key}}")
    private String apiKey;
    
    @Value("${ai.embedding.base-url:https://dashscope.aliyuncs.com/api/v1}")
    private String baseUrl;
    
    @Value("${ai.embedding.model:text-embedding-v2}")
    private String model;
    
    @Value("${ai.embedding.dimension:1536}")
    private int dimension;
    
    @Autowired
    public DashScopeEmbeddingService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public List<Float> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本不能为空");
        }
        
        try {
            // 构建请求
            String url = baseUrl + "/services/embeddings/text-embedding/text-embedding";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Content-Type", "application/json");
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", text);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.debug("[DashScope Embedding] 请求: model={}, text={}", model, text);
            
            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class
            );
            
            // 解析响应
            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (root.has("output") && root.get("output").has("embeddings")) {
                JsonNode embeddings = root.get("output").get("embeddings");
                if (embeddings.isArray() && embeddings.size() > 0) {
                    JsonNode embedding = embeddings.get(0);
                    if (embedding.has("embedding")) {
                        JsonNode embeddingArray = embedding.get("embedding");
                        List<Float> vector = new ArrayList<>();
                        for (JsonNode value : embeddingArray) {
                            vector.add(value.floatValue());
                        }
                        log.debug("[DashScope Embedding] 成功: dimension={}", vector.size());
                        return vector;
                    }
                }
            }
            
            // 如果响应格式不符合预期，尝试兼容格式
            if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                JsonNode data = root.get("data").get(0);
                if (data.has("embedding")) {
                    JsonNode embeddingArray = data.get("embedding");
                    List<Float> vector = new ArrayList<>();
                    for (JsonNode value : embeddingArray) {
                        vector.add(value.floatValue());
                    }
                    log.debug("[DashScope Embedding] 成功（兼容格式）: dimension={}", vector.size());
                    return vector;
                }
            }
            
            throw new RuntimeException("无法解析 Embedding 响应: " + response.getBody());
            
        } catch (Exception e) {
            log.error("[DashScope Embedding] 调用失败: text={}", text, e);
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        // DashScope 支持批量，最多 25 条
        int batchSize = 25;
        List<List<Float>> results = new ArrayList<>();
        
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            
            try {
                String url = baseUrl + "/services/embeddings/text-embedding/text-embedding";
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + apiKey);
                headers.set("Content-Type", "application/json");
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", model);
                requestBody.put("input", batch);
                
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class
                );
                
                JsonNode root = objectMapper.readTree(response.getBody());
                
                if (root.has("output") && root.get("output").has("embeddings")) {
                    JsonNode embeddings = root.get("output").get("embeddings");
                    for (JsonNode embedding : embeddings) {
                        if (embedding.has("embedding")) {
                            JsonNode embeddingArray = embedding.get("embedding");
                            List<Float> vector = new ArrayList<>();
                            for (JsonNode value : embeddingArray) {
                                vector.add(value.floatValue());
                            }
                            results.add(vector);
                        }
                    }
                }
                
            } catch (Exception e) {
                log.error("[DashScope Embedding] 批量调用失败: batch={}", batch, e);
                // 如果批量失败，逐个调用
                for (String text : batch) {
                    results.add(embed(text));
                }
            }
        }
        
        return results;
    }
    
    @Override
    public int getDimension() {
        return dimension;
    }
}
```

### 4.4 RestTemplate 配置

**文件**：`src/main/java/com/ityfz/yulu/common/config/RestTemplateConfig.java`

```java
package com.ityfz.yulu.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置
 */
@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(clientHttpRequestFactory());
        return restTemplate;
    }
    
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 连接超时 10 秒
        factory.setReadTimeout(30000);     // 读取超时 30 秒
        return factory;
    }
}
```

### 4.5 Qdrant 向量存储服务

**文件**：`src/main/java/com/ityfz/yulu/common/ai/impl/QdrantVectorStore.java`

```java
package com.ityfz.yulu.common.ai.impl;

import com.ityfz.yulu.common.config.QdrantProperties;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantClientConfig;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量存储服务
 */
@Slf4j
@Service
public class QdrantVectorStore {
    
    private final QdrantProperties properties;
    private QdrantClient client;
    
    @Autowired
    public QdrantVectorStore(QdrantProperties properties) {
        this.properties = properties;
    }
    
    @PostConstruct
    public void init() {
        try {
            // 创建 Qdrant 客户端配置
            QdrantClientConfig config = QdrantClientConfig.newBuilder(properties.getHost(), properties.getPort(), false)
                    .withTimeout(properties.getTimeout())
                    .build();
            
            // 创建客户端
            client = new QdrantClient(config);
            
            log.info("[Qdrant] 连接成功: {}:{}", properties.getHost(), properties.getPort());
        } catch (Exception e) {
            log.error("[Qdrant] 连接失败", e);
            throw new RuntimeException("Qdrant 连接失败", e);
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (client != null) {
            try {
                client.close();
                log.info("[Qdrant] 连接已关闭");
            } catch (Exception e) {
                log.error("[Qdrant] 关闭连接失败", e);
            }
        }
    }
    
    /**
     * 创建集合（Collection）
     * 
     * @param collectionName 集合名称
     * @param vectorSize 向量维度
     */
    public void createCollection(String collectionName, int vectorSize) {
        try {
            // 检查集合是否存在
            Collections.CollectionInfo collectionInfo = client.collectionInfo(collectionName).get();
            if (collectionInfo != null) {
                log.info("[Qdrant] 集合已存在: {}", collectionName);
                return;
            }
            
            // 创建集合配置
            Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                    .setSize(vectorSize)
                    .setDistance(Collections.Distance.Cosine)  // 使用余弦相似度
                    .build();
            
            Collections.CreateCollection createCollection = Collections.CreateCollection.newBuilder()
                    .setVectorsConfig(Collections.VectorsConfig.newBuilder()
                            .setParams(vectorParams)
                            .build())
                    .build();
            
            // 创建集合
            client.createCollection(collectionName, createCollection).get();
            
            log.info("[Qdrant] 集合创建成功: {}, dimension={}", collectionName, vectorSize);
        } catch (Exception e) {
            log.error("[Qdrant] 创建集合失败: {}", collectionName, e);
            throw new RuntimeException("创建集合失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 插入向量
     * 
     * @param collectionName 集合名称
     * @param pointId 点 ID
     * @param vector 向量
     * @param payload 元数据（可选）
     */
    public void upsertPoint(String collectionName, long pointId, List<Float> vector, Map<String, Object> payload) {
        try {
            // 转换为 Qdrant 格式
            Points.PointStruct point = Points.PointStruct.newBuilder()
                    .setId(Points.PointId.newBuilder().setNum(pointId).build())
                    .setVectors(Points.Vectors.newBuilder()
                            .setVector(Points.Vector.newBuilder()
                                    .addAllData(vector)
                                    .build())
                            .build())
                    .putAllPayload(convertPayload(payload))
                    .build();
            
            // 插入点
            client.upsert(collectionName, List.of(point)).get();
            
            log.debug("[Qdrant] 插入点成功: collection={}, pointId={}", collectionName, pointId);
        } catch (Exception e) {
            log.error("[Qdrant] 插入点失败: collection={}, pointId={}", collectionName, pointId, e);
            throw new RuntimeException("插入点失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 相似度搜索
     * 
     * @param collectionName 集合名称
     * @param queryVector 查询向量
     * @param topK 返回 Top-K 结果
     * @param filter 过滤条件（可选）
     * @return 搜索结果
     */
    public List<Points.ScoredPoint> search(String collectionName, List<Float> queryVector, int topK, Points.Filter filter) {
        try {
            Points.SearchPoints searchPoints = Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setVector(Points.Vector.newBuilder().addAllData(queryVector).build())
                    .setTop(topK)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(false).build())
                    .build();
            
            if (filter != null) {
                searchPoints = searchPoints.toBuilder().setFilter(filter).build();
            }
            
            Points.SearchResponse response = client.search(searchPoints).get();
            
            log.debug("[Qdrant] 搜索成功: collection={}, topK={}, results={}", 
                    collectionName, topK, response.getResultCount());
            
            return new ArrayList<>(response.getResultList());
        } catch (Exception e) {
            log.error("[Qdrant] 搜索失败: collection={}", collectionName, e);
            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 转换 Payload（元数据）
     */
    private Map<String, Points.Value> convertPayload(Map<String, Object> payload) {
        Map<String, Points.Value> result = new HashMap<>();
        if (payload != null) {
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                Object value = entry.getValue();
                Points.Value.Builder valueBuilder = Points.Value.newBuilder();
                
                if (value instanceof String) {
                    valueBuilder.setStringValue((String) value);
                } else if (value instanceof Integer) {
                    valueBuilder.setIntegerValue((Integer) value);
                } else if (value instanceof Long) {
                    valueBuilder.setIntegerValue((Long) value);
                } else if (value instanceof Double || value instanceof Float) {
                    valueBuilder.setDoubleValue(value instanceof Double ? (Double) value : ((Float) value).doubleValue());
                } else if (value instanceof Boolean) {
                    valueBuilder.setBoolValue((Boolean) value);
                } else {
                    valueBuilder.setStringValue(value.toString());
                }
                
                result.put(entry.getKey(), valueBuilder.build());
            }
        }
        return result;
    }
    
    /**
     * 获取客户端（用于高级操作）
     */
    public QdrantClient getClient() {
        return client;
    }
}
```

**注意**：如果 `io.qdrant.client` 包无法导入，说明依赖仍然有问题。可以：

1. **使用 HTTP 客户端直接调用 Qdrant REST API**（见下方替代方案）
2. **检查 Qdrant Java 客户端的最新版本和正确的 artifactId**

### 4.6 Qdrant HTTP 客户端实现（替代方案）

如果 Qdrant Java 客户端依赖无法解决，可以使用 HTTP 客户端直接调用 Qdrant REST API：

**文件**：`src/main/java/com/ityfz/yulu/common/ai/impl/QdrantHttpVectorStore.java`

```java
package com.ityfz.yulu.common.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ityfz.yulu.common.config.QdrantProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant HTTP 客户端实现（如果 Java 客户端无法使用）
 */
@Slf4j
@Service
public class QdrantHttpVectorStore {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final QdrantProperties properties;
    
    private String baseUrl;
    
    @Autowired
    public QdrantHttpVectorStore(RestTemplate restTemplate, ObjectMapper objectMapper, QdrantProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.baseUrl = String.format("http://%s:%d", properties.getHost(), properties.getPort());
    }
    
    /**
     * 创建集合
     */
    public void createCollection(String collectionName, int vectorSize) {
        try {
            String url = baseUrl + "/collections/" + collectionName;
            
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> vectors = new HashMap<>();
            vectors.put("size", vectorSize);
            vectors.put("distance", "Cosine");
            requestBody.put("vectors", vectors);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            if (properties.getApiKey() != null) {
                headers.set("api-key", properties.getApiKey());
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            restTemplate.put(url, request);
            
            log.info("[Qdrant HTTP] 集合创建成功: {}, dimension={}", collectionName, vectorSize);
        } catch (Exception e) {
            log.error("[Qdrant HTTP] 创建集合失败: {}", collectionName, e);
            throw new RuntimeException("创建集合失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 插入向量
     */
    public void upsertPoint(String collectionName, long pointId, List<Float> vector, Map<String, Object> payload) {
        try {
            String url = baseUrl + "/collections/" + collectionName + "/points";
            
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> points = new ArrayList<>();
            
            Map<String, Object> point = new HashMap<>();
            point.put("id", pointId);
            point.put("vector", vector);
            if (payload != null) {
                point.put("payload", payload);
            }
            points.add(point);
            
            requestBody.put("points", points);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            if (properties.getApiKey() != null) {
                headers.set("api-key", properties.getApiKey());
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            restTemplate.put(url, request);
            
            log.debug("[Qdrant HTTP] 插入点成功: collection={}, pointId={}", collectionName, pointId);
        } catch (Exception e) {
            log.error("[Qdrant HTTP] 插入点失败: collection={}, pointId={}", collectionName, pointId, e);
            throw new RuntimeException("插入点失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 相似度搜索
     */
    public List<Map<String, Object>> search(String collectionName, List<Float> queryVector, int topK) {
        try {
            String url = baseUrl + "/collections/" + collectionName + "/points/search";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("vector", queryVector);
            requestBody.put("limit", topK);
            requestBody.put("with_payload", true);
            requestBody.put("with_vector", false);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            if (properties.getApiKey() != null) {
                headers.set("api-key", properties.getApiKey());
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode result = root.get("result");
            
            List<Map<String, Object>> results = new ArrayList<>();
            if (result.isArray()) {
                for (JsonNode item : result) {
                    Map<String, Object> resultItem = new HashMap<>();
                    resultItem.put("id", item.get("id").asLong());
                    resultItem.put("score", item.get("score").asDouble());
                    if (item.has("payload")) {
                        resultItem.put("payload", objectMapper.convertValue(item.get("payload"), Map.class));
                    }
                    results.add(resultItem);
                }
            }
            
            log.debug("[Qdrant HTTP] 搜索成功: collection={}, topK={}, results={}", 
                    collectionName, topK, results.size());
            
            return results;
        } catch (Exception e) {
            log.error("[Qdrant HTTP] 搜索失败: collection={}", collectionName, e);
            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
        }
    }
}
```

---

## 五、使用示例

### 5.1 初始化集合

```java
@Service
public class RAGInitService {
    
    @Autowired
    private DashScopeEmbeddingService embeddingService;
    
    @Autowired
    private QdrantVectorStore vectorStore;  // 或 QdrantHttpVectorStore
    
    @PostConstruct
    public void init() {
        // 创建集合（每个租户一个集合，实现多租户隔离）
        String collectionName = "knowledge_base_tenant_001";
        int dimension = embeddingService.getDimension();  // 1536
        
        vectorStore.createCollection(collectionName, dimension);
    }
}
```

### 5.2 添加文档到向量库

```java
@Service
public class DocumentService {
    
    @Autowired
    private DashScopeEmbeddingService embeddingService;
    
    @Autowired
    private QdrantVectorStore vectorStore;
    
    public void addDocument(String collectionName, String text, Map<String, Object> metadata) {
        // 1. 生成向量
        List<Float> vector = embeddingService.embed(text);
        
        // 2. 插入到 Qdrant
        long pointId = System.currentTimeMillis();  // 或使用数据库自增 ID
        vectorStore.upsertPoint(collectionName, pointId, vector, metadata);
    }
}
```

### 5.3 检索相关文档

```java
@Service
public class RAGService {
    
    @Autowired
    private DashScopeEmbeddingService embeddingService;
    
    @Autowired
    private QdrantVectorStore vectorStore;
    
    public List<Map<String, Object>> search(String collectionName, String query, int topK) {
        // 1. 将查询转换为向量
        List<Float> queryVector = embeddingService.embed(query);
        
        // 2. 在 Qdrant 中搜索
        List<Points.ScoredPoint> results = vectorStore.search(collectionName, queryVector, topK, null);
        
        // 3. 转换为业务对象
        List<Map<String, Object>> documents = new ArrayList<>();
        for (Points.ScoredPoint point : results) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", point.getId().getNum());
            doc.put("score", point.getScore());
            doc.put("payload", point.getPayloadMap());
            documents.add(doc);
        }
        
        return documents;
    }
}
```

---

## 六、部署 Qdrant

### 6.1 Docker 部署（推荐）

```bash
# 拉取镜像
docker pull qdrant/qdrant

# 运行容器
docker run -p 6333:6333 -p 6334:6334 \
    -v $(pwd)/qdrant_storage:/qdrant/storage \
    qdrant/qdrant
```

### 6.2 Docker Compose

创建 `docker-compose.yml`：

```yaml
version: '3.8'

services:
  qdrant:
    image: qdrant/qdrant
    ports:
      - "6333:6333"  # HTTP API
      - "6334:6334"  # gRPC
    volumes:
      - ./qdrant_storage:/qdrant/storage
    environment:
      - QDRANT__SERVICE__HTTP_PORT=6333
      - QDRANT__SERVICE__GRPC_PORT=6334
```

启动：
```bash
docker-compose up -d
```

### 6.3 验证部署

```bash
# 检查健康状态
curl http://localhost:6333/health

# 查看集合列表
curl http://localhost:6333/collections
```

---

## 七、多租户隔离策略

### 7.1 方案：每个租户一个 Collection

```java
public String getCollectionName(Long tenantId) {
    return "knowledge_base_tenant_" + tenantId;
}
```

**优点**：
- ✅ 完全隔离
- ✅ 易于管理

**缺点**：
- ❌ Collection 数量多

### 7.2 方案：使用 Payload 过滤

```java
// 所有租户共用一个 Collection
String collectionName = "knowledge_base_all";

// 搜索时添加过滤条件
Points.Filter filter = Points.Filter.newBuilder()
    .setMust(Points.Condition.newBuilder()
        .setField(Points.FieldCondition.newBuilder()
            .setKey("tenant_id")
            .setMatch(Points.Match.newBuilder()
                .setValue(Points.Value.newBuilder()
                    .setIntegerValue(tenantId)
                    .build())
                .build())
            .build())
        .build())
    .build();

List<Points.ScoredPoint> results = vectorStore.search(collectionName, queryVector, topK, filter);
```

**优点**：
- ✅ 只需一个 Collection
- ✅ 节省资源

**缺点**：
- ❌ 需要过滤，性能略低

---

## 八、测试

### 8.1 单元测试示例

```java
@SpringBootTest
class DashScopeEmbeddingServiceTest {
    
    @Autowired
    private DashScopeEmbeddingService embeddingService;
    
    @Test
    void testEmbed() {
        String text = "你好，世界";
        List<Float> vector = embeddingService.embed(text);
        
        assertNotNull(vector);
        assertEquals(1536, vector.size());
    }
    
    @Test
    void testEmbedBatch() {
        List<String> texts = Arrays.asList("文本1", "文本2", "文本3");
        List<List<Float>> vectors = embeddingService.embedBatch(texts);
        
        assertNotNull(vectors);
        assertEquals(3, vectors.size());
        assertEquals(1536, vectors.get(0).size());
    }
}
```

---

## 九、常见问题

### 9.1 Qdrant 依赖无法解析

**解决方案**：
1. 使用 HTTP 客户端实现（见 4.6 节）
2. 检查 Maven 仓库配置
3. 尝试其他版本号

### 9.2 Embedding API 调用失败

**检查**：
1. API Key 是否正确
2. 网络连接是否正常
3. 请求格式是否符合 DashScope 要求

### 9.3 向量维度不匹配

**确保**：
- Embedding 服务返回的维度与 Qdrant Collection 配置的维度一致（1536）

---

## 十、总结

### 10.1 实现步骤

1. ✅ **修复 Qdrant 依赖**：使用 `qdrant-java:1.7.0` 或 HTTP 客户端
2. ✅ **配置 DashScope Embedding**：在 `application.yml` 中添加配置
3. ✅ **实现 EmbeddingService**：调用 DashScope API
4. ✅ **实现 QdrantVectorStore**：封装向量存储操作
5. ✅ **部署 Qdrant**：使用 Docker
6. ✅ **集成到 RAG 流程**：在 ChatService 中使用

### 10.2 下一步

- 实现文档切分（Chunking）
- 实现 RAG 服务（检索 + 生成）
- 集成到现有 ChatService

---

**文档完成时间**：2025-01-16  
**适用版本**：YuLu v1.0  
**技术栈**：Spring Boot 2.7.18, DashScope API, Qdrant

































