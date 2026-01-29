package com.ityfz.yulu.handoff.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 客服拒绝转人工请求DTO
 */
@Data
public class HandoffRejectRequest {

    /**
     * 转人工请求ID
     */
    @NotNull(message = "转人工请求ID不能为空")
    private Long handoffRequestId;

    /**
     * 拒绝原因（可选）
     */
    private String reason;
}
