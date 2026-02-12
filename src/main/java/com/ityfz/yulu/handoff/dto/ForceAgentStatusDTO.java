package com.ityfz.yulu.handoff.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 强制客服状态DTO
 */
@Data
public class ForceAgentStatusDTO {
    @NotBlank(message = "状态不能为空")
    private String status; // ONLINE, AWAY, OFFLINE
}
