package com.ityfz.yulu.user.dto;

import lombok.Data;

/**
 * 登录响应 DTO。
 */
@Data
public class LoginResponse {

    private String token;
    private long expireIn;
    private Long userId;
    private Long tenantId;
    private String role;
    private String username;
}












