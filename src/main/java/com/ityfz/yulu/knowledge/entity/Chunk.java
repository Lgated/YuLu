package com.ityfz.yulu.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切分实体
 */
@Data
@TableName("knowledge_chunk")
public class Chunk {


    /**
     * Chunk ID（主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 租户ID（冗余字段，便于查询）
     */
    private Long tenantId;

    /**
     * 在文档中的序号（从0开始）
     */
    private Integer chunkIndex;

    /**
     * Chunk 内容
     */
    private String content;

    /**
     * 内容长度（字符数）
     */
    private Integer contentLength;

    /**
     * Qdrant 中的 Point ID
     */
    private Long qdrantPointId;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}


