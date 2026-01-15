package com.ityfz.yulu.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 角色权限注解
 * 用于标记方法需要特定角色才能访问
 *
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 允许的角色（至少需要其中一个）
     */
    String[] value();

    /**
     * 错误提示信息
     */
    String message() default "权限不足，禁止访问";

}
