package com.ityfz.yulu.admin.dto;

import lombok.Data;

/**
 * 用户列表查询请求
 */
@Data
public class UserListRequest {

    /**
     * 角色筛选：USER/ADMIN/AGENT，如果为null则查询所有
     */
    private String role;

    /**
     * 状态筛选：1-启用，0-禁用，如果为null则查询所有
     */
    private Integer status;

    /**
     * 关键词搜索（用户名、昵称、邮箱、手机号）
     */
    private String keyword;

    /**
     * 页码（从1开始）
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 10;

}
