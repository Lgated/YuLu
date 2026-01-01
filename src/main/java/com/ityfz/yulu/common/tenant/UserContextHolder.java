package com.ityfz.yulu.common.tenant;

import com.ityfz.yulu.common.security.JwtUtil;
import com.ityfz.yulu.common.security.JwtUtil.LoginUser;

/**
 * 用户上下文持有者，基于 ThreadLocal 实现。
 * 用于在请求处理过程中存储和获取当前登录用户信息。
 */
public class UserContextHolder {
    private static final ThreadLocal<LoginUser> USER_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前登录用户
     */
    public static void setUser(LoginUser user) {
        USER_HOLDER.set(user);
    }

    /**
     * 获取当前登录用户
     */
    public static LoginUser getUser() {
        return USER_HOLDER.get();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        LoginUser user = getUser();
        return user != null ? user.getUserId() : null;
    }

    /**
     * 获取当前用户角色
     */
    public static String getRole() {
        LoginUser user = getUser();
        return user != null ? user.getRole() : null;
    }

    // 获取当前租户 ID
    public static Long getTenantId() {
        LoginUser user = getUser();
        return user != null ? user.getTenantId() : null;
    }

    // 获取当前用户名
    public static String getUsername() {
        LoginUser user = getUser();
        return user != null ? user.getUsername() : null;
    }

    /**
     * 清除当前线程的用户上下文
     */
    public static void clear() {
        USER_HOLDER.remove();
    }

}
