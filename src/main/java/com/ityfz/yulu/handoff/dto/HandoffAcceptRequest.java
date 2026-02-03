package com.ityfz.yulu.handoff.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 客服接受转人工请求DTO
 */
@Data
public class HandoffAcceptRequest {
    /**
     * 转人工请求ID
     */
    @NotNull(message = "转人工请求ID不能为空")
    private Long handoffRequestId;
}
