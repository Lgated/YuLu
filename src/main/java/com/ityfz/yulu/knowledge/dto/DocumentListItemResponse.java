package com.ityfz.yulu.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 *  分页查询文件dto
 */
@Data
public class DocumentListItemResponse {

    private Long id;
    private String title;
    private String source;
    private String fileType;
    private Long fileSize;
    private Integer status;
    private LocalDateTime createTime;

}
