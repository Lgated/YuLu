package com.ityfz.yulu.knowledge.service;


/**
 * 索引服务
 */
public interface ChunkIndexService {

    /**
     * 索引某个文档下所有 chunk（幂等）
     */
    void indexDocument(Long tenantId, Long documentId);

    /**
     *  重建索引：先删 Qdrant 再索引（可选）
     */
    void rebuildDocumentIndex(Long tenantId, Long documentId);

}
