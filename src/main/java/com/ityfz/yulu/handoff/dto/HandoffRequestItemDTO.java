package com.ityfz.yulu.handoff.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 转人工请求列表项DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoffRequestItemDTO {


    /**
     * 转人工请求ID
     */
    private Long handoffRequestId;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 客户ID
     */
    private Long userId;

    /**
     * 客户名称
     */
    private String userName;

    /**
     * 工单ID
     */
    private Long ticketId;

    /**
     * 工单标题
     */
    private String ticketTitle;

    /**
     * 优先级
     */
    private String priority;

    /**
     * 转人工原因
     */
    private String reason;

    /**
     * 排队位置
     */
    private Integer queuePosition;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
