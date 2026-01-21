package com.ityfz.yulu.knowledge.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ityfz.yulu.common.ai.EmbeddingService;
import com.ityfz.yulu.common.ai.impl.QdrantVectorStore;
import com.ityfz.yulu.knowledge.entity.Chunk;
import com.ityfz.yulu.knowledge.entity.Document;
import com.ityfz.yulu.knowledge.mapper.ChunkMapper;
import com.ityfz.yulu.knowledge.mapper.DocumentMapper;
import com.ityfz.yulu.knowledge.service.ChunkIndexService;
import io.qdrant.client.grpc.JsonWithInt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ChunkIndexServiceImpl implements ChunkIndexService {

    // 集合名
    private static final String COLLECTION = "yulu_chunks";

    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final QdrantVectorStore qdrantVectorStore;

    public ChunkIndexServiceImpl(DocumentMapper documentMapper,
                                 ChunkMapper chunkMapper,
                                 EmbeddingService embeddingService,
                                 QdrantVectorStore qdrantVectorStore){
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
        this.qdrantVectorStore = qdrantVectorStore;
    }

    @Override
    @Transactional
    public void indexDocument(Long tenantId, Long documentId) {

        Document doc = documentMapper.selectOne(new LambdaQueryWrapper<Document>()
                .eq(Document::getTenantId, tenantId)
                .eq(Document::getId, documentId)
        );
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在或不属于当前租户: " + documentId);
        }

        //只查找未索引的chunk
        List<Chunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getDocumentId, documentId)
                .eq(Chunk::getTenantId, tenantId)
                .orderByAsc(Chunk::getChunkIndex)  //   按切片顺序返回
        );

        if (chunks == null || chunks.isEmpty()) {
            log.info("文档无 chunk，无需索引. docId={}", documentId);
            return;
        }
        int dimension = embeddingService.getDimension();
        qdrantVectorStore.createCollection(COLLECTION, dimension);
        // 1、批量向量化
        List<String> texts = chunks.stream().map(Chunk::getContent).toList();
        List<List<Float>> vectors = embeddingService.embedBatch(texts);
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("embedding 返回数量不一致: chunks=" + chunks.size() + ", vectors=" + vectors.size());
        }

        // 2、组装 points 并 upsert 到 Qdrant
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            List<Float> v = vectors.get(i);

            // 维度一致性校验
            if (v == null || v.size() != embeddingService.getDimension()) {
                throw new IllegalStateException("向量维度不匹配: got=" + (v == null ? 0 : v.size())
                        + ", expected=" + embeddingService.getDimension());
            }

            long pointId = c.getId(); // 幂等策略：pointId=chunkId
            // 构建负载
            Map<String, Object> payload = buildPayload(tenantId, doc, c);
            // 更新插入向量点
            qdrantVectorStore.upsertPoint(COLLECTION, pointId, v, payload);

            // DB 映射（如果你 pointId=chunkId，可直接写入）
            c.setQdrantPointId(pointId);
        }

        //TODO: mapper新增批量更新chunk的qdrantPointId的方法

        // 3) 批量更新 chunk 的 qdrantPointId（你目前 Mapper 没有批量方法，可选择循环 update 或补一个批量方法）
        //   如需批量方法，请在 Mapper + XML 中新增；此处示例循环更新（简单但多 SQL）
        for (Chunk c : chunks) {
            chunkMapper.updateById(c); // 仅更新 qdrantPointId，确保字段有值
        }

        // 4) 更新文档索引时间/状态
        doc.setIndexedAt(LocalDateTime.now());
        doc.setStatus(1); // 1-已索引
        documentMapper.updateById(doc);

        log.info("索引完成 docId={}, chunkCount={}", documentId, chunks.size());
    }



    @Override
    @Transactional
    public void rebuildDocumentIndex(Long tenantId, Long documentId) {
/*        // 可选：删除该文档在 Qdrant 的所有点（按 payload 过滤 document_id）
        qdrantVectorStore.deleteByFilter(COLLECTION, qdrantVectorStore.eqLong("tenant_id", tenantId),
                qdrantVectorStore.eqLong("document_id", documentId));

        // 清空 DB 映射（可选：需你在 Mapper 中实现）
        // chunkMapper.clearQdrantPointIdByDocument(documentId, tenantId);
        // documentMapper.clearIndexedAt(documentId, tenantId);

        // 重新索引
        indexDocument(tenantId, documentId);*/
    }


    // 构建payload
    private Map<String, Object> buildPayload(Long tenantId, Document doc, Chunk c) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("tenant_id", tenantId);
        raw.put("document_id", doc.getId());
        raw.put("chunk_id", c.getId());
        raw.put("chunk_index", c.getChunkIndex());
        raw.put("title", doc.getTitle());
        raw.put("source", doc.getSource());
        raw.put("file_type", doc.getFileType());

        // chunk 文本建议截断，避免 payload 过大（按你实际 chunkSize 决定）
        raw.put("chunk", safeTruncate(c.getContent(), 1000));
        return raw;
    }

    // 截断
    private String safeTruncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

}
