package com.ityfz.yulu.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索返回结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResultDTO {

    private Long documentId;
    private Long chunkId;
    private Integer chunkIndex;
    private String title;
    private String source;
    private String fileType;
    private String chunkText;
    private Double score;

}
