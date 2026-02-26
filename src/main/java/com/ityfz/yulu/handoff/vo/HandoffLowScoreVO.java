package com.ityfz.yulu.handoff.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HandoffLowScoreVO {
    private Long id;
    private Long handoffRequestId;
    private Long sessionId;
    private Long userId;
    private Long agentId;
    private Integer score;
    private String comment;
    private String status;
    private LocalDateTime submitTime;
    private LocalDateTime processedTime;
}