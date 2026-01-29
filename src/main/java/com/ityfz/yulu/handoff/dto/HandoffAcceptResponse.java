package com.ityfz.yulu.handoff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客服接受转人工响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoffAcceptResponse {

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
     * 工单ID
     */
    private Long ticketId;
}
