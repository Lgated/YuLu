package com.ityfz.yulu.handoff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 转人工申请响应DTO
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class HandoffTransferResponse {

    /**
     * 转人工请求ID
     */
    private Long handoffRequestId;

    /**
     * 关联工单ID
     */
    private Long ticketId;

    /**
     * 排队位置
     */
    private Integer queuePosition;

    /**
     * 预计等待时间（秒）
     */
    private Integer estimatedWaitTime;

}
