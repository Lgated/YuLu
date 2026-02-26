package com.ityfz.yulu.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 回答引用来源（对应某条检索结果）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRefDTO {

    private Long documentId;
    private Long chunkId;
    private Integer chunkIndex;
    private String title;
    private String source;
    private String fileType;
    private Double score;
}























