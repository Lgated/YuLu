package com.ityfz.yulu.handoff.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 用户结束对话请求
 */
@Data
public class EndHandoffByUserRequest {

    @NotNull
    private Long handoffRequestId;
}




