package com.ityfz.yulu.knowledge.service;

import com.ityfz.yulu.knowledge.entity.Chunk;

import java.util.List;

public interface ChunkService {

    /**
     * 切分文档内容
     *
     * @param content 文档内容
     * @param chunkSize 每个 Chunk 的最大字符数
     * @param overlapSize 重叠字符数
     * @return Chunk 列表（按顺序）
     */
    List<Chunk> chunkText(String content, int chunkSize, int overlapSize);


    /**
     * 切分文档并保存到数据库
     *
     * @param documentId 文档ID
     * @param tenantId 租户ID
     * @param content 文档内容
     * @param chunkSize 每个 Chunk 的最大字符数
     * @param overlapSize 重叠字符数
     * @return 保存的 Chunk 列表
     */
    List<Chunk> chunkAndSave(Long documentId, Long tenantId, String content,
                             int chunkSize, int overlapSize);

    /**
     * 根据文档ID获取所有 Chunk
     *
     * @param documentId 文档ID
     * @return Chunk 列表（按序号排序）
     */
    List<Chunk> getChunksByDocumentId(Long documentId);


    /**
     * 根据文档ID删除所有 Chunk
     *
     * @param documentId 文档ID
     */
    void deleteChunksByDocumentId(Long documentId);
}
