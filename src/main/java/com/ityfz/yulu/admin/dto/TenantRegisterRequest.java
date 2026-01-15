package com.ityfz.yulu.admin.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 租户注册请求。
 */
@Data
public class TenantRegisterRequest {

    @NotBlank
    private String tenantCode;

    @NotBlank
    private String tenantName;

    @NotBlank
    private String adminUsername;

    @NotBlank
    private String adminPassword;

    /**
     * 用户角色：ADMIN、AGENT、USER
     * 如果不提供，默认为 ADMIN（保持向后兼容）
     */
    private String role;

    /**
     * 租户标识码（可选）
     * 如果不提供，默认等于tenantCode
     * C端用户使用此字段登录，而不是tenantCode
     */
    private String tenantIdentifier;
}


