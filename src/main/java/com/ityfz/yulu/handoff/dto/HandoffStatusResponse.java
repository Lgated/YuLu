package com.ityfz.yulu.handoff.dto;

import lombok.Data;

/**
 * 转人工状态查询响应DTO
 */
@Data
public class HandoffStatusResponse {

    /**
     * 转人工请求ID
     */
    private Long handoffRequestId;

    /**
     * 状态
     */
    private String status;

    /**
     * 排队位置
     */
    private Integer queuePosition;

    /**
     * 预计等待时间（秒）
     */
    private Integer estimatedWaitTime;

    /**
     * 分配的客服ID
     */
    private Long assignedAgentId;

    /**
     * 分配的客服名称
     */
    private String assignedAgentName;
}


