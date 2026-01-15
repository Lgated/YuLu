package com.ityfz.yulu.common.aspect;


import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.tenant.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 角色权限校验切面
 * 使用AOP统一处理权限校验，避免在每个Controller方法中重复写权限判断代码
 *
 * 执行顺序：Order(1) 确保在业务逻辑之前执行
 */

@Slf4j
@Aspect
@Component
@Order(1)  // 优先级设为1，确保在业务逻辑之前执行
public class RoleCheckAspect {

    /**
     * 定义切点：所有标注了 @RequireRole 的方法
     */
    @Pointcut("@annotation(com.ityfz.yulu.common.annotation.RequireRole)")
    public void requireRolePointcut() {}

    /**
     * 定义切点：所有标注了 @RequireAnyRole 的方法
     */
    @Pointcut("@annotation(com.ityfz.yulu.common.annotation.RequireAnyRole)")
    public void requireAnyRolePointcut() {}

    /**
     * 定义切点：类上标注了 @RequireRole 的所有方法
     */
    @Pointcut("@within(com.ityfz.yulu.common.annotation.RequireRole)")
    public void requireRoleClassPointcut() {}

    /**
     * 定义切点：类上标注了 @RequireAnyRole 的所有方法
     */
    @Pointcut("@within(com.ityfz.yulu.common.annotation.RequireAnyRole)")
    public void requireAnyRoleClassPointcut() {}


    /**
     * 处理 @RequireRole 注解
     */
    @Before("@annotation(com.ityfz.yulu.common.annotation.RequireRole)")
    public void checkRole(JoinPoint joinPoint) {
        //获取当前登录用户的角色
        String currentRole = UserContextHolder.getRole();
        if (currentRole == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        //joinPoint.getSignature();	把 JoinPoint 里的通用签名强转成 方法级 签名，这样才能拿到 Method 对象。
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireRole annotation = method.getAnnotation(RequireRole.class);

        if (annotation == null) {
            return;
        }

        String[] requiredRoles = annotation.value();
        String message = annotation.message();

        boolean hasPermission = Arrays.stream(requiredRoles)
                .anyMatch(role -> role.equalsIgnoreCase(currentRole));

        if (!hasPermission) {
            log.warn("权限校验失败: 当前角色={}, 需要角色={}, 方法={}.{}",
                    currentRole, Arrays.toString(requiredRoles),
                    method.getDeclaringClass().getSimpleName(), method.getName());
            throw new BizException(ErrorCodes.FORBIDDEN, message);
        }

        log.debug("权限校验通过: 角色={}, 方法={}.{}",
                currentRole, method.getDeclaringClass().getSimpleName(), method.getName());
    }

    /**
     * 处理 @RequireAnyRole 注解
     *//*
    @Before("@annotation(com.ityfz.yulu.common.annotation.RequireAnyRole)")
    public void checkAnyRole(JoinPoint joinPoint) {
        String currentRole = UserContextHolder.getRole();
        if (currentRole == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireAnyRole annotation = method.getAnnotation(RequireAnyRole.class);

        if (annotation == null) {
            return;
        }

        String[] requiredRoles = annotation.value();
        String message = annotation.message();

        boolean hasPermission = Arrays.stream(requiredRoles)
                .anyMatch(role -> role.equalsIgnoreCase(currentRole));

        if (!hasPermission) {
            log.warn("权限校验失败: 当前角色={}, 需要角色之一={}, 方法={}.{}",
                    currentRole, Arrays.toString(requiredRoles),
                    method.getDeclaringClass().getSimpleName(), method.getName());
            throw new BizException(ErrorCodes.FORBIDDEN, message);
        }

        log.debug("权限校验通过: 角色={}, 方法={}.{}",
                currentRole, method.getDeclaringClass().getSimpleName(), method.getName());
    }
*/
    /**
     * 处理类级别的 @RequireRole 注解
     */
    @Before("@within(com.ityfz.yulu.common.annotation.RequireRole)")
    public void checkRoleOnClass(JoinPoint joinPoint) {
        String currentRole = UserContextHolder.getRole();
        if (currentRole == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        RequireRole annotation = joinPoint.getTarget().getClass()
                .getAnnotation(RequireRole.class);

        if (annotation == null) {
            return;
        }

        String[] requiredRoles = annotation.value();
        String message = annotation.message();

        boolean hasPermission = Arrays.stream(requiredRoles)
                .anyMatch(role -> role.equalsIgnoreCase(currentRole));

        if (!hasPermission) {
            log.warn("权限校验失败: 当前角色={}, 需要角色={}, 类={}",
                    currentRole, Arrays.toString(requiredRoles),
                    joinPoint.getTarget().getClass().getSimpleName());
            throw new BizException(ErrorCodes.FORBIDDEN, message);
        }
    }

   /* *//**
     * 处理类级别的 @RequireAnyRole 注解
     *//*
    @Before("@within(com.ityfz.yulu.common.annotation.RequireAnyRole)")
    public void checkAnyRoleOnClass(JoinPoint joinPoint) {
        String currentRole = UserContextHolder.getRole();
        if (currentRole == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        RequireAnyRole annotation = joinPoint.getTarget().getClass()
                .getAnnotation(RequireAnyRole.class);

        if (annotation == null) {
            return;
        }

        String[] requiredRoles = annotation.value();
        String message = annotation.message();

        boolean hasPermission = Arrays.stream(requiredRoles)
                .anyMatch(role -> role.equalsIgnoreCase(currentRole));

        if (!hasPermission) {
            log.warn("权限校验失败: 当前角色={}, 需要角色之一={}, 类={}",
                    currentRole, Arrays.toString(requiredRoles),
                    joinPoint.getTarget().getClass().getSimpleName());
            throw new BizException(ErrorCodes.FORBIDDEN, message);
        }
    }*/

}
