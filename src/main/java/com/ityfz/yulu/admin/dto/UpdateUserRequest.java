package com.ityfz.yulu.admin.dto;


import lombok.Data;

import javax.validation.constraints.Pattern;

/**
 * 更新用户请求
 */
@Data
public class UpdateUserRequest {

    private String nickName;

    private String email;

    private String phone;

    /**
     * 角色（仅B端用户可修改）
     */
    @Pattern(regexp = "ADMIN|AGENT", message = "B端用户角色只能是ADMIN或AGENT")
    private String role;

    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;

}
