package com.ityfz.yulu.common.error;

/**
 * 统一错误码常量定义。
 */
public final class ErrorCodes {
    private ErrorCodes() {}

    // 通用
    public static final String SUCCESS = "SUCCESS";
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    // 租户/用户/鉴权
    public static final String TENANT_EXISTS = "TENANT_EXISTS";
    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    public static final String TENANT_REQUIRED = "TENANT_REQUIRED";
    public static final String USER_EXISTS = "USER_EXISTS";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    public static final String INVALID_ROLE = "INVALID_ROLE";
    public static final String TOKEN_INVALID = "TOKEN_INVALID";

    // 聊天/会话
    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    public static final String CHAT_RATE_LIMIT = "CHAT_RATE_LIMIT";
}

