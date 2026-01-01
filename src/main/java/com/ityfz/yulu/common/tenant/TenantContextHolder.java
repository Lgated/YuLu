package com.ityfz.yulu.common.tenant;

/**
 * 租户上下文持有者，基于 ThreadLocal 实现。
 * 用于在请求处理过程中存储和获取当前租户信息。
 */
public class TenantContextHolder {


    private static final ThreadLocal<Long> TENANT_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> TENANT_CODE_HOLDER = new ThreadLocal<>();

    /**
     * 设置租户id
     */
    public static void setTenantId(Long tenantId){
        TENANT_ID_HOLDER.set(tenantId);
    }

    /**
     * 获取租户ID
     */
    public static Long getTenantId() {
        return TENANT_ID_HOLDER.get();
    }

    /**
     * 设置租户编码
     */
    public static void setTenantCode(String tenantCode) {
        TENANT_CODE_HOLDER.set(tenantCode);
    }

    /**
     * 获取租户编码
     */
    public static String getTenantCode() {
        return TENANT_CODE_HOLDER.get();
    }

    /**
     * 清除当前线程的租户上下文
     */
    public static void clear() {
        TENANT_ID_HOLDER.remove();
        TENANT_CODE_HOLDER.remove();
    }

}
