package com.ityfz.yulu.handoff.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服实时状态看板
 */
@Data
@Builder
public class AgentMonitorVO {

    private Long agentId;
    private String agentName;
    private String status;         // 来自 Redis
    private Integer currentSessions; // 来自 Redis
    private Integer maxSessions;
    private LocalDateTime lastActiveTime;
}
