package com.ityfz.yulu.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 重置密码请求
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = ".{6,20}", message = "密码长度必须在6-20位之间")
    private String newPassword;

}
