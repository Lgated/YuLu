package com.ityfz.yulu.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserRegisterRequest {
    @NotBlank
    private String tenantCode;

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    /**
     * 角色：ADMIN / AGENT / USER
     */
    @NotBlank
    private String role;

    private String nickName;
    private String email;
    private String phone;
}
