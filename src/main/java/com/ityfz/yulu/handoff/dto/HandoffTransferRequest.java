package com.ityfz.yulu.handoff.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 转人工申请请求DTO
 */
@Data
public class HandoffTransferRequest {


    /**
     * 会话ID
     */
    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    /**
     * 转人工原因（可选）
     */
    private String reason;
}
