package com.ityfz.yulu.user.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO。
 */
@Data
public class LoginRequest {

    @NotBlank
    private String tenantCode;

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}












