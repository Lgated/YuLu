package com.ityfz.yulu.handoff.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class HandoffRatingRecordVO {
    private Long id;
    private Long handoffRequestId;
    private Long sessionId;
    private Long userId;
    private Long agentId;
    private Integer score;
    private List<String> tags;
    private String comment;
    private String status;
    private LocalDateTime submitTime;
    private Long processedBy;
    private String processedNote;
    private LocalDateTime processedTime;
}