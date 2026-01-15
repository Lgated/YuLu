package com.ityfz.yulu.admin.dto;

import lombok.Data;

@Data
public class UserRegisterResponse {
    private Long userId;
    private Long tenantId;
    private String tenantCode;
    private String username;
    private String role;
}
