package com.ityfz.yulu.handoff.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转人工记录详情
 */
@Data
@Builder
public class HandoffRecordVO {

    private Long handoffRequestId;
    private Long sessionId;
    private Long userId;
    private String userName;
    private Long agentId;
    private String agentName;
    private Long ticketId;
    private String status;
    private String priority;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private Long waitDurationSeconds; // 等待耗时
    private Long chatDurationSeconds; // 通话耗时

}
