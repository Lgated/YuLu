package com.ityfz.yulu.user.dto;

import lombok.Data;

/**
 * 租户注册响应。
 */
@Data
public class TenantRegisterResponse {

    private Long tenantId;
    private String tenantCode;
    private Long adminUserId;
}












