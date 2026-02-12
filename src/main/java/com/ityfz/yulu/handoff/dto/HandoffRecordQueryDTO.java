package com.ityfz.yulu.handoff.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 记录分页查询DTO
 */
@Data
public class HandoffRecordQueryDTO {
    private Long userId;
    private Long agentId;
    private String status;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
