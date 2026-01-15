package com.ityfz.yulu.customer.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * C端注册请求
 * 用户需要输入租户标识码
 */
@Data
public class CustomerRegisterRequest {
    @NotBlank(message = "租户标识不能为空")
    private String tenantIdentifier;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String nickName;
    private String email;
    private String phone;
}

