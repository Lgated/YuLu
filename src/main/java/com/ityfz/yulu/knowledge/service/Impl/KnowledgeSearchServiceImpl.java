package com.ityfz.yulu.knowledge.service.Impl;

import com.ityfz.yulu.common.ai.EmbeddingService;
import com.ityfz.yulu.common.ai.impl.QdrantVectorStore;
import com.ityfz.yulu.knowledge.dto.RetrievalResultDTO;
import com.ityfz.yulu.knowledge.service.KnowledgeSearchService;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {

    private static final String COLLECTION = "yulu_chunks";

    private final EmbeddingService embeddingService;
    private final QdrantVectorStore qdrantVectorStore;


    @Override
    public List<RetrievalResultDTO> search(Long tenantId, String query, int topK, double minScore) {
        // minScore：相似度门槛，低于该值直接丢弃
        // 1、将用户问题向量化
        List<Float> qv = embeddingService.embed(query);

        if (qv == null || qv.size() != embeddingService.getDimension()) {
            throw new IllegalStateException("query 向量维度异常: got=" + (qv == null ? 0 : qv.size())
                    + ", expected=" + embeddingService.getDimension());
        }

        // Qdrant 搜索（tenant 过滤 + payload 返回）
        Points.Filter filter = buildTenantFilter(tenantId);
        List<Points.ScoredPoint> points = qdrantVectorStore.search(COLLECTION, qv, topK, filter);

        // 二次排序 + 阈值过滤 + DTO 化
        return points.stream()
                // 按分数倒叙，高的在前
                .sorted(Comparator.comparingDouble(Points.ScoredPoint::getScore).reversed())
                // 滤掉低分，防止“无关段落”混进来
                .filter(p -> p.getScore() >= minScore)
                // 把 ScoredPoint 转成业务对象
                .map(this::toDTO)
                .collect(Collectors.toList());
    }


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
                .score((double) p.getScore())
                .build();
    }


    // 构建过滤链 ： 按照租户id
    private Points.Filter buildTenantFilter(Long tenantId) {
        if (tenantId == null) return null;

        Points.FieldCondition cond = Points.FieldCondition.newBuilder()
                .setKey("tenant_id") // 要匹配的字段名
                .setMatch(Points.Match.newBuilder().setInteger(tenantId)) // 整型精确匹配
                .build();

        return Points.Filter.newBuilder()
                .addMust(Points.Condition.newBuilder().setField(cond))
                .build();
    }


    private Long getLong(Map<String, JsonWithInt.Value> p, String k) {
        JsonWithInt.Value v = p.get(k);
        if (v == null) return null;
        // 使用 getKindCase() 判断类型
        JsonWithInt.Value.KindCase kindCase = v.getKindCase();
        if (kindCase == JsonWithInt.Value.KindCase.INTEGER_VALUE) {
            return v.getIntegerValue();
        } else if (kindCase == JsonWithInt.Value.KindCase.DOUBLE_VALUE) {
            return (long) v.getDoubleValue();
        } else if (kindCase == JsonWithInt.Value.KindCase.STRING_VALUE) {
            try {
                return Long.parseLong(v.getStringValue());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer getInt(Map<String, JsonWithInt.Value> p, String k) {
        Long l = getLong(p, k);
        return l == null ? null : l.intValue();
    }

    private String getString(Map<String, JsonWithInt.Value> p, String k) {
        JsonWithInt.Value v = p.get(k);
        if (v == null) return null;
        // 使用 getKindCase() 判断类型
        JsonWithInt.Value.KindCase kindCase = v.getKindCase();
        if (kindCase == JsonWithInt.Value.KindCase.STRING_VALUE) {
            return v.getStringValue();
        } else if (kindCase == JsonWithInt.Value.KindCase.INTEGER_VALUE) {
            return String.valueOf(v.getIntegerValue());
        } else if (kindCase == JsonWithInt.Value.KindCase.DOUBLE_VALUE) {
            return String.valueOf(v.getDoubleValue());
        } else if (kindCase == JsonWithInt.Value.KindCase.BOOL_VALUE) {
            return String.valueOf(v.getBoolValue());
        }
        return null;
    }
}
