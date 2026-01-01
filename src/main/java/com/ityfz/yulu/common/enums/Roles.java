package com.ityfz.yulu.common.enums;

// 用户角色枚举类
public final class Roles {
    private Roles() {}

    //管理员
    public static final String ADMIN = "ADMIN";
    //客服
    public static final String AGENT = "AGENT";
    //普通用户
    public static final String USER = "USER";

    /**
     * 简单判断是否管理员
     */
    public static boolean isAdmin(String role) {
        return ADMIN.equalsIgnoreCase(role);
    }

    public static boolean isAgent(String role) {
        return AGENT.equalsIgnoreCase(role);
    }

    public static boolean isUser(String role) {
        return USER.equalsIgnoreCase(role);
    }
}
