package com.ityfz.yulu.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档查询dto
 */
@Data
public class DocumentDetailResponse {

    private Long id;
    private String title;
    private String source;
    private String fileType;
    private Long fileSize;
    private Integer status;
    private LocalDateTime indexedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String contentPreview; // 建议截取前 500~1000 字符


}
