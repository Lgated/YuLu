package com.ityfz.yulu.customer.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * C端登录请求
 */
@Data
public class CustomerLoginRequest {
    @NotBlank(message = "租户标识不能为空")
    private String tenantIdentifier;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
