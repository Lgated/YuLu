package com.ityfz.yulu.admin.dto;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息响应
 */
@Data
public class UserResponse {

    private Long id;
    private Long tenantId;
    private String username;
    private String role;
    private Integer status;
    private String nickName;
    private String email;
    private String phone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 状态文本（前端显示用）
     */
    private String statusText;

    /**
     * 角色文本（前端显示用）
     */
    private String roleText;

}
