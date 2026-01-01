package com.ityfz.yulu.common.security;

import com.ityfz.yulu.common.enums.Roles;
import com.ityfz.yulu.common.error.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.tenant.UserContextHolder;

/**
 * 简易的权限校验
 */
public class SecurityUtil {

    private SecurityUtil(){}

    public static Long currentUserId() {
        return UserContextHolder.getUserId();
    }

    public static Long currentTenantId() {
        return UserContextHolder.getTenantId();
    }

    public static String currentRole() {
        return UserContextHolder.getRole();
    }

    public static String currentUsername() {
        return UserContextHolder.getUsername();
    }

    // 检查是否至少是某个角色
    public static void checkAdmin() {
        if (!Roles.isAdmin(currentRole())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "当前用户不是管理员，禁止访问");
        }
    }

    public static void checkAgentOrAdmin() {
        String role = currentRole();
        if (!Roles.isAdmin(role) && !Roles.isAgent(role)) {
            throw new BizException(ErrorCodes.FORBIDDEN, "仅客服或管理员可访问");
        }
    }
}
