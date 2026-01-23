package com.ityfz.yulu.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 创建用户请求
 */
@Data
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = ".{6,20}", message = "密码长度必须在6-20位之间")
    private String password;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "USER|ADMIN|AGENT", message = "角色必须是USER、ADMIN或AGENT")
    private String role;

    private String nickName;

    private String email;

    private String phone;

    /**
     * 状态：1-启用，0-禁用，默认为1
     */
    private Integer status = 1;

}
