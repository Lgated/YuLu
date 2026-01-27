package com.ityfz.yulu.common.ai.impl;

import com.ityfz.yulu.common.config.QdrantProperties;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.ValueFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Qdrant 向量存储服务
 */
@Slf4j
@Service
public class QdrantVectorStore {

    private final QdrantProperties properties;

    /**
     * -- GETTER --
     *  获取客户端（用于高级操作）
     */
    @Getter
    private QdrantClient client;

    @Autowired
    public QdrantVectorStore(QdrantProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            // 构建 gRPC 客户端
            QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(
                    properties.getHost(),
                    properties.getGrpcPort(),
                    false // false 表示不使用 SSL/TLS
            );
            
         /*   // 如果配置了 API Key，则设置
            if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()) {
                grpcClientBuilder.withApiKey(properties.getApiKey());
            }*/
            
            // 创建 Qdrant 客户端
            client = new QdrantClient(grpcClientBuilder.build());
            // 测试连接：简单的健康检查
            client.healthCheckAsync().get();
            log.info("[Qdrant] 连接成功: {}:{}", properties.getHost(), properties.getGrpcPort());
        } catch (Exception e) {
            log.error("[Qdrant] 连接失败", e);
            throw new RuntimeException("Qdrant 连接失败", e);
        }
    }

    /**
     *  关闭客户端
     */
    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.close(); // 新版 close 不需要 try-catch，或者它是 AutoCloseable
            log.info("[Qdrant] 连接已关闭");
        }
    }


    /**
     * 创建集合
     * @param collectionName 集合名字
     * @param vectorSize 向量维度
     */
    public void createCollection(String collectionName, int vectorSize) {
        try {
            // 1. 检查集合是否存在
            // 推荐方式：获取所有集合列表进行比对，避免处理异常
            boolean exists = client.listCollectionsAsync().get().stream() //异步拉取当前 Qdrant 里 所有集合名。
                    .anyMatch(c -> c.equals(collectionName)); //anyMatch(...)：看返回的列表里有没有同名集合

            if (exists) {
                log.info("[Qdrant] 集合已存在: {}", collectionName);
                return;
            }

            // 2. 配置向量参数
            Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                    .setSize(vectorSize) // // 维度
                    .setDistance(Collections.Distance.Cosine) // 相似度算法：余弦
                    .build();

            // 3. 创建集合
            client.createCollectionAsync(collectionName, vectorParams).get();

            log.info("[Qdrant] 集合创建成功: {}, dimension={}", collectionName, vectorSize);
        } catch (Exception e) {
            log.error("[Qdrant] 创建集合失败: {}", collectionName, e);
            throw new RuntimeException("创建集合失败", e);
        }
    }

    /**
     * 插入/更新向量点
     * “给某条业务记录（已知主键）生成新向量后，一键更新到 Qdrant，保证后续搜索能立即看到最新语义。”
     */
    public void upsertPoint(String collectionName, long pointId, List<Float> vector, Map<String, Object> payload) {
        try {
            // 1. 构建基础点结构
            Points.PointStruct.Builder pointBuilder = Points.PointStruct.newBuilder()
                    .setId(Points.PointId.newBuilder().setNum(pointId).build())
                    .setVectors(Points.Vectors.newBuilder()
                            .setVector(Points.Vector.newBuilder().addAllData(vector).build())
                            .build());

            // 2. 处理 Payload (元数据)
            if (payload != null && !payload.isEmpty()) {
                Map<String, JsonWithInt.Value> qdrantPayload = new HashMap<>();
                for (Map.Entry<String, Object> entry : payload.entrySet()) {
                    // 使用下方的辅助方法进行转换
                    JsonWithInt.Value value = objectToValue(entry.getValue());
                    if (value != null) {
                        qdrantPayload.put(entry.getKey(), value);
                    }
                }
                pointBuilder.putAllPayload(qdrantPayload);
            }

            // 3. 执行插入
            client.upsertAsync(collectionName, List.of(pointBuilder.build())).get();

            log.debug("[Qdrant] 插入点成功: collection={}, pointId={}", collectionName, pointId);
        } catch (Exception e) {
            log.error("[Qdrant] 插入点失败: collection={}, pointId={}", collectionName, pointId, e);
            throw new RuntimeException("插入点失败", e);
        }
    }


    /**
     * 相似度搜索
     */
    public List<Points.ScoredPoint> search(String collectionName, List<Float> queryVector, int topK, Points.Filter filter) {
        try {

            // 1. 先检查集合是否存在，不存在则返回空列表（兜底策略）
            if (!collectionExists(collectionName)) {
                log.warn("[Qdrant] 集合不存在，返回空结果: collection={}", collectionName);
                return java.util.Collections.emptyList();
            }

            // 2. 构建搜索请求
            Points.SearchPoints.Builder searchBuilder = Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(queryVector) // 把 List<Float> 一次性塞进去
                    .setLimit(topK) // 最多回多少条
                    .setWithPayload( // 要求把原始字段也带回来
                            Points.WithPayloadSelector.newBuilder().setEnable(true).build());

            if (filter != null) {
                searchBuilder.setFilter(filter);
            }

            // 3. 执行搜索
            // 返回值直接就是 List<ScoredPoint>，不再需要从 response.getResultList() 获取
            List<Points.ScoredPoint> results = client.searchAsync(searchBuilder.build()).get();

            log.debug("[Qdrant] 搜索成功: collection={}, topK={}, resultCount={}",
                    collectionName, topK, results.size());

            return results;

        } catch (Exception e) {
            log.error("[Qdrant] 搜索失败: collection={}", collectionName, e);
            throw new RuntimeException("搜索失败", e);
        }
    }

    /**
     * 检查集合是否存在
     * @param collectionName 集合名称
     * @return 是否存在
     */
    private boolean collectionExists(String collectionName) {
        try {
            List<String> collections = client.listCollectionsAsync().get();
            return collections.stream().anyMatch(c -> c.equals(collectionName));
        } catch (Exception e) {
            log.warn("[Qdrant] 检查集合是否存在失败: collection={}", collectionName, e);
            return false;
        }
    }

    /**
     * 辅助方法：将 Object 转换为 Qdrant 的 Value 类型
     * ValueFactory 不支持直接传 Object，必须手动判断类型
     * 注意：Qdrant 1.10.0 中 Value 类型是 JsonWithInt.Value，不是 Points.Value
     */
    private JsonWithInt.Value objectToValue(Object value) {
        if (value == null) return null;

        if (value instanceof String) {
            return ValueFactory.value((String) value);
        } else if (value instanceof Integer) {
            return ValueFactory.value((Integer) value);
        } else if (value instanceof Long) {
            return ValueFactory.value((Long) value);
        } else if (value instanceof Float) {
            // Qdrant 底层数值是 double
            return ValueFactory.value(((Float) value).doubleValue());
        } else if (value instanceof Double) {
            return ValueFactory.value((Double) value);
        } else if (value instanceof Boolean) {
            return ValueFactory.value((Boolean) value);
        } else if (value instanceof List) {
            // 如果是列表，这里可以根据需要进一步处理，暂且转为 String 或者忽略
            return ValueFactory.value(value.toString());
        }
        // 默认转 String
        return ValueFactory.value(value.toString());
    }


    /**
     * 获取集合信息
     *
     * @param collectionName 集合名称
     * @return 集合信息
     */
    public Collections.CollectionInfo getCollectionInfo(String collectionName) {
        try {
            return client.getCollectionInfoAsync(collectionName).get();
        } catch (Exception e) {
            log.error("[Qdrant] 获取集合信息失败: collection={}", collectionName, e);
            throw new RuntimeException("获取集合信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除集合
     *
     * @param collectionName 集合名称
     */
    public void deleteCollection(String collectionName) {
        try {
            client.deleteCollectionAsync(collectionName).get();
            log.info("[Qdrant] 集合删除成功: {}", collectionName);
        } catch (Exception e) {
            log.error("[Qdrant] 删除集合失败: collection={}", collectionName, e);
            throw new RuntimeException("删除集合失败: " + e.getMessage(), e);
        }
    }

}
